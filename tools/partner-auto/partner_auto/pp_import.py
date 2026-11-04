"""Read Pedro Visualizer .pp projects without executing any robot code.

The declaration tree fixes curve origins; ``sequence`` fixes execution order.
Unsupported data is retained as unknown work and reported, never given zero time.
Schema reference: Pedro-Pathing/Visualizer src/types.ts, utils/normalize.ts,
utils/pathTraversal.ts and model/curve.ts (inspected 2 November 2026).
"""
from __future__ import annotations

import copy
import math
from typing import Any


class _Invalid(ValueError):
    pass


def _number(value: Any, label: str, *, nonnegative: bool = False) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise _Invalid(f"{label} must be a finite number")
    result = float(value)
    if not math.isfinite(result) or (nonnegative and result < 0):
        raise _Invalid(f"{label} must be {'nonnegative and ' if nonnegative else ''}finite")
    return result


def _point(value: Any, label: str, scale: float) -> list[float]:
    if not isinstance(value, dict):
        raise _Invalid(f"{label} must contain x and y")
    point = [_number(value.get("x"), label + ".x") * scale,
             _number(value.get("y"), label + ".y") * scale]
    if not all(math.isfinite(v) for v in point):
        raise _Invalid(f"{label} overflows when converted to inches")
    return point


def _json_copy(value: Any, label: str, depth: int = 0) -> Any:
    if depth > 32:
        raise _Invalid(f"{label} exceeds the supported metadata depth")
    if isinstance(value, dict):
        if not all(isinstance(key, str) for key in value):
            raise _Invalid(f"{label} must have string keys")
        return {key: _json_copy(item, label + "." + key, depth + 1) for key, item in value.items()}
    if isinstance(value, list):
        return [_json_copy(item, label + "[]", depth + 1) for item in value]
    if value is None or isinstance(value, (str, bool)):
        return value
    return _number(value, label)


def _bezier_at(points: list[list[float]], fraction: float) -> list[float]:
    work = [p[:] for p in points]
    while len(work) > 1:
        work = [[a[0] + (b[0] - a[0]) * fraction,
                 a[1] + (b[1] - a[1]) * fraction] for a, b in zip(work, work[1:])]
    return work[0]


def _length(points: list[list[float]]) -> float:
    # This is used to divide a compound heading, not to establish travel timing.
    if len(points) == 2:
        value = math.dist(points[0], points[1])
        if not math.isfinite(value):
            raise _Invalid("Path length is nonfinite")
        return value
    previous = points[0]
    length = 0.0
    for i in range(1, 201):
        point = _bezier_at(points, i / 200)
        length += math.dist(point, previous)
        previous = point
    if not math.isfinite(length):
        raise _Invalid("Path length is nonfinite")
    return length


def _through_controls(points: list[list[float]]) -> list[list[float]]:
    """Fit the same single interpolating Bezier used by Pedro Paths.through."""
    degree = len(points) - 1
    if degree < 2:
        return copy.deepcopy(points)
    if degree > 16:
        raise _Invalid("throughPoints has more than 15 interior points; interpolation is not supported")
    rows = []
    for i, point in enumerate(points):
        t = i / degree
        rows.append([math.comb(degree, j) * t ** j * (1 - t) ** (degree - j)
                     for j in range(degree + 1)] + point[:])
    for col in range(degree + 1):
        pivot = max(range(col, degree + 1), key=lambda r: abs(rows[r][col]))
        rows[col], rows[pivot] = rows[pivot], rows[col]
        divisor = rows[col][col]
        if abs(divisor) < 1e-14:
            raise _Invalid("throughPoints interpolation is numerically singular")
        rows[col] = [v / divisor for v in rows[col]]
        for row in range(degree + 1):
            if row == col:
                continue
            factor = rows[row][col]
            rows[row] = [a - factor * b for a, b in zip(rows[row], rows[col])]
    result = [row[-2:] for row in rows]
    if not all(math.isfinite(v) for p in result for v in p):
        raise _Invalid("throughPoints interpolation produced nonfinite geometry")
    return result


def _bool(value: Any, label: str, default: bool = False) -> bool:
    if value is None:
        return default
    if not isinstance(value, bool):
        raise _Invalid(f"{label} must be boolean")
    return value


def _heading(value: Any, label: str, scale: float, legacy: dict | None = None) -> dict:
    legacy = legacy or {}
    if value is None:
        value = legacy.get("heading", "tangential")
    if isinstance(value, str):
        raw = dict(legacy)
        raw["type"] = value
    elif isinstance(value, dict):
        raw = value
    else:
        raise _Invalid(f"{label} must be a heading object or legacy heading string")
    kind = raw.get("type", "tangential")
    reverse = _bool(raw.get("reverse", raw.get("reversed")), label + ".reverse")
    if kind in ("tangential", "tangent"):
        return {"type": "tangent", "reverse": reverse}
    if kind == "constant":
        angle = math.radians(_number(raw.get("degrees"), label + ".degrees"))
        return {"type": kind, "start_rad": angle, "end_rad": angle, "reverse": reverse}
    if kind == "linear":
        return {"type": kind,
                "start_rad": math.radians(_number(raw.get("startDeg"), label + ".startDeg")),
                "end_rad": math.radians(_number(raw.get("endDeg"), label + ".endDeg")),
                "reverse": reverse}
    if kind == "facing-point":
        return {"type": kind, "point": _point(raw.get("point"), label + ".point", scale),
                "reverse": reverse}
    if kind != "piecewise":
        raise _Invalid(f"{label} has unsupported heading type {kind!r}")
    segments = raw.get("piecewiseHeading", {}).get("segments")
    if not isinstance(segments, list) or not segments:
        raise _Invalid(f"{label}.piecewiseHeading.segments must be a nonempty list")
    if len(segments) > 256:
        raise _Invalid(f"{label} has too many heading segments")
    normalized = []
    cursor = 0.0
    for index, segment in enumerate(segments):
        where = f"{label}.segments[{index}]"
        if not isinstance(segment, dict):
            raise _Invalid(f"{where} must be an object")
        start = _number(segment.get("startProgress"), where + ".startProgress")
        end = _number(segment.get("endProgress"), where + ".endProgress")
        if abs(start - cursor) > 1e-9 or not start < end <= 1:
            raise _Invalid(f"{where} must be ordered, contiguous and within [0, 1]")
        parameters = segment.get("parameters", {})
        if not isinstance(parameters, dict):
            raise _Invalid(f"{where}.parameters must be an object")
        inner = dict(parameters)
        inner["type"] = segment.get("interpolationType")
        inner["reverse"] = segment.get("reversed", False)
        rule = _heading(inner, where, scale)
        if rule["type"] == "piecewise":
            raise _Invalid(f"{where} cannot nest piecewise headings")
        rule.update(start_progress=start, end_progress=end,
                    continue_from_previous=_bool(segment.get("continueFromPrevious"),
                                                  where + ".continueFromPrevious"))
        normalized.append(rule)
        cursor = end
    if abs(cursor - 1) > 1e-9:
        raise _Invalid(f"{label} final heading segment must end at 1")
    return {"type": kind, "segments": normalized}


class _Reader:
    def __init__(self, data: Any, name: str):
        self.data = data
        self.name = name
        self.diagnostics: list[dict] = []
        self.actions: list[dict] = []
        self.leaves: list[dict] = []
        self.by_id: dict[str, dict] = {}
        self.duplicates: set[str] = set()
        self.scale = 1.0
        self.wait_notice = False
        self.nodes = 0
        self.dependent_path_count = 0

    def source(self, where: str) -> dict:
        return {"file": self.name, "location": where}

    def issue(self, code: str, message: str, where: str = "$", severity: str = "error") -> None:
        self.diagnostics.append({"severity": severity, "code": code, "message": message,
                                 "source": self.source(where)})

    def unknown(self, code: str, message: str, where: str, *, report: bool = True) -> dict:
        if report:
            self.issue(code, message, where)
        return {"kind": "unknown", "detail": message, "source": self.source(where),
                "diagnostic_reported": True,
                "duration": {"min_s": 0.0, "max_s": None, "basis": "unknown"}}

    def wait(self, raw: dict, position: str, where: str) -> dict | None:
        key = "wait" + position.capitalize()
        nested = raw.get(key)
        if nested is not None and not isinstance(nested, dict):
            raise _Invalid(f"{where}.{key} must be an object")
        nested = nested or {}
        value = raw.get(key + "Ms", nested.get("durationMs", 0))
        seconds = _number(value, where + "." + key + "Ms", nonnegative=True) / 1000
        if seconds == 0:
            return None
        if not self.wait_notice:
            self.issue("PP_PATH_WAIT", "Declared path waits are preserved. Some Pedro versions use them "
                       "for visualization only; confirm the partner's robot actually performs these waits.",
                       where, "warning")
            self.wait_notice = True
        return {"kind": "wait", "name": raw.get(key + "Name", nested.get("name", key)),
                "source": self.source(where + "." + key),
                "duration": {"min_s": seconds, "max_s": seconds, "basis": "source"}}

    def walk(self, paths: list, start: list[float] | None, ancestors: list, where: str,
             depth: int = 0) -> list[float] | None:
        if depth > 32:
            raise _Invalid("compound paths exceed the supported nesting depth of 32")
        current = start
        for index, raw in enumerate(paths):
            self.nodes += 1
            if self.nodes > 10000:
                raise _Invalid("project contains more than 10000 paths")
            location = f"{where}[{index}]"
            if not isinstance(raw, dict):
                self.issue("INVALID_PATH", f"{location} must be an object", location)
                current = None
                continue
            path_id = raw.get("id", f"legacy-{self.nodes}")
            if not isinstance(path_id, str) or not path_id:
                self.issue("INVALID_PATH_ID", "Path IDs must be nonempty strings", location)
                path_id = f"invalid-{self.nodes}"
            record = {"id": path_id, "raw": raw, "where": location, "ancestors": ancestors[:],
                      "leaf_start": len(self.leaves), "before": None, "after": None}
            if path_id in self.by_id:
                self.duplicates.add(path_id)
                self.issue("DUPLICATE_PATH_ID", f"Path ID {path_id!r} is duplicated", location)
            else:
                self.by_id[path_id] = record
            try:
                record["before"] = self.wait(raw, "before", location)
                record["after"] = self.wait(raw, "after", location)
                kind = raw.get("kind", "compound" if "segments" in raw else "atomic")
                if kind == "compound":
                    children = raw.get("segments")
                    if not isinstance(children, list) or not children:
                        raise _Invalid(f"{location}.segments must be a nonempty list")
                    record["kind"] = "compound"
                    record["heading"] = (_heading(raw["heading"], location + ".heading", self.scale)
                                             if raw.get("heading") is not None else None)
                    current = self.walk(children, current, ancestors + [record], location + ".segments", depth + 1)
                    record["leaf_end"] = len(self.leaves)
                    continue
                if kind != "atomic":
                    raise _Invalid(f"Unsupported path kind {kind!r}")
                end = _point(raw.get("endPoint"), location + ".endPoint", self.scale)
                controls = raw.get("controlPoints", [])
                through = raw.get("throughPoints", [])
                if not isinstance(controls, list) or not isinstance(through, list):
                    raise _Invalid("controlPoints and throughPoints must be lists")
                if len(controls) > 50:
                    raise _Invalid("Bezier path has more than 50 control points")
                if len(through) > 15:
                    raise _Invalid("throughPoints has more than 15 interior points; interpolation is not supported")
                # Validate independent fields even if an earlier error lost the origin.
                # Otherwise one bad path would hide a later, unrelated schema error.
                control_points = ([] if through else
                                  [_point(p, location + ".controlPoints", self.scale) for p in controls])
                through_points = [_point(p, location + ".throughPoints", self.scale) for p in through]
                heading = _heading(raw.get("heading"), location + ".heading", self.scale,
                                   raw.get("endPoint"))
                if current is None:
                    record.update(kind="invalid", leaf_end=len(self.leaves),
                                  error="This path's declared origin is unknown because an earlier path is invalid")
                    self.leaves.append(record)
                    self.dependent_path_count += 1
                    continue
                if through and controls:
                    self.issue("THROUGH_OVERRIDES_CONTROLS", "throughPoints replaces controlPoints in Pedro; "
                               "the interpolating curve is retained.", location, "warning")
                points = ([current] + through_points + [end]
                          if through else [current] + control_points + [end])
                if through:
                    points = _through_controls(points)
                record.update(kind="atomic", points=points,
                              heading=heading, length=_length(points))
                self.leaves.append(record)
                record["leaf_end"] = len(self.leaves)
                current = end
            except (_Invalid, TypeError, KeyError, AttributeError, OverflowError) as error:
                record.update(kind="invalid", error=str(error), leaf_end=len(self.leaves))
                self.issue("INVALID_PATH", str(error), location)
                # Keep invalid work in the declaration order so it cannot disappear in default playback.
                self.leaves.append(record)
                current = None
        return current

    def resolved_heading(self, leaf: dict) -> dict:
        heading = copy.deepcopy(leaf["heading"])
        override = next((p for p in leaf["ancestors"] if p.get("heading") is not None), None)
        if override is None:
            return heading
        heading = copy.deepcopy(override["heading"])
        members = self.leaves[override["leaf_start"]:override["leaf_end"]]
        total = sum(p.get("length", 0) for p in members)
        distance = 0.0
        for member in members:
            if member is leaf:
                break
            distance += member.get("length", 0)
        low = distance / total if total > 1e-12 else 0
        high = (distance + leaf["length"]) / total if total > 1e-12 else 0
        if heading["type"] == "linear":
            first = heading["start_rad"]
            delta = math.remainder(heading["end_rad"] - first, math.tau)
            heading.update(start_rad=first + delta * low, end_rad=first + delta * high)
        elif heading["type"] == "piecewise":
            heading.update(progress_start=low, progress_end=high)
        return heading

    def emit_leaf(self, leaf: dict, current: list[float] | None) -> list[float] | None:
        where = leaf["where"]
        if leaf.get("kind") != "atomic":
            # walk() already reports the original schema error and summarizes paths
            # affected by its missing origin. Keep the work without repeating errors.
            self.actions.append(self.unknown("UNRESOLVED_PATH", leaf.get("error", "Invalid path"), where,
                                             report=False))
            return None
        for group in leaf["ancestors"]:
            members = self.leaves[group["leaf_start"]:group["leaf_end"]]
            if members and members[0] is leaf and group.get("before"):
                self.actions.append(copy.deepcopy(group["before"]))
        if leaf.get("before"):
            self.actions.append(copy.deepcopy(leaf["before"]))
        start = leaf["points"][0]
        if current is None or math.dist(current, start) > 1e-6:
            self.issue("DISCONTINUOUS_SEQUENCE", f"Path {leaf['id']!r} starts at its declared origin {start}; "
                       "the previous executed action does not end there. No connector is invented.", where)
        action = {"kind": "move", "name": leaf["raw"].get("name", leaf["id"]),
                  "source": self.source(where), "path_id": leaf["id"],
                  "path": {"kind": "bezier", "points": copy.deepcopy(leaf["points"]),
                           "heading": self.resolved_heading(leaf)}}
        self.actions.append(action)
        if leaf.get("after"):
            self.actions.append(copy.deepcopy(leaf["after"]))
        for group in reversed(leaf["ancestors"]):
            members = self.leaves[group["leaf_start"]:group["leaf_end"]]
            if members and members[-1] is leaf and group.get("after"):
                self.actions.append(copy.deepcopy(group["after"]))
        return leaf["points"][-1][:]

    def run(self) -> dict:
        output = {"format": "pp", "entries": [], "selected_entry": None, "start_pose": None,
                  "actions": self.actions, "diagnostics": self.diagnostics, "metadata": {}}
        try:
            if not isinstance(self.data, dict):
                raise _Invalid("A .pp project must be a JSON object")
            data = self.data
            settings = data.get("settings", {})
            robot = data.get("robot", {})
            if not isinstance(settings, dict) or not isinstance(robot, dict):
                raise _Invalid("settings and robot must be objects")
            units = data.get("units", settings.get("units", "inches"))
            if not isinstance(units, str):
                raise _Invalid("units must be inches, cm, mm or meters")
            scales = {"in": 1, "inch": 1, "inches": 1, "cm": 1 / 2.54,
                      "centimeters": 1 / 2.54, "mm": 1 / 25.4, "millimeters": 1 / 25.4,
                      "m": 100 / 2.54, "meter": 100 / 2.54, "meters": 100 / 2.54}
            if units.lower() not in scales:
                raise _Invalid(f"Unsupported coordinate units {units!r}; no scale is guessed")
            self.scale = scales[units.lower()]
            frame = data.get("coordinateSystem", settings.get("coordinateSystem", "pedro"))
            if not isinstance(frame, str) or frame.lower() not in ("pedro", "field", "cartesian"):
                raise _Invalid(f"Unsupported coordinateSystem {frame!r}; explicitly convert to Pedro coordinates")
            output["metadata"] = {"version": data.get("version", "legacy"), "source_units": units,
                                  "units": "inches", "coordinate_system": frame,
                                  "settings": _json_copy(settings, "settings"),
                                  "robot": _json_copy(robot, "robot"),
                                  "unit_scale_to_inches": self.scale}
            if data.get("version") and not str(data["version"]).startswith("1."):
                self.issue("UNVERIFIED_PP_VERSION", f"Project version {data['version']!r} is not a verified 1.x schema; "
                           "recognized fields are still imported.", "version", "warning")
            start_raw = data.get("startPoint")
            start = _point(start_raw, "startPoint", self.scale)
            if "headingDeg" in start_raw:
                angle = math.radians(_number(start_raw["headingDeg"], "startPoint.headingDeg"))
            elif start_raw.get("heading") == "linear":
                angle = math.radians(_number(start_raw.get("startDeg"), "startPoint.startDeg"))
            elif start_raw.get("heading") == "constant":
                angle = math.radians(_number(start_raw.get("degrees"), "startPoint.degrees"))
            else:
                angle = 0.0
                self.issue("START_HEADING_DEFAULT", "No numeric initial heading was stored; Pedro's legacy default "
                           "of zero is retained. Confirm the initial heading.", "startPoint", "warning")
            output["start_pose"] = {"x": start[0], "y": start[1], "heading_rad": angle}
            paths = data.get("lines")
            if not isinstance(paths, list):
                raise _Invalid("lines must be a list of Pedro paths")
            self.walk(paths, start, [], "lines")
            if self.dependent_path_count:
                self.issue("DEPENDENT_PATH_ORIGINS",
                           f"{self.dependent_path_count} subsequent paths have unresolved origins because an earlier "
                           "path is invalid. Their actions remain unknown; fix the original path error first.",
                           "lines", "info")
            sequence = data.get("sequence")
            if sequence is not None and not isinstance(sequence, list):
                raise _Invalid("sequence must be a list when present")
            if not sequence:
                sequence = [{"kind": "path", "lineId": record["id"]} for record in self.leaves]
            if not sequence:
                raise _Invalid("The project has no executable paths or waits")
            if len(sequence) > 20000:
                raise _Invalid("sequence has more than 20000 actions")
            current = start
            for index, item in enumerate(sequence):
                where = f"sequence[{index}]"
                if isinstance(item, str):
                    self.issue("LEGACY_SEQUENCE_REFERENCE", "A string sequence item was interpreted as a path ID.",
                               where, "warning")
                    item = {"kind": "path", "lineId": item}
                if not isinstance(item, dict):
                    self.actions.append(self.unknown("INVALID_SEQUENCE_ITEM", "Sequence items must be objects", where))
                    current = None
                    continue
                kind = item.get("kind")
                if kind == "path":
                    path_id = item.get("lineId")
                    if not isinstance(path_id, str) or path_id not in self.by_id or path_id in self.duplicates:
                        self.actions.append(self.unknown("UNRESOLVED_PATH_REFERENCE", f"Cannot uniquely resolve path "
                                                         f"reference {path_id!r}", where))
                        current = None
                        continue
                    record = self.by_id[path_id]
                    members = (self.leaves[record["leaf_start"]:record["leaf_end"]]
                               if record["kind"] == "compound" else [record])
                    for member in members:
                        current = self.emit_leaf(member, current)
                elif kind in ("wait", "shoot"):
                    try:
                        if "durationMs" in item:
                            seconds = _number(item["durationMs"], where + ".durationMs", nonnegative=True) / 1000
                        elif "durationS" in item:
                            seconds = _number(item["durationS"], where + ".durationS", nonnegative=True)
                        else:
                            raise _Invalid(f"{kind} action is missing an explicit durationMs or durationS")
                        self.actions.append({"kind": kind, "name": item.get("name", kind),
                                             "source": self.source(where),
                                             "duration": {"min_s": seconds, "max_s": seconds, "basis": "source"}})
                        if kind == "shoot":
                            self.issue("PP_SHOOT_EXTENSION", "Explicit shoot action retained; this is a partner-profile "
                                       "extension rather than the standard Pedro sequence schema.", where, "info")
                    except _Invalid as error:
                        self.actions.append(self.unknown("INVALID_ACTION_DURATION", str(error), where))
                else:
                    self.actions.append(self.unknown("UNSUPPORTED_SEQUENCE_ACTION", f"Unsupported sequence action "
                                                     f"{kind!r}; its duration and motion are unknown", where))
                    current = None
        except (_Invalid, TypeError, KeyError, AttributeError, OverflowError, RecursionError) as error:
            self.actions.append(self.unknown("INVALID_PP_PROJECT", str(error), "$"))
        return output


def import_pp(data: dict, source_name: str = "<input>") -> dict:
    """Return normalized source-linked actions and explicit diagnostics for a .pp object."""
    return _Reader(data, source_name).run()
