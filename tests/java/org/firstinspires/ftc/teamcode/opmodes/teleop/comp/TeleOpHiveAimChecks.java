package org.firstinspires.ftc.teamcode.opmodes.teleop.comp;

import com.pedropathing.algorithm.Foresight;
import com.pedropathing.drivetrain.DrivePowers;
import com.pedropathing.drivetrain.Drivetrain;
import com.pedropathing.follower.Follower;
import com.pedropathing.localization.Localizer;
import com.pedropathing.localization.MotionState;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.hardware.Gamepad;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Desktop control checks using SDK gamepad events and the Pedro holding controller. */
public final class TeleOpHiveAimChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (Alliance alliance : new Alliance[]{Alliance.RED, Alliance.BLUE}) {
            for (double y : new double[]{9, 72, Math.nextUp(72.0), 133}) {
                Pose start = new Pose(9, y, 0);
                Harness teleop = new Harness(start, alliance);
                teleop.tick(false, 0, 0, 0);
                check(teleop.drive.manual(), "normal driving before A");
                int poseReads = teleop.drive.poseReads;
                Shooter.HiveDistance cachedHive = teleop.shooter.get_target_hive();
                teleop.tick(true, 0, 0, 0);
                check(teleop.drive.poseReads == poseReads + 1, "A adds no follower pose retrieval to the loop");
                check(teleop.shooter.get_target_hive() == cachedHive, "A reuses the already validated hive");
                check(teleop.drive.holding(), "A enters HOLD instead of overwriting it with MANUAL");
                close(teleop.drive.target.x(), start.x());
                close(teleop.drive.target.y(), start.y());
                Shooter.HiveDistance hive = Shooter.nearest_hive(start.x(), start.y(), alliance);
                close_heading(teleop.drive.target.heading(), Math.atan2(hive.position.y - y, hive.position.x - 9));
                check(teleop.shooter.get_state() == Shooter.State.PREPARE, "idle shooter prepares");
                check(!teleop.intake.is_shooting_requested(), "aiming alone does not request feeding");

                Pose target = teleop.drive.target;
                teleop.move_pose(new Pose(start.x() + 3, start.y() + 2, target.heading() + 0.2));
                teleop.tick(true, 0, 0, 0);
                check(teleop.drive.holdCalls == 1 && teleop.drive.target == target, "held A captures only once");
                check(Math.abs(teleop.drivetrain.powers.forward()) + Math.abs(teleop.drivetrain.powers.strafe()) > 0,
                        "Pedro corrects displacement after a push");
                check(Math.abs(teleop.drivetrain.powers.turn()) > 0, "Pedro corrects disturbed heading");
                teleop.tick(false, 0, 0, 0);
                check(teleop.drive.holding(), "releasing A retains correction");
                teleop.tick(false, 0.02f, -0.049f, 0.02f);
                check(teleop.drive.holding(), "stick noise within deadband retains correction");
                check(teleop.shooter.prepareCalls == 1, "preparation does not restart every loop");
                teleop.tick(false, 0.06f, 0, 0);
                check(teleop.drive.manual(), "slight strafe cancels in the same loop");
                check(Math.abs(teleop.drivetrain.powers.forward()) + Math.abs(teleop.drivetrain.powers.strafe()) > 0,
                        "driver translation is applied immediately");
                teleop.tick(false, 0, 0, 0);
                check(teleop.drive.manual(), "centering sticks does not resume cancelled aim");
                teleop.tick(true, 0, 0, 0);
                check(teleop.drive.holding() && teleop.drive.holdCalls == 2, "a new press re-aims");
                close(teleop.drive.target.x(), start.x() + 3);
                close(teleop.drive.target.y(), start.y() + 2);
                teleop.tick(true, 0, 0, 0.06f);
                check(teleop.drive.manual() && Math.abs(teleop.drivetrain.powers.turn()) > 0,
                        "manual rotation cancels while A is still held");
                teleop.tick(true, 0, 0, 0);
                check(teleop.drive.manual(), "held A cannot re-engage after stick cancellation");
            }
        }
        Harness moving = new Harness(new Pose(9, 9, 0), Alliance.RED);
        moving.tick(true, 0, 0.06f, 0);
        check(moving.drive.manual(), "forward input wins over a simultaneous A press");

        Harness shooting = new Harness(new Pose(9, 9, 0), Alliance.RED);
        shooting.one.right_trigger = 1;
        shooting.tick(true, 0, 0, 0);
        check(shooting.shooter.get_state() == Shooter.State.SHOOT && shooting.shooter.prepareCalls == 0,
                "aiming preserves an active shooting request");

        Harness reset = new Harness(new Pose(30, 30, 0), Alliance.RED);
        reset.tick(true, 0, 0, 0);
        reset.two.dpad_right = true;
        reset.tick(false, 0, 0, 0);
        check(reset.drive.manual(), "pose reset cancels the stale hold target");
        close(reset.drive.pose().x(), 9);
        close(reset.drive.pose().y(), 9);

        for (Pose invalid : new Pose[]{new Pose(Double.NaN, 9, 0), new Pose(9, Double.POSITIVE_INFINITY, 0),
                new Pose(Double.NEGATIVE_INFINITY, 9, 0), new Pose(59.25, 59.58, 0)}) {
            Harness teleop = new Harness(invalid, Alliance.RED);
            teleop.tick(true, 0, 0, 0);
            check(teleop.drive.holdCalls == 0 && teleop.shooter.prepareCalls == 0,
                    "invalid pose or zero target distance cannot start aiming");
        }
        Harness prepared = new Harness(new Pose(9, 9, 0), Alliance.RED);
        field(prepared.shooter, Shooter.class, "state", Shooter.State.PREPARE);
        prepared.tick(true, 0, 0, 0);
        check(prepared.shooter.prepareCalls == 1, "A also latches preparation when state was already PREPARE");
        check_target_cache();
        check_drive_pose_faults();
        check_input_boundaries();
        System.out.println("TeleOp hive aim checks passed: " + checks);
    }

    private static void check_input_boundaries() throws Exception {
        Harness released = new Harness(new Pose(9, 9, 0), Alliance.RED);
        released.one.a = true;
        released.two.x = true;
        released.sample_gamepads();
        released.one.a = false;
        released.two.x = false;
        released.sample_gamepads();
        released.on_start();
        released.tick(false, 0, 0, 0);
        check(released.drive.holdCalls == 0 && !released.shooter.is_bypass_enabled(),
                "released INIT presses cannot leak into active controls");

        Harness held = new Harness(new Pose(9, 9, 0), Alliance.RED);
        held.one.a = true;
        held.two.x = true;
        held.two.right_bumper = true;
        held.sample_gamepads();
        held.on_start();
        held.tick(true, 0, 0, 0);
        check(held.drive.holdCalls == 1, "A held at START acts on the first active sample");
        check(!held.shooter.is_bypass_enabled(), "INIT-held configuration buttons are ignored");
        held.tick(true, 0, 0, 0);
        check(held.drive.holdCalls == 1, "seeded held A fires only once");
        held.two.x = false;
        held.two.right_bumper = false;
        held.tick(false, 0, 0, 0);
        held.two.x = true;
        held.two.right_bumper = true;
        held.tick(false, 0, 0, 0);
        check(held.shooter.is_bypass_enabled(), "released configuration controls rearm");
        close(held.shooter.get_fixed_rpm(), Shooter.STANDARD_RPM);
        held.tick(false, 0, 0, 0);
        close(held.shooter.get_fixed_rpm(), Shooter.STANDARD_RPM);
        held.two.right_bumper = false;
        held.tick(false, 0, 0, 0);
        held.two.right_bumper = true;
        held.tick(false, 0, 0, 0);
        close(held.shooter.get_fixed_rpm(), Shooter.STANDARD_RPM + 100);

        Harness sticks = new Harness(new Pose(9, 9, 0), Alliance.RED);
        sticks.tick(false, 0, -1, 0);
        close(sticks.drivetrain.powers.forward(), 1);
        sticks.tick(false, 1, 0, 0);
        close(sticks.drivetrain.powers.strafe(), -1);
        sticks.tick(false, 0, 0, 1);
        close(sticks.drivetrain.powers.turn(), -1);
    }

    private static void check_drive_pose_faults() throws Exception {
        Pose start = new Pose(9, 9, 0);
        Pose[] faults = {null,
                new Pose(Double.NaN, 9, 0), new Pose(Double.POSITIVE_INFINITY, 9, 0),
                new Pose(Double.NEGATIVE_INFINITY, 9, 0), new Pose(9, Double.NaN, 0),
                new Pose(9, Double.POSITIVE_INFINITY, 0), new Pose(9, Double.NEGATIVE_INFINITY, 0),
                new Pose(9, 9, Double.NaN), new Pose(9, 9, Double.POSITIVE_INFINITY),
                new Pose(9, 9, Double.NEGATIVE_INFINITY)};
        for (Alliance alliance : Alliance.values()) {
            for (Pose fault : faults) {
                Harness manual = new Harness(start, alliance);
                manual.tick(false, 0, -0.6f, 0);
                check(Math.abs(manual.drivetrain.powers.forward()) > 0, "manual drive moves before pose fault");
                manual.move_pose(fault);
                manual.tick(true, 0, 0, 0);
                check_stopped(manual, "known pose fault stops previous motion despite neutral sticks");
                check(manual.drive.holdCalls == 0, "a simultaneous A press cannot aim from invalid pose");
                check(!aim_flag(manual, "aimRequested") && !aim_flag(manual, "aimingAtHive"),
                        "pose fault clears pending and active aim flags");
                check_recovery(manual, start, true);

                Harness holding = new Harness(start, alliance);
                holding.tick(true, 0, 0, 0);
                holding.move_pose(new Pose(12, 11, 0.2));
                holding.tick(true, 0, 0, 0);
                check(holding.drive.holding(), "HOLD corrects a disturbed pose before fault");
                holding.move_pose(fault);
                holding.tick(true, 0, 0, 0);
                check_stopped(holding, "known pose fault cancels HOLD and explicitly stops drivetrain");
                check_recovery(holding, start, true);

                Harness fresh = new Harness(start, alliance);
                fresh.tick(false, 0, -0.6f, 0);
                fresh.localizer.pose_on_next_update(fault);
                fresh.tick(false, 0, -0.6f, 0);
                check_stopped(fresh, "new pose fault from follower.update stops output in that iteration");
                check_recovery(fresh, start, false);

                // Pinpoint always constructs a Pose; a null pose during Pedro's HOLD calculation
                // throws inside Pedro and is handled by the OpMode's ordinary finally cleanup.
                if (fault != null) {
                    Harness freshHold = new Harness(start, alliance);
                    freshHold.tick(true, 0, 0, 0);
                    freshHold.move_pose(new Pose(12, 11, 0.2));
                    freshHold.tick(true, 0, 0, 0);
                    freshHold.localizer.pose_on_next_update(fault);
                    freshHold.tick(true, 0, 0, 0);
                    check_stopped(freshHold, "new nonfinite pose during HOLD stops output in that iteration");
                    check_recovery(freshHold, start, true);
                }
            }
        }
    }

    private static void check_stopped(Harness teleop, String message) {
        check(teleop.drive.idle(), message + ": Pedro mode cleared");
        close(teleop.drivetrain.powers.forward(), 0);
        close(teleop.drivetrain.powers.strafe(), 0);
        close(teleop.drivetrain.powers.turn(), 0);
    }

    private static void check_recovery(Harness teleop, Pose recoveredPose, boolean aHeld) {
        int holdCalls = teleop.drive.holdCalls;
        teleop.move_pose(recoveredPose);
        teleop.tick(aHeld, 0, 0, 0);
        check(teleop.drive.manual() && teleop.drive.holdCalls == holdCalls,
                "neutral valid recovery stays manual and cannot revive cancelled aim");
        close(teleop.drivetrain.powers.forward(), 0);
        close(teleop.drivetrain.powers.strafe(), 0);
        close(teleop.drivetrain.powers.turn(), 0);
        teleop.tick(false, 0, 0, 0);
        teleop.tick(true, 0, 0, 0);
        check(teleop.drive.holding() && teleop.drive.holdCalls == holdCalls + 1,
                "a new A press can explicitly aim after valid recovery");
    }

    private static boolean aim_flag(Harness teleop, String name) throws Exception {
        Field flag = Main.class.getDeclaredField(name);
        flag.setAccessible(true);
        return flag.getBoolean(teleop);
    }

    private static void check_target_cache() {
        Robot robot = new Robot();
        robot.set_alliance(Alliance.RED);
        CountingPose pose = new CountingPose(9, 9, 0);
        robot.update_targeting(pose);
        check(robot.pose_valid(pose) && robot.pose_valid(pose), "drive guards reuse cached pose validity");
        check(pose.xReads == 2 && pose.yReads == 2 && pose.headingReads == 1,
                "one central pose validity check plus one coordinate read for targeting");
        Shooter.HiveDistance hive = robot.get_shooter().get_target_hive();
        robot.update_targeting(pose);
        check(pose.xReads == 2 && pose.yReads == 2 && pose.headingReads == 1,
                "cached pose causes no repeated sensor checks or coordinate reads");
        check(robot.get_shooter().get_target_hive() == hive, "unchanged pose reuses hive validity");
        robot.set_alliance(Alliance.BLUE);
        robot.update_targeting(pose);
        check(pose.headingReads == 1 && pose.xReads == 3 && pose.yReads == 3,
                "alliance change reuses pose validity");
        hive = robot.get_shooter().get_target_hive();
        check(hive.valid && hive.position.name.equals("Bottom blue"), "alliance change refreshes hive immediately");
        CountingPose rotated = new CountingPose(9, 9, 0.1);
        robot.update_targeting(rotated);
        check(rotated.headingReads == 1, "a new pose gets one validity check");
        check(robot.get_shooter().get_target_hive() == hive, "heading-only changes reuse hive validity");
        robot.update_targeting(new Pose(Double.NaN, 9, 0));
        check(!robot.get_shooter().get_target_hive().valid, "invalid pose invalidates the central hive result");
        robot.update_targeting(null);
        check(!robot.get_shooter().get_target_hive().valid, "missing pose cannot reuse a valid hive");
        robot.update_targeting(new Pose(9, 9, 0));
        check(robot.get_shooter().get_target_hive().valid, "fresh valid pose recovers targeting");
    }

    private static final class CountingPose extends Pose {
        int xReads, yReads, headingReads;
        CountingPose(double x, double y, double heading) { super(x, y, heading); }
        @Override public double x() { xReads++; return super.x(); }
        @Override public double y() { yReads++; return super.y(); }
        @Override public double heading() { headingReads++; return super.heading(); }
    }

    private static final class Harness extends Main {
        final Gamepad one = new Gamepad(), two = new Gamepad();
        final TestLocalizer localizer;
        final TestDrivetrain drivetrain = new TestDrivetrain();
        final RecordingFollower drive;
        final RecordingShooter shooter = new RecordingShooter();
        final Intake intake = new Intake();
        final Robot robot = new Robot();

        Harness(Pose pose, Alliance selectedAlliance) throws Exception {
            localizer = new TestLocalizer(pose);
            drive = new RecordingFollower(localizer, drivetrain);
            follower = drive;
            currentPose = pose;
            alliance = selectedAlliance;
            gamepad1 = new Gamepad();
            gamepad2 = new Gamepad();
            field(shooter, Shooter.class, "intakeSubsystem", intake);
            field(robot, Robot.class, "shooterSubsystem", shooter);
            robot.set_alliance(selectedAlliance);
            robot.update_targeting(currentPose);
            field(this, Main.class, "robot", robot);
            field(this, Main.class, "shooterSubsystem", shooter);
            field(this, Main.class, "intakeSubsystem", intake);
            field(this, Main.class, "flowerIntakeSubsystem", new FlowerIntake());
        }

        void move_pose(Pose pose) {
            localizer.setPose(pose);
            currentPose = pose;
            robot.update_targeting(currentPose);
        }

        void tick(boolean a, float x, float y, float turn) {
            one.a = a;
            one.left_stick_x = x;
            one.left_stick_y = y;
            one.right_stick_x = turn;
            sample_gamepads();
            update_controls();
            before_drive_update();
            drive.update(0.02);
            currentPose = drive.pose();
            after_drive_update();
            robot.update_targeting(currentPose);
        }

        void sample_gamepads() {
            gamepad1.copy(one);
            gamepad2.copy(two);
        }
    }

    private static final class RecordingShooter extends Shooter {
        int prepareCalls;
        @Override public void prepare_shot() { prepareCalls++; super.prepare_shot(); }
    }

    private static final class RecordingFollower extends Follower {
        Pose target;
        int holdCalls, poseReads;
        RecordingFollower(Localizer localizer, Drivetrain drivetrain) {
            super(localizer, drivetrain, new Foresight(Constants.foresightConfig));
        }
        @Override public void hold(Pose pose) { target = pose; holdCalls++; super.hold(pose); }
        @Override public Pose pose() { poseReads++; return super.pose(); }
    }

    private static final class TestLocalizer implements Localizer {
        MotionState state = MotionState.zero();
        Pose reportedPose;
        Pose pendingPose;
        boolean hasPendingPose;
        TestLocalizer(Pose pose) { setPose(pose); }
        void pose_on_next_update(Pose pose) { pendingPose = pose; hasPendingPose = true; }
        @Override public void setPose(Pose pose) {
            reportedPose = pose;
            // A missing reported pose tests the application boundary without constructing an invalid MotionState.
            if (pose != null) state = MotionState.zero().withPose(pose);
        }
        @Override public Pose pose() { return reportedPose; }
        @Override public MotionState state() { return state; }
        @Override public void update() {
            if (hasPendingPose) {
                setPose(pendingPose);
                hasPendingPose = false;
            }
        }
        @Override public void reset() { setPose(Pose.zero()); }
    }

    private static final class TestDrivetrain implements Drivetrain {
        DrivePowers powers = DrivePowers.zero();
        @Override public void drive(DrivePowers powers, boolean manual) { this.powers = powers; }
        @Override public double maxScaling(DrivePowers first, DrivePowers second) { return 1; }
        @Override public void stop() { powers = DrivePowers.zero(); }
        @Override public void stop(boolean manual) { stop(); }
        @Override public double interpolateVelocity(double forward, double strafe, double turn) { return 0; }
        @Override public Map<String, Object> debug() { return Collections.emptyMap(); }
    }

    private static void field(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void close_heading(double actual, double expected) {
        close(Math.atan2(Math.sin(actual - expected), Math.cos(actual - expected)), 0);
    }

    private static void close(double actual, double expected) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-9, "expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
