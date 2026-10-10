package org.firstinspires.ftc.teamcode.core.hardware;

import com.qualcomm.robotcore.hardware.Servo;

/** Wraps a Servo and only sends set_position() to the hub when the value changed. */
public final class CachedServo {
    private final Servo servo;
    private final double eps;
    private double last;
    private boolean initialized;

    public CachedServo(Servo servo, double eps) {
        this.servo = servo;
        this.eps = eps;
    }

    public synchronized boolean set_position(double pos) {
        if (!SensorReadings.is_valid(pos)) return false;
        pos = Math.max(0, Math.min(1, pos));
        if (!initialized || Math.abs(pos - last) >= eps) {
            servo.setPosition(pos);
            last = pos;
            initialized = true;
        }
        return true;
    }

    public synchronized void invalidate() {
        initialized = false;
    }

    public Servo raw() {
        return servo;
    }
}
