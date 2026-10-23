# Portfolio page plan: Limelight Pose Correction & Butine

Goal: Give an external editing agent the English content, calculations and image ideas for one FTC portfolio page, following the team's requested sequence.

## Editorial instructions

Create one page with two main sections in this order:

1. **Limelight pose correction**, explained through an engineering cycle and a side view of the tipping Hive.
2. **Butine**, introduced by its problem and explained step by step from top to bottom:
   - **2.1 Training the neural network**
   - **2.2 Calculating the game pieces' positions**
   - **2.3 Planning and optimising the route**, including the motion model, obstacle avoidance and future runs that inform the current decision.

Use the supplied green-and-black F.R.O.G. style. Team identity: **#10183 F.R.O.G. - Frog Robots of Germany**. Keep text and equations editable. There is no prescribed slide geometry in this brief.

All portfolio copy must be English. Do not include source filenames, repository paths, class names or code listings. Mention the dedicated test OpMode by its purpose. Describe the requested images; do not generate graphics. The existing Game Strategy / Organisation page stays separate.

The text marked **Suggested copy** is usable on the page. Equations and image directions supply the external agent with the context needed to edit it. Do not paste all explanatory notes onto the page. In particular, distinguish illustrative calculations from measured results.

## 1. Limelight pose correction

### Engineering flow

**Define the problem**

Odometry can accumulate position and heading error. A visual reference could correct it, but the Hive tips: its AprilTags change position and orientation. A moving tag cannot simply be assigned one fixed field pose.

**Suggested copy:**

> Odometry drift affects aiming and navigation. We wanted a visual correction using the Hive's AprilTags, but the tags move as the Hive tips. Treating them as fixed landmarks would make Hive motion appear to be robot motion.

**Develop the approach**

Use the fixed hinge as the field reference. The tag-to-hinge relationship is rigid in the Hive's own frame. A full camera-relative tag pose provides both translation and orientation. Rotate the known tag-to-hinge vector into the camera frame, then add it to the measured tag position.

**Suggested copy:**

> Our approach reconstructs the fixed hinge from the measured tag pose and known Hive geometry. Rotating the tag-to-hinge offset accounts for the Hive's tilt. The hinge direction provides an orientation reference, allowing us to recover robot position and heading after accounting for camera mounting.

**Verify and iterate**

Compare different Hive angles while holding the simulated camera fixed. Both observations should recover the same hinge and robot pose. This is an engineering verification of the geometry. Field testing must later evaluate measurement noise, mounting calibration and difficult tag views.

**Suggested copy:**

> We verify the geometry by changing the Hive angle while keeping the camera fixed. Both observations should recover the same hinge position. Physical validation will then check whether camera estimates remain consistent across viewing angles.

Do not claim a completed field accuracy test. The mathematical estimator and camera-pose conversion exist in software; measured mounting values and a complete, validated competition correction remain pending.

### Clean calculation for the Hive side-view image

Use a simple two-dimensional side view to explain the rotating offset. It is a slice through the hinge motion, not the complete heading calculation.

Define:

- \(d_T,z_T\): measured tag position relative to the camera, forward and upward.
- \(L\): distance along an illustrative rigid arm from hinge to tag.
- \(\phi\): observed arm inclination above horizontal, determined from tag orientation and its known alignment with the Hive.
- \(d_H,z_H\): reconstructed hinge position relative to the camera.

For this simple arm geometry:

\[
d_H=d_T-L\cos\phi,\qquad z_H=z_T-L\sin\phi
\]

The vertical correction changes sign as the arm moves above or below the hinge.

**Illustrative example from the geometry discussion:**

\[
L=20\ \mathrm{in}
\]

| Observation | Upper position | Lower position |
|---|---:|---:|
| Arm inclination \(\phi\) | \(+30^\circ\) | \(-30^\circ\) |
| Measured tag forward position \(d_T\) | \(77.32\) in | \(77.32\) in |
| Measured tag height relative to camera \(z_T\) | \(40\) in | \(20\) in |
| Reconstructed hinge forward position \(d_H\) | \(60.00\) in | \(60.00\) in |
| Reconstructed hinge height relative to camera \(z_H\) | \(30.00\) in | \(30.00\) in |

For example, in the upper position:

\[
d_H=77.32-20\cos30^\circ\approx60.00\ \mathrm{in}
\]
\[
z_H=40-20\sin30^\circ=30.00\ \mathrm{in}
\]

In the lower position the vertical correction is \(+10\) in, giving the same hinge height.

These are arbitrary teaching values, not actual Hive dimensions, CAD measurements or camera results. The real geometry requires the full tag-to-hinge vector, not only a scalar arm length.

**Image description:**

Show the Hive from the side with a fixed hinge and two semi-transparent tipping positions. Mark the two tag positions on a circular arc. Place the camera to one side. Label the hinge, tag, arm length \(L\), signed angle \(\phi\), horizontal component \(L\cos\phi\) and vertical component \(L\sin\phi\). Put the two short reconstruction equations next to the corresponding dimensions. The visible idea is that the tag moves while the reconstructed hinge stays fixed.

Use an actual Hive silhouette or CAD view if supplied. Mark the simplified arm and example numbers **“Illustrative geometry”**. A side view illustrates tilt compensation; it does not by itself demonstrate recovery of field heading.

### General calculation behind the illustration

This is supporting context for the editing agent, so the simplified illustration does not misrepresent the actual method.

Let \(C\) denote the camera frame and \(T\) a known tag-attached frame. Let \(R_{CT}\) rotate tag-frame vectors into camera coordinates, \(t_{CT}\) be the measured camera-to-tag translation and \(r_{TH}\) the known tag-to-hinge vector.

\[
h_C=t_{CT}+R_{CT}r_{TH}
\]

The same measured orientation maps the known signed hinge-axis direction \(a_T\) into the camera frame:

\[
a_C=R_{CT}a_T
\]

For a level camera, heading follows by comparing that axis with its known field direction \(a_W\):

\[
\psi_C=\operatorname{atan2}(a_{W,y},a_{W,x})
-\operatorname{atan2}(a_{C,\mathrm{left}},a_{C,\mathrm{forward}})
\]

Subtract camera mounting yaw to obtain robot heading. If the camera or robot is tilted, use the gravity direction with the hinge axis to recover the full orientation instead of applying this level-camera shortcut.

With known field hinge position \(H_W\), camera position is:

\[
p_C^W=H_W-R_{WC}h_C
\]

Then subtract the robot-to-camera mounting offset expressed in field coordinates:

\[
p_R^W=p_C^W-R_{WR}o_{RC}
\]

Here \(R_{WC}\) and \(R_{WR}\) rotate camera and robot vectors into the field, and \(o_{RC}\) is the camera mounting offset in robot coordinates. Raw camera axes must first be converted consistently. A full relative tag pose, known hinge geometry and a vertical reference are required. Tag-centre angles alone do not provide all these inputs.

For the page, use the simple side-view equations plus a concise statement that hinge direction and mounting geometry recover field position and heading. The full matrix chain is background, not a requirement to crowd the page.

## 2. Butine: the problem

**Suggested copy:**

> Pollen positions change during a match, so a fixed collection path can miss available pieces. Butine combines object detection, a field map and time-based route planning to decide what to collect and when to return to score.

Present the next three steps vertically. Each should explain the input, the calculation or decision, and the result that becomes the next step's input.

## 2.1 Training the neural network

**Suggested copy:**

> We prepared a dedicated test OpMode to capture training images directly from Limelight, manually or every two seconds. The training workflow uses labelled pollen images, including difficult views, followed by evaluation on separate scenes before deploying the model for detection.

**What this step should explain:**

Capture representative images, annotate pollen, train the detector offline, evaluate on separate images, then inspect failures and improve the dataset. Keep captures from the same recording session together when separating training and evaluation data, so near-identical frames do not give misleading results.

The test OpMode implements image capture. It does not train a neural network on the Control Hub. Dataset size, model architecture and achieved detection accuracy have not been supplied; do not invent them or imply that the complete workflow has already been demonstrated.

**Image description:**

Request one real camera image from a difficult scenario: partially occluded pollen close to similar-coloured field features. Annotate the visible pollen and explain the difficulty in one short caption. Ground-truth annotations and model predictions should be visibly distinguishable if both are shown. If there is no inference output, show only labelled training data.

**Possible caption:**

> A difficult training example: partial occlusion and a similar-coloured background.

## 2.2 Calculating the game pieces' positions

**Suggested copy:**

> A detection tells us where pollen appears in the image. Camera geometry converts its viewing angles into a position relative to the robot. We rotate and translate that position using the robot pose at image capture, then update a field map that remembers targets outside the current view.

### Calculation to accompany this step

Define \(h\) as camera height above the target plane, \(\alpha\) as downward camera pitch, \(t_x\) as horizontal viewing angle positive to camera right and \(t_y\) as vertical viewing angle positive upward. Let \(f\) and \(l\) be forward and left displacement from the camera.

For the geometric ray-to-plane calculation:

\[
f=\frac{h}{\tan(\alpha-t_y)}
\]
\[
l=-\frac{h\tan(t_x)}{\sin\alpha-\tan(t_y)\cos\alpha}
\]

Angles must use consistent units. Apply camera mounting yaw and translation to obtain robot-relative displacement \((f_r,l_r)\). Then, with capture-time robot pose \((x_r,y_r,\theta)\):

\[
x_p=x_r+f_r\cos\theta-l_r\sin\theta
\]
\[
y_p=y_r+f_r\sin\theta+l_r\cos\theta
\]

**Clean illustrative example:**

With a level robot, a camera pitched downward by \(\alpha=30^\circ\), \(h=10\) in, \(t_y=0^\circ\) and \(t_x=10^\circ\):

\[
f=17.32\ \mathrm{in},\qquad l=-3.53\ \mathrm{in}
\]

Assume zero mounting yaw and horizontal offsets. For a robot at \((50,40)\) in facing \(90^\circ\), the estimated target is approximately \((53.53,57.32)\) in.

Use only the key equations or the worked example on the page. Label numerical values as an illustrative calculation.

**Image description:**

A small camera-to-pollen side view can label camera height, pitch and the sight ray. Pair it with a top-down map showing the same pollen ID, robot heading and transformed field position. This connects the training-image step with the route-planning step.

**Editing note:** The geometric projection is implemented as an alternative requiring measured mounting values. The enabled default currently uses an uncalibrated range approximation. Present the equations as the geometric approach being validated, not a camera calibration already achieved. Choose the detection point and target-plane height consistently: a floor-contact point and an object's centre are different geometric inputs.

## 2.3 Planning and optimising the route

### Motion model: estimate time, not only distance

**Suggested copy:**

> Butine estimates travel time using acceleration, braking and turning. It builds collection runs that respect carrying capacity and include the return to score, then improves the visiting order by reversing or relocating parts of the route.

For cruise speed \(v\), acceleration \(a\), braking magnitude \(b\) and leg distance \(d\), the transition distance is:

\[
d_c=\frac{v^2}{2a}+\frac{v^2}{2b}
\]

For a leg starting and ending at rest:

\[
t(d)=
\begin{cases}
\sqrt{2d(1/a+1/b)},&d<d_c\\
v/a+v/b+(d-d_c)/v,&d\ge d_c
\end{cases}
\]

Turning can overlap translation. The implemented model adds a fraction of turn time that exceeds the straight-line time. These are planning estimates requiring drivetrain calibration.

A compact inset can compare one 24-in leg with two 12-in legs and an intermediate stop. Using software seed values \(v=72\) in/s, \(a=162\) in/s² and \(b=173\) in/s² gives **0.757 s** versus **1.071 s** predicted translation time. This explains the effect of stopping; it is not a measured performance improvement.

The planner evaluates total collection count first, collection in earlier runs second and predicted time third. It uses local route improvements and exact ordering for sufficiently small runs. Do not claim a globally optimal solution.

### Avoiding obstacles

**Include this topic as a planned extension.** Current collection planning uses direct target-to-target legs and has no implemented obstacle map or collision-aware detour search.

**Suggested copy:**

> Planned extension: account for the robot's footprint when checking routes around field structures. Blocked connections must use a feasible detour, whose travel time becomes part of the route comparison.

**General calculation and idea:**

For a circular approximation, enlarge an obstacle's radius \(r_o\) by robot footprint radius \(r_r\) and a clearance margin \(m\):

\[
r_{\mathrm{blocked}}=r_o+r_r+m
\]

A proposed segment is blocked if its minimum distance to the obstacle centre is at most \(r_{\mathrm{blocked}}\). A route around it needs collision-checked waypoints and a travel-time estimate for that detour. This is a concept, not an implemented algorithm. Noncircular field structures require their actual outlines or a stated conservative approximation.

**Image description:**

On the route map, show a field structure, its enlarged clearance boundary, a crossed-out direct connection and a proposed detour. Label this element **“Planned obstacle-aware routing”**. Do not imply opponent detection or dynamic obstacle avoidance.

### Planning ahead while executing only the first run

**Suggested copy:**

> The best immediate run can leave an inefficient set of targets for later. Butine therefore evaluates several runs together, but commits only to the first. Before another run, the map and remaining time are updated and the problem is solved again. Future runs guide the present choice without becoming a fixed script.

**Image description:**

Use one top-down map with the selected current run as a solid path and a later evaluated run as a dashed path. Both return to the scoring position. At the next decision, show that fresh observations can replace the dashed route.

**Caption:**

> Solid: selected current run. Dashed: evaluated future run, not committed.

The default lookahead is two runs. It is a planning horizon, not a claim that the robot predicts future game-piece motion. Later runs are evaluated using the currently known targets. The caller must request the next solve; this brief does not assert that a complete repeated collection cycle is already active in competition.

## Closing instructions for the editing agent

Keep the narrative in the requested order: solve the moving-reference problem, introduce the changing collection problem, then explain detection, position and route selection.

Do not add separate bulk-caching charts, CPU/thread explanations or a general optimisation section to this page. Here, optimisation means improving collection-route decisions.

Use a short footnote for the evidence status: **“Illustrative calculations. Mounting calibration and field validation pending. Obstacle-aware routing proposed.”** Keep these distinctions near the relevant claims as well.

For portfolio context, the [official FTC Awards guidance](https://ftc-resources.firstinspires.org/ftc/game/manual-06) values engineering process and mathematics that explain decisions. Make the reasoning visible, embed the important evidence and include the portfolio's AI-assistance credit. The requested visuals are descriptions for the team, not generated assets.

## Team changes

- Preferred wording:
- Hive image / diagram source:
- Difficult neural-network example:
- Confirmed training and field-test results:
- Changes to obstacle-avoidance scope:
- Other adjustments:

Written by --GPT-6--, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
