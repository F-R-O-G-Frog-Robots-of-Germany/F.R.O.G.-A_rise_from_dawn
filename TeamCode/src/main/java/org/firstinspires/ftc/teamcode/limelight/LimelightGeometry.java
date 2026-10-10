package org.firstinspires.ftc.teamcode.limelight;

/** Defaults copied from ma douce. Measure the mounting before enabling pinhole range. */
public final class LimelightGeometry {
    private LimelightGeometry() { }

    public static final double CAMERA_HEIGHT_IN = 10.0;
    public static final double CAMERA_FORWARD_OFFSET_IN = 0.0;
    public static final double CAMERA_LEFT_OFFSET_IN = 0.0;
    public static final double CAMERA_PITCH_DEG = 0.0;
    public static final double CAMERA_YAW_DEG = 0.0;
    public static final double NEAREST_FORWARD_IN = 8.0;
    public static final double FAR_FORWARD_IN = 60.0;
    public static final double HORIZONTAL_FOV_DEG = 62.5;
    public static final double VERTICAL_FOV_DEG = 48.9;
    public static final boolean USE_PINHOLE_RANGE = false;
    public static final double QUADRATIC_LINEAR_IN = 20.0;
    public static final double QUADRATIC_CURVE_IN =
            FAR_FORWARD_IN - NEAREST_FORWARD_IN - QUADRATIC_LINEAR_IN;
    public static final double POLLEN_DIAMETER_IN = 3.0;
    public static final double TRACK_MATCH_DISTANCE_IN = POLLEN_DIAMETER_IN;
    public static final double FIELD_WIDTH_IN = 144.0;
    public static final double FIELD_CENTRE_IN = FIELD_WIDTH_IN / 2.0;
}
