# Complete numerical example: from a tipping Hive tag to robot X, Y and heading

**Goal:** Show every calculation from one measured relative tag pose to robot position and heading, including the circular tag motion and camera mounting offset.

18 October 2026. Arbitrary illustrative values, perfect measurements and exact rigid geometry. None of these numbers are taken from the Hive CAD or proposed as production constants. Markdown explanation only; no implementation or competition changes.

## 1. Coordinate conventions and what is actually measured

All lengths are inches and all angles below are degrees. World coordinates are X east, Y north and Z up. Heading is zero facing east and positive toward north.

For arithmetic, camera coordinates are **forward, left, up**. This is a convenient converted frame, not the SDK's raw optical coordinate convention. The camera is level and aligned with the robot. Its field heading is initially unknown.

T denotes a rigid tag-attached reference frame chosen for this example. It is not asserted to be the SDK's default AprilTag frame. Its fixed relationship to the printed tag must be known when converting a real measurement. Here its origin is the tag center, its local +Y is the signed hinge direction, and the vector to the chosen pivot point is along its local +Z.

The starting measurement is a **full camera-relative 3D tag pose**: translation and rotation. We are not deriving that pose from pixels in this worked example. That is the camera/PnP measurement stage discussed in the [research report](Hive-Single-Frame-Localization-and-MegaTag2-2026-10-18.md). A tag center or `tx`/`ty` alone does not supply these inputs.

| Input | Arbitrary value | Source within this example |
|---|---|---|
| Pivot world position `H_W` | `(100,100,40)` | Previously measured field geometry |
| Signed hinge direction `a_W` | `(1,0,0)`, east | Previously known field geometry |
| Tag → pivot vector `r_TH` | `(0,0,20)` in T | Previously known rigid geometry |
| Signed hinge direction `a_T` | `(0,1,0)` in T | Previously known tag/hinge orientation |
| Camera → tag translation `t_CT` | `(70,8,30−10√3)` = `(70,8,12.6794919243)` | Camera measurement |
| Tag rotation `R_CT` | −30° about the camera's left axis | Camera measurement |
| Robot → camera mounting offset `t_RC` | `(4,2,10)` in robot coordinates | Previously known mounting |
| Camera mounting rotation `R_RC` | Identity | Assumed aligned mounting |
| Vertical direction | Camera/robot level | Explicit simplifying assumption |

Only robot field position and heading are unknown. The measured −30° is a camera-relative tag orientation in this chosen example, not a separately assumed global Hive angle.

## 2. Write out the measured rotation

The rotation around the left/Y axis is:

```text
R_y(theta) = [ cos(theta)   0   sin(theta) ]
             [     0       1       0      ]
             [-sin(theta)  0   cos(theta) ]

cos(-30°) = sqrt(3)/2 = 0.866025403784...
sin(-30°) = -1/2      = -0.5

R_CT = [ 0.866025403784   0  -0.5            ]
       [ 0                1   0              ]
       [ 0.5              0   0.866025403784 ]
```

This matrix tells us how vectors expressed in the tag-attached frame appear in camera coordinates. It includes the tag tilt that a fixed-offset addition would miss.

## 3. Rotate the tag-to-pivot vector

Multiply the matrix by the known local vector `(0,0,20)`:

```text
forward = 0.8660254*0 + 0*0 + (-0.5)*20 = -10
left    = 0*0         + 1*0 + 0*20       =   0
up      = 0.5*0       + 0*0 + 0.8660254*20
        = 10*sqrt(3)
        = 17.3205080757

R_CT * r_TH = (-10, 0, 17.3205080757)
```

The pivot is therefore 10 inches backward and 17.320508 inches upward **from the observed tag**, not 20 inches straight up in the camera frame.

Add that rotated offset to the measured tag position:

```text
h_C = t_CT + R_CT*r_TH

forward = 70            - 10            = 60
left    = 8             + 0             =  8
up      = (30-10√3)     + 10√3          = 30

h_C = (60,8,30)
```

This is the camera-to-pivot vector: 60 inches forward, 8 left and 30 up. The pivot has not been visually detected. Its relative position has been inferred from the measured tag pose and known rigid geometry.

## 4. Calculate heading, rather than assume it

Use the same measured rotation on the known signed axis vector:

```text
a_C = R_CT*a_T = R_CT*(0,1,0)

forward component = 0.8660254*0 + 0*1 + (-0.5)*0 = 0
left component    = 0*0         + 1*1 + 0*0      = 1
up component      = 0.5*0       + 0*1 + 0.8660*0 = 0

a_C = (0,1,0)
```

The signed hinge direction points directly left in the level camera frame. Its known world direction is east. These are two descriptions of the same physical direction.

```text
axis bearing in world  = atan2(a_W.y, a_W.x) = atan2(0,1) =  0°
axis bearing in camera = atan2(a_C.left, a_C.forward)
                       = atan2(1,0) = +90°

camera heading = world bearing - camera bearing
               = 0° - 90°
               = -90°
```

Thus the camera faces south. East is to its left, exactly as observed. Because camera and robot are aligned, robot heading is also −90°. Known level orientation is essential to this short two-dimensional bearing calculation; the general nonlevel construction in the report uses gravity explicitly. The signed axis avoids a 180° ambiguity.

## 5. Convert the camera-to-pivot vector into world coordinates

For heading `psi`, the horizontal rotation is:

```text
worldX = cos(psi)*forward - sin(psi)*left
worldY = sin(psi)*forward + cos(psi)*left
worldZ = up
```

Here `cos(-90°)=0` and `sin(-90°)=-1`. Apply this to `(60,8,30)`:

```text
worldX = 0*60 - (-1)*8 =   8
worldY = -1*60 + 0*8   = -60
worldZ = 30

camera-to-pivot vector in world = (8,-60,30)
```

The known pivot is `(100,100,40)`. Since `camera position + camera-to-pivot vector = pivot position`, subtract:

```text
camera world X = 100 - 8     =  92
camera world Y = 100 - (-60) = 160
camera world Z = 40 - 30    =  10

camera world position = (92,160,10)
```

## 6. Convert camera position into robot position

The camera is mounted 4 inches forward, 2 left and 10 up from the robot reference point. Rotate that offset by the robot heading of −90°:

```text
mounting offset world X = 0*4 - (-1)*2 =  2
mounting offset world Y = -1*4 + 0*2   = -4
mounting offset world Z = 10
```

Subtract it from the camera position:

```text
robot X = 92  - 2    =  90
robot Y = 160 - (-4) = 164
robot Z = 10  - 10   =   0

RESULT: robot X = 90 in, Y = 164 in, heading = -90°
```

The reference point ends on the floor, consistent with the example's assumptions. Neither X, Y nor heading was supplied as a measurement input.

## 7. Check the whole chain backward

Starting with the result, reconstruct the field pivot:

```text
robot position                            = (90,164,0)
+ rotated robot-to-camera offset          = ( 2, -4,10)
                                          = (92,160,10)   camera
+ rotated camera-to-tag translation       = ( 8,-70,12.6794919243)
                                          = (100,90,22.6794919243) tag
+ rotated tag-to-pivot offset             = ( 0, 10,17.3205080757)
                                          = (100,100,40)  pivot
```

We recover the original measured field pivot exactly before rounding.

## 8. Check an actual circular change in Hive position

Keep camera and robot stationary. In another Hive orientation, suppose the tag-attached frame is aligned with the camera, so `R_CT=I`. The tag measurement is now `(60,8,10)`.

```text
rotated tag-to-pivot offset = I*(0,0,20) = (0,0,20)
h_C = (60,8,10) + (0,0,20) = (60,8,30)
a_C = I*(0,1,0) = (0,1,0)
```

Both the inferred pivot vector and axis direction are unchanged, so all subsequent calculations give the same robot pose.

To show that these two tag positions are on a circle, compare their vectors from the pivot, in camera coordinates:

```text
untilted: (60,8,10)       - (60,8,30) = (0,0,-20)
tilted:   (70,8,30-10√3)  - (60,8,30) = (10,0,-10√3)

untilted radius = sqrt(0² + 0² + (-20)²) = 20
tilted radius   = sqrt(10² + 0² + (-10√3)²)
                = sqrt(100+300) = 20
```

Equivalently, for this example's rotation `theta` about the left axis:

```text
tag(theta) = pivot + R_y(theta)*(0,0,-20)
           = (60-20*sin(theta), 8, 30-20*cos(theta))

theta = 0°:   tag = (60,8,10)
theta = -30°: tag = (70,8,12.6794919243)
```

These are two positions on the same 20-inch-radius arc, not straight-line motion. Rotating the opposite tag-to-pivot vector cancels that circular displacement.

## 9. What is assumed, and what has been checked

The assumptions are exact full relative tag pose, correct pose branch, known tag-to-pivot rigid geometry, a fixed signed hinge axis, known mounting and a level camera. The moving Hive is not assumed stationary. Real SDK axes require explicit conversion to the frames used here.

All vector products, the heading, the two pose results and the backward chain were checked numerically with NumPy. An independent mathematical review found the calculations consistent. This is arithmetic verification, not hardware validation or an accuracy prediction.

## 10. Steps 1–4 explained without previous matrix knowledge

### 10.1 A vector is an arrow described by three numbers

In the camera frame, `(70,8,12.679492)` means an arrow that goes 70 inches forward, 8 inches left and 12.679492 inches upward. The arrow starts at the camera and ends at the tag.

Now imagine three short arrows attached rigidly to the Hive at the tag center, labeled tag X, tag Y and tag Z. Their directions move with the Hive. These labels describe the chosen tag-attached frame; tag Z does not mean permanently upward relative to the floor.

The same physical arrow has different numbers depending on which frame describes it. A 20-inch arrow along tag Z is `(0,0,20)` in the tag frame. When that tag Z direction leans, the arrow has both horizontal and vertical components in the camera frame.

### 10.2 Step 1: known geometry supplies the arrow from tag to pivot

For the arbitrary geometry in this example, following tag Z for 20 inches reaches our selected pivot point. This is why `r_TH=(0,0,20)`. The tag-to-pivot relationship stays fixed in the rigid body's own coordinates while its camera-coordinate direction changes during tipping.

We also know that the signed hinge direction follows tag Y: `a_T=(0,1,0)`. Independently, we know that this same signed direction points east on the field: `a_W=(1,0,0)`. Signed means an arrow toward one specified end, not merely an east–west line.

These are known relationships, not camera outputs. Robot position and heading have not been supplied. The chosen axes are illustrative and require a fixed conversion from actual tag/SDK axes.

### 10.3 Step 2: the measured rotation describes where the three arrows point

A rotation matrix is a compact table. Each **column** tells us how one tag-frame direction appears in camera coordinates:

| Tag-frame direction | Camera forward | Camera left | Camera up |
|---|---:|---:|---:|
| One unit along tag X, first matrix column | 0.8660254 | 0 | 0.5 |
| One unit along tag Y, second matrix column | 0 | 1 | 0 |
| One unit along tag Z, third matrix column | −0.5 | 0 | 0.8660254 |

This table describes the same rotation matrix printed in section 2; the presentation above lists its columns as rows for readability. The coefficients are direction proportions, not extra positions to add.

The third column says that each inch along tag Z goes half an inch backward and approximately 0.8660254 inches upward in the camera view. Those numbers follow from the observed 30-degree lean:

```text
horizontal fraction = sin(-30°) = -0.5
vertical fraction   = cos(-30°) =  0.8660254...
```

The negative horizontal sign means backward. Sine and cosine split the sloping arrow into horizontal and vertical parts. Because `0.5² + 0.8660254...² = 1`, a one-unit arrow retains its length when rotated.

Limelight's full relative tag-pose estimate supplies this orientation and the tag translation. We do not obtain it from an assumed robot heading or from a previously known global Hive angle. The image-to-relative-pose calculation is the upstream camera measurement, not something derived in this example.

### 10.4 Step 3: matrix multiplication is a weighted sum of these arrows

For any tag-frame vector `(x,y,z)`, its camera-frame description is:

```text
x * first column + y * second column + z * third column
```

For our vector `(0,0,20)`, the first two terms are zero. Only the third remains:

```text
0*(0.8660254,0,0.5) + 0*(0,1,0) + 20*(-0.5,0,0.8660254)
= (-10,0,17.320508...)
```

That is the entire meaning of `R_CT*r_TH` here. The camera-to-tag arrow and the rotated tag-to-pivot arrow now use the same axes, so we can join them head to tail by adding components:

```text
camera → tag → pivot

(70,8,12.679492...) + (-10,0,17.320508...)
= (60,8,30)
```

Adding `(0,0,20)` directly to the camera measurement would be wrong: it would confuse tag Z with camera up. Rotating first is exactly what handles the circular motion.

### 10.5 Step 4: a known axis direction supplies the compass reference

Our physical hinge direction follows tag Y, so its camera direction is simply the matrix's second column: `(0,1,0)`. It points directly left. This is orientation information, not another tag-position measurement.

We already know this signed physical direction points east on the field. Ask which way a level camera must face for east to be on its left. It faces south.

The angle calculation expresses that same reasoning. A direction straight ahead has camera bearing 0 degrees; directly left has +90 degrees. East has world bearing 0 degrees:

```text
camera heading + bearing relative to camera = bearing in world
camera heading + 90°                       = 0°
camera heading                             = -90°
```

`atan2(y,x)` in the original calculation is simply the function that converts a two-component direction into its angle: `atan2(1,0)=90°` for camera left, and `atan2(0,1)=0°` for world east. A plain ratio `y/x` would be inconvenient here because one X component is zero; `atan2` handles that and identifies the correct quadrant.

The pivot point alone cannot supply heading. The signed axis direction supplies the extra orientation reference, together with the known level/vertical direction. While the Hive tips, its local Z direction rotates around the hinge, but the direction along that hinge remains unchanged. That is why the heading calculation does not require the Hive to be at an endpoint.

## 11. Why the vertical offset changes between +30° and −30°

The fixed tag-frame vector is not a fixed vertical difference in the camera or world frame. The vertical component used by the calculation is `(R_CT*r_TH).up`, and it changes with the measured orientation.

The earlier arbitrary `(0,0,20)` geometry chose an auxiliary tag Z arrow pointing toward the pivot. It was not a statement about the real Hive's tag axes or a universal 20-inch height difference. Its zero-angle tag lay directly below the pivot, so it was also a poor illustration of one Cell moving above and below the pivot. The following alternative arbitrary geometry makes that particular issue explicit.

Imagine a rigid 20-inch arm from pivot to tag. Define tag X along that arm, from pivot toward tag, so the known tag-to-pivot vector is `r_TH=(-20,0,0)`. When the camera sees that axis inclined by `theta` above horizontal, the vector back toward the pivot has camera components:

```text
forward = -20*cos(theta)
up      = -20*sin(theta)
```

This follows directly by reversing the measured arm direction. Equivalently it is `R_y(-theta)*(-20,0,0)` in the previously defined forward/left/up coordinates. The angle is obtained from the full measured orientation and the known tag-to-arm alignment. Distance alone does not determine it.

Suppose the camera reports these two different observations:

| Quantity, relative to the same stationary camera | Upper example | Lower example |
|---|---:|---:|
| Measured arm inclination `theta` | +30° | −30° |
| Measured tag forward position | 77.320508 in | 77.320508 in |
| Measured tag height above camera | 40 in | 20 in |
| Calculated tag → pivot forward component | −17.320508 in | −17.320508 in |
| Calculated tag → pivot vertical component | **−10 in** | **+10 in** |

For the upper observation:

```text
tag-to-pivot forward = -20*cos(+30°) = -17.320508...
tag-to-pivot up      = -20*sin(+30°) = -10
pivot forward       = 77.320508... - 17.320508... = 60
pivot up            = 40 - 10 = 30
```

For the lower observation:

```text
tag-to-pivot forward = -20*cos(-30°) = -17.320508...
tag-to-pivot up      = -20*sin(-30°) = +10
pivot forward       = 77.320508... - 17.320508... = 60
pivot up            = 20 + 10 = 30
```

The tag height changes by 20 inches. The pivot is below the upper tag but above the lower tag. The calculated vertical offset changes sign accordingly. Only the length of the rigid tag-to-pivot arrow stays 20 inches:

```text
sqrt((17.320508...)² + 10²) = sqrt(300+100) = 20
```

Both observations yield the same pivot vector relative to the stationary camera. No fixed camera-frame tag-to-pivot height has been assumed. These calculations were checked numerically. For a moving camera, its relative pivot vector changes as expected; only the pivot's field position is fixed.

Real geometry requires the full tag-to-pivot vector and its orientation relative to the printed tag, not merely the scalar distance. The field reference position and the measured relative tag pose remain separate inputs.

## Requested changes / owner's notes

- A different set of arbitrary dimensions or orientations to work through: …
- A desired diagram or a particular multiplication to explain further: …
- Further questions about the measurement inputs: …

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
