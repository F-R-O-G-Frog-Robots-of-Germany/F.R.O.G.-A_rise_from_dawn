package org.firstinspires.ftc.teamcode.limelight;

/**
 * Nominal BIOBUZZ geometry, inches. World origin is field center on the tiles;
 * +X is audience-right, +Y away from the audience, +Z up. This is not shooting-pose data.
 * Sources: https://ftc-resources.firstinspires.org/ftc/game/manual (9.6, 9.9, Fig. 9-15–17)
 * https://ftc-resources.firstinspires.org/ftc/field/field-cad-step
 * Inspected STEP SHA256: 05b35961c7df847741031f00fda73ddd068537f809e92a11b1cd59a94bcc8331
 */
public final class HiveGeometry {
    private HiveGeometry() { }

    public static final double TAG_SIZE_IN = 3.25; // Manual: black-square side, 36h11.
    public static final double HINGE_HEIGHT_IN = 43.95; // Manual nominal height above tiles.
    public static final double HINGE_X_IN = 12.75; // CAD: half the 25.50-inch hinge-point spacing.
    private static final double HINGE_DOWN_FROM_TAG_IN = 14.269059480313; // Derived CAD + sticker dimensions.
    private static final double HINGE_NORMAL_FROM_STICKER_IN = 1.488167842276; // Derived CAD, sticker Z=0.
    // ASSUMED: print is on sticker CAD Z=+0.01. CAD has a 0.01-inch slab, but no printed texture.
    private static final double PRINTED_FACE_OFFSET_IN = 0.01;
    private static final double CELL_AXIS_OFFSET_IN = 0.000944881890; // Tiny actual CAD assembly offset.
    private static final double[] TAG_X_IN = {6.5, 2.75, -2.75, -6.5}; // Manual strip, increasing IDs.

    // ARBITRARY: future robot mounting is unknown; replace with measured FLU mounting values.
    public static final double CAMERA_FORWARD_IN = 0;
    public static final double CAMERA_LEFT_IN = 0; // ARBITRARY: unknown mounting.
    public static final double CAMERA_HEIGHT_IN = 10; // ARBITRARY: unknown mounting.
    public static final double CAMERA_PITCH_UP_DEG = 0; // ARBITRARY: unknown mounting; level camera.

    public static HivePoseEstimator.TagModel[] tag_models() {
        HivePoseEstimator.TagModel[] tags = new HivePoseEstimator.TagModel[16];
        for (int id = 30; id <= 45; id++) {
            boolean red = id <= 37;
            boolean audience = id >= 34 && id <= 41;
            double lateral = TAG_X_IN[(id - 30) % 4]
                    + (audience ? -CELL_AXIS_OFFSET_IN : CELL_AXIS_OFFSET_IN);
            // ASSUMED: native target axes are print-right/down/into the tag, a proper rotation frame.
            // Limelight's prose says right/down/out (left-handed); physical normal/sign needs verification.
            // ASSUMED: individual code frames align with strip artwork; CAD has no printed tag texture.
            tags[id - 30] = new HivePoseEstimator.TagModel(id,
                    new double[]{red ? -HINGE_X_IN : HINGE_X_IN, 0, HINGE_HEIGHT_IN},
                    new double[]{1, 0, 0},
                    new double[]{lateral, HINGE_DOWN_FROM_TAG_IN,
                            HINGE_NORMAL_FROM_STICKER_IN + PRINTED_FACE_OFFSET_IN},
                    new double[]{audience ? 1 : -1, 0, 0}, true,
                    "Manual/CAD; ASSUMED native tag normal and printed slab face");
        }
        return tags;
    }

    public static HivePoseEstimator.CameraMount camera_mount() {
        double pitch = Math.toRadians(CAMERA_PITCH_UP_DEG);
        double c = Math.cos(pitch), s = Math.sin(pitch);
        // Camera FLU pitched upward into robot FLU. Yaw and roll are ARBITRARY zero until measured.
        return new HivePoseEstimator.CameraMount(new double[]{c,0,-s, 0,1,0, s,0,c},
                new double[]{CAMERA_FORWARD_IN, CAMERA_LEFT_IN, CAMERA_HEIGHT_IN}, true);
    }
}
