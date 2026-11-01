package com.lenspreset.core.model;

import java.util.List;
import java.util.Map;

/** What a camera body can do. Read from {@code profiles/cameras/*.json}. */
public final class CameraProfile {
    public enum Status { VERIFIED, COMMUNITY_TESTED, UNTESTED }

    public final String id;
    public final String name;
    /** Values of the {@code model.name} property this profile answers to, e.g. {@code ILCE-5100}. */
    public final List<String> models;
    public final Sensor sensor;
    public final boolean ibis;
    /** Lens profile id of a compact's built-in lens; null on interchangeable-lens bodies. */
    public final String fixedLens;
    public final int isoMin;
    public final int isoMax;
    /** Highest ISO the camera's Auto ISO may reach, when known; null means {@link #isoMax}. */
    public final Integer isoAutoMax;
    /** Exposure compensation range, plus and minus, in EV. */
    public final double evRange;
    public final Double burstFpsLo;
    public final Double burstFpsHi;
    public final Controls controls;
    public final Map<String, Feature> features;
    public final Status status;
    public final List<String> testedFirmware;
    public final List<String> testedBy;

    public CameraProfile(String id, String name, List<String> models, Sensor sensor, boolean ibis,
                         String fixedLens, int isoMin, int isoMax, Integer isoAutoMax, double evRange,
                         Double burstFpsLo, Double burstFpsHi, Controls controls,
                         Map<String, Feature> features, Status status, List<String> testedFirmware,
                         List<String> testedBy) {
        this.id = id;
        this.name = name;
        this.models = models;
        this.sensor = sensor;
        this.ibis = ibis;
        this.fixedLens = fixedLens;
        this.isoMin = isoMin;
        this.isoMax = isoMax;
        this.isoAutoMax = isoAutoMax;
        this.evRange = evRange;
        this.burstFpsLo = burstFpsLo;
        this.burstFpsHi = burstFpsHi;
        this.controls = controls;
        this.features = features;
        this.status = status;
        this.testedFirmware = testedFirmware;
        this.testedBy = testedBy;
    }

    public Feature feature(String name) {
        Feature f = features.get(name);
        return f == null ? Feature.UNKNOWN : f;
    }

    public int autoIsoCeiling() {
        return isoAutoMax != null ? isoAutoMax : isoMax;
    }

    public static final class Sensor {
        public final String format;
        public final double widthMm;
        public final double heightMm;
        /** Circle of confusion used for depth of field. */
        public final double cocMm;

        public Sensor(String format, double widthMm, double heightMm, double cocMm) {
            this.format = format;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
            this.cocMm = cocMm;
        }

        public double diagonalMm() {
            return Math.sqrt(widthMm * widthMm + heightMm * heightMm);
        }
    }

    public static final class Controls {
        public final boolean touchscreen;
        public final boolean frontDial;
        public final boolean rearWheel;
        public final boolean topDial;
        public final int customKeys;

        public Controls(boolean touchscreen, boolean frontDial, boolean rearWheel, boolean topDial, int customKeys) {
            this.touchscreen = touchscreen;
            this.frontDial = frontDial;
            this.rearWheel = rearWheel;
            this.topDial = topDial;
            this.customKeys = customKeys;
        }
    }
}
