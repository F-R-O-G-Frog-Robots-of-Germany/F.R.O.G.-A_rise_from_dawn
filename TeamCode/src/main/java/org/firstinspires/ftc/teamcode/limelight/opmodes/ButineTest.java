package org.firstinspires.ftc.teamcode.limelight.opmodes;

import java.util.ArrayList;
import java.util.List;
import com.pedropathing.follower.Follower;
import com.pedropathing.math.Pose;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import org.firstinspires.ftc.teamcode.core.pedro.Constants;
import org.firstinspires.ftc.teamcode.limelight.Butine;
import org.firstinspires.ftc.teamcode.limelight.ButineRunner;
import org.firstinspires.ftc.teamcode.limelight.Limelight;
import org.firstinspires.ftc.teamcode.limelight.PedroButineDrive;
import org.firstinspires.ftc.teamcode.limelight.Pollen;
import org.firstinspires.ftc.teamcode.limelight.PollenMap;
import org.firstinspires.ftc.teamcode.limelight.VisionPose;

/** Synthetic/vision planner comparison and held-trigger drive calibration; no shooter commands. */
@Disabled
@TeleOp(name = "Butine planner test", group = "Limelight")
public final class ButineTest extends LinearOpMode {
    private final Butine.Params params = new Butine.Params();
    private Butine.Plan plan;
    private boolean synthetic;
    private String comparison = "Y to compare greedy and lookahead";
    private List<ButineRunner.Leg> inspected = new ArrayList<>();

    @Override public void runOpMode() throws InterruptedException {
        Follower follower = null;
        Limelight camera = new Limelight(hardwareMap);
        PollenMap map = new PollenMap(camera);
        ButineRunner runner = null;
        try {
            for (LynxModule hub : hardwareMap.getAll(LynxModule.class)) {
                hub.setBulkCachingMode(LynxModule.BulkCachingMode.AUTO);
            }
            follower = Constants.create_follower(hardwareMap);
            follower.setPose(new Pose(72, 72, 0));
            PedroButineDrive drive = new PedroButineDrive(follower);
            runner = new ButineRunner(map, drive);
            camera.init();
            map.init();
            VisionPose depot = drive.pose();
            telemetry.setMsTransmissionInterval(100);
            telemetry.addLine("Bench start pose 72,72,0: correct before driving. This test drives; intake is caller-owned.");
            telemetry.update();
            gamepad2.setTriggerThreshold(Math.nextUp(0.5f));
            waitForStart();
            gamepad2.resetEdgeDetection();
            gamepad2.copy(gamepad2);
            boolean followedThisHold = false;
            long statusNanos = 0;
            while (opModeIsActive()) {
                boolean requested = gamepad1.right_trigger > 0.5;
                if (!requested) {
                    if (runner.is_busy()) runner.stop();
                    followedThisHold = false;
                }
                if (!runner.is_busy()) {
                    follower.manual(-gamepad1.left_stick_y * 0.5, -gamepad1.left_stick_x * 0.5,
                            -gamepad1.right_stick_x * 0.5);
                }
                follower.update();
                VisionPose here = drive.pose();
                camera.update(here);
                if (System.nanoTime() - statusNanos >= 250_000_000L) {
                    camera.update_full(here);
                    statusNanos = System.nanoTime();
                }
                if (!synthetic) map.update();
                runner.update();
                // Consume controls while driving too, so they cannot replay after the run.
                boolean solve = gamepad2.aWasPressed();
                boolean solveFullLoad = gamepad2.bWasPressed();
                boolean compare = gamepad2.yWasPressed();
                boolean inject = gamepad2.xWasPressed();
                boolean clear = gamepad2.backWasPressed();
                boolean moreLookahead = gamepad2.dpadUpWasPressed();
                boolean lessLookahead = gamepad2.dpadDownWasPressed();
                boolean moreDiscount = gamepad2.dpadRightWasPressed();
                boolean lessDiscount = gamepad2.dpadLeftWasPressed();
                boolean moreCapacity = gamepad2.rightBumperWasPressed();
                boolean lessCapacity = gamepad2.leftBumperWasPressed();
                boolean setDepot = gamepad2.rightStickButtonWasPressed();
                boolean inspect = gamepad2.leftStickButtonWasPressed();
                boolean moreTime = gamepad2.rightTriggerWasPressed();
                boolean lessTime = gamepad2.leftTriggerWasPressed();
                if (!runner.is_busy()) {
                    if (solve) plan = Butine.solve(map.snapshot(), here, depot, params);
                    if (solveFullLoad) {
                        params.capacity = Butine.POSSESSION_LIMIT;
                        plan = Butine.solve(map.snapshot(), here, depot, params);
                    }
                    if (compare) {
                        int runs = Math.max(2, params.lookaheadRuns);
                        double[] greedy = simulate(map.snapshot(), depot, 1, runs);
                        double[] ahead = simulate(map.snapshot(), depot, params.lookaheadRuns, runs);
                        comparison = "greedy " + greedy[0] + " s / " + (int) greedy[1]
                                + "; lookahead " + ahead[0] + " s / " + (int) ahead[1];
                    }
                    if (inject) { inject_synthetic(map); runner.clear_unreachable(); plan = null; }
                    if (clear) { map.clear(); synthetic = false; runner.clear_unreachable(); plan = null; }
                    if (moreLookahead) params.lookaheadRuns = Math.min(Butine.MAX_RUNS, params.lookaheadRuns + 1);
                    if (lessLookahead) params.lookaheadRuns = Math.max(1, params.lookaheadRuns - 1);
                    if (moreDiscount) params.discount = Math.min(1, params.discount + 0.05);
                    if (lessDiscount) params.discount = Math.max(0, params.discount - 0.05);
                    if (moreCapacity) params.capacity = Math.min(Butine.POSSESSION_LIMIT, params.capacity + 1);
                    if (lessCapacity) params.capacity = Math.max(1, params.capacity - 1);
                    if (setDepot) depot = here;
                    if (inspect && plan != null && plan.fittingRuns > 0) {
                        try {
                            inspected = ButineRunner.build_run_legs(plan.runs[0], here, depot,
                                    params.kinematics, plan.field);
                        } catch (IllegalArgumentException blockedRoute) {
                            inspected = new ArrayList<>();
                            comparison = "Inspection unavailable: " + blockedRoute.getMessage();
                        }
                    }
                    if (moreTime) params.remainingS += 2.5;
                    if (lessTime) params.remainingS = Math.max(2.5, params.remainingS - 2.5);
                    if (requested && !followedThisHold && plan != null && plan.fittingRuns > 0) {
                        Butine.Params runParams = new Butine.Params(params);
                        followedThisHold = true;
                        runner.run_butine(depot, runParams, 1.0);
                    }
                }
                camera.telemetry(telemetry);
                runner.telemetry(telemetry);
                telemetry.addData("Map", "%d tracks (%s)", map.tracks().size(), synthetic ? "synthetic frozen" : "camera");
                telemetry.addData("Params", "capacity %d / lookahead %d / discount %.2f / %.1f s",
                        params.capacity, params.lookaheadRuns, params.discount, params.remainingS);
                telemetry.addData("Comparison", comparison);
                if (plan != null) {
                    telemetry.addData("Plan", "%d pollen, %d fitting runs, %.2f s, %.0f us solve",
                            plan.expectedCollected, plan.fittingRuns, plan.committedTimeS, plan.solveUs);
                    for (int r = 0; r < plan.runCount; r++) {
                        telemetry.addData("Run " + r, java.util.Arrays.toString(plan.runs[r].ids)
                                + " " + plan.runs[r].timeS + " s");
                    }
                }
                double predicted = 0;
                telemetry.addData("Intake geometry", "%.1f in wide, %.1f in ahead, %.1f in overshoot",
                        params.intakeWidthIn, params.intakeForwardOffsetIn, params.intakeOvershootIn);
                for (int i = 0; i < inspected.size(); i++) {
                    ButineRunner.Leg leg = inspected.get(i);
                    predicted += leg.predictedS;
                    telemetry.addData("Built " + i, "%s -> %.1f,%.1f @ %.1f deg / %.2f s",
                            leg.returning ? "home linear" : leg.constantHeading ? "intake constant"
                                    : leg.intakeActive ? "intake transit" : "intake off",
                            leg.target.x, leg.target.y, Math.toDegrees(leg.target.heading), leg.predictedS);
                }
                if (!inspected.isEmpty()) telemetry.addData("Built prediction sum", predicted);
                telemetry.addLine("gp2 A solve | B full load (4) | Y compare | X synth | BACK clear | RSB depot | LSB legs");
                telemetry.addLine("dpad UD lookahead LR discount | bumpers capacity | triggers time | gp1 RT hold drive");
                telemetry.update();
                idle();
            }
        } finally {
            try { if (runner != null) runner.stop(); }
            finally {
                try { camera.stop(); }
                finally { if (follower != null) {
                    try { follower.stop(); } finally { follower.drivetrain.stop(); }
                } }
            }
        }
    }

    private double[] simulate(List<Pollen> observations, VisionPose depot, int lookahead, int runs) {
        ArrayList<Pollen> remaining = new ArrayList<>(observations);
        Butine.Params local = new Butine.Params(params);
        local.lookaheadRuns = lookahead;
        double total = 0;
        int collected = 0;
        for (int r = 0; r < runs && !remaining.isEmpty(); r++) {
            local.remainingS = Math.max(0, params.remainingS - total);
            Butine.Plan step = Butine.solve(remaining, depot, local);
            if (step.fittingRuns == 0 || step.runs[0].count == 0) break;
            Butine.Run run = step.runs[0];
            total += run.timeS + local.scoreOverheadS;
            collected += run.count;
            for (int id : run.ids) for (int i = remaining.size() - 1; i >= 0; i--) {
                if (remaining.get(i).id == id) remaining.remove(i);
            }
        }
        return new double[]{total, collected};
    }

    private void inject_synthetic(PollenMap map) {
        map.clear();
        double[][] positions = {{38,36}, {44,41}, {35,45}, {47,33}, {108,112}, {114,106}, {103,104},
                {116,117}, {72,22}, {22,100}, {130,62}, {62,130}, {88,70}, {56,84}};
        for (double[] position : positions) map.inject(new VisionPose(position[0], position[1], 0), 1, 1);
        synthetic = true;
        inspected.clear();
        comparison = "Y to compare this synthetic field";
    }
}
