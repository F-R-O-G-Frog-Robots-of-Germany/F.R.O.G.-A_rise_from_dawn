package org.firstinspires.ftc.teamcode.limelight;

/** Limelight 3A geometry. Measure the mounting before using detections to drive the robot. */
public final class LimelightGeometry {
    private LimelightGeometry() { }

    public static final double CAMERA_HEIGHT_IN = 10.0;
    public static final double CAMERA_FORWARD_OFFSET_IN = 0.0;
    public static final double CAMERA_LEFT_OFFSET_IN = 0.0;
    public static final double CAMERA_PITCH_DEG = 0.0;
    public static final double CAMERA_YAW_DEG = 0.0;
    public static final double NEAREST_FORWARD_IN = 8.0;
    public static final double FAR_FORWARD_IN = 60.0;
    // Published Limelight 3A field of view, measured across the full image.
    public static final double HORIZONTAL_FOV_DEG = 54.5;
    public static final double VERTICAL_FOV_DEG = 42.0;
    public static final double POLLEN_DIAMETER_IN = 3.0;
    public static final double TRACK_MATCH_DISTANCE_IN = POLLEN_DIAMETER_IN;
    public static final double FIELD_WIDTH_IN = 144.0;
    public static final double FIELD_CENTRE_IN = FIELD_WIDTH_IN / 2.0;
}
