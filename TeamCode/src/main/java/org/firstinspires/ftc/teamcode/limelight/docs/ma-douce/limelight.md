# Limelight subsystem — reference and change history

## Target / Goal - dont edit 

The limelight subsystem is split into two parts: 
- Position finding via April Tags
    We use the known positon of the April Tags on the field to find the robot position due to the deadwheels/ imu not beeing accurate over larger distances( they will still be used in between detections to provide a solid estimate)
- Pollen detection, remembering and collection
    We detect the pollen with a neural network(onboard limelight)
    We get the results and parse them via our java to c++ bridge to our c++ code part
    We calculate its position using trigonometry(the higher the pollen, the further away it is)-- We need a test opMode for that to find a formula f(x)= with x beeing the height on the screen of the pollen and f(x) beeing the distance, same applies to left and right
    We store its position on the pollen map
    We calculate the most efficient path on demand, which collects n pollen in least time
---
## Details/ Ideas for Reaching the goal:
Position finding: just use the returned botpose of the limelight+ potentially small latency compensation
Parsing of the pollen pose: Make sure we are able to parse multiples pollen data not just one
I think it is a quadratic formula for pollen position calculation
use efficient architecture for the datastructure of the pollen, make a cap on how many pollen the map may store: to many-> pop the oldest
For the path finding algorithm: The path doesn't have to be the fastest(still good), but you should rather focus on reducing compute time, also add a variable(int n = 2;) which calculates the best n runs to not only pick of the best path for now but also for the future run;

---

## Goal status — what is done and what is not

Checked against the code, not against intent. The section above is the spec and
is not edited; this is the scoreboard for it.

| # | Goal | Status |
|---|---|---|
| 1 | Read AprilTag botpose from the camera | **Done** |
| 2 | Convert botpose into the Pedro frame | **Done** |
| 3 | Use it to correct the odometry over distance | **NOT DONE — nothing calls `pedro::setPose()`** |
| 4 | Latency-compensate the botpose | **NOT DONE** |
| 5 | Detect pollen with the onboard neural net | **Done** |
| 6 | Parse them across the Java→C++ bridge | **Done** |
| 7 | Parse *multiple* pollen, not just one | **Done** — up to 128 per frame |
| 8 | Project a detection to a field position | **Partly** — bearing exact, range uncalibrated |
| 9 | Test opmode to fit the range formula | **Done — but never run** |
| 10 | Store positions on a persistent map | **Done** |
| 11 | Cap the map, evict the oldest | **Done** — 32 tracks, stalest evicted |
| 12 | Plan the best route on demand | **Done** |
| 13 | Look `n` runs ahead, not just the next one | **Done** — `lookahead_runs`, defaults to 3 |

### The three that actually block the goal

**#3 — the AprilTag fix is not wired into anything.** This is the big one. The
whole stated reason for the AprilTag half is "deadwheels/IMU not being accurate
over larger distances", and the fix for that is to periodically reset the
localizer to the tag solve. `limelight::getPose()` works and returns a correct
Pedro-frame pose, but the only caller in the entire codebase is the telemetry
line in `limelight_position_test`. `pedro::setPose()` exists and is never called
with it. Until something closes that loop, the AprilTag pipeline is a readout,
not a localizer.

Closing it is not just `setPose(*getPose())`, and the reasons matter:

- **You cannot have both halves at once.** One camera, one pipeline. The detector
  and the AprilTag solve are mutually exclusive (see `set_pipeline()`), so a
  match plan has to decide *when* to look at tags — e.g. at the depot between
  runs, where the robot is stationary and the pollen map is least likely to go
  stale — rather than assuming both stream continuously.
- **A tag fix must not fight the follower.** Snapping the pose mid-path makes the
  follower see a step disturbance. Correct at run boundaries, or blend rather
  than snap.
- **Gate on quality.** `botposeValid` only says "a solve exists". A single-tag
  solve at 3 m is far worse than a two-tag solve at 1 m. Reject fixes that
  disagree with odometry by more than a threshold, rather than trusting them.

**#4 — botpose latency compensation.** The machinery already exists: the pose
trail and `pose_at()` were built for the pollen projection and would work
unchanged here. `getPose()` currently returns `last_result.x/y/yaw` composed with
nothing, so the pose it reports is one pipeline latency (40–80 ms) stale. At
70 in/s that is 3–6 in — the same error the pollen path goes to trouble to
remove. Feeding the fix through `pose_at(captured_at)` and correcting for the
odometry delta since then is the missing piece.

**#8 / #9 — the range curve is still a placeholder.** The bearing (left/right)
is *not* a problem and never was: the Limelight reports `tx` in degrees, so
lateral offset is exactly `forward × tan(tx)`. No formula needs fitting for that
axis, which resolves the "same applies to left and right" line in the spec.

The range axis is the open one. Two notes on the spec's guess that it is
quadratic:

- The true physics is **not** quadratic. For a pinhole camera on a flat floor it
  is `height / tan(pitch − ty)` — a tangent. `USE_PINHOLE_RANGE` selects it and
  is off, because `CAMERA_PITCH_DEG` is still 0.
- The quadratic currently running is a *fit*, not a model, and a deliberate
  placeholder. It is monotonic and roughly the right shape over the frame, which
  is enough to not be nonsense, and nothing more.

The right move is not to fit a better quadratic — it is to measure the two
numbers the exact model needs. See §4: mount height, aim ~30° down, put both in,
flip the switch. `Limelight Position Test` exists to make that a five-minute job:
it prints `f(ty)` live and lets you tune height and pitch on the gamepad.

### Smaller gaps, worth knowing about

- **Confidence threshold is untuned.** `pollen_map::Config::min_confidence = 0.25`
  is now the only defence against a false positive entering the map, and it was
  picked, not measured.
- **`CAMERA_FORWARD/LEFT_OFFSET_IN` are both 0**, i.e. the code assumes the camera
  sits exactly on the robot's centre of rotation. It does not. This is a fixed
  offset and trivially measurable.
- **`butine` output is never driven outside the test opmode.** `butine::solve()`
  returns a `Plan` — an ordered list of waypoints, not a path and not an opmode.
  The only code that turns one into motion is `butine_test::follow_tick()`, which
  also supplies the collect handshake (`pollen_map::remove(id)` on arrival, since
  a pollen under the robot sits inside the camera's near limit and the map can
  never observe it vanish). **There is no autonomous routine using any of this
  yet** — that is the last integration step, and it is the same conversation as
  #3, because an auto is where "look at tags at the depot, drive pollen in
  between" has to actually be scheduled.

---

---
Covers the Limelight bridge, the pixel/angle → field projection, the persistent
pollen map, and the two opmodes that drive them. Written after a commit-by-commit
review of the whole arc, from `b59acb5` ("gevibeter commit für die limelight —
ungetestet") through `5f6ec73`.

Everything asserted here about the FTC SDK was checked against the vendored
sources (`~/.gradle/caches/modules-2/files-2.1/org.firstinspires.ftc/Hardware/11.2.0/…-sources.jar`,
`com/qualcomm/hardware/limelightvision/`), not recalled. Where something is a
guess or a placeholder it says so.

---

## 1. What exists

| File | Role |
|---|---|
| `java/…/pedroPathing/LimelightBridge.java` | Owns the `Limelight3A`. Packs one shared `double[]`. |
| `cpp/teamcode/header/subsystems/limelight.h` | C++ API, and `limelight::geometry` — the single definition of the camera. |
| `cpp/teamcode/sources/subsystems/limelight.cpp` | JNI binding, pose history, projection, frame-to-frame pollen tracking. |
| `cpp/teamcode/header/subsystems/pollen_map.h` | Persistent map: `Track`, `Config`, `Stats`. |
| `cpp/teamcode/sources/subsystems/pollen_map.cpp` | Frustum replacement, id carry-over. |
| `cpp/…/opmodes/test/limelight_neural_network_trainer.cpp` | Dataset collection (snapshots for offline labelling). Trains nothing on-robot. |
| `cpp/…/opmodes/test/butine_test.cpp` | Planner test bench; also the only place the map is exercised end to end. |

Data flow:

```
Limelight3A ──► LimelightBridge.update()      Java: one shared double[920]
                        │
                        ▼  JNI, no per-tick allocation
             limelight::update()               C++: read buffer, track pollen
                        │
                        ▼
             limelight::get_pollen()           current analysed frame ONLY
                        │
                        ▼
             pollen_map::update()              persistence + eviction
                        │
                        ▼
             butine::solve()                   routing
```

The split matters: `get_pollen()` is **memoryless**. It is the current frame and
nothing else — a frame with no detections wipes it. Persistence is
`pollen_map`'s job, and it is a separate layer precisely so that "the camera
cannot see it right now" and "it is not there any more" stay distinguishable.

---

## 2. The shared buffer protocol

One `double[]`, held as a JNI global ref, written by Java and bulk-read by C++.
No JNI objects are allocated per tick. **The layout is duplicated in
`LimelightBridge.java` and `limelight.cpp` — change one, change the other.**

| Slot | Name | Meaning |
|---|---|---|
| 0 | `VALID` | Result had a target (`isValid()`) |
| 1–3 | `TX`, `TY`, `TA` | Primary target offsets / area |
| 4–9 | `BOTPOSE_X…` | x, y, z (metres), roll, pitch, yaw (degrees) |
| 10 | `CAPTURE_LATENCY` | ms |
| 11 | `TARGETING_LATENCY` | ms |
| 12–15 | `TEMP`, `CPU`, `FPS`, `PIPELINE_INDEX` | Only refreshed by `updateFull()` |
| 16 | `FRAME_AVAILABLE` | A frame was analysed — including one containing nothing |
| 17 | `FRAME_TIMESTAMP` | Limelight-local monotonic timestamp |
| 18 | `DETECTION_COUNT` | |
| 19 | `BOTPOSE_PRESENT` | An AprilTag solve actually exists |
| 20 | `PARSE_LATENCY` | ms, Control Hub JSON cost |
| 21 | `STATUS` | 0 not initialised, 1 ok, 2 init failed |
| 22–23 | reserved | |
| 24… | detector records | stride 7 |

Detector record: `[isPollen, confidence 0..1, txPixels, tyPixels, area, txDegrees, tyDegrees]`

Size: `24 + 128 × 7 = 920`. The C++ side's furthest read is
`24 + 127×7 + 7 = 920`. Exactly fits, no over-read.

Two slot groups are pulled as a single `GetDoubleArrayRegion` rather than one
call each — `[16..18]` and `[19..21]`. Their adjacency is load-bearing, so
`limelight.cpp` `static_assert`s it. Renumbering a slot is a build error, not a
silent offset shift.

### Clearing semantics

`update()` clears everything from slot 16 up before doing anything else, then
restores `STATUS` (which is not per-frame state). This is deliberate: a frame
that contains no pollen must reach the native side as *"an image was analysed
and it was empty"*, because that is what lets the map delete things. Only slots
0–15 survive a frame, and the target block within them is now explicitly zeroed
when `isValid()` is false so a stale target can never be read back as current.

---

## 3. Coordinate frames

Three frames are in play and two of them nearly agree, which is the trap.

| | Origin | Units | Notes |
|---|---|---|---|
| **Pedro** | field corner | inches, radians | `0..144`. What everything on the robot uses. |
| **Limelight `botpose`** | field **centre** | metres, degrees | `±1.8 m`. Converting needs a scale **and** a shift. |
| **Camera-relative** | the crosshair | degrees | `tx` positive right, `ty` positive up. |

`limelight::getPose()` does the full conversion:
`metres → inches`, then `+ FIELD_CENTRE_IN` on both axes, then `deg → rad` on
yaw. Without the shift a robot at Pedro `(100, 40)` reads back as `(28, −32)`.

The robot position is calculated in 2D space (although it exists in 3D, it can't flip or fly) — therefore Z, pitch, and roll values from Limelight are useless.

Two more things about `getPose()`:

- It returns `nullopt` unless `botposeValid`. A missing `"botpose"` key yields an
  all-zero pose rather than an absent field, so without that check it reported the
  field origin as a genuine reading.
- **On the detector pipeline there is never a solve.** The neural-net detector
  produces no AprilTag pose, so on that pipeline `getPose()` always returns
  `nullopt`. That is correct behaviour, not a fault.
- **Switching pipelines is now possible.** `limelight::set_pipeline()` selects a
  slot; `DETECTOR_PIPELINE` (0) for pollen, `APRILTAG_PIPELINE` (1) for botpose.
  The two are mutually exclusive — one camera, one pipeline — so a run that wants
  both has to alternate. Switching clears the pollen map and the last result,
  because whatever is held describes the pipeline being left.

### Camera-relative → field

Bearings come from the Limelight's own `getTargetXDegrees()/getTargetYDegrees()`.
They used to be reconstructed from pixel columns, which needed an assumed image
width, an assumed FOV and an assumed linear pixel-to-angle mapping — three
assumptions to recover a number the camera had already computed.

Positive `tx` is camera-right, which is robot **−y** in the Pedro frame. The
projection composes: range and bearing → camera-local forward/left → camera yaw
rotation → robot pose rotation → field.

> **Crosshair, not optical centre.** Both the degrees and the pixels the
> Limelight reports are measured from the *crosshair*, which is settable in the
> Limelight web UI. Nudging it there silently biases every pollen this code
> places. Leave it centred, or fold the offset into `limelight::geometry`.

---

## 4. Calibration status — read this before trusting a position

`limelight::geometry` (in `limelight.h`) is the single definition of the camera.
`pollen_map::Config` takes its defaults from it, so the wedge used for eviction
cannot drift away from the wedge used for projection.

| Constant | Value | Status |
|---|---|---|
| `CAMERA_HEIGHT_IN` | 10.0 | **Guess.** Never measured. |
| `CAMERA_PITCH_DEG` | 0.0 | **Guess**, and physically impossible — a level camera never meets the floor. |
| `CAMERA_FORWARD/LEFT_OFFSET_IN` | 0.0 | Assumes the camera sits on the robot centre. |
| `CAMERA_YAW_DEG` | 0.0 | |
| `HORIZONTAL_FOV_DEG` | 62.5 | **Confirmed** against Limelight's published spec. |
| `VERTICAL_FOV_DEG` | 48.9 | **Confirmed** against Limelight's published spec. |
| `NEAREST/FAR_FORWARD_IN` | 8 / 60 | Working assumption about usable range. |
| `POLLEN_DIAMETER_IN` | 3.0 | Sets the 3-inch id carry-over gate. |
| `FIELD_WIDTH_IN` | 144.0 | Real. |

The FOV pair is `62.5 × 48.9`, taken from Limelight's published spec for this
unit. It was briefly changed to `54 × 41` — the **Limelight 1** figure — and that
is worth knowing about, because the mistake is invisible and expensive:
`VERTICAL_FOV_DEG` sets the entire scale of the placeholder range curve (a
detection at `ty = −10°` reads 16.7 in under the correct pair and 15.2 in under
the wrong one, ~9% off with nothing to indicate it), and `HORIZONTAL_FOV_DEG`
sets the wedge `pollen_map` is allowed to clear, so narrowing it by 8° leaves a
4°-wide ring on each side where dead tracks are never swept and ghosts
accumulate. Do not "tidy" these numbers.

### Range: two models, one switch

```cpp
constexpr bool USE_PINHOLE_RANGE = false;
```

- **Off (current)** — a fitted quadratic across the vertical FOV. It is a guess
  dressed as a curve. It is now driven by `ty` **degrees** rather than by a pixel
  row, so it no longer depends on the pipeline's output resolution, but it is
  still not a camera model.
- **On** — the exact flat-floor solution, `height / tan(pitch − ty)`. Correct
  geometry, and it is what should be running.

It ships off because `CAMERA_PITCH_DEG` is still `0`.

Be careful with the reason. A level camera **does** see the floor — the vertical
FOV reaches below the horizon, so the bottom half of the frame is floor, and
`depression = pitch − ty` is positive there. The earlier claim that "a level
camera never meets the floor" was wrong about the optics. What is true is that
the *geometry is useless* at pitch 0, for three separate reasons:

- **Half the frame is dead.** Every ray at or above the horizon (`ty ≥ 0`) never
  reaches the floor, so 50% of the sensor cannot range anything.
- **The near limit is wrong by nearly 3×.** The closest visible floor point is at
  the steepest downward ray, `ty = −VFOV/2`: `10 / tan(24.45°) = 22.0 in`.
  `NEAREST_FORWARD_IN` claims 8. The pollen you most want to collect are exactly
  the ones the optics cannot reach.
- **It is ill-conditioned.** `d(range)/d(ty) = −h / sin²(depression)` blows up
  near the horizon. At 1° of depression the range is 573 in and one more degree
  of `ty` moves it by ~570 in. Combined with the 120 in plausibility cap, only
  `ty < −4.8°` survives at all — 40% of the frame.

Pitching the camera **down** fixes all three at once: it slides the usable band
toward the robot, puts the whole frame on the floor, and moves the measurement
into the well-conditioned part of the curve. At a 10 in height, **~30° down**
maps the frame onto `7.1 .. 103 in`, with the configured `8 .. 60 in` band
sitting strictly inside it and nothing rejected.

**Measure the mounting height and downward pitch, put them in, flip the switch.**
That is the single highest-value calibration action available, and until it
happens every pollen position carries error the planner cannot recover from.

The switch and the two constants it depends on are also mirrored in
`limelight::calibration`, which is runtime-mutable, so `limelight_position_test`
can fit the curve on the field without a rebuild. `limelight::geometry` stays the
place the defaults are written; copy the fitted numbers back there when done.

`CAMERA_HEIGHT_IN` and `CAMERA_PITCH_DEG` used to be declared and never read,
which made a fitted quadratic look like a pinhole model. They are now genuinely
used — by the branch that is switched off.

---

## 5. Latency compensation

A detection describes where the world was when the **shutter opened**, not when
the Control Hub finished parsing it. The projection therefore looks up where the
robot *was*, not where it is:

```
captured_at = now − (captureLatency + targetingLatency + parseLatency)
capture_pose = interpolate(pose_history, captured_at)
```

`limelight.cpp` keeps a 64-deep ring of `(timestamp, pedro::robot_pose)`, pushed
by every `update()`/`update_full()`. At the 10 ms poll rate that is ~640 ms of
history. The depth is **tied to the latency clamp below** and a `static_assert`
enforces it: the ring must span at least `MAX_LATENCY_MS`, or a slow frame falls
off the oldest end and the lookup silently substitutes the oldest pose it still
holds — a wrong answer with no error. Lookup brackets the two nearest
samples and interpolates linearly, with heading interpolated through
`wrap_pi` so the ±π seam does not spin the robot the long way round. If history
cannot cover the request it falls back to the current pose, which is the old
behaviour and no worse.

Total latency is clamped to `[0, 500] ms` so a garbage reading cannot reach far
back into the ring.

Two caveats worth knowing:

- `pedro::robot_pose` itself only refreshes at 20 ms, so the trail's real
  granularity is 20 ms even though it is sampled at 10 ms.
- In the trainer, `robot::init(TELE, true)` selects foxd, and `pedro::robot_pose`
  is kept current by `protocols::sync_foxd_to_legacy()`. The compensation works
  in both drivetrain modes for that reason — if that sync is ever removed, this
  breaks silently.

---

## 6. The pollen map

`get_pollen()` gives one frame over a ~62° wedge reaching roughly 8–60 inches.
The field is 144 inches across. A planner cannot route from that, so
`pollen_map` remembers.

### The rule

**A frame is authoritative over the region it covers.** Everything inside the
camera frustum is replaced wholesale by what that frame saw; everything outside
it is left alone, however old — not seeing what you are not looking at is not
evidence.

```
for each track:  if in_frustum(robot, track) -> clear it
for each detection: insert it
```

That is the whole model. There is no matching a detection to a track to decide
whether they are "the same pollen", no consecutive-miss counter, and no
confirmation delay. All three guessed at continuity between frames, and the
guess is unnecessary: the camera just measured that wedge, so the answer for
that wedge is whatever it returned.

```cpp
bool in_frustum(robot, target):
    range in [near + margin, far − margin]
    and |bearing − robot.heading| <= hfov/2 − margin
```

The margins shrink the wedge before anything is cleared, so the region the map
claims to have measured stays strictly inside the region the camera can actually
see. A track hovering exactly on the edge would otherwise be dropped and
recreated on alternate frames as the pose jitters.

`robot` here is **`limelight::pollen_frame_pose()`, not `pedro::robot_pose`** —
the latency-compensated pose the frame's own detections were projected from. The
two differ by the pipeline latency plus whatever gap the scheduler added. Using
the current pose instead sweeps a wedge the camera never looked at: a robot
turning between capture and sweep deletes pollen that are really there and keeps
ghosts where it is now pointing.

### Why replace rather than reconcile

The old rule matched each detection to the nearest track within 3 inches, and
deleted a track only after N consecutive in-frustum misses. That has a failure
the replace rule cannot have: **a pollen that MOVED further than the match
distance** — knocked by an opponent, or displaced by projection error — matched
nothing, so its old track was never refreshed, while the new sighting created a
second track. One real pollen, two entries, and the planner routing to both.
Replacing the region cannot produce that, because the stale entry was in view.

It is also idempotent. Re-running the same frame yields the same map, so a
repeated frame is harmless rather than destructive.

### Ids, and why they still exist

Track ids are carried across frames by proximity, but **only as a label, never
to decide existence**. A detection inherits the id of the nearest track the same
frame just cleared, within `match_distance_in`.

There is exactly **one** id-carry pass, and it lives in `pollen_map`.
`limelight.cpp` used to run a second one — matching the current frame to the
previous one and tagging it — but those ids were overwritten by
`pollen_map::update()` before anything read them. It has been removed;
`limelight::Pollen::id` is now always `−1` on the way out of `limelight.cpp`, and
the frame's detections are identified by position in the frame. Only
`pollen_map`'s ids mean anything.

The planner needs a stable id to say "I collected this one" (`remove(id)`),
because a pollen being swallowed sits under the robot, closer than the camera's
near limit, where the map can never observe its disappearance for itself. Get
`match_distance_in` wrong and ids churn; the map still holds exactly what the
camera saw.

The same gate does one more job: a detection supersedes any track that survived
the frustum sweep but sits on top of it. Without that, a pollen straddling the
margin-shrunk boundary — not quite inside the cleared wedge, but detected anyway
— would be entered a second time next to its own older entry.

### Cost of the rule

A detector flicker (one frame where the net misses a pollen it can see) deletes
that track immediately, where the miss counter tolerated a frame or two. It
returns on the next frame that covers it, so the exposure is one frame wide and
the planner only reads the map at run boundaries. In practice the old tolerance
was 2 real frames — tens of milliseconds — so the difference is small.

The reverse also holds: a single false positive enters the map at once, with
`min_confidence` as the only filter. **That threshold is now the entire defence
against detector noise** and is worth tuning against real detections.
`butine::Params::min_confidence` is a *second*, independent gate applied at solve
time (default `0.0`, i.e. off); `pollen_map::Config::min_confidence` (default
`0.25`) is the one that actually guards the map. Tune the map's; leave the
planner's unless you want a planning-time filter.

### Frame gating

`update()` is gated on `limelight::pollen_frame_sequence()`. `getLatestResult()`
can hand back the same frame repeatedly; processing it again is harmless under
the replace rule, but it would churn ids for nothing. A **counter**, not the
frame timestamp: the timestamp is `0` when the bridge cannot supply one, and a
counter also distinguishes a genuinely new *empty* frame from a stale one —
which is exactly the case the replace rule depends on, since an empty frame is
what clears a wedge.

Confidence is `0..1` here. See BUG 1.

---

## 7. Commit history

| Commit | Date | Size | |
|---|---|---|---|
| `b59acb5` | 31 Aug | +111 | limelight.h/.cpp created |
| `b8870f4` | 1 Sep | +220 | `update_full`, snapshots, NN trainer opmode |
| `e1b3a2c` | 2 Sep | +87 | LimelightBridge.java |
| `8eea9c3` | 3 Sep | +22/−1 | `getPose()` |
| `b93ef49` | 8 Sep | +2302/−24 | detector pipeline, tracking, JNI hardening, planner |
| `5f6ec73` | 8 Sep | +548/−184 | the nine bug fixes below |

### `b59acb5` — the bridge, before there was anything to bridge to

Created the JNI side against a Java class **that would not exist for two more
days**. `FindClass` failed, `ensure_init` bailed, every entry point was a silent
no-op. Nothing could have run, which is what "ungetestet" meant.

The shape was right — cache the class and method ids once, hold a global ref to a
shared array, bulk-read on each tick. Five JNI-lifetime bugs came with it:
uncleared pending exceptions after `GetStaticMethodID`, an unchecked null buffer,
calling through a null method id, `stop()` leaving stale method ids, and a
partial init cached forever. The first four were fixed in `b93ef49`; the fifth in
`5f6ec73`.

### `b8870f4` — "Den vibe von gestern entfernt"

Despite the message, added surface rather than removing it: `update_full()`,
`capture_snapshot()`, the status fields, and the dataset-collection opmode. Also
introduced the dataset-overwrite bug (BUG 5). `limelight.h` began using
`std::string` without including `<string>`, compiling only because `sdk.hpp`
happened to pull it in.

### `e1b3a2c` — the Java side finally exists

The bridge became live. Clean and defensive — `init` swallows hardware-map
failures so a competition opmode still starts, `hardInit` throws for testing.
But `hardInit` is not reachable from C++, and `init` swallowing the exception
meant a missing camera was undiscoverable (BUG 7).

### `8eea9c3` — `getPose()`

Small. Unit conversions correct; frame wrong (BUG 3) and silently zero-valued
when there is no solve (BUG 4). Also fixed the `data[20]`→`data[16]` over-read
and added the missing includes.

### `b93ef49` — detections, tracking, and the planner

Two authors' work in one commit. The detector pipeline, the projection, the
nearest-neighbour tracker with stable ids, and the JNI hardening that fixed four
of the original five bugs. Plus `pollen_map`, `butine` (the planner) and
`butine_test`.

### `5f6ec73` — the fix pass

---

## 8. The nine bugs

### BUG 1 — confidence was on the wrong scale

`DetectorResult.getConfidence()` is documented **0–100**. Everything downstream
treated it as a 0–1 fraction.

`pollen_map`'s `min_confidence = 0.25` therefore admitted anything the net was
more than **0.25%** sure about. The noise filter the whole map depends on was
doing nothing, and junk detections became tracks the planner drove to. It matters
more now than it did then: the confirmation delay that used to sit behind it is
gone, so `min_confidence` is the only filter left (see §6).
Worst on a single-class detector, where low-confidence false positives are
exactly what you get.

**Fixed** by normalising once at the Java boundary (`/ 100.0`), the single place
the value crosses. The native side now rejects anything outside `0..1` rather
than only negatives, so a future protocol slip is caught rather than absorbed.

*This one was introduced by the planner work, not by the original bridge.*

### BUG 2 — nothing compensated for latency

Detections were composed against the odometry pose at parse time.
`captureLatency` and `targetingLatency` were carried all the way into
`last_result` and never read.

At 70 in/s and the 40–80 ms this pipeline actually costs, every pollen landed
3–6 inches behind itself along the direction of travel. That is **further than
the 3-inch association gate**, so a moving robot could also fail to match a
pollen to its own track and mint a duplicate id.

**Fixed** by the pose-history ring described in §5. `getParseLatency()` is now
forwarded too — it also elapses before we see the frame. Frame-level fields moved
out of the `isValid()` branch, because the native side was otherwise reading the
*previous* frame's latencies, which is precisely the data this depends on.

### BUG 3 — `getPose()` returned a field-centred pose into a corner-origin frame

Metres→inches and degrees→radians were right; the half-field origin shift was
missing. A robot at Pedro `(100, 40)` read back as `(28, −32)` — off the field.

**Fixed** by adding `FIELD_CENTRE_IN` to both axes. Latent, since nothing
consumed `getPose()`.

### BUG 4 — `getPose()` reported `(0,0,0)` as a real pose

`getDoubleArray("botpose", 6)` returns an all-zero array when the key is absent,
so `getBotpose()` is never null and an absent solve is indistinguishable from a
pose at the field origin. The function gated on `last_result.valid` — which means
"the frame had a target", not "a pose exists". On the detector pipeline there is
no solve at all, so this was every call.

**Fixed**: the bridge reports `BOTPOSE_PRESENT` (**all** components exactly zero
⇒ no solve) and `getPose()` returns `nullopt` when it is unset. Note `getPose()`
still also requires `last_result.valid` (a target was found) *in addition to*
`botposeValid`.

The test is `||`, and it has to stay `||`. It was briefly changed to `&&`, which
inverts the meaning from "reject only when everything is zero" to "reject when
anything is zero" — and since botpose is measured about the field **centre**,
`x == 0` is the entire centre line, `y == 0` the other one, and `yaw == 0` a
robot squared to the field axis. Under `&&` a perfectly good solve is discarded
across two full lines of the field.

### BUG 5 — the trainer overwrote its own dataset

`img_count` is process-lifetime state that nothing reset, and snapshots are named
on the Limelight, where a repeated name replaces the file. Restarting the robot
app began again at `img_1`. With auto-capture on a 2 s timer that destroys a
session in minutes, silently.

**Fixed**: filenames carry a per-session epoch-seconds prefix
(`img_<session>_<n>`), so every run writes into its own namespace and sessions
sort chronologically.

### BUG 6 — the projection rebuilt angles the camera had already computed

Bearing was derived from pixel columns via an assumed 320 px width, an assumed
62° FOV and an assumed linear pixel-to-angle mapping.

**Fixed**: `getTargetXDegrees()/getTargetYDegrees()` are forwarded and used
directly. All three assumptions gone, along with any dependence on the pipeline's
output resolution — and with any ambiguity about which way the pixel row axis
points, which the SDK does not document.

### BUG 7 — a missing Limelight was undiscoverable

`init()` swallowed the exception into a null handle; `hardInit` — the documented
*"throws so you get crucial information"* path — is not reachable from the native
side; C++ `init()` returns `void`. A wrong hardware-map name gave an empty map,
forever, with no indication. Combined with BUG 1 the map could not tell "camera
absent" from "camera sees nothing".

**Fixed**: the reason goes to `RobotLog`, the state goes to
`limelight::status()` / `is_connected()`, and both opmodes show a telemetry line.

### BUG 8 — a partially failed init was cached forever

`ensure_init` set `j_bridgeClass` *before* resolving the method ids, then used it
as the "already done" guard. A class that loaded while its methods did not — a
stale APK, a renamed method — stayed half-built for the life of the process.

**Fixed**: initialisation is all-or-nothing behind `j_ready`, and any failure
calls `release_bridge()` so the next call retries cleanly.

### BUG 9 — the camera geometry existed twice

Once as file-static constants in `limelight.cpp` (in an anonymous namespace, so
unincludable), once hand-copied into `pollen_map::Config`, tied together only by
a comment. Since the map uses its copy to decide when *not* seeing a pollen
counts as evidence, disagreement means eviction either stops firing or fires on
things still in view. Both fail silently.

**Fixed**: `limelight::geometry` in the header is the single definition; both
read it.

### Also fixed in passing

- `tx/ty/ta` and botpose cleared on invalid frames instead of left stale.
- The detector pipeline index is a named constant, not a literal `0`.
- Both opmodes poll detections at frame rate but the status block only every
  250 ms, instead of parsing a second JSON document at 100 Hz.
- The two single-region JNI reads assert their own slot adjacency.

### Checked and *not* changed

`getTargetXPixels()` is documented as an offset **from the crosshair**, so the
original centre-relative assumption was correct. This was suspected to be an
absolute-pixel origin bug — it is not one. (It is the crosshair rather than the
optical centre, which is a separate, documented caveat; see §3.)

---

## 9. Rules for future edits

1. **The buffer layout lives in two files.** `LimelightBridge.java` and
   `limelight.cpp`. The `static_assert`s catch adjacency breaks, not renumbering
   that stays adjacent. Change both, deliberately.
2. **Confidence is `0..1` everywhere past the bridge.** The `0–100` scale exists
   only inside `LimelightBridge.update()`. Do not "fix" a threshold by scaling it
   up — fix the boundary.
3. **Never treat a repeated frame as evidence.** Gate on
   `pollen_frame_sequence()`. Miss-counting a repeat deletes the map.
4. **Observed poses are already in the real field frame.** Pollen positions are
   built from `pedro::robot_pose`, so on BLUE they are already mirrored.
   `pedro::lp/tp` mirror what they are given at build time and would mirror them
   a second time — measured coordinates go through `tp_internal`/`lp_internal`.
   Authored constants still go through `lp/tp` as usual.
5. **`limelight::geometry` is the only definition of the camera.** Do not
   re-declare a FOV or a range anywhere else.
6. **`update()` feeds the pose trail.** If you replace it with a bare
   `update_full()` at a slow rate, the latency compensation quietly degrades to
   the nearest available sample.
7. **`getPose()` returns `nullopt` on the detector pipeline.** That is correct.
   An AprilTag pose needs a different pipeline selected.

---

## 10. Verification status

Done:

- Java compiles (`:TeamCode:compileDebugJavaWithJavac`).
- Native library builds for `arm64-v8a` and `armeabi-v7a`
  (`:TeamCode:externalNativeBuildDebug`).
- Buffer layout cross-checked across the language boundary: 920 doubles both
  sides, furthest read exactly 920.
- The planner's host check suite still passes (capacity/duplicate invariants,
  deadline monotonicity, compute-budget adherence, determinism, degenerate
  inputs).

**Not done — needs the robot.** None of the following has ever run on hardware:

- Latency compensation against real motion.
- Frustum eviction against a real camera.
- `getPose()` against a real AprilTag solve.
- Any range estimate whatsoever, since the mounting is unmeasured.

### Bring-up order

1. **Confirm the confidence scale.** Run *Butine Planner Test*, look at real
   detections, confirm values now sit in `0..1`. Fastest possible check that the
   boundary normalisation is right.
2. **Measure the camera.** Height above the floor and downward pitch. Put them in
   `limelight::geometry`, set `USE_PINHOLE_RANGE = true`. Until this is done
   every position is a guess.
3. **Confirm `VERTICAL_FOV_DEG`.** It was added during the fix pass and is a
   nominal figure; it drives the placeholder range curve.
4. **Check the map behaves.** Place a few pollen, sweep the camera across them,
   watch the track count rise and settle. Turn away — the count must **not**
   drop. Remove one by hand and look back at the empty spot; its track must
   disappear on the first frame that covers it.
5. **Check latency compensation.** Drive past a stationary pollen and watch its
   tracked field position. It should stay put; if it shifts with velocity, the
   latency figures or the pose trail are wrong.
6. **Trainer sanity.** Capture a few images, restart the app, capture again,
   confirm two distinct session prefixes.

Deploy with `bash TeamCode/src/main/cpp/download.sh -w` (or drop `-w` for the
Wi-Fi target at `192.168.43.1:5555`).
