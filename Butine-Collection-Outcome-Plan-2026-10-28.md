# Collection outcome handling — 28 October 2026

Status: approved, with implementation confined to the dormant optional collection code. Butine and Limelight remain disconnected from active competition autonomous and TeleOp programs: the master integration gate and camera startup flag are both false. This describes behavior for later deliberate use; it does not enable collection or replace the active authored routes and their existing static parking.

The purpose is to decide what happens after each collection attempt. A stopped drive is not evidence of successful arrival. Sensor-full interruption stays immediate, the posecalculator and exported coordinates stay in place, and no new neural-network confidence threshold or random search behavior is introduced.

## One result per attempt

Keep execution state separate from an immutable terminal result:

| Outcome | Meaning |
|---|---|
| COMPLETE_AT_DEPOT | Collection or the sensor-full return reached the requested depot under the existing arrival gates. |
| NO_TARGETS | A usable fresh map supplied no eligible pollen. |
| NO_FEASIBLE_RUN | Observed targets exist, but no checked collection and return fit the geometry and allowance. |
| REFUSED | The attempt never started: unavailable service, unusable vision, occupied drive or another admission failure. |
| ABORTED | Accepted work failed, with a separate reason such as invalid localization or unsafe progress. |
| TIMED_OUT | An action allowance or arrival wait expired; distinguish this from the hard match deadline. |
| CANCELLED | Manual override, intentional terminal parking, STOP or shutdown ended accepted work. |

Record the attempt ID, requested depot, final valid pose when available, contact count, full-sensor flag and readable reason. A refusal must not overwrite another active attempt. Cleanup preserves the terminal result; a new request cannot inherit an earlier success.

## Default decisions for each case

**Successful depot arrival.** The caller may continue with its authored action whose origin and purpose match that depot. Arrival proves location, not retained inventory. Existing load and shooter-readiness conditions still apply; the collection result does not introduce a new inventory estimator or independently command a shot. A failed return with a partial or uncertain load follows the conservative parking policy below.

**Full sensor during collection.** Disable intake immediately, cancel the remaining collection path and execute the existing checked return. Repeated full signals leave that return alone. If the return reaches the depot, publish COMPLETE_AT_DEPOT with the full-sensor flag, even when vision counted zero contacts. Do not invent pickup IDs. If the return fails, report that failure and apply the fallback rules below.

**A fresh empty map.** Publish NO_TARGETS, finish the collection sequence, skip its dependent shot and move to terminal parking if possible. Do not drive around looking for pollen, repeatedly solve the same empty map or add a confidence cutoff to manufacture a target. Existing carried pollen remains carried.

**Targets, but no feasible run.** Publish NO_FEASIBLE_RUN. Do not dispatch a partial unchecked route or repeatedly retry unchanged geometry. Skip the collection-dependent continuation and consider terminal parking.

**Vision or planner unavailable.** Publish a refusal, or an abort if accepted work already began. Missing/stale evidence differs from a fresh empty map. With valid current odometry, parking can still use the static field geometry; it need not wait for vision to become ready again. A refusal cannot steal manual drive ownership.

**Partial collection, interruption or failed return.** Stop collection and feeding, preserve carried pollen and report the actual outcome. Path-idle, geometric contacts and a previous attempt's success cannot establish depot arrival. The conservative fallback is parking with the load rather than an uncertain shot.

**Invalid localization.** Stop. Do not park from an old cached pose or a guessed origin. A plausible target or previously clear route does not repair invalid current localization.

**No checked route to park.** Stop outputs and retain the failure detail. Never substitute a direct line through an obstacle or use unrestricted geometry to force acceptance.

**A local movement/settling timeout.** The action failed, but the match may still have time. Park only if the current pose is usable and a newly calculated route fits the remaining official allowance; otherwise stop. A local timeout cannot extend the hard deadline.

**Driver STOP or the hard autonomous deadline.** Cancel pending work and stop mechanisms. Start no parking, recovery, new collection or shot. All match decisions use the FTC SDK autonomous runtime, without a separate match clock.

**Explicit manual override.** Cancel autonomous collection and hand control back. Do not automatically begin parking or take ownership back from the driver.

## Terminal parking handoff

For an opted-in future collection sequence, a recoverable failure requests immediate terminal parking instead of waiting for the normal end-of-auto trigger. Calculate its approach from the current pose, check geometry and remaining official time, disable intake and shooting, and preserve any carried load. Park at most once. A normally completed optional sequence still waits until its estimated parking approach plus reserve needs the remaining AUTO time.

Once parking begins, suppress later collection-dependent shots and paths. Cancel the old planner request so a late publication cannot restart collection. Parking completion does not rewrite the collection result as COMPLETE_AT_DEPOT.

Active competition programs retain their authored paths and static park tails. The optional dynamic parking/collection policy remains dormant behind the integration gate until deliberately enabled.

## Implementation and verification

ButineRunner owns terminal transitions; CompetitionWorkers exposes the correct attempt's result; the optional queue_collection action resolves it before advancing. This requires no new scheduler or worker.

Checks cover empty versus unavailable vision, infeasible routes, refusal during another attempt, partial collection, zero-visual-contact sensor-full success, blocked/failed returns, invalid localization, local timeout with/without parking time, STOP/deadline, manual override, result preservation, one-time parking and stale planner publication. Isolation checks prove that active competition programs create no camera/map/planner workers and execute no optional collection or dynamic-parking actions.

Validation on 1 November 2026 passed all 33 applicable standalone programs and 119 end-to-end autonomous collection checks. Dedicated checks include 271 default-competition isolation assertions, 70 runner-outcome assertions and 56 facade-outcome assertions. The six pre-existing stale Performance test sources remain excluded from this scoped validation; the repository-wide runner is unchanged. See the [planning and parking verification record](Butine-Planning-Parking-2026-10-28.md) for scope and limits.

