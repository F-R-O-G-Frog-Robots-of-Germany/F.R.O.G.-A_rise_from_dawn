# `butine.cpp` — the pollen harvest planner, explained line by line

This document walks through **every** declaration, function, struct, loop and
local variable in `TeamCode/src/main/cpp/teamcode/sources/core/logic/butine.cpp`
(982 lines). Each section pastes the real code and then explains what it is for.

The explanations are about **intent** — why this thing exists in the planner, and
what would go wrong without it — not about C++ language features. If a section
says "we keep a lookup table", it will tell you *what the table buys us*, not
what a two-dimensional array is.

Line ranges in the headings refer to the source file as it stands at the commit
this document was written against. Use them to jump; do not trust them after a
large refactor.

---

## 0. Naming, and what "butine" means

| Name | Origin | What it means here |
|---|---|---|
| **`butine`** | French verb *butiner* — what a bee does when it forages flowers for nectar and pollen | The whole file is the "go forage" planner. It decides which pollen the robot picks up and in what order. |
| **Ma-Douce** | French, "my sweet one" | The robot / repository name. The bee-and-honey theme is why the game pieces are called *pollen* throughout the codebase. |
| **`pedro`** | Pedro Pathing, the FTC path-following library this robot drives with | `pedro::Pose` is that library's `(x, y, heading)` triple — inches, inches, radians. Everything geometric in this file is a `pedro::Pose`. |
| **`limelight`** | The Limelight smart camera | `limelight::Pollen` is one detected game piece: an id, a field position, a confidence, a blob area, and when it was last seen. The planner consumes these and never talks to the camera itself. |
| **depot** | Operations-research term for the single node a vehicle starts and ends at | The fixed pose the robot leaves from and returns to in order to score. Every run in this planner is a closed loop through the depot. |
| **run** | — | One depot to pollen to pollen to depot loop. The robot can only hold `capacity` pollen, so a harvest is a sequence of runs. |
| **candidate** | — | One pollen the planner is allowed to consider this solve. The map may hold more pollen than that; see `candidate_cap`. |

### Abbreviations used in identifiers

| Abbreviation | Expansion | Note |
|---|---|---|
| `Ctx` | **C**on**t**e**x**t | The immutable description of one problem instance. |
| `K` | — | Standard operations-research shorthand for "how many customers/targets there are". Here, `ctx.count`, the number of candidate pollen. |
| `INF` | infinity | Sentinel for "no solution found yet", so that the first real answer always wins the comparison. |
| `EPSILON` | epsilon | Float slack. Used so that an "improvement" of 1e-15 — which is rounding noise, not a real gain — cannot be accepted and send the search into an infinite accept/re-accept loop. |
| `_in` | inches | e.g. `DEGENERATE_LEG_IN`, `distance_in`. |
| `_s` | seconds | e.g. `turn_settle_s`, `remaining_s`. |
| `_in_s` | inches per second | `cruise_speed_in_s`. |
| `_in_s2` | inches per second squared | `accel_in_s2`, `decel_in_s2`. |
| `_rad` / `_rad_s` | radians / radians per second | `turn_deadband_rad`, `omega_max_rad_s`. |
| `_ms` / `_us` | milliseconds / microseconds | `compute_budget_ms`, `solve_us`. |
| `omega` | the Greek letter used for angular velocity | How fast the robot can spin. |
| `gamma` | the Greek letter used for a discount factor | The per-run discount applied to time. |
| `J` | — | Control-theory notation for the cost functional being minimised. In this file, `Solution::objective`. |
| `rng` | random number generator | A tiny deterministic one; see `Rng`. |
| `2-opt` | "two-optimal" | A tour-improvement move: reverse one contiguous stretch of the tour. |
| `Or-opt` | named after Ilhan Or | A tour-improvement move: lift a short stretch out and re-insert it somewhere else, possibly reversed. |
| `TSP` | Travelling Salesman Problem | Visit every node once, return to start, minimise cost. |
| `CVRP` | Capacitated Vehicle Routing Problem | The same, but the vehicle can only carry `capacity` items, so the tour is split into several depot-to-depot runs. |
| `OP` | Orienteering Problem | Collect as much as you can *within a time limit*, and you are allowed to skip things. |
| `p` | prefix / position | In `sequence_time_from`, the index the walk resumes at. |
| `r` | run index | Which run of the plan. |
| `m` | member index | Which pollen inside a run. |
| `c` | candidate index | Which candidate pollen, `0..K-1`. |
| `j` | tour position | Where along the giant tour the split has got to. |
| `s` | span | How many pollen a trial run holds. |
| `a`, `b` | run indices | The two runs a cross-run move is shuffling between. |
| `PINK` | — | This team's enum name for what FTC calls the red alliance. Relevant only because `solve()` demands *unmirrored* PINK-side field coordinates. |

---

## 1. What problem this file actually solves

The robot works in cycles. It leaves a fixed pose (the **depot**), drives over
some pollen to pick it up, comes back to that same pose, and scores. The
question this file answers is:

> Given the pollen we currently know about and the time we have left,
> **which pollen do we fetch, and in what order?**

That is a vehicle-routing problem, but three things make the textbook answer
wrong for this robot:

1. **Greedy strands things.** Grabbing the three nearest pollen this run can
   leave one isolated pollen on the far side of the field, which then costs an
   entire dedicated run of its own. So the planner scores the next
   `lookahead_runs` runs *together*, commits only to the first one, and re-solves
   at the depot once the camera has updated the map. This is a **receding
   horizon**: the future runs exist to inform the present one, not to be driven
   blind.

2. **Distance is not time.** Every leg is its own path, so the robot is at rest
   at both ends of it. This drivetrain needs roughly 34 inches just to ramp up to
   cruise and back down again, and the field is only 144 inches across — so most
   legs *never reach cruise at all*, and their duration grows like
   `sqrt(distance)`, not like `distance`. Two short hops can therefore cost more
   than one long one. A planner that minimises distance ranks tours in the wrong
   order. This one minimises **modelled time**.

3. **Turning costs.** The follower uses tangent heading, so the robot has to
   swing to face each new leg. That means a leg's cost depends on where the robot
   was coming *from*, not only on the leg's two endpoints — the cost is not a
   plain symmetric matrix, and that is why the turn penalty cannot be
   pre-tabulated.

And one non-negotiable constraint: `solve()` is called during the roughly 50 ms
the robot spends scoring at the depot. It must therefore be an **anytime
algorithm** — produce a usable answer immediately, spend whatever budget is left
improving it, and never return something worse than its own first construction.
It allocates nothing after entry and is deterministic for a given seed.

### The objective, in priority order

This is the single most important thing to understand about the file, because
almost every design decision follows from it. "Best" means, strictly in this
order:

1. **Collect more pollen.** If you rank on time alone, the optimal plan is to
   collect *nothing* — every extra pollen can only add time. The brief is "as
   much as possible before the clock runs out", so haul leads and time is only
   ever the tie-break.
2. **Collect it earlier.** Only run 0 is actually driven before the map is
   re-observed and the plan thrown away. At capacity 3, a plan that collects eight
   pollen as 3+3+2 and one that collects them as 2+3+3 score identically on key 1
   — but the second one drives a *short* first run and keeps its full runs as
   advice that will be discarded. So a full early run beats a full late one. (The
   source comment makes the same point with 3+3+3 against 2+3+4; that second
   split is not actually reachable at capacity 3, but the argument is identical.)
3. **Be faster**, measured as discounted time.

Keys 2 and 3 are deliberately kept separate. An earlier version folded the
ordering preference into the same `gamma` that discounts time, which meant that
setting `gamma = 1.0` — measurably the better setting for time — silently
switched the front-loading off.

### The solve pipeline

```text
pollen map (may be large)
      |
      v
 [step 0] filter + keep the candidate_cap cheapest-to-reach  -> Ctx.pose / Ctx.id
      |
      v
 [step 1] tabulate pairwise straight-line time and direction -> Ctx.straight / direction / degenerate
      |
      v
 [step 2] build_giant_tour     nearest-neighbour over all candidates, ignoring capacity
      |
      v
 [step 3] optimize_sequence    2-opt + Or-opt to a local optimum
      |
      v
 [step 4] construct():
            capacitated   -> split_tour (dynamic programming) -> polish_solution (cross-run moves)
            uncapacitated -> build_uncapacitated (trim to deadline, then grow back)
      |
      v
 [step 6] anytime loop: double_bridge kick -> re-optimise -> re-construct -> keep if better
      |
      v
 [step 5] fit the deadline: shed members from runs that overrun, mark what fits
      |
      v
    Plan
```

(The step numbers are the ones used in the source comments; step 5 genuinely
does run after step 6, because the deadline fit is applied once to the winner
rather than inside the loop.)

---

## 2. Includes — lines 1–6

```cpp
#include "core/logic/butine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>
```

`butine.h` brings in `pedro::Pose` and `limelight::Pollen`, plus the public
`Params`, `Kinematics`, `Run` and `Plan` types and the capacity constants.

The four standard headers are each pulled in for exactly one job:

- **`<algorithm>`** — `std::sort` (ranking candidates by reach time),
  `std::reverse` (the 2-opt move), `std::next_permutation` (exact ordering of
  short runs), `std::swap`, `std::min` / `std::max` / `std::clamp`.
- **`<chrono>`** — the wall clock. This is the only thing in the file that knows
  what time it is, and it is used solely to enforce the compute budget and to
  report how long the solve actually took. The planner deliberately owns no
  clock for *match* time; the caller passes `remaining_s`.
- **`<cmath>`** — `sqrt`, `atan2`, `fabs`, `isfinite`, and `M_PI`.
- **`<limits>`** — only to build `INF`.

Note what is **not** included: no `<vector>` for internal use, no `<memory>`, no
containers. Everything the solver touches is a fixed-size stack array. That is a
hard requirement, not a style preference — a heap allocation inside a 50 ms
budget on an Android device can stall for milliseconds with no warning.

---

## 3. The anonymous namespace — line 8 — and the `using` block — lines 9–13

```cpp
namespace {
    using butine::Kinematics;
    using butine::MAX_CANDIDATES;
    using butine::MAX_RUNS;
    using butine::MAX_RUN_LEN;
    using butine::EXACT_ORDER_LIMIT;
```

Everything from line 8 to line 698 lives in an **anonymous namespace**. That
gives every helper in this file internal linkage: none of it is visible to any
other translation unit, none of it can collide with a same-named symbol
elsewhere, and the compiler is free to inline and discard aggressively because it
can prove nothing outside this file calls it. The public surface of the whole
planner is the four functions declared in `butine.h` and nothing else.

The five `using` declarations pull the shared vocabulary in from the header so
the body can say `MAX_CANDIDATES` instead of `butine::MAX_CANDIDATES` on every
line. What they refer to:

- **`Kinematics`** — the motion model struct: cruise speed, acceleration,
  deceleration, turn rate, dwell time. Passed in by the caller so it can be
  calibrated without touching this file.
- **`MAX_CANDIDATES` (20)** — the largest number of pollen a single solve will
  ever reason about. Every table in the file is sized from this.
- **`MAX_RUNS` (6)** — the most runs a plan can hold.
- **`MAX_RUN_LEN`** — the most pollen one run can hold. It equals
  `MAX_CANDIDATES`, because in the uncapacitated variant a single run legitimately
  holds everything.
- **`EXACT_ORDER_LIMIT` (5)** — runs no longer than this get their internal order
  solved *exactly*, by trying every permutation. Above it, the heuristic takes
  over. See `optimize_run`.

---

## 4. File-local constants — lines 15–20

```cpp
    constexpr double INF = std::numeric_limits<double>::infinity();
    constexpr double EPSILON = 1e-9;
    // Below this a leg is a no-op: two pollen at the same spot, or a member sitting
    // on the depot. Turning to face a zero-length leg is meaningless, so such legs
    // carry the incoming heading straight through.
    constexpr double DEGENERATE_LEG_IN = 1e-3;
```

**`INF`** is the "nothing found yet" sentinel. Every search in this file that
looks for a minimum starts its incumbent at `INF`, so the first real candidate
always wins on the first comparison and no separate "is this the first one?" flag
is needed.

**`EPSILON`** is the float-comparison slack, and it is doing real work. The local
searches in this file loop *until nothing improves*. If an "improvement" of
1e-15 — pure rounding noise from re-adding the same legs in a different order —
were accepted, the search could accept move A, then accept move B that undoes A,
and loop forever inside the compute budget. Every acceptance test in the file is
therefore written `candidate < best - EPSILON`, meaning *strictly and
meaningfully* better.

**`DEGENERATE_LEG_IN`** (one thousandth of an inch) is the threshold below which
a leg is treated as not existing at all. This happens for real: two pollen
detected at the same spot, or a pollen sitting on top of the depot. The reason it
needs handling is the heading. The travel direction of a leg is `atan2(dy, dx)`,
and for a zero-length leg that is meaningless — it would return an arbitrary
angle, the planner would charge the robot for a turn onto it, and then charge it
again to turn back. So a degenerate leg costs zero time and passes the incoming
heading straight through, as if it never happened.

---

## 5. `wrap_pi` — lines 22–26

```cpp
    double wrap_pi(double angle) {
        while (angle > M_PI) angle -= 2.0 * M_PI;
        while (angle < -M_PI) angle += 2.0 * M_PI;
        return angle;
    }
```

**Summary:** folds any angle into the range (−pi, +pi].

**Why it exists:** the file constantly asks "how far does the robot have to swing
to get from heading A to heading B?", and computes it as `A - B`. Raw subtraction
of two headings can come out as, say, 350 degrees — but the robot would never
turn 350 degrees one way; it would turn 10 degrees the other way. Feeding the
unwrapped number into the turn-time model would charge the robot 35 times too
much for that turn and make the planner avoid perfectly good routes.

Wrapping first means `fabs(wrap_pi(A - B))` is always the *shortest* rotation
between the two headings, which is what the robot will actually perform.

The `while` loops rather than a modulo operation are deliberate: the inputs are
always already close to the range (they are differences of two `atan2` results,
so at most 2 pi out), so each loop runs at most once and is cheaper and more
predictable than `fmod`.

---

## 6. `struct Rng` — lines 28–40

```cpp
    // Deterministic and cheap. rand() is neither, and a planner whose answer moves
    // around between identical inputs cannot be debugged.
    struct Rng {
        std::uint32_t state;
        explicit Rng(std::uint32_t seed) : state(seed ? seed : 0x9E3779B9u) {}
        std::uint32_t next() {
            state ^= state << 13;
            state ^= state >> 17;
            state ^= state << 5;
            return state;
        }
        int below(int bound) { return bound <= 0 ? 0 : static_cast<int>(next() % static_cast<std::uint32_t>(bound)); }
    };
```

**Summary:** a 32-bit xorshift pseudo-random generator, four lines long, with no
global state.

**Why not `rand()`:** two reasons, and both are about being able to debug this
thing.

1. **Determinism.** `rand()` draws from process-global state that anything else
   in the program can advance. A planner that returns a different route every
   time you run it on identical inputs cannot be reasoned about — you can never
   tell whether the route changed because you changed the map or because the dice
   fell differently. This generator's entire state is one `uint32_t` owned by the
   solve, seeded from `params.seed`. Same inputs, same seed, same plan, every
   time, forever.
2. **Cost.** This is three shifts and three exclusive-ors. It is called inside
   the anytime loop, where the budget is measured in milliseconds.

### The members

**`state`** — the whole generator. One 32-bit word.

**The constructor** takes a seed and stores it, *except* that a seed of zero is
replaced with `0x9E3779B9`. Zero is the one value an xorshift generator cannot
recover from: shifting and exclusive-or-ing zero gives zero forever, so a zero
seed would produce an infinite stream of zeros and the anytime loop would try the
identical "random" kick every single iteration. `0x9E3779B9` is 2^32 divided by
the golden ratio — the conventional choice for this kind of fallback because its
bits are well mixed. It is also the default value of `Params::seed`.

**`next()`** — the xorshift step itself. The shift triple 13/17/5 is the classic
Marsaglia set for 32-bit xorshift; it gives a full period of 2^32 − 1 and passes
the basic randomness tests, which is far more than a route-kicker needs.

**`below(bound)`** — returns a value in `[0, bound)`, which is what every caller
actually wants (an array index). The `bound <= 0 ? 0` guard matters because the
callers pass expressions like `length - 3`, which really can go non-positive on a
tiny tour; without the guard that would be a modulo by zero.

There is a very small modulo bias here (values below 2^32 mod `bound` are a hair
more likely). For choosing which segment of a 20-element tour to shuffle, that is
irrelevant.

---

## 7. `struct Ctx` — lines 42–63

```cpp
    // ------------------------------------------------------------------------
    // Everything the solver needs about this problem instance. Node 0 is the
    // depot; candidate c is node c + 1.
    // ------------------------------------------------------------------------
    struct Ctx {
        pedro::Pose depot;
        pedro::Pose pose[MAX_CANDIDATES];
        int         id[MAX_CANDIDATES];
        int         count;                                   // K
        Kinematics  k;
        // Straight-line time and travel direction between every pair of nodes.
        // Only these are tabulated: the turn penalty depends on where the robot
        // came from, so it cannot live in a pairwise table and is added while
        // walking a sequence instead.
        double      straight[MAX_CANDIDATES + 1][MAX_CANDIDATES + 1];
        double      direction[MAX_CANDIDATES + 1][MAX_CANDIDATES + 1];
        bool        degenerate[MAX_CANDIDATES + 1][MAX_CANDIDATES + 1];

        const pedro::Pose& node(int index) const {
            return index == 0 ? depot : pose[index - 1];
        }
    };
```

**Summary:** everything the solver needs to know about *this* problem instance,
packed into one stack object that is built once at the top of `solve()` and then
passed by const reference to every helper. Nothing in the solver reads global
state; if it is not in `Ctx`, it is not an input.

### The node numbering convention

This is the single convention you must hold in your head to read the rest of the
file:

> **Node 0 is the depot. Candidate `c` is node `c + 1`.**

That is why you see `+ 1` scattered through the code (`members[m] + 1`, `c + 1`)
and why the lookup tables are sized `MAX_CANDIDATES + 1`. The extra slot is the
depot's row and column. Candidate indices (`0..K-1`) and node indices (`0..K`)
are two different numbering systems, and mixing them up is the easiest bug to
write in this file.

### The members

**`depot`** — the pose the robot leaves from and returns to. Its `heading`
matters as much as its position: the robot must be facing the right way to score,
so every run pays for the final swing back onto this heading.

**`pose[MAX_CANDIDATES]`** — the field position of each candidate pollen, indexed
by candidate index. A copy, not a pointer into the map: the map may be mutated by
the vision thread while the solve is running.

**`id[MAX_CANDIDATES]`** — the `limelight::Pollen::id` of each candidate, parallel
to `pose`. The solver works entirely in dense candidate indices `0..K-1` because
that lets it use small fixed arrays; this array is the translation back to the
ids the rest of the robot uses, and it is only read at the very end of `solve()`
when the `Plan` is filled in.

**`count`** — how many candidates there actually are, `K`. Everything else in the
struct is sized for the worst case and only the first `count` entries are live.

**`k`** — the `Kinematics` for this solve. Lives inside `Ctx` so cost functions
need only one parameter instead of two.

### The three lookup tables

**`straight[i][j]`** — how many seconds it takes to drive the straight leg from
node `i` to node `j`, starting and ending at rest, *ignoring* any turn.

**`direction[i][j]`** — the compass heading of that leg, in radians, that is,
`atan2(dy, dx)`.

**`degenerate[i][j]`** — whether that pair of nodes is closer together than
`DEGENERATE_LEG_IN`, so the leg should be skipped entirely.

These are tabulated because the inner loops of the local searches evaluate the
same legs thousands of times, and each entry costs a `sqrt` and an `atan2` to
compute. Filling the table is `O(K^2)` once — at most 441 entries — and every
subsequent evaluation is a memory read.

**Why the turn penalty is _not_ in the table.** This is the crucial asymmetry.
The cost of arriving at `j` from `i` depends on the heading the robot was already
travelling at, which depends on where it came from *before* `i`. That is a
property of a three-node sequence, not of a pair, so it cannot live in a pairwise
table. It is computed on the fly while walking a sequence, in `leg_cost`.

**`node(index)`** — the one accessor that implements the numbering convention:
index 0 gives the depot, anything else gives `pose[index - 1]`. Having it in one
place means the `- 1` appears once rather than everywhere.

---

## 8. `leg_cost` — lines 65–83

```cpp
    double leg_cost(double incoming_heading, int from, int to, const Ctx& ctx, double* out_heading) {
        if (ctx.degenerate[from][to]) {
            if (out_heading) *out_heading = incoming_heading;
            return 0.0;
        }

        const double direction = ctx.direction[from][to];
        if (out_heading) *out_heading = direction;

        double seconds = ctx.straight[from][to];
        const double swing = std::fabs(wrap_pi(direction - incoming_heading));
        if (swing > ctx.k.turn_deadband_rad && ctx.k.omega_max_rad_s > EPSILON) {
            const double rotate = swing / ctx.k.omega_max_rad_s + ctx.k.turn_settle_s;
            // Holonomic: the swing happens while driving, and only costs extra once
            // it outlasts the drive itself.
            seconds += ctx.k.turn_coupling * std::max(0.0, rotate - seconds);
        }
        return seconds;
    }
```

**Summary:** the cost of one leg, in seconds, including the turn needed to get
onto it. This is the atom the entire planner is built from — every route cost in
the file is a sum of `leg_cost` calls.

**Parameters**

- **`incoming_heading`** — the direction the robot is *already* travelling when
  it reaches the start of this leg. This is what makes the cost
  sequence-dependent.
- **`from`, `to`** — node indices (remember: 0 is the depot).
- **`ctx`** — for the tables and the kinematics.
- **`out_heading`** — optional output. If given, it receives the heading the robot
  ends up travelling at, which the caller feeds into the next call as that leg's
  `incoming_heading`. Passing `nullptr` means "I only want the cost, I am not
  continuing a walk" — `build_giant_tour` does that when merely comparing
  candidates.

**The degenerate case (lines 66–69).** A zero-length leg costs nothing and leaves
the heading untouched. The heading passthrough is the important half: if this
returned some arbitrary direction instead, the next leg would be charged for a
turn away from an angle the robot was never actually pointing at.

**The straight part (lines 71–74).** The table lookup gives the drive time and
the travel direction. `out_heading` is set to the travel direction because this
robot uses tangent heading — it points along the direction of travel.

**The turn part (lines 75–80).** Three things happen here:

- `swing` is the shortest rotation from the heading the robot has to the heading
  this leg needs, via `wrap_pi` as described above.
- The turn is only charged if it exceeds `turn_deadband_rad` (about 5 degrees).
  Below that the follower absorbs the correction while driving, and charging for
  it would just add noise to every cost in the problem.
- `rotate` is how long the swing takes: the sweep at maximum angular velocity,
  plus `turn_settle_s` for the heading controller to stop ringing.

**The holonomic coupling (line 79)** is the subtle line:

```text
seconds += turn_coupling * max(0.0, rotate - seconds);
```

A mecanum drivetrain can translate and rotate at the same time, so the robot does
not stop to turn — it spins *while* driving down the leg. The turn therefore
costs nothing extra until it takes longer than the drive itself, at which point
the leg is gated by the rotation rather than by the translation, and only the
difference gets added.

`turn_coupling` scales that: at `1.0` (the default) a turn is free until it
becomes the binding constraint, which is the physically correct model for
mecanum. At `0.0` turning is ignored entirely, which collapses the whole problem
into a plain symmetric TSP — useful as an A/B test to see how much the turn model
is actually buying.

---
## 9. `struct Walk` — lines 85–91

```cpp
    // How far along a sequence a walk has got: what it has cost, the heading it
    // left the robot at, and the node it is standing on.
    struct Walk {
        double spent;
        double heading;
        int    at;
    };
```

**Summary:** a bookmark. It records how far along a route the robot has got: what
that has cost so far, which way it is pointing, and where it is standing.

**Why it exists:** because of the turn model. If leg cost depended only on the
two endpoints, you could price any part of a route in isolation. It does not — it
depends on the incoming heading — so to resume pricing a route halfway through
you need *three* numbers, not one. `Walk` is that triple, given a name so it can
be passed around as a unit.

**The members**

- **`spent`** — seconds accumulated from the depot up to this point, pickup dwell
  included.
- **`heading`** — the direction the robot is travelling at this point. This is the
  member the whole struct exists for.
- **`at`** — the node index the robot is standing on (again, 0 is the depot).

This is what makes the "partial re-evaluation" optimisation in `optimize_sequence`
possible: a move that does not disturb the first `p` members of a route can be
priced starting from the `Walk` at position `p`, instead of re-walking the route
from the depot every time.

---

## 10. `sequence_time_from` — lines 93–116

```cpp
    // depot -> members[p..] -> depot, plus the final swing back onto the depot
    // heading (the robot has to be pointing the right way to score). Travel only;
    // the caller adds score_overhead_s. `from` must already cover members[0..p-1],
    // and is read for the node the walk resumes at -- members[p - 1] is not
    // touched, so the caller may hand in an array whose head is stale.
    double sequence_time_from(const int* members, int length, int p, Walk from, const Ctx& ctx) {
        double heading = from.heading;
        int previous = from.at;
        double total = from.spent;

        for (int m = p; m < length; ++m) {
            const int node = members[m] + 1;
            total += leg_cost(heading, previous, node, ctx, &heading);
            total += ctx.k.pickup_dwell_s;
            previous = node;
        }
        total += leg_cost(heading, previous, 0, ctx, &heading);

        const double settle = std::fabs(wrap_pi(ctx.depot.heading - heading));
        if (settle > ctx.k.turn_deadband_rad && ctx.k.omega_max_rad_s > EPSILON) {
            total += settle / ctx.k.omega_max_rad_s + ctx.k.turn_settle_s;
        }
        return total;
    }
```

**Summary:** the total time of the closed route
`depot -> members[p] -> members[p+1] -> ... -> members[length-1] -> depot`,
resumed from a bookmark that already accounts for everything before `p`.

This is the workhorse of the file. Practically every cost number the planner ever
compares comes out of here.

**Parameters**

- **`members`** — the route, as *candidate* indices (not node indices; the `+ 1`
  happens inside).
- **`length`** — how many members the route has in total.
- **`p`** — where to resume. `0` means "walk the whole thing from the depot".
- **`from`** — the `Walk` bookmark covering `members[0..p-1]`.
- **`ctx`** — tables and kinematics.

**Lines 99–101** unpack the bookmark into the three running variables: the
heading carried in, the node the robot is standing on, and the cost so far.

**Lines 103–108, the main loop.** For each remaining member: translate the
candidate index into a node index (`members[m] + 1`), charge the leg from where
the robot is to that node, and charge `pickup_dwell_s` — the time the robot
stands still to actually take the pollen in. Note that `leg_cost` writes the new
heading back into `heading` through the out-parameter, so the next iteration
charges the correct turn. `previous` then moves to the node just visited.

**Line 109, the return leg.** Every run is closed: after the last pollen the
robot drives back to node 0, the depot. This is charged exactly like any other
leg, turn included.

**Lines 111–114, the final settle.** Arriving at the depot is not enough — the
robot has to be *pointing the right way* to score. So the last thing charged is
the swing from whatever heading the return leg left it at onto `depot.heading`.
Same deadband and same angular-velocity model as any other turn, but with no
holonomic coupling subtracted: the robot is stationary at the depot by then, so
there is no drive for the rotation to hide inside.

**The stale-head contract (documented at lines 95–97).** This is the one piece of
subtlety in the function, and callers depend on it:

> `from` must already cover `members[0..p-1]`, and is read for the node the walk
> resumes at — `members[p - 1]` is not touched, so the caller may hand in an array
> whose head is stale.

Because the walk resumes from the bookmark's `at` field rather than by reading
`members[p - 1]`, the array's first `p` entries are never read at all. That is
what lets `optimize_sequence` build a trial route into a scratch buffer and only
fill in the part that actually changed, leaving whatever garbage the previous
trial left in the head of that buffer. It saves a copy on every one of the tens
of thousands of trial moves.

**What is deliberately not included:** `score_overhead_s`. This function returns
*travel* time only. The fixed cost of being at the depot is added by whoever is
building an objective, because it is charged once per run rather than once per
route evaluation.

---

## 11. `sequence_time` — lines 118–122

```cpp
    // The same walk, from the depot over the whole sequence.
    double sequence_time(const int* members, int length, const Ctx& ctx) {
        if (length <= 0) return 0.0;
        return sequence_time_from(members, length, 0, Walk{0.0, ctx.depot.heading, 0}, ctx);
    }
```

**Summary:** the convenience wrapper — price a whole route from the depot.

It supplies the only bookmark that is always valid without having computed
anything: zero seconds spent, the robot pointing along `depot.heading`, standing
on node 0. The `length <= 0` guard returns zero rather than charging a depot to
depot leg plus a settle, because an empty run is not a run — it is something the
planner decided not to drive, and it must cost nothing or the comparisons that
choose between plans would be distorted.

---

## 12. `struct Prefix` — lines 124–141

```cpp
    // Every prefix of a sequence, so a move that leaves members[0..p-1] alone can be
    // priced from p rather than from the depot. Or-opt is where nearly all of the
    // solve goes, and the average relocation it tries leaves the front third of the
    // tour alone. Rebuilt whenever a move is accepted -- improving moves are rare
    // beside the moves merely tried, so the rebuild costs far less than it saves.
    struct Prefix {
        Walk step[MAX_RUN_LEN + 1];

        void build(const int* members, int length, const Ctx& ctx) {
            step[0] = Walk{0.0, ctx.depot.heading, 0};
            for (int m = 0; m < length; ++m) {
                const int node = members[m] + 1;
                double heading = step[m].heading;
                const double leg = leg_cost(step[m].heading, step[m].at, node, ctx, &heading);
                step[m + 1] = Walk{step[m].spent + leg + ctx.k.pickup_dwell_s, heading, node};
            }
        }
    };
```

**Summary:** every bookmark for a route at once — `step[p]` is the `Walk` that
covers `members[0..p-1]`.

**Why it exists — the performance argument.** `optimize_sequence` is where nearly
all of the solve time goes, and it works by trying an enormous number of small
modifications to a route and keeping the ones that help. The natural way to price
a trial is to re-walk it from the depot, which is `O(length)` per trial.

But almost every move it tries leaves the *front* of the route completely
untouched: reversing the stretch `[i, j]` does not disturb anything before `i`,
and relocating a segment does not disturb anything before the earlier of the two
splice points. With a table of prefix bookmarks, such a trial can be priced from
that point onward instead of from the depot. On average the moves tried leave the
front third of the route alone, so this is roughly a third off the cost of the
hottest loop in the planner.

**`step[MAX_RUN_LEN + 1]`** — one more than the maximum length, because there are
`length + 1` prefixes of a route of `length` members (the empty one included).

**`build`** — fills the table in one forward pass. `step[0]` is the depot
bookmark. Each subsequent entry extends the previous one by one leg plus one
pickup dwell, carrying the heading forward exactly as `sequence_time_from` would.
By construction `Prefix::build` and `sequence_time_from` agree; if they ever
disagreed, the optimiser would accept moves that are not actually improvements.

**When it is rebuilt.** Only when a move is *accepted*. That is the whole trick:
accepted moves are rare compared to the moves merely tried (hundreds against tens
of thousands), so paying `O(length)` per acceptance to save a fraction of
`O(length)` on every trial is an easy trade.

---

## 13. `optimize_sequence` — lines 143–216

```cpp
    // 2-opt (segment reversal) plus Or-opt (relocate a 1..3 long segment, either
    // orientation), looped to a local optimum. Reorders `members` in place.
    double optimize_sequence(int* members, int length, const Ctx& ctx) {
        double best = sequence_time(members, length, ctx);
        if (length < 3) return best;
```

**Summary:** takes a route and reorders it *in place* into a locally optimal one,
returning its time. "Locally optimal" here means: no single 2-opt move and no
single Or-opt move can improve it any further.

This is the generic route improver. It is used for the giant tour over all
candidates, for long runs that are too big for the exact permutation search, and
for the single tour in the uncapacitated variant.

**Line 146** prices the route as given, so that the function can never return a
worse answer than it was handed.

**Line 147** bails out for routes of fewer than three members, returning the cost
as given. With one member there is only one order. With two, `A -> B` and
`B -> A` really are different under this asymmetric cost model, and the guard
means this function will not try that swap — but it is not a loss in practice,
because runs that short are ordered by `optimize_run`'s exact permutation search
instead, and a two-candidate giant tour is a degenerate case where the difference
is a single turn.

### Working storage and the convergence loop — lines 148–158

```cpp

        int scratch[MAX_RUN_LEN];
        int segment[3];
        Prefix head;
        head.build(members, length, ctx);

        bool improved = true;
        int guard = 0;
        while (improved && guard++ < 32) {
            improved = false;

```

- **`scratch`** — the buffer a trial Or-opt route is assembled in before it is
  priced. Only ever partially written, per the stale-head contract above.
- **`segment`** — the 1 to 3 members currently being relocated.
- **`head`** — the prefix bookmark table, built once for the incoming route and
  rebuilt on every acceptance.
- **`improved`** — did this pass find anything? The passes repeat while it keeps
  coming back true, which is what drives the search to a local optimum.
- **`guard`** — a hard ceiling of 32 passes. In practice the search converges in a
  handful, but this is safety-critical code running against a wall clock: a
  cost-model quirk that made two moves undo each other would otherwise spin until
  the budget ran out. The guard turns "hangs forever" into "returns a slightly
  worse route", which is the correct failure mode for a robot.

### Pass 1 — 2-opt, lines 159–173

```cpp
            for (int i = 0; i < length - 1; ++i) {
                for (int j = i + 1; j < length; ++j) {
                    std::reverse(members + i, members + j + 1);
                    // Reversing [i, j] leaves everything before i where it was.
                    const double candidate =
                            sequence_time_from(members, length, i, head.step[i], ctx);
                    if (candidate < best - EPSILON) {
                        best = candidate;
                        improved = true;
                        head.build(members, length, ctx);
                    } else {
                        std::reverse(members + i, members + j + 1);
                    }
                }
            }
```

**What the move is:** reverse the contiguous stretch of the route between
positions `i` and `j`. In a classic symmetric TSP this is exactly the move that
removes a crossing in the path — if two legs cross, reversing the stretch between
them uncrosses them and the route gets shorter.

**How it is applied here:** optimistically. Line 161 performs the reversal
*first*, prices the result, and then either keeps it (lines 165–168) or undoes it
by reversing the same stretch back (line 170). Reversing twice is its own inverse,
so no copy of the original route is needed at all.

**The partial pricing (lines 163–164).** Because the reversal touches nothing
before position `i`, the cost is computed with `sequence_time_from` resuming from
`head.step[i]`.

**On acceptance (lines 166–168)** the new best time is recorded, the pass is
marked as having improved, and the prefix table is rebuilt against the route as it
now stands — necessary because every bookmark from `i` onward is now wrong.

**A note on correctness under the turn model.** In a symmetric problem, reversing
a stretch leaves the cost of the legs *inside* that stretch unchanged and only
alters the two legs at its ends. Here, reversal flips the travel direction of
every leg inside the stretch, so the turn penalties change too. That is exactly
why the trial is priced by re-walking from `i` rather than by the usual O(1)
delta-evaluation you would find in a textbook 2-opt.

### Pass 2 — Or-opt, lines 175–213

```cpp

            for (int span = 1; span <= 3 && span < length; ++span) {
                for (int start = 0; start + span <= length; ++start) {
                    for (int flip = 0; flip < 2; ++flip) {
                        for (int insert = 0; insert + span <= length; ++insert) {
                            if (insert == start && flip == 0) continue;

                            for (int s = 0; s < span; ++s) segment[s] = members[start + s];
                            if (flip) std::reverse(segment, segment + span);
```

**What the move is:** lift a short run of consecutive members out of the route and
splice it back in somewhere else, optionally reversed. Where 2-opt fixes crossings,
Or-opt fixes "this pollen is on the wrong side of the tour" — it moves a member or
a small cluster to a better place without disturbing the rest of the order.

**The four nested loops** enumerate the move:

- **`span`** — how many consecutive members to move: 1, 2 or 3. Longer relocations
  are rarely useful and cost more to enumerate.
- **`start`** — where that segment currently begins.
- **`flip`** — whether to reverse the segment when re-inserting it. Under an
  asymmetric cost model this genuinely matters: entering a two-pollen cluster from
  the other end changes both the approach turn and the exit turn.
- **`insert`** — the position in the shortened route where the segment goes back.

**Line 179** skips the no-op: putting the segment back exactly where it came from,
unreversed, is the route you already have.

**Lines 181–182** copy the segment out into `segment` and reverse it if this trial
is the flipped variant.

```cpp

                            // Nothing below the earlier of the two splice points
                            // moves, so scratch is only rewritten from there and
                            // its head stays whatever the last trial left in it.
                            // That is why the walk resumes off `head`, which
                            // describes `members`, and never reads scratch[settled - 1].
                            const int settled = std::min(start, insert);
                            int write = settled;
                            for (int r = settled; r < length; ++r) {
                                if (r >= start && r < start + span) continue;
                                if (write == insert) {
                                    for (int s = 0; s < span; ++s) scratch[write++] = segment[s];
                                }
                                scratch[write++] = members[r];
                            }
                            while (write < length) {
                                for (int s = 0; s < span && write < length; ++s) scratch[write++] = segment[s];
                            }
```

**Rebuilding the trial route.** `settled` is the earlier of the two splice points:
everything before it is unaffected by this move, no matter which direction the
segment travels. So the writing starts at `settled`, and the head of `scratch` is
left holding whatever the previous trial wrote there. That is safe *only* because
of the stale-head contract on `sequence_time_from` — it resumes from the bookmark
rather than reading `scratch[settled - 1]`. The comment at lines 184–188 exists
because this is precisely the kind of thing a later reader would "fix" by adding a
full copy, silently making the hottest loop in the planner 30 percent slower.

**The copy loop (lines 191–197)** walks the original route from `settled`,
skipping the members that belong to the segment being moved, and drops the whole
segment in when the write cursor reaches `insert`.

**Lines 198–200, the tail case.** If `insert` is at or past the end of the
shortened route, the write cursor never reaches it during the loop, so the segment
has not been placed. This loop appends it. The `write < length` condition in both
the `while` and the inner `for` is what keeps the write inside the buffer.

```cpp

                            const double candidate =
                                    sequence_time_from(scratch, length, settled, head.step[settled], ctx);
                            if (candidate < best - EPSILON) {
                                best = candidate;
                                for (int r = settled; r < length; ++r) members[r] = scratch[r];
                                improved = true;
                                head.build(members, length, ctx);
                            }
                        }
                    }
                }
            }
        }
        return best;
    }
```

**Pricing and acceptance (lines 202–209).** Same shape as the 2-opt pass: price
from `settled` using the bookmark, and on a strict improvement copy the changed
tail of `scratch` back over `members`, mark the pass as improved, and rebuild the
prefix table. Note that the copy back is also only from `settled` onward — the head
of `members` was never modified.

Unlike the 2-opt pass there is no undo step, because the trial was built in a
separate buffer and `members` was never touched speculatively.

**Line 215** returns the best time found, with `members` left holding the route
that achieves it.

**Complexity.** Per pass, 2-opt is `O(length^2)` trials and Or-opt is
`O(3 * 2 * length^2)` trials, each priced in `O(length)` in the worst case and
rather less on average thanks to the prefix table. For the sizes this planner
works at (up to 20), that is a few tens of thousands of cheap operations per pass
— which is exactly why the whole thing is bounded by a candidate cap and a wall
clock rather than by asymptotics.

---

## 14. `optimize_run` — lines 218–242

```cpp
    // The within-run ordering. Short runs are solved EXACTLY -- at the default
    // capacity of 3 that is six orderings, so only the assignment of pollen to
    // runs is ever approximate.
    double optimize_run(int* members, int length, const Ctx& ctx) {
        if (length <= 0) return 0.0;
        if (length == 1) return sequence_time(members, length, ctx);
        if (length > EXACT_ORDER_LIMIT) return optimize_sequence(members, length, ctx);
```

**Summary:** order the members *within one run* as cheaply as possible, and return
that cost. This is the function `split_tour` and `polish_solution` call thousands
of times, and it is the reason the rest of the planner can treat a run as a plain
*set* of pollen rather than as an ordered list.

**The three fast exits**

- **Empty run (line 222)** — costs nothing. Not "the cost of driving to the depot
  and back", which would be zero anyway, but explicitly nothing, so that a plan
  with fewer runs is not penalised for the runs it declined to make.
- **One member (line 223)** — there is only one possible order, so just price it.
- **Longer than `EXACT_ORDER_LIMIT` (line 224)** — hand it to the heuristic
  `optimize_sequence`. Above five members the factorial search stops being free.

### The exact branch — lines 225–242

```cpp

        int scratch[MAX_RUN_LEN];
        int best_order[MAX_RUN_LEN];
        for (int i = 0; i < length; ++i) scratch[i] = members[i];
        std::sort(scratch, scratch + length);

        double best = INF;
        do {
            const double candidate = sequence_time(scratch, length, ctx);
            if (candidate < best) {
                best = candidate;
                for (int i = 0; i < length; ++i) best_order[i] = scratch[i];
            }
        } while (std::next_permutation(scratch, scratch + length));

        for (int i = 0; i < length; ++i) members[i] = best_order[i];
        return best;
    }
```

**Summary of the branch:** try *every* ordering of the run and keep the best one.

**Why this is affordable.** `std::next_permutation` enumerates orderings in
lexicographic order, which requires the sequence to *start* sorted — hence the
copy into `scratch` and the `std::sort` on line 229. At the default capacity of
3 that is 3! = 6 orderings; at the limit of 5 it is 120. Each is priced by a
single `sequence_time` call. That is nothing compared to the cost of the search
that surrounds it.

**Why it matters so much.** Because the within-run order is solved *exactly*, a
run is completely characterised by which pollen are in it — the order is a
derived property, not a decision. That is what lets `polish_solution` treat
"relocate a pollen from run A to run B" as a pure set operation and simply append
the pollen to run B without searching for the best insertion position: whatever
position is best, `optimize_run` will find it. In this planner, **only the
assignment of pollen to runs is ever approximate**; the driving order inside each
run is optimal.

**`best_order`** holds the winning permutation, because `scratch` keeps being
advanced past it by the enumeration. Line 240 copies the winner back into the
caller's array, so the function's contract is "I reorder your run in place and
tell you what it costs".

---
## 15. `struct Solution` — lines 244–254

```cpp
    struct Solution {
        int    members[MAX_RUNS][MAX_RUN_LEN];
        int    length[MAX_RUNS];
        double seconds[MAX_RUNS];
        int    count;            // runs
        int    horizon;          // runs the front_load weights were built for
        int    collected;        // pollen across all of them
        double front_load;       // how much of that haul lands in the EARLY runs
        double objective;        // J, the discounted time
        bool   valid;
    };
```

**Summary:** a complete multi-run plan as the *solver* sees it — in candidate
indices, with its score already computed. This is the internal working type; the
public `Plan` is built from it at the very end of `solve()`.

**The members**

- **`members[MAX_RUNS][MAX_RUN_LEN]`** — run `r` visits candidates
  `members[r][0..length[r]-1]`, in that driving order. Candidate indices, not
  pollen ids; the translation happens once at the end.
- **`length[MAX_RUNS]`** — how many pollen each run holds.
- **`seconds[MAX_RUNS]`** — each run's *travel* time, depot to depot, dwell and
  final settle included but `score_overhead_s` excluded.
- **`count`** — how many runs the plan actually uses. Can be fewer than the
  horizon: the planner is free to decide that two runs beat three.
- **`horizon`** — **the run count the `front_load` weights were built against.**
  This is not the same as `count`, and the distinction is load-bearing. See
  `front_load_weight` below and the discussion in `score_solution`.
- **`collected`** — total pollen across all runs. The primary ranking key.
- **`front_load`** — a single number encoding *how early in the plan* the haul
  lands. The secondary ranking key.
- **`objective`** — `J`, the discounted total time, overhead included. The
  tertiary ranking key.
- **`valid`** — whether this is a real plan at all. A fresh `Solution{}` is
  invalid, so "we have nothing yet" needs no separate flag.

---

## 16. `front_load_weight` — lines 256–265

```cpp
    // Weight of a pollen sitting in run `r` of `runs`, for the front-load term.
    // Base is capacity + 1 so that one extra pollen in run r always outweighs a
    // full run r+1 -- which makes the comparison an exact lexicographic ordering
    // on (count in run 0, count in run 1, ...), with no tuning constant.
    double front_load_weight(int r, int runs, int capacity) {
        double weight = 1.0;
        const double base = static_cast<double>(std::max(capacity, 1) + 1);
        for (int i = r + 1; i < runs; ++i) weight *= base;
        return weight;
    }
```

**Summary:** how much one pollen sitting in run `r` contributes to the
`front_load` score, given a plan of `runs` runs and a per-run `capacity`.

**What it computes:** `(capacity + 1)` raised to the power `(runs - 1 - r)`. Run 0
gets the largest weight, each later run gets a weight smaller by a factor of
`capacity + 1`, and the last run in the horizon gets weight 1.

**Why the base is `capacity + 1`, and why that is not a tuning constant.** This is
positional notation. Treat `(count in run 0, count in run 1, ..., count in run
n-1)` as the digits of a number written in base `capacity + 1`. Because no run can
hold more than `capacity` pollen, every digit is a legal digit in that base — so
comparing the resulting numbers is *exactly* a lexicographic comparison of the
digit tuples.

Concretely, with `capacity = 3` the base is 4, and:

| Plan | digits | front_load |
|---|---|---|
| 3 + 3 + 3 | (3,3,3) | 3·16 + 3·4 + 3 = 63 |
| 3 + 3 + 2 | (3,3,2) | 3·16 + 3·4 + 2 = 62 |
| 3 + 2 + 3 | (3,2,3) | 3·16 + 2·4 + 3 = 59 |
| 2 + 3 + 3 | (2,3,3) | 2·16 + 3·4 + 3 = 47 |

One extra pollen in run 0 is worth 16, which beats *anything* the later runs can
add (3·4 + 3 = 15 at most) — so 3+3+2 outranks 2+3+3 even though both collect
eight. The ordering "fill run 0 first, then run 1, then run 2" falls out of the
arithmetic with no weighting factor to tune and no risk of a hand-picked constant
being wrong at some other capacity.

**Why it is computed with a loop rather than `pow`.** The exponent is at most 5,
so this is a handful of multiplications, and it avoids pulling a transcendental
function into an inner loop for a result that must be exactly an integer.

---

## 17. `score_solution` — lines 267–284

```cpp
    // `horizon` is the run count the weights are built against, and must be the
    // same for every solution being compared -- it is the planning horizon, not
    // however many runs this particular solution happened to use. Scoring a
    // two-run plan on a base of two and a three-run plan on a base of three puts
    // them on different scales, and the three-run one then wins ties it should
    // lose (more depot returns for the same pollen).
    void score_solution(Solution& solution, const Ctx& ctx, const double* gamma,
                        double overhead, int capacity, int horizon) {
        solution.horizon = horizon;
        solution.objective = 0.0;
        solution.collected = 0;
        solution.front_load = 0.0;
        for (int r = 0; r < solution.count; ++r) {
            solution.objective += gamma[r] * (solution.seconds[r] + overhead);
            solution.collected += solution.length[r];
            solution.front_load += front_load_weight(r, horizon, capacity) * solution.length[r];
        }
    }
```

**Summary:** compute all three ranking keys for a finished plan.

**Parameters**

- **`solution`** — mutated in place; this is where the keys land.
- **`ctx`** — present for symmetry with the rest of the API.
- **`gamma`** — the per-run discount factors, `gamma[r] = discount^r`.
- **`overhead`** — `score_overhead_s`, charged once per run.
- **`capacity`** — needed for the base of the front-load weights.
- **`horizon`** — the run count the weights are built against.

**The loop (lines 279–283)** accumulates, per run:

- **`objective += gamma[r] * (seconds[r] + overhead)`** — the run's own travel time
  plus the fixed cost of coming back and scoring, discounted by how far into the
  future the run is. This is the `J` referred to in the header.
- **`collected += length[r]`** — total haul.
- **`front_load += front_load_weight(r, horizon, capacity) * length[r]`** — the
  positional-notation score described above.

**The `horizon` argument is the subtle part**, and the comment at lines 267–272
explains why. The weights must be built against the *planning horizon* — the run
count every solution being compared is scored on — and **not** against however
many runs this particular solution happened to use.

Suppose you scored a two-run plan on a base of two and a three-run plan on a base
of three. The two plans then live on different numeric scales, and the three-run
plan wins comparisons it ought to lose: it pays for an extra depot return without
collecting any more pollen, but its inflated weights make its `front_load` look
larger. Fixing `horizon` for all competitors makes the comparison meaningful.

This is also why `Solution` stores `horizon` as a member: `polish_solution` later
has to compute *differences* of front-load weights, and it must use the same base
the solution was originally scored with.

---

## 18. `struct Value` and `better_value` — lines 286–326

The block comment at lines 286–308 is the definitive statement of what the planner
is optimising, and is reproduced here in full because everything else in the file
serves it:

```cpp
    // ------------------------------------------------------------------------
    // What "best" means here, in strict priority order:
    //
    //   1. COLLECT MORE. On time alone the planner has a trivially optimal
    //      answer -- collect nothing, or only the pollen nearest the depot --
    //      because every extra pollen can only ever add time. The brief is "as
    //      much as possible before the clock runs out", so the haul leads and
    //      time is the tie-break, never the goal.
    //
    //   2. COLLECT IT EARLIER. Only run 0 is ever driven before the map is
    //      re-observed and the plan redone; runs 1..n exist to inform run 0 and
    //      are then thrown away. Two plans that collect nine pollen as 3+3+3 and
    //      as 2+3+4 read identically on the first key, but the second one drives
    //      a short run and keeps its good runs as advice. So a full early run
    //      beats a full late one, by exactly a lexicographic ordering on the
    //      per-run counts.
    //
    //   3. BE FASTER, as discounted time.
    //
    // Keeping 2 separate from 3 matters: an earlier version folded the ordering
    // into the same gamma that discounts time, which meant gamma = 1 (measurably
    // the better setting for time) silently switched the front-loading off.
    // ------------------------------------------------------------------------
```

### `struct Value` — lines 309–314

```cpp
    struct Value {
        bool   reached;
        int    collected;
        double front_load;
        double cost;
    };
```

**Summary:** one point in the comparison space — the three ranking keys plus a
reachability flag.

`Value` exists separately from `Solution` because the dynamic-programming split
needs to compare *partial* plans, which are not yet solutions. Its members:

- **`reached`** — whether this state is achievable at all. The split's table starts
  entirely unreached, and only the starting state is seeded as reachable.
- **`collected`** — pollen taken so far. Primary key, maximised.
- **`front_load`** — weighted earliness. Secondary key, maximised.
- **`cost`** — discounted time. Tertiary key, minimised.

### `better_value` — lines 316–326

```cpp
    bool better_value(const Value& candidate, const Value& incumbent) {
        if (!incumbent.reached) return true;
        if (!candidate.reached) return false;
        if (candidate.collected != incumbent.collected) {
            return candidate.collected > incumbent.collected;
        }
        if (std::fabs(candidate.front_load - incumbent.front_load) > EPSILON) {
            return candidate.front_load > incumbent.front_load;
        }
        return candidate.cost < incumbent.cost - EPSILON;
    }
```

**Summary:** the single comparison the entire planner ranks on. Returns true when
`candidate` should replace `incumbent`.

Read it as a cascade:

1. **Lines 317–318, reachability.** An unreached incumbent loses to anything; an
   unreached candidate never wins. This is what removes the need for special-casing
   empty table entries everywhere else.
2. **Lines 319–321, haul.** More pollen wins outright. Note `!=` then `>`: this is
   a strict ordering on an integer, so no epsilon is involved.
3. **Lines 322–324, earliness.** Equal haul, so prefer the plan that puts more of
   it in the earlier runs. Compared with an epsilon because `front_load` is stored
   as a `double`, even though it is mathematically an integer — at capacity 3 and
   six runs the largest value is about 4^5 · 3, far inside exact double range, so
   the epsilon is belt-and-braces rather than a real necessity.
4. **Line 325, time.** Everything else equal, faster wins — and by a *meaningful*
   margin, hence the epsilon. This is the only key of the three that is minimised.

The ordering is strict and total, which matters: the split's dynamic program
assumes that "better" is transitive, or its table entries would not compose.

---

## 19. `build_giant_tour` — lines 328–350

```cpp
    void build_giant_tour(const Ctx& ctx, int* tour) {
        bool visited[MAX_CANDIDATES] = {};
        double heading = ctx.depot.heading;
        int previous = 0;

        for (int step = 0; step < ctx.count; ++step) {
            int best = -1;
            double best_cost = INF;
            for (int c = 0; c < ctx.count; ++c) {
                if (visited[c]) continue;
                const double cost = leg_cost(heading, previous, c + 1, ctx, nullptr);
                if (cost < best_cost) {
                    best_cost = cost;
                    best = c;
                }
            }
            if (best < 0) break;
            visited[best] = true;
            tour[step] = best;
            leg_cost(heading, previous, best + 1, ctx, &heading);
            previous = best + 1;
        }
    }
```

**Summary:** produce a first, cheap ordering of *all* candidates as one long route,
ignoring capacity entirely — a nearest-neighbour walk in which "nearest" means
"cheapest in modelled time, turn included".

**Why a giant tour at all?** This is the classic *route-first, cluster-second*
strategy for capacitated routing. Rather than deciding which pollen go together
and then ordering each group, the planner first lays every pollen out in a single
sensible order, then cuts that order into runs (`split_tour`). Cutting a good tour
is a problem that can be solved *exactly* by dynamic programming in milliseconds,
whereas grouping-then-ordering is not.

**`visited[MAX_CANDIDATES]`** — which candidates have already been placed.
Zero-initialised, so all false.

**`heading`** — the robot's travel direction as the walk proceeds, starting at the
depot's heading. Carrying this is what makes the greedy choice turn-aware: a
pollen slightly further away but straight ahead can beat a nearer one that
requires a 150-degree swing.

**`previous`** — the node the walk is currently standing on, starting at node 0,
the depot.

**The outer loop (line 333)** places one candidate per iteration.

**The inner loop (lines 336–343)** scores every unplaced candidate with
`leg_cost` and keeps the cheapest. `nullptr` is passed for `out_heading` here
because these are hypothetical legs being compared, not legs being committed to —
the heading must not be advanced by a candidate that loses.

**Line 344**, `if (best < 0) break;` — defensive. If every remaining candidate
somehow scored as `INF`, there is nothing to pick and the loop stops rather than
writing an index of −1 into the tour.

**Lines 345–348** commit the winner: mark it visited, write it into the tour, and
*now* call `leg_cost` a second time with a real `out_heading` to advance the
heading along the leg actually taken. The second call is a table lookup and a
little arithmetic — cheaper and clearer than storing the winner's heading during
the search.

**What this function is not.** It is not a good tour. Nearest-neighbour famously
paints itself into corners, leaving a few stranded nodes that must be collected by
long legs at the end. It is a *starting point* — `optimize_sequence` is run over
its output immediately afterwards, and the anytime loop keeps perturbing it.

---

## 20. `double_bridge` — lines 352–369

```cpp
    void double_bridge(int* tour, int length, Rng& rng) {
        if (length < 8) {
            if (length >= 2) std::swap(tour[rng.below(length)], tour[rng.below(length)]);
            return;
        }
        int cuts[3];
        cuts[0] = 1 + rng.below(length - 3);
        cuts[1] = cuts[0] + 1 + rng.below(length - cuts[0] - 2);
        cuts[2] = cuts[1] + 1 + rng.below(length - cuts[1] - 1);

        int scratch[MAX_CANDIDATES];
        int write = 0;
        for (int i = 0; i < cuts[0]; ++i) scratch[write++] = tour[i];
        for (int i = cuts[1]; i < cuts[2]; ++i) scratch[write++] = tour[i];
        for (int i = cuts[0]; i < cuts[1]; ++i) scratch[write++] = tour[i];
        for (int i = cuts[2]; i < length; ++i) scratch[write++] = tour[i];
        for (int i = 0; i < length; ++i) tour[i] = scratch[i];
    }
```

**Summary:** the "kick" — a randomised perturbation of the tour that the anytime
loop applies before re-optimising, in order to escape a local optimum.

**Why a kick is needed.** `optimize_sequence` drives a tour to a point where no
2-opt and no Or-opt move improves it. That point is usually not the best tour; it
is just a valley the local search cannot climb out of, because every single step
out of it goes uphill. The standard escape is to *deliberately damage* the tour in
a way the local search cannot immediately undo, then re-optimise and see whether
you landed in a better valley. This pattern is called iterated local search.

**Why specifically a double bridge.** A double bridge cuts the tour into four
parts A|B|C|D and reassembles it as A|C|B|D. This is the canonical kick for tour
optimisation for one precise reason: **it cannot be undone by a single 2-opt
move.** A kick that 2-opt could immediately reverse would just be re-optimised
straight back to where it started, and the loop would make no progress. This one
forces the local search to explore genuinely new territory.

**Lines 353–356, the small-tour case.** The double bridge needs at least four
non-empty segments, so it needs a reasonable number of nodes; below 8 the cut
arithmetic would not have room. For a small tour it falls back to swapping two
random positions, which is a weaker but still valid perturbation. If the tour has
fewer than two nodes there is nothing to perturb and it returns untouched. (Note
the two draws may pick the same index, in which case the swap is a no-op and that
iteration of the anytime loop simply finds no improvement — harmless.)

**Lines 357–360, the three cut points.** Each is drawn to leave room for the ones
after it: `cuts[0]` lands at least one past the start and at least three before
the end, `cuts[1]` at least one past `cuts[0]`, `cuts[2]` at least one past
`cuts[1]` and at least one before the end. The result is four non-empty segments,
guaranteed, with no rejection sampling.

**Lines 362–368, the reassembly.** Segments are copied into `scratch` in the order
A, C, B, D and copied back. Segment B (`cuts[0]` to `cuts[1]`) and segment C
(`cuts[1]` to `cuts[2]`) have traded places; the orientation of every segment is
preserved, which is exactly what makes the move invisible to a single reversal.

---

## 21. `split_tour` — lines 371–481

The header comment:

```cpp
    // ------------------------------------------------------------------------
    // Optimal split: cut the giant tour into `runs` runs of at most `capacity`,
    // minimising the discounted total. Positions may be SKIPPED, which is how the
    // planner declines a pollen that is not worth its detour.
    //
    // Runs are formed at full capacity, the tail aside: with a fixed cost for
    // being at the depot, a short run wastes an entire scoring cycle.
    // ------------------------------------------------------------------------
```

**Summary:** given the giant tour, decide where to cut it into runs — and which
positions to skip entirely — so as to optimise the three-key objective. This is an
*exact* dynamic program over the given tour: for that tour and that horizon, no
better partition exists.

The approximation in the planner is therefore precisely located: the *tour order*
is heuristic (nearest-neighbour plus local search plus kicks), and the *assignment
of tour positions to runs* is exact given that order. The anytime loop exists to
attack the one remaining heuristic layer by trying many tours.

### Setup — lines 379–399

```cpp
    Solution split_tour(const int* tour, const Ctx& ctx, int capacity, int runs,
                        const double* gamma, double overhead) {
        const int K = ctx.count;
        Solution solution{};
        solution.valid = false;

        static_assert(MAX_RUNS >= 1, "at least one run");
        Value value[MAX_CANDIDATES + 1][MAX_RUNS + 1];
        int back_position[MAX_CANDIDATES + 1][MAX_RUNS + 1];
        int back_length[MAX_CANDIDATES + 1][MAX_RUNS + 1];

        for (int j = 0; j <= K; ++j) {
            for (int r = 0; r <= runs; ++r) {
                value[j][r] = Value{false, 0, 0.0, INF};
                back_position[j][r] = -1;
                back_length[j][r] = 0;
            }
        }
        value[0][0] = Value{true, 0, 0.0, 0.0};

        int scratch[MAX_RUN_LEN];
```

**Parameters**

- **`tour`** — the ordering to cut, as candidate indices.
- **`ctx`** — geometry and kinematics.
- **`capacity`** — maximum pollen per run.
- **`runs`** — the planning horizon: the maximum number of runs to cut into.
- **`gamma`** — per-run time discounts.
- **`overhead`** — `score_overhead_s`, charged once per run.

**`K`** — shorthand for the candidate count, matching the operations-research
convention.

**The three tables** are indexed `[position in tour][runs used]`:

- **`value[j][r]`** — the best achievable `Value` after considering the first `j`
  tour positions using exactly `r` runs.
- **`back_position[j][r]`** — the tour position the best path into `[j][r]` came
  from. This is the breadcrumb trail for reconstructing the answer.
- **`back_length[j][r]`** — how many tour positions that step consumed: `0` means
  "the candidate at that position was skipped", anything else is the length of the
  run that was formed.

Tables are stack arrays sized for the maximum, not for `K` — no allocation.

**Lines 390–396** clear every state to unreachable with cost `INF` and no
breadcrumb.

**Line 397** seeds the one state that is trivially true: at position 0 having used
0 runs, you have collected nothing, front-loaded nothing, and spent nothing.

**`static_assert` (line 385)** — a compile-time sanity check that the run capacity
constant was not set to something nonsensical.

**`scratch`** — the buffer a trial run is assembled in before being priced.

### The forward pass — lines 401–432

```cpp
        for (int j = 0; j < K; ++j) {
            for (int r = 0; r <= runs; ++r) {
                if (!value[j][r].reached) continue;

                // Decline this candidate and move on. This is how the planner
                // walks past a pollen that is not worth its detour.
                if (better_value(value[j][r], value[j + 1][r])) {
                    value[j + 1][r] = value[j][r];
                    back_position[j + 1][r] = j;
                    back_length[j + 1][r] = 0;
                }

                if (r >= runs) continue;
                const int span = std::min(capacity, K - j);
                for (int s = 1; s <= span; ++s) {
                    for (int m = 0; m < s; ++m) scratch[m] = tour[j + m];
                    const double seconds = optimize_run(scratch, s, ctx);

                    const Value candidate{
                            true,
                            value[j][r].collected + s,
                            value[j][r].front_load + front_load_weight(r, runs, capacity) * s,
                            value[j][r].cost + gamma[r] * (seconds + overhead)};

                    if (better_value(candidate, value[j + s][r + 1])) {
                        value[j + s][r + 1] = candidate;
                        back_position[j + s][r + 1] = j;
                        back_length[j + s][r + 1] = s;
                    }
                }
            }
        }
```

**The two loops** walk every reachable state in order of increasing tour position,
which is valid because every transition moves strictly forward through the tour.

**Line 403** skips states nothing can reach.

**Lines 405–411, the skip transition.** Carry the state forward one tour position
without taking the candidate there. **This is how the planner declines a pollen.**
Without it, the split would be forced to either collect every candidate or stop
early, and it could never walk past an awkwardly placed pollen to reach three good
ones behind it. `back_length = 0` is what marks the step as a skip during
reconstruction.

Note that the skip is written through `better_value` rather than unconditionally:
another path may already have reached `[j+1][r]` with a better value, and the
skip must not clobber it.

**Line 413** stops run-forming once the horizon is used up; only skips remain
available, which lets the tail of the tour be walked past to reach the final
state.

**Lines 414–430, the run transitions.** For each possible run length `s` from 1 up
to `span` (capacity, or however much tour is left):

- copy the next `s` tour positions into `scratch`;
- call `optimize_run`, which reorders them optimally and returns the run's time —
  this is where the exact permutation search gets its enormous call count;
- build the successor `Value`: `s` more pollen collected, `s` pollen worth of
  front-load weight at run index `r`, and the run's discounted time plus overhead
  added to the cost;
- keep it if it beats whatever is already recorded at `[j+s][r+1]`.

**Why runs come out full.** Nothing here forbids short runs — but every run pays
`overhead` regardless of how many pollen it holds, so splitting four pollen into
2 + 2 pays the depot cost twice for the same haul. The dynamic program discovers
that on its own; there is no rule enforcing it.

**Complexity.** `K * runs * capacity` states-and-transitions, each costing one
`optimize_run` call. At the real sizes (16 candidates, 3 runs, capacity 3) that is
about 150 calls of at most 6 permutations each — trivial, which is what makes it
affordable to re-run inside the anytime loop.

### Choosing the end state — lines 434–448

```cpp
        // Best over every reachable end state.
        int chosen_runs = -1;
        int chosen_position = -1;
        Value chosen{false, 0, 0.0, INF};
        for (int r = 1; r <= runs; ++r) {
            for (int j = 0; j <= K; ++j) {
                if (!value[j][r].reached) continue;
                if (better_value(value[j][r], chosen)) {
                    chosen = value[j][r];
                    chosen_position = j;
                    chosen_runs = r;
                }
            }
        }
        if (chosen_runs < 0) return solution;
```

**Why a search rather than just reading `value[K][runs]`.** Two reasons.

1. **The plan may legitimately use fewer runs than the horizon.** If two runs
   collect as much as three, the two-run plan is better — it pays one less
   overhead — and it lives at `[.][2]`, not `[.][3]`.
2. **The plan may stop before the end of the tour.** If the last few candidates
   are not worth collecting, the best state sits at some `j < K`. (The skip
   transition means such a state is also reachable at `j = K`, so this is
   belt-and-braces, but it makes the intent explicit.)

Every reachable state with at least one run is compared under the same
`better_value`, so the winner is the best plan by the same three-key rule used
everywhere else. `r` starts at 1 because a zero-run plan collects nothing.

**Line 448** — if nothing at all was reachable, return the invalid `Solution` built
at line 382. Callers check `valid` before using it.

### Reconstruction — lines 450–473

```cpp
        int found = 0;
        int j = chosen_position;
        int r = chosen_runs;
        while (r > 0 && j >= 0) {
            const int from = back_position[j][r];
            const int length = back_length[j][r];
            if (from < 0) break;
            if (length > 0) {
                solution.length[found] = length;
                for (int m = 0; m < length; ++m) solution.members[found][m] = tour[from + m];
                ++found;
                --r;
            }
            j = from;
        }

        // The walk produced them last-first.
        solution.count = found;
        for (int a = 0, b = found - 1; a < b; ++a, --b) {
            std::swap(solution.length[a], solution.length[b]);
            for (int m = 0; m < MAX_RUN_LEN; ++m) {
                std::swap(solution.members[a][m], solution.members[b][m]);
            }
        }
```

**Walking the breadcrumbs.** From the chosen end state, repeatedly step back to
`back_position`. A step with `back_length > 0` was a run: copy its members out of
the tour and decrement the run counter. A step with `back_length == 0` was a skip:
move the position back and record nothing.

**`found`** counts the runs recovered, which is the plan's true run count.

**Line 456**, `if (from < 0) break;` — the terminating condition. The seed state
at `[0][0]` has no predecessor, so its breadcrumb is still −1.

**Lines 466–473, the reversal.** Back-tracking naturally produces the runs
last-first, so they are swapped into driving order. Note that the member arrays
are swapped over the **full** `MAX_RUN_LEN`, not just over the live length: the
lengths are being swapped at the same time, so swapping only the live prefix would
leave the two arrays inconsistent with their new lengths.

### Final scoring — lines 475–481

```cpp
        for (int run = 0; run < solution.count; ++run) {
            solution.seconds[run] = optimize_run(solution.members[run], solution.length[run], ctx);
        }
        score_solution(solution, ctx, gamma, overhead, capacity, runs);
        solution.valid = solution.count > 0;
        return solution;
    }
```

**Lines 475–477** re-run `optimize_run` on each recovered run. The runs were
already ordered optimally during the forward pass, but that result was computed in
`scratch` and discarded — only the *cost* was kept in the table. This recomputes
the ordering into the real member arrays.

**Line 478** computes the three ranking keys, with `horizon` set to `runs` — the
planning horizon, exactly as the comment on `score_solution` requires, and *not*
`solution.count`.

**Line 479** marks the solution usable if it contains at least one run.

---
## 22. `polish_solution` — lines 483–603

The header comment:

```cpp
    // ------------------------------------------------------------------------
    // Cross-run local search. The split can only cut the giant tour into
    // CONTIGUOUS segments, so it cannot put two pollen in the same run unless the
    // tour already happened to place them next to each other. These moves break
    // that restriction, and are usually the largest remaining win.
    //
    // Because optimize_run solves the within-run order exactly, a run is fully
    // described by its member SET -- so relocating and swapping are pure set
    // operations and no insertion position has to be searched.
    // ------------------------------------------------------------------------
```

**Summary:** improve a split plan by moving pollen *between* runs — which the
split itself is structurally incapable of doing.

**Why it is needed.** `split_tour` can only cut the giant tour into **contiguous**
segments. So two pollen can only end up in the same run if the tour happened to
place them next to each other. If the tour order is slightly wrong — and it is a
heuristic, so it often is — two pollen that obviously belong together may be split
across runs with no way for the dynamic program to fix it. These cross-run moves
break that restriction, and in practice they are the largest remaining win after
the split.

**Why the moves are simple.** Because `optimize_run` solves within-run ordering
exactly, **a run is fully described by its member set**. Moving a pollen into run
B means appending it to B's member list and calling `optimize_run` — there is no
insertion position to search for, because whatever the best position is,
`optimize_run` will find it. That collapses what is normally a nested search into
a flat one.

### Setup — lines 493–506

```cpp
    void polish_solution(Solution& solution, const Ctx& ctx, int capacity,
                         const double* gamma, double overhead,
                         const int* unused, int unused_count) {
        int spare[MAX_CANDIDATES];
        int spare_count = unused_count;
        for (int i = 0; i < unused_count; ++i) spare[i] = unused[i];

        int trial_a[MAX_RUN_LEN];
        int trial_b[MAX_RUN_LEN];

        bool improved = true;
        int guard = 0;
        while (improved && guard++ < 16) {
            improved = false;
```

**Parameters**

- **`solution`** — the plan, mutated in place.
- **`ctx`**, **`capacity`**, **`gamma`**, **`overhead`** — as elsewhere.
- **`unused` / `unused_count`** — the candidates the split declined to collect.
  These are the stock the third move type draws from.

**`spare` / `spare_count`** — a local, mutable copy of the unused list, because the
exchange move swaps pollen *into* and *out of* it.

**`trial_a` / `trial_b`** — scratch member lists for the two runs a move is
considering. Every move is built and priced here before anything is committed.

**`improved` / `guard`** — the same convergence-plus-safety-ceiling pattern as
`optimize_sequence`, with a ceiling of 16 passes. The three move types below all
run once per pass, and the pass repeats while anything at all was accepted.

### Move 1 — relocate, lines 507–555

```cpp

            for (int a = 0; a < solution.count; ++a) {
                for (int b = 0; b < solution.count; ++b) {
                    if (a == b) continue;
                    if (solution.length[b] >= capacity) continue;
                    if (solution.length[a] <= 1) continue;

                    for (int i = 0; i < solution.length[a]; ++i) {
                        int write = 0;
                        for (int m = 0; m < solution.length[a]; ++m) {
                            if (m != i) trial_a[write++] = solution.members[a][m];
                        }
                        const int moved_length_a = write;
                        for (int m = 0; m < solution.length[b]; ++m) trial_b[m] = solution.members[b][m];
                        trial_b[solution.length[b]] = solution.members[a][i];
                        const int moved_length_b = solution.length[b] + 1;

                        const double time_a = optimize_run(trial_a, moved_length_a, ctx);
                        const double time_b = optimize_run(trial_b, moved_length_b, ctx);
                        const double delta = gamma[a] * (time_a - solution.seconds[a])
                                           + gamma[b] * (time_b - solution.seconds[b]);
                        // Relocating moves a pollen between runs, so unlike the
                        // swap and exchange below it changes WHERE the haul lands.
                        // Pulled earlier the front-load rises, pushed later it
                        // falls -- and front-load outranks time, so a move that
                        // hollows out the run we are about to drive is rejected
                        // however much time it saves. Without this the polish
                        // quietly undoes the split and run 0, the only run that
                        // actually happens, comes back short.
                        const double front_delta =
                                front_load_weight(b, solution.horizon, capacity)
                              - front_load_weight(a, solution.horizon, capacity);
                        const bool accept = front_delta > EPSILON
                                || (std::fabs(front_delta) <= EPSILON && delta < -EPSILON);
                        if (accept) {
                            solution.length[a] = moved_length_a;
                            solution.length[b] = moved_length_b;
                            solution.seconds[a] = time_a;
                            solution.seconds[b] = time_b;
                            for (int m = 0; m < moved_length_a; ++m) solution.members[a][m] = trial_a[m];
                            for (int m = 0; m < moved_length_b; ++m) solution.members[b][m] = trial_b[m];
                            solution.objective += delta;
                            solution.front_load += front_delta;
                            improved = true;
                            break;
                        }
                    }
                }
            }
```

**What the move is:** take one pollen out of run `a` and put it into run `b`.

**The guards (lines 510–512)**

- `a == b` — nothing to do.
- `length[b] >= capacity` — the destination is full; the move would violate the
  capacity constraint.
- `length[a] <= 1` — refuse to empty a run completely. An empty run in the middle
  of a plan would leave `count` claiming runs that do not exist, and the run
  indices that `gamma` and the front-load weights are keyed on would no longer
  mean what they say.

**Building the trial (lines 514–522).** Run `a` is copied without member `i`; run
`b` is copied with that member appended at the end. The append position is
arbitrary precisely because `optimize_run` will reorder.

**Pricing (lines 524–527).** Both runs are re-optimised and the *change* in
discounted time is computed. Only these two runs can change, so the delta is the
whole story — no need to rescore the plan.

**The acceptance rule (lines 528–540) is the most important part of this
function**, and the comment explains why at length. Relocating changes **where the
haul lands**, unlike the two moves below, which only shuffle *which* pollen are
where. And front-load outranks time in the objective. So:

- **`front_delta > EPSILON`** — the pollen is moving to an *earlier* run (a lower
  run index carries a larger weight, so moving to a lower index makes the
  difference `weight(b) - weight(a)` positive). Front-load improved, which outranks
  time, so accept **regardless of what it costs in seconds**.
- **`front_delta` is zero and `delta < -EPSILON`** — this can only happen when
  `a` and `b` carry the same weight, which does not occur for distinct runs under
  the current weighting; the branch is kept so the rule reads as the full
  lexicographic comparison rather than a special case.
- **Otherwise reject** — including moves that save a great deal of time by pushing
  a pollen into a later run.

That last case is the bug this rule was written to fix. Without it, the polish
would happily hollow out run 0 to make runs 1 and 2 faster — and run 0 is the only
run that ever actually gets driven. The plan would look better on paper and the
robot would collect less.

**On acceptance (lines 541–552)** both runs' lengths, times and member lists are
updated, and `objective` and `front_load` are adjusted by their deltas rather than
recomputed. The `break` exits the loop over `i` because the lengths just changed
underneath it.

### Move 2 — swap, lines 556–580

```cpp

            for (int a = 0; a < solution.count; ++a) {
                for (int b = a + 1; b < solution.count; ++b) {
                    for (int i = 0; i < solution.length[a]; ++i) {
                        for (int j = 0; j < solution.length[b]; ++j) {
                            for (int m = 0; m < solution.length[a]; ++m) trial_a[m] = solution.members[a][m];
                            for (int m = 0; m < solution.length[b]; ++m) trial_b[m] = solution.members[b][m];
                            std::swap(trial_a[i], trial_b[j]);

                            const double time_a = optimize_run(trial_a, solution.length[a], ctx);
                            const double time_b = optimize_run(trial_b, solution.length[b], ctx);
                            const double delta = gamma[a] * (time_a - solution.seconds[a])
                                               + gamma[b] * (time_b - solution.seconds[b]);
                            if (delta < -EPSILON) {
                                solution.seconds[a] = time_a;
                                solution.seconds[b] = time_b;
                                for (int m = 0; m < solution.length[a]; ++m) solution.members[a][m] = trial_a[m];
                                for (int m = 0; m < solution.length[b]; ++m) solution.members[b][m] = trial_b[m];
                                solution.objective += delta;
                                improved = true;
                            }
                        }
                    }
                }
            }
```

**What the move is:** exchange one pollen in run `a` for one in run `b`.

**Why it is separate from relocate.** Relocate cannot help when both runs are
already at capacity — there is nowhere to put anything. Swapping keeps both
lengths identical, so it is always legal, and it is the only move that can fix
"these two pollen are in the wrong runs" once the plan is packed full.

**Why `b` starts at `a + 1`** (line 558): swapping `a` with `b` and swapping `b`
with `a` are the same move, so only one direction is enumerated.

**Why there is no front-load term.** A swap moves one pollen each way, so the
per-run counts do not change at all, and `front_load` is a function of the counts
alone. The acceptance test is therefore purely "is it faster" — line 569, the plain
`delta < -EPSILON`.

Note that unlike relocate this branch does not `break` on acceptance: the run
lengths are unchanged, so the enclosing loop bounds remain valid and the search can
carry straight on.

### Move 3 — exchange with an unused pollen, lines 581–603

```cpp

            // Trade a planned pollen for one the split skipped entirely.
            for (int a = 0; a < solution.count && spare_count > 0; ++a) {
                for (int i = 0; i < solution.length[a]; ++i) {
                    for (int u = 0; u < spare_count; ++u) {
                        for (int m = 0; m < solution.length[a]; ++m) trial_a[m] = solution.members[a][m];
                        trial_a[i] = spare[u];

                        const double time_a = optimize_run(trial_a, solution.length[a], ctx);
                        const double delta = gamma[a] * (time_a - solution.seconds[a]);
                        if (delta < -EPSILON) {
                            spare[u] = solution.members[a][i];
                            solution.seconds[a] = time_a;
                            for (int m = 0; m < solution.length[a]; ++m) solution.members[a][m] = trial_a[m];
                            solution.objective += delta;
                            improved = true;
                            break;
                        }
                    }
                }
            }
        }
    }
```

**What the move is:** trade a pollen the plan is collecting for one the split
skipped entirely.

**Why it is needed.** The split chose which pollen to skip based on the tour order
it was given. If that order put a pollen in an awkward place, the split may have
declined a perfectly good pollen and kept a worse one. This move gives the plan a
second opinion, using the actual run contents rather than tour adjacency.

**Why the haul is unchanged by it.** One in, one out — `collected` and every
per-run count stay the same, so again neither `collected` nor `front_load` can
move, and pure time is the right acceptance criterion (line 591).

**The bookkeeping on acceptance (line 592)** is the neat part: the pollen that was
displaced from the run is written back into the `spare` slot the new one came from.
The spare pool therefore stays exactly the same size, and a later pass can trade
the displaced pollen back in somewhere else.

**`spare_count > 0`** in the loop condition (line 583) skips the whole move when
the split collected everything, which is the common case on a small map.

---

## 23. `detour_cost` — lines 605–622

The header comment introduces both this function and `build_uncapacitated`:

```cpp
    // ------------------------------------------------------------------------
    // The uncapacitated variant: one closed tour, no intermediate returns.
    // "As much as possible" is then an orienteering problem -- trim members until
    // the tour fits the deadline, then put back anything the slack allows.
    //
    // Both passes rank by the DETOUR a member costs where it currently sits,
    // rather than re-optimising once per trial. That is a slightly weaker choice
    // but it is O(length) instead of O(length^2 * optimise), which is what keeps
    // this usable inside the anytime loop.
    // ------------------------------------------------------------------------
```

```cpp
    double detour_cost(const int* members, int length, int position, const Ctx& ctx) {
        int scratch[MAX_RUN_LEN];
        int write = 0;
        for (int m = 0; m < length; ++m) {
            if (m != position) scratch[write++] = members[m];
        }
        return sequence_time(members, length, ctx) - sequence_time(scratch, write, ctx);
    }
```

**Summary:** how much time member `position` is costing the route *where it
currently sits* — the difference between the route with it and the route without
it, leaving every other member exactly where it is.

**Where it is used:** by `build_uncapacitated` to decide what to throw away when a
tour does not fit the deadline, and by the deadline fit at the end of `solve()` for
the same purpose. Both want to answer "which single member is buying us the least?"
and both want the answer cheaply.

**What it deliberately does not do.** It does not re-optimise the shortened route.
A better measure would be "remove this member, re-optimise what is left, and see
how much that route costs" — but that is `O(length)` removals times the cost of an
optimisation pass, and this is called inside the anytime loop. The comment at lines
610–613 states the trade explicitly: a slightly weaker ranking, but `O(length)`
instead of `O(length^2 · optimise)`, which is what keeps the uncapacitated variant
usable at all.

**Implementation.** `scratch` is the member list with `position` elided; the return
is the full route's time minus the shortened route's time. Both are priced from the
depot with `sequence_time`, so the turn penalties on both sides of the removed
member — which is where most of the saving usually comes from — are correctly
accounted for.

---

## 24. `build_uncapacitated` — lines 624–683

**Summary:** the alternative construction used when `params.capacity <= 0`. One
single closed tour, no intermediate returns to the depot, trimmed until it fits the
deadline and then grown back into whatever slack remains.

**When you would want this.** When the robot does not have to return to score
between pickups — it can hold everything, or the "scoring" is incidental. The
problem then stops being a capacitated routing problem and becomes an
**orienteering problem**: not "visit everything in the best order" but "given a time
budget, choose a subset and an order that collects as much as possible". The
lookahead is meaningless here, because there is only ever one run.

### Seeding — lines 624–634

```cpp
    Solution build_uncapacitated(const int* tour, const Ctx& ctx, double budget,
                                 const double* gamma, double overhead) {
        Solution solution{};
        solution.count = 1;
        solution.valid = true;

        int length = ctx.count;
        for (int i = 0; i < length; ++i) solution.members[0][i] = tour[i];

        bool dropped[MAX_CANDIDATES] = {};
        double seconds = optimize_sequence(solution.members[0], length, ctx);
```

**Parameters**

- **`tour`** — the giant tour, which becomes the starting route.
- **`budget`** — the time the single run has to fit inside. Computed by the caller
  as `remaining_s - overhead`, since one scoring cycle is still paid for.
- **`gamma`, `overhead`** — passed through to `score_solution` at the end.

**Lines 627–628** declare up front that this is a one-run, valid plan; unlike the
split, there is no way for this construction to fail.

**`dropped[MAX_CANDIDATES]`** — which candidates the trim has thrown out. The
grow-back pass at the end only considers these, which is what stops it from trying
to re-add pollen that are already in the route.

**Line 634** optimises the starting route and gets its time.

### The trim pass — lines 636–652

```cpp
        while (length > 0 && seconds > budget) {
            int worst = 0;
            double worst_saving = -INF;
            for (int m = 0; m < length; ++m) {
                const double saving = detour_cost(solution.members[0], length, m, ctx);
                if (saving > worst_saving) {
                    worst_saving = saving;
                    worst = m;
                }
            }
            dropped[solution.members[0][worst]] = true;
            for (int m = worst; m + 1 < length; ++m) {
                solution.members[0][m] = solution.members[0][m + 1];
            }
            --length;
            seconds = optimize_sequence(solution.members[0], length, ctx);
        }
```

**What it does:** while the route is over budget, find the member whose detour cost
is highest — the one buying the least per second spent — remove it, and re-optimise.

**Lines 637–645** scan every member with `detour_cost` and keep the worst.
`worst_saving` starts at `-INF` so the first member always becomes the incumbent,
even if removing it somehow saved negative time.

**Line 646** records the removal in `dropped` *before* the member is shifted out of
the array, because after the shift its index no longer refers to it.

**Lines 647–650** close the gap by shifting the tail down, and shorten the route.

**Line 651** re-optimises what is left. This matters: after removing a member, the
remaining route often has a better order available, and re-optimising may bring it
under budget in one step where the un-optimised route would have needed two
removals.

**`length > 0`** in the loop condition guarantees termination — in the worst case
everything is dropped and an empty route trivially fits.

### The grow-back pass — lines 654–677

```cpp
        // Grow back into whatever slack is left: the trim removes by local detour,
        // which can throw away a pollen that a re-optimised tour would have kept.
        bool added = true;
        while (added && length < MAX_RUN_LEN) {
            added = false;
            for (int c = 0; c < ctx.count; ++c) {
                bool present = false;
                for (int m = 0; m < length && !present; ++m) present = solution.members[0][m] == c;
                if (present || !dropped[c]) continue;

                int trial[MAX_RUN_LEN];
                for (int m = 0; m < length; ++m) trial[m] = solution.members[0][m];
                trial[length] = c;
                const double candidate = optimize_sequence(trial, length + 1, ctx);
                if (candidate <= budget) {
                    for (int m = 0; m <= length; ++m) solution.members[0][m] = trial[m];
                    ++length;
                    seconds = candidate;
                    dropped[c] = false;
                    added = true;
                    break;
                }
            }
        }
```

**Why this exists.** The trim removes greedily, by local detour, one member at a
time. That measure is myopic: it can throw away a pollen that a *re-optimised*
route would have kept comfortably, particularly when removing some other member
later opens up the route around it. So once the route fits, the planner tries to
put the discarded pollen back.

**Lines 660–662** skip any candidate that is already in the route, and any that was
never dropped in the first place (those were never in contention — though after the
trim, every candidate not in the route is in `dropped`, so this is mostly a
readability guard).

**Lines 664–667** build a trial route with the candidate appended and optimise it.
Appending at the end is fine for the same reason as in `polish_solution`: the
optimiser will move it to wherever it belongs.

**Lines 668–675, acceptance.** If the grown route still fits the budget, commit it:
copy it back, extend the length, record the new time, clear the `dropped` flag, and
`break` out to restart the scan from the beginning — because adding one member
changes the route, and a candidate that did not fit a moment ago may fit now.

**`length < MAX_RUN_LEN`** in the loop condition bounds the route to the array size.

### Finishing — lines 679–683

```cpp
        solution.length[0] = length;
        solution.seconds[0] = seconds;
        score_solution(solution, ctx, gamma, overhead, ctx.count, 1);
        return solution;
    }
```

The single run's length and time are recorded, and the plan is scored. Note the
arguments to `score_solution`: capacity is `ctx.count` (the run can hold
everything) and horizon is `1` (there is only one run). The front-load term is
therefore constant across all uncapacitated solutions and carries no information —
which is correct, and is why `better` has a separate branch for this case.

---

## 25. `better` — lines 685–697

```cpp
    bool better(const Solution& candidate, const Solution& incumbent, bool uncapacitated) {
        if (!incumbent.valid) return candidate.valid;
        if (!candidate.valid) return false;
        if (uncapacitated) {
            // Maximise how much gets collected; only then care how fast.
            if (candidate.length[0] != incumbent.length[0]) {
                return candidate.length[0] > incumbent.length[0];
            }
            return candidate.seconds[0] < incumbent.seconds[0] - EPSILON;
        }
        return better_value(Value{true, candidate.collected, candidate.front_load, candidate.objective},
                            Value{true, incumbent.collected, incumbent.front_load, incumbent.objective});
    }
```

**Summary:** compare two complete `Solution`s. This is what the anytime loop uses
to decide whether a newly constructed plan should replace the incumbent.

**Lines 686–687, validity.** An invalid incumbent is replaced by anything valid; an
invalid candidate never wins. This is what lets the anytime loop start from a
possibly-failed first construction without special-casing it.

**Lines 688–694, the uncapacitated branch.** With one run there is no partition and
no earliness to speak of, so the comparison reduces to two keys: collect more
first, then be faster. Written directly against `length[0]` and `seconds[0]` rather
than going through `Value`, because `front_load` is meaningless here.

**Lines 695–696, the capacitated branch.** Repackage both solutions' three keys
into `Value`s (both trivially `reached`) and defer to `better_value`, so that the
anytime loop ranks plans by **exactly** the same rule the dynamic program used
internally. If these two rules ever disagreed, the loop could replace a plan the
split considered better, and the planner would contradict itself.

---

## 26. End of the anonymous namespace — line 698

```cpp
}
```

Everything above is private to this translation unit. Everything below is the
public API declared in `butine.h`.

---
## 27. `namespace butine` — line 700

```cpp
namespace butine {

```

Everything from here to the end of the file is the public API. It is four
functions: the three cost-model primitives (`straight_time`, `leg_time`,
`run_time`) and the planner itself (`solve`).

The three cost primitives are public **for calibration**. The planner test opmode
drives real legs and prints the predicted time next to the measured one, and it
needs to call the exact same model the planner ranks with — not a re-implementation
that might drift. If you change the motion model, that opmode is how you find out
whether you made it better.

---

## 28. `straight_time` — lines 702–716

```cpp
    double straight_time(double distance_in, const Kinematics& k) {
        if (!(distance_in > EPSILON)) return 0.0;
        const double cruise = std::max(k.cruise_speed_in_s, EPSILON);
        const double accel = std::max(k.accel_in_s2, EPSILON);
        const double decel = std::max(k.decel_in_s2, EPSILON);

        const double ramp = cruise * cruise / (2.0 * accel) + cruise * cruise / (2.0 * decel);
        if (distance_in >= ramp) {
            return cruise / accel + cruise / decel + (distance_in - ramp) / cruise;
        }
        // Never reaches cruise. This is the common case on a 144 inch field, and
        // it is why leg time grows like sqrt(distance) rather than linearly.
        const double peak = std::sqrt(2.0 * distance_in * accel * decel / (accel + decel));
        return peak / accel + peak / decel;
    }
```

**Summary:** how long it takes to drive a straight line of `distance_in` inches,
starting at rest and ending at rest. **This is the single most important function
in the file**, because it is the reason the planner minimises time rather than
distance.

**Line 703, the guard.** Zero or negative distance costs nothing. Written as
`!(distance_in > EPSILON)` rather than `distance_in <= EPSILON` so that a `NaN`
distance — which compares false against everything — takes the safe branch and
returns 0 instead of propagating `NaN` through every cost in the problem.

**Lines 704–706** clamp the kinematics away from zero. If someone passes a
configuration with zero acceleration, this yields an absurdly large but finite
time rather than a division by zero and an `INF` that poisons the comparisons.

**Line 708, `ramp`.** The distance consumed by accelerating from rest to cruise
speed and then decelerating back to rest, with no cruising in between. From the
standard constant-acceleration relation, that is `v^2 / 2a` to speed up plus
`v^2 / 2d` to slow down. With the default numbers — 72 in/s cruise, 162 in/s^2
accelerating, 173 in/s^2 braking — that comes to **31 inches** (the header's
prose quotes a rounder 34; the exact figure moves with whatever kinematics the
caller supplies).

Hold that number against the field: it is 144 inches across. **Most legs the robot
ever drives are shorter than the distance it needs just to reach full speed.**

**Lines 709–711, the trapezoidal case.** Long enough to reach cruise: spend
`cruise / accel` seconds speeding up, `cruise / decel` slowing down, and cover the
leftover distance at constant speed. The velocity profile is a trapezoid.

**Lines 712–715, the triangular case — the common one.** The robot accelerates,
hits some peak speed below cruise, and immediately starts braking. The velocity
profile is a triangle. Setting the accelerating distance plus the braking distance
equal to the total and solving for the peak speed gives

```text
peak = sqrt(2 * distance * accel * decel / (accel + decel))
```

and the time is then the time to reach that peak plus the time to shed it.

**Why this is the crux of the whole planner.** In the triangular regime the time
is proportional to `sqrt(distance)`, not to `distance`. That has a direct
consequence for routing: **two short hops can cost more than one long one.**

| Distance | Time (default kinematics) | Regime |
|---|---|---|
| 4 in | 0.31 s | triangular |
| 8 in | 0.44 s | triangular |
| 16 in | 0.62 s | triangular |
| 24 in | 0.76 s | triangular |
| 36 in | 0.93 s | trapezoidal |
| 64 in | 1.32 s | trapezoidal |
| 144 in | 2.43 s | trapezoidal |

Two 16-inch hops cover 32 inches and cost 1.24 s. A single 36-inch leg — *longer*
in distance — costs 0.93 s. A planner that minimises distance would prefer the
pair, and would be choosing something 33 percent slower. That is the single fact
that justifies the existence of this cost model and, with it, most of the
complexity in this file.

---

## 29. `leg_time` — lines 718–738

```cpp
    double leg_time(double incoming_heading, const pedro::Pose& from, const pedro::Pose& to,
                    const Kinematics& k, double* out_heading) {
        const double dx = to.x - from.x;
        const double dy = to.y - from.y;
        const double distance = std::sqrt(dx * dx + dy * dy);
        if (distance < DEGENERATE_LEG_IN) {
            if (out_heading) *out_heading = incoming_heading;
            return 0.0;
        }

        const double direction = std::atan2(dy, dx);
        if (out_heading) *out_heading = direction;

        double seconds = straight_time(distance, k);
        const double swing = std::fabs(wrap_pi(direction - incoming_heading));
        if (swing > k.turn_deadband_rad && k.omega_max_rad_s > EPSILON) {
            const double rotate = swing / k.omega_max_rad_s + k.turn_settle_s;
            seconds += k.turn_coupling * std::max(0.0, rotate - seconds);
        }
        return seconds;
    }
```

**Summary:** the full cost of one leg between two poses, including the turn onto
it. This is the public, pose-based twin of the private, table-based `leg_cost`.

**Why both exist.** They compute the same quantity and must agree exactly.
`leg_cost` reads `ctx.straight` and `ctx.direction` because inside the solver the
same legs are evaluated thousands of times and the `sqrt`/`atan2` are worth
caching. `leg_time` takes raw poses because callers outside the solver — the
calibration opmode, anything that wants to price a path it is about to drive — have
no `Ctx` and should not have to build one.

**The body** is line-for-line the same logic as `leg_cost`:

- lines 720–722, the geometry;
- lines 723–726, the degenerate case, with the heading passed straight through;
- lines 728–729, the travel direction, which is also the tangent heading the robot
  will adopt;
- line 731, the straight-line time;
- lines 732–736, the swing, the deadband, the rotation time, and the holonomic
  coupling that only charges the turn once it outlasts the drive.

If you modify one of these two functions you must modify the other. A divergence
would not crash anything; it would quietly make the planner rank routes by one
model and the calibration opmode report against another.

---

## 30. `run_time` — lines 740–760

```cpp
    double run_time(const pedro::Pose& depot, const pedro::Pose* poses, int count,
                    const Kinematics& k) {
        if (count <= 0) return 0.0;

        double heading = depot.heading;
        double total = 0.0;
        const pedro::Pose* previous = &depot;

        for (int i = 0; i < count; ++i) {
            total += leg_time(heading, *previous, poses[i], k, &heading);
            total += k.pickup_dwell_s;
            previous = &poses[i];
        }
        total += leg_time(heading, *previous, depot, k, &heading);

        const double settle = std::fabs(wrap_pi(depot.heading - heading));
        if (settle > k.turn_deadband_rad && k.omega_max_rad_s > EPSILON) {
            total += settle / k.omega_max_rad_s + k.turn_settle_s;
        }
        return total;
    }
```

**Summary:** the closed-loop time of an explicit run —
`depot -> poses[0] -> ... -> poses[count-1] -> depot` — including the final swing
back onto the depot heading, and excluding `score_overhead_s`.

This is the pose-based twin of `sequence_time`, and exists for the same reason
`leg_time` does: so callers outside the solver can price a concrete run. Its
primary consumer is the test opmode, which uses it to print a predicted time for
each run the planner produced so it can be compared against the stopwatch.

**Line 742** — an empty run costs nothing, matching `sequence_time`.

**Lines 744–746** initialise the walk: the robot starts at the depot, pointing
along the depot heading, having spent nothing.

**Lines 748–752, the main loop** — one leg and one pickup dwell per member,
carrying the heading forward through the out-parameter.

**Line 753, the return leg** back to the depot.

**Lines 755–758, the final settle** onto the depot heading, charged without
holonomic coupling because the robot is stationary by then.

The `Run::time_s` field in a returned `Plan` is exactly this quantity, computed
internally by `sequence_time`. Calling `run_time` on `Run::poses` reproduces it.

---

## 31. `solve` — lines 762–981

This is the entry point, and the only function most callers ever touch. It is
described in `butine.h` as:

> The whole planner. `pollen` is normally `pollen_map::snapshot()`; `depot` is
> where every run starts and ends, in unmirrored PINK field coordinates — `pedro`
> mirrors for BLUE at build time, so nothing here may pre-mirror.

That coordinate contract matters: if you hand this function a depot that has
already been mirrored for the blue alliance, every pollen position it is comparing
against is still unmirrored, and the plan will be geometrically nonsense.

### Signature and initial state — lines 762–774

```cpp
    Plan solve(const std::vector<limelight::Pollen>& pollen,
               const pedro::Pose& depot,
               const Params& params) {
        const auto started = std::chrono::steady_clock::now();

        Plan plan{};
        plan.depot = depot;
        plan.uncapacitated = params.capacity <= 0;

        Ctx ctx{};
        ctx.depot = depot;
        ctx.k = params.kinematics;
        ctx.count = 0;
```

**`started`** — the wall-clock reading the entire compute budget is measured
against, taken as the very first statement so that even the candidate filtering
counts against it. `steady_clock` specifically, because it cannot jump backwards
if the system clock is adjusted mid-match.

**`plan`** — the result, zero-initialised, so every field has a defined value even
on the early-return paths.

**`plan.uncapacitated`** — the branch flag for the whole function, derived from
`params.capacity <= 0`. Recorded in the `Plan` so the caller can see which variant
actually ran.

**`ctx`** — the problem instance, filled in over the next two steps.

### Step 0 — candidate selection, lines 776–811

```cpp
        // --- step 0: filter, then keep only the cheapest-to-reach candidates ---
        // The cap is what makes the solve time independent of how large the map
        // has grown, which is what lets the compute budget be honoured.
        const int wanted = std::clamp(params.candidate_cap, 1, MAX_CANDIDATES);
        struct Scored { double seconds; int index; };
        Scored scored[64];
        int scored_count = 0;

        for (std::size_t i = 0; i < pollen.size() && scored_count < 64; ++i) {
            const limelight::Pollen& item = pollen[i];
            if (item.confidence < params.min_confidence) continue;
            if (!std::isfinite(item.position.x) || !std::isfinite(item.position.y)) continue;
            const double dx = item.position.x - depot.x;
            const double dy = item.position.y - depot.y;
            scored[scored_count].seconds = straight_time(std::sqrt(dx * dx + dy * dy), ctx.k);
            scored[scored_count].index = static_cast<int>(i);
            ++scored_count;
        }

        std::sort(scored, scored + scored_count,
                  [](const Scored& a, const Scored& b) { return a.seconds < b.seconds; });
```

**What it does:** reduce the pollen map to at most `candidate_cap` candidates,
keeping the ones that are cheapest to reach from the depot.

**Why there is a cap at all.** The map can grow to whatever the camera has seen.
The solve cost grows with the number of candidates, and the budget is a hard
50 ms. Capping the candidate count is what makes the solve time **independent of
map size**, which is the only way the budget can be honoured at all. Without it,
a busy field would silently blow through the budget and return a half-improved
plan.

**`wanted`** (line 779) — the cap, clamped into `[1, MAX_CANDIDATES]` so a caller
cannot ask for more than the fixed arrays can hold.

**`struct Scored`** (line 780) — a local pairing of "how long to reach it" with
"where it is in the caller's vector". Declared inside the function because it is
meaningless anywhere else.

**`scored[64]`** — the shortlist buffer, bounded independently of the map so the
filter pass itself has a fixed worst case. If the map holds more than 64 usable
pollen, the rest are not even considered — an acceptable simplification, since
`candidate_cap` is 16 by default and the 64 nearest will contain the 16 cheapest in
any realistic field.

**The filter loop (lines 784–793)** rejects two kinds of entry:

- **`confidence < min_confidence`** — the caller's quality gate on the vision
  detections. Default is 0, so nothing is rejected unless asked.
- **non-finite coordinates** — a `NaN` from a bad projection would poison every
  distance computation downstream and, worse, make comparisons non-transitive and
  the sort undefined. This is the one place it can be caught cheaply.

Each survivor is scored by `straight_time` on its direct depot distance — **time,
not distance**, so the ranking uses the same currency as the rest of the planner.
The turn is ignored here because at this stage there is no route and therefore no
incoming heading; this is a coarse pre-filter, not a cost.

**The sort (lines 795–796)** puts the cheapest-to-reach first.

```cpp
        const int keep = std::min(scored_count, wanted);
        for (int i = 0; i < keep; ++i) {
            const limelight::Pollen& item = pollen[static_cast<std::size_t>(scored[i].index)];
            ctx.pose[ctx.count] = item.position;
            ctx.id[ctx.count] = item.id;
            ++ctx.count;
        }
        plan.candidates_considered = ctx.count;

        if (ctx.count == 0) {
            plan.solve_us = std::chrono::duration<double, std::micro>(
                    std::chrono::steady_clock::now() - started).count();
            return plan;
        }
```

**Lines 798–804** copy the surviving candidates into `Ctx`, building the dense
candidate index space (`0..K-1`) the solver works in, together with the `id` array
that maps back to the caller's pollen ids.

**Line 805** records how many were considered, for the caller's telemetry.

**Lines 807–811, the empty case.** No candidates means no plan. The elapsed time is
still measured and reported, so a caller watching `solve_us` sees a consistent
number on every path, and the zero-initialised `Plan` correctly reports zero runs
and zero expected pollen.

### Step 1 — the pairwise tables, lines 813–826

```cpp
        // --- step 1: pairwise straight-line times and directions ---------------
        const int nodes = ctx.count + 1;
        for (int i = 0; i < nodes; ++i) {
            for (int j = 0; j < nodes; ++j) {
                const pedro::Pose& from = ctx.node(i);
                const pedro::Pose& to = ctx.node(j);
                const double dx = to.x - from.x;
                const double dy = to.y - from.y;
                const double distance = std::sqrt(dx * dx + dy * dy);
                ctx.degenerate[i][j] = distance < DEGENERATE_LEG_IN;
                ctx.straight[i][j] = ctx.degenerate[i][j] ? 0.0 : straight_time(distance, ctx.k);
                ctx.direction[i][j] = ctx.degenerate[i][j] ? 0.0 : std::atan2(dy, dx);
            }
        }
```

**What it does:** fill `ctx.straight`, `ctx.direction` and `ctx.degenerate` for
every ordered pair of nodes, depot included.

**`nodes = ctx.count + 1`** — the `+ 1` is the depot, per the numbering
convention.

The three tables are filled together from one distance computation per pair:
degenerate pairs get zero time and zero direction (both of which are then never
used, because `leg_cost` short-circuits on the degenerate flag), and everything
else gets the modelled straight-line time and the travel bearing.

**Cost:** at most 21 × 21 = 441 entries, one `sqrt` and one `atan2` each. That is
paid once and amortised over the tens of thousands of leg evaluations the search
performs.

**Note on symmetry:** `straight[i][j] == straight[j][i]` and
`direction[i][j] == direction[j][i] + pi`. The tables are filled both ways anyway,
because the inner loops index them directly and a branch to normalise the order
would cost more than the memory saved.

### Discounts, capacity and horizon — lines 828–849

```cpp
        const double overhead = std::max(0.0, params.score_overhead_s);
        double gamma[MAX_RUNS];
        const double discount = std::clamp(params.discount, 0.0, 1.0);
        gamma[0] = 1.0;
        for (int r = 1; r < MAX_RUNS; ++r) gamma[r] = gamma[r - 1] * discount;

        const int capacity = plan.uncapacitated
                ? ctx.count
                : std::clamp(params.capacity, 1, MAX_RUN_LEN);
```

**`overhead`** — `score_overhead_s`, floored at zero. This is the fixed cost of
being at the depot: scoring, settling, whatever the cycle needs beyond driving.
**It is what makes short runs wasteful, and therefore what gives the capacity
constraint its teeth** — without it, splitting a run in two would be nearly free
and the planner would have no reason to fill runs.

**`gamma[MAX_RUNS]`** — the per-run time discounts, built as successive powers of
`discount`: `gamma[0] = 1`, `gamma[1] = discount`, `gamma[2] = discount^2`, and so
on. `discount` is clamped to `[0, 1]` so a caller cannot make the future cost
*more* than the present, which would invert the whole receding-horizon logic.

The default is `1.0`, meaning no discounting. The header records why: measured over
40 random fields per setting, clearing the field under a receding horizon, 1.0 beat
0.75 at every capacity and both field shapes by 0.1 to 0.4 percent. That makes
sense once you see that earliness is handled by the separate `front_load` key —
with the haul already protected, discounting the future only distorts the
partition. Lower it if you would rather have a fast first run than a good
three-run plan, for instance near the end of a match.

**`capacity`** — in the uncapacitated variant, everything fits in one run, so it is
set to the candidate count. Otherwise it is the caller's value clamped into
`[1, MAX_RUN_LEN]`.

```cpp
        // How many runs it is worth thinking about: what was asked for, capped by
        // how many the candidates can fill and by how many could ever fit in the
        // time left.
        int horizon = 1;
        if (!plan.uncapacitated) {
            const int by_candidates = (ctx.count + capacity - 1) / capacity;
            const int by_clock = overhead > EPSILON
                    ? static_cast<int>(params.remaining_s / overhead)
                    : MAX_RUNS;
            horizon = std::clamp(params.lookahead_runs, 1, MAX_RUNS);
            horizon = std::max(1, std::min({horizon, by_candidates, std::max(1, by_clock)}));
        }
```

**`horizon`** — how many runs to weigh together. This is the `n` of the receding
horizon, and it is the caller's `lookahead_runs` reduced by two realities:

- **`by_candidates`** — there is no point planning four runs when there are only
  five candidates and a capacity of 3; that is two runs' worth of pollen. The
  ceiling division rounds up so a partial final run still counts.
- **`by_clock`** — there is no point planning runs that could not possibly happen.
  Each run costs at least `overhead`, so at most `remaining_s / overhead` runs can
  fit however fast the driving is. When `overhead` is zero this bound is
  meaningless and `MAX_RUNS` is used instead.

The final clamp (line 848) takes the smallest of the three and never goes below 1.

**Why capping the horizon matters beyond speed.** The horizon is the base of the
front-load weighting. A horizon larger than the number of runs that can actually
happen would spread the weighting across phantom runs and dilute the preference for
filling run 0 — the one run that will really be driven.

In the uncapacitated variant the horizon stays at 1: there is only ever one run, so
there is nothing to look ahead over.

### Steps 2 and 3 — the starting tour, lines 851–858

```cpp
        // --- steps 2 and 3: giant tour, improved to a local optimum ------------
        int tour[MAX_CANDIDATES];
        int best_tour[MAX_CANDIDATES];
        build_giant_tour(ctx, tour);
        optimize_sequence(tour, ctx.count, ctx);
        for (int i = 0; i < ctx.count; ++i) best_tour[i] = tour[i];

        const double budget = std::max(0.0, params.remaining_s - overhead);
```

**`tour`** — the working tour, perturbed and re-optimised on every iteration of the
anytime loop.

**`best_tour`** — the tour that produced the incumbent plan. Kept separately so
each kick starts from the best known tour rather than from wherever the last
failed attempt ended up.

**Line 854** builds the nearest-neighbour giant tour; **line 855** drives it to a
2-opt/Or-opt local optimum; **line 856** records it as the best so far.

**`budget`** (line 858) — the time available for *driving* in the uncapacitated
variant: the match time left, less the one scoring cycle that still has to be paid
for. Note this is a distance-in-seconds budget for the route, and has nothing to do
with `compute_budget_ms`, which bounds the solver's own CPU time. Two different
budgets, both in the same function.

### Step 4 — `construct`, lines 860–880

```cpp
        auto construct = [&](const int* source) {
            if (plan.uncapacitated) {
                return build_uncapacitated(source, ctx, budget, gamma, overhead);
            }
            Solution built = split_tour(source, ctx, capacity, horizon, gamma, overhead);
            if (!built.valid) return built;

            int unused[MAX_CANDIDATES];
            int unused_count = 0;
            bool taken[MAX_CANDIDATES] = {};
            for (int r = 0; r < built.count; ++r) {
                for (int m = 0; m < built.length[r]; ++m) taken[built.members[r][m]] = true;
            }
            for (int c = 0; c < ctx.count; ++c) {
                if (!taken[c]) unused[unused_count++] = c;
            }
            polish_solution(built, ctx, capacity, gamma, overhead, unused, unused_count);
            return built;
        };
```

**Summary:** turn a tour into a complete, scored plan. This lambda is the bridge
between the tour-level search and the plan-level objective, and it is called once
per anytime iteration.

**Line 861–863, the uncapacitated branch** — hand the tour to
`build_uncapacitated` and return.

**Line 864–865, the capacitated branch** — cut the tour into runs with the exact
dynamic program. If the split failed, return the invalid solution unchanged;
`better` will reject it.

**Lines 867–875, computing the leftovers.** Mark every candidate that made it into
some run, then collect the rest into `unused`. These are the pollen the split
declined, and `polish_solution`'s third move type trades against them.

**Line 876, the polish** — the cross-run moves that the split structurally cannot
make.

**Why this is a lambda rather than a function.** It captures `plan.uncapacitated`,
`ctx`, `capacity`, `horizon`, `gamma`, `overhead` and `budget` — seven parameters
that never change during the solve. Capturing them by reference keeps the call site
inside the loop down to `construct(tour)` and makes it obvious that a tour is the
*only* thing that varies between iterations.

```cpp
        Solution best = construct(best_tour);

```

**Line 880, the first construction.** This is the answer the function is already
prepared to return. Everything after this point is optional improvement — which is
exactly what "anytime algorithm" means, and why shrinking the budget degrades the
plan gracefully instead of breaking it.

### Step 6 — the anytime loop, lines 882–911

```cpp
        // --- step 6: anytime improvement --------------------------------------
        Rng rng(params.seed);
        const double budget_ms = std::max(0.0, params.compute_budget_ms);
        int iterations = 0;
        int since_improvement = 0;
        bool exhausted = false;
        const int stagnation_limit = std::max(1, params.stagnation_limit);

```

**`rng`** — seeded from `params.seed`, so the whole search is reproducible.

**`budget_ms`** — the CPU budget, floored at zero.

**`iterations`** — how many kicks were attempted. Reported in the `Plan`.

**`since_improvement`** — consecutive failed kicks. The stagnation detector.

**`exhausted`** — set if the wall clock, rather than the iteration cap or
stagnation, is what stopped the search. Reported in the `Plan` so the caller can
tell "I converged" from "I ran out of time" — the latter is a signal that the
budget is too tight for the problem size.

**`stagnation_limit`** — floored at 1, since a limit of zero would mean the loop
never runs at all.

```cpp
        while (iterations < params.iteration_cap && since_improvement < stagnation_limit) {
            const double elapsed_ms = std::chrono::duration<double, std::milli>(
                    std::chrono::steady_clock::now() - started).count();
            if (elapsed_ms >= budget_ms) {
                exhausted = iterations > 0;
                break;
            }
            ++iterations;

            for (int i = 0; i < ctx.count; ++i) tour[i] = best_tour[i];
            double_bridge(tour, ctx.count, rng);
            optimize_sequence(tour, ctx.count, ctx);

            Solution candidate = construct(tour);
            if (better(candidate, best, plan.uncapacitated)) {
                best = candidate;
                for (int i = 0; i < ctx.count; ++i) best_tour[i] = tour[i];
                since_improvement = 0;
            } else {
                ++since_improvement;
            }
        }
```

**The three exit conditions**, in order of how they read:

1. **`iterations < params.iteration_cap`** — an absolute ceiling (20000 by
   default) so the loop is bounded even if the clock misbehaves.
2. **`since_improvement < stagnation_limit`** — give up when the search stops
   finding anything. At these problem sizes the local optimum is usually reached
   within a handful of restarts, and spending the remaining 40 ms on nothing is
   40 ms the rest of the robot could have had. This is the condition that actually
   fires most of the time.
3. **The clock check (lines 891–896)** — the hard cap. Checked at the *top* of
   each iteration, so an iteration is never started that cannot be afforded.

**`exhausted = iterations > 0`** (line 894) is a small but deliberate detail: if the
budget was already gone before the first iteration, the search never ran at all,
and reporting that as "exhausted" would be misleading. It is reported as zero
iterations instead.

**The iteration body (lines 899–903)**

- **Line 899** — start from the best known tour, not from the last one tried. This
  is what makes the search a proper iterated local search rather than a random
  walk.
- **Line 900** — kick it with the double bridge.
- **Line 901** — re-optimise to a new local optimum.
- **Line 903** — build a full plan from it.

**Acceptance (lines 904–910).** If the new plan is better by the same three-key
rule used everywhere, it becomes the incumbent, its tour is remembered, and the
stagnation counter resets. Otherwise the stagnation counter advances and the tour
is discarded.

Note that the incumbent is only ever *replaced by something better*. That is the
guarantee stated in the header: `solve` never returns something worse than its own
first construction.

```cpp
        plan.iterations = iterations;
        plan.budget_exhausted = exhausted;
        plan.objective = best.valid ? best.objective : 0.0;

        if (!best.valid) {
            plan.solve_us = std::chrono::duration<double, std::micro>(
                    std::chrono::steady_clock::now() - started).count();
            return plan;
        }
```

**Lines 913–915** record the search telemetry into the `Plan`.

**Lines 917–921, the failure path.** If no valid plan was ever constructed, report
the elapsed time and return a `Plan` that says zero runs. Callers must check
`run_count` or `fitting_runs` before driving anything.

### Step 5 — fitting the deadline, lines 923–975

This runs *after* the search, once, on the winner — not inside the loop. The search
optimises the plan; this decides how much of the plan there is actually time for.

```cpp
        // --- step 5: fit the deadline -----------------------------------------
        double accumulated = 0.0;
        plan.run_count = std::min(best.count, MAX_RUNS);

        for (int r = 0; r < plan.run_count; ++r) {
            Run& run = plan.runs[r];
            run.count = best.length[r];
            run.time_s = best.seconds[r];
            run.fits = false;

```

**`accumulated`** — total committed time as runs are accepted, overhead included.

**`plan.run_count`** — how many runs the winning solution holds, bounded by the
array size.

**Lines 928–931** start each `Run` from the solution's run: its length, its time,
and `fits` pessimistically false until proven otherwise.

```cpp
            if (accumulated + run.time_s + overhead > params.remaining_s) {
                // Shed the most expensive member and try again, rather than
                // throwing the whole run away: a partial run still scores.
                while (run.count > 0 &&
                       accumulated + best.seconds[r] + overhead > params.remaining_s) {
                    int worst = 0;
                    double worst_saving = -INF;
                    for (int m = 0; m < best.length[r]; ++m) {
                        const double saving = detour_cost(best.members[r], best.length[r], m, ctx);
                        if (saving > worst_saving) {
                            worst_saving = saving;
                            worst = m;
                        }
                    }
                    for (int m = worst; m + 1 < best.length[r]; ++m) {
                        best.members[r][m] = best.members[r][m + 1];
                    }
                    --best.length[r];
                    best.seconds[r] = optimize_run(best.members[r], best.length[r], ctx);
                    run.count = best.length[r];
                    run.time_s = best.seconds[r];
                }
            }
```

**The overrun case.** If this run would push the total past `remaining_s`, the
planner does **not** throw the run away. It sheds members one at a time until the
run fits, because a partial run still scores — collecting one pollen in the time
left is strictly better than collecting none.

**Lines 938–946** pick the member with the highest `detour_cost`: the one buying
the least for what it costs, exactly as in the uncapacitated trim.

**Lines 947–950** remove it by shifting the tail down and shortening the run.

**Line 951** re-optimises the shortened run, which often recovers more time than
the removal alone, and updates `run.count` and `run.time_s` from it.

**The loop condition (lines 936–937)** re-tests against the freshly computed
`best.seconds[r]`, so the shedding continues until the run genuinely fits or is
empty.

```cpp
            for (int m = 0; m < run.count; ++m) {
                const int candidate = best.members[r][m];
                run.ids[m] = ctx.id[candidate];
                run.poses[m] = ctx.pose[candidate];
            }

            if (run.count > 0 && accumulated + run.time_s + overhead <= params.remaining_s) {
                run.fits = true;
                accumulated += run.time_s + overhead;
                plan.expected_collected += run.count;
                ++plan.fitting_runs;
            } else {
                // Nothing after this one can fit either.
                for (int later = r; later < plan.run_count; ++later) {
                    plan.runs[later].fits = false;
                }
                break;
            }
        }
```

**Lines 957–961, translating back out.** For each surviving member, the candidate
index is converted into the pollen `id` and the field `pose` the caller needs. This
is the only place `ctx.id` is read, and it is the boundary between the solver's
dense internal index space and the rest of the robot.

**Lines 963–967, acceptance.** A run fits if it is non-empty and still inside the
deadline. Accepting it advances `accumulated` by the run's time plus one scoring
overhead, adds its haul to `expected_collected`, and increments `fitting_runs`.

**Lines 968–974, the cut-off.** If this run does not fit, nothing after it can
either — later runs are strictly further into the future and the clock only moves
one way. Every remaining run is marked as not fitting and the loop stops.

This is why `Plan` documents that "fitting runs come first": the array may contain
runs beyond `fitting_runs`, and those are planning artefacts, not instructions. A
caller must drive only `runs[0 .. fitting_runs-1]`, and in practice drives only
`runs[0]` before re-solving.

```cpp
        plan.committed_time_s = accumulated;
        plan.solve_us = std::chrono::duration<double, std::micro>(
                std::chrono::steady_clock::now() - started).count();
        return plan;
    }
```

**Line 977** records the committed time — what the fitting runs will actually cost,
overhead included.

**Line 978** measures the solve for real, in microseconds, rather than assuming it
met its budget. Comparing `solve_us` against `compute_budget_ms * 1000` on the
driver station is how you find out whether the budget is realistic on the actual
hardware.

```cpp
}
```

End of `namespace butine`, and of the file.

---
## Appendix A — every function at a glance

| Function | Lines | Linkage | One-line summary |
|---|---|---|---|
| `wrap_pi` | 22–26 | file-local | Fold an angle into (−pi, +pi] so turn costs use the shortest rotation. |
| `Rng::next` | 33–38 | file-local | One xorshift step; deterministic pseudo-randomness with no global state. |
| `Rng::below` | 39 | file-local | A random index in `[0, bound)`, safe when `bound` is non-positive. |
| `Ctx::node` | 60–62 | file-local | Node index to pose, implementing "node 0 is the depot". |
| `leg_cost` | 65–83 | file-local | Cost of one leg from the tables, including the turn onto it. The atom of the cost model. |
| `sequence_time_from` | 98–116 | file-local | Closed-run time, resumed from a prefix bookmark. The workhorse. |
| `sequence_time` | 119–122 | file-local | Closed-run time from the depot over the whole sequence. |
| `Prefix::build` | 132–140 | file-local | Fill the table of prefix bookmarks for a route. |
| `optimize_sequence` | 145–216 | file-local | 2-opt + Or-opt to a local optimum; reorders the route in place. |
| `optimize_run` | 221–242 | file-local | Optimal within-run order by full permutation search; falls back to the heuristic above `EXACT_ORDER_LIMIT`. |
| `front_load_weight` | 260–265 | file-local | Positional-notation weight making earliness an exact lexicographic key. |
| `score_solution` | 273–284 | file-local | Compute a plan's three ranking keys. |
| `better_value` | 316–326 | file-local | The three-key comparison: more pollen, then earlier, then faster. |
| `build_giant_tour` | 328–350 | file-local | Turn-aware nearest-neighbour ordering of all candidates. |
| `double_bridge` | 352–369 | file-local | The kick: A\|B\|C\|D becomes A\|C\|B\|D, which 2-opt cannot undo. |
| `split_tour` | 379–481 | file-local | Exact dynamic program cutting the tour into runs, skips allowed. |
| `polish_solution` | 493–603 | file-local | Cross-run relocate, swap and exchange-with-unused moves. |
| `detour_cost` | 615–622 | file-local | What one member costs the route where it currently sits. |
| `build_uncapacitated` | 624–683 | file-local | Single-tour orienteering variant: trim to the deadline, then grow back. |
| `better` | 685–697 | file-local | Compare two complete plans; the anytime loop's acceptance test. |
| `straight_time` | 702–716 | **public** | Trapezoidal/triangular time for a straight leg at rest at both ends. |
| `leg_time` | 718–738 | **public** | Pose-based twin of `leg_cost`, for calibration. |
| `run_time` | 740–760 | **public** | Pose-based twin of `sequence_time`, for calibration. |
| `solve` | 762–981 | **public** | The planner. Pollen map plus depot plus params, in; `Plan`, out. |

## Appendix B — every type at a glance

| Type | Lines | Summary |
|---|---|---|
| `Rng` | 30–40 | 32-bit xorshift generator; one word of state, seeded per solve. |
| `Ctx` | 46–63 | The whole problem instance: depot, candidate poses and ids, kinematics, and the three pairwise tables. |
| `Walk` | 87–91 | A bookmark into a route: cost so far, current heading, current node. |
| `Prefix` | 129–141 | All `length + 1` bookmarks for a route, so a move that leaves the front alone can be priced from where it starts. |
| `Solution` | 244–254 | A complete multi-run plan in candidate indices, with its three ranking keys. |
| `Value` | 309–314 | One point in the comparison space: reachable, collected, front-loaded, cost. |
| `Scored` | 780 | Local to `solve`: a candidate's reach time paired with its index in the caller's vector. |

## Appendix C — index of working variables

Struct members are documented in their own sections. This covers the locals that
recur across functions.

| Name | Where | What it holds |
|---|---|---|
| `members` | route functions | A route as candidate indices. |
| `length` | route functions | How many members that route has. |
| `scratch` | many | A trial route or run, built before being priced. Frequently only partially written; see the stale-head contract in section 10. |
| `segment` | `optimize_sequence` | The 1–3 members currently being relocated by Or-opt. |
| `head` | `optimize_sequence` | The `Prefix` bookmark table for the current route. |
| `improved` | local searches | Did this pass accept anything? Drives the repeat-to-convergence loop. |
| `guard` | local searches | Hard ceiling on passes (32 in `optimize_sequence`, 16 in `polish_solution`) so a cost-model quirk cannot spin forever. |
| `best` | many | The incumbent cost or plan. Starts at `INF` or invalid so the first real answer always wins. |
| `best_order` | `optimize_run` | The winning permutation, since `scratch` keeps being advanced past it. |
| `tour` / `best_tour` | `solve` | The working tour and the tour that produced the incumbent plan. |
| `value` | `split_tour` | The dynamic-programming table, indexed `[tour position][runs used]`. |
| `back_position` / `back_length` | `split_tour` | Breadcrumbs: where a state came from, and how many tour positions that step consumed (`0` means skipped). |
| `chosen` / `chosen_runs` / `chosen_position` | `split_tour` | The winning end state and where it sits in the table. |
| `spare` / `spare_count` | `polish_solution` | The pollen the split declined, available for trade. |
| `trial_a` / `trial_b` | `polish_solution` | Candidate member lists for the two runs a move is considering. |
| `delta` | `polish_solution` | Change in discounted time from a move — the two affected runs only. |
| `front_delta` | `polish_solution` | Change in the front-load key from a relocate. Outranks `delta`. |
| `dropped` | `build_uncapacitated` | Which candidates the trim removed, so the grow-back pass knows what to try. |
| `worst` / `worst_saving` | trim loops | The member with the highest detour cost, the next to be shed. |
| `accumulated` | `solve` step 5 | Committed time as runs are accepted, overhead included. |
| `overhead` | `solve` | `score_overhead_s`, floored at zero. Charged once per run. |
| `gamma` | `solve` | Per-run time discounts, `gamma[r] = discount^r`. |
| `horizon` | `solve` | Runs to weigh together, after capping by candidates and by clock. |
| `budget` | `solve` | Seconds available for *driving* in the uncapacitated variant. |
| `budget_ms` | `solve` | Milliseconds available for *computing*. A different budget entirely. |
| `since_improvement` | `solve` | Consecutive failed kicks; the stagnation detector. |

## Appendix D — call graph

```text
solve
 |-- straight_time                      (candidate ranking, then the pairwise table)
 |-- build_giant_tour --> leg_cost
 |-- optimize_sequence --> sequence_time_from, sequence_time, Prefix::build --> leg_cost
 |-- construct (lambda)
 |     |-- split_tour --> optimize_run --> sequence_time / optimize_sequence
 |     |                  better_value, front_load_weight, score_solution
 |     |-- polish_solution --> optimize_run, front_load_weight
 |     \-- build_uncapacitated --> optimize_sequence, detour_cost, score_solution
 |-- double_bridge --> Rng::below
 |-- better --> better_value
 \-- detour_cost, optimize_run          (the deadline fit)

leg_cost   --> wrap_pi                  (table-based; solver-internal)
leg_time   --> straight_time, wrap_pi   (pose-based; public, for calibration)
run_time   --> leg_time, wrap_pi        (pose-based; public, for calibration)
```

## Appendix E — the inputs and outputs, from `butine.h`

`solve` reads these and nothing else. They are declared in the header but listed
here because the body's behaviour is unreadable without them.

### `Kinematics` — the motion model

| Field | Default | Meaning |
|---|---|---|
| `cruise_speed_in_s` | 72.0 | Top speed, in/s. Deliberately conservative: the robot is recorded at 95.3 in/s on a fresh 13.65 V pack but reads 80 in/s at 11.5 V. Planning against a speed the robot cannot reach makes every estimate optimistic by the same factor — harmless for *ranking*, useless for the *deadline*. |
| `accel_in_s2` | 162.0 | Acceleration, in/s². Derated from the configured 202. |
| `decel_in_s2` | 173.0 | Braking, in/s². Zero-power decel times the decel multiplier. |
| `omega_max_rad_s` | 4.0 | Turn rate. A seed — measure it with a standing turn. |
| `turn_settle_s` | 0.08 | Time for the heading controller to stop ringing after a swing. |
| `turn_deadband_rad` | 0.09 | About 5 degrees, below which turning is free. |
| `turn_coupling` | 1.0 | How much of a turn overlaps the drive. 1.0 is the mecanum-correct model; 0.0 reduces the problem to a symmetric TSP and is useful as an A/B. |
| `pickup_dwell_s` | 0.25 | Time stood still at a pollen. Zero if the intake can sweep one up without slowing. |

### `Params` — the knobs

| Field | Default | Meaning |
|---|---|---|
| `capacity` | 3 | Pollen per run before the robot must return. **Zero or less selects the uncapacitated variant.** |
| `lookahead_runs` | 3 | The `n` of the receding horizon. 1 is plain greedy. |
| `discount` | 1.0 | `gamma`, applied to time only. See section on discounts. |
| `remaining_s` | 30.0 | Time left to work with. The planner owns no match clock; the caller supplies this. |
| `score_overhead_s` | 2.0 | Fixed cost of being at the depot. What makes short runs wasteful. |
| `candidate_cap` | 16 | Only the cheapest-to-reach this many pollen are considered. What makes solve time independent of map size. |
| `compute_budget_ms` | 45.0 | Wall-clock cap on the solve itself. |
| `iteration_cap` | 20000 | Absolute ceiling on anytime iterations. |
| `stagnation_limit` | 40 | Give up after this many consecutive failed kicks. |
| `min_confidence` | 0.0 | Reject detections below this confidence. |
| `seed` | `0x9E3779B9` | Makes the search reproducible. |
| `kinematics` | — | The motion model above. |

### `Run` and `Plan` — the output

| Field | Meaning |
|---|---|
| `Run::ids` | `limelight::Pollen` ids, in driving order. |
| `Run::poses` | Their field positions, same order. |
| `Run::count` | How many members this run has. |
| `Run::time_s` | Depot to members to depot, travel only — overhead excluded. |
| `Run::fits` | Whether this run survived the deadline fit. |
| `Plan::depot` | Echo of the depot the plan was built around. |
| `Plan::run_count` | Runs planned. Fitting ones come first. |
| `Plan::fitting_runs` | How many of them fit in `remaining_s`. **Drive only these.** |
| `Plan::expected_collected` | Pollen collected by the fitting runs. |
| `Plan::committed_time_s` | Their total time, overhead included. |
| `Plan::objective` | `J`, the value actually minimised. Diagnostic. |
| `Plan::candidates_considered` | How many pollen survived the filter and cap. |
| `Plan::solve_us` | Measured solve time, microseconds. Not assumed — measured. |
| `Plan::iterations` | Anytime iterations performed. |
| `Plan::budget_exhausted` | True if the wall clock, rather than convergence, stopped the search. |
| `Plan::uncapacitated` | Which variant ran. |

### How it is called

```cpp
butine::Params params{};
params.remaining_s = seconds_left_in_match;
butine::Plan plan = butine::solve(pollen_map::snapshot(), depot, params);

for (int m = 0; m < plan.runs[0].count; ++m) {
    drive_to(plan.runs[0].poses[m]);   // only run 0 is ever committed
}
// back at the depot: score, then call solve() again on the updated map
```

The receding horizon is not optional bookkeeping — it is the contract. Runs 1 and
beyond exist to make run 0 a good decision, and are discarded.

## Appendix F — invariants worth not breaking

1. **Node 0 is the depot; candidate `c` is node `c + 1`.** Every `+ 1` and every
   `MAX_CANDIDATES + 1` in the file is this convention. Candidate indices and node
   indices are different numbering systems.

2. **`leg_cost` and `leg_time` must agree exactly**, as must `sequence_time` and
   `run_time`. One pair is table-based for the solver, the other pose-based for
   calibration. A divergence would not crash; it would make the calibration opmode
   report against a model the planner does not use.

3. **`Prefix::build` and `sequence_time_from` must agree exactly.** If they drift,
   the optimiser accepts moves that are not improvements, and the local searches
   stop converging.

4. **The stale-head contract.** `sequence_time_from` resumes from the bookmark's
   `at` field and never reads `members[p - 1]`. That is what allows trial routes to
   be built with a stale head. "Fixing" it by copying the whole route would make the
   hottest loop in the planner noticeably slower.

5. **`horizon` is the planning horizon, not the run count.** Front-load weights
   must be built on the same base for every solution being compared, or a plan with
   more runs wins ties it should lose.

6. **Relocate must respect front-load.** A cross-run move that pushes pollen into a
   later run must be rejected however much time it saves. Without that rule the
   polish undoes the split, and run 0 — the only run that actually happens — comes
   back short.

7. **`solve` never returns worse than its first construction.** The anytime loop
   only ever replaces the incumbent with something strictly better under `better`.

8. **No allocation after entry.** Every buffer is a fixed-size stack array sized
   from `MAX_CANDIDATES` / `MAX_RUNS` / `MAX_RUN_LEN`. Raising those constants
   raises stack usage quadratically through `split_tour`'s tables.

9. **Coordinates are unmirrored PINK.** `pedro` mirrors for BLUE at path-build
   time. Nothing handed to `solve` may be pre-mirrored.

## Appendix G — tuning, and what each knob actually does

| You want | Change | Effect |
|---|---|---|
| A faster first run, at the cost of the overall plan | lower `discount` toward 0.75 | Later runs are discounted, so the split stops sacrificing run 0's time for the partition's benefit. Measured as slightly worse overall (0.1–0.4 percent over 40 random fields), so only worth it near the end of a match. |
| Plain greedy, no lookahead | `lookahead_runs = 1` | The split plans one run. Fast, and strands isolated pollen. |
| Less CPU | lower `candidate_cap` | The dominant lever. Solve cost grows with the square of the candidate count in the tables and worse in the searches. |
| Less CPU, cheaply | lower `stagnation_limit` | Stops the anytime loop sooner. Usually the loop is already stopping on stagnation rather than on the clock. |
| To know whether the budget is realistic | read `Plan::solve_us` and `Plan::budget_exhausted` | If `budget_exhausted` is true on a normal field, the cap is too tight for the candidate count. |
| To test how much the turn model is worth | `kinematics.turn_coupling = 0.0` | Collapses the problem to a symmetric TSP. Compare plan quality against the default. |
| One long tour with no returns | `capacity = 0` | Switches to `build_uncapacitated`. The lookahead stops applying. |
| Optimal ordering in longer runs | raise `EXACT_ORDER_LIMIT` (source constant) | Factorial: 5 members is 120 orderings, 6 is 720, 7 is 5040. The current limit of 5 is where the cost stops being free. |

## Appendix H — known limitations

**The pickup is modelled as a point, not as an approach.** Today a pollen is a
position and any heading collects it, so leg cost is symmetric in distance and the
tangent-heading interpolation handles facing for free. In reality the intake sits
on one face of the robot, so the robot has to **arrive pointing at the pollen**.

The header sketches the fix, deliberately left out until the intake geometry is
measured on the robot:

- add an `INTAKE_REACH` and offset the pickup pose from the pollen centre along the
  chosen approach heading;
- make the approach heading a decision variable per pollen, so cost becomes a
  genuine function of the (previous, current, next) triple rather than a turn
  penalty bolted onto a symmetric pair cost;
- discretise that heading (16 sectors is plenty) and let `optimize_run` choose it
  per pollen — with the exact permutation search that is 24 × 16³ for a three-pollen
  run, still trivial;
- **2-opt stops being valid as written**, because reversing a segment changes every
  approach inside it. Or-opt survives; prefer it.

**The motion model is seeded, not measured.** `omega_max_rad_s` in particular is a
guess. The planner test opmode prints predicted against actual time for every leg
it drives; that is the tool for fixing this, and until it has been run against the
real robot, every absolute time this file produces should be read as a ranking
signal rather than as a prediction.

**The candidate pre-filter ignores turning.** Candidates are ranked for the cap by
straight-line time from the depot only. A pollen that is close but awkwardly placed
can therefore displace one that is slightly further but on the way. Given that the
cap (16) is usually near or above the real map size, this rarely binds.

**`detour_cost` does not re-optimise.** Both trim paths rank members by what they
cost *where they currently sit*, which can shed a member that a re-optimised route
would have kept. This is a deliberate trade for being `O(length)` instead of
`O(length² · optimise)` inside the anytime loop.
