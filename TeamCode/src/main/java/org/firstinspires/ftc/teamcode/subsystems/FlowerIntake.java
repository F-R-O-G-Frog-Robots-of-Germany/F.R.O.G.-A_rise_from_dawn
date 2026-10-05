package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

public class FlowerIntake {
    private static Servo flowerServoLeft;
    private static Servo flowerServoRight;
    private static final  double flowerServoDownPose = 0; // should be 0 in optimal case
    private static final double flowerServoUpPose = 1;
    private enum FlowerIntakePose{UP,DOWN}
    private static FlowerIntakePose flowerIntakePose = FlowerIntakePose.DOWN;

    public void init(HardwareMap hardwareMap){
        flowerServoLeft = hardwareMap.get(Servo.class, "flowerServoLeft");
        flowerServoRight = hardwareMap.get(Servo.class, "flowerServoRight");

    }
    public static void up(){
        flowerServoLeft.setPosition(flowerServoUpPose);
        flowerServoRight.setPosition(flowerServoUpPose);
        flowerIntakePose=FlowerIntakePose.UP;
    }
    public static void down(){
        flowerServoLeft.setPosition(flowerServoDownPose);
        flowerServoRight.setPosition(flowerServoDownPose);
        flowerIntakePose=FlowerIntakePose.DOWN;
    }
    public static void toggle(){
        if (flowerIntakePose==FlowerIntakePose.UP){
            down();
        } else{
            up();
        }
    }

}
