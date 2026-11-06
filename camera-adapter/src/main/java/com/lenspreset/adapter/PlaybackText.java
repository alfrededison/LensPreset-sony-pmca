package com.lenspreset.adapter;

import com.lenspreset.core.optics.Scales;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the playback screen says about a picture, from the Exif values the camera's index returns
 * (AvindexContentInfo): no Android here, so it is unit-tested on the JVM.
 */
public final class PlaybackText {
    private PlaybackText() {
    }

    /** {@code f/1.4 · 1/250 · ISO 400 · +0.3 · 30mm}; values the picture does not record are left out. */
    public static String exposure(double fNumber, double exposureSeconds, int iso, double ev, double focalMm) {
        List<String> parts = new ArrayList<String>();
        if (fNumber > 0) parts.add(Scales.formatAperture(fNumber));
        if (exposureSeconds > 0) parts.add(Scales.nearestShutter(exposureSeconds).format());
        if (iso > 0) parts.add("ISO " + iso);
        if (!Double.isNaN(ev)) parts.add(Scales.formatEv(ev) + "EV");
        if (focalMm > 0) parts.add(Math.round(focalMm) + "mm");
        return join(parts);
    }

    /** Exif {@code 2026:11:03 14:05:09} as {@code 2026-11-03 14:05}; anything else unchanged. */
    public static String date(String exif) {
        if (exif == null) return "";
        String t = exif.trim();
        if (t.length() >= 16 && t.charAt(4) == ':' && t.charAt(7) == ':' && t.charAt(10) == ' ') {
            return t.substring(0, 4) + "-" + t.substring(5, 7) + "-" + t.substring(8, 10) + " " + t.substring(11, 16);
        }
        return t;
    }

    /** Clockwise rotation that shows a picture upright, from its Exif orientation (1, 3, 6, 8). */
    public static int rotation(int exifOrientation) {
        switch (exifOrientation) {
            case 3: return 180;
            case 6: return 90;
            case 8: return 270;
            default: return 0;
        }
    }

    /** {@code 12 / 345}: position counts from the oldest, as the camera's own playback does. */
    public static String position(int index, int count) {
        return (index + 1) + " / " + count;
    }

    /** {@code 100MSDCF/DSC01234.JPG}, or whichever part is known. */
    public static String fileName(String folder, String file) {
        boolean hasFolder = folder != null && folder.length() > 0;
        boolean hasFile = file != null && file.length() > 0;
        if (hasFolder && hasFile) return folder + "/" + file;
        return hasFile ? file : hasFolder ? folder : "";
    }

    /** {@code 2.0x}: the zoom shown while zoomed. */
    public static String zoom(double zoom) {
        return String.format(Locale.US, "%.1fx", zoom);
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p);
        }
        return sb.toString();
    }
}
