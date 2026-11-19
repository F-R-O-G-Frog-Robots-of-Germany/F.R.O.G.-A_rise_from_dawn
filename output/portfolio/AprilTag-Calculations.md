# How we find the robot's pose while the Hive tilts

The Limelight measures a tag's position and orientation in one image. A tag attached to the Hive moves when the Hive tips. The hinge point and the signed direction of its axis stay fixed on the field. We use the measured tag pose to find that hinge in camera coordinates, then use the known field hinge to work back to the robot.

## What the image tells us about tilt

A tilted square looks foreshortened. The positions of its four corners show how it is distorted by perspective. A pose solver uses that geometry, the known tag size and camera calibration to estimate the tag's position and rotation. Pixel area alone mixes tilt with distance and cannot distinguish opposite tilts of the same magnitude in a symmetric view. Our adapter reads the Limelight's full tag pose as its input. [AprilTag pose estimation](https://github.com/AprilRobotics/apriltag/blob/master/apriltag_estimate_tag_pose.docstring) · [Limelight 3D AprilTags](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-3d)

The tag's rotation already tells us how to rotate the known offset from tag to hinge. We can recover the fixed hinge directly while the Hive tilts. The localization equations never need a separate Hive tilt angle.

## Reading the notation

| Symbol | Coordinate frame | Origin |
| --- | --- | --- |
| $T$ | Tag | Centre of the printed tag |
| $C$ | Camera | Camera lens |
| $R$ | Robot | Robot position reference point |
| $W$ | Field | Field centre for the Hive calculation |

$R_{AB}$ rotates a vector **from frame $B$ into frame $A$**. $T_{AB}$ also adds a translation when it transforms a point. $p_H^C$ means “the position of the hinge point $H$, expressed in camera coordinates.” A transpose is written $R^{\mathsf T}$. For a rotation matrix, the transpose reverses the rotation.

## The short portfolio calculation for a level robot

These are the three steps on the [compact formula panel](AprilTag-Calculations.tex). The robot's roll and pitch are set to zero. Its heading $\psi$ is free, and the camera mount may have an arbitrary known rotation and offset.

1. **Put the hinge in robot coordinates.** Compose the camera measurement $T_{CT}$ and the camera mounting transform $T_{RC}$. Apply the resulting transform to the hinge point $d_T=p_H^T$. Rotate the signed hinge axis $a_T$ with the rotation part alone.

   $$
   T_{RT}=T_{RC}T_{CT},\qquad
   h_R=T_{RT}(d_T),\qquad u_R=R_{RT}a_T.
   $$

   Expanded, the first point calculation is $h_R=t_{RC}+R_{RC}(t_{CT}+R_{CT}d_T)$. That is the path from robot to camera, camera to tag, and tag to hinge, with each offset turned into the right frame before adding.

2. **Read heading from the signed hinge axis.** Our field geometry defines the positive hinge direction as field $+X$. If the hinge axis points toward robot right, its bearing in robot coordinates is negative, so the robot is facing counterclockwise from field $+X$. The heading is the negative of that bearing.

   $$
   \psi=-\operatorname{atan2}(u_{R,y},u_{R,x}).
   $$

   The signed axis must agree with the tag-ID geometry. Flipping it would change the heading by $180^\circ$. The horizontal components must also have nonzero length. For a different field hinge direction, use its field bearing minus the robot-frame bearing, wrapped to the desired angle range.

3. **Subtract the offset from the known hinge.** $h_{R,xy}$ is the horizontal vector from the robot to the hinge. Turn it through the robot heading with the planar rotation $R_2(\psi)$, then subtract it from the field hinge position $H_{xy}$.

   $$
   p_{xy}=H_{xy}-R_2(\psi)h_{R,xy}.
   $$

For the panel's synthetic check, $H_{xy}=(60,0)$ inches, the robot faces $+90^\circ$, and its lens is 10 inches above its position reference. At $+30^\circ$ Hive tilt, the tag centre in **robot coordinates** is approximately $(47.32,-20,50)$, and the rotated tag-to-hinge offset is $(-17.32,0,-10)$. Their sum is $h_R=(30,-20,40)$. The signed hinge axis in robot coordinates is $u_R=(0,-1,0)$, so $\psi=+90^\circ$. A $90^\circ$ rotation takes $(30,-20)$ to $(20,30)$. Subtracting that from $(60,0)$ gives the robot position $(40,-30)$.

### How this matches the Java estimator

With a level robot, the Java estimator aligns two horizontal signed hinge directions while keeping down fixed. The only remaining rotation is yaw. The short heading equation computes that same rotation directly. Grouping the two position offsets in robot coordinates gives the short position equation. The full calculation below also handles a tilted robot when supplied with measured gravity, and reports height and hinge-axis error for diagnostics.

The existing JVM tests passed all 9,138 estimator checks and 95 Limelight adapter checks. In a separate comparison against the Java estimator, the short equations agreed to floating-point precision in 10,000 exact and 10,000 perturbed synthetic level-robot cases. The portfolio example recovered $(40,-30)$ inches and $90^\circ$ at all three Hive angles. The estimator and adapter remain prototypes without a production call site in the active robot software. Real-camera results depend on measuring the mount and verifying the tag-axis conventions on the robot.

## Full calculation in the Java estimator

### 1. Convert the Limelight measurement

The Limelight reports the tag centre at $(x,y,z)$ in optical coordinates. Positive $x$ points right, positive $y$ points down and positive $z$ points forward. The estimator uses forward, left and up. A tag that is ahead, to the right and below the camera therefore has positive forward, negative left and negative up coordinates.

$$
t_{CT}=39.3701\,(z,-x,-y)^{\mathsf T}
$$

The factor converts metres to inches. The axis change is a matrix $S$. Its first row selects the old forward component, and its second and third rows negate the old right and down components.

$$
S=\begin{bmatrix}
0&0&1\\
-1&0&0\\
0&-1&0
\end{bmatrix}.
$$

We apply this same axis change to the tag's rotation matrix.

$$
R_{CT}=S\,R_z(\gamma)R_y(\beta)R_x(\alpha)
$$

Here $\alpha,\beta,\gamma$ are the **measured tag** roll, pitch and yaw. The rightmost rotation acts first. The result $R_{CT}$ tells us how a direction in the tag's own coordinate frame points in camera coordinates.

For the robot pose **relative to the printed tag**, combine the measured tag-to-camera transform $T_{CT}$ with the known camera-to-robot mount $T_{RC}$ and invert the result.

$$
T_{TR}=(T_{RC}T_{CT})^{-1}
$$

For a transform $p_A=R_{AB}p_B+t_{AB}$, reversing it gives $p_B=R_{AB}^{\mathsf T}(p_A-t_{AB})$. We subtract the translation and undo the rotation. The coordinates relative to the tag can change even for a parked robot because the tag's coordinate frame tilts. The following steps recover the pose relative to the stationary field.

### 2. Recover the fixed hinge from the moving tag

The tag ID gives us the hinge point and axis.

- $p_H^T$ is the offset from the tag centre to a point on the hinge, expressed in tag coordinates.
- $a_T$ is a unit vector along the signed hinge axis, also in tag coordinates. The sign identifies which direction along the hinge we use in both frames.

Rotate the offset into camera coordinates before adding it to the measured tag position. The hinge axis is a direction, so it only needs the rotation.

$$
p_H^C=t_{CT}+R_{CT}p_H^T,
\qquad a_C=R_{CT}a_T.
$$

Think of following two arrows from the camera to the tag and from the tag to the hinge. When the Hive tips, the first arrow changes and the second turns with the tag. Their sum still ends at the same hinge point. Rotating the Hive about the hinge also preserves the direction of the hinge axis.

### 3. Orient the camera using the hinge and down

**The zero-roll assumption is about the robot chassis.** We expect the robot to stay upright on the tiles. In the level-floor model, we set robot roll and pitch to zero and use a known unit down vector $g_R=(0,0,-1)^{\mathsf T}$. The tag still gets its full measured rotation, so the Hive can tilt. The estimator can also accept a measured down direction aligned to the image exposure.

The camera may be mounted at an angle. Transpose the camera mounting rotation $R_{RC}$ to take the down direction from robot coordinates into camera coordinates.

$$
g_C=R_{RC}^{\mathsf T}g_R.
$$

The physical hinge is horizontal. If noise makes the estimated axis $a_C$ point a little up or down, the dot product $a_C\cdot g_C$ tells us how much of it points along down. Subtract that component and normalise the remaining vector to length one.

$$
\hat a_C=\operatorname{unit}\bigl(a_C-(a_C\cdot g_C)g_C\bigr).
$$

Picture a set of three arrows. One points along the hinge axis $a$. The second, $g\times a$, points sideways at a right angle to the hinge and down. The third points down along $g$. Together they make the basis $B(a,g)=[\,a,\ g\times a,\ g\,]$.

In the camera frame, the arrows are $B_C=B(\hat a_C,g_C)$. In the field, they are $B_W=B(a_W,g_W)$, using the known field hinge axis and field down. Composing the two bases gives the camera-to-field rotation.

$$
R_{WC}=B_W B_C^{\mathsf T}.
$$

Read it right to left. $B_C^{\mathsf T}$ expresses a camera vector along the three arrows. $B_W$ expresses those same components in field coordinates. Down establishes the vertical reference and the signed hinge axis establishes the horizontal reference needed for heading.

### 4. Work back from the field hinge to the robot

The inverse camera mounting rotation takes robot directions into camera coordinates. Applying the camera-to-field rotation next gives the robot's field orientation.

$$
R_{WR}=R_{WC}R_{RC}^{\mathsf T}.
$$

Start at the known hinge point $p_H^W$. Subtract the camera-to-hinge vector $p_H^C$ to get to the camera. Then subtract the robot-to-camera mounting offset $t_{RC}$ to get to the robot. Rotate each displacement into the field frame before subtracting it.

$$
\underbrace{p_R^W}_{\text{robot on field}}
=\underbrace{p_H^W}_{\text{hinge on field}}
-\underbrace{R_{WC}p_H^C}_{\text{camera to hinge}}
-\underbrace{R_{WR}t_{RC}}_{\text{robot to camera}}.
$$

The result contains $x,y,z$ in inches. To find heading, rotate the robot's forward vector $(1,0,0)$ into the field frame. Use its horizontal components in $\operatorname{atan2}$ to get the heading $\psi$ in radians.

$$
f=R_{WR}(1,0,0)^{\mathsf T},
\qquad \psi=\operatorname{atan2}(f_y,f_x).
$$

### 5. Follow a second test at three Hive angles

In the existing Java unit test, the fixed hinge is at $(60,0,40)$ inches and its axis points along field $+Y$. This is a separate geometry from the portfolio example. The robot faces field $+X$ and the camera has no rotation relative to the robot. The lens is 10 inches above the robot origin, so $t_{RC}=(0,0,10)$.

The test places the tag 20 inches from the hinge. For a Hive tilt of $+30^\circ$, the tag centre in camera coordinates is

$$
t_{CT}=(60+20\cos30^\circ,\ 0,\ 30+20\sin30^\circ)
\approx(77.32,0,40).
$$

The tag-to-hinge offset in tag coordinates is $(-20,0,0)$. Rotating it by the synthetic $R_y(-30^\circ)$ gives approximately $(-17.32,0,-10)$. Adding it to the tag centre gives

$$
p_H^C\approx(77.32,0,40)+(-17.32,0,-10)=(60,0,30).
$$

The camera, robot and field axes are aligned in this test, so the rotations in the position equation are identity matrices. Working back from the hinge gives

$$
p_R^W=(60,0,40)-(60,0,30)-(0,0,10)=(0,0,0),
\qquad \psi=0.
$$

| Hive tilt | Tag in camera coordinates, inches | Hinge in camera coordinates, inches | Robot on field, inches |
| --- | --- | --- | --- |
| −30° | $(77.32,0,20)$ | $(60,0,30)$ | $(0,0,0)$ |
| 0° | $(80,0,30)$ | $(60,0,30)$ | $(0,0,0)$ |
| +30° | $(77.32,0,40)$ | $(60,0,30)$ | $(0,0,0)$ |

The test uses exact geometry and rounds only the numbers shown here. On the robot, the estimate depends on usable 3D tag data, camera mounting calibration, accurate tag-to-hinge geometry and a valid down direction. The Hive estimator is a prototype with synthetic tests, and the current geometry marks some mounting and tag-frame values as assumed.

## 6. Convert the pose for fixed field tags

With a configured field map and camera mount, the Limelight supplies the field pose as **botpose**. The team code converts $x,y$ from metres to inches, adds half the 144-inch field size on both axes to shift from field centre to the corner, and converts heading from degrees to radians.

$$
(x_{\mathrm{in}},y_{\mathrm{in}})
=39.3701(x_{\mathrm m},y_{\mathrm m})+(72,72),
\qquad
\psi_{\mathrm{rad}}=\psi_{\mathrm{deg}}\frac{\pi}{180}.
$$

For example, $(1,-1)$ metres and $90^\circ$ become $(111.37,32.63)$ inches and $\pi/2$ radians. This conversion starts with a pose that Limelight has already computed in field coordinates. The Hive estimator starts with a moving tag measured in camera coordinates.

## Where to find it in the code

- [HiveLimelightObservation.java](../../TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/HiveLimelightObservation.java) converts the per-tag pose.
- [HiveGeometry.java](../../TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/HiveGeometry.java) defines the tag-to-hinge and camera mounting geometry.
- [HivePoseEstimator.java](../../TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/HivePoseEstimator.java) computes the hinge, field rotations, robot position and heading.
- [HivePoseEstimatorChecks.java](../../tests/java/org/firstinspires/ftc/teamcode/limelight/HivePoseEstimatorChecks.java) contains the three-angle synthetic case.
- [Limelight.java](../../TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Limelight.java) converts the fixed-tag field pose.
