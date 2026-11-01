package com.lenspreset.app;

import android.content.res.AssetManager;

import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.json.JsonException;
import com.lenspreset.core.profile.AssetBundle;
import com.lenspreset.core.profile.ProfileException;
import com.lenspreset.core.profile.ProfileSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profiles and language tables, packed by build.sh into the one asset {@link AssetBundle#ASSET}. */
final class Assets {
    private Assets() {
    }

    static final class Loaded {
        final Map<String, Map<String, String>> languages;
        final List<ProfileSet.Entry> profiles;

        Loaded(Map<String, Map<String, String>> languages, List<ProfileSet.Entry> profiles) {
            this.languages = languages;
            this.profiles = profiles;
        }
    }

    /** Reads the bundle. English is required; another broken table is left out. */
    static Loaded load(AssetManager assets) throws IOException {
        List<AssetBundle.Item> items;
        try {
            items = AssetBundle.parse(Text.readBytes(assets.open(AssetBundle.ASSET)));
        } catch (ProfileException e) {
            throw new IOException(e.getMessage());
        }
        Map<String, Map<String, String>> languages = new LinkedHashMap<String, Map<String, String>>();
        List<ProfileSet.Entry> profiles = new ArrayList<ProfileSet.Entry>();
        for (AssetBundle.Item item : items) {
            if ("i18n".equals(item.kind)) {
                String lang = item.name.endsWith(".json") ? item.name.substring(0, item.name.length() - 5) : item.name;
                try {
                    languages.put(lang, I18n.parseTable(item.text));
                } catch (JsonException e) {
                    if (I18n.FALLBACK.equals(lang)) throw new IOException("i18n/" + item.name + ": " + e.getMessage());
                }
                continue;
            }
            ProfileSet.Kind kind = "cameras".equals(item.kind) ? ProfileSet.Kind.CAMERA
                    : "lenses".equals(item.kind) ? ProfileSet.Kind.LENS
                    : "presets".equals(item.kind) ? ProfileSet.Kind.PRESET : null;
            if (kind != null) profiles.add(new ProfileSet.Entry(kind, "apk:" + item.kind + "/" + item.name, item.text, false));
        }
        if (!languages.containsKey(I18n.FALLBACK)) throw new IOException("i18n/en.json is missing");
        return new Loaded(languages, profiles);
    }
}
