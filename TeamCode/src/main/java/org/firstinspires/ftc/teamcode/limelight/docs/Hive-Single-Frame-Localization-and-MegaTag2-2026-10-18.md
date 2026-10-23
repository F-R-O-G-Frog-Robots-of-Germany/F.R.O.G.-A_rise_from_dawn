# Hive localization from one image: hinge geometry, MegaTag1 and MegaTag2

**Goal:** Find a credible way to correct robot X, Y and heading within roughly 100 ms, including intermediate Hive positions, without requiring useful robot travel between readings.

18 October 2026. Research and synthetic experiments only. This is a comparison of approaches, not an implementation plan. All repository changes for this investigation are Markdown inside the Limelight folder. No camera configuration, Java code, competition behavior or hardware is changed. The robot is not built.

**Subsequent implementation, authorized 18 October:** The isolated `HivePoseEstimator`, `HiveLimelightObservation` and `HiveGeometry` now implement the single-tag hinge conversion. Full nominal tag-to-hinge offsets were subsequently extracted from the official STEP; remaining print-frame and mounting assumptions are commented. See the [package README](../README.md#hive-pose-from-a-moving-tag) for provenance and usage. The original research below records what was known before that extraction. No competition pose integration or native MT1 object-map mode was added.

## 1. Main result

The most useful observation is that a moving tag can describe a **fixed hinge point and a fixed hinge direction**. The tag does not need to be at an endpoint. With its full camera-relative 3D pose, known tag-to-hinge geometry and robot gravity direction, both quantities can be recovered from one exposure. Together they determine robot position and heading. The Hive angle can be eliminated from this calculation.

This is our geometric derivation, not a claim that Limelight already implements an articulated-Hive localizer. It is equivalent to the earlier tag-pose-plus-gravity construction, but removes an intermediate quantity that the owner does not need.

There is also a promising way to use Limelight's native processing: describe **one rigid Hive body** with an object-local tag map, obtain its full relative pose using MegaTag1, then apply the hinge conversion. Limelight documents object maps and full 3D localization; their use together for this particular moving Hive remains a proposal requiring firmware validation.

MegaTag2 deserves consideration, but its supplied-heading assumption and fixed-map model prevent it from independently solving the complete problem as currently posed. More detail follows in section 3.

The decisive distinction is **spatial information within one image**, rather than displacement between two images. A tag already has four corners at known metric separation. Four tags on a Cell supply a much wider same-exposure pattern. The robot can remain stationary while that information is measured.

## 2. Constraints and physical geometry

The owner's constraints are retained:

- Required output is X, Y and heading; rising/falling classification is unnecessary.
- Do not wait for the Hive to settle into either endpoint.
- Do not require 2–3 inches of robot travel within a 100 ms observation window.
- Existing odometry is already approximately right; treat its few inches/degrees of error as uncertainty, not an exact input.
- Code points named after Hives are desired shooting positions. They are not physical outlines, tag locations or hinge geometry.

The [original geometry report](Hive-Kippzustand-und-Roboterlokalisierung-Konzept-2026-10-18.md) derives physical hinge coordinates independently from the official CAD. In its centered world frame, +x is the audience's right, +y points away from the audience, and +z is upward. The derived nominal reference points are:

```text
H_red  = (-12.75, 0, 43.95) inches
H_blue = (+12.75, 0, 43.95) inches
signed hinge direction a_W = (1, 0, 0)
```

These are our nominal CAD-derived coordinates, not measured field calibration or directly published coordinate entries. Both geometric axis lines are collinear, but each Hive has its own independent rotation. The particular point selected on each axis matters: tag-to-axis distance alone leaves an unknown translation along that axis.

The [official manual, sections 9.6 and 9.9](https://ftc-resources.firstinspires.org/ftc/game/manual), specifies four tags beneath each Cell, 16 moving tags overall, and 3.25-inch tag black squares. Its sticker dimensions give tag centers at local coordinates −6.5, −2.75, +2.75 and +6.5 inches, a 13-inch outer-center span. Complete tag-to-hinge rigid transformations and print orientations are still needed. Opening heights alone do not determine those transformations. The [official STEP model](https://ftc-resources.firstinspires.org/ftc/field/field-cad-step) is the geometric source; the earlier report records the inspected file hash and frame conversion.

## 3. What MegaTag2 can and cannot solve here

### Documented behavior

[Limelight's MegaTag2 documentation](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-robot-localization-megatag2) requires a tag map, calibrated camera mounting and externally supplied robot heading. It can produce translation from one image, including single-tag observations. The general API optionally accepts fuller orientation information. Its internal-IMU assistance modes described on that page are for **Limelight 4**, not the 3A.

### Consequence for FROG

MT2 avoids the need for robot-motion triangulation. However, its heading is conditioned on the supplied orientation; it is not an independent visual measurement that can simply replace a drifting heading estimate. Likewise, a fixed field map cannot represent changing world poses of Hive tags. There is no documented articulated-landmark input that supplies a hinge model and lets MT2 jointly infer its current rotation.

Therefore, ordinary MT2 with field yaw and a nominal endpoint map does not solve intermediate Hive localization. A Hive-local map also changes the orientation reference frame: feeding ordinary field yaw into that map is inconsistent. Correctly transforming that orientation would itself require the missing body orientation. Per-frame map uploads are not a documented exposure-synchronized solution.

MT2 remains useful when an independent heading and a genuinely fixed tag map are available. That is a different measurement situation; the official Hive tags cannot be assumed fixed. Its returned yaw must not be fused as a second independent heading observation after supplying that same yaw as input.

### FTC API details matter to the 100 ms target

The [FTC programming guide](https://docs.limelightvision.io/docs/docs-limelight/apis/ftc-programming) exposes `updateRobotOrientation(yaw)` and `getBotpose_MT2()`. The locally cached FTC Hardware 12.0.0 sources show that the orientation update performs a synchronous HTTP POST with a 15-second response-read timeout and a separate 100-ms connect timeout. These are configured failure timeouts, not typical latency or an overall deadline for the complete call. It belongs with the existing vision I/O owner in any future application, not in the important control loop.

The typed SDK call has no orientation-acquisition timestamp argument. Its `getStaleness()` is based on result construction/receipt time, not a guaranteed image-exposure timestamp. Neither polling frequency nor this value alone proves a sub-100-ms exposure-to-correction age. FRC NetworkTables timing assumptions must not be copied into the FTC path without checking them.

Local source inspected: `org.firstinspires.ftc:Hardware:12.0.0`, `Limelight3A.java` (`updateRobotOrientation`, `sendPostRequest`, timeout constants) and `LLResult.java` (construction timestamp and `getBotpose_MT2`). This is SDK-version-specific source evidence.

## 4. A single moving tag can describe a stationary hinge

A [complete numerical example](Hive-Hinge-Full-Numerical-Example-2026-10-18.md) now shows every rotation, heading calculation, field-position subtraction, camera mounting correction and circular-motion consistency check using arbitrary values.

### 4.1 Measurements and notation

`T_AB` transforms coordinates from frame B into frame A. Its rotation is `R_AB` and translation is `t_AB`.

| Symbol | Meaning |
|---|---|
| W, R, C, T | World, robot, optical camera and identified tag frames |
| `R_CT`, `t_CT` | Measured full tag pose in camera coordinates |
| `r_TH` | Vector from the tag origin to the chosen fixed hinge point, expressed in tag coordinates |
| `a_T` | Signed unit hinge-axis direction expressed in tag coordinates |
| `H_W`, `a_W` | That same hinge point and signed direction in world coordinates |
| `R_RC`, `t_RC` | Calibrated camera mounting: camera coordinates into robot coordinates |
| `g_R` | Unit downward gravity direction expressed in the robot frame at exposure time |

`r_TH` and `a_T` are fixed because the tag is rigidly attached to the rotating body. They differ by tag identity and print orientation. The physical hinge point need not be visible.

### 4.2 Construct a virtual hinge observation

```text
h_C = t_CT + R_CT * r_TH
a_C = R_CT * a_T
```

Think of extending an invisible rigid rod from the tag to the hinge. As the Hive tips, the tag moves and rotates, but the end of that rod remains at the same physical hinge point. The tag also carries the hinge direction without changing that direction under rotation about the hinge.

Thus `h_C` and `a_C` describe stationary world geometry in camera coordinates, independently of Hive angle. They still change when the camera moves. This does not require knowing whether the tag is rising or falling, or waiting for two readings.

Algebraically, if a tag point at zero tilt is `r_0` from the hinge and its reference orientation is `B`, then:

```text
tag origin in world = H_W + Rot(a_W, alpha) * r_0
tag rotation        = Rot(a_W, alpha) * B
r_TH                = -transpose(B) * r_0
a_T                 =  transpose(B) * a_W
```

Substituting cancels the rotating point offset. Also `Rot(a_W, alpha) * a_W = a_W`. That is why the Hive angle disappears.

### 4.3 Recover orientation and translation

Gravity supplies vertical direction. The horizontal signed hinge axis supplies the remaining heading direction. Two nonparallel directions determine full orientation. Absolute IMU yaw is not required for this construction.

One explicit version uses orthonormal frames made from these directions. Let `g_W = (0,0,-1)` and normalize all direction vectors. Project the noisy measured axis perpendicular to gravity before normalizing it:

```text
g_C = transpose(R_RC) * g_R
a_C = normalize(a_C - g_C * dot(g_C, a_C))

F_W = columns(a_W, cross(g_W, a_W), g_W)
F_C = columns(a_C, cross(g_C, a_C), g_C)

R_WC = F_W * transpose(F_C)
R_WR = R_WC * transpose(R_RC)
p_WR = H_W - R_WC * h_C - R_WR * t_RC

X       = p_WR.x
Y       = p_WR.y
heading = atan2(R_WR[1,0], R_WR[0,0])  # zero-based matrix indices
```

This construction assumes a horizontal physical hinge. A vertical hinge and gravity would be parallel and leave yaw unobservable. An unsigned hinge line gives a 180-degree ambiguity; known tag orientation and signed model metadata distinguish its directions. Tiny projected-axis length is a rejection condition, not a division to continue through.

On an ideal level floor, `g_R = (0,0,-1)` can be an explicit approximation. With robot tilt, use exposure-matched attitude-derived gravity. Raw accelerometer readings during acceleration are not automatically gravity. Errors in this input remain errors in the result.

### 4.4 Information gained, and information still missing

The previous ambiguity argument concerned a target center or relative target-position vector with insufficient orientation information. A complete metric tag pose has orientation as well. Its hinge direction provides the extra constraint. Consequently that earlier center-only ambiguity does not prove this richer problem is unobservable.

However, complete tag pose itself can be ambiguous or noisy. [OpenCV's PnP documentation](https://docs.opencv.org/4.11.0/d5/d1f/calib3d_solvePnP.html) describes pose from calibrated image/object correspondences and multiple planar candidates. [The IPPE authors](https://github.com/tobycollins/IPPE/blob/master/README.md) explain why small or distant near-affine planar targets can have nearly equivalent poses. A correct hinge conversion cannot manufacture information absent from those pixels.

Reject or reduce the weight of ambiguous observations using geometry, height, gravity, mechanical bounds and the original odometry prior. All four coplanar tags improve the spatial baseline but do not guarantee elimination of every planar ambiguity.

## 5. A useful native route: MegaTag1 with a Hive-body map

The [Limelight map specification](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-map-specification) explicitly supports maps of objects carrying multiple tags, in addition to environments. Each tag has a fixed transform relative to that map. A rigid Hive body therefore has a valid local map even while that body moves in the field.

[MegaTag1](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-robot-localization) provides full 3D robot localization using mapped tags in arbitrary orientations without MT2's external-heading requirement. Our proposed application is to interpret its result as robot pose **relative to the rigid Hive map**, rather than already-correct field pose. The hinge and gravity conversion then supplies the world reference.

This could keep multi-tag image fitting on the Limelight. It avoids treating moving tags as fixed world landmarks or rebuilding the map each frame. All tags included in a given map must actually share the same rigid body. Two independently moving Hives cannot form one rigid object map; tags of the other Hive must not enter that solve.

This combination is an inference from documented capabilities, not a validated native BIOBUZZ mode. It needs confirmation that the 3A firmware retains the expected object-frame Z, roll and pitch, that mounting conventions are correct, and that output is not unexpectedly constrained to a floor assumption. Screenshots show a “Snap Robot to Floor” setting, but the official text inspected does not establish whether it changes solving or only visualization. No particular setting behavior is claimed here.

If full `T_HR` is supplied, inversion gives `T_RH`; known camera mounting gives `T_CH`. The equations in section 4 then work with the Hive frame H in place of a tag frame T, using hinge geometry expressed in that Hive frame.

## 6. Direct fitting of all corners from one exposure

For greater control, use the actual tag corners as observations and jointly fit a small state:

```text
s = (robotX, robotY, robotHeading, alphaRed [, alphaBlue])
```

Robot height and roll/pitch are supplied or constrained separately. A Hive angle is an internal nuisance variable in this version; it need not be classified, differentiated or published. The angle-free construction above is a useful starting solution and diagnostic.

The objective combines:

1. Reprojection errors between observed corners and predicted corners under the hinge model.
2. The original odometry prior with its uncertainty, kept fixed during all inner iterations over that frame.
3. Physical bounds and independent geometry constraints where appropriate.

One tag supplies eight scalar pixel observations; four tags supply 32. Merely counting observations does not prove uniqueness, but their known separation makes scale and orientation measurable without temporal triangulation. Do not count the corner data and the PnP pose derived from those same corners as independent observations.

One coherent X/Y/heading result should be associated with its exposure time and quality. Sequential numeric updates inside the solve must not publish inconsistent intermediate poses or repeatedly treat one frame as new evidence. A joint fit directly addresses the coupling discussed in the [alternation experiment](Hive-Pose-Heading-Alternation-Synthetischer-Test-2026-10-18.md).

## 7. Other approaches assessed

| Approach | Useful result | Main limitation | Assessment |
|---|---|---|---|
| Full tag/body pose → virtual hinge point and signed axis | Single-exposure X/Y/heading, intermediate Hive positions | Needs accurate full relative pose, gravity and hinge geometry | Strong geometric candidate |
| Joint same-exposure fit of several tags | Wider spatial baseline; explicit priors and mechanical constraints | Image ambiguity, calibration and fitting remain | Strong candidate; quantitative experiment below |
| MT1 with a rigid Hive-local map, then hinge conversion | Potential native multi-tag processing on camera | Exact firmware/object-frame behavior unverified | Most interesting native route |
| MT2 with supplied field heading and fixed endpoint map | Translation when its assumptions hold | Heading dependency and moving-tag map mismatch | Incomplete for this problem |
| Both Hives visible in the same exposure | Two virtual fixed points, nominally 25.5 inches apart | Both must be visible with usable metric poses | Useful additional constraint |
| Recognize fixed support/hinge features directly | Static known CAD points avoid tag-motion model | Repeated shapes, occlusion, contrast, feature matching | Plausible custom-vision alternative |
| Wait for upper/lower endpoint | Simplifies tag world poses | Conflicts with continuous-efficiency requirement | Not the preferred route |
| Triangulate from two nearby robot positions | Can add temporal geometry | Insufficient translation and noise within 100 ms | Not required by the preferred routes |

For two Hives, subtract their simultaneous virtual hinge vectors: their known world-point separation provides an additional heading constraint. Independent Hive rotations do not change those particular points. This is a same-frame spatial baseline, not two-frame robot travel. Derived pivot errors may be correlated, so their uncertainty still matters.

Static support geometry would require several identifiable, appropriately spread known 3D features; one symmetric edge alone is insufficient. This is a proposed vision target, not a claim that the standard tag pipeline already detects it. It also does not assume adding markers to the competition field.

## 8. Synthetic experiments already completed

### 8.1 Exact hinge construction

A deterministic NumPy experiment reconstructed 1,000 noiseless random configurations with variable robot position, heading, Hive angle within ±90 degrees, nonzero robot roll/pitch and an angled camera. Exact gravity and exact relative tag poses were provided. Maximum absolute position-component error was `4.27e-14 inches`; maximum rotation-matrix-entry error was `3.34e-16`.

This verifies transform directions and algebra to floating-point precision. It is not a camera-accuracy result, and those algebra cases were not a field-visibility survey.

### 8.2 Paired noisy-pixel experiment

The experiment below compares one tag with four tags from **the same synthetic image**, using the same original prior and shared first-tag corner noise in each pair. The single-tag case always uses the leftmost tag, not an optimally selected tag. It fits X/Y/heading and an internal Hive angle, not an alternation between exact assumed coordinates.

| Assumption | Value and limitation |
|---|---|
| Samples | 576 paired trials per method: 3 distances × 3 angles × 64 noise draws |
| Distance parameter | 48, 72, 96 inches along field Y to the hinge plane; not optical tag range |
| Hive angle | −25, 0, +25 degrees; none is an endpoint; assumed bounds ±30 degrees |
| Camera | Synthetic 640×480, focal lengths 550 pixels, principal point (320,240), no distortion |
| Camera mounting | Assumed 10-inch height and 35-degree upward pitch |
| Tag geometry | Documented square size and strip spacing; assumed 16-inch hinge offset |
| Corner noise | Independent Gaussian noise, standard deviation 0.35 pixels per coordinate |
| Original odometry prior | Gaussian standard deviation 2 inches per X/Y component, 2 degrees heading |
| Initialization | Prior X/Y/heading; Hive angle starts at zero, not at its true value |
| Solver | Local damped Gauss–Newton with backtracking, fixed prior, at most 25 iterations |

The assumed camera, hinge offset, robot position and tilt geometry are **not measured FROG or field geometry**. They demonstrate a principle. They are not proposed production constants. All four tags were visible in the nine synthetic views. Pixel trials use one robot heading and lateral offset, exact calibration, exact level attitude and a rigid target.

| Result | Original odometry | One tag | Four tags in same image |
|---|---:|---:|---:|
| 2D position error RMSE | 2.862 in | 1.127 in | 0.337 in |
| 95th-percentile 2D position error | 4.963 in | 2.416 in | 0.742 in |
| Heading error RMSE | 1.969° | 0.891° | 0.249° |
| 95th-percentile absolute heading error | 3.824° | 1.844° | 0.503° |
| Median / maximum iterations | — | 6 / 8 | 5 / 6 |
| Reported numerical nonconvergence | — | 0 / 576 | 0 / 576 |

Position RMSE means the root mean square of Euclidean XY error, not per-axis error. Zero numerical nonconvergence only means the local step tolerance was met in these trials. The solve starts close to truth and does not enumerate competing planar PnP branches. Some individual noisy frames can still worsen the prior.

This supports a same-image multi-tag approach under the modeled assumptions. It establishes neither sub-inch real-world accuracy nor global convergence. It cannot be compared directly with the previous alternation report as a contest between algorithms: that report used a different observation model.

Not modeled: calibration bias, mechanical play, imperfect IMU tilt, rolling-shutter distortion, motion blur, timing errors, tag detection failures, wrong IDs, occlusion, correlated corner errors or extreme ambiguous views. All of those can dominate the ideal random-noise result.

The measured desktop Python/NumPy fit p95 was approximately 8.04 ms for one tag and 6.95 ms for four. Those figures exclude image capture, detection, transport, polling and readiness, and are **not Control Hub measurements**. The small timing difference is not evidence that four-tag solving is inherently faster.

## 9. What a 100 ms estimate actually means

The [Limelight 3A overview](https://docs.limelightvision.io/docs/docs-limelight/getting-started/limelight-3a) lists AprilTag operation at 20 FPS for 1280×960 with 2× downscale, and 50 FPS for 640×480 with 2× downscale. Those imply nominal frame intervals of 50 and 20 ms. The 3A uses a rolling-shutter image sensor.

With a continuously running camera, one usable exposure is enough geometrically; useful robot translation does not need to accumulate. A roughly 100 ms result is consequently a reasonable target to investigate, but it is not proven by frame rate or the desktop fit timing. Higher resolution can improve usable corner geometry while reducing frame frequency; the best setting remains an accuracy/latency tradeoff.

Two different quantities must be distinguished: time from a request to receiving a result, and age of that result's underlying exposure. Capture/readout, image processing, transport, polling delay, orientation alignment and the final solve all contribute. Re-reading a cached frame does not restart its exposure age. A pose solved at exposure time must be propagated through timestamped odometry to correct a moving robot now.

No camera-only method guarantees a valid full pose every 100 ms while the relevant geometry is hidden or ambiguous. A failed observation need not stop driving or wait for an endpoint: retain odometry and process the next available observation. This retains continuous control and uses intermediate positions whenever the image supports them.

## 10. Current repository support and remaining evidence gaps

The official FTC API exposes per-fiducial full relative poses and tag corners; corner output must be enabled in the pipeline. The current FROG `Limelight.Result` snapshot does not retain that full per-tag geometry. Aggregate target angles and an existing field-pose value alone are insufficient for the new construction. This report does not change that wrapper.

Camera intrinsics must correspond to the actual resolution and calibration. The existing `LimelightGeometry` view-angle defaults are not measured calibration for the future robot. The synthetic focal length used here is deliberately generic and must not be copied into them. Camera mounting and gravity alignment also need actual values.

The main unresolved geometry is the complete rigid tag-to-chosen-hinge transform for every accepted tag, including print orientation. The main native-processing uncertainty is whether the installed 3A MT1 firmware returns the required unconstrained Hive-local 3D pose. The main performance uncertainty is complete exposure-to-current-pose latency and accuracy on the eventual hardware.

These unknowns do not invalidate the single-exposure geometry. They limit how confidently it can be promoted from a mathematical candidate to a competition measurement source. No physical validation is claimed while the robot does not exist.

## Requested changes / owner's notes

- Preferred output reference point and world-axis convention: …
- Available exact tag-to-hinge CAD transforms: …
- Planned rigid camera mounting and available robot-attitude source: …
- Acceptable position/heading uncertainty at shooting range: …
- Further approaches or assumptions to challenge: …

## Appendix: reproducible experiment source

Save the following as a temporary Python file outside the repository and run it with Python and NumPy. It prints the aggregate results and writes a neighboring JSON file with individual trials. Numeric geometry results are deterministic for the seed; wall-clock timing is not. The source is retained here only as documentation, with no repository test or runtime integration.


```python
import json
import time
from pathlib import Path
import numpy as np

SEED = 20261019
rng = np.random.default_rng(SEED)


def rx(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = np.cos(a), np.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


H = np.array([0., 0., 43.95])
A = np.array([1., 0., 0.])
B = rx(np.pi / 2)
R0 = np.array([0., -16., 0.])
RC = ry(np.deg2rad(-35)) @ np.array([[0, 0, 1], [-1, 0, 0], [0, -1, 0]])
TC = np.array([0., 0., 10.])
SIGMA_PIXEL = .35
SIGMA_POSITION = 2.
SIGMA_YAW = np.deg2rad(2.)
K = np.array([[550., 0, 320], [0, 550., 240], [0, 0, 1.]])

# Synthetic rigid panel with the documented tag spacing, but an ASSUMED hinge offset,
# camera and target geometry. These coordinates are not FROG shooting coordinates.
corners = np.array([[u + du, dv, 0.] for u in [-6.5, -2.75, 2.75, 6.5]
                    for du, dv in [(-1.625, 1.625), (1.625, 1.625), (1.625, -1.625), (-1.625, -1.625)]])


def project(state, points):
    x, y, yaw, alpha = state
    rwr = rz(yaw)
    rwc = rwr @ RC
    c_world = np.array([x, y, 0.]) + rwr @ TC
    world = H + (points @ B.T + R0) @ rx(alpha).T
    optical = (world - c_world) @ rwc
    if np.any(optical[:, 2] <= 0):
        raise ValueError('point behind camera')
    return optical[:, :2] / optical[:, 2, None] * [550., 550.] + [320., 240.]


def fit(observed, points, prior):
    state = np.array([*prior, 0.])
    def residual(values):
        image = ((project(values, points) - observed) / SIGMA_PIXEL).ravel()
        return np.r_[image, (values[:2] - prior[:2]) / SIGMA_POSITION,
                     (values[2] - prior[2]) / SIGMA_YAW]
    for iteration in range(25):
        r = residual(state)
        jacobian = np.empty((len(r), 4))
        for column, epsilon in enumerate([1e-4, 1e-4, 1e-6, 1e-6]):
            step = np.zeros(4)
            step[column] = epsilon
            jacobian[:, column] = (residual(state + step) - residual(state - step)) / (2 * epsilon)
        normal = jacobian.T @ jacobian
        delta = np.linalg.solve(normal + np.diag(np.maximum(np.diag(normal), 1) * 1e-8), -jacobian.T @ r)
        accepted = False
        for scale in [1., .5, .25, .125, .0625, .03125]:
            candidate = state + scale * delta
            candidate[3] = np.clip(candidate[3], -np.pi / 6, np.pi / 6)
            if np.sum(residual(candidate) ** 2) <= np.sum(r ** 2) + 1e-12:
                accepted = True
                break
        if not accepted:
            return state, iteration + 1, False
        change = candidate - state
        state = candidate
        if np.max(np.abs(change[:2])) < 1e-5 and np.max(np.abs(change[2:])) < 1e-7:
            return state, iteration + 1, True
    return state, 25, False


def axis_pose(rct, tct, g_robot, hinge_in_tag, axis_in_tag):
    h_camera = tct + rct @ hinge_in_tag
    a_camera = rct @ axis_in_tag
    g_camera = RC.T @ g_robot
    a_camera = a_camera - g_camera * np.dot(g_camera, a_camera)
    a_camera /= np.linalg.norm(a_camera)
    g_world = np.array([0., 0., -1.])
    frame_world = np.column_stack((A, np.cross(g_world, A), g_world))
    frame_camera = np.column_stack((a_camera, np.cross(g_camera, a_camera), g_camera))
    rwc = frame_world @ frame_camera.T
    rwr = rwc @ RC.T
    robot = H - rwc @ h_camera - rwr @ TC
    return robot, rwr


max_position = max_rotation = 0.
for _ in range(1000):
    alpha, yaw = rng.uniform(-np.pi / 2, np.pi / 2), rng.uniform(-np.pi, np.pi)
    rwr = rz(yaw) @ ry(rng.uniform(-.15, .15)) @ rx(rng.uniform(-.15, .15))
    robot = np.r_[rng.uniform(-72, 72, 2), 0.]
    rwc = rwr @ RC
    camera = robot + rwr @ TC
    rwt = rx(alpha) @ B
    tag = H + rx(alpha) @ R0
    rct, tct = rwc.T @ rwt, rwc.T @ (tag - camera)
    estimate, estimate_r = axis_pose(rct, tct, rwr.T @ [0., 0., -1.], -B.T @ R0, B.T @ A)
    max_position = max(max_position, float(np.max(np.abs(estimate - robot))))
    max_rotation = max(max_rotation, float(np.max(np.abs(estimate_r - rwr))))

samples = []
visibility_failures = 0
for distance in [48., 72., 96.]:
    for tilt in [-25., 0., 25.]:
        truth = np.array([5., -distance, np.pi / 2, np.deg2rad(tilt)])
        exact = project(truth, corners)
        if not (np.all(exact >= [3., 3.]) and np.all(exact <= [637., 477.])):
            visibility_failures += 1
            continue
        for _ in range(64):
            prior = truth[:3] + rng.normal(0, [SIGMA_POSITION, SIGMA_POSITION, SIGMA_YAW])
            measured = exact + rng.normal(0, SIGMA_PIXEL, exact.shape)
            for name, count in [('one_tag', 4), ('four_tags_same_image', 16)]:
                started = time.perf_counter_ns()
                estimated, iterations, converged = fit(measured[:count], corners[:count], prior)
                samples.append({'kind': name, 'hinge_distance_in': distance, 'hive_angle_deg': tilt,
                                'prior_position_error_in': float(np.linalg.norm(prior[:2] - truth[:2])),
                                'prior_heading_error_deg': float(np.rad2deg(abs(prior[2] - truth[2]))),
                                'position_error_in': float(np.linalg.norm(estimated[:2] - truth[:2])),
                                'heading_error_deg': float(np.rad2deg(abs(estimated[2] - truth[2]))),
                                'hive_angle_error_deg': float(np.rad2deg(abs(estimated[3] - truth[3]))),
                                'iterations': iterations, 'converged': converged,
                                'desktop_solve_ms': (time.perf_counter_ns() - started) / 1e6})

result = {'seed': SEED, 'image': {'width': 640, 'height': 480, 'fx': 550, 'fy': 550,
                               'cx': 320, 'cy': 240, 'pixel_sigma': SIGMA_PIXEL},
          'all_geometry_but_tag_spacing_assumed': True,
          'noiseless_invariant_cases': 1000, 'max_position_component_error_in': max_position,
          'max_rotation_entry_error': max_rotation, 'invisible_geometries': visibility_failures,
          'rows': {}}
for name in ['one_tag', 'four_tags_same_image']:
    subset = [s for s in samples if s['kind'] == name]
    entry = {'samples': len(subset), 'nonconverged': sum(not s['converged'] for s in subset)}
    for metric in ['prior_position_error_in', 'prior_heading_error_deg', 'position_error_in', 'heading_error_deg', 'hive_angle_error_deg']:
        values = np.array([s[metric] for s in subset])
        entry[metric] = {'rmse': float(np.sqrt(np.mean(values ** 2))), 'p95': float(np.percentile(values, 95))}
    entry['iterations_median'] = float(np.median([s['iterations'] for s in subset]))
    entry['iterations_max'] = max(s['iterations'] for s in subset)
    entry['desktop_solve_p95_ms_not_hub'] = float(np.percentile([s['desktop_solve_ms'] for s in subset], 95))
    result['rows'][name] = entry
print(json.dumps(result, indent=2))
Path(__file__).with_suffix('.json').write_text(json.dumps({'summary': result, 'samples': samples}, indent=2), encoding='utf-8')
```

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
