package com.lenspreset.core.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** What the camera reports right now, and the user's adjustments to the preset. */
public final class Conditions {
    /** Current focal length in mm, or null when the camera does not say (a zoom then assumes its wide end). */
    public final Double focalMm;
    /** Widest aperture the lens offers right now, as the camera reports it; null when unknown. */
    public final Double liveMaxAperture;
    /** Values chosen for the preset's {@code adjust} settings, by key. */
    public final Map<String, Integer> overrides;
    /** Leave Creative Style and Soft Skin unchanged while applying shooting settings. */
    public final boolean preserveLook;
    /** User-selected absolute exposure compensation in third stops; null uses the preset's EV. */
    public final Integer evThirds;
    /** Null follows the preset; 0 selects Auto ISO; positive values select a fixed ISO. */
    public final Integer iso;

    public Conditions(Double focalMm, Double liveMaxAperture, Map<String, Integer> overrides) {
        this(focalMm, liveMaxAperture, overrides, false);
    }

    public Conditions(Double focalMm, Double liveMaxAperture, Map<String, Integer> overrides,
                      boolean preserveLook) {
        this(focalMm, liveMaxAperture, overrides, preserveLook, null);
    }

    public Conditions(Double focalMm, Double liveMaxAperture, Map<String, Integer> overrides,
                      boolean preserveLook, Integer evThirds) {
        this(focalMm, liveMaxAperture, overrides, preserveLook, evThirds, null);
    }

    public Conditions(Double focalMm, Double liveMaxAperture, Map<String, Integer> overrides,
                      boolean preserveLook, Integer evThirds, Integer iso) {
        this.focalMm = focalMm;
        this.liveMaxAperture = liveMaxAperture;
        this.overrides = overrides == null ? Collections.<String, Integer>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, Integer>(overrides));
        this.preserveLook = preserveLook;
        this.evThirds = evThirds;
        this.iso = iso;
    }

    public static Conditions none() {
        return new Conditions(null, null, null);
    }
}
