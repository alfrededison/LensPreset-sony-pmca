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

    /** The zoom lever's steps; 1 is the whole picture. */
    private static final double[] ZOOM_STEPS = {1, 1.5, 2, 3, 4, 6, 8, 12, 16};

    /**
     * The next zoom step in {@code direction} (+1 in, -1 out) from {@code zoom}: in stops at
     * {@code max} (itself a step when it falls between two), out stops at 1.
     */
    public static double nextZoom(double zoom, int direction, double max) {
        if (direction > 0) {
            for (double s : ZOOM_STEPS) if (s > zoom + 1e-6) return Math.min(s, Math.max(1, max));
            return Math.max(zoom, Math.min(ZOOM_STEPS[ZOOM_STEPS.length - 1], max));
        }
        double out = 1;
        for (double s : ZOOM_STEPS) if (s < zoom - 1e-6) out = s;
        return out;
    }

    /**
     * Where a picture centred on {@code (cx, cy)} (fractions of the turned picture) and zoomed
     * {@code zoom} times its {@link #fit} is drawn: {@code {scaleX, scaleY, left, top}} as fit
     * returns them. {@code width x height} is the turned picture; the centre should be
     * {@link #clampCentre clamped} first.
     */
    public static float[] zoom(float[] fit, int width, int height, int viewWidth, int viewHeight,
                               double zoom, double cx, double cy) {
        double sx = fit[0] * zoom;
        double sy = fit[1] * zoom;
        return new float[]{(float) sx, (float) sy,
                (float) (viewWidth / 2.0 - cx * width * sx), (float) (viewHeight / 2.0 - cy * height * sy)};
    }

    /**
     * A centre (a fraction of the picture along one axis) moved as little as needed so that a
     * picture {@code size} view pixels long leaves no empty edge in a view {@code view} pixels
     * long; 0.5 when the whole picture fits.
     */
    public static double clampCentre(double c, double size, int view) {
        if (size <= view) return 0.5;
        double half = view / (2 * size);
        return Math.max(half, Math.min(1 - half, c));
    }

    /**
     * The part of a picture drawn {@code width x height} view pixels from (left, top) that a view
     * {@code viewWidth x viewHeight} shows: {@code {x0, y0, x1, y1}}, fractions of the picture, for
     * the zoomed view's minimap.
     */
    public static float[] shown(float left, float top, float width, float height, int viewWidth, int viewHeight) {
        if (width <= 0 || height <= 0) return new float[]{0, 0, 1, 1};
        return new float[]{
                clamp01(-left / width), clamp01(-top / height),
                clamp01((viewWidth - left) / width), clamp01((viewHeight - top) / height)};
    }

    private static float clamp01(float f) {
        return Math.max(0, Math.min(1, f));
    }

    /** The largest power-of-two decode step that still leaves the picture at least {@code min} pixels on its long side. */
    public static int sampleSize(int width, int height, int min) {
        int longSide = Math.max(width, height);
        int sample = 1;
        while (longSide / (sample * 2) >= min) sample *= 2;
        return sample;
    }
}
