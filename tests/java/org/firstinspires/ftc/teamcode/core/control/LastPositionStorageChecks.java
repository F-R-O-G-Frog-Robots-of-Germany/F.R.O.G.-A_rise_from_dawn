package org.firstinspires.ftc.teamcode.core.control;

import com.pedropathing.math.Pose;
import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.core.units.Units.Time;

public final class LastPositionStorageChecks {
    private static int checks;

    public static void main(String[] args) {
        for (Alliance alliance : Alliance.values()) {
            Pose valid = new Pose(10, 20, 0.5);
            LastPositionStorage.store_data(valid, alliance);
            check(LastPositionStorage.valid_data_available(), "valid position available");
            check(LastPositionStorage.get_last_position() == valid, "latest valid pose saved");
            check(LastPositionStorage.get_current_alliance() == alliance, "valid alliance saved");

            Alliance latestAlliance = alliance == Alliance.RED ? Alliance.BLUE : Alliance.RED;
            Pose[] invalidPoses = {
                    null,
                    new Pose(Double.NaN, 50, 0),
                    new Pose(30, Double.POSITIVE_INFINITY, 0),
                    new Pose(30, 50, Double.NEGATIVE_INFINITY),
                    new Pose(30, 50, Double.NaN)
            };
            for (Pose invalid : invalidPoses) {
                LastPositionStorage.store_data(valid, alliance);
                LastPositionStorage.store_data(invalid, latestAlliance);
                check(!LastPositionStorage.valid_data_available(), "invalid latest handoff clears old availability");
                check(LastPositionStorage.get_current_alliance() == latestAlliance, "latest valid alliance remains selected");
            }

            LastPositionStorage.store_data(valid, alliance);
            LastPositionStorage.store_data(new Pose(30, 50, 0), null);
            check(!LastPositionStorage.valid_data_available(), "missing alliance invalidates latest handoff");
            check(LastPositionStorage.get_current_alliance() == alliance, "missing alliance retains valid selection");

            Pose recovered = new Pose(30, 50, 1);
            LastPositionStorage.store_data(recovered, latestAlliance);
            check(LastPositionStorage.valid_data_available(), "valid handoff recovers after rejection");
            check(LastPositionStorage.get_last_position() == recovered, "recovered latest coordinates saved");
            check(LastPositionStorage.get_current_alliance() == latestAlliance, "recovered alliance saved");
            LastPositionStorage.STORAGE_TIME = Time.ms(System.currentTimeMillis()
                    - LastPositionStorage.DATA_VALID_DURATION.ms() - 1);
            check(!LastPositionStorage.valid_data_available(), "old valid pose still expires");
        }
        System.out.println("Passed " + checks + " saved-position handoff checks");
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }
}
