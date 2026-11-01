package com.lenspreset.core.model;

/**
 * Whether a camera can do something. Only a test on a real body may turn {@link #UNKNOWN} into
 * {@link #YES} or {@link #NO}: profiles record what was observed, never what a spec sheet implies.
 */
public enum Feature {
    YES, NO, UNKNOWN;

    public static Feature parse(String s) {
        if ("yes".equals(s)) return YES;
        if ("no".equals(s)) return NO;
        if ("unknown".equals(s)) return UNKNOWN;
        return null;
    }

    /** Names a camera profile may list under {@code features}. */
    public static final String FACE_DETECTION = "face_detection";
    public static final String EYE_AF = "eye_af";
    public static final String SOFT_SKIN = "soft_skin";
    public static final String CREATIVE_STYLE = "creative_style";
    public static final String SILENT_SHUTTER = "silent_shutter";
    public static final String READ_LENS_INFO = "read_lens_info";
    public static final String READ_FOCAL_LENGTH = "read_focal_length";
    public static final String SET_EXPOSURE_MODE = "set_exposure_mode";
    public static final String AUTO_ISO_MIN_SHUTTER = "auto_iso_min_shutter";
    public static final String CONTINUOUS_AF = "continuous_af";
    public static final String BURST = "burst";
    public static final String SELF_TIMER = "self_timer";
    public static final String METERING = "metering";

    public static final String[] NAMES = {
            FACE_DETECTION, EYE_AF, SOFT_SKIN, CREATIVE_STYLE, SILENT_SHUTTER, READ_LENS_INFO,
            READ_FOCAL_LENGTH, SET_EXPOSURE_MODE, AUTO_ISO_MIN_SHUTTER, CONTINUOUS_AF, BURST,
            SELF_TIMER, METERING,
    };
}
