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
    private final boolean[] previous = new boolean[20];
    private final Butine.Params params = new Butine.Params();
    private Butine.Plan plan;
    private boolean synthetic;
    private String comparison = "Y to compare greedy and lookahead";
    private List<ButineRunner.Leg> inspected = new ArrayList<>();

    private boolean pressed(int index, boolean down) {
        boolean edge = down && !previous[index];
        previous[index] = down;
        return edge;
    }

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
            waitForStart();
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
                if (!runner.is_busy()) {
                    if (pressed(0, gamepad2.a)) plan = Butine.solve(map.snapshot(), here, depot, params);
                    if (pressed(1, gamepad2.b)) {
                        Butine.Params uncapped = new Butine.Params(params);
                        uncapped.capacity = 0;
                        plan = Butine.solve(map.snapshot(), here, depot, uncapped);
                    }
                    if (pressed(2, gamepad2.y)) {
                        int runs = Math.max(2, params.lookaheadRuns);
                        double[] greedy = simulate(map.snapshot(), depot, 1, runs);
                        double[] ahead = simulate(map.snapshot(), depot, params.lookaheadRuns, runs);
                        comparison = "greedy " + greedy[0] + " s / " + (int) greedy[1]
                                + "; lookahead " + ahead[0] + " s / " + (int) ahead[1];
                    }
                    if (pressed(3, gamepad2.x)) { inject_synthetic(map); runner.clear_unreachable(); plan = null; }
                    if (pressed(4, gamepad2.back)) { map.clear(); synthetic = false; runner.clear_unreachable(); plan = null; }
                    if (pressed(5, gamepad2.dpad_up)) params.lookaheadRuns = Math.min(Butine.MAX_RUNS, params.lookaheadRuns + 1);
                    if (pressed(6, gamepad2.dpad_down)) params.lookaheadRuns = Math.max(1, params.lookaheadRuns - 1);
                    if (pressed(7, gamepad2.dpad_right)) params.discount = Math.min(1, params.discount + 0.05);
                    if (pressed(8, gamepad2.dpad_left)) params.discount = Math.max(0, params.discount - 0.05);
                    if (pressed(9, gamepad2.right_bumper)) params.capacity = Math.min(Butine.MAX_RUN_LEN, params.capacity + 1);
                    if (pressed(10, gamepad2.left_bumper)) params.capacity = Math.max(0, params.capacity - 1);
                    if (pressed(11, gamepad2.right_stick_button)) depot = here;
                    if (pressed(12, gamepad2.left_stick_button) && plan != null && plan.fittingRuns > 0) {
                        inspected = ButineRunner.build_run_legs(plan.runs[0], here, depot, params.kinematics);
                    }
                    if (pressed(13, gamepad2.right_trigger > 0.5)) params.remainingS += 2.5;
                    if (pressed(14, gamepad2.left_trigger > 0.5)) params.remainingS = Math.max(2.5, params.remainingS - 2.5);
                    if (requested && !followedThisHold && plan != null && plan.fittingRuns > 0) {
                        Butine.Params runParams = new Butine.Params(params);
                        if (plan.uncapacitated) runParams.capacity = 0;
                        followedThisHold = true;
                        runner.run_butine(depot, runParams, 1.0);
                    }
                } else {
                    // Observe releases while driving so a held button is not replayed after the run.
                    pressed(0, gamepad2.a); pressed(1, gamepad2.b); pressed(2, gamepad2.y);
                    pressed(3, gamepad2.x); pressed(4, gamepad2.back); pressed(5, gamepad2.dpad_up);
                    pressed(6, gamepad2.dpad_down); pressed(7, gamepad2.dpad_right); pressed(8, gamepad2.dpad_left);
                    pressed(9, gamepad2.right_bumper); pressed(10, gamepad2.left_bumper);
                    pressed(11, gamepad2.right_stick_button); pressed(12, gamepad2.left_stick_button);
                    pressed(13, gamepad2.right_trigger > 0.5); pressed(14, gamepad2.left_trigger > 0.5);
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
                for (int i = 0; i < inspected.size(); i++) {
                    ButineRunner.Leg leg = inspected.get(i);
                    predicted += leg.predictedS;
                    telemetry.addData("Built " + i, "%s -> %.1f,%.1f @ %.1f deg / %.2f s",
                            leg.returning ? "home linear" : "#" + leg.pollenId + " tangent",
                            leg.target.x, leg.target.y, Math.toDegrees(leg.target.heading), leg.predictedS);
                }
                if (!inspected.isEmpty()) telemetry.addData("Built prediction sum", predicted);
                telemetry.addLine("gp2 A solve | B uncapped | Y compare | X synth | BACK clear | RSB depot | LSB legs");
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
        local.computeBudgetMs = Math.max(0.5, params.computeBudgetMs / (runs * 2));
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
