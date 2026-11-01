package com.lenspreset.core.engine;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.profile.ProfileParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetEngineTest {
    private static final CameraProfile A5100 = Fixtures.camera("ilce-5100");
    private static final LensProfile SIGMA_30 = Fixtures.lens("sigma-30-f14-dc-dn");
    private static final LensProfile KIT = Fixtures.lens("sony-e-pz-16-50-f35-56-oss");
    private static final LensProfile MANUAL_50 = Fixtures.lens("manual-50-f18");

    private static Recommendation run(CameraProfile c, LensProfile l, String preset, Conditions now) {
        return PresetEngine.compute(c, l, Fixtures.preset(preset), now);
    }

    private static Conditions focal(double mm) {
        return new Conditions(mm, null, null);
    }

    private static Conditions override(String key, int value) {
        Map<String, Integer> m = new HashMap<String, Integer>();
        m.put(key, value);
        return new Conditions(null, null, m);
    }

    @Test
    void sigma30PortraitStopsDownToF18() {
        Recommendation r = run(A5100, SIGMA_30, "portrait-bokeh", Conditions.none());
        assertEquals(Preset.ExposureMode.APERTURE_PRIORITY, r.exposureMode);
        assertEquals(1.8, r.aperture);
        assertTrue(r.apertureSettable);
        assertNull(r.shutter);
        assertEquals("1/125", r.minShutter.format());
        assertTrue(r.isoAuto);
        assertEquals(100, r.isoAutoMin);
        assertEquals(25600, r.isoAutoMax);
        assertEquals("single", r.focusMode);
        assertTrue(r.faceDetection);
        assertFalse(r.eyeAf, "eye AF is unknown on the A5100, so it is not switched on");
        assertEquals("portrait", r.creativeStyle);
        assertEquals("low", r.softSkin);
        assertEquals(1.95, r.subjectDistanceM, 0.01);
        assertTrue(r.dofNearM < r.subjectDistanceM && r.dofFarM > r.subjectDistanceM);
        assertEquals(46.0, r.equivalentFocalMm, 0.1);
    }

    @Test
    void messagesComeHintsFirstThenNotices() {
        Recommendation r = run(Fixtures.camera("generic"), SIGMA_30, "portrait-bokeh", Conditions.none());
        assertEquals("hint.distance.half_body", r.messages.get(0).key);
        assertEquals("2.0", r.messages.get(0).args.get("m"));
        assertEquals("hint.dof", r.messages.get(1).key);
        assertTrue(r.hasMessage("hint.eye_af_button"));
        assertTrue(r.hasMessage("hint.background_far"));
        assertEquals("notice.untested_camera", r.messages.get(r.messages.size() - 1).key);
    }

    @Test
    void aTestedCameraGetsNoUntestedNotice() {
        assertFalse(run(A5100, SIGMA_30, "portrait-bokeh", Conditions.none()).hasMessage("notice.untested_camera"));
    }

    @Test
    void eyeAfIsUsedWhenTheBodyHasIt() {
        CameraProfile withEye = Fixtures.cameraWithFeatures("ilce-5100", "eye_af", "yes");
        Recommendation r = run(withEye, SIGMA_30, "portrait-bokeh", Conditions.none());
        assertTrue(r.eyeAf);
        assertFalse(r.hasMessage("hint.eye_af_button"));
    }

    @Test
    void kitZoomUsesTheCurrentFocalLength() {
        Recommendation wide = run(A5100, KIT, "portrait-bokeh", focal(16));
        Recommendation tele = run(A5100, KIT, "portrait-bokeh", focal(50));
        assertEquals(4.5, wide.aperture);
        assertEquals(7.1, tele.aperture, "f/5.6 at 50 mm, two thirds closed");
        assertFalse(wide.hasMessage("notice.focal_assumed"));
        assertTrue(tele.subjectDistanceM > wide.subjectDistanceM * 3);
    }

    @Test
    void kitZoomWithoutAFocalReadingAssumesWide() {
        Recommendation r = run(A5100, KIT, "portrait-bokeh", Conditions.none());
        assertEquals(16, r.focalMm);
        assertTrue(r.hasMessage("notice.focal_assumed"));
    }

    @Test
    void liveMaxApertureOverridesTheInterpolation() {
        Recommendation r = run(A5100, KIT, "indoor-low-light", new Conditions(30.0, 5.0, null));
        assertEquals(5.0, r.aperture);
    }

    @Test
    void stabilisationRelaxesTheHandheldSpeed() {
        // Kit lens at 50 mm with OSS: 1/(2*50*1.53) = 1/153, two stops slower is 1/38, floored at 1/60.
        Recommendation r = run(A5100, KIT, "indoor-low-light", focal(50));
        assertEquals("1/60", r.minShutter.format());
        // Sigma 30 has no OSS: 1/92 rounds up to 1/100.
        Recommendation s = run(A5100, SIGMA_30, "indoor-low-light", Conditions.none());
        assertEquals("1/100", s.minShutter.format());
        assertEquals(1.4, s.aperture);
    }

    @Test
    void moreRowsNeedASmallerAperture() {
        Recommendation one = run(A5100, SIGMA_30, "group", override("rows", 1));
        Recommendation four = run(A5100, SIGMA_30, "group", override("rows", 4));
        assertTrue(four.aperture > one.aperture, one.aperture + " vs " + four.aperture);
        assertTrue(one.aperture >= 2.8, "min_f");
        double depth = 0.5 + 0.6 * 3;
        assertTrue(four.dofNearM <= four.subjectDistanceM - depth / 2 + 1e-9);
        assertTrue(four.dofFarM >= four.subjectDistanceM + depth / 2 - 1e-9);
    }

    @Test
    void groupDefaultsToTwoRowsAndIgnoresDisallowedOverrides() {
        Recommendation def = run(A5100, SIGMA_30, "group", Conditions.none());
        Recommendation two = run(A5100, SIGMA_30, "group", override("rows", 2));
        Recommendation bogus = run(A5100, SIGMA_30, "group", override("rows", 9));
        assertEquals(two.aperture, def.aperture);
        assertEquals(two.aperture, bogus.aperture);
        assertEquals("burst_lo", def.drive);
    }

    @Test
    void selfTimerFollowsTheOverride() {
        assertEquals(0, run(A5100, SIGMA_30, "group", Conditions.none()).selfTimer);
        assertEquals(10, run(A5100, SIGMA_30, "group", override("self_timer", 10)).selfTimer);
    }

    @Test
    void kidsUseShutterPriority() {
        Recommendation r = run(A5100, KIT, "kids-action", focal(30));
        assertEquals(Preset.ExposureMode.SHUTTER_PRIORITY, r.exposureMode);
        assertNull(r.aperture);
        assertEquals("1/500", r.shutter.format());
        assertNull(r.minShutter);
        assertEquals("continuous", r.focusMode);
        assertEquals("burst_hi", r.drive);
    }

    @Test
    void unavailableFeaturesFallBackWithANotice() {
        CameraProfile limited = Fixtures.cameraWithFeatures("ilce-5100",
                "continuous_af", "no", "burst", "no", "face_detection", "no", "creative_style", "no", "soft_skin", "no");
        Recommendation kids = run(limited, KIT, "kids-action", focal(30));
        assertEquals("single", kids.focusMode);
        assertEquals("single", kids.drive);
        assertFalse(kids.faceDetection);
        assertTrue(kids.hasMessage("notice.continuous_af_unavailable"));
        assertTrue(kids.hasMessage("notice.burst_unavailable"));
        assertTrue(kids.hasMessage("notice.face_unavailable"));
        Recommendation portrait = run(limited, SIGMA_30, "portrait-bokeh", Conditions.none());
        assertNull(portrait.creativeStyle);
        assertNull(portrait.softSkin);
        assertTrue(portrait.hasMessage("notice.style_unavailable"));
        assertTrue(portrait.hasMessage("notice.soft_skin_unavailable"));
    }

    @Test
    void manualLensGetsRingInstructions() {
        Recommendation r = run(A5100, MANUAL_50, "portrait-bokeh", Conditions.none());
        assertFalse(r.apertureSettable);
        assertEquals(2.2, r.aperture, "f/1.8 two thirds closed");
        assertEquals("manual", r.focusMode);
        assertFalse(r.faceDetection);
        assertTrue(r.hasMessage("hint.set_aperture_ring"));
        assertTrue(r.hasMessage("hint.manual_focus_distance"));
        assertFalse(r.hasMessage("hint.distance.half_body"), "the distance is in the manual focus hint");
    }

    @Test
    void apsCLensOnFullFrameCrops() throws Exception {
        CameraProfile ff = ProfileParser.camera("{\"id\": \"ff\", \"name\": \"FF\", \"sensor\": {\"format\": \"full-frame\","
                + " \"width_mm\": 35.8, \"height_mm\": 23.9, \"coc_mm\": 0.03}, \"iso\": {\"min\": 100, \"max\": 25600},"
                + " \"controls\": {\"touchscreen\": false}, \"status\": \"verified\"}");
        Recommendation r = run(ff, SIGMA_30, "portrait-bokeh", Conditions.none());
        assertTrue(r.hasMessage("notice.crop_mode"));
        assertEquals(45.0, r.equivalentFocalMm, 1.0);
        assertFalse(r.hasMessage("notice.untested_camera"));
    }

    @Test
    void evIsClampedToTheBodyRange() {
        CameraProfile generic = Fixtures.camera("generic");
        Recommendation r = run(generic, SIGMA_30, "silhouette", Conditions.none());
        assertEquals(-5 / 3.0, r.ev, 1e-9);
        assertEquals("spot", r.metering);
        assertEquals(1600, r.isoAutoMax);
    }

    @Test
    void aBodyWithoutAnAutoIsoRangeKeepsItsOwn() {
        // ILCE-5100 3.10 has no setISOAutoMin/Max: nothing to send, a note only where the preset asks for a ceiling.
        Recommendation silhouette = run(A5100, SIGMA_30, "silhouette", Conditions.none());
        assertFalse(silhouette.isoAutoRangeSettable);
        assertTrue(silhouette.hasMessage("notice.iso_range_unavailable"));
        Recommendation portrait = run(A5100, SIGMA_30, "portrait-bokeh", Conditions.none());
        assertFalse(portrait.hasMessage("notice.iso_range_unavailable"), "no ceiling asked for, nothing to say");
        assertTrue(run(Fixtures.camera("generic"), SIGMA_30, "silhouette", Conditions.none()).isoAutoRangeSettable);
    }

    @Test
    void isoCeilingIsCappedByTheBody() {
        CameraProfile generic = Fixtures.camera("generic");
        Recommendation r = run(generic, SIGMA_30, "portrait-environment", Conditions.none());
        assertEquals(3200, r.isoAutoMax);
        Recommendation indoor = run(generic, SIGMA_30, "indoor-low-light", Conditions.none());
        assertEquals(6400, indoor.isoAutoMax);
    }

    @Test
    void sweetSpotMidIsTheGeometricMean() {
        Recommendation r = run(A5100, SIGMA_30, "portrait-environment", Conditions.none());
        assertEquals(4.0, r.aperture);
    }

    @Test
    void apertureForDepthReturnsMinusOneWhenImpossible() {
        assertEquals(-1, PresetEngine.apertureForDepth(50, 0.02, 1.0, 5.0, 1.8, 22));
    }
}
