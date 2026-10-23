# Autonomous localization fault implementation plan

Goal: stop autonomous on an unusable pose, validate each sensor sample once, and skip bad shooter encoder samples without interrupting an active shot.

Updated 17 October 2026 to follow the user's request for a smaller implementation. Implementation is authorized.

## Explanation

A pose contains X, Y and heading. NaN (not a number), infinity or a missing pose cannot be used for navigation. An inaccurate but finite position is a different problem and is outside this check.

The hardware-free reproduction exercised the pinned Pedro follower, mecanum conversion and motor caches. An invalid heading produced invalid wheel commands, which the caches ignored. The previous finite motor power stayed applied until an explicit zero command. This change sends that zero and uses the existing autonomous abort.

## Implementation

- In `AbstractAuto.update_follower_and_telemetry()`, update the follower once and validate the resulting pose once. On failure, stop the follower, explicitly stop drive output and throw the existing `AutoStopped`. The existing outer catch/finally aborts the remaining route and stops mechanisms.
- Reuse the computed pose-validity boolean for shooter targeting. Keep the existing action loop, timeout handling and cleanup. No additional fault class, fault flag, pre-action checks or cleanup abstraction is needed.
- In TeleOp, let the drive guard reuse Robot's existing pose-validity cache. The old pose before the follower update uses its cached result; the new pose after the update is validated once and shared with shooter targeting. Repeated consumers do not repeat the numeric check.
- The shooter encoder is read and validated once in `sample_velocity()` per shooter update. Readiness and telemetry reuse its cached value/validity. For an invalid sample and a valid positive target, skip flywheel regulation for that iteration and retain the last motor command and controller history. The next good sample resumes regulation. An already active shot keeps feeding; a new shot still needs valid readiness. Explicit STOP, zero target and unavailable target handling remain effective.
- Repair the null-pose test fixture so a missing reported pose reaches the application guard without asking Pedro's numeric MotionState constructor to accept null.

The post-update guard runs after the SDK returns; it cannot interrupt a blocking SDK call. No tuning values or route definitions change.

## Verification

Use focused checks for FOLLOW/HOLD with missing/nonfinite pose, explicit zero drive and one pose validation per normal loop. Existing TeleOp checks verify cache reuse and fault/recovery behavior. Existing shooter checks cover valid-invalid-valid encoder sequences in FLOAT, PREPARE and SHOOT, both flywheel motors, readiness, feeding and STOP.

Compile current Java sources and run the relevant standalone regression programs. A normal Gradle/APK build and physical robot trials remain separate verification; report any environment limitation accurately.

## Changes you would like to request

- Notes:

Written by Codex (GPT-6), I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn