package com.lenspreset.adapter;

import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Scales;
import com.lenspreset.core.optics.Shutter;

import java.util.List;

/**
 * Profile values to the strings and codes Sony's camera API takes. No Android here, so the
 * mapping is unit-tested on the JVM. Where firmware versions are known or suspected to name a
 * setting differently, a list of candidates is tried against the camera's supported values.
 */
public final class SonyValues {
    private SonyValues() {
    }

    public static String sceneMode(Preset.ExposureMode mode) {
        switch (mode) {
            case APERTURE_PRIORITY: return "aperture-priority";
            case SHUTTER_PRIORITY: return "shutter-speed";
            case MANUAL: return "manual-exposure";
            default: return "program-auto";
        }
    }

    /** CameraEx auto-focus mode for an AF rule mode; null for manual focus. */
    public static String autoFocusMode(String mode) {
        if (Preset.AfRule.CONTINUOUS.equals(mode)) return "af-c";
        if (Preset.AfRule.AUTOMATIC.equals(mode)) return "af-a";
        if (Preset.AfRule.MANUAL.equals(mode)) return null;
        return "af-s";
    }

    public static String[] focusAreaCandidates(String area) {
        // The A5100 lists "wide" but reads back "center" after it; "multi" is tried next.
        if ("wide".equals(area)) return new String[]{"wide", "multi"};
        if ("zone".equals(area)) return new String[]{"zone", "semi-multi", "wide"};
        if ("center".equals(area)) return new String[]{"center"};
        if ("flexible_spot".equals(area)) return new String[]{"flex-spot", "spot", "center"};
        if ("tracking".equals(area)) return new String[]{"tracking", "wide"};
        return new String[0];
    }

    public static String[] meteringCandidates(String metering) {
        if ("center".equals(metering)) return new String[]{"center-weighted", "center-weighted-average"};
        if ("spot".equals(metering)) return new String[]{"center-spot", "spot"};
        return new String[]{"multi"};
    }

    /** {drive mode, burst speed or null}. */
    public static String[] drive(String drive) {
        if ("burst_hi".equals(drive)) return new String[]{"burst", "high"};
        if ("burst_lo".equals(drive)) return new String[]{"burst", "low"};
        return new String[]{"single", null};
    }

    public static String softSkin(String level) {
        if ("mid".equals(level)) return "middle";
        return level;
    }

    /**
     * The first candidate the camera lists as supported. When the camera gives no list (null), the
     * first candidate is tried as is; when it gives a list without any candidate, null.
     */
    /** Every candidate the camera lists as supported, in order; all of them when it gives no list. */
    public static List<String> supportedCandidates(List<?> supported, String... candidates) {
        List<String> out = new java.util.ArrayList<String>();
        for (String c : candidates) {
            if (supported == null) {
                out.add(c);
                continue;
            }
            for (Object s : supported) {
                if (c.equals(String.valueOf(s))) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    public static String pick(List<?> supported, String... candidates) {
        if (candidates.length == 0) return null;
        if (supported == null) return candidates[0];
        for (String c : candidates) {
            for (Object s : supported) if (c.equals(String.valueOf(s))) return c;
        }
        return null;
    }

    /**
     * {@code setAutoShutterSpeedLowLimit} codes, one per entry of {@link Scales#SHUTTERS} from 1/4000
     * to 1". Determined experimentally by obs1dium/BetterManual (MIT); roughly the exposure time in
     * microseconds x 0.87. Speeds slower than 1" are not offered: no preset needs them, and the
     * published table has an inconsistent entry there. 1/4000 is not offered either: the table's
     * value for it (1) does not follow the pattern.
     */
    static final int[] MIN_SHUTTER_CODES = {
            -1, 276, 347, 437, 551, 693, 873, 1101, 1385, 1743, 2202, 2771, 3486, 4404, 5540, 6972, 8808,
            11081, 13945, 17617, 22162, 27888, 35232, 44321, 55777, 70465, 93451, 116628, 145553, 181652,
            226703, 282927, 354560, 446220, 563719, 709135, 892421,
    };

    /** The low-limit code for a scale speed, or -1 when the speed has none. */
    public static int minShutterCode(Shutter s) {
        for (int i = 0; i < MIN_SHUTTER_CODES.length; i++) {
            int[] scale = Scales.SHUTTERS[i];
            if (new Shutter(scale[0], scale[1]).equals(s)) return MIN_SHUTTER_CODES[i];
        }
        return -1;
    }

    /** {@code Camera.Parameters} exposure-compensation index for an EV value, clamped to the camera's range. */
    public static int evIndex(double ev, float step, int min, int max) {
        if (step <= 0) return 0;
        int idx = Math.round((float) (ev / step));
        return Math.max(min, Math.min(max, idx));
    }

    /** f-number from {@code ApertureInfo.currentAperture} (f-number x 100); null when the lens reports none. */
    public static Double fNumber(int hundredths) {
        return hundredths > 0 ? hundredths / 100.0 : null;
    }
}
