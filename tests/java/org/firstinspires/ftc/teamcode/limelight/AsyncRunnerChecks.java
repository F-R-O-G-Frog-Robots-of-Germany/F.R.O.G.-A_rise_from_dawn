package org.firstinspires.ftc.teamcode.limelight;

/** Real worker handoff tests: only the caller can touch the drive. No robot hardware. */
public final class AsyncRunnerChecks {
    private static int checks;
    private static final VisionPose HOME = new VisionPose(20,20,0);
    private static final class Drive implements ButineRunner.Drive {
        final Thread owner = Thread.currentThread();
        VisionPose pose = HOME;
        boolean busy;
        boolean continuous;
        int dispatched;
        ButineRunner.Leg leg;
        void ownership() { if (Thread.currentThread() != owner) throw new AssertionError("worker touched drive"); }
        public VisionPose pose() { ownership(); return pose; }
        public boolean is_busy() { ownership(); return busy; }
        public void follow(ButineRunner.Leg next, double scale) { ownership(); dispatched++; leg=next; busy=true; }
        public void stop() { ownership(); busy=false; }
        public void set_pose(VisionPose value) { ownership(); pose=value; }
        public boolean supports_continuous() { ownership(); return continuous; }
        public void follow_run(java.util.List<ButineRunner.Leg> legs, double scale) {
            ownership(); dispatched++; leg=legs.get(legs.size()-1); busy=true;
        }
        void arrive() { pose=leg.target; busy=false; }
    }
    private static Butine.Params params() {
        Butine.Params p=new Butine.Params();p.capacity=1;p.lookaheadRuns=1;
        p.sweepIntake=false; // These traces exercise legacy per-leg recovery; continuous handoff has integration coverage.
        p.iterationCap=0;p.localSearchPasses=0;return p;
    }
    private static PollenMap map() { PollenMap map=new PollenMap();map.inject(new VisionPose(40,20,0));return map; }
    private static void await(ButineRunner runner) throws Exception {
        long deadline=System.nanoTime()+3_000_000_000L;
        while (runner.state()==ButineRunner.State.PLANNING) {
            runner.update();
            if(System.nanoTime()>deadline) throw new AssertionError("planning did not finish");
            Thread.sleep(1);
        }
    }
    private static final class AutoTimer implements java.util.function.DoubleSupplier {
        final Thread owner=Thread.currentThread();
        double elapsed;
        int samples;
        AutoTimer(double elapsed) { this.elapsed=elapsed; }
        public double getAsDouble() {
            require(Thread.currentThread()==owner,"official timer is sampled only by the caller, never the planner worker");
            samples++;
            return elapsed;
        }
    }
    private static PollenMap two_targets() {
        PollenMap map=map();map.inject(new VisionPose(60,20,0));return map;
    }
    private static void official_timer_handoff(ButineWorker worker) throws Exception {
        Drive drive=new Drive();drive.continuous=true;
        ButineRunner runner=new ButineRunner(two_targets(),drive,worker);
        AutoTimer timer=new AutoTimer(24.9);runner.set_auto_timer(timer);
        Butine.Params physical=new Butine.Params();physical.capacity=1;physical.lookaheadRuns=2;
        physical.remainingS=100;physical.elapsedAutoS=99;
        require(runner.run_butine(HOME,physical,1),"physical asynchronous solve accepts the official elapsed timer");
        physical.elapsedAutoS=1000;timer.elapsed=25;
        await(runner);
        require(timer.samples==1&&runner.last_plan().planningHorizonRuns==2&&runner.last_plan().runCount==2,
                "worker receives the copied 24.9-second snapshot despite later timer/parameter mutation");
        require(drive.dispatched==0,"hypothetical second run never dispatches while accepting the plan");
        runner.stop();
        physical.elapsedAutoS=0;
        require(runner.run_butine(HOME,physical,1),"next request samples the same official timer at twenty-five seconds");
        await(runner);
        require(timer.samples==2&&runner.last_plan().planningHorizonRuns==1&&runner.last_plan().runCount==1,
                "official timer at exactly twenty-five switches the worker to one-run greedy");
        require(physical.remainingS==100,"elapsed cutoff is not inferred from a thirty-second remaining allowance");
        runner.stop();

        drive=new Drive();runner=new ButineRunner(two_targets(),drive,worker);
        timer=new AutoTimer(24.9);runner.set_auto_timer(timer);
        require(runner.run_butine(HOME,params(),1),"legacy recovery trace starts before the elapsed cutoff");
        await(runner);runner.update();runner.update();
        long firstRequest=worker.stats().latestRequestId;
        timer.elapsed=25;drive.busy=false;runner.update();
        require(timer.samples==2&&runner.state()==ButineRunner.State.PLANNING
                        &&worker.stats().latestRequestId>firstRequest,
                "a real missed-pickup replan re-samples the official timer after crossing twenty-five");
        await(runner);
        require(runner.state()==ButineRunner.State.DRIVING&&runner.last_plan()!=null,
                "re-sampled timer is accepted through the normal asynchronous replan handoff");
        runner.stop();

        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}) {
            drive=new Drive();drive.continuous=true;
            runner=new ButineRunner(two_targets(),drive,worker);
            timer=new AutoTimer(invalid);runner.set_auto_timer(timer);
            require(!runner.run_butine(HOME,physical,1)&&drive.dispatched==0&&!runner.intake_required()
                            &&runner.state()==ButineRunner.State.IDLE,
                    "invalid official timer safely declines collection without dispatch or intake");
        }
        drive=new Drive();runner=new ButineRunner(two_targets(),drive,worker);
        timer=new AutoTimer(24.9);runner.set_auto_timer(timer);
        require(runner.run_butine(HOME,params(),1),"invalid-replan trace begins with a valid official timer");
        await(runner);runner.update();runner.update();
        firstRequest=worker.stats().latestRequestId;
        timer.elapsed=Double.NaN;drive.busy=false;runner.update();
        require(worker.stats().latestRequestId==firstRequest&&timer.samples==2,
                "invalid timer during a real replan never submits another planner request");
        runner.update();
        require(runner.state()==ButineRunner.State.COMPLETE&&drive.dispatched==1&&!runner.intake_required(),
                "invalid timer recovery returns at the existing home without another pickup dispatch");
    }
    public static void main(String[] args) throws Exception {
        ButineWorker worker=new ButineWorker();worker.preferredCore=-1;
        require(worker.start(),"planner starts");
        try {
            Drive drive=new Drive();PollenMap map=map();ButineRunner runner=new ButineRunner(map,drive,worker);
            require(runner.run_butine(HOME,params(),1),"request accepted");
            require(runner.state()==ButineRunner.State.PLANNING&&runner.is_busy()&&drive.dispatched==0,
                    "accepted request waits without dispatch");
            await(runner);
            require(runner.state()==ButineRunner.State.DRIVING&&runner.stats().runsStarted==1,"valid plan accepted once");
            runner.update();runner.update();drive.arrive();runner.update();
            require(map.snapshot().isEmpty()&&runner.stats().collected==1,"pickup stays on caller");
            runner.update();runner.update();drive.arrive();runner.update();
            require(runner.state()==ButineRunner.State.COMPLETE&&runner.stats().runsCompleted==1,"home completes");

            drive=new Drive();map=map();runner=new ButineRunner(map,drive,worker);
            runner.run_butine(HOME,params(),1);await(runner);runner.update();runner.update();
            drive.busy=false;runner.update();
            require(runner.state()==ButineRunner.State.PLANNING&&runner.stats().replans==1,
                    "missed pickup requests one background replan");
            await(runner);runner.update();
            require(runner.state()==ButineRunner.State.COMPLETE&&map.snapshot().size()==1
                    &&runner.stats().collected==0,"empty replan returns home without claiming failed pickup");

            drive=new Drive();map=map();runner=new ButineRunner(map,drive,worker);
            runner.run_butine(HOME,params(),1);map.clear();await(runner);
            require(runner.state()==ButineRunner.State.IDLE&&drive.dispatched==0,"removed targets invalidate result");
            drive=new Drive();runner=new ButineRunner(map(),drive,worker);
            runner.run_butine(HOME,params(),1);drive.pose=new VisionPose(23,20,0);await(runner);
            require(runner.state()==ButineRunner.State.IDLE&&drive.dispatched==0,"moved start invalidates result");

            drive=new Drive();runner=new ButineRunner(map(),drive,worker);
            runner.run_butine(HOME,params(),1);runner.stop();
            for(int i=0;i<20;i++){runner.update();Thread.sleep(1);}
            require(runner.state()==ButineRunner.State.ABORTED&&drive.dispatched==0,"cancel cannot dispatch later publication");
            drive=new Drive();runner=new ButineRunner(new PollenMap(),drive,worker);
            runner.run_butine(HOME,params(),1);await(runner);
            require(runner.state()==ButineRunner.State.IDLE&&runner.stats().runsStarted==0,"empty asynchronous plan is no completed run");

            drive=new Drive();runner=new ButineRunner(map(),drive,worker);
            runner.run_butine(HOME,params(),1);drive.pose=null;runner.update();
            require(runner.state()==ButineRunner.State.ABORTED,"invalid localization aborts planning");
            drive=new Drive();runner=new ButineRunner(map(),drive,worker);
            runner.run_butine(HOME,params(),1);worker.cancel_pending();runner.update();
            require(runner.state()==ButineRunner.State.ABORTED,"superseded request cannot wait forever");
            drive=new Drive();runner=new ButineRunner(map(),drive,worker);
            Butine.Params expired=params();expired.remainingS=0;
            runner.run_butine(HOME,expired,1);runner.update();
            require(runner.state()==ButineRunner.State.ABORTED&&drive.dispatched==0,"planning respects expired match time");
            runner.stop();
            official_timer_handoff(worker);
        } finally { require(worker.stop(),"worker cleaned up"); }
        ButineRunner stopped=new ButineRunner(map(),new Drive(),worker);
        require(!stopped.run_butine(HOME,params(),1),"stopped planner declines requests");
        System.out.println("Async runner checks passed: "+checks);
    }
    private static void require(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
}
