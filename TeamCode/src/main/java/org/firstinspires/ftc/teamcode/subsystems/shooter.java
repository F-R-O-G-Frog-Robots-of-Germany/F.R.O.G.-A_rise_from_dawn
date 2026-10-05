package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;
import org.firstinspires.ftc.teamcode.core.units.Units;

public class shooter {

    // Shooter motor configuration and initialization
    private static final double GEAR_RATIO = 1.0;
    public static final double TICKS_PER_REV = 2048.0;

    public static CachedMotor shooterMotor;
    private static CachedServo servoHood;
    private Units.Alliance alliance;

    public void init(HardwareMap hardwareMap, intake intakeSubsystem, Units.Alliance alliance) {
        this.intakeSubsystem = intakeSubsystem;
        this.alliance = alliance;
        DcMotorEx rawShooterMotor = hardwareMap.get(DcMotorEx.class, "shooter");
        rawShooterMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        // 0.002 tolerance (finer than the default 0.01): shooter power comes from a PIDF loop, so small corrections must reach the motor
        shooterMotor = new CachedMotor(rawShooterMotor, 0.002);
        servoHood = new CachedServo(hardwareMap.get(Servo.class, "servoHood"), 0.005);
        integralSum = 0.0;
        lastError = 0.0;
        lastTime = System.nanoTime() / 1e9;
        bypassEnabled = false;
        state = State.FLOAT;
    }

    // Distance calculations
    public double calculateFlywheelVelocity(double distanceToNearestHive) { // Placeholder; tune as a quadratic function.
        double speed = distanceToNearestHive;
        return speed;
    }

    private static double distance(double robotX, double robotY, HivePosition hive) {
        double xDelta = hive.x - robotX;
        double yDelta = hive.y - robotY;
        return Math.sqrt(xDelta * xDelta + yDelta * yDelta);
    }

    // HIVE positions and lookup calculations
    private static final HivePosition BOTTOM_RED_HIVE = new HivePosition("Bottom red", 59.25, 59.58, 55.5);
    private static final HivePosition TOP_RED_HIVE = new HivePosition("Top red", 59.25, 83.42, 55.5);
    private static final HivePosition TOP_BLUE_HIVE = new HivePosition("Top blue", 84.25, 83.42, 55.5);
    private static final HivePosition BOTTOM_BLUE_HIVE = new HivePosition("Bottom blue", 84.25, 59.58, 55.5);

    public static class HivePosition {
        public final String name;
        public final double x;
        public final double y;
        public final double z;

        public HivePosition(String name, double x, double y, double z) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class HiveDistance {
        public final HivePosition position;
        public final double distance;

        public HiveDistance(HivePosition position, double distance) {
            this.position = position;
            this.distance = distance;
        }
    }

    public static HiveDistance nearestHive(double robotX, double robotY, Units.Alliance alliance) {
        HivePosition targetHive;
        if (robotY > 72.0) {
            if (alliance == Units.Alliance.RED) {
                targetHive = TOP_RED_HIVE;
            } else {
                targetHive = TOP_BLUE_HIVE;
            }
        } else {
            if (alliance == Units.Alliance.RED) {
                targetHive = BOTTOM_RED_HIVE;
            } else {
                targetHive = BOTTOM_BLUE_HIVE;
            }
        }
        return new HiveDistance(targetHive, distance(robotX, robotY, targetHive));
    }

    // PIDF calculation
    private static final double kP = 0.0002;
    private static final double kI = 0.0000001;
    private static final double kD = 0.00001;
    private static final double kF = 0.00017;
    private static final double PIDF_RANGE_RPM = 150.0;

    private static double integralSum = 0.0;
    private static double lastError = 0.0;
    private static double lastTime = 0.0;

    public void setFlywheelVelocity(double targetVelocity, double currentVelocityTicksPerSec) {
        double currentTime = System.nanoTime() / 1e9;

        double targetTicksPerSec = (targetVelocity / 60.0) * TICKS_PER_REV * GEAR_RATIO;
        double error = targetTicksPerSec - currentVelocityTicksPerSec;
        double currentRpm = currentVelocityTicksPerSec * 60.0 / (TICKS_PER_REV * GEAR_RATIO);

        double dt = currentTime - lastTime;
        if (dt <= 0) dt = 1e-3;

        double output;
        if (currentRpm < targetVelocity - PIDF_RANGE_RPM) {
            integralSum = 0.0;
            output = 1.0;
        } else if (currentRpm > targetVelocity) {
            integralSum = 0.0;
            output = 0.0;
        } else {
            integralSum += error * dt;
            double derivative = (error - lastError) / dt;
            output = (kP * error) + (kI * integralSum) + (kD * derivative) + (kF * targetTicksPerSec);
        }

        lastError = error;
        lastTime = currentTime;

        output = Math.max(-1.0, Math.min(1.0, output));
        shooterMotor.setPower(output);
    }

    // Shooter state and controls
    public static final int STANDARD_RPM = 3000; // RPM when shooting from the field side panel.
    private static final double FLOAT_RPM = 3500.0;
    private static final double SHOOT_RPM_OVERSHOOT_WHILE_SHOOTING = 30.0;

    public enum State {
        SHOOT,
        FLOAT,
        PREPARE
    }

    private boolean bypassEnabled;
    private double targetRpm;
    private State state = State.FLOAT;
    private intake intakeSubsystem;

    public State getState() {
        return state;
    }

    private void checkLoaded() {
        state = intake.loaded() ? State.PREPARE : State.FLOAT;
    }

    public void shoot() {
        state = State.SHOOT;
        intakeSubsystem.feed();
        applyShootPower();
    }

    public void stopShooting() {
        if (state == State.SHOOT) {
            checkLoaded();
        }
        intakeSubsystem.stopFeed();
    }

    public boolean toggleBypass() {
        setBypassEnabled(!bypassEnabled);
        return bypassEnabled;
    }

    public void setBypassEnabled(boolean enabled) {
        bypassEnabled = enabled;
        if (enabled) {
            targetRpm = STANDARD_RPM;
        }
    }

    public void adjustTargetRpm(double delta) {
        targetRpm = Math.max(0.0, targetRpm + delta);
    }

    public boolean isBypassEnabled() {
        return bypassEnabled;
    }

    public double getTargetRpm() {
        return targetRpm;
    }

    public double getCurrentRpm() {
        return shooterMotor.raw().getVelocity() * 60.0 / (TICKS_PER_REV * GEAR_RATIO);
    }

    public void setTargetRpm(double targetRpm) {
        this.targetRpm = Math.max(0.0, targetRpm);
    }

    public void update(double robotX, double robotY) {
        HiveDistance nearestHive = nearestHive(robotX, robotY, alliance);
        if (!bypassEnabled) {
            targetRpm = calculateFlywheelVelocity(nearestHive.distance);
        }

        if (state != State.SHOOT) {
            checkLoaded();
        }

        switch (state) {
            case SHOOT:
                intakeSubsystem.feed();
                applyShootPower();
                break;
            case PREPARE:
                setFlywheelVelocity(targetRpm, shooterMotor.raw().getVelocity());
                break;
            case FLOAT:
                setFlywheelVelocity(FLOAT_RPM, shooterMotor.raw().getVelocity());
                break;
        }
    }

    private void applyShootPower() {
        double currentRpm = shooterMotor.raw().getVelocity() * 60.0 / (TICKS_PER_REV * GEAR_RATIO);
        shooterMotor.setPower(currentRpm < targetRpm + SHOOT_RPM_OVERSHOOT_WHILE_SHOOTING ? 1.0 : 0.0);
    }

    public void stop() {
        if (shooterMotor != null) {
            shooterMotor.setPower(0.0);
        }
        if (intakeSubsystem != null) {
            intakeSubsystem.stopFeed();
        }
    }
}
