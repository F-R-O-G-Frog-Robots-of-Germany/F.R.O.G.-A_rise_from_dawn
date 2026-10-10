package com.qualcomm.hardware.limelightvision;
import java.util.*;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
public class LLResult {
    public boolean valid=true; public long age, hub=1; public int pipeline,tags;
    public double ts=1,tx,ty,ta=1,capture,targeting,parse;
    public Pose3D botpose;
    public List<LLResultTypes.DetectorResult> detectors=new ArrayList<>();
    public List<LLResultTypes.FiducialResult> fiducials=new ArrayList<>();
    public long getStaleness(){return age;} public int getPipelineIndex(){return pipeline;}
    public boolean isValid(){return valid;} public double getTx(){return tx;} public double getTy(){return ty;}
    public double getTa(){return ta;} public double getCaptureLatency(){return capture;}
    public double getTargetingLatency(){return targeting;} public double getParseLatency(){return parse;}
    public double getTimestamp(){return ts;} public long getControlHubTimeStamp(){return hub;}
    public List<LLResultTypes.DetectorResult> getDetectorResults(){return detectors;}
    public List<LLResultTypes.FiducialResult> getFiducialResults(){return fiducials;}
    public int getBotposeTagCount(){return tags;} public Pose3D getBotpose(){return botpose;}
}
