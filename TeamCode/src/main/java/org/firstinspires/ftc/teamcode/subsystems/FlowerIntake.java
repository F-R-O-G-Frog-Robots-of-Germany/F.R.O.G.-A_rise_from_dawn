package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import org.firstinspires.ftc.teamcode.core.hardware.CachedServo;

public class FlowerIntake {
    private CachedServo flowerServoLeft;
    private CachedServo flowerServoRight;
    private static final double FLOWER_SERVO_DOWN_POSE = 0;
    private static final double FLOWER_SERVO_UP_POSE = 1;
    private enum FlowerIntakePose { UP, DOWN }
    private FlowerIntakePose flowerIntakePose = FlowerIntakePose.UP;

    public void init(HardwareMap hardwareMap) {
        flowerServoLeft = new CachedServo(hardwareMap.get(Servo.class, "flowerServoLeft"), 0.005);
        flowerServoLeft.set_position(FLOWER_SERVO_UP_POSE);
        flowerServoRight = new CachedServo(hardwareMap.get(Servo.class, "flowerServoRight"), 0.005);
        up();
    }

    public void up() {
        if (flowerServoLeft != null) flowerServoLeft.set_position(FLOWER_SERVO_UP_POSE);
        if (flowerServoRight != null) flowerServoRight.set_position(FLOWER_SERVO_UP_POSE);
        flowerIntakePose = FlowerIntakePose.UP;
    }

    public void down() {
        flowerServoLeft.set_position(FLOWER_SERVO_DOWN_POSE);
        flowerServoRight.set_position(FLOWER_SERVO_DOWN_POSE);
        flowerIntakePose = FlowerIntakePose.DOWN;
    }

    public void toggle() {
        if (flowerIntakePose == FlowerIntakePose.UP) down();
        else up();
    }
}
