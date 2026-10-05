package org.firstinspires.ftc.teamcode.core.hardware;

import com.qualcomm.robotcore.hardware.DcMotorEx;

/** Wraps a DcMotorEx and only sends setPower() to the hub when the value changed. */
public final class CachedMotor {
    private final DcMotorEx motor;
    private final double eps;
    private double last = Double.NaN; // NaN forces the first write

    public CachedMotor(DcMotorEx motor, double eps) {
        this.motor = motor;
        this.eps = eps;
    }

    public synchronized void setPower(double p) {
        boolean stopNow = (p == 0.0 && last != 0.0); // always send an exact stop
        if (stopNow || Double.isNaN(last) || Math.abs(p - last) >= eps) {
            motor.setPower(p);
            last = p;
        }
    }

    /** Forces the next setPower() to hit the hardware. Call after setMode()/direction changes. */
    public synchronized void invalidate() {
        last = Double.NaN;
    }

    /** Underlying motor for reads (position, velocity) and mode/direction/zero-power config. */
    public DcMotorEx raw() {
        return motor;
    }
}
