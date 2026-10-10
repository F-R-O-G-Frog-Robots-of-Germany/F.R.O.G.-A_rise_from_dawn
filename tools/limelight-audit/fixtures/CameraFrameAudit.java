import com.qualcomm.hardware.limelightvision.*;
import com.qualcomm.robotcore.hardware.HardwareMap;
import org.firstinspires.ftc.robotcore.external.navigation.*;
import org.firstinspires.ftc.teamcode.limelight.*;
import java.util.*;

/** Build-only SDK stand-ins feed actual production Limelight.update; no robot calls. */
public final class CameraFrameAudit {
    static int checks; static final VisionPose POSE=new VisionPose(20,20,0);
    public static void main(String[] args) {
        lifecycle(); frame_matrix(); calibration_and_pose(); limits();
        System.out.println("Camera frame audit checks passed: "+checks);
    }
    static void lifecycle(){
        HardwareMap hardware=new HardwareMap(); Limelight3A device=new Limelight3A();hardware.device=device;
        Limelight camera=new Limelight(hardware);require(camera.init(),"init succeeds");
        require(device.started==1&&device.pollRate==100&&device.pipeline==0,"polling lifecycle");
        require(camera.capture_snapshot("img_session_1")&&"img_session_1".equals(device.snapshot),"snapshot name preserved");
        require(!camera.capture_snapshot(null)&&!camera.capture_snapshot(""),"empty snapshot names rejected");
        device.failSnapshot=true;require(!camera.capture_snapshot("img_session_2"),"snapshot exception handled");
        require(!camera.set_pipeline(-1)&&!camera.set_pipeline(10),"invalid pipeline rejected");
        device.switchOk=false;require(!camera.set_pipeline(1)&&camera.pipeline()==0,"failed switch preserves pipeline");
        device.switchOk=true;camera.update_full(POSE);camera.stop();
        require(camera.get_result().temp==0&&camera.get_result().cpu==0&&camera.get_result().fps==0,"stop clears health readings");
        require(!camera.is_connected()&&device.stopped>=1,"stop releases camera");
        device.failStart=true; boolean thrown=false;
        try{camera.hard_init(hardware);}catch(RuntimeException expected){thrown=true;}
        require(thrown&&camera.status()==Limelight.Status.INIT_FAILED&&!camera.is_connected(),"hard init failure cleans up");
        device.failStart=false;hardware.fail=true;
        require(!camera.init()&&camera.status()==Limelight.Status.INIT_FAILED,"soft init exposes failure");
        hardware.fail=false;require(camera.init(),"failed initialization can be retried");
    }
    static void frame_matrix(){
        Limelight3A device=new Limelight3A();HardwareMap hardware=new HardwareMap();hardware.device=device;
        Limelight camera=new Limelight(hardware);camera.init(); PollenMap map=new PollenMap(camera);
        LLResult first=frame(9000,1);first.detectors.add(new LLResultTypes.DetectorResult());device.result=first;
        camera.update(POSE);map.update();long sequence=camera.pollen_frame_sequence();
        require(camera.get_pollen().size()==1&&Math.abs(camera.get_pollen().get(0).confidence-.83984375)<1e-12,"JSON confidence remains fraction");
        require(map.snapshot().size()==1&&Math.abs(map.snapshot().get(0).position.x-46)<1e-9,"default range26 creates field46,20");
        camera.update(POSE);require(camera.pollen_frame_sequence()==sequence,"same object repeat ignored");
        device.result=frame(9000,2);camera.update(POSE);require(camera.pollen_frame_sequence()==sequence,"same camera timestamp new receipt ignored");
        LLResult stale=frame(9001,3);stale.age=251;device.result=stale;camera.update(POSE);map.update();
        require(camera.pollen_frame_sequence()==sequence&&!camera.has_pollen_frame()&&map.snapshot().size()==1,"stale frame preserves map");
        device.result=null;camera.update(POSE);map.update();require(map.snapshot().size()==1&&!camera.has_target(),"missing frame preserves map");
        device.result=frame(9002,4);device.result.pipeline=1;camera.update(POSE);map.update();
        require(camera.pollen_frame_sequence()==sequence&&!camera.has_pollen_frame(),"wrong pipeline ignored");
        device.result=frame(10,5);camera.update(POSE);map.update();
        require(camera.pollen_frame_sequence()==sequence+1&&map.snapshot().isEmpty(),"fresh lower uptime starts camera epoch");
        sequence=camera.pollen_frame_sequence();device.result=frame(9,4);camera.update(POSE);
        require(camera.pollen_frame_sequence()==sequence,"older uptime with older receipt is rejected");
        device.result=frame(11,6);device.result.valid=false;camera.update(POSE);
        require(camera.pollen_frame_sequence()==sequence+1&&!camera.has_target(),"valid=false analyzed empty detector frame is authoritative");
        device.result=frame(12,7);device.result.detectors.add(new LLResultTypes.DetectorResult());camera.update(null);
        require(camera.pollen_frame_sequence()==sequence+1,"null odometry rejects frame publication");
        camera.update(POSE);require(camera.pollen_frame_sequence()==sequence+2,"same frame can be retried with valid pose");
        camera.clear_pollen();require(!camera.has_pollen_frame(),"manual clearing cannot become empty frame evidence");
        device.failRead=true;camera.update(POSE);require(!camera.has_target()&&!camera.has_pollen_frame(),"read exception invalidates latest diagnostics");
    }
    static void calibration_and_pose(){
        Limelight3A device=new Limelight3A();HardwareMap hardware=new HardwareMap();hardware.device=device;
        Limelight camera=new Limelight(hardware);camera.init();camera.set_pipeline(1);
        LLResult result=frame(1,1);result.pipeline=1;result.tags=1;
        result.botpose=new Pose3D(new Position(DistanceUnit.METER,0,0,0,0),new YawPitchRollAngles(AngleUnit.DEGREES,0,0,0,0));
        device.result=result;camera.update(POSE);
        require(camera.get_pose()!=null&&camera.get_pose().x==72&&camera.get_pose().y==72,"actual zero-centre tag pose is valid");
        result.tags=0;camera.update(POSE);require(camera.get_pose()==null,"zero default pose without tags is rejected");
        result.tags=1;result.botpose=new Pose3D(new Position(DistanceUnit.METER,1,-1,0,0),new YawPitchRollAngles(AngleUnit.DEGREES,90,0,0,0));
        camera.update(POSE);require(Math.abs(camera.get_pose().x-111.37007874015748)<1e-9&&Math.abs(camera.get_pose().y-32.62992125984252)<1e-9,"meter/corner conversion");
        require(Math.abs(camera.get_pose().heading-Math.PI/2)<1e-12,"yaw degrees to radians");
        camera.set_pipeline(0);PollenMap map=new PollenMap(camera);map.inject(new VisionPose(35,20,0));map.inject(new VisionPose(50,20,0));
        camera.calibration.usePinholeRange=true;camera.calibration.cameraHeightIn=10;camera.calibration.cameraPitchDeg=0;
        device.result=frame(2,2);camera.update(POSE);map.update();
        require(map.snapshot().size()==1&&Math.abs(map.snapshot().get(0).position.x-35)<1e-9,"vertical FOV protects unseen15inch floor but clears visible30inch floor");
        long sequence=camera.pollen_frame_sequence();camera.calibration.cameraHeightIn=Double.NaN;device.result=frame(3,3);camera.update(POSE);
        require(camera.pollen_frame_sequence()==sequence,"invalid mounting is not negative evidence");
        camera.calibration.cameraHeightIn=10;device.result=frame(4,4);device.result.tx=Double.NaN;camera.update(POSE);
        require(!camera.has_target()&&camera.get_tx()==0,"nonfinite aggregate target invalidated");
    }
    static void limits(){
        Limelight3A device=new Limelight3A();HardwareMap hardware=new HardwareMap();hardware.device=device;
        Limelight camera=new Limelight(hardware);camera.init();long sequence=0;
        for(double latency:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,501}){
            device.result=frame(sequence+1,sequence+1);device.result.capture=latency;camera.update(POSE);
            require(camera.pollen_frame_sequence()==0,"invalid latency supplies no detector evidence: "+latency);
        }
        LLResult result=frame(5,5);result.age=250;result.capture=250;device.result=result;camera.update(POSE);
        require(camera.pollen_frame_sequence()==1,"500ms total latency and250ms age boundaries accepted");
        result=frame(6,6);result.detectors.add(null);
        for(double confidence:new double[]{-.01,Double.NaN,Double.POSITIVE_INFINITY,1.01}){
            LLResultTypes.DetectorResult d=new LLResultTypes.DetectorResult();d.confidence=confidence;result.detectors.add(d);
        }
        LLResultTypes.DetectorResult zero=new LLResultTypes.DetectorResult();zero.confidence=0;result.detectors.add(zero);
        LLResultTypes.DetectorResult one=new LLResultTypes.DetectorResult();one.confidence=1;result.detectors.add(one);
        device.result=result;camera.update(POSE);require(camera.get_pollen().size()==2,"normalized confidence edges0/1 accepted; invalid confidence rejected");
        camera.confidenceScale=.01;result=frame(7,7);LLResultTypes.DetectorResult legacy=new LLResultTypes.DetectorResult();legacy.confidence=83.984375;result.detectors.add(legacy);
        device.result=result;camera.update(POSE);require(Math.abs(camera.get_pollen().get(0).confidence-.83984375)<1e-12,"explicit percentage firmware scale works");
    }
    static LLResult frame(double ts,long hub){LLResult result=new LLResult();result.ts=ts;result.hub=hub;return result;}
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
}
