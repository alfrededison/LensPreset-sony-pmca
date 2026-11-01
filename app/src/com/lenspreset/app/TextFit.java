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
