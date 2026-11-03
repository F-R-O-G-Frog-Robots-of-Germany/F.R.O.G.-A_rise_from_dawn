"""CLI and localhost interface for importing partner source without executing it."""
from __future__ import annotations

import argparse
from collections.abc import Mapping
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import logging
import math
import os
from pathlib import Path, PurePosixPath
import secrets
import subprocess
import sys
import threading
import time
from urllib.parse import parse_qs, urlsplit
import webbrowser
import zipfile


HERE = Path(__file__).resolve().parent
PROJECT_ROOT = HERE.parents[2]
DEFAULT_UI_PORT = 8765
MAX_FILES = 1000
MAX_BYTES = 24 * 1024 * 1024
SKIP_PARTS = {".git", ".gradle", "build", "node_modules", ".idea", "__pycache__"}
DEFAULTS = {
    "field_size_in": 144.0,
    "robot_length_in": 18.0,
    "robot_width_in": 18.0,
    "clearance_in": 2.0,
    "speed_in_s": 30.0,
    "acceleration_in_s2": 40.0,
    "turn_rate_rad_s": math.pi,
    "start_delay_s": 0.0,
    "timing_uncertainty_s": 0.5,
    "sample_period_s": 0.1,
    "auto_duration_s": 30.0,
}


def validate_settings(settings: Mapping | None) -> dict:
    if settings is not None and not isinstance(settings, Mapping):
        raise ValueError("Settings must be an object.")
    result = dict(DEFAULTS)
    for key, value in (settings or {}).items():
        if key not in DEFAULTS:
            raise ValueError(f"Unknown setting: {key}")
        if isinstance(value, bool):
            raise ValueError(f"{key} must be a finite number.")
        try:
            number = float(value)
        except (TypeError, ValueError, OverflowError) as error:
            raise ValueError(f"{key} must be a finite number.") from error
        if not math.isfinite(number):
            raise ValueError(f"{key} must be a finite number.")
        if number < 0 or (key not in {"clearance_in", "start_delay_s", "timing_uncertainty_s"} and number == 0):
            raise ValueError(f"{key} is outside its permitted range.")
        result[key] = number
    if result["sample_period_s"] < 0.02 or result["auto_duration_s"] > 300:
        raise ValueError("Sampling must be at least 0.02 s and AUTO duration at most 300 s.")
    if any(result[key] > 10000 for key in result):
        raise ValueError("A model parameter is unreasonably large; check units.")
    if result["start_delay_s"] > result["auto_duration_s"]:
        raise ValueError("Start delay exceeds the autonomous duration.")
    return result


def select_sources(sources: Mapping) -> dict[str, str]:
    if not isinstance(sources, Mapping) or not sources:
        raise ValueError("Provide a .pp file, Java source, or a folder of Java files.")
    if len(sources) > MAX_FILES:
        raise ValueError(f"Import at most {MAX_FILES} source files at once.")
    cleaned: dict[str, str] = {}
    total = 0
    for name, content in sources.items():
        if not isinstance(name, str) or not isinstance(content, str):
            raise ValueError("Each source needs a text filename and text content.")
        normalized = name.replace("\\", "/")
        if not Path(normalized).is_absolute() and any(part in SKIP_PARTS for part in PurePosixPath(normalized).parts):
            continue
        if Path(normalized).suffix.lower() not in {".java", ".pp", ".json"}:
            continue
        total += len(content.encode("utf-8"))
        if total > MAX_BYTES:
            raise ValueError("Source import exceeds the 24 MB limit.")
        if normalized in cleaned:
            raise ValueError(f"Duplicate input path: {normalized}")
        cleaned[normalized] = content.lstrip("\ufeff")
    # Preserve explicitly supplied helper roots alongside TeamCode. Dependency source
    # outside that tree must not disappear merely because one TeamCode file exists.
    return cleaned


def read_sources(paths: list[Path]) -> dict[str, str]:
    sources: dict[str, str] = {}
    size = 0

    def add(name: str, raw: bytes) -> None:
        nonlocal size
        size += len(raw)
        if size > MAX_BYTES or len(sources) >= MAX_FILES:
            raise ValueError("Source import is too large; select the TeamCode source folder.")
        try:
            content = raw.decode("utf-8-sig")
        except UnicodeError as error:
            raise ValueError(f"Source is not UTF-8 text: {name}") from error
        previous = sources.get(name)
        if previous is not None and previous != content:
            raise ValueError(f"Two different files have the same input name: {name}")
        sources[name] = content

    for original in paths:
        path = original.expanduser().resolve()
        if path.is_dir():
            source_root = path / "TeamCode/src/main/java"
            if not source_root.is_dir():
                source_root = path
            for file in sorted(source_root.rglob("*.java")):
                relative = file.relative_to(source_root)
                if not any(part in SKIP_PARTS for part in relative.parts):
                    if file.stat().st_size > MAX_BYTES:
                        raise ValueError(f"Source file is too large: {file}")
                    add(str(file), file.read_bytes())
        elif path.is_file() and path.suffix.lower() == ".zip":
            # Read members in memory; never extract paths or execute project build scripts.
            with zipfile.ZipFile(path) as archive:
                for item in archive.infolist():
                    member = PurePosixPath(item.filename)
                    if member.suffix.lower() not in {".java", ".pp"} or any(p in SKIP_PARTS for p in member.parts):
                        continue
                    if item.file_size > MAX_BYTES or size + item.file_size > MAX_BYTES:
                        raise ValueError("ZIP contents exceed the source size limit.")
                    add(path.name + "/" + item.filename, archive.read(item))
        elif path.is_file():
            if path.stat().st_size > MAX_BYTES:
                raise ValueError(f"Source file is too large: {path}")
            add(str(path), path.read_bytes())
        else:
            raise ValueError(f"Input does not exist: {path}")
    return select_sources(sources)


def import_sources(sources: Mapping, kind: str = "auto", entry: str | None = None,
                   settings: Mapping | None = None, list_entries: bool = False) -> dict:
    sources = select_sources(sources)
    if not sources:
        raise ValueError("No .pp or Java source files were found.")
    model = validate_settings(settings)
    if kind == "auto":
        if len(sources) == 1 and next(iter(sources.values())).lstrip().startswith("{"):
            kind = "pp"
        elif all(name.lower().endswith(".java") for name in sources):
            kind = "java"
        else:
            raise ValueError("Choose Java or .pp format when importing mixed files.")
    if kind == "java":
        from .java_import import import_java
        from .java_sources import split_units, local_frog_helpers, restore_locations
        prepared, origins, findings = split_units(dict(sources))
        prepared, helper_findings = local_frog_helpers(prepared)
        imported = import_java(prepared, entry=entry, options={"list_entries": list_entries})
        restore_locations(imported, origins)
        imported["diagnostics"] = findings + helper_findings + imported.get("diagnostics", [])
        imported["resolved_files"] = list(prepared)
    elif kind == "pp":
        from .pp_import import import_pp
        if len(sources) != 1:
            raise ValueError("Import one .pp project at a time.")
        name, content = next(iter(sources.items()))
        try:
            data = json.loads(content, parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"Non-finite JSON number: {value}")))
        except (ValueError, TypeError) as error:
            raise ValueError(f"Invalid .pp JSON: {error}") from error
        if not isinstance(data, dict):
            raise ValueError("A .pp project must be a JSON object.")
        imported = import_pp(data, source_name=name)
    else:
        raise ValueError("Format must be auto, java or pp.")
    imported["input_files"] = list(sources)
    if list_entries or (kind == "java" and not imported.get("selected_entry")):
        status = "invalid" if any(d.get("severity") == "error" for d in imported.get("diagnostics", [])) else "selection_required"
        imported.update(schema_version="1.0", status=status, settings=model,
                        timeline=[], reservations=[], summary={})
        return imported
    from .motion import build_profile
    return build_profile(imported, model)


def dump_json(value: object) -> str:
    return json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + "\n"


class ImportServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, port: int):
        self.token = secrets.token_urlsafe(32)
        self.import_lock = threading.Lock()
        super().__init__(("127.0.0.1", port), ImportHandler)

    def ui_url(self) -> str:
        return f"http://127.0.0.1:{self.server_port}/?token={self.token}"


class ImportHandler(BaseHTTPRequestHandler):
    server: ImportServer

    def log_message(self, *_args) -> None:
        pass

    def reply(self, status: int, body: str, content_type: str = "application/json; charset=utf-8") -> None:
        raw = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'")
        self.end_headers()
        try:
            self.wfile.write(raw)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            logging.getLogger("partner_auto").info("Browser disconnected before the local response was delivered.")

    def valid_host(self) -> bool:
        return self.headers.get("Host") in {f"127.0.0.1:{self.server.server_port}", f"localhost:{self.server.server_port}"}

    def do_GET(self) -> None:
        route = urlsplit(self.path)
        if not self.valid_host():
            self.reply(403, dump_json({"error": "Local host required."}))
            return
        if route.path == "/api/health":
            origin = self.headers.get("Origin")
            if origin and origin not in {f"http://127.0.0.1:{self.server.server_port}", f"http://localhost:{self.server.server_port}"}:
                self.reply(403, dump_json({"error": "Local import session required."}))
                return
            self.reply(200, dump_json({"app": "partner-auto", "url": self.server.ui_url(), "pid": os.getpid()}))
            return
        if route.path != "/":
            self.reply(404, dump_json({"error": "Not found."}))
            return
        token = parse_qs(route.query).get("token", [""])[0]
        if not secrets.compare_digest(token, self.server.token):
            self.reply(403, dump_json({"error": "Open the complete local URL printed by the launcher."}))
            return
        template = (HERE / "ui.html").read_text(encoding="utf-8")
        self.reply(200, template.replace("__IMPORT_TOKEN__", self.server.token), "text/html; charset=utf-8")

    def do_POST(self) -> None:
        origin = self.headers.get("Origin")
        allowed_origins = {f"http://127.0.0.1:{self.server.server_port}", f"http://localhost:{self.server.server_port}"}
        if not self.valid_host() or (origin and origin not in allowed_origins) or not secrets.compare_digest(self.headers.get("X-Import-Token", ""), self.server.token):
            self.reply(403, dump_json({"error": "Local import session required."}))
            return
        route = urlsplit(self.path).path
        if route == "/api/shutdown":
            self.reply(200, dump_json({"stopped": True}))
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return
        if route != "/api/import":
            self.reply(404, dump_json({"error": "Not found."}))
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_BYTES + 1024 * 1024:
                raise ValueError("Import payload is empty or too large.")
            payload = json.loads(self.rfile.read(length))
            if not isinstance(payload, dict):
                raise ValueError("Import request must be an object.")
            if not self.server.import_lock.acquire(blocking=False):
                self.reply(409, dump_json({"error": "An import is already running."}))
                return
            try:
                result = import_sources(payload.get("sources", {}), payload.get("format", "auto"),
                                        payload.get("entry") or None, payload.get("settings"))
            finally:
                self.server.import_lock.release()
            self.reply(200, dump_json(result))
        except (ValueError, TypeError, KeyError, OSError, RuntimeError, zipfile.BadZipFile) as error:
            self.reply(400, dump_json({"error": str(error)}))
        except Exception as error:
            # Keep a malformed partner source from terminating the local interface.
            logging.getLogger("partner_auto").exception("Local source import failed")
            self.reply(500, dump_json({"error": f"Import failed: {type(error).__name__}: {error}"}))


def serve(port: int = 0, open_browser: bool = True, ready_file: Path | None = None) -> None:
    try:
        server = ImportServer(port or DEFAULT_UI_PORT)
    except OSError:
        if port:
            raise
        server = ImportServer(0)
    with server:
        address = server.ui_url()
        if ready_file:
            pending = ready_file.with_suffix(".pending")
            pending.write_text(dump_json({"url": address, "pid": os.getpid()}), encoding="utf-8")
            pending.replace(ready_file)
        print(f"Partner Auto Import\n{address}\nOffline source analysis. Ctrl+C stops the local application.", flush=True)
        if open_browser:
            webbrowser.open(address)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nImporter stopped.")


def running_ui_url(port: int) -> str | None:
    """Contact only loopback, directly; internet/proxy settings are not involved."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=1)
    try:
        connection.request("GET", "/api/health")
        response = connection.getresponse()
        result = json.loads(response.read(4096))
        if response.status == 200 and result.get("app") == "partner-auto":
            parsed = urlsplit(result.get("url", ""))
            if parsed.scheme == "http" and parsed.hostname == "127.0.0.1" and parsed.port == port:
                return result["url"]
    except (OSError, ValueError, http.client.HTTPException):
        pass
    finally:
        connection.close()
    return None


def launch_detached(port: int = 0, open_browser: bool = True) -> None:
    existing = running_ui_url(port or DEFAULT_UI_PORT)
    if existing:
        if open_browser:
            webbrowser.open(existing)
        print(f"Opened the existing local importer: {existing}")
        return
    folder = PROJECT_ROOT / "build/partner-auto"
    folder.mkdir(parents=True, exist_ok=True)
    identity = time.strftime("%Y%m%d-%H%M%S") + "-" + secrets.token_hex(4)
    ready = folder / f"launch-{identity}.json"
    log = folder / f"server-{identity}.log"
    command = [sys.executable, str(HERE.parent / "import_partner_auto.py"), "--ui", "--no-browser",
               "--port", str(port), "--ready-file", str(ready)]
    options = {"creationflags": subprocess.CREATE_NO_WINDOW | subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == "nt" else {"start_new_session": True}
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, **options)
    for _ in range(100):
        if ready.is_file():
            result = json.loads(ready.read_text(encoding="utf-8"))
            address = result["url"]
            if open_browser:
                webbrowser.open(address)
            ready.unlink(missing_ok=True)
            print(f"Partner Auto Import: {address}\nLocal Python stays running when this launcher closes.\nLog: {log}")
            return
        if process.poll() is not None:
            raise RuntimeError(f"Local importer could not start. See {log}")
        time.sleep(0.1)
    process.terminate()
    raise RuntimeError(f"Local importer did not start in time. See {log}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Import partner .pp/Java autos without executing robot code.")
    parser.add_argument("inputs", nargs="*", type=Path, help=".pp/.java files, source folder or ZIP")
    parser.add_argument("--format", choices=("auto", "pp", "java"), default="auto")
    parser.add_argument("--source-root", type=Path, action="append", default=[], help="Additional Java dependency source folder")
    parser.add_argument("--entry", help="Concrete autonomous class, preferably fully qualified")
    parser.add_argument("--list-entries", action="store_true")
    parser.add_argument("--stdin-format", choices=("pp", "java"), help="Read pasted source from standard input")
    parser.add_argument("--output", type=Path, help="Write the normalized local JSON profile")
    parser.add_argument("--settings", type=Path, help="JSON motion/body settings; estimates, not measured timing")
    parser.add_argument("--ui", action="store_true", help="Open the local paste/import interface")
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--no-browser", action="store_true")
    parser.add_argument("--detach", action="store_true", help="Keep the local UI running after its launcher closes")
    parser.add_argument("--ready-file", type=Path, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    try:
        if args.ui or (not args.inputs and not args.stdin_format and not args.source_root):
            if not 0 <= args.port <= 65535:
                raise ValueError("Port must be between 0 and 65535.")
            if args.detach:
                launch_detached(args.port, not args.no_browser)
            else:
                serve(args.port, not args.no_browser, args.ready_file)
            return 0
        sources = read_sources(args.inputs + args.source_root) if args.inputs or args.source_root else {}
        if args.stdin_format:
            content = sys.stdin.read(MAX_BYTES + 1)
            if len(content.encode("utf-8")) > MAX_BYTES:
                raise ValueError("Pasted source exceeds the size limit.")
            sources["PastedAuto." + ("java" if args.stdin_format == "java" else "pp")] = content
        settings = json.loads(args.settings.read_text(encoding="utf-8-sig")) if args.settings else None
        result = import_sources(sources, args.stdin_format or args.format, args.entry, settings, args.list_entries)
        output = dump_json(result)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(output, encoding="utf-8")
            print(f"{result.get('status')}: {args.output.resolve()}")
            for diagnostic in result.get("diagnostics", []):
                if diagnostic.get("severity") != "info":
                    print(f"{diagnostic.get('severity')}: {diagnostic.get('message')}", file=sys.stderr)
        else:
            print(output, end="")
        if args.list_entries and result.get("status") != "invalid":
            return 0
        return 0 if result.get("status") == "complete" else 1
    except (ValueError, OSError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"Import error: {error}", file=sys.stderr)
        return 2

