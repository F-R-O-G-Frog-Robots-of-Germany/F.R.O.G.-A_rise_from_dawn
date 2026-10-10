# F.R.O.G. — Competition readiness audit

**Goal:** Check the current repository and recent commits for realistic software defects, missing competition behavior, and useful performance improvements, without implementing behavioral changes.

**Reviewed:** 7 October 2026.  
**Checkout:** `C:\Users\pmkoe_eque6\StudioProjects\F.R.O.G.-A_rise_from_dawn`.  
**Git baseline:** `572c10562a6e7bd978ea6c584671d0266cd86c99`, including staged, unstaged, and untracked source present at the end of this review.

## Assessment

**Requested follow-up, 7 October 2026:** F02 is now implemented. In Main, holding gamepad 2 B continuously for three seconds switches RED/BLUE once per hold, during INIT or TeleOp. X now toggles shooter bypass in INIT and TeleOp; the shared autonomous INIT selector also uses X. The selected alliance reaches drive orientation, shooter targeting and the saved handoff. INIT fallback poses follow the selected alliance; valid saved poses and active localized poses are preserved. Direct Java compilation and focused hold/control checks passed; Gradle still fails before compilation because it cannot establish a loopback connection. The assessment below records the original audit snapshot, with F02 marked resolved; F01 remains open.

I found **two concrete tuning/setup gaps**. These are detailed below. I found no remaining reproduced competition-control logic defect or substantiated match-loop performance defect that currently warrants an optimization.

The tuning gap is that the shooter tuner exercises a different velocity controller from competition code. The setup gap is that standalone TeleOp cannot explicitly select BLUE after a fresh Robot Controller process starts. A collection-after-shooting defect reproduced earlier in this review was corrected by other ongoing work before the final verification and is excluded from current findings.

This is a fresh review. The previous readiness report and its conclusions are excluded from the evidence. A short findings list is an acceptable result; speculative problems and already resolved historical defects are not counted as current findings.

The robot is not assembled. Numerical tuning is excluded from this assessment and collected in the **final appendix**. Limelight and hood behavior are intentionally deferred. Your decision to prioritize autonomous cycle speed over final pose accuracy is accepted: **no additional settling wait or arrival-accuracy gate is recommended**.

No behavioral fixes were implemented. The only robot-source edit made for this audit is the subsequently requested explanatory comment in [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:108).

## Coverage and verification

The repository inventory covered source, build files, manifests, resources, registration, documentation, and recent changes. The final source set contains **55 TeamCode Java files** and **70 FtcRobotController Java files**. The shooter tuner, Intake, Shooter and Main changed during the review, and the regression helper was removed by other ongoing work; the final assessment uses the updated source and excludes the removed helper.

| Area | Review performed |
| --- | --- |
| Robot and mechanisms | Read initialization, update and stop behavior for Robot, Intake, Shooter and FlowerIntake; followed all competition command callers. |
| Competition TeleOp | Read the shared lifecycle, every input abstraction, all Main bindings, shooter selection, pose handoff and field-centric drive mapping. |
| Autonomous | Read the shared action executor, all nine RED routes, all nine BLUE subclasses, shared geometry and mirroring. Checked action ordering and path-to-shot transitions. |
| Control and hardware utilities | Read TaskManager, PeriodicRegistry, logging, timing, position storage, numeric validation and actuator caches. Checked actual callers before assessing utility risks. |
| Tuning | Read the shooter tuner and all Pedro registration/procedure source: Mecanum, Pinpoint, Foresight and follower tests. |
| Controller module | Checked integration, registration and manifests; verified it has no changes relative to the repository's upstream SDK baseline. All bundled sample/utility OpModes remain disabled. Their demonstration algorithms were not individually re-audited as competition robot behavior. |
| Build and dependencies | Checked both modules and shared Gradle configuration. Verified API behavior where necessary against cached FTC SDK 12.0.0 and Pedro 3.0.1 classes. |
| Recent commits | Inspected `572c105`, `2f5378e` and `6328401`, with the preceding custom-code baseline for context. Historical issues are separated from current source below. |

There are **20 explicitly annotated TeamCode OpModes**: 18 autonomous entries, Main and the shooter tuner. Their annotation names are unique. Pedro's AutoTune procedure registration uses its separate `@Tuner` mechanism.

| Fresh check | Result and limitation |
| --- | --- |
| Normal Gradle Java build, offline with no persistent daemon | Failed before compilation with `java.io.IOException: Unable to establish loopback connection`. This establishes an environment/build-startup limitation, not a Java-source defect. |
| Direct Java compilation of final repository sources | **Passed:** all 125 repository Java files plus the existing generated controller BuildConfig, using cached dependencies and existing generated resource jars. |
| Partial and direct-full trigger sequences | **Passed:** ordinary press/release events work in current source. |
| Collection → trigger shooting → release | **Passed:** the current Main release sequence stops shooting and explicitly resumes collection while stopping transfer. |
| Persistent SHOOT mode without a trigger request | **Passed:** prepares the flywheel without feeding; releasing a shot cancels its temporary request. |
| Feed readiness and invalid flywheel sample | **Passed tested cases:** a ready sample permits feed; an invalid sample stops shooter and feed. |
| Cached motor exact stop | **Passed:** an exact zero command reaches hardware even when the previous nonzero command differs by less than the cache tolerance. |
| Task cancellation while a body is still executing | **Passed:** `cancel_and_await()` returns false at its deadline instead of claiming the body exited. |
| Pose mirroring | **Passed representative RED/BLUE round trip**, including heading modulo a full turn. |
| Fresh-process alliance default | **Confirmed:** no stored pose and alliance RED; relevant to F02. |

Behavioral checks used freshly compiled project classes, temporary Java harnesses, and fake motor interfaces. No robot hardware was accessed and no tests were added to the repository. The direct compilations produced a Java bootstrap-classpath warning and an existing controller Activity deprecation note, with no compilation errors.

Direct compilation does not establish a clean Android build, regenerated resources, annotation scanning on a device, DEX conversion, APK packaging, installation, or hardware behavior. The normal Gradle check remains unverified. Software inspection also cannot establish mechanism clearance, successful shots, actual loop speed, or route duration on the unbuilt robot.

## Current findings

**P1** means address before relying on the affected competition behavior. **P2** means a concrete workflow/completeness gap with a bounded impact. No tuning value contributes to these priorities.

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| F01 | P2 | Shooter PIDF tuner exercises a different controller from competition | Confirmed motor modes, commands and gain ownership |
| F02 | P2, resolved | Standalone TeleOp lacked BLUE selection without a prior alliance handoff | Original gap confirmed; requested B-hold selection now implemented and checked |

### F01 — The shooter tuner does not tune the deployed controller

Sources: [ShooterPidfTuner initialization](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:70), [tuner velocity command](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:107), [tuner gain application](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:200), [Shooter initialization](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:50), [competition velocity control](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:177).

The tuner sets `RUN_USING_ENCODER`, writes hub velocity PIDF coefficients, and commands `setVelocity()`. Competition Shooter sets `RUN_WITHOUT_ENCODER`, computes its own power, and writes `set_power()`. While shooting, it uses the separate full-power/zero-power controller visible in `Shooter.update()`.

PIDF means proportional, integral, derivative and feedforward control. Having P/I/D/F labels in both places does not make the gains interchangeable: one controller runs in hub firmware; the other directly computes motor power in the robot loop. Changes made with the tuner's PIDF controls do not edit or validate competition Shooter's `K_P`, `K_I`, `K_D` or `K_F`.

FIRST's [motor-mode documentation](https://ftc-docs.firstinspires.org/en/latest/tech_tips/tech-tips/tech-tip-motor-modes/tech-tip-motor-modes.html) confirms this API distinction: encoder modes use hub velocity control, while `RUN_WITHOUT_ENCODER` applies power. The repository source establishes which mode each OpMode actually uses.

**Realistic impact:** Once the robot is built, time spent obtaining stable hub PIDF settings can be mistaken for validation of the competition flywheel controller. The distance-model controls still have value, but successful model tests on the hub controller do not validate the deployed controller's spin-up and feeding behavior.

**Practical correction:** Provide a tuning/test option that exercises the existing competition Shooter controller. Keep the hub-controller tuner clearly identified if it is useful. This recommendation does not require replacing competition control or unifying both strategies in this review.

**Sensor feasibility:** The mapped flywheel motor's encoder is already the measurement used by both controllers. No extra sensing is needed to compare requested RPM, measured RPM and output. Scoring success still requires physical observation until the planned vision work is available.

**Cost and speed:** Some test-OpMode integration and gain-editing plumbing. A tuner that runs the deployed control has the same control workload to measure. This change need not add work to competition loops or reduce match speed.

### F02 — A fresh standalone TeleOp was always RED (resolved after audit)

**Resolution:** Gamepad 2 B now requires a continuous three-second hold before switching alliance. Releasing before the deadline cancels the hold; continuing to hold after a switch cannot switch again until B is released. A short rumble confirms the change, and an active TeleOp change displays the selected alliance once. The hold uses the existing control loop: TaskManager's delayed task API was inspected, but it has no continuous-hold helper, and no worker or blocking wait is needed here. X replaces B for shooter bypass, including the existing protection against an INIT button press being repeated at START.

Sources: [LastPositionStorage](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:15), [TeleOp starting setup](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:165), [field-centric alliance rotation](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:94), [Main INIT loop](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:78).

`LastPositionStorage.currentAlliance` initializes to RED. Main obtains its alliance from that storage even when no saved pose is available. INIT displays the alliance but only offers shooter target selection; Main has no explicit alliance selection or separate BLUE TeleOp entry.

**Realistic impact:** Starting standalone TeleOp for BLUE practice after launching the Robot Controller app uses the RED field-centric orientation. A fresh process also lacks the usual autonomous handoff. The selected alliance feeds the shooter hive lookup when distance mode is enabled.

**Bounded scope:** Normal BLUE autonomous-to-TeleOp handoff works in source: BLUE autonomous explicitly chooses BLUE and stores that alliance at shutdown. This finding is about standalone setup and loss of in-process handoff, not a claim that all BLUE matches are broken. If the team's operating procedure always provides a valid handoff, this has lower practical priority.

**Practical correction:** Add an explicit INIT alliance choice, using the stored alliance as the initial selection when available. Apply the chosen alliance consistently to manual drive and the shooter, which currently retains its own alliance from initialization. An existing saved field pose must retain its coordinate meaning; fallback pose mirroring belongs to fallback setup.

**Sensor feasibility:** Alliance comes from the driver, so no new sensors or vision are needed.

**Cost and speed:** INIT-only configuration plus consistent propagation of the selection. The match loop can continue using its current cached alliance and rotation branch. There is no reason to add a settling wait or additional hardware reads.

## One small optional operational improvement

[AbstractAuto's timeout handler](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:84) only displays its existing abort reason when diagnostic telemetry is enabled. With the current competition policy, a path or flywheel wait timeout stops the route without that explanation reaching the Driver Station.

Displaying the existing reason **once on the abort event** would help the team distinguish a path deadline from flywheel readiness failure. It uses the fault already detected in software; no sensor-based diagnosis is invented. The cost is one telemetry transmission on a run that is already aborting, with no recurring match-loop traffic. This is an optional usability improvement, not another control-logic defect.

The route already has bounded waits and cleanup. I do not propose automatic retries or recovery parking using localization that may have caused the path failure. Recovery behavior would need evidence and an explicit strategy on the assembled robot.

## Performance assessment

No measured runtime or profiling data exists for the unbuilt robot, so I cannot support a speedup claim. The source already contains the useful structural measures expected here:

- Competition hardware updates run on the OpMode thread. Autonomous no longer schedules flywheel motor writes on a background worker.
- Competition loops clear manual hub bulk caches once per iteration before reads.
- Shooter velocity is sampled once per mechanism update and reused for control and feed readiness.
- Cached motor writes suppress small redundant changes while preserving exact stops.
- Intake range readings are shared and rate-limited.
- Autonomous paths and action lists are built during INIT.
- Panels/video/recurring diagnostic telemetry are suppressed by the competition policy.

I recommend preserving those measures. Replacing simple arithmetic, uncontended synchronization, or the existing listener containers has no demonstrated payoff here. No fixed sleeps or additional settling intervals are proposed.

One suspicion was specifically ruled out: starting the shooter tuner after a competition OpMode does **not** necessarily leave its encoder reads stuck in the preceding manual bulk cache. In the pinned FTC SDK 12.0.0, `OpModeManagerImpl.resetHardwareForOpMode()` resets Lynx modules before initialization, and `LynxModule.resetDeviceConfigurationForOpMode()` sets bulk caching OFF. This was checked against the actual cached SDK bytecode. It is excluded from the findings.

## Recent-commit findings and current status

The following historical defects were verified directly from Git source. They are not counted as additional current problems and should not be reintroduced by restoring old files or cherry-picking only part of the current changes.

| Historical source | Verified problem | Current status |
| --- | --- | --- |
| `2f5378e` and `572c105`, `opModes/teleOp/comp/main.java` with `core/inputs/GamepadAnalogStick.java` | The wrapper normalized stick Y, then Main negated it again. Raw stick-up became a negative forward request. | Current Main consumes the normalized Y directly; that software sign mismatch is absent. Final hardware directions remain a bring-up item. |
| `572c105`, `opModes/auto/structure/AbstractAuto.java` with `subsystems/shooter.java` | Autonomous scheduled custom flywheel power control on the TaskManager worker while `shoot()`/`update()` also wrote flywheel power. Its action waits had no independent deadline. | Current autonomous calls one Shooter update from the loop, measures action time there, and bounds path and flywheel waits. No competing scheduled flywheel writer remains in the competition callers. |

`6328401` changed README text only and contributed no robot logic. The historical generic trigger abstraction emitted ordinary press callbacks on some release transitions, but the committed competition Main used the full-press callback. I do not label that as a demonstrated partial-trigger shooting latch in those commits. The current ordinary press/release bindings passed fresh checks.

Other reviewed utilities, route sequencing and registration did not produce an additional well-supported match-impacting finding. This is not proof that all possible hardware outcomes are correct; it records the evidence threshold used for this report.

### Worktree issue resolved during this review

The earlier worktree turned collection off on right-trigger release without restoring it, and persistent SHOOT mode itself counted as a feeding request. The final source separates the explicit shot request from flywheel preparation, and [Main's release callback](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:38) calls `stop_shooting()` followed by `collect()`. Fresh checks of those calls passed: collection resumes and transfer stops; selecting SHOOT without pressing the shooting trigger does not feed. These corrections came from other ongoing work, not this audit. They are not counted as current findings.

## Accepted decisions and deferred work

- **Autonomous arrival accuracy:** Proceed using Pedro's existing completion signal. You explicitly accept the accuracy cost for cycle speed. An explanatory comment now records this in the shared path action. No extra position/heading gate is requested.
- **Limelight/Butine:** Empty integration scaffolds and the video hook are expected at this stage. Vision implementation, missed-pickup localization and hive-outcome confirmation are deferred; they are not current defects in this report.
- **Hood:** Hood control is intentionally deferred. `Shooter.init()` still looks up `servoHood`, so initial bring-up configuration must include that mapped servo entry even before hood motion is implemented. A configured servo port does not need an attached physical servo for that lookup; no hood-control implementation is requested here.
- **Pickup/delivery success:** The two intake range sensors can provide occupancy observations. They cannot verify hive tilt, classify every jam, or locate pollen missed outside the robot. Automatic retries based on those unmeasured outcomes are not recommended.
- **Tuning platforms:** The Pedro procedures explicitly assume the team's selected newer Android devices for `List.of()`. No unsupported-device failure is asserted without evidence about the actual target device.

## Changes you would like to request

- [ ] F01: Add a test option for the deployed shooter controller.
- [x] F02: Add alliance selection with a three-second B hold; move shooter bypass to X.
- [ ] Optional: Display an autonomous abort reason once in competition mode.
- [ ] Team notes: ________________________________________________

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn.

## Final appendix — Untuned constants, values and physical validation

Everything in this appendix is excluded from the defect count. These are calibration, configuration or assembly checks, not evidence that the software logic is wrong. No replacement values are proposed before hardware exists.

| Location | Values/configuration to establish on hardware |
| --- | --- |
| `core/pedro/Constants.java` | Drive hardware names and directions; Pinpoint name, pod offsets, pod type, encoder directions and units; feedback/feedforward gains; heading, linear and quadratic brake coefficients; acceleration/velocity/deceleration limits and natural deceleration; end constraints and the internal timeout. Confirm each setting's meaning for pinned Pedro 3.0.1, including its parametric end-constraint convention. |
| `core/pedro/PathConstraints.java` | Unused `autoPilot` profile and its values. It has no current competition caller, so its numbers cannot presently slow the robot. Review them only if that profile is connected later. |
| `subsystems/Shooter.java` | Encoder ticks and gearing convention; standard, idle and selected fixed RPM; overshoot/readiness band; P/I/D/F gains and controller range; quadratic distance coefficients; hive coordinate/reference geometry. Validate the controller transition and RPM recovery under real feeding load. |
| `subsystems/Intake.java` | Intake/transfer power and direction; motor braking behavior; upper/lower distance thresholds; sensor sampling interval, mounting, response and loaded interpretation. Placeholder thresholds are not counted as a logic finding. |
| `subsystems/FlowerIntake.java` | UP/DOWN servo positions, directions, travel clearance and simultaneous motion. These positions need physical validation before relying on them. |
| `core/hardware/CachedMotor.java`, `CachedServo.java` and their constructors | Change tolerances versus acceptable actuator response and bus traffic. Exact motor stop behavior already passed a software check. |
| `opmodes/auto/structure/AbstractAuto.java` | Path-action deadline, flywheel-wait deadline, flower lift timing and shot-duration behavior under real feed interruptions. Validate actual timing while keeping the accepted no-extra-settling policy. |
| All competition autonomous routes | Shot durations, flower-lowering delays and any estimated-path/intake timing. Shared RED geometry, hotfix values, BLUE reflection, robot footprint and field clearance; actual complete route duration. A Bézier control point outside a field boundary does not by itself prove the curve leaves the field. |
| `opmodes/auto/structure/OptimalPoses.java` | Shared shooting poses, ideal pickup/park coordinates, desired headings and field corrections. Verify the actual swept robot footprint and mechanism reach on both alliances. |
| `opmodes/teleop/AbstractTeleOp.java`, `comp/Main.java` | Fallback/reset poses and reset heading; deadband and drive scaling; handoff assumptions. Match the physical reference orientation to the chosen alliance. |
| `core/control/LastPositionStorage.java` | Saved-pose validity interval and the operating procedure for moving the robot between OpModes. |
| `opmodes/teleop/test/ShooterPidfTuner.java` | Test target RPM, adjustment sizes, control period and coefficient step sizes. Establish which controller a measured gain belongs to before copying it. Distance-fit values are now entered manually from the team's external fitting workflow. |
| `core/pedro/procedures/*` | Test distances, target speeds, power levels, braking/test durations, fitting inputs and clear test space. Motion-identification routines must be supervised during commissioning; their existing STOP controls do not establish collision-free travel. |
| Driver Station / Robot Controller configuration | Hardware-map entries, motor types and wiring; matching FTC app versions; the team's actual Android device and firmware. Verify a clean APK build/install when the Gradle environment is working. |
| Planned hood and Limelight | Once implemented: hood positions/angle model, camera mounting and coordinate transforms, pipelines, target freshness and acceptance policy. These belong to later implementation and hardware calibration. |
