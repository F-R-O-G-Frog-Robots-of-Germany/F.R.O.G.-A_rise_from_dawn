package org.firstinspires.ftc.teamcode.limelight;

import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Slow diagnostics sampler: call from a diagnostics worker, never a motor/control loop.
 * It enumerates every native thread in this app, including FTC, ART and SDK workers.
 * Null values mean unavailable or awaiting the second sample, never zero utilization.
 * Thread CPU% uses elapsed monotonic time and sysconf(_SC_CLK_TCK): 100% is one full CPU.
 * Tick quantization/read timing can briefly produce slightly over 100%. Core CPU% is
 * busy time across all processes, counting iowait as idle and avoiding guest double-counting.
 * lastCpu is the last CPU that ran a thread, NOT a CPU assignment; use allowedCpuList/mask.
 */
public final class CpuStats {
    private static final int MAX_FILE_BYTES = 1_048_576;
    private final File procRoot;
    private final File sysRoot;
    private final long ticksPerSecond;
    private final Map<Integer, ThreadBaseline> previousThreads = new HashMap<>();
    private Map<Integer, CoreTicks> previousCores = new HashMap<>();

    public CpuStats() {
        this(new File("/proc"), new File("/sys"), clock_ticks());
    }

    CpuStats(File procRoot, File sysRoot, long ticksPerSecond) {
        this.procRoot = procRoot;
        this.sysRoot = sysRoot;
        this.ticksPerSecond = ticksPerSecond;
    }

    public static final class Snapshot {
        public final long capturedNanos;
        public final List<ThreadSample> threads;
        public final List<CoreSample> cores;
        public final MemorySample memory;
        public final List<ThermalSample> thermals;
        public final String detail;

        private Snapshot(long capturedNanos, List<ThreadSample> threads, List<CoreSample> cores,
                         MemorySample memory, List<ThermalSample> thermals, String detail) {
            this.capturedNanos = capturedNanos;
            this.threads = immutable(threads);
            this.cores = immutable(cores);
            this.memory = memory;
            this.thermals = immutable(thermals);
            this.detail = detail;
        }
    }

    public static final class ThreadSample {
        public final int tid;
        public final String name;
        public final String state;
        public final Integer nativePriority;
        public final Integer nice;
        public final Integer lastCpu;
        public final String allowedCpuList;
        public final String allowedCpuMask;
        public final Double cpuPercent;
        public final String detail;

        private ThreadSample(int tid, ThreadStat stat, Map<String, String> status, Double percent, String detail) {
            this.tid = tid;
            this.name = stat == null ? value(status, "Name") : stat.name;
            this.state = stat == null ? value(status, "State") : stat.state;
            this.nativePriority = stat == null ? null : stat.priority;
            this.nice = stat == null ? null : stat.nice;
            this.lastCpu = stat == null ? null : stat.lastCpu;
            this.allowedCpuList = value(status, "Cpus_allowed_list");
            this.allowedCpuMask = value(status, "Cpus_allowed");
            this.cpuPercent = percent;
            this.detail = detail;
        }
    }

    public static final class CoreSample {
        public final int cpuId;
        public final Double cpuPercent;
        public final Long frequencyKhz;
        public final Boolean online;
        public final String detail;

        private CoreSample(int cpuId, Double percent, Long frequencyKhz, Boolean online, String detail) {
            this.cpuId = cpuId;
            this.cpuPercent = percent;
            this.frequencyKhz = frequencyKhz;
            this.online = online;
            this.detail = detail;
        }
    }

    public static final class MemorySample {
        public final Long processRssBytes;
        public final Long processVirtualBytes;
        public final Long javaUsedBytes;
        public final Long javaCommittedBytes;
        public final Long javaMaxBytes;
        public final String detail;

        private MemorySample(Map<String, String> status) {
            processRssBytes = parse_kib(value(status, "VmRSS"));
            processVirtualBytes = parse_kib(value(status, "VmSize"));
            Runtime runtime = Runtime.getRuntime();
            javaCommittedBytes = runtime.totalMemory();
            javaUsedBytes = javaCommittedBytes - runtime.freeMemory();
            javaMaxBytes = runtime.maxMemory();
            detail = processRssBytes == null || processVirtualBytes == null
                    ? "Process /proc memory unavailable; Java heap excludes native allocations" : "Java heap excludes native allocations";
        }
    }

    public static final class ThermalSample {
        public final String name;
        public final String type;
        /** Unmodified sysfs text, retained for vendor-specific unit checks. */
        public final String rawTemperature;
        public final Double celsius;
        public final String detail;

        private ThermalSample(String name, String type, String rawTemperature, Double celsius, String detail) {
            this.name = name;
            this.type = type;
            this.rawTemperature = rawTemperature;
            this.celsius = celsius;
            this.detail = detail;
        }
    }

    /** Serial sampling retains CPU deltas; returned objects and lists never change afterward. */
    public synchronized Snapshot sample() {
        return sample_at(System.nanoTime());
    }

    synchronized Snapshot sample_at(long now) {
        List<ThreadSample> threads = new ArrayList<>();
        File taskDirectory = new File(procRoot, "self/task");
        File[] taskFiles = list_files(taskDirectory);
        Map<Integer, ThreadBaseline> nextThreads = new HashMap<>();
        if (taskFiles != null) {
            for (File task : taskFiles) {
                Integer tid = parse_nonnegative_int(task.getName());
                if (tid == null || tid == 0) continue;
                ThreadStat stat = parse_thread_stat(read(new File(task, "stat")));
                Map<String, String> status = parse_status(read(new File(task, "status")));
                Double cpu = null;
                String detail;
                if (stat == null || stat.tid != tid) {
                    detail = "Thread stat unavailable, malformed, or thread exited during sampling";
                } else {
                    ThreadBaseline before = previousThreads.get(tid);
                    if (before != null && before.startTicks == stat.startTicks) {
                        cpu = thread_percent(stat.cpuTicks, before.cpuTicks, now - before.sampledNanos, ticksPerSecond);
                    }
                    nextThreads.put(tid, new ThreadBaseline(stat, now));
                    detail = cpu == null ? "CPU unavailable: first sample, reused TID, counter reset, or missing clock tick rate" : "";
                }
                if (value(status, "Cpus_allowed_list") == null || value(status, "Cpus_allowed") == null) {
                    detail = append(detail, "Allowed CPU nodes unavailable");
                }
                threads.add(new ThreadSample(tid, stat, status, cpu, detail));
            }
        }
        previousThreads.clear();
        previousThreads.putAll(nextThreads);
        Collections.sort(threads, Comparator.comparingInt(row -> row.tid));

        String cpuFile = read(new File(procRoot, "stat"));
        Map<Integer, CoreTicks> currentCores = parse_core_stats(cpuFile);
        TreeSet<Integer> coreIds = new TreeSet<>(currentCores.keySet());
        File cpuDirectory = new File(sysRoot, "devices/system/cpu");
        File[] cpuFiles = list_files(cpuDirectory);
        if (cpuFiles != null) {
            for (File file : cpuFiles) {
                if (!file.getName().startsWith("cpu")) continue;
                Integer id = parse_nonnegative_int(file.getName().substring(3));
                if (id != null) coreIds.add(id);
            }
        }
        List<CoreSample> cores = new ArrayList<>();
        for (int id : coreIds) {
            CoreTicks current = currentCores.get(id);
            Double percent = core_percent(current, previousCores.get(id));
            File directory = new File(cpuDirectory, "cpu" + id);
            Long frequency = parse_nonnegative_long(read(new File(directory, "cpufreq/scaling_cur_freq")));
            if (frequency == null) frequency = parse_nonnegative_long(read(new File(directory, "cpufreq/cpuinfo_cur_freq")));
            String onlineText = read(new File(directory, "online"));
            Boolean online = "1".equals(onlineText) ? Boolean.TRUE : "0".equals(onlineText) ? Boolean.FALSE : null;
            String detail = percent == null ? "CPU unavailable: first sample, offline, unreadable, or counter reset" : "";
            if (frequency == null) detail = append(detail, "Frequency unavailable");
            if (online == null) detail = append(detail, "Online node unavailable");
            cores.add(new CoreSample(id, percent, frequency, online, detail));
        }
        previousCores = currentCores;

        List<ThermalSample> thermals = new ArrayList<>();
        File[] thermalFiles = list_files(new File(sysRoot, "class/thermal"));
        if (thermalFiles != null) {
            for (File zone : thermalFiles) {
                if (!zone.getName().startsWith("thermal_zone")) continue;
                String type = read(new File(zone, "type"));
                String rawTemperature = read(new File(zone, "temp"));
                Double temperature = parse_temperature(rawTemperature);
                thermals.add(new ThermalSample(zone.getName(), type, rawTemperature, temperature,
                        temperature == null ? "Thermal temperature unavailable" : "Kernel thermal-zone temperature, degrees C"));
            }
        }
        Collections.sort(thermals, Comparator.comparing(row -> row.name));
        String detail = taskFiles == null ? "App thread enumeration unavailable" : "App threads only; CPU% needs two samples";
        if (ticksPerSecond <= 0) detail = append(detail, "sysconf(_SC_CLK_TCK) unavailable");
        if (currentCores.isEmpty()) detail = append(detail, "Per-core /proc/stat unavailable");
        if (thermals.isEmpty()) detail = append(detail, "Thermal zones unavailable");
        MemorySample memory = new MemorySample(parse_status(read(new File(procRoot, "self/status"))));
        return new Snapshot(now, threads, cores, memory, thermals, detail);
    }

    static final class ThreadStat {
        final int tid;
        final String name;
        final String state;
        final int priority;
        final int nice;
        final long cpuTicks;
        final long startTicks;
        final Integer lastCpu;

        ThreadStat(int tid, String name, String state, int priority, int nice,
                   long cpuTicks, long startTicks, Integer lastCpu) {
            this.tid = tid;
            this.name = name;
            this.state = state;
            this.priority = priority;
            this.nice = nice;
            this.cpuTicks = cpuTicks;
            this.startTicks = startTicks;
            this.lastCpu = lastCpu;
        }
    }

    private static final class ThreadBaseline {
        final long startTicks;
        final long cpuTicks;
        final long sampledNanos;
        ThreadBaseline(ThreadStat stat, long sampledNanos) {
            startTicks = stat.startTicks;
            cpuTicks = stat.cpuTicks;
            this.sampledNanos = sampledNanos;
        }
    }

    static final class CoreTicks {
        final long[] ticks;
        CoreTicks(long[] ticks) { this.ticks = ticks.clone(); }
    }

    static ThreadStat parse_thread_stat(String text) {
        if (text == null) return null;
        int opening = text.indexOf('(');
        int closing = text.lastIndexOf(')');
        if (opening < 1 || closing < opening || closing + 2 >= text.length()) return null;
        try {
            int tid = Integer.parseInt(text.substring(0, opening).trim());
            String[] fields = text.substring(closing + 1).trim().split("\\s+");
            if (tid <= 0 || fields.length < 20 || fields[0].length() != 1) return null;
            long user = Long.parseLong(fields[11]); // Linux field 14; fields[0] is field 3.
            long system = Long.parseLong(fields[12]);
            long start = Long.parseLong(fields[19]);
            if (user < 0 || system < 0 || start < 0 || user > Long.MAX_VALUE - system) return null;
            Integer cpu = fields.length > 36 ? parse_nonnegative_int(fields[36]) : null;
            return new ThreadStat(tid, text.substring(opening + 1, closing), fields[0],
                    Integer.parseInt(fields[15]), Integer.parseInt(fields[16]), user + system, start, cpu);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    static Map<String, String> parse_status(String text) {
        Map<String, String> fields = new HashMap<>();
        if (text == null) return fields;
        for (String line : text.split("\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) fields.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        return fields;
    }

    static Map<Integer, CoreTicks> parse_core_stats(String text) {
        Map<Integer, CoreTicks> cores = new TreeMap<>();
        if (text == null) return cores;
        for (String line : text.split("\\n")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 5 || !fields[0].startsWith("cpu")) continue;
            Integer id = parse_nonnegative_int(fields[0].substring(3));
            if (id == null) continue; // Skip the aggregate "cpu" row.
            long[] ticks = new long[8]; // guest and guest_nice are already in user/nice.
            boolean valid = true;
            for (int i = 0; i < Math.min(8, fields.length - 1); i++) {
                Long tick = parse_nonnegative_long(fields[i + 1]);
                if (tick == null) { valid = false; break; }
                ticks[i] = tick;
            }
            if (valid) cores.put(id, new CoreTicks(ticks));
        }
        return cores;
    }

    static Double thread_percent(long current, long before, long elapsedNanos, long rate) {
        if (rate <= 0 || elapsedNanos <= 0 || before < 0 || current < before) return null;
        return (current - before) / (double) rate / (elapsedNanos / 1_000_000_000.0) * 100.0;
    }

    static Double core_percent(CoreTicks current, CoreTicks before) {
        if (current == null || before == null) return null;
        double total = 0;
        double idle = 0;
        for (int i = 0; i < 8; i++) {
            if (current.ticks[i] < before.ticks[i]) return null;
            double delta = current.ticks[i] - before.ticks[i];
            total += delta;
            if (i == 3 || i == 4) idle += delta;
        }
        return total <= 0 ? null : Math.max(0.0, Math.min(100.0, (total - idle) / total * 100.0));
    }

    static Long parse_kib(String text) {
        if (text == null) return null;
        String[] fields = text.trim().split("\\s+");
        if (fields.length != 2 || !fields[1].equals("kB")) return null;
        Long count = parse_nonnegative_long(fields[0]);
        return count == null || count > Long.MAX_VALUE / 1024 ? null : count * 1024;
    }

    static Double parse_temperature(String text) {
        if (text == null) return null;
        try {
            // The kernel thermal-zone ABI specifies millidegrees Celsius.
            double value = Double.parseDouble(text) / 1000.0;
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException e) { return null; }
    }

    private static long clock_ticks() {
        try { return Os.sysconf(OsConstants._SC_CLK_TCK); }
        catch (RuntimeException | LinkageError e) { return -1; }
    }

    private static <T> List<T> immutable(List<T> items) {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    private static String value(Map<String, String> fields, String key) {
        String value = fields.get(key);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String append(String first, String next) {
        return first.isEmpty() ? next : first + "; " + next;
    }

    private static Integer parse_nonnegative_int(String text) {
        Long number = parse_nonnegative_long(text);
        return number == null || number > Integer.MAX_VALUE ? null : number.intValue();
    }

    private static Long parse_nonnegative_long(String text) {
        if (text == null) return null;
        try {
            long value = Long.parseLong(text.trim());
            return value < 0 ? null : value;
        } catch (NumberFormatException e) { return null; }
    }

    private static File[] list_files(File directory) {
        try { return directory.listFiles(); }
        catch (SecurityException denied) { return null; }
    }

    private static String read(File file) {
        try (FileInputStream stream = new FileInputStream(file);
             ByteArrayOutputStream captured = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (captured.size() + count > MAX_FILE_BYTES) return null;
                captured.write(buffer, 0, count);
            }
            return new String(captured.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Exception unavailable) { return null; }
    }
}
