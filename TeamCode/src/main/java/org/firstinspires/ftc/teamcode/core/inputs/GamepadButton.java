package org.firstinspires.ftc.teamcode.core.inputs;

public class GamepadButton {

    private final ListenerList<ButtonListener> pressListeners = new ListenerList<>();
    private final ListenerList<ButtonListener> releaseListeners = new ListenerList<>();

    boolean state = false;

    public interface ButtonListener {
        void execute();
    }

    public void add_button_press_listener(ButtonListener listener) {
        pressListeners.add(listener);
    }

    public void remove_button_press_listener(ButtonListener listener) {
        pressListeners.remove(listener);
    }

    public void clear_button_press_listeners() {
        pressListeners.clear();
    }

    public void add_button_release_listener(ButtonListener listener) {
        releaseListeners.add(listener);
    }

    public void remove_button_release_listener(ButtonListener listener) {
        releaseListeners.remove(listener);
    }

    public void clear_button_release_listeners() {
        releaseListeners.clear();
    }

    void update(boolean newState) {
        if (newState != state) {
            if (newState) {
                on_press();
            } else {
                on_release();
            }
            state = newState;
        }
    }

    void on_press() {
        for (ButtonListener listener : pressListeners.get()) {
            listener.execute();
        }
    }

    void on_release() {
        for (ButtonListener listener : releaseListeners.get()) {
            listener.execute();
        }
    }

}
