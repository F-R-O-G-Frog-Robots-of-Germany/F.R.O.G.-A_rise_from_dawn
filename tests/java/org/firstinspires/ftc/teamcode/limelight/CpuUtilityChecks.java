package org.firstinspires.ftc.teamcode.limelight;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Desktop parser and fake-command checks; never changes this computer's CPU affinity. */
public final class CpuUtilityChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        affinity_checks();
        parser_checks();
        sampler_checks();
        System.out.println("CpuUtilityChecks: " + checks + " checks passed");
    }

    private static void affinity_checks() {
        check("f".equals(ThreadAffinity.parse_mask("pid 42's current affinity mask: 000F")), "mask normalized");
        check("4".equals(ThreadAffinity.parse_mask("pid 42's current affinity mask: f\npid 42's new affinity mask: 4")), "last mask");
        check(ThreadAffinity.parse_mask("pid 42 failed") == null, "missing mask");
        check(ThreadAffinity.parse_mask("affinity mask: 0") == null, "zero mask invalid");
        check(ThreadAffinity.parse_mask(null) == null, "null output");
        FakeCommands commands = new FakeCommands("f");
        ThreadAffinity.Result pinned = ThreadAffinity.bind_thread(2, 42, commands);
        check(pinned.applied && pinned.core == 2 && pinned.tid == 42, "verified CPU2");
        check(commands.calls.equals(Arrays.asList("-p 42", "-p 4 42", "-p 42")), "per-thread commands only");
        Thread.currentThread().interrupt();
        check(ThreadAffinity.restore_thread(pinned, commands), "restores original mask");
        check(Thread.interrupted(), "restore preserves prior interruption");
        check(!commands.sawInterrupted, "cleanup command runs with old interruption cleared");
        check(commands.mask.equals("f"), "restored all originally allowed CPUs");
        check(ThreadAffinity.restore_thread(pinned, commands), "repeat restore harmless");
        int count = commands.calls.size();
        check(!ThreadAffinity.bind_thread(-1, 42, commands).applied, "scheduler mode");
        check(!ThreadAffinity.bind_thread(-2, 42, commands).applied, "invalid negative core");
        check(!ThreadAffinity.bind_thread(1024, 42, commands).applied, "bounded mask size");
        check(!ThreadAffinity.bind_thread(1, 0, commands).applied, "reject TID zero");
        check(commands.calls.size() == count, "invalid/scheduler request does not spawn command");
        FakeCommands rejected = new FakeCommands("3");
        rejected.failSet = true;
        ThreadAffinity.Result failure = ThreadAffinity.bind_thread(3, 42, rejected);
        check(!failure.applied && failure.detail.contains("failed"), "command denial reported");
        rejected.failSet = false;
        check(ThreadAffinity.restore_thread(failure, rejected), "failed set can restore after timeout");
        FakeCommands intersected = new FakeCommands("f");
        intersected.overrideReadback = "3";
        check(!ThreadAffinity.bind_thread(2, 42, intersected).applied, "verify effective mask instead of trusting exit code");
        FakeCommands unreadable = new FakeCommands("f");
        unreadable.failRead = true;
        check(!ThreadAffinity.bind_thread(1, 42, unreadable).applied, "original must be known");
        check(unreadable.calls.size() == 1, "no set if original unavailable");
        FakeCommands largeCore = new FakeCommands("ffffffffffffffff");
        check(ThreadAffinity.bind_thread(63, 42, largeCore).applied, "64-bit mask has no signed overflow");
    }

    private static void parser_checks() {
        CpuStats.ThreadStat stat = CpuStats.parse_thread_stat(stat(42, "a (nested) name)", 120, 30, 90, 3));
        check(stat != null && stat.tid == 42 && stat.name.equals("a (nested) name)"), "stat names contain spaces and parentheses");
        check(stat.cpuTicks == 150 && stat.startTicks == 90 && stat.lastCpu == 3, "correct stat field positions");
        check(stat.priority == 25 && stat.nice == 5 && stat.state.equals("S"), "native scheduler fields");
        check(CpuStats.parse_thread_stat("42 invalid") == null, "malformed stat rejected");
        check(CpuStats.parse_thread_stat(stat(42, "x", -1, 0, 1, 0)) == null, "negative CPU counter rejected");
        check(CpuStats.parse_thread_stat(stat(42, "x", Long.MAX_VALUE, 1, 1, 0)) == null, "counter sum overflow rejected");
        check(CpuStats.parse_thread_stat(stat(42, "x", 0, 0, -1, 0)) == null, "negative starttime rejected");
        Map<String, String> status = CpuStats.parse_status("Name:\tworker\nCpus_allowed:\t00000004\nCpus_allowed_list:\t2\n");
        check("2".equals(status.get("Cpus_allowed_list")), "allowed CPU list parsed");
        near(CpuStats.thread_percent(152, 150, 500_000_000L, 100), 4.0, "tick-rate converted to CPU percent");
        near(CpuStats.thread_percent(150, 150, 500_000_000L, 100), 0.0, "measured idle is actual zero");
        check(CpuStats.thread_percent(149, 150, 500_000_000L, 100) == null, "CPU counter rollback unavailable");
        check(CpuStats.thread_percent(152, 150, 0, 100) == null, "zero time unavailable");
        check(CpuStats.thread_percent(152, 150, 500_000_000L, -1) == null, "unknown tick rate unavailable");
        Map<Integer, CpuStats.CoreTicks> before = CpuStats.parse_core_stats("cpu 999 0 0 0\ncpu0 10 0 20 70 0 0 0 0 10 0\ncpu2 5 0 0 5\n");
        Map<Integer, CpuStats.CoreTicks> after = CpuStats.parse_core_stats("cpu0 20 0 30 150 0 0 0 0 20 0\ncpu2 10 0 0 10\n");
        check(before.size() == 2 && !before.containsKey(-1), "skip aggregate and retain sparse CPU IDs");
        near(CpuStats.core_percent(after.get(0), before.get(0)), 20.0, "guest not counted twice");
        near(CpuStats.core_percent(after.get(2), before.get(2)), 50.0, "older four-counter core row");
        check(CpuStats.core_percent(before.get(0), after.get(0)) == null, "core counter reset unavailable");
        check(CpuStats.core_percent(before.get(0), before.get(0)) == null, "no core elapsed ticks unavailable");
        check(CpuStats.parse_core_stats("cpu0 x 0 0 0\ncpu1 0 -1 0 0\ncpu 1 1 1 1").isEmpty(), "bad core rows rejected");
        check(CpuStats.parse_kib("123 kB") == 125952L, "kB is KiB in proc status");
        check(CpuStats.parse_kib("123 bytes") == null, "unknown memory unit unavailable");
        check(CpuStats.parse_kib(Long.MAX_VALUE + " kB") == null, "memory overflow unavailable");
        near(CpuStats.parse_temperature("45123"), 45.123, "thermal ABI millidegrees");
        check(CpuStats.parse_temperature("NaN") == null && CpuStats.parse_temperature("Infinity") == null, "nonfinite thermal unavailable");
    }

    private static void sampler_checks() throws Exception {
        File root = Files.createTempDirectory("frog-cpu-check-").toFile();
        File proc = new File(root, "proc");
        File sys = new File(root, "sys");
        try {
            write(proc, "self/task/42/stat", stat(42, "vision", 100, 20, 5, 2));
            write(proc, "self/task/42/status", "Name: vision\nCpus_allowed: 00000004\nCpus_allowed_list: 2\n");
            write(proc, "self/task/43/stat", "malformed");
            write(proc, "stat", "cpu0 10 0 10 80 0 0 0 0\ncpu2 5 0 5 90 0 0 0 0\n");
            write(proc, "self/status", "VmRSS: 1024 kB\nVmSize: 2048 kB\n");
            write(sys, "devices/system/cpu/cpu2/cpufreq/scaling_cur_freq", "1200000");
            write(sys, "devices/system/cpu/cpu2/online", "1");
            write(sys, "devices/system/cpu/cpu3/online", "0");
            write(sys, "class/thermal/thermal_zone0/type", "cpu-thermal");
            write(sys, "class/thermal/thermal_zone0/temp", "42500");
            CpuStats sampler = new CpuStats(proc, sys, 100);
            CpuStats.Snapshot first = sampler.sample_at(1_000_000_000L);
            check(first.threads.size() == 2 && first.threads.get(0).cpuPercent == null, "enumerate all app TIDs and warmup");
            check(first.threads.get(1).cpuPercent == null && first.threads.get(1).nice == null, "unreadable thread stays explicitly unavailable");
            check(first.cores.size() == 3 && first.cores.get(2).online.equals(false), "include offline sysfs CPU");
            check(first.cores.get(0).online == null && first.cores.get(0).frequencyKhz == null, "missing core nodes not zero");
            check(first.cores.get(1).frequencyKhz == 1_200_000L, "frequency kHz");
            check(first.memory.processRssBytes == 1_048_576L && first.memory.processVirtualBytes == 2_097_152L, "process memory bytes");
            near(first.thermals.get(0).celsius, 42.5, "thermal zone reading");
            check(first.thermals.get(0).rawTemperature.equals("42500"), "raw thermal preserved");
            boolean immutable = false;
            try { first.threads.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            check(immutable, "snapshot list immutable");
            write(proc, "self/task/42/stat", stat(42, "vision", 125, 45, 5, 2));
            write(proc, "stat", "cpu0 20 0 20 160 0 0 0 0\ncpu2 15 0 15 170 0 0 0 0\n");
            CpuStats.Snapshot second = sampler.sample_at(2_000_000_000L);
            near(second.threads.get(0).cpuPercent, 50.0, "thread sample delta");
            near(second.cores.get(0).cpuPercent, 20.0, "core sample delta");
            check(first.threads.get(0).cpuPercent == null, "older snapshot unchanged");
            write(proc, "self/task/42/stat", stat(42, "different", 130, 50, 9, 1));
            CpuStats.Snapshot reused = sampler.sample_at(3_000_000_000L);
            check(reused.threads.get(0).cpuPercent == null, "TID reused with new starttime resets baseline");
            CpuStats.Snapshot missing = new CpuStats(new File(root, "absent-proc"), new File(root, "absent-sys"), -1).sample_at(1);
            check(missing.threads.isEmpty() && missing.cores.isEmpty() && missing.memory.processRssBytes == null,
                    "unreadable proc data unavailable");
            check(missing.detail.contains("unavailable"), "missing data has explanatory detail");
        } finally {
            delete_fixture(root);
        }
    }

    private static String stat(int tid, String name, long user, long system, long started, int cpu) {
        String[] fields = new String[50];
        Arrays.fill(fields, "0");
        fields[0] = "S";
        fields[11] = Long.toString(user);
        fields[12] = Long.toString(system);
        fields[15] = "25";
        fields[16] = "5";
        fields[19] = Long.toString(started);
        fields[36] = Integer.toString(cpu);
        return tid + " (" + name + ") " + String.join(" ", fields);
    }

    private static void write(File root, String relative, String text) throws Exception {
        File file = new File(root, relative);
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static void delete_fixture(File file) throws Exception {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete_fixture(child);
        Files.deleteIfExists(file.toPath());
    }

    private static void near(Double actual, double expected, String message) {
        check(actual != null && Math.abs(actual - expected) < 1e-8, message + " expected=" + expected + " actual=" + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeCommands implements ThreadAffinity.CommandRunner {
        final List<String> calls = new ArrayList<>();
        String mask;
        boolean failSet;
        boolean failRead;
        boolean sawInterrupted;
        String overrideReadback;

        FakeCommands(String mask) { this.mask = mask; }

        public ThreadAffinity.CommandResult run(String... args) {
            sawInterrupted |= Thread.currentThread().isInterrupted();
            calls.add(String.join(" ", args));
            if (args.length == 2) {
                return new ThreadAffinity.CommandResult(!failRead, failRead ? "denied" : "pid 42's current affinity mask: " + mask);
            }
            if (failSet) return new ThreadAffinity.CommandResult(false, "denied");
            mask = overrideReadback == null ? args[1] : overrideReadback;
            return new ThreadAffinity.CommandResult(true, "ok");
        }
    }
}
