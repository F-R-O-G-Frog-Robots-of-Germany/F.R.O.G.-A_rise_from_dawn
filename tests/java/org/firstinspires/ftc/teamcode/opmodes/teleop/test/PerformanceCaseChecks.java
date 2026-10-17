package org.firstinspires.ftc.teamcode.opmodes.teleop.test;

import com.qualcomm.hardware.lynx.LynxModule;
import java.lang.reflect.Method;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.CaseSelector;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.Config;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.ShotMode;
import org.firstinspires.ftc.teamcode.opmodes.teleop.test.PerformanceComparison.WritePolicy;

/** Fixed user-requested cycle and independent, single-optimization configuration checks. */
public final class PerformanceCaseChecks {
    private static int checks;
    private static final int[] EXPECTED_ORDER = {5, 1, 4, 2, 7, 3, 8, 9, 6, 10, 0};

    public static void main(String[] args) {
        fixed_cycle();
        case_configs();
        snapshots();
        no_recording_rank();
        System.out.println("Passed " + checks + " performance case checks");
    }

    private static void fixed_cycle() {
        CaseSelector selector = new CaseSelector();
        check(CaseSelector.CASE_COUNT == EXPECTED_ORDER.length, "Ten optimizations plus baseline");
        String metadata = selector.order_metadata();
        for (int pass = 0; pass < 3; pass++) {
            for (int expected : EXPECTED_ORDER) {
                check(selector.index == expected, "Exact fixed estimate order, pass " + pass);
                check(selector.label().contains(expected == 0 ? "BASELINE" : "P" + expected + " "),
                        "The displayed label identifies the selected case");
                check(selector.ranking_detail().contains("ESTIMATED"), "Order never claims a measured rank");
                selector.config();
                check(metadata.equals(selector.order_metadata()), "Configuration cannot change selection order");
                selector.next();
            }
        }
        check(selector.index == 5, "A complete cycle wraps to P5");
        for (int press = 0; press < 10; press++) selector.next();
        check(selector.index == 0, "Ten presses from the first optimization select baseline");
        check(metadata.contains("P5,P1,P4,P2,P7,P3,P8,P9,P6,P10,BASELINE"),
                "Export preserves the fixed estimated order");
        check(metadata.contains("selection_order_changes_with_recordings=false"),
                "Export explicitly states measurements cannot change order");
    }

    private static void case_configs() {
        CaseSelector selector = new CaseSelector();
        for (int id = 0; id <= 10; id++) {
            selector.index = id;
            Config actual = selector.config();
            check(actual.bulkMode == (id == 1 ? LynxModule.BulkCachingMode.MANUAL
                    : LynxModule.BulkCachingMode.OFF), "P1 alone enables manual caching, case " + id);
            check(actual.powerPolicy == (id == 2 ? WritePolicy.EPSILON : WritePolicy.RAW),
                    "P2 alone suppresses power writes, case " + id);
            check(actual.servoPolicy == (id == 3 ? WritePolicy.EPSILON : WritePolicy.RAW),
                    "P3 alone suppresses servo writes, case " + id);
            check(actual.shareReads == (id == 4), "P4 alone shares reads, case " + id);
            check(actual.rangeIntervalNanos == (id == 5 || id == 6 ? 300_000_000L : 0L),
                    "P5 timed reads are also the explicit P6 prerequisite, case " + id);
            check(actual.staggerRanges == (id == 6), "P6 alone staggers reads, case " + id);
            check(actual.telemetryPeriodNanos == (id == 7 ? 100_000_000L : 0L),
                    "P7 alone throttles telemetry, case " + id);
            check(actual.reuseTarget == (id == 8), "P8 alone reuses targets, case " + id);
            check(actual.shotMode == (id == 9 ? ShotMode.REUSE : ShotMode.ALLOCATE),
                    "P9 alone reuses shot results, case " + id);
            check(actual.cacheVelocity == (id == 10), "P10 alone caches velocity targets, case " + id);
            Config defaults = new Config();
            check(actual.outputMode == defaults.outputMode && actual.servoLive == defaults.servoLive
                    && actual.stageTimings == defaults.stageTimings
                    && actual.commandProfile == defaults.commandProfile
                    && actual.poseProfile == defaults.poseProfile,
                    "Case selection does not change the recording workload, case " + id);
        }
        selector.index = 6;
        check(selector.label().contains("P5") && selector.label().contains("prerequisite"),
                "The P6 dependency is displayed clearly");
        selector.index = 10;
        check(selector.label().contains("inactive") && selector.ranking_detail().contains("N/A"),
                "P10 explicitly discloses inapplicability to the custom power controller");
    }

    private static void snapshots() {
        CaseSelector selector = new CaseSelector();
        Config p5 = selector.config();
        Config independent = selector.config();
        check(p5 != independent, "Every case configuration is a fresh snapshot");
        p5.rangeIntervalNanos = -1;
        p5.shareReads = true;
        check(selector.config().rangeIntervalNanos == 300_000_000L && !selector.config().shareReads,
                "Mutating an old snapshot cannot alter the active case");
        selector.next();
        check(independent.rangeIntervalNanos == 300_000_000L
                && independent.bulkMode == LynxModule.BulkCachingMode.OFF,
                "Advancing selection cannot alter an earlier recording's configuration");
    }

    private static void no_recording_rank() {
        for (Method method : CaseSelector.class.getDeclaredMethods()) {
            String name = method.getName();
            check(!name.equals("record_result") && !name.equals("rank_cases")
                    && !name.equals("save_ranking") && !name.equals("load_ranking"),
                    "Recorded results have no route to reorder fixed cases: " + name);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
