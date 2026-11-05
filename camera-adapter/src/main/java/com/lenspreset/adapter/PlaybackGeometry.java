package com.lenspreset.adapter;

/**
 * Where a picture goes on the camera's screen. The UI framebuffer (640x480 on an A5100) is
 * stretched over a screen of another shape (16:9 on the A5100), so its pixels are not square: a picture drawn
 * with equal x and y scales would look squeezed. Sony's own playback draws in physical
 * proportions; so does this, from the screen shape DisplayManager reports. No Android here.
 */
public final class PlaybackGeometry {
    private PlaybackGeometry() {
    }

    /** DisplayManager.ASPECT_RATIO_* (3:2 = 1, 16:9 = 2, 4:3 = 3, 5:3 = 4, 11:9 = 5) as width / height; 0 when unknown. */
    public static double screenAspect(int code) {
        switch (code) {
            case 1: return 3.0 / 2;
            case 2: return 16.0 / 9;
            case 3: return 4.0 / 3;
            case 4: return 5.0 / 3;
            case 5: return 11.0 / 9;
            default: return 0;
        }
    }

    /**
     * Width / height of one framebuffer pixel on the physical screen: 4/3 for 640x480 on a 16:9
     * screen (the A5100), 1.125 on a 3:2 one. 1 (square) when the screen shape is unknown.
     */
    public static double pixelAspect(double screenAspect, int framebufferWidth, int framebufferHeight) {
        if (!(screenAspect > 0) || framebufferWidth <= 0 || framebufferHeight <= 0) return 1;
        return screenAspect * framebufferHeight / framebufferWidth;
    }

    /**
     * Fits a picture of {@code width x height} pixels, turned clockwise by {@code rotation} degrees
     * (0, 90, 180, 270), inside a view in framebuffer pixels, centred and keeping its physical
     * proportions. Returns {@code {scaleX, scaleY, left, top}}: the turned picture is drawn
     * {@code width' * scaleX} by {@code height' * scaleY} pixels from (left, top), where width' and
     * height' are swapped for 90 and 270.
     */
    public static float[] fit(int width, int height, int rotation, int viewWidth, int viewHeight, double pixelAspect) {
        if (width <= 0 || height <= 0 || viewWidth <= 0 || viewHeight <= 0 || !(pixelAspect > 0)) {
            return new float[]{1, 1, 0, 0};
        }
        boolean turned = rotation == 90 || rotation == 270;
        int w = turned ? height : width;
        int h = turned ? width : height;
        // Physical size of a view pixel is (pixelAspect x 1): fit in physical units, then convert.
        double sy = Math.min(viewWidth * pixelAspect / w, (double) viewHeight / h);
        double sx = sy / pixelAspect;
        float left = (float) ((viewWidth - w * sx) / 2);
        float top = (float) ((viewHeight - h * sy) / 2);
        return new float[]{(float) sx, (float) sy, left, top};
    }

    /** The largest power-of-two decode step that still leaves the picture at least {@code min} pixels on its long side. */
    public static int sampleSize(int width, int height, int min) {
        int longSide = Math.max(width, height);
        int sample = 1;
        while (longSide / (sample * 2) >= min) sample *= 2;
        return sample;
    }
}
