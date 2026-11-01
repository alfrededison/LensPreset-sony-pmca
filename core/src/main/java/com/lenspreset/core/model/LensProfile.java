package com.lenspreset.core.model;

import java.util.List;

/** An optical lens. Read from {@code profiles/lenses/*.json}. */
public final class LensProfile {
    public final String id;
    public final String name;
    /** {@code E}, {@code A}, or {@code fixed} for a compact's built-in lens. */
    public final String mount;
    /** Image circle: {@link Coverage#APS_C}, {@link Coverage#FULL_FRAME}, or the format of a fixed lens. */
    public final String coverage;
    public final boolean zoom;
    public final double focalMin;
    public final double focalMax;
    /** Widest aperture at {@link #focalMin}. */
    public final double apertureMax;
    /** Widest aperture at {@link #focalMax}; equals {@link #apertureMax} on constant-aperture lenses. */
    public final double apertureMaxTele;
    /** Narrowest aperture (largest f-number). */
    public final double apertureMin;
    /** The sharpest f-numbers, low and high; null when nobody has measured them. */
    public final double[] sweetSpot;
    public final boolean ois;
    /** False for manual and adapted lenses: the camera can neither set nor read the aperture. */
    public final boolean electronic;
    public final boolean af;
    public final double minFocusM;
    /** Lens names as the camera reports them; a reported name containing one of these matches. */
    public final List<String> matchNames;
    /** Lens ids as the camera reports them. */
    public final List<Long> matchIds;

    public LensProfile(String id, String name, String mount, String coverage, boolean zoom,
                       double focalMin, double focalMax, double apertureMax, double apertureMaxTele,
                       double apertureMin, double[] sweetSpot, boolean ois, boolean electronic,
                       boolean af, double minFocusM, List<String> matchNames, List<Long> matchIds) {
        this.id = id;
        this.name = name;
        this.mount = mount;
        this.coverage = coverage;
        this.zoom = zoom;
        this.focalMin = focalMin;
        this.focalMax = focalMax;
        this.apertureMax = apertureMax;
        this.apertureMaxTele = apertureMaxTele;
        this.apertureMin = apertureMin;
        this.sweetSpot = sweetSpot;
        this.ois = ois;
        this.electronic = electronic;
        this.af = af;
        this.minFocusM = minFocusM;
        this.matchNames = matchNames;
        this.matchIds = matchIds;
    }

    public double clampFocal(double focal) {
        return Math.max(focalMin, Math.min(focalMax, focal));
    }

    /**
     * Widest aperture at a focal length. Variable-aperture zooms lose light along the zoom range;
     * the loss is interpolated in stops against log focal length, which matches real lenses within
     * a third of a stop.
     */
    public double maxApertureAt(double focal) {
        if (!zoom || apertureMaxTele == apertureMax || focalMax == focalMin) return apertureMax;
        double f = clampFocal(focal);
        double t = Math.log(f / focalMin) / Math.log(focalMax / focalMin);
        double stopsWide = 2 * log2(apertureMax);
        double stopsTele = 2 * log2(apertureMaxTele);
        return Math.pow(2, (stopsWide + t * (stopsTele - stopsWide)) / 2);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    public static final class Coverage {
        public static final String APS_C = "APS-C";
        public static final String FULL_FRAME = "full-frame";

        private Coverage() {
        }
    }
}
