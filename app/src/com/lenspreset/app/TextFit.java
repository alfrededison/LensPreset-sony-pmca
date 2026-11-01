package com.lenspreset.app;

import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

/** Fitting text to a width with a paint's current size. */
final class TextFit {
    private TextFit() {
    }

    static String ellipsize(Paint paint, String s, float max) {
        if (max <= 0) return "";
        if (paint.measureText(s) <= max) return s;
        String e = "…";
        int n = s.length();
        while (n > 0 && paint.measureText(s.substring(0, n) + e) > max) n--;
        return s.substring(0, n) + e;
    }

    /** Wrap whole setting labels, so values such as ISO AUTO 100–6400 stay together. */
    static List<String> chips(Paint paint, List<String> items, float max) {
        List<String> out = new ArrayList<String>();
        String line = "";
        for (String item : items) {
            String label = ellipsize(paint, item, max);
            String candidate = line.length() == 0 ? label : line + "   " + label;
            if (line.length() > 0 && paint.measureText(candidate) > max) {
                out.add(line);
                line = label;
            } else line = candidate;
        }
        if (line.length() > 0) out.add(line);
        return out;
    }

    /** Word wrap; a single word wider than {@code max} gets a line of its own. */
    static List<String> wrap(Paint paint, String s, float max) {
        List<String> out = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) <= max || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
