package org.firstinspires.ftc.teamcode.limelight;

import java.util.List;
import java.lang.reflect.Field;

/** Contact and nonstop-dispatch traces; no sleeps or simulated endpoint pickup shortcuts. */
public final class ContinuousRunnerChecks {
    private static int checks;
    private static final VisionPose HOME = new VisionPose(40, 40, Math.PI / 2);

    private static final class Drive implements ButineRunner.Drive {
        VisionPose pose = HOME;
        List<ButineRunner.Leg> legs;
        boolean busy;
        int index, dispatches, stops, holds;
        double speed;
        public VisionPose pose() { return pose; }
        public boolean is_busy() { return busy; }
        public void follow(ButineRunner.Leg leg, double scale) { throw new AssertionError("per-leg dispatch"); }
        public void stop() { busy = false; speed = 0; stops++; }
        public void set_pose(VisionPose value) { pose = value; }
        public boolean supports_continuous() { return true; }
        public void follow_run(List<ButineRunner.Leg> value, double scale) {
            legs = value; busy = true; index = 0; dispatches++;
        }
        public int path_index() { return index; }
        public double path_fraction() { return 0; }
        public double speed_in_s() { return speed; }
        public void hold_pose(VisionPose target) { pose = target; busy = false; speed = 0; holds++; }
    }

    public static void main(String[] args) throws Exception {
        neighboring_contacts_and_precise_home();
        unexpected_fifth_contact_stops_intake();
        unsupported_drive_is_rejected();
        depot_alignment_precedes_intake();
        return_boundary_never_invents_contact();
        alignment_time_is_not_charged_twice();
        loaded_false_preserves_collection();
        loaded_sensor_replaces_collection_with_safe_home();
        loaded_sensor_cancels_pending_plan();
        loaded_sensor_interrupts_initial_alignment();
        loaded_sensor_rejects_blocked_live_pose();
        loaded_sensor_does_not_restart_an_existing_return();
        loaded_sensor_observes_current_pedro_return_boundary();
        loaded_sensor_ignores_unowned_drive();
        System.out.println("Continuous runner checks passed: " + checks);
    }

    private static Butine.Params parameters() {
        Butine.Params p = new Butine.Params();
        p.field = new ButineField(144, 18, 18, 1);
        p.lookaheadRuns = 1; p.remainingS = 100; p.scoreOverheadS = 0;
        return p;
    }

    private static PollenMap pair() {
        PollenMap map = new PollenMap();
        map.inject(new VisionPose(35, 70, 0));
        map.inject(new VisionPose(45, 70, 0));
        return map;
    }

    private static void neighboring_contacts_and_precise_home() {
        Drive drive = new Drive(); PollenMap map = pair();
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "front sweep accepted");
        require(runner.last_plan().runs[0].count == 2, "both neighbors planned as contacts");
        require(runner.legs().size() == 2, "single collection pass plus home");
        runner.update();
        require(drive.dispatches == 1 && map.snapshot().size() == 2, "one dispatch does not claim pollen");
        drive.pose = new VisionPose(40, 50, HOME.heading); drive.speed = 8;
        runner.update();
        require(map.snapshot().size() == 2, "body proximity before mouth contact is insufficient");
        for (int i = 1; i <= 16; i++) {
            ButineRunner.Leg leg = drive.legs.get(0);
            double t = (double) i / 16;
            drive.pose = new VisionPose(40 + (leg.target.x - 40) * t,
                    50 + (leg.target.y - 50) * t, HOME.heading);
            runner.update();
        }
        require(map.snapshot().isEmpty() && runner.stats().collected == 2,
                "actual front-mouth sweep removes both neighbors once");
        require(drive.busy && drive.dispatches == 1 && drive.stops == 0,
                "pickup endpoint neither stops nor dispatches another path");
        require(runner.intake_required(), "intake keeps pulling through the planned overshoot segment");
        drive.index = 1;
        drive.pose = new VisionPose(HOME.x, HOME.y + .6, HOME.heading); drive.busy = false; drive.speed = 0;
        runner.update();
        require(!runner.intake_required(), "intake is off once the return segment starts");
        require(runner.is_busy(), "Pedro completion alone cannot accept inaccurate depot position");
        drive.pose = new VisionPose(HOME.x, HOME.y, HOME.heading + Math.toRadians(3));
        runner.update(); require(runner.is_busy(), "depot heading must settle within two degrees");
        drive.pose = HOME; drive.speed = 2;
        runner.update(); require(runner.is_busy(), "moving through depot does not permit scoring");
        drive.speed = 0; runner.update();
        require(runner.state() == ButineRunner.State.COMPLETE && runner.stats().runsCompleted == 1,
                "precise stationary depot completes the continuous run");
        require(drive.dispatches == 1 && drive.stops == 0, "successful run leaves Pedro holding without intermediate stops");
    }

    private static void unexpected_fifth_contact_stops_intake() {
        Drive drive = new Drive(); PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 70, 0));
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "single target initially planned");
        runner.update();
        for (int x : new int[]{32, 36, 44, 48}) map.inject(new VisionPose(x, 70, 0));
        drive.pose = drive.legs.get(0).target; drive.speed = 5;
        runner.update();
        require(runner.state() == ButineRunner.State.ABORTED && !runner.intake_required(),
                "unexpected fifth live contact disables intake and aborts");
        require(drive.stops == 1 && runner.stats().collected == 0,
                "overflow is never silently truncated or recorded as a legal four");
    }

    private static void unsupported_drive_is_rejected() {
        ButineRunner.Drive old = new ButineRunner.Drive() {
            public VisionPose pose() { return HOME; }
            public boolean is_busy() { return false; }
            public void follow(ButineRunner.Leg leg, double scale) { throw new AssertionError("legacy fallback"); }
            public void stop() { }
            public void set_pose(VisionPose pose) { }
        };
        ButineRunner runner = new ButineRunner(pair(), old);
        require(!runner.run_butine(HOME, parameters(), 1), "continuous plan cannot fall back to stopping per-leg drive");
    }

    private static void depot_alignment_precedes_intake() {
        Drive drive = new Drive(); PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 16.5, 0));
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "wall pollen reachable with a different depot orientation");
        require(!runner.intake_required(), "intake stays off before depot alignment");
        runner.update();
        require(drive.holds == 1 && drive.dispatches == 0 && map.size() == 1,
                "orientation changes in place at depot before any pickup claim");
        runner.update();
        require(drive.dispatches == 1 && runner.intake_required(), "aligned collection starts one continuous path");
        for (int segment = 0; segment < drive.legs.size(); segment++) {
            drive.index = segment;
            ButineRunner.Leg leg = drive.legs.get(segment);
            for (int step = 1; step <= 12; step++) {
                double t = step / 12.0;
                drive.pose = new VisionPose(leg.from.x + (leg.target.x - leg.from.x) * t,
                        leg.from.y + (leg.target.y - leg.from.y) * t,
                        leg.from.heading + VisionPose.wrap_pi(leg.target.heading - leg.from.heading) * t);
                drive.busy = segment != drive.legs.size() - 1 || step != 12;
                drive.speed = drive.busy ? 3 : 0;
                runner.update();
            }
        }
        require(map.size() == 0 && runner.stats().collected == 1
                && runner.state() == ButineRunner.State.COMPLETE, "offset path indices survive initial depot alignment");
        require(drive.dispatches == 1 && drive.stops == 0, "alignment introduces no stops along collection paths");
    }

    private static void return_boundary_never_invents_contact() {
        Drive drive = new Drive(); PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 70, 0));
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "boundary test plans a real pollen");
        runner.update();
        drive.index = drive.legs.size() - 1;
        VisionPose planned = drive.legs.get(0).target;
        drive.pose = new VisionPose(planned.x + 20, planned.y, planned.heading);
        runner.update();
        require(map.size() == 1 && runner.stats().collected == 0,
                "path progress cannot replace actual motion with a fictional planned pickup vertex");
        require(!runner.intake_required(), "missed contact still shuts intake off for return");
        runner.stop();
    }

    private static void alignment_time_is_not_charged_twice() throws Exception {
        Drive drive = new Drive(); PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 16.5, 0));
        Butine.Params p = parameters();
        Butine.Plan estimate = Butine.solve(map.snapshot(), HOME, HOME, p);
        p.remainingS = estimate.runs[0].timeS + .2;
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, p, 1), "tight but fitting aligned run accepted");
        runner.update();
        double alignment = runner.legs().get(0).predictedS;
        require(drive.holds == 1 && alignment > .1, "test includes a modeled depot rotation");
        long began = System.nanoTime() - (long) (alignment * 1e9);
        for (String name : new String[]{"runStartedNanos", "legStartedNanos"}) {
            Field timer = ButineRunner.class.getDeclaredField(name);
            timer.setAccessible(true); timer.setLong(runner, began);
        }
        runner.update();
        require(drive.dispatches == 1 && runner.state() == ButineRunner.State.DRIVING,
                "completed alignment is deducted from the remaining prediction exactly once");
        runner.stop();
    }

    private static void loaded_false_preserves_collection() {
        Drive drive = new Drive(); PollenMap map = pair();
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "sensor-false collection starts");
        require(!runner.return_to_depot_if_loaded(false), "false sensor does not request a return");
        runner.update();
        List<ButineRunner.Leg> original = drive.legs;
        require(!runner.return_to_depot_if_loaded(false), "false sensor leaves active collection alone");
        runner.update();
        require(runner.state() == ButineRunner.State.DRIVING && runner.intake_required()
                        && drive.dispatches == 1 && drive.stops == 0 && drive.legs == original,
                "false sensor preserves the collection path and intake state");
        require(map.size() == 2 && runner.stats().collected == 0, "false sensor invents no contacts");
        runner.stop();
    }

    private static void loaded_sensor_replaces_collection_with_safe_home() {
        Drive drive = new Drive(); PollenMap map = pair();
        Butine.Params p = parameters();
        p.field = new ButineField(144, 18, 18, 1, new ButineField.Box(60, 70, 20, 90));
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, p, 1), "loaded-interrupt collection starts");
        runner.update();
        List<ButineRunner.Leg> abandoned = drive.legs;
        VisionPose live = new VisionPose(105, 70, 0.4);
        drive.pose = live; drive.speed = 9;
        require(!p.field.segment_clear(live, HOME), "loaded return fixture requires a real detour");
        require(runner.return_to_depot_if_loaded(true), "first loaded observation interrupts collection");
        require(drive.stops == 1 && !runner.intake_required(), "loaded observation immediately stops old drive and intake");
        require(runner.state() == ButineRunner.State.DRIVING && map.size() == 2
                        && runner.stats().collected == 0, "sensor return keeps unreached pollen in the map");
        runner.update();
        require(drive.dispatches == 2 && drive.legs != abandoned && drive.legs.size() >= 3,
                "old path is replaced once with an obstacle detour");
        require(drive.legs.get(0).from.distance_to(live) < 1e-9,
                "sensor return begins at the live pose instead of the old pickup waypoint");
        int stopped = drive.stops, dispatched = drive.dispatches;
        for (int sample = 0; sample < 4; sample++) {
            require(!runner.return_to_depot_if_loaded(true), "latched loaded sensor is idempotent");
            runner.update();
        }
        require(drive.stops == stopped && drive.dispatches == dispatched,
                "repeated loaded observations do not stop or redispatch the return");
        require(runner.stats().sensorReturns == 1, "full-load sensor counts one installed return");
        finish_loaded_return(runner, drive, p.field, 2);
        require(map.size() == 2 && runner.stats().collected == 0,
                "loaded-sensor completion does not claim visually unreached pollen");
        require(!runner.return_to_depot_if_loaded(true), "loaded sensor after completion has no action");
    }

    private static void loaded_sensor_cancels_pending_plan() {
        ButineWorker worker = new ButineWorker(); worker.preferredCore = -1;
        require(worker.start(), "worker starts for loaded planning cancellation");
        try {
            Drive drive = new Drive(); drive.pose = new VisionPose(60, 40, HOME.heading);
            PollenMap map = pair();
            ButineRunner runner = new ButineRunner(map, drive, worker);
            require(runner.run_butine(HOME, parameters(), 1)
                            && runner.state() == ButineRunner.State.PLANNING,
                    "sensor fixture has a pending asynchronous collection plan");
            long originalRequest = worker.stats().latestRequestId;
            require(runner.return_to_depot_if_loaded(true), "loaded sensor interrupts PLANNING immediately");
            require(worker.stats().latestRequestId > originalRequest && worker.latest_result() == null,
                    "loaded sensor invalidates pending and already-published old request results");
            require(runner.state() == ButineRunner.State.DRIVING && !runner.intake_required(),
                    "planning cancellation installs intake-off home without waiting for a solver result");
            runner.update();
            require(drive.dispatches == 1, "only sensor return is dispatched after planning cancellation");
            finish_loaded_return(runner, drive, parameters().field, 1);
            require(map.size() == 2 && runner.stats().collected == 0,
                    "cancelled planner never dispatches or claims its former targets");
        } finally { require(worker.stop(), "worker stops after loaded cancellation test"); }
    }

    private static void loaded_sensor_rejects_blocked_live_pose() {
        Drive drive = new Drive(); PollenMap map = pair();
        Butine.Params p = parameters();
        p.field = new ButineField(144, 18, 18, 1, new ButineField.Box(100, 110, 60, 80));
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, p, 1), "blocked sensor return fixture starts clear");
        runner.update();
        drive.pose = new VisionPose(105, 70, HOME.heading);
        runner.return_to_depot_if_loaded(true);
        require(runner.state() == ButineRunner.State.ABORTED && !runner.intake_required()
                        && drive.stops > 0 && drive.dispatches == 1,
                "blocked live origin stops safely instead of inventing a sensor return");
        require(map.size() == 2 && runner.stats().collected == 0, "blocked sensor return never removes targets");
    }

    private static void loaded_sensor_interrupts_initial_alignment() {
        Drive drive = new Drive(); PollenMap map = new PollenMap();
        map.inject(new VisionPose(40, 16.5, 0));
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "loaded alignment fixture starts");
        runner.update();
        require(drive.holds == 1 && drive.dispatches == 0 && !runner.intake_required(),
                "sensor can become loaded during initial intake-off alignment");
        require(runner.return_to_depot_if_loaded(true), "initial alignment is still interruptible collection");
        runner.update();
        require(drive.dispatches == 1 && !runner.intake_required(),
                "initial alignment replacement dispatches only an intake-off home path");
        finish_loaded_return(runner, drive, parameters().field, 1);
        require(map.size() == 1 && runner.stats().collected == 0,
                "alignment interruption leaves its unreached wall pollen untouched");
    }

    private static void loaded_sensor_does_not_restart_an_existing_return() {
        Drive drive = new Drive(); PollenMap map = pair();
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "normal-return loaded fixture starts");
        runner.update();
        drive.pose = drive.legs.get(0).target; runner.update();
        drive.index = drive.legs.size() - 1; runner.update();
        require(!runner.intake_required() && runner.is_busy(), "normal collection is already returning intake-off");
        List<ButineRunner.Leg> returning = drive.legs;
        int stopped = drive.stops, dispatched = drive.dispatches;
        require(!runner.return_to_depot_if_loaded(true), "loaded sensor does not replace an existing depot return");
        runner.update();
        require(drive.legs == returning && drive.stops == stopped && drive.dispatches == dispatched,
                "already-returning path remains uninterrupted");
        runner.stop();
    }

    private static void loaded_sensor_ignores_unowned_drive() {
        Drive drive = new Drive(); drive.busy = true; drive.pose = new VisionPose(Double.NaN, 0, 0);
        ButineRunner runner = new ButineRunner(pair(), drive);
        require(!runner.return_to_depot_if_loaded(true) && runner.state() == ButineRunner.State.IDLE,
                "loaded sensor outside a collection does not validate or take over an unrelated drive");
        require(drive.busy && drive.stops == 0 && drive.dispatches == 0,
                "unowned busy drive is unchanged even if its pose is unavailable");
    }

    private static void loaded_sensor_observes_current_pedro_return_boundary() {
        Drive drive = new Drive(); PollenMap map = pair();
        ButineRunner runner = new ButineRunner(map, drive);
        require(runner.run_butine(HOME, parameters(), 1), "Pedro return-boundary sensor fixture starts");
        runner.update();
        drive.pose = drive.legs.get(0).target; runner.update();
        List<ButineRunner.Leg> returning = drive.legs;
        drive.index = drive.legs.size() - 1;
        int stopped = drive.stops, dispatched = drive.dispatches;
        require(!runner.return_to_depot_if_loaded(true),
                "Pedro's current return part is preserved before the next runner update");
        require(!runner.intake_required() && drive.legs == returning && drive.stops == stopped,
                "full signal on the return boundary disables intake without stopping the existing home path");
        runner.update();
        require(drive.dispatches == dispatched && drive.legs == returning,
                "sensor-first loop ordering cannot redispatch an already-active return");
        runner.stop();
    }

    private static void finish_loaded_return(ButineRunner runner, Drive drive,
                                             ButineField field, int dispatches) {
        require(drive.legs != null && !drive.legs.isEmpty(), "loaded return has executable legs");
        for (int segment = 0; segment < drive.legs.size(); segment++) {
            ButineRunner.Leg leg = drive.legs.get(segment);
            require(!leg.intakeActive && leg.kind != ButineRunner.Leg.Kind.PICKUP && leg.pollenId == -1,
                    "loaded return contains only intake-off transit and depot legs");
            require(field.segment_clear(leg.from, leg.target), "each loaded return segment is geometrically clear");
            drive.index = segment; drive.pose = leg.target;
            drive.busy = segment != drive.legs.size() - 1;
            drive.speed = drive.busy ? 4 : 0;
            if (!drive.busy) {
                drive.pose = new VisionPose(HOME.x, HOME.y + .6, HOME.heading);
                runner.update();
                require(runner.state() == ButineRunner.State.DRIVING,
                        "loaded sensor cannot waive precise depot position");
                drive.pose = new VisionPose(HOME.x, HOME.y, HOME.heading + Math.toRadians(3));
                runner.update();
                require(runner.state() == ButineRunner.State.DRIVING,
                        "loaded sensor cannot waive precise depot heading");
                drive.pose = HOME; drive.speed = 2;
                runner.update();
                require(runner.state() == ButineRunner.State.DRIVING,
                        "loaded sensor cannot score while moving through the depot");
                drive.speed = 0;
            }
            runner.update();
            require(!runner.intake_required(), "loaded return never restarts intake");
        }
        require(drive.pose.distance_to(HOME) < 1e-9 && runner.state() == ButineRunner.State.COMPLETE,
                "loaded sensor permits precise depot completion without a visual contact count");
        require(drive.dispatches == dispatches, "loaded return does not dispatch an abandoned collection result");
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
