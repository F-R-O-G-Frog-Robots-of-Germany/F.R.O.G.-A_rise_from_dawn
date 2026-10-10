package org.firstinspires.ftc.teamcode.limelight;

/** A detector observation, or a persistent map track with its assigned id. */
public final class Pollen {
    public final int id;
    public final VisionPose position;
    public final double confidence;
    public final double area;
    public final long lastDetectedMs;

    public Pollen(int id, VisionPose position, double confidence, double area,
                  long lastDetectedMs) {
        this.id = id;
        this.position = position;
        this.confidence = confidence;
        this.area = area;
        this.lastDetectedMs = lastDetectedMs;
    }

    public Pollen(int id, VisionPose position, double confidence, double area) {
        this(id, position, confidence, area, VisionPose.now_ms());
    }
}
