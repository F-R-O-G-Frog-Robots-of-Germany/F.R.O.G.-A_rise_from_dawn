package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

public class FlowerIntake {
    private Servo flowerServoLeft;
    private Servo flowerServoRight;
    private static final double FLOWER_SERVO_DOWN_POSE = 0;
    private static final double FLOWER_SERVO_UP_POSE = 1;
    private enum FlowerIntakePose { UP, DOWN }
    private FlowerIntakePose flowerIntakePose = FlowerIntakePose.UP;

    public void init(HardwareMap hardwareMap) {
        flowerServoLeft = hardwareMap.get(Servo.class, "flowerServoLeft");
        flowerServoLeft.setPosition(FLOWER_SERVO_UP_POSE);
        flowerServoRight = hardwareMap.get(Servo.class, "flowerServoRight");
        up();
    }

    public void up() {
        if (flowerServoLeft != null) flowerServoLeft.setPosition(FLOWER_SERVO_UP_POSE);
        if (flowerServoRight != null) flowerServoRight.setPosition(FLOWER_SERVO_UP_POSE);
        flowerIntakePose = FlowerIntakePose.UP;
    }

    public void down() {
        flowerServoLeft.setPosition(FLOWER_SERVO_DOWN_POSE);
        flowerServoRight.setPosition(FLOWER_SERVO_DOWN_POSE);
        flowerIntakePose = FlowerIntakePose.DOWN;
    }

    public void toggle() {
        if (flowerIntakePose == FlowerIntakePose.UP) down();
        else up();
    }
}
