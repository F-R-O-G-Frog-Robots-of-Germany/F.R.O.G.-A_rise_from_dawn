# Competition-Readiness TODO

Use this as a gated checklist: do not mark hardware or software items complete until they have been tested on the assembled robot. Hardware names, mechanisms, limits, sensor thresholds, tuning values, and match strategy are deliberately left for the team to decide and verify.

## P0 — Confirm the robot and competition requirements

- [ ] Confirm the current FTC game, season manual, robot inspection checklist, event rules, and match timing; record the applicable links/version and assign someone to track rule changes.
- [ ] Finalize the robot design and mechanism list with the build team: drive type, odometry/localization hardware, intake/transfer, scoring mechanism, sensors, servos, and any vision hardware.
- [ ] Create and verify the Robot Controller configuration on the actual Control Hub/Expansion Hub: exact device names, ports, motor/servo types, and sensor types. Record a backup/export outside the robot.
- [ ] Agree on the robot coordinate frame, alliance selection, starting tile/pose, drive orientation, and what state may (or may not) carry between OpModes.
- [ ] Set measurable acceptance criteria for driver control, scoring reliability, autonomous performance, battery life, setup time, and safe stopping. Assign owners and test dates.

## P1 — Mechanical and electrical readiness

- [ ] Finish a legal, robust build: check dimensions, mass, fasteners, wiring strain relief, moving-part clearance, and access to the battery and main switch.
- [ ] Inspect gears, wheels, belts, chains, slides, pivots, and other mechanisms under load; add guards or retention where needed and confirm no mechanism can unexpectedly fall or pinch.
- [ ] Verify battery mounting, connector polarity, wire gauge/routing, hub ventilation, and that every motor/servo is connected to the intended port.
- [ ] Label and document every hardware-map device and port; keep a spare-parts and inspection kit appropriate to the final design.
- [ ] With the robot supported off the floor and mechanisms clear, verify each actuator one at a time at low power; confirm direction, brake/float behavior, end stops, and emergency stop behavior before full-power tests.

## P1 — Bring up drivetrain and localization

- [ ] Reconcile the names and directions in `core/pedro/Constants.java` with the real configuration; its current motor names, directions, Pinpoint name/type, offsets, and tuning values are provisional until checked on hardware.
- [ ] Validate the implemented field-centric controls in `opmodes/teleop/comp/Main.java` on hardware, including stick signs, both alliances, heading reset and speed scaling.
- [ ] Test each drive motor and wheel individually, then verify forward, reverse, strafe, and rotation at low speed. Fix swapped motors, reversed directions, wheel binding, and cable/structure interference.
- [ ] Calibrate the selected localization setup on the assembled robot: encoder/pod directions, offsets, distance units, heading reference, reset behavior, and start-pose entry. Confirm pose against measured field positions and headings.
- [ ] Tune Pedro Pathing only after the real drivetrain and localization are stable; validate acceleration, speed, braking, path following, and repeatability under realistic battery and robot load.
- [ ] Validate the explicit RED/BLUE autonomous entries, mirrored start poses and saved-pose handoff into TeleOp. The default alliance is RED when no autonomous selection has run.

## P1 — Finish and safety-check mechanisms

- [ ] Confirm which current subsystems (`subsystems/Intake.java`, `subsystems/Shooter.java`) match the final robot/game; remove or adapt mechanisms that are not actually on the robot.
- [ ] Validate the implemented subsystem initialization, update and ordinary STOP cleanup in `Robot.java`. Additional cleanup exception aggregation is intentionally omitted by team choice.
- [ ] Replace provisional motor/servo names and directions with the verified Robot Controller configuration; test each output at reduced power before enabling full operation.
- [ ] Define mechanism safe states and hard/software limits with the build team. Test OpMode stop, DS stop, interrupted initialization, sensor disconnect, and stall/current-limit behavior so no actuator is left hazardous.
- [ ] Set and validate intake sensor thresholds using real game elements and multiple trials; `Intake.java` currently uses zero-distance placeholders, so its `loaded()` result is not ready for match control.
- [ ] Verify transfer/intake timing, jam handling, reversals, sensor ordering, and behavior when a game element is absent, stuck, or detected inconsistently.
- [ ] Measure shooter output on the real mechanism and replace/validate the distance-to-velocity placeholder, PIDF gains, encoder conversion, hood positions, and any target-distance model. Do not tune by assumption.
- [ ] Add operator controls and clear feedback for each mechanism, including a way to stop/recover it safely; prevent conflicting simultaneous commands.

## P1 — Build and validate autonomous

- [ ] Decide which autonomous actions are realistic and repeatable for the final robot and current game; prioritize a reliable legal start/parking routine before adding scoring complexity.
- [ ] Validate the nine existing routes and their RED/BLUE entries, with paths prepared during INIT and bounded path/shooter waits. An independent fallback route is intentionally omitted.
- [ ] Test localization and path segments individually on a marked practice field before running complete routes; ensure robot and mechanism motion remain inside safe/legal boundaries.
- [ ] Add autonomous failure handling: stop or retreat safely on localization loss, mechanism fault, missed sensor event, or timeout; do not continue blindly after a failed step.
- [ ] Run repeated end-to-end trials from the actual start tile with representative battery charge and game elements; record completion rate, timing, final pose, and failure causes.

## P2 — Integration, operator practice, and match operations

- [ ] Run the project compile check: `./gradlew :TeamCode:compileDebugJavaWithJavac`; resolve build errors after the final code/configuration changes.
- [ ] Do a controlled integration sequence: robot on blocks, low-power drive, floor drive, each mechanism, combined TeleOp, then autonomous. Keep a spotter and stop immediately for unexpected motion, heat, smell, sound, or current draw.
- [ ] Check match-loop stability: no unhandled exceptions, blocking waits, stale sensor reads, excessive telemetry, or task leaks; confirm all outputs stop and scheduled work is canceled when the OpMode ends.
- [ ] Confirm telemetry needed for diagnosis is available during practice and that competition mode (`CompConfig.compMode`) disables Panels, video, and diagnostic telemetry; `CompConfig.loopTimeTelemetry` can selectively show loop time.
- [ ] Practice driver/operator handoffs, controls, recovery from jams and missed actions, endgame timing, and safe disabled-robot handling; simplify controls that are unreliable under pressure.
- [ ] Run full mock matches and inspect the robot between runs for loose fasteners, shifted sensors, damaged wiring, overheating, and battery or mechanism issues. Keep a simple issue log and verify each fix in a repeat test.
- [ ] Prepare a competition checklist: charge and label batteries, verify configuration and OpMode selection, select alliance/start pose, inspect robot legality, test stop behavior, bring spares/tools, and back up the known-good code/configuration.

## Hardware decisions to fill in before implementation

- Robot/game and legal scoring objective: **TBD by team**
- Final drive and localization hardware, device names, ports, directions, and measured offsets: **TBD from assembled robot/configuration**
- Final subsystem list and safe positions/limits: **TBD by build team**
- Sensor thresholds, calibration data, and shooter/mechanism tuning: **TBD from bench/field tests**
- Alliance/start-pose selection and autonomous strategy: **TBD by team and validated on field**
- Acceptance targets and named task owners: **TBD by team**
