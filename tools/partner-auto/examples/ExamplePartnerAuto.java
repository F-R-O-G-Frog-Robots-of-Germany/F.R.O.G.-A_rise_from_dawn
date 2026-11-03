package example;

import com.pedropathing.api.Paths;
import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;

/** Import example only; this file is outside the deployed TeamCode source tree. */
@Autonomous(name = "Partner import example")
public class ExamplePartnerAuto extends AbstractAuto {
    private static final Pose START = new Pose(20, 20, 0);
    private static final Pose DEPOT = new Pose(50, 40, Math.PI / 2);

    @Override
    protected Pose starting_pose() {
        return START;
    }

    @Override
    protected void build_autonomous() {
        queue_path(Paths.curve(START, new Pose(40, 20, 0), DEPOT)
                .linear(START.heading(), DEPOT.heading()));
        queue_shot(1.25);
        queue_path(Paths.line(DEPOT, START).linear(DEPOT.heading(), START.heading()));
    }
}
