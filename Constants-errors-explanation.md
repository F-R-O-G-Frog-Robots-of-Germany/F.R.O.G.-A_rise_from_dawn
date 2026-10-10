# `Constants.java` Error Explanation

## Summary

`Constants.java` was written for the Pedro Pathing 1.x API. The project now uses Pedro Pathing 3.0.0:

```gradle
implementation 'com.pedropathing:core:3.0.0'
implementation 'com.pedropathing:revhub:3.0.0'
implementation 'com.pedropathing:tuning:1.0.0'
```

Pedro 3 reorganized the library. The old `FollowerConstants`, builder classes, drivetrain constants, localizer constants, and `PathConstraints` types are not compatible with the new API.

## Import errors

### `com.pedropathing.control.FilteredPIDFCoefficients`

This is an old Pedro 1.x package. Pedro 3 uses the new controller API under packages such as `com.pedropathing.controllers` and `com.pedropathing.algorithm`.

The old `FilteredPIDFCoefficients` object is not a Pedro 3 configuration type.

### `com.pedropathing.control.PIDFCoefficients`

This is also an old Pedro 1.x class. Pedro 3 configures controllers using `Controller` objects, for example:

```java
Controller.proportional(0.3)
Controller.proportionalFeedforward(0.01)
```

### `com.pedropathing.follower.FollowerConstants`

Pedro 3 no longer uses the old fluent `FollowerConstants` builder. Follower settings are divided into configuration objects, primarily:

- `ForesightConfig` for path-following and control behavior
- `MecanumConfig` for drivetrain settings
- `PinpointConfig` for the Pinpoint localizer

### `com.pedropathing.ftc.FollowerBuilder`

`FollowerBuilder` is an old Pedro 1.x construction API. Pedro 3 constructs a follower directly:

```java
return new Follower(
        new PinpointLocalizer(hardwareMap, localizerConfig),
        new Mecanum(hardwareMap, drivetrainConfig),
        new Foresight(foresightConfig)
);
```

### `com.pedropathing.ftc.drivetrains.MecanumConstants`

This class was replaced by:

```java
import com.pedropathing.revhub.drivetrains.MecanumConfig;
```

The old fluent methods such as `.maxPower()`, `.rightFrontMotorName()`, and `.xVelocity()` are not Pedro 3 methods.

### `com.pedropathing.ftc.localization.constants.PinpointConstants`

This class was replaced by:

```java
import com.pedropathing.revhub.localizers.PinpointConfig;
```

The old fluent methods such as `.forwardPodY()`, `.hardwareMapName()`, and `.encoderResolution()` are not Pedro 3 methods.

### `com.pedropathing.paths.PathConstraints`

This is not part of the Pedro 3 API. Pedro 3 configures path speed, acceleration, braking, and deceleration through `ForesightConfig`.

The replacement settings include:

```java
c.brakeAggression.set(1.0);
c.maxBrakingPower.set(0.2);
c.maxAccelerationConstraint.set(40.0);
c.maxVelocityConstraint.set(30.0);
c.maxDecelerationConstraint.set(50.0);
c.maxDecelerationScale.set(0.8);
c.coastDownToVelocity.set(0.0);
```

## `followerConstants` errors

This declaration uses the removed class and removed fluent methods:

```java
public static FollowerConstants followerConstants = new FollowerConstants()
```

The following methods belong to the old API and must be replaced with fields in `ForesightConfig`:

- `.mass(...)`
- `.forwardZeroPowerAcceleration(...)`
- `.lateralZeroPowerAcceleration(...)`
- `.translationalPIDFCoefficients(...)`
- `.headingPIDFCoefficients(...)`
- `.drivePIDFCoefficients(...)`
- `.centripetalScaling(...)`

In Pedro 3, these values are configured in the `ForesightConfig` lambda. Some values do not have a one-to-one replacement because Pedro 3 uses different controller and braking models.

## `defaultPathConstraints` errors

This declaration cannot compile in Pedro 3:

```java
public static PathConstraints defaultPathConstraints = new PathConstraints(...);
```

The class and its eight-argument constructor were from an older API. The values also cannot be copied directly into the new API because the meaning and units of several parameters changed.

The Pedro 3 replacement is a `ForesightConfig`, such as the configuration in:

```text
TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/pedro/PathConstraints.java
```

The old comments describe these legacy parameters:

- `tValueConstraint`
- `velocityConstraint`
- `translationalConstraint`
- `headingConstraint`
- `timeoutConstraint`
- `brakingStrength`
- `BEZIER_CURVE_SEARCH_LIMIT`
- `brakingStart`

Pedro 3 does not expose these as the same eight constructor parameters.

## `driveConstants` errors

This declaration uses a removed class:

```java
public static MecanumConstants driveConstants = new MecanumConstants()
```

The old fluent methods are also removed or renamed:

- `.maxPower(...)`
- `.rightFrontMotorName(...)`
- `.rightRearMotorName(...)`
- `.leftFrontMotorName(...)`
- `.leftRearMotorName(...)`
- `.rightFrontMotorDirection(...)`
- `.rightRearMotorDirection(...)`
- `.leftFrontMotorDirection(...)`
- `.leftRearMotorDirection(...)`
- `.xVelocity(...)`
- `.yVelocity(...)`

Pedro 3 uses `MecanumConfig` with a configuration lambda:

```java
public static MecanumConfig drivetrainConfig = new MecanumConfig(c -> {
    c.frontLeftName.set("leftFront");
    c.backLeftName.set("leftRear");
    c.frontRightName.set("rightFront");
    c.backRightName.set("rightRear");
});
```

The exact property names for motor directions and velocity tuning should be checked against the current Pedro 3 `MecanumConfig` API.

## `localizerConstants` errors

This declaration uses the removed class:

```java
public static PinpointConstants localizerConstants = new PinpointConstants()
```

The old fluent methods are not Pedro 3 methods:

- `.forwardPodY(...)`
- `.strafePodX(...)`
- `.distanceUnit(...)`
- `.hardwareMapName(...)`
- `.encoderResolution(...)`
- `.forwardEncoderDirection(...)`
- `.strafeEncoderDirection(...)`

Pedro 3 uses `PinpointConfig`:

```java
public static PinpointConfig localizerConfig = new PinpointConfig(c -> {
    c.name.set("pinpoint");
    c.xPodOffset.set(0.0);
    c.yPodOffset.set(0.0);
    c.xPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
    c.yPodDirection.set(GoBildaPinpointDriver.EncoderDirection.FORWARD);
});
```

## `create_follower` errors

This old construction chain is incompatible with Pedro 3:

```java
return new FollowerBuilder(followerConstants, hardwareMap)
        .pinpointLocalizer(localizerConstants)
        .pathConstraints(defaultPathConstraints)
        .mecanumDrivetrain(driveConstants)
        .build();
```

Pedro 3 uses direct construction:

```java
public static Follower create_follower(HardwareMap hardwareMap) {
    return new Follower(
            new PinpointLocalizer(hardwareMap, localizerConfig),
            new Mecanum(hardwareMap, drivetrainConfig),
            new Foresight(foresightConfig)
    );
}
```

Required imports for that construction include:

```java
import com.pedropathing.algorithm.Foresight;
import com.pedropathing.algorithm.ForesightConfig;
import com.pedropathing.follower.Follower;
import com.pedropathing.revhub.drivetrains.Mecanum;
import com.pedropathing.revhub.drivetrains.MecanumConfig;
import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
```

## Unused warnings

Warnings such as these are not compilation errors:

- `Class 'Constants' is never used`
- `Field 'followerConstants' is never used`
- `Field 'defaultPathConstraints' is never used`
- `Method 'create_follower' is never used`

They mean no other source file currently references those declarations. They can be resolved by calling `Constants.create_follower(hardwareMap)` from an op mode, or suppressed if the file is intended as a configuration holder.

## Important note

Changing only the import statements will not complete this migration. `Constants.java` needs to be rewritten around Pedro 3 configuration objects. The current `PathConstraints.java` already demonstrates the new `ForesightConfig` style, but its tuning values should be retuned because Pedro 1.x constraint values do not map exactly to Pedro 3.

This file is documentation only and is intentionally not staged or added to Git.
