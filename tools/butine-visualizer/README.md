# Random RED pollen routes for Pedro

Run from the project root with Python 3.10+ and a JDK:

```powershell
python tools/butine-visualizer/generate_pollen_routes.py
```

The default output is `build/butine-visualizer/red-pollen.pp`. Open that file using **File → Open** in the [Pedro Pathing Visualizer](https://visualizer.pedropathing.com/) or a compatible desktop Pedro visualizer. It uses the same `.pp` project structure as the existing autonomous exports: atomic paths, explicit sequence, per-path colors and polygon obstacle shapes.

For a repeatable map and a specific output file:

```powershell
python tools/butine-visualizer/generate_pollen_routes.py --seed 20261019 --output tools/butine-visualizer/examples/red-pollen-seed-20261019.pp
```

Omit `--seed` to get a new random map each time. The script prints the seed so the map can be recreated. Run it from another directory by passing the full script path; source and Java-cache paths are resolved relative to the script, not the current directory.

## What the file shows

- Exactly 40 pollen positions are generated, with 20 on each half. RED is the audience-view left half, `x < 72`; Y spans the field. All generated positions are within the field clearance model and do not overlap other pollen markers. A pollen position can still be unreachable by the configured front intake at a particular robot heading; these pollen remain visible and uncollected.
- The 20 BLUE-side pollen are discarded before planning and are omitted from the `.pp` display. The map sidecar retains the complete 40-point generation for inspection.
- The actual production Java `Butine`, `ButineField`, `ButineIntake`, `Pollen` and `VisionPose` classes run in a desktop JVM. Python does not implement a second collection optimizer. A small Java bridge executes the first fitting run, removes every pollen contacted by the active intake, returns to the depot, deducts travel/scoring time and solves again against the remaining map.
- Collection uses an **18-inch front intake, 9 inches ahead of the robot center**, with a 1.4-inch pollen radius. The robot keeps a constant heading during each collection pass and can strafe. At the depot, Java can choose a better cardinal heading and turns with the intake off before collecting; away from the depot, it preserves the actual starting heading. Its center aims behind the pollen so the front intake reaches them; collection is no longer modeled as driving the robot center onto each pollen. Every incidental contact along an active intake segment counts toward the four-pollen possession limit. The intake stays off on the return to the depot.
- Every collection trip carries at most four pollen. All segments belonging to one trip share a color. Trip order moves from white through green to black; five trips use white, pale green, bright green, dark green and black.
- Yellow 2.8-inch polygon shapes mark all 20 RED pollen, using Pedro's obstacle-shape feature as requested. Six slate-colored shapes show the physical field obstacles. The pollen shapes are only display markers: Java's collision model receives the six physical obstacles, not the pollen markers. The visualizer may therefore report contact with a pollen marker during a pickup; that contact is intentional.
- Each actual center-path segment is retained, including transit corners and the final heading at the depot. Collection segments have constant robot headings and no waits; grouped pickup labels show when one pass sweeps multiple pollen. The production runner follows a continuous path until the depot rather than stopping at each pollen. Segment speeds and contact fractions are recorded in the trace. A scoring wait is included after each depot return. The exporter does not replace a safe polyline with a smoothing curve.
- Before beginning its return, the robot continues **1 inch past the final intake contact along the same incoming direction**, with the intake still on. Java rechecks that continuation for field clearance and additional pollen; incidental contacts still count toward the four-pollen limit. If another pollen is collected during that continuation, Java extends along the same line until the endpoint is 1 inch beyond the final contact, or rejects an unsafe/over-capacity route.

Adjust the measured active intake dimensions with:

```powershell
python tools/butine-visualizer/generate_pollen_routes.py --intake-width 18 --intake-forward-offset 9 --intake-overshoot 1
```

These values describe the intake mouth and its final continuation, not a circular pickup radius around the robot. Intake width must be positive; forward offset and overshoot must be nonnegative. Changing these values changes Java's sweep contacts and route planning. Omitting `--intake-overshoot` uses the production Java default; `--intake-overshoot 0` ends collection at the final surface contact.

## Time and starting pose

The default virtual budget is **150 seconds**, allowing the whole RED-side test map to be displayed when it fits. Use an autonomous-sized budget with:

```powershell
python tools/butine-visualizer/generate_pollen_routes.py --seed 20261019 --seconds 30 --output build/butine-visualizer/red-auto.pp
```

Uncollected RED pollen remain visible and are listed in the Java trace. The budget deducts the production travel/turn estimate and `--score-seconds` after each trip; desktop CPU solve time is recorded separately and is not deducted, keeping seeded exports reproducible. This is a route simulation, not a camera/detection or robot-physics simulation. The visualizer's own playback duration can differ from the production Java estimate.

The default start and depot are `(59.25, 18, 90°)`, a clear illustrative RED pose. They do not change the authored robot scoring poses. Coordinates are inches in the project's 0–144 field frame; headings supplied to this script are degrees and are converted to radians for Java.

```powershell
python tools/butine-visualizer/generate_pollen_routes.py --start 30 24 90 --depot 59.25 18 90 --score-seconds 2
```

Blocked start/depot poses fail with an explanation. The geometry retains the verified center underpass for the 18 × 18 × 18-inch robot with 1 inch of body clearance.

The `.pp` file includes 144-inch field dimensions for compatible desktop visualizers. The current official web version uses a fixed 141.5-inch canvas and ignores that dimension setting. Exported coordinates retain the robot project's 144-inch convention without rescaling; the generated pollen/routes remain inside that canvas, while perimeter obstacle polygons can be clipped at the top/right edge. Shapes are enabled through `experimentalFeatures.obstacles`.

## Files and dependencies

For `red-pollen.pp`, the script also writes:

| File | Contents |
|---|---|
| `red-pollen.map.json` | Seed, all 40 generated positions and the 20 RED IDs retained for planning. |
| `red-pollen.plan.json` | Java configuration including collection overshoot, physical/exclusion bounds, actual robot-center paths, intake ON/OFF segments, grouped pickup IDs and contact fractions, segment speeds/timing, remaining budget and uncollected IDs. |

The script uses only Python's standard library. It compiles the six required Java sources into a source-hashed cache under `build/butine-visualizer/java-cache`, using the existing cached **Pedro core 3.0.1 JAR**. No Android SDK, camera, robot, JNI or package installation is required. Sync the Android project first if that JAR is not yet cached, or supply it explicitly:

```powershell
python tools/butine-visualizer/generate_pollen_routes.py --pedro-jar C:/path/to/core-3.0.1.jar
```

`JAVA_HOME` or `PATH` must supply `java` and `javac`; `--java` and `--javac` accept explicit executable paths. Subprocesses use argument arrays rather than a shell, including paths with spaces.

## Production possession limit and checks

`Butine.POSSESSION_LIMIT` is four and `Butine.Params.capacity` defaults to that value. Physical-field solves and runner requests clamp oversized or nonpositive capacities to four. Smaller requested loads remain supported. Explicit `ButineField.unrestricted()` calibration preserves its existing mathematical unbounded-tour mode. Intake sensor thresholds are separate from planner possession counts and are unchanged.

The disabled `ButineTest` bench OpMode now selects capacities from one to four; its B button selects a full four-pollen load rather than the former unbounded option.

Run the exporter integration checks with:

```powershell
python -m unittest discover -s tools/butine-visualizer -p "test_*.py" -v
```

These checks run the actual Java bridge, verify seeded reproducibility, 40/20 counts, spacing/clearance, front-intake offsets, a paired-pollen pass with multiple intake widths, configurable same-direction overshoot beyond the final contact, legacy input defaults, incidental-contact capacity, intake-off return segments, continuous headings/no pickup waits, route continuity, gradient, marker separation from planner obstacles, shortened budgets and rejected BLUE/blocked input. Robot-code checks remain in `tools/limelight-audit/run.ps1` and `tools/competition-audit/run.ps1`.

The included seed-20261019 sample collects all 20 RED pollen in five four-pollen trips, with 26 center-path segments, 1 inch of final collection overshoot and an estimated 29.305522 seconds of travel/scoring. Other maps or intake dimensions can leave unreachable pollen visible. The example PNG is a static preview drawn from the same exported file and Java trace. The `.pp` structure follows the [official visualizer's import normalizers](https://github.com/Pedro-Pathing/Visualizer/tree/f953ee0ec28ad1eca7b2fa32edfda294c27c00d7). Desktop checks do not establish physical pickup performance or actual match timing.
