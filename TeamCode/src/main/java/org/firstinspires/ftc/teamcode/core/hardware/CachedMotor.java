package org.firstinspires.ftc.teamcode.core.hardware;

import com.qualcomm.robotcore.hardware.DcMotorEx;

/** Wraps a DcMotorEx and only sends motor.setPower() to the hub when the value changed. */
public final class CachedMotor {
    private final DcMotorEx motor;
    private final double eps;
    private double last;
    private boolean initialized;
    private final SensorReadings.Sample velocity = new SensorReadings.Sample();

    public CachedMotor(DcMotorEx motor, double eps) {
        this.motor = motor;
        this.eps = eps;
    }

    public synchronized boolean set_power(double p) {
        return write_power(p, false);
    }

    /** Reject a faulty calculated command while retaining the last accepted finite power. */
    public synchronized boolean set_power_if_valid(double p) {
        return write_power(p, true);
    }

    private boolean write_power(double p, boolean retainOnInvalid) {
        boolean valid = SensorReadings.is_valid(p);
        if (!valid && retainOnInvalid) return false;
        p = valid ? Math.max(-1, Math.min(1, p)) : 0;
        boolean stopNow = (p == 0.0 && last != 0.0); // always send an exact stop
        if (stopNow || !initialized || Math.abs(p - last) >= eps) {
            motor.setPower(p);
            last = p;
            initialized = true;
        }
        return valid;
    }

    public synchronized boolean set_velocity(double ticksPerSecond) {
        boolean valid = SensorReadings.is_valid(ticksPerSecond);
        motor.setVelocity(valid ? ticksPerSecond : 0);
        initialized = false;
        return valid;
    }

    /** One hardware read and one finite check; consumers reuse the returned sample. */
    public synchronized SensorReadings.Sample sample_velocity() {
        velocity.update(motor.getVelocity());
        return velocity;
    }

    public SensorReadings.Sample get_velocity_sample() {
        return velocity;
    }

    public synchronized double get_power() { return last; }

/** Forces the next set_power() to hit the hardware. Call after setMode()/direction changes. */
    public synchronized void invalidate() {
        initialized = false;
    }

    /** Underlying motor for reads (position, velocity) and mode/direction/zero-power config. */
    public DcMotorEx raw() {
        return motor;
    }
}
