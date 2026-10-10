import com.qualcomm.hardware.limelightvision.*;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.navigation.*;
import org.firstinspires.ftc.teamcode.limelight.*;
import java.lang.reflect.Field;

/** Production state machines exercised against explicit build-only SDK stand-ins. */
public final class CameraRunnerAudit {
    static int checks;
    static final VisionPose CENTER = new VisionPose(72,72,0);
    static final class Drive implements ButineRunner.Drive {
        VisionPose pose=CENTER; boolean busy,failSet; int applied;
        public VisionPose pose(){return pose;}
        public boolean is_busy(){return busy;}
        public void follow(ButineRunner.Leg leg,double scale){busy=true;}
        public void stop(){busy=false;}
        public void set_pose(VisionPose fix){if(failSet)throw new IllegalStateException("pose failure");pose=fix;applied++;}
    }
    static final class Session {
        Limelight3A device=new Limelight3A(); HardwareMap hardware=new HardwareMap();
        Drive drive=new Drive(); Limelight camera; PollenMap map; LimelightRunner runner;
        Session(){hardware.device=device;camera=new Limelight(hardware);map=new PollenMap(camera);
            runner=new LimelightRunner(camera,map,drive);require(runner.init(),"vision runner initializes");}
        void fix(double xMeters,double yMeters,double yaw){
            LLResult result=new LLResult();result.pipeline=1;result.tags=1;
            result.botpose=new Pose3D(new Position(DistanceUnit.METER,xMeters,yMeters,0,0),
                    new YawPitchRollAngles(AngleUnit.DEGREES,yaw,0,0,0));device.result=result;
        }
    }
    public static void main(String[] args)throws Exception{
        cached_expiry(); relocalization_matrix(); motion_and_deadline(); failure_cleanup(); snapshots();
        System.out.println("Camera runner audit checks passed: "+checks);
    }
    static void cached_expiry()throws Exception{
        Session s=new Session();s.device.result=new LLResult();
        s.device.result.detectors.add(new LLResultTypes.DetectorResult());s.camera.update(CENTER);
        require(s.camera.has_target()&&s.camera.has_pollen_frame(),"fresh detector getters expose result");
        past_ms(s.camera,"lastResultReadMs",251);
        require(!s.camera.has_target()&&!s.camera.has_pollen_frame(),"cached detector getters expire without polling");
        s.camera.set_pipeline(1);s.fix(0,0,0);s.camera.update(CENTER);
        require(s.camera.get_pose()!=null,"fresh tag getter exposes pose");past_ms(s.camera,"lastResultReadMs",251);
        require(s.camera.get_pose()==null,"cached tag getter expires without polling");
        s.camera.set_pipeline(0);s.device.result=new LLResult();s.camera.update(CENTER);
        past_ms(s.camera,"publishedFrameMs",251);
        require(s.camera.has_target()&&!s.camera.has_pollen_frame(),"publication age independently gates detector evidence");
    }
    static void relocalization_matrix()throws Exception{
        for(int scenario=0;scenario<6;scenario++){
            Session s=new Session();s.drive.pose=new VisionPose(70,72,0);
            require(s.runner.relocalize(12,.6,.2),"starts stationary tag lookup");
            require(!s.runner.relocalize(),"active correction cannot be replaced");
            if(scenario==0)s.fix(0,0,0);                    // (72,72), jump 2, accepted
            if(scenario==1)s.fix((82-72)*.0254,0,0);       // jump exactly 12, accepted
            if(scenario==2)s.fix((82.001-72)*.0254,0,0);   // jump just over 12, rejected
            if(scenario==3)s.fix((151-72)*.0254,0,0);      // outside field + six inch margin
            if(scenario==4){s.fix(0,0,0);s.drive.busy=true;}
            if(scenario==5){s.fix(0,0,0);s.device.result.tags=0;}
            s.runner.update();
            require(s.drive.applied==(scenario<=1?1:0),"computed correction outcome "+scenario);
            if(scenario==2)require(s.runner.stats().rejectedJump==1,"jump rejection counted");
            if(scenario==3)require(s.runner.stats().rejectedBounds==1,"bounds rejection counted");
            if(scenario==4)require(s.runner.stats().rejectedMoving==1,"motion rejection counted");
            if(scenario==5){past_nanos(s.runner,"startedNanos",.7);s.runner.update();
                require(s.runner.stats().noSolve==1,"no-tag solve times out");}
            require(s.camera.pipeline()==0&&s.runner.is_busy(),"detector restored during settling");
            past_nanos(s.runner,"startedNanos",.3);s.runner.update();
            require(!s.runner.is_busy(),"settling expires without blocking");s.runner.stop();
        }
        Session s=new Session();s.drive.busy=true;require(!s.runner.relocalize(),"busy start rejected");
        s.drive.busy=false;s.drive.pose=null;require(!s.runner.relocalize(),"invalid origin rejected");
        s.drive.pose=CENTER;s.device.connected=false;require(!s.runner.relocalize(),"disconnected camera rejected");
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}){
            boolean rejected=false;try{s.runner.relocalize(value,.6,.2);}catch(IllegalArgumentException expected){rejected=true;}
            require(rejected,"invalid correction limit "+value);
        }
    }
    static void motion_and_deadline()throws Exception{
        Session s=new Session();s.runner.relocalize();
        s.drive.pose=new VisionPose(73,72,0);s.runner.update();
        s.drive.pose=CENTER;s.fix(0,0,0);s.runner.update();
        require(s.drive.applied==0&&s.runner.stats().rejectedMoving==1,"out-and-back motion remains rejected");
        s=new Session();s.runner.relocalize(12,0,.2);s.fix(0,0,0);s.runner.update();
        require(s.drive.applied==0&&s.runner.stats().noSolve==1,"zero look window cannot accept late pose");
        s=new Session();s.runner.relocalize();past_nanos(s.runner,"startedNanos",.7);s.fix(0,0,0);s.runner.update();
        require(s.drive.applied==0&&s.runner.stats().noSolve==1,"fresh result after deadline is rejected");
    }
    static void failure_cleanup(){
        Session s=new Session();s.runner.relocalize();s.fix(0,0,0);s.drive.failSet=true;
        boolean thrown=false;try{s.runner.update();}catch(IllegalStateException expected){thrown=true;}
        require(thrown&&s.camera.pipeline()==0,"odometry failure restores detector before propagation");
        s=new Session();s.device.switchOk=false;require(!s.runner.relocalize()&&!s.runner.is_busy(),"failed switch cannot enter lookup");
        s=new Session();s.runner.relocalize();s.fix(0,0,0);s.device.switchOk=false;s.runner.update();
        require(!s.camera.is_connected()&&s.runner.last_fix().contains("restore failed"),"failed restore stops camera explicitly");
    }
    static void snapshots()throws Exception{
        Session s=new Session();SnapshotTrainer trainer=new SnapshotTrainer(s.camera);
        require(trainer.take_snapshot()&&trainer.image_count()==1,"manual snapshot counted");
        String first=trainer.last_filename();s.device.failSnapshot=true;
        require(!trainer.take_snapshot()&&trainer.failed_count()==1&&!first.equals(trainer.last_filename()),"failed attempt consumes unique name");
        s.device.failSnapshot=false;trainer.set_auto_capture(true);trainer.update();
        require(trainer.image_count()==1,"auto capture waits two seconds");
        past_nanos(trainer,"lastCaptureNanos",2.1);trainer.update();
        require(trainer.image_count()==2,"auto capture fires once at elapsed deadline");trainer.update();
        require(trainer.image_count()==2,"same loop cannot burst duplicate captures");
        trainer.set_auto_capture(false);past_nanos(trainer,"lastCaptureNanos",3);trainer.update();
        require(trainer.image_count()==2,"disabled auto capture stays idle");
        require(!trainer.session_id().equals(new SnapshotTrainer(s.camera).session_id()),"sessions have distinct image namespaces");
    }
    static void past_nanos(Object owner,String name,double seconds)throws Exception{set(owner,name,System.nanoTime()-(long)(seconds*1e9));}
    static void past_ms(Object owner,String name,long ms)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.setLong(owner,f.getLong(owner)-ms);}
    static void set(Object owner,String name,long value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.setLong(owner,value);}
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
