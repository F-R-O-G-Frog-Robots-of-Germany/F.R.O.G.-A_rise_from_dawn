"""Offline time estimates and conservative swept occupancy for imported autonomous actions.

All motion times are predictions from the supplied motion model.  They are not measured
partner positions or a collision-avoidance guarantee.  Unknown source behavior stays
unknown; a missing duration is never converted into a zero-second action.
"""

from __future__ import annotations

from bisect import bisect_left
from copy import deepcopy
from dataclasses import dataclass
import json
import math
from typing import Any


DEFAULT_SETTINGS = {
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
MAX_ACTIONS = 5000
MAX_DEPTH = 80
MAX_CONTROL_POINTS = 64
MAX_SAMPLES = 20000
MAX_POSES = 128
EPSILON = 1e-9


def _number(value: Any, name: str, minimum: float | None = None,
            positive: bool = False) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(f"{name} must be a finite number")
    number = float(value)
    if positive and number <= 0:
        raise ValueError(f"{name} must be greater than zero")
    if minimum is not None and number < minimum:
        raise ValueError(f"{name} must be at least {minimum:g}")
    return number


def _wrap(angle: float) -> float:
    return (angle + math.pi) % (2 * math.pi) - math.pi


def _point(value: Any) -> tuple[float, float]:
    if isinstance(value, dict):
        return _number(value.get("x"), "point.x"), _number(value.get("y"), "point.y")
    if not isinstance(value, (list, tuple)) or len(value) < 2:
        raise ValueError("Each path point must contain x and y")
    return _number(value[0], "point.x"), _number(value[1], "point.y")


def _pose(value: Any) -> tuple[float, float, float | None] | None:
    if value is None:
        return None
    if not isinstance(value, dict):
        raise ValueError("start_pose must be an object")
    x, y = _point(value)
    heading = value.get("heading_rad")
    return x, y, None if heading is None else _number(heading, "pose.heading_rad")


def _pose_json(value: tuple[float, float, float | None] | None) -> dict | None:
    return None if value is None else {"x": value[0], "y": value[1], "heading_rad": value[2]}


def _split(points: list[tuple[float, float]], t: float) -> tuple[list, list]:
    """Exact de Casteljau control polygons of the two portions of a Bezier."""
    work = points[:]
    left, right = [work[0]], [work[-1]]
    while len(work) > 1:
        work = [(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
                for a, b in zip(work, work[1:])]
        left.append(work[0])
        right.append(work[-1])
    return left, list(reversed(right))


def _subcurve(points: list[tuple[float, float]], begin: float, end: float) -> list:
    if end <= EPSILON:
        return [points[0]]
    prefix = points if end >= 1 else _split(points, end)[0]
    if begin <= EPSILON:
        return prefix
    return _split(prefix, min(1.0, begin / end))[1]


def _bezier(points: list[tuple[float, float]], t: float) -> tuple[float, float]:
    return _split(points, t)[0][-1]


def _bounds(points: list[tuple[float, float]], inflation: float = 0.0) -> dict:
    return {"min_x": min(p[0] for p in points) - inflation,
            "max_x": max(p[0] for p in points) + inflation,
            "min_y": min(p[1] for p in points) - inflation,
            "max_y": max(p[1] for p in points) + inflation}


def _union_bounds(bounds: list[dict]) -> dict | None:
    if not bounds:
        return None
    return {"min_x": min(b["min_x"] for b in bounds), "max_x": max(b["max_x"] for b in bounds),
            "min_y": min(b["min_y"] for b in bounds), "max_y": max(b["max_y"] for b in bounds)}


class _Curve:
    def __init__(self, points: list, kind: str):
        if not isinstance(points, list) or len(points) < 2 or len(points) > MAX_CONTROL_POINTS:
            raise ValueError(f"A path needs 2–{MAX_CONTROL_POINTS} finite control points")
        self.points = [_point(p) for p in points]
        self.polyline = kind in ("polyline", "line", "straight")
        if kind in ("line", "straight") and len(self.points) != 2:
            raise ValueError("A straight path must have exactly two endpoints")
        if kind not in ("polyline", "line", "straight", "bezier", "curve", ""):
            raise ValueError(f"Unsupported path geometry {kind!r}")
        if self.polyline:
            self.table = [(i / (len(self.points) - 1), p) for i, p in enumerate(self.points)]
        else:
            # Adaptively flatten for the arc-length estimate, while reservations use
            # exact subcurve control hulls and do not rely on sampled points alone.
            self.table = [(0.0, self.points[0])]
            self._flatten(self.points, 0, 1, 0)
        self.distances = [0.0]
        for (_, a), (_, b) in zip(self.table, self.table[1:]):
            self.distances.append(self.distances[-1] + math.dist(a, b))
        self.length = self.distances[-1]

    def _flatten(self, points: list, a: float, b: float, depth: int) -> None:
        polygon = sum(math.dist(x, y) for x, y in zip(points, points[1:]))
        if not math.isfinite(polygon):
            raise ValueError("Path geometry exceeds the finite motion model")
        if polygon - math.dist(points[0], points[-1]) <= 0.002 or depth >= 12:
            self.table.append((b, points[-1]))
            return
        left, right = _split(points, 0.5)
        middle = (a + b) / 2
        self._flatten(left, a, middle, depth + 1)
        self._flatten(right, middle, b, depth + 1)

    def parameter(self, distance: float) -> float:
        if self.length <= EPSILON:
            return 1.0 if distance > 0 else 0.0
        distance = min(self.length, max(0.0, distance))
        index = max(1, min(len(self.distances) - 1, bisect_left(self.distances, distance)))
        previous, following = self.distances[index - 1:index + 1]
        ratio = 0 if following <= previous else (distance - previous) / (following - previous)
        return self.table[index - 1][0] + ratio * (self.table[index][0] - self.table[index - 1][0])

    def at(self, t: float) -> tuple[float, float]:
        t = max(0.0, min(1.0, t))
        if not self.polyline:
            return _bezier(self.points, t)
        scaled = t * (len(self.points) - 1)
        index = min(len(self.points) - 2, int(scaled))
        a, b = self.points[index:index + 2]
        return a[0] + (b[0] - a[0]) * (scaled - index), a[1] + (b[1] - a[1]) * (scaled - index)

    def tangent(self, t: float) -> float | None:
        if self.polyline:
            index = min(len(self.points) - 2, max(0, int(t * (len(self.points) - 1))))
            pairs = list(zip(self.points, self.points[1:]))
            pairs = pairs[index:] + pairs[:index]
            for a, b in pairs:
                if math.dist(a, b) > EPSILON:
                    return math.atan2(b[1] - a[1], b[0] - a[0])
            return None
        derivative = [(b[0] - a[0], b[1] - a[1]) for a, b in zip(self.points, self.points[1:])]
        dx, dy = _bezier(derivative, t) if len(derivative) > 1 else derivative[0]
        if math.hypot(dx, dy) <= EPSILON:
            for offset in (1e-6, -1e-6, 1e-3, -1e-3):
                sample = min(1, max(0, t + offset))
                dx, dy = _bezier(derivative, sample) if len(derivative) > 1 else derivative[0]
                if math.hypot(dx, dy) > EPSILON:
                    break
        return None if math.hypot(dx, dy) <= EPSILON else math.atan2(dy, dx)

    def hull(self, begin: float, end: float) -> list:
        if not self.polyline:
            return _subcurve(self.points, begin, end)
        values = [self.at(begin)]
        values.extend(p for i, p in enumerate(self.points)
                      if begin < i / (len(self.points) - 1) < end)
        values.append(self.at(end))
        return values


class _Path:
    def __init__(self, data: dict):
        if not isinstance(data, dict):
            raise ValueError("A move action must contain a path object")
        segments = data.get("segments", [data])
        if not isinstance(segments, list) or not segments or len(segments) > MAX_CONTROL_POINTS:
            raise ValueError("A path must contain a bounded nonempty segment list")
        self.parts = []
        for segment in segments:
            if not isinstance(segment, dict):
                raise ValueError("Path segments must be objects")
            curve = _Curve(segment.get("points"), segment.get("kind", "bezier"))
            heading = segment.get("heading", data.get("heading", {"type": "tangent"}))
            self.parts.append((curve, heading))
        for (left, _), (right, _) in zip(self.parts, self.parts[1:]):
            if math.dist(left.points[-1], right.points[0]) > 1e-6:
                raise ValueError("A compound path has disconnected segments")
        self.length = sum(curve.length for curve, _ in self.parts)

    def locate(self, distance: float) -> tuple[int, float]:
        distance = min(self.length, max(0.0, distance))
        for i, (curve, _) in enumerate(self.parts):
            if distance <= curve.length or i == len(self.parts) - 1:
                return i, curve.parameter(distance)
            distance -= curve.length
        return len(self.parts) - 1, 1.0

    def at(self, distance: float) -> tuple[float, float]:
        index, t = self.locate(distance)
        return self.parts[index][0].at(t)

    def heading(self, distance: float, fallback: float | None, progress: float | None = None) -> float | None:
        index, t = self.locate(distance)
        if self.length <= EPSILON and progress is not None:
            t = progress
        curve, heading = self.parts[index]
        return self._heading(heading, curve, t, fallback)

    def _heading(self, heading: Any, curve: _Curve, t: float, fallback: float | None) -> float | None:
        if not isinstance(heading, dict):
            raise ValueError("Path heading must be an object")
        kind = heading.get("type", "tangent")
        if kind in ("tangent", "tangential"):
            angle = curve.tangent(t)
            return None if angle is None else _wrap(angle + (math.pi if heading.get("reverse") else 0))
        if kind in ("constant", "linear"):
            start = heading.get("start_rad", heading.get("angle_rad", heading.get("heading_rad", fallback)))
            if start is None:
                return None
            start = _number(start, "heading.start_rad")
            if kind == "constant":
                return _wrap(start)
            end = heading.get("end_rad")
            return None if end is None else _wrap(start + _wrap(_number(end, "heading.end_rad") - start) * t)
        if kind in ("facing-point", "facing_point", "facingPoint"):
            target = _point(heading.get("point"))
            here = curve.at(t)
            return None if math.dist(here, target) <= EPSILON else _wrap(
                math.atan2(target[1] - here[1], target[0] - here[0])
                + (math.pi if heading.get("reverse") else 0))
        if kind == "piecewise":
            progress_start = _number(heading.get("progress_start", 0), "heading.progress_start")
            progress_end = _number(heading.get("progress_end", 1), "heading.progress_end")
            if not 0 <= progress_start < progress_end <= 1:
                raise ValueError("Piecewise heading slice must stay inside [0, 1]")
            progress = progress_start + t * (progress_end - progress_start)
            segments = heading.get("segments")
            if not isinstance(segments, list) or not segments:
                raise ValueError("Piecewise heading must contain segments")
            previous = fallback
            for i, segment in enumerate(segments):
                if not isinstance(segment, dict):
                    raise ValueError("Piecewise heading segments must be objects")
                begin = _number(segment.get("start_progress"), "heading.start_progress")
                end = _number(segment.get("end_progress"), "heading.end_progress")
                if not 0 <= begin < end <= 1:
                    raise ValueError("Piecewise heading progress must stay inside [0, 1]")
                spec = dict(segment)
                if spec.get("continue_from_previous") and previous is not None:
                    spec["start_rad"] = previous
                if begin <= progress <= end or i == len(segments) - 1 and progress > end:
                    fraction = min(1, max(0, (progress - begin) / (end - begin)))
                    geometric = spec.get("type") in ("tangent", "tangential", "facing-point", "facing_point", "facingPoint")
                    return self._heading(spec, curve, t if geometric else fraction, previous)
                boundary_t = min(1, max(0, (end - progress_start) / (progress_end - progress_start)))
                geometric = spec.get("type") in ("tangent", "tangential", "facing-point", "facing_point", "facingPoint")
                if geometric and end <= progress_start + EPSILON and previous is not None:
                    # A compound slice cannot evaluate an earlier child's tangent
                    # using its own geometry. Its incoming state carries that angle.
                    continue
                previous = self._heading(spec, curve, boundary_t if geometric else 1, previous)
            return None
        raise ValueError(f"Unsupported heading interpolation {kind!r}")

    def hull(self, begin: float, end: float) -> list:
        first, a = self.locate(begin)
        last, b = self.locate(end)
        points = []
        for index in range(first, last + 1):
            points.extend(self.parts[index][0].hull(a if index == first else 0,
                                                  b if index == last else 1))
        return points


class _Travel:
    def __init__(self, distance: float, velocity: float, acceleration: float):
        self.distance, self.acceleration = distance, acceleration
        self.peak = min(velocity, math.sqrt(max(0, distance * acceleration)))
        self.ramp = self.peak / acceleration
        self.cruise = 0 if self.peak <= EPSILON else max(0, (distance - self.peak * self.ramp) / self.peak)
        self.duration = 2 * self.ramp + self.cruise

    def position(self, fraction: float) -> float:
        t = min(self.duration, max(0, fraction * self.duration))
        if t <= self.ramp:
            return self.acceleration * t * t / 2
        if t <= self.ramp + self.cruise:
            return self.peak * self.ramp / 2 + self.peak * (t - self.ramp)
        remaining = self.duration - t
        return self.distance - self.acceleration * remaining * remaining / 2


@dataclass
class _State:
    minimum: float
    maximum: float | None
    poses: list[tuple[float, float, float | None]] | None


def _max_nullable(values: list[float | None]) -> float | None:
    return None if any(value is None for value in values) else max(values, default=0.0)


def _poses_union(states: list[_State]) -> list | None:
    if any(state.poses is None for state in states):
        return None
    result = []
    for state in states:
        for pose in state.poses:
            if pose not in result:
                result.append(pose)
    if len(result) > MAX_POSES:
        return None
    return result


def _has_motion(action: dict) -> bool:
    kind = action.get("kind")
    if kind in ("move", "turn") or kind == "unknown" and action.get("affects_motion", True):
        return True
    return any(_has_motion(child) for child in action.get("children", []) if isinstance(child, dict)) or any(
        _has_motion(child) for branch in action.get("branches", []) if isinstance(branch, dict)
        for child in branch.get("actions", []) if isinstance(child, dict))


class _Builder:
    def __init__(self, imported: dict, settings: dict, *, time_scale_factor: float = 1.0,
                 timing_only: bool = False):
        self.result = deepcopy(imported)
        if not isinstance(self.result.get("diagnostics", []), list):
            raise ValueError("diagnostics must be a list")
        self.result.setdefault("diagnostics", [])
        self.settings = dict(DEFAULT_SETTINGS)
        self.settings.update(settings)
        self.time_scale_factor = time_scale_factor
        self.timing_only = timing_only
        for key in DEFAULT_SETTINGS:
            self.settings[key] = _number(self.settings[key], key, minimum=0,
                positive=key not in ("clearance_in", "start_delay_s", "timing_uncertainty_s"))
        self.inflation = math.hypot(self.settings["robot_length_in"], self.settings["robot_width_in"]) / 2 \
            + self.settings["clearance_in"]
        self.timeline, self.reservations = [], []
        self.unresolved = False
        self.total_actions = 0
        self.total_samples = 0
        self.groups = 0
        self.abort_count = 0

    def diagnostic(self, code: str, message: str, action: dict | None = None,
                   severity: str = "warning", unresolved: bool = False) -> None:
        item = {"severity": severity, "code": code, "message": message}
        if action is not None and action.get("source"):
            item["source"] = action["source"]
        self.result["diagnostics"].append(item)
        self.unresolved |= unresolved

    def field_bounds(self) -> dict:
        size = self.settings["field_size_in"]
        return {"min_x": -self.inflation, "max_x": size + self.inflation,
                "min_y": -self.inflation, "max_y": size + self.inflation}

    def stationary_bounds(self, poses: list | None) -> dict:
        return self.field_bounds() if not poses else _bounds([(p[0], p[1]) for p in poses], self.inflation)

    def reservation(self, begin: _State, end: _State, bounds: dict, kind: str, index: int,
                    label: str, basis: str, offset_a: float = 0, offset_b: float = 0,
                    spatial_basis: str = "swept_body") -> None:
        if self.timing_only:
            return
        uncertainty, limit = self.settings["timing_uncertainty_s"], self.settings["auto_duration_s"]
        first = max(0, begin.minimum + offset_a - uncertainty)
        last = None if end.maximum is None else end.maximum + offset_b + uncertainty
        if first >= limit or last is not None and last < 0:
            return
        if len(self.reservations) >= MAX_SAMPLES:
            raise ValueError(f"Reservation limit of {MAX_SAMPLES} exceeded; use a larger sample period")
        self.reservations.append({
            "start_min_s": first,
            "start_max_s": None if begin.maximum is None else begin.maximum + offset_a + uncertainty,
            "end_min_s": max(0, end.minimum + offset_b - uncertainty),
            "end_max_s": last,
            "window_start_s": first,
            "window_end_s": limit if last is None else min(limit, max(first, last)),
            "bounds": bounds, "kind": kind, "action_index": index,
            "branch": label, "timing_basis": basis, "spatial_basis": spatial_basis,
        })

    def event(self, action: dict, start: _State, duration: tuple[float, float | None, str],
              end_poses: list | None, label: str, group: int | None) -> tuple[dict, _State]:
        lo, hi, basis = duration
        end = _State(start.minimum + lo,
                     None if start.maximum is None or hi is None else start.maximum + hi, end_poses)
        if self.time_scale_factor < 1:
            # Floating-point accumulation can put the final fitted event a few
            # ulps beyond AUTO even though its mathematical total is the limit.
            limit = self.settings["auto_duration_s"]
            end.minimum = min(limit, end.minimum)
            if end.maximum is not None:
                end.maximum = min(limit, end.maximum)
        item = {"index": len(self.timeline), "kind": action.get("kind", "unknown"),
                "start_min_s": start.minimum, "start_max_s": start.maximum,
                "end_min_s": end.minimum, "end_max_s": end.maximum,
                "duration_min_s": lo, "duration_max_s": hi, "timing_basis": basis,
                "time_scale_factor": self.time_scale_factor,
                "source": action.get("source"), "branch": label, "parallel_group": group,
                "start_pose": _pose_json(start.poses[0]) if start.poses and len(start.poses) == 1 else None,
                "end_pose": _pose_json(end_poses[0]) if end_poses and len(end_poses) == 1 else None}
        for key in ("path", "detail", "label", "name", "target_heading_rad", "angle_rad"):
            if key in action:
                item[key] = deepcopy(action[key])
        self.timeline.append(item)
        return item, end

    def duration(self, action: dict, default: tuple | None = None) -> tuple:
        raw = action.get("duration")
        if raw is None:
            if default is not None:
                lo, hi, basis = default
                return lo * self.time_scale_factor, None if hi is None else hi * self.time_scale_factor, basis
            self.diagnostic("duration_unknown", "An action has no bounded source duration", action, unresolved=True)
            return 0.0, None, "unknown"
        if not isinstance(raw, dict):
            raise ValueError("Action duration must be an object")
        lo = _number(raw.get("min_s", 0), "duration.min_s", minimum=0)
        hi = None if raw.get("max_s") is None else _number(raw["max_s"], "duration.max_s", minimum=lo)
        basis = raw.get("basis", "source")
        if basis not in ("source", "estimated", "unknown"):
            raise ValueError("Duration basis must be source, estimated or unknown")
        if hi is None:
            self.unresolved = True
        return lo * self.time_scale_factor, None if hi is None else hi * self.time_scale_factor, basis

    def abort_holds(self, action: dict, start: _State, bounds: dict, poses: list | None,
                    label: str, group: int | None, spatial_basis: str) -> None:
        """Retain source timeout/abort outcomes as occupancy, not just normal completion."""
        if self.timing_only:
            return
        metadata = action.get("metadata", {})
        if not isinstance(metadata, dict):
            raise ValueError("Action metadata must be an object")
        for timeout_key, outcome_key in (("readiness_timeout_s", "on_readiness_timeout"),
                                         ("timeout_s", "on_timeout")):
            timeout = action.get(timeout_key, metadata.get(timeout_key))
            outcome = action.get(outcome_key, metadata.get(outcome_key))
            if timeout is None or outcome != "abort":
                continue
            timeout = _number(timeout, timeout_key, minimum=0) * self.time_scale_factor
            earliest = start.minimum + timeout
            if earliest >= self.settings["auto_duration_s"]:
                continue
            abort_start = _State(earliest, None if start.maximum is None else start.maximum + timeout, poses)
            deadline = self.settings["auto_duration_s"]
            if abort_start.maximum is not None:
                abort_start.maximum = min(deadline, abort_start.maximum)
            abort_end = _State(deadline, deadline, poses)
            branch_label = "/".join(filter(None, [label, "abort:" + timeout_key]))
            item, _ = self.event({"kind": "abort_hold", "source": action.get("source"),
                                  "detail": "Source timeout ends the sequence; the robot remains an obstacle"},
                                 abort_start, (0 if abort_start.maximum is None else max(0, deadline - abort_start.maximum),
                                               max(0, deadline - earliest), "source"),
                                 poses, branch_label, group)
            item["end_min_s"] = item["end_max_s"] = deadline
            item["possible_bounds"] = bounds
            self.reservation(abort_start, abort_end, bounds, "abort_hold", item["index"], branch_label,
                             "source", spatial_basis=spatial_basis)
            self.abort_count += 1

    def sequence(self, actions: list, state: _State, label: str = "", group: int | None = None,
                 stationary: bool = True, depth: int = 0) -> _State:
        if not isinstance(actions, list):
            raise ValueError("Action sequences must be lists")
        for action in actions:
            state = self.action(action, state, label, group, stationary, depth + 1)
        return state

    def action(self, action: dict, state: _State, label: str, group: int | None,
               stationary: bool, depth: int) -> _State:
        if not isinstance(action, dict):
            raise ValueError("Each action must be an object")
        self.total_actions += 1
        if self.total_actions > MAX_ACTIONS or depth > MAX_DEPTH:
            raise ValueError("Autonomous action/dependency limit exceeded")
        kind = action.get("kind", "unknown")
        if kind == "sequence":
            return self.sequence(action.get("children", []), state, label, group, stationary, depth)
        if kind == "branch":
            branches = action.get("branches")
            if not isinstance(branches, list) or not branches:
                raise ValueError("A branch action must preserve its alternatives")
            self.diagnostic("conditional_alternatives", "Conditional paths are retained as alternatives", action)
            results = []
            for index, branch in enumerate(branches):
                if not isinstance(branch, dict):
                    raise ValueError("Branch alternatives must be objects")
                branch_label = "/".join(filter(None, [label, str(branch.get("label", index))]))
                results.append(self.sequence(branch.get("actions", []), state, branch_label, group, stationary, depth))
            return _State(min(s.minimum for s in results), _max_nullable([s.maximum for s in results]), _poses_union(results))
        if kind == "parallel":
            children = action.get("children", [])
            if not isinstance(children, list) or any(not isinstance(c, dict) for c in children):
                raise ValueError("Parallel actions must contain action objects")
            self.groups += 1
            parallel_group = self.groups
            moving = [i for i, child in enumerate(children) if _has_motion(child)]
            results = [self.action(child, state, label, parallel_group,
                        stationary and len(moving) == 1 and i in moving, depth + 1)
                       for i, child in enumerate(children)]
            if not results:
                return state
            end = _State(max(r.minimum for r in results), _max_nullable([r.maximum for r in results]), state.poses)
            if len(moving) == 1:
                motion = results[moving[0]]
                end.poses = motion.poses
                if stationary and (motion.minimum < end.minimum or motion.maximum != end.maximum):
                    self.reservation(motion, end, self.stationary_bounds(motion.poses), "parallel_hold",
                                     len(self.timeline) - 1, label, "source")
            elif len(moving) > 1:
                end.poses = _poses_union([results[i] for i in moving])
                self.diagnostic("parallel_drive_conflict", "Several parallel children command robot movement; occupancy is unresolved",
                                action, unresolved=True)
                if stationary:
                    self.reservation(state, end, self.field_bounds(), "unknown", len(self.timeline) - 1,
                                     label, "unknown", spatial_basis="unknown_entire_field")
            elif stationary:
                self.reservation(state, end, self.stationary_bounds(state.poses), "parallel_wait",
                                 len(self.timeline) - 1, label, "source")
            return end
        if kind == "move":
            return self.move(action, state, label, group)
        if kind == "turn":
            return self.turn(action, state, label, group, stationary)
        if kind in ("wait", "shoot", "hold", "mechanism"):
            default = (0.0, 0.0, "source") if kind == "mechanism" else None
            if kind == "hold" and "duration" not in action:
                deadline = self.settings["auto_duration_s"] * self.time_scale_factor
                duration = (0 if state.maximum is None else max(0, deadline - state.maximum),
                            max(0, deadline - state.minimum), "source")
                # Hold until an absolute deadline; unlike an ordinary duration it does
                # not get longer when a prior action is delayed.
                item, end = self.event(action, state, duration, state.poses, label, group)
                end.minimum = max(state.minimum, deadline)
                end.maximum = None if state.maximum is None else max(state.maximum, deadline)
                item["end_min_s"], item["end_max_s"] = end.minimum, end.maximum
            else:
                item, end = self.event(action, state, self.duration(action, default), state.poses, label, group)
            if stationary and kind != "mechanism":
                self.reservation(state, end, self.stationary_bounds(state.poses), kind, item["index"], label,
                                 item["timing_basis"], spatial_basis="stationary_body" if state.poses else "unknown_entire_field")
                self.abort_holds(action, state, self.stationary_bounds(state.poses), state.poses, label,
                                 group, "stationary_body" if state.poses else "unknown_entire_field")
            return end
        if action.get("diagnostic_reported"):
            self.unresolved = True
        else:
            self.diagnostic("action_unknown", f"Unsupported or unresolved action {kind!r}; duration and possible motion remain unknown",
                            action, unresolved=True)
        duration = self.duration(action)
        # Even a supplied bound cannot establish where an unknown movement goes.
        poses = None if action.get("affects_motion", True) else state.poses
        item, end = self.event(action, state, duration, poses, label, group)
        if stationary:
            self.reservation(state, end, self.stationary_bounds(poses), "unknown", item["index"], label,
                             "unknown", spatial_basis="unknown_entire_field" if poses is None else "stationary_body")
        return end

    def move(self, action: dict, state: _State, label: str, group: int | None) -> _State:
        path = _Path(action.get("path"))
        start, finish = path.at(0), path.at(path.length)
        origin_valid = state.poses is not None and all(math.dist((p[0], p[1]), start) <= 1e-5 for p in state.poses)
        if not origin_valid:
            self.diagnostic("path_origin_unresolved", "The declared path start does not match all possible preceding positions",
                            action, unresolved=True)
        fallback = state.poses[0][2] if state.poses and len(state.poses) == 1 else None
        first_heading = path.heading(0, fallback)
        headings = [path.heading(path.length * i / 128, fallback, i / 128) for i in range(129)]
        angular_distance = 0.0
        if fallback is not None and first_heading is not None:
            angular_distance += abs(_wrap(first_heading - fallback))
        for before, after in zip(headings, headings[1:]):
            if before is not None and after is not None:
                angular_distance += abs(_wrap(after - before))
        if any(h is None for h in headings):
            self.diagnostic("heading_unresolved", "Heading interpolation is not fully known; motion timing remains a model estimate", action)
        travel = _Travel(path.length, self.settings["speed_in_s"], self.settings["acceleration_in_s2"])
        nominal = max(travel.duration, angular_distance / self.settings["turn_rate_rad_s"])
        duration = self.duration(action, (nominal, nominal, "estimated"))
        if path.length > EPSILON and duration[1] is not None and duration[1] <= 0:
            raise ValueError("A moving path cannot have a zero maximum duration")
        end_poses = [(finish[0], finish[1], headings[-1])]
        item, end = self.event(action, state, duration, end_poses, label, group)
        item.update({"distance_in": path.length, "model_duration_s": nominal * self.time_scale_factor,
                     "unscaled_model_duration_s": nominal,
                     "motion_model": "rest-to-rest trapezoid; angular lower bound; estimated", "samples": []})
        if self.timing_only:
            return end
        if not origin_valid:
            self.reservation(state, end, self.field_bounds(), "unknown_transition", item["index"], label,
                             "unknown", spatial_basis="unknown_entire_field")
        # The complete geometry is retained even when a caller supplied a different
        # duration. Long paths use coarser (still continuous-hull) intervals rather
        # than losing an un-sampled tail or allocating unbounded samples.
        period = self.settings["sample_period_s"]
        sample_duration = duration[1] if duration[1] is not None else nominal * self.time_scale_factor
        relevant = min(sample_duration, self.settings["auto_duration_s"] + self.settings["timing_uncertainty_s"])
        count = max(1, math.ceil(relevant / period)) if nominal > EPSILON else 1
        self.total_samples += count + 1
        if self.total_samples > MAX_SAMPLES:
            raise ValueError(f"Motion sample limit of {MAX_SAMPLES} exceeded; use a larger sample period")
        for index in range(count + 1):
            offset = nominal * index / count if nominal > EPSILON else 0.0
            fraction = min(1, offset / nominal) if nominal > EPSILON else 1
            distance = travel.position(fraction) if travel.duration > EPSILON else path.length * fraction
            point = path.at(distance)
            item["samples"].append({"offset_s": fraction * duration[0],
                                    "offset_max_s": None if duration[1] is None else fraction * duration[1],
                                    "x": point[0], "y": point[1],
                                    "heading_rad": path.heading(distance, fallback, fraction)})
            if index == 0:
                continue
            prior_offset = nominal * (index - 1) / count if nominal > EPSILON else 0.0
            prior_fraction = min(1, prior_offset / nominal) if nominal > EPSILON else 1
            prior_distance = travel.position(prior_fraction) if travel.duration > EPSILON else path.length * prior_fraction
            relative_a = prior_fraction * duration[0]
            relative_b = fraction * duration[0]
            lower = _State(state.minimum + relative_a,
                           None if state.maximum is None or duration[1] is None else state.maximum + prior_fraction * duration[1], None)
            upper = _State(state.minimum + relative_b,
                           None if state.maximum is None or duration[1] is None else state.maximum + fraction * duration[1], None)
            self.reservation(lower, upper, _bounds(path.hull(prior_distance, distance), self.inflation),
                             "move", item["index"], label, duration[2])
        if nominal <= EPSILON:
            self.reservation(state, end, _bounds([start, finish], self.inflation), "move", item["index"], label, duration[2])
        self.abort_holds(action, state, _bounds(path.hull(0, path.length), self.inflation), None,
                         label, group, "possible_abort_along_path")
        return end

    def turn(self, action: dict, state: _State, label: str, group: int | None, stationary: bool) -> _State:
        target = action.get("target_heading_rad", action.get("heading_rad"))
        relative = action.get("angle_rad")
        if target is not None:
            target = _number(target, "turn.target_heading_rad")
        if relative is not None:
            relative = _number(relative, "turn.angle_rad")
        poses, angles = [], []
        if state.poses:
            for x, y, before in state.poses:
                after = target if target is not None else None if before is None or relative is None else before + relative
                poses.append((x, y, None if after is None else _wrap(after)))
                if before is not None and after is not None:
                    angles.append(abs(relative) if relative is not None and target is None else abs(_wrap(after - before)))
        complete = bool(poses) and len(angles) == len(poses)
        default = (max(angles) / self.settings["turn_rate_rad_s"],) * 2 + ("estimated",) if complete else None
        item, end = self.event(action, state, self.duration(action, default), poses or state.poses, label, group)
        if stationary:
            self.reservation(state, end, self.stationary_bounds(state.poses), "turn", item["index"], label,
                             item["timing_basis"], spatial_basis="stationary_body" if state.poses else "unknown_entire_field")
        return end

    def build(self) -> dict:
        actions = self.result.get("actions", [])
        if not isinstance(actions, list):
            raise ValueError("Imported actions must be a list")
        initial = _pose(self.result.get("start_pose"))
        if initial is None:
            candidates = actions
            while candidates and isinstance(candidates[0], dict) and candidates[0].get("kind") == "sequence":
                candidates = candidates[0].get("children", [])
            if candidates and isinstance(candidates[0], dict) and candidates[0].get("kind") == "move":
                path = _Path(candidates[0].get("path"))
                x, y = path.at(0)
                initial = x, y, path.heading(0, None)
                self.diagnostic("start_pose_inferred", "Initial position was inferred from the first declared path")
        poses = None if initial is None else [initial]
        if poses is None:
            self.diagnostic("start_pose_unknown", "Initial robot position is unresolved", unresolved=True)
        self.result["start_pose"] = _pose_json(initial)
        state = _State(0, 0, poses)
        delay = self.settings["start_delay_s"]
        if delay:
            state = self.action({"kind": "wait", "label": "Configured start delay",
                                 "duration": {"min_s": delay, "max_s": delay, "basis": "source"}},
                                state, "", None, True, 0)
        state = self.sequence(actions, state)
        if self.timing_only:
            return {"summary": {"duration_min_s": state.minimum, "duration_max_s": state.maximum},
                    "settings": self.settings}
        if state.minimum < self.settings["auto_duration_s"]:
            hold_start = state
            deadline = self.settings["auto_duration_s"]
            # Each branch's plausible endpoint stays occupied after the program,
            # rather than disappearing at the last path's predicted completion.
            end = _State(deadline, deadline, state.poses)
            item, _ = self.event({"kind": "hold", "label": "Terminal occupancy until AUTO end"}, state,
                                 (0 if state.maximum is None else max(0, deadline - state.maximum),
                                  max(0, deadline - state.minimum), "source"),
                                 state.poses, "", None)
            item["end_min_s"], item["end_max_s"] = deadline, deadline
            self.reservation(hold_start, end, self.stationary_bounds(state.poses), "hold", item["index"], "", "source",
                             spatial_basis="stationary_body" if state.poses else "unknown_entire_field")
        errors = any(isinstance(item, dict) and item.get("severity") == "error" for item in self.result["diagnostics"])
        importer_incomplete = self.result.get("status") in ("incomplete", "invalid") or self.result.get("complete") is False
        self.result.update({"schema_version": "1.0", "status": "invalid" if errors else
            "incomplete" if self.unresolved or importer_incomplete else "complete", "settings": self.settings,
            "timeline": self.timeline, "reservations": self.reservations,
            "summary": {"duration_min_s": state.minimum, "duration_max_s": state.maximum,
                "action_count": self.total_actions, "timeline_count": len(self.timeline),
                "reservation_count": len(self.reservations), "prediction_only": True,
                "possible_abort_count": self.abort_count,
                "corridor_bounds": _union_bounds([r["bounds"] for r in self.reservations]),
                "model_note": "Source actions retain their original waits and order; timeline times are proportionally "
                              "scaled when a bounded estimate exceeds AUTO duration. Drive times use supplied rest-to-rest estimates. "
                              "These reservations predict possible occupancy and do not establish observed clearance."}})
        return self.result


def build_profile(imported: dict, settings: dict | None = None) -> dict:
    """Return an exportable prediction profile; malformed input produces status=invalid.

    Clock ranges use elapsed AUTO seconds. A null maximum means unbounded/unknown,
    not zero. Finite overlong schedules are proportionally fitted to AUTO before
    generating occupancy; source actions retain their unscaled durations.
    Reservation window_* values are the uncertainty-expanded interval
    clipped to the configured AUTO horizon. Bounds contain the entire swept body
    between samples via subcurve control hulls and all-heading body inflation.
    """
    base = imported if isinstance(imported, dict) else {}
    try:
        if not isinstance(imported, dict) or settings is not None and not isinstance(settings, dict):
            raise ValueError("Imported data and settings must be objects")
        json.dumps(imported, allow_nan=False)
        json.dumps(settings or {}, allow_nan=False)
        # Determine the full completion bound before any reservations are clipped
        # to AUTO. Rebuild in the fitted clock so late paths and abort outcomes
        # remain present, and apply the configured uncertainty in actual seconds.
        estimate = _Builder(imported, settings or {}, timing_only=True).build()
        original = estimate["summary"]
        maximum = original["duration_max_s"]
        limit = estimate["settings"]["auto_duration_s"]
        factor = limit / maximum if maximum is not None and maximum > limit else 1.0
        result = _Builder(imported, settings or {}, time_scale_factor=factor).build()
        result["summary"].update(time_scale_factor=factor,
                                 unscaled_duration_min_s=original["duration_min_s"],
                                 unscaled_duration_max_s=maximum)
        if factor < 1:
            result["diagnostics"].append({"severity": "info", "code": "timeline_scaled",
                "message": f"Timeline scaled by {factor:.6g}: {maximum:.6g} s estimate fits {limit:g} s AUTO. "
                           "Paths, waits, start delay and timeout thresholds use the same factor; source actions retain original times."})
        json.dumps(result, allow_nan=False)
        return result
    except (ValueError, TypeError, KeyError, OverflowError, RecursionError) as failure:
        # Invalid numeric input must still yield valid exportable JSON. Preserve
        # its location as null, accompanied by an explicit invalid diagnostic.
        result = json.loads(json.dumps(base, default=lambda value: str(type(value).__name__)),
                            parse_constant=lambda value: None)
        diagnostics = result.get("diagnostics")
        if not isinstance(diagnostics, list):
            diagnostics = []
        diagnostics.append({"severity": "error", "code": "invalid_motion_model", "message": str(failure)})
        result.update({"schema_version": "1.0", "status": "invalid", "diagnostics": diagnostics,
                       "timeline": [], "reservations": [], "summary": {"prediction_only": True,
                       "duration_min_s": None, "duration_max_s": None, "action_count": 0,
                       "reservation_count": 0}})
        return result
