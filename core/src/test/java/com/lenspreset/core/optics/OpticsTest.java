package com.lenspreset.core.optics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpticsTest {
    private static final double APSC_DIAG = Math.sqrt(23.5 * 23.5 + 15.6 * 15.6);

    @Test
    void apsCCropIsAboutOnePointFive() {
        assertEquals(1.53, Optics.cropFactor(APSC_DIAG), 0.01);
    }

    @Test
    void handheldRuleDoublesTheReciprocal() {
        // 30 mm on APS-C: 1 / (2 x 30 x 1.53) = 1/92
        assertEquals(1 / 92.0, Optics.handheldSeconds(30, 1.53, 0), 0.0002);
        assertEquals(4 / 92.0, Optics.handheldSeconds(30, 1.53, 2), 0.001);
    }

    @Test
    void distanceForHalfBodyAtThirtyMillimetres() {
        // 1 m of height on 15.6 mm of sensor at 30 mm: about 1.95 m.
        assertEquals(1.95, Optics.distanceForExtentM(1.0, 30, 15.6), 0.01);
    }

    @Test
    void fieldOfView() {
        assertEquals(42.8, Optics.fieldOfViewDeg(30, 23.5), 0.1);
    }

    @Test
    void depthOfFieldMatchesPublishedTables() {
        // 50 mm, f/8, CoC 0.03, focus 5 m. The exact thin-lens formulas give 3.39 / 9.53 m;
        // calculators that drop the +f terms (DOFMaster) give 3.42 / 9.31 m.
        assertEquals(3.39, Optics.dofNearM(50, 8, 0.03, 5), 0.01);
        assertEquals(9.53, Optics.dofFarM(50, 8, 0.03, 5), 0.01);
        assertTrue(Double.isInfinite(Optics.dofFarM(16, 8, 0.02, 3)));
    }

    @Test
    void shutterParsesAndFormats() {
        assertEquals("1/125", Shutter.parse("1/125").format());
        // Sony shows half a second as 0.5", not 1/2.
        assertEquals("0.5\"", Shutter.parse("0.5").format());
        assertEquals("1/3", Shutter.parse("1/3").format());
        assertEquals("2\"", Shutter.parse("2\"").format());
        assertEquals("1.3\"", new Shutter(13, 10).format());
        assertNull(Shutter.parse("fast"));
        assertNull(Shutter.parse("1/0"));
        assertNull(Shutter.parse("-1"));
        assertNull(Shutter.parse("100000/1"), "over 60 s");
        assertNull(Shutter.parse("NaN"));
        assertEquals("1/8", Shutter.parse("1/7").format(), "snapped to the nearest mark in stops");
        assertEquals("1/4000", Shutter.parse("1/8000").format(), "the scale's fastest");
    }

    @Test
    void scalesSnapToMarkedValues() {
        assertEquals(1.8, Scales.apertureAtLeast(1.4 * Math.pow(2, 0.335)));
        assertEquals(1.6, Scales.apertureAtLeast(1.6));
        assertEquals(4.5, Scales.apertureAtLeast(4.3));
        assertEquals(4.0, Scales.apertureAtMost(4.3));
        assertEquals(22.0, Scales.nearestAperture(22));
        assertEquals("1/100", Scales.shutterAtLeastAsFast(1 / 92.0).format());
        assertEquals("1/125", Scales.shutterAtLeastAsFast(1 / 125.0).format());
        assertEquals("1/4000", Scales.shutterAtLeastAsFast(1 / 10000.0).format());
    }

    @Test
    void formats() {
        assertEquals("f/1.8", Scales.formatAperture(1.8));
        assertEquals("f/11", Scales.formatAperture(11));
        assertEquals("-1.7", Scales.formatEv(-1.7));
        assertEquals("+1.3", Scales.formatEv(1.3));
        assertEquals("0.0", Scales.formatEv(0.1));
        assertEquals("2.4", Scales.formatMetres(2.43));
        assertEquals("12", Scales.formatMetres(12.4));
        assertEquals("∞", Scales.formatMetres(Double.POSITIVE_INFINITY));
    }
}
