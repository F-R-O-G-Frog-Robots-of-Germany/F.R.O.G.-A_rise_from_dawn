package org.firstinspires.ftc.teamcode.limelight;

import com.pedropathing.utils.Angle;

/** Field corner coordinates in inches; heading is counterclockwise radians. */
public final class VisionPose {
    public static final VisionPose ZERO = new VisionPose(0, 0, 0);
    private static final long CLOCK_START = System.nanoTime();
    public final double x;
    public final double y;
    public final double heading;

    public VisionPose(double x, double y, double heading) {
        this.x = x;
        this.y = y;
        this.heading = heading;
    }

    public boolean is_finite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(heading);
    }

    public double distance_to(VisionPose other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    public double distance_squared(VisionPose other) {
        double dx = other.x - x;
        double dy = other.y - y;
        return dx * dx + dy * dy;
    }

    public static double wrap_pi(double angle) {
        if (!Double.isFinite(angle)) return Double.NaN;
        double wrapped = Angle.normalizeSigned(angle);
        return wrapped == -Math.PI && angle > 0 ? Math.PI : wrapped;
    }

    static long now_ms() {
        return (System.nanoTime() - CLOCK_START) / 1_000_000;
    }
}
