package com.lenspreset.core.engine;

import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Scales;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The short labels the overlay shows for a recommendation: one chip per setting. */
public final class Hud {
    private Hud() {
    }

    private static final Map<String, String> NO_ARGS = Collections.emptyMap();

    /** Only advertise live-view controls that the selected preset handles. */
    public static String liveLegend(Preset preset, boolean touchscreen, I18n i18n) {
        String adjust = "";
        if (preset != null && !preset.adjust.isEmpty()) {
            String key = preset.adjust.keySet().iterator().next();
            adjust = i18n.t("ui.legend.adjust",
                    Collections.singletonMap("setting", i18n.t("ui.adjust." + key))) + " · ";
        }
        String legend = i18n.t("ui.legend.live", Collections.singletonMap("adjust", adjust));
        if (touchscreen) legend += " · " + i18n.t("ui.legend.touch");
        return legend;
    }

    /** Keep the selected values visible with the settings, even when hints or keys are hidden. */
    public static List<String> adjustments(Preset preset, Map<String, Integer> overrides, I18n i18n) {
        List<String> out = new ArrayList<String>();
        if (preset == null) return out;
        for (Map.Entry<String, Preset.Adjust> e : preset.adjust.entrySet()) {
            Integer saved = overrides.get(e.getKey());
            int value = saved != null && e.getValue().allows(saved) ? saved : e.getValue().defaultValue;
            out.add(i18n.t("ui.adjust." + e.getKey()) + ": " + adjustmentValue(e.getKey(), value, i18n));
        }
        return out;
    }

    public static String adjustmentValue(String key, int value, I18n i18n) {
        if (Preset.Adjust.SELF_TIMER.equals(key)) {
            if (value == 0) return i18n.t("ui.off");
            return i18n.t("ui.seconds", Collections.singletonMap("s", Integer.toString(value)));
        }
        return Integer.toString(value);
    }

    public static List<String> chips(Recommendation r, I18n i18n) {
        return chips(r, null, Collections.<String, Integer>emptyMap(), i18n);
    }

    public static List<String> chips(Recommendation r, Preset preset,
                                     Map<String, Integer> overrides, I18n i18n) {
        List<String> out = adjustments(preset, overrides, i18n);
        out.add(modeLetter(r.exposureMode));
        if (r.aperture != null) {
            String f = Scales.formatAperture(r.aperture);
            out.add(r.apertureSettable ? f : f + " " + i18n.t("hud.ring"));
        }
        if (r.shutter != null) out.add(r.shutter.format());
        if (r.minShutter != null) out.add("≥" + r.minShutter.format());
        if (r.isoAuto && r.isoAutoRangeSettable) out.add("ISO AUTO " + r.isoAutoMin + "–" + r.isoAutoMax);
        else if (r.isoAuto) out.add("ISO AUTO");
        else out.add("ISO " + r.iso);
        out.add("EV " + Scales.formatEv(r.ev));
        if (r.metering != null && !"multi".equals(r.metering)) out.add(i18n.t("hud.metering." + r.metering));

        StringBuilder focus = new StringBuilder(focusLabel(r.focusMode));
        if (r.faceDetection) focus.append(" · ").append(i18n.t("hud.face"));
        if (r.eyeAf) focus.append(" · ").append(i18n.t("hud.eye"));
        out.add(focus.toString());

        if (!"single".equals(r.drive)) out.add(i18n.t("hud." + r.drive));
        if (r.selfTimer > 0 && (preset == null || !preset.adjust.containsKey(Preset.Adjust.SELF_TIMER))) {
            Map<String, String> args = new java.util.HashMap<String, String>();
            args.put("s", Integer.toString(r.selfTimer));
            out.add(i18n.t("hud.timer", args));
        }
        if (r.creativeStyle != null) out.add(i18n.t("style." + r.creativeStyle, NO_ARGS));
        return out;
    }

    public static String modeLetter(Preset.ExposureMode mode) {
        switch (mode) {
            case APERTURE_PRIORITY: return "A";
            case SHUTTER_PRIORITY: return "S";
            case MANUAL: return "M";
            default: return "P";
        }
    }

    static String focusLabel(String mode) {
        if (Preset.AfRule.CONTINUOUS.equals(mode)) return "AF-C";
        if (Preset.AfRule.AUTOMATIC.equals(mode)) return "AF-A";
        if (Preset.AfRule.MANUAL.equals(mode)) return "MF";
        return "AF-S";
    }

    /** Messages in display order, translated. */
    public static List<String> lines(Recommendation r, I18n i18n) {
        List<String> out = new ArrayList<String>();
        for (Message m : r.messages) out.add(m.render(i18n));
        return out;
    }
}
