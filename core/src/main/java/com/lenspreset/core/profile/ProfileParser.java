package com.lenspreset.core.profile;

import com.lenspreset.core.json.Json;
import com.lenspreset.core.json.JsonException;
import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.Feature;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Shutter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the three profile kinds. Files on the memory card come from anyone, so every value is
 * checked and a bad file is rejected whole with the reason, never clamped or half-read. The JSON
 * schemas in {@code profiles/schema} describe the same rules for editors and CI.
 */
public final class ProfileParser {
    private ProfileParser() {
    }

    public static final String[] SENSOR_FORMATS = {"APS-C", "full-frame", "1-inch", "1/1.7-inch", "1/2.3-inch"};
    public static final String[] STATUSES = {"verified", "community-tested", "untested"};
    public static final String[] EXPOSURE_MODES = {"program", "aperture_priority", "shutter_priority", "manual"};
    public static final String[] METERING = {"multi", "center", "spot"};
    public static final String[] DRIVES = {"single", "burst_lo", "burst_hi"};
    public static final String[] AF_MODES = {"single", "continuous", "automatic", "manual"};
    public static final String[] AF_AREAS = {"wide", "zone", "center", "flexible_spot", "tracking"};
    public static final String[] CREATIVE_STYLES = {
            "standard", "vivid", "neutral", "clear", "deep", "light", "portrait", "landscape",
            "sunset", "night", "red-leaves", "mono", "sepia",
    };
    public static final String[] SOFT_SKIN = {"off", "low", "mid", "high"};
    public static final String[] FRAMINGS = {"head_shoulders", "half_body", "full_body", "environment", "group"};
    public static final String[] OVERLAYS = {"thirds", "center", "horizon"};
    public static final String[] GOALS = {
            "shallow_dof", "deep_dof", "subject_and_scene", "freeze_motion", "low_light",
            "silhouette", "high_key", "custom",
    };
    public static final int[] SELF_TIMERS = {0, 2, 10};

    public static CameraProfile camera(String text) throws ProfileException {
        Node n = root(text);
        String id = id(n);
        String name = n.string("name");
        List<String> models = n.optStrings("models");

        Node s = n.object("sensor");
        CameraProfile.Sensor sensor = new CameraProfile.Sensor(
                s.oneOf("format", null, SENSOR_FORMATS),
                s.number("width_mm", 3, 60),
                s.number("height_mm", 2, 45),
                s.number("coc_mm", 0.002, 0.05));
        s.finish();

        Node iso = n.object("iso");
        int isoMin = iso.integer("min", 50, 3200);
        int isoMax = iso.integer("max", 400, 409600);
        Integer isoAutoMax = null;
        if (iso.has("auto_max")) isoAutoMax = iso.integer("auto_max", isoMin, isoMax);
        else iso.optString("auto_max", null);
        if (isoMax <= isoMin) throw new ProfileException("iso.max: must be above iso.min");
        iso.finish();

        Double burstLo = null;
        Double burstHi = null;
        Node burst = n.optObject("burst_fps");
        if (burst != null) {
            burstLo = burst.optNumberOrNull("lo", 0.5, 30);
            burstHi = burst.optNumberOrNull("hi", 0.5, 30);
            burst.finish();
        }

        Node c = n.object("controls");
        CameraProfile.Controls controls = new CameraProfile.Controls(
                c.bool("touchscreen"), c.optBool("front_dial", false), c.optBool("rear_wheel", false),
                c.optBool("top_dial", false), c.optInt("custom_keys", 0, 0, 10));
        c.finish();

        Map<String, Feature> features = new LinkedHashMap<String, Feature>();
        Node f = n.optObject("features");
        if (f != null) {
            for (String key : f.keys()) {
                if (!contains(Feature.NAMES, key))
                    throw new ProfileException("features." + key + ": unknown feature");
                features.put(key, Feature.parse(f.oneOf(key, null, "yes", "no", "unknown")));
            }
            f.finish();
        }

        String status = n.oneOf("status", "untested", STATUSES);
        CameraProfile profile = new CameraProfile(id, name, models, sensor, n.optBool("ibis", false),
                n.optString("fixed_lens", null), isoMin, isoMax, isoAutoMax,
                n.optNumber("ev_range", 3, 1, 5), burstLo, burstHi, controls, features,
                "verified".equals(status) ? CameraProfile.Status.VERIFIED
                        : "community-tested".equals(status) ? CameraProfile.Status.COMMUNITY_TESTED
                        : CameraProfile.Status.UNTESTED,
                n.optStrings("tested_firmware"), n.optStrings("tested_by"));
        n.optString("notes", null);
        n.finish();
        return profile;
    }

    public static LensProfile lens(String text) throws ProfileException {
        Node n = root(text);
        String id = id(n);
        String name = n.string("name");
        String mount = n.oneOf("mount", null, "E", "A", "fixed");
        String coverage = n.oneOf("coverage", null, SENSOR_FORMATS);
        boolean zoom = "zoom".equals(n.oneOf("type", null, "prime", "zoom"));
        double focalMin = n.number("focal_min", 4, 1200);
        double focalMax = n.number("focal_max", 4, 1200);
        if (focalMax < focalMin) throw new ProfileException("focal_max: must not be below focal_min");
        if (!zoom && focalMax != focalMin) throw new ProfileException("focal_max: a prime has one focal length");
        double apMax = n.number("aperture_max", 0.95, 11);
        Double apTele = n.optNumberOrNull("aperture_max_tele", 0.95, 11);
        double apertureMaxTele = apTele == null ? apMax : apTele;
        if (apertureMaxTele < apMax) throw new ProfileException("aperture_max_tele: must not be wider than aperture_max");
        double apMin = n.number("aperture_min", 2.8, 64);
        if (apMin <= apertureMaxTele) throw new ProfileException("aperture_min: must be narrower than the widest aperture");

        double[] sweet = null;
        List<Double> sweetList = n.optNumbers("sweet_spot");
        if (!sweetList.isEmpty()) {
            if (sweetList.size() != 2) throw new ProfileException("sweet_spot: must be [low, high]");
            sweet = new double[]{sweetList.get(0), sweetList.get(1)};
            if (sweet[0] > sweet[1] || sweet[0] < apMax - 0.01 || sweet[1] > apMin + 0.01)
                throw new ProfileException("sweet_spot: must be [low, high] within the lens's apertures");
        }

        boolean electronic = n.optBool("electronic", true);
        boolean af = n.optBool("af", electronic);
        if (af && !electronic) throw new ProfileException("af: a non-electronic lens cannot autofocus");

        List<String> matchNames = Collections.emptyList();
        List<Long> matchIds = new ArrayList<Long>();
        Node match = n.optObject("match");
        if (match != null) {
            matchNames = match.optStrings("names");
            for (Double d : match.optNumbers("ids")) {
                if (d != Math.rint(d) || d < 0) throw new ProfileException("match.ids: must be whole numbers");
                matchIds.add((long) (double) d);
            }
            match.finish();
        }

        LensProfile lens = new LensProfile(id, name, mount, coverage, zoom, focalMin, focalMax,
                apMax, apertureMaxTele, apMin, sweet, n.optBool("ois", false), electronic, af,
                n.optNumber("min_focus_m", 0.3, 0.01, 10), matchNames, matchIds);
        n.optString("notes", null);
        n.finish();
        return lens;
    }

    public static Preset preset(String text) throws ProfileException {
        Node n = root(text);
        String id = id(n);
        Map<String, String> name = n.localized("name", true);
        Map<String, String> description = n.localized("description", false);
        String goal = n.oneOf("goal", "custom", GOALS);
        int order = n.optInt("order", 1000, 0, 100000);
        Preset.ExposureMode mode = exposureMode(n.oneOf("exposure_mode", null, EXPOSURE_MODES));

        Preset.ApertureRule aperture = null;
        Node a = n.optObject("aperture");
        if (a != null) {
            String strategy = a.oneOf("strategy", null, "widest", "widest_safe", "sweet_spot", "dof", "fixed");
            aperture = new Preset.ApertureRule(strategy,
                    a.optNumber("stop_down_from_max", 0, 0, 4),
                    a.oneOf("position", "low", "low", "mid", "high"),
                    "fixed".equals(strategy) ? a.number("value", 0.95, 64) : a.optNumber("value", 0, 0, 64),
                    a.optNumberOrNull("min_f", 0.95, 64),
                    a.optNumberOrNull("max_f", 0.95, 64),
                    a.optNumber("depth_m", 0.5, 0.05, 50),
                    a.optNumber("per_row_m", 0.6, 0, 5));
            if (aperture.minF != null && aperture.maxF != null && aperture.minF > aperture.maxF)
                throw new ProfileException("aperture.min_f: must not be above aperture.max_f");
            a.finish();
        }

        Preset.ShutterRule shutter = null;
        Node sh = n.optObject("shutter");
        if (sh != null) {
            String strategy = sh.oneOf("strategy", null, "handheld_min", "fixed");
            Shutter min = shutterField(sh, "min", false);
            Shutter value = shutterField(sh, "value", "fixed".equals(strategy));
            shutter = new Preset.ShutterRule(strategy, min, value, sh.optNumber("stabilization_stops", 2, 0, 5));
            sh.finish();
        }

        Preset.IsoRule iso = isoRule(n);
        double ev = n.optNumber("ev", 0, -3, 3);
        String metering = n.oneOf("metering", "multi", METERING);

        Node afNode = n.object("af");
        Preset.AfRule af = new Preset.AfRule(
                afNode.oneOf("mode", null, AF_MODES),
                afNode.has("area") ? afNode.oneOf("area", null, AF_AREAS) : afNode.optString("area", null),
                afNode.optBool("face", false),
                afNode.oneOf("eye", "off", "off", "if_available"));
        afNode.finish();

        Node d = n.optObject("drive");
        String drive = "single";
        if (d != null) {
            drive = d.oneOf("mode", null, DRIVES);
            d.finish();
        }
        int selfTimer = n.optInt("self_timer", 0, 0, 10);
        if (!containsInt(SELF_TIMERS, selfTimer)) throw new ProfileException("self_timer: must be 0, 2 or 10");

        Preset.StyleRule style = null;
        Node st = n.optObject("style");
        if (st != null) {
            String cs = st.has("creative_style") ? st.oneOf("creative_style", null, CREATIVE_STYLES) : st.optString("creative_style", null);
            String fb = st.has("fallback") ? st.oneOf("fallback", null, CREATIVE_STYLES) : st.optString("fallback", null);
            String skin = st.has("soft_skin") ? st.oneOf("soft_skin", null, SOFT_SKIN) : st.optString("soft_skin", null);
            style = new Preset.StyleRule(cs, fb, skin);
            st.finish();
        }

        Preset.Subject subject = null;
        Node sub = n.optObject("subject");
        if (sub != null) {
            subject = new Preset.Subject(sub.oneOf("framing", null, FRAMINGS), sub.optInt("people_per_row", 5, 1, 30));
            sub.finish();
        }

        List<String> hints = n.optStrings("hints");
        for (String h : hints) {
            if (!h.matches("[a-z0-9_]{1,40}")) throw new ProfileException("hints: \"" + h + "\" is not a hint key");
        }
        Map<String, Map<String, String>> hintText = new LinkedHashMap<String, Map<String, String>>();
        Node ht = n.optObject("hint_text");
        if (ht != null) {
            for (String key : ht.keys()) {
                if (!hints.contains(key))
                    throw new ProfileException("hint_text." + key + ": must be listed in hints");
                Map<String, String> translations = ht.localized(key, true);
                for (Map.Entry<String, String> entry : translations.entrySet()) {
                    if (!entry.getKey().matches("[a-z]{2}(-[A-Za-z]{2,4})?") || entry.getValue().trim().length() == 0)
                        throw new ProfileException("hint_text." + key + ": needs language codes and non-empty text");
                }
                hintText.put(key, translations);
            }
            ht.finish();
        }
        for (String hint : hints) {
            if (!hintText.containsKey(hint))
                throw new ProfileException("hint_text." + hint + ": required text is missing");
        }
        List<String> overlays = n.optStrings("overlays");
        for (String o : overlays) {
            if (!contains(OVERLAYS, o)) throw new ProfileException("overlays: \"" + o + "\" is not one of thirds, center, horizon");
        }

        Map<String, Preset.Adjust> adjust = new LinkedHashMap<String, Preset.Adjust>();
        Node adj = n.optObject("adjust");
        if (adj != null) {
            for (String key : adj.keys()) {
                if (Preset.Adjust.ROWS.equals(key)) {
                    Node r = adj.object(key);
                    int lo = r.integer("min", 1, 10);
                    int hi = r.integer("max", lo, 10);
                    int def = r.optInt("default", lo, lo, hi);
                    r.finish();
                    int[] values = new int[hi - lo + 1];
                    for (int i = 0; i < values.length; i++) values[i] = lo + i;
                    adjust.put(key, new Preset.Adjust(values, def));
                } else if (Preset.Adjust.SELF_TIMER.equals(key)) {
                    Node r = adj.object(key);
                    List<Double> vals = r.optNumbers("values");
                    if (vals.isEmpty()) throw new ProfileException("adjust.self_timer.values: required list is missing");
                    int[] values = new int[vals.size()];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = (int) (double) vals.get(i);
                        if (!containsInt(SELF_TIMERS, values[i]) || values[i] != vals.get(i))
                            throw new ProfileException("adjust.self_timer.values: must be 0, 2 or 10");
                    }
                    Preset.Adjust ad = new Preset.Adjust(values, r.optInt("default", selfTimer, 0, 10));
                    if (!ad.allows(ad.defaultValue)) throw new ProfileException("adjust.self_timer.default: not in values");
                    r.finish();
                    adjust.put(key, ad);
                } else {
                    throw new ProfileException("adjust." + key + ": unknown setting (rows, self_timer)");
                }
            }
            adj.finish();
        }

        Preset p = new Preset(id, name, description, goal, order, mode, aperture, shutter, iso, ev,
                metering, af, drive, selfTimer, style, subject, hints, hintText, overlays, adjust);
        checkConsistency(p);
        n.finish();
        return p;
    }

    private static void checkConsistency(Preset p) throws ProfileException {
        boolean fixedShutter = p.shutter != null && Preset.ShutterRule.FIXED.equals(p.shutter.strategy);
        switch (p.exposureMode) {
            case APERTURE_PRIORITY:
                if (p.aperture == null) throw new ProfileException("aperture: aperture_priority needs an aperture rule");
                if (fixedShutter) throw new ProfileException("shutter: aperture_priority cannot fix the shutter; use handheld_min");
                break;
            case SHUTTER_PRIORITY:
                if (!fixedShutter) throw new ProfileException("shutter: shutter_priority needs a fixed shutter");
                if (p.aperture != null) throw new ProfileException("aperture: shutter_priority leaves the aperture to the camera");
                break;
            case MANUAL:
                if (p.aperture == null || !fixedShutter)
                    throw new ProfileException("manual: needs an aperture rule and a fixed shutter");
                break;
            case PROGRAM:
                if (p.aperture != null) throw new ProfileException("aperture: program leaves the aperture to the camera");
                if (fixedShutter) throw new ProfileException("shutter: program cannot fix the shutter");
                break;
        }
        if (p.aperture != null && Preset.ApertureRule.DOF.equals(p.aperture.strategy) && p.subject == null)
            throw new ProfileException("aperture: the dof strategy needs a subject to know the distance");
        if (p.adjust.containsKey(Preset.Adjust.ROWS)
                && (p.subject == null || !Preset.Subject.GROUP.equals(p.subject.framing)))
            throw new ProfileException("adjust.rows: only a group subject has rows");
        Preset.Adjust timer = p.adjust.get(Preset.Adjust.SELF_TIMER);
        if (timer != null && !timer.allows(p.selfTimer))
            throw new ProfileException("self_timer: must be one of adjust.self_timer.values");
        if (p.iso.max != null && p.iso.min != null && p.iso.min > p.iso.max)
            throw new ProfileException("iso.min: must not be above iso.max");
    }

    private static Preset.IsoRule isoRule(Node n) throws ProfileException {
        if (!n.has("iso")) {
            n.optString("iso", null);
            return new Preset.IsoRule(true, 0, null, null);
        }
        if (!(isObject(n, "iso"))) {
            n.oneOf("iso", null, "auto");
            return new Preset.IsoRule(true, 0, null, null);
        }
        Node i = n.object("iso");
        boolean auto = "auto".equals(i.oneOf("mode", null, "auto", "fixed"));
        int value = auto ? i.optInt("value", 0, 0, 0) : i.integer("value", 50, 409600);
        Integer max = i.has("max") ? Integer.valueOf(i.integer("max", 100, 409600)) : null;
        if (!i.has("max")) i.optString("max", null);
        Integer min = i.has("min") ? Integer.valueOf(i.integer("min", 50, 409600)) : null;
        if (!i.has("min")) i.optString("min", null);
        if (!auto && (max != null || min != null)) throw new ProfileException("iso: min/max only apply to auto");
        i.finish();
        return new Preset.IsoRule(auto, value, max, min);
    }

    private static boolean isObject(Node n, String key) {
        try {
            n.optObject(key);
            return true;
        } catch (ProfileException e) {
            return false;
        }
    }

    private static Shutter shutterField(Node n, String key, boolean required) throws ProfileException {
        String s = required ? n.string(key) : n.optString(key, null);
        if (s == null) return null;
        Shutter v = Shutter.parse(s);
        if (v == null) throw new ProfileException(n.path() + "." + key + ": \"" + s + "\" is not a shutter speed like 1/125");
        return v;
    }

    private static Preset.ExposureMode exposureMode(String s) {
        if ("aperture_priority".equals(s)) return Preset.ExposureMode.APERTURE_PRIORITY;
        if ("shutter_priority".equals(s)) return Preset.ExposureMode.SHUTTER_PRIORITY;
        if ("manual".equals(s)) return Preset.ExposureMode.MANUAL;
        return Preset.ExposureMode.PROGRAM;
    }

    private static Node root(String text) throws ProfileException {
        Object value;
        try {
            value = Json.parse(text);
        } catch (JsonException e) {
            throw new ProfileException("not valid JSON: " + e.getMessage());
        }
        Node n = new Node(value, "");
        // Editors use "$schema" to offer completion; it means nothing to the app.
        n.optString("$schema", null);
        return n;
    }

    private static String id(Node n) throws ProfileException {
        String id = n.string("id");
        if (!id.matches("[a-z0-9][a-z0-9-]{0,47}"))
            throw new ProfileException("id: \"" + id + "\" must be lower-case letters, digits and dashes");
        return id;
    }

    static boolean contains(String[] items, String s) {
        for (String i : items) if (i.equals(s)) return true;
        return false;
    }

    private static boolean containsInt(int[] items, int v) {
        for (int i : items) if (i == v) return true;
        return false;
    }
}
