package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * On-demand planner thread. Submit snapshots from the OpMode thread; drive only there too.
 * There is one pending request: a newer request replaces it and invalidates older results.
 * A running solve is finite but cannot be interrupted inside Butine, so stop never waits
 * indefinitely and start refuses to overlap a still-finishing solver.
 */
public final class ButineWorker implements AutoCloseable {
    /** Android CPU index, not a reservation. -1 leaves placement to Android. Set before start. */
    public int preferredCore = 2;
    private final Object lock = new Object();
    private Thread thread;
    private volatile boolean running;
    private Request pending;
    private Result latest;
    private long requestId;
    private long completed;
    private long discarded;
    private volatile ThreadAffinity.Result affinity;
    private volatile double lastSolveMs;
    private volatile double maxSolveMs;

    private static final class Request {
        final long id, mapVersion, submittedNanos;
        final List<Pollen> pollen;
        final VisionPose start, depot;
        final Butine.Params params;

        Request(long id, long mapVersion, List<Pollen> pollen, VisionPose start,
                VisionPose depot, Butine.Params params) {
            this.id = id;
            this.mapVersion = mapVersion;
            submittedNanos = System.nanoTime();
            this.pollen = pollen;
            this.start = start;
            this.depot = depot;
            this.params = params;
        }
    }

    /** A completed snapshot; check freshness immediately before handing its first run to a drive. */
    public static final class Result {
        public final long requestId, mapVersion, submittedNanos, completedNanos;
        public final VisionPose start, depot;
        public final Butine.Plan plan;
        public final String error;
        public final double solveMs;
        /** Budget left at publication, after both queueing and solving. */
        public final double remainingS;
        private final double requestedRemainingS, scoreOverheadS;
        private final ButineWorker owner;

        private Result(ButineWorker owner, Request request, Butine.Plan plan, String error, long beganNanos,
                       long completedNanos) {
            this.owner = owner;
            requestId = request.id;
            mapVersion = request.mapVersion;
            submittedNanos = request.submittedNanos;
            this.completedNanos = completedNanos;
            start = request.start;
            depot = request.depot;
            this.plan = plan;
            this.error = error;
            solveMs = Math.max(0, (completedNanos - beganNanos) / 1e6);
            requestedRemainingS = request.params.remainingS;
            scoreOverheadS = Math.max(0, request.params.scoreOverheadS);
            remainingS = remaining_at(completedNanos);
        }

        /** Remaining budget continues to decrease while the result waits for its caller. */
        public double remaining_s() { return remaining_at(System.nanoTime()); }

        private double remaining_at(long now) {
            return Math.max(0, requestedRemainingS - Math.max(0, (now - submittedNanos) / 1e9));
        }

        /**
         * A changed map version or moved robot requires a fresh request. Choose tolerances
         * for your drive; matching a version does not prove the camera map is recent.
         * Budget validation includes the first pickup run, return and scoring overhead.
         */
        public boolean executable(VisionPose currentStart, long currentMapVersion,
                                  double translationToleranceIn, double headingToleranceRad) {
            if (!owner.is_current(requestId) || error != null || plan == null
                    || plan.fittingRuns == 0 || plan.runCount == 0
                    || currentStart == null || !currentStart.is_finite()
                    || !Double.isFinite(translationToleranceIn) || translationToleranceIn < 0
                    || !Double.isFinite(headingToleranceRad) || headingToleranceRad < 0
                    || currentMapVersion != mapVersion) return false;
            if (start.distance_to(currentStart) > translationToleranceIn
                    || Math.abs(VisionPose.wrap_pi(VisionPose.wrap_pi(currentStart.heading)
                    - VisionPose.wrap_pi(start.heading)))
                    > headingToleranceRad) return false;
            Butine.Run run = plan.runs[0];
            return run.fits && run.count > 0 && Double.isFinite(run.timeS)
                    && run.timeS + scoreOverheadS <= remaining_s();
        }
    }

    public static final class Stats {
        public final boolean running;
        public final int tid, cpuCore;
        public final boolean affinityApplied;
        public final String affinityDetail;
        public final long latestRequestId, completed, discarded;
        public final double lastSolveMs, maxSolveMs;
        private Stats(ButineWorker worker) {
            running = worker.running;
            ThreadAffinity.Result bound = worker.affinity;
            tid = bound == null ? -1 : bound.tid;
            cpuCore = bound != null && bound.applied ? bound.core : -1;
            affinityApplied = bound != null && bound.applied;
            affinityDetail = bound == null ? "not started" : bound.detail;
            latestRequestId = worker.requestId;
            completed = worker.completed;
            discarded = worker.discarded;
            lastSolveMs = worker.lastSolveMs;
            maxSolveMs = worker.maxSolveMs;
        }
    }

    public boolean start() {
        synchronized (lock) {
            if (thread != null && thread.isAlive()) return false;
            if (preferredCore < -1 || preferredCore > 1023)
                throw new IllegalArgumentException("CPU index must be -1 or 0..1023");
            running = true;
            ++requestId;
            pending = null;
            latest = null;
            affinity = null;
            try {
                thread = new Thread(this::work, "FROG-Butine");
                thread.setDaemon(true);
                thread.start();
            }
            catch (RuntimeException | Error failure) { running = false; thread = null; throw failure; }
            return true;
        }
    }

    /** Copy all mutable inputs now. Returns 0 when stopped; mapVersion is the caller's map-change counter. */
    public long request_plan(List<Pollen> pollen, VisionPose start, VisionPose depot,
                             Butine.Params params, long mapVersion) {
        Objects.requireNonNull(pollen, "pollen");
        Objects.requireNonNull(params, "params");
        if (start == null || !start.is_finite() || depot == null || !depot.is_finite())
            throw new IllegalArgumentException("Finite start and depot required");
        ArrayList<Pollen> copied = new ArrayList<>(pollen.size());
        for (Pollen point : pollen) {
            if (point == null) { copied.add(null); continue; }
            VisionPose position = point.position;
            copied.add(new Pollen(point.id, position == null ? null
                    : new VisionPose(position.x, position.y, position.heading),
                    point.confidence, point.area, point.lastDetectedMs));
        }
        Butine.Params settings = new Butine.Params(params);
        synchronized (lock) {
            if (!running) return 0;
            long next = ++requestId;
            pending = new Request(next, mapVersion, copied,
                    new VisionPose(start.x, start.y, start.heading),
                    new VisionPose(depot.x, depot.y, depot.heading), settings);
            latest = null;
            lock.notifyAll();
            return next;
        }
    }

    public Result latest_result() { synchronized (lock) { return latest; } }
    public Stats stats() { synchronized (lock) { return new Stats(this); } }
    public boolean is_running() { return running; }
    private boolean is_current(long id) { synchronized (lock) { return running && id == requestId; } }

    /** Invalidate the current work and result, retaining the sleeping worker for another request. */
    public void cancel_pending() {
        synchronized (lock) {
            ++requestId;
            pending = null;
            latest = null;
            lock.notifyAll();
        }
    }

    /** Nonblocking cancellation invalidates pending and already-published results. */
    public void request_stop() {
        synchronized (lock) {
            cancel_locked();
        }
    }

    private void cancel_locked() {
        running = false;
        ++requestId;
        pending = null;
        latest = null;
        lock.notifyAll();
        if (thread != null) thread.interrupt();
    }

    /** Returns false if a finite in-progress solve still needs to finish; no replacement is started. */
    public boolean stop() {
        Thread stopping;
        synchronized (lock) { stopping = thread; cancel_locked(); }
        if (stopping == null) return true;
        if (stopping == Thread.currentThread()) return false;
        try { stopping.join(250); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        return !stopping.isAlive();
    }

    @Override public void close() { stop(); }

    private void work() {
        ThreadAffinity.Result bound = null;
        try {
            bound = ThreadAffinity.bind_current_thread(preferredCore);
            affinity = bound;
            while (true) {
                Request request;
                synchronized (lock) {
                    while (running && pending == null) lock.wait();
                    if (!running) return;
                    request = pending;
                    pending = null;
                }
                long began = System.nanoTime();
                Butine.Plan plan = null;
                String error = null;
                try {
                    Butine.Params settings = new Butine.Params(request.params);
                    settings.remainingS = Math.max(0, settings.remainingS
                            - Math.max(0, (began - request.submittedNanos) / 1e9));
                    plan = Butine.solve(request.pollen, request.start, request.depot, settings);
                } catch (RuntimeException failure) {
                    error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                }
                long ended = System.nanoTime();
                lastSolveMs = Math.max(0, (ended - began) / 1e6);
                maxSolveMs = Math.max(maxSolveMs, lastSolveMs);
                synchronized (lock) {
                    ++completed;
                    if (running && request.id == requestId) {
                        latest = new Result(this, request, plan, error, began, ended);
                    } else ++discarded;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            try { if (bound != null) ThreadAffinity.restore_current_thread(bound); }
            finally { synchronized (lock) { running = false; lock.notifyAll(); } }
        }
    }
}
