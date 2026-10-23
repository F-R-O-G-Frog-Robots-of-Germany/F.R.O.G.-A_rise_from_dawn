# Butine alliance traffic review and proposed fix

Goal: keep our autonomous collection and other autonomous movements from colliding with, occupying the route of, or trapping our alliance partner.

Date: 19 October 2026. Status: review and proposed implementation; this document does not change robot behavior.

Reviewed the current files, including uncommitted work, in `C:\Users\pmkoe_eque6\StudioProjects\F.R.O.G.-A_rise_from_dawn`. The existing field-obstacle implementation and its geometry record remain the starting point. The robot is not built; physical timing, localization error, mechanism envelopes and stopping performance cannot yet be established.

## 1. Recommendation

The user's idea is useful: obtain the partner's autonomous route before the match and reserve the space they need. However, knowing their program gives us a prediction of their position, not their measured position at every instant. A late start, a slower drive, a missed pickup, a conditional branch or a stopped robot can invalidate an exact timetable.

Implement this in two stages:

1. **Reserve the partner's complete swept route for the whole autonomous period.** Agree on separate collection lanes, scoring locations and waiting areas. This can reuse the current static obstacle router and works without relying on exact timing.
2. **Only then add timed reservations and onboard observations**, if shared lanes are needed. Check uncertainty over the entire movement and stopping horizon. Wait in a designated safe area, replan when appropriate, and retain a conservative fallback when the partner cannot be observed.

A complete corridor reservation reduces available collection space. That is an explicit strategy tradeoff. If it leaves no safe route from our start to our depot, change the selected autos, lanes or depot before the match; do not silently remove a reservation.

Neither version guarantees avoidance of arbitrary unexpected partner motion. The static version is conditional on the partner remaining within the agreed corridor. Timed reuse additionally needs a defensible bound on their progress or timely observations. Without either, retain the complete corridor reservation.

## 2. Findings in the current implementation

Line references identify the reviewed working copy and can move with subsequent edits. All Java paths here are relative to `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/`.

| Priority | Evidence | Consequence and required change |
|---|---|---|
| P1 | `limelight/ButineField.java:130–133` defines six fixed field boxes. `is_clear()`, `segment_clear()` and `route()` at lines 166–187 accept neither another robot nor time. | A stationary or moving partner is invisible to the planner. Add an agreed partner corridor to the planning environment. |
| P1 | `limelight/ButineRunner.java:430–468` checks the static pose and checks the segment when dispatching. Lines 472–501 subsequently monitor follower state and timeouts. `limelight/PedroButineDrive.java:42–63` also checks only at dispatch. | A partner entering an already executing leg cannot trigger a traffic stop. A later moving-obstacle implementation needs continuous prediction through the stopping horizon, before motor output. |
| P1 | `opmodes/auto/structure/AbstractAuto.java:212–222` cancels worker drive ownership and directly submits authored paths. Production search currently finds no competition auto calling `queue_collection()`. | Adding reservations only to Butine does not protect the competition autos currently being run. Apply the traffic policy to authored paths, return/park paths and stationary scoring too. |
| P1 | `limelight/Limelight.java:264` accepts only detector results named `pollen`; `core/control/CompetitionWorkers.java:52–57` constructs pollen mapping/localization/collection services. | The present camera pipeline provides no partner robot track. Do not display a schedule-derived pose as a measured partner pose. Actual robot detection would be a separate feature. |
| P1, latent integration bug | `opmodes/auto/structure/AbstractAuto.java:174` waits only until worker drive ownership ends. `core/control/CompetitionWorkers.java:191–193` derives this from `ButineRunner.is_busy()` at line 603, which excludes both `COMPLETE` and `ABORTED`. The action loop at `AbstractAuto.java:87–90` then continues. | A failed collection can let the next authored path restart motion or let a queued shot run from the failure location. Expose the collection outcome and require successful depot arrival before dependent actions. This becomes active when a competition auto uses `queue_collection()`. |
| P2 | `limelight/ButineWorker.java:87–101` checks request ownership, pollen-map version, our start and time budget. The runner uses map version zero and separately checks whether pickup targets remain. | A future partner schedule/observation change needs its own reservation revision, match-clock origin and freshness validation. Matching the current field object is not enough. |
| P2 | `limelight/Butine.java:441–475` caches one routed cost per endpoint pair, without departure time. | An obstacle at the partner's current predicted position is insufficient. A timed planner must evaluate travel, turns, waits and dwell at the times they occur. |
| P2 | `limelight/ButineRunner.java:565–580` excludes failed pickup IDs/locations and permits only one failure replan; lines 589–600 also bound dispatch recovery. | Do not treat normal temporary traffic as a failed pickup. Add a distinct traffic wait/resume path that preserves pollen eligibility and capacity. |
| P2 | `opmodes/auto/structure/AbstractAuto.java:292–293` calls `follower.update()` before `workers.update()`. | A new traffic check added only at the end of worker servicing would run after that tick's follower output. Integrate the guard before drive commands while keeping localization current. |
| P2 | `opmodes/auto/structure/AbstractAuto.java:269–275` measures action time continuously, including any future wait. | Traffic waiting needs explicit action-state handling. Preserve the global autonomous deadline; distinguish movement timeout from intentional waiting. |

These are gaps relative to alliance-aware behavior. The existing static router does already protect its routed Butine legs and routed recovery against the configured field boxes; it should be retained.

### Blocking can happen while we are stationary

Avoiding intersecting moving paths is insufficient. `ButineRunner` can stop for pickup dwell at lines 505–510 and finish at a depot at lines 531–533; `PedroButineDrive.java:62` enables holding that endpoint. `AbstractAuto.java:244–252` prepares and shoots from the current location. Planning, waiting, pickup, shooting and the final park all occupy space.

For example, stopping at the entrance to a shared underpass can avoid driving into the partner while still blocking their entire route. A valid waiting location must lie outside their future swept corridor for the entire waiting interval, including our stopping motion.

### Additional autonomous-area policy

Butine's current candidate selection uses the full field model, and the competition facade supplies no alliance-area filter. Add an explicit autonomous operating-area policy shared by candidate selection, routing and recovery. The policy must validate intermediate path segments and our body envelope as well as pickup centers.

The official TU05 [game rules](https://ftc-resources.firstinspires.org/ftc/game/manual-11), G402, prohibit disrupting the opposing alliance's autonomous and assign priority to each alliance's side. Crossing the boundary is not described as an automatic foul in every circumstance; keeping dynamic collection within our agreed side is the recommended conservative policy. Derive the coordinates from the official field grid rather than guessing which axis corresponds to the columns.

## 3. What to obtain from the partner before a match

Use a named, locally stored partner-auto profile selected during INIT. A generic description such as "we collect the far pollen" is not enough to derive safe space.

| Input | Why it matters |
|---|---|
| Team/profile identity and version | Confirms both teams selected the same route. |
| Actual starting pose and alliance | Places the route in the same field coordinate frame as our localization. |
| Robot dimensions and localization reference | A robot is an extended body, not a waypoint. Include deployed intake/mechanisms. |
| Every line/curve and possible branch | Reservations must cover the movement between waypoints and all permitted alternatives. |
| Turning, pickup, scoring, waiting and parking areas | A stationary robot or an extending mechanism still occupies space. |
| Placement, tracking and route uncertainty | Expands the reserved corridor for expected deviations. |
| Optional earliest/latest arrival and departure windows | Enables a later timed version; a nominal timestamp alone is insufficient. |
| Agreed lane/underpass priority and fallback | Prevents both robots from waiting in each other's required path. |

Convert authored coordinates to the actual field frame once, following the existing `VisionPose` convention of field inches and counterclockwise radians. Record the source convention and transformation. Do not mirror an already transformed route again for BLUE.

Configure and freeze the profile before starting the planning worker. No communication thread between robots is needed for this first stage.

The official TU05 [robot construction rules](https://ftc-resources.firstinspires.org/ftc/game/manual-12), R704, restrict wireless communication to the official control system. A custom robot-to-robot Wi-Fi/Bluetooth position stream is therefore not the proposed solution. G401 in the [game rules](https://ftc-resources.firstinspires.org/ftc/game/manual-11) also limits interaction after the countdown begins: select the profile before then. Onboard sensing can supply observations without a partner radio link.

## 4. Stage one: reserve a complete corridor

### Geometry and clearance

For an orientation-independent model of a rectangular partner centered on its localization reference, derive:

```text
ourBodyRadius = hypot(ourLength / 2, ourWidth / 2)
partnerBodyRadius = hypot(partnerLength / 2, partnerWidth / 2)
minimumCenterSeparation = ourBodyRadius + partnerBodyRadius
    + desiredBodyGap + ourPositionError + partnerPositionError
```

For two 18 × 18-inch robots, each body radius is about 12.728 inches. With a single 1-inch body gap and no uncertainty reserves, the conservative center separation is about **26.456 inches**. This numerical example is geometry, not a measured reliable operating gap. Do not use an 18-inch center separation while allowing arbitrary headings.

The existing `ButineField` expands every supplied physical box by **our** body radius, requested gap and configured reserves at lines 100–103. Consequently, a partner physical corridor must include the partner's body and its uncertainty, but must not already include our body radius. Otherwise the constructor counts our robot twice.

For straight partner-center segments, a simple conservative implementation can use the axis-aligned bounding rectangle of the segment, expanded by the partner's radius and uncertainty. The existing constructor then adds our envelope. This sacrifices diagonal space, but reuses the established segment checker and recovery router.

Illustrative geometry helper, not production code:

```java
static ButineField.Box swept_partner_box(VisionPose from, VisionPose to,
                                         double partnerRadiusIn,
                                         double partnerUncertaintyIn) {
    // Validate finite poses and nonnegative finite dimensions before this call.
    double pad = partnerRadiusIn + partnerUncertaintyIn;
    return new ButineField.Box(
            Math.min(from.x, to.x) - pad,
            Math.max(from.x, to.x) + pad,
            Math.min(from.y, to.y) - pad,
            Math.max(from.y, to.y) + pad);
}
```

For curves, reserve a proven bound on the complete curve. A Bézier control-point hull is a conservative option; subdivision can tighten it. Sampling without a bound on the space between samples is insufficient. Add stationary turn/dwell/score/park envelopes and include every possible branch. Use the maximum mechanism envelope relevant to each portion, or one conservative maximum envelope for the initial profile.

### Compose with the existing field

Create a new immutable `ButineField` from the existing physical field boxes plus the partner's physical corridor boxes, retaining our dimensions and all configured reserves. Keep the base field immutable. Pass this combined snapshot in `Butine.Params.field` before the worker request. That makes existing candidate filtering, detour routing, deadline costs, return routing and the Pedro adapter use the same reservations.

Important constraints:

- The default map uses six of `MAX_BOXES = 32`. A detailed route can exceed the remaining 26 boxes. Merge boxes conservatively, bound profile complexity or extend the shape representation. Never truncate a reservation to fit the limit.
- Reject a profile if its reservations overlap our start, depot or planned waiting area. The existing planner already rejects blocked start/depot points; expose this as an understandable pre-match strategy conflict.
- A reservation can close the only connection between two free areas. Validate the entire start–collection–depot route, not just the pickup locations.
- Reserve the partner's final location through the rest of AUTO. Do not remove it when their movement program nominally ends.
- Show the actual combined exclusion map, skipped pickups and rejection reason in the desktop visualizer and INIT diagnostics.
- A complete corridor reservation cannot be relaxed merely because the nominal schedule says the partner has passed.

### The central underpass requires priority

The existing geometry record gives a nominal physical gap of 36.2 inches between low supports. Two aligned 18-inch robots already consume 36 inches before meaningful clearance or localization error. The current all-heading envelope is more conservative still. Treat the underpass as a resource used by one robot at a time; do not plan passing or waiting inside it.

For the first stage, give one auto that corridor and route the other elsewhere. If our complete-corridor reservations then eliminate a collection route, choose different collection lanes rather than forcing a narrow passage.

## 5. Protect all autonomous movements

A Butine-only change would leave the currently authored competition paths outside this protection. Add one shared traffic policy at the autonomous drive boundary.

For stage one, validate every authored path's complete swept footprint against partner reservations before execution. These paths include deliberate approaches to physical game elements, so preserve their existing mechanism-specific allowances; do not blindly impose the free-floor collection model on every authored approach. Partner reservations must still apply to them.

Check stationary occupancy for the planned duration of INIT/start staging, solving, pickup, shooter preparation, shooting, waiting and final parking. Preview our selected auto and the selected partner profile together. An incompatible pair should be identified before START.

For later runtime traffic handling, introduce explicit action outcomes such as `COMPLETE`, `SKIPPED_TRAFFIC` and `ABORTED`. A stopped collection action must not be interpreted as successful arrival at its depot. Following actions need a valid live-origin route and an explicit strategy decision before resuming motion or shooting.

## 6. Stage two: timed reservations and observations

Keep static field geometry and dynamic traffic distinct:

```text
ButineField              fixed field structures and our geometric envelope
AllianceTrafficPlan      immutable partner profile, corridor, uncertainty and revision
TrafficSnapshot          current prediction/observation, timestamp and validity
DriveTrafficSupervisor   movement permission, yielding, rerouting and failure outcome
```

An additional worker thread is not needed. Reuse the planning worker for bounded route calculations and keep the final drive decision on the existing control thread.

### Time and uncertainty

Use one monotonic clock origin captured at our autonomous START. Do not restart the partner schedule when a later collection run starts. Partner start mismatch belongs in the timing uncertainty. Convert every reservation into an interval with earliest possible entry and latest possible exit, including turns, scoring dwell and mechanism movement.

At time `t`, the possible partner occupancy is the union of all positions allowed by the profile and its uncertainty, not one interpolated point. A stalled partner must remain possible unless observed progress or a justified bounded-progress assumption excludes it. With no observation or defensible delay bound, revert to the whole-route reservation. Do not discard a detection and snap back to the nominal schedule when observations disagree.

Robot observations would need identity/association, capture-time compensation, field-frame position estimation, a body envelope and bounded error. Our present pollen detector supplies none of these. If identity is ambiguous, treat detected robots as generic obstacles. Expand uncertainty as evidence ages; disappearance from the camera is not proof that a corridor is clear. Additional proximity sensing could help, but its hardware and field of view have not been selected.

### Check complete intervals

For every proposed movement, evaluate our occupancy during acceleration, translation, turns, braking and arrival. Check stationary pickup/score/wait occupancy too. Two geometric paths may cross safely at different times; two currently separated centers can collide later on the same segment.

The current pairwise route-cost cache cannot describe this by itself. Keep a static route cache if useful, but add a bounded schedule evaluation for each candidate sequence at its actual departure times. Include all inserted waiting time in collection selection and deadline fitting. A conservative implementation can evaluate a finite set of static detours plus safe waits; a later safe-interval graph can search which vertices and time intervals permit travel. Both approaches must reject unresolved conflicts rather than assume an unchecked detour exists.

Store the exact timed legs or equivalent deterministic schedule in the result. The runner must execute the movement and timing that were checked. Rebuilding only the same geometry after a delay can still produce a collision.

### Publication and runtime checks

Each request/result needs the static field identity, partner profile revision, traffic snapshot revision, clock origin, prediction/observation timestamps and relevant validity interval. Recheck the whole scheduled movement when accepting a result after queue/solve latency. A same-version snapshot still ages, so a revision comparison alone is insufficient. Avoid cancelling every solve solely because time advanced; re-evaluate relevant conflicts and the accepted uncertainty bounds.

Immediately before motor output and on every subsequent control tick:

1. Obtain current localization and velocity and the latest traffic evidence.
2. Evaluate the reachable motion through control latency and stopping, plus a safety reserve.
3. Permit the follower only when that motion remains clear.
4. On conflict, stop early enough, preserve the action state and yield or request a bounded reroute.

The existing `follower.update()` also services localization, so implement this at an appropriate localization/output boundary or Pedro hook after checking the actual API. Simply moving `workers.update()` earlier could use an old pose; simply appending the guard later would allow that tick's motor output first.

For a stationary obstacle, a basic longitudinal stopping estimate is:

```text
stoppingDistance = speed * worstCaseLatency
    + speed * speed / (2 * minimumGuaranteedDeceleration)
    + extraReserve
```

For a moving partner, include their possible motion during that interval, and check the full two-body occupancy rather than treating this distance formula as a collision proof. Butine's current default speeds and accelerations are seed estimates, not guaranteed braking parameters. Desktop verification can prove behavior under stated bounds; it cannot establish those physical bounds before the robot exists.

### Yield without becoming an obstruction

Add an explicit `YIELDING` state, separate from pickup failure:

- Yield in an agreed bay outside the partner's future corridor, reached by a checked route. If no such movement is safe, stop with a clear failure outcome; do not invent an escape maneuver.
- Include our stationary footprint and time in the traffic model. An emergency stop is necessary when motion is unsafe, but it does not guarantee a partner will avoid the stopped robot.
- Preserve pollen targets, collected count, remaining capacity and the reason for waiting. Do not call `fail_leg()` merely because a partner has priority.
- Resume only after a sufficient clear interval and recheck the route/deadline. Use hysteresis so uncertain observations do not repeatedly start and stop the robot.
- Separate traffic wait/replan limits from pickup-failure limits. Bound waiting and replanning, keep STOP/cancellation responsive, and never extend the global autonomous deadline.
- Give the selected partner auto explicit priority at choke points. Two robots independently deciding to wait or reroute can otherwise deadlock or oscillate.

## 7. Implementation sequence

1. Define and validate a partner profile containing coordinates, physical envelope, all branches, stationary areas and conservative error bounds. Add a named INIT selection and visible profile/version confirmation.
2. Build whole-AUTO corridor boxes and compose them with the static Butine field without double inflation. Retain all reservations within bounded graph complexity.
3. Add combined-auto previews and start/depot/reachability checks. Agree on separate lanes and underpass priority.
4. Apply partner reservations to authored competition paths and stationary scoring/parking. Propagate explicit action outcomes so a traffic failure cannot look like a successful return.
5. Add independent desktop traffic checks and extend integration fixtures. Keep the existing field-obstacle regression suite.
6. If shared timed lanes are still needed, introduce traffic snapshots, interval-aware scheduling, publication freshness checks and explicit yielding.
7. Integrate continuous output permission using current localization/velocity and validated stopping bounds. Add onboard robot observations only with an explicit detection/error model.

Steps 1–5 form the recommended first implementation. Steps 6–7 are an additional feature, not something achieved by moving one obstacle box along a nominal timeline.

## 8. Desktop acceptance checks

No physical robot is available. Use deterministic synthetic trajectories and simulated timing/drive output.

- A partner parked on a direct route causes a detour or an explicit rejection.
- A long segment is reserved continuously, including a midpoint far from both waypoints.
- Curve bounds cover all intermediate positions; turns and mechanism extensions remain inside the declared envelope.
- Our radius, partner radius and requested gap are counted correctly; uncertainty increases separation.
- Profile mirroring is applied once for each alliance; the same real-frame corridor reaches planner, runner and adapter.
- More than 26 additional boxes cannot cause route truncation. Conservative merging retains the original forbidden set.
- An overlapping start/depot, blocked return or disconnected region is rejected with a clear reason.
- Authored curves, parking, empty plans and recovery are subject to the same partner reservations.
- A collection abort cannot start a queued shot or path under a false successful-depot assumption.
- The autonomous area policy rejects an intermediate segment crossing outside the allowed region even when both pickups are allowed.
- Two robots crossing at overlapping times conflict; the same crossing at proven separated intervals is permitted.
- A late start, long scoring dwell, branch change or partner stall does not release occupied space based only on nominal elapsed time.
- A partner observation or profile revision during planning invalidates or revalidates the affected schedule before dispatch.
- A mid-leg conflict detected with sufficient look-ahead causes a stop while the modeled stopping trajectories remain disjoint, under the configured latency/deceleration and partner-motion bounds. A closer sudden intrusion separately triggers an emergency stop and an explicit unavoidable-conflict outcome; no collision-prevention guarantee is claimed for that case.
- A stale/lost observation expands uncertainty or restores static reservations; it does not mean clear space.
- Waiting inside a future partner corridor is rejected; waiting in a safe bay is permitted for its checked interval.
- Repeated yield/resume does not blacklist pollen, consume pickup capacity, replay intake actions or exhaust the single pickup-failure replan.
- Waiting consumes match budget while preserving meaningful movement-timeout behavior; cancellation and STOP always stop output.
- A stopped/aborted collection cannot be immediately overridden by an unchecked authored path.

### Verification performed during this review

The existing desktop check programs passed: `ButineObstacleChecks` (86 checks), `ButineRegression` (1,828 assertions), `RunnerChecks` (26), `RunnerAuditChecks` (35), `ButineWorkerChecks` (31), and `AsyncRunnerChecks` (17). These establish existing static-planning/runner contracts under their fixtures. They do not establish moving-robot avoidance, physical braking, measured Control Hub timing or actual robot tracking accuracy.

## 9. Changes requested by the user

- Confirmed concern: avoid hitting or obstructing our alliance partner during autonomous.
- Proposed input: partner shares their autonomous route before the match.
- Recommended first version: complete partner corridor reservation with separate lanes and agreed priority.
- Partner routes/branch descriptions to support: …
- Partner dimensions and mechanism envelopes: …
- Our preferred scoring depot, waiting bays and lane allocation: …
- Shared spaces that require timed reuse: …
- Future onboard robot/proximity observations, if desired: …
- Other requested changes: …

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
