package com.lenspreset.core.optics;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** ISO choices for the wheel: Auto (0), then the body's supported fixed sensitivities. */
public final class IsoScale {
    private IsoScale() {
    }

    private static final int[] STANDARD = {
            50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800,
            1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400, 8000,
            10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200,
            64000, 80000, 102400, 128000, 160000, 204800, 256000, 320000, 409600
    };

    /** A missing capability list falls back to standard third stops inside the profile's range. */
    public static List<Integer> values(List<?> supported, int min, int max) {
        TreeSet<Integer> choices = new TreeSet<Integer>();
        choices.add(0);
        if (supported == null || supported.isEmpty()) {
            for (int iso : STANDARD) if (iso >= min && iso <= max) choices.add(iso);
        } else {
            for (Object value : supported) {
                try {
                    int iso = Integer.parseInt(String.valueOf(value));
                    if (iso > 0 && iso >= min && iso <= max) choices.add(iso);
                } catch (NumberFormatException ignored) {
                    // Ignore nonnumeric labels; Auto is already present as 0.
                }
            }
        }
        return new ArrayList<Integer>(choices);
    }

    /** One step in either direction, clamped at Auto and the highest supported fixed ISO. */
    public static int step(int current, int direction, List<Integer> choices) {
        if (direction > 0) {
            for (int iso : choices) if (iso > current) return iso;
            return choices.get(choices.size() - 1);
        }
        if (direction < 0) {
            for (int i = choices.size() - 1; i >= 0; i--) if (choices.get(i) < current) return choices.get(i);
            return choices.get(0);
        }
        return current;
    }
}
