package com.lenspreset.adapter;

import com.sony.scalar.sysutil.ScalarProperties;

/**
 * Which body the app runs on. {@code Build.MODEL} is "ScalarA" on every Sony body, so the model
 * comes from Sony's properties. Every value is null off a camera or when the call is missing.
 */
public final class DeviceIdentity {
    public final String model;
    public final String firmware;
    public final String platform;

    private DeviceIdentity(String model, String firmware, String platform) {
        this.model = model;
        this.firmware = firmware;
        this.platform = platform;
    }

    public static DeviceIdentity read() {
        return new DeviceIdentity(property(ScalarProperties.PROP_MODEL_NAME), firmware(),
                property(ScalarProperties.PROP_VERSION_PLATFORM));
    }

    private static String property(String key) {
        try {
            return ScalarProperties.getString(key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String firmware() {
        try {
            return ScalarProperties.getFirmwareVersion();
        } catch (Throwable t) {
            return null;
        }
    }
}
