# Limelight review — what was wrong and what changed

Written after reviewing the whole Limelight arc, from `b59acb5` (2026-08-31,
"gevibeter commit für die limelight") through the uncommitted working tree.
Fifteen defects were found; all are fixed. This file explains each one, and
spends most of its length on the camera geometry, because that is the part where
the intuition is genuinely subtle and where the working tree had swapped one
wrong explanation for another.

---

## Part 1 — The geometry: can a level camera see the floor?

### Short answer

**Yes.** The old comment in `limelight.h` said "a camera whose optical axis is
level never meets the floor". That is wrong. The working tree replaced it with
"safe to turn on even with a level camera". That is also wrong, for different
reasons. Both are now gone.

### Why a level camera does see the floor

A camera does not see along a line. It sees a **cone**. `VERTICAL_FOV_DEG = 48.9°`
means rays leave the lens anywhere from 24.45° above the optical axis to 24.45°
below it.

```
        pitch = 0  (optical axis horizontal)

                      .  +24.45°  ← top of frame, sky, never hits floor
                  .
    [camera] ----------------------  0°  ← the optical axis. Parallel to the
                  '                         floor. Never meets it. TRUE — but
                      '  -24.45°           this is one ray out of a whole cone.
                          '
    ~~~~~~~~~~~~~~~~~~~~~~~X~~~~~~~~~~~~~~~~~ floor
                           ^
                     22.0 in ahead
```

So the *axis* never meets the floor, and the old comment confused the axis with
the camera. The bottom half of the frame is floor. `depression = pitch − ty` is
positive for every negative `ty`, and `height / tan(depression)` returns a real,
finite number. Nothing divides by zero.

### Why the geometry is nevertheless useless at pitch 0

Three separate problems, all of which the "safe to turn on" comment missed.

**1. Half the sensor is dead.** Every ray at or above the horizon (`ty ≥ 0`)
never reaches the floor. `forward_distance_for()` rejects them
(`if (depression <= 1e-3) return nullopt`). At pitch 0 that is exactly the upper
half of the image — 50% of the pixels cannot range anything, ever.

**2. The near limit is wrong by nearly 3×.** The closest floor point you can see
is the steepest downward ray, at the bottom edge of the frame:

```
forward_min = height / tan(VFOV/2 + pitch)
            = 10 / tan(24.45° + 0°)
            = 10 / 0.4547
            = 22.0 in
```

`NEAREST_FORWARD_IN` claims 8 in. The pollen you most want — the ones you are
about to drive over and swallow — sit inside 22 in, where the optics simply do
not point. The config promises a near limit the mounting cannot deliver.

**3. It is ill-conditioned, catastrophically so, near the horizon.** Differentiate
the range formula:

```
forward   = h / tan(d)                 d = depression
d(forward)/dd = −h / sin²(d)
```

That denominator is the whole story. Range error per degree of `ty` error:

| depression | range | error per 1° of `ty` |
|---|---|---|
| 1° | 573 in | **573 in** |
| 5° | 114 in | 23 in |
| 10° | 57 in | 5.8 in |
| 24.45° | 22.0 in | 1.0 in |
| 54.45° | 7.1 in | 0.26 in |

Near the horizon the model amplifies noise by a factor of hundreds. That is why
`forward_distance_for()` also rejects anything past `2 × FAR_FORWARD_IN = 120 in`
— and that cap, at pitch 0, throws away everything above `ty = −4.76°`:

```
h / tan(d) ≤ 120   →   tan(d) ≥ 10/120 = 0.0833   →   d ≥ 4.76°
```

So the surviving band is `ty ∈ (−24.45°, −4.76°)` — **19.7° out of 48.9°, i.e.
40% of the frame**, mapping to 22 .. 120 in. The other 60% returns `nullopt`.

### What to do instead: pitch the camera down

Pitching down slides the whole usable band toward the robot, puts the entire
frame on the floor, and moves the measurement into the well-conditioned part of
the curve. With `CAMERA_HEIGHT_IN = 10` and `VERTICAL_FOV_DEG = 48.9`:

```
pitch = 30° down

  bottom of frame:  ty = −24.45°  →  d = 54.45°  →  10/tan(54.45°) =   7.1 in
  top of frame:     ty = +24.45°  →  d =  5.55°  →  10/tan( 5.55°) = 102.9 in
```

That is `7.1 .. 102.9 in`. Every ray lands on the floor, nothing is rejected
(102.9 in is under the 120 in cap), and the configured `8 .. 60 in` working band
sits strictly inside with margin at both ends. Across that working band the
sensitivity runs 0.26 in/deg at the near edge to 6.5 in/deg at 60 in — against
23 in/deg and worse at pitch 0.

The 48.9° FOV is wider than the 8 .. 60 in band strictly needs, which is a good
problem: there is a range of workable pitches rather than one exact value.
Anywhere from about 27° to 34° puts the whole working band on the floor.

So: mount the camera at a measured height, aim it ~30° down, put the two real
numbers in, and flip `USE_PINHOLE_RANGE`. Until then the placeholder quadratic
runs, which is a guess but at least a monotonic one over the whole frame.

---

## Part 2 — What the crosshair does, and why it matters here

### What it is

The Limelight reports `tx` and `ty` as angles **measured from the crosshair**,
not from the optical centre of the lens. The crosshair is a point you drag around
in the Limelight web UI (or set with its dual-crosshair calibration). By default
it sits at the image centre, i.e. on the optical axis.

Its intended purpose is mechanical: if your shooter is mounted 3 in to the left
of the camera, you move the crosshair left until `tx = 0` means "aligned with the
**shooter**" instead of "aligned with the **lens**". It is an aiming convenience.

### Why it is dangerous for this code

Every formula in `limelight.cpp` assumes the crosshair *is* the optical axis:

- `depression = CAMERA_PITCH_DEG − ty` treats `CAMERA_PITCH_DEG` as the angle of
  the **optical axis** below horizontal, and `ty` as the angle below **that
  axis**.
- `bearing = tx × π/180` treats `tx` as the angle off the **optical axis**.

Move the crosshair down by `b` degrees and every reported `ty` shifts by a
constant: `ty_reported = ty_true − b`. Substitute:

```
depression = pitch − ty_reported
           = pitch − ty_true + b
           = (pitch + b) − ty_true
```

The camera now behaves as though it were pitched by `pitch + b`. **A browser
setting has silently re-calibrated the range curve**, with no error, no warning,
and nothing in git. Same for `tx`: a horizontal crosshair offset slides every
pollen sideways in the field frame. Then `pollen_map` clears a wedge centred on
the true heading while the detections in it were placed on a biased bearing, and
tracks start being created and destroyed on alternate frames.

### The coupling to pitch 0

This is the part worth internalising. The range error caused by a crosshair bias
`b` is:

```
Δforward ≈ (h / sin²(d)) · b
```

which is the same amplification factor from the table above. At pitch 0 the
usable band sits at small depressions, so **1° of crosshair drift costs 23 in at
the far end and 573 in near the horizon**. At pitch 30° the same 1° costs between
0.3 in and 6.4 in.

So pitching the camera down does not merely extend the range — it makes the whole
model robust against the one input that lives outside the repository and can be
changed by anyone with a browser and no commit.

**Rule: leave the crosshair centred.** If you must move it, fold the offset into
`CAMERA_PITCH_DEG` / `CAMERA_YAW_DEG` in `limelight.h` so the code knows about
it. That is what the comment at the top of `limelight::geometry` has always been
asking for.

---

## Part 3 — Every change, and why

### Correctness

**1. `LimelightBridge.java` — the botpose-present guard was inverted.**
The working tree had `solved = x != 0.0 && y != 0.0 && yaw != 0.0`. The original
was `||`. These are not variations on a theme; they are opposites:

- `||` means *reject only when **everything** is zero* — the documented signature
  of "the `botpose` key was absent", which the FTC SDK returns as an all-zero
  pose rather than `null`.
- `&&` means *reject when **anything** is zero*.

Botpose is measured about the **field centre**. So `x == 0` is the entire centre
line of the field, `y == 0` the other one, and `yaw == 0` a robot squared to the
field axis. Under `&&`, a perfectly good AprilTag solve is thrown away across two
full lines of the field — exactly the open middle where you most want a fix.
Restored to `||`, with the reasoning written down so it does not get flipped
again.

**2. `limelight_position_test.cpp` — no drivetrain task, so odometry was frozen.**
`pedro::robot_pose` is only written by `read_buffer()` inside `pedro::update()` /
`pedro::teleOpUpdate()`. The new opmode called `pedro::startTeleOpDrive()` but
registered neither. Consequences: the robot could not be driven; the "Odometry
Pose" readout never changed; and every detection was projected against a capture
pose stuck at the init value, so the calibration numbers the opmode exists to
produce would have been confidently wrong. A `pedro` task at 20 ms is now
registered, with a comment saying why it is not optional.

**3. `LimelightBridge.java` / `limelight.{h,cpp}` — the pipeline was pinned.**
`hardInit()` called `pipelineSwitch(DETECTOR_PIPELINE)` and nothing else could
ever change it. The detector produces no AprilTag solve, so `getPose()` returned
`nullopt` unconditionally — meaning the AprilTag half of the stated goal, and the
`LL AprilTag Pose` half of the new test opmode, were unreachable code.
Added `LimelightBridge.setPipeline(int)` and `limelight::set_pipeline()` /
`limelight::pipeline()`, with named `DETECTOR_PIPELINE` / `APRILTAG_PIPELINE`
constants on both sides. BACK toggles them in the test opmode.

Two details that matter:
- Switching **drops** the current frame, the pose trail and the last result. They
  describe the pipeline being left.
- While a non-detector pipeline is selected, `update_pollen_tracking()` returns
  early and the map **freezes**. Zero detections from a pipeline that is not
  detecting is *"not looking"*, not *"looked and saw nothing"* — feeding those
  empty frames to `pollen_map` would clear the wedge in front of the robot on
  evidence that does not exist. That distinction is the entire premise of
  `pollen_map`, so it has to be honoured here too.

**4. `limelight.h` — the `USE_PINHOLE_RANGE` comment.**
Replaced with the analysis in Part 1: a level camera *does* see the floor, and the
model is *still* unusable at pitch 0, with the numbers spelled out. The old
comment was wrong about the optics; the new one was wrong about the conclusion.

**5. `butine_test.cpp` — the 0.5 Hz experiment.**
`limelight::update()` had been moved from 10 ms to 2000 ms and
`pollen_map::update()` from 50 ms to 2000 ms, "since the map only consumes
analysed frames anyway". Three things were wrong with that:
- It does not do what it says. `limelight::update_full()`, still registered at
  250 ms, runs the **same** detector parse — so the camera was still being pulled
  at 4 Hz and the 250 ms status task had quietly become the frame source.
- `update()` is also what feeds the pose trail, so slowing it coarsens the
  latency compensation from 10 ms granularity to 250 ms. Interpolating a robot
  pose across a 250 ms gap at 70 in/s spreads ~17 in of uncertainty over a
  correction meant to remove 3–6 in. The cure became worse than the disease.
- `pollen_map`'s rule is "a frame is authoritative over the wedge it covers",
  which only holds while wedges arrive faster than the robot turns away from
  them. At 0.5 Hz a robot turning at 90°/s sweeps 180° between frames.
Restored to 10 ms / 250 ms / 50 ms, with the reasoning in the comment.

**6. `limelight.cpp` — the pose trail was shorter than the latency it corrects.**
`POSE_HISTORY` had been halved from 64 to 32, giving ~320 ms of history at the
10 ms rate — while `update_pollen_tracking()` clamps latency to 500 ms twenty
lines below. Any frame between 320 and 500 ms of latency fell off the oldest end,
and `pose_at()` silently substituted the oldest pose it still held: a wrong
answer with no error path. Restored to 64, the clamp is now a named
`MAX_LATENCY_MS`, and a `static_assert` ties the two together so the next person
to shrink one gets a build error instead of a subtle drift.

**7. `pollen_map.cpp` — the wedge was cleared from the wrong pose.**
`limelight.cpp` goes to real trouble to project each detection from
`pose_at(captured_at)`, the latency-compensated capture pose. `pollen_map::update()`
then computed the frustum from `pedro::robot_pose` — the pose *now*. The two
differ by the pipeline latency plus the scheduler gap, so the region the map
claimed to have measured was rotated away from the region it actually measured. A
robot turning between capture and sweep deletes pollen that are really there and
keeps ghosts where it is currently pointing. Added
`limelight::pollen_frame_pose()`, and `pollen_map` now uses it.

**8. `limelight.h` / `docs` — the FOV was restored to the published spec.**
The working tree had `HORIZONTAL_FOV_DEG` 62.0 → 54.0 and `VERTICAL_FOV_DEG`
48.9 → 41.0 with no source recorded, while three other places still documented
the old values. `54 × 41` is the **Limelight 1** figure. The correct number for
this unit, per Limelight's published spec, is **`62.5 × 48.9`**, and that is what
is now in the header, in the reference doc, and behind every calculation in
Part 1.

Worth knowing why this class of mistake is expensive: it is completely invisible
at runtime. `VERTICAL_FOV_DEG` scales the entire placeholder range curve — a
detection at `ty = −10°` reads 16.7 in under the correct pair and 15.2 in under
the Limelight 1 pair, ~9% off with nothing to indicate it — and
`HORIZONTAL_FOV_DEG` sets the wedge `pollen_map` is allowed to clear, so
narrowing it by 8° leaves a 4°-wide ring on each side where dead tracks are never
swept and ghosts accumulate for the planner to route to. The header now carries a
"do not tidy this to 54 × 41" note next to the constants.

**9. `limelight_position_test.cpp` — the calibration controls did nothing.**
`temp_camera_height` and `temp_camera_pitch` were write-only locals; the geometry
they claimed to adjust is `constexpr`. The on-screen help advertised them anyway,
so an operator would have watched a number move on the Driver Station while the
projection ignored it entirely. The pitch control was also signed backwards — it
clamped to `min(0.0, …)`, so it could only ever aim the camera *up*, the one
direction a floor camera never wants.

Fixed at the root rather than by deleting the controls: added
`limelight::calibration`, a small runtime-mutable struct holding
`camera_height_in`, `camera_pitch_deg` and `use_pinhole_range`, initialised from
the `geometry` constants. `forward_distance_for()` reads it. The rest of
`geometry` stays compile-time on purpose — `pollen_map` derives its frustum from
those constants, so making *them* mutable would let the wedge the map clears
drift away from the wedge the projection assumes.

Also exposed `limelight::forward_distance(ty)` so the opmode can print the curve.
Without it the controls are unobservable: you could only see their effect when a
real pollen happened to be in frame. The opmode now shows `f(ty)` at the live
`ty` plus a fixed ladder (−20°, −15°, −10°, −5°, 0°), so the curve can be fitted
against a pollen at a known distance, on the field, without a rebuild.

**10. `limelight_position_test.java` — the opmode could not be run.**
Every opmode needs a `LinearOpMode` wrapper in
`org/firstinspires/ftc/teamcode/opmodes/`; `lib_loader.cpp` dispatches by
`getSimpleName()` → `dlsym`. `CMakeLists.txt` globs `*.cpp`, so the C++ compiled
into `libteamcode.so` and looked finished, but the entry point was unreachable
and the opmode never appeared on the Driver Station. Wrapper added.

**11. `limelight_position_test.cpp` — `(0,0)` is a real place.**
`if (start_pose.x != 0 || start_pose.y != 0)` used a float-equality sentinel to
mean "no reference set". `(0, 0)` is the field corner, a normal staging spot.
Replaced with an explicit `bool start_pose_set`.

### Cleanup

**12. `limelight.cpp` — removed a dead id-matching pass.**
`update_pollen_tracking()` ran an O(detections × tracks) nearest-neighbour match
plus a `std::vector<bool>` allocation on every frame to assign ids that
`pollen_map::update()` then overwrote from scratch. Nothing read them. The doc
already said so; now the code agrees. `limelight::Pollen::id` is `−1` on the way
out, `next_pollen_id` and the now-unused `distance_squared()` are gone, and the
test opmode numbers detections by frame position instead. One id scheme, one
owner.

**13. `LimelightBridge.java` — the layout comment lied.**
The file declares itself the authoritative mirror of `limelight.cpp`, and its
slot comment said "yaw at index 9 or compacted" while both sides use 6.
Rewritten to describe the actual 2D layout, with slots 7–9 named as the reserved
hole the compaction left and zeroed by a new `clearBotpose()` helper — which also
de-duplicates the three-line clear that had been copy-pasted into both branches.

**14. `limelight_position_test.cpp` — missing `<algorithm>`.**
`std::max` / `std::min` were used with only `<chrono> <cmath> <iomanip>
<sstream> <string>` included. It would have built through a transitive include
and broken later, somewhere unrelated.

**15. Documentation.**
`docs/limelight/limelight.md` was edited in the same working tree but left
contradicting the code it describes: the FOV table, the 32-vs-64 pose ring, the
"all six components" botpose test (the struct has three), and the two-id-pass
note. All reconciled, plus the new pipeline switching, the capture-pose frustum
rule, and the pitch-0 analysis from Part 1. `pollen_map.h` no longer hardcodes
"~62 degree wedge" — it points at `limelight::geometry` instead, so there is one
place to change.

---

## What still needs a human

Nothing in this pass could measure the robot. Two numbers remain guesses, and
they are the ones everything downstream depends on:

1. **`CAMERA_HEIGHT_IN`** — never measured. Currently 10.0.
2. **`CAMERA_PITCH_DEG`** — currently 0, which Part 1 shows is the worst possible
   value. Aim for ~30° down if the height stays at 10 in (anywhere in ~27–34°
   works).

The FOV pair is no longer one of them: `62.5 × 48.9`, confirmed against
Limelight's published spec for this unit.

Run `Limelight Position Test`, put a pollen at a measured distance, and turn
LT/RT and LB/RB until `f(ty)` matches the tape measure. Then copy the two numbers
into `limelight::geometry` so they survive the next boot, and flip
`USE_PINHOLE_RANGE`.
