# Repository naming convention audit

Implementation completed: renamed 266 declarations across 31 TeamCode Java files, updated references and Javadocs, and recorded PascalCase class filenames and lowercase opmodes/teleop package paths in Git. All 33 TeamCode files compile to Java 8 bytecode against the cached dependencies; the follow-up naming scan finds zero actionable declarations. The full Gradle Android build could not run because its daemon fails to establish a loopback connection. The audit findings below are historical: PascalCase enum/unit types, API overrides, mutable static-final references, bundled FTC/build files, and tool/resource/document filename exceptions were retained.

Checked on 6 October 2026. This report evaluates the naming rules in your message and identifies where they differ from the existing rules in [Agents.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/Agents.md:38>).

## Scope and method

Inventoried all 151 tracked and nonignored untracked repository files present before this audit. Parsed all 103 Java source files: 33 in TeamCode and 70 in FtcRobotController. The Java parser inspected 181 named type declarations, 581 method declarations, and 1,867 variable declarations, including parameters, locals, fields, and 44 enum members. There were no Java parsing errors. Build outputs, Git internals, downloaded dependencies, and binaries were excluded from declaration analysis; their repository filenames were included in the inventory.

Gradle scripts, Gradle wrapper scripts, Android resource XML, and other repository filenames were also checked. Documentation examples and code inside strings/comments were not counted as executable declarations. No robot source, build configuration, or existing documentation was changed. This is a naming audit, not a compilation or robot-behavior test.

Locations indicate declaration starts, which can be on an annotation preceding the method. Overloads and repeated parameters count as separate declarations. Conditional categories can overlap, so the counts below should not be summed.

## Rules and conflicts

- Functions: snake_case. Single lowercase words such as `init`, `update`, `stop`, and `execute` already comply. Constructors follow class names and are not counted as functions.
- Classes and interfaces: PascalCase. Acronyms are allowed; names such as `ShooterPidfTuner` and `APInterpolation` were not rejected merely for acronym styling.
- Constants: UPPER_SNAKE_CASE, also called SCREAMING_SNAKE_CASE, for example `MAX_SPEED`. This is the name for the all-capitals style you described.
- Variables and objects: assumed camelCase, using the existing Agents.md rules because your message leaves their convention undecided. These findings are conditional on retaining that rule.
- Enum members: UPPER_SNAKE_CASE. All 44 declared enum members comply.
- Enum types: your message says all caps, while Agents.md says PascalCase. The 13 TeamCode enum types are listed separately as literal-message differences; they currently satisfy Agents.md.
- Units/poses: your wording could mean fixed values, every value whose type represents a unit/pose, or the types themselves. The report lists all non-uppercase unit/pose value declarations and the 11 unit classes separately. Their current PascalCase class names satisfy the class rule. Applying all caps to every unit/pose value would also override the camelCase variable/object rule.
- Filenames: your message says camelCase. For public top-level Java types in this Android/Java build, the source filename must match the type name, so a public `TaskManager` belongs in `TaskManager.java`. PascalCase public types and camelCase filenames cannot both be enforced here. Current public type names match their filenames. [Java specification, §7.6](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.6).
- Framework/API contracts: inherited methods such as `runOpMode`, `toString`, and Pedro's `runTuningOpMode` retain the names required by their parent APIs. Calling SDK methods such as `setPower` or accessing SDK fields such as `left_stick_y` is not a declaration violation in your project.

The listed rules are a project convention, not a uniform official FTC SDK naming standard. FIRST's own samples use camelCase methods such as `runOpMode` and `setPower`, camelCase variables such as `leftDrive`, and sample class names containing underscores. The iterative lifecycle also uses `init_loop`. [FIRST linear sample](https://github.com/FIRST-Tech-Challenge/FtcRobotController/blob/master/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Linear.java), [FIRST iterative sample](https://github.com/FIRST-Tech-Challenge/FtcRobotController/blob/master/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Iterative.java).

## TeamCode results

| Category | Declaration count | Interpretation |
| --- | ---: | --- |
| Class names outside PascalCase | 8 | Clear violations |
| Project-controlled methods outside snake_case | 166 | 160 ordinary declarations plus 6 overrides of project-owned hooks |
| Named constants outside UPPER_SNAKE_CASE | 11 | Scalar constants and intended fixed unit values |
| Variables/objects outside camelCase | 22 | Assumes the existing variable/object rule |
| Other lowercase static-final references | 7 | Mutable containers/configuration; constant policy decision |
| Framework-owned camelCase overrides | 33 | Required API names; exceptions |
| Enum types outside all caps | 13 | Literal user rule; currently valid PascalCase |
| Unit/pose values outside all caps | 58 | Conditional; includes 3 constants already counted above |
| Unit classes outside all caps | 11 | Conditional; conflicts with PascalCase class rule |
| Java filenames outside camelCase | 25 | Literal differences; Java filename exceptions needed |
| Package declarations containing uppercase letters | 6 | Additional findings under Agents.md's lowercase package rule |
| Public top-level type/filename mismatches | 0 | No mismatches found |
| Enum-member capitalization violations | 0 | All declared members comply |
| Interface names outside PascalCase | 0 | No violations found |

The method list includes wrappers `CachedMotor.setPower` and `CachedServo.setPosition`: they wrap SDK objects but do not implement SDK interfaces, so their declared names are controlled by this project. The six local overrides are `SoloWithSteal.startingPose`, `SoloWithSteal.runAutonomous`, and `main.onInit/onStart/opModeLoop/opModeStop`; they can be renamed together with the corresponding abstract declarations.

The unit helpers `A`, `mA`, `N`, and `Nm` preserve SI symbol capitalization but violate the literal function rule. Decide whether SI-symbol helpers deserve an exception; mechanically lowercasing them can remove useful distinctions between symbols.


## Class-name violations

Rename the type, its constructors, imports/usages, and its Java file together if applying PascalCase. The camelCase filename rule needs a Java exception.

### [core/control/lastPositionStorage.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java>)

- [Line 9](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:9>): `lastPositionStorage` → `LastPositionStorage` (CLASS).

### [core/control/logManager.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LogManager.java>)

- [Line 9](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LogManager.java:9>): `logManager` → `LogManager` (CLASS).

### [core/control/periodicRegistry.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java>)

- [Line 17](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java:17>): `periodicRegistry` → `PeriodicRegistry` (CLASS).

### [core/control/taskManager.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java>)

- [Line 36](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:36>): `taskManager` → `TaskManager` (CLASS).

### [core/pedro/pathConstraints.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PathConstraints.java>)

- [Line 5](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PathConstraints.java:5>): `pathConstraints` → `PathConstraints` (CLASS).

### [opModes/teleOp/comp/main.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java>)

- [Line 16](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:16>): `main` → `Main` (CLASS).

### [subsystems/intake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java>)

- [Line 15](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:15>): `intake` → `Intake` (CLASS).

### [subsystems/shooter.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java>)

- [Line 12](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:12>): `shooter` → `Shooter` (CLASS).


## Project-controlled function violations

Suggested snake_case spellings are illustrative, not applied changes. Renaming project-owned hooks requires updating both declarations and overrides. Annotation-driven tuner methods should also be checked for changes to their displayed names.

### [core/control/CompConfig.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java>)

- [Line 14](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:14>): `CompConfig.panelsEnabled` → `CompConfig.panels_enabled` (boolean).
- [Line 18](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:18>): `CompConfig.telemetryEnabled` → `CompConfig.telemetry_enabled` (boolean).
- [Line 22](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:22>): `CompConfig.videoEnabled` → `CompConfig.video_enabled` (boolean).
- [Line 26](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:26>): `CompConfig.loopTimeTelemetryEnabled` → `CompConfig.loop_time_telemetry_enabled` (boolean).

### [core/control/lastPositionStorage.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java>)

- [Line 18](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:18>): `lastPositionStorage.storeData` → `lastPositionStorage.store_data` (void).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:25>): `lastPositionStorage.validDataAvailable` → `lastPositionStorage.valid_data_available` (boolean).
- [Line 29](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:29>): `lastPositionStorage.getLastPosition` → `lastPositionStorage.get_last_position` (Pose).
- [Line 33](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:33>): `lastPositionStorage.getCurrentAlliance` → `lastPositionStorage.get_current_alliance` (Alliance).

### [core/control/periodicRegistry.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java>)

- [Line 31](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java:31>): `periodicRegistry.runAll` → `periodicRegistry.run_all` (void).

### [core/control/taskManager.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java>)

- [Line 67](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:67>): `taskManager.runOnce` → `taskManager.run_once` (void).
- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:71>): `taskManager.scheduleDelayed` → `taskManager.schedule_delayed` (void).
- [Line 75](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:75>): `taskManager.runOnceDelayed` → `taskManager.run_once_delayed` (void).
- [Line 115](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:115>): `taskManager.toMillis` → `taskManager.to_millis` (long).
- [Line 164](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:164>): `taskManager.causedByInterrupt` → `taskManager.caused_by_interrupt` (boolean).
- [Line 214](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:214>): `taskManager.cancelAndAwait` → `taskManager.cancel_and_await` (boolean).
- [Line 257](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:257>): `taskManager.stopAll` → `taskManager.stop_all` (void).
- [Line 303](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:303>): `taskManager.getRunningTaskNames` → `taskManager.get_running_task_names` (Set<String>).
- [Line 307](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:307>): `taskManager.getTelemetry` → `taskManager.get_telemetry` (String).
- [Line 317](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:317>): `taskManager.isRunning` → `taskManager.is_running` (boolean).
- [Line 322](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:322>): `taskManager.taskCount` → `taskManager.task_count` (int).
- [Line 329](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:329>): `taskManager.ensureExecutor` → `taskManager.ensure_executor` (ScheduledExecutorService).
- [Line 354](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:354>): `taskManager.logError` → `taskManager.log_error` (void).

### [core/hardware/CachedMotor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedMotor.java>)

- [Line 16](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedMotor.java:16>): `CachedMotor.setPower` → `CachedMotor.set_power` (void).

### [core/hardware/CachedServo.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedServo.java>)

- [Line 16](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedServo.java:16>): `CachedServo.setPosition` → `CachedServo.set_position` (void).

### [core/inputs/GamepadAnalogStick.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java>)

- [Line 12](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java:12>): `GamepadAnalogStick.addUpdateListener` → `GamepadAnalogStick.add_update_listener` (void).
- [Line 16](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java:16>): `GamepadAnalogStick.removeUpdateListener` → `GamepadAnalogStick.remove_update_listener` (void).
- [Line 20](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java:20>): `GamepadAnalogStick.clearUpdateListenerList` → `GamepadAnalogStick.clear_update_listener_list` (void).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java:25>): `GamepadAnalogStick.invertY` → `GamepadAnalogStick.invert_y` (double).

### [core/inputs/GamepadAnalogSticks.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java>)

- [Line 13](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:13>): `GamepadAnalogSticks.addUpdateListener` → `GamepadAnalogSticks.add_update_listener` (void).
- [Line 17](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:17>): `GamepadAnalogSticks.removeUpdateListener` → `GamepadAnalogSticks.remove_update_listener` (void).
- [Line 21](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:21>): `GamepadAnalogSticks.clearUpdateListenerList` → `GamepadAnalogSticks.clear_update_listener_list` (void).

### [core/inputs/GamepadButton.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java>)

- [Line 14](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:14>): `GamepadButton.addButtonPressListener` → `GamepadButton.add_button_press_listener` (void).
- [Line 18](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:18>): `GamepadButton.removeButtonPressListener` → `GamepadButton.remove_button_press_listener` (void).
- [Line 22](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:22>): `GamepadButton.clearButtonPressListeners` → `GamepadButton.clear_button_press_listeners` (void).
- [Line 26](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:26>): `GamepadButton.addButtonReleaseListener` → `GamepadButton.add_button_release_listener` (void).
- [Line 30](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:30>): `GamepadButton.removeButtonReleaseListener` → `GamepadButton.remove_button_release_listener` (void).
- [Line 34](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:34>): `GamepadButton.clearButtonReleaseListeners` → `GamepadButton.clear_button_release_listeners` (void).
- [Line 49](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:49>): `GamepadButton.onPress` → `GamepadButton.on_press` (void).
- [Line 55](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:55>): `GamepadButton.onRelease` → `GamepadButton.on_release` (void).

### [core/inputs/GamepadTrigger.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java>)

- [Line 19](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:19>): `GamepadTrigger.addFullPressListener` → `GamepadTrigger.add_full_press_listener` (void).
- [Line 23](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:23>): `GamepadTrigger.removeFullPressListener` → `GamepadTrigger.remove_full_press_listener` (void).
- [Line 27](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:27>): `GamepadTrigger.clearFullPressListeners` → `GamepadTrigger.clear_full_press_listeners` (void).
- [Line 31](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:31>): `GamepadTrigger.addPressListener` → `GamepadTrigger.add_press_listener` (void).
- [Line 35](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:35>): `GamepadTrigger.removePressListener` → `GamepadTrigger.remove_press_listener` (void).
- [Line 39](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:39>): `GamepadTrigger.clearPressListeners` → `GamepadTrigger.clear_press_listeners` (void).
- [Line 43](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:43>): `GamepadTrigger.addTriggerReleaseListener` → `GamepadTrigger.add_trigger_release_listener` (void).
- [Line 47](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:47>): `GamepadTrigger.removeTriggerReleaseListener` → `GamepadTrigger.remove_trigger_release_listener` (void).
- [Line 51](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:51>): `GamepadTrigger.clearTriggerReleaseListeners` → `GamepadTrigger.clear_trigger_release_listeners` (void).
- [Line 74](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:74>): `GamepadTrigger.onFullPress` → `GamepadTrigger.on_full_press` (void).
- [Line 80](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:80>): `GamepadTrigger.onPress` → `GamepadTrigger.on_press` (void).
- [Line 87](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:87>): `GamepadTrigger.onRelease` → `GamepadTrigger.on_release` (void).

### [core/pedro/Constants.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java>)

- [Line 87](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:87>): `Constants.createFollower` → `Constants.create_follower` (Follower).

### [core/pedro/procedures/ForesightTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java>)

- [Line 326](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:326>): `HeadingBraking.biasedGradient` → `HeadingBraking.biased_gradient` (double[]).
- [Line 467](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:467>): `BrakingTuner.headingPower` → `BrakingTuner.heading_power` (double).
- [Line 475](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:475>): `BrakingTuner.biasedGradient` → `BrakingTuner.biased_gradient` (double[]).

### [core/pedro/procedures/MecanumTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java>)

- [Line 60](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java:60>): `MecanumTuner.testMotor` → `MecanumTuner.test_motor` (Direction).

### [core/pedro/procedures/PinpointTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java>)

- [Line 80](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:80>): `PinpointTuner.createConfig` → `PinpointTuner.create_config` (PinpointConfig).

### [core/pedro/Tuning.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Tuning.java>)

- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Tuning.java:25>): `Tuning.mecanumTuner` → `Tuning.mecanum_tuner` (Procedure).
- [Line 30](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Tuning.java:30>): `Tuning.pinpointTuner` → `Tuning.pinpoint_tuner` (Procedure).
- [Line 35](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Tuning.java:35>): `Tuning.foresightTuner` → `Tuning.foresight_tuner` (Procedure).

### [core/units/Units.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java>)

- [Line 89](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:89>): `Units.Angle.clampDeg` → `Units.Angle.clamp_deg` (Angle).
- [Line 152](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:152>): `Units.Current.A` → `Units.Current.a` (Current).
- [Line 156](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:156>): `Units.Current.mA` → `Units.Current.m_a` (Current).
- [Line 160](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:160>): `Units.Current.uA` → `Units.Current.u_a` (Current).
- [Line 164](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:164>): `Units.Current.kA` → `Units.Current.k_a` (Current).
- [Line 168](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:168>): `Units.Current.A` → `Units.Current.a` (double).
- [Line 172](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:172>): `Units.Current.mA` → `Units.Current.m_a` (double).
- [Line 176](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:176>): `Units.Current.uA` → `Units.Current.u_a` (double).
- [Line 180](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:180>): `Units.Current.kA` → `Units.Current.k_a` (double).
- [Line 198](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:198>): `Units.Force.N` → `Units.Force.n` (Force).
- [Line 202](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:202>): `Units.Force.kN` → `Units.Force.k_n` (Force).
- [Line 206](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:206>): `Units.Force.N` → `Units.Force.n` (double).
- [Line 210](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:210>): `Units.Force.kN` → `Units.Force.k_n` (double).
- [Line 344](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:344>): `Units.Torque.Nm` → `Units.Torque.nm` (Torque).
- [Line 348](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:348>): `Units.Torque.Ncm` → `Units.Torque.ncm` (Torque).
- [Line 352](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:352>): `Units.Torque.Nm` → `Units.Torque.nm` (double).
- [Line 356](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:356>): `Units.Torque.Ncm` → `Units.Torque.ncm` (double).

### [opModes/auto/comp/SoloWithSteal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java>)

- [Line 48](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:48>): `SoloWithSteal.fieldPose` → `SoloWithSteal.field_pose` (Pose).
- [Line 52](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:52>): `SoloWithSteal.startingPose` → `SoloWithSteal.starting_pose` (Pose).
- [Line 57](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:57>): `SoloWithSteal.runAutonomous` → `SoloWithSteal.run_autonomous` (void).
- [Line 76](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:76>): `SoloWithSteal.opponentPickUp` → `SoloWithSteal.opponent_pick_up` (Path).
- [Line 81](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:81>): `SoloWithSteal.opponentDropOff` → `SoloWithSteal.opponent_drop_off` (Path).
- [Line 87](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:87>): `SoloWithSteal.farFlowerPickUp` → `SoloWithSteal.far_flower_pick_up` (Path).
- [Line 92](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:92>): `SoloWithSteal.farFlowerDropOff` → `SoloWithSteal.far_flower_drop_off` (Path).
- [Line 97](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:97>): `SoloWithSteal.midFlowerPickUp` → `SoloWithSteal.mid_flower_pick_up` (Path).
- [Line 102](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:102>): `SoloWithSteal.shootInUpperHive` → `SoloWithSteal.shoot_in_upper_hive` (Path).
- [Line 106](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:106>): `SoloWithSteal.intakeGarden` → `SoloWithSteal.intake_garden` (Path).
- [Line 110](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:110>): `SoloWithSteal.shootInLowerHive` → `SoloWithSteal.shoot_in_lower_hive` (Path).

### [opModes/auto/structure/AbstractAuto.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java>)

- [Line 37](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:37>): `AbstractAuto.startingPose` → `AbstractAuto.starting_pose` (Pose).
- [Line 39](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:39>): `AbstractAuto.runAutonomous` → `AbstractAuto.run_autonomous` (void).
- [Line 41](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:41>): `AbstractAuto.currentAlliance` → `AbstractAuto.current_alliance` (Alliance).
- [Line 86](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:86>): `AbstractAuto.followPath` → `AbstractAuto.follow_path` (void).
- [Line 91](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:91>): `AbstractAuto.followIntakePath` → `AbstractAuto.follow_intake_path` (void).
- [Line 107](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:107>): `AbstractAuto.followIntakePath` → `AbstractAuto.follow_intake_path` (void).
- [Line 139](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:139>): `AbstractAuto.followShootPath` → `AbstractAuto.follow_shoot_path` (void).
- [Line 146](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:146>): `AbstractAuto.shootAtCurrentPosition` → `AbstractAuto.shoot_at_current_position` (void).
- [Line 164](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:164>): `AbstractAuto.updateFlywheelTarget` → `AbstractAuto.update_flywheel_target` (void).
- [Line 177](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:177>): `AbstractAuto.flywheelAtShootingSpeed` → `AbstractAuto.flywheel_at_shooting_speed` (boolean).
- [Line 184](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:184>): `AbstractAuto.runUntil` → `AbstractAuto.run_until` (void).
- [Line 188](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:188>): `AbstractAuto.runUntil` → `AbstractAuto.run_until` (void).
- [Line 205](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:205>): `AbstractAuto.updateFollowerAndTelemetry` → `AbstractAuto.update_follower_and_telemetry` (void).
- [Line 227](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:227>): `AbstractAuto.requirePositive` → `AbstractAuto.require_positive` (void).
- [Line 233](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:233>): `AbstractAuto.requireNonNegative` → `AbstractAuto.require_non_negative` (void).

### [opModes/teleOp/AbstractTeleOp.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java>)

- [Line 131](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:131>): `AbstractTeleOp.clearBulkCache` → `AbstractTeleOp.clear_bulk_cache` (void).
- [Line 138](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:138>): `AbstractTeleOp.setTelemetryManager` → `AbstractTeleOp.set_telemetry_manager` (void).
- [Line 142](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:142>): `AbstractTeleOp.getTelemetryManager` → `AbstractTeleOp.get_telemetry_manager` (Telemetry).
- [Line 147](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:147>): `AbstractTeleOp.setLimelightVideoEnabled` → `AbstractTeleOp.set_limelight_video_enabled` (void).
- [Line 150](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:150>): `AbstractTeleOp.resetPose` → `AbstractTeleOp.reset_pose` (void).
- [Line 156](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:156>): `AbstractTeleOp.initializeStartingPose` → `AbstractTeleOp.initialize_starting_pose` (void).
- [Line 175](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:175>): `AbstractTeleOp.onInit` → `AbstractTeleOp.on_init` (void).
- [Line 177](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:177>): `AbstractTeleOp.opModeStop` → `AbstractTeleOp.op_mode_stop` (void).
- [Line 179](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:179>): `AbstractTeleOp.onStart` → `AbstractTeleOp.on_start` (void).
- [Line 181](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:181>): `AbstractTeleOp.opModeLoop` → `AbstractTeleOp.op_mode_loop` (void).

### [opModes/teleOp/comp/main.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java>)

- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:25>): `main.onInit` → `main.on_init` (void).
- [Line 52](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:52>): `main.onStart` → `main.on_start` (void).
- [Line 57](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:57>): `main.opModeLoop` → `main.op_mode_loop` (void).
- [Line 78](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:78>): `main.adjustShooterTargetRpm` → `main.adjust_shooter_target_rpm` (void).
- [Line 86](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:86>): `main.opModeStop` → `main.op_mode_stop` (void).

### [opModes/teleOp/test/ShooterPidfTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java>)

- [Line 99](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:99>): `ShooterPidfTuner.updateControls` → `ShooterPidfTuner.update_controls` (void).
- [Line 149](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:149>): `ShooterPidfTuner.setTargetRpm` → `ShooterPidfTuner.set_target_rpm` (void).
- [Line 154](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:154>): `ShooterPidfTuner.changeSelectedPidf` → `ShooterPidfTuner.change_selected_pidf` (void).
- [Line 172](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:172>): `ShooterPidfTuner.applyPidf` → `ShooterPidfTuner.apply_pidf` (void).
- [Line 178](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:178>): `ShooterPidfTuner.addTelemetry` → `ShooterPidfTuner.add_telemetry` (void).
- [Line 202](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:202>): `ShooterPidfTuner.rpmToTicksPerSecond` → `ShooterPidfTuner.rpm_to_ticks_per_second` (double).

### [Robot.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java>)

- [Line 28](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:28>): `Robot.initRobot` → `Robot.init_robot` (void).
- [Line 48](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:48>): `Robot.getTelemetryManager` → `Robot.get_telemetry_manager` (Telemetry).
- [Line 53](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:53>): `Robot.startRobot` → `Robot.start_robot` (void).
- [Line 58](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:58>): `Robot.updateRobot` → `Robot.update_robot` (void).
- [Line 66](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:66>): `Robot.getShooter` → `Robot.get_shooter` (shooter).
- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:71>): `Robot.getIntake` → `Robot.get_intake` (intake).
- [Line 76](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:76>): `Robot.getFlowerIntake` → `Robot.get_flower_intake` (FlowerIntake).
- [Line 81](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:81>): `Robot.stopRobot` → `Robot.stop_robot` (void).

### [subsystems/intake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java>)

- [Line 37](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:37>): `intake.toggleForward` → `intake.toggle_forward` (void).
- [Line 45](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:45>): `intake.toggleReverse` → `intake.toggle_reverse` (void).
- [Line 52](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:52>): `intake.toggleForwardReverse` → `intake.toggle_forward_reverse` (void).
- [Line 65](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:65>): `intake.getIntakePower` → `intake.get_intake_power` (double).
- [Line 69](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:69>): `intake.getIntakeMode` → `intake.get_intake_mode` (IntakeMode).
- [Line 79](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:79>): `intake.transferForward` → `intake.transfer_forward` (void).
- [Line 83](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:83>): `intake.transferReverse` → `intake.transfer_reverse` (void).
- [Line 87](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:87>): `intake.transferOff` → `intake.transfer_off` (void).
- [Line 91](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:91>): `intake.toggleTransferForward` → `intake.toggle_transfer_forward` (void).
- [Line 98](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:98>): `intake.toggleTransferReverse` → `intake.toggle_transfer_reverse` (void).
- [Line 105](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:105>): `intake.toggleTransferForwardReverse` → `intake.toggle_transfer_forward_reverse` (void).
- [Line 117](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:117>): `intake.getTransferPower` → `intake.get_transfer_power` (double).
- [Line 121](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:121>): `intake.getTransferMode` → `intake.get_transfer_mode` (TransferMode).
- [Line 136](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:136>): `intake.refreshSensorReadings` → `intake.refresh_sensor_readings` (void).
- [Line 148](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:148>): `intake.getUpperDistance` → `intake.get_upper_distance` (Units.Length).
- [Line 153](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:153>): `intake.getLowerDistance` → `intake.get_lower_distance` (Units.Length).
- [Line 167](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:167>): `intake.getSensorTelemetry` → `intake.get_sensor_telemetry` (String).
- [Line 196](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:196>): `intake.stopFeed` → `intake.stop_feed` (void).
- [Line 205](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:205>): `intake.setScoringMode` → `intake.set_scoring_mode` (void).
- [Line 217](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:217>): `intake.toggleShootMode` → `intake.toggle_shoot_mode` (void).

### [subsystems/shooter.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java>)

- [Line 38](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:38>): `shooter.calculateFlywheelVelocity` → `shooter.calculate_flywheel_velocity` (double).
- [Line 79](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:79>): `shooter.nearestHive` → `shooter.nearest_hive` (HiveDistance).
- [Line 108](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:108>): `shooter.setFlywheelVelocity` → `shooter.set_flywheel_velocity` (void).
- [Line 154](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:154>): `shooter.getState` → `shooter.get_state` (State).
- [Line 158](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:158>): `shooter.checkLoaded` → `shooter.check_loaded` (void).
- [Line 168](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:168>): `shooter.stopShooting` → `shooter.stop_shooting` (void).
- [Line 175](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:175>): `shooter.toggleBypass` → `shooter.toggle_bypass` (boolean).
- [Line 180](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:180>): `shooter.setBypassEnabled` → `shooter.set_bypass_enabled` (void).
- [Line 187](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:187>): `shooter.adjustTargetRpm` → `shooter.adjust_target_rpm` (void).
- [Line 191](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:191>): `shooter.isBypassEnabled` → `shooter.is_bypass_enabled` (boolean).
- [Line 195](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:195>): `shooter.getTargetRpm` → `shooter.get_target_rpm` (double).
- [Line 199](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:199>): `shooter.getCurrentRpm` → `shooter.get_current_rpm` (double).
- [Line 203](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:203>): `shooter.setTargetRpm` → `shooter.set_target_rpm` (void).
- [Line 231](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:231>): `shooter.applyShootPower` → `shooter.apply_shoot_power` (void).


## Constant-name violations

These are scalar constants or named fixed unit values. A final object reference does not, by itself, guarantee that its object is immutable.

### [core/control/CompConfig.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java>)

- [Line 9](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:9>): `CompConfig.compMode` → `CompConfig.COMP_MODE` (boolean).
- [Line 12](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:12>): `CompConfig.loopTimeTelemetry` → `CompConfig.LOOP_TIME_TELEMETRY` (boolean).

### [core/control/lastPositionStorage.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java>)

- [Line 13](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:13>): `lastPositionStorage.dataValidDuration` → `lastPositionStorage.DATA_VALID_DURATION` (Time).

### [subsystems/FlowerIntake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java>)

- [Line 10](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java:10>): `FlowerIntake.flowerServoDownPose` → `FlowerIntake.FLOWER_SERVO_DOWN_POSE` (double).
- [Line 11](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java:11>): `FlowerIntake.flowerServoUpPose` → `FlowerIntake.FLOWER_SERVO_UP_POSE` (double).

### [subsystems/intake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java>)

- [Line 133](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:133>): `intake.emptyUpperDistance` → `intake.EMPTY_UPPER_DISTANCE` (Units.Length).
- [Line 134](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:134>): `intake.emptyLowerDistance` → `intake.EMPTY_LOWER_DISTANCE` (Units.Length).

### [subsystems/shooter.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java>)

- [Line 98](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:98>): `shooter.kP` → `shooter.K_P` (double).
- [Line 99](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:99>): `shooter.kI` → `shooter.K_I` (double).
- [Line 100](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:100>): `shooter.kD` → `shooter.K_D` (double).
- [Line 101](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:101>): `shooter.kF` → `shooter.K_F` (double).


## Variables and objects, assuming camelCase

The custom Gamepad wrapper's snake_case fields are owned by this project even though they mirror SDK terminology. The repeated stick parameters appear in both the listener interface and update method. logManager.ENABLED is mutable, so its uppercase name is a variable-style difference unless mutable configuration flags are explicitly exempted.

### [core/control/logManager.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LogManager.java>)

- [Line 13](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LogManager.java:13>): `logManager.ENABLED` → `logManager.enabled` (boolean).

### [core/inputs/Gamepad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java>)

- [Line 5](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:5>): `Gamepad.left_stick` → `Gamepad.leftStick` (GamepadAnalogStick).
- [Line 6](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:6>): `Gamepad.right_stick` → `Gamepad.rightStick` (GamepadAnalogStick).
- [Line 8](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:8>): `Gamepad.both_sticks` → `Gamepad.bothSticks` (GamepadAnalogSticks).
- [Line 10](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:10>): `Gamepad.left_stick_button` → `Gamepad.leftStickButton` (GamepadButton).
- [Line 11](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:11>): `Gamepad.right_stick_button` → `Gamepad.rightStickButton` (GamepadButton).
- [Line 13](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:13>): `Gamepad.left_trigger` → `Gamepad.leftTrigger` (GamepadTrigger).
- [Line 14](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:14>): `Gamepad.right_trigger` → `Gamepad.rightTrigger` (GamepadTrigger).
- [Line 16](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:16>): `Gamepad.left_bumper` → `Gamepad.leftBumper` (GamepadButton).
- [Line 17](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:17>): `Gamepad.right_bumper` → `Gamepad.rightBumper` (GamepadButton).
- [Line 24](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:24>): `Gamepad.dpad_up` → `Gamepad.dpadUp` (GamepadButton).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:25>): `Gamepad.dpad_right` → `Gamepad.dpadRight` (GamepadButton).
- [Line 26](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:26>): `Gamepad.dpad_down` → `Gamepad.dpadDown` (GamepadButton).
- [Line 27](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:27>): `Gamepad.dpad_left` → `Gamepad.dpadLeft` (GamepadButton).

### [core/inputs/GamepadAnalogSticks.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java>)

- [Line 10](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:10>): `GamepadAnalogSticks.UpdateListener.left_x` → `GamepadAnalogSticks.UpdateListener.leftX` (double).
- [Line 10](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:10>): `GamepadAnalogSticks.UpdateListener.left_y` → `GamepadAnalogSticks.UpdateListener.leftY` (double).
- [Line 10](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:10>): `GamepadAnalogSticks.UpdateListener.right_x` → `GamepadAnalogSticks.UpdateListener.rightX` (double).
- [Line 10](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:10>): `GamepadAnalogSticks.UpdateListener.right_y` → `GamepadAnalogSticks.UpdateListener.rightY` (double).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:25>): `GamepadAnalogSticks.left_x` → `GamepadAnalogSticks.leftX` (double).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:25>): `GamepadAnalogSticks.left_y` → `GamepadAnalogSticks.leftY` (double).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:25>): `GamepadAnalogSticks.right_x` → `GamepadAnalogSticks.rightX` (double).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:25>): `GamepadAnalogSticks.right_y` → `GamepadAnalogSticks.rightY` (double).


## Static-final references requiring a policy decision

These seven fields hold mutable configuration/state containers or a lock. Their references are final, but their contents/state are not constant. Retaining camelCase is reasonable for mutable shared objects; if your rule means every static-final field must be uppercase, these are additional differences.

### [core/control/periodicRegistry.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java>)

- [Line 18](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/PeriodicRegistry.java:18>): `periodicRegistry.callbacks` → `periodicRegistry.CALLBACKS` (CopyOnWriteArrayList<Runnable>).

### [core/control/taskManager.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java>)

- [Line 42](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:42>): `taskManager.lock` → `taskManager.LOCK` (Object).
- [Line 50](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:50>): `taskManager.runningTasks` → `taskManager.RUNNING_TASKS` (ConcurrentMap<String, Entry>).

### [core/pedro/Constants.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java>)

- [Line 28](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:28>): `Constants.drivetrainConfig` → `Constants.DRIVETRAIN_CONFIG` (MecanumConfig).
- [Line 41](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:41>): `Constants.localizerConfig` → `Constants.LOCALIZER_CONFIG` (PinpointConfig).
- [Line 53](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:53>): `Constants.foresightConfig` → `Constants.FORESIGHT_CONFIG` (ForesightConfig).

### [core/pedro/pathConstraints.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PathConstraints.java>)

- [Line 6](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PathConstraints.java:6>): `pathConstraints.autoPilot` → `pathConstraints.AUTO_PILOT` (ForesightConfig).


## Required framework/API method exceptions

These 33 declarations retain inherited names: 19 Pedro runTuningOpMode methods, 11 Java toString methods, and 3 FTC runOpMode methods. They are excluded from the 166 project-controlled function findings.

### [core/pedro/procedures/ForesightTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java>)

- [Line 136](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:136>): `ForwardVelocity.runTuningOpMode` (Double).
- [Line 176](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:176>): `StrafeVelocity.runTuningOpMode` (Double).
- [Line 213](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:213>): `DecelerationTuner.runTuningOpMode` (Double).
- [Line 280](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:280>): `HeadingBraking.runTuningOpMode` (List<Double>).
- [Line 356](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:356>): `HeadingTuner.runTuningOpMode` (Double).
- [Line 418](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:418>): `BrakingTuner.runTuningOpMode` (List<Double>).
- [Line 514](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:514>): `TranslationalTuner.runTuningOpMode` (List<Double>).

### [core/pedro/procedures/MecanumTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java>)

- [Line 86](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java:86>): `MecanumTuner.SpinMotor.runTuningOpMode` (Void).

### [core/pedro/procedures/PinpointTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java>)

- [Line 111](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:111>): `PinpointTuner.PinpointCustomPodScalar.runTuningOpMode` (Double).
- [Line 136](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:136>): `PinpointTuner.PinpointForwardDirection.runTuningOpMode` (Boolean).
- [Line 161](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:161>): `PinpointTuner.PinpointStrafeDirection.runTuningOpMode` (Boolean).
- [Line 192](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:192>): `PinpointTuner.PinpointOffsets.runTuningOpMode` (List<Double>).

### [core/pedro/procedures/Tests.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java>)

- [Line 120](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:120>): `TestsHold.runTuningOpMode` (Boolean).
- [Line 143](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:143>): `TestsLine.runTuningOpMode` (Boolean).
- [Line 175](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:175>): `TestsCurve.runTuningOpMode` (Boolean).
- [Line 208](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:208>): `TestsInterpolation.runTuningOpMode` (Boolean).
- [Line 243](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:243>): `TestsLocalization.runTuningOpMode` (Boolean).
- [Line 267](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:267>): `TestsDriving.runTuningOpMode` (Boolean).
- [Line 286](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:286>): `TestsPose.runTuningOpMode` (Boolean).

### [core/units/Units.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java>)

- [Line 46](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:46>): `Units.Acceleration.toString` (String).
- [Line 95](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:95>): `Units.Angle.toString` (String).
- [Line 133](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:133>): `Units.AngularVelocity.toString` (String).
- [Line 184](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:184>): `Units.Current.toString` (String).
- [Line 214](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:214>): `Units.Force.toString` (String).
- [Line 260](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:260>): `Units.Length.toString` (String).
- [Line 285](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:285>): `Units.Position.toString` (String).
- [Line 330](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:330>): `Units.Time.toString` (String).
- [Line 360](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:360>): `Units.Torque.toString` (String).
- [Line 402](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:402>): `Units.Velocity.toString` (String).
- [Line 440](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:440>): `Units.Weight.toString` (String).

### [opModes/auto/structure/AbstractAuto.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java>)

- [Line 45](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:45>): `AbstractAuto.runOpMode` (void).

### [opModes/teleOp/AbstractTeleOp.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java>)

- [Line 56](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:56>): `AbstractTeleOp.runOpMode` (void).

### [opModes/teleOp/test/ShooterPidfTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java>)

- [Line 59](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:59>): `ShooterPidfTuner.runOpMode` (void).


## Enum types under the literal all-caps rule

All listed names comply with the existing PascalCase enum-type rule. Their enum members already use uppercase names. Suggested all-caps type names apply only if the type rule in your message is intentional.

### [core/pedro/procedures/MecanumTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java>)

- [Line 15](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java:15>): `MecanumTuner.Direction` → `MecanumTuner.DIRECTION` (enum).

### [core/pedro/procedures/PinpointTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java>)

- [Line 21](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:21>): `PinpointTuner.PodType` → `PinpointTuner.POD_TYPE` (enum).

### [core/pedro/procedures/Tests.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java>)

- [Line 28](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:28>): `Tests.Test` → `Tests.TEST` (enum).

### [core/units/Units.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java>)

- [Line 53](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:53>): `Units.Alliance` → `Units.ALLIANCE` (enum).
- [Line 140](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:140>): `Units.APInterpolation` → `Units.AP_INTERPOLATION` (enum).
- [Line 267](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:267>): `Units.ObeliskRead` → `Units.OBELISK_READ` (enum).
- [Line 292](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:292>): `Units.RobotState` → `Units.ROBOT_STATE` (enum).

### [opModes/teleOp/test/ShooterPidfTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java>)

- [Line 210](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:210>): `ShooterPidfTuner.PidfParameter` → `ShooterPidfTuner.PIDF_PARAMETER` (enum).

### [subsystems/FlowerIntake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java>)

- [Line 12](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java:12>): `FlowerIntake.FlowerIntakePose` → `FlowerIntake.FLOWER_INTAKE_POSE` (enum).

### [subsystems/intake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java>)

- [Line 18](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:18>): `intake.IntakeMode` → `intake.INTAKE_MODE` (enum).
- [Line 75](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:75>): `intake.TransferMode` → `intake.TRANSFER_MODE` (enum).
- [Line 172](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:172>): `intake.ScoringMode` → `intake.SCORING_MODE` (enum).

### [subsystems/shooter.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java>)

- [Line 143](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Shooter.java:143>): `shooter.State` → `shooter.STATE` (enum).


## Unit/pose value names under the literal all-caps rule

All 58 explicitly unit/pose-typed declarations outside uppercase are listed, including fields, locals, and parameters. Primitive doubles representing distances, headings, or time are treated as ordinary numeric variables unless they are constants; type declarations and external dependency types are not renamed by this category. Field coordinates x/y/z in Units.Position are included because they are Length objects. Most entries are currently valid camelCase variables.

### [core/control/lastPositionStorage.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java>)

- [Line 12](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:12>): `lastPositionStorage.storageTime` → `lastPositionStorage.STORAGE_TIME` (Time).
- [Line 13](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:13>): `lastPositionStorage.dataValidDuration` → `lastPositionStorage.DATA_VALID_DURATION` (Time).
- [Line 15](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:15>): `lastPositionStorage.lastPosition` → `lastPositionStorage.LAST_POSITION` (Pose).
- [Line 18](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/LastPositionStorage.java:18>): `lastPositionStorage.position` → `lastPositionStorage.POSITION` (Pose).

### [core/control/taskManager.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java>)

- [Line 63](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:63>): `taskManager.period` → `taskManager.PERIOD` (Time).
- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:71>): `taskManager.delay` → `taskManager.DELAY` (Time).
- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:71>): `taskManager.period` → `taskManager.PERIOD` (Time).
- [Line 75](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:75>): `taskManager.delay` → `taskManager.DELAY` (Time).
- [Line 115](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/TaskManager.java:115>): `taskManager.time` → `taskManager.TIME` (Time).

### [core/pedro/PoseMirroring.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PoseMirroring.java>)

- [Line 14](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PoseMirroring.java:14>): `PoseMirroring.pose` → `PoseMirroring.POSE` (Pose).
- [Line 21](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PoseMirroring.java:21>): `PoseMirroring.pose` → `PoseMirroring.POSE` (Pose).

### [core/pedro/procedures/PinpointTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java>)

- [Line 180](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:180>): `PinpointTuner.PinpointOffsets.previous` → `PinpointTuner.PinpointOffsets.PREVIOUS` (Pose).

### [core/pedro/procedures/Tests.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java>)

- [Line 146](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:146>): `TestsLine.start` → `TestsLine.START` (Pose).
- [Line 147](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:147>): `TestsLine.end` → `TestsLine.END` (Pose).
- [Line 178](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:178>): `TestsCurve.start` → `TestsCurve.START` (Pose).
- [Line 179](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:179>): `TestsCurve.corner` → `TestsCurve.CORNER` (Pose).
- [Line 180](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:180>): `TestsCurve.end` → `TestsCurve.END` (Pose).
- [Line 211](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:211>): `TestsInterpolation.start` → `TestsInterpolation.START` (Pose).
- [Line 212](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:212>): `TestsInterpolation.corner` → `TestsInterpolation.CORNER` (Pose).
- [Line 213](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:213>): `TestsInterpolation.end` → `TestsInterpolation.END` (Pose).

### [core/units/Units.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java>)

- [Line 275](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:275>): `Units.Position.x` → `Units.Position.X` (Length).
- [Line 276](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:276>): `Units.Position.y` → `Units.Position.Y` (Length).
- [Line 277](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:277>): `Units.Position.z` → `Units.Position.Z` (Length).
- [Line 279](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:279>): `Units.Position.x` → `Units.Position.X` (Length).
- [Line 279](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:279>): `Units.Position.y` → `Units.Position.Y` (Length).
- [Line 279](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:279>): `Units.Position.z` → `Units.Position.Z` (Length).

### [opModes/auto/comp/SoloWithSteal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java>)

- [Line 21](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:21>): `SoloWithSteal.shootInLowerHive` → `SoloWithSteal.SHOOT_IN_LOWER_HIVE` (Pose).
- [Line 22](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:22>): `SoloWithSteal.shootInUpperHive` → `SoloWithSteal.SHOOT_IN_UPPER_HIVE` (Pose).
- [Line 23](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:23>): `SoloWithSteal.start` → `SoloWithSteal.START` (Pose).
- [Line 25](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:25>): `SoloWithSteal.opponentPickUp` → `SoloWithSteal.OPPONENT_PICK_UP` (Pose).
- [Line 26](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:26>): `SoloWithSteal.opponentPickUpControl1` → `SoloWithSteal.OPPONENT_PICK_UP_CONTROL1` (Pose).
- [Line 27](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:27>): `SoloWithSteal.opponentPickUpControl2` → `SoloWithSteal.OPPONENT_PICK_UP_CONTROL2` (Pose).
- [Line 28](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:28>): `SoloWithSteal.opponentDropOff` → `SoloWithSteal.OPPONENT_DROP_OFF` (Pose).
- [Line 29](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:29>): `SoloWithSteal.opponentDropOffControl1` → `SoloWithSteal.OPPONENT_DROP_OFF_CONTROL1` (Pose).
- [Line 30](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:30>): `SoloWithSteal.opponentDropOffControl2` → `SoloWithSteal.OPPONENT_DROP_OFF_CONTROL2` (Pose).
- [Line 31](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:31>): `SoloWithSteal.opponentDropOffControl3` → `SoloWithSteal.OPPONENT_DROP_OFF_CONTROL3` (Pose).
- [Line 32](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:32>): `SoloWithSteal.farFlowerPickUp` → `SoloWithSteal.FAR_FLOWER_PICK_UP` (Pose).
- [Line 33](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:33>): `SoloWithSteal.farFlowerPickUpControl1` → `SoloWithSteal.FAR_FLOWER_PICK_UP_CONTROL1` (Pose).
- [Line 34](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:34>): `SoloWithSteal.farFlowerPickUpControl2` → `SoloWithSteal.FAR_FLOWER_PICK_UP_CONTROL2` (Pose).
- [Line 35](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:35>): `SoloWithSteal.farFlowerDropOff` → `SoloWithSteal.FAR_FLOWER_DROP_OFF` (Pose).
- [Line 36](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:36>): `SoloWithSteal.farFlowerDropOffControl1` → `SoloWithSteal.FAR_FLOWER_DROP_OFF_CONTROL1` (Pose).
- [Line 37](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:37>): `SoloWithSteal.farFlowerDropOffControl2` → `SoloWithSteal.FAR_FLOWER_DROP_OFF_CONTROL2` (Pose).
- [Line 38](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:38>): `SoloWithSteal.midFlowerPickUp` → `SoloWithSteal.MID_FLOWER_PICK_UP` (Pose).
- [Line 39](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:39>): `SoloWithSteal.midFlowerPickUpControl1` → `SoloWithSteal.MID_FLOWER_PICK_UP_CONTROL1` (Pose).
- [Line 40](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:40>): `SoloWithSteal.midFlowerPickUpControl2` → `SoloWithSteal.MID_FLOWER_PICK_UP_CONTROL2` (Pose).
- [Line 42](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:42>): `SoloWithSteal.shootInUpperHiveControl` → `SoloWithSteal.SHOOT_IN_UPPER_HIVE_CONTROL` (Pose).
- [Line 44](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:44>): `SoloWithSteal.intakeGarden` → `SoloWithSteal.INTAKE_GARDEN` (Pose).
- [Line 45](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:45>): `SoloWithSteal.park` → `SoloWithSteal.PARK` (Pose).
- [Line 46](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:46>): `SoloWithSteal.parkControl1` → `SoloWithSteal.PARK_CONTROL1` (Pose).
- [Line 48](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:48>): `SoloWithSteal.redPose` → `SoloWithSteal.RED_POSE` (Pose).

### [opModes/auto/structure/AbstractAuto.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java>)

- [Line 170](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:170>): `AbstractAuto.currentPose` → `AbstractAuto.CURRENT_POSE` (Pose).

### [opModes/auto/structure/OptimalPoses.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/OptimalPoses.java>)

- [Line 37](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/OptimalPoses.java:37>): `OptimalPoses.Delta.base` → `OptimalPoses.Delta.BASE` (Pose).

### [opModes/teleOp/AbstractTeleOp.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java>)

- [Line 151](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:151>): `AbstractTeleOp.startPose` → `AbstractTeleOp.START_POSE` (Pose).

### [Robot.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java>)

- [Line 60](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:60>): `Robot.pose` → `Robot.POSE` (Pose).

### [subsystems/intake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java>)

- [Line 129](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:129>): `intake.cachedUpperDistance` → `intake.CACHED_UPPER_DISTANCE` (Units.Length).
- [Line 130](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:130>): `intake.cachedLowerDistance` → `intake.CACHED_LOWER_DISTANCE` (Units.Length).
- [Line 133](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:133>): `intake.emptyUpperDistance` → `intake.EMPTY_UPPER_DISTANCE` (Units.Length).
- [Line 134](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/Intake.java:134>): `intake.emptyLowerDistance` → `intake.EMPTY_LOWER_DISTANCE` (Units.Length).


## Unit class names under a literal all-caps type rule

These 11 nested classes satisfy PascalCase. Uppercasing them would require an explicit unit-type exception to the general class rule. If the Units container itself is included in the all-caps instruction, Units → UNITS is a further conditional difference. Imported Pedro Pose is outside this repository and is not a project type to rename.

### [core/units/Units.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java>)

- [Line 15](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:15>): `Units.Acceleration` → `Units.ACCELERATION` (class).
- [Line 59](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:59>): `Units.Angle` → `Units.ANGLE` (class).
- [Line 102](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:102>): `Units.AngularVelocity` → `Units.ANGULAR_VELOCITY` (class).
- [Line 145](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:145>): `Units.Current` → `Units.CURRENT` (class).
- [Line 191](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:191>): `Units.Force` → `Units.FORCE` (class).
- [Line 221](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:221>): `Units.Length` → `Units.LENGTH` (class).
- [Line 274](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:274>): `Units.Position` → `Units.POSITION` (class).
- [Line 299](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:299>): `Units.Time` → `Units.TIME` (class).
- [Line 337](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:337>): `Units.Torque` → `Units.TORQUE` (class).
- [Line 367](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:367>): `Units.Velocity` → `Units.VELOCITY` (class).
- [Line 409](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:409>): `Units.Weight` → `Units.WEIGHT` (class).


## TeamCode Java filenames under the literal camelCase rule

These 25 filenames do not begin with a lowercase letter. Most name public top-level Java types and should retain matching PascalCase names. ListenerList is package-private, so its filename is not constrained by the public-class requirement, although matching its PascalCase class is conventional.

### [core/control/CompConfig.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java:1>): `CompConfig` → `compConfig`.

### [core/hardware/CachedMotor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedMotor.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedMotor.java:1>): `CachedMotor` → `cachedMotor`.

### [core/hardware/CachedServo.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedServo.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/hardware/CachedServo.java:1>): `CachedServo` → `cachedServo`.

### [core/inputs/Gamepad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/Gamepad.java:1>): `Gamepad` → `gamepad`.

### [core/inputs/GamepadAnalogStick.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogStick.java:1>): `GamepadAnalogStick` → `gamepadAnalogStick`.

### [core/inputs/GamepadAnalogSticks.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadAnalogSticks.java:1>): `GamepadAnalogSticks` → `gamepadAnalogSticks`.

### [core/inputs/GamepadButton.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadButton.java:1>): `GamepadButton` → `gamepadButton`.

### [core/inputs/GamepadTrigger.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/GamepadTrigger.java:1>): `GamepadTrigger` → `gamepadTrigger`.

### [core/inputs/InputManager.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/InputManager.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/InputManager.java:1>): `InputManager` → `inputManager`.

### [core/inputs/ListenerList.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/ListenerList.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/inputs/ListenerList.java:1>): `ListenerList` → `listenerList`.

### [core/pedro/Constants.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Constants.java:1>): `Constants` → `constants`.

### [core/pedro/PoseMirroring.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PoseMirroring.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PoseMirroring.java:1>): `PoseMirroring` → `poseMirroring`.

### [core/pedro/procedures/ForesightTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/ForesightTuner.java:1>): `ForesightTuner` → `foresightTuner`.

### [core/pedro/procedures/MecanumTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/MecanumTuner.java:1>): `MecanumTuner` → `mecanumTuner`.

### [core/pedro/procedures/PinpointTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/PinpointTuner.java:1>): `PinpointTuner` → `pinpointTuner`.

### [core/pedro/procedures/Tests.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/procedures/Tests.java:1>): `Tests` → `tests`.

### [core/pedro/Tuning.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Tuning.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/Tuning.java:1>): `Tuning` → `tuning`.

### [core/units/Units.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/units/Units.java:1>): `Units` → `units`.

### [opModes/auto/comp/SoloWithSteal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:1>): `SoloWithSteal` → `soloWithSteal`.

### [opModes/auto/structure/AbstractAuto.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:1>): `AbstractAuto` → `abstractAuto`.

### [opModes/auto/structure/OptimalPoses.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/OptimalPoses.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/OptimalPoses.java:1>): `OptimalPoses` → `optimalPoses`.

### [opModes/teleOp/AbstractTeleOp.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:1>): `AbstractTeleOp` → `abstractTeleOp`.

### [opModes/teleOp/test/ShooterPidfTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:1>): `ShooterPidfTuner` → `shooterPidfTuner`.

### [Robot.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Robot.java:1>): `Robot` → `robot`.

### [subsystems/FlowerIntake.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/FlowerIntake.java:1>): `FlowerIntake` → `flowerIntake`.


## Additional package findings from Agents.md

Six declarations contain opModes and/or teleOp. The existing lowercase package rule would spell these segments opmodes and teleop and requires updating directory paths and imports. This rule was not explicitly included in your latest list.

### [opModes/auto/comp/SoloWithSteal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/comp/SoloWithSteal.java:1>): `org.firstinspires.ftc.teamcode.opModes.auto.comp`.

### [opModes/auto/structure/AbstractAuto.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java:1>): `org.firstinspires.ftc.teamcode.opModes.auto.structure`.

### [opModes/auto/structure/OptimalPoses.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/OptimalPoses.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/OptimalPoses.java:1>): `org.firstinspires.ftc.teamcode.opModes.auto.structure`.

### [opModes/teleOp/AbstractTeleOp.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java:1>): `org.firstinspires.ftc.teamcode.opModes.teleOp`.

### [opModes/teleOp/comp/main.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/comp/Main.java:1>): `org.firstinspires.ftc.teamcode.opModes.teleOp.comp`.

### [opModes/teleOp/test/ShooterPidfTuner.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/test/ShooterPidfTuner.java:1>): `org.firstinspires.ftc.teamcode.opModes.teleOp.test`.


## FtcRobotController and bundled samples

All 70 Java files were inspected. These files form the FTC controller scaffold, utilities, and bundled samples. Their differences from the custom rules are reported separately so they are not mistaken for project-owned naming defects. A naming-only cleanup of this module would create substantial changes to upstream code.

| Literal pattern difference | Count |
| --- | ---: |
| Java filenames outside camelCase | 70 |
| Class names containing underscores | 11 |
| Non-override methods outside snake_case | 108 |
| CamelCase overrides | 100 |
| Variable declarations outside camelCase | 44 |
| Lowercase static-final fields | 2 |
| Enum types outside uppercase | 1 |
| Unit/pose-typed values outside uppercase | 9 |

Overrides below are contract-review entries, not blanket rename recommendations. Several uppercase non-final fields in the samples act as tunable configuration values; they are literal camelCase differences rather than necessarily erroneous names.

## Bundled class-name differences

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/BasicOmniOpMode_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOmniOpMode_Linear.java>)

- [Line 66](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOmniOpMode_Linear.java:66>): `BasicOmniOpMode_Linear` → `BasicOmniOpMode_Linear` (CLASS).

### [external/samples/BasicOpMode_Iterative.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Iterative.java>)

- [Line 53](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Iterative.java:53>): `BasicOpMode_Iterative` → `BasicOpMode_Iterative` (CLASS).

### [external/samples/BasicOpMode_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Linear.java>)

- [Line 53](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Linear.java:53>): `BasicOpMode_Linear` → `BasicOpMode_Linear` (CLASS).

### [external/samples/ConceptVisionColorLocator_Circle.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Circle.java>)

- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Circle.java:71>): `ConceptVisionColorLocator_Circle` → `ConceptVisionColorLocator_Circle` (CLASS).

### [external/samples/ConceptVisionColorLocator_Rectangle.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Rectangle.java>)

- [Line 67](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Rectangle.java:67>): `ConceptVisionColorLocator_Rectangle` → `ConceptVisionColorLocator_Rectangle` (CLASS).

### [external/samples/RobotAutoDriveByEncoder_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java>)

- [Line 64](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java:64>): `RobotAutoDriveByEncoder_Linear` → `RobotAutoDriveByEncoder_Linear` (CLASS).

### [external/samples/RobotAutoDriveByGyro_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java>)

- [Line 91](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:91>): `RobotAutoDriveByGyro_Linear` → `RobotAutoDriveByGyro_Linear` (CLASS).

### [external/samples/RobotAutoDriveByTime_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByTime_Linear.java>)

- [Line 57](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByTime_Linear.java:57>): `RobotAutoDriveByTime_Linear` → `RobotAutoDriveByTime_Linear` (CLASS).

### [external/samples/RobotAutoDriveToLine_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java>)

- [Line 63](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java:63>): `RobotAutoDriveToLine_Linear` → `RobotAutoDriveToLine_Linear` (CLASS).

### [external/samples/RobotTeleopPOV_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopPOV_Linear.java>)

- [Line 51](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopPOV_Linear.java:51>): `RobotTeleopPOV_Linear` → `RobotTeleopPOV_Linear` (CLASS).

### [external/samples/RobotTeleopTank_Iterative.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopTank_Iterative.java>)

- [Line 52](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopTank_Iterative.java:52>): `RobotTeleopTank_Iterative` → `RobotTeleopTank_Iterative` (CLASS).


## Bundled function-name differences

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/ConceptAprilTag.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTag.java>)

- [Line 149](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTag.java:149>): `ConceptAprilTag.initAprilTag` → `ConceptAprilTag.init_april_tag` (void).
- [Line 218](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTag.java:218>): `ConceptAprilTag.telemetryAprilTag` → `ConceptAprilTag.telemetry_april_tag` (void).

### [external/samples/ConceptAprilTagEasy.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagEasy.java>)

- [Line 123](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagEasy.java:123>): `ConceptAprilTagEasy.initAprilTag` → `ConceptAprilTagEasy.init_april_tag` (void).
- [Line 142](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagEasy.java:142>): `ConceptAprilTagEasy.telemetryAprilTag` → `ConceptAprilTagEasy.telemetry_april_tag` (void).

### [external/samples/ConceptAprilTagLocalization.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java>)

- [Line 175](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java:175>): `ConceptAprilTagLocalization.initAprilTag` → `ConceptAprilTagLocalization.init_april_tag` (void).
- [Line 244](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java:244>): `ConceptAprilTagLocalization.telemetryAprilTag` → `ConceptAprilTagLocalization.telemetry_april_tag` (void).

### [external/samples/ConceptAprilTagOptimizeExposure.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java>)

- [Line 159](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java:159>): `ConceptAprilTagOptimizeExposure.initAprilTag` → `ConceptAprilTagOptimizeExposure.init_april_tag` (void).
- [Line 175](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java:175>): `ConceptAprilTagOptimizeExposure.setManualExposure` → `ConceptAprilTagOptimizeExposure.set_manual_exposure` (boolean).
- [Line 218](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java:218>): `ConceptAprilTagOptimizeExposure.getCameraSetting` → `ConceptAprilTagOptimizeExposure.get_camera_setting` (void).

### [external/samples/ConceptAprilTagSwitchableCameras.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java>)

- [Line 115](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java:115>): `ConceptAprilTagSwitchableCameras.initAprilTag` → `ConceptAprilTagSwitchableCameras.init_april_tag` (void).
- [Line 136](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java:136>): `ConceptAprilTagSwitchableCameras.telemetryCameraSwitching` → `ConceptAprilTagSwitchableCameras.telemetry_camera_switching` (void).
- [Line 151](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java:151>): `ConceptAprilTagSwitchableCameras.telemetryAprilTag` → `ConceptAprilTagSwitchableCameras.telemetry_april_tag` (void).
- [Line 190](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java:190>): `ConceptAprilTagSwitchableCameras.doCameraSwitching` → `ConceptAprilTagSwitchableCameras.do_camera_switching` (void).

### [external/samples/ConceptExploringIMUOrientation.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java>)

- [Line 173](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java:173>): `ConceptExploringIMUOrientation.updateOrientation` → `ConceptExploringIMUOrientation.update_orientation` (void).

### [external/samples/ConceptGamepadEdgeDetection.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java>)

- [Line 72](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java:72>): `ConceptGamepadEdgeDetection.telemetryButtonData` → `ConceptGamepadEdgeDetection.telemetry_button_data` (void).

### [external/samples/ConceptMotorBulkRead.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java>)

- [Line 219](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java:219>): `ConceptMotorBulkRead.displayCycleTimes` → `ConceptMotorBulkRead.display_cycle_times` (void).

### [external/samples/ConceptTelemetry.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java>)

- [Line 160](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java:160>): `ConceptTelemetry.emitPoemLine` → `ConceptTelemetry.emit_poem_line` (void).
- [Line 167](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java:167>): `ConceptTelemetry.getBatteryVoltage` → `ConceptTelemetry.get_battery_voltage` (double).

### [external/samples/externalhardware/RobotHardware.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java>)

- [Line 119](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java:119>): `RobotHardware.driveRobot` → `RobotHardware.drive_robot` (void).
- [Line 142](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java:142>): `RobotHardware.setDrivePower` → `RobotHardware.set_drive_power` (void).
- [Line 153](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java:153>): `RobotHardware.setArmPower` → `RobotHardware.set_arm_power` (void).
- [Line 162](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java:162>): `RobotHardware.setHandPositions` → `RobotHardware.set_hand_positions` (void).

### [external/samples/RobotAutoDriveByEncoder_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java>)

- [Line 135](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java:135>): `RobotAutoDriveByEncoder_Linear.encoderDrive` → `RobotAutoDriveByEncoder_Linear.encoder_drive` (void).

### [external/samples/RobotAutoDriveByGyro_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java>)

- [Line 227](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:227>): `RobotAutoDriveByGyro_Linear.driveStraight` → `RobotAutoDriveByGyro_Linear.drive_straight` (void).
- [Line 290](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:290>): `RobotAutoDriveByGyro_Linear.turnToHeading` → `RobotAutoDriveByGyro_Linear.turn_to_heading` (void).
- [Line 328](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:328>): `RobotAutoDriveByGyro_Linear.holdHeading` → `RobotAutoDriveByGyro_Linear.hold_heading` (void).
- [Line 361](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:361>): `RobotAutoDriveByGyro_Linear.getSteeringCorrection` → `RobotAutoDriveByGyro_Linear.get_steering_correction` (double).
- [Line 381](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:381>): `RobotAutoDriveByGyro_Linear.moveRobot` → `RobotAutoDriveByGyro_Linear.move_robot` (void).
- [Line 405](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:405>): `RobotAutoDriveByGyro_Linear.sendTelemetry` → `RobotAutoDriveByGyro_Linear.send_telemetry` (void).
- [Line 425](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:425>): `RobotAutoDriveByGyro_Linear.getHeading` → `RobotAutoDriveByGyro_Linear.get_heading` (double).

### [external/samples/RobotAutoDriveToAprilTagOmni.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java>)

- [Line 259](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:259>): `RobotAutoDriveToAprilTagOmni.moveRobot` → `RobotAutoDriveToAprilTagOmni.move_robot` (void).
- [Line 288](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:288>): `RobotAutoDriveToAprilTagOmni.initAprilTag` → `RobotAutoDriveToAprilTagOmni.init_april_tag` (void).
- [Line 319](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:319>): `RobotAutoDriveToAprilTagOmni.setManualExposure` → `RobotAutoDriveToAprilTagOmni.set_manual_exposure` (void).

### [external/samples/RobotAutoDriveToAprilTagTank.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java>)

- [Line 244](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:244>): `RobotAutoDriveToAprilTagTank.moveRobot` → `RobotAutoDriveToAprilTagTank.move_robot` (void).
- [Line 264](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:264>): `RobotAutoDriveToAprilTagTank.initAprilTag` → `RobotAutoDriveToAprilTagTank.init_april_tag` (void).
- [Line 295](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:295>): `RobotAutoDriveToAprilTagTank.setManualExposure` → `RobotAutoDriveToAprilTagTank.set_manual_exposure` (void).

### [external/samples/RobotAutoDriveToLine_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java>)

- [Line 135](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java:135>): `RobotAutoDriveToLine_Linear.getBrightness` → `RobotAutoDriveToLine_Linear.get_brightness` (double).

### [external/samples/RobotTeleopMecanumFieldRelativeDrive.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopMecanumFieldRelativeDrive.java>)

- [Line 118](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopMecanumFieldRelativeDrive.java:118>): `RobotTeleopMecanumFieldRelativeDrive.driveFieldRelative` → `RobotTeleopMecanumFieldRelativeDrive.drive_field_relative` (void).

### [external/samples/SampleRevBlinkinLedDriver.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java>)

- [Line 120](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java:120>): `SampleRevBlinkinLedDriver.handleGamepad` → `SampleRevBlinkinLedDriver.handle_gamepad` (void).
- [Line 143](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java:143>): `SampleRevBlinkinLedDriver.setDisplayKind` → `SampleRevBlinkinLedDriver.set_display_kind` (void).
- [Line 149](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java:149>): `SampleRevBlinkinLedDriver.doAutoDisplay` → `SampleRevBlinkinLedDriver.do_auto_display` (void).
- [Line 158](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java:158>): `SampleRevBlinkinLedDriver.displayPattern` → `SampleRevBlinkinLedDriver.display_pattern` (void).

### [external/samples/SensorBNO055IMU.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java>)

- [Line 116](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java:116>): `SensorBNO055IMU.composeTelemetry` → `SensorBNO055IMU.compose_telemetry` (void).
- [Line 179](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java:179>): `SensorBNO055IMU.formatAngle` → `SensorBNO055IMU.format_angle` (String).
- [Line 183](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java:183>): `SensorBNO055IMU.formatDegrees` → `SensorBNO055IMU.format_degrees` (String).

### [external/samples/SensorBNO055IMUCalibration.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java>)

- [Line 176](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java:176>): `SensorBNO055IMUCalibration.composeTelemetry` → `SensorBNO055IMUCalibration.compose_telemetry` (void).
- [Line 223](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java:223>): `SensorBNO055IMUCalibration.formatAngle` → `SensorBNO055IMUCalibration.format_angle` (String).
- [Line 227](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java:227>): `SensorBNO055IMUCalibration.formatDegrees` → `SensorBNO055IMUCalibration.format_degrees` (String).

### [external/samples/SensorColor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorColor.java>)

- [Line 115](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorColor.java:115>): `SensorColor.runSample` → `SensorColor.run_sample` (void).

### [external/samples/SensorGoBildaPinpoint.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorGoBildaPinpoint.java>)

- [Line 77](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorGoBildaPinpoint.java:77>): `SensorGoBildaPinpoint.configurePinpoint` → `SensorGoBildaPinpoint.configure_pinpoint` (void).

### [external/samples/SensorKLNavxMicro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java>)

- [Line 117](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java:117>): `SensorKLNavxMicro.formatRate` → `SensorKLNavxMicro.format_rate` (String).
- [Line 121](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java:121>): `SensorKLNavxMicro.formatAngle` → `SensorKLNavxMicro.format_angle` (String).
- [Line 125](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java:125>): `SensorKLNavxMicro.formatDegrees` → `SensorKLNavxMicro.format_degrees` (String).

### [external/samples/SensorMRGyro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java>)

- [Line 148](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java:148>): `SensorMRGyro.formatRaw` → `SensorMRGyro.format_raw` (String).
- [Line 152](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java:152>): `SensorMRGyro.formatRate` → `SensorMRGyro.format_rate` (String).
- [Line 156](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java:156>): `SensorMRGyro.formatFloat` → `SensorMRGyro.format_float` (String).

### [external/samples/SensorOctoQuad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java>)

- [Line 141](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java:141>): `SensorOctoQuad.readOdometryPods` → `SensorOctoQuad.read_odometry_pods` (void).

### [external/samples/SensorOctoQuadAdv.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java>)

- [Line 193](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:193>): `OctoSwerveDrive.updateModules` → `OctoSwerveDrive.update_modules` (void).
- [Line 268](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:268>): `OctoSwerveModule.updateModule` → `OctoSwerveModule.update_module` (void).

### [external/samples/SensorOctoQuadLocalization.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadLocalization.java>)

- [Line 89](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadLocalization.java:89>): `SensorOctoQuadLocalization.runOpMode` → `SensorOctoQuadLocalization.run_op_mode` (void).
- [Line 223](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadLocalization.java:223>): `SensorOctoQuadLocalization.warnIfNotTuned` → `SensorOctoQuadLocalization.warn_if_not_tuned` (void).

### [external/samples/SensorSparkFunOTOS.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorSparkFunOTOS.java>)

- [Line 75](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorSparkFunOTOS.java:75>): `SensorSparkFunOTOS.configureOtos` → `SensorSparkFunOTOS.configure_otos` (void).

### [external/samples/UtilityOctoQuadConfigMenu.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java>)

- [Line 265](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:265>): `UtilityOctoQuadConfigMenu.sendSettingsToRam` → `UtilityOctoQuadConfigMenu.send_settings_to_ram` (void).
- [Line 534](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:534>): `UtilityOctoQuadConfigMenu.TelemetryMenu.MenuElement.addChild` → `UtilityOctoQuadConfigMenu.TelemetryMenu.MenuElement.add_child` (void).
- [Line 544](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:544>): `UtilityOctoQuadConfigMenu.TelemetryMenu.MenuElement.addChildren` → `UtilityOctoQuadConfigMenu.TelemetryMenu.MenuElement.add_children` (void).
- [Line 570](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:570>): `UtilityOctoQuadConfigMenu.TelemetryMenu.OptionElement.onClick` → `UtilityOctoQuadConfigMenu.TelemetryMenu.OptionElement.on_click` (void).
- [Line 575](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:575>): `UtilityOctoQuadConfigMenu.TelemetryMenu.OptionElement.onLeftInput` → `UtilityOctoQuadConfigMenu.TelemetryMenu.OptionElement.on_left_input` (void).
- [Line 580](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:580>): `UtilityOctoQuadConfigMenu.TelemetryMenu.OptionElement.onRightInput` → `UtilityOctoQuadConfigMenu.TelemetryMenu.OptionElement.on_right_input` (void).
- [Line 635](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:635>): `UtilityOctoQuadConfigMenu.TelemetryMenu.EnumOption.getValue` → `UtilityOctoQuadConfigMenu.TelemetryMenu.EnumOption.get_value` (Enum).
- [Line 690](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:690>): `UtilityOctoQuadConfigMenu.TelemetryMenu.IntegerOption.getValue` → `UtilityOctoQuadConfigMenu.TelemetryMenu.IntegerOption.get_value` (int).
- [Line 752](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:752>): `UtilityOctoQuadConfigMenu.TelemetryMenu.BooleanOption.getValue` → `UtilityOctoQuadConfigMenu.TelemetryMenu.BooleanOption.get_value` (boolean).
- [Line 786](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:786>): `UtilityOctoQuadConfigMenu.TelemetryMenu.StaticClickableOption.onClick` → `UtilityOctoQuadConfigMenu.TelemetryMenu.StaticClickableOption.on_click` (void).
- [Line 799](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:799>): `UtilityOctoQuadConfigMenu.TelemetryMenu.Element.setParent` → `UtilityOctoQuadConfigMenu.TelemetryMenu.Element.set_parent` (void).
- [Line 809](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:809>): `UtilityOctoQuadConfigMenu.TelemetryMenu.Element.getDisplayText` → `UtilityOctoQuadConfigMenu.TelemetryMenu.Element.get_display_text` (String).

### [external/utilities/UtilityTestHardware.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java>)

- [Line 140](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:140>): `UtilityTestHardware.testMotor` → `UtilityTestHardware.test_motor` (void).
- [Line 152](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:152>): `UtilityTestHardware.testServo` → `UtilityTestHardware.test_servo` (void).
- [Line 163](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:163>): `UtilityTestHardware.testCRServo` → `UtilityTestHardware.test_cr_servo` (void).
- [Line 174](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:174>): `UtilityTestHardware.testColorSensor` → `UtilityTestHardware.test_color_sensor` (void).
- [Line 180](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:180>): `UtilityTestHardware.testDistanceSensor` → `UtilityTestHardware.test_distance_sensor` (void).
- [Line 185](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:185>): `UtilityTestHardware.testTouchSensor` → `UtilityTestHardware.test_touch_sensor` (void).
- [Line 189](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:189>): `UtilityTestHardware.testIMU` → `UtilityTestHardware.test_imu` (void).
- [Line 193](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:193>): `UtilityTestHardware.testWebcam` → `UtilityTestHardware.test_webcam` (void).
- [Line 197](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:197>): `UtilityTestHardware.testAnalogSensor` → `UtilityTestHardware.test_analog_sensor` (void).
- [Line 200](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:200>): `UtilityTestHardware.testDigitalChannel` → `UtilityTestHardware.test_digital_channel` (void).

### [internal/FtcRobotControllerActivity.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java>)

- [Line 140](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:140>): `FtcRobotControllerActivity.getTag` → `FtcRobotControllerActivity.get_tag` (String).
- [Line 188](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:188>): `FtcRobotControllerActivity.RobotRestarter.requestRestart` → `FtcRobotControllerActivity.RobotRestarter.request_restart` (void).
- [Line 228](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:228>): `FtcRobotControllerActivity.passReceivedUsbAttachmentsToEventLoop` → `FtcRobotControllerActivity.pass_received_usb_attachments_to_event_loop` (void).
- [Line 251](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:251>): `FtcRobotControllerActivity.enforcePermissionValidator` → `FtcRobotControllerActivity.enforce_permission_validator` (boolean).
- [Line 264](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:264>): `FtcRobotControllerActivity.setPermissionsValidated` → `FtcRobotControllerActivity.set_permissions_validated` (void).
- [Line 414](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:414>): `FtcRobotControllerActivity.createUpdateUI` → `FtcRobotControllerActivity.create_update_ui` (UpdateUI).
- [Line 422](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:422>): `FtcRobotControllerActivity.createUICallback` → `FtcRobotControllerActivity.create_ui_callback` (UpdateUI.Callback).
- [Line 487](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:487>): `FtcRobotControllerActivity.bindToService` → `FtcRobotControllerActivity.bind_to_service` (void).
- [Line 494](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:494>): `FtcRobotControllerActivity.unbindFromService` → `FtcRobotControllerActivity.unbind_from_service` (void).
- [Line 501](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:501>): `FtcRobotControllerActivity.readNetworkType` → `FtcRobotControllerActivity.read_network_type` (void).
- [Line 531](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:531>): `FtcRobotControllerActivity.isRobotRunning` → `FtcRobotControllerActivity.is_robot_running` (boolean).
- [Line 631](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:631>): `FtcRobotControllerActivity.updateMonitorLayout` → `FtcRobotControllerActivity.update_monitor_layout` (void).
- [Line 667](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:667>): `FtcRobotControllerActivity.onServiceBind` → `FtcRobotControllerActivity.on_service_bind` (void).
- [Line 698](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:698>): `FtcRobotControllerActivity.updateUIAndRequestRobotSetup` → `FtcRobotControllerActivity.update_ui_and_request_robot_setup` (void).
- [Line 713](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:713>): `FtcRobotControllerActivity.requestRobotSetup` → `FtcRobotControllerActivity.request_robot_setup` (void).
- [Line 744](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:744>): `FtcRobotControllerActivity.createOpModeRegister` → `FtcRobotControllerActivity.create_op_mode_register` (OpModeRegister).
- [Line 748](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:748>): `FtcRobotControllerActivity.shutdownRobot` → `FtcRobotControllerActivity.shutdown_robot` (void).
- [Line 752](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:752>): `FtcRobotControllerActivity.requestRobotRestart` → `FtcRobotControllerActivity.request_robot_restart` (void).
- [Line 765](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:765>): `FtcRobotControllerActivity.showRestartRobotCompleteToast` → `FtcRobotControllerActivity.show_restart_robot_complete_toast` (void).
- [Line 769](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:769>): `FtcRobotControllerActivity.checkPreferredChannel` → `FtcRobotControllerActivity.check_preferred_channel` (void).
- [Line 787](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:787>): `FtcRobotControllerActivity.hittingMenuButtonBrightensScreen` → `FtcRobotControllerActivity.hitting_menu_button_brightens_screen` (void).
- [Line 815](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:815>): `FtcRobotControllerActivity.initWifiMute` → `FtcRobotControllerActivity.init_wifi_mute` (void).

### [internal/PermissionValidatorWrapper.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java>)

- [Line 61](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java:61>): `PermissionValidatorWrapper.mapPermissionToExplanation` → `PermissionValidatorWrapper.map_permission_to_explanation` (String).
- [Line 86](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java:86>): `PermissionValidatorWrapper.onStartApplication` → `PermissionValidatorWrapper.on_start_application` (Class).


## Bundled overridden method names

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/BasicOmniOpMode_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOmniOpMode_Linear.java>)

- [Line 77](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOmniOpMode_Linear.java:77>): `BasicOmniOpMode_Linear.runOpMode` (void).

### [external/samples/BasicOpMode_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Linear.java>)

- [Line 62](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Linear.java:62>): `BasicOpMode_Linear.runOpMode` (void).

### [external/samples/ConceptAprilTag.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTag.java>)

- [Line 103](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTag.java:103>): `ConceptAprilTag.runOpMode` (void).

### [external/samples/ConceptAprilTagEasy.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagEasy.java>)

- [Line 85](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagEasy.java:85>): `ConceptAprilTagEasy.runOpMode` (void).

### [external/samples/ConceptAprilTagLocalization.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java>)

- [Line 129](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java:129>): `ConceptAprilTagLocalization.runOpMode` (void).

### [external/samples/ConceptAprilTagMultiPortal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagMultiPortal.java>)

- [Line 54](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagMultiPortal.java:54>): `ConceptAprilTagMultiPortal.runOpMode` (void).

### [external/samples/ConceptAprilTagOptimizeExposure.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java>)

- [Line 88](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java:88>): `ConceptAprilTagOptimizeExposure.runOpMode` (void).

### [external/samples/ConceptAprilTagSwitchableCameras.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java>)

- [Line 75](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java:75>): `ConceptAprilTagSwitchableCameras.runOpMode` (void).

### [external/samples/ConceptExploringIMUOrientation.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java>)

- [Line 87](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java:87>): `ConceptExploringIMUOrientation.runOpMode` (void).

### [external/samples/ConceptGamepadEdgeDetection.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java>)

- [Line 58](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java:58>): `ConceptGamepadEdgeDetection.runOpMode` (void).

### [external/samples/ConceptGamepadRumble.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadRumble.java>)

- [Line 104](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadRumble.java:104>): `ConceptGamepadRumble.runOpMode` (void).

### [external/samples/ConceptGamepadTouchpad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadTouchpad.java>)

- [Line 40](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadTouchpad.java:40>): `ConceptGamepadTouchpad.runOpMode` (void).

### [external/samples/ConceptMotorBulkRead.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java>)

- [Line 97](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java:97>): `ConceptMotorBulkRead.runOpMode` (void).

### [external/samples/ConceptRampMotorSpeed.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRampMotorSpeed.java>)

- [Line 64](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRampMotorSpeed.java:64>): `ConceptRampMotorSpeed.runOpMode` (void).

### [external/samples/ConceptRevSPARKMini.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevSPARKMini.java>)

- [Line 60](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevSPARKMini.java:60>): `ConceptRevSPARKMini.runOpMode` (void).

### [external/samples/ConceptScanServo.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptScanServo.java>)

- [Line 66](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptScanServo.java:66>): `ConceptScanServo.runOpMode` (void).

### [external/samples/ConceptSoundsASJava.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsASJava.java>)

- [Line 84](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsASJava.java:84>): `ConceptSoundsASJava.runOpMode` (void).

### [external/samples/ConceptSoundsOnBotJava.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsOnBotJava.java>)

- [Line 79](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsOnBotJava.java:79>): `ConceptSoundsOnBotJava.runOpMode` (void).

### [external/samples/ConceptSoundsSKYSTONE.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsSKYSTONE.java>)

- [Line 60](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsSKYSTONE.java:60>): `ConceptSoundsSKYSTONE.runOpMode` (void).

### [external/samples/ConceptTelemetry.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java>)

- [Line 83](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java:83>): `ConceptTelemetry.runOpMode` (void).

### [external/samples/ConceptVisionColorLocator_Circle.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Circle.java>)

- [Line 74](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Circle.java:74>): `ConceptVisionColorLocator_Circle.runOpMode` (void).

### [external/samples/ConceptVisionColorLocator_Rectangle.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Rectangle.java>)

- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Rectangle.java:71>): `ConceptVisionColorLocator_Rectangle.runOpMode` (void).

### [external/samples/ConceptVisionColorSensor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorSensor.java>)

- [Line 66](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorSensor.java:66>): `ConceptVisionColorSensor.runOpMode` (void).

### [external/samples/externalhardware/ConceptExternalHardwareClass.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/ConceptExternalHardwareClass.java>)

- [Line 75](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/ConceptExternalHardwareClass.java:75>): `ConceptExternalHardwareClass.runOpMode` (void).

### [external/samples/RobotAutoDriveByEncoder_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java>)

- [Line 88](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java:88>): `RobotAutoDriveByEncoder_Linear.runOpMode` (void).

### [external/samples/RobotAutoDriveByGyro_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java>)

- [Line 138](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:138>): `RobotAutoDriveByGyro_Linear.runOpMode` (void).

### [external/samples/RobotAutoDriveByTime_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByTime_Linear.java>)

- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByTime_Linear.java:71>): `RobotAutoDriveByTime_Linear.runOpMode` (void).

### [external/samples/RobotAutoDriveToAprilTagOmni.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java>)

- [Line 132](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:132>): `RobotAutoDriveToAprilTagOmni.runOpMode` (void).

### [external/samples/RobotAutoDriveToAprilTagTank.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java>)

- [Line 126](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:126>): `RobotAutoDriveToAprilTagTank.runOpMode` (void).

### [external/samples/RobotAutoDriveToLine_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java>)

- [Line 77](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java:77>): `RobotAutoDriveToLine_Linear.runOpMode` (void).

### [external/samples/RobotTeleopPOV_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopPOV_Linear.java>)

- [Line 69](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopPOV_Linear.java:69>): `RobotTeleopPOV_Linear.runOpMode` (void).

### [external/samples/SensorAndyMarkIMUNonOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUNonOrthogonal.java>)

- [Line 85](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUNonOrthogonal.java:85>): `SensorAndyMarkIMUNonOrthogonal.runOpMode` (void).

### [external/samples/SensorAndyMarkIMUOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUOrthogonal.java>)

- [Line 92](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUOrthogonal.java:92>): `SensorAndyMarkIMUOrthogonal.runOpMode` (void).

### [external/samples/SensorAndyMarkTOF.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkTOF.java>)

- [Line 56](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkTOF.java:56>): `SensorAndyMarkTOF.runOpMode` (void).

### [external/samples/SensorBNO055IMU.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java>)

- [Line 78](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java:78>): `SensorBNO055IMU.runOpMode` (void).

### [external/samples/SensorBNO055IMUCalibration.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java>)

- [Line 117](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java:117>): `SensorBNO055IMUCalibration.runOpMode` (void).

### [external/samples/SensorColor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorColor.java>)

- [Line 93](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorColor.java:93>): `SensorColor.runOpMode` (void).

### [external/samples/SensorDigitalTouch.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorDigitalTouch.java>)

- [Line 50](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorDigitalTouch.java:50>): `SensorDigitalTouch.runOpMode` (void).

### [external/samples/SensorHuskyLens.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorHuskyLens.java>)

- [Line 70](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorHuskyLens.java:70>): `SensorHuskyLens.runOpMode` (void).

### [external/samples/SensorIMUNonOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUNonOrthogonal.java>)

- [Line 83](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUNonOrthogonal.java:83>): `SensorIMUNonOrthogonal.runOpMode` (void).

### [external/samples/SensorIMUOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUOrthogonal.java>)

- [Line 89](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUOrthogonal.java:89>): `SensorIMUOrthogonal.runOpMode` (void).

### [external/samples/SensorKLNavxMicro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java>)

- [Line 69](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java:69>): `SensorKLNavxMicro.runOpMode` (void).

### [external/samples/SensorLimelight3A.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorLimelight3A.java>)

- [Line 75](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorLimelight3A.java:75>): `SensorLimelight3A.runOpMode` (void).

### [external/samples/SensorMRColor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRColor.java>)

- [Line 60](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRColor.java:60>): `SensorMRColor.runOpMode` (void).

### [external/samples/SensorMRGyro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java>)

- [Line 69](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java:69>): `SensorMRGyro.runOpMode` (void).

### [external/samples/SensorMROpticalDistance.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMROpticalDistance.java>)

- [Line 50](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMROpticalDistance.java:50>): `SensorMROpticalDistance.runOpMode` (void).

### [external/samples/SensorMRRangeSensor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRRangeSensor.java>)

- [Line 54](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRRangeSensor.java:54>): `SensorMRRangeSensor.runOpMode` (void).

### [external/samples/SensorOctoQuad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java>)

- [Line 87](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java:87>): `SensorOctoQuad.runOpMode` (void).

### [external/samples/SensorOctoQuadAdv.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java>)

- [Line 88](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:88>): `SensorOctoQuadAdv.runOpMode` (void).

### [external/samples/SensorREV2mDistance.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorREV2mDistance.java>)

- [Line 58](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorREV2mDistance.java:58>): `SensorREV2mDistance.runOpMode` (void).

### [external/samples/SensorSparkFunOTOS.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorSparkFunOTOS.java>)

- [Line 33](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorSparkFunOTOS.java:33>): `SensorSparkFunOTOS.runOpMode` (void).

### [external/samples/SensorTouch.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorTouch.java>)

- [Line 55](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorTouch.java:55>): `SensorTouch.runOpMode` (void).

### [external/samples/UtilityCameraFrameCapture.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java>)

- [Line 77](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java:77>): `UtilityCameraFrameCapture.runOpMode` (void).

### [external/samples/UtilityOctoQuadConfigMenu.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java>)

- [Line 83](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:83>): `UtilityOctoQuadConfigMenu.runOpMode` (void).
- [Line 117](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:117>): `UtilityOctoQuadConfigMenu.onClick` (void).
- [Line 181](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:181>): `UtilityOctoQuadConfigMenu.getDisplayText` (String).
- [Line 202](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:202>): `UtilityOctoQuadConfigMenu.onClick` (void).
- [Line 216](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:216>): `UtilityOctoQuadConfigMenu.getDisplayText` (String).
- [Line 237](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:237>): `UtilityOctoQuadConfigMenu.onClick` (void).
- [Line 553](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:553>): `UtilityOctoQuadConfigMenu.TelemetryMenu.MenuElement.getDisplayText` (String).
- [Line 601](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:601>): `UtilityOctoQuadConfigMenu.TelemetryMenu.EnumOption.onLeftInput` (void).
- [Line 612](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:612>): `UtilityOctoQuadConfigMenu.TelemetryMenu.EnumOption.onRightInput` (void).
- [Line 623](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:623>): `UtilityOctoQuadConfigMenu.TelemetryMenu.EnumOption.onClick` (void).
- [Line 629](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:629>): `UtilityOctoQuadConfigMenu.TelemetryMenu.EnumOption.getDisplayText` (String).
- [Line 656](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:656>): `UtilityOctoQuadConfigMenu.TelemetryMenu.IntegerOption.onLeftInput` (void).
- [Line 667](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:667>): `UtilityOctoQuadConfigMenu.TelemetryMenu.IntegerOption.onRightInput` (void).
- [Line 678](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:678>): `UtilityOctoQuadConfigMenu.TelemetryMenu.IntegerOption.onClick` (void).
- [Line 684](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:684>): `UtilityOctoQuadConfigMenu.TelemetryMenu.IntegerOption.getDisplayText` (String).
- [Line 717](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:717>): `UtilityOctoQuadConfigMenu.TelemetryMenu.BooleanOption.onLeftInput` (void).
- [Line 723](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:723>): `UtilityOctoQuadConfigMenu.TelemetryMenu.BooleanOption.onRightInput` (void).
- [Line 729](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:729>): `UtilityOctoQuadConfigMenu.TelemetryMenu.BooleanOption.onClick` (void).
- [Line 735](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:735>): `UtilityOctoQuadConfigMenu.TelemetryMenu.BooleanOption.getDisplayText` (String).
- [Line 770](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:770>): `UtilityOctoQuadConfigMenu.TelemetryMenu.StaticItem.getDisplayText` (String).
- [Line 788](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:788>): `UtilityOctoQuadConfigMenu.TelemetryMenu.StaticClickableOption.getDisplayText` (String).
- [Line 814](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:814>): `UtilityOctoQuadConfigMenu.TelemetryMenu.SpecialUpElement.getDisplayText` (String).

### [internal/FtcRobotControllerActivity.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java>)

- [Line 196](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:196>): `FtcRobotControllerActivity.onServiceConnected` (void).
- [Line 202](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:202>): `FtcRobotControllerActivity.onServiceDisconnected` (void).
- [Line 209](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:209>): `FtcRobotControllerActivity.onNewIntent` (void).
- [Line 268](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:268>): `FtcRobotControllerActivity.onCreate` (void).
- [Line 324](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:324>): `FtcRobotControllerActivity.onClick` (void).
- [Line 328](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:328>): `FtcRobotControllerActivity.onMenuItemClick` (boolean).
- [Line 428](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:428>): `FtcRobotControllerActivity.onStart` (void).
- [Line 434](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:434>): `FtcRobotControllerActivity.onTouch` (boolean).
- [Line 442](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:442>): `FtcRobotControllerActivity.onResume` (void).
- [Line 451](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:451>): `FtcRobotControllerActivity.onPause` (void).
- [Line 457](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:457>): `FtcRobotControllerActivity.onStop` (void).
- [Line 465](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:465>): `FtcRobotControllerActivity.onDestroy` (void).
- [Line 514](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:514>): `FtcRobotControllerActivity.onWindowFocusChanged` (void).
- [Line 524](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:524>): `FtcRobotControllerActivity.onCreateOptionsMenu` (boolean).
- [Line 551](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:551>): `FtcRobotControllerActivity.onOptionsItemSelected` (boolean).
- [Line 620](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:620>): `FtcRobotControllerActivity.onConfigurationChanged` (void).
- [Line 651](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:651>): `FtcRobotControllerActivity.onActivityResult` (void).
- [Line 676](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:676>): `FtcRobotControllerActivity.getWebServer` (WebServer).
- [Line 682](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:682>): `FtcRobotControllerActivity.getOnBotJavaHelper` (OnBotJavaHelper).
- [Line 688](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:688>): `FtcRobotControllerActivity.getEventLoopManager` (EventLoopManager).
- [Line 791](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:791>): `FtcRobotControllerActivity.onMenuVisibilityChanged` (void).
- [Line 802](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:802>): `FtcRobotControllerActivity.SharedPreferencesListener.onSharedPreferenceChanged` (void).
- [Line 824](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:824>): `FtcRobotControllerActivity.onMotionDetected` (void).
- [Line 839](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:839>): `FtcRobotControllerActivity.onUserInteraction` (void).

### [internal/PermissionValidatorWrapper.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java>)

- [Line 78](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java:78>): `PermissionValidatorWrapper.onCreate` (void).


## Bundled variable-name differences

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/ConceptExploringIMUOrientation.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java>)

- [Line 79](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java:79>): `ConceptExploringIMUOrientation.LAST_DIRECTION` → `ConceptExploringIMUOrientation.lastDirection` (int).
- [Line 80](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java:80>): `ConceptExploringIMUOrientation.TRIGGER_THRESHOLD` → `ConceptExploringIMUOrientation.triggerThreshold` (float).

### [external/samples/ConceptGamepadRumble.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadRumble.java>)

- [Line 101](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadRumble.java:101>): `ConceptGamepadRumble.HALF_TIME` → `ConceptGamepadRumble.halfTime` (double).
- [Line 102](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadRumble.java:102>): `ConceptGamepadRumble.TRIGGER_THRESHOLD` → `ConceptGamepadRumble.triggerThreshold` (double).

### [external/samples/ConceptMotorBulkRead.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java>)

- [Line 86](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java:86>): `ConceptMotorBulkRead.TEST_CYCLES` → `ConceptMotorBulkRead.testCycles` (int).

### [external/samples/ConceptRevLED.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevLED.java>)

- [Line 56](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevLED.java:56>): `ConceptRevLED.frontLED_red` → `ConceptRevLED.frontledRed` (LED).
- [Line 57](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevLED.java:57>): `ConceptRevLED.frontLED_green` → `ConceptRevLED.frontledGreen` (LED).

### [external/samples/ConceptSoundsASJava.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsASJava.java>)

- [Line 82](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsASJava.java:82>): `ConceptSoundsASJava.WasB` → `ConceptSoundsASJava.wasB` (boolean).

### [external/samples/ConceptSoundsOnBotJava.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsOnBotJava.java>)

- [Line 77](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsOnBotJava.java:77>): `ConceptSoundsOnBotJava.WasB` → `ConceptSoundsOnBotJava.wasB` (boolean).

### [external/samples/ConceptSoundsSKYSTONE.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsSKYSTONE.java>)

- [Line 66](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsSKYSTONE.java:66>): `ConceptSoundsSKYSTONE.was_dpad_up` → `ConceptSoundsSKYSTONE.wasDpadUp` (boolean).
- [Line 67](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsSKYSTONE.java:67>): `ConceptSoundsSKYSTONE.was_dpad_down` → `ConceptSoundsSKYSTONE.wasDpadDown` (boolean).

### [external/samples/externalhardware/RobotHardware.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java>)

- [Line 119](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java:119>): `RobotHardware.Drive` → `RobotHardware.drive` (double).
- [Line 119](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java:119>): `RobotHardware.Turn` → `RobotHardware.turn` (double).

### [external/samples/RobotAutoDriveToAprilTagOmni.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java>)

- [Line 100](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:100>): `RobotAutoDriveToAprilTagOmni.DESIRED_DISTANCE` → `RobotAutoDriveToAprilTagOmni.desiredDistance` (double).
- [Line 105](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:105>): `RobotAutoDriveToAprilTagOmni.SPEED_GAIN` → `RobotAutoDriveToAprilTagOmni.speedGain` (double).
- [Line 106](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:106>): `RobotAutoDriveToAprilTagOmni.STRAFE_GAIN` → `RobotAutoDriveToAprilTagOmni.strafeGain` (double).
- [Line 107](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:107>): `RobotAutoDriveToAprilTagOmni.TURN_GAIN` → `RobotAutoDriveToAprilTagOmni.turnGain` (double).
- [Line 109](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:109>): `RobotAutoDriveToAprilTagOmni.MAX_AUTO_SPEED` → `RobotAutoDriveToAprilTagOmni.maxAutoSpeed` (double).
- [Line 110](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:110>): `RobotAutoDriveToAprilTagOmni.MAX_AUTO_STRAFE` → `RobotAutoDriveToAprilTagOmni.maxAutoStrafe` (double).
- [Line 111](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:111>): `RobotAutoDriveToAprilTagOmni.MAX_AUTO_TURN` → `RobotAutoDriveToAprilTagOmni.maxAutoTurn` (double).
- [Line 118](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:118>): `RobotAutoDriveToAprilTagOmni.USE_WEBCAM` → `RobotAutoDriveToAprilTagOmni.useWebcam` (boolean).
- [Line 119](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:119>): `RobotAutoDriveToAprilTagOmni.DESIRED_TAG_ID` → `RobotAutoDriveToAprilTagOmni.desiredTagId` (int).
- [Line 120](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:120>): `RobotAutoDriveToAprilTagOmni.DESIRED_CLUSTER_NAME` → `RobotAutoDriveToAprilTagOmni.desiredClusterName` (String).

### [external/samples/RobotAutoDriveToAprilTagTank.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java>)

- [Line 98](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:98>): `RobotAutoDriveToAprilTagTank.DESIRED_DISTANCE` → `RobotAutoDriveToAprilTagTank.desiredDistance` (double).
- [Line 103](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:103>): `RobotAutoDriveToAprilTagTank.SPEED_GAIN` → `RobotAutoDriveToAprilTagTank.speedGain` (double).
- [Line 104](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:104>): `RobotAutoDriveToAprilTagTank.TURN_GAIN` → `RobotAutoDriveToAprilTagTank.turnGain` (double).
- [Line 106](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:106>): `RobotAutoDriveToAprilTagTank.MAX_AUTO_SPEED` → `RobotAutoDriveToAprilTagTank.maxAutoSpeed` (double).
- [Line 107](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:107>): `RobotAutoDriveToAprilTagTank.MAX_AUTO_TURN` → `RobotAutoDriveToAprilTagTank.maxAutoTurn` (double).
- [Line 112](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:112>): `RobotAutoDriveToAprilTagTank.USE_WEBCAM` → `RobotAutoDriveToAprilTagTank.useWebcam` (boolean).
- [Line 113](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:113>): `RobotAutoDriveToAprilTagTank.DESIRED_TAG_ID` → `RobotAutoDriveToAprilTagTank.desiredTagId` (int).
- [Line 114](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:114>): `RobotAutoDriveToAprilTagTank.DESIRED_CLUSTER_NAME` → `RobotAutoDriveToAprilTagTank.desiredClusterName` (String).

### [external/samples/SensorHuskyLens.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorHuskyLens.java>)

- [Line 66](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorHuskyLens.java:66>): `SensorHuskyLens.READ_PERIOD` → `SensorHuskyLens.readPeriod` (int).

### [external/samples/SensorOctoQuad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java>)

- [Line 70](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java:70>): `SensorOctoQuad.ODO_LEFT` → `SensorOctoQuad.odoLeft` (int).
- [Line 71](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java:71>): `SensorOctoQuad.ODO_RIGHT` → `SensorOctoQuad.odoRight` (int).
- [Line 72](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java:72>): `SensorOctoQuad.ODO_PERP` → `SensorOctoQuad.odoPerp` (int).

### [external/samples/SensorOctoQuadAdv.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java>)

- [Line 147](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:147>): `OctoSwerveDrive.LeftFront` → `OctoSwerveDrive.leftFront` (OctoSwerveModule).
- [Line 148](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:148>): `OctoSwerveDrive.RightFront` → `OctoSwerveDrive.rightFront` (OctoSwerveModule).
- [Line 149](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:149>): `OctoSwerveDrive.LeftBack` → `OctoSwerveDrive.leftBack` (OctoSwerveModule).
- [Line 150](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:150>): `OctoSwerveDrive.RightBack` → `OctoSwerveDrive.rightBack` (OctoSwerveModule).

### [external/samples/UtilityCameraFrameCapture.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java>)

- [Line 67](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java:67>): `UtilityCameraFrameCapture.USING_WEBCAM` → `UtilityCameraFrameCapture.usingWebcam` (boolean).
- [Line 68](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java:68>): `UtilityCameraFrameCapture.INTERNAL_CAM_DIR` → `UtilityCameraFrameCapture.internalCamDir` (BuiltinCameraDirection).
- [Line 69](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java:69>): `UtilityCameraFrameCapture.RESOLUTION_WIDTH` → `UtilityCameraFrameCapture.resolutionWidth` (int).
- [Line 70](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java:70>): `UtilityCameraFrameCapture.RESOLUTION_HEIGHT` → `UtilityCameraFrameCapture.resolutionHeight` (int).

### [internal/PermissionValidatorWrapper.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java>)

- [Line 45](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java:45>): `PermissionValidatorWrapper.TAG` → `PermissionValidatorWrapper.tag` (String).


## Bundled static-final reference differences

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/ConceptTelemetry.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java>)

- [Line 58](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java:58>): `ConceptTelemetry.poem` → `ConceptTelemetry.POEM` (String[]).

### [internal/PermissionValidatorWrapper.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java>)

- [Line 59](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java:59>): `PermissionValidatorWrapper.startApplication` → `PermissionValidatorWrapper.START_APPLICATION` (Class).


## Bundled enum-type differences

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/SampleRevBlinkinLedDriver.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java>)

- [Line 74](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java:74>): `SampleRevBlinkinLedDriver.DisplayKind` → `SampleRevBlinkinLedDriver.DISPLAY_KIND` (enum).


## Bundled unit/pose value differences

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/ConceptAprilTagLocalization.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java>)

- [Line 99](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java:99>): `ConceptAprilTagLocalization.cameraPosition` → `ConceptAprilTagLocalization.CAMERA_POSITION` (Position).

### [external/samples/ConceptExploringIMUOrientation.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java>)

- [Line 156](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java:156>): `ConceptExploringIMUOrientation.angularVelocity` → `ConceptExploringIMUOrientation.ANGULAR_VELOCITY` (AngularVelocity).

### [external/samples/SensorAndyMarkIMUNonOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUNonOrthogonal.java>)

- [Line 182](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUNonOrthogonal.java:182>): `SensorAndyMarkIMUNonOrthogonal.angularVelocity` → `SensorAndyMarkIMUNonOrthogonal.ANGULAR_VELOCITY` (AngularVelocity).

### [external/samples/SensorAndyMarkIMUOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUOrthogonal.java>)

- [Line 133](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUOrthogonal.java:133>): `SensorAndyMarkIMUOrthogonal.angularVelocity` → `SensorAndyMarkIMUOrthogonal.ANGULAR_VELOCITY` (AngularVelocity).

### [external/samples/SensorBNO055IMU.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java>)

- [Line 72](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java:72>): `SensorBNO055IMU.gravity` → `SensorBNO055IMU.GRAVITY` (Acceleration).

### [external/samples/SensorIMUNonOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUNonOrthogonal.java>)

- [Line 173](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUNonOrthogonal.java:173>): `SensorIMUNonOrthogonal.angularVelocity` → `SensorIMUNonOrthogonal.ANGULAR_VELOCITY` (AngularVelocity).

### [external/samples/SensorIMUOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUOrthogonal.java>)

- [Line 135](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUOrthogonal.java:135>): `SensorIMUOrthogonal.angularVelocity` → `SensorIMUOrthogonal.ANGULAR_VELOCITY` (AngularVelocity).

### [external/samples/SensorKLNavxMicro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java>)

- [Line 99](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java:99>): `SensorKLNavxMicro.rates` → `SensorKLNavxMicro.RATES` (AngularVelocity).

### [external/samples/SensorMRGyro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java>)

- [Line 125](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java:125>): `SensorMRGyro.rates` → `SensorMRGyro.RATES` (AngularVelocity).


## Bundled Java filename differences

Literal differences against the project rules; retained upstream/API conventions should be treated as exceptions.

### [external/samples/BasicOmniOpMode_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOmniOpMode_Linear.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOmniOpMode_Linear.java:1>): `BasicOmniOpMode_Linear`.

### [external/samples/BasicOpMode_Iterative.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Iterative.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Iterative.java:1>): `BasicOpMode_Iterative`.

### [external/samples/BasicOpMode_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Linear.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/BasicOpMode_Linear.java:1>): `BasicOpMode_Linear`.

### [external/samples/ConceptAprilTag.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTag.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTag.java:1>): `ConceptAprilTag`.

### [external/samples/ConceptAprilTagEasy.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagEasy.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagEasy.java:1>): `ConceptAprilTagEasy`.

### [external/samples/ConceptAprilTagLocalization.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagLocalization.java:1>): `ConceptAprilTagLocalization`.

### [external/samples/ConceptAprilTagMultiPortal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagMultiPortal.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagMultiPortal.java:1>): `ConceptAprilTagMultiPortal`.

### [external/samples/ConceptAprilTagOptimizeExposure.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagOptimizeExposure.java:1>): `ConceptAprilTagOptimizeExposure`.

### [external/samples/ConceptAprilTagSwitchableCameras.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptAprilTagSwitchableCameras.java:1>): `ConceptAprilTagSwitchableCameras`.

### [external/samples/ConceptBlackboard.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptBlackboard.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptBlackboard.java:1>): `ConceptBlackboard`.

### [external/samples/ConceptExploringIMUOrientation.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptExploringIMUOrientation.java:1>): `ConceptExploringIMUOrientation`.

### [external/samples/ConceptGamepadEdgeDetection.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadEdgeDetection.java:1>): `ConceptGamepadEdgeDetection`.

### [external/samples/ConceptGamepadRumble.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadRumble.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadRumble.java:1>): `ConceptGamepadRumble`.

### [external/samples/ConceptGamepadTouchpad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadTouchpad.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptGamepadTouchpad.java:1>): `ConceptGamepadTouchpad`.

### [external/samples/ConceptLEDStick.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptLEDStick.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptLEDStick.java:1>): `ConceptLEDStick`.

### [external/samples/ConceptMotorBulkRead.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptMotorBulkRead.java:1>): `ConceptMotorBulkRead`.

### [external/samples/ConceptNullOp.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptNullOp.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptNullOp.java:1>): `ConceptNullOp`.

### [external/samples/ConceptRampMotorSpeed.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRampMotorSpeed.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRampMotorSpeed.java:1>): `ConceptRampMotorSpeed`.

### [external/samples/ConceptRevLED.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevLED.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevLED.java:1>): `ConceptRevLED`.

### [external/samples/ConceptRevSPARKMini.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevSPARKMini.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptRevSPARKMini.java:1>): `ConceptRevSPARKMini`.

### [external/samples/ConceptScanServo.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptScanServo.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptScanServo.java:1>): `ConceptScanServo`.

### [external/samples/ConceptSoundsASJava.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsASJava.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsASJava.java:1>): `ConceptSoundsASJava`.

### [external/samples/ConceptSoundsOnBotJava.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsOnBotJava.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsOnBotJava.java:1>): `ConceptSoundsOnBotJava`.

### [external/samples/ConceptSoundsSKYSTONE.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsSKYSTONE.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptSoundsSKYSTONE.java:1>): `ConceptSoundsSKYSTONE`.

### [external/samples/ConceptTelemetry.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptTelemetry.java:1>): `ConceptTelemetry`.

### [external/samples/ConceptVisionColorLocator_Circle.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Circle.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Circle.java:1>): `ConceptVisionColorLocator_Circle`.

### [external/samples/ConceptVisionColorLocator_Rectangle.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Rectangle.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorLocator_Rectangle.java:1>): `ConceptVisionColorLocator_Rectangle`.

### [external/samples/ConceptVisionColorSensor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorSensor.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/ConceptVisionColorSensor.java:1>): `ConceptVisionColorSensor`.

### [external/samples/externalhardware/ConceptExternalHardwareClass.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/ConceptExternalHardwareClass.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/ConceptExternalHardwareClass.java:1>): `ConceptExternalHardwareClass`.

### [external/samples/externalhardware/RobotHardware.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/externalhardware/RobotHardware.java:1>): `RobotHardware`.

### [external/samples/RobotAutoDriveByEncoder_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByEncoder_Linear.java:1>): `RobotAutoDriveByEncoder_Linear`.

### [external/samples/RobotAutoDriveByGyro_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByGyro_Linear.java:1>): `RobotAutoDriveByGyro_Linear`.

### [external/samples/RobotAutoDriveByTime_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByTime_Linear.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveByTime_Linear.java:1>): `RobotAutoDriveByTime_Linear`.

### [external/samples/RobotAutoDriveToAprilTagOmni.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagOmni.java:1>): `RobotAutoDriveToAprilTagOmni`.

### [external/samples/RobotAutoDriveToAprilTagTank.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToAprilTagTank.java:1>): `RobotAutoDriveToAprilTagTank`.

### [external/samples/RobotAutoDriveToLine_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotAutoDriveToLine_Linear.java:1>): `RobotAutoDriveToLine_Linear`.

### [external/samples/RobotTeleopMecanumFieldRelativeDrive.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopMecanumFieldRelativeDrive.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopMecanumFieldRelativeDrive.java:1>): `RobotTeleopMecanumFieldRelativeDrive`.

### [external/samples/RobotTeleopPOV_Linear.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopPOV_Linear.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopPOV_Linear.java:1>): `RobotTeleopPOV_Linear`.

### [external/samples/RobotTeleopTank_Iterative.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopTank_Iterative.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/RobotTeleopTank_Iterative.java:1>): `RobotTeleopTank_Iterative`.

### [external/samples/SampleRevBlinkinLedDriver.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SampleRevBlinkinLedDriver.java:1>): `SampleRevBlinkinLedDriver`.

### [external/samples/SensorAndyMarkIMUNonOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUNonOrthogonal.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUNonOrthogonal.java:1>): `SensorAndyMarkIMUNonOrthogonal`.

### [external/samples/SensorAndyMarkIMUOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUOrthogonal.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkIMUOrthogonal.java:1>): `SensorAndyMarkIMUOrthogonal`.

### [external/samples/SensorAndyMarkTOF.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkTOF.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorAndyMarkTOF.java:1>): `SensorAndyMarkTOF`.

### [external/samples/SensorBNO055IMU.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMU.java:1>): `SensorBNO055IMU`.

### [external/samples/SensorBNO055IMUCalibration.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorBNO055IMUCalibration.java:1>): `SensorBNO055IMUCalibration`.

### [external/samples/SensorColor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorColor.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorColor.java:1>): `SensorColor`.

### [external/samples/SensorDigitalTouch.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorDigitalTouch.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorDigitalTouch.java:1>): `SensorDigitalTouch`.

### [external/samples/SensorGoBildaPinpoint.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorGoBildaPinpoint.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorGoBildaPinpoint.java:1>): `SensorGoBildaPinpoint`.

### [external/samples/SensorHuskyLens.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorHuskyLens.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorHuskyLens.java:1>): `SensorHuskyLens`.

### [external/samples/SensorIMUNonOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUNonOrthogonal.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUNonOrthogonal.java:1>): `SensorIMUNonOrthogonal`.

### [external/samples/SensorIMUOrthogonal.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUOrthogonal.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorIMUOrthogonal.java:1>): `SensorIMUOrthogonal`.

### [external/samples/SensorKLNavxMicro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorKLNavxMicro.java:1>): `SensorKLNavxMicro`.

### [external/samples/SensorLimelight3A.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorLimelight3A.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorLimelight3A.java:1>): `SensorLimelight3A`.

### [external/samples/SensorMRColor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRColor.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRColor.java:1>): `SensorMRColor`.

### [external/samples/SensorMRGyro.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRGyro.java:1>): `SensorMRGyro`.

### [external/samples/SensorMROpticalDistance.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMROpticalDistance.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMROpticalDistance.java:1>): `SensorMROpticalDistance`.

### [external/samples/SensorMRRangeSensor.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRRangeSensor.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorMRRangeSensor.java:1>): `SensorMRRangeSensor`.

### [external/samples/SensorOctoQuad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuad.java:1>): `SensorOctoQuad`.

### [external/samples/SensorOctoQuadAdv.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadAdv.java:1>): `SensorOctoQuadAdv`.

### [external/samples/SensorOctoQuadLocalization.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadLocalization.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorOctoQuadLocalization.java:1>): `SensorOctoQuadLocalization`.

### [external/samples/SensorREV2mDistance.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorREV2mDistance.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorREV2mDistance.java:1>): `SensorREV2mDistance`.

### [external/samples/SensorSparkFunOTOS.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorSparkFunOTOS.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorSparkFunOTOS.java:1>): `SensorSparkFunOTOS`.

### [external/samples/SensorTouch.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorTouch.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/SensorTouch.java:1>): `SensorTouch`.

### [external/samples/UtilityCameraFrameCapture.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityCameraFrameCapture.java:1>): `UtilityCameraFrameCapture`.

### [external/samples/UtilityOctoQuadConfigMenu.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/UtilityOctoQuadConfigMenu.java:1>): `UtilityOctoQuadConfigMenu`.

### [external/utilities/UtilityTestGamepad.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestGamepad.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestGamepad.java:1>): `UtilityTestGamepad`.

### [external/utilities/UtilityTestHardware.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/utilities/UtilityTestHardware.java:1>): `UtilityTestHardware`.

### [internal/FtcOpModeRegister.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcOpModeRegister.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcOpModeRegister.java:1>): `FtcOpModeRegister`.

### [internal/FtcRobotControllerActivity.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/FtcRobotControllerActivity.java:1>): `FtcRobotControllerActivity`.

### [internal/PermissionValidatorWrapper.java](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java>)

- [Line 1](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/internal/PermissionValidatorWrapper.java:1>): `PermissionValidatorWrapper`.


## Other repository filenames

The following 31 non-Java filenames fail a strict camelCase basename test. This is an inventory of literal differences, not a recommendation to rename tool entry points, Android resources, licenses, binaries, or human-readable legal documents. Android resources and tool-recognized names need their own filename conventions. Extensions are excluded from the casing test; additional dots in a basename still count as differences.

| File | Treatment |
| --- | --- |
| [.github/CONTRIBUTING.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/.github/CONTRIBUTING.md>) | Tool/community-standard filename exception |
| [.github/PULL_REQUEST_TEMPLATE.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/.github/PULL_REQUEST_TEMPLATE.md>) | Tool/community-standard filename exception |
| [.gitignore](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/.gitignore>) | Tool/community-standard filename exception |
| [AGENTS.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/AGENTS.md>) | Tool/community-standard filename exception |
| [Competition-Readiness-TODO.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Competition-Readiness-TODO.md>) | Documentation/asset naming decision |
| [Constants-errors-explanation.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Constants-errors-explanation.md>) | Documentation/asset naming decision |
| [FtcRobotController/src/main/AndroidManifest.xml](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/AndroidManifest.xml>) | Android manifest entry-point exception |
| [FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/sample_conventions.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples/sample_conventions.md>) | Documentation/asset naming decision |
| [FtcRobotController/src/main/res/drawable-xhdpi/icon_menu.png](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/drawable-xhdpi/icon_menu.png>) | Android resource naming/reference exception |
| [FtcRobotController/src/main/res/drawable-xhdpi/icon_robotcontroller.png](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/drawable-xhdpi/icon_robotcontroller.png>) | Android resource naming/reference exception |
| [FtcRobotController/src/main/res/layout/activity_ftc_controller.xml](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml>) | Android resource naming/reference exception |
| [FtcRobotController/src/main/res/menu/ftc_robot_controller.xml](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/menu/ftc_robot_controller.xml>) | Android resource naming/reference exception |
| [FtcRobotController/src/main/res/xml/app_settings.xml](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/xml/app_settings.xml>) | Android resource naming/reference exception |
| [FtcRobotController/src/main/res/xml/device_filter.xml](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/xml/device_filter.xml>) | Android resource naming/reference exception |
| [LICENSE](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/LICENSE>) | Tool/community-standard filename exception |
| [README.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/README.md>) | Tool/community-standard filename exception |
| [TeamCode/lib/OpModeAnnotationProcessor.jar](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/lib/OpModeAnnotationProcessor.jar>) | Build/distribution filename and reference exception |
| [TeamCode/src/main/AndroidManifest.xml](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/AndroidManifest.xml>) | Android manifest entry-point exception |
| [TeamCode/src/main/java/Agents.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/Agents.md>) | Instruction filename decision; preserve instruction discovery |
| [build.common.gradle](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build.common.gradle>) | Shared FTC build script; rename would require updating apply-from paths |
| [build.dependencies.gradle](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/build.dependencies.gradle>) | Shared FTC build script; rename would require updating apply-from paths |
| [doc/legal/AudioBlocksSounds.txt](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/doc/legal/AudioBlocksSounds.txt>) | Documentation/asset naming decision |
| [doc/legal/Exhibit A - LEGO Open Source License Agreement.txt](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/doc/legal/Exhibit A - LEGO Open Source License Agreement.txt>) | Documentation/asset naming decision |
| [doc/legal/LEGO Open Source License.pdf](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/doc/legal/LEGO Open Source License.pdf>) | Documentation/asset naming decision |
| [doc/media/PullRequest.PNG](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/doc/media/PullRequest.PNG>) | Documentation/asset naming decision |
| [gradle/wrapper/gradle-wrapper.jar](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradle/wrapper/gradle-wrapper.jar>) | Build/distribution filename and reference exception |
| [gradle/wrapper/gradle-wrapper.properties](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradle/wrapper/gradle-wrapper.properties>) | Build/distribution filename and reference exception |
| [libs/README.txt](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/libs/README.txt>) | Documentation/asset naming decision |
| [libs/ftc.debug.keystore](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/libs/ftc.debug.keystore>) | Build/distribution filename and reference exception |
| [taskManager-explanation.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/taskManager-explanation.md>) | Documentation/asset naming decision |
| [taskScheduler-errors.md](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/taskScheduler-errors.md>) | Documentation/asset naming decision |

## Gradle, wrapper scripts, and XML

No additional user-defined Gradle function declarations were found. The local Gradle variables in build.common.gradle (apkStoreFile, manifestFile, manifestText, vCodePattern, matcher, vCode, vNamePattern, vName) comply with camelCase. Gradle DSL method calls such as compileSdkVersion and mavenCentral are externally defined APIs, not functions declared by this repository.

The bundled shell wrapper declares [splitJvmOpts](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:158>) outside snake_case; a literal spelling would be split_jvm_opts. Shell/batch wrapper assignments also use uppercase names. Their locations are listed below for completeness; these scripts follow Gradle wrapper and shell conventions and should be treated as a tool-script exception rather than applying the Java variable rule indiscriminately. Environment names such as JAVA_HOME, CLASSPATH, and GRADLE_OPTS are contracts with tools.

| Wrapper variable | Assignment locations |
| --- | --- |
| `gradlew: DEFAULT_JVM_OPTS` | [Line 10](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:10>) |
| `gradlew: APP_NAME` | [Line 12](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:12>) |
| `gradlew: APP_BASE_NAME` | [Line 13](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:13>) |
| `gradlew: MAX_FD` | [Line 16](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:16>), [Line 97](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:97>) |
| `gradlew: PRG` | [Line 52](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:52>), [Line 58](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:58>), [Line 60](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:60>) |
| `gradlew: SAVED` | [Line 63](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:63>) |
| `gradlew: APP_HOME` | [Line 65](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:65>), [Line 115](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:115>) |
| `gradlew: CLASSPATH` | [Line 68](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:68>), [Line 116](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:116>) |
| `gradlew: JAVACMD` | [Line 74](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:74>), [Line 76](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:76>), [Line 85](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:85>) |
| `gradlew: MAX_FD_LIMIT` | [Line 94](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:94>) |
| `gradlew: GRADLE_OPTS` | [Line 110](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:110>) |
| `gradlew: ROOTDIRSRAW` | [Line 119](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:119>) |
| `gradlew: SEP` | [Line 120](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:120>), [Line 123](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:123>) |
| `gradlew: ROOTDIRS` | [Line 122](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:122>) |
| `gradlew: OURCYGPATTERN` | [Line 125](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:125>), [Line 128](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:128>) |
| `gradlew: CHECK` | [Line 133](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:133>) |
| `gradlew: CHECK2` | [Line 134](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:134>) |
| `gradlew: JVM_OPTS` | [Line 159](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew:159>) |
| `gradlew.bat: DEFAULT_JVM_OPTS` | [Line 12](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:12>) |
| `gradlew.bat: DIRNAME` | [Line 14](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:14>) |
| `gradlew.bat: APP_BASE_NAME` | [Line 16](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:16>) |
| `gradlew.bat: APP_HOME` | [Line 17](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:17>) |
| `gradlew.bat: JAVA_EXE` | [Line 22](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:22>), [Line 36](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:36>) |
| `gradlew.bat: JAVA_HOME` | [Line 35](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:35>) |
| `gradlew.bat: CMD_LINE_ARGS` | [Line 56](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:56>), [Line 62](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:62>), [Line 67](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:67>) |
| `gradlew.bat: _SKIP` | [Line 57](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:57>) |
| `gradlew.bat: CLASSPATH` | [Line 72](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/gradlew.bat:72>) |

Android resource schemas, XML attributes, style names, and IDs are not Java classes/functions/variables. Existing Android resource filenames contain valid lowercase underscores, so they need an exception to the camelCase filename rule. The additional Agents.md instruction “XML config: snake_case” is best read as a rule for team-owned robot hardware configuration names, rather than every SDK schema attribute. The repository does not contain a robot hardware configuration XML; the webcam calibration XML currently has only commented-out examples. Hardware-name strings such as Constants.FRONT_LEFT_MOTOR = "leftFront" are deployment configuration keys and must match the actual Robot Controller configuration before any rename.

If “XML config” is intended to cover Android view IDs as well, the controller layout has these 12 additional differences:

- [Line 52](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:52>): `robotIcon` → `robot_icon`.
- [Line 60](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:60>): `textDeviceName` → `text_device_name`.
- [Line 91](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:91>): `RelativeLayout` → `relative_layout`.
- [Line 101](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:101>): `textNetworkConnectionStatus` → `text_network_connection_status`.
- [Line 108](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:108>): `textRobotStatus` → `text_robot_status`.
- [Line 116](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:116>): `textOpMode` → `text_op_mode`.
- [Line 130](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:130>): `monitorContainer` → `monitor_container`.
- [Line 136](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:136>): `cameraMonitorViewId` → `camera_monitor_view_id`.
- [Line 152](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:152>): `textErrorMessage` → `text_error_message`.
- [Line 162](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:162>): `textGamepad1` → `text_gamepad1`.
- [Line 170](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:170>): `textGamepad2` → `text_gamepad2`.
- [Line 180](<C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/FtcRobotController/src/main/res/layout/activity_ftc_controller.xml:180>): `webViewBlocksRuntime` → `web_view_blocks_runtime`.

## Suggested convention clarification

A workable version of your rules would retain snake_case for project-defined functions, camelCase for ordinary variables/objects, PascalCase for classes/interfaces/enum types and matching Java filenames, and UPPER_SNAKE_CASE for enum members and fixed constants/poses. Inherited SDK/API method names, Android resource filenames, and build/tool entry points need explicit exceptions. If you instead want every unit/pose variable or every enum type uppercase, the conditional sections above show the additional declarations affected.

The findings above preserve the original audit snapshot. Source link targets have been updated to the renamed files.

