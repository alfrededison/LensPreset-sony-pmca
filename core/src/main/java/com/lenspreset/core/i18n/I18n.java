package com.lenspreset.core.i18n;

import com.lenspreset.core.json.Json;
import com.lenspreset.core.json.JsonException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every word on screen goes through here. Tables are flat JSON objects in {@code i18n/<lang>.json},
 * key to text, with {@code {name}} placeholders. A key missing from a language falls back to
 * English, then to the key itself, so a half-finished translation still works.
 */
public final class I18n {
    public static final String FALLBACK = "en";
    /** Key every table must have: the language's own name, as shown in the language menu. */
    public static final String LANGUAGE_NAME = "language.name";

    private final Map<String, Map<String, String>> tables;
    private final String lang;

    private I18n(Map<String, Map<String, String>> tables, String lang) {
        this.tables = tables;
        this.lang = tables.containsKey(lang) ? lang : FALLBACK;
    }

    /** Parses a table. Values must be text. */
    public static Map<String, String> parseTable(String json) throws JsonException {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) throw new JsonException("a language table must be an object", 1, 1);
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) root).entrySet()) {
            if (!(e.getValue() instanceof String))
                throw new JsonException("\"" + e.getKey() + "\" must be text", 1, 1);
            out.put((String) e.getKey(), (String) e.getValue());
        }
        return out;
    }

    /** {@code tables} maps a language code to its table and must contain English. */
    public static I18n of(Map<String, Map<String, String>> tables, String lang) {
        if (!tables.containsKey(FALLBACK)) throw new IllegalArgumentException("no English table");
        return new I18n(tables, lang);
    }

    public I18n withLanguage(String lang) {
        return new I18n(tables, lang);
    }

    /** The language for a choice: an explicit code, or "auto" meaning the system language. */
    public static String resolve(String choice, String systemLanguage, List<String> available) {
        if (choice != null && !"auto".equals(choice) && available.contains(choice)) return choice;
        if (systemLanguage != null && available.contains(systemLanguage)) return systemLanguage;
        return FALLBACK;
    }

    public String language() {
        return lang;
    }

    public List<String> languages() {
        List<String> out = new ArrayList<String>(tables.keySet());
        Collections.sort(out);
        return out;
    }

    public String languageName(String code) {
        Map<String, String> t = tables.get(code);
        String name = t == null ? null : t.get(LANGUAGE_NAME);
        return name == null ? code : name;
    }

    public boolean has(String key) {
        return tables.get(FALLBACK).containsKey(key) || tables.get(lang).containsKey(key);
    }

    public String t(String key) {
        return t(key, Collections.<String, String>emptyMap());
    }

    public String t(String key, Map<String, String> args) {
        String s = tables.get(lang).get(key);
        if (s == null) s = tables.get(FALLBACK).get(key);
        if (s == null) return key;
        return format(s, args);
    }

    /** Text from a profile's {@code {"en": ..., "vi": ...}} map. */
    public String pick(Map<String, String> localized) {
        if (localized == null || localized.isEmpty()) return "";
        String s = localized.get(lang);
        if (s == null) s = localized.get(FALLBACK);
        if (s == null) s = localized.values().iterator().next();
        return s;
    }

    public static String format(String template, Map<String, String> args) {
        if (args.isEmpty() || template.indexOf('{') < 0) return template;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            int close = c == '{' ? template.indexOf('}', i) : -1;
            if (close > i) {
                String name = template.substring(i + 1, close);
                String v = args.get(name);
                if (v != null) {
                    sb.append(v);
                    i = close + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Placeholder names in a template, for the consistency test. */
    public static List<String> placeholders(String template) {
        List<String> out = new ArrayList<String>();
        int i = 0;
        while ((i = template.indexOf('{', i)) >= 0) {
            int close = template.indexOf('}', i);
            if (close < 0) break;
            out.add(template.substring(i + 1, close));
            i = close + 1;
        }
        Collections.sort(out);
        return out;
    }
}
