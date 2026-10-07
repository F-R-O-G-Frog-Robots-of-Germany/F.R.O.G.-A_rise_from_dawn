package org.firstinspires.ftc.teamcode.core.inputs;

public class Gamepad {

    public GamepadAnalogStick leftStick = new GamepadAnalogStick();
    public GamepadAnalogStick rightStick = new GamepadAnalogStick();

    public GamepadAnalogSticks bothSticks = new GamepadAnalogSticks();

    public GamepadButton leftStickButton = new GamepadButton();
    public GamepadButton rightStickButton = new GamepadButton();

    public GamepadTrigger leftTrigger = new GamepadTrigger();
    public GamepadTrigger rightTrigger = new GamepadTrigger();

    public GamepadButton leftBumper = new GamepadButton();
    public GamepadButton rightBumper = new GamepadButton();

    public GamepadButton a = new GamepadButton();
    public GamepadButton b = new GamepadButton();
    public GamepadButton x = new GamepadButton();
    public GamepadButton y = new GamepadButton();

    public GamepadButton dpadUp = new GamepadButton();
    public GamepadButton dpadRight = new GamepadButton();
    public GamepadButton dpadDown = new GamepadButton();
    public GamepadButton dpadLeft = new GamepadButton();

    void update(com.qualcomm.robotcore.hardware.Gamepad gamepad) {
        leftStick.update(gamepad.left_stick_x, gamepad.left_stick_y);
        rightStick.update(gamepad.right_stick_x, gamepad.right_stick_y);

        bothSticks.update(gamepad.left_stick_x, gamepad.left_stick_y, gamepad.right_stick_x, gamepad.right_stick_y);

        leftStickButton.update(gamepad.left_stick_button);
        rightStickButton.update(gamepad.right_stick_button);

        leftTrigger.update(gamepad.left_trigger);
        rightTrigger.update(gamepad.right_trigger);

        leftBumper.update(gamepad.left_bumper);
        rightBumper.update(gamepad.right_bumper);

        a.update(gamepad.a);
        b.update(gamepad.b);
        x.update(gamepad.x);
        y.update(gamepad.y);

        dpadUp.update(gamepad.dpad_up);
        dpadRight.update(gamepad.dpad_right);
        dpadDown.update(gamepad.dpad_down);
        dpadLeft.update(gamepad.dpad_left);
    }

}
