"""PP import checks cover declared geometry, playback order and explicit uncertainty."""
from __future__ import annotations

import copy
import json
import math
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from partner_auto.pp_import import import_pp
from partner_auto.motion import build_profile


def line(path_id="a", x=20, y=10, **extra):
    return {"id": path_id, "kind": "atomic", "endPoint": {"x": x, "y": y},
            "controlPoints": [], "heading": {"type": "constant", "degrees": 30}, **extra}


def project(*paths, **extra):
    return {"version": "1.5.0", "startPoint": {"x": 10, "y": 10, "headingDeg": 30},
            "lines": list(paths), **extra}


def errors(result):
    return [d for d in result["diagnostics"] if d["severity"] == "error"]


class PPImportTests(unittest.TestCase):
    def test_repository_pp_keeps_every_path_and_scoring_wait(self):
        root = Path(__file__).resolve().parents[3]
        sample = root / "tools/butine-visualizer/examples/red-pollen-seed-20261019.pp"
        raw = json.loads(sample.read_text(encoding="utf-8"))
        result = import_pp(raw, "seed.pp")
        self.assertEqual([], errors(result))
        moves = [a for a in result["actions"] if a["kind"] == "move"]
        waits = [a for a in result["actions"] if a["kind"] == "wait"]
        self.assertEqual(len(raw["lines"]), len(moves))
        self.assertEqual(5, len(waits))
        self.assertTrue(all(w["duration"]["min_s"] == w["duration"]["max_s"] for w in waits))
        self.assertEqual(raw["startPoint"]["x"], result["start_pose"]["x"])
        for source, move in zip(raw["lines"], moves):
            self.assertEqual([source["endPoint"]["x"], source["endPoint"]["y"]], move["path"]["points"][-1])
            self.assertEqual("seed.pp", move["source"]["file"])
        self.assertEqual(1, sum(d["code"] == "PP_PATH_WAIT" for d in result["diagnostics"]))

    def test_legacy_endpoint_and_path_headings(self):
        first = {"endPoint": {"x": 20, "y": 10, "heading": "linear", "startDeg": 30, "endDeg": 90},
                 "controlPoints": [{"x": 10, "y": 20}]}
        second = {"heading": "constant", "endPoint": {"x": 20, "y": 20, "degrees": 120}}
        result = import_pp(project(first, second))
        self.assertEqual([], errors(result))
        a, b = result["actions"]
        self.assertEqual([[10, 10], [10, 20], [20, 10]], a["path"]["points"])
        self.assertAlmostEqual(math.pi / 2, a["path"]["heading"]["end_rad"])
        self.assertAlmostEqual(math.radians(120), b["path"]["heading"]["start_rad"])
        self.assertEqual([20, 10], b["path"]["points"][0])

    def test_sequence_order_waits_repeats_and_shoot_extension(self):
        data = project(line("a"), line("b", 10, 10), sequence=[
            {"kind": "path", "lineId": "a"}, {"kind": "wait", "durationMs": 700},
            {"kind": "path", "lineId": "b"}, {"kind": "path", "lineId": "a"},
            {"kind": "shoot", "durationS": 1.25}])
        result = import_pp(data)
        self.assertEqual([], errors(result))
        self.assertEqual(["move", "wait", "move", "move", "shoot"], [a["kind"] for a in result["actions"]])
        self.assertEqual(["a", "b", "a"], [a["path_id"] for a in result["actions"] if a["kind"] == "move"])
        self.assertEqual(.7, result["actions"][1]["duration"]["max_s"])
        self.assertEqual(1.25, result["actions"][-1]["duration"]["max_s"])

    def test_discontinuous_reordered_path_does_not_invent_a_connector(self):
        result = import_pp(project(line("a"), line("b", 30, 10), sequence=[{"kind": "path", "lineId": "b"}]))
        self.assertIn("DISCONTINUOUS_SEQUENCE", [d["code"] for d in errors(result)])
        self.assertEqual([[20, 10], [30, 10]], result["actions"][0]["path"]["points"])

    def test_unknown_reference_and_unknown_action_are_not_dropped(self):
        data = project(line(), sequence=[{"kind": "path", "lineId": "absent"},
                                        {"kind": "sensorWait", "until": "loaded"}])
        result = import_pp(data)
        self.assertEqual(["unknown", "unknown"], [a["kind"] for a in result["actions"]])
        self.assertTrue(all(a["duration"]["max_s"] is None for a in result["actions"]))
        self.assertEqual(2, len(errors(result)))

    def test_path_waits_and_nested_group_waits_are_preserved(self):
        data = project({"id": "group", "kind": "compound", "waitBeforeMs": 100, "waitAfterMs": 400,
                        "segments": [line("a", waitBefore={"durationMs": 200, "name": "start"}, waitAfterMs=300),
                                     line("b", 30, 10)]})
        result = import_pp(data)
        self.assertEqual([], errors(result))
        self.assertEqual(["wait", "wait", "move", "wait", "move", "wait"],
                         [a["kind"] for a in result["actions"]])
        self.assertEqual([.1, .2, .3, .4], [a["duration"]["min_s"] for a in result["actions"] if a["kind"] == "wait"])

    def test_explicit_group_reference_expands_group_once(self):
        data = project({"id": "group", "kind": "compound", "segments": [line("a"), line("b", 30, 10)]},
                       sequence=[{"kind": "path", "lineId": "group"}])
        result = import_pp(data)
        self.assertEqual([], errors(result))
        self.assertEqual(["a", "b"], [a["path_id"] for a in result["actions"]])

    def test_group_linear_heading_uses_whole_group_distance(self):
        data = project({"id": "group", "kind": "compound",
                        "heading": {"type": "linear", "startDeg": 0, "endDeg": 90},
                        "segments": [line("a"), line("b", 50, 10)]})
        result = import_pp(data)
        self.assertEqual([], errors(result))
        first, second = [a["path"]["heading"] for a in result["actions"]]
        self.assertAlmostEqual(math.radians(22.5), first["end_rad"])
        self.assertAlmostEqual(first["end_rad"], second["start_rad"])
        self.assertAlmostEqual(math.pi / 2, second["end_rad"])

    def test_outer_group_heading_takes_precedence(self):
        inner = {"kind": "compound", "id": "inner", "heading": {"type": "constant", "degrees": 80},
                 "segments": [line()]}
        result = import_pp(project({"kind": "compound", "id": "outer",
                                    "heading": {"type": "constant", "degrees": 45}, "segments": [inner]}))
        self.assertEqual([], errors(result))
        self.assertAlmostEqual(math.pi / 4, result["actions"][0]["path"]["heading"]["start_rad"])

    def test_through_points_are_interpolated_not_control_points(self):
        result = import_pp(project(line("a", 30, 10, throughPoints=[{"x": 20, "y": 20}])))
        self.assertEqual([], errors(result))
        points = result["actions"][0]["path"]["points"]
        self.assertEqual([[10, 10], [20, 30], [30, 10]], points)
        # At t=.5 the fitted curve passes through (20,20), unlike a Bezier control at (20,20).
        self.assertEqual([20, 20], [(points[0][axis] + 2 * points[1][axis] + points[2][axis]) / 4 for axis in (0, 1)])

    def test_piecewise_heading_and_facing_point_are_retained(self):
        heading = {"type": "piecewise", "piecewiseHeading": {"segments": [
            {"startProgress": 0, "endProgress": .5, "interpolationType": "linear",
             "parameters": {"startDeg": 30, "endDeg": 60}},
            {"startProgress": .5, "endProgress": 1, "interpolationType": "facing-point", "reversed": True,
             "parameters": {"point": {"x": 40, "y": 40}}}]}}
        result = import_pp(project(line(heading=heading)))
        self.assertEqual([], errors(result))
        converted = result["actions"][0]["path"]["heading"]["segments"]
        self.assertAlmostEqual(math.pi / 3, converted[0]["end_rad"])
        self.assertEqual([40, 40], converted[1]["point"])
        self.assertTrue(converted[1]["reverse"])
        heading["piecewiseHeading"]["segments"][1]["startProgress"] = .6
        self.assertTrue(errors(import_pp(project(line(heading=heading)))))

    def test_piecewise_group_heading_keeps_arc_progress_slice(self):
        heading = {"type": "piecewise", "piecewiseHeading": {"segments": [
            {"startProgress": 0, "endProgress": 1, "interpolationType": "linear",
             "parameters": {"startDeg": 0, "endDeg": 90}}]}}
        result = import_pp(project({"id": "g", "segments": [line("a"), line("b", 30, 10)], "heading": heading}))
        self.assertEqual([], errors(result))
        headings = [a["path"]["heading"] for a in result["actions"]]
        self.assertEqual((0, .5), (headings[0]["progress_start"], headings[0]["progress_end"]))
        self.assertEqual((.5, 1), (headings[1]["progress_start"], headings[1]["progress_end"]))

    def test_tangent_reverse_and_units_are_not_conflated(self):
        data = project(line(heading={"type": "tangential", "reverse": True}), units="cm")
        result = import_pp(data)
        self.assertEqual([], errors(result))
        self.assertTrue(result["actions"][0]["path"]["heading"]["reverse"])
        self.assertAlmostEqual(10 / 2.54, result["start_pose"]["x"])
        self.assertAlmostEqual(20 / 2.54, result["actions"][0]["path"]["points"][-1][0])
        self.assertEqual("inches", result["metadata"]["units"])

    def test_duplicate_path_id_is_ambiguous_not_silently_selected(self):
        result = import_pp(project(line("a"), line("a", 30, 10)))
        self.assertIn("DUPLICATE_PATH_ID", [d["code"] for d in errors(result)])
        self.assertTrue(all(a["kind"] == "unknown" for a in result["actions"]))

    def test_invalid_first_path_reports_cause_once_and_keeps_unknown_work(self):
        paths = [line(f"path-{i}", x=20 + i) for i in range(26)]
        paths[0]["endPoint"] = [{"x": 20, "y": 10}]
        result = import_pp(project(*paths))
        self.assertEqual(["INVALID_PATH"], [d["code"] for d in errors(result)])
        self.assertEqual("lines[0]", errors(result)[0]["source"]["location"])
        summaries = [d for d in result["diagnostics"] if d["code"] == "DEPENDENT_PATH_ORIGINS"]
        self.assertEqual(1, len(summaries))
        self.assertIn("25 subsequent paths", summaries[0]["message"])
        self.assertEqual(26, len(result["actions"]))
        self.assertTrue(all(a["kind"] == "unknown" and a["diagnostic_reported"]
                            and a["duration"]["max_s"] is None for a in result["actions"]))
        profile = build_profile(result)
        self.assertEqual("invalid", profile["status"])
        self.assertIsNone(profile["summary"]["duration_max_s"])

    def test_lost_origins_do_not_hide_independent_path_errors(self):
        paths = [line(f"path-{i}", x=20 + i) for i in range(4)]
        paths[0]["endPoint"] = []
        paths[2]["heading"] = {"type": "constant", "degrees": "bad"}
        result = import_pp(project(*paths))
        self.assertEqual(["lines[0]", "lines[2]"],
                         [d["source"]["location"] for d in errors(result)])
        self.assertIn("degrees must be a finite number", errors(result)[1]["message"])
        self.assertEqual(4, len(result["actions"]))
        self.assertTrue(all(a["kind"] == "unknown" for a in result["actions"]))

    def test_no_source_object_mutation_and_deterministic_import(self):
        original = project(line(), sequence=[{"kind": "path", "lineId": "a"}])
        before = copy.deepcopy(original)
        first = import_pp(original)
        self.assertEqual(before, original)
        self.assertEqual(first, import_pp(original))
        json.dumps(first, allow_nan=False)

    def test_invalid_inputs_always_report_unresolved_work(self):
        bad = [None, [], {}, project(), project(line(), units="furlongs"),
               project(line(), coordinateSystem="ftc"), project(line(x=float("nan"))),
               project(line(), settings={"rLength": float("inf")}),
               project(line(x=1.79e308), units="m"),
               project(line(x=True)), project(line(waitBeforeMs=-1)),
               project(line(), sequence=[{"kind": "wait"}]),
               project(line(), sequence=[{"kind": "wait", "durationMs": "5"}]),
               project(line(heading={"type": "constant"})), project(line(), sequence="a")]
        for value in bad:
            with self.subTest(value=value):
                result = import_pp(value)
                self.assertTrue(errors(result))
                self.assertTrue(any(a["kind"] == "unknown" for a in result["actions"]))


if __name__ == "__main__":
    unittest.main()
