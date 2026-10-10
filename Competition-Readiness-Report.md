# F.R.O.G. — Competition Readiness Report

**Implementation update:** Use the decision table in
[Competition-Readiness-Changes.md](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Readiness-Changes.md)
for current status. The analysis below records the baseline before those changes.
The shared shooter PIDF refactor and SafeStop helper were reversed as requested.

**Goal:** Identify software logic errors, reliability gaps, missing functionality, and useful performance improvements before the robot is assembled.

**Reviewed:** 6 October 2026. **Baseline verdict before implementation:** The software was not competition ready yet, even when every untuned value is excluded from the assessment. The main blockers are input handling, competing shooter controllers, autonomous failure handling, alliance selection, and shutdown behavior. There is already a working software structure to build on: driver controls, subsystem initialization, nine autonomous routines, cached actuator wrappers, and Pedro tuning procedures are present.

This baseline review preceded the implementation documented in Competition-Readiness-Changes.md. Existing staged, unstaged, and untracked work was included and preserved. Source line numbers below refer to that baseline; follow file links for the current source.

## 1. Scope, evidence, and limits

The checkout reviewed was `C:\Users\pmkoe_eque6\StudioProjects\F.R.O.G.-A_rise_from_dawn`, based on Git commit `572c10562a6e7bd978ea6c584671d0266cd86c99` plus the current working tree.

- Inventoried the repository, including build configuration, manifests, resources, documentation, and OpMode registration. The initial nonhidden, nonignored inventory contained 162 files; hidden project metadata was checked separately.
- Read all **43 TeamCode Java files**, totaling **5,344 lines**, including every competition autonomous routine, shared lifecycle, subsystem, input abstraction, utility, and tuning procedure.
- Reviewed the integration and registration of the **70 FtcRobotController Java files**. Bundled SDK samples received a registration/configuration sweep and compilation check; their individual demonstration algorithms were not reviewed to the same depth as the robot's competition code. Their annotated sample/utility OpModes are disabled.
- Checked the pinned dependencies: FTC SDK **12.0.0**, Pedro core/revhub **3.0.1**, Pedro tuning **1.0.1**, and Panels/telemetry **1.0.5**. Inspected cached dependency source and bytecode where API behavior mattered.
- Ran hardware-free reproductions against the actual compiled project classes, using fake motor/sensor interfaces where needed. No hardware was accessed.

### Build and verification results

| Check | Result | What it establishes |
| --- | --- | --- |
| `gradlew.bat :TeamCode:compileDebugJavaWithJavac --console=plain` | Failed before Java compilation: `java.io.IOException: Unable to establish loopback connection` | The normal Gradle build cannot currently be verified in this execution environment. |
| Gradle retries with no daemon and alternate JVM networking options | Same startup failure | This is not evidence of a robot-source compilation error. |
| Direct `javac -source 8 -target 8 -proc:none` on all 43 TeamCode files | Passed | TeamCode compiles against the cached pinned dependencies and Android 35 compile classes. |
| Direct Java compilation of all 113 repository Java files | Passed | Both modules' Java sources compile when supplied cached dependencies, generated resource classes, and generated BuildConfig. |
| Trigger, axis mapping, task cancellation, feed cleanup, and invalid motor command reproductions | Findings reproduced | See the evidence table below. |
| RED/BLUE pose mirroring through pinned Pedro | Passed representative position/heading round trip | The helper correctly mirrors the tested pose; this does not establish field clearance or hardware behavior. |

The fallback compilations do **not** verify a clean Android build, annotation processing, resource regeneration/merging, DEX conversion, APK packaging, installation, older Android runtime compatibility, or robot operation. The whole-repository fallback used existing generated Android resource classes and BuildConfig. Its bootstrap-classpath warning and the controller Activity's deprecation note were not source errors.

The robot does not exist physically yet. Mechanical safety, wiring, real motion, current draw, scoring, trajectory clearance, match duration, and tuning remain unverified. All numerical calibration and placeholder-value work is collected in the final appendix and is excluded from the software verdict.

### Evidence classification and priorities

**Reproduced** means a hardware-free execution demonstrated the behavior. **Confirmed in source** means the control flow or missing implementation is directly visible. **Conditional risk** means a particular fault, device version, or intended mechanism is needed for the impact to occur.

- **P1:** Fix before the affected competition feature or powered bring-up is relied on.
- **P2:** Reliability, completeness, or performance improvement to address before competition validation.
- **P3:** Maintenance improvement; lower immediate match impact.

Several entries describe one shared defect affecting many files. They should be fixed centrally rather than independently in every autonomous routine.

## 2. Prioritized findings

| ID | Priority | Finding | Evidence / affected operation |
| --- | --- | --- | --- |
| F01 | P1 | Partial trigger release can leave shooting active; a direct full press can skip shooting | Reproduced; competition TeleOp |
| F02 | P1 | Joystick Y is inverted twice | Reproduced; competition drive mapping |
| F03 | P1 | Autonomous runs two different flywheel control strategies against one motor | Confirmed in source; every autonomous shot |
| F04 | P1 | Shot-ending callbacks share one worker with hardware control | Conditional delay/failure risk; autonomous scoring |
| F05 | P1 | Autonomous waits have no independent action deadline or recovery result | Confirmed in source; stalled path or unreachable flywheel speed |
| F06 | P1 | Path completion is treated as permission to shoot without checking arrival quality | Confirmed in source; autonomous scoring |
| F07 | P1 | No competition control selects RED alliance | Confirmed in source; all match modes |
| F08 | P1 | Shutdown is incomplete and one failed cleanup can skip later stops | Confirmed in source; stop and partial initialization |
| F09 | P1 | `cancel_and_await()` reports success before a running task exits | Reproduced; currently unused utility contract |
| F10 | P1 | Static subsystem modes are not reset between runs | Confirmed in source; repeated OpMode initialization |
| F11 | P1 | Intake/transfer commands have multiple independent owners | Confirmed in source; conflicting operator commands |
| F12 | P1 | TeleOp feeds immediately without a shooter-ready interlock | Confirmed in source; trigger press |
| F13 | P1 if hood is used | Hood servo is mapped but never commanded | Confirmed missing behavior; scoring mechanism |
| F14 | P1 | Driver controls lack complete mechanism stop and jam recovery | Confirmed missing controls; TeleOp recovery |
| F15 | P1 for tuning | Shooter tuner tunes a different controller from competition code | Confirmed in source; controller validation |
| F16 | P1 on API 24–29 | Tuning code calls `List.of()` without configured API desugaring | Conditional Android runtime failure |
| F17 | P1 for bring-up | Motion tuners lack bounded travel/fault guards and reliable local cleanup | Confirmed in source; hardware commissioning |
| F18 | P2 | Sensor health is reduced to a loaded/not-loaded boolean | Confirmed missing fault distinction |
| F19 | P2 | Autonomous pickup and delivery success are not verified | Confirmed missing outcome checks |
| F20 | P2 | Competition mode suppresses all existing operational telemetry | Confirmed in source; driver feedback and fault visibility |
| F21 | P2 | Limelight and Butine are empty scaffolds | Confirmed missing feature; optional vision scope |
| F22 | P2 | Flywheel inputs and controller state lack robust bounds/reset behavior | Confirmed missing guards; invalid commands and controller transitions |
| F23 | P2 | Automated behavioral regression tests and CI are absent | Confirmed repository gap |
| F24 | P2 | There is no independent simple fallback autonomous | Confirmed missing recovery option |
| F25 | P3 | Older documentation misstates current readiness | Confirmed documentation drift |

### F01 — Trigger handling can latch shooting on

Sources: [GamepadTrigger.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java), [Main.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java).

The trigger abstraction only emits a release event after it has entered `fullyPressed`. Main registers shooting on the ordinary press listener and stopping on the release listener.

1. Start at zero, press halfway, then release to zero. The ordinary listener runs on both transitions. `shoot()` is called again on release, and `stop_shooting()` is never called.
2. Start at zero and move directly to full press in one sampled update. Only the full-press listener runs. Main has no right-trigger full-press listener, so shooting never starts.
3. Ordinary press listeners also run on changes while releasing a partial press, rather than representing a single press edge.

**Impact:** The driver can release the trigger while the transfer continues feeding. This does not depend on flywheel gains or physical tuning.

**Correction:** Track ordinary pressed/released state independently of full-press state. Emit ordinary press once when entering the pressed range, release once when leaving it, and full-press once when entering that range. If analog change events are useful, give them their own listener. Test partial presses, direct full presses, gradual release, and jitter around state boundaries.

### F02 — The custom stick abstraction and Main disagree on Y direction

Sources: [GamepadAnalogStick.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java), [Main.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java).

The input wrapper converts raw stick Y to `-y`, explicitly defining stick-up as positive. Main negates that normalized value again. A raw stick-up value of `-1` becomes `+1` at the listener and then `-1` as `driveForward`.

**Correction:** Choose one normalization boundary and make Main consume that convention consistently. Also reconcile strafe and turn signs: Main uses positive raw X, whereas the local Pedro drive test uses negative raw X for strafe and turn. This sign inconsistency is a software contract issue; final motor wiring/directions remain in the tuning appendix. Verify the combined field-centric transform with synthetic headings and both alliances before motor tests.

### F03 — Autonomous flywheel control has two owners

Sources: [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java), [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java).

Autonomous schedules periodic `set_flywheel_velocity()` calls on the TaskManager worker. That method computes PIDF motor power. When the loop thread calls `shoot()`, it also writes motor power using a separate on/off strategy with a shooting overshoot target.

The two writers can replace one another's outputs during the same shot. The cache synchronizes individual writes, but synchronization does not decide which control strategy should own the motor. PIDF means proportional, integral, derivative, and feedforward control; the shooting method uses a different strategy rather than that controller's state.

**Correction:** Give the shooter one update method and one motor-output owner. Commands should set requested state and target; the update computes the output. If shooting requires an adjusted target or controller, switch it inside that one update. Run it on the OpMode thread or adopt an explicit single-worker ownership design that never writes the same actuator elsewhere.

### F04 — The same worker controls flywheel hardware and shot deadlines

Sources: [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), [TaskManager.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java).

TaskManager has one worker. The flywheel job performs a hub velocity read and potentially a motor write. The delayed job that signals the end of a shot is queued behind that hardware work, as are flower-intake timing callbacks. A slow or stuck worker delays those actions even while the main OpMode loop continues.

TaskManager also catches ordinary task exceptions, logs them, and repeats periodic tasks. Autonomous receives no failure result. It can continue feeding or wait for speed while its controller is repeatedly failing.

**Correction:** Measure shot and flower timing from a monotonic clock in the loop. Propagate a controller fault into the autonomous action result. A delayed callback should not be the only mechanism that ends feeding. Changing the numerical delay or thread priority cannot establish that guarantee.

### F05 — A failed action can consume the entire autonomous

Source: [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), especially `shoot_at_current_position()` and `run_until()`.

`run_until()` exits only when its supplied condition becomes true or the OpMode stops. Waiting for shooting speed has no timeout. Path following has no independent whole-path deadline or no-progress check. A disconnected encoder, jammed flywheel, invalid localization, or blocked path can prevent all later actions, including parking.

The configured Pedro end timeout applies to endpoint correction; it is not a watchdog for every point along a path. [Pedro end-constraint documentation](https://pedropathing.com/docs/pathing/reference/endconstraints).

**Correction:** Return explicit action outcomes such as completed, timed out, localization fault, or stopped. Add elapsed-time and no-progress guards and a route-level remaining-time budget. A failed scoring action should stop feeding and select a known recovery action. The existence and handling of deadlines can be implemented now; measured deadline values belong at the end of this report.

### F06 — A completed path is not necessarily a successful shooting arrival

Source: [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java).

`follow_shoot_path()` waits for `!follower.isBusy()` and immediately proceeds to shooting. It does not inspect position error, heading error, robot motion, or localization validity. Pedro can finish endpoint correction because its timeout elapsed even when all desired endpoint constraints were not met. [Pedro end-constraint documentation](https://pedropathing.com/docs/pathing/reference/endconstraints).

**Correction:** Treat path completion and permission to feed as separate conditions. Validate the shooting pose/heading and a healthy, sufficiently stationary robot before opening the feed path. If arrival is unacceptable, correct within a bounded window or skip the shot. Do not fix this by merely enlarging an endpoint timeout.

### F07 — Alliance selection is missing from the competition workflow

Sources: [LastPositionStorage.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java), [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), [AbstractTeleOp.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java).

Stored alliance defaults to BLUE. Autonomous takes that value, and the competition modes provide no RED selector. The current call graph stores alliance values already inherited from these defaults or previous runs; it has no competition path that selects RED. TeleOp also chooses BLUE whenever no valid saved pose exists. Autonomous reads the stored alliance without checking pose-storage freshness.

**Correction:** Select and display alliance during INIT, or register explicitly named RED and BLUE variants. Keep alliance configuration separate from temporary last-pose storage. Define the permitted auto-to-TeleOp handoff and reject accidental reuse after another match or manual repositioning. Expiration duration is a deferred value; the missing selection workflow is not.

### F08 — Safe stopping is incomplete and fragile under exceptions

Sources: [Robot.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java), [Main.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java), [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), [Intake.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java).

- TeleOp's stop chain stops shooter/intake and stores pose. It never explicitly stops the follower/drivetrain or transitions the flower intake to a chosen safe state.
- Intake stopping assumes both cached motors exist. Partial initialization can cause a null dereference during cleanup, hiding the original initialization failure and skipping later actions.
- Auto's protected `try/finally` starts after hardware initialization and the INIT flower command. An exception earlier does not enter that cleanup.
- Auto's final mechanism cleanup is sequential. If `FlowerIntake.up()` throws, shooter and intake stops below it are skipped. Similar ordering problems exist in Robot and Intake cleanup.

The SDK has its own stop/reset handling. The issue is that the robot's explicit cleanup does not independently cover all initialized actuators and failure paths, particularly worker activity and servo states. In pinned Pedro, `follower.stop()` changes follower mode; direct drivetrain stopping is also needed when relying on an immediate stop without another update.

**Correction:** Enclose initialization in lifecycle cleanup, make individual subsystem stops tolerate partial initialization, and attempt every stop independently. Stop/disable hardware-producing work before final actuator writes and retain the first fault for diagnosis. Define flower/hood safe-state policy with the build team; the actual positions stay deferred.

### F09 — `cancel_and_await()` does not await task-body completion

Source: [TaskManager.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java).

The method cancels the future, calls `future.get()`, and returns true on `CancellationException`. A future can become canceled immediately while its task body is still running and ignoring or finishing after interruption. The method therefore does not fulfill its documented shutdown guarantee. This was reproduced with a running task held behind a latch: the method returned true before the task's exit signal occurred. [Java Future contract](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html).

**Correction:** Track actual execution entry/exit separately, using a completion signal released in the task wrapper's `finally`. Account for tasks canceled before entry and self-cancellation from the worker. Keep timed-out work disabled from issuing future robot outputs.

**Scope:** No current competition caller uses this method. It is a latent utility defect, not the explanation for every current stop. Ordinary cooperative cancellation remains useful.

### F10 — Static mechanism modes survive a new OpMode

Sources: [Intake.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java), [Intake.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java), [FlowerIntake.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java), [Robot.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java).

Intake, transfer, scoring mode, flower pose, and hardware handles are static. Intake initialization resets the sensor-read flag but does not reset its command modes. Normal stopping resets intake/transfer modes through their off methods but leaves `scoringMode` untouched. A second run can therefore interpret the first score-mode toggle differently. Flower initialization maps servos without synchronizing the stored pose with an issued position command.

**Correction:** Prefer instance-owned hardware and per-run state. Reset all command modes in init and issue the chosen initial safe commands. Make the stored state describe the command actually sent, and explicitly reset shooter/controller state for a new run. A new `Robot` instance should not inherit mechanism behavior from a previous instance.

### F11 — Operator commands can override one another without arbitration

Sources: [Main.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java), [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java), [Intake.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java).

The left trigger's score-mode toggle writes transfer power directly. The right trigger controls Shooter state, and Shooter's SHOOT update calls `feed()` every loop. If the left trigger switches to COLLECT while SHOOT is active, the later shooter update immediately re-enables transfer. Conversely, releasing the right trigger calls `stop_feed()` and disables transfer even if the scoring-mode flag still says SHOOT.

`feed()` also starts the intake, while `stop_feed()` stops only transfer. The reproduction confirmed that a shoot/release cycle leaves intake power at its forward command. Continued collection might be intended, but it is currently a side effect rather than an explicit restoration of the driver's earlier request.

**Correction:** Keep collection request, feed request, shooter readiness, jam recovery, and disable state separate. Resolve them once per loop into the final intake/transfer outputs, with documented precedence. Restore collection according to the driver's collection request after shooting ends.

### F12 — TeleOp has no shooter-ready feed gate

Source: [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java).

`shoot()` immediately calls `intakeSubsystem.feed()` before checking flywheel speed. TeleOp never tests shooting readiness. Autonomous has an initial speed wait, but neither mode centrally enforces readiness at the subsystem boundary. A flywheel dropout during feeding also has no shared readiness/fault handling.

**Correction:** Make a shoot command request feeding and let the subsystem permit it only when the mechanism is ready. Provide a deliberate, visible operator override if required. Define whether feeding pauses after a speed drop and how long readiness must remain stable. Numerical tolerances and recovery delays are deferred.

### F13 — The hood has no implementation beyond hardware lookup

Source: [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java).

`servoHood` is constructed in init but never receives a position command. There is no initial hood command, distance/state selection, operator adjustment, or stop policy. The current Java value for a hood position cannot be tuned because the command path itself does not exist.

**Correction:** If the final design uses the hood, implement its encapsulated command and limits before tuning. If the design does not use it, remove the mandatory hardware lookup. This finding is conditional on the intended mechanical design; it does not establish what hood position is correct.

### F14 — TeleOp recovery controls are incomplete

Sources: [Main.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java), [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java).

Intake provides reverse and off methods, but Main does not bind them. Transfer reversal is similarly unreachable from competition controls. Shooter has no persistent disabled state: FLOAT is still a spinning state, and releasing the shoot trigger changes feeding rather than shutting down the flywheel. The driver has no complete software mechanism-disable/recovery control besides stopping the whole OpMode.

**Correction:** Add an explicit disable state and usable stop/reverse/recovery controls with clear precedence over scoring. Provide a robot-centric drive fallback or another agreed recovery control for a bad field heading. Keep these driver workflows visible in the controls documentation and telemetry.

### F15 — The shooter tuner validates another control system

Sources: [ShooterPidfTuner.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java), [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java).

The tuner selects `RUN_USING_ENCODER`, configures the hub's velocity PIDF coefficients, and commands `setVelocity()`. Competition Shooter selects `RUN_WITHOUT_ENCODER` and implements its own power controller. Their gains, state behavior, and update cadence are not interchangeable. The tuner also omits the competition gear-ratio conversion, which becomes a mismatch if the eventual ratio differs from its current provisional value.

**Correction:** Choose the production controller first, then exercise that same code and conversion functions from the tuner. If both controllers are intentionally retained, name them explicitly and avoid copying gains between them. Use signed velocity in diagnostic tests so `Math.abs()` does not hide a direction mismatch. Record controller version with measured tuning results.

### F16 — Java compilation can pass while tuning crashes on older Android

Sources: [PinpointTuner.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java), [ForesightTuner.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java), [build.common.gradle](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build.common.gradle).

The tuning procedures repeatedly call `List.of()`. Android provides these methods natively starting at API 30, while this project permits API 24. The Gradle files do not configure core-library desugaring. Compiling Java 8 bytecode or setting compileSdk to 35 does not by itself add these library methods to an older device. [Android List API](https://developer.android.com/reference/java/util/List), [Android API desugaring](https://developer.android.com/studio/write/java8-support).

**Correction:** Replace these calls with supported list construction, or configure and verify appropriate core-library desugaring. Validate the installed device's Android API level. This is an API compatibility gap, not an untuned numeric value; no older Android device was executed during this review.

### F17 — Bring-up procedures need their own motion limits and cleanup

Sources: [ForesightTuner.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java), [ForesightTuner.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java), [MecanumTuner.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java), [ShooterPidfTuner.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java).

Max-velocity procedures continue commanding drive until measured distance is reached. Deceleration procedures drive until a measured speed is reached. If localization remains frozen or the speed is unreachable, those powered phases have no independent deadline or travel watchdog. User-supplied tuning distances/speeds are not checked for finite positive values. Several fits return zeros on insufficient data, and other fit results are not validated before being presented as usable configuration.

Local motor/drivetrain stopping generally occurs after the loop rather than inside a `finally`. The pinned tuning base signals completion in its `finally` but does not perform actuator-specific cleanup. Mecanum's spin test can command power after `waitForStart()` returns without first rejecting a stop requested during INIT. Exceptions and interrupted setup should be covered explicitly, even with SDK stop handling present.

**Correction:** Validate inputs and outputs, add elapsed-time/travel/no-progress guards, distinguish aborted/invalid measurements from legitimate zero results, check active state before issuing powered commands, and put every powered procedure's local cleanup in `finally`. Implement these guards before using the tools on the newly assembled robot.

### F18 — Sensor faults and an empty intake look the same

Source: [Intake.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java).

Distance readings are cached and compared to thresholds, but there is no validity/fault status. Nonfinite readings simply fail the comparison and become “not loaded.” Callers cannot distinguish an empty intake from unavailable data. Read exceptions propagate, and there is no deliberate degraded-operation policy. A negative invalid reading could also pass a less-than comparison, depending on the sensor's failure behavior.

**Correction:** Publish a paired sensor snapshot with timestamp, validity, and occupancy state. Reject invalid readings, expose a fault, and define the safe operator override. Add debounce/hysteresis logic to avoid occupancy-state chatter. Actual distances and read cadence are deferred to the appendix.

### F19 — Autonomous assumes collection and delivery happened

Sources: [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), all nine competition route classes.

Pickup helpers turn on intake during motion but never check whether a load was acquired. Shots end according to elapsed time without verifying departure, a jam, or scoring. Subsequent hive choices follow the planned sequence even after a missed pickup/delivery. The two carry-load routes finish their planned motion and stop intake, but do not confirm the load they intend to carry into TeleOp.

**Correction:** Track collected, partially collected, empty, jammed, and unknown outcomes using the sensing the final mechanism supports. Add bounded retries or skip behavior. If hive tilt cannot be sensed directly, keep delivery certainty separate from the route's expected scoring sequence. A path endpoint is not evidence that a game element was collected.

### F20 — Competition mode removes operational feedback

Sources: [CompConfig.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java), [AbstractTeleOp.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java), [AbstractAuto.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java).

With COMP_MODE enabled, the existing telemetry paths are suppressed unless loop-time display is separately enabled. No always-available competition status channel reports alliance, shooter ready/disabled, bypass selection, loaded/fault state, current auto action, or timeout/failure. In particular, the missing saved-pose message is hidden in competition mode. Bypass toggling itself provides no state feedback.

**Correction:** Separate essential driver/fault status from verbose diagnostics. Keep a small, rate-limited Driver Station status view during competition. Disable Panels and detailed plotting independently. Thread-task errors should be surfaced to the controlling action, not only written through Android logging.

### F21 — Vision is planned but not integrated

Sources: [Limelight.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Limelight.java), [Butine.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Butine.java), [AbstractTeleOp.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java).

Both vision classes are empty. Main's proposed Limelight update listener is commented out, and the video-enable hook does nothing. No pipeline selection, result-age validation, MegaTag2 orientation input, pose fusion/rejection, targeting, or vision shutdown exists in competition code.

**Correction:** Define which vision behaviors are required for the competition strategy and implement their lifecycle and fault handling if selected. Otherwise document that the match code currently relies on Pinpoint and the stored field model. Vision is not automatically mandatory for driving or the existing routes. The presence of an SDK Limelight sample is not a competition integration.

### F22 — Invalid commands and controller transitions are not guarded centrally

Sources: [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java), [Shooter.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java), [CachedMotor.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedMotor.java), [CachedServo.java](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedServo.java).

The target setters clamp below zero but do not reject NaN/infinity or impose a design maximum. Controller calculations likewise accept invalid velocity data. The cached wrappers do not validate finite commands: after a prior motor command, NaN fails the change comparison and can leave the prior motor output in effect. That behavior was reproduced.

PID integral state is not bounded against saturated output, controller timing spans the interval since init/previous update, and stop does not reset controller history or establish a disabled state. Long gaps and changed targets can produce stale integral/derivative transients independently of the eventual gains.

**Correction:** Validate finite commands and sensor inputs, apply design bounds at the subsystem boundary, reset/freeze controller history on disable and strategy changes, and add anti-windup. Anti-windup prevents the integral accumulator growing while actuator saturation prevents the requested correction. Define the fault response explicitly; do not silently continue the previous output after an invalid command. Numerical limits remain deferred.

### F23 — There is no persistent behavioral regression suite

The repository contains driver-operated tuning/testing procedures but no automated unit/integration test suite for the robot abstractions and no CI build workflow. The hardware-free checks run for this report were temporary audit checks, not committed production tests.

**Correction:** Add focused tests for the actual failure contracts: trigger press/release sequences, drive transform signs, one shooter output owner, feed interlocks, partial-init cleanup, actual task completion, timeout outcomes, invalid sensors, RED/BLUE route construction, and repeated-run state reset. Add a reproducible clean Android build check. Avoid tests that merely restate every getter or constant.

### F24 — The fallback route depends on scoring hardware too

Every registered competition autonomous starts with a preload shot. Shared initialization also requires intake, transfer, both range/color sensors, both flower servos, shooter, and hood. There is no simple drive/park-only alternative that can run when a scoring subsystem is unavailable or a scoring action fails.

**Correction:** Add a deliberate, named simple autonomous fallback and a route-level recovery policy. Separate required drive hardware from optional scoring hardware for that fallback. Choose its legal path and final pose with the team; this report does not invent a competition strategy or certify a parking location.

### F25 — Historical documentation can mislead the next change

Sources: [Competition-Readiness-TODO.md](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Readiness-TODO.md), [Constants-errors-explanation.md](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Constants-errors-explanation.md), [taskScheduler-errors.md](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/taskScheduler-errors.md), [TeamCode readme](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/readme.md).

The old readiness TODO says Main has no controls, Robot does not initialize mechanisms, and the auto directory is empty. Those statements are no longer true. The constants document describes older dependency/API problems, and the task-scheduler report includes historical compile errors and replacement races that the current implementation already addresses. TeamCode's default readme still describes an empty module.

**Correction:** Mark older reports as historical and update the current checklist from verified code and test results. Use this report's findings instead of reintroducing already-fixed naming/import/registration problems.

## 3. Performance improvements

These are source-level opportunities, not measured Control Hub benchmarks. No numerical speedup or guaranteed loop frequency is claimed.

### O01 — Put time-critical output and timing decisions in one loop

Resolving F03/F04 gives the largest structural benefit: one sensor/control/output cycle avoids duplicate flywheel reads, motor-output arbitration, background hub contention, and queued safety timing. TaskManager uses fixed delay, so its next run occurs after the previous task finishes plus the requested delay; its requested period is not a guaranteed fixed sampling interval.

Keep background work for tasks with a justified independent lifecycle. A second control thread does not make serialized hub communications faster by itself.

### O02 — Enable appropriate bulk reads in autonomous and commissioning

TeleOp explicitly uses MANUAL caching and clears each hub at the top of the loop. Auto and the shooter tuner do not select a bulk-cache mode; FTC's OpMode reset restores hubs to OFF. Multiple eligible hub reads can therefore become separate transactions. Choose AUTO for simple independent read patterns or a single-threaded MANUAL snapshot with one clear before each cycle's reads. The existing [SDK bulk-read sample](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java) explains both modes.

Bulk reads do not combine arbitrary I2C devices such as the range/color sensors or Pinpoint. Avoid promising a speedup for those devices from motor bulk caching. Do not introduce shared MANUAL caching across unsynchronized worker and loop reads.

### O03 — Remove redundant calculations and reads

- Shooter `update()` calculates a nearest-hive result even while bypass mode ignores the distance-derived target. Put that computation inside the non-bypass branch unless its result is needed for status.
- Share one velocity measurement per control cycle with readiness, controller, and telemetry consumers. Auto currently reads it on both loop and worker threads.
- Keep intake occupancy evaluation on one paired snapshot instead of repeatedly asking accessors to refresh/check cache state.
- Cache a pose once when assembling telemetry or shooter inputs instead of calling `follower.pose()` repeatedly.

Keep accuracy and data freshness ahead of reducing method-call count; the expensive operations are hardware transactions and avoidable allocations.

### O04 — Build autonomous paths during INIT

Each route constructs its next `Path` inside `run_autonomous()`. Path creation includes curves, heading interpolation, and per-point mirrored poses. Build the chosen alliance's path objects before START, once the route/alliance selection is finalized. Then the match sequence only references prepared paths and action state.

This also makes route construction failures visible during INIT and makes synthetic route checks easier. Do not preserve a path prepared for another alliance after changing the selection.

### O05 — Reduce avoidable hot-loop allocation

Current loop allocations include `HiveDistance`, field-centric `DrivePowers`, and periodic `Units.Length` distance wrappers. Some allocation is normal and controlled by library APIs. Optimize the avoidable project allocations after profiling, for example primitive sensor snapshot fields and deferred hive computations.

The listener and periodic callback collections use CopyOnWriteArrayList, which is appropriate for infrequent registration and frequent reading. Do not replace it solely for style. Avoid registering/removing callbacks on every tick. Sample arrays/lists in tuners should be bounded or sampled deliberately instead of growing as fast as a sensor-poll loop can execute.

### O06 — Measure useful loop timing and throttle status

Main puts `follower.manual(powers)` after the base loop's `follower.update()`. Pinned Pedro applies stored manual powers during update, so the new request is applied on the next cycle. This adds one cycle of command latency. Reorder localization/input/control/output deliberately while preserving fresh heading data; do not call full follower update twice just to compensate.

Current “Loop ms” measures the work inside the loop, not the full interval between consecutive loop starts, and omits telemetry transmission after the measurement. Record both work duration and actual cycle interval, including occasional long cycles. Keep essential status at a controlled transmission rate.

Some tuner loops use `isStopRequested()` without `idle()` or deliberate sampling. Those can poll/allocate continuously. By contrast, FTC 12's `opModeIsActive()` already calls `idle()` when active; Main and auto's central wait use it. Their loops were not classified as missing all cooperative yielding.

## 4. Missing functionality and scope decisions

| Area | Current implementation | Decision / work needed |
| --- | --- | --- |
| Match configuration | Alliance inherited from static storage; pose handoff has a time check | Explicit alliance/starting-pose workflow and visible confirmation |
| Driver recovery | Shoot, score-mode toggle, flower toggle, pose reset, bypass and RPM adjustment | Disable, intake/transfer off/reverse, clear control precedence, heading recovery |
| Shooter | Custom controller and state machine; tuner uses hub control | One production controller, shared readiness gate, fault state, actual hood commands if used |
| Mechanism protection | Command methods exist; `transferStallCurrent` is unused | Decide current/stall/jam detection and recovery appropriate to the finished mechanism |
| Vision | Empty Limelight/Butine classes | Define required vision scope, then implement it or explicitly exclude it |
| Autonomous recovery | Planned sequences and stop checks | Deadlines, action outcomes, collection certainty, arrival checks, remaining-time handling |
| Simple fallback | No scoring-independent mode | Choose and implement a minimal fallback with independently required hardware |
| Planned turret / servo gate | Mentioned as future parts in project instructions; no subsystem implementation | Only required if included in the final design. Define limits and transfer/gate/turret interlocks before adding commands. |
| Competition feedback | Diagnostics disabled globally | Minimal driver and fault status that remains enabled |
| Build/release verification | Cached Java compilation works | Clean Android build, device install/start, configuration backup, reproducible tested release |

No specific scoring rule, hive-tilt count, opponent route legality, inspection requirement, or match deadline is certified by this source audit. Existing route documentation records the team's scoring assumptions; verify them against the applicable event rules before adopting the strategy. The missing software support identified above can be developed before the robot is built.

## 5. Autonomous coverage

Every route begins at the shared LOWER shooting pose and attempts a preload shot. All inherit F03–F08 and F19. Route names below are Java class names; Driver Station names for the seven imported routes are recorded in [Visualizer-Autonomous-Routes.md](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Visualizer-Autonomous-Routes.md).

| Route | Actions after preload | Intended finish |
| --- | --- | --- |
| BothFlowersNoGarden | Far pickup → UPPER shot → mid pickup → UPPER shot | Park |
| SoloWithSteal | Opponent pickup → UPPER shot → far pickup → UPPER shot → mid pickup → LOWER shot → garden pickup → LOWER shot | Park |
| FarMidGardenPark | Far pickup → UPPER shot → mid pickup → UPPER shot → garden pickup → UPPER shot | Park |
| FarMidPark | Far pickup → UPPER shot → mid pickup → UPPER shot | Park |
| GardenFarMidPark | Garden pickup → UPPER shot → far pickup → UPPER shot → mid pickup → LOWER shot | Park |
| GardenFarPark | Garden pickup → UPPER shot → far pickup → UPPER shot | Park |
| OpponentFarGardenPark | Opponent pickup → UPPER shot → far pickup → UPPER shot → garden pickup → LOWER shot | Park |
| OpponentFarMidGardenPickup | Opponent pickup → UPPER shot → far pickup → UPPER shot → mid pickup → LOWER shot → garden pickup | Carry garden load; no park path |
| OpponentFarMidPickupPark | Opponent pickup → UPPER shot → far pickup → UPPER shot → mid pickup | Park carrying mid load |

The missing final shot/park in the two carry-load routes is documented intent, not an accidental missing step. The existing fixed-RPM bypass in autonomous is also deliberate; it was not reported as a failure to use the distance model.

The shared path factories apply RED corrections before BLUE mirroring. Source inspection found no obvious sequence-level endpoint mix-up in these nine routines. The previously recorded comprehensive Visualizer checks are historical evidence; this audit did not rerun all of them. Field clearance, tangent-heading behavior, route timing, and pickup/shot repeatability remain physical validation tasks.

## 6. Checks that should not become false bug reports

- **No current Java compile errors were found.** All 113 repository Java files passed the cached compilation check. Historical import/class-name failures are not current blockers.
- **CachedMotor deliberately sends an exact transition to zero.** Its tolerance does not suppress an ordinary nonzero-to-zero stop command. Invalid-command and competing-owner issues are separate.
- **Task replacement identity is already protected.** Conditional `remove(name, entry)` and the scheduling lock address old replacement/registration races described in historical documents.
- **The flower callback cleanup has a real guard.** Its shared lock and `pathActive` flag prevent the normal canceled callback from lowering the intake after that path's cleanup. Worker delays and broader lifecycle failures remain issues.
- **FTC resets hub caching on an OpMode reset.** Cached FTC 12 source shows `OpModeManagerImpl` resets devices and `LynxModule.resetDeviceConfigurationForOpMode()` sets caching OFF. MANUAL caching was not reported as automatically leaking into the next OpMode.
- **Pedro `twist()` is not a displacement-per-loop error here.** Pinned `MotionState.ofVelocity()` converts velocity into a robot-frame twist. The tuning procedures' uses of twist were not classified as a missing division by elapsed time.
- **Mirroring did not fail the representative execution check.** RED `(59.25, 9, π/2)` became BLUE `(84.75, 9, π/2)` and returned to the original under a second mirror.
- **Out-of-field Bézier control points are not automatically out-of-field robot paths.** The curve must be sampled with the robot footprint. A control point is not a waypoint the robot necessarily visits.
- **High controller gains, zero sensor thresholds, provisional RPM, servo endpoints, pod offsets, and route coordinates were excluded from severity scoring.** Their values are listed only in the final appendix.

## 7. Recommended implementation and acceptance order

1. **Make operator behavior deterministic:** F01/F02, per-run reset, explicit alliance selection, mechanism request arbitration, disable/recovery controls.
2. **Make hardware ownership and stopping reliable:** one shooter output owner, safe partial-init cleanup, independent stop attempts, corrected task completion semantics, finite-command guards.
3. **Make autonomous failures recoverable:** bounded actions, arrival validation, loop-owned timers, fault propagation, pickup outcomes, minimal fallback.
4. **Make commissioning trustworthy:** production-controller shooter tuner, Android API compatibility, guarded powered procedures, invalid-result rejection.
5. **Add essential status and selected missing mechanisms/vision:** hood and any final gate/turret protection, optional Limelight integration, driver fault feedback.
6. **Verify the complete Android build and focused behavioral tests.** Retain the currently successful Java compilation as a limited check, not an APK acceptance result.
7. **After assembly, tune and validate physically:** follow the deferred register at the end and perform repeatable bench, field, and full-match trials.

### Acceptance checks that can run before assembly

- [ ] Trigger sequences emit the correct press, full-press, and release events exactly once.
- [ ] Raw joystick inputs and both alliance transforms produce the agreed field commands.
- [ ] A new OpMode always starts from defined mechanism requests and selected alliance.
- [ ] Only one control path owns each actuator's output.
- [ ] Requested feed is blocked while disabled, unready, or faulted; recovery/override precedence is tested.
- [ ] Every wait has a bounded result and every failed action selects a deliberate next state.
- [ ] Actual task exit, not canceled-future status, controls any shutdown completion claim.
- [ ] A failed actuator stop cannot prevent all other initialized actuators from being stopped.
- [ ] Invalid sensor/pose/RPM values create an observable fault and the chosen safe output.
- [ ] Production tuner and competition controller use the same conversions and behavior.
- [ ] A clean APK build, registration check, and runtime compatibility check pass on the selected Android device/environment.

### Acceptance checks requiring the assembled robot

- [ ] Stop during INIT, powered action, path, shot, jam recovery, and fault handling leaves the agreed safe physical state.
- [ ] Verify directions, localization, field-centric control, shooting readiness, and mechanism limits at controlled power.
- [ ] Introduce controlled faults: absent load, missed pickup, blocked path, unreachable shooter speed, invalid/disconnected sensor, and interrupted sequence. Verify bounded recovery.
- [ ] Verify both alliances and fresh/expired/manual-repositioned pose handoff.
- [ ] Validate every selected route with real robot footprint, representative battery state, and game elements.
- [ ] Run repeated complete mock matches and record success, failure reason, route time, loop timing, and physical condition afterward.

Nothing in these acceptance lists is marked complete solely because the source compiles.

## 8. Hardware-free reproduction record

The audit executed the compiled repository classes with fake SDK motor/sensor interfaces. The task test deliberately kept its task body alive after interruption, then released it and shut down the worker. The fake hardware tests did not communicate with hubs.

| Experiment | Observed result |
| --- | --- |
| Partial trigger: `0 → 0.5 → 0` | `[press:0.5, press:0.0]`; no release callback |
| Direct full trigger: `0 → 1 → 0` | `[full, release:0.0]`; no ordinary press callback |
| Raw left-stick Y `-1` | Wrapper listener receives `+1`; Main computes forward `-1` before field rotation |
| Cancel active task that does not exit on interruption | `cancel_and_await()` returns true while task body is still running |
| Actual `shoot()` then `stop_shooting()` with fake motors/sensors | Intake stays at forward power; transfer becomes zero |
| Cached motor command `0.5 → NaN` | Previous `0.5` output remains; invalid value is not rejected |
| Pose mirror and second mirror through Pedro 3.0.1 | Representative geometry/heading round trip passed |

These observations support the corresponding findings; they do not measure physical scoring or stop response.

## 9. Requested changes / team notes

Use this space to specify implementation choices before changing powered behavior.

- Production shooter controller choice:
- Intended ordinary trigger / full-trigger behavior:
- Agreed drive-axis and field-frame convention:
- Operator collection/feed/disable/reverse precedence:
- Required hood, gate, turret, and vision scope:
- Chosen simple fallback autonomous and fault recovery policy:
- Owners / follow-up work:

**Written by Codex (GPT-6), with access to this repository and the current context of F.R.O.G.-A_rise_from_dawn.**

## Appendix — Deferred constants, values, and physical tuning

**This is the final section intentionally.** The robot is not assembled, so the following values are not graded as logic errors and do not lower the software-readiness verdict. Retain them as provisional until they can be measured. This report does not choose replacements or claim that a plausible-looking value is tuned.

| Area / location | Deferred values or calibration | Validation when hardware is available |
| --- | --- | --- |
| `core/pedro/Constants.java`: drivetrain | Motor names `leftFront`, `leftRear`, `rightFront`, `rightRear`; directions; braking behavior | Match device configuration and physical wheel directions; verify individual and combined motion. |
| `Constants.java`: Pinpoint | Device name `pinpoint`; pod type; x/y pod directions; zero pod offsets; distance/resolution units and scale | Measure offsets, select actual pods, verify units and axes, calibrate and compare measured distance/heading. |
| `Constants.java`: Foresight | Forward/lateral/heading controller gains; feedforward; linear/quadratic braking coefficients; braking power; achievable speeds; natural deceleration; acceleration/velocity/deceleration constraints; braking/coasting policy | Run guarded commissioning procedures on the finished drive and then validate path following under representative load/battery. |
| `Constants.java`: path endpoint conditions | Parametric, velocity, translation, heading, and endpoint correction timeout values | Measure acceptable arrival behavior. Keep independent action watchdogs separate from endpoint tuning. |
| `core/pedro/PathConstraints.java` | The unused `autoPilot` configuration, including its provisional constraint values | Decide whether this configuration is needed; if used, confirm each field's physical units and tune it. It currently has no competition caller. |
| `core/pedro/PoseMirroring.java` and route geometry | Field center used for mirroring; field origin, dimensions, and heading convention | Verify field-frame agreement with actual layout/localization. The representative software mirror check passed. |
| `opmodes/auto/structure/OptimalPoses.java` | All ideal start/pickup/shoot/park poses, headings, control points, and field-specific corrections | Check sampled curves and swept robot footprint; validate pickup/shot/park placement. Do not assume the robot visits control points. |
| `opmodes/auto/comp/*.java` | `SHOT_DURATION_SECONDS`; all flower-lowering delays; each per-point `hotfix()`; estimated-duration/last-seconds intake parameters if the corresponding overload is used | Measure travel and collection timing; confirm feeding duration and mechanisms clear the field objects. |
| `AbstractAuto.java` | Flower lift-after duration and shooting-speed tolerance; values for newly implemented whole-action deadlines/readiness windows | Measure mechanism travel and shooter settling; set conservative bounded timing after guard logic exists. |
| `opmodes/teleop/AbstractTeleOp.java` and `Main.java` | Default/reset poses; drive speed/veto scales; joystick deadband; RPM adjustment step; chosen driver heading offset | Verify agreed driver convention and useful recovery positions; tune feel and scales with drivers. The extra Y inversion is a separate logic defect. |
| `core/inputs/GamepadTrigger.java` | Full-press and release thresholds | Set comfortable thresholds and hysteresis after correcting event-state logic. |
| `core/control/LastPositionStorage.java` | Saved-pose validity duration | Measure/setup the auto-to-TeleOp transition workflow. Explicit alliance selection and freshness policy must exist regardless of the selected duration. |
| `subsystems/Intake.java` | Intake/transfer powers, device names/directions, zero-power policy, empty upper/lower distance thresholds, read interval, future stall/current limits | Measure empty/occupied/partial occupancy and faults; characterize load/jam current; validate collection/transfer behavior. Current zero distance thresholds are acknowledged placeholders. |
| `subsystems/FlowerIntake.java` | Servo names/directions, down/up positions, motion duration, mechanical travel limits | Establish safe positions and mirrored linkage behavior, then verify initial/stop commands and timed motion. |
| `subsystems/Shooter.java`: motor conversion | `TICKS_PER_REV`, gear ratio, motor name/direction, zero-power behavior | Verify actual encoder counts and motor-to-flywheel ratio. Share conversions with the chosen tuner. |
| `Shooter.java`: control values | `K_P`, `K_I`, `K_D`, `K_F`, controller range, STANDARD/FLOAT RPM, shooting overshoot, future maximum RPM and fault limits | Tune the selected single production control strategy; measure stable speed and disturbance response. |
| `Shooter.java`: target model | `calculate_flywheel_velocity()` currently returns distance as its placeholder result; hive x/y/z values and future hood/distance calibration | Collect shot-distance/velocity/hood data and implement the chosen calibrated model with explicit units and a valid range. The current placeholder model is deferred rather than graded as a numeric defect. |
| `Shooter.java`: hood | Servo name, endpoints, initial/scoring/safe positions and distance relationship | Implement the missing command path first, then measure physical limits and shot behavior. |
| `opmodes/teleop/test/ShooterPidfTuner.java` | Default RPM, gains, gain steps, RPM step, update period | Establish production controller parity first; then choose useful safe tuning ranges and sampling. |
| `core/pedro/procedures/*.java` | Trial distances/speeds/powers, durations, thresholds, sample counts, fit assumptions, and numeric model factors | Validate guarded tests and fit quality on the real drivetrain; reject incomplete or invalid trials. These are commissioning values, not match-ready settings. |
| `core/hardware/CachedMotor.java`, `CachedServo.java`, and wrapper call sites | Command-change tolerances | Compare communication savings against control accuracy and mechanism response. Exact motor stop behavior already exists; add finite-command handling independently. |
| Final build / optional future mechanisms | Hardware ports/configuration, current/temperature/load acceptance limits, turret/gate travel bounds and timings, vision camera mounting/calibration/pipelines if used | Record measured configuration and calibration with the known-good release; define and verify physical interlocks for the actual design. |

Keep measured values, units, controller type, hardware configuration, test date, and battery/load conditions together. Tune only after the logic and protection that consume these values are implemented and tested.
