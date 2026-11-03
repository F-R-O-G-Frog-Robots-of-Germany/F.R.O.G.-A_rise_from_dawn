# Butine planning and terminal parking — 28 October 2026

Optional software behavior for the future robot. **Butine and Limelight are disconnected from active competition autonomous and TeleOp.** `CompConfig.OPTIONAL_ASSISTANCE_ENABLED` and `CompConfig.VISION_ENABLED` are both false. Actual competition routes retain their original authored paths and static parking tails; the garden-carry export still intentionally ends without a park. No camera/planner worker, pose correction, dynamic parking or optional timer reset runs in those modes.

The following planning and parking behavior remains available behind that gate for later deliberate use. The footprint, motion and intake values remain provisional. Hardware calibration and posecalculator restructuring are outside this change.

## One current run and one advisory run

The physical sweep planner now evaluates alternative first runs against one second run on the remaining pollen. It compares total feasible pickups, then first-run pickups, then total predicted time. Both runs include their collection, checked depot return and scoring allowance.

The plan contains at most two runs. The runner still dispatches only the first. After the real run and scoring, the caller must solve again from fresh observations; the advisory route is never dispatched automatically. The original greedy rollout remains a candidate.

The finite candidate search keeps obstacle, swept-intake, incidental-contact, capacity and overshoot checks. A desktop regression demonstrates a different first run producing eight predicted contacts over two runs where the previous greedy rollout produced seven, within the same allowance. This is an algorithm example, not measured collection performance.

## Greedy cutoff and official timer

Butine.Params.greedyAtS defaults to 25.0 elapsed autonomous seconds. Before the cutoff, the default horizon is two total runs. At or beyond the cutoff, the physical planner uses one-run greedy. Explicit one-run requests remain one-run; setting the cutoff to zero selects greedy immediately.

When optional assistance is enabled, General uses the FTC SDK resetRuntime() once after START and getRuntime() thereafter. Active static competition operation skips this optional reset and timing policy. The previous separate autoStartedNanos match timer was removed. CompetitionWorkers supplies the SDK timer to ButineRunner; each initial request or replan samples it on the control thread and copies elapsedAutoS into the worker request. The pure planner creates no autonomous clock and does not derive elapsed time from remainingS.

## Terminal parking

The optional queue_park() action provides one terminal park. Its calls are commented out in the competition exports, which keep their original authored tails. With the integration gate disabled, queue_park() and queue_collection() add no actions or parking setup. Optional parking preserves carry-into-TeleOp behavior: it adds no shot. Its alliance zone is selected in actual field coordinates.

A normal optional terminal action waits until the routed parking time plus a one-second allowance needs the remaining 30-second AUTO time. An active optional action may be interrupted when parking becomes due. A failed optional collection instead requests immediate terminal parking and skips its dependent shots and paths. Intake and shooting stop, the flower intake rises, and parking starts from the current localized pose only when its checked approach fits the remaining official allowance. STOP, invalid localization, explicit manual override and the SDK 30-second deadline do not begin fallback motion.

An agreed parking target is a real target: already overlapping another part of the loading zone does not skip a requested move to the partner-coordinated pose. Completion requires target proximity, heading, low measured speed and actual body overlap.

Optional collection requests reserve the depot-to-park allowance once, outside the two-run collection budget. The final route is recalculated from the live position, including after an interrupted collection. A blocked approach or insufficient remaining time stops the sequence. Parking is attempted at most once and never changes a failed collection result into depot success.

## Nominal target and manual override

The official loading volume is approximately 23 inches along the wall by 11 inches into the field and includes the tape. PARK requires partial robot overlap; no minimum majority or center inclusion is required. Red is at A5 and Blue at F2, not the field corners. Sources: [manual 9.3 and 10.5.4](https://ftc-resources.firstinspires.org/ftc/game/cm-html), [setup guide 8.3](https://ftc-resources.firstinspires.org/ftc/field/eventfieldguide).

In the repo's nominal 0–144-inch frame, the derived Red rectangle is X=[0,11], Y=[96.5,119.5]; Blue is X=[133,144], Y=[24.5,47.5]. These are configurable model bounds derived from the official dimensions and tile placement, not a published survey.

For the provisional centered 18-inch square, calculated targets are:

| Alliance | X | Y | Heading | Nominal body overlap |
|---|---:|---:|---:|---|
| Red | 19 | 108 | 0° | 1 inch into the loading volume |
| Blue | 125 | 36 | 180° | 1 inch into the loading volume |

For later optional operation, override parking_pose() in an auto to choose an actual-field pose with your partner. To author one RED target for both alliances, return field_pose(new Pose(redX, redY, headingRadians)). The default is generated by ButineParking.default_target(). parking_parameters() controls the provisional body and motion model. These hooks do not replace any current competition route while assistance is disabled.

The model checks intersection of the rotated physical body with the zone, independently of the larger all-heading routing envelope. A concave body would need its actual occupied outline before a thin arm alone could be used as parking evidence.

Some authored shooting/pickup coordinates already violate the provisional clearance model. Parking handles these with a separate fixed-heading recovery: existing modeled wall/obstacle deficits may only decrease, no new overlap may occur anywhere along the swept body, and the exit must enter the normal conservative routing region. The exit is revalidated from the live pose before dispatch, stops at a clearance point, and the normal parking route is then recomputed from measured arrival. This is recovery from an already-invalid nominal pose, not a claim that the initial body is physically collision-free. A trapped or newly obstructed exit is rejected.

## Vision change

Custom capture-time compensation was removed. Detector projection and detector-frame pose metadata now use the current supplied robot pose. Pose-history buffers and latency subtraction/interpolation were removed. SDK latency metadata, existing freshness checks, repeated-frame handling, pipeline acknowledgment and pose-reset race protection remain.

The neural detection acceptance policy, map replacement policy, Hive estimator and posecalculator structure were not changed.

## Approved collection-outcome policy

The user approved the policy in [Butine-Collection-Outcome-Plan-2026-10-28.md](Butine-Collection-Outcome-Plan-2026-10-28.md). It is implemented only in the dormant optional flow. Each attempt has a typed terminal result; only successful arrival at its requested depot permits its authored continuation. A fresh empty map, infeasible run, refusal, abort or local timeout skips collection-dependent actions and considers immediate checked parking from valid current odometry. Missing vision is distinguished from an empty fresh map. Invalid localization, STOP, the hard deadline and explicit manual override stop rather than taking ownership for parking. Cleanup preserves the attempt's result and cancelled planner work cannot resume collection.

## Desktop verification — 1 November 2026

The latest scoped audit compiled all 72 production files and 33 applicable test sources against the cached real FTC 12.0.0/Pedro 3.0.1 libraries. All 33 standalone programs passed, including 271 active Auto/TeleOp isolation checks, 1,782 optional parking assertions, 70 runner-outcome checks and 56 facade-outcome checks. The real autonomous queue/camera-worker/planner/Pedro integration fixture passed 119 checks, including fresh-empty-map parking, zero-contact full-sensor return, manual override, Driver Station STOP and occupied-drive admission. The separate camera transport/worker fixture passed 101 checks, including typed stale-vision, invalid-pose and service-failure results.

The scoped audit explicitly excludes the same six unrelated stale Performance test sources described below. Its log is build/butine-final-audit-2026-11-01.log. No APK was deployed and no physical behavior was measured.

## Historical desktop verification

Before the latest assistance-isolation and collection-outcome changes, the Limelight audit passed 16,901 checks across 19 programs. Full production compilation against the cached real FTC 12.0.0/Pedro 3.0.1 signatures passed: 72 production files and 30 applicable test sources. All 30 corresponding standalone programs and the end-to-end collection integration fixture passed. The parking integration contributed 1,716 assertions, including the actual authored near-wall/carry endpoints, both alliances, live preemption, partner target overrides, STOP in each phase, SDK deadline, blocked routes and hold-end restoration. These are historical results, not validation of the subsequent changes.

The unfiltered competition audit remains blocked by existing benchmark-test API drift: CaseSelector, RegularRobot and Benchmark classes referenced by the unrelated Performance test sources are absent. Those six benchmark test sources were excluded only from the separate scoped validation command; the repository-wide runner was not changed to hide the failure. Logs are saved under build/limelight-audit-2026-10-28.log, build/competition-audit-2026-10-28.log and build/butine-final-audit-2026-10-28.log. No APK was deployed or physical behavior measured.

