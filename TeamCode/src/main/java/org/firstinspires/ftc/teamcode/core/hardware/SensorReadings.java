package org.firstinspires.ftc.teamcode.core.hardware;

import com.pedropathing.math.Pose;

/** Shared numeric boundary checks. Cache a sample's validity instead of checking it again. */
public final class SensorReadings {
    private SensorReadings() { }

    public static boolean is_valid(double value) {
        return Double.isFinite(value);
    }

    public static boolean is_valid(Pose pose) {
        return pose != null && are_valid(pose.x(), pose.y(), pose.heading());
    }

    public static boolean are_valid(double a, double b) {
        return is_valid(a) && is_valid(b);
    }

    public static boolean are_valid(double a, double b, double c) {
        return are_valid(a, b) && is_valid(c);
    }

    public static boolean are_valid(double a, double b, double c, double d) {
        return are_valid(a, b) && are_valid(c, d);
    }

    public static boolean are_valid(double a, double b, double c, double d, double e, double f) {
        return are_valid(a, b, c) && are_valid(d, e, f);
    }

    public static boolean are_valid(double a, double b, double c, double d, double e, double f, double g) {
        return are_valid(a, b, c, d, e, f) && is_valid(g);
    }

    public static boolean are_valid(double... values) {
        for (double value : values) {
            if (!is_valid(value)) return false;
        }
        return true;
    }

    /** Check before clipping: infinity must not become a valid maximum command. */
    public static double clamp(double value, double min, double max) {
        return are_valid(value, min, max) && min <= max
                ? Math.max(min, Math.min(max, value)) : Double.NaN;
    }

    public static final class Sample {
        private double value;
        private boolean valid;

        public boolean update(double reading) {
            valid = SensorReadings.is_valid(reading);
            if (valid) value = reading;
            return valid;
        }

        public double get_value() { return value; }
        public boolean is_valid() { return valid; }
        public void reset() { value = 0; valid = false; }
    }
}
