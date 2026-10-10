package com.qualcomm.hardware.limelightvision;
public final class LLResultTypes {
    public static class DetectorResult {
        public String name="pollen"; public double confidence=0.83984375, tx,ty,area=1;
        public String getClassName(){return name;} public double getConfidence(){return confidence;}
        public double getTargetXDegrees(){return tx;} public double getTargetYDegrees(){return ty;}
        public double getTargetArea(){return area;}
    }
    public static class FiducialResult {}
}
