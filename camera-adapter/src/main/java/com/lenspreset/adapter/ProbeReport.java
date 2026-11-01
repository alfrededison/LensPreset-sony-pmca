package com.lenspreset.adapter;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of PROBE.TXT: what the camera API answers on this body, section by section. A call
 * that is missing on the firmware or throws is recorded as such rather than aborting the probe.
 * Contributors attach the file to a compatibility report; it is what fills a camera profile's
 * {@code features}.
 */
public final class ProbeReport {
    /** A read that may throw. */
    public interface Read {
        Object get() throws Throwable;
    }

    private final List<String> lines = new ArrayList<String>();

    public ProbeReport section(String name) {
        if (!lines.isEmpty()) lines.add("");
        lines.add("[" + name + "]");
        return this;
    }

    public ProbeReport put(String key, Object value) {
        lines.add(key + " = " + format(value));
        return this;
    }

    /** Runs {@code read} and records its value, {@code missing} for a method the firmware lacks, or the error. */
    public ProbeReport probe(String key, Read read) {
        try {
            put(key, read.get());
        } catch (NoSuchMethodError e) {
            lines.add(key + " = missing (NoSuchMethodError)");
        } catch (NoClassDefFoundError e) {
            lines.add(key + " = missing (NoClassDefFoundError)");
        } catch (Throwable t) {
            lines.add(key + " = error (" + ApplyReport.describe(t) + ")");
        }
        return this;
    }

    public ProbeReport raw(String line) {
        lines.add(line);
        return this;
    }

    static String format(Object v) {
        if (v == null) return "null";
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) v;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(format(list.get(i)));
            }
            return sb.append(']').toString();
        }
        String s = String.valueOf(v);
        return s.replace('\n', ' ');
    }

    public List<String> lines() {
        return lines;
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append("\r\n");
        return sb.toString();
    }
}
