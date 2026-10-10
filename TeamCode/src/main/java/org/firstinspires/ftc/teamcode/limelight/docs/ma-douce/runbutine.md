# `runButine()` — driving a butine plan

`butine::solve()` decides *which pollen to collect and in what order*. It stops
there: no paths, no drivetrain, no clock. This document is about the piece that
closes the gap — `butine_runner.h` / `.cpp`, which turns a solved run into Pedro
paths and queues them onto the `lierre` sequencer so an autonomous can drive it.

Its companion documents are [`butine.md`](butine.md) (the planner, line by line)
and [`limelight.md`](limelight.md) (the camera and the map that feeds it).

---

## 1. The contract

```cpp
namespace butine {
    void runButine(int    lookahead_runs    = 3,
                   double depot_x           = NAN,
                   double depot_y           = NAN,
                   double depot_heading_deg = NAN,
                   double max_power         = 1.0,
                   double min_confidence    = 0.25);
}
```

Every parameter is optional.

| Parameter | Meaning |
|---|---|
| `lookahead_runs` | How many runs the planner weighs together. `1` is plain greedy. |
| `depot_x`, `depot_y` | Field inches, **unmirrored PINK**, like every authored pose. Omitted keeps the depot from the previous call. |
| `depot_heading_deg` | **Degrees**, like `pedro::p(x, y, deg)` — not radians. |
| `max_power` | `0..1`, straight into `followPath`. Also scales the planner's cruise speed. |
| `min_confidence` | Floor on pollen confidence. `pollen_map` already filters at its own `config.min_confidence`; this only ever raises it. |

Used in an autonomous, beside the ordinary `lierre` verbs:

```cpp
runButine(3, 91.5, 81.0, 270.0);   // set the depot, look 3 runs ahead
fS(W_shoot);                       // score what we collected
runButine();                       // same depot, solved afresh
fS(W_shoot);
runButine(2, 78.5, 69.8, 0.0);     // a different depot, shallower lookahead
```

### One call drives one run

Not the whole autonomous. This is the single most important thing about the
function, and it follows directly from what the lookahead is *for*.

The planner solves `lookahead_runs` runs deep and commits only to the first.
Runs 1..n exist to **shape** run 0 — they are what stops it taking the three
nearest pollen and stranding one on the far side of the field, which then costs a
whole dedicated run. By the time run 0 is over the camera has swept more field,
so runs 1..n describe a map that no longer exists and are thrown away. Calling
`runButine()` again re-solves against the map as it then stands. That is a
receding horizon, and driving runs 1..n blind would defeat the whole point of
computing them.

### The depot is remembered, unmirrored

The depot is kept in two forms. `g_authored` is the pose exactly as the caller
wrote it — unmirrored — so a later call that changes only `x` stays coherent with
a heading set two calls earlier. `g_depot` is that same pose mirrored, and is
what everything downstream uses. Passing nothing at all on the very first call
harvests from wherever the robot is standing (`pedro::robot_pose`, which is
already in the real frame and so must *not* be mirrored).

---

## 2. Why every leg is its own path

`PedroBridge.buildLP` / `buildTP` turn **2 poses into a `BezierLine`** and **3 or
more into a `BezierCurve`** over those poses as *control points*.

A Bezier curve does not pass through its control points. Handing the bridge one
chain containing every pollen in the run would therefore produce a smooth curve
that drives *past* all of them, collecting nothing. A drive-over tour has to be
**one two-pose path per leg**.

They chain for free. `lp_internal` / `tp_internal` build
`[j_lastPose, poses…]` and then move `j_lastPose` to the last pose
(`pedro.cpp:369-416`), so legs built in driving order each start where the
previous one ended. This is the same mechanism that lets `A21G5.cpp` build every
path up front.

### The build cursor has to be believed

That cursor is a single file-static global with no setter. Two consequences:

- **Something else building a path moves it.** `runButine` therefore re-seats it
  to the robot's real pose immediately before it builds — by building a throwaway
  path that ends there and discarding the id, the only way to move it.
- **Re-seat once per run, not per leg.** `PedroBridge` caches path chains in a
  `HashMap` keyed by a hash of their pose list, and never evicts. A path built
  from the live, continuously-varying robot pose is a permanent new cache entry.
  One per run is a handful per match; one per leg would be dozens.

---

## 3. Heading

### Pollen legs: tangent, forwards

Tangent heading means the robot points along the direction it is travelling, like
a car. That is what the intake needs — it sits on one face, so the robot must
arrive pointing at the pollen — and it is what `butine`'s cost model charges for.

One wrinkle the converter has to fix. A `pollen_map::Track` carries
`heading == 0`: the map never sets one, because a pollen has no orientation. Left
alone, a path built to that pose would interpolate the robot onto 0 rad at every
single pickup. So `build_run_legs` **overwrites each target's heading with the
leg's own direction**, `atan2(dy, dx)`, before building. A leg shorter than
`DEGENERATE_LEG_IN` carries the incoming heading through instead, because `atan2`
of a zero-length leg is meaningless and would charge a turn onto an arbitrary
angle and then a turn back off it.

### The leg home: linear

The depot is the scoring pose. The robot has to *arrive pointing a particular
way*, and tangent heading cannot do that — it would leave the robot facing along
whatever direction the last pollen happened to lie in.

So the return leg alone uses linear heading, which interpolates from the heading
the robot has onto `depot.heading` **during** the drive. On a mecanum that is
free: the robot rotates while it translates. It is dispatched with
`holdEnd = true` so it does not drift off the scoring pose while the next solve
runs.

`butine::run_time` charges this final swing, so `build_run_legs` adds it to the
return leg's prediction too. Leaving it out would make the last leg of every run
read systematically fast and quietly bias the calibration.

### On the 90° question

The worst-case swing between two consecutive legs is worth being precise about,
because the answer changes with one assumption.

Consecutive legs with travel directions `d₁` then `d₂` need a swing of
`s = |wrap_pi(d₂ − d₁)|`, which is at most 180°. *If* the robot were free to drive
a leg in reverse, it could instead face `d₂ + 180°`, making the required swing
`min(s, 180° − s) ≤ 90°`, worst case exactly 90° at `s = 90°`.

**That halving does not apply here**, because the intake is on one face: a leg
driven in reverse would arrive with the pollen at the robot's back. Every pollen
leg is built with `reverse = false`, and the worst case stays 180°.

This costs nothing, because the planner already prices it. A near-180° swing means
collecting a distant pollen and then doubling straight back to one beside it —
`leg_cost` charges that around 0.87 s at the default `omega_max_rad_s`, so the
2-opt / Or-opt search orders the tour away from it without any help. `leg_cost`
is therefore left exactly as it is.

If the intake geometry is ever measured and reverse pickup turns out to work, the
change is: pick `reverse` per leg in `build_run_legs`, and use
`min(s, π − s)` in `butine.cpp`'s `leg_cost`. Both, or neither — a cost model that
prices a turn the robot does not perform is worse than one that is merely
pessimistic.

---

## 4. Mirroring

`lp_internal` and `tp_internal` deliberately **do not mirror**, and every leg is
built through them. That is correct, and it is also the thing most likely to be
"fixed" into a bug.

Pollen positions are *observed*. They come out of the camera in the real field
frame — already mirrored, if we are BLUE. Putting them through `pedro::lp` / `tp`,
which mirror at build time, would mirror them a second time and send the robot to
the reflection of every pollen.

The depot is the opposite: it is authored, so it arrives unmirrored. `runButine`
mirrors it once, in `resolve_depot`, and hands the mirrored pose to **both**
`butine::solve` and the path builders. If those two ever disagreed about the
frame, the planner would rank a tour it was not going to drive.

The comment in `butine.h` used to claim the depot must stay unmirrored PINK. It
has been corrected; see the note there.

---

## 5. How the legs are sequenced

`runButine` queues, in order:

1. **one `lierre::d()`** that does the solve and the build when the sequencer
   reaches it — not at call time, because at build time the map is empty,
2. **`MAX_LEGS` wait/settle pairs**, one per leg the run *could* have.

### Why the slots are queued up front

The obvious design is for the solve step to append exactly the legs it built. It
is a trap. `Sequencer::start_current()` invokes the callback *through the
`std::function` that lives in the task vector*:

```cpp
if (m_tasks[m_index].on_start) m_tasks[m_index].on_start();
```

A `push_back` from inside that call can reallocate the vector and destroy the
callable while it is still running. Queuing the worst case up front and letting
unused slots fall straight through costs a handful of no-op `std::function`s and
cannot do that. A slot with `index >= g_leg_count` returns `true` from its
condition immediately, and `Sequencer::update()` chains through all of them in one
tick.

### Why not `lierre::f(path)`

The obvious verb is also a trap, for a related reason. `f(path)` expands to

```cpp
u([]() { return !pedro::isBusy(); }, [path]() { pedro::followPath(path); }, timeout);
```

and `Sequencer::update()` evaluates that condition inside the **same** call that
fired the dispatch. If the follower has not picked the path up yet, `isBusy()` is
still false, the condition is already true, and the leg completes instantly.

So each leg waits on a guarded condition instead: watch for `isBusy()` to go
**true** first (arming), and only then for it to go false. A leg that never arms
within `ARM_TIMEOUT_S` is treated as finished but flagged — the usual cause is a
path the follower silently refused — and counted in `legs_never_busy`.

### Leg timeouts

```
deadline = ARM_TIMEOUT_S + max(2.0, predicted × 2.5 + 1.5)
```

Scaled off the leg's own prediction rather than flat. A flat timeout is wrong in
both directions: a 0.3 s hop that hangs wastes tens of times its budget before
anyone notices, and a long leg on a tired battery honestly exceeds a flat figure
and gets cut off *while still driving* — the dangerous direction. Scaling makes
the timeout mean the same thing on every leg: "the cost model was wrong by more
than this factor", which is also exactly the condition worth reporting.

`lierre`'s own timeout on the step is a backstop only, at `STEP_BACKSTOP_S`. It
has to be, because the real deadline depends on a prediction that does not exist
until the solve has run — long after the step was queued.

### Arriving *is* collecting, even on a timeout

`settle_leg` calls `pollen_map::remove(id)` on every outbound leg regardless of
how it ended. The map cannot observe a pickup for itself: the pollen sits under
the robot, inside the camera's near limit, where no frame is authoritative about
the place it used to be.

Doing it on a timeout too is deliberate. The robot may never have got there — but
leaving the pollen in the map guarantees the next solve routes straight back to
the same unreachable point, and the autonomous livelocks. A pollen driven at for
several times its predicted time and not reached is either mis-projected or
unreachable; either way dropping it is the only ending. `legs_timed_out` is the
number that says how often that happened.

---

## 6. Checking it without a robot

`butine_test`, **gamepad2 left stick button**, builds the current plan's run
exactly as `runButine` would and prints it without driving a foot of it. Press A
first to solve, X first if you want the synthetic field.

What to look at:

- **`legs n/m`** — should be `run.count + 1`, the extra one being the leg home.
- **the last line's `home linear`** — every other leg must read `tangent`.
- **each leg's `-> x,y @ heading`** — an outbound leg's heading must equal its own
  direction of travel; the leg home's must equal `depot_heading`.
- **`worst swing`** — up to 180° is legitimate (see §3), but a tour that routinely
  produces large swings is a sign `turn_coupling` or `omega_max_rad_s` is wrong.
- **`sum … vs run …`** — the summed leg predictions against the plan's own
  `run.time_s`. These must agree. If they do not, the converter and the planner
  disagree about what is being driven, which is the one failure that would
  silently invalidate every calibration number this opmode prints.

On the robot, the right trigger still drives the plan leg by leg and prints
predicted against actual. A ratio far from 1.0 means `cruise_speed_in_s` /
`accel_in_s2` / `omega_max_rad_s` want tuning — not that the converter is wrong.

---

## 7. The autonomous

`butine_harvest.cpp` is the worked example: three harvest runs off the A21G5
scoring pose, with the limelight and map tasks registered (they are **not** part
of `schedule_auto_tasks` — only an opmode that wants them registers them).

It is registered the way every C++ opmode is: an annotation comment in
`header/opmodes/auto/butine_harvest.h` above each `extern "C"` symbol, which
`createOpMode.sh` scrapes into a Java `LinearOpMode` shim whose class name equals
the C symbol. CMake globs `sources/**/*.cpp` with `CONFIGURE_DEPENDS`, so neither
file needed a build change.
