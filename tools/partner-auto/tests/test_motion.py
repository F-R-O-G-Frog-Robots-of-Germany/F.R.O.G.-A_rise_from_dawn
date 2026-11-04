"""Behavior checks for source timing, alternative outcomes and swept occupancy."""
from __future__ import annotations

import json
import math
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from partner_auto.motion import build_profile


def imported(actions, start=(0, 0, 0)):
    return {"format": "test", "selected_entry": "TestAuto", "diagnostics": [],
            "actions": actions, "start_pose": None if start is None else
            {"x": start[0], "y": start[1], "heading_rad": start[2]}}


def move(points, heading=None, **extra):
    return {"kind": "move", "path": {"points": points,
            "heading": heading or {"type": "constant", "start_rad": 0}}, **extra}


def wait(lo, hi="same", kind="wait", **extra):
    return {"kind": kind, "duration": {"min_s": lo, "max_s": lo if hi == "same" else hi,
                                      "basis": "source"}, **extra}


def contains(box, x, y):
    return box["min_x"] - 1e-8 <= x <= box["max_x"] + 1e-8 \
        and box["min_y"] - 1e-8 <= y <= box["max_y"] + 1e-8


class MotionChecks(unittest.TestCase):
    def profile(self, actions, start=(0, 0, 0), **settings):
        return build_profile(imported(actions, start), {"timing_uncertainty_s": 0, **settings})

    def test_trapezoid_time_and_terminal_occupancy(self):
        profile = self.profile([move([[0, 0], [100, 0]])], speed_in_s=20,
                               acceleration_in_s2=10, robot_length_in=4,
                               robot_width_in=6, clearance_in=1)
        self.assertEqual(profile["status"], "complete")
        self.assertAlmostEqual(profile["summary"]["duration_min_s"], 7)
        self.assertAlmostEqual(profile["summary"]["duration_max_s"], 7)
        hold = profile["reservations"][-1]
        self.assertEqual(hold["kind"], "hold")
        self.assertAlmostEqual(hold["start_min_s"], 7)
        self.assertEqual(hold["window_end_s"], 30)
        radius = math.hypot(4, 6) / 2 + 1
        self.assertAlmostEqual(hold["bounds"]["min_x"], 100 - radius)
        self.assertTrue(profile["summary"]["prediction_only"])
        self.assertEqual(profile["timeline"][0]["timing_basis"], "estimated")

    def test_source_wait_and_shoot_range_are_not_flattened(self):
        profile = self.profile([wait(2), wait(1.25, 4.25, kind="shoot"), wait(1)])
        events = profile["timeline"]
        self.assertEqual((events[2]["start_min_s"], events[2]["start_max_s"]), (3.25, 6.25))
        self.assertEqual((profile["summary"]["duration_min_s"], profile["summary"]["duration_max_s"]), (4.25, 7.25))
        self.assertEqual(events[1]["timing_basis"], "source")

    def test_start_delay_and_uncertainty_include_stationary_body(self):
        profile = build_profile(imported([wait(1)]), {"start_delay_s": 3, "timing_uncertainty_s": 0.5})
        delay = profile["reservations"][0]
        self.assertEqual((delay["window_start_s"], delay["window_end_s"]), (0, 3.5))
        self.assertEqual(profile["timeline"][1]["start_min_s"], 3)
        self.assertEqual(profile["summary"]["duration_min_s"], 4)

    def test_triangle_motion_profile(self):
        profile = self.profile([move([[0, 0], [10, 0]])], speed_in_s=100, acceleration_in_s2=10)
        self.assertAlmostEqual(profile["summary"]["duration_min_s"], 2)
        samples = profile["timeline"][0]["samples"]
        middle = min(samples, key=lambda s: abs(s["offset_s"] - 1))
        self.assertAlmostEqual(middle["x"], 5)

    def test_in_place_turn_has_rotation_time_and_body_occupancy(self):
        profile = self.profile([{"kind": "turn", "angle_rad": math.pi / 2}], turn_rate_rad_s=math.pi / 2)
        self.assertAlmostEqual(profile["summary"]["duration_min_s"], 1)
        self.assertAlmostEqual(profile["timeline"][0]["end_pose"]["heading_rad"], math.pi / 2)
        self.assertTrue(contains(profile["reservations"][0]["bounds"], 0, 0))

    def test_zero_length_bezier_can_rotate_heading(self):
        profile = self.profile([move([[0, 0], [0, 0]], {"type": "linear", "start_rad": 0, "end_rad": math.pi / 2})],
                               turn_rate_rad_s=math.pi / 2)
        self.assertAlmostEqual(profile["summary"]["duration_min_s"], 1)
        self.assertAlmostEqual(profile["timeline"][0]["end_pose"]["heading_rad"], math.pi / 2)
        self.assertAlmostEqual(profile["timeline"][0]["samples"][-1]["heading_rad"], math.pi / 2)

    def test_quadratic_length_uses_curve_not_control_polygon(self):
        profile = self.profile([move([[0, 0], [0, 20], [20, 20]])])
        length = profile["timeline"][0]["distance_in"]
        self.assertGreater(length, math.sqrt(800))
        self.assertLess(length, 40)
        self.assertAlmostEqual(length, 32.4645, delta=0.02)

    def test_continuous_hull_covers_curve_between_coarse_samples(self):
        profile = self.profile([move([[0, 0], [10, 50], [20, -50], [30, 0]])],
                               sample_period_s=1000, robot_length_in=0.1,
                               robot_width_in=0.1, clearance_in=0)
        reservations = [r for r in profile["reservations"] if r["kind"] == "move"]
        self.assertEqual(len(reservations), 1)
        # Closed-form points include both excursions, invisible from endpoints.
        for t in [i / 100 for i in range(101)]:
            x = 30 * t
            y = 150 * t * (1 - t) * (1 - 2 * t)
            self.assertTrue(contains(reservations[0]["bounds"], x, y))

    def test_polyline_corners_are_not_smoothed(self):
        action = move([[0, 0], [0, 10], [10, 10]])
        action["path"]["kind"] = "polyline"
        profile = self.profile([action])
        self.assertAlmostEqual(profile["timeline"][0]["distance_in"], 20)
        samples = profile["timeline"][0]["samples"]
        self.assertTrue(all(abs(p["x"]) < 1e-8 or abs(p["y"] - 10) < 1e-8 for p in samples))

    def test_compound_segments_preserve_geometry(self):
        action = {"kind": "move", "path": {"segments": [
            {"kind": "line", "points": [[0, 0], [0, 10]], "heading": {"type": "constant", "start_rad": 0}},
            {"kind": "line", "points": [[0, 10], [10, 10]], "heading": {"type": "constant", "start_rad": 0}}]}}
        profile = self.profile([action])
        self.assertEqual(profile["status"], "complete")
        self.assertEqual(profile["timeline"][0]["end_pose"]["x"], 10)
        self.assertEqual(profile["timeline"][0]["end_pose"]["y"], 10)
        self.assertAlmostEqual(profile["timeline"][0]["distance_in"], 20)

    def test_disconnected_compound_is_invalid(self):
        action = {"kind": "move", "path": {"segments": [
            {"points": [[0, 0], [1, 0]]}, {"points": [[2, 0], [3, 0]]}]}}
        self.assertEqual(self.profile([action])["status"], "invalid")

    def test_source_motion_duration_keeps_complete_geometry(self):
        action = move([[0, 0], [100, 0]], duration={"min_s": 1, "max_s": 1, "basis": "source"})
        profile = self.profile([action], speed_in_s=1, acceleration_in_s2=1)
        self.assertEqual(profile["summary"]["duration_min_s"], 1)
        self.assertEqual(profile["timeline"][0]["samples"][-1]["x"], 100)
        self.assertEqual(profile["timeline"][0]["samples"][-1]["offset_s"], 1)
        last_move = [r for r in profile["reservations"] if r["kind"] == "move"][-1]
        self.assertTrue(contains(last_move["bounds"], 100, 0))
        self.assertLessEqual(last_move["window_end_s"], 1)

    def test_overlong_schedule_scales_every_action_and_keeps_late_occupancy(self):
        actions = [move([[0, 0], [10, 0]], duration={"min_s": 20, "max_s": 20, "basis": "source"}),
                   wait(10), {"kind": "turn", "angle_rad": math.pi},
                   move([[10, 0], [20, 0]], duration={"min_s": 25, "max_s": 25, "basis": "source"})]
        profile = self.profile(actions, start_delay_s=4)
        summary = profile["summary"]
        self.assertEqual((summary["unscaled_duration_max_s"], summary["duration_max_s"]), (60, 30))
        self.assertEqual(summary["time_scale_factor"], 0.5)
        self.assertEqual([e["duration_max_s"] for e in profile["timeline"]], [2, 10, 5, 0.5, 12.5])
        self.assertEqual(profile["actions"][0]["duration"]["max_s"], 20)
        self.assertEqual(profile["timeline"][1]["samples"][-1]["offset_s"], 10)
        late = [r for r in profile["reservations"] if r["action_index"] == 4]
        self.assertTrue(any(r["window_start_s"] <= 29.9 <= r["window_end_s"]
                            and contains(r["bounds"], 20, 0) for r in late))
        self.assertIn("timeline_scaled", [d["code"] for d in profile["diagnostics"]])

    def test_scaling_preserves_ranges_and_configured_uncertainty(self):
        profile = self.profile([wait(40, 60)], timing_uncertainty_s=0.5)
        event = profile["timeline"][0]
        self.assertEqual((event["duration_min_s"], event["duration_max_s"]), (20, 30))
        self.assertEqual(profile["reservations"][0]["end_max_s"], 30.5)
        self.assertEqual(profile["reservations"][0]["window_end_s"], 30)
        hold = profile["timeline"][-1]
        self.assertEqual((hold["duration_min_s"], hold["duration_max_s"]), (0, 10))
        self.assertEqual((hold["end_min_s"], hold["end_max_s"]), (30, 30))

    def test_scaling_uses_parallel_completion_and_custom_deadline(self):
        profile = self.profile([{"kind": "parallel", "children": [
            move([[0, 0], [50, 0]], duration={"min_s": 20, "max_s": 20, "basis": "source"}), wait(60)]}],
            auto_duration_s=20)
        self.assertAlmostEqual(profile["summary"]["time_scale_factor"], 1 / 3)
        self.assertEqual(profile["summary"]["duration_max_s"], 20)
        hold = next(r for r in profile["reservations"] if r["kind"] == "parallel_hold")
        self.assertAlmostEqual(hold["start_min_s"], 20 / 3)
        self.assertEqual(hold["window_end_s"], 20)
        self.assertTrue(contains(hold["bounds"], 50, 0))

    def test_late_abort_outcome_is_scaled_before_deadline_filtering(self):
        profile = self.profile([wait(10, 20), move([[0, 0], [100, 0]],
            duration={"min_s": 40, "max_s": 40, "basis": "source"}, timeout_s=35, on_timeout="abort")])
        abort = next(e for e in profile["timeline"] if e["kind"] == "abort_hold")
        self.assertEqual((abort["start_min_s"], abort["start_max_s"]), (22.5, 27.5))
        self.assertEqual((abort["duration_min_s"], abort["duration_max_s"]), (2.5, 7.5))
        self.assertEqual((abort["end_min_s"], abort["end_max_s"]), (30, 30))
        reservation = next(r for r in profile["reservations"] if r["kind"] == "abort_hold")
        self.assertEqual(reservation["window_end_s"], 30)
        self.assertTrue(contains(reservation["bounds"], 100, 0))
        late = self.profile([wait(0, 50), move([[0, 0], [30, 0]], timeout_s=15, on_timeout="abort")])
        late_abort = next(e for e in late["timeline"] if e["kind"] == "abort_hold")
        self.assertEqual(late_abort["start_max_s"], 30)
        self.assertEqual(late_abort["duration_min_s"], 0)

    def test_unknown_duration_is_not_given_an_invented_scaling_factor(self):
        profile = self.profile([wait(60), wait(0, None)])
        self.assertEqual(profile["summary"]["time_scale_factor"], 1)
        self.assertIsNone(profile["summary"]["unscaled_duration_max_s"])
        self.assertIsNone(profile["summary"]["duration_max_s"])
        self.assertEqual(profile["status"], "incomplete")
        for total in (10, 30):
            with self.subTest(total=total):
                bounded = self.profile([wait(total)])
                self.assertEqual(bounded["summary"]["time_scale_factor"], 1)
                self.assertEqual(bounded["summary"]["duration_max_s"], total)

    def test_source_deadline_hold_scales_but_terminal_occupancy_keeps_auto_deadline(self):
        profile = self.profile([{"kind": "hold"}, wait(30)])
        self.assertEqual(profile["summary"]["unscaled_duration_max_s"], 60)
        self.assertEqual(profile["timeline"][0]["end_max_s"], 15)
        self.assertEqual(profile["summary"]["duration_max_s"], 30)

    def test_source_hold_preserves_late_and_unknown_arrivals(self):
        profile = self.profile([wait(20, 60), {"kind": "hold"}])
        self.assertEqual(profile["summary"]["time_scale_factor"], 0.5)
        self.assertEqual(profile["summary"]["duration_max_s"], 30)
        hold = profile["timeline"][1]
        self.assertEqual((hold["start_min_s"], hold["start_max_s"]), (10, 30))
        self.assertEqual((hold["end_min_s"], hold["end_max_s"]), (15, 30))
        unknown = self.profile([wait(20, None), {"kind": "hold"}])
        self.assertEqual(unknown["summary"]["time_scale_factor"], 1)
        self.assertIsNone(unknown["summary"]["duration_max_s"])
        self.assertIsNone(unknown["timeline"][1]["end_max_s"])

    def test_unknown_action_propagates_time_and_spatial_uncertainty(self):
        profile = self.profile([{"kind": "unknown", "detail": "sensor-dependent custom movement"},
                                move([[0, 0], [10, 0]])])
        self.assertEqual(profile["status"], "incomplete")
        self.assertIsNone(profile["summary"]["duration_max_s"])
        self.assertIsNone(profile["timeline"][1]["start_max_s"])
        unknown = profile["reservations"][0]
        self.assertEqual(unknown["spatial_basis"], "unknown_entire_field")
        self.assertEqual(unknown["window_end_s"], 30)
        self.assertGreaterEqual(unknown["bounds"]["max_x"], 144)

    def test_unknown_stationary_wait_does_not_invent_motion(self):
        profile = self.profile([wait(0, None)])
        self.assertEqual(profile["status"], "incomplete")
        self.assertIsNone(profile["timeline"][0]["duration_max_s"])
        box = profile["reservations"][0]["bounds"]
        self.assertLess(box["max_x"], 20)
        self.assertEqual(profile["reservations"][0]["window_end_s"], 30)

    def test_all_conditional_paths_and_join_ranges_survive(self):
        profile = self.profile([{"kind": "branch", "branches": [
            {"label": "left", "actions": [wait(2)]},
            {"label": "right", "actions": [wait(5)]}]}, wait(1)])
        self.assertEqual(profile["summary"]["duration_min_s"], 3)
        self.assertEqual(profile["summary"]["duration_max_s"], 6)
        self.assertEqual({e["branch"] for e in profile["timeline"][:2]}, {"left", "right"})
        self.assertEqual((profile["timeline"][2]["start_min_s"], profile["timeline"][2]["start_max_s"]), (2, 5))

    def test_distinct_branch_endpoints_remain_occupied(self):
        profile = self.profile([{"kind": "branch", "branches": [
            {"label": "north", "actions": [move([[0, 0], [0, 50]])]},
            {"label": "south", "actions": [move([[0, 0], [0, -50]])]}]}])
        hold = profile["reservations"][-1]
        self.assertTrue(contains(hold["bounds"], 0, 50))
        self.assertTrue(contains(hold["bounds"], 0, -50))

    def test_parallel_mechanism_wait_is_not_a_second_stationary_robot(self):
        profile = self.profile([{"kind": "parallel", "children": [move([[0, 0], [50, 0]]), wait(10)]}])
        self.assertEqual(profile["summary"]["duration_min_s"], 10)
        self.assertFalse(any(r["kind"] == "wait" for r in profile["reservations"]))
        wait_after_motion = next(r for r in profile["reservations"] if r["kind"] == "parallel_hold")
        self.assertTrue(contains(wait_after_motion["bounds"], 50, 0))
        self.assertFalse(contains(wait_after_motion["bounds"], 0, 0))

    def test_parallel_waits_use_maximum_not_sum(self):
        profile = self.profile([{"kind": "parallel", "children": [wait(2), wait(5)]}])
        self.assertEqual(profile["summary"]["duration_min_s"], 5)
        self.assertEqual(len([r for r in profile["reservations"] if r["kind"] == "parallel_wait"]), 1)

    def test_parallel_drive_conflict_is_not_reported_complete(self):
        profile = self.profile([{"kind": "parallel", "children": [
            move([[0, 0], [50, 0]]), move([[0, 0], [0, 50]])]}])
        self.assertEqual(profile["status"], "incomplete")
        self.assertTrue(any(d["code"] == "parallel_drive_conflict" for d in profile["diagnostics"]))
        self.assertTrue(any(r["spatial_basis"] == "unknown_entire_field" for r in profile["reservations"]))

    def test_readiness_timeout_preserves_aborted_shooting_position(self):
        shoot = wait(1.25, 4.25, kind="shoot", readiness_timeout_s=3, on_readiness_timeout="abort")
        profile = self.profile([shoot, move([[0, 0], [100, 0]])])
        aborted = [r for r in profile["reservations"] if r["kind"] == "abort_hold"]
        self.assertEqual(len(aborted), 1)
        self.assertEqual((aborted[0]["window_start_s"], aborted[0]["window_end_s"]), (3, 30))
        self.assertTrue(contains(aborted[0]["bounds"], 0, 0))
        self.assertFalse(contains(aborted[0]["bounds"], 100, 0))
        self.assertTrue(any(e["kind"] == "move" for e in profile["timeline"]))

    def test_path_timeout_can_leave_partner_anywhere_along_route(self):
        profile = self.profile([move([[0, 0], [0, 50], [50, 50]],
                                    timeout_s=8, on_timeout="abort")])
        aborted = next(r for r in profile["reservations"] if r["kind"] == "abort_hold")
        self.assertEqual((aborted["window_start_s"], aborted["window_end_s"]), (8, 30))
        self.assertTrue(contains(aborted["bounds"], 0, 0))
        self.assertTrue(contains(aborted["bounds"], 50, 50))
        self.assertEqual(profile["summary"]["possible_abort_count"], 1)

    def test_piecewise_heading_uses_source_progress(self):
        heading = {"type": "piecewise", "segments": [
            {"start_progress": 0, "end_progress": 0.5, "type": "linear", "start_rad": 0, "end_rad": math.pi / 2},
            {"start_progress": 0.5, "end_progress": 1, "type": "constant", "continue_from_previous": True}]}
        profile = self.profile([move([[0, 0], [30, 0]], heading)])
        self.assertEqual(profile["status"], "complete")
        self.assertAlmostEqual(profile["timeline"][0]["end_pose"]["heading_rad"], math.pi / 2)

    def test_compound_continuation_keeps_previous_child_tangent(self):
        first = {"type": "piecewise", "progress_start": 0, "progress_end": 0.5, "segments": [
            {"start_progress": 0, "end_progress": 0.5, "type": "tangent"},
            {"start_progress": 0.5, "end_progress": 1, "type": "constant", "continue_from_previous": True}]}
        second = {**first, "progress_start": 0.5, "progress_end": 1}
        profile = self.profile([move([[0, 0], [0, 10]], first), move([[0, 10], [10, 10]], second)])
        self.assertEqual(profile["status"], "complete")
        self.assertAlmostEqual(profile["timeline"][1]["end_pose"]["heading_rad"], math.pi / 2)

    def test_terminal_hold_duration_reflects_prior_timing_range(self):
        profile = self.profile([wait(5, 10)])
        hold = profile["timeline"][-1]
        self.assertEqual((hold["duration_min_s"], hold["duration_max_s"]), (20, 25))
        self.assertEqual((hold["end_min_s"], hold["end_max_s"]), (30, 30))

    def test_facing_point_heading(self):
        profile = self.profile([move([[0, 0], [20, 0]], {"type": "facing-point", "point": [20, 20]})])
        self.assertAlmostEqual(profile["timeline"][0]["end_pose"]["heading_rad"], math.pi / 2)

    def test_missing_initial_pose_is_inferred_only_from_first_motion(self):
        profile = self.profile([move([[10, 20], [20, 20]])], start=None)
        self.assertEqual(profile["start_pose"]["x"], 10)
        unknown = self.profile([wait(1), move([[10, 20], [20, 20]])], start=None)
        self.assertEqual(unknown["status"], "incomplete")
        self.assertIsNone(unknown["start_pose"])

    def test_origin_gap_does_not_create_unchecked_connector(self):
        profile = self.profile([move([[50, 50], [100, 50]])])
        self.assertEqual(profile["status"], "incomplete")
        self.assertTrue(any(r["kind"] == "unknown_transition" for r in profile["reservations"]))

    def test_invalid_or_nonfinite_inputs_remain_exportable(self):
        for settings in ({"sample_period_s": 0}, {"speed_in_s": -1}, {"robot_width_in": float("nan")},
                         {"acceleration_in_s2": True}):
            result = build_profile(imported([wait(1)]), settings)
            self.assertEqual(result["status"], "invalid")
            json.dumps(result, allow_nan=False)
        result = self.profile([move([[0, 0], [float("inf"), 1]])])
        self.assertEqual(result["status"], "invalid")
        json.dumps(result, allow_nan=False)

    def test_unbounded_allocation_is_rejected(self):
        profile = self.profile([move([[0, 0], [10, 0]])], sample_period_s=1e-10)
        self.assertEqual(profile["status"], "invalid")
        self.assertIn("sample limit", profile["diagnostics"][-1]["message"])

    def test_import_errors_are_not_lost_by_motion_build(self):
        raw = imported([wait(1)])
        raw["diagnostics"].append({"severity": "error", "code": "missing_source", "message": "BaseAuto missing"})
        result = build_profile(raw)
        self.assertEqual(result["status"], "invalid")
        self.assertEqual(result["selected_entry"], "TestAuto")


if __name__ == "__main__":
    unittest.main()
