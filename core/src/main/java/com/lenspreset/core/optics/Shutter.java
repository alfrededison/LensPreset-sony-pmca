package com.lenspreset.core.optics;

import java.util.Locale;

/** A shutter speed, kept as the camera's own fraction ({@code 1/125}, {@code 13/10}). */
public final class Shutter {
    public final int numerator;
    public final int denominator;

    public Shutter(int numerator, int denominator) {
        if (numerator <= 0 || denominator <= 0) throw new IllegalArgumentException("shutter must be positive");
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public double seconds() {
        return numerator / (double) denominator;
    }

    /** Light-gathering in stops relative to one second; faster speeds are negative. */
    public double stops() {
        return Math.log(seconds()) / Math.log(2);
    }

    /**
     * Parses {@code 1/125}, {@code 0.5}, {@code 2"} or {@code 2s} and snaps it to the camera's scale
     * ({@code 1/7} becomes {@code 1/8}), since the camera can only step between marked speeds.
     * Returns null for anything else or anything over 60 s; callers report the field name.
     */
    public static Shutter parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        double sec;
        try {
            int slash = t.indexOf('/');
            if (slash > 0) {
                int n = Integer.parseInt(t.substring(0, slash).trim());
                int d = Integer.parseInt(t.substring(slash + 1).trim());
                if (n <= 0 || d <= 0) return null;
                sec = n / (double) d;
            } else {
                if (t.endsWith("\"") || t.endsWith("s")) t = t.substring(0, t.length() - 1);
                sec = Double.parseDouble(t);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (!(sec > 0) || sec > 60) return null;
        return Scales.nearestShutter(sec);
    }

    /** As the camera shows it: {@code 1/125}, {@code 0.5"}, {@code 2"}. */
    public String format() {
        if (numerator == 1 && denominator > 2) return "1/" + denominator;
        if (denominator == 1) return numerator + "\"";
        double sec = seconds();
        if (sec >= 1 && sec == Math.rint(sec)) return ((long) sec) + "\"";
        return String.format(Locale.US, "%.1f\"", sec);
    }

    public boolean fasterThan(Shutter other) {
        return seconds() < other.seconds() - 1e-9;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Shutter)) return false;
        Shutter s = (Shutter) o;
        return Math.abs(seconds() - s.seconds()) < 1e-9;
    }

    @Override
    public int hashCode() {
        return (int) Math.round(seconds() * 1e6);
    }

    @Override
    public String toString() {
        return format();
    }
}
