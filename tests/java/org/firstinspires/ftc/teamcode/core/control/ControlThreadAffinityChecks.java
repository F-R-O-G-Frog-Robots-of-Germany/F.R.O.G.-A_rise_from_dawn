package org.firstinspires.ftc.teamcode.core.control;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.firstinspires.ftc.teamcode.limelight.ThreadAffinity;

/** Desktop lifecycle checks using synthetic affinity results; no affinity commands run. */
public final class ControlThreadAffinityChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        scheduler_and_invalid_checks();
        verified_checks();
        partial_failure_checks();
        backend_failure_checks();
        thread_owner_checks();
        System.out.println("ControlThreadAffinityChecks: " + checks + " checks passed");
    }

    private static void scheduler_and_invalid_checks() throws Exception {
        for (int core : new int[] { -1, -2, 1024, Integer.MAX_VALUE }) {
            Backend backend = new Backend(result(true));
            ControlThreadAffinity affinity = new ControlThreadAffinity(backend::bind, backend::restore);
            check(affinity.restore(), "cleanup before INIT is harmless");
            affinity.bind(core);
            check(backend.bindCalls == 0 && backend.restoreCalls == 0, "scheduler/invalid config skips native backend");
            check(affinity.requested_core() == core && affinity.tid() == -1, "request retained without native TID query");
            check(!affinity.applied() && !affinity.placement_unknown(), "skipped affinity has a known unchanged mask");
            check(affinity.restore(), "skipped binding needs no restoration");
            check(affinity.detail().contains(core == -1 ? "skipped" : "Invalid core"), "configuration diagnostic");
        }
        ControlThreadAffinity nativeSkipped = new ControlThreadAffinity();
        nativeSkipped.bind(-1);
        check(nativeSkipped.restore(), "real scheduler mode works without Android runtime/native methods");
    }

    private static void verified_checks() throws Exception {
        Backend backend = new Backend(result(true));
        ControlThreadAffinity affinity = new ControlThreadAffinity(backend::bind, backend::restore);
        affinity.bind(2);
        check(affinity.applied() && !affinity.placement_unknown(), "verified result kept active");
        check(affinity.tid() == 42 && affinity.requested_core() == 2, "native TID and requested CPU retained");
        check(affinity.detail().contains("Verified CPU 2"), "verification detail exposed without I/O");
        affinity.bind(3);
        check(backend.bindCalls == 1 && affinity.requested_core() == 2, "duplicate bind cannot capture an already pinned original mask");
        Thread.currentThread().interrupt();
        check(affinity.restore(), "verified binding restores at cleanup");
        check(Thread.interrupted(), "cleanup preserves prior interruption");
        check(backend.seen == backend.result, "restoration receives the exact original result");
        check(!affinity.applied() && affinity.detail().contains("Original affinity restored"), "restored status stops claiming current binding");
        check(affinity.restore() && backend.restoreCalls == 1, "successful restoration is not repeated");
        affinity.bind(0);
        check(backend.bindCalls == 1, "one lifecycle cannot rebind after cleanup");
    }

    private static void partial_failure_checks() throws Exception {
        Backend backend = new Backend(result(false));
        backend.failRestoreCount = 1;
        ControlThreadAffinity affinity = new ControlThreadAffinity(backend::bind, backend::restore);
        affinity.bind(2);
        check(backend.restoreCalls == 1 && backend.seen == backend.result, "unverified partial change restores immediately using complete result");
        check(affinity.placement_unknown() && !affinity.applied(), "failed recovery reports unknown placement");
        check(affinity.detail().contains("Restoration failed"), "failed recovery is visible");
        check(affinity.restore(), "cleanup retries failed immediate recovery");
        check(backend.restoreCalls == 2 && !affinity.placement_unknown(), "retry restores known inherited mask");
        Backend recovered = new Backend(result(false));
        ControlThreadAffinity immediate = new ControlThreadAffinity(recovered::bind, recovered::restore);
        immediate.bind(2);
        check(!immediate.placement_unknown() && immediate.detail().contains("Original affinity restored"), "unverified binding recovered before continuing");
        check(immediate.restore() && recovered.restoreCalls == 1, "successful immediate restore is not repeated at shutdown");
    }

    private static void backend_failure_checks() throws Exception {
        Backend bindFailure = new Backend(result(true));
        bindFailure.throwBind = true;
        ControlThreadAffinity failedBind = new ControlThreadAffinity(bindFailure::bind, bindFailure::restore);
        failedBind.bind(2);
        check(failedBind.placement_unknown() && !failedBind.restore(), "unexpected bind exception cannot claim restored placement without saved result");
        check(failedBind.detail().contains("original mask unavailable"), "unexpected bind exception explains recovery limitation");
        failedBind.bind(2);
        check(bindFailure.bindCalls == 1, "unexpected failure cannot trigger original-mask recapture");
        Backend restoreFailure = new Backend(result(true));
        restoreFailure.throwRestore = true;
        ControlThreadAffinity failedRestore = new ControlThreadAffinity(restoreFailure::bind, restoreFailure::restore);
        failedRestore.bind(2);
        check(!failedRestore.restore() && failedRestore.placement_unknown(), "unexpected restore exception does not escape cleanup");
        restoreFailure.throwRestore = false;
        check(failedRestore.restore() && restoreFailure.seen == restoreFailure.result, "saved result survives restoration exception for retry");
        ControlThreadAffinity missingResult = new ControlThreadAffinity(core -> null, ignored -> true);
        missingResult.bind(2);
        check(missingResult.placement_unknown() && !missingResult.restore(), "missing backend result is unknown rather than silent scheduler fallback");
    }

    private static void thread_owner_checks() throws Exception {
        Backend backend = new Backend(result(true));
        ControlThreadAffinity affinity = new ControlThreadAffinity(backend::bind, backend::restore);
        affinity.bind(2);
        AtomicBoolean otherRestore = new AtomicBoolean(true);
        AtomicReference<Throwable> duplicateFailure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            otherRestore.set(affinity.restore());
            try { affinity.bind(2); } catch (Throwable failure) { duplicateFailure.set(failure); }
        });
        other.start();
        other.join();
        check(!otherRestore.get() && backend.restoreCalls == 0, "other Java thread cannot issue restore for OpMode thread");
        check(duplicateFailure.get() instanceof IllegalStateException && backend.bindCalls == 1, "other Java thread cannot rebind lifecycle");
        check(affinity.restore(), "correct owner can restore after cross-thread misuse");
    }

    private static ThreadAffinity.Result result(boolean applied) throws Exception {
        Constructor<ThreadAffinity.Result> constructor = ThreadAffinity.Result.class.getDeclaredConstructor(
                boolean.class, int.class, int.class, String.class, String.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(applied, 42, 2,
                applied ? "Verified CPU 2 (mask 4)" : "Affinity not verified; requested 4, read 3", "f", true);
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }

    private static final class Backend {
        final ThreadAffinity.Result result;
        ThreadAffinity.Result seen;
        int bindCalls;
        int restoreCalls;
        int failRestoreCount;
        boolean throwBind;
        boolean throwRestore;

        Backend(ThreadAffinity.Result result) { this.result = result; }

        ThreadAffinity.Result bind(int core) {
            bindCalls++;
            if (throwBind) throw new IllegalStateException("bind failed unexpectedly");
            return result;
        }

        boolean restore(ThreadAffinity.Result original) {
            seen = original;
            restoreCalls++;
            if (throwRestore) throw new IllegalStateException("restore failed unexpectedly");
            return restoreCalls > failRestoreCount;
        }
    }
}
