"""Import boundaries and the actual local HTTP/CLI workflow."""
from __future__ import annotations

import contextlib
import http.client
import io
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from partner_auto.app import ImportServer, dump_json, import_sources, main, read_sources, validate_settings, running_ui_url


PP = {"version": "1.5.0", "startPoint": {"x": 20, "y": 20, "headingDeg": 0},
      "lines": [{"id": "drive", "kind": "atomic", "endPoint": {"x": 50, "y": 20},
                 "controlPoints": [], "heading": {"type": "constant", "degrees": 0}}],
      "sequence": [{"kind": "path", "lineId": "drive"}]}


class AppChecks(unittest.TestCase):
    def test_model_rejects_invalid_and_unknown_parameters(self):
        for supplied in ({"speed_in_s": 0}, {"speed_in_s": float("nan")},
                         {"speed_in_s": True}, {"sample_period_s": 0.001},
                         {"start_delay_s": 31}, {"speed": 123}):
            with self.subTest(settings=supplied), self.assertRaises(ValueError):
                validate_settings(supplied)

    def test_full_pp_import_produces_profile_not_just_parse_tree(self):
        result = import_sources({"partner.pp": json.dumps(PP)})
        self.assertEqual(result["format"], "pp")
        self.assertEqual(result["status"], "complete", result.get("diagnostics"))
        self.assertTrue(result["timeline"])
        self.assertTrue(result["reservations"])
        self.assertTrue(result["summary"]["prediction_only"])
        self.assertIn('"schema_version": "1.0"', dump_json(result))

    def test_nonfinite_pp_json_never_exports(self):
        with self.assertRaisesRegex(ValueError, "Non-finite"):
            import_sources({"bad.pp": '{"x": NaN}'})

    def test_java_syntax_failure_is_not_presented_as_entry_selection(self):
        result = import_sources({"broken.java": "@Autonomous class Broken { void runOpMode( {"})
        self.assertEqual(result["status"], "invalid")
        self.assertIn("JAVA_SYNTAX", [item["code"] for item in result["diagnostics"]])

    def test_multiple_pp_projects_are_not_silently_merged(self):
        with self.assertRaises(ValueError):
            import_sources({"a.pp": json.dumps(PP), "b.pp": json.dumps(PP)}, kind="pp")

    def test_zip_import_is_in_memory_and_preserves_dependency_sources(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            archive = folder / "partner.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr("repo/TeamCode/src/main/java/Auto.java", "class Auto {}")
                zipped.writestr("repo/build/ignored.java", "bad")
                zipped.writestr("repo/shared/Other.java", "class Other {}")
            result = read_sources([archive])
            self.assertEqual(len(result), 2)
            self.assertTrue(any(name.endswith("Auto.java") for name in result))
            self.assertTrue(any(name.endswith("Other.java") for name in result))
            self.assertEqual(list(folder.iterdir()), [archive], "ZIP should not extract project files")

    def test_explicit_file_in_build_is_still_importable(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "build" / "partner.pp"
            path.parent.mkdir()
            path.write_text(json.dumps(PP), encoding="utf-8-sig")
            self.assertEqual(len(read_sources([path])), 1)

    def test_cli_writes_reusable_json_and_reports_errors(self):
        with tempfile.TemporaryDirectory() as temporary:
            source, output = Path(temporary) / "input.pp", Path(temporary) / "output.json"
            source.write_text(json.dumps(PP), encoding="utf-8")
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                status = main([str(source), "--output", str(output)])
                bad = main([str(source), "--entry", "unused", "--settings", str(output)])
            self.assertEqual(status, 0)
            self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["format"], "pp")
            self.assertEqual(bad, 2)

    def test_http_paste_import_and_session_boundaries(self):
        server = ImportServer(0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=20)
        try:
            connection.request("GET", "/?token=" + server.token)
            response = connection.getresponse()
            page = response.read().decode()
            self.assertEqual(response.status, 200)
            self.assertIn("Save pasted source", page)
            self.assertNotIn("__IMPORT_TOKEN__", page)
            self.assertIn("frame-ancestors 'none'", response.headers["Content-Security-Policy"])
            body = json.dumps({"sources": {"Pasted.pp": json.dumps(PP)}})
            connection.request("POST", "/api/import", body, {"Content-Type": "application/json"})
            response = connection.getresponse()
            response.read()
            self.assertEqual(response.status, 403)
            connection.request("POST", "/api/import", body,
                               {"Content-Type": "application/json", "X-Import-Token": server.token})
            response = connection.getresponse()
            result = json.loads(response.read())
            self.assertEqual(response.status, 200, result)
            self.assertEqual(result["status"], "complete", result)
            connection.request("POST", "/api/import", body,
                               {"Content-Type": "application/json", "X-Import-Token": server.token,
                                "Origin": "https://example.com"})
            response = connection.getresponse()
            response.read()
            self.assertEqual(response.status, 403)
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_health_and_explicit_shutdown_use_the_local_session(self):
        server = ImportServer(0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5)
        try:
            self.assertEqual(running_ui_url(server.server_port), server.ui_url())
            connection.request("POST", "/api/shutdown")
            response = connection.getresponse()
            response.read()
            self.assertEqual(response.status, 403)
            connection.request("POST", "/api/shutdown", headers={"X-Import-Token": server.token})
            response = connection.getresponse()
            self.assertEqual(json.loads(response.read()), {"stopped": True})
            thread.join(timeout=3)
            self.assertFalse(thread.is_alive())
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)

    def test_detached_launcher_keeps_python_alive_and_reuses_existing_service(self):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        launcher = Path(__file__).resolve().parents[1] / "import_partner_auto.py"
        command = [sys.executable, str(launcher), "--ui", "--detach", "--no-browser", "--port", str(port)]
        address = None
        try:
            first = subprocess.run(command, capture_output=True, text=True, timeout=20)
            self.assertEqual(first.returncode, 0, first.stdout + first.stderr)
            self.assertIn("stays running", first.stdout)
            address = running_ui_url(port)
            self.assertIsNotNone(address, "Python must still answer after the launcher has exited")
            second = subprocess.run(command, capture_output=True, text=True, timeout=10)
            self.assertEqual(second.returncode, 0, second.stdout + second.stderr)
            self.assertIn("existing local importer", second.stdout)
            self.assertEqual(running_ui_url(port), address)
        finally:
            if address:
                from urllib.parse import parse_qs, urlsplit
                token = parse_qs(urlsplit(address).query)["token"][0]
                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=3)
                try:
                    connection.request("POST", "/api/shutdown", headers={"X-Import-Token": token})
                    response = connection.getresponse()
                    response.read()
                finally:
                    connection.close()

    def test_server_returns_structured_error_then_accepts_the_next_import(self):
        server = ImportServer(0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=10)
        headers = {"Content-Type": "application/json", "X-Import-Token": server.token}
        try:
            for body, expected in (("{not JSON", 400), (json.dumps({"sources": {"good.pp": json.dumps(PP)}}), 200)):
                connection.request("POST", "/api/import", body, headers)
                response = connection.getresponse()
                result = json.loads(response.read())
                self.assertEqual(response.status, expected, result)
                if expected == 400:
                    self.assertIn("error", result)
                else:
                    self.assertEqual(result["status"], "complete")
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
