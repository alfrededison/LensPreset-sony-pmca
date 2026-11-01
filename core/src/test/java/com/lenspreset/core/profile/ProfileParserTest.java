package com.lenspreset.core.profile;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.Feature;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileParserTest {
    private static final String LENS =
            "{\"id\": \"x\", \"name\": \"X\", \"mount\": \"E\", \"coverage\": \"APS-C\", \"type\": \"prime\","
                    + " \"focal_min\": 35, \"focal_max\": 35, \"aperture_max\": 1.8, \"aperture_min\": 22%s}";

    private static String lens(String extra) {
        return String.format(LENS, extra);
    }

    private static String error(ThrowingRunnable r) {
        try {
            r.run();
        } catch (ProfileException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a ProfileException");
    }

    interface ThrowingRunnable {
        void run() throws ProfileException;
    }

    @Test
    void readsTheA5100Profile() {
        CameraProfile c = Fixtures.camera("ilce-5100");
        assertEquals("Sony A5100", c.name);
        assertEquals(Arrays.asList("ILCE-5100"), c.models);
        assertEquals(23.5, c.sensor.widthMm);
        assertEquals(Feature.YES, c.feature(Feature.FACE_DETECTION));
        assertEquals(Feature.NO, c.feature(Feature.SILENT_SHUTTER));
        assertEquals(Feature.UNKNOWN, c.feature(Feature.EYE_AF));
        assertTrue(c.controls.touchscreen);
        assertNull(c.fixedLens);
        assertEquals(CameraProfile.Status.COMMUNITY_TESTED, c.status);
    }

    @Test
    void readsAVariableApertureZoom() {
        LensProfile l = Fixtures.lens("sony-e-pz-16-50-f35-56-oss");
        assertTrue(l.zoom);
        assertEquals(3.5, l.apertureMax);
        assertEquals(5.6, l.apertureMaxTele);
        assertArrayEquals(new double[]{5.6, 8}, l.sweetSpot);
        assertTrue(l.ois);
    }

    @Test
    void readsAPreset() {
        Preset p = Fixtures.preset("portrait-bokeh");
        assertEquals("1 người xoá phông", p.name.get("vi"));
        assertEquals(Preset.ExposureMode.APERTURE_PRIORITY, p.exposureMode);
        assertEquals("widest_safe", p.aperture.strategy);
        assertEquals("1/125", p.shutter.min.format());
        assertTrue(p.iso.auto);
        assertEquals("if_available", p.af.eye);
        assertEquals("portrait", p.style.creativeStyle);
    }

    @Test
    void manualLensDefaultsToNoAutofocus() throws ProfileException {
        LensProfile l = ProfileParser.lens(lens(", \"electronic\": false"));
        assertFalse(l.electronic);
        assertFalse(l.af);
    }

    @Test
    void unknownKeysAreErrors() {
        assertEquals("aperture_mni: unknown key", error(() -> ProfileParser.lens(lens(", \"aperture_mni\": 2"))));
    }

    @Test
    void errorsNameTheField() {
        assertEquals("aperture_max: 0.5 is outside 0.95..11",
                error(() -> ProfileParser.lens(lens(", \"x\": 1").replace("1.8", "0.5"))));
        assertEquals("sweet_spot: must be [low, high] within the lens's apertures",
                error(() -> ProfileParser.lens(lens(", \"sweet_spot\": [1.4, 4]"))));
        assertEquals("af: a non-electronic lens cannot autofocus",
                error(() -> ProfileParser.lens(lens(", \"electronic\": false, \"af\": true"))));
        assertEquals("focal_max: a prime has one focal length",
                error(() -> ProfileParser.lens(lens("").replace("\"focal_max\": 35", "\"focal_max\": 50"))));
    }

    @Test
    void rejectsBadIds() {
        assertTrue(error(() -> ProfileParser.lens(lens("").replace("\"x\"", "\"Bad Id\""))).startsWith("id:"));
    }

    @Test
    void rejectsInvalidJsonWithPosition() {
        assertTrue(error(() -> ProfileParser.lens("{\"id\": }")).contains("line 1"));
    }

    private static final String PRESET =
            "{\"id\": \"p\", \"name\": \"P\", \"exposure_mode\": \"%s\", %s \"af\": {\"mode\": \"single\"}}";

    @Test
    void presetModesNeedTheMatchingRules() {
        assertEquals("aperture: aperture_priority needs an aperture rule",
                error(() -> ProfileParser.preset(String.format(PRESET, "aperture_priority", ""))));
        assertEquals("shutter: shutter_priority needs a fixed shutter",
                error(() -> ProfileParser.preset(String.format(PRESET, "shutter_priority", ""))));
        assertEquals("aperture: program leaves the aperture to the camera",
                error(() -> ProfileParser.preset(String.format(PRESET, "program", "\"aperture\": {\"strategy\": \"widest\"},"))));
        assertEquals("aperture: the dof strategy needs a subject to know the distance",
                error(() -> ProfileParser.preset(String.format(PRESET, "aperture_priority", "\"aperture\": {\"strategy\": \"dof\"},"))));
    }

    @Test
    void presetShutterMustParse() {
        assertEquals("shutter.value: \"fast\" is not a shutter speed like 1/125",
                error(() -> ProfileParser.preset(String.format(PRESET, "shutter_priority",
                        "\"shutter\": {\"strategy\": \"fixed\", \"value\": \"fast\"},"))));
    }

    @Test
    void presetNameNeedsEnglish() {
        assertEquals("name: needs an \"en\" entry",
                error(() -> ProfileParser.preset(String.format(PRESET, "program", "").replace("\"P\"", "{\"vi\": \"P\"}"))));
    }

    @Test
    void presetAdjustRowsOnlyForGroups() {
        assertEquals("adjust.rows: only a group subject has rows",
                error(() -> ProfileParser.preset(String.format(PRESET, "program",
                        "\"adjust\": {\"rows\": {\"min\": 1, \"max\": 3}},"))));
    }

    @Test
    void presetSelfTimerAdjustDefaultsToThePresetsTimer() throws ProfileException {
        Preset p = ProfileParser.preset(String.format(PRESET, "program",
                "\"self_timer\": 2, \"adjust\": {\"self_timer\": {\"values\": [0, 2, 10]}},"));
        assertEquals(2, p.adjust.get("self_timer").defaultValue);
    }

    @Test
    void isoShorthand() throws ProfileException {
        Preset p = ProfileParser.preset(String.format(PRESET, "program", "\"iso\": \"auto\","));
        assertTrue(p.iso.auto);
        assertEquals("iso: \"high\" is not one of auto",
                error(() -> ProfileParser.preset(String.format(PRESET, "program", "\"iso\": \"high\","))));
        Preset fixed = ProfileParser.preset(String.format(PRESET, "program", "\"iso\": {\"mode\": \"fixed\", \"value\": 400},"));
        assertFalse(fixed.iso.auto);
        assertEquals(400, fixed.iso.value);
    }
}
