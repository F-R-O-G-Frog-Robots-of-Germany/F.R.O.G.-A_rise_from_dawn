package org.firstinspires.ftc.teamcode.subsystems;

import static org.firstinspires.ftc.teamcode.core.units.Units.Length.mm;

import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;
import org.firstinspires.ftc.teamcode.core.units.Units;

public class Intake {
    // Intake motor and controls
    private CachedMotor intakeMotor;
    private enum IntakeMode {OFF, FORWARD, REVERSE}
    private IntakeMode intakeMode = IntakeMode.OFF;
    int intakePow = 1;

    public void forward() {
        intakeMotor.set_power(intakePow);
        intakeMode = IntakeMode.FORWARD;
    }

    public void reverse() {
        intakeMotor.set_power(-intakePow);
        intakeMode = IntakeMode.REVERSE;
    }

    public void off() {
        if (intakeMotor != null) intakeMotor.set_power(0);
        intakeMode = IntakeMode.OFF;
    }

    public void toggle_forward() {
        if (intakeMode != IntakeMode.FORWARD) {
            forward();
        } else {
            off();
        }
    }

    public void toggle_reverse() {
        if (intakeMode != IntakeMode.REVERSE) {
            reverse();
        } else {
            off();
        }
    }
    public void toggle_forward_reverse(){
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

    public double get_intake_power() {
        return intakeMotor.raw().getPower();
    }

    public IntakeMode get_intake_mode() {
        return intakeMode;
    }

    // Transfer motor and controls
    private CachedMotor transferMotor;
    private enum TransferMode {OFF, FORWARD, REVERSE}
    private TransferMode transferMode = TransferMode.OFF;
    int transferPow = 1;
    double transferStallCurrent;
    public void transfer_forward(){
        transferMotor.set_power(transferPow);
        transferMode=TransferMode.FORWARD;
    }
    public void transfer_reverse(){
        transferMotor.set_power(-transferPow);
        transferMode=TransferMode.REVERSE;
    }
    public void transfer_off(){
        if (transferMotor != null) transferMotor.set_power(0);
        transferMode= TransferMode.OFF;
    }
    public void toggle_transfer_forward() {
        if (transferMode != TransferMode.FORWARD) {
            transfer_forward();
        } else {
            transfer_off();
        }
    }
    public void toggle_transfer_reverse() {
        if (transferMode != TransferMode.REVERSE) {
            transfer_reverse();
        } else {
            transfer_off();
        }
    }
    public void toggle_transfer_forward_reverse(){
        if(transferMode == TransferMode.FORWARD){
            transfer_reverse();
        } else if (transferMode == TransferMode.REVERSE) {
            transfer_forward();
        }
        else{
            //-> transfer is off, most likely scenario needs transfer forward
            //not clean code
            transfer_forward();
        }
    }
    public double get_transfer_power() {
        return transferMotor.raw().getPower();
    }

    public TransferMode get_transfer_mode() {
        return transferMode;
    }

    // Color sensors and sensor readings
    private static final long SENSOR_READ_INTERVAL_NANOS = 300_000_000L;
    private ColorRangeSensor colorSensorLow;
    private ColorRangeSensor colorSensorHigh;
    private double cachedUpperDistanceMm;
    private double cachedLowerDistanceMm;
    private long lastSensorReadNanos;
    private boolean sensorReadingsInitialized;
    private static final Units.Length EMPTY_UPPER_DISTANCE = mm(0); // tune pls, placeholder value
    private static final Units.Length EMPTY_LOWER_DISTANCE = mm(0); // tune pls, placeholder value

    private synchronized void refresh_sensor_readings() {
        long now = System.nanoTime();
        if (!sensorReadingsInitialized || now - lastSensorReadNanos >= SENSOR_READ_INTERVAL_NANOS) {
            double lowerDistanceMm = colorSensorLow.getDistance(DistanceUnit.MM);
            double upperDistanceMm = colorSensorHigh.getDistance(DistanceUnit.MM);
            cachedLowerDistanceMm = lowerDistanceMm;
            cachedUpperDistanceMm = upperDistanceMm;
            lastSensorReadNanos = now;
            sensorReadingsInitialized = true;
        }
    }

    public synchronized Units.Length get_upper_distance() {
        refresh_sensor_readings();
        return mm(cachedUpperDistanceMm);
    }

    public synchronized Units.Length get_lower_distance() {
        refresh_sensor_readings();
        return mm(cachedLowerDistanceMm);
    }

    public synchronized boolean loaded() {
        refresh_sensor_readings();
        return SensorReadings.is_valid(cachedLowerDistanceMm)
                && SensorReadings.is_valid(cachedUpperDistanceMm)
                && cachedLowerDistanceMm < EMPTY_LOWER_DISTANCE.mm()
                && cachedUpperDistanceMm < EMPTY_UPPER_DISTANCE.mm();
    }

    public synchronized String get_sensor_telemetry() {
        return get_lower_distance() + "\t" + get_upper_distance() + "\t" + loaded();
    }

    // Combined subsystem operations
    private enum ScoringMode {COLLECT, SHOOT}
    private ScoringMode scoringMode = ScoringMode.COLLECT;
    private boolean shotRequested;

    public void init(HardwareMap hardwareMap) { // tune directions of motors
        intakeMode = IntakeMode.OFF;
        transferMode = TransferMode.OFF;
        scoringMode = ScoringMode.COLLECT;
        shotRequested = false;
        sensorReadingsInitialized = false;
        DcMotorEx rawIntakeMotor = hardwareMap.get(DcMotorEx.class, "intakeMotor");
        intakeMotor = new CachedMotor(rawIntakeMotor, 0.01);
        intakeMotor.set_power(0);
        rawIntakeMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        intakeMotor.invalidate();

        DcMotorEx rawTransferMotor = hardwareMap.get(DcMotorEx.class, "transferMotor");
        transferMotor = new CachedMotor(rawTransferMotor, 0.01);
        transferMotor.set_power(0);
        rawTransferMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        rawTransferMotor.setDirection(DcMotorSimple.Direction.REVERSE);
        rawTransferMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        transferMotor.invalidate();

        colorSensorLow = hardwareMap.get(ColorRangeSensor.class, "colorSensorLow");
        colorSensorHigh = hardwareMap.get(ColorRangeSensor.class, "colorSensorHigh");
        sensorReadingsInitialized = false;
    }

    public void request_shot() { shotRequested = true; }

    public void stop_feed() {
        shotRequested = false;
        off();
        transfer_off();
    }

    public void stop() {
        scoringMode = ScoringMode.COLLECT;
        shotRequested = false;
        off();
        transfer_off();
    }

    public void set_scoring_mode(ScoringMode mode) {
        scoringMode = mode;
        shotRequested = false;
        if (mode == ScoringMode.COLLECT) forward();
        else off();
        transfer_off();
    }
    public void collect() { set_scoring_mode(ScoringMode.COLLECT); }
    public boolean is_scoring_requested() { return scoringMode == ScoringMode.SHOOT; }
    public boolean is_shooting_requested() { return shotRequested; }

    /** Feed only for an explicit shot request and a ready shooter sample. */
    public void update_feed(boolean ready) {
        if (is_shooting_requested()) {
            if (ready) {
                forward();
                transfer_forward();
            } else {
                off();
                transfer_off();
            }
        } else {
            transfer_off();
        }
    }
    public void toggle_score_mode() {
        if (scoringMode == ScoringMode.COLLECT) {
            set_scoring_mode(ScoringMode.SHOOT);
        } else {
            collect();
        }
    }
}
