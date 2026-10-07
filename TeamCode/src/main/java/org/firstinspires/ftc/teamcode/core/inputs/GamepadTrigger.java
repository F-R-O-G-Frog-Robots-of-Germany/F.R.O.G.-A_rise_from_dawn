package org.firstinspires.ftc.teamcode.core.inputs;

public class GamepadTrigger {

    private static final double FULL_PRESS_THRESHOLD = 0.95;
    private static final double RELEASE_THRESHOLD = 0.05;

    private final ListenerList<TriggerListener> fullPressListeners = new ListenerList<>();
    private final ListenerList<TriggerListener> releaseListeners = new ListenerList<>();
    private final ListenerList<TriggerListener> pressListeners = new ListenerList<>();

    double value;
    private boolean fullyPressed;
    private boolean pressed;

    public interface TriggerListener {
        void execute(double value);
    }

    public void add_full_press_listener(TriggerListener listener) {
        fullPressListeners.add(listener);
    }

    public void remove_full_press_listener(TriggerListener listener) {
        fullPressListeners.remove(listener);
    }

    public void clear_full_press_listeners() {
        fullPressListeners.clear();
    }

    public void add_press_listener(TriggerListener listener) {
        pressListeners.add(listener);
    }

    public void remove_press_listener(TriggerListener listener) {
        pressListeners.remove(listener);
    }

    public void clear_press_listeners() {
        pressListeners.clear();
    }

    public void add_trigger_release_listener(TriggerListener listener) {
        releaseListeners.add(listener);
    }

    public void remove_trigger_release_listener(TriggerListener listener) {
        releaseListeners.remove(listener);
    }

    public void clear_trigger_release_listeners() {
        releaseListeners.clear();
    }

    void update(double newValue) {
        if (newValue == value) {
            return;
        }

        if (newValue <= RELEASE_THRESHOLD) {
            boolean wasPressed = pressed;
            pressed = false;
            fullyPressed = false;
            if (wasPressed) on_release(newValue);
        } else if (!pressed) {
            pressed = true;
            on_press(newValue);
        }

        if (pressed && !fullyPressed && newValue >= FULL_PRESS_THRESHOLD) {
            fullyPressed = true;
            on_full_press();
        }

        value = newValue;
    }


    void on_full_press() {
        for (TriggerListener listener : fullPressListeners.get()) {
            listener.execute(1);
        }
    }

    void on_press(double value) {
        for (TriggerListener listener : pressListeners.get()) {
            listener.execute(value);
        }
    }

    // Übergibt jetzt den echten Wert statt hartcodierter 0.
    void on_release(double value) {
        for (TriggerListener listener : releaseListeners.get()) {
            listener.execute(value);
        }
    }

}
