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
    // FILLER fit: constant 3000 RPM. Replace with your Desmos regression coefficients.
    // RPM = A * distance[in]^2 + B * distance[in] + C (horizontal robot-to-hive distance).
    public static final double DISTANCE_A = 0.0;
    public static final double DISTANCE_B = 0.0;
    public static final double DISTANCE_C = STANDARD_RPM;
    public static final double MAX_RPM = 6000.0; // Set to the measured flywheel limit.
    // FILLER fit: constant 45 degrees. Desmos: hood[deg] = A * distance[in]^2 + B * distance[in] + C.
    // Angle is the measured hood angle in degrees, using the same convention as your shot data.
    // Fit BOTH curves to the SAME shot rows: distance, lowest reliable RPM, matching hood angle.
    // Together they describe one paired shot solution; regression itself does not minimize RPM.
    public static final double HOOD_DISTANCE_A = 0.0;
    public static final double HOOD_DISTANCE_B = 0.0;
    public static final double HOOD_DISTANCE_C = 45.0;
    public static final double DEFAULT_HOOD_ANGLE_DEG = 45.0;
    // FILLER VALUES: replace with measured safe endpoints once the hood is built.
    // These values are active immediately. Reversed servo travel is supported.
    public static final double HOOD_MIN_ANGLE_DEG = 0.0;
    public static final double HOOD_MAX_ANGLE_DEG = 90.0;
    public static final double HOOD_SERVO_AT_MIN_ANGLE = 0.0;
    public static final double HOOD_SERVO_AT_MAX_ANGLE = 1.0;
    public static final double HOOD_POSITION_EPSILON = 0.005;
    // Provisional full-travel settling time; replace with a measured worst-case value.
    public static final long HOOD_SETTLE_TIME_MS = 250;
    private static final boolean HOOD_CONFIG_VALID = SensorReadings.are_valid(HOOD_MIN_ANGLE_DEG,
            HOOD_MAX_ANGLE_DEG, HOOD_SERVO_AT_MIN_ANGLE, HOOD_SERVO_AT_MAX_ANGLE)
            && HOOD_MAX_ANGLE_DEG > HOOD_MIN_ANGLE_DEG
            && HOOD_SERVO_AT_MIN_ANGLE >= 0 && HOOD_SERVO_AT_MIN_ANGLE <= 1
            && HOOD_SERVO_AT_MAX_ANGLE >= 0 && HOOD_SERVO_AT_MAX_ANGLE <= 1
            && HOOD_SERVO_AT_MIN_ANGLE != HOOD_SERVO_AT_MAX_ANGLE;
    private static final boolean DISTANCE_FIT_VALID = SensorReadings.are_valid(DISTANCE_A, DISTANCE_B,
            DISTANCE_C, HOOD_DISTANCE_A, HOOD_DISTANCE_B, HOOD_DISTANCE_C);

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
    private double currentRpm;
    private boolean velocityValid;
    private boolean motorControlValid;
    private boolean targetAvailable;
    private double fixedHoodAngle = DEFAULT_HOOD_ANGLE_DEG;
    private double targetHoodAngle = DEFAULT_HOOD_ANGLE_DEG;
    private double commandedHoodPosition;
    private double mappedHoodAngle;
    private boolean hoodMapped;
    private boolean hoodCommanded;
    private boolean hoodTargetValid;
    private long lastHoodCommandNanos;
    private double targetDistance;
    private HiveDistance targetHive = INVALID_BLUE_HIVE;
    private double hiveRobotX = Double.NaN, hiveRobotY = Double.NaN;
    private Units.Alliance hiveAlliance;
    private boolean hivePoseValid;
    private boolean preparingShot;
    // Latched only after the cached start gates pass; target RPM/hood remain fixed until an explicit stop.
    private boolean shootingActive;
    private State state = State.FLOAT;

    public enum State { SHOOT, FLOAT, PREPARE }

    public void init(HardwareMap hardwareMap, Intake intakeSubsystem, Units.Alliance alliance) {
        this.intakeSubsystem = intakeSubsystem;
        this.alliance = alliance;
        shooterMotor = new CachedMotor(hardwareMap.get(DcMotorEx.class, "shooter"), 0.002);
        shooterMotor.set_power(0);
        shooterMotor.raw().setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        shooterMotor.invalidate();
        servoHood = new CachedServo(hardwareMap.get(Servo.class, "servoHood"), HOOD_POSITION_EPSILON);
        fixedHoodAngle = DEFAULT_HOOD_ANGLE_DEG;
        targetHoodAngle = fixedHoodAngle;
        hoodMapped = false;
        hoodCommanded = false;
        targetAvailable = false;
        targetDistance = 0;
        targetHive = INVALID_BLUE_HIVE;
        hiveRobotX = Double.NaN;
        hiveRobotY = Double.NaN;
        hiveAlliance = null;
        hivePoseValid = false;
        update_hood();
        integralSum = 0;
        lastError = 0;
        lastTime = System.nanoTime() / 1e9;
        bypassEnabled = DEFAULT_FIXED_RPM;
        fixedRpm = STANDARD_RPM;
        targetRpm = fixedRpm;
        currentRpm = 0;
        velocityValid = false;
        motorControlValid = false;
        preparingShot = false;
        shootingActive = false;
        state = State.FLOAT;
    }

    public double calculate_flywheel_velocity(double distanceToNearestHive) {
        return calculate_rpm(distanceToNearestHive, DISTANCE_A, DISTANCE_B, DISTANCE_C);
    }

    public double calculate_hood_angle(double distanceInches) {
        return calculate_hood_angle(distanceInches, HOOD_DISTANCE_A, HOOD_DISTANCE_B, HOOD_DISTANCE_C);
    }

    /** A matched RPM/hood pair fitted from minimum-RPM successful shots at each distance. */
    public static final class ShotSolution {
        public final double rpm;
        public final double hoodAngleDegrees;
        public final boolean valid;

        private ShotSolution(double rpm, double hoodAngleDegrees, boolean valid) {
            this.rpm = rpm;
            this.hoodAngleDegrees = hoodAngleDegrees;
            this.valid = valid;
        }
    }
    private static final ShotSolution INVALID_SHOT = new ShotSolution(Double.NaN, Double.NaN, false);

    public static ShotSolution calculate_shot_solution(double distanceInches) {
        return SensorReadings.is_valid(distanceInches) && distanceInches >= 0
                ? calculate_shot_solution_validated(distanceInches) : INVALID_SHOT;
    }

    private static ShotSolution calculate_shot_solution_validated(double distanceInches) {
        return DISTANCE_FIT_VALID ? fit_shot(distanceInches, DISTANCE_A, DISTANCE_B, DISTANCE_C,
                HOOD_DISTANCE_A, HOOD_DISTANCE_B, HOOD_DISTANCE_C) : INVALID_SHOT;
    }

    /** Custom coefficients let the tuner test the same paired model as competition code. */
    public static ShotSolution calculate_shot_solution(double distanceInches,
            double rpmA, double rpmB, double rpmC, double hoodA, double hoodB, double hoodC) {
        return SensorReadings.are_valid(distanceInches, rpmA, rpmB, rpmC, hoodA, hoodB, hoodC)
                && distanceInches >= 0
                ? fit_shot(distanceInches, rpmA, rpmB, rpmC, hoodA, hoodB, hoodC) : INVALID_SHOT;
    }

    private static ShotSolution fit_shot(double distance, double rpmA, double rpmB, double rpmC,
            double hoodA, double hoodB, double hoodC) {
        double rpm = (rpmA * distance + rpmB) * distance + rpmC;
        double hood = (hoodA * distance + hoodB) * distance + hoodC;
        return HOOD_CONFIG_VALID && SensorReadings.are_valid(rpm, hood)
                ? new ShotSolution(Math.max(0, Math.min(MAX_RPM, rpm)),
                        Math.max(HOOD_MIN_ANGLE_DEG, Math.min(HOOD_MAX_ANGLE_DEG, hood)), true)
                : INVALID_SHOT;
    }

    public static double calculate_rpm(double distance, double a, double b, double c) {
        return calculate_curve(distance, a, b, c, 0, MAX_RPM);
    }

    public static double calculate_hood_angle(double distance, double a, double b, double c) {
        return calculate_curve(distance, a, b, c, HOOD_MIN_ANGLE_DEG, HOOD_MAX_ANGLE_DEG);
    }

    private static double calculate_curve(double distance, double a, double b, double c, double min, double max) {
        return SensorReadings.are_valid(distance, a, b, c) && distance >= 0
                ? SensorReadings.clamp((a * distance + b) * distance + c, min, max) : Double.NaN;
    }

    public static double hood_angle_to_servo_position(double angleDegrees) {
        return HOOD_CONFIG_VALID && SensorReadings.is_valid(angleDegrees)
                ? hood_position_validated(angleDegrees) : Double.NaN;
    }

    private static double hood_position_validated(double angleDegrees) {
        double angle = Math.max(HOOD_MIN_ANGLE_DEG, Math.min(HOOD_MAX_ANGLE_DEG, angleDegrees));
        // Assumes a linear angle/servo relationship; measure the linkage before relying on it.
        double fraction = (angle - HOOD_MIN_ANGLE_DEG) / (HOOD_MAX_ANGLE_DEG - HOOD_MIN_ANGLE_DEG);
        return HOOD_SERVO_AT_MIN_ANGLE + fraction * (HOOD_SERVO_AT_MAX_ANGLE - HOOD_SERVO_AT_MIN_ANGLE);
    }

    private void update_hood() {
        if (servoHood == null || (hoodMapped && mappedHoodAngle == targetHoodAngle)) return;
        mappedHoodAngle = targetHoodAngle;
        hoodMapped = true;
        double position = hood_position_validated(targetHoodAngle);
        hoodTargetValid = HOOD_CONFIG_VALID;
        if (hoodTargetValid && (!hoodCommanded
                || Math.abs(position - commandedHoodPosition) >= HOOD_POSITION_EPSILON)) {
            hoodTargetValid = servoHood.set_position(position);
            if (hoodTargetValid) {
                commandedHoodPosition = position;
                hoodCommanded = true;
                lastHoodCommandNanos = System.nanoTime();
            }
        }
    }

    /** Valid command plus a settling delay; there is no measured hood-angle feedback. */
    public boolean hood_ready() {
        return targetAvailable && hoodTargetValid && hoodCommanded
                && System.nanoTime() - lastHoodCommandNanos >= HOOD_SETTLE_TIME_MS * 1_000_000L;
    }

    public static final class HivePosition {
        public final String name;
        public final double x, y, z;
        public final boolean valid;
        public HivePosition(String name, double x, double y, double z) {
            this.name = name; this.x = x; this.y = y; this.z = z;
            valid = SensorReadings.are_valid(x, y, z);
        }
    }

    public static final class HiveDistance {
        public final HivePosition position;
        public final double distance;
        public final boolean valid;
        public HiveDistance(HivePosition position, double distance) {
            this.position = position; this.distance = distance;
            valid = position != null && position.valid && SensorReadings.is_valid(distance) && distance > 0;
        }
    }

    private static final HivePosition BOTTOM_RED_HIVE = new HivePosition("Bottom red", 59.25, 59.58, 55.5);
    private static final HivePosition TOP_RED_HIVE = new HivePosition("Top red", 59.25, 83.42, 55.5);
    private static final HivePosition TOP_BLUE_HIVE = new HivePosition("Top blue", 84.25, 83.42, 55.5);
    private static final HivePosition BOTTOM_BLUE_HIVE = new HivePosition("Bottom blue", 84.25, 59.58, 55.5);

    private static final HiveDistance INVALID_RED_HIVE = new HiveDistance(BOTTOM_RED_HIVE, Double.NaN);
    private static final HiveDistance INVALID_BLUE_HIVE = new HiveDistance(BOTTOM_BLUE_HIVE, Double.NaN);

    /** Select the alliance hive by field half; y = 72 belongs to the lower half. */
    public static HiveDistance nearest_hive(double robotX, double robotY, Units.Alliance alliance) {
        return nearest_hive(robotX, robotY, alliance, SensorReadings.are_valid(robotX, robotY));
    }

    /** Consume pose validity from its sampling boundary without checking coordinates again. */
    public static HiveDistance nearest_hive(double robotX, double robotY, Units.Alliance alliance, boolean poseValid) {
        if (!poseValid || alliance == null) {
            return alliance == Units.Alliance.RED ? INVALID_RED_HIVE : INVALID_BLUE_HIVE;
        }
        HivePosition hive = robotY > 72.0
                ? (alliance == Units.Alliance.RED ? TOP_RED_HIVE : TOP_BLUE_HIVE)
                : (alliance == Units.Alliance.RED ? BOTTOM_RED_HIVE : BOTTOM_BLUE_HIVE);
        return new HiveDistance(hive, Math.hypot(hive.x - robotX, hive.y - robotY));
    }

    public State get_state() { return state; }
    /** Check cached readiness once to begin a shot; the update loop writes mechanism commands. */
    public void shoot() {
        intakeSubsystem.request_shot();
        state = State.SHOOT;
        if (ready_to_feed()) shootingActive = true;
    }

    public void prepare_shot() {
        preparingShot = true;
        if (!intakeSubsystem.is_shooting_requested()) state = State.PREPARE;
    }

    public boolean shot_in_progress() { return shootingActive && intakeSubsystem.is_shooting_requested(); }

    /** Driver-facing status uses cached gates; it never reads an encoder or distance sensor again. */
    public String feed_status() {
        if (!intakeSubsystem.is_shooting_requested()) return "IDLE";
        if (shootingActive) return "FEEDING";
        if (!targetAvailable) return "WAITING: target";
        if (!velocityValid) return "WAITING: encoder";
        if (!motorControlValid) return "WAITING: controller";
        if (!hood_ready()) return "WAITING: hood";
        if (targetRpm <= 0) return "WAITING: zero RPM target";
        return "WAITING: RPM";
    }

    public void stop_shooting() {
        shootingActive = false;
        preparingShot = false;
        motorControlValid = false; // A later shot must earn readiness again after this explicit stop.
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
        if (shootingActive) return; // Mode edits apply after the current shot, without moving its hood.
        targetAvailable = enabled;
        if (enabled) {
            targetRpm = fixedRpm;
            targetHoodAngle = fixedHoodAngle;
            update_hood();
        }
    }

    public boolean is_bypass_enabled() { return bypassEnabled; }
    public void set_alliance(Units.Alliance alliance) {
        this.alliance = alliance;
        update_target_hive(hiveRobotX, hiveRobotY, hivePoseValid);
    }
    public double get_target_rpm() { return targetRpm; }
    public double get_fixed_rpm() { return fixedRpm; }
    public double get_target_distance() { return targetDistance; }
    public boolean distance_valid() { return targetHive.valid; }
    public HiveDistance get_target_hive() { return targetHive; }
    public boolean target_available() { return targetAvailable; }
    public double get_target_hood_angle() { return targetHoodAngle; }
    public double get_fixed_hood_angle() { return fixedHoodAngle; }
    public double get_commanded_hood_position() { return commandedHoodPosition; }
    public void adjust_hood_angle(double delta) { set_hood_angle(fixedHoodAngle + delta); }
    public void set_hood_angle(double angleDegrees) {
        if (!SensorReadings.is_valid(angleDegrees)) throw new IllegalArgumentException("Hood angle must be finite");
        fixedHoodAngle = Math.max(HOOD_MIN_ANGLE_DEG, Math.min(HOOD_MAX_ANGLE_DEG, angleDegrees));
        if (bypassEnabled && !shootingActive) {
            targetHoodAngle = fixedHoodAngle;
            update_hood();
        }
    }
    /** Cached sample from the current update, also used for readiness and telemetry. */
    public double get_current_rpm() { return currentRpm; }
    public boolean velocity_valid() { return velocityValid; }
    public void adjust_target_rpm(double delta) { set_target_rpm(fixedRpm + delta); }

    public void set_target_rpm(double rpm) {
        if (!SensorReadings.is_valid(rpm)) throw new IllegalArgumentException("RPM must be finite");
        fixedRpm = Math.max(0, Math.min(MAX_RPM, rpm));
        if (bypassEnabled && !shootingActive) targetRpm = fixedRpm;
    }

    public boolean ready_to_feed() {
        return motorControlValid && velocityValid && hood_ready() && targetRpm > 0
                && Math.abs(currentRpm - targetRpm) <= SHOOT_SPEED_TOLERANCE_RPM;
    }

    public void update(double robotX, double robotY) {
        update(robotX, robotY, SensorReadings.are_valid(robotX, robotY));
    }

    /** Cache the selected hive and its validity for aiming, shot calculations and telemetry. */
    public void update_target_hive(double robotX, double robotY, boolean poseValid) {
        double x = poseValid ? robotX : 0;
        double y = poseValid ? robotY : 0;
        if (x == hiveRobotX && y == hiveRobotY && alliance == hiveAlliance && poseValid == hivePoseValid) return;
        hiveRobotX = x;
        hiveRobotY = y;
        hiveAlliance = alliance;
        hivePoseValid = poseValid;
        targetHive = nearest_hive(x, y, alliance, poseValid);
        if (targetHive.valid) targetDistance = targetHive.distance;
    }

    /** Reuse the pose validity already checked at the subsystem's input boundary. */
    public void update(double robotX, double robotY, boolean poseValid) {
        update_target_hive(robotX, robotY, poseValid);
        update();
    }

    public void update() {
        boolean shootingRequested = intakeSubsystem.is_shooting_requested();
        if (!shootingRequested) shootingActive = false;
        // Collection must see full detection even while another reason already requests preparation.
        boolean loaded = !shootingRequested && (intakeSubsystem.loaded() || intakeSubsystem.full_load_held());
        if (!shootingActive) {
            targetAvailable = bypassEnabled;
            if (bypassEnabled) {
                targetRpm = fixedRpm;
                targetHoodAngle = fixedHoodAngle;
            } else if (targetHive.valid) {
                ShotSolution solution = calculate_shot_solution_validated(targetDistance);
                targetAvailable = solution.valid;
                if (targetAvailable) {
                    targetRpm = solution.rpm;
                    targetHoodAngle = solution.hoodAngleDegrees;
                }
            }
        }
        if (targetAvailable) update_hood();
        SensorReadings.Sample velocity = shooterMotor.sample_velocity();
        velocityValid = velocity.is_valid();
        if (velocityValid) currentRpm = velocity.get_value() * (60.0 / ENCODER_TICKS_PER_REV);
        if (shootingRequested) {
            state = State.SHOOT;
        } else {
            state = preparingShot || intakeSubsystem.is_scoring_requested() || loaded
                    ? State.PREPARE : State.FLOAT;
        }
        // Keep regulating the fitted RPM while feeding, instead of forcing full power.
        motorControlValid = regulate_flywheel(state == State.FLOAT ? FLOAT_RPM : targetRpm,
                velocity.get_value(), velocityValid && (state == State.FLOAT || targetAvailable));
        if (shootingRequested && !shootingActive && ready_to_feed()) shootingActive = true;
        intakeSubsystem.update_feed(shootingActive, loaded);
    }

    public void stop() {
        shootingActive = false;
        preparingShot = false;
        state = State.FLOAT;
        reset_controller();
        motorControlValid = false;
        if (shooterMotor != null) shooterMotor.set_power(0);
        if (intakeSubsystem != null) intakeSubsystem.stop();
    }

    /** Original competition PIDF, kept separate from the tuner's hub velocity controller. */
    public void set_flywheel_velocity(double targetVelocity, double currentVelocityTicksPerSec) {
        motorControlValid = regulate_flywheel(targetVelocity, currentVelocityTicksPerSec,
                SensorReadings.are_valid(targetVelocity, currentVelocityTicksPerSec)
                        && targetVelocity >= 0 && targetVelocity <= MAX_RPM);
    }

    private boolean regulate_flywheel(double targetVelocity, double currentVelocityTicksPerSec, boolean valid) {
        if (!valid) {
            reset_controller();
            if (!shootingActive) shooterMotor.set_power(0);
            return false;
        }
        double currentTime = System.nanoTime() / 1e9;
        double targetTicksPerSec = targetVelocity / 60.0 * ENCODER_TICKS_PER_REV;
        double error = targetTicksPerSec - currentVelocityTicksPerSec;
        double rpm = currentVelocityTicksPerSec * (60.0 / ENCODER_TICKS_PER_REV);
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
        // A faulty sample/output cannot interrupt an active shot; retain the previous finite command.
        boolean accepted = shootingActive ? shooterMotor.set_power_if_valid(output) : shooterMotor.set_power(output);
        if (accepted) {
            lastError = error;
            lastTime = currentTime;
        } else {
            reset_controller();
        }
        return accepted;
    }

    private void reset_controller() {
        integralSum = 0;
        lastError = 0;
        lastTime = System.nanoTime() / 1e9;
    }
}
