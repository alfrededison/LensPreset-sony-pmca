package com.sony.scalar.hardware.avio;

/** Compile-time stub (see CameraEx). Used to find where the live view sits on the screen. */
public class DisplayManager {
    public DisplayManager() { throw new RuntimeException("Stub!"); }
    public VideoRect getDisplayedVideoRect() { throw new RuntimeException("Stub!"); }
    public void finish() { throw new RuntimeException("Stub!"); }

    public static class VideoRect {
        private VideoRect() {}

        public int pxBottom;
        public int pxLeft;
        public int pxRight;
        public int pxTop;
    }
}
