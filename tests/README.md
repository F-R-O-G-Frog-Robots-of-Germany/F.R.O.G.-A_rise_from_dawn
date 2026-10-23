# Separate desktop regression checks

Robot code is in `TeamCode/src/main/java`. Desktop checks live in this separate `tests/java` folder. They are not included in the robot APK or registered as an Android test source set.

In Android Studio, sync the Gradle project after this move. The **Android** view then shows the robot package under TeamCode; the **Project** view shows this separate tests folder.

All production and standalone test sources can be compiled against the cached real Android/FTC/Pedro/Panels signatures, followed by the standalone desktop checks:

```powershell
.\tools\competition-audit\run.ps1
```

The new `ControlThreadAffinityChecks`, `CompetitionLifecycleChecks`, and `CompetitionShutdownChecks` cover mask recovery, real SDK OpMode startup/STOP and callback ordering, drive arbitration, and cleanup faults. These use simulated robot hardware and affinity commands; they do not establish behavior on a Control Hub. `CompetitionWorkersAudit` exercises the concrete workers/runners with injected camera transport using the separate fixture runner below.

The competition runner also executes `tools/competition-audit/fixtures/CompetitionCollectionAudit.java` after compiling all production sources against the real SDK. It runs the actual autonomous lifecycle and queued collection twice, including the asynchronous vision/planner workers, map, collection runner, Pedro adapter and Foresight follower. It requires a nonempty collection to reach `COMPLETE`, return to the depot, stop intake/raise the flower intake, execute the next authored path, then shoot. It checks continued common-loop/shooter updates, control-thread ownership and final worker shutdown. Only camera transport, physical localization feedback and mechanism hardware are simulated; worker/runner states are not forced to completion. Transport doubles live outside the Android source sets and cannot substitute for the preceding real-SDK compilation.

`limelight/CpuUtilityChecks` verifies one/two/three-CPU affinity sets, exact effective-mask readback, partial/denied requests and restoration of the complete original mask through fake commands. The checks never change the desktop computer's affinity. `limelight/LoopPeriodStatsChecks` uses synthetic timing distributions to verify p95/p99, rounding, long-pause overflow, reset and exclusion of warmup/transition gaps. These checks do not establish a performance gain on a Control Hub.

The Limelight regression and camera-fixture checks retain their existing runner:

`limelight/ButineObstacleChecks` covers the height-aware center underpass, all four flowers, rotating-corner clearance, separate reserves, boundary tangency, detours, unreachable returns, routed deadline fitting, worker geometry snapshots and transit/collection/recovery semantics. Existing mathematical assignment oracles explicitly use `ButineField.unrestricted()`; the full collection integration fixture retains the obstacle-aware default with a valid depot.

```powershell
.\tools\limelight-audit\run.ps1
```

These scripts require Java 17 on PATH, Android SDK 35 and the project's cached dependencies. They are not JUnit tests automatically run by Android Gradle. Real SDK signature compilation runs before any camera transport fixtures, and the scripts do not build or deploy an APK.

`opmodes/auto/structure/AutonomousPoseFaultChecks` exercises the production update loop with real Pedro calculations and simulated localization/drive. It checks FOLLOW/HOLD fault stopping and verifies that a normal loop validates pose once and reuses that result for the shooter.

`opmodes/teleop/comp/TeleOpHiveAimChecks` includes missing-pose and nonfinite-pose checks for both alliances. Its fixture reports a missing pose separately from Pedro's numeric MotionState, so null injection reaches the application guards.

`subsystems/PollenFlowChecks` also covers averaging the two shooter encoders, falling back to either valid encoder, one read per encoder per update, and skipping regulation when both readings are invalid in FLOAT, PREPARE and active SHOOT. Skipped ticks preserve both motor outputs and controller history, including after target edits, until valid feedback returns. Readiness and explicit STOP remain checked.

The TeleOp/tuner input fixtures inject gamepad samples with the real SDK's `Gamepad.copy()` so native edge detection is exercised. They cover control order, held/released buttons across START, disabled configuration actions, stick signs and trigger thresholds/rearming.

`core/pedro/PathSimplificationChecks` samples shared path helpers for both alliances and checks heading behavior, explicit garden lines, hotfix units/validation and mirroring. `core/control/LastPositionStorageChecks` checks handoff rejection/recovery and the SDK elapsed timer's 200-second expiration/reset behavior. `limelight/ButineRegression` includes finite-input and exact-half-turn angle contracts.
