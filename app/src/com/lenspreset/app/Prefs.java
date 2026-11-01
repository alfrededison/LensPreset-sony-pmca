package com.lenspreset.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.lenspreset.core.model.Preset;

import java.util.HashMap;
import java.util.Map;

/** The user's choices, kept in app storage: they survive power-off, not an uninstall. */
final class Prefs {
    static final String AUTO = "auto";

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("lenspreset", Context.MODE_PRIVATE);
    }

    /** The preset last applied, or null. */
    String preset() {
        return sp.getString("preset", null);
    }

    void setPreset(String id) {
        sp.edit().putString("preset", id).commit();
    }

    /** A lens id, or {@link #AUTO} to follow what the camera reports. */
    String lens() {
        return sp.getString("lens", AUTO);
    }

    void setLens(String id) {
        sp.edit().putString("lens", id).commit();
    }

    /** A language code, or {@link #AUTO}. */
    String language() {
        return sp.getString("language", AUTO);
    }

    void setLanguage(String code) {
        sp.edit().putString("language", code).commit();
    }

    boolean preserveLook() {
        return sp.getBoolean("preserve_look", true);
    }

    void setPreserveLook(boolean enabled) {
        sp.edit().putBoolean("preserve_look", enabled).commit();
    }

    boolean autoReopen() {
        return sp.getBoolean("auto_reopen", false);
    }

    void setAutoReopen(boolean enabled) {
        sp.edit().putBoolean("auto_reopen", enabled).commit();
    }

    /** The user's values for a preset's {@code adjust} settings. */
    Map<String, Integer> overrides(Preset p) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (String key : p.adjust.keySet()) {
            String k = "adjust." + p.id + "." + key;
            if (sp.contains(k)) out.put(key, sp.getInt(k, 0));
        }
        return out;
    }

    void setOverride(Preset p, String key, int value) {
        sp.edit().putInt("adjust." + p.id + "." + key, value).commit();
    }

    /** Absolute EV in third stops, saved separately from the preset's optional adjust settings. */
    Integer evThirds(Preset p) {
        String key = "ev." + p.id;
        return sp.contains(key) ? sp.getInt(key, 0) : null;
    }

    void setEvThirds(Preset p, int value) {
        sp.edit().putInt("ev." + p.id, value).commit();
    }

    Integer iso(Preset p) {
        String key = "iso." + p.id;
        return sp.contains(key) ? sp.getInt(key, 0) : null;
    }

    void setIso(Preset p, int value) {
        sp.edit().putInt("iso." + p.id, value).commit();
    }
}
