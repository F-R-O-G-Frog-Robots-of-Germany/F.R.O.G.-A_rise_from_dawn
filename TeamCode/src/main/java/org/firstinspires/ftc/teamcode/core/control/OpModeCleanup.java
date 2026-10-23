package org.firstinspires.ftc.teamcode.core.control;

/** Attempts every shutdown step, retaining the original failure and subsequent diagnostics. */
public final class OpModeCleanup {
    private OpModeCleanup() { }

    public static void run(Throwable primary, Runnable... steps) {
        Throwable failure = primary;
        for (Runnable step : steps) {
            try { step.run(); }
            catch (RuntimeException | Error next) {
                if (failure == null) failure = next;
                else if (next != failure) failure.addSuppressed(next);
            }
        }
        if (primary == null) {
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
        }
    }
}
