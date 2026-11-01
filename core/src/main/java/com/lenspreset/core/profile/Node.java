package com.lenspreset.core.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON object being read into a profile. Every accessor checks the type and range and names
 * the full path of a bad field, so a contributor sees {@code aperture.stop_down_from_max} rather
 * than a ClassCastException. Keys the reader never asked for are reported by {@link #finish()}:
 * a misspelt key is an error, not a silently ignored setting.
 */
final class Node {
    private final Map<String, Object> map;
    private final String path;
    private final Set<String> seen = new java.util.HashSet<String>();

    @SuppressWarnings("unchecked")
    Node(Object value, String path) throws ProfileException {
        if (!(value instanceof Map)) throw new ProfileException(label(path) + "must be an object");
        this.map = (Map<String, Object>) value;
        this.path = path;
    }

    private static String label(String path) {
        return path.length() == 0 ? "" : path + ": ";
    }

    private String at(String key) {
        return path.length() == 0 ? key : path + "." + key;
    }

    boolean has(String key) {
        return map.containsKey(key) && map.get(key) != null;
    }

    private Object raw(String key) {
        seen.add(key);
        return map.get(key);
    }

    String string(String key) throws ProfileException {
        Object v = raw(key);
        if (!(v instanceof String) || ((String) v).length() == 0)
            throw new ProfileException(at(key) + ": required text is missing");
        return (String) v;
    }

    String optString(String key, String fallback) throws ProfileException {
        Object v = raw(key);
        if (v == null) return fallback;
        if (!(v instanceof String)) throw new ProfileException(at(key) + ": must be text");
        return (String) v;
    }

    String oneOf(String key, String fallback, String... allowed) throws ProfileException {
        String v = fallback == null ? string(key) : optString(key, fallback);
        for (String a : allowed) if (a.equals(v)) return v;
        throw new ProfileException(at(key) + ": \"" + v + "\" is not one of " + join(allowed));
    }

    double number(String key, double min, double max) throws ProfileException {
        Object v = raw(key);
        if (!(v instanceof Double)) throw new ProfileException(at(key) + ": required number is missing");
        return check(key, (Double) v, min, max);
    }

    double optNumber(String key, double fallback, double min, double max) throws ProfileException {
        Object v = raw(key);
        if (v == null) return fallback;
        if (!(v instanceof Double)) throw new ProfileException(at(key) + ": must be a number");
        return check(key, (Double) v, min, max);
    }

    /** A number, or null when the key is absent or JSON null. */
    Double optNumberOrNull(String key, double min, double max) throws ProfileException {
        Object v = raw(key);
        if (v == null) return null;
        if (!(v instanceof Double)) throw new ProfileException(at(key) + ": must be a number");
        return Double.valueOf(check(key, (Double) v, min, max));
    }

    int optInt(String key, int fallback, int min, int max) throws ProfileException {
        double d = optNumber(key, fallback, min, max);
        if (d != Math.rint(d)) throw new ProfileException(at(key) + ": must be a whole number");
        return (int) d;
    }

    int integer(String key, int min, int max) throws ProfileException {
        double d = number(key, min, max);
        if (d != Math.rint(d)) throw new ProfileException(at(key) + ": must be a whole number");
        return (int) d;
    }

    private double check(String key, double v, double min, double max) throws ProfileException {
        if (v < min || v > max)
            throw new ProfileException(at(key) + ": " + fmt(v) + " is outside " + fmt(min) + ".." + fmt(max));
        return v;
    }

    boolean bool(String key) throws ProfileException {
        Object v = raw(key);
        if (!(v instanceof Boolean)) throw new ProfileException(at(key) + ": must be true or false");
        return (Boolean) v;
    }

    boolean optBool(String key, boolean fallback) throws ProfileException {
        Object v = raw(key);
        if (v == null) return fallback;
        if (!(v instanceof Boolean)) throw new ProfileException(at(key) + ": must be true or false");
        return (Boolean) v;
    }

    Node object(String key) throws ProfileException {
        Object v = raw(key);
        if (v == null) throw new ProfileException(at(key) + ": required object is missing");
        return new Node(v, at(key));
    }

    Node optObject(String key) throws ProfileException {
        Object v = raw(key);
        return v == null ? null : new Node(v, at(key));
    }

    List<String> optStrings(String key) throws ProfileException {
        Object v = raw(key);
        if (v == null) return Collections.emptyList();
        if (!(v instanceof List)) throw new ProfileException(at(key) + ": must be a list of text");
        List<String> out = new ArrayList<String>();
        for (Object o : (List<?>) v) {
            if (!(o instanceof String)) throw new ProfileException(at(key) + ": must be a list of text");
            out.add((String) o);
        }
        return out;
    }

    List<Double> optNumbers(String key) throws ProfileException {
        Object v = raw(key);
        if (v == null) return Collections.emptyList();
        if (!(v instanceof List)) throw new ProfileException(at(key) + ": must be a list of numbers");
        List<Double> out = new ArrayList<Double>();
        for (Object o : (List<?>) v) {
            if (!(o instanceof Double)) throw new ProfileException(at(key) + ": must be a list of numbers");
            out.add((Double) o);
        }
        return out;
    }

    /** A map of language code to text, e.g. {@code {"en": "...", "vi": "..."}}; a plain string means English. */
    Map<String, String> localized(String key, boolean required) throws ProfileException {
        Object v = raw(key);
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (v == null) {
            if (required) throw new ProfileException(at(key) + ": required text is missing");
            return out;
        }
        if (v instanceof String) {
            out.put("en", (String) v);
            return out;
        }
        if (!(v instanceof Map)) throw new ProfileException(at(key) + ": must be text or {\"en\": ..., \"vi\": ...}");
        for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
            if (!(e.getValue() instanceof String))
                throw new ProfileException(at(key) + "." + e.getKey() + ": must be text");
            out.put((String) e.getKey(), (String) e.getValue());
        }
        if (!out.containsKey("en")) throw new ProfileException(at(key) + ": needs an \"en\" entry");
        return out;
    }

    /** Keys the reader did not consume. Called once a profile has been read completely. */
    void finish() throws ProfileException {
        for (String key : map.keySet()) {
            if (!seen.contains(key)) throw new ProfileException(at(key) + ": unknown key");
        }
    }

    Set<String> keys() {
        return map.keySet();
    }

    String path() {
        return path;
    }

    static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return Long.toString((long) d);
        return Double.toString(d);
    }

    private static String join(String[] items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(items[i]);
        }
        return sb.toString();
    }
}
