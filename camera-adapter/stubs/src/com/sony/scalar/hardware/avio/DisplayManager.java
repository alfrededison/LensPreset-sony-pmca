package com.sony.scalar.hardware.avio;

/**
 * Compile-time stub (see CameraEx). Kept open for the activity's life, as BetterManual does: where
 * the live view sits on screen, and when the output switches (LCD, viewfinder, HDMI).
 */
public class DisplayManager {
    public static final int EVENT_SWITCH_DEVICE = 4096;

    public DisplayManager() { throw new RuntimeException("Stub!"); }
    public VideoRect getDisplayedVideoRect() { throw new RuntimeException("Stub!"); }
    public void setDisplayStatusListener(DisplayEventListener l) { throw new RuntimeException("Stub!"); }
    public void releaseDisplayStatusListener() { throw new RuntimeException("Stub!"); }
    public void finish() { throw new RuntimeException("Stub!"); }

    public interface DisplayEventListener {
        void onDeviceStatusChanged(int event);
    }

    public static class VideoRect {
        private VideoRect() {}

        public int pxBottom;
        public int pxLeft;
        public int pxRight;
        public int pxTop;
    }
}
