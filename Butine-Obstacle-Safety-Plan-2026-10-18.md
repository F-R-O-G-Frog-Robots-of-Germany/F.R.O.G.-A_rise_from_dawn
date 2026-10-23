# Butine field obstacles and robot clearance

Goal: make Butine select, time and execute collection routes around the four FLOWERS and the central HIVE structure using the robot's approximately 18 × 18-inch footprint plus 1 inch of clearance.

Date: 18 October 2026. Status: implemented and verified with desktop audits. The scope is dynamic Butine collection, including its recovery and return paths.

## Implemented behavior

The user approved implementation and confirmed a maximum robot height of 18 inches. `Butine.Params.field` now defaults to immutable BIOBUZZ geometry for an 18 × 18 × 18-inch robot with 1 inch of body clearance. The center underpass is open: only the low frame supports are blocked. The earlier full-center projection has been replaced with height-aware support bounds, as explained below.

`ButineField` routes through a bounded visibility graph; the solver costs the selected detours, including turns, pickup dwell and final depot heading. `ButineRunner` expands the same model into explicit transit, pickup and depot legs, routes recovery returns, and checks live origins and remaining time. `PedroButineDrive` independently checks the actual line it is about to dispatch. A blocked start/depot or missing safe route cannot result in an unchecked direct home path.

The field and tracking reserves are separate constructor inputs and both affect obstacle expansion and wall inset. They default to zero additional reserve, leaving the requested nominal 1-inch body clearance explicit. The configurable BIOBUZZ factory retains the underpass only when `robotHeight + bodyClearance + fieldReserve <= 19` inches; taller envelopes use the conservative full-center exclusion until another support slice is derived. These are nominal CAD assumptions, not measured robot performance.

Existing mathematical/oracle tests explicitly use `ButineField.unrestricted()`; production defaults remain obstacle-aware. The collection integration fixture uses a depot at X=18 inches instead of its former X=12 to satisfy the new wall envelope.

## 1. Confirmed robot model

The user's original 9 inches meant a distance from the robot center, assuming an 18 × 18-inch robot. The updated requirement is to account for the robot's physical area and add 1 inch outside it. Nine inches alone protects the midpoint of an aligned side, but does not protect a corner while the robot turns.

Use these independent configuration inputs:

```java
robotLengthIn = 18.0;
robotWidthIn = 18.0;
bodyClearanceIn = 1.0;
```

The footprint is centered on the localization reference point. This is an explicit provisional assumption: if the eventual robot extends different distances forward, backward, left or right from that reference, configure those four extents instead. Include protruding mechanisms in their collection position; an 18-inch chassis does not bound an extended intake.

For the first implementation, derive an envelope that remains valid at every heading:

```java
double halfLength = robotLengthIn / 2.0;
double halfWidth = robotWidthIn / 2.0;
double keepoutRadiusIn = Math.hypot(halfLength, halfWidth) + bodyClearanceIn;
// 13.7279220614 inches for 18 × 18 inches with a 1-inch outer clearance.
```

This is the enclosing circle of the actual rectangle, with a 1-inch Euclidean buffer. It is deliberately conservative when the robot is aligned with an obstacle. It is not a new assumed chassis width, and it must be recalculated when dimensions change. It protects corners even when Pedro rotates during a line or settles the depot heading. An axis-aligned 20 × 20-inch box alone does not cover that box after rotation.

A future orientation-aware implementation could recover space using the exact buffered rectangle. It must validate translation and the full intervening turn, including any heading interpolation. Checking only a footprint at the start and end cannot establish clearance during that turn. That extra planner is unnecessary for the initial small, deterministic solution.

For reference, the buffered square's half-extent along either field axis at heading `theta` is `9 * (abs(cos(theta)) + abs(sin(theta))) + 1`: 10 inches at 0 degrees and about 13.73 inches at 45 degrees. Use the heading-independent bound for unrestricted turns.

## 2. Official geometry and its provenance

Research uses FIRST's [Playing Field Resources](https://ftc-resources.firstinspires.org/ftc/field), the [BIOBUZZ manual, ARENA section](https://ftc-resources.firstinspires.org/ftc/game/manual-09), and the [official field STEP model](https://ftc-resources.firstinspires.org/ftc/field/field-cad-step). The field resources also link the [official Onshape assembly](https://cad.onshape.com/documents/a355e772e3d24813de7852ee/w/f106353168f1f92100b81259/e/95d1e1e442b4138cccaf2d73). The current full manual is TU05, updated 15 October 2026.

The manual identifies CAD as the geometric reference and gives a general ±1-inch tolerance. Its central frame is nominally 49.46 inches wide and 38.95 inches deep. That frame rectangle alone does not enclose the tipping HIVE assemblies. Four FLOWERS attach to the perimeter; their ring centers are offset inward from the wall mounts. These are physical obstacles, independently of the painted gameplay zones.

Keep physical obstacle bounds, robot dimensions, and clearance as separate data. Do not infer an obstacle boundary from an autonomous pickup pose or use the FLOWER's 4-inch scoring opening as its outside diameter.

### CAD footprint table

The following are our derived conservative bounds from the official STEP assembly, rounded outward to 0.1 inch. They are not coordinates printed as such in the manual. FLOWER bounds include their mounting hardware. The center uses only fixed FRAME geometry above the tiles and at or below 19 inches: an 18-inch robot plus 1 inch above it. Project that clipped geometry onto the field, rather than projecting the entire structure at every height.

| Obstacle | Centered X range (in) | Centered Y range (in) | Planner X range (in) | Planner Y range (in) |
|---|---:|---:|---:|---:|
| Left low frame support | −24.8 to −18.1 | −19.5 to 19.5 | 47.2 to 53.9 | 52.5 to 91.5 |
| Right low frame support | 18.1 to 24.8 | −19.5 to 19.5 | 90.1 to 96.8 | 52.5 to 91.5 |
| Top FLOWER | −26.8 to −20.0 | 64.4 to 71.8 | 45.2 to 52.0 | 136.4 to 143.8 |
| Right FLOWER | 64.4 to 71.8 | 20.0 to 26.8 | 136.4 to 143.8 | 92.0 to 98.8 |
| Bottom FLOWER | 20.0 to 26.8 | −71.8 to −64.4 | 92.0 to 98.8 | 0.2 to 7.6 |
| Left FLOWER | −71.8 to −64.4 | −26.8 to −20.0 | 0.2 to 7.6 | 45.2 to 52.0 |

The extraction used Open CASCADE 8's `BRepBndLib.AddOptimal` on transformed boundary-representation assemblies, with `useTriangulation=false` and `useShapeTolerance=false`, converted to inches. Curved faces matter: a lightweight list of STEP vertices gave too little FLOWER depth, so the table uses the BREP bounds. The four flower instances reproduce quarter-turn symmetry after outward rounding.

Inspected STEP SHA256: `05b35961c7df847741031f00fda73ddd068537f809e92a11b1cd59a94bcc8331`. The official field page identifies its STEP download as v26-27.2, updated 15 September 2026. Retain this identity with the proposed constants; re-extract the bounds if the official model changes.

### Why the center underpass is allowed

The 43.95-inch measurement is the pivot height, not the underside clearance. The full-height projection in the initial plan was too conservative for this robot. The corrected support boxes come from exact Boolean intersection of the entire fixed FRAME with Z=0 to 19 inches, split into left and right assemblies. They include feet, bolts, floor bars and the low parts of the sloping legs. The physical corridor is 36.2 inches wide. The cross-field mounting bars lie below the tiles and do not obstruct this passage.

With the all-heading robot radius of approximately 13.727922 inches, the allowed center corridor between the supports is X=67.627922 to 76.372078 inches in the planner frame. A centerline at X=72 is clear at every heading, including 45 degrees.

The fixed panels begin at approximately Z=34.072 inches and the top bar at Z=40.950 inches. Moving HIVE clearance must also cover the transition between both stable positions. [Manual Figure 9-10](https://ftc-resources.firstinspires.org/ftc/game/cm-html/BIOBUZZ%20Competition%20Manual%20-%20TU05_files/image030.png) identifies 30-degree tipping stops. Exact CAD endpoints have a minimum HIVE height of Z=30.651541 inches. Splitting the allowed 60-degree transition into two halves and bounding each half from its nearest endpoint gives this conservative continuous lower bound:

```text
hingeHeight = 43.9497 in
endpointUnderside = 30.651541 in
downwardRelevantEndpointYBound = 20.064099 in
undersideLowerBound = hingeHeight
    - (hingeHeight - endpointUnderside) * cos(30 degrees)
    - downwardRelevantEndpointYBound * sin(30 degrees)
    = 22.401107 in
requiredVerticalEnvelope = 18 + 1 = 19 in
```

The 22.401-inch value is a deliberately loose lower bound, not the actual lowest underside height. It establishes nominal overhead clearance throughout the permitted sweep without needing live Hive-angle measurements. The exact measurements, coordinate conversion and derivation are retained in [the geometry record](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/docs/Butine-Center-Underpass-Geometry-2026-10-18.json).

For a taller configured robot, the fallback full-center box remains X=47.2 to 96.8 and Y=38.5 to 105.5 inches. That fallback uses a deliberately wide full-rotation projection; it is not used for the confirmed 18-inch robot.

### Field coordinates

Butine and `VisionPose` use corner-origin field inches and counterclockwise radians. The existing `HiveGeometry` documentation uses a centered world frame, with +X toward the audience's right and +Y away from the audience. Convert the CAD-aligned centered coordinates once:

```text
plannerX = centeredX + 72
plannerY = centeredY + 72
```

In the planner frame the four nominal FLOWER wall anchors are top `(48,144)`, right `(144,96)`, bottom `(96,0)` and left `(0,48)`. These anchors identify placements; the footprint table must contain the actual inward extensions and mounting hardware.

The field model describes the whole field, so it does not change with alliance. A caller transforms an authored depot into the actual field frame once. Camera observations are already in that frame. Do not mirror the obstacle map again for BLUE. A 180-degree transform `(144-x,144-y)` should reproduce its symmetry.

### Clearance and uncertainty

For an obstacle axis-aligned bounding box `[xmin,xmax] × [ymin,ymax]`, use the conservative robot-center exclusion rectangle:

```text
[xmin - R, xmax + R] × [ymin - R, ymax + R]
R = hypot(robotLength/2, robotWidth/2) + bodyClearance
```

This rectangular expansion encloses the true rounded expansion and sacrifices some diagonal space. It makes segment intersection and routing simple and conservative. Reject contact with the expanded boundary, allowing only separately offset routing vertices outside it.

For the agreed 18 × 18-inch footprint plus 1 inch, the nominal proposed center exclusions are below. Displayed values are rounded to 0.01 inch for readability; calculate from the physical boxes and full-precision radius at runtime. Do not copy these display-rounded values as exact collision constants.

| Obstacle | Excluded center X (in) | Excluded center Y (in) |
|---|---:|---:|
| Left low frame support | 33.47 to 67.63 | 38.77 to 105.23 |
| Right low frame support | 76.37 to 110.53 | 38.77 to 105.23 |
| Top FLOWER | 31.47 to 65.73 | 122.67 to 157.53 |
| Right FLOWER | 122.67 to 157.53 | 78.27 to 112.53 |
| Bottom FLOWER | 78.27 to 112.53 | −13.53 to 21.33 |
| Left FLOWER | −13.53 to 21.33 | 31.47 to 65.73 |

Exclusions can extend beyond the field. Keep the full boxes in geometry calculations and separately intersect allowed travel with the field domain; clip only their drawing to the visible field. The nominal all-heading center domain is approximately 13.73 to 130.27 inches on each axis.

The requested 1 inch is a geometric body clearance against the chosen obstacle model. Nominal CAD has its own field tolerance, and tracking/localization have further errors. Do not silently describe this nominal clearance as a guaranteed physical gap. Retain a separately named field uncertainty reserve and tracking reserve, with their effect explicit: `Rtotal = R + fieldReserve + trackingReserve`. Show both the requested nominal model and any enlarged model in diagnostics. A one-inch nominal gap can be consumed by a one-inch obstacle placement error.

Keep the robot envelope inside the field perimeter too. With unrestricted heading, its nominal center domain is `[R,144-R]` on both axes; the active domain is `[Rtotal,144-Rtotal]` when reserves are configured. Apply the same active radius to obstacle expansion, field bounds, execution checks, monitoring, recovery and diagnostics. Existing shooting poses have `y=9` or `y=135`; those do not satisfy this conservative domain. Report such a depot as invalid for dynamic collection and require a deliberately chosen valid depot. Do not silently move the scoring pose or alter the authored autonomous routines.

## 3. Where the current code needs changes

All Java paths below are under `TeamCode/src/main/java/org/firstinspires/ftc/teamcode/` in the main checkout.

| Location | Current behavior | Required change |
|---|---|---|
| `limelight/Butine.java`, `select_candidates()` | Ranks candidates by straight-line reach time before limiting their count. | Remove points inside blocked space and candidates with no safe route before limiting candidates; rank with routed reach cost. |
| `Butine.Context.build_tables()` and `sequence_time()` | Cache one distance/direction per pair and cost direct lines. | Cache deterministic routed polylines and calculate their turns and travel time. |
| `limelight/ButineRunner.java`, `build_run_legs()` | Reconstructs a direct line to every pickup and then home. | Consume the same routed geometry and explicitly distinguish transit, pickup and depot arrival. |
| `ButineRunner.finish_empty_plan()` and `fail_leg()` | Can create an independent direct `home_leg()`. | Route recovery through the same geometry model; stop if no valid return exists. |
| `limelight/PedroButineDrive.java`, `follow()` | Builds the actual line from the live follower pose, rather than `Leg.from`. | Recheck that exact live segment immediately before submitting it to Pedro. |
| `limelight/ButineWorker.java`, `request_plan()` | Snapshots parameters and observations for the worker. | Include an immutable geometry/clearance snapshot and identify it in the result. |

Endpoint filtering alone is insufficient: two legal pickup points can have a connecting line through the central obstacle. A solver-only change is also insufficient because recovery paths and actual live origins bypass its nominal lines.

## 4. Small routing model shared by solver and runner

Introduce one immutable `ButineField` or similarly named model holding physical boxes, robot dimensions, clearance, field domain and a geometry identity. Avoid general-purpose robotics middleware or another thread. Geometry and graph work belong to the existing planner worker.

Start with six conservative obstacle boxes: four FLOWERS and two low frame supports. Create a visibility graph from their outward-offset corners aomnd the pair's endpoints. An edge exists only when its entire line lies in the permitted center dain and outside every exclusion rectangle. Remove buried corners when exclusions overlap. Exposed original rectangle corners suffice for the exterior visibility graph; new axis-aligned edge intersections form inward notches and do not require a polygon-union library. Check every offset vertex and edge against all boxes. Bound the accepted obstacle count and derived graph size; fixed official geometry requires only a small graph.

Use deterministic shortest-distance routing and stable tie breaking. This chooses a repeatable safe polyline, not a claim of globally fastest motion. Calculate the travel time of that chosen polyline with the existing kinematics. Do not substitute total detour length into one `straight_time()` call: a route with intermediate turns has a different cost and outgoing heading.

For each ordered pair retain the route points, segment directions, first and last nonzero direction, and internal time. The cost of entering the first segment depends on the incoming robot heading; later segments depend on the previous segment direction. Preserve heading through coincident points. Charge pickup dwell exactly once at the pickup and depot heading settle exactly once at final arrival. Every capacity split, local-search comparison and deadline trim must use that same routed cost.

Use explicit reachability/status for a missing path. Do not let infinity reach existing `finite_time()` guards or silently turn an unreachable pair into a cheap zero-cost edge. Invalid start/depot or a missing return path must produce an empty/nonexecutable result with a clear reason.

Publish the route actually costed with the plan, or publish sufficient immutable route data to expand it identically. The drive adapter must not invent a fresh detour after the solver has accepted the deadline. Replanning from a changed live origin must update the route cost and check remaining time again.

## 5. Execution semantics and recovery

Add explicit leg kinds, for example:

```java
enum LegKind { TRANSIT, PICKUP, DEPOT }
```

Transit points advance the route without removing a pollen track, incrementing collection counts, consuming capacity or applying pickup dwell. Depot arrival alone finishes the run and settles its final heading. Intermediate segments of a return route are transit; marking every one as `returning=true` would complete the run at the first corner.

The current runner accepts pickup arrival within 5 inches and depot arrival within 6 inches. Those are not suitable corner tolerances. Use a separate transit arrival tolerance and make routing clearance account for it. The tracking reserve must cover arrival error, overshoot and stopping distance during detection/control latency at the selected speed, once those are measured. A numerical epsilon only resolves arithmetic; it cannot substitute for follower accuracy. Revalidate from the actual arrived pose before the next dispatch so early corner acceptance cannot introduce an unchecked corner cut.

Before `Drive.follow()` issues any new movement:

1. Read and validate the live pose.
2. Verify the same geometry snapshot is still active.
3. Check the actual live-origin segment, endpoint and field domain.
4. If that segment is blocked, stop and request a bounded reroute from the live pose, with elapsed time deducted; otherwise abort with a reason.

Use the same route helper for initial plans, normal return, empty replans, missed pickups and timeout recovery. Never fall back to a direct home line when routing fails. A robot reported inside a forbidden region stops; no automatic escape is assumed safe. A transit failure does not mark its location as an unreachable pollen pickup.

During execution also monitor pose validity and entry into forbidden space, stopping if localization reports a violation. Detection after entry does not preserve the clearance that was already crossed. A geometrically clear centerline is a planning property; it does not establish the physical follower's tracking error or stopping envelope. Keep tracking reserves explicit and do not claim hardware validation from desktop checks.

## 6. Worker ownership and configuration

Configure the field model during INIT alongside the existing worker configuration. Freeze the model before worker startup or deeply snapshot it with every request. The worker result identifies the geometry used so a result computed against old dimensions cannot be dispatched after configuration changes.

Reuse the existing single planner worker. Keep graph size, shortest-path searches, candidate count and optimization iterations bounded. Account for graph construction and routing in solver timing; benchmark the complete solve with maximum candidate count. Preserve bounded cleanup and the current rule that startup never starts collection by itself.

Pollen inside a forbidden robot-center region can remain visible in `PollenMap`; skip it for free-floor collection rather than deleting the observation. Deliberate retrieval from a FLOWER is a separately authored mechanism approach and must not acquire a global bypass of the free-floor obstacle rules.

## 7. Desktop acceptance checks

The robot is not built. Verify geometry and planner behavior in standalone checks; leave physical tracking and intake-envelope calibration as future measurements.

- Verify CAD-to-corner conversion, the four FLOWER placements and 180-degree symmetry against source-derived expected bounds.
- Independently verify the 18 × 18-inch corner radius plus 1 inch, including 0-, 45- and 90-degree headings. Changing length/width must change the derived buffer.
- Check crossing, tangency, corner contact, zero-length segments, overlapping boxes, invalid numeric settings, out-of-field endpoints and blocked start/depot.
- Give two safe points whose direct line crosses the center; require a detour and verify every emitted segment independently. Also test an impossible passage and an unreachable return.
- Verify routed time equals the emitted segment costs, including detour turns, one pickup dwell per pickup and one depot heading settle. Detours must affect deadline fitting.
- Fill the map with nearby inaccessible pollen and farther accessible pollen; verify blocked candidates do not fill the candidate cap.
- Exercise displaced live origins, early corner arrival, all fallback home paths, transit failure and invalid localization. No unchecked direct home segment may be dispatched.
- Verify transit legs do not modify collection counts or map tracks. Verify cancellation and geometry snapshot isolation across asynchronous requests.
- Run the existing Butine, runner and worker regressions through `tools/limelight-audit/run.ps1`, followed by `tools/competition-audit/run.ps1` for integration and real-SDK compilation.

### Verification completed on 18 October 2026

- `tools/limelight-audit/run.ps1` passed real-SDK source compilation, all planner/runner/vision regressions, 86 dedicated obstacle checks, and the separate camera and competition-worker fixtures. The obstacle checks include an independent segment-to-box clearance calculation and a real Pedro-adapter rejection of a blocked actual-origin line whose nominal origin was safe.
- `tools/competition-audit/run.ps1` compiled 69 production and 25 test sources against real SDK signatures, passed all 25 standalone check programs, and passed 56 full collection integration assertions using the real queue, workers, runner, Pedro adapter and Foresight with simulated motion/hardware.
- Independent code reviews found no remaining routing/geometry defects. An empty-plan diagnostic and compatibility state handling were corrected during verification.
- Verification is on the desktop. Physical localization/tracking error, stopping distance, actual mechanism envelope and field variation remain inputs for the future robot; these checks do not establish Control Hub performance or a measured one-inch gap.

## 8. Implementation order and completion criteria

1. Record verified official physical envelopes and coordinate conversion with their source/version/hash.
2. Implement the immutable footprint/exclusion model and geometry checks.
3. Add bounded routing and route-aware solver costs.
4. Publish and execute transit/pickup/depot legs, including safe recovery.
5. Add live-origin validation and clear failure reports.
6. Run the targeted checks and integration audits; document remaining calibration inputs.

Completion requires every dynamic collection movement, including recovery, to use and validate the same robot-envelope geometry; solver deadlines must describe the paths actually emitted. A non-fitting or unreachable run must leave the drive stopped with an explanation.

## Changes requested by the user

- Confirmed: replace the original center-based 9 inches with an approximately 18 × 18-inch robot footprint plus 1 inch of outer clearance.
- Confirmed: maximum robot height is 18 inches; allow driving below the center structure while avoiding the low supports.
- Actual footprint/reference-point offsets once known: …
- Mechanism extensions in the collection configuration: …
- Additional field/tracking reserve, if desired: …
- Alternative valid Butine scoring depot: …
- Other changes: …

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
