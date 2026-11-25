package org.firstinspires.ftc.teamcode.subsystems;

import org.firstinspires.ftc.teamcode.core.control.OpModeCleanup;

import static org.firstinspires.ftc.teamcode.core.units.Units.Length.mm;

import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.function.LongSupplier;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;
import org.firstinspires.ftc.teamcode.core.units.Units;

public class Intake {
    private final LongSupplier nanoClock;

    public Intake() {
        this(System::nanoTime);
    }

    Intake(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
        reset_sensor_schedule();
    }

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
    // Alternate sensors every 150 ms, for a nominal 300 ms per sensor.
    private static final long SENSOR_READ_SPACING_NANOS = 150_000_000L;
    // Allow 50 ms of I²C and scheduling delay beyond the nominal age and pair spacing.
    private static final long MAX_SENSOR_AGE_NANOS = 350_000_000L;
    private static final long MAX_PAIR_SPACING_NANOS = 200_000_000L;
    private ColorRangeSensor colorSensorLow;
    private ColorRangeSensor colorSensorHigh;
    private final SensorReadings.Sample upperDistance = new SensorReadings.Sample();
    private final SensorReadings.Sample lowerDistance = new SensorReadings.Sample();
    private long nextSensorReadNanos;
    private long lastLowerReadNanos;
    private long lastUpperReadNanos;
    private boolean readUpperNext;
    private boolean haveSensorPair;
    private static final Units.Length EMPTY_UPPER_DISTANCE = mm(0); // tune pls, placeholder value
    private static final Units.Length EMPTY_LOWER_DISTANCE = mm(0); // tune pls, placeholder value

    /** Caller holds the monitor. Return the timestamp used to judge both samples. */
    private long refresh_sensor_readings() {
        long now = nanoClock.getAsLong();
        if (now - nextSensorReadNanos < 0) return now;

        if (readUpperNext) {
            upperDistance.update(colorSensorHigh.getDistance(DistanceUnit.MM));
            lastUpperReadNanos = now;
            haveSensorPair = true;
        } else {
            lowerDistance.update(colorSensorLow.getDistance(DistanceUnit.MM));
            lastLowerReadNanos = now;
        }
        readUpperNext = !readUpperNext;
        // Space reads from completion so a slow call never queues an immediate catch-up read.
        now = nanoClock.getAsLong();
        nextSensorReadNanos = now + SENSOR_READ_SPACING_NANOS;
        return now;
    }

    private synchronized void reset_sensor_schedule() {
        readUpperNext = false;
        haveSensorPair = false;
        nextSensorReadNanos = nanoClock.getAsLong();
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
        return loaded_at(refresh_sensor_readings());
    }

    private boolean loaded_at(long now) {
        return sensor_readings_valid_at(now)
                && lowerDistance.get_value() < EMPTY_LOWER_DISTANCE.mm()
                && upperDistance.get_value() < EMPTY_UPPER_DISTANCE.mm();
    }

    public synchronized boolean sensor_readings_valid() {
        return sensor_readings_valid_at(refresh_sensor_readings());
    }

    private boolean sensor_readings_valid_at(long now) {
        return haveSensorPair && lowerDistance.is_valid() && upperDistance.is_valid()
                && now - lastLowerReadNanos <= MAX_SENSOR_AGE_NANOS
                && now - lastUpperReadNanos <= MAX_SENSOR_AGE_NANOS
                && Math.abs(lastLowerReadNanos - lastUpperReadNanos) <= MAX_PAIR_SPACING_NANOS;
    }

    public synchronized String get_sensor_telemetry() {
        long now = refresh_sensor_readings();
        return mm(lowerDistance.get_value()) + "\t" + mm(upperDistance.get_value()) + "\t" + loaded_at(now)
                + "\tvalid: " + sensor_readings_valid_at(now);
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
        lowerDistance.reset();
        upperDistance.reset();
        DcMotorEx rawIntakeMotor = hardwareMap.get(DcMotorEx.class, "intakeMotor");
        intakeMotor = new CachedMotor(rawIntakeMotor, 0.01);
        intakeMotor.set_power(0);
        rawIntakeMotor.setDirection(DcMotorSimple.Direction.REVERSE);
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
        reset_sensor_schedule();
    }

    public void request_shot() { shotRequested = true; }

    public void stop_feed() {
        // Do not reuse a pre-shot full sample after pollen may have left the robot.
        if (transferMode == TransferMode.FORWARD) reset_sensor_schedule();
        shotRequested = false;
        OpModeCleanup.run(null, this::off, this::transfer_off);
    }

    public void stop() {
        fullLoadHeld = false;
        reset_sensor_schedule();
        scoringMode = ScoringMode.COLLECT;
        shotRequested = false;
        OpModeCleanup.run(null, this::off, this::transfer_off);
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
