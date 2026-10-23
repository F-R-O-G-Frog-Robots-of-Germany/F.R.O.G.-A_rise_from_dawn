package org.firstinspires.ftc.teamcode.core.control;

import org.firstinspires.ftc.teamcode.limelight.ThreadAffinity;

/** Owns one OpMode thread's optional INIT binding and original-mask restoration. */
public final class ControlThreadAffinity {
    interface Binder {
        ThreadAffinity.Result bind(int core);
    }

    interface Restorer {
        boolean restore(ThreadAffinity.Result result);
    }

    private final Binder binder;
    private final Restorer restorer;
    private Thread owner;
    private ThreadAffinity.Result binding;
    private boolean attempted;
    private boolean restorePending;
    private boolean placementUnknown;
    private int requestedCore = -1;
    private int nativeTid = -1;
    private String status = "Not requested";

    public ControlThreadAffinity() {
        this(ThreadAffinity::bind_current_thread, ThreadAffinity::restore_current_thread);
    }

    ControlThreadAffinity(Binder binder, Restorer restorer) {
        if (binder == null || restorer == null) throw new IllegalArgumentException("Affinity backend is required");
        this.binder = binder;
        this.restorer = restorer;
    }

    /** Call once on the OpMode thread after deliberate worker/thread launches finish. */
    public void bind(int core) {
        if (attempted) {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException("Control affinity belongs to a different thread");
            }
            return;
        }
        attempted = true;
        owner = Thread.currentThread();
        requestedCore = core;
        if (core == -1) {
            status = "Android scheduler (affinity skipped)";
            return;
        }
        if (core < 0 || core > 1023) {
            status = "Invalid core; expected -1 or 0..1023; inherited affinity unchanged";
            return;
        }
        try {
            // Keep the complete result even if verification failed after a mask change.
            binding = binder.bind(core);
            if (binding == null) throw new IllegalStateException("Affinity backend returned no result");
            nativeTid = binding.tid;
            restorePending = true;
            status = binding.detail;
            if (!binding.applied) restore();
        } catch (RuntimeException | LinkageError failure) {
            placementUnknown = true;
            status = "Affinity backend failed; placement unknown; original mask unavailable: " + failure;
        }
    }

    /** Call from the same OpMode thread's outermost finally; a failed restore can be retried. */
    public boolean restore() {
        if (!attempted) return true;
        if (owner != Thread.currentThread()) return false;
        if (!restorePending) return !placementUnknown;
        boolean wasInterrupted = Thread.currentThread().isInterrupted();
        try {
            if (!restorer.restore(binding)) {
                placementUnknown = true;
                status = "Restoration failed; placement unknown; " + binding.detail;
                return false;
            }
            restorePending = false;
            placementUnknown = false;
            status = "Original affinity restored; " + binding.detail;
            return true;
        } catch (RuntimeException | LinkageError failure) {
            placementUnknown = true;
            status = "Restoration failed; placement unknown: " + failure + "; " + binding.detail;
            return false;
        } finally {
            if (wasInterrupted) Thread.currentThread().interrupt();
        }
    }

    public String detail() {
        return "Requested CPU " + requestedCore + ", TID " + nativeTid + ": " + status;
    }

    public int requested_core() { return requestedCore; }
    public int tid() { return nativeTid; }
    public boolean applied() { return restorePending && binding != null && binding.applied && !placementUnknown; }
    public boolean placement_unknown() { return placementUnknown; }
}
