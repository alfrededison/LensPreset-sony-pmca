package com.lenspreset.core.profile;

import java.util.Locale;

/**
 * Profiles on the memory card. Sony's card layer only accepts DOS 8.3 names (see Recipe Lab's
 * notes), so the folder is {@code LENSPRST} and files are named like {@code MYPRESET.JSN}; a file
 * a computer gave a long name shows up under its 8.3 alias such as {@code MYPRES~1.JSO}.
 */
public final class CardFiles {
    private CardFiles() {
    }

    public static final String FOLDER = "LENSPRST";
    public static final String CAMERAS = "CAMERAS";
    public static final String LENSES = "LENSES";
    public static final String PRESETS = "PRESETS";
    public static final String PROBE_REPORT = "PROBE.TXT";
    public static final String LOG = "LOG.TXT";
    /** Larger files are ignored: no profile comes close. */
    public static final long MAX_BYTES = 32 * 1024;

    public static boolean isProfileFile(String name) {
        if (name.startsWith(".")) return false;
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".json") || lower.endsWith(".jsn") || lower.endsWith(".jso");
    }
}
