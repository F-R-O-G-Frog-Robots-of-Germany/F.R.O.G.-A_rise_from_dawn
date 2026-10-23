# Separate desktop regression checks

Robot code is in `TeamCode/src/main/java`. The existing desktop checks are now in this separate `tests/java` folder. Their contents and Java packages are unchanged. They are not included in the robot APK or registered as an Android test source set.

In Android Studio, sync the Gradle project after this move. The **Android** view then shows the robot package under TeamCode; the **Project** view shows this separate tests folder.

The Limelight regression and camera-fixture checks retain their existing runner:

```powershell
.\tools\limelight-audit\run.ps1
```

That script requires Java 17 on PATH, Android SDK 35 and the cached FTC/Pedro dependencies. The remaining checks are the existing standalone Java programs with `main()` methods, under `core`, `subsystems` and `opmodes`. They are not JUnit tests automatically run by Android Gradle.


## Butine collection checks

Run `tools/limelight-audit/run.ps1` for field clearance, intake sweep/overshoot, continuous runner and actual Pedro adapter checks. Run `tools/competition-audit/run.ps1` to compile the complete source tree against cached real SDK signatures, run standalone checks and exercise the autonomous collection queue through its real workers/follower using simulated camera and hardware feedback. The integration fixture also injects a REV loaded signal before a planned pickup and checks safe return with intake off and no fabricated pickup count.

`tools/butine-visualizer/test_generate_pollen_routes.py` exercises the actual Java planner bridge and exported field/path files. These desktop checks do not establish physical tracking, pickup performance or match timing.
