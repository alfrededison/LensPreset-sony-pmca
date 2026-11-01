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

    public Conditions(Double focalMm, Double liveMaxAperture, Map<String, Integer> overrides) {
        this.focalMm = focalMm;
        this.liveMaxAperture = liveMaxAperture;
        this.overrides = overrides == null ? Collections.<String, Integer>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, Integer>(overrides));
    }

    public static Conditions none() {
        return new Conditions(null, null, null);
    }
}
