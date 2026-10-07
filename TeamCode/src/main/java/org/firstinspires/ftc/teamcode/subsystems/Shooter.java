package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;
import org.firstinspires.ftc.teamcode.core.units.Units;

public class Shooter {
    private static final double GEAR_RATIO = 1.0;
    public static final double TICKS_PER_REV = 2048.0;
    public static final double ENCODER_TICKS_PER_REV = TICKS_PER_REV * GEAR_RATIO;
    public static final int STANDARD_RPM = 3000;
    public static final boolean DEFAULT_FIXED_RPM = false;
    private static final double FLOAT_RPM = 3000.0;
    public static final double SHOOT_SPEED_TOLERANCE_RPM = 70.0;
    // Manually copy coefficients fitted in Desmos: RPM = A * distance[in]^2 + B * distance[in] + C.
    public static final double DISTANCE_A = 0.0;
    public static final double DISTANCE_B = 1.0;
    public static final double DISTANCE_C = 0.0;

    private CachedMotor shooterMotor;
    private CachedServo servoHood;
    private Units.Alliance alliance;
    private Intake intakeSubsystem;
    private static final double K_P = 0.0002;
    private static final double K_I = 0.0000001;
    private static final double K_D = 0.00001;
    private static final double K_F = 0.00017;
    private static final double PIDF_RANGE_RPM = 150.0;
    private double integralSum;
    private double lastError;
    private double lastTime;
    private boolean bypassEnabled = DEFAULT_FIXED_RPM;
    private double fixedRpm = STANDARD_RPM;
    private double targetRpm = STANDARD_RPM;
    private double currentRpm = Double.NaN;
    private boolean preparingShot;
    private State state = State.FLOAT;

    public enum State { SHOOT, FLOAT, PREPARE }

    public void init(HardwareMap hardwareMap, Intake intakeSubsystem, Units.Alliance alliance) {
        this.intakeSubsystem = intakeSubsystem;
        this.alliance = alliance;
        shooterMotor = new CachedMotor(hardwareMap.get(DcMotorEx.class, "shooter"), 0.002);
        shooterMotor.set_power(0);
        shooterMotor.raw().setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        shooterMotor.invalidate();
        servoHood = new CachedServo(hardwareMap.get(Servo.class, "servoHood"), 0.005);
        // TODO: Hood control intentionally deferred by the team until the mechanism is ready.
        integralSum = 0;
        lastError = 0;
        lastTime = System.nanoTime() / 1e9;
        bypassEnabled = DEFAULT_FIXED_RPM;
        fixedRpm = STANDARD_RPM;
        targetRpm = fixedRpm;
        currentRpm = Double.NaN;
        preparingShot = false;
        state = State.FLOAT;
    }

    public double calculate_flywheel_velocity(double distanceToNearestHive) {
        return DISTANCE_A * distanceToNearestHive * distanceToNearestHive
                + DISTANCE_B * distanceToNearestHive + DISTANCE_C;
    }

    public static final class HivePosition {
        public final String name;
        public final double x, y, z;
        public HivePosition(String name, double x, double y, double z) {
            this.name = name; this.x = x; this.y = y; this.z = z;
        }
    }

    public static final class HiveDistance {
        public final HivePosition position;
        public final double distance;
        public HiveDistance(HivePosition position, double distance) {
            this.position = position; this.distance = distance;
        }
    }

    private static final HivePosition BOTTOM_RED_HIVE = new HivePosition("Bottom red", 59.25, 59.58, 55.5);
    private static final HivePosition TOP_RED_HIVE = new HivePosition("Top red", 59.25, 83.42, 55.5);
    private static final HivePosition TOP_BLUE_HIVE = new HivePosition("Top blue", 84.25, 83.42, 55.5);
    private static final HivePosition BOTTOM_BLUE_HIVE = new HivePosition("Bottom blue", 84.25, 59.58, 55.5);

    public static HiveDistance nearest_hive(double robotX, double robotY, Units.Alliance alliance) {
        HivePosition hive = robotY > 72
                ? (alliance == Units.Alliance.RED ? TOP_RED_HIVE : TOP_BLUE_HIVE)
                : (alliance == Units.Alliance.RED ? BOTTOM_RED_HIVE : BOTTOM_BLUE_HIVE);
        return new HiveDistance(hive, Math.hypot(hive.x - robotX, hive.y - robotY));
    }

    public State get_state() { return state; }
    /** Request shooting; the loop gates transfer and writes flywheel power. */
    public void shoot() {
        intakeSubsystem.request_shot();
        state = State.SHOOT;
    }
    public void prepare_shot() { preparingShot = true; state = State.PREPARE; }

    public void stop_shooting() {
        preparingShot = false;
        if (intakeSubsystem != null) intakeSubsystem.stop_feed();
        state = intakeSubsystem != null && intakeSubsystem.is_scoring_requested()
                ? State.PREPARE : State.FLOAT;
    }

    public boolean toggle_bypass() {
        set_bypass_enabled(!bypassEnabled);
        return bypassEnabled;
    }

    public void set_bypass_enabled(boolean enabled) {
        bypassEnabled = enabled;
        if (enabled) targetRpm = fixedRpm;
    }

    public boolean is_bypass_enabled() { return bypassEnabled; }
    public void set_alliance(Units.Alliance alliance) { this.alliance = alliance; }
    public double get_target_rpm() { return targetRpm; }
    public double get_fixed_rpm() { return fixedRpm; }
    /** Cached sample from the current update, also used for readiness and telemetry. */
    public double get_current_rpm() { return currentRpm; }
    public void adjust_target_rpm(double delta) { set_target_rpm(fixedRpm + delta); }

    public void set_target_rpm(double rpm) {
        if (!SensorReadings.is_valid(rpm)) throw new IllegalArgumentException("RPM must be finite");
        fixedRpm = Math.max(0, rpm);
        if (bypassEnabled) targetRpm = fixedRpm;
    }

    public boolean ready_to_feed() {
        return ready_inputs_valid() && targetRpm > 0
                && Math.abs(currentRpm - targetRpm) <= SHOOT_SPEED_TOLERANCE_RPM;
    }

    public void update(double robotX, double robotY) {
        if (!bypassEnabled) {
            targetRpm = SensorReadings.is_valid(robotX) && SensorReadings.is_valid(robotY)
                    ? calculate_flywheel_velocity(nearest_hive(robotX, robotY, alliance).distance)
                    : Double.NaN;
        }
        double measuredTicks = shooterMotor.raw().getVelocity();
        currentRpm = measuredTicks * 60.0 / ENCODER_TICKS_PER_REV;
        boolean shootingRequested = intakeSubsystem.is_shooting_requested();
        if (shootingRequested) {
            state = State.SHOOT;
        } else {
            state = preparingShot || intakeSubsystem.is_scoring_requested() || intakeSubsystem.loaded()
                    ? State.PREPARE : State.FLOAT;
        }
        if (shootingRequested) {
            shooterMotor.set_power(ready_inputs_valid() && targetRpm > 0 ? 1 : 0);
        } else {
            set_flywheel_velocity(state == State.PREPARE ? targetRpm : FLOAT_RPM, measuredTicks);
        }
        intakeSubsystem.update_feed(ready_to_feed());
    }

    public void stop() {
        preparingShot = false;
        state = State.FLOAT;
        integralSum = 0;
        if (shooterMotor != null) shooterMotor.set_power(0);
        if (intakeSubsystem != null) intakeSubsystem.stop();
    }

    /** Original competition PIDF, kept separate from the tuner's hub velocity controller. */
    public void set_flywheel_velocity(double targetVelocity, double currentVelocityTicksPerSec) {
        if (!SensorReadings.is_valid(targetVelocity)
                || !SensorReadings.is_valid(currentVelocityTicksPerSec)) {
            integralSum = 0;
            lastError = 0;
            lastTime = System.nanoTime() / 1e9;
            shooterMotor.set_power(0);
            return;
        }
        double currentTime = System.nanoTime() / 1e9;
        double targetTicksPerSec = targetVelocity / 60.0 * ENCODER_TICKS_PER_REV;
        double error = targetTicksPerSec - currentVelocityTicksPerSec;
        double rpm = currentVelocityTicksPerSec * 60.0 / ENCODER_TICKS_PER_REV;
        double dt = currentTime - lastTime;
        if (dt <= 0) dt = 1e-3;
        double output;
        if (rpm < targetVelocity - PIDF_RANGE_RPM) {
            integralSum = 0;
            output = 1;
        } else if (rpm > targetVelocity) {
            integralSum = 0;
            output = 0;
        } else {
            integralSum += error * dt;
            double derivative = (error - lastError) / dt;
            output = K_P * error + K_I * integralSum + K_D * derivative + K_F * targetTicksPerSec;
        }
        lastError = error;
        lastTime = currentTime;
        shooterMotor.set_power(Math.max(-1, Math.min(1, output)));
    }

    private boolean ready_inputs_valid() {
        return SensorReadings.is_valid(currentRpm) && SensorReadings.is_valid(targetRpm);
    }
}
