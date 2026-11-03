import math
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from partner_auto.java_import import import_java

REPO = Path(__file__).resolve().parents[3]


class JavaImportChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        folder = REPO / "TeamCode/src/main/java"
        cls.sources = {str(p.relative_to(folder)): p.read_text(encoding="utf-8-sig") for p in folder.rglob("*.java")}

    def test_every_export_resolves_inheritance_and_mirrors_once(self):
        counts = {"BothFlowersNoGarden": (5,3), "FarMidPark": (5,3), "FarMidGardenPark": (7,4),
                  "GardenFarMidPark": (7,4), "GardenFarPark": (5,3), "OpponentFarGardenPark": (7,4),
                  "OpponentFarMidPickupPark": (6,3), "OpponentFarMidGardenPickup": (7,4), "SoloWithSteal": (9,5)}
        listed = import_java(self.sources, options={"list_entries": True})
        self.assertEqual(len(listed["entries"]), 18)
        self.assertEqual(listed["actions"], [])
        for name, (moves, shots) in counts.items():
            with self.subTest(name=name):
                red = import_java(self.sources, name)
                blue = import_java(self.sources, "Blue"+name)
                self.assertFalse([x for x in red["diagnostics"] + blue["diagnostics"] if x["severity"] == "error"])
                self.assertEqual(sum(a["kind"] == "move" for a in red["actions"]), moves)
                self.assertEqual(sum(a["kind"] == "shoot" for a in red["actions"]), shots)
                self.assertEqual(len(red["actions"]), moves+shots)
                self.assertEqual([a["kind"] for a in red["actions"]], [a["kind"] for a in blue["actions"]])
                self.assertAlmostEqual(red["start_pose"]["x"] + blue["start_pose"]["x"], 144)
                self.assertAlmostEqual(red["start_pose"]["y"] + blue["start_pose"]["y"], 144)
                for a, b in zip(red["actions"],blue["actions"]):
                    if a["kind"] == "move":
                        self.assertEqual(len(a["path"]["points"]),len(b["path"]["points"]))
                        for p,q in zip(a["path"]["points"], b["path"]["points"]):
                            self.assertAlmostEqual(p[0]+q[0],144); self.assertAlmostEqual(p[1]+q[1],144)
                    else:
                        self.assertEqual(a["duration"]["min_s"],1.25)
                        self.assertEqual(a["duration"]["max_s"],4.25)
                        self.assertEqual(a["on_readiness_timeout"],"abort")

    def test_entry_selection_required(self):
        result = import_java(self.sources)
        self.assertIsNone(result["selected_entry"])
        self.assertFalse(result["actions"])
        self.assertIn("ENTRY_SELECTION_REQUIRED", [x["code"] for x in result["diagnostics"]])

    def test_pasted_filename_need_not_match_public_class(self):
        source = '''@Autonomous public class Plain extends LinearOpMode {
          public void runOpMode() { drive.setPoseEstimate(new Pose2d(2,3,0));
            waitForStart(); Actions.runBlocking(new SequentialAction(
              drive.actionBuilder(new Pose2d(2,3,0)).strafeTo(new Vector2d(12,13)).waitSeconds(2).build(),
              new SleepAction(0.5))); }
        }'''
        result = import_java({"clipboard.java": source})
        self.assertEqual(result["selected_entry"], "Plain")
        self.assertFalse([x for x in result["diagnostics"] if x["severity"] == "error"])
        action = result["actions"][0]
        self.assertEqual(action["kind"],"sequence")
        self.assertEqual(action["children"][0]["children"][0]["path"]["points"], [[2,3],[12,13]])
        self.assertEqual(action["children"][1]["duration"]["min_s"],0.5)

    def test_legacy_pedro_builder_preserves_segments(self):
        source = '''@Autonomous class Legacy extends LinearOpMode {
          void runOpMode() { PathChain p = follower.pathBuilder()
             .addPath(new BezierLine(new Point(1,2),new Point(3,4)))
             .setConstantHeadingInterpolation(Math.toRadians(90))
             .addPath(new BezierCurve(new Point(3,4),new Point(9,10),new Point(12,14)))
             .setLinearHeadingInterpolation(0,Math.PI).build(); follower.followPath(p);
             while (follower.isBusy()) { follower.update(); } }
        }'''
        result = import_java({"Legacy.java":source})
        children = result["actions"][0]["children"]
        self.assertEqual(len(children),2)
        self.assertEqual(children[0]["path"]["kind"],"line")
        self.assertAlmostEqual(children[0]["path"]["heading"]["start_rad"], math.pi/2)
        self.assertEqual(children[1]["path"]["points"], [[3,4],[9,10],[12,14]])

    def test_legacy_cartesian_point_path_and_java_numeric_semantics(self):
        source = '''@Autonomous class Coordinates extends LinearOpMode {
          void runOpMode() { double x = 5; double half = x / 2;
            Path p = new Path(new BezierLine(new Point(5/2, (int)3.9, Point.CARTESIAN),
              new Point(half, 4, Point.CARTESIAN))); p.setConstantHeadingInterpolation(0);
            follower.followPath(p); while (follower.isBusy()) { follower.update(); } }
        }'''
        result = import_java({"paste.java":source})
        self.assertFalse([x for x in result["diagnostics"] if x["severity"] == "error"])
        self.assertEqual(result["actions"][0]["path"]["points"],[[2,3],[2.5,4]])

    def test_abstract_base_helpers_and_nonzero_hotfix(self):
        source = '''@Autonomous class Blue extends Base { boolean blue() { return true; } }
          abstract class Base extends LinearOpMode {
            static final Pose A = new Pose(10,20,0).plus(new Pose(2,3,0));
            boolean blue() { return false; }
            Pose mirror(Pose p) { return blue() ? new Pose(144-p.x(),144-p.y(),p.heading()+Math.PI) : p; }
            void runOpMode() { follower.follow(Paths.line(mirror(A), mirror(new Pose(30,40,0))).constant(0));
                while (follower.isBusy()) { follower.update(); } }
          }'''
        result = import_java({"paste.java":source})
        self.assertFalse([x for x in result["diagnostics"] if x["severity"] == "error"])
        self.assertEqual(result["actions"][0]["path"]["points"],[[132,121],[114,104]])

    def test_runtime_branch_never_silently_selects_true(self):
        source = '''@Autonomous class Choice extends LinearOpMode { void runOpMode() {
          if (sensor.loaded()) follower.follow(Paths.line(new Pose(0,0),new Pose(1,0)));
          else follower.follow(Paths.line(new Pose(0,0),new Pose(0,1))); sleep(100);
        }}'''
        result = import_java({"paste.java":source})
        branch = result["actions"][0]
        self.assertEqual(branch["kind"], "branch")
        self.assertEqual(len(branch["branches"]),2)
        self.assertEqual(branch["branches"][0]["actions"][0]["path"]["points"][-1],[1,0])
        self.assertEqual(branch["branches"][1]["actions"][0]["path"]["points"][-1],[0,1])
        self.assertTrue(any(a["kind"]=="unknown" and a["duration"]["max_s"] is None for a in result["actions"]))

    def test_constructor_and_static_initializer_resolve_geometry(self):
        source = '''class Points { static Pose START; static { START = new Pose(4,5,0); } }
          abstract class Base extends LinearOpMode {
            Pose end; Base() { this.end = new Pose(8,9,0); }
            void go() { follower.follow(Paths.line(Points.START, end));
                while (follower.isBusy()) { follower.update(); } }
          }
          @Autonomous class Constructed extends Base {
            Constructed() { super(); }
            void runOpMode() { go(); }
          }'''
        result = import_java({"pasted.java":source})
        self.assertFalse([x for x in result["diagnostics"] if x["severity"] == "error"])
        self.assertEqual(result["actions"][0]["path"]["points"],[[4,5],[8,9]])

    def test_interface_default_helper_and_super_method(self):
        source = '''interface Route { default Pose end() { return new Pose(7,8); } }
          abstract class Base extends LinearOpMode {
            void driveIt() { follower.follow(Paths.line(new Pose(1,2),end()));
                while (follower.isBusy()) { follower.update(); } }
          }
          @Autonomous class Leaf extends Base implements Route {
            void runOpMode() { super.driveIt(); }
          }'''
        result = import_java({"paste.java":source})
        self.assertFalse([x for x in result["diagnostics"] if x["severity"] == "error"])
        self.assertEqual(result["actions"][0]["path"]["points"],[[1,2],[7,8]])

    def test_raw_dispatch_does_not_pretend_to_wait_before_later_actions(self):
        source = '''@Autonomous class Async extends LinearOpMode { void runOpMode() {
            follower.followPath(new BezierLine(new Point(0,0),new Point(20,0)));
            sleep(1000); Actions.runBlocking(new SleepAction(2)); follower.waitForIdle();
        }}'''
        result = import_java({"paste.java":source})
        self.assertEqual(result["actions"][0]["kind"],"move")
        self.assertTrue(any(a["kind"]=="unknown" for a in result["actions"]))
        self.assertIn("ASYNC_COMPLETION",[x["code"] for x in result["diagnostics"]])
        self.assertIn("UNRESOLVED_COMPLETION_WAIT",[x["code"] for x in result["diagnostics"]])

    def test_immediate_completion_wait_resolves_only_its_dispatch(self):
        source = '''@Autonomous class Waiting extends LinearOpMode { void runOpMode() {
            follower.followPath(new BezierLine(new Point(0,0),new Point(20,0)));
            follower.waitForIdle(); sleep(1000);
        }}'''
        result = import_java({"paste.java":source})
        self.assertFalse([x for x in result["diagnostics"] if x["severity"]=="error"])
        self.assertEqual([x["kind"] for x in result["actions"]],["move","wait"])

    def test_generic_schedule_is_async_and_pose_reset_requires_review(self):
        source = '''@Autonomous class Scheduled extends LinearOpMode { void runOpMode() {
            scheduler.schedule(new SequentialAction(new SleepAction(2)));
            drive.setPoseEstimate(new Pose2d(50,50,0));
        }}'''
        result = import_java({"paste.java":source})
        codes = [x["code"] for x in result["diagnostics"]]
        self.assertIn("ASYNC_COMPLETION",codes)
        self.assertIn("POSE_RESET_DURING_AUTO",codes)

    def test_missing_superclass_is_named(self):
        result = import_java({"paste.java":"@Autonomous class Leaf extends PartnerBase {}"})
        errors = [x for x in result["diagnostics"] if x["code"]=="MISSING_SUPERCLASS"]
        self.assertEqual(len(errors),1)
        self.assertIn("PartnerBase",errors[0]["message"])

    def test_unrecognized_calls_and_sensor_loops_block_complete_claim(self):
        source = '''@Autonomous class Unsafe extends LinearOpMode { void runOpMode() {
            while (sensor.busy()) { drive.customMotion(); } System.exit(0);
        }}'''
        result = import_java({"paste.java":source})
        self.assertTrue(any(x["kind"]=="unknown" for x in result["actions"]))
        self.assertIn("RUNTIME_LOOP",[x["code"] for x in result["diagnostics"]])
        self.assertTrue(any("System.exit" in x["message"] for x in result["diagnostics"]))

    def test_invalid_java_produces_syntax_diagnostic(self):
        result = import_java({"paste.java":"@Autonomous class Broken { void runOpMode( {"})
        self.assertIn("JAVA_SYNTAX", [x["code"] for x in result["diagnostics"]])
        self.assertEqual(result["actions"],[])

    def test_rr_spline_is_not_replaced_with_an_unsafe_straight_line(self):
        result = import_java({"paste.java":'''@Autonomous class Spline extends LinearOpMode {
          void runOpMode() { Actions.runBlocking(drive.actionBuilder(new Pose2d(0,0,0))
            .splineTo(new Vector2d(20,20),0).build()); }
        }'''})
        self.assertIn("RR_SPLINE_GEOMETRY",[x["code"] for x in result["diagnostics"]])
        self.assertEqual(result["actions"][0]["children"][0]["kind"], "unknown")


if __name__ == "__main__": unittest.main()
