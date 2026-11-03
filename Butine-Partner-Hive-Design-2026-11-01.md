# Butine: alliance cooperation and changing Hive targets

1 November 2026, revised 2 November after the owner's selection of the Python approach. The offline importer is implemented under [tools/partner-auto](tools/partner-auto/README.md); runtime cooperation remains a design proposal. Butine and Limelight remain disconnected from competition operation. The robot does not exist yet; this work does not add a hardware-calibration task or change neural pollen acceptance, mapping policy or ownership of pose calculations.

## Recommended direction

Keep the reliable authored opening. Use Butine for scattered pollen and remaining opportunities in space the alliance partner has agreed to leave available. The next valuable improvement is a complete feasible collection-and-score opportunity: reachable pollen, clear partner traffic, a usable shooting approach, an available shooting turn, and enough time to park.

The selected implementation is a local Python paste/import application that reads Pedro .pp projects and Java source and produces partner corridors, shooting occupancy and timing ranges. Its bundled JDK bridge parses Java syntax without running imported robot code; the Python reader interprets supported inherited source/action patterns. Missing dependencies and unsupported behavior remain explicit. A common profile enables later partner-aware planning without requiring a partner to rewrite their auto.

The partner's route file is useful prior information. It does not establish their actual position. Coordinate shared passages and shooting positions. An unobservable in-flight tip race is an accepted limitation, not an additional prediction project.

## What the current source actually supplies

| Existing part | Consequence for the design |
|---|---|
| One driven run plus one advisory run, with the SDK elapsed-AUTO greedy cutoff | Retain this. More lookahead depth is not the current priority. |
| Swept intake, incidental-contact capacity, static footprint routing, full-sensor return and typed outcomes | Retain these rather than building a new collector. |
| One fixed depot passed to Butine.solve_sweep, including the advisory second run | A changing Cell can invalidate the assumed scoring destination. Future return choices must account for both scoring approaches. |
| ButineIntake.Route contains segment duration and endpoint-speed information | There is a starting point for checking occupancy over time. |
| ButineField contains static obstacles; execution checks static corridors | No partner occupancy prediction or observation currently exists. |
| Candidate pruning keeps one preferred route per pickup-ID set | A slower corridor or delayed departure can be lost before a partner check sees it. |
| The motion model starts and ends at zero speed | A fresh plan cannot simply be spliced into moving execution using its current timing assumptions. |
| Shooter.nearest_hive chooses top/bottom by robotY > 72 | These are field-side names, not an observed upward-Cell decision. |
| HivePoseEstimator reconstructs camera/world orientation internally | Reuse this calculation to derive Cell state; do not move the pose calculation elsewhere. |

Relevant source: Butine.java solve_sweep; ButineIntake.java offer_candidate and motion; Shooter.java nearest_hive; HivePoseEstimator.java estimate.

## Selecting the correct Cell

The official manual describes one two-Cell pivoting Hive per alliance. The permitted target is its upward-facing Cell. During a tip, comparing two center heights alone is insufficient. See [TU07, sections 9.6, 10.5.1 and G417](https://ftc-resources.firstinspires.org/ftc/game/cm-html/BIOBUZZ%20Competition%20Manual%20-%20TU07.htm).

Extend the existing calculator's output with the identified alliance/Cell, opening geometry or upward-facing state, and observation timestamp. Its internal world-camera rotation and measured camera-tag rotation already give the world-tag rotation. Known rigid geometry can then describe both Cells from one usable tag observation.

Use explicit identities such as audience Cell and far Cell, plus a separate state: UNKNOWN, AUDIENCE_UP, TIPPING, FAR_UP. This avoids confusing field north/south, tag identity and current physical orientation. Tag ID identifies the attached Cell; it does not by itself prove that Cell is upward.

HivePoseEstimator.Result.z is estimated robot-reference height, and axisErrorDeg checks hinge-axis consistency. Neither is a Hive tilt measurement. The useful rotation exists inside the calculator but the present public result does not expose the needed scoring state. The estimator is also currently isolated from the production camera snapshot.

Stability is a feeding condition, not a prerequisite for travelling to the next shooting position. When a tip from Cell A toward Cell B is recognized, immediately plan toward B's future shooting position. Use the last stable side and observed tip direction to select the expected endpoint. Wait at that position until the Hive settles and our shooting turn begins. If the partner occupies the shared shooting area, wait at the agreed approach holding point rather than entering their space. If the observed motion reverses, revise the destination using the same checked routing. This uses the existing posecalculator; it introduces no pollen-confidence filter.

Treat the destination selected when leaving to collect as provisional. Recheck before committing to the final scoring approach and before feeding. If it changed, route to the other valid approach, wait in an agreed location, or carry the load to parking if the remaining allowance cannot support scoring. Never route across the partner's reserved space just because the other Cell became the target.

The advisory second run must not assume the same scoring destination remains usable after the first shot. Initially allow for a possible relocation or conservative return allowance; eventually evaluate the alternative destination explicitly. Do not predict an exact tip from our geometric contact count: partner shots and retained inventory make that count incomplete evidence.

## Shared shooting position and turns

The owner's accepted scope is to coordinate shooting turns and avoid entering the same launch position together. Do not add airborne-ball tracking, Hive-load estimation or a projectile prediction system to solve the unobservable tip race.

Reserve the shared shooting area for the partner from approach through aiming, feeding and departure. A robot can occupy that area while stationary or waiting for readiness. Our robot can keep collecting or move to its own checked holding point during that interval. When a tip starts, travel toward the expected next target and wait; begin feeding only when the target is stable, the area is available and it is our turn.

Keep actual target changes as an explicit feeding interruption. Do not globally replace Shooter's existing active-shot latch with a continuously checked generic RPM/encoder readiness gate. Short-burst optimization is removed from the immediate plan; it is not required to address the user's two cooperation concerns.

## Alternatives to an exact-timestamp Python converter

| Approach | Benefit | Limitation and proposed role |
|---|---|---|
| Source-aware paste/import application | Follows the actual Java auto, including abstract bases and helpers; fits the requested copy-paste workflow | Preferred input method. Needs source dependencies and known action semantics. |
| Geometry-first corridor reservations | Uses the union of the partner's swept route and named shared shooting areas; avoids dependence on exact travel timing | Preferred conservative baseline. Add timed sharing only where known; exclusive corridors can reduce available collection space. |
| Recorded pose/time trace | Independent of the partner's action framework; captures actual travel and waits | Supplement when available. Several runs or bounds are needed; one trace is not a guaranteed future schedule. |
| Controlled desktop builder capture | Executes real inheritance and pose arithmetic in supported pure builders and records paths | Secondary extraction backend. Does not infer sensor outcomes and must not execute arbitrary runOpMode code against hardware. |
| Graphical corridor/stops editor | Works even when a partner has unfamiliar code or no shareable source | Universal fallback in the same interface, rather than rejecting that partner. |
| Runtime robot observation alone | Does not require importing the partner's code | Future supplement. Current software has no general partner tracker, and observation alone does not communicate future shooting intent. |

These can share one partner profile. The recommended combination is source-aware input, corridor reservations by default, and timing ranges for shared passages and the shooting area. This changes the assumption that every part of a partner auto must become an exact timestamped moving obstacle.

The tool remains a pre-match desktop application exporting a local profile. The current [manual R704](https://ftc-resources.firstinspires.org/ftc/game/cm-html/BIOBUZZ%20Competition%20Manual%20-%20TU07.htm) restricts robot wireless communication and requires programming laptops to disconnect during matches.

## Preferred copy-paste workflow

1. Paste Java source, paste a repository/folder link, or drop the partner's TeamCode folder/ZIP. Accept several pasted files in the same import session.
2. Detect concrete autonomous entry classes and select the intended auto/alliance/INIT variant.
3. Resolve the dependency closure: extends chains, overridden methods, super calls, interfaces/default methods, imported constants, pose classes, helpers and relevant library configuration. Support constructors and static/instance initialization where they affect route construction.
4. Follow action execution, not just path declaration order. Expand queue wrappers, helpers and builder calls into movement, turn, wait, shoot, sequential/parallel actions, branches and terminal holding occupancy.
5. Show the extracted route and shooting/holding regions with source references. Distinguish code-derived values, motion estimates and unresolved timing. Never drop an unknown action or give it zero duration.
6. Resolve only the remaining missing inputs, then save the partner/team/variant profile for reuse. The ordinary user flow is import, choose auto, review, save.

A single self-contained auto can be one paste. When the selected class inherits from a source file that was not supplied, automatically look within the imported project/repository. If that still fails, identify the precise missing class or method. No reader can recover the contents of an absent BaseAuto from its name; folder/repository import keeps this close to one action without pretending otherwise.

For unfamiliar custom state machines, the same interface can retain all feasible movement branches or fall back to a conservative corridor. It should not silently choose a branch based on a guessed sensor value. Every partner can produce a usable profile through automatic extraction plus a visible fallback; exact automatic timing for every arbitrary program is not a promised capability.

## Reader design and first acceptance example

The implemented reader uses the JDK's parse-only syntax trees and a bounded Python source interpreter, with adapters for supported FTC frameworks. The partner's program is not compiled or executed. [JavaParser and JavaSymbolSolver](https://github.com/javaparser/javaparser) and [Spoon](https://spoon.gforge.inria.fr/) were considered as alternative parsing foundations; they are not dependencies of this tool. The importer must understand action semantics in addition to source structure, including calls such as queue_shoot_path or ParallelAction.

FTC 19859's existing [AutoTool Java reader](https://github.com/N0v4ont0p/BIOBUZZ-AutoTool/blob/main/autotool/src/shared/java/reader.ts) is a concrete alternative to starting a Python parser from scratch: it reads multiple source files and follows inheritance, fields and helper calls. Its inspected implementation can read an unknown if condition as true; our importer must preserve alternatives or mark incomplete sections instead. Reuse of its UI/reader is an implementation option, with this stricter behavior required.

Our own BlueFarMidPark is the first required acceptance case. Its paths are inherited through FarMidPark, AbstractAuto and General; pose constants live in OptimalPoses. The reader must resolve the BLUE alliance override, expand inherited queue_shoot_path into drive then shoot, evaluate hotfix/Pose.plus and mirror once. Reading only the leaf class or grepping follower.follow would miss the routine.

Timing also lives in superclass methods. queue_shot(1.25) first waits for shooter readiness for up to three seconds and then feeds for 1.25 seconds; a readiness timeout aborts. Capture that uncertainty/branch rather than assuming a 1.25-second stop. A dry run with an always-ready fake is useful for extracting a scenario, but cannot establish the full timing range.

Before calling the reader generally usable, import all 18 FROG RED/BLUE exports and verify inherited action order, geometry, mirroring and shooting occupancy. Then cover Pedro legacy builders, Road Runner actions and common command frameworks. Unknown custom behavior remains explicit rather than being advertised as complete support.

Python now owns the interface, CLI, .pp reader, Java-source interpretation and occupancy estimation. Its small JDK bridge supplies syntax trees for Java input, following the existing desktop-bridge pattern in tools/butine-visualizer. There is no Python process or importer integration in the robot runtime. The graphical fallback and live partner-aware execution remain future work.

## Partner profile and timing

Required partner profile:

- Coordinate frame, units, alliance mirroring and start position.
- Body and mechanism envelope, including the configuration used during each phase.
- Path order and geometry, speed/acceleration bounds, start delay, waits and shot-duration ranges.
- Conditional branches, plausible early/late execution and a stopped-partner scenario.
- Final holding/parking region, not just the last path endpoint.
- Preferably repeated recorded pose-versus-official-AUTO-time traces when a partner can provide them.

Pedro describes its follower as time-independent. Geometric path parameter t is not elapsed time. Our own exporter also documents that visualizer playback duration can differ from the production estimate. See [Pedro's explanation](https://pedropathing.com/docs/pathing) and tools/butine-visualizer/README.md.

Output a nominal animation and conservative occupied regions over time, not one exact ghost pose. For a time window, include every partner body pose allowed by the timing bounds, plus spatial uncertainty. Compare against our complete swept body and response/stopping envelope. Include heading, return motion, waiting and parking. Do not accidentally inflate our body twice when combining this with Butine's existing footprint-expanded geometry.

For example, a partner expected to cross at eight seconds could plausibly occupy the crossing from seven to ten seconds. Our eight-second arrival conflicts even if nominal center-point samples miss each other. If they stall there, the region remains occupied; ten seconds passing does not prove it cleared. Early/late numbers in an example are illustrative, not prescribed robot constants.

Check swept motion between samples. Sampling both robots every fixed interval is useful for review but can miss a collision between samples unless the swept geometry or a justified relative-speed margin covers it.

## Runtime cooperation

Use three increasing levels of cooperation:

| Level | Practical behavior | Limitation |
|---|---|---|
| Spatial agreement | Exclusive collection regions, crossing priority, separate waiting bays and parking allocations | Less freedom, but workable without live partner localization. |
| Imported reservations | Check candidate runs against conservative timed occupancy; allow a bounded wait at a verified holding point | Predictions can drift or branch. No elapsed timestamp proves absence. |
| Observed execution | Observe relevant traffic, update the near-future occupancy and stop/replan when the current route becomes invalid | Needs suitable coverage and enough range/time to respond; the current pollen model and intake sensors do not establish robot tracking. |

For the first version, filter existing candidate routes and try safe departure delays or another candidate before implementing a full time-dependent router. A wait is valid only if its entire interval and the stopped body are outside the partner's traffic. Braking in the partner's future lane is not a safe waiting strategy.

Check the near-future route during execution, including the space needed to react and stop. Replan on meaningful changes such as a conflict, observed delay, route departure or new scoring target. Do not stop and solve after every detection. Preserve the current clear motion prefix when appropriate; otherwise stop at a checked holding location before installing a plan that assumes rest. A later rolling replan would need explicit current-velocity support.

Online replanning without new information does not solve inaccurate partner prediction: it can keep producing plans against the same wrong ghost. If live observation is unavailable, keep Butine inside agreed disjoint regions and handle shared crossings conservatively.

Future collection-failure parking must obey these same traffic constraints. The existing static-field park check does not account for another robot; parking cannot become an automatic escape through a blocked partner corridor.

The SIPP research formalizes the useful concept of safe time intervals and waiting. Borrowing this idea does not require adopting a full new planner initially. [Phillips and Likhachev, ICRA 2011](https://publications.ri.cmu.edu/sipp-safe-interval-path-planning-for-dynamic-environments).

## Camera phase constraint

Current pollen detection uses pipeline 0; AprilTags use pipeline 1. CompetitionWorkers.ready requires a fresh detector frame and currently aborts active collection when that disappears. Robot tracking would introduce another perception requirement. Do not assume one camera supplies all three simultaneously.

A minimal first flow can finish collection/return at an agreed observation staging point, release collection ownership, select the tag pipeline, identify the usable Cell, then take a checked final approach and shoot. Restore fresh detector readiness before the next collection request. This may add travel but avoids silently weakening existing freshness checks.

A later phase-aware return could observe the Hive while approaching, but would require an explicit transition and revised readiness policy for return versus collection. It should not be implemented as arbitrary pipeline switching during a run.

## Other collection improvements, after cooperation

1. **Feasible collection-and-score value.** Reject runs without a usable return/scoring window. Prefer completed useful cycles over collecting more pieces that cannot be delivered before the deadline. Keep hard motion/possession constraints separate from ranking preferences.
2. **More sweep orientations.** Current extra headings are cardinal and mainly considered at the depot. A small number of cluster-derived diagonal headings could improve scattered/diagonal pickups, with alignment time and swept-body/fifth-contact checks preserved.
3. **Existing cluster/sweep selection: retain.** The previous seed-diversity recommendation overstated an unproven limitation. Current planning already compares multi-pollen sweeps, checks contacts against all observations and uses advisory-run lookahead. No change is proposed without a reproducible case showing that candidate pruning loses a useful route. Existing ButineIntakeChecks include contacts outside the seed cap and a lookahead example selecting a slower first run for a better total.
4. **Partner-aware target availability.** Avoid racing to pollen in the partner's reserved sweep. This is a planning decision; it does not require deleting detections or changing neural confidence acceptance.
5. **Replay comparison.** Compare completed cycles, carried loads, waits, conflicts and missed opportunities on the same recorded scenarios. Include a partner stopped at a crossing and a Cell change during our return. More predicted contacts alone is an incomplete success metric.

## Concrete team and software references

- **FTC 19859, BIOBUZZ AutoTool:** implemented partner .pp import, timed body comparisons and WAIT suggestions. Its importer drops some waits with warnings, so restoring dwell information matters. Its reported timing comparison is against a simulator; this is an offline review reference, not proof of runtime partner avoidance. [Repository](https://github.com/N0v4ont0p/BIOBUZZ-AutoTool), [comparison source](https://github.com/N0v4ont0p/BIOBUZZ-AutoTool/blob/main/autotool/src/shared/compare.ts), [import source](https://github.com/N0v4ont0p/BIOBUZZ-AutoTool/blob/main/autotool/src/shared/pp.ts), [author's validation limits](https://georgehu.org/biobuzz-autotool.html).
- **FTC 19725, BluePower:** the inspected commit derives Hive angle/state in MT1Localizer and AutoAimAction selects a goal from that state. RobotPosition retains old state when no observation succeeds; FROG should preserve an explicit stale/unknown condition for feed permission. [Pinned AutoAimAction](https://github.com/BluePowerRobotics/Team19725_BIOBUZZ/blob/85cdca4d0e9cb102640f6e8f896b607046420917/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Auto/AutoActions/AutoAimAction.java), [pinned localizer](https://github.com/BluePowerRobotics/Team19725_BIOBUZZ/blob/85cdca4d0e9cb102640f6e8f896b607046420917/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/Processors/VisionLocalizer/MT1Localizer.java).
- **FTC 23381, The Marksmen:** their BIOBUZZ Vision class derives AUDIENCE_UP/SCORING_UP alongside robot position. This supports reusing localization geometry for scoring state. Their acceptance logic is not a reason to replace FROG's existing freshness handling. [Pinned Vision](https://github.com/St-Marks-Robotics-FTC/Biobuzz/blob/517a517d1188b543571f772689933df82f3b6ed9/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsys/Vision.java).
- **FTC 21229, Quality Control, CENTERSTAGE:** a concrete autonomous waits using side-distance evidence before moving into a partner's scoring area. This is a useful constrained lane-clearance pattern, not a general robot tracker. [Pinned autonomous](https://github.com/21229QualityControl/CenterstageV2/blob/d9702fcd8c936321b78cbcf6ae60e1be49ca1c02/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmode/auto/BlueRightAuto2_3.java).
- **FTC 36117, The Allsparks, ViDAR:** publishes robot-plate observations and a spatial model. Its README explicitly says motion tracking still needs field validation. Useful perception research, not a drop-in validated avoidance system. [Repository and maturity notes](https://github.com/The-Allsparks/ViDAR).
- **FRC 3044, Oxplorer:** reports 2024 competition-auto use of inflated obstacles, visibility graphs and A*. It supports the general routing approach already in Butine, without establishing live partner tracking. This is FRC, not FTC. [Team FAQ](https://github.com/FRCTeam3044/Oxplorer/wiki/FAQ).
- **PathPlanner:** its dynamic-obstacle API accepts spatial bounding boxes, without a future-time parameter. A separate occupancy prediction/scheduling policy is still needed for timed crossings. [Official API](https://pathplanner.dev/api/java/com/pathplanner/lib/pathfinding/Pathfinding.html).

BluePower, Marksmen and Quality Control source findings were rechecked against the locally cached, commit-pinned research checkouts from 27 October. New partner-tool findings were inspected on 1 November. Source inspection establishes what the published implementation contains, not how reliably every class performed during competition.

## Proposed order

1. Define a partner profile that supports route corridors, shared shooting-position occupancy, turns, crossings, waiting bays and park allocation. Provide a graphical fallback for any partner.
2. Build or adapt the source-aware paste/import reader. First prove full inherited extraction on all 18 FROG exports; preserve unknown branches and timing instead of fabricating timestamps.
3. Reuse the existing calculator for Cell state. During tipping, route toward the predicted next shooting position and wait there, or before the occupied shared area. Do not add in-flight tip prediction.
4. Connect partner corridors and shared-area reservations to candidate selection and safe waiting. Add timed sharing only where the profile supports it, retaining a few corridor/timing alternatives.
5. Add observed short-horizon execution checks when suitable robot observations exist. Until then use spatial separation and agreed priority.
6. Consider further search changes only after a concrete replay demonstrates a benefit. Seed diversification is removed from the immediate plan.

All proposed runtime work remains optional and disabled until separately implemented and deliberately connected.
