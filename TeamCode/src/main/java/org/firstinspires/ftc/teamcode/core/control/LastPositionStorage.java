package org.firstinspires.ftc.teamcode.core.control;

import com.pedropathing.math.Pose;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;


public abstract class LastPositionStorage {

    static boolean dataStored = false;
    private static final ElapsedTime storageAge = new ElapsedTime();
    private static final double DATA_VALID_SECONDS = 200;

    static Pose LAST_POSITION = new Pose(0.0, 0.0, 0.0);
    static Alliance currentAlliance = Alliance.RED;

    public static void store_data(Pose POSITION, Alliance alliance) {
        if (alliance != null) currentAlliance = alliance;
        if (alliance == null || !SensorReadings.is_valid(POSITION)) {
            dataStored = false;
            return;
        }
        dataStored = true;
        LAST_POSITION = POSITION;
        storageAge.reset();
    }

    public static boolean valid_data_available() {
        return dataStored && storageAge.seconds() < DATA_VALID_SECONDS;
    }

    public static Pose get_last_position() {
        return LAST_POSITION;
    }

    public static Alliance get_current_alliance() {
        return currentAlliance;
    }
}
