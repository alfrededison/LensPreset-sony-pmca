package com.sony.scalar.hardware;

import android.hardware.Camera;
import android.util.Pair;

import java.util.List;

/**
 * Compile-time stub of Sony's camera extension API: only the members this app calls, with the
 * signatures and constant values of ma1co/OpenMemories-Framework's stubs (MIT). It is never
 * packaged; the camera supplies the real class. tools/firmware/contract.py checks every member
 * here against the framework extracted from each firmware.
 */
public class CameraEx {
    private CameraEx() {}

    public static CameraEx open(int cameraId, OpenOptions options) { throw new RuntimeException("Stub!"); }
    public Camera getNormalCamera() { throw new RuntimeException("Stub!"); }
    public Camera.Parameters createEmptyParameters() { throw new RuntimeException("Stub!"); }
    public ParametersModifier createParametersModifier(Camera.Parameters p) { throw new RuntimeException("Stub!"); }
    public void release() { throw new RuntimeException("Stub!"); }
    public void startDirectShutter() { throw new RuntimeException("Stub!"); }
    public void stopDirectShutter(DirectShutterStoppedCallback c) { throw new RuntimeException("Stub!"); }
    public void burstableTakePicture() { throw new RuntimeException("Stub!"); }
    public void cancelTakePicture() { throw new RuntimeException("Stub!"); }
    public void setShutterListener(ShutterListener l) { throw new RuntimeException("Stub!"); }
    public void incrementAperture() { throw new RuntimeException("Stub!"); }
    public void decrementAperture() { throw new RuntimeException("Stub!"); }
    public void incrementShutterSpeed() { throw new RuntimeException("Stub!"); }
    public void decrementShutterSpeed() { throw new RuntimeException("Stub!"); }
    public LensInfo getLensInfo() { throw new RuntimeException("Stub!"); }
    public final void setApertureChangeListener(ApertureChangeListener l) { throw new RuntimeException("Stub!"); }
    public final void setShutterSpeedChangeListener(ShutterSpeedChangeListener l) { throw new RuntimeException("Stub!"); }
    public void setFocalLengthChangeListener(FocalLengthChangeListener l) { throw new RuntimeException("Stub!"); }
    public final void setEquipmentCallback(EquipmentCallback c) { throw new RuntimeException("Stub!"); }

    public static class OpenOptions {
        public OpenOptions() { throw new RuntimeException("Stub!"); }
    }

    public static class ApertureInfo {
        /** f-number x 100. */
        public int currentAperture;
        public int currentAvailableMax;
        public int currentAvailableMin;
    }

    public interface DirectShutterStoppedCallback {
        void onShutterStopped(CameraEx camera);
    }

    public interface ShutterListener {
        /** {@code status}: 0 taken, 1 cancelled, 2 error (BetterManual's reading). */
        void onShutter(int status, CameraEx camera);
    }

    public interface ApertureChangeListener {
        void onApertureChange(ApertureInfo info, CameraEx camera);
    }

    public static class ShutterSpeedInfo {
        public int currentAvailableMax_d;
        public int currentAvailableMax_n;
        public int currentAvailableMin_d;
        public int currentAvailableMin_n;
        public int currentShutterSpeed_d;
        public int currentShutterSpeed_n;
    }

    public interface ShutterSpeedChangeListener {
        void onShutterSpeedChange(ShutterSpeedInfo info, CameraEx camera);
    }

    public interface FocalLengthChangeListener {
        void onFocalLengthChanged(int focalLength, CameraEx camera);
    }

    public interface EquipmentCallback {
        void onEquipmentChange(int device, int status, CameraEx camera);
    }

    public static class WideTele {
        public int tele;
        public int wide;
    }

    public static class LensInfo {
        public boolean FixedAperture;
        public WideTele FocalLength;
        public long LensId;
        public String LensName;
        public String LensType;
        public WideTele MaxFValue;
        public WideTele MinFValue;
    }

    public static class ParametersModifier {
        private ParametersModifier() {}

        public static final String SCENE_MODE_PROGRAM_AUTO = "program-auto";
        public static final String SCENE_MODE_APERTURE_PRIORITY = "aperture-priority";
        public static final String SCENE_MODE_SHUTTER_PRIORITY = "shutter-speed";
        public static final String SCENE_MODE_MANUAL_EXPOSURE = "manual-exposure";

        public static final String AUTO_FOCUS_MODE_AF_A = "af-a";
        public static final String AUTO_FOCUS_MODE_AF_C = "af-c";
        public static final String AUTO_FOCUS_MODE_AF_S = "af-s";
        public static final String FOCUS_MODE_MANUAL = "manual";

        public static final String FOCUS_AREA_MODE_WIDE = "wide";
        public static final String FOCUS_AREA_MODE_CENTER = "center";
        public static final String FOCUS_AREA_MODE_FLEX_SPOT = "flex-spot";
        public static final String FOCUS_AREA_MODE_SEMI_MULTI = "semi-multi";
        public static final String FOCUS_AREA_MODE_TRACKING = "tracking";

        public static final String FACE_DETECTION_MODE_AUTO = "auto";
        public static final String FACE_DETECTION_MODE_OFF = "off";

        public static final String METERING_MODE_MULTI = "multi";
        public static final String METERING_MODE_CENTER_WEIGHTED = "center-weighted";
        public static final String METERING_MODE_CENTER_SPOT = "center-spot";

        public static final String DRIVE_MODE_SINGLE = "single";
        public static final String DRIVE_MODE_BURST = "burst";
        public static final String BURST_DRIVE_SPEED_HIGH = "high";
        public static final String BURST_DRIVE_SPEED_LOW = "low";

        public static final String SOFT_SKIN_EFFECT_OFF = "off";
        public static final String SOFT_SKIN_EFFECT_LOW = "low";
        public static final String SOFT_SKIN_EFFECT_MIDDLE = "middle";
        public static final String SOFT_SKIN_EFFECT_HIGH = "high";

        public int getISOSensitivity() { throw new RuntimeException("Stub!"); }
        public void setISOSensitivity(int iso) { throw new RuntimeException("Stub!"); }
        public List getSupportedISOSensitivities() { throw new RuntimeException("Stub!"); }
        public void setISOAutoMax(int iso) { throw new RuntimeException("Stub!"); }
        public void setISOAutoMin(int iso) { throw new RuntimeException("Stub!"); }
        public List getSupportedISOAutoMaxMin() { throw new RuntimeException("Stub!"); }
        public boolean isSupportedAutoShutterSpeedLowLimit() { throw new RuntimeException("Stub!"); }
        public int getAutoShutterSpeedLowLimit() { throw new RuntimeException("Stub!"); }
        public void setAutoShutterSpeedLowLimit(int value) { throw new RuntimeException("Stub!"); }
        public int getAperture() { throw new RuntimeException("Stub!"); }
        public Pair getShutterSpeed() { throw new RuntimeException("Stub!"); }

        public String getAutoExposureLock() { throw new RuntimeException("Stub!"); }
        public List getSupportedAutoExposureLocks() { throw new RuntimeException("Stub!"); }
        public String getMeteringMode() { throw new RuntimeException("Stub!"); }
        public void setMeteringMode(String mode) { throw new RuntimeException("Stub!"); }
        public List getSupportedMeteringModes() { throw new RuntimeException("Stub!"); }

        public String getAutoFocusMode() { throw new RuntimeException("Stub!"); }
        public void setAutoFocusMode(String mode) { throw new RuntimeException("Stub!"); }
        public List getSupportedAutoFocusModes() { throw new RuntimeException("Stub!"); }
        public String getFocusAreaMode() { throw new RuntimeException("Stub!"); }
        public void setFocusAreaMode(String mode) { throw new RuntimeException("Stub!"); }
        public List getSupportedFocusAreaModes() { throw new RuntimeException("Stub!"); }
        public String getFaceDetectionMode() { throw new RuntimeException("Stub!"); }
        public void setFaceDetectionMode(String mode) { throw new RuntimeException("Stub!"); }
        public List getSupportedFaceDetectionModes() { throw new RuntimeException("Stub!"); }

        public String getDriveMode() { throw new RuntimeException("Stub!"); }
        public void setDriveMode(String mode) { throw new RuntimeException("Stub!"); }
        public List getSupportedDriveModes() { throw new RuntimeException("Stub!"); }
        public String getBurstDriveSpeed() { throw new RuntimeException("Stub!"); }
        public void setBurstDriveSpeed(String speed) { throw new RuntimeException("Stub!"); }
        public List getSupportedBurstDriveSpeeds() { throw new RuntimeException("Stub!"); }
        public int getSelfTimer() { throw new RuntimeException("Stub!"); }
        public void setSelfTimer(int seconds) { throw new RuntimeException("Stub!"); }
        public List getSupportedSelfTimers() { throw new RuntimeException("Stub!"); }

        public String getColorMode() { throw new RuntimeException("Stub!"); }
        public void setColorMode(String mode) { throw new RuntimeException("Stub!"); }
        public List getSupportedColorModes() { throw new RuntimeException("Stub!"); }
        public String getSoftSkinEffect() { throw new RuntimeException("Stub!"); }
        public void setSoftSkinEffect(String effect) { throw new RuntimeException("Stub!"); }
        public List getSupportedSoftSkinEffects() { throw new RuntimeException("Stub!"); }

        public boolean isSupportedLensInfo() { throw new RuntimeException("Stub!"); }
    }
}
