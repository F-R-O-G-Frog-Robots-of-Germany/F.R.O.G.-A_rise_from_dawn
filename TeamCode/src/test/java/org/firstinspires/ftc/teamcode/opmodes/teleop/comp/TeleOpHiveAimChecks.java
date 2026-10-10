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
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.firstinspires.ftc.teamcode.Robot;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.subsystems.FlowerIntake;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Shooter;

/** Desktop control checks using the real input listeners and Pedro holding controller. */
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
        shooting.gamepad1.right_trigger = 1;
        shooting.tick(true, 0, 0, 0);
        check(shooting.shooter.get_state() == Shooter.State.SHOOT && shooting.shooter.prepareCalls == 0,
                "aiming preserves an active shooting request");

        Harness reset = new Harness(new Pose(30, 30, 0), Alliance.RED);
        reset.tick(true, 0, 0, 0);
        reset.gamepad2.dpad_right = true;
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
        System.out.println("TeleOp hive aim checks passed: " + checks);
    }

    private static void check_target_cache() {
        Robot robot = new Robot();
        robot.set_alliance(Alliance.RED);
        CountingPose pose = new CountingPose(9, 9, 0);
        robot.update_targeting(pose);
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
            Method register = Main.class.getDeclaredMethod("register_controls");
            register.setAccessible(true);
            register.invoke(this);
        }

        void move_pose(Pose pose) {
            localizer.setPose(pose);
            currentPose = pose;
            robot.update_targeting(currentPose);
        }

        void tick(boolean a, float x, float y, float turn) {
            gamepad1.a = a;
            gamepad1.left_stick_x = x;
            gamepad1.left_stick_y = y;
            gamepad1.right_stick_x = turn;
            inputManager.update(gamepad1, gamepad2);
            before_drive_update();
            drive.update(0.02);
            currentPose = drive.pose();
            robot.update_targeting(currentPose);
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
        MotionState state;
        TestLocalizer(Pose pose) { setPose(pose); }
        @Override public void setPose(Pose pose) { state = MotionState.zero().withPose(pose); }
        @Override public MotionState state() { return state; }
        @Override public void update() { }
        @Override public void reset() { state = MotionState.zero(); }
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
