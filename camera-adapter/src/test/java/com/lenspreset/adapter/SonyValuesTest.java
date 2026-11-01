package com.lenspreset.adapter;

import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Scales;
import com.lenspreset.core.optics.Shutter;
import com.lenspreset.core.profile.ProfileParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonyValuesTest {
    @Test
    void sceneModesUseSonysNames() {
        assertEquals("aperture-priority", SonyValues.sceneMode(Preset.ExposureMode.APERTURE_PRIORITY));
        assertEquals("shutter-speed", SonyValues.sceneMode(Preset.ExposureMode.SHUTTER_PRIORITY));
        assertEquals("manual-exposure", SonyValues.sceneMode(Preset.ExposureMode.MANUAL));
        assertEquals("program-auto", SonyValues.sceneMode(Preset.ExposureMode.PROGRAM));
    }

    @Test
    void everyProfileValueHasAMapping() {
        for (String m : ProfileParser.AF_MODES) {
            if (!"manual".equals(m)) assertTrue(SonyValues.autoFocusMode(m).startsWith("af-"), m);
        }
        assertNull(SonyValues.autoFocusMode("manual"));
        for (String a : ProfileParser.AF_AREAS) assertTrue(SonyValues.focusAreaCandidates(a).length > 0, a);
        for (String m : ProfileParser.METERING) assertTrue(SonyValues.meteringCandidates(m).length > 0, m);
        for (String d : ProfileParser.DRIVES) assertTrue(SonyValues.drive(d)[0] != null, d);
        for (String s : ProfileParser.SOFT_SKIN) assertTrue(SonyValues.softSkin(s) != null, s);
        assertEquals("middle", SonyValues.softSkin("mid"));
        assertArrayEquals(new String[]{"burst", "high"}, SonyValues.drive("burst_hi"));
        assertArrayEquals(new String[]{"single", null}, SonyValues.drive("single"));
    }

    @Test
    void pickPrefersTheFirstSupportedCandidate() {
        List<String> supported = Arrays.asList("wide", "semi-multi", "center");
        assertEquals("semi-multi", SonyValues.pick(supported, "zone", "semi-multi", "wide"));
        assertNull(SonyValues.pick(supported, "tracking"));
        assertEquals("zone", SonyValues.pick(null, "zone", "wide"), "no list: try the first");
        assertNull(SonyValues.pick(supported));
        assertEquals("10", SonyValues.pick(Arrays.asList(0, 2, 10), "10"), "numbers compare as text");
    }

    @Test
    void minShutterCodesLineUpWithTheScale() {
        assertEquals(6972, SonyValues.minShutterCode(new Shutter(1, 125)));
        assertEquals(892421, SonyValues.minShutterCode(new Shutter(1, 1)));
        assertEquals(-1, SonyValues.minShutterCode(new Shutter(1, 4000)));
        assertEquals(-1, SonyValues.minShutterCode(new Shutter(2, 1)));
        assertEquals("1/1", Scales.SHUTTERS[SonyValues.MIN_SHUTTER_CODES.length - 1][0] + "/"
                + Scales.SHUTTERS[SonyValues.MIN_SHUTTER_CODES.length - 1][1]);
        for (int i = 2; i < SonyValues.MIN_SHUTTER_CODES.length; i++)
            assertTrue(SonyValues.MIN_SHUTTER_CODES[i] > SonyValues.MIN_SHUTTER_CODES[i - 1], "monotonic at " + i);
    }

    @Test
    void minShutterCodesCoverEveryBundledPreset() {
        // Every minimum shutter speed the bundled presets can produce has a code.
        for (int[] s : Scales.SHUTTERS) {
            Shutter sh = new Shutter(s[0], s[1]);
            if (sh.seconds() >= 1 / 3200.0 && sh.seconds() <= 1.0)
                assertTrue(SonyValues.minShutterCode(sh) > 0, sh.format());
        }
    }

    @Test
    void evIndexRoundsAndClamps() {
        assertEquals(-5, SonyValues.evIndex(-1.7, 1 / 3f, -9, 9));
        assertEquals(4, SonyValues.evIndex(1.3, 1 / 3f, -9, 9));
        assertEquals(9, SonyValues.evIndex(5, 1 / 3f, -9, 9));
        assertEquals(0, SonyValues.evIndex(1, 0, -9, 9));
    }

    @Test
    void apertureHundredths() {
        assertEquals(2.8, SonyValues.fNumber(280));
        assertNull(SonyValues.fNumber(0));
    }

    @Test
    void reportsCountAndLog() {
        ApplyReport r = new ApplyReport();
        r.add("iso", ApplyReport.Status.OK, "auto");
        r.add("soft_skin", ApplyReport.Status.UNSUPPORTED, "low not in [off]");
        r.add("aperture", ApplyReport.Status.FAILED, ApplyReport.describe(new NoSuchMethodError("incrementAperture")));
        assertEquals(1, r.okCount());
        assertEquals(2, r.problems().size());
        assertEquals("aperture", r.failures().get(0).key);
        assertEquals(1, r.failures().size());
        assertEquals("soft_skin", r.unsupported().get(0).key);
        assertEquals("[OK iso auto, UNSUPPORTED soft_skin low not in [off], FAILED aperture NoSuchMethodError: incrementAperture]",
                r.logLines().toString());
    }

    @Test
    void probeRecordsMissingMethods() {
        ProbeReport p = new ProbeReport();
        p.section("supported")
                .probe("ok", () -> Arrays.asList("a", "b"))
                .probe("missing", () -> { throw new NoSuchMethodError("x"); })
                .probe("broken", () -> { throw new IllegalStateException("busy"); });
        assertEquals(Arrays.asList("[supported]", "ok = [a, b]", "missing = missing (NoSuchMethodError)",
                "broken = error (IllegalStateException: busy)"), p.lines());
        assertTrue(p.text().endsWith("\r\n"));
    }
}
