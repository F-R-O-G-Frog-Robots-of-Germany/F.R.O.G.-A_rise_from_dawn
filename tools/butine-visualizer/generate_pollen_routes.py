#!/usr/bin/env python3
"""Generate 40 pollen, plan the 20 RED-side targets in production Java, and save Pedro .pp JSON."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import random
import shutil
import subprocess
import sys
import tempfile


PROJECT_ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = PROJECT_ROOT / "TeamCode/src/main/java/org/firstinspires/ftc/teamcode"
JAVA_PACKAGE = JAVA_ROOT / "limelight"
BRIDGE_SOURCE = Path(__file__).with_name("ButineVisualizerBridge.java")
BRIDGE_CLASS = "org.firstinspires.ftc.teamcode.tools.ButineVisualizerBridge"
POLLEN_PER_SIDE = 20
POLLEN_RADIUS_IN = 1.4


def run_process(arguments: list[str], timeout: float = 120) -> str:
    result = subprocess.run(arguments, cwd=PROJECT_ROOT, shell=False, capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"{Path(arguments[0]).name} failed ({result.returncode}):\n{detail}")
    return result.stdout


def find_java_tool(name: str, supplied: str | None) -> str:
    if supplied:
        candidate = Path(supplied).expanduser().resolve()
        if not candidate.is_file():
            raise ValueError(f"{name} executable does not exist: {candidate}")
        return str(candidate)
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = Path(java_home) / "bin" / (name + (".exe" if os.name == "nt" else ""))
        if candidate.is_file():
            return str(candidate)
    executable = shutil.which(name)
    if not executable:
        raise RuntimeError(f"Cannot find {name}. Install a JDK and set JAVA_HOME or put its bin directory on PATH.")
    return executable


def find_pedro_jar(supplied: Path | None) -> Path:
    if supplied:
        candidate = supplied.expanduser().resolve()
        if not candidate.is_file():
            raise ValueError(f"Pedro JAR does not exist: {candidate}")
        return candidate
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    cached = gradle_home / "caches/modules-2/files-2.1/com.pedropathing/core/3.0.1"
    matches = sorted(cached.glob("*/core-3.0.1.jar"))
    if not matches:
        raise RuntimeError("Pedro core 3.0.1 is not cached. Sync the Android project once or pass --pedro-jar PATH.")
    return matches[0]


def compile_bridge(javac: str, jar: Path) -> Path:
    sources = [JAVA_PACKAGE / name for name in ("Butine.java", "ButineField.java", "ButineIntake.java", "Pollen.java", "VisionPose.java")]
    sources.extend((JAVA_ROOT / "core/hardware/RobotDimensions.java", BRIDGE_SOURCE))
    fingerprint = hashlib.sha256()
    fingerprint.update(str(jar).encode("utf-8"))
    fingerprint.update(jar.read_bytes())
    for source in sources:
        if not source.is_file():
            raise ValueError(f"Required Java source missing: {source}")
        fingerprint.update(source.read_bytes())
    classes = PROJECT_ROOT / "build/butine-visualizer/java-cache" / fingerprint.hexdigest()[:20] / "classes"
    complete = classes.parent / "compiled.ok"
    if not complete.is_file() or not (classes / (BRIDGE_CLASS.replace(".", "/") + ".class")).is_file():
        classes.mkdir(parents=True, exist_ok=True)
        run_process([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-proc:none",
                     "-classpath", str(jar), "-d", str(classes), *map(str, sources)])
        complete.write_text("Compiled from the current production sources.\n", encoding="utf-8")
    return classes


def invoke_bridge(java: str, classes: Path, jar: Path, argument: str) -> dict:
    raw = run_process([java, "-Dfile.encoding=UTF-8", "-classpath", os.pathsep.join((str(classes), str(jar))),
                       BRIDGE_CLASS, argument])
    result = json.loads(raw)
    if not isinstance(result, dict):
        raise ValueError("Java bridge returned something other than a JSON object")
    return result


def clear_point(x: float, y: float, geometry: dict) -> bool:
    radius = geometry["config"]["radiusIn"]
    far = geometry["config"]["fieldSizeIn"] - radius
    if not radius < x < far or not radius < y < far:
        return False
    for obstacle in geometry["obstacles"]:
        box = obstacle["exclusion"]
        if box["minX"] - 1e-9 <= x <= box["maxX"] + 1e-9 and box["minY"] - 1e-9 <= y <= box["maxY"] + 1e-9:
            return False
    return True


def partner_corridor(profile: dict, field_size: float, box_budget: int) -> list[tuple[float, float, float, float]]:
    """Cover the complete partner corridor with boxes the Java router can handle."""
    if not isinstance(profile, dict) or profile.get("schema_version") != "1.0" or profile.get("status") != "complete":
        raise ValueError("Butine needs a complete partner profile from the importer.")
    settings = profile.get("settings")
    if not isinstance(settings, dict) or settings.get("field_size_in") != field_size:
        raise ValueError(f"The partner profile must use the {field_size:g}-inch field.")
    reservations = profile.get("reservations")
    if not isinstance(reservations, list) or not reservations:
        raise ValueError("The partner profile has no occupancy reservations.")

    bounds = []
    for reservation in reservations:
        box = reservation.get("bounds") if isinstance(reservation, dict) else None
        values = [box.get(key) for key in ("min_x", "max_x", "min_y", "max_y")] if isinstance(box, dict) else []
        if len(values) != 4 or not all(type(value) in (int, float) and abs(value) <= sys.float_info.max for value in values):
            raise ValueError("The partner profile contains invalid occupancy bounds.")
        left, right, bottom, top = values
        if left >= right or bottom >= top:
            raise ValueError("The partner profile contains empty or reversed occupancy bounds.")
        if right > 0 and left < field_size and top > 0 and bottom < field_size:
            bounds.append((left, right, bottom, top))
    if not bounds:
        raise ValueError("The partner profile has no occupancy inside the field.")

    for divisions in (24, 12, 6, 1):
        step = field_size / divisions
        occupied = [set() for _ in range(divisions)]
        for left, right, bottom, top in bounds:
            low_x = max(0, math.floor(left / step))
            high_x = min(divisions, math.ceil(right / step))
            columns = range(low_x, high_x)
            for y in range(max(0, math.floor(bottom / step)), min(divisions, math.ceil(top / step))):
                occupied[y].update(columns)

        # Join adjacent rows with identical occupied spans while keeping gaps open.
        boxes, open_spans = [], {}
        for y, row in enumerate(occupied + [set()]):
            spans = {}
            for x in range(divisions):
                if x in row and x - 1 not in row:
                    end = x + 1
                    while end in row:
                        end += 1
                    spans[x, end] = open_spans.get((x, end), y)
            for (start, end), first_row in open_spans.items():
                if (start, end) not in spans:
                    boxes.append((start * step, end * step, first_row * step, y * step))
            open_spans = spans
        if len(boxes) <= box_budget:
            return boxes
    raise ValueError("The Butine field has no room for partner obstacles.")


def generate_map(seed: int, geometry: dict) -> list[dict]:
    generator = random.Random(seed)
    size = geometry["config"]["fieldSizeIn"]
    radius = geometry["config"]["radiusIn"]
    points: list[dict] = []
    for side in ("RED", "BLUE"):
        lo = radius + .05 if side == "RED" else size / 2 + .05
        hi = size / 2 - .05 if side == "RED" else size - radius - .05
        generated = 0
        for _ in range(20000):
            if generated == POLLEN_PER_SIDE:
                break
            x, y = round(generator.uniform(lo, hi), 6), round(generator.uniform(radius + .05, size - radius - .05), 6)
            if not clear_point(x, y, geometry):
                continue
            if any(math.hypot(x - point["x"], y - point["y"]) < 2 * POLLEN_RADIUS_IN + .2 for point in points):
                continue
            points.append({"id": len(points) + 1, "x": x, "y": y, "allianceSide": side})
            generated += 1
        if generated != POLLEN_PER_SIDE:
            raise RuntimeError(f"Could not place 20 non-overlapping {side} pollen in the clear field")
    return points


def route_color(index: int, count: int) -> str:
    """Trip order: white, through bright green, to black; every leg in one trip shares its color."""
    position = index / (count - 1) if count > 1 else 0
    if position <= .5:
        channel = round(255 * (1 - 2 * position))
        rgb = (channel, 255, channel)
    else:
        rgb = (0, round(255 * (2 - 2 * position)), 0)
    return "#" + "".join(f"{channel:02x}" for channel in rgb)


def polygon_marker(point: dict) -> dict:
    vertices = [{"x": point["x"] + POLLEN_RADIUS_IN * math.cos(2 * math.pi * step / 16),
                 "y": point["y"] + POLLEN_RADIUS_IN * math.sin(2 * math.pi * step / 16)} for step in range(16)]
    return {"id": f"pollen-{point['id']:02d}", "name": f"P{point['id']:02d} RED pollen (display marker)",
            "vertices": vertices, "color": "#eab308", "fillColor": "#eab308", "type": "obstacle",
            "visible": True, "locked": True}


def make_project(plan: dict, eligible: list[dict], seed: int) -> dict:
    lines, sequence = [], []
    trips = plan["runs"]
    for trip_index, trip in enumerate(trips):
        color = route_color(trip_index, len(trips))
        pickup_index = 0
        for leg_index, leg in enumerate(trip["legs"]):
            kind, target, origin = leg["kind"], leg["target"], leg["from"]
            line_id = f"run-{trip['runId']:02d}-leg-{leg_index + 1:03d}"
            if leg["pollenIds"]:
                pickup_index += len(leg["pollenIds"])
                group = " + ".join(f"P{pollen_id:02d}" for pollen_id in leg["pollenIds"])
                label = f"intake ON · {group} ({pickup_index}/{trip['pickupCount']})"
            else:
                label = ("intake ON · transit" if leg["intakeOn"] else
                         "intake OFF · depot" if kind == "DEPOT" else
                         "intake OFF · turn at depot" if kind == "ALIGNMENT" else "intake OFF · return")
            start_deg, end_deg = math.degrees(origin["heading"]), math.degrees(target["heading"])
            mode = "constant" if leg["intakeOn"] else "linear"
            endpoint = {"x": target["x"], "y": target["y"], "heading": mode,
                        "startDeg": start_deg, "endDeg": end_deg, "degrees": end_deg}
            heading = {"type": mode, "reverse": False}
            if mode == "linear":
                heading.update(startDeg=start_deg, endDeg=end_deg)
            else:
                heading["degrees"] = end_deg
            lines.append({"id": line_id, "name": f"R{trip['runId']:02d} · {label}", "kind": "atomic",
                          "endPoint": endpoint, "controlPoints": [], "heading": heading, "color": color,
                          "locked": False, "waitBeforeMs": 0,
                          "waitAfterMs": round(trip["scoreOverheadS"] * 1000) if kind == "DEPOT" else 0,
                          "butine": {"intakeOn": leg["intakeOn"], "collectedPollenIds": leg["pollenIds"],
                                     "contactFractions": leg["contactFractions"],
                                     "startSpeedInS": leg["startSpeedInS"], "endSpeedInS": leg["endSpeedInS"]}})
            sequence.append({"kind": "path", "lineId": line_id})
    shapes = [polygon_marker(point) for point in eligible]
    for obstacle in plan["obstacles"]:
        box = obstacle["physical"]
        partner = obstacle.get("partner", False)
        label = "Partner corridor" if partner else "Physical field obstacle"
        color = "#d97706" if partner else "#64748b"
        shapes.append({"id": f"field-obstacle-{obstacle['index']}", "name": f"{label} {obstacle['index'] + 1}",
                       "vertices": [{"x": box["minX"], "y": box["minY"]}, {"x": box["maxX"], "y": box["minY"]},
                                    {"x": box["maxX"], "y": box["maxY"]}, {"x": box["minX"], "y": box["maxY"]}],
                       "color": color, "fillColor": color, "type": "obstacle", "visible": True, "locked": True})
    config = plan["config"]
    start = plan["start"]
    start_deg = math.degrees(start["heading"])
    return {"version": "1.5.0", "startPoint": {"x": start["x"], "y": start["y"], "headingDeg": start_deg,
            "heading": "constant", "degrees": start_deg, "locked": False},
            "lines": lines, "sequence": sequence, "shapes": shapes, "fieldPoints": [], "activePaths": [],
            "settings": {"fieldWidth": 144, "fieldHeight": 144, "fieldMap": "biobuzz.webp",
                         "rLength": config["robotLengthIn"], "rWidth": config["robotWidthIn"],
                         "rHeight": 18, "safetyMargin": 1, "pathOpacity": 1,
                         "showGhostPaths": False, "showOnionLayers": False,
                         "experimentalFeatures": {"obstacles": True}},
            "butineVisualization": {"seed": seed, "alliance": "RED", "sideCondition": "x < 72",
                                     "generatedPollen": 40, "displayedPollen": 20, "possessionLimit": 4,
                                     "pollenShapesAreDisplayMarkersOnly": True, "routeGradient": "white-green-black",
                                     "routeCount": len(trips), "virtualBudgetSeconds": config["remainingSeconds"],
                                     "robotCenterForwardIn": config["robotCenterForwardIn"],
                                     "intakeWidthIn": config["intakeWidthIn"],
                                     "intakeForwardOffsetIn": config["intakeForwardOffsetIn"],
                                     "intakeOvershootIn": config["intakeOvershootIn"],
                                     "pollenRadiusIn": config["pollenRadiusIn"],
                                     "collectionModel": "front intake sweep at constant robot heading",
                                     "continuousCollection": True}}


def validate_plan(plan: dict, eligible: list[dict]) -> None:
    if plan["config"]["capacity"] != 4 or plan["config"]["productionDefaultCapacity"] != 4:
        raise ValueError("The Java production possession limit must be four")
    if not plan["diagnostics"]["allSegmentsClear"] or not plan["diagnostics"]["legTimingMatches"]:
        raise ValueError("Java did not certify segment clearance and routed timing")
    available, seen = {point["id"] for point in eligible}, set()
    previous = plan["start"]
    for trip in plan["runs"]:
        if not 1 <= trip["pickupCount"] <= 4 or len(trip["pickupIds"]) != trip["pickupCount"]:
            raise ValueError("Exported collection exceeds the possession limit")
        for pollen_id in trip["pickupIds"]:
            if pollen_id not in available or pollen_id in seen:
                raise ValueError("Java selected a duplicate or non-RED pollen ID")
            seen.add(pollen_id)
        contacts, returning, collection_started = [], False, False
        for leg in trip["legs"]:
            if math.hypot(previous["x"] - leg["from"]["x"], previous["y"] - leg["from"]["y"]) > 1e-7:
                raise ValueError("Exported path sequence is discontinuous")
            if not leg["intakeOn"] and collection_started:
                returning = True
            if returning and leg["intakeOn"]:
                raise ValueError("The intake must stay off on the return to depot")
            collection_started |= leg["intakeOn"]
            if len(leg["pollenIds"]) != len(leg["contactFractions"]) or (leg["pollenIds"] and not leg["intakeOn"]):
                raise ValueError("Invalid intake contact group")
            if any(not math.isfinite(fraction) or not 0 <= fraction <= 1 for fraction in leg["contactFractions"]):
                raise ValueError("Intake contact fractions must be in the segment")
            if leg["intakeOn"] and abs(math.remainder(leg["target"]["heading"] - trip["collectionHeading"], math.tau)) > 1e-7:
                raise ValueError("Collection requires a constant robot heading")
            contacts.extend(leg["pollenIds"])
            previous = leg["target"]
        if contacts != trip["pickupIds"]:
            raise ValueError("Grouped intake contacts differ from the run's pickup order")
        if not trip["legs"] or trip["legs"][-1]["kind"] != "DEPOT" or trip["legs"][-1]["intakeOn"]:
            raise ValueError("Collection must end at the depot with the intake off")
        final_collection = [leg for leg in trip["legs"] if leg["intakeOn"]][-1]
        if not final_collection["contactFractions"]:
            raise ValueError("Final collection segment must include the final intake contact")
        final_fraction = final_collection["contactFractions"][-1]
        length = math.hypot(final_collection["target"]["x"] - final_collection["from"]["x"],
                            final_collection["target"]["y"] - final_collection["from"]["y"])
        if (not math.isclose(trip["intakeOvershootIn"], plan["config"]["intakeOvershootIn"], abs_tol=1e-7) or
                not math.isclose((1 - final_fraction) * length, trip["intakeOvershootIn"], abs_tol=1e-6)):
            raise ValueError("Collection must continue the configured distance past its final intake contact")
        if not math.isclose(sum(leg["predictedS"] for leg in trip["legs"]), trip["travelTimeS"], abs_tol=1e-7):
            raise ValueError("Exported segment times do not match the production sweep route")
    if seen | set(plan["uncollectedIds"]) != available or seen & set(plan["uncollectedIds"]):
        raise ValueError("Collected and uncollected IDs do not partition the RED map")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, help="Repeatable nonnegative Java-int seed; omitted means a new random map.")
    parser.add_argument("--output", type=Path, default=PROJECT_ROOT / "build/butine-visualizer/red-pollen.pp")
    parser.add_argument("--seconds", type=float, default=150, help="Virtual planning budget; default full-match 150 seconds, use 30 for autonomous.")
    parser.add_argument("--score-seconds", type=float, default=2, help="Scoring time deducted after every depot return.")
    parser.add_argument("--intake-width", type=float,
                        help="Active front intake width in inches (default: production Java setting).")
    parser.add_argument("--intake-forward-offset", type=float,
                        help="Distance from chassis center to front intake in inches (default: production Java setting).")
    parser.add_argument("--intake-overshoot", type=float,
                        help="Continue collection this many inches beyond the last intake contact (default: production Java setting, 1 inch).")
    parser.add_argument("--depot", nargs=3, type=float, default=(59.25, 18, 90), metavar=("X", "Y", "DEGREES"))
    parser.add_argument("--start", nargs=3, type=float, metavar=("X", "Y", "DEGREES"), help="Defaults to the depot.")
    parser.add_argument("--partner-profile", type=Path, help="Reserve the corridor from a complete partner-auto JSON profile.")
    parser.add_argument("--pedro-jar", type=Path, help="Override the project's cached Pedro core 3.0.1 JAR.")
    parser.add_argument("--java", help="Java executable override.")
    parser.add_argument("--javac", help="Java compiler executable override.")
    args = parser.parse_args(argv)
    try:
        if args.seed is not None and not 0 <= args.seed < 2 ** 31:
            raise ValueError("--seed must be between 0 and 2147483647")
        if not math.isfinite(args.seconds) or args.seconds < 0 or not math.isfinite(args.score_seconds) or args.score_seconds < 0:
            raise ValueError("Time budgets must be finite and nonnegative")
        if args.intake_width is not None and (not math.isfinite(args.intake_width) or args.intake_width <= 0):
            raise ValueError("--intake-width must be finite and positive")
        if args.intake_forward_offset is not None and (not math.isfinite(args.intake_forward_offset) or args.intake_forward_offset < 0):
            raise ValueError("--intake-forward-offset must be finite and nonnegative")
        if args.intake_overshoot is not None and (not math.isfinite(args.intake_overshoot) or args.intake_overshoot < 0):
            raise ValueError("--intake-overshoot must be finite and nonnegative")
        depot, start = tuple(args.depot), tuple(args.start or args.depot)
        if not all(math.isfinite(value) for value in (*start, *depot)) or start[0] >= 72 or depot[0] >= 72:
            raise ValueError("Start/depot must be finite RED-side poses (x < 72)")
        seed = args.seed if args.seed is not None else random.SystemRandom().randrange(2 ** 31)
        java, javac = find_java_tool("java", args.java), find_java_tool("javac", args.javac)
        jar = find_pedro_jar(args.pedro_jar)
        classes = compile_bridge(javac, jar)
        geometry = invoke_bridge(java, classes, jar, "--describe-field")
        boxes = []
        if args.partner_profile is not None:
            profile = json.loads(args.partner_profile.read_text(encoding="utf-8-sig"))
            boxes = partner_corridor(profile, geometry["config"]["fieldSizeIn"],
                                     geometry["config"]["maxBoxes"] - len(geometry["obstacles"]))
        defaults = geometry["config"]
        intake_width = defaults["intakeWidthIn"] if args.intake_width is None else args.intake_width
        intake_forward_offset = (defaults["intakeForwardOffsetIn"] if args.intake_forward_offset is None
                                 else args.intake_forward_offset)
        intake_overshoot = defaults["intakeOvershootIn"] if args.intake_overshoot is None else args.intake_overshoot
        points = generate_map(seed, geometry)
        eligible = [point for point in points if point["x"] < 72]
        output = args.output.expanduser().resolve()
        if output.suffix.lower() != ".pp":
            raise ValueError("--output must end in .pp")
        output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="butine-map-") as temporary:
            source = Path(temporary) / "map.tsv"
            values = (*start[:2], math.radians(start[2]), *depot[:2], math.radians(depot[2]),
                      args.seconds, args.score_seconds, seed, intake_width, intake_forward_offset, intake_overshoot)
            rows = ["CONFIG\t" + "\t".join(map(str, values))]
            rows.extend(f"POLLEN\t{point['id']}\t{point['x']}\t{point['y']}" for point in eligible)
            rows.extend("PARTNER\t" + "\t".join(map(str, box)) for box in boxes)
            source.write_text("\n".join(rows) + "\n", encoding="utf-8")
            plan = invoke_bridge(java, classes, jar, str(source))
        validate_plan(plan, eligible)
        project = make_project(plan, eligible, seed)
        output.write_text(json.dumps(project, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        map_path, plan_path = output.with_suffix(".map.json"), output.with_suffix(".plan.json")
        map_path.write_text(json.dumps({"seed": seed, "totalPollen": 40, "pollenPerSide": 20,
                                       "redCondition": "x < 72", "fullPollen": points,
                                       "eligiblePollenIds": [p["id"] for p in eligible]}, indent=2) + "\n", encoding="utf-8")
        plan_path.write_text(json.dumps(plan, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        print(f"Seed {seed}: generated 40 pollen (20 per side); discarded 20 BLUE-side targets.")
        print(f"Java planned {plan['diagnostics']['totalCollected']}/20 RED pollen in {len(plan['runs'])} trips; maximum load 4.")
        if boxes:
            print(f"Reserved the partner corridor for the whole test ({len(boxes)} boxes).")
        print(f"Front intake: {intake_width:g} inches wide, {intake_forward_offset:g} inches ahead of chassis center; "
              f"{intake_overshoot:g}-inch collection overshoot.")
        print(f"Estimated travel/scoring: {plan['diagnostics']['totalCommittedTimeS']:.2f}/{args.seconds:g} seconds.")
        print(f"Pedro project: {output}\nMap: {map_path}\nJava trace: {plan_path}")
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
