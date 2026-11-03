# Partner Auto Import

An offline Python application for importing a partner's Pedro `.pp` project or Java autonomous source, reviewing the extracted route and stationary actions, and exporting a local JSON planning profile. It does not modify or enable Butine, Limelight or any competition OpMode.

## Start the paste/import interface

From the repository root:

```powershell
python tools/partner-auto/import_partner_auto.py --ui
```

On Windows, `tools/partner-auto/launch.bat` starts the interface with `--detach`: Python stays running after the launcher window closes. Launching it again reopens the existing local service. Use **Stop local importer** in the page to stop it. The command above runs in the foreground, where `Ctrl+C` stops Python; add `--detach` for the background behavior. `--no-browser` leaves opening the printed URL to you. Port 8765 is preferred so drafts survive normal restarts; if occupied, an unused port is selected. `--port NUMBER` explicitly chooses a port. Background startup logs are under `build/partner-auto/server-*.log`.

The source controls accept individual `.pp`/`.java` files, a Java source folder, or text pasted into the editor. **Save pasted source** retains the editable text; **New pasted source** starts another file while keeping the previous one in the source list. **Edit** reopens any saved file. Imports, errors and retries never clear the editor. Drafts and saved sources are stored in this browser and restored on refresh, where browser storage is available. **Clear** is the explicit way to remove them. Changing source or model settings requires another import before exporting.

Several complete Java files can also be pasted together. The reader splits compilation units at actual package declarations, ignoring package-like text in comments/strings, and preserves original diagnostic line references. If pasted source exactly matches this FROG checkout, missing local helpers such as `PoseMirroring` can be resolved from the project and are listed in the findings. Unfamiliar partner code is not filled with guessed FROG helpers. Choose the concrete autonomous when several are found, review the route/findings, then export the JSON profile.

Wi-Fi may stay connected. No internet service or online check is used: the browser sends imports only to Python at `127.0.0.1` on this computer. A local connection error means that Python could not be reached, not that Wi-Fi needs disconnecting. Reopen `launch.bat` if needed; the source remains in the editor and draft storage. Open the app through its launcher rather than double-clicking `ui.html`, which cannot run Python by itself.

Requirements: Python 3.10 or newer. `.pp` import uses only the Python standard library. Java source import additionally requires a JDK 17 or newer with `java` and `javac` available through `JAVA_HOME` or `PATH`. Android Studio users can use an installed compatible JDK. Imported Java is parsed and interpreted by supported source-analysis adapters; its robot program is never executed. The importer does not run the partner's Gradle scripts or require their hardware.

## Import from the command line

### Pedro project

```powershell
python tools/partner-auto/import_partner_auto.py tools/butine-visualizer/examples/red-pollen-seed-20261019.pp --output build/partner-auto/red-partner.json
```

A short editable example is included at `tools/partner-auto/examples/partner.pp`.

Use `--format pp` or `--format java` to choose explicitly. Automatic format selection handles one JSON `.pp` project or a set of Java sources. Import one `.pp` project at a time; unrelated projects are not combined into a single trajectory.

### Java autonomous with dependencies

List the concrete autos in our source tree:

```powershell
python tools/partner-auto/import_partner_auto.py TeamCode/src/main/java --list-entries
```

Select an entry and export its reviewed profile:

```powershell
python tools/partner-auto/import_partner_auto.py TeamCode/src/main/java --entry org.firstinspires.ftc.teamcode.opmodes.auto.comp.BlueFarMidPark --output build/partner-auto/blue-far-mid.json
```

A partner repository/folder or ZIP can be passed in place of the source folder. `--source-root PATH` adds Java dependencies outside the selected file's directory. Multiple source-file arguments are also accepted. ZIP members are read in memory rather than extracted.

The included plain Java example deliberately inherits from the real FROG base. This command tests importing a leaf file plus its separate dependency root:

```powershell
python tools/partner-auto/import_partner_auto.py tools/partner-auto/examples/ExamplePartnerAuto.java --source-root TeamCode/src/main/java --entry ExamplePartnerAuto --output build/partner-auto/example-partner.json
```

### Plain pasted source

```powershell
Get-Content -Raw partner-auto.java | python tools/partner-auto/import_partner_auto.py --stdin-format java --output build/partner-auto/pasted.json
```

The same `--stdin-format pp` option accepts `.pp` JSON. If pasted Java depends on other files, supply them as input paths or with `--source-root` rather than expecting the class name alone to reveal their contents.

## Supported extraction

Pedro `.pp` support includes legacy endpoint heading fields and current 1.x atomic/compound projects; explicit sequence order and repeated references; sequence and path waits; Bezier controls and interpolating through-points; tangent, constant, linear and valid piecewise headings; and declared inch/centimeter/millimeter/meter coordinates. Compound heading rules are applied over the group's arc-length progression. A changed sequence that jumps between disconnected declared origins is reported instead of receiving an invented connector. Display shapes and pollen markers do not become robot movements.

Path-level waits are retained, with a finding explaining that some Pedro versions use them as visualization pauses. The partner's actual autonomous is the authority on whether the robot performs those waits. A `.pp` file does not automatically describe shooting just because a path ends near a Hive. Only explicitly represented actions can be extracted.

Java support follows source-defined inheritance, methods, constants and supported helper/action patterns. Framework adapters cover FROG's inherited queues and common Pedro and Road Runner builder/action forms. The input must include the relevant parent and helper sources. Source parsing is not a promise to understand every custom FTC framework or arbitrary runtime state machine: unsupported behavior remains an explicit finding/unknown action, including branches whose motion or duration cannot be resolved. A successful parse alone is not a complete extracted autonomous.

Road Runner straight segments, turns and supported sequential/parallel actions are understood. Road Runner spline geometry and concurrent temporal/displacement markers currently remain unresolved instead of being replaced with guessed straight lines or omitted actions. Raw asynchronous Pedro dispatch also requires a recognized completion wait; an intervening action cannot be assumed to happen after path arrival.

The output retains action and diagnostic source references so a route can be checked against the supplied code. Preserve unresolved branches and duration bounds during review; a guessed sensor outcome must not become an exact partner schedule.

## Body and timing model

Travel and turn timing are estimated from the supplied model. The interface exposes field size, partner body dimensions, clearance, cruise speed, acceleration, turn rate, start delay, timing allowance and AUTO duration. These are provisional inputs, not measured performance of the partner.

For CLI imports, `--settings settings.json` supplies a JSON object with any of these keys:

```json
{
  "field_size_in": 144,
  "robot_length_in": 18,
  "robot_width_in": 18,
  "clearance_in": 2,
  "speed_in_s": 30,
  "acceleration_in_s2": 40,
  "turn_rate_rad_s": 3.141592653589793,
  "start_delay_s": 0,
  "timing_uncertainty_s": 0.5,
  "sample_period_s": 0.1,
  "auto_duration_s": 30
}
```

Source-defined waits and modeled travel are labeled separately. Unknown duration stays unbounded; it does not become zero seconds. The route preview and occupied regions are predictions for offline planning. They are not observations of where the partner is, and passing a predicted departure time does not prove a lane has cleared. Runtime cooperation remains separate work.

## Export and exit status

The JSON contains the selected source/entry, normalized actions, findings, model settings, estimated timeline and swept occupancy reservations where the extracted information permits them. An incomplete profile can be saved for review and retains that incomplete status. It must not be treated as an accepted collision-free partner schedule. Unknown future motion remains visible rather than disappearing from the export.

Without `--output`, CLI JSON is printed to standard output. With `--output`, the file is written and non-information findings are also printed to standard error. Exit code `0` means a complete extraction, or successful `--list-entries`; `1` means selection or unresolved/invalid extraction remains; `2` means an input/launch error. Completeness concerns extracted source, not a guarantee of physical timing or safe cooperation.

## Checks

```powershell
python -m unittest discover -s tools/partner-auto/tests -v
```

The checks exercise `.pp` migration, execution order and waits, inherited Java extraction, source errors, timing/occupancy behavior, command-line export and the actual local HTTP import boundary. Java checks require the JDK. No robot testing is performed by this command.

Validation on 2 November 2026 passed 71 test methods, including all 18 actual FROG RED/BLUE autonomous exports. CLI imports of the repository `.pp` sample and inherited Java autos produced JSON profiles. A real headless Chrome check exercised paste, HTTP import, route/occupancy rendering and export availability. Generic default motion estimates can exceed the match duration; the tool reports this rather than claiming production timing. The test log is `build/partner-auto-tests-2026-11-02.log`.

Follow-up fixes passed 80 tests, adding failed-request draft retention, edited retries without duplicate classes, refresh restoration, combined Java pastes, the exact AbstractAuto + OptimalPoses + BothFlowersNoGarden input, and detached-launch/reopen/shutdown behavior. The test log is `build/partner-auto-fixes-2026-11-02.log`. Node.js is optional for the UI interaction regression checks; the importer itself still needs only Python and, for Java source, a JDK.
