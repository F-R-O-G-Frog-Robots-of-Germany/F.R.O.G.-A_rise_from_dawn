package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.pedropathing.math.Velocity;
import com.pedropathing.revhub.localizers.PinpointConfig;
import com.pedropathing.revhub.localizers.PinpointLocalizer;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.Register;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;

import static com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.DeviceStatus.FAULT_BAD_READ;
import static com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.ErrorDetectionType.CRC;
import static com.qualcomm.hardware.gobilda.GoBildaPinpointDriver.ErrorDetectionType.LOCAL_TEST;

/** Selects the Pinpoint read window before Pedro initializes its localizer. */
final class PinpointBulkReads {
    private static final Register[] COMPACT_WINDOW = {
            Register.DEVICE_STATUS, Register.X_POSITION, Register.Y_POSITION, Register.H_ORIENTATION,
            Register.X_VELOCITY, Register.Y_VELOCITY, Register.H_VELOCITY
    };
    private static final Register[] STANDARD_WINDOW = {
            Register.DEVICE_STATUS, Register.LOOP_TIME, Register.X_ENCODER_VALUE, Register.Y_ENCODER_VALUE,
            Register.X_POSITION, Register.Y_POSITION, Register.H_ORIENTATION,
            Register.X_VELOCITY, Register.Y_VELOCITY, Register.H_VELOCITY
    };

    private PinpointBulkReads() { }

    static PinpointLocalizer create(HardwareMap hardwareMap, PinpointConfig config, boolean preferCompact) {
        GoBildaPinpointDriver pinpoint = hardwareMap.get(GoBildaPinpointDriver.class, config.name.get());
        if (configure_window(pinpoint, preferCompact)) {
            RobotLog.ii("Pinpoint", "Compact bulk read (28 bytes + CRC)");
            return new CompactLocalizer(hardwareMap, config, pinpoint);
        }
        RobotLog.ii("Pinpoint", "Standard bulk read (40 bytes)");
        return new PinpointLocalizer(hardwareMap, config);
    }

    static boolean configure_window(GoBildaPinpointDriver pinpoint, boolean preferCompact) {
        int firmwareVersion = pinpoint.getDeviceVersion();
        if (firmwareVersion <= 0) firmwareVersion = pinpoint.getDeviceVersion();
        if (firmwareVersion <= 0) {
            throw new IllegalStateException("Pinpoint firmware version could not be read");
        }
        if (firmwareVersion < 3) return false;

        // The SDK changes its parser before writing the device, and the write has no result.
        // CRC confirms that the device responds with a frame of the requested length.
        pinpoint.setErrorDetectionType(CRC);
        boolean compact = false;
        RuntimeException compactFailure = null;
        try {
            if (preferCompact) {
                try {
                    compact = write_verified_window(pinpoint, COMPACT_WINDOW);
                    if (compact) return true;
                } catch (RuntimeException failure) {
                    compactFailure = failure;
                }
            }
            if (!write_verified_window(pinpoint, STANDARD_WINDOW)) {
                throw new IllegalStateException("Pinpoint could not verify its standard bulk read");
            }
            return false;
        } catch (RuntimeException failure) {
            if (compactFailure != null && compactFailure != failure) failure.addSuppressed(compactFailure);
            throw failure;
        } finally {
            if (!compact) pinpoint.setErrorDetectionType(LOCAL_TEST);
        }
    }

    private static boolean write_verified_window(GoBildaPinpointDriver pinpoint, Register[] registers) {
        pinpoint.setBulkReadScope(registers);
        pinpoint.update();
        if (pinpoint.getDeviceStatus() != FAULT_BAD_READ) return true;
        pinpoint.update();
        return pinpoint.getDeviceStatus() != FAULT_BAD_READ;
    }

    /** Prevents Pedro from using a held sample when a compact frame fails CRC. */
    static final class CompactLocalizer extends PinpointLocalizer {
        private final GoBildaPinpointDriver pinpoint;
        private final double maxPositionDelta;
        private final double maxLinearVelocity;
        private double previousHeading;

        CompactLocalizer(HardwareMap hardwareMap, PinpointConfig config, GoBildaPinpointDriver pinpoint) {
            super(hardwareMap, config);
            this.pinpoint = pinpoint;
            maxPositionDelta = config.globalDistanceUnit.get().fromMm(5000);
            maxLinearVelocity = config.globalDistanceUnit.get().fromMm(10000);
            if (pinpoint.getDeviceStatus() == FAULT_BAD_READ) super.update();
            previousHeading = verify_state(null);
        }

        @Override
        public void setPose(Pose pose) {
            super.setPose(pose);
            previousHeading = pose.heading();
        }

        @Override
        public void update() {
            MotionState previous = state();
            super.update();
            // Pedro's super constructor calls update() before this instance is initialized.
            if (pinpoint == null) return;
            if (pinpoint.getDeviceStatus() == FAULT_BAD_READ) super.update();
            previousHeading = verify_state(previous == null ? null : previous.pose());
        }

        private double verify_state(Pose previousPose) {
            if (pinpoint.getDeviceStatus() == FAULT_BAD_READ) {
                throw new IllegalStateException("Pinpoint compact bulk read failed CRC twice");
            }
            // The SDK's CRC mode replaces its local plausibility checks, so enforce them here.
            Pose pose = pose();
            Velocity velocity = velocity();
            double heading = pinpoint.getHeading(UnnormalizedAngleUnit.RADIANS);
            if (!SensorReadings.is_valid(pose)
                    || !SensorReadings.are_valid(heading, velocity.vx, velocity.vy, velocity.omega)
                    || Math.abs(velocity.vx) > maxLinearVelocity || Math.abs(velocity.vy) > maxLinearVelocity
                    || Math.abs(velocity.omega) > 120
                    || (previousPose != null && (Math.abs(pose.x() - previousPose.x()) > maxPositionDelta
                        || Math.abs(pose.y() - previousPose.y()) > maxPositionDelta
                        || Math.abs(heading - previousHeading) > 120))) {
                throw new IllegalStateException("Pinpoint compact bulk read reported implausible odometry");
            }
            return heading;
        }
    }
}
