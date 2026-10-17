package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.hardware.lynx.LynxModule;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.CommandProfile;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.Config;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.OutputMode;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.PoseProfile;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.Recorder;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.ShotMode;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.Stats;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.Suite;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.Summary;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.Trial;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.WritePolicy;

/** Standalone JVM checks for measurement correctness and comparable, isolated trial settings. */
public final class PerformanceComparisonChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        statistics();
        paired_csv();
        recording_allocations();
        configuration_snapshots();
        single_change_references();
        all_minus_one_dependencies();
        repeat_order();
        System.out.println("Passed " + checks + " performance comparison checks");
    }

    private static void statistics() {
        boolean rejected = false;
        try { new Recorder(0); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "A recorder must have positive capacity");

        Recorder recorder = new Recorder(100);
        Summary empty = recorder.summarize();
        for (Stats stat : new Stats[]{empty.cycle, empty.work, empty.read,
                empty.compute, empty.write, empty.telemetry}) {
            check(stat.n == 0, "An empty stage has zero samples");
            check(Double.isNaN(stat.meanMs) && Double.isNaN(stat.medianMs)
                    && Double.isNaN(stat.p95Ms) && Double.isNaN(stat.p99Ms)
                    && Double.isNaN(stat.maxMs), "Missing samples must not report zero-cost work");
            check(stat.over10ms == 0 && stat.over20ms == 0, "Empty deadline counts");
        }
        add_sample(recorder, 9_000_000L);
        Stats single = recorder.summarize().cycle;
        near(single.meanMs, 9.0, "Single sample mean");
        near(single.medianMs, 9.0, "Single sample median");
        near(single.p95Ms, 9.0, "Single sample p95");
        near(single.p99Ms, 9.0, "Single sample p99");

        recorder.reset();
        add_sample(recorder, 9_000_000L);
        add_sample(recorder, 1_000_000L);
        add_sample(recorder, 5_000_000L);
        Stats odd = recorder.summarize().cycle;
        near(odd.meanMs, 5.0, "Odd sample mean");
        near(odd.medianMs, 5.0, "Odd sample median");
        near(odd.p95Ms, 9.0, "Small samples use nearest-rank p95");
        near(odd.p99Ms, 9.0, "Small samples use nearest-rank p99");
        add_sample(recorder, 3_000_000L);
        Stats even = recorder.summarize().cycle;
        near(even.meanMs, 4.5, "Even sample mean");
        near(even.medianMs, 4.0, "Even median averages the middle pair");

        recorder.reset();
        add_sample(recorder, 10_000_000L);
        add_sample(recorder, 20_000_000L);
        add_sample(recorder, 20_000_001L);
        Stats deadlines = recorder.summarize().cycle;
        check(deadlines.over10ms == 2, "Only cycles strictly above 10 ms exceed that deadline");
        check(deadlines.over20ms == 1, "Only cycles strictly above 20 ms exceed that deadline");

        recorder.reset();
        for (int i = 100; i >= 1; i--) add_sample(recorder, i * 1_000_000L);
        check(recorder.count == 100 && recorder.full, "The last available slot stops recording");
        Summary hundred = recorder.summarize();
        Stats cycle = hundred.cycle;
        check(cycle.n == 100, "All stored cycle samples are summarized");
        near(cycle.meanMs, 50.5, "Mean of 1 through 100");
        near(cycle.medianMs, 50.5, "Median of 1 through 100");
        near(cycle.p95Ms, 95.0, "Nearest-rank p95 of 100 samples");
        near(cycle.p99Ms, 99.0, "Nearest-rank p99 of 100 samples");
        near(cycle.maxMs, 100.0, "Maximum of unsorted input");
        check(cycle.over10ms == 90 && cycle.over20ms == 80, "Deadline counts of 100 cycles");
        near(hundred.work.meanMs, 25.25, "Work samples remain separate from cycles");
        near(hundred.read.maxMs, 33.333333, "Read samples remain separate");
        near(hundred.compute.maxMs, 25.0, "Compute samples remain separate");
        near(hundred.write.maxMs, 20.0, "Write samples remain separate");
        near(hundred.telemetry.maxMs, 16.666666, "Telemetry samples remain separate");
        rejected = false;
        try { add_sample(recorder, 101_000_000L); }
        catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "Full buffers reject writes instead of silently dropping samples");
        check(recorder.count == 100 && recorder.full, "A rejected write preserves all samples");

        recorder.reset();
        check(recorder.count == 0 && !recorder.full, "Reset releases capacity");
        add_sample(recorder, 2_000_000L);
        near(recorder.summarize().cycle.meanMs, 2.0, "Reset excludes the previous trial");
        Recorder one = new Recorder(1);
        add_sample(one, Long.MAX_VALUE);
        check(one.full && one.count == 1, "Capacity one stops after one sample");
        near(one.summarize().cycle.meanMs, Long.MAX_VALUE / 1_000_000.0,
                "Summation does not overflow long nanos");
        Recorder two = new Recorder(2);
        add_sample(two, Long.MAX_VALUE);
        add_sample(two, Long.MAX_VALUE - 2L);
        near(two.summarize().cycle.medianMs, Long.MAX_VALUE / 1_000_000.0,
                "The middle-pair sum does not overflow long nanos");
    }

    private static void paired_csv() throws Exception {
        Recorder recorder = new Recorder(3);
        // Work and stages deliberately have different sort orders from cycle times.
        recorder.record(30, 2, 70, 6, 50, 4);
        recorder.record(10, 3, 20, 5, 60, 9);
        recorder.record(20, 1, 90, 8, 40, 7);
        recorder.summarize();
        recorder.summarize();
        Path directory = Files.createTempDirectory("ftc-performance-checks-");
        File target = directory.resolve("paired.csv").toFile();
        try {
            check(recorder.export(target, "trial=a\r\nprofile=b\nthird=c\rfourth=d").equals(target),
                    "Export returns the file actually written");
            List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
            check(lines.size() == 11, "CSV contains metadata, one header, and three samples");
            check(lines.get(0).equals("# sample_count=3"), "CSV records actual sample count");
            check(lines.get(2).equals("# stopped_at_capacity=true"), "CSV discloses full-buffer stop");
            check(lines.get(3).equals("# trial=a") && lines.get(6).equals("# fourth=d"),
                    "All metadata newline styles remain comments");
            check(lines.get(7).equals("sample,cycle_ns,work_ns,read_ns,compute_ns,write_ns,telemetry_ns"),
                    "CSV declares raw nanosecond columns");
            check(lines.get(8).equals("1,30,2,70,6,50,4"), "Sorting does not change the first paired row");
            check(lines.get(9).equals("2,10,3,20,5,60,9"), "All six times stay paired in the second row");
            check(lines.get(10).equals("3,20,1,90,8,40,7"), "All six times stay paired in the final row");
            recorder.reset();
            recorder.record(7, 6, 5, 4, 3, 2);
            recorder.export(target, null);
            lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
            check(lines.size() == 5 && lines.get(4).equals("1,7,6,5,4,3,2"),
                    "A new export contains only the new trial, without stale CSV rows");
        } finally {
            Files.deleteIfExists(target.toPath());
            Files.deleteIfExists(directory);
        }
    }

    private static void recording_allocations() {
        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof com.sun.management.ThreadMXBean)) return;
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) baseBean;
        if (!bean.isThreadAllocatedMemorySupported()) return;
        bean.setThreadAllocatedMemoryEnabled(true);
        Recorder recorder = new Recorder(1024);
        long thread = Thread.currentThread().getId();
        // Warm the recorder, measurement API, and JIT before the allocation assertion.
        measure_recording_allocations(bean, thread, recorder);
        measure_recording_allocations(bean, thread, recorder);
        long allocated = measure_recording_allocations(bean, thread, recorder);
        check(allocated == 0L, "One million warmed recording/reset calls allocate zero bytes: " + allocated);
    }

    private static long measure_recording_allocations(com.sun.management.ThreadMXBean bean,
            long thread, Recorder recorder) {
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1_000_000; i++) {
            if (recorder.full) recorder.reset();
            recorder.record(i, i, i, i, i, i);
        }
        return bean.getThreadAllocatedBytes(thread) - before;
    }

    private static void configuration_snapshots() {
        Config selected = selected_config();
        String original = selected.metadata();
        Config copy = selected.copy();
        check(copy != selected && copy.metadata().equals(original), "Config.copy preserves every setting");
        Trial snapshot = new Trial("snapshot", "reference", selected, 2);
        selected.rangeIntervalNanos = 17;
        selected.commandProfile = CommandProfile.CONSTANT;
        check(copy.metadata().equals(original), "Editing the source does not change its copy");
        check(snapshot.config.metadata().equals(original), "Trials freeze settings when constructed");

        Config shadow = selected_config();
        PerformanceComparison.apply_baseline(shadow);
        assert_baseline(shadow);
        check(!shadow.live(), "The baseline preset cannot enable live actuation");
        PerformanceComparison.apply_optimized(shadow);
        assert_optimized(shadow);
        check(!shadow.live(), "The optimized preset cannot enable live actuation");
        check(shadow.commandProfile == CommandProfile.SMALL_CHANGES
                        && shadow.poseProfile == PoseProfile.HEADING_ONLY && !shadow.stageTimings,
                "Presets preserve workload profiles and instrumentation settings");

        Config custom = selected_config();
        String before = custom.metadata();
        List<Trial> trials = PerformanceComparison.build_trials(custom, Suite.CUSTOM, 3);
        check(trials.size() == 3, "CUSTOM produces one independent trial per repeat");
        for (int i = 0; i < trials.size(); i++) {
            Trial trial = trials.get(i);
            check(trial.repeat == i + 1 && trial.label.equals("CUSTOM") && trial.reference.equals("CUSTOM"),
                    "CUSTOM preserves repeat numbering and comparison identity");
            check(trial.config != custom && trial.config.metadata().equals(before),
                    "CUSTOM freezes the selected settings");
        }
        trials.get(0).config.rangeIntervalNanos = 999;
        check(trials.get(1).config.metadata().equals(before), "Repeated trials do not share mutable configs");
        check(custom.metadata().equals(before), "Building or editing trials leaves the selected config intact");
    }

    private static void single_change_references() {
        Config selected = selected_config();
        String before = selected.metadata();
        List<Trial> trials = PerformanceComparison.build_trials(selected, Suite.SINGLE_CHANGES, 1);
        Map<String, Trial> byLabel = index_trials(trials);
        check(trials.size() == 18, "Single-change suite includes every P1-P10 comparison variant");
        Config baseline = byLabel.get("BASELINE").config;
        assert_baseline(baseline);
        assert_optimized(byLabel.get("ALL").config);
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("P1_AUTO", keys("bulk_mode"));
        expected.put("P1_MANUAL", keys("bulk_mode"));
        expected.put("P2_EXACT", keys("power_policy"));
        expected.put("P2_EPSILON", keys("power_policy"));
        expected.put("P3_EXACT", keys("servo_policy"));
        expected.put("P3_EPSILON", keys("servo_policy"));
        expected.put("P4_SHARE", keys("share_reads"));
        expected.put("P5_RANGE_300", keys("range_interval_ns"));
        expected.put("P7_TELEMETRY_100", keys("telemetry_period_ns"));
        expected.put("P7_TELEMETRY_200", keys("telemetry_period_ns"));
        expected.put("P7_TELEMETRY_OFF", keys("telemetry_period_ns"));
        expected.put("P8_TARGET", keys("reuse_target"));
        expected.put("P9_REUSE", keys("shot_mode"));
        expected.put("P9_CACHE", keys("shot_mode"));
        expected.put("P10_VELOCITY", keys("cache_velocity"));
        for (Map.Entry<String, Set<String>> entry : expected.entrySet()) {
            Trial trial = byLabel.get(entry.getKey());
            check(trial != null, "Required variant exists: " + entry.getKey());
            check(trial.reference.equals("BASELINE"), "Single changes reference the baseline: " + trial.label);
            check(differences(baseline, trial.config).equals(entry.getValue()),
                    "Only intended settings change: " + trial.label);
        }
        Trial paired = byLabel.get("P5_RANGE_300");
        Trial staggered = byLabel.get("P6_STAGGER_300");
        check(staggered.reference.equals(paired.label), "P6 names its equal-freshness P5 reference");
        check(paired.config.rangeIntervalNanos == 300_000_000L
                        && staggered.config.rangeIntervalNanos == paired.config.rangeIntervalNanos,
                "P6 compares equal per-sensor refresh intervals");
        check(!paired.config.staggerRanges && staggered.config.staggerRanges,
                "The P6 comparison switches paired reads to staggered reads");
        check(differences(paired.config, staggered.config).equals(keys("stagger_ranges")),
                "P6 changes no other setting relative to its declared reference");
        for (Trial trial : trials) check(!trial.config.live(), "Suites cannot enable live output: " + trial.label);
        check(selected.metadata().equals(before), "Single-change suite preserves the selected config");
    }

    private static void all_minus_one_dependencies() {
        Config selected = selected_config();
        String before = selected.metadata();
        List<Trial> trials = PerformanceComparison.build_trials(selected, Suite.ALL_MINUS_ONE, 1);
        Map<String, Trial> byLabel = index_trials(trials);
        check(trials.size() == 12, "All-minus-one includes baseline, all, and ten exclusions");
        Config all = byLabel.get("ALL").config;
        Trial withoutRange = byLabel.get("WITHOUT_P5_AND_P6");
        check(withoutRange != null && !byLabel.containsKey("WITHOUT_P5"),
                "Removing refresh limiting explicitly names the P6 dependency");
        check(withoutRange.reference.equals("ALL") && withoutRange.config.rangeIntervalNanos == 0
                        && !withoutRange.config.staggerRanges,
                "The P5 exclusion disables its dependent stagger schedule");
        check(differences(all, withoutRange.config).equals(keys("range_interval_ns", "stagger_ranges")),
                "Removing P5 and its dependency changes exactly those settings");
        Trial withoutStagger = byLabel.get("WITHOUT_P6");
        check(differences(all, withoutStagger.config).equals(keys("stagger_ranges"))
                        && withoutStagger.config.rangeIntervalNanos == all.rangeIntervalNanos,
                "Removing P6 preserves the P5 refresh interval");
        for (Trial trial : trials) {
            if (trial.label.startsWith("WITHOUT_")) {
                check(trial.reference.equals("ALL"), "Every exclusion names its ALL reference");
            }
            check(!trial.config.live(), "Exclusion presets cannot enable live output");
        }
        check(selected.metadata().equals(before), "All-minus-one preserves the selected config");
    }

    private static void repeat_order() {
        Config selected = selected_config();
        for (Suite suite : new Suite[]{Suite.BASELINE_AND_ALL, Suite.SINGLE_CHANGES, Suite.ALL_MINUS_ONE}) {
            List<Trial> first = PerformanceComparison.build_trials(selected, suite, 3);
            List<Trial> second = PerformanceComparison.build_trials(selected, suite, 3);
            int groupSize = first.size() / 3;
            Set<String> expectedLabels = new HashSet<>();
            for (int i = 0; i < groupSize; i++) expectedLabels.add(first.get(i).label);
            for (int repeat = 1; repeat <= 3; repeat++) {
                Set<String> actualLabels = new HashSet<>();
                for (int i = (repeat - 1) * groupSize; i < repeat * groupSize; i++) {
                    Trial a = first.get(i), b = second.get(i);
                    check(a.repeat == repeat, "Each trial retains its repeat group: " + suite);
                    check(actualLabels.add(a.label), "Every comparison occurs once per repeat: " + suite);
                    check(a.label.equals(b.label) && a.repeat == b.repeat && a.reference.equals(b.reference)
                                    && a.config.metadata().equals(b.config.metadata()),
                            "The fixed seed reproduces order and settings: " + suite);
                }
                check(actualLabels.equals(expectedLabels), "Every repeat contains the same comparisons: " + suite);
            }
        }
    }

    private static Config selected_config() {
        Config c = new Config();
        c.bulkMode = LynxModule.BulkCachingMode.AUTO;
        c.powerPolicy = WritePolicy.EXACT_CHANGE;
        c.servoPolicy = WritePolicy.RAW;
        c.shareReads = false;
        c.staggerRanges = true;
        c.rangeIntervalNanos = 100_000_000L;
        c.telemetryPeriodNanos = 200_000_000L;
        c.reuseTarget = false;
        c.shotMode = ShotMode.CACHE_UNCHANGED;
        c.cacheVelocity = false;
        c.stageTimings = false;
        c.commandProfile = CommandProfile.SMALL_CHANGES;
        c.poseProfile = PoseProfile.HEADING_ONLY;
        c.outputMode = OutputMode.SHADOW;
        c.servoLive = false;
        return c;
    }

    private static void assert_baseline(Config c) {
        check(c.bulkMode == LynxModule.BulkCachingMode.OFF && c.powerPolicy == WritePolicy.RAW
                        && c.servoPolicy == WritePolicy.RAW && !c.shareReads && c.rangeIntervalNanos == 0
                        && !c.staggerRanges && c.telemetryPeriodNanos == 0 && !c.reuseTarget
                        && c.shotMode == ShotMode.ALLOCATE && !c.cacheVelocity,
                "Baseline disables all ten application optimizations");
    }

    private static void assert_optimized(Config c) {
        check(c.bulkMode == LynxModule.BulkCachingMode.MANUAL && c.powerPolicy == WritePolicy.EPSILON
                        && c.servoPolicy == WritePolicy.EPSILON && c.shareReads
                        && c.rangeIntervalNanos == 300_000_000L && c.staggerRanges
                        && c.telemetryPeriodNanos == 100_000_000L && c.reuseTarget
                        && c.shotMode == ShotMode.REUSE && c.cacheVelocity,
                "Optimized preset enables all ten comparison settings");
    }

    private static Map<String, Trial> index_trials(List<Trial> trials) {
        Map<String, Trial> result = new HashMap<>();
        for (Trial trial : trials) {
            check(result.put(trial.label, trial) == null, "Trial labels identify unique comparisons");
        }
        return result;
    }

    private static Set<String> differences(Config a, Config b) {
        Map<String, String> left = metadata_values(a.metadata());
        Map<String, String> right = metadata_values(b.metadata());
        check(left.keySet().equals(right.keySet()), "Compared settings have matching metadata keys");
        Set<String> changed = new HashSet<>();
        for (String key : left.keySet()) if (!left.get(key).equals(right.get(key))) changed.add(key);
        return changed;
    }

    private static Map<String, String> metadata_values(String metadata) {
        Map<String, String> result = new HashMap<>();
        for (String line : metadata.split("\n")) {
            int equals = line.indexOf('=');
            if (equals < 1) throw new AssertionError("Malformed setting metadata: " + line);
            result.put(line.substring(0, equals), line.substring(equals + 1));
        }
        return result;
    }

    private static Set<String> keys(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static void add_sample(Recorder recorder, long cycle) {
        recorder.record(cycle, cycle / 2, cycle / 3, cycle / 4, cycle / 5, cycle / 6);
    }

    private static void near(double actual, double expected, String label) {
        check(Math.abs(actual - expected) <= Math.max(1.0e-10, Math.abs(expected) * 1.0e-14),
                label + ": actual=" + actual + ", expected=" + expected);
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
