package org.firstinspires.ftc.teamcode.core.hardware;

/** Measured footprint with the intake at the front of the drivetrain. */
public final class RobotDimensions {
    private static final double CM_PER_INCH = 2.54;
    public static final double DRIVETRAIN_WIDTH_IN = 45.3 / CM_PER_INCH;
    public static final double DRIVETRAIN_LENGTH_IN = 42.2 / CM_PER_INCH;
    public static final double LENGTH_WITH_INTAKE_IN = 18.0;

    // The localization reference is modeled at the center of the drivetrain.
    public static final double REAR_EXTENT_IN = DRIVETRAIN_LENGTH_IN / 2;
    public static final double FRONT_EXTENT_IN = LENGTH_WITH_INTAKE_IN - REAR_EXTENT_IN;
    public static final double BODY_CENTER_FORWARD_IN = (FRONT_EXTENT_IN - REAR_EXTENT_IN) / 2;

    private RobotDimensions() { }
}
