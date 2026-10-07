package org.firstinspires.ftc.teamcode.core.inputs;

public class GamepadAnalogStick {

    private final org.firstinspires.ftc.teamcode.core.inputs.ListenerList<UpdateListener> listeners
            = new org.firstinspires.ftc.teamcode.core.inputs.ListenerList<>();

    public interface UpdateListener {
        void execute(double x, double y);
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

    // Gamepad-y ist invertiert; "nach oben" soll positiv sein.
    static double invert_y(double y) {
        return -y;
    }

    void update(double x, double y) {
        for (UpdateListener listener : listeners.get()) {
            listener.execute(x, invert_y(y));
        }
    }

}
