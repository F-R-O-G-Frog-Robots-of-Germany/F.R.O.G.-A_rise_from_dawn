package com.qualcomm.robotcore.hardware;
public class HardwareMap {
    public Object device; public boolean fail;
    public <T> T get(Class<? extends T> type, String name){if(fail)throw new IllegalStateException("missing "+name);return type.cast(device);}
}
