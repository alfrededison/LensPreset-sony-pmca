package com.lenspreset.core.model;

import com.lenspreset.core.optics.Shutter;

import java.util.List;
import java.util.Map;

/**
 * A shooting situation, written as rules rather than numbers, so one preset works on every body
 * and lens. Read from {@code profiles/presets/*.json}; {@link com.lenspreset.core.engine.PresetEngine}
 * turns it into settings.
 */
public final class Preset {
    public enum ExposureMode { PROGRAM, APERTURE_PRIORITY, SHUTTER_PRIORITY, MANUAL }

    public final String id;
    public final Map<String, String> name;
    public final Map<String, String> description;
    public final String goal;
    /** Position in the preset list; ties sort by id. */
    public final int order;
    public final ExposureMode exposureMode;
    public final ApertureRule aperture;
    public final ShutterRule shutter;
    public final IsoRule iso;
    public final double ev;
    public final String metering;
    public final AfRule af;
    public final String drive;
    public final int selfTimer;
    public final StyleRule style;
    public final Subject subject;
    public final List<String> hints;
    public final List<String> overlays;
    public final Map<String, Adjust> adjust;

    public Preset(String id, Map<String, String> name, Map<String, String> description, String goal,
                  int order, ExposureMode exposureMode, ApertureRule aperture, ShutterRule shutter,
                  IsoRule iso, double ev, String metering, AfRule af, String drive, int selfTimer,
                  StyleRule style, Subject subject, List<String> hints, List<String> overlays,
                  Map<String, Adjust> adjust) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.goal = goal;
        this.order = order;
        this.exposureMode = exposureMode;
        this.aperture = aperture;
        this.shutter = shutter;
        this.iso = iso;
        this.ev = ev;
        this.metering = metering;
        this.af = af;
        this.drive = drive;
        this.selfTimer = selfTimer;
        this.style = style;
        this.subject = subject;
        this.hints = hints;
        this.overlays = overlays;
        this.adjust = adjust;
    }

    public static final class ApertureRule {
        public static final String WIDEST = "widest";
        public static final String WIDEST_SAFE = "widest_safe";
        public static final String SWEET_SPOT = "sweet_spot";
        public static final String DOF = "dof";
        public static final String FIXED = "fixed";

        public final String strategy;
        /** widest_safe: stops to close down from the widest aperture. */
        public final double stopDownFromMax;
        /** sweet_spot: {@code low}, {@code mid} or {@code high} end of the lens's sweet spot. */
        public final String position;
        /** fixed: the f-number. */
        public final double value;
        /** Bounds applied after the strategy; null when absent. */
        public final Double minF;
        public final Double maxF;
        /** dof: depth that must be sharp around the focus point for one row of people, in metres. */
        public final double depthM;
        /** dof: extra depth for each additional row. */
        public final double perRowM;

        public ApertureRule(String strategy, double stopDownFromMax, String position, double value,
                            Double minF, Double maxF, double depthM, double perRowM) {
            this.strategy = strategy;
            this.stopDownFromMax = stopDownFromMax;
            this.position = position;
            this.value = value;
            this.minF = minF;
            this.maxF = maxF;
            this.depthM = depthM;
            this.perRowM = perRowM;
        }
    }

    public static final class ShutterRule {
        public static final String HANDHELD_MIN = "handheld_min";
        public static final String FIXED = "fixed";

        public final String strategy;
        /** Slowest speed the subject allows (handheld_min); null for none. */
        public final Shutter min;
        /** fixed: the speed. */
        public final Shutter value;
        /** How many stops a stabilised lens or body buys. */
        public final double stabilizationStops;

        public ShutterRule(String strategy, Shutter min, Shutter value, double stabilizationStops) {
            this.strategy = strategy;
            this.min = min;
            this.value = value;
            this.stabilizationStops = stabilizationStops;
        }
    }

    public static final class IsoRule {
        public final boolean auto;
        /** Fixed ISO when not auto. */
        public final int value;
        /** Auto ISO ceiling; null uses the camera's own ceiling. */
        public final Integer max;
        public final Integer min;

        public IsoRule(boolean auto, int value, Integer max, Integer min) {
            this.auto = auto;
            this.value = value;
            this.max = max;
            this.min = min;
        }
    }

    public static final class AfRule {
        public static final String SINGLE = "single";
        public static final String CONTINUOUS = "continuous";
        public static final String AUTOMATIC = "automatic";
        public static final String MANUAL = "manual";

        public final String mode;
        /** {@code wide}, {@code zone}, {@code center}, {@code flexible_spot}, {@code tracking}, or null to leave it. */
        public final String area;
        public final boolean face;
        /** {@code off} or {@code if_available}. */
        public final String eye;

        public AfRule(String mode, String area, boolean face, String eye) {
            this.mode = mode;
            this.area = area;
            this.face = face;
            this.eye = eye;
        }
    }

    public static final class StyleRule {
        /** Sony Creative Style, e.g. {@code portrait}; null leaves the camera's. */
        public final String creativeStyle;
        public final String fallback;
        /** {@code off}, {@code low}, {@code mid}, {@code high}; null leaves the camera's. */
        public final String softSkin;

        public StyleRule(String creativeStyle, String fallback, String softSkin) {
            this.creativeStyle = creativeStyle;
            this.fallback = fallback;
            this.softSkin = softSkin;
        }
    }

    public static final class Subject {
        public static final String HEAD_SHOULDERS = "head_shoulders";
        public static final String HALF_BODY = "half_body";
        public static final String FULL_BODY = "full_body";
        public static final String ENVIRONMENT = "environment";
        public static final String GROUP = "group";

        public final String framing;
        /** group: people standing side by side in one row. */
        public final int peoplePerRow;

        public Subject(String framing, int peoplePerRow) {
            this.framing = framing;
            this.peoplePerRow = peoplePerRow;
        }
    }

    /** A value the user may change from the preset list (left / right). */
    public static final class Adjust {
        public static final String ROWS = "rows";
        public static final String SELF_TIMER = "self_timer";

        /** Allowed values in order. */
        public final int[] values;
        public final int defaultValue;

        public Adjust(int[] values, int defaultValue) {
            this.values = values;
            this.defaultValue = defaultValue;
        }

        /** The value {@code steps} away from {@code current}, clamped to the ends. */
        public int step(int current, int steps) {
            int idx = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == current) idx = i;
            idx = Math.max(0, Math.min(values.length - 1, idx + steps));
            return values[idx];
        }

        public boolean allows(int v) {
            for (int x : values) if (x == v) return true;
            return false;
        }
    }
}
