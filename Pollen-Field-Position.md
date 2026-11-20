# How F.R.O.G. locates pollen on the field

Imagine looking at the field from above. The robot knows where it is and which way it is facing. The Limelight knows where the center of a pollen piece appears in its image. To put that piece on the field map, we first work out where it is relative to the camera, then relative to the robot, and finally relative to the field.

## 1. Find the pollen relative to the camera

The Limelight reports two angles in degrees. The horizontal angle $t_x$ is positive to the right of the image center. The vertical angle $t_y$ is positive above the image center.

### Estimate the forward distance

The default code uses the vertical angle to estimate the forward distance. The camera's configured vertical field of view is $48.9^\circ$, so the bottom of the view is at $-24.45^\circ$ and the top is at $+24.45^\circ$. Adding $24.45^\circ$ and dividing by $48.9^\circ$ turns that angle into a number between 0 and 1. The code clamps any value below 0 to 0 and any value above 1 to 1.

$$
u=\max\left(0,\min\left(1,\frac{t_y+24.45^\circ}{48.9^\circ}\right)\right)
$$

The code then plugs $u$ into a quadratic curve. The $u^2$ term makes the estimate increase faster near the top of the image.

$$
f_c=(8+20u+32u^2)\,\mathrm{in}
$$

At the bottom of the view ($u=0$), the curve gives 8 inches. At the top ($u=1$), it gives 60 inches. These are values from the code's range model.

### Calculate the sideways distance

From above, the forward distance and sideways distance form a right triangle. Tangent gives the ratio of sideways distance to forward distance. The code measures sideways distance as positive to the left, while the camera's horizontal angle is positive to the right, so the formula needs a minus sign.

$$
\ell_c=-f_c\tan(t_x)
$$

**Worked example.** Suppose the Limelight reports $t_y=0^\circ$ and $t_x=10^\circ$. Then $u=0.5$, so $f_c=8+10+8=26$ inches. The sideways displacement is $\ell_c=-26\tan(10^\circ)\approx-4.58$ inches. The pollen is about **26 inches forward and 4.58 inches right** of the camera.

### Optional pinhole model

The code also has a geometric model. Imagine a straight line from the camera through the detected point. Where this line reaches the assumed horizontal target plane gives the estimated position. If we model the detected point as the center of the pollen, that plane is at the height of the pollen center.

The camera points down by $\alpha$, and a detection higher in the image points the line upward by $t_y$. The angle below horizontal is therefore $\alpha-t_y$. In the side-view triangle, the vertical drop is $h$ and the forward distance is $f_c$. Taking the tangent and solving for $f_c$ gives

$$
\tan(\alpha-t_y)=\frac{h}{f_c}
\qquad\Longrightarrow\qquad
f_c=\frac{h}{\tan(\alpha-t_y)}.
$$

For the sideways distance, the code scales the viewing line by how far it must travel to reach the target plane. The expression in the denominator accounts for both the camera tilt and the vertical detection angle.

$$
\ell_c=-\frac{h\tan(t_x)}{\sin\alpha-\tan(t_y)\cos\alpha}.
$$

For example, with $h=10$ inches, $\alpha=30^\circ$, $t_x=10^\circ$, and $t_y=0^\circ$, this model gives about 17.32 inches forward and 3.53 inches right. Both range models feed into the same next two steps.

## 2. Find the pollen relative to the robot

If the camera is aimed sideways by a mounting angle $\psi$, rotate the camera's forward and left distances by $\psi$. Then add the camera's forward and left mounting offsets $d_f$ and $d_\ell$. Sine and cosine tell us how much of each rotated distance points forward and how much points left.

$$
\begin{aligned}
f_r &= d_f+f_c\cos\psi-\ell_c\sin\psi,\\
\ell_r &= d_\ell+f_c\sin\psi+\ell_c\cos\psi.
\end{aligned}
$$

**Worked example, continued.** Suppose the camera points straight ahead ($\psi=0^\circ$) and is mounted 2 inches forward and 1 inch left of the robot center. Using the default model's result, the pollen is $2+26=28$ inches forward and $1-4.58=-3.58$ inches left. That is **28 inches forward and 3.58 inches right** of the robot center.

## 3. Put the pollen on the field map

The last rotation uses the robot's heading $\theta$. A heading of $0^\circ$ points along the field's positive $x$-axis, and a heading of $90^\circ$ points along its positive $y$-axis. After rotating the displacement, add the robot's field coordinates $(x_r,y_r)$.

$$
\begin{aligned}
x_p &= x_r+f_r\cos\theta-\ell_r\sin\theta,\\
y_p &= y_r+f_r\sin\theta+\ell_r\cos\theta.
\end{aligned}
$$

**Worked example, continued.** Put the robot at $(50,40)$ inches with a heading of $90^\circ$. Forward now points along positive $y$, and right points along positive $x$. Since $\cos 90^\circ=0$ and $\sin 90^\circ=1$,

$$
\begin{aligned}
x_p &= 50-(-3.58)=53.58\,\mathrm{in},\\
y_p &= 40+28=68\,\mathrm{in}.
\end{aligned}
$$

The estimated pollen position on the field map is **$(53.58,68)$ inches**. The example uses illustrative mounting and pose values. In the code, trigonometric functions take radians, so the camera angles are converted from degrees. The robot heading is already supplied in radians.

## Variables

| Symbol | Meaning |
| --- | --- |
| $t_x$, $t_y$ | Horizontal and vertical angles to the detected pollen center. Positive means right and up, respectively. |
| $u$ | Vertical angle scaled to a number between 0 and 1. |
| $f_c$, $\ell_c$ | Forward and left displacement from the camera to the pollen. |
| $h$, $\alpha$ | Camera height above the target plane and downward pitch for the optional pinhole model. |
| $d_f$, $d_\ell$ | Forward and left displacement from the robot center to the camera. |
| $\psi$ | Camera mounting yaw, positive counterclockwise from robot forward. |
| $f_r$, $\ell_r$ | Forward and left displacement from the robot center to the pollen. |
| $x_r$, $y_r$ | Robot center in field coordinates. |
| $\theta$ | Robot heading, positive counterclockwise from the field's positive $x$-axis. |
| $x_p$, $y_p$ | Estimated pollen position in field coordinates. |

[Projection implementation](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightCalibration.java) · [Default constants](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightGeometry.java)
