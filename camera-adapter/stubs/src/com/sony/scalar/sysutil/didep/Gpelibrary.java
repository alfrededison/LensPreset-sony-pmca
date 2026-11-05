package com.sony.scalar.sysutil.didep;

/**
 * Compile-time stub (see CameraEx). The UI framebuffer's colour depth: RGBA4444 with the live view,
 * ABGR8888 only while playback shows pictures and the camera is closed (see BaseActivity).
 */
public class Gpelibrary {
    private Gpelibrary() {}

    public static void changeFrameBufferPixel(GS_FRAMEBUFFER_TYPE type) { throw new RuntimeException("Stub!"); }

    public enum GS_FRAMEBUFFER_TYPE { ABGR8888, RGBA4444 }
}
