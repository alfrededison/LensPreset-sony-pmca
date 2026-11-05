package com.sony.scalar.hardware.avio;

/**
 * Compile-time stub (see CameraEx). Kept open for the activity's life, as BetterManual does: where
 * the live view sits on screen, when the output switches (LCD, viewfinder, HDMI), and the shape of
 * the active screen (playback draws pictures with the right proportions on non-square pixels).
 */
public class DisplayManager {
    public static final int EVENT_SWITCH_DEVICE = 4096;
    public static final String DEVICE_ID_NONE = "DEVICE_ID_NONE";
    /** DeviceInfo.aspect: the shape of the physical screen, which the framebuffer is stretched to. */
    public static final int ASPECT_RATIO_UNDEFINED = 0;
    public static final int ASPECT_RATIO_3_2 = 1;
    public static final int ASPECT_RATIO_16_9 = 2;
    public static final int ASPECT_RATIO_4_3 = 3;
    public static final int ASPECT_RATIO_5_3 = 4;
    public static final int ASPECT_RATIO_11_9 = 5;

    public DisplayManager() { throw new RuntimeException("Stub!"); }
    public VideoRect getDisplayedVideoRect() { throw new RuntimeException("Stub!"); }
    public void setDisplayStatusListener(DisplayEventListener l) { throw new RuntimeException("Stub!"); }
    public void releaseDisplayStatusListener() { throw new RuntimeException("Stub!"); }
    public void finish() { throw new RuntimeException("Stub!"); }
    public String getActiveDevice() { throw new RuntimeException("Stub!"); }
    public DeviceInfo getDeviceInfo(String device) { throw new RuntimeException("Stub!"); }

    public interface DisplayEventListener {
        void onDeviceStatusChanged(int event);
    }

    public static class DeviceInfo {
        private DeviceInfo() {}

        public int aspect;
    }

    public static class VideoRect {
        private VideoRect() {}

        public int pxBottom;
        public int pxLeft;
        public int pxRight;
        public int pxTop;
    }
}
