package com.lenspreset.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackTest {
    @Test
    void exposureLineAsTheCameraWritesIt() {
        assertEquals("f/1.4 · 1/250 · ISO 400 · +0.3EV · 30mm", PlaybackText.exposure(1.4, 1 / 250.0, 400, 1 / 3.0, 30));
        assertEquals("f/11 · 2\" · ISO 100 · 0.0EV", PlaybackText.exposure(11, 2, 100, 0, 0));
        // Exif rationals are not exact: 1/250 recorded as 0.004 still reads 1/250.
        assertEquals("1/250", PlaybackText.exposure(0, 0.004, 0, Double.NaN, 0));
        assertEquals("", PlaybackText.exposure(0, 0, 0, Double.NaN, 0));
    }

    @Test
    void exifDatesReadAsDates() {
        assertEquals("2026-11-03 14:05", PlaybackText.date("2026:11:03 14:05:09"));
        assertEquals("", PlaybackText.date(null));
        assertEquals("yesterday", PlaybackText.date("yesterday"));
    }

    @Test
    void orientationTurnsClockwise() {
        assertEquals(0, PlaybackText.rotation(1));
        assertEquals(90, PlaybackText.rotation(6));
        assertEquals(180, PlaybackText.rotation(3));
        assertEquals(270, PlaybackText.rotation(8));
        assertEquals(0, PlaybackText.rotation(0));
    }

    @Test
    void positionsAndNames() {
        assertEquals("12 / 345", PlaybackText.position(11, 345));
        assertEquals("100MSDCF/DSC01234.JPG", PlaybackText.fileName("100MSDCF", "DSC01234.JPG"));
        assertEquals("DSC01234.JPG", PlaybackText.fileName("", "DSC01234.JPG"));
        assertEquals("", PlaybackText.fileName(null, null));
    }

    @Test
    void a5100PixelsAreWiderThanTall() {
        // 640x480 framebuffer on the A5100's 16:9 panel: the body reports ASPECT_RATIO_16_9 (2).
        assertEquals(4.0 / 3, PlaybackGeometry.pixelAspect(PlaybackGeometry.screenAspect(2), 640, 480), 1e-9);
        assertEquals(1.125, PlaybackGeometry.pixelAspect(PlaybackGeometry.screenAspect(1), 640, 480), 1e-9);
        assertEquals(1.0, PlaybackGeometry.pixelAspect(PlaybackGeometry.screenAspect(0), 640, 480), 1e-9);
        assertEquals(16.0 / 9, PlaybackGeometry.screenAspect(2), 1e-9);
        assertEquals(1.0, PlaybackGeometry.pixelAspect(4.0 / 3, 640, 480), 1e-9);
    }

    @Test
    void a32PictureFillsThe32Screen() {
        // A 3:2 picture on a 3:2 panel fills the whole 640x480 framebuffer.
        float[] f = PlaybackGeometry.fit(1620, 1080, 0, 640, 480, 1.125);
        assertEquals(640, 1620 * f[0], 1.0);
        assertEquals(480, 1080 * f[1], 1.0);
        assertEquals(0, f[2], 0.5);
        assertEquals(0, f[3], 0.5);
    }

    @Test
    void a32PictureIsPillarboxedOnThe169Screen() {
        // On the A5100 a 3:2 picture takes the full height and 540 of the 640 pixels, centred.
        float[] f = PlaybackGeometry.fit(1620, 1080, 0, 640, 480, 4.0 / 3);
        assertEquals(540, 1620 * f[0], 1.0);
        assertEquals(480, 1080 * f[1], 1.0);
        assertEquals(50, f[2], 0.5);
        assertEquals(1.5, 1620 * f[0] * 4 / 3 / (1080 * f[1]), 1e-3);
    }

    @Test
    void portraitPicturesKeepTheirShape() {
        float[] f = PlaybackGeometry.fit(1620, 1080, 90, 640, 480, 1.125);
        // Turned: 1080 wide, 1620 high; the full height, centred, physically 2:3.
        double w = 1080 * f[0];
        double h = 1620 * f[1];
        assertEquals(480, h, 1.0);
        assertEquals(2.0 / 3, w * 1.125 / h, 1e-3);
        assertEquals((640 - w) / 2, f[2], 0.5);
        assertEquals(0, f[3], 0.5);
    }

    @Test
    void squarePixelsLetterbox() {
        float[] f = PlaybackGeometry.fit(1600, 900, 0, 640, 480, 1);
        assertEquals(f[0], f[1], 1e-6);
        assertEquals(640, 1600 * f[0], 1.0);
        assertEquals((480 - 900 * f[1]) / 2, f[3], 0.5);
        assertArrayEquals(new float[]{1, 1, 0, 0}, PlaybackGeometry.fit(0, 0, 0, 640, 480, 1));
    }

    @Test
    void decodeStepKeepsEnoughPixels() {
        assertEquals(2, PlaybackGeometry.sampleSize(1616, 1080, 800));
        assertEquals(1, PlaybackGeometry.sampleSize(1616, 1080, 1000));
        assertEquals(4, PlaybackGeometry.sampleSize(6000, 4000, 800));
        assertEquals(1, PlaybackGeometry.sampleSize(160, 120, 800));
    }
}
