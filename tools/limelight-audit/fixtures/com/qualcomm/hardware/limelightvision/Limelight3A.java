package com.qualcomm.hardware.limelightvision;
public class Limelight3A {
    public LLResult result; public boolean connected=true,switchOk=true,failStart,failRead,failStop,failSnapshot;
    public int started,stopped,pipeline,pollRate; public String snapshot;
    public boolean pipelineSwitch(int p){if(switchOk)pipeline=p;return switchOk;}
    public void start(){started++;if(failStart)throw new IllegalStateException("start failed");}
    public void stop(){stopped++;if(failStop)throw new IllegalStateException("stop failed");}
    public void setPollRateHz(int hz){pollRate=hz;}
    public boolean isConnected(){return connected;}
    public LLResult getLatestResult(){if(failRead)throw new IllegalStateException("read failed");return result;}
    public LLStatus getStatus(){return new LLStatus();}
    public boolean captureSnapshot(String name){if(failSnapshot)throw new IllegalStateException("snapshot failed");snapshot=name;return true;}
}
