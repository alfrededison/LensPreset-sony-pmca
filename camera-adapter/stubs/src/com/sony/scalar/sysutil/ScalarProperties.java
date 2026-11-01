package com.sony.scalar.sysutil;

/** Compile-time stub (see CameraEx). Model and firmware identification. */
public class ScalarProperties {
    private ScalarProperties() {}

    public static final String PROP_MODEL_NAME = "model.name";
    public static final String PROP_VERSION_PLATFORM = "version.platform";

    public static String getString(String key) { throw new RuntimeException("Stub!"); }
    public static String getFirmwareVersion() { throw new RuntimeException("Stub!"); }
}
