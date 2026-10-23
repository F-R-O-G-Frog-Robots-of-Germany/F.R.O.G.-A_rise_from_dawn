package org.firstinspires.ftc.teamcode.core.pedro;

import com.pedropathing.api.Paths;
import com.pedropathing.math.Pose;
import com.pedropathing.paths.Path;
import com.pedropathing.paths.curves.Line;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.AbstractAuto;
import org.firstinspires.ftc.teamcode.opmodes.auto.structure.OptimalPoses;

/** Samples production path helpers against independently transformed RED geometry. */
public final class PathSimplificationChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check_hotfixes();
        Pose[] redPoints = {
                OptimalPoses.hotfix(1.5, -2, 20).plus(new Pose(59.25, 9, Math.toRadians(355))),
                new Pose(75, 25, Math.toRadians(12)),
                new Pose(85, 70, Math.toRadians(160)),
                new Pose(48, 100, Math.toRadians(-70)),
                new Pose(48, 132, Math.toRadians(91))
        };
        for (Alliance alliance : Alliance.values()) {
            Harness auto = new Harness(alliance);
            for (int size = 3; size <= redPoints.length; size++) {
                Pose[] points = Arrays.copyOf(redPoints, size);
                Pose[] expected = field_points(points, alliance);
                same_path(auto.pickup(points), Paths.curve(expected).tangent(), alliance + " pickup " + size);
                same_path(auto.shoot(points), Paths.curve(expected).linear(expected[0], expected[size - 1]),
                        alliance + " shoot " + size);
                same_path(auto.park(points[size - 1], points),
                        Paths.curve(expected).constant(expected[size - 1]), alliance + " constant park " + size);
            }
            Pose start = redPoints[0];
            Pose end = new Pose(9, 9, Math.PI);
            Path garden = auto.garden(start, end);
            check(garden.curve instanceof Line, alliance + " garden keeps an explicit line");
            same_path(garden, Paths.line(field_pose(start, alliance), field_pose(end, alliance)).tangent(),
                    alliance + " garden");
            for (Pose point : redPoints) {
                same_pose(PoseMirroring.mirror_if_blue(point, alliance), field_pose(point, alliance),
                        alliance + " mirror");
                same_pose(PoseMirroring.mirror_across_field_center(
                        PoseMirroring.mirror_across_field_center(point)), point, alliance + " mirror twice");
            }
        }
        System.out.println("Passed " + checks + " Pedro path/pose simplification checks");
    }

    private static void check_hotfixes() {
        Pose base = new Pose(20, 30, Math.toRadians(355));
        check(OptimalPoses.hotfix(0, 0, 0).plus(base) == base, "zero hotfix preserves the original pose reference");
        for (double degrees : new double[]{20, -370, 725}) {
            Pose corrected = OptimalPoses.hotfix(1.5, -2, degrees).plus(base);
            Pose expected = OptimalPoses.FIELD_FIX
                    ? new Pose(21.5, 28, base.heading() + Math.toRadians(degrees)) : base;
            same_pose(corrected, expected, "hotfix uses inches and degrees across heading wrap");
            if (!OptimalPoses.FIELD_FIX) check(corrected == base, "disabled hotfix preserves original reference");
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                double[] values = {0, 0, 0};
                values[coordinate] = invalid;
                boolean rejected = false;
                try { OptimalPoses.hotfix(values[0], values[1], values[2]); }
                catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "hotfix rejects a nonfinite correction in coordinate " + coordinate);
            }
        }
    }

    private static Pose[] field_points(Pose[] points, Alliance alliance) {
        Pose[] result = new Pose[points.length];
        for (int i = 0; i < points.length; i++) result[i] = field_pose(points[i], alliance);
        return result;
    }

    private static Pose field_pose(Pose red, Alliance alliance) {
        return alliance == Alliance.BLUE
                ? new Pose(144 - red.x(), 144 - red.y(), red.heading() + Math.PI) : red;
    }

    private static void same_path(Path actual, Path expected, String label) {
        for (double t : new double[]{0, 0.1, 0.35, 0.5, 0.85, 1}) {
            same_pose(actual.get(t), expected.get(t), label + " t=" + t);
        }
    }

    private static void same_pose(Pose actual, Pose expected, String label) {
        double difference = actual.heading() - expected.heading();
        check(Math.abs(actual.x() - expected.x()) < 1e-9
                && Math.abs(actual.y() - expected.y()) < 1e-9
                && Math.abs(Math.atan2(Math.sin(difference), Math.cos(difference))) < 1e-9, label);
    }

    private static void check(boolean valid, String label) {
        checks++;
        if (!valid) throw new AssertionError(label);
    }

    private static final class Harness extends AbstractAuto {
        Harness(Alliance alliance) throws ReflectiveOperationException {
            Field field = AbstractAuto.class.getSuperclass().getDeclaredField("alliance");
            field.setAccessible(true);
            field.set(this, alliance);
        }

        Path pickup(Pose... points) { return tangent_path(points); }
        Path shoot(Pose... points) { return shoot_in_hive(points); }
        Path garden(Pose start, Pose end) { return tangent_line_path(start, end); }
        Path park(Pose heading, Pose... points) { return constant_heading_path(heading, points); }
        @Override protected Pose starting_pose() { return Pose.zero(); }
        @Override protected void build_autonomous() { }
    }
}
