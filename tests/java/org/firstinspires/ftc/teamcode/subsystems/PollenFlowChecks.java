package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.Servo;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.firstinspires.ftc.teamcode.core.hardware.CachedMotor;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.opmodes.teleop.comp.Main;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Regression checks using production controls with simulated hardware and a given binary loaded signal. */
public final class PollenFlowChecks {
    private static int checks, matrixCases, sensorCases, sequenceChecks;
    private static final String[] conditions = {
        "ready", "rpm-70", "rpm-below-70", "rpm+70", "rpm-above+70", "rpm-zero", "rpm-high",
        "encoder-nan", "encoder+inf", "encoder-inf", "hood-moving", "hood-uncommanded",
        "hood-invalid", "model-invalid-pose", "fixed-invalid-pose", "fixed-zero", "pid-invalid", "rpm-negative"
    };

    public static void main(String[] args) throws Exception {
        for (boolean loaded : new boolean[]{false, true})
            for (boolean scoring : new boolean[]{false, true})
                for (boolean preparing : new boolean[]{false, true})
                    for (boolean requested : new boolean[]{false, true})
                        for (int previous : new int[]{-1, 0, 1})
                            for (String condition : conditions)
                                matrix_case(loaded, scoring, preparing, requested, previous, condition);
        sensor_cases();
        trigger_sequences();
        active_shot_faults();
        dual_encoder_feedback();
        encoder_sample_recovery();
        full_load_lifecycle();
        deferred_shot_targets();
        System.out.println("Production-state matrix: " + matrixCases + " combinations passed");
        System.out.println("Production loaded() implementation: " + sensorCases + " distance pairs passed");
        System.out.println("Production TeleOp trigger sequences: " + sequenceChecks + " assertions passed");
        System.out.println("Total audit assertions: " + checks);
        System.out.println("Full collection pauses until feeding starts; an active shot survives readiness faults.");
        System.out.println("Current feedforward at 3000 RPM: " + (0.00017 * 3000 / 60 * Shooter.ENCODER_TICKS_PER_REV));
    }

    private static void matrix_case(boolean loaded, boolean scoring, boolean preparing,
                                    boolean requested, int previous, String condition) throws Exception {
        Rig rig = new Rig();
        rig.intake.loaded = loaded;
        Field mode = field(Intake.class, "scoringMode");
        Object[] values = mode.getType().getEnumConstants();
        mode.set(rig.intake, values[scoring ? 1 : 0]);
        if (previous > 0) rig.intake.forward();
        else if (previous < 0) rig.intake.reverse();
        else rig.intake.off();
        if (preparing) rig.shooter.prepare_shot();
        if (requested) rig.shooter.shoot();
        boolean fixed = condition.startsWith("fixed-");
        boolean targetValid = !condition.equals("model-invalid-pose");
        rig.shooter.set_bypass_enabled(fixed);
        if (condition.equals("fixed-zero")) rig.shooter.set_target_rpm(0);
        rig.shooter.update_target_hive(9, 9, !condition.contains("invalid-pose"));
        double rpm = 3000;
        switch (condition) {
            case "rpm-70": rpm = 2930; break;
            case "rpm-below-70": rpm = 2929.9; break;
            case "rpm+70": rpm = 3070; break;
            case "rpm-above+70": rpm = 3070.1; break;
            case "rpm-zero": case "fixed-zero": rpm = 0; break;
            case "rpm-high": rpm = 3500; break;
            case "encoder-nan": rpm = Double.NaN; break;
            case "encoder+inf": rpm = Double.POSITIVE_INFINITY; break;
            case "encoder-inf": rpm = Double.NEGATIVE_INFINITY; break;
            case "rpm-negative": rpm = -100; break;
            default: break;
        }
        rig.velocity[0] = rpm * Shooter.ENCODER_TICKS_PER_REV / 60.0;
        double measured = rig.velocity[0] * (60.0 / Shooter.ENCODER_TICKS_PER_REV);
        set(rig.shooter, Shooter.class, "lastHoodCommandNanos", condition.equals("hood-moving")
                ? System.nanoTime() : System.nanoTime() - 1_000_000_000L);
        if (condition.equals("hood-uncommanded")) set(rig.shooter, Shooter.class, "hoodCommanded", false);
        if (condition.equals("hood-invalid")) set(rig.shooter, Shooter.class, "hoodTargetValid", false);
        if (condition.equals("pid-invalid")) set(rig.shooter, Shooter.class, "integralSum", Double.POSITIVE_INFINITY);
        rig.shooter.update();

        Shooter.State expected = requested ? Shooter.State.SHOOT
                : preparing || scoring || loaded ? Shooter.State.PREPARE : Shooter.State.FLOAT;
        boolean controlValid = Double.isFinite(measured) && (expected == Shooter.State.FLOAT || targetValid)
                && !condition.equals("pid-invalid");
        boolean ready = controlValid && targetValid && !condition.startsWith("hood-")
                && !condition.equals("fixed-zero") && Math.abs(measured - 3000) <= 70;
        String context = loaded + "/" + scoring + "/" + preparing + "/" + requested + "/" + previous + "/" + condition;
        check(rig.shooter.get_state() == expected, "state " + context);
        check(rig.shooter.ready_to_feed() == ready, "ready " + context);
        close(rig.intake.get_intake_power(), requested ? ready ? 1 : 0 : loaded ? 0 : previous, "intake " + context);
        close(rig.intake.get_transfer_power(), requested && ready ? 1 : 0, "transfer " + context);
        check(rig.intake.loadedCalls == (!requested ? 1 : 0), "loaded short circuit " + context);
        if (!controlValid) close(rig.flywheelPower[0], 0, "flywheel fault " + context);
        matrixCases++;
    }

    private static void sensor_cases() throws Exception {
        double[] readings = {-1, 0, 10, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double lower : readings) for (double upper : readings) {
            Intake intake = new Intake();
            set(intake, Intake.class, "colorSensorLow", sensor(lower));
            set(intake, Intake.class, "colorSensorHigh", sensor(upper));
            boolean valid = Double.isFinite(lower) && Double.isFinite(upper);
            check(intake.sensor_readings_valid() == valid, "finite sensor pair");
            check(intake.loaded() == (valid && lower < 0 && upper < 0), "zero threshold loaded result");
            sensorCases++;
        }
    }

    private static void trigger_sequences() throws Exception {
        Controls c = new Controls();
        sequence(!c.rig.intake.is_shooting_requested() && c.rig.intake.get_intake_power() == 0, "startup off");
        c.tick(0.94f, 0);
        sequence(!c.rig.intake.is_scoring_requested(), "LT not fully pressed");
        c.tick(1, 0);
        sequence(c.rig.intake.is_scoring_requested() && c.rig.intake.get_intake_power() == 0, "LT first scoring");
        c.tick(0.8f, 0); c.tick(1, 0);
        sequence(c.rig.intake.is_scoring_requested(), "partial LT release does not rearm");
        c.tick(0, 0); c.tick(1, 0);
        sequence(!c.rig.intake.is_scoring_requested() && c.rig.intake.get_intake_power() == 1, "LT second collect");

        c = new Controls();
        c.tick(0, 0.04f);
        sequence(!c.rig.intake.is_shooting_requested(), "RT below press threshold");
        c.tick(0, 0.06f);
        sequence(c.rig.intake.is_shooting_requested() && c.rig.intake.get_transfer_power() == 1, "small RT press shoots");
        c.tick(1, 0.06f);
        sequence(c.rig.intake.is_shooting_requested() && c.rig.intake.is_scoring_requested(), "LT mode edit preserves held RT");
        sequence(c.rig.intake.get_intake_power() == 1 && c.rig.intake.get_transfer_power() == 1, "mode edit cannot pulse an active feed off");
        c.tick(1, 0.8f);
        sequence(c.rig.intake.is_shooting_requested(), "held RT continues after a mode edit");
        c.tick(1, 0);
        sequence(!c.rig.intake.is_scoring_requested() && c.rig.intake.get_intake_power() == 1, "RT release resumes collection");
        c.tick(1, 0.06f);
        sequence(c.rig.intake.is_shooting_requested() && c.rig.intake.get_transfer_power() == 1, "new RT edge starts another shot");
        c.rig.velocity[0] = 0; c.tick(1, 0.06f);
        sequence(c.rig.intake.get_transfer_power() == 1 && c.rig.intake.get_intake_power() == 1, "speed drop cannot interrupt feed");
        c.rig.velocity[0] = 3000 * Shooter.ENCODER_TICKS_PER_REV / 60; c.tick(1, 0.06f);
        sequence(c.rig.intake.get_transfer_power() == 1 && c.rig.intake.get_intake_power() == 1, "speed recovery keeps feeding");

        c = new Controls(); c.tick(1, 0.06f);
        sequence(c.rig.intake.is_scoring_requested() && c.rig.intake.is_shooting_requested(), "simultaneous new LT/RT preserves shot priority");
        c.tick(0, 0.06f); c.tick(1, 0);
        sequence(!c.rig.intake.is_scoring_requested() && !c.rig.intake.is_shooting_requested()
                && c.rig.intake.get_intake_power() == 1, "simultaneous LT full/RT release: collect wins");

        c = new Controls();
        float belowRelease = Math.nextDown(0.05f);
        float aboveRelease = 0.05f;
        float belowFull = 0.95f;
        float aboveFull = Math.nextUp(0.95f);
        c.tick(belowFull, belowRelease);
        sequence(!c.rig.intake.is_scoring_requested() && !c.rig.intake.is_shooting_requested(),
                "float samples below the original double thresholds stay inactive");
        c.tick(aboveFull, aboveRelease);
        sequence(c.rig.intake.is_scoring_requested() && c.rig.intake.is_shooting_requested(),
                "next representable threshold samples preserve full-press and light-shot edges");
        c.tick(aboveRelease, belowRelease);
        sequence(!c.rig.intake.is_shooting_requested(), "right trigger releases only below its threshold");
        c.tick(aboveFull, belowRelease);
        sequence(!c.rig.intake.is_scoring_requested(), "partial left release cannot rearm its full-press latch");
        c.tick(belowRelease, belowRelease);
        c.tick(aboveFull, belowRelease);
        sequence(c.rig.intake.is_scoring_requested(), "left trigger rearms after crossing the release threshold");

        Rig r = new Rig(); r.intake.loaded = true; r.intake.collect(); r.shooter.update(9, 9, true);
        sequence(r.shooter.get_state() == Shooter.State.PREPARE && r.intake.get_intake_power() == 0, "loaded full stops collection");
        r.shooter.prepare_shot(); r.intake.loaded = false; r.shooter.update();
        sequence(r.shooter.get_state() == Shooter.State.PREPARE, "explicit preparation survives detection change");
        r.shooter.shoot(); r.shooter.update();
        sequence(r.intake.get_transfer_power() == 1 && !r.intake.loaded, "shoots with unloaded signal");
        r.shooter.stop();
        sequence(r.flywheelPower[0] == 0 && r.intake.get_intake_power() == 0 && r.intake.get_transfer_power() == 0, "STOP all motor commands zero");
    }

    private static void active_shot_faults() throws Exception {
        for (boolean loaded : new boolean[]{false, true}) for (String condition : conditions) {
            Rig rig = new Rig();
            rig.intake.loaded = loaded;
            rig.shooter.update(9, 9, true);
            rig.shooter.shoot();
            check(rig.shooter.shot_in_progress(), "cached readiness begins the shot immediately");
            double acceptedPower = rig.flywheelPower[0];
            int writes = rig.flywheelWrites[0];
            if (condition.equals("encoder-nan")) rig.velocity[0] = Double.NaN;
            else if (condition.equals("encoder+inf")) rig.velocity[0] = Double.POSITIVE_INFINITY;
            else if (condition.equals("encoder-inf")) rig.velocity[0] = Double.NEGATIVE_INFINITY;
            else if (condition.startsWith("rpm-") || condition.equals("rpm-zero")) rig.velocity[0] = 0;
            else if (condition.startsWith("rpm+") || condition.equals("rpm-high")) rig.velocity[0] = 4000 * Shooter.ENCODER_TICKS_PER_REV / 60;
            if (condition.equals("hood-moving")) set(rig.shooter, Shooter.class, "lastHoodCommandNanos", System.nanoTime());
            if (condition.equals("hood-uncommanded")) set(rig.shooter, Shooter.class, "hoodCommanded", false);
            if (condition.equals("hood-invalid")) set(rig.shooter, Shooter.class, "hoodTargetValid", false);
            if (condition.equals("pid-invalid")) set(rig.shooter, Shooter.class, "integralSum", Double.POSITIVE_INFINITY);
            if (condition.equals("fixed-zero")) rig.shooter.set_target_rpm(0);
            if (condition.contains("invalid-pose")) rig.shooter.update_target_hive(0, 0, false);
            rig.shooter.update();
            check(rig.shooter.shot_in_progress(), "fault retains active shot: " + condition);
            close(rig.intake.get_intake_power(), 1, "active intake " + condition);
            close(rig.intake.get_transfer_power(), 1, "active transfer " + condition);
            if (condition.startsWith("encoder") || condition.equals("pid-invalid")) {
                close(rig.flywheelPower[0], acceptedPower, "retain accepted power " + condition);
                check(rig.flywheelWrites[0] == writes, "fault cannot send a transient zero power command");
            }
            rig.shooter.stop_shooting();
            check(!rig.shooter.shot_in_progress() && !rig.intake.is_shooting_requested(), "explicit release ends shot");
            close(rig.intake.get_intake_power(), 0, "release stops intake");
            close(rig.intake.get_transfer_power(), 0, "release stops transfer");
            // A second shot must pass readiness again; the old latch must never authorize it.
            rig.velocity[0] = Double.NaN;
            rig.shooter.shoot();
            rig.shooter.update();
            check(!rig.shooter.shot_in_progress(), "new shot cannot reuse old active latch");
            close(rig.intake.get_transfer_power(), 0, "fault blocks second shot");
        }
    }

    private static void full_load_lifecycle() throws Exception {
        Rig rig = new Rig();
        rig.intake.collect();
        rig.shooter.prepare_shot();
        rig.intake.loaded = true;
        rig.shooter.update(9, 9, true);
        check(rig.intake.full_load_held(), "full is noticed even during explicit preparation");
        close(rig.intake.get_intake_power(), 0, "full collection paused");
        rig.intake.loaded = false;
        rig.shooter.update();
        check(rig.intake.full_load_held(), "dropout cannot forget a known full load");
        close(rig.intake.get_intake_power(), 0, "dropout cannot restart intake");
        rig.shooter.shoot(); rig.shooter.update();
        check(!rig.intake.full_load_held(), "feeding clears full hold");
        close(rig.intake.get_intake_power(), 1, "shooting overrides full stop");
        close(rig.intake.get_transfer_power(), 1, "shooting uses transfer");
        set(rig.intake, Intake.class, "sensorReadingsInitialized", true);
        rig.shooter.stop_shooting();
        check(!(boolean) field(Intake.class, "sensorReadingsInitialized").get(rig.intake), "post-shot full reading must be refreshed");
        rig.intake.collect(); rig.shooter.update();
        close(rig.intake.get_intake_power(), 1, "not-full post-shot collection resumes");
        rig.intake.loaded = true; rig.shooter.update();
        close(rig.intake.get_intake_power(), 0, "next full load pauses again");
        rig.shooter.stop();
        close(rig.flywheelPower[0], 0, "STOP overrides retention");
        close(rig.intake.get_intake_power(), 0, "STOP intake");
        close(rig.intake.get_transfer_power(), 0, "STOP transfer");
    }

    private static void dual_encoder_feedback() throws Exception {
        double ticksPerRpm = Shooter.ENCODER_TICKS_PER_REV / 60.0;
        double[] rightVelocity = {3200 * ticksPerRpm};
        Rig rig = new Rig(rightVelocity);
        rig.velocity[0] = 2800 * ticksPerRpm;
        rig.shooter.update(9, 9, true);
        close(rig.shooter.get_current_rpm(), 3000, "both encoders contribute equally to RPM");
        check(rig.shooter.ready_to_feed(), "averaged RPM controls readiness");
        rig.velocity[0] = 2600 * ticksPerRpm;
        rightVelocity[0] = 3600 * ticksPerRpm;
        rig.shooter.update();
        close(rig.shooter.get_current_rpm(), 3100, "average also feeds the custom controller");
        close(rig.flywheelPower[0], 0, "averaged overspeed cuts left power");
        close(rig.rightFlywheelPower[0], 0, "averaged overspeed cuts right power");

        for (double fault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            rig.velocity[0] = fault;
            rightVelocity[0] = 2000 * ticksPerRpm;
            rig.shooter.update();
            check(rig.shooter.velocity_valid(), "right encoder alone is usable");
            close(rig.shooter.get_current_rpm(), 2000, "invalid left reading is excluded");
            close(rig.flywheelPower[0], 1, "right feedback spins up both motors");
            close(rig.rightFlywheelPower[0], 1, "right feedback reaches paired motor");
            rig.velocity[0] = 3500 * ticksPerRpm;
            rightVelocity[0] = fault;
            rig.shooter.update();
            check(rig.shooter.velocity_valid(), "left encoder alone is usable");
            close(rig.shooter.get_current_rpm(), 3500, "invalid right reading is excluded");
            close(rig.flywheelPower[0], 0, "left feedback cuts overspeed power");
            close(rig.rightFlywheelPower[0], 0, "left feedback cuts paired power");
        }
        rig.velocity[0] = Double.MAX_VALUE;
        rightVelocity[0] = Double.MAX_VALUE;
        rig.shooter.update();
        check(rig.shooter.velocity_valid() && Double.isFinite(rig.shooter.get_current_rpm()),
                "averaging finite readings cannot overflow");
        int leftReads = rig.flywheelReads[0], rightReads = rig.rightFlywheelReads[0];
        rig.shooter.get_current_rpm();
        rig.shooter.velocity_valid();
        rig.shooter.ready_to_feed();
        rig.shooter.feed_status();
        check(leftReads == 9 && rightReads == 9, "one hardware read per encoder per update");
        check(rig.flywheelReads[0] == leftReads && rig.rightFlywheelReads[0] == rightReads,
                "status and readiness reuse both cached samples");
        rig.velocity[0] = 0;
        rightVelocity[0] = 0;
        rig.shooter.update();
        set(rig.shooter, Shooter.class, "integralSum", 0.25);
        set(rig.shooter, Shooter.class, "lastError", 12.5);
        double time = field(Shooter.class, "lastTime").getDouble(rig.shooter);
        int leftWrites = rig.flywheelWrites[0], rightWrites = rig.rightFlywheelWrites[0];
        for (double leftFault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (double rightFault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                rig.velocity[0] = leftFault;
                rightVelocity[0] = rightFault;
                rig.shooter.update();
                check(!rig.shooter.velocity_valid() && !rig.shooter.ready_to_feed(), "two invalid encoders block readiness");
                close(rig.shooter.get_current_rpm(), 0, "two invalid readings preserve last accepted RPM");
                close(rig.flywheelPower[0], 1, "two invalid readings hold nonzero left power");
                close(rig.rightFlywheelPower[0], 1, "two invalid readings hold nonzero right power");
                check(rig.flywheelWrites[0] == leftWrites && rig.rightFlywheelWrites[0] == rightWrites,
                        "repeated invalid pairs do not write either motor");
                close(field(Shooter.class, "integralSum").getDouble(rig.shooter), 0.25, "retain nonzero integration");
                close(field(Shooter.class, "lastError").getDouble(rig.shooter), 12.5, "retain previous error");
                close(field(Shooter.class, "lastTime").getDouble(rig.shooter), time, "retain controller timestamp");
            }
        }
        rig.velocity[0] = 3500 * ticksPerRpm;
        rig.shooter.update();
        check(rig.shooter.velocity_valid(), "one returning encoder resumes regulation");
        close(rig.flywheelPower[0], 0, "returning left feedback regulates both motors");
        close(rig.rightFlywheelPower[0], 0, "returning left feedback reaches paired motor");
        rig.velocity[0] = Double.NaN;
        rightVelocity[0] = 2000 * ticksPerRpm;
        rig.shooter.update();
        close(rig.shooter.get_current_rpm(), 2000, "right-only feedback can replace left-only feedback");
        close(rig.flywheelPower[0], 1, "returning right feedback restores spin-up");
        close(rig.rightFlywheelPower[0], 1, "returning right feedback reaches paired motor");
    }

    private static void encoder_sample_recovery() throws Exception {
        for (Shooter.State state : Shooter.State.values()) {
            for (double fault : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                Rig rig = new Rig();
                if (state == Shooter.State.PREPARE) rig.shooter.prepare_shot();
                if (state == Shooter.State.SHOOT) {
                    rig.shooter.update(9, 9, true);
                    rig.shooter.shoot();
                }
                rig.velocity[0] = 0;
                rig.shooter.update(9, 9, true);
                close(rig.flywheelPower[0], 1, "spin-up power before bad sample");
                close(rig.rightFlywheelPower[0], 1, "paired spin-up power before bad sample");
                int leftWrites = rig.flywheelWrites[0], rightWrites = rig.rightFlywheelWrites[0];
                double integral = field(Shooter.class, "integralSum").getDouble(rig.shooter);
                double error = field(Shooter.class, "lastError").getDouble(rig.shooter);
                double time = field(Shooter.class, "lastTime").getDouble(rig.shooter);
                rig.velocity[0] = fault;
                rig.shooter.update(9, 9, true);
                check(rig.shooter.get_state() == state && !rig.shooter.velocity_valid()
                        && !rig.shooter.ready_to_feed(), "bad encoder sample does not authorize a new shot or change state");
                check(rig.flywheelWrites[0] == leftWrites && rig.rightFlywheelWrites[0] == rightWrites,
                        "bad sample skips both motor writes");
                close(rig.flywheelPower[0], 1, "bad sample retains left power");
                close(rig.rightFlywheelPower[0], 1, "bad sample retains right power");
                close(field(Shooter.class, "integralSum").getDouble(rig.shooter), integral, "skip integration");
                close(field(Shooter.class, "lastError").getDouble(rig.shooter), error, "skip derivative history");
                close(field(Shooter.class, "lastTime").getDouble(rig.shooter), time, "skip control timestamp");
                close(rig.intake.get_transfer_power(), state == Shooter.State.SHOOT ? 1 : 0,
                        "only an already active shot continues feeding");
                rig.velocity[0] = 3500 * Shooter.ENCODER_TICKS_PER_REV / 60;
                rig.shooter.update(9, 9, true);
                check(rig.shooter.velocity_valid() && rig.shooter.get_current_rpm() > 3400,
                        "next valid sample is accepted");
                close(rig.flywheelPower[0], 0, "next valid overspeed sample regulates left motor");
                close(rig.rightFlywheelPower[0], 0, "next valid overspeed sample regulates right motor");
                check(rig.flywheelWrites[0] == leftWrites + 1 && rig.rightFlywheelWrites[0] == rightWrites + 1,
                        "control resumes on next valid sample");
                rig.velocity[0] = 0;
                rig.shooter.update(9, 9, true);
                rig.velocity[0] = fault;
                rig.shooter.stop();
                close(rig.flywheelPower[0], 0, "explicit STOP overrides missing encoder");
                close(rig.rightFlywheelPower[0], 0, "explicit STOP stops paired motor");
                close(rig.intake.get_transfer_power(), 0, "explicit STOP still ends feed");
            }
        }
        Rig zero = new Rig();
        zero.shooter.prepare_shot();
        zero.velocity[0] = 0;
        zero.shooter.update(9, 9, true);
        zero.shooter.set_target_rpm(0);
        zero.velocity[0] = Double.NaN;
        zero.shooter.update(9, 9, true);
        close(zero.flywheelPower[0], 1, "two invalid encoders skip regulation even after a zero target edit");
        close(zero.rightFlywheelPower[0], 1, "zero target edit cannot change paired power on a skipped tick");
        zero.velocity[0] = 0;
        zero.shooter.update(9, 9, true);
        close(zero.flywheelPower[0], 0, "zero target applies when encoder feedback returns");
        close(zero.rightFlywheelPower[0], 0, "zero target then stops both motors");
        zero.shooter.set_target_rpm(3000);
        zero.velocity[0] = 0;
        zero.shooter.update(9, 9, true);
        zero.shooter.set_bypass_enabled(false);
        zero.velocity[0] = Double.NaN;
        zero.shooter.update(Double.NaN, 9, false);
        close(zero.flywheelPower[0], 1, "two invalid encoders skip regulation with an unavailable target");
        close(zero.rightFlywheelPower[0], 1, "unavailable target does not change power on a skipped tick");
        zero.velocity[0] = 0;
        zero.shooter.update(Double.NaN, 9, false);
        close(zero.flywheelPower[0], 0, "unavailable target stops motors after feedback returns");
        close(zero.rightFlywheelPower[0], 0, "unavailable target then stops both motors");
    }

    private static void deferred_shot_targets() throws Exception {
        Rig rig = new Rig();
        rig.shooter.update(9, 9, true);
        rig.shooter.shoot(); rig.shooter.update();
        int hoodWrites = rig.hoodWrites[0];
        rig.shooter.set_target_rpm(4000);
        rig.shooter.set_hood_angle(60);
        rig.shooter.set_bypass_enabled(false);
        rig.shooter.set_alliance(Alliance.BLUE);
        rig.shooter.update(Double.NaN, 0);
        close(rig.shooter.get_target_rpm(), 3000, "active shot RPM locked");
        close(rig.shooter.get_target_hood_angle(), 45, "active shot hood locked");
        check(rig.hoodWrites[0] == hoodWrites, "configuration/pose change cannot move hood mid-shot");
        close(rig.intake.get_transfer_power(), 1, "configuration changes cannot interrupt shot");
        rig.shooter.stop_shooting();
        rig.shooter.set_bypass_enabled(true);
        rig.shooter.update();
        close(rig.shooter.get_target_rpm(), 4000, "stored RPM applies after shot");
        close(rig.shooter.get_target_hood_angle(), 60, "stored hood applies after shot");
        check(!rig.shooter.hood_ready(), "new hood command must settle for the next shot");
    }

    private static final class GivenIntake extends Intake {
        boolean loaded;
        int loadedCalls;
        @Override public boolean loaded() { loadedCalls++; return loaded; }
    }

    private static final class Rig {
        final GivenIntake intake = new GivenIntake();
        final Shooter shooter = new Shooter();
        final double[] velocity = {3000 * Shooter.ENCODER_TICKS_PER_REV / 60}, flywheelPower = {0};
        final double[] rightFlywheelPower = {0};
        final int[] flywheelWrites = {0}, rightFlywheelWrites = {0}, hoodWrites = {0};
        final int[] flywheelReads = {0}, rightFlywheelReads = {0};
        Rig() throws Exception {
            this(null);
        }
        Rig(double[] rightVelocity) throws Exception {
            set(intake, Intake.class, "intakeMotor", new CachedMotor(motor(new double[]{0}, new double[]{0}), 0.01));
            set(intake, Intake.class, "transferMotor", new CachedMotor(motor(new double[]{0}, new double[]{0}), 0.01));
            set(shooter, Shooter.class, "shooterMotorLeft", new CachedMotor(motor(velocity, flywheelPower, flywheelWrites, flywheelReads), 0.002));
            set(shooter, Shooter.class, "shooterMotorRight", new CachedMotor(motor(rightVelocity == null ? velocity : rightVelocity,
                    rightFlywheelPower, rightFlywheelWrites, rightFlywheelReads), 0.002));
            set(shooter, Shooter.class, "servoHood", new CachedServo((Servo) Proxy.newProxyInstance(
                    Servo.class.getClassLoader(), new Class<?>[]{Servo.class}, (p, m, a) -> {
                        if (m.getName().equals("setPosition")) hoodWrites[0]++;
                        return null;
                    }), 0.005));
            set(shooter, Shooter.class, "intakeSubsystem", intake);
            shooter.set_alliance(Alliance.RED);
            shooter.set_bypass_enabled(true);
            set(shooter, Shooter.class, "lastHoodCommandNanos", System.nanoTime() - 1_000_000_000L);
        }
    }

    private static final class Controls extends Main {
        final Rig rig = new Rig();
        final Gamepad one = new Gamepad(), two = new Gamepad();
        Controls() throws Exception {
            gamepad1 = new Gamepad();
            gamepad2 = new Gamepad();
            set(this, Main.class, "shooterSubsystem", rig.shooter);
            set(this, Main.class, "intakeSubsystem", rig.intake);
            set(this, Main.class, "flowerIntakeSubsystem", new FlowerIntake());
        }
        void tick(float left, float right) {
            one.left_trigger = left; one.right_trigger = right;
            gamepad1.copy(one);
            gamepad2.copy(two);
            update_controls();
            rig.shooter.update(9, 9, true);
        }
    }

    private static DcMotorEx motor(double[] velocity, double[] power) {
        return motor(velocity, power, new int[]{0});
    }

    private static DcMotorEx motor(double[] velocity, double[] power, int[] writes) {
        return motor(velocity, power, writes, new int[]{0});
    }

    private static DcMotorEx motor(double[] velocity, double[] power, int[] writes, int[] reads) {
        return (DcMotorEx) Proxy.newProxyInstance(DcMotorEx.class.getClassLoader(), new Class<?>[]{DcMotorEx.class},
                (p, m, a) -> {
                    if (m.getName().equals("getVelocity")) { reads[0]++; return velocity[0]; }
                    if (m.getName().equals("setPower")) { power[0] = (double) a[0]; writes[0]++; }
                    return null;
                });
    }
    private static ColorRangeSensor sensor(double distance) {
        return (ColorRangeSensor) Proxy.newProxyInstance(ColorRangeSensor.class.getClassLoader(),
                new Class<?>[]{ColorRangeSensor.class}, (p, m, a) -> m.getName().equals("getDistance") ? distance : null);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void set(Object object, Class<?> type, String name, Object value) throws Exception { field(type, name).set(object, value); }
    private static void sequence(boolean valid, String message) { sequenceChecks++; check(valid, message); }
    private static void close(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-9, message); }
    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }
}
