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
    // Preserve collection intent when a full load temporarily suppresses the motor output.
    private IntakeMode requestedIntakeMode = IntakeMode.OFF;
    int intakePow = 1;

    public void forward() {
        requestedIntakeMode = IntakeMode.FORWARD;
        if (!shotRequested) apply_intake_output(fullLoadHeld ? IntakeMode.OFF : requestedIntakeMode);
    }

    public void reverse() {
        requestedIntakeMode = IntakeMode.REVERSE;
        if (!shotRequested) apply_intake_output(fullLoadHeld ? IntakeMode.OFF : requestedIntakeMode);
    }

    public void off() {
        requestedIntakeMode = IntakeMode.OFF;
        apply_intake_output(IntakeMode.OFF);
    }

    private void apply_intake_output(IntakeMode mode) {
        if (intakeMotor != null) intakeMotor.set_power(mode == IntakeMode.FORWARD ? intakePow
                : mode == IntakeMode.REVERSE ? -intakePow : 0);
        intakeMode = mode;
    }

    public void toggle_forward() {
        if (requestedIntakeMode != IntakeMode.FORWARD) {
            forward();
        } else {
            off();
        }
    }

    public void toggle_reverse() {
        if (requestedIntakeMode != IntakeMode.REVERSE) {
            reverse();
        } else {
            off();
        }
    }
    public void toggle_forward_reverse(){
        if(requestedIntakeMode == IntakeMode.FORWARD){
            reverse();
        } else if (requestedIntakeMode == IntakeMode.REVERSE) {
            forward();
        }
        else{
            //-> intake is off, most likely scenario needs intake forward
            // not clean code
            forward();
        }
    }

    public double get_intake_power() {
        return intakeMotor.get_power();
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
        return transferMotor.get_power();
    }

    public TransferMode get_transfer_mode() {
        return transferMode;
    }

    // Color sensors and sensor readings
    private static final long SENSOR_READ_INTERVAL_NANOS = 300_000_000L;
    private ColorRangeSensor colorSensorLow;
    private ColorRangeSensor colorSensorHigh;
    private final SensorReadings.Sample upperDistance = new SensorReadings.Sample();
    private final SensorReadings.Sample lowerDistance = new SensorReadings.Sample();
    private long lastSensorReadNanos;
    private boolean sensorReadingsInitialized;
    private static final Units.Length EMPTY_UPPER_DISTANCE = mm(0); // tune pls, placeholder value
    private static final Units.Length EMPTY_LOWER_DISTANCE = mm(0); // tune pls, placeholder value

    private synchronized void refresh_sensor_readings() {
        long now = System.nanoTime();
        if (!sensorReadingsInitialized || now - lastSensorReadNanos >= SENSOR_READ_INTERVAL_NANOS) {
            lowerDistance.update(colorSensorLow.getDistance(DistanceUnit.MM));
            upperDistance.update(colorSensorHigh.getDistance(DistanceUnit.MM));
            lastSensorReadNanos = now;
            sensorReadingsInitialized = true;
        }
    }

    public synchronized Units.Length get_upper_distance() {
        refresh_sensor_readings();
        return mm(upperDistance.get_value());
    }

    public synchronized Units.Length get_lower_distance() {
        refresh_sensor_readings();
        return mm(lowerDistance.get_value());
    }

    public synchronized boolean loaded() {
        refresh_sensor_readings();
        return lowerDistance.is_valid() && upperDistance.is_valid()
                && lowerDistance.get_value() < EMPTY_LOWER_DISTANCE.mm()
                && upperDistance.get_value() < EMPTY_UPPER_DISTANCE.mm();
    }

    public synchronized boolean sensor_readings_valid() {
        refresh_sensor_readings();
        return lowerDistance.is_valid() && upperDistance.is_valid();
    }

    public synchronized String get_sensor_telemetry() {
        return get_lower_distance() + "\t" + get_upper_distance() + "\t" + loaded()
                + "\tvalid: " + sensor_readings_valid();
    }

    // Combined subsystem operations
    private enum ScoringMode {COLLECT, SHOOT}
    private ScoringMode scoringMode = ScoringMode.COLLECT;
    private boolean shotRequested;
    // Pollen cannot leave a full robot before feeding starts; a sensor dropout must not restart collection.
    private boolean fullLoadHeld;

    public boolean full_load_held() { return fullLoadHeld; }

    public void init(HardwareMap hardwareMap) { // tune directions of motors
        intakeMode = IntakeMode.OFF;
        requestedIntakeMode = IntakeMode.OFF;
        fullLoadHeld = false;
        transferMode = TransferMode.OFF;
        scoringMode = ScoringMode.COLLECT;
        shotRequested = false;
        sensorReadingsInitialized = false;
        lowerDistance.reset();
        upperDistance.reset();
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
        // Do not reuse a pre-shot full sample after pollen may have left the robot.
        if (transferMode == TransferMode.FORWARD) sensorReadingsInitialized = false;
        shotRequested = false;
        off();
        transfer_off();
    }

    public void stop() {
        fullLoadHeld = false;
        sensorReadingsInitialized = false;
        scoringMode = ScoringMode.COLLECT;
        shotRequested = false;
        off();
        transfer_off();
    }

    /** SHOOT mode means preparation, not immediate transfer. A held shot keeps priority over mode edits. */
    public void set_scoring_mode(ScoringMode mode) {
        scoringMode = mode;
        requestedIntakeMode = mode == ScoringMode.COLLECT ? IntakeMode.FORWARD : IntakeMode.OFF;
        if (!shotRequested) {
            apply_intake_output(fullLoadHeld ? IntakeMode.OFF : requestedIntakeMode);
            transfer_off();
        }
    }
    public void collect() { set_scoring_mode(ScoringMode.COLLECT); }
    public boolean is_scoring_requested() { return scoringMode == ScoringMode.SHOOT; }
    public boolean is_shooting_requested() { return shotRequested; }

    /** Compatibility entry point; Shooter passes its single loaded result through the overload below. */
    public void update_feed(boolean feedAllowed) {
        update_feed(feedAllowed, !is_shooting_requested() && loaded());
    }

    /** Shooter owns the shot latch; readiness is required to start feeding, not to continue it. */
    public void update_feed(boolean feedAllowed, boolean loaded) {
        if (is_shooting_requested()) {
            if (feedAllowed) {
                fullLoadHeld = false;
                apply_intake_output(IntakeMode.FORWARD);
                transfer_forward();
            } else {
                apply_intake_output(IntakeMode.OFF);
                transfer_off();
            }
        } else {
            if (loaded) fullLoadHeld = true;
            apply_intake_output(fullLoadHeld ? IntakeMode.OFF : requestedIntakeMode);
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
