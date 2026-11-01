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

    public static List<String> chips(Recommendation r, I18n i18n) {
        List<String> out = new ArrayList<String>();
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
        if (r.selfTimer > 0) {
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
        for (Message m : r.messages) out.add(i18n.t(m.key, m.args));
        return out;
    }
}
