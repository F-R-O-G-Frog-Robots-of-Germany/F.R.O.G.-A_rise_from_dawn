# Competition readiness — implemented changes and decisions

**Goal:** Implement the approved software fixes while keeping the control design lean and leaving physical tuning for the assembled robot.

Updated 7 October 2026. Existing working-tree changes were preserved. The shared shooter PIDF refactor was reversed: competition PIDF and shooting bang-bang remain in Shooter, and the tuner retains its separate hub velocity PIDF. The SafeStop helper and exception aggregation were removed at your request.

## Decision table

| Item | Result |
| --- | --- |
| F01 | Fixed: ordinary press, full press and release are independent edges; partial release stops shooting. |
| F02 | Fixed: consume normalized positive-forward Y; strafe and turn signs match the local Pedro drive test. |
| F03 | Fixed competing writes; fixed RPM and distance model are selectable during INIT. Existing competition controller strategies retained. |
| F04 | Fixed: shot and flower timers run in the OpMode loop, with no motor task on the background worker. |
| F05 | Implemented: bounded path and flywheel waits abort the remaining route and perform ordinary STOP cleanup. |
| F06 | Left to Pedro as requested; no additional arrival-quality gate. |
| F07 | Implemented: nine RED and nine BLUE Driver Hub entries; BLUE inherits and mirrors the RED route. |
| F08 | Ordinary cleanup and initialization coverage improved. Additional cleanup failure handling omitted as requested. |
| F09 | Fixed: cancellation waits for the observed Runnable body to exit, rather than a cancelled Future. |
| F10 | Fixed: hardware handles and mechanism state belong to subsystem instances; INIT resets modes. |
| F11 | Fixed: left trigger selects COLLECT/SHOOT preparation mode; only the right trigger requests feeding. COLLECT cancels a held shot. Right-trigger release stops transfer and switches to COLLECT, immediately restarting intake. One loop applies the RPM gate. |
| F12 | Implemented: explicit shot requests gate transfer on current RPM; speed loss pauses feed. SHOOT mode prepares the flywheel without requesting feed. |
| F13 | Deferred; Shooter contains an explicit TODO saying hood control is intentionally deferred. |
| F14 | Explained below; no new recovery controls added. Existing gamepad 2 pose resets retained. |
| F15 | Regression fitting moved to Desmos; in-repo fitting and sample capture removed. Manual coefficient editing and model testing retained. Values copied manually. Hub PIDF retained. |
| F16 | Older-device compatibility intentionally ignored; comments added beside the affected Pedro procedure classes. No algorithm or build settings changed. |
| F17 | Pedro motion algorithms left untouched; no travel guards or maximum-RPM cap added. |
| F18 | Existing loaded/not-loaded interface retained. |
| F19 | Deferred to future Limelight integration; pickup and hive-tilt TODO comments added. No distance-sensor retry/gambling behavior. |
| F20 | INIT selection telemetry now has the INIT_TELEMETRY competition exception switch; selection buttons still work with the display off. |
| F21 | Vision scaffolds left for later implementation. |
| F22 | Small constant-time finite checks retained/centralized in SensorReadings; invalid readings block feed and invalid motor power becomes zero. No monitoring thread or RPM cap. |
| F23 | Declined: no CI or permanent testing framework added. |
| F24 | Independent fallback autonomous omitted as requested. |
| F25 | Corrected stale claims in the readiness checklist, TeamCode readme and autonomous guide. |
| 3.1 | Competition hardware/timers run on the OpMode thread. Unused TaskManager INIT removed; no application background tasks are scheduled. FTC/Android still owns system threads. |
| 3.2 | Autonomous uses MANUAL hub bulk caching, clearing caches before every path, speed-wait and shot iteration. |
| 3.3 | All four improvements implemented: skip fixed-mode distance calculation; sample shooter velocity once; refresh paired intake readings once; share a pose snapshot with mechanisms/telemetry. |
| 3.4 | Paths and queued action order are constructed during INIT, before START. |
| 3.5 | General allocation guidance retained; no separate speculative optimization. |
| 3.6 | Current drive inputs reach Pedro before its update; work time and full cycle time are measured separately. |

## Selecting alliance and shooter target

Choose an autonomous whose Driver Hub name starts with **RED |** or **BLUE |**. Both use the same RED route definitions and corrections; BLUE mirrors geometry and headings through the existing helper. Alliance selection does not depend on an old saved autonomous pose. TeleOp receives the stored alliance and a fresh saved pose when available; otherwise it uses the alliance's mirrored default pose. RED is the initial default.

During INIT in autonomous or Main TeleOp, gamepad 2 **B** toggles **FIXED RPM / DISTANCE MODEL**. Left/right bumpers decrease/increase the remembered fixed RPM. START locks the autonomous choice; Main also retains its existing runtime controls. Changing modes preserves the fixed RPM you selected. This lets you test distance-based calculation, then select the fixed velocity you prefer.

The distance model is now the default target mode; fixed RPM remains available as the manual override. The two target modes select an RPM; they do not require the same PIDF implementation in the tuner and competition code. No tuner gains or fitted coefficients are saved or applied automatically.

## Quadratic model tuner

The existing **Shooter PIDF Tuner** still runs the hub's RUN_USING_ENCODER velocity controller and retains its original gamepad 1 PIDF controls.

| Control | Action |
| --- | --- |
| Gamepad 1 Start | Enable/disable shooter |
| Gamepad 1 Y / B | Increase/decrease manual target RPM |
| Gamepad 1 D-pad and bumpers | Existing PIDF parameter selection/editing |
| Gamepad 2 Y | Toggle testing the quadratic model |
| Gamepad 2 D-pad left/right | Select coefficient A, B or C |
| Gamepad 2 bumpers | Decrease/increase the selected coefficient |

Place the robot at several measured shooting positions and establish a successful manual RPM at each. Record the distance/RPM pairs manually and fit RPM = A × distance² + B × distance + C in Desmos. Enter the resulting coefficients in Shooter.DISTANCE_A/B/C manually; the tuner initializes its coefficients from those values. Gamepad 2 permits further manual edits and live model testing. Distance is horizontal inches; output is RPM. The repository no longer captures samples or fits regression coefficients.

## Explanations requested

**F05 — why a deadline:** STOP-aware loops can still run for the entire autonomous when a path never finishes or the flywheel never reaches its target. An elapsed-time limit now aborts the remaining sequence; it does not make the robot attempt an unrequested recovery route. Shot and flower timing use System.nanoTime on the same loop, so a delayed background worker cannot postpone their callbacks. Like any loop deadline, it is checked when control returns from an SDK call; it cannot interrupt a hardware call that itself blocks.

**F11 — why multiple commands matter:** Previously, mode selection and the right trigger both requested feeding. That let latched SHOOT keep feeding after the right trigger was released. The controls now separate mode selection from the explicit shot request.

**Current controls:** Gamepad 1 left trigger toggles COLLECT/SHOOT at full press
(95%). COLLECT starts intake and keeps transfer off. SHOOT stops intake/transfer
and prepares the flywheel at the selected target RPM; selecting it alone never
feeds. Switching mode clears the momentary shot request. Gamepad 1 right trigger
requests shooting above 5%. Release at 5% or below stops the shot and transfer,
switches to COLLECT and immediately restarts intake. This also clears SHOOT
scoring mode; the flywheel returns to FLOAT at 3000 RPM unless a load is still
detected, in which case it stays in PREPARE.
The left trigger can select SHOOT again for the next shot. A fresh
right-trigger press can also shoot from COLLECT. Each loop uses the current RPM
sample: feed requires a positive, finite target and actual RPM within
SHOOT_SPEED_TOLERANCE_RPM (currently ±70 RPM). Both feed motors pause outside
that range and resume when ready while the shot request remains active.
Autonomous path collection and explicit autonomous shot requests are preserved.

Shooter state priority is explicit shot request → SHOOT, scoring mode SHOOT,
autonomous prepare request or detected load → PREPARE, otherwise → FLOAT.
A detected load selects PREPARE even during COLLECT without starting transfer.

| Shooter state | Flywheel command |
| --- | --- |
| SHOOT | Constant power 1 while the shot is requested, with finite-reading/positive-target checks; no RPM overshoot power cutoff |
| PREPARE | Distance-model target by default, or manually selected fixed RPM, using the existing competition PIDF |
| FLOAT | 3000 RPM using the existing competition PIDF |

The PIDF range is 150 RPM **below** its target: below target −150 RPM the
controller commands power 1, from target −150 through target it calculates
PIDF, and above target it commands power 0. This applies to PREPARE and FLOAT.
The separate feed check remains ±70 RPM around the shooting target. Constant
power in SHOOT can exceed that upper feed limit; in that case transfer/intake
pause even though the flywheel stays at power 1.

**F14 — what recovery meant:** Your gamepad 2 D-pad reset corrects the localizer's believed position. The original finding was about stopping/reversing mechanisms after a jam, separately from localization. It was not a proposal to reconstruct unknown physical servo positions. No additional recovery state machine or controls were implemented.

The relevant pose code constructs `new Pose(x, y, toRadians(90))`, mirrors it for
BLUE, calls `follower.setPose(...)`, then stores `follower.pose()` in
`currentPose`. Main's next manual-drive calculation uses that corrected heading,
and the regular follower update resumes tracking from that reference. This works
when the robot is actually at the known reset position and heading; pressing a
reset elsewhere does not measure or recover its physical location.

Reset coordinates are in inches, with heading 90° for both alliances:

| Gamepad 2 button | RED (x, y) | BLUE (x, y) |
| --- | --- | --- |
| D-pad right | (9, 9) | (135, 9) |
| D-pad left | (9, 133) | (135, 133) |

The ordinary STOP code commands flywheel/intake/transfer power to zero, clears
shooting requests, raises the flower intake and stores the final pose for handoff.
Initialization creates/reset per-run mode state. These commands prevent stale
requests from restarting mechanisms in a later run; they are not servo-position
calibration or jam recovery. No SafeStop helper or exception aggregation exists.

**F16 — Android compatibility:** List.of builds a list conveniently, but Android added that method in API 30. This project permits API 24 and has no core-library API backporting configuration. On an older Android device, reaching those tuner branches can fail even when desktop Java compilation passes. That is a runtime API issue, independent of trust in Pedro's tuning algorithms. Check the real hub Android version before deciding whether to change compatibility settings. Sources: [Android List.of documentation](https://developer.android.com/reference/java/util/List#of(E...)), [Android API desugaring documentation](https://developer.android.com/studio/write/java8-support#library-desugaring).

The team has chosen to ignore older-device compatibility. Code comments record
that decision; no backporting configuration was added.

**F17 — which tuners:** The report referred to the repository's Pedro procedures ForesightTuner, MecanumTuner and PinpointTuner. Their algorithms remain unchanged; only the requested F16 compatibility comments were added. Power clamping limits commanded power, not travel distance. The team has chosen to keep these tuners without additional travel guards.

**F19 — what was verified:** Inspection of every route and the shared runner shows that collecting ends when Pedro finishes the path, and shooting ends after the requested elapsed duration. Intake.loaded selects shooter PREPARE but is not an autonomous pickup-success result. No code confirms a hive tilt or delivered load. Therefore a missed pickup can still be followed by the planned shot. This confirms the software's lack of outcome verification, not a physical scoring failure. Successful loads and route completion rates require robot trials. No retry or sensor policy was added without your request.

Outcome verification is now explicitly deferred in the autonomous code to future
Limelight integration. The current distance sensors are not used to guess where
missed pollen lies or add retry movements.

**F23 — automated tests and CI:** Regression tests replay known inputs and assert expected behavior, such as a half-trigger release stopping a shot. CI means running those checks automatically when code changes. It does not require more robot telemetry or replace hardware testing. Hardware-free checks were run for these fixes; no permanent test framework or CI was installed.

CI and a permanent testing framework are declined by the team.

**F20 and F22 implementation:** `CompConfig.INIT_TELEMETRY` is an exception like
`LOOP_TIME_TELEMETRY`: with COMP_MODE enabled, false suppresses INIT display
while all configuration button handling remains active. Outside competition mode,
diagnostics are enabled normally. `SensorReadings.is_valid(double)` centralizes
the finite-number check without allocating or launching any thread. It is called
where readings are used; invalid shooter samples/targets block feeding, invalid
PIDF inputs stop flywheel output, and invalid cached motor commands become zero
instead of silently keeping the previous power. No excessive-RPM cap is added.
Finite but physically wrong localization still requires the existing manual reset.

## Loop and cancellation details

Main supplies the current input's manual drive powers before follower.update applies them. The field-centric transform uses the last cached heading; Pedro then refreshes localization once and applies those powers. This removes the extra whole-loop delay for joystick commands without updating the follower twice. A pose reset updates the cached pose immediately and the normal loop handles the subsequent update.

Work ms measures the active update work. Cycle ms measures start-to-start time, including the previous iteration's telemetry and loop pacing. Hz is calculated from cycle time. The first cycle has no previous interval and displays zero. Diagnostic telemetry remains controlled by CompConfig.

TaskManager cancellation uses an execution marker protected by the task entry's monitor. Cancellation prevents new invocations, interrupts the future, and awaits the running body clearing its marker in finally. A cancelled Future alone cannot establish body completion. Self-await returns false. Call cancel_and_await instead of cancel when exit confirmation is required; an already-removed name cannot identify its old body. Detailed monitor behavior is documented in taskManager-explanation.md.

## Verification

- Before restoring load-triggered PREPARE: direct compilation passed for all 125 repository Java files. 23 assertions passed for loaded COLLECT remaining FLOAT at 3000 RPM, distance targeting by default, the fixed-RPM override, the 150 RPM PIDF band, full-power SHOOT without an overshoot cutoff, independent RPM feed gating, release into COLLECT/FLOAT, autonomous preparation and invalid-reading/STOP handling.
- Earlier automatic collection after a TeleOp shot: direct compilation passed for all 125 repository Java files. 11 assertions passed for immediate intake restart on right-trigger release, transfer staying off, COLLECT persisting through the next update, the next left-trigger toggle selecting SHOOT, RPM gating, STOP and autonomous cleanup.
- Earlier COLLECT/SHOOT controls, before automatic collection on release: direct compilation passed for all 125 repository Java files. 25 assertions passed against actual subsystem/trigger classes with simulated motor readings, covering preparation without feeding, RPM drop/recovery, release, mode switching during a held shot, autonomous collection/shooting and both alliances' reset coordinates/headings. The preparation mode was subsequently renamed from SCORE to SHOOT without changing behavior.
- After removing in-repo regression fitting for the Desmos workflow, direct Java compilation passed for all 125 repository Java files plus the cached generated BuildConfig. No Java references to the deleted fitting class remain.
- Direct Java compilation passed for all 125 repository Java files (55 TeamCode and 70 controller files), plus the cached generated BuildConfig, against the pinned cached dependencies.
- Before regression fitting was removed, 59 assertions passed against actual compiled classes using fake motor/sensor interfaces: trigger edges, normalized Y, current-sample speed gating, paired sensor caching, retained fixed RPM, separate instance state, ordinary stop, quadratic fit and invalid sample rejection, cancellation including ignored interrupts, all RED/BLUE annotations, Pedro mirroring and cycle timing.
- Additional checks passed for the actual autonomous expired-wait, timed-duration and STOP branches, and source ordering confirmed drive commands precede Pedro updates and route construction precedes START. These exercised the deadline branches without constructing Android hardware.
- Source tracing confirmed autonomous pickup/delivery outcome checks are absent. Hardware scoring success remains unverified.
- The normal Gradle compile was retried and failed before source compilation with `java.io.IOException: Unable to establish loopback connection`. Direct compilation uses cached generated Android resources and does not prove a clean APK build.
- No physical motor, servo, path-clearance or scoring tests were possible before assembly.

Earlier 7 October verification (before the revised trigger behavior): all 126 repository Java files compiled against the cached
dependencies and generated Android resources. The existing 59 assertions and
autonomous deadline checks passed. An additional 26 assertions covered COLLECT
overriding a held shot, release preserving latched SHOOT, stopping both feed motors,
autonomous collection, invalid readings and INIT telemetry. Those 26 checks passed
with both INIT_TELEMETRY=true and a temporary false configuration. No permanent
test framework or CI was added.

Written by Codex (GPT-6), I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn.

## Appendix — values to enter or tune after assembly

These numerical items are intentionally excluded from the software-fix verdict.

- Shooter fixed RPM, FLOAT RPM, shooting overshoot, RPM gate tolerance, encoder ticks/revolution and gearing, competition PIDF and separate hub PIDF.
- Quadratic A/B/C and the hive locations; validate the model over measured distances instead of extrapolating from a few points.
- Path timeout and flywheel-speed wait timeout; these are provisional action limits, not measured motion/scoring performance.
- Shot duration, flower lowering delays, flower lift duration and servo UP/DOWN positions; hood control remains deferred.
- Intake loaded thresholds and sensor polling interval.
- Drivetrain hardware directions, localization setup, Pedro gains/constraints and route hotfixes.
- INIT RPM adjustment step, tuner coefficient adjustment steps, joystick deadband and diagnostic transmission interval.

