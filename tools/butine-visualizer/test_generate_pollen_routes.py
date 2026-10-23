"""Integration checks using the real Java planner and the generated Pedro project."""

import contextlib
import importlib.util
import io
import json
import math
from pathlib import Path
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location("pollen_exporter", Path(__file__).with_name("generate_pollen_routes.py"))
exporter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(exporter)


class ExportChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.java = exporter.find_java_tool("java", None)
        cls.jar = exporter.find_pedro_jar(None)
        cls.classes = exporter.compile_bridge(exporter.find_java_tool("javac", None), cls.jar)
        cls.geometry = exporter.invoke_bridge(cls.java, cls.classes, cls.jar, "--describe-field")

    def export(self, folder, name="red sample.pp", *extra):
        output = Path(folder) / name
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = exporter.main(["--seed", "20261019", "--output", str(output), *extra])
        self.assertEqual(code, 0)
        return (json.loads(output.read_text(encoding="utf-8")),
                json.loads(output.with_suffix(".map.json").read_text(encoding="utf-8")),
                json.loads(output.with_suffix(".plan.json").read_text(encoding="utf-8")))

    def plan_pollen(self, points, width=18, offset=9, overshoot=None):
        """Small controlled maps make intake contacts observable without random-map assumptions."""
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "controlled.tsv"
            config = (30, 18, math.pi / 2, 30, 18, math.pi / 2, 150, 2, 1, width, offset)
            if overshoot is not None:
                config += (overshoot,)
            rows = ["CONFIG\t" + "\t".join(map(str, config))]
            rows.extend(f"POLLEN\t{index + 1}\t{x}\t{y}" for index, (x, y) in enumerate(points))
            source.write_text("\n".join(rows) + "\n", encoding="utf-8")
            return exporter.invoke_bridge(self.java, self.classes, self.jar, str(source))

    def test_map_count_determinism_spacing_and_geometry(self):
        for seed in (0, 1, 20261019, 2147483647):
            points = exporter.generate_map(seed, self.geometry)
            self.assertEqual(points, exporter.generate_map(seed, self.geometry))
            self.assertEqual(len(points), 40)
            self.assertEqual(len({point["id"] for point in points}), 40)
            self.assertEqual(sum(point["x"] < 72 for point in points), 20)
            for index, point in enumerate(points):
                self.assertTrue(exporter.clear_point(point["x"], point["y"], self.geometry))
                for other in points[:index]:
                    self.assertGreaterEqual(math.hypot(point["x"] - other["x"], point["y"] - other["y"]), 3)

    def test_requested_gradient(self):
        self.assertEqual([exporter.route_color(i, 5) for i in range(5)],
                         ["#ffffff", "#80ff80", "#00ff00", "#008000", "#000000"])

    def test_real_java_routes_and_display_only_markers(self):
        with tempfile.TemporaryDirectory(prefix="Pedro export with spaces ") as temporary:
            project, world, plan = self.export(temporary)
            self.assertEqual(len(world["fullPollen"]), 40)
            self.assertEqual(len(world["eligiblePollenIds"]), 20)
            self.assertGreater(plan["diagnostics"]["totalCollected"], 0)
            self.assertTrue(all(1 <= run["pickupCount"] <= 4 for run in plan["runs"]))
            self.assertEqual(plan["diagnostics"]["totalCollected"] + len(plan["uncollectedIds"]), 20)
            self.assertEqual(plan["config"]["intakeWidthIn"], 18)
            self.assertEqual(plan["config"]["intakeForwardOffsetIn"], 9)
            self.assertEqual(plan["config"]["intakeOvershootIn"], self.geometry["config"]["intakeOvershootIn"])
            self.assertEqual(len(plan["obstacles"]), 6, "Pollen markers must never enter the Java collision map")
            markers = [shape for shape in project["shapes"] if shape["id"].startswith("pollen-")]
            self.assertEqual(len(markers), 20)
            self.assertEqual(len(project["shapes"]), 26)
            self.assertTrue(all(sum(v["x"] for v in shape["vertices"]) / 16 < 72 for shape in markers))
            self.assertTrue(project["settings"]["experimentalFeatures"]["obstacles"])
            legs = [leg for run in plan["runs"] for leg in run["legs"]]
            self.assertEqual(len(project["lines"]), len(legs))
            self.assertEqual(len(project["sequence"]), len(legs))
            self.assertEqual([item["lineId"] for item in project["sequence"]], [line["id"] for line in project["lines"]])
            for line, leg in zip(project["lines"], legs):
                self.assertAlmostEqual(line["endPoint"]["x"], leg["target"]["x"])
                self.assertAlmostEqual(line["endPoint"]["y"], leg["target"]["y"])
                self.assertEqual(line["controlPoints"], [], "Safe straight segments must not become smoothing curves")
                if not leg["intakeOn"]:
                    self.assertEqual(line["heading"]["type"], "linear")
                    self.assertAlmostEqual(line["heading"]["endDeg"], math.degrees(leg["target"]["heading"]))
                else:
                    self.assertEqual(line["heading"]["type"], "constant")
                if leg["kind"] == "DEPOT":
                    self.assertEqual(line["waitAfterMs"], 2000)
                else:
                    self.assertEqual(line["waitAfterMs"], 0, "Collection must not pause at each pollen")
                self.assertEqual(line["waitBeforeMs"], 0)
                self.assertEqual(line["butine"]["intakeOn"], leg["intakeOn"])
                self.assertEqual(line["butine"]["collectedPollenIds"], leg["pollenIds"])
                if not leg["intakeOn"]:
                    self.assertEqual(leg["pollenIds"], [])
            self.assertTrue(plan["diagnostics"]["allSegmentsClear"])
            self.assertTrue(plan["diagnostics"]["legTimingMatches"])

    def test_front_intake_collects_pair_in_one_pass_and_width_changes_contacts(self):
        points = [(26, 36), (34, 36)]
        wide = self.plan_pollen(points, width=18)
        narrow = self.plan_pollen(points, width=2)
        self.assertEqual(wide["diagnostics"]["totalCollected"], 2)
        self.assertEqual(narrow["diagnostics"]["totalCollected"], 2)
        wide_groups = [leg for run in wide["runs"] for leg in run["legs"] if leg["pollenIds"]]
        narrow_groups = [leg for run in narrow["runs"] for leg in run["legs"] if leg["pollenIds"]]
        self.assertTrue(any(len(leg["pollenIds"]) == 2 for leg in wide_groups))
        self.assertTrue(all(len(leg["pollenIds"]) == 1 for leg in narrow_groups))
        for leg in wide_groups:
            length = math.hypot(leg["target"]["x"] - leg["from"]["x"], leg["target"]["y"] - leg["from"]["y"])
            self.assertAlmostEqual((1 - leg["contactFractions"][-1]) * length, 1,
                                   msg="Collection continues one inch beyond the final pollen surface contact")
        self.assertTrue(all(abs(leg["target"]["heading"] - math.pi / 2) < 1e-7 for leg in wide_groups))

    def test_collection_overshoot_keeps_the_incoming_direction(self):
        baseline = self.plan_pollen([(30, 36)], overshoot=0)
        base_leg = [leg for leg in baseline["runs"][0]["legs"] if leg["intakeOn"]][-1]
        dx = base_leg["target"]["x"] - base_leg["from"]["x"]
        dy = base_leg["target"]["y"] - base_leg["from"]["y"]
        length = math.hypot(dx, dy)
        for overshoot in (1, 2.5):
            plan = self.plan_pollen([(30, 36)], overshoot=overshoot)
            self.assertEqual(plan["config"]["intakeOvershootIn"], overshoot)
            leg = [leg for leg in plan["runs"][0]["legs"] if leg["intakeOn"]][-1]
            self.assertAlmostEqual(leg["target"]["x"], base_leg["target"]["x"] + overshoot * dx / length)
            self.assertAlmostEqual(leg["target"]["y"], base_leg["target"]["y"] + overshoot * dy / length)
            self.assertLess(leg["contactFractions"][-1], 1)
            self.assertAlmostEqual((1 - leg["contactFractions"][-1]) * math.hypot(
                leg["target"]["x"] - leg["from"]["x"], leg["target"]["y"] - leg["from"]["y"]), overshoot)
            self.assertEqual(leg["target"]["heading"], base_leg["target"]["heading"])

    def test_legacy_tsv_uses_production_overshoot_default(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "legacy.tsv"
            config = (30, 18, math.pi / 2, 30, 18, math.pi / 2, 150, 2, 1)
            source.write_text("CONFIG\t" + "\t".join(map(str, config)) + "\nPOLLEN\t1\t30\t36\n",
                              encoding="utf-8")
            plan = exporter.invoke_bridge(self.java, self.classes, self.jar, str(source))
            self.assertEqual(plan["config"]["intakeOvershootIn"], self.geometry["config"]["intakeOvershootIn"])
            self.assertEqual(plan["diagnostics"]["totalCollected"], 1)

    def test_incidental_contacts_obey_capacity_and_return_intake_is_off(self):
        plan = self.plan_pollen([(28, 36), (33, 36), (38, 36), (43, 36), (48, 36)])
        self.assertEqual(plan["diagnostics"]["totalCollected"], 5)
        self.assertGreaterEqual(len(plan["runs"]), 2, "Five swept pollen require more than one depot trip")
        seen = set()
        for run in plan["runs"]:
            self.assertLessEqual(run["pickupCount"], 4)
            grouped = [pollen_id for leg in run["legs"] for pollen_id in leg["pollenIds"]]
            self.assertEqual(grouped, run["pickupIds"])
            self.assertTrue(seen.isdisjoint(grouped))
            seen.update(grouped)
            self.assertTrue(any(not leg["intakeOn"] for leg in run["legs"]))
            self.assertTrue(all(not leg["pollenIds"] for leg in run["legs"] if not leg["intakeOn"]))
            self.assertAlmostEqual(sum(leg["predictedS"] for leg in run["legs"]), run["travelTimeS"])

    def test_collinear_pickups_keep_moving_between_contacts(self):
        plan = self.plan_pollen([(30, 40), (30, 50), (30, 60)])
        self.assertEqual(plan["diagnostics"]["totalCollected"], 3)
        contacts = [leg for run in plan["runs"] for leg in run["legs"] if leg["pollenIds"]]
        self.assertEqual(len(contacts), 1, "Collinear pollen should share a continuous collection segment")
        self.assertEqual(len(contacts[0]["pollenIds"]), 3)
        self.assertTrue(all(0 < fraction < 1 for fraction in contacts[0]["contactFractions"][:-1]),
                        "Intermediate pollen must be collected while passing through the segment")
        for run in plan["runs"]:
            for before, after in zip(run["legs"], run["legs"][1:]):
                self.assertAlmostEqual(before["endSpeedInS"], after["startSpeedInS"])

    def test_intake_cli_overrides_and_invalid_dimensions(self):
        with tempfile.TemporaryDirectory() as temporary:
            project, _, plan = self.export(temporary, "narrow.pp", "--intake-width", "12",
                                           "--intake-forward-offset", "8", "--intake-overshoot", "2.5")
            self.assertEqual(plan["config"]["intakeWidthIn"], 12)
            self.assertEqual(plan["config"]["intakeForwardOffsetIn"], 8)
            self.assertEqual(plan["config"]["intakeOvershootIn"], 2.5)
            self.assertEqual(project["butineVisualization"]["intakeOvershootIn"], 2.5)
            for option, value in (("--intake-width", "0"), ("--intake-width", "nan"),
                                  ("--intake-forward-offset", "-1"), ("--intake-overshoot", "-1"),
                                  ("--intake-overshoot", "nan")):
                output = Path(temporary) / "invalid.pp"
                with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(exporter.main(["--output", str(output), option, value]), 1)
                self.assertFalse(output.exists())

    def test_repeatable_pp_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            self.export(temporary, "first.pp")
            self.export(temporary, "second.pp")
            self.assertEqual((Path(temporary) / "first.pp").read_bytes(), (Path(temporary) / "second.pp").read_bytes())

    def test_deadlines_preserve_uncollected_markers(self):
        with tempfile.TemporaryDirectory() as temporary:
            for budget in (0, 10, 30):
                project, _, plan = self.export(temporary, f"budget-{budget}.pp", "--seconds", str(budget))
                self.assertLessEqual(plan["diagnostics"]["totalCommittedTimeS"], budget + 1e-7)
                self.assertEqual(len([s for s in project["shapes"] if s["id"].startswith("pollen-")]), 20)
                if budget == 0:
                    self.assertEqual(project["lines"], [])
                    self.assertEqual(len(plan["uncollectedIds"]), 20)

    def test_blocked_depot_and_blue_input_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "invalid.pp"
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                code = exporter.main(["--seed", "1", "--output", str(output), "--depot", "50", "72", "90"])
            self.assertEqual(code, 1)
            self.assertFalse(output.exists())
            source = Path(temporary) / "blue.tsv"
            source.write_text("CONFIG\t59.25\t18\t0\t59.25\t18\t0\t150\t2\t1\nPOLLEN\t1\t73\t30\n", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "RED"):
                exporter.invoke_bridge(self.java, self.classes, self.jar, str(source))


if __name__ == "__main__":
    unittest.main()
