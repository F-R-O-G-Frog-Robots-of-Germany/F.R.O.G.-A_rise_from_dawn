package org.firstinspires.ftc.teamcode.core.hardware;

import com.qualcomm.robotcore.hardware.Servo;

/** Wraps a Servo and only sends setPosition() to the hub when the value changed. */
public final class CachedServo {
    private final Servo servo;
    private final double eps;
    private double last = Double.NaN;

    public CachedServo(Servo servo, double eps) {
        this.servo = servo;
        this.eps = eps;
    }

    public synchronized void setPosition(double pos) {
        if (Double.isNaN(last) || Math.abs(pos - last) >= eps) {
            servo.setPosition(pos);
            last = pos;
        }
    }

    public synchronized void invalidate() {
        last = Double.NaN;
    }

    public Servo raw() {
        return servo;
    }
}
