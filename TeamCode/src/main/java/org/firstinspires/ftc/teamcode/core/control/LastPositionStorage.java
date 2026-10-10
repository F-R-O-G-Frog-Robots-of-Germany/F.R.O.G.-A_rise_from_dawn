package org.firstinspires.ftc.teamcode.core.control;

import com.pedropathing.math.Pose;

import org.firstinspires.ftc.teamcode.core.units.Units.Alliance;
import org.firstinspires.ftc.teamcode.core.units.Units.Time;
import org.firstinspires.ftc.teamcode.core.hardware.SensorReadings;


public abstract class LastPositionStorage {

    static boolean dataStored = false;
    static Time STORAGE_TIME = Time.s(0);
    final static Time DATA_VALID_DURATION = Time.s(200);

    static Pose LAST_POSITION = new Pose(0.0, 0.0, 0.0);
    static Alliance currentAlliance = Alliance.RED;

    public static void store_data(Pose POSITION, Alliance alliance) {
        if (alliance == null || !SensorReadings.is_valid(POSITION)) return;
        dataStored = true;
        LAST_POSITION = POSITION;
        currentAlliance = alliance;
        STORAGE_TIME = Time.ms(System.currentTimeMillis());
    }

    public static boolean valid_data_available() {
        return dataStored && STORAGE_TIME.ms() + DATA_VALID_DURATION.ms() > System.currentTimeMillis();
    }

    public static Pose get_last_position() {
        return LAST_POSITION;
    }

    public static Alliance get_current_alliance() {
        return currentAlliance;
    }
}
