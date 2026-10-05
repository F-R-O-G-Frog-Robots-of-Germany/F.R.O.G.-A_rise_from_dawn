package org.firstinspires.ftc.teamcode.subsystems;

import static org.firstinspires.ftc.teamcode.core.units.Units.Length.mm;

import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.units.Units;

public class intake {
    // Intake motor and controls
    public static CachedMotor intakeMotor;
    private enum IntakeMode {OFF, FORWARD, REVERSE}
    private static IntakeMode intakeMode = IntakeMode.OFF;
    int intakePow = 1;

    public void forward() {
        intakeMotor.setPower(intakePow);
        intakeMode = IntakeMode.FORWARD;
    }

    public void reverse() {
        intakeMotor.setPower(-intakePow);
        intakeMode = IntakeMode.REVERSE;
    }

    public void off() {
        intakeMotor.setPower(0);
        intakeMode = IntakeMode.OFF;
    }

    public void toggleForward() {
        if (intakeMode != IntakeMode.FORWARD) {
            forward();
        } else {
            off();
        }
    }

    public void toggleReverse() {
        if (intakeMode != IntakeMode.REVERSE) {
            reverse();
        } else {
            off();
        }
    }
    public void toggleForwardReverse(){
        if(intakeMode == IntakeMode.FORWARD){
            reverse();
        } else if (intakeMode == IntakeMode.REVERSE) {
            forward();
        }
        else{
            //-> intake is off, most likely scenario needs intake forward
            // not clean code
            forward();
        }
    }

    public double getIntakePower() {
        return intakeMotor.raw().getPower();
    }

    public IntakeMode getIntakeMode() {
        return intakeMode;
    }

    // Transfer motor and controls
    public static CachedMotor transferMotor;
    private enum TransferMode {OFF, FORWARD, REVERSE}
    private static TransferMode transferMode = TransferMode.OFF;
    int transferPow = 1;
    double transferStallCurrent;
    public void transferForward(){
        transferMotor.setPower(transferPow);
        transferMode=TransferMode.FORWARD;
    }
    public void transferReverse(){
        transferMotor.setPower(-transferPow);
        transferMode=TransferMode.REVERSE;
    }
    public void transferOff(){
        transferMotor.setPower(0);
        transferMode= TransferMode.OFF;
    }
    public void toggleTransferForward() {
        if (transferMode != TransferMode.FORWARD) {
            transferForward();
        } else {
            transferOff();
        }
    }
    public void toggleTransferReverse() {
        if (transferMode != TransferMode.REVERSE) {
            transferReverse();
        } else {
            transferOff();
        }
    }
    public void toggleTransferForwardReverse(){
        if(transferMode == TransferMode.FORWARD){
            transferReverse();
        } else if (transferMode == TransferMode.REVERSE) {
            transferForward();
        }
        else{
            //-> transfer is off, most likely scenario needs transfer forward
            //not clean code
            transferForward();
        }
    }
    public static double getTransferPower() {
        return transferMotor.raw().getPower();
    }

    public TransferMode getTransferMode() {
        return transferMode;
    }

    // Color sensors and sensor readings
    private static final long SENSOR_READ_INTERVAL_NANOS = 300_000_000L;
    private static ColorRangeSensor colorSensorLow;
    private static ColorRangeSensor colorSensorHigh;
    private static Units.Length cachedUpperDistance = mm(0);
    private static Units.Length cachedLowerDistance = mm(0);
    private static long lastSensorReadNanos;
    private static boolean sensorReadingsInitialized;
    private static final Units.Length emptyUpperDistance = mm(0); // tune pls, placeholder value
    private static final Units.Length emptyLowerDistance = mm(0); // tune pls, placeholder value

    private static synchronized void refreshSensorReadings() {
        long now = System.nanoTime();
        if (!sensorReadingsInitialized || now - lastSensorReadNanos >= SENSOR_READ_INTERVAL_NANOS) {
            double lowerDistanceMm = colorSensorLow.getDistance(DistanceUnit.MM);
            double upperDistanceMm = colorSensorHigh.getDistance(DistanceUnit.MM);
            cachedLowerDistance = mm(lowerDistanceMm);
            cachedUpperDistance = mm(upperDistanceMm);
            lastSensorReadNanos = now;
            sensorReadingsInitialized = true;
        }
    }

    public static Units.Length getUpperDistance() {
        refreshSensorReadings();
        return cachedUpperDistance;
    }

    public static Units.Length getLowerDistance() {
        refreshSensorReadings();
        return cachedLowerDistance;
    }

    public static boolean loaded() {
        boolean loaded = false;
        if (getLowerDistance().mm() < emptyLowerDistance.mm()
                && getUpperDistance().mm() < emptyUpperDistance.mm()) {
            loaded = true;
        }
        return loaded;
    }

    public static String getSensorTelemetry() {
        return getLowerDistance() + "\t" + getUpperDistance() + "\t" + loaded();
    }

    // Combined subsystem operations
    private enum ScoringMode {COLLECT, SHOOT}
    private static ScoringMode shootMode = ScoringMode.COLLECT;

    public void init(HardwareMap hardwareMap) { // tune directions of motors
        DcMotorEx rawIntakeMotor = hardwareMap.get(DcMotorEx.class, "intakeMotor");
        rawIntakeMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        intakeMotor = new CachedMotor(rawIntakeMotor, 0.01);

        DcMotorEx rawTransferMotor = hardwareMap.get(DcMotorEx.class, "transferMotor");
        rawTransferMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        rawTransferMotor.setDirection(DcMotorSimple.Direction.REVERSE);
        rawTransferMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        transferMotor = new CachedMotor(rawTransferMotor, 0.01);

        colorSensorLow = hardwareMap.get(ColorRangeSensor.class, "colorSensorLow");
        colorSensorHigh = hardwareMap.get(ColorRangeSensor.class, "colorSensorHigh");
        sensorReadingsInitialized = false;
    }

    public void feed() {
        forward();
        transferForward();
    }

    public void stopFeed() {
        transferOff();
    }

    public void stop() {
        off();
        transferOff();
    }

    public void setScoringMode(ScoringMode mode) {
        shootMode = mode;
        forward();

        if (mode == ScoringMode.SHOOT) {
            transferMotor.setPower(transferPow);
            transferMode = TransferMode.FORWARD;
        } else { // COLLECT
            transferMotor.setPower(0);
            transferMode = TransferMode.OFF;
        }
    }
    public void toggleShootMode() {
        if (shootMode == ScoringMode.COLLECT) {
            setScoringMode(ScoringMode.SHOOT);
        } else {//Scoring Mode is SHOOT
            setScoringMode(ScoringMode.COLLECT);
        }
    }
}
