package com.lenspreset.core;

import com.lenspreset.core.engine.Conditions;
import com.lenspreset.core.engine.Message;
import com.lenspreset.core.engine.PresetEngine;
import com.lenspreset.core.engine.Recommendation;
import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Scales;
import com.lenspreset.core.profile.ProfileSet;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every bundled camera x lens x preset, at both ends of a zoom and every adjust value, must give
 * settings the body and lens can take. This is the CI gate for community profiles.
 */
class BundledProfilesTest {
    private static final ProfileSet SET = Fixtures.bundled();
    private static final Map<String, String> EN = Fixtures.languageTables().get("en");

    @Test
    void everyBundledFileParses() {
        assertTrue(SET.problems().isEmpty(), SET.problems().toString());
        assertEquals(Fixtures.jsonFiles("profiles/cameras").size(), SET.cameras().size());
        assertEquals(Fixtures.jsonFiles("profiles/presets").size(), SET.presets().size());
    }

    @Test
    void fileNamesMatchIds() {
        for (File f : Fixtures.jsonFiles("profiles/cameras"))
            assertTrue(SET.camera(f.getName().replace(".json", "")) != null, f.getName());
        for (File f : Fixtures.jsonFiles("profiles/lenses"))
            assertTrue(SET.lens(f.getName().replace(".json", "")) != null, f.getName());
        for (File f : Fixtures.jsonFiles("profiles/presets"))
            assertTrue(SET.preset(f.getName().replace(".json", "")) != null, f.getName());
    }

    @Test
    void thereIsAGenericCamera() {
        assertTrue(SET.camera("generic") != null);
    }

    @Test
    void everyPresetHasEveryBundledLanguage() {
        for (Preset p : SET.presets()) {
            for (String hint : p.hints)
                assertTrue(p.hintText.containsKey(hint), p.id + " has no local text for " + hint);
            for (String lang : Fixtures.languageTables().keySet()) {
                assertTrue(p.name.containsKey(lang), p.id + " name has no " + lang);
                assertTrue(p.description.containsKey(lang), p.id + " description has no " + lang);
                for (Map.Entry<String, Map<String, String>> h : p.hintText.entrySet()) {
                    assertTrue(h.getValue().containsKey(lang), p.id + " hint " + h.getKey() + " has no " + lang);
                    assertEquals(com.lenspreset.core.i18n.I18n.placeholders(h.getValue().get("en")),
                            com.lenspreset.core.i18n.I18n.placeholders(h.getValue().get(lang)));
                }
            }
        }
    }

    @Test
    void everyCombinationStaysWithinLimits() {
        int checked = 0;
        for (CameraProfile c : SET.cameras()) {
            for (LensProfile l : SET.interchangeableLenses()) {
                for (Preset p : SET.presets()) {
                    for (Conditions now : conditions(l, p)) {
                        Recommendation r = PresetEngine.compute(c, l, p, now);
                        check(c, l, p, now, r);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 100, "checked " + checked);
    }

    private static List<Conditions> conditions(LensProfile l, Preset p) {
        List<Double> focals = new ArrayList<Double>();
        focals.add(null);
        if (l.zoom) {
            focals.add(l.focalMin);
            focals.add(Math.sqrt(l.focalMin * l.focalMax));
            focals.add(l.focalMax);
        }
        List<Map<String, Integer>> overrides = new ArrayList<Map<String, Integer>>();
        overrides.add(new HashMap<String, Integer>());
        for (Map.Entry<String, Preset.Adjust> e : p.adjust.entrySet()) {
            for (int v : e.getValue().values) {
                Map<String, Integer> m = new HashMap<String, Integer>();
                m.put(e.getKey(), v);
                overrides.add(m);
            }
        }
        List<Conditions> out = new ArrayList<Conditions>();
        for (Double f : focals) for (Map<String, Integer> o : overrides) out.add(new Conditions(f, null, o));
        return out;
    }

    private static void check(CameraProfile c, LensProfile l, Preset p, Conditions now, Recommendation r) {
        String where = c.id + " x " + l.id + " x " + p.id + " @" + now.focalMm + " " + now.overrides;
        if (r.aperture != null) {
            double widest = l.maxApertureAt(r.focalMm);
            if (r.aperture < Scales.apertureAtLeast(widest) - 1e-9 || r.aperture > l.apertureMin + 1e-9)
                fail(where + ": aperture f/" + r.aperture + " outside the lens");
            if (Scales.nearestAperture(r.aperture) != r.aperture) fail(where + ": aperture off scale");
        }
        if (r.shutter != null && !onShutterScale(r.shutter.seconds())) fail(where + ": shutter off scale");
        if (r.minShutter != null && !onShutterScale(r.minShutter.seconds())) fail(where + ": min shutter off scale");
        if (r.isoAuto) {
            if (r.isoAutoMin < c.isoMin || r.isoAutoMax > c.autoIsoCeiling() || r.isoAutoMin > r.isoAutoMax)
                fail(where + ": auto ISO " + r.isoAutoMin + ".." + r.isoAutoMax);
        } else if (r.iso < c.isoMin || r.iso > c.isoMax) {
            fail(where + ": ISO " + r.iso);
        }
        if (Math.abs(r.ev) > c.evRange + 1e-9) fail(where + ": EV " + r.ev);
        if (Math.abs(r.ev * 3 - Math.round(r.ev * 3)) > 1e-9) fail(where + ": EV not in thirds");
        if (r.focalMm < l.focalMin || r.focalMm > l.focalMax) fail(where + ": focal " + r.focalMm);
        if (r.subjectDistanceM != null && r.subjectDistanceM < l.minFocusM - 1e-9) fail(where + ": inside min focus");
        for (Message m : r.messages) {
            if (!EN.containsKey(m.key) && !m.localizedText.containsKey("en"))
                fail(where + ": no English text for " + m.key);
        }
    }

    private static boolean onShutterScale(double seconds) {
        for (int[] s : Scales.SHUTTERS) if (Math.abs(s[0] / (double) s[1] - seconds) < 1e-9) return true;
        return false;
    }
}
