package org.firstinspires.ftc.teamcode.limelight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hardware-free concurrency, snapshot, cancellation and deadline checks. */
public final class ButineWorkerChecks {
    private static int checks;
    private static final VisionPose START = new VisionPose(72, 72, 0);

    private static void require(boolean value, String message) {
        ++checks;
        if (!value) throw new AssertionError(message);
    }

    private static Butine.Params settings() {
        Butine.Params settings = new Butine.Params();
        settings.iterationCap = 0;
        settings.localSearchPasses = 0;
        settings.capacity = 1;
        settings.lookaheadRuns = 1;
        return settings;
    }

    private static List<Pollen> points(int id) {
        return Collections.singletonList(new Pollen(id, new VisionPose(73, 72, 0), 1, 1));
    }

    private static ButineWorker.Result await(ButineWorker worker, long id) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            ButineWorker.Result result = worker.latest_result();
            if (result != null && result.requestId == id) return result;
            Thread.sleep(1);
        }
        throw new AssertionError("Timed out waiting for request " + id);
    }

    public static void main(String[] args) throws Exception {
        ButineWorker worker = new ButineWorker();
        worker.preferredCore = -1;
        try {
            require(worker.request_plan(points(1), START, START, settings(), 1) == 0,
                    "Stopped worker declines request with sentinel zero");
            require(worker.start(), "Start creates worker");
            require(!worker.start(), "Start cannot create overlapping worker");
            Butine.Params original = settings();
            ArrayList<Pollen> mutable = new ArrayList<>(points(9));
            long copiedId = worker.request_plan(mutable, START, START, original, 17);
            original.remainingS = 0;
            original.kinematics.cruiseSpeedInS = Double.NaN;
            mutable.clear();
            ButineWorker.Result copied = await(worker, copiedId);
            require(copied.error == null && copied.plan.expectedCollected == 1,
                    "Request copies list and nested mutable settings before publication");
            require(copied.plan.runs[0].ids[0] == 9 && copied.mapVersion == 17,
                    "Copied identity and map version survive asynchronous solve");
            require(copied.remainingS <= 30 && copied.remainingS >= 0,
                    "Remaining budget deducts queue and solve time");
            double expected = Math.max(0, 30 - (copied.completedNanos - copied.submittedNanos) / 1e9);
            require(Math.abs(copied.remainingS - expected) < 1e-9, "Publication budget matches elapsed clock");
            require(copied.remaining_s() <= copied.remainingS,
                    "Consumer waiting time also reduces available budget");
            require(copied.executable(START, 17, 0, 0), "Fresh first run can execute");
            require(!copied.executable(START, 18, 0, 0), "Changed map version rejects result");
            require(!copied.executable(new VisionPose(74,72,0), 17, 1, 0), "Moved robot rejects result");
            require(!copied.executable(new VisionPose(72,72,1), 17, 0, .5), "Changed heading rejects result");
            require(!copied.executable(START, 17, Double.NaN, 0), "Nonfinite tolerance rejects result");
            require(!copied.executable(null, 17, 0, 0), "Unavailable robot pose rejects result");
            worker.cancel_pending();
            require(worker.latest_result() == null && worker.is_running(), "Cancel leaves an idle worker alive");
            require(!copied.executable(START, 17, 0, 0), "Retained cancelled result cannot execute");

            long newest = 0;
            for (int i = 1; i <= 100; ++i)
                newest = worker.request_plan(points(i), START, START, settings(), i);
            ButineWorker.Result latest = await(worker, newest);
            require(latest.plan.runs[0].ids[0] == 100 && latest.mapVersion == 100,
                    "Bounded latest-request mailbox publishes the newest input");
            require(!copied.executable(START, 17, 0, 0), "Superseded result stays invalid");

            Butine.Params invalid = settings();
            invalid.kinematics.omegaMaxRadS = Double.NaN;
            ButineWorker.Result failed = await(worker,
                    worker.request_plan(points(1), START, START, invalid, 1));
            require(failed.error != null && failed.plan == null, "Solve exception is published as diagnostic data");
            require(!failed.executable(START, 1, 0, 0), "Failed solve cannot execute");
            ButineWorker.Result recovered = await(worker,
                    worker.request_plan(points(1), START, START, settings(), 1));
            require(recovered.error == null && recovered.executable(START, 1, 0, 0),
                    "Next request recovers after an invalid solve");

            Butine.Params expiredSettings = settings();
            expiredSettings.remainingS = 0.000001;
            ButineWorker.Result expired = await(worker,
                    worker.request_plan(points(1), START, START, expiredSettings, 1));
            require(!expired.executable(START, 1, 0, 0), "Deadline-expired result cannot execute");
            require(expired.remaining_s() == 0, "Expired deadline reports zero budget");
            require(worker.stop(), "Idle worker stops within bounded join");
            require(!worker.is_running() && worker.latest_result() == null,
                    "Stopped worker clears published output");
            require(!recovered.executable(START, 1, 0, 0), "Stop invalidates held prior result");
            require(worker.start(), "Worker can restart after previous thread fully exits");
            require(!recovered.executable(START, 1, 0, 0), "Restart does not resurrect old generation");
            ButineWorker.Result restarted = await(worker,
                    worker.request_plan(points(4), START, START, settings(), 4));
            require(restarted.plan.runs[0].ids[0] == 4, "Restart solves new work");
            require(worker.stats().completed >= 6, "Statistics count completed attempts");
        } finally { worker.stop(); }
        ButineWorker invalidCore = new ButineWorker();
        invalidCore.preferredCore = -2;
        try { invalidCore.start(); throw new AssertionError("Invalid core accepted"); }
        catch (IllegalArgumentException expected) { require(!invalidCore.is_running(), "Invalid core never starts worker"); }
        System.out.println("Butine worker checks passed: " + checks);
    }
}
