package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.api.PoseFactory;
import com.pedropathing.math.Pose;

import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;

public final class PoseMirroring {
    private static final PoseFactory DEGREES_MIRRORED_ACROSS_FIELD_CENTER = PoseFactory.degrees().mirrorAroundPoint(72.0, 72.0);

    private PoseMirroring() {
    }

    public static Pose mirror_across_field_center(Pose POSE) {
        return DEGREES_MIRRORED_ACROSS_FIELD_CENTER.of(POSE.x(), POSE.y(), Math.toDegrees(POSE.heading()));
    }

    public static Pose mirror_if_blue(Pose POSE, Alliance alliance) {
        if (alliance == Alliance.BLUE) {
            return mirror_across_field_center(POSE);
        }
        return POSE;
    }

    public static Pose mirror_if_red(Pose POSE, Alliance alliance) {
        if (alliance == Alliance.RED) {
            return mirror_across_field_center(POSE);
        }
        return POSE;
    }
}
