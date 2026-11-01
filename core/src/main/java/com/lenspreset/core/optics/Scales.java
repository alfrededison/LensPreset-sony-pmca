package com.lenspreset.core.optics;

import java.util.Locale;

/**
 * The camera's third-stop scales. Every value the engine proposes lands on one of these, so the
 * overlay never shows an f-number or speed the camera cannot set.
 */
public final class Scales {
    private Scales() {
    }

    /** Marked f-numbers in third stops, as Sony bodies display them. */
    public static final double[] APERTURES = {
            1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3,
            7.1, 8.0, 9.0, 10, 11, 13, 14, 16, 18, 20, 22, 25, 29, 32, 36, 40, 45,
    };

    /** Shutter speeds in third stops from 1/4000 to 30", as numerator/denominator pairs. */
    public static final int[][] SHUTTERS = {
            {1, 4000}, {1, 3200}, {1, 2500}, {1, 2000}, {1, 1600}, {1, 1250}, {1, 1000}, {1, 800},
            {1, 640}, {1, 500}, {1, 400}, {1, 320}, {1, 250}, {1, 200}, {1, 160}, {1, 125},
            {1, 100}, {1, 80}, {1, 60}, {1, 50}, {1, 40}, {1, 30}, {1, 25}, {1, 20}, {1, 15},
            {1, 13}, {1, 10}, {1, 8}, {1, 6}, {1, 5}, {1, 4}, {1, 3}, {10, 25}, {1, 2}, {10, 16},
            {4, 5}, {1, 1}, {13, 10}, {16, 10}, {2, 1}, {25, 10}, {16, 5}, {4, 1}, {5, 1}, {6, 1},
            {8, 1}, {10, 1}, {13, 1}, {15, 1}, {20, 1}, {25, 1}, {30, 1},
    };

    /** Aperture in stops from f/1: f/1.4 is 1, f/2 is 2. */
    public static double apertureStops(double fNumber) {
        return 2 * Math.log(fNumber) / Math.log(2);
    }

    public static double fromApertureStops(double stops) {
        return Math.pow(2, stops / 2);
    }

    /** The marked f-number nearest in stops. */
    public static double nearestAperture(double fNumber) {
        double best = APERTURES[0];
        double bestDist = Double.MAX_VALUE;
        for (double a : APERTURES) {
            double d = Math.abs(apertureStops(a) - apertureStops(fNumber));
            if (d < bestDist) {
                bestDist = d;
                best = a;
            }
        }
        return best;
    }

    /** The smallest marked f-number at or above {@code fNumber} (allowing for rounding of the marks). */
    public static double apertureAtLeast(double fNumber) {
        for (double a : APERTURES) {
            if (apertureStops(a) >= apertureStops(fNumber) - 0.12) return a;
        }
        return APERTURES[APERTURES.length - 1];
    }

    /** The largest marked f-number at or below {@code fNumber}. */
    public static double apertureAtMost(double fNumber) {
        double best = APERTURES[0];
        for (double a : APERTURES) {
            if (apertureStops(a) <= apertureStops(fNumber) + 0.12) best = a;
        }
        return best;
    }

    public static Shutter nearestShutter(double seconds) {
        Shutter best = null;
        double bestDist = Double.MAX_VALUE;
        double target = Math.log(seconds);
        for (int[] s : SHUTTERS) {
            Shutter c = new Shutter(s[0], s[1]);
            double d = Math.abs(Math.log(c.seconds()) - target);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** The slowest scale speed that is still at least as fast as {@code seconds}. */
    public static Shutter shutterAtLeastAsFast(double seconds) {
        Shutter best = new Shutter(SHUTTERS[0][0], SHUTTERS[0][1]);
        for (int[] s : SHUTTERS) {
            Shutter c = new Shutter(s[0], s[1]);
            // 4% slack: the marks are rounded (1/125 is really 1/128).
            if (c.seconds() <= seconds * 1.04) best = c;
        }
        return best;
    }

    public static String formatAperture(double fNumber) {
        if (fNumber >= 10) return "f/" + Math.round(fNumber);
        return String.format(Locale.US, "f/%.1f", fNumber);
    }

    /** Snaps exposure compensation to third stops. */
    public static double snapEv(double ev) {
        return Math.round(ev * 3) / 3.0;
    }

    /** {@code +1.3}, {@code -0.7}, {@code 0.0}. */
    public static String formatEv(double ev) {
        double snapped = snapEv(ev);
        if (Math.abs(snapped) < 1e-9) return "0.0";
        return String.format(Locale.US, "%+.1f", snapped);
    }

    /** Distance in metres, to one decimal below 10 m. */
    public static String formatMetres(double m) {
        if (Double.isInfinite(m)) return "∞";
        if (m >= 10) return Long.toString(Math.round(m));
        return String.format(Locale.US, "%.1f", m);
    }
}
