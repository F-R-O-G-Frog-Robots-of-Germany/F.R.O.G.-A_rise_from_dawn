<!-- Seven visualizer routes: LOWER preload, flower/garden collection, hive shots, and pickup/park endings. -->
# Visualizer autonomous routes

These seven modes extend `AbstractAuto`. Their Java filenames use PascalCase; their driver-station names use the same words in UPPER_SNAKE_CASE. Every Java file starts with its complete action sequence. All routes shoot the preload into LOWER first.

| Driver-station mode | Actions after the LOWER preload |
| --- | --- |
| `OPPONENT_FAR_MID_PICKUP_PARK` | OPPONENT_FLOWER → SHOOTUPPERHIVE → FAR_FLOWER → SHOOTUPPERHIVE → MID_FLOWER → PARK |
| `OPPONENT_FAR_MID_GARDEN_PICKUP` | OPPONENT_FLOWER → SHOOTUPPERHIVE → FAR_FLOWER → SHOOTUPPERHIVE → MID_FLOWER → SHOOTLOWERHIVE → GARDEN |
| `OPPONENT_FAR_GARDEN_PARK` | OPPONENT_FLOWER → SHOOTUPPERHIVE → FAR_FLOWER → SHOOTUPPERHIVE → GARDEN → SHOOTLOWERHIVE → PARK |
| `GARDEN_FAR_MID_PARK` | GARDEN → SHOOTUPPERHIVE → FAR_FLOWER → SHOOTUPPERHIVE → MID_FLOWER → SHOOTLOWERHIVE → PARK |
| `FAR_MID_PARK` | FAR_FLOWER → SHOOTUPPERHIVE → MID_FLOWER → SHOOTUPPERHIVE → PARK |
| `FAR_MID_GARDEN_PARK` | FAR_FLOWER → SHOOTUPPERHIVE → MID_FLOWER → SHOOTUPPERHIVE → GARDEN → SHOOTUPPERHIVE → PARK |
| `GARDEN_FAR_PARK` | GARDEN → SHOOTUPPERHIVE → FAR_FLOWER → SHOOTUPPERHIVE → PARK |

Rows follow the seven Java exports in the supplied text file, in order. `closeFlower` was renamed FAR_FLOWER; the fifth export's misleading `opponentDropOff` at `(48, 132)` is also a FAR_FLOWER pickup. The first route carries its MID load into TeleOp. The second ends holding its GARDEN load and has no park path.

Ideal RED coordinates are in the matching nested `OptimalPoses.*Route` classes. Each mode retains explicit per-point `hotfix(dx, dy, ddeg)` corrections. Shared path builders in `AbstractAuto` apply alliance mirroring once. Shooting uses the existing shared hive poses, LOWER `(59.25, 9, 90°)` and UPPER `(59.25, 135, -90°)`, instead of the exports' nearby Y = 8/132 shooting points. Shooting paths interpolate linearly to the hive-facing heading. Pickup and park curves keep their exported control order and heading mode, including the first route's constant-heading park.

The starting hive is assumed half filled, so the preload is its first completion attempt. After switching hives, send at least two loads to the same hive before switching again; extra loads are allowed for redundancy. `FAR_MID_GARDEN_PARK` preserves the sixth export's three consecutive UPPER deliveries, including its original GARDEN return controls and PARK start at UPPER.

Shot duration is provisionally 1.25 seconds, following the existing modes. Flower-lowering delays are editable in each mode; the shared helper raises the flower intake one second after lowering, or earlier when the path ends. Garden paths run the intake without lowering the flower intake. Confirm the stored alliance before START and measure these timings, collection, path clearance, and full route duration on the robot.

Validation on 2026-10-06: all 11 autonomous Java sources compiled with the cached project dependencies and pinned Pedro 3.0.1. Checks using the actual compiled route fields and shared path helpers passed for all 44 imported paths across RED/BLUE and original/tuned geometry: endpoint continuity, control order, mirrored curve samples and headings, hive aim, constant-heading park, and nonzero hotfixes. The normal Gradle task still fails before compilation with `Unable to establish loopback connection`; this fallback does not verify a full Android build or hardware behavior.
