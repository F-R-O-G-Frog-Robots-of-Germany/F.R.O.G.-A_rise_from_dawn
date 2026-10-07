package org.firstinspires.ftc.teamcode.core.inputs;

import org.firstinspires.ftc.teamcode.core.inputs.GamepadAnalogStick;

public class GamepadAnalogSticks {

    private final ListenerList<UpdateListener> listeners = new ListenerList<>();

    public interface UpdateListener {
        void execute(double leftX, double leftY, double rightX, double rightY);
    }

    public void add_update_listener(UpdateListener listener) {
        listeners.add(listener);
    }

    public void remove_update_listener(UpdateListener listener) {
        listeners.remove(listener);
    }

    public void clear_update_listener_list() {
        listeners.clear();
    }

    void update(double leftX, double leftY, double rightX, double rightY) {
        for (UpdateListener listener : listeners.get()) {
            listener.execute(leftX, GamepadAnalogStick.invert_y(leftY), rightX, GamepadAnalogStick.invert_y(rightY));
        }
    }

}
