package com.lenspreset.core.profile;

import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.LensProfile;

import java.util.Locale;

/** Finds the profiles for the body and lens the camera reports. */
public final class Matcher {
    private Matcher() {
    }

    /** The id of the profile used for a body no profile names. */
    public static final String GENERIC_CAMERA = "generic";

    /**
     * The camera profile whose {@code models} contains {@code modelName} (as {@code model.name}
     * reports it, e.g. {@code ILCE-5100}), or the generic profile, or null if there is none.
     */
    public static CameraProfile camera(ProfileSet set, String modelName) {
        if (modelName != null) {
            String want = normalize(modelName);
            for (CameraProfile c : set.cameras()) {
                for (String m : c.models) if (normalize(m).equals(want)) return c;
            }
        }
        return set.camera(GENERIC_CAMERA);
    }

    /**
     * The lens profile for what the camera reports: an exact id match first, then a profile whose
     * {@code match.names} entry appears in the reported name (ignoring case and spaces).
     * Returns null when nothing matches; the user then picks the lens.
     */
    public static LensProfile lens(ProfileSet set, String reportedName, Long reportedId) {
        if (reportedId != null && reportedId != 0) {
            for (LensProfile l : set.interchangeableLenses()) {
                if (l.matchIds.contains(reportedId)) return l;
            }
        }
        if (reportedName != null && reportedName.trim().length() > 0) {
            String have = normalize(reportedName);
            LensProfile best = null;
            int bestLen = 0;
            for (LensProfile l : set.interchangeableLenses()) {
                for (String n : l.matchNames) {
                    String want = normalize(n);
                    // The longest matching name wins: "50mm F1.8 OSS" beats "50mm".
                    if (want.length() > bestLen && have.contains(want)) {
                        best = l;
                        bestLen = want.length();
                    }
                }
            }
            return best;
        }
        return null;
    }

    static String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        String lower = s.toLowerCase(Locale.US);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c != ' ' && c != '\t' && c != '-' && c != '_') sb.append(c);
        }
        return sb.toString();
    }

    private static final int[] FOCAL_SCALES = {1, 10, 100};

    /**
     * Converts a focal length the camera reported into millimetres. The firmware's unit has not been
     * confirmed on a body yet (1, 10 or 100 raw units per mm), so it is worked out from the lens:
     * from {@code rawWide}, the wide end the lens reports, when there is one; otherwise from the
     * reading itself, but only when exactly one unit puts it inside the lens's range (on zooms of
     * about 10x and more, more than one does). Returns null when the unit cannot be told.
     */
    public static Double focalMm(int raw, int rawWide, LensProfile lens) {
        if (raw <= 0 || lens == null) return null;
        Integer scale = null;
        if (rawWide > 0) {
            for (int s : FOCAL_SCALES) {
                if (Math.abs(rawWide / (double) s - lens.focalMin) <= lens.focalMin * 0.1) scale = s;
            }
        } else {
            for (int s : FOCAL_SCALES) {
                double mm = raw / (double) s;
                if (mm >= lens.focalMin * 0.95 && mm <= lens.focalMax * 1.05) {
                    if (scale != null) return null;
                    scale = s;
                }
            }
        }
        if (scale == null) return null;
        double mm = raw / (double) scale;
        if (mm < lens.focalMin * 0.95 || mm > lens.focalMax * 1.05) return null;
        return lens.clampFocal(mm);
    }
}
