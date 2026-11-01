package com.lenspreset.app;

import android.content.res.AssetManager;

import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.json.JsonException;
import com.lenspreset.core.profile.ProfileSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profiles and language tables packed into the APK's assets by build.sh. */
final class Assets {
    private Assets() {
    }

    static List<ProfileSet.Entry> profiles(AssetManager assets) {
        List<ProfileSet.Entry> out = new ArrayList<ProfileSet.Entry>();
        read(assets, out, "profiles/cameras", ProfileSet.Kind.CAMERA);
        read(assets, out, "profiles/lenses", ProfileSet.Kind.LENS);
        read(assets, out, "profiles/presets", ProfileSet.Kind.PRESET);
        return out;
    }

    private static void read(AssetManager assets, List<ProfileSet.Entry> out, String dir, ProfileSet.Kind kind) {
        String[] names;
        try {
            names = assets.list(dir);
        } catch (IOException e) {
            return;
        }
        Arrays.sort(names);
        for (String name : names) {
            if (!name.endsWith(".json")) continue;
            String origin = "apk:" + dir + "/" + name;
            try {
                out.add(new ProfileSet.Entry(kind, origin, Text.read(assets.open(dir + "/" + name)), false));
            } catch (IOException e) {
                out.add(new ProfileSet.Entry(kind, origin, null, false));
            }
        }
    }

    /** Every {@code i18n/<lang>.json}. English is required; a broken table is left out. */
    static Map<String, Map<String, String>> languages(AssetManager assets) throws IOException {
        Map<String, Map<String, String>> tables = new LinkedHashMap<String, Map<String, String>>();
        String[] names = assets.list("i18n");
        Arrays.sort(names);
        for (String name : names) {
            if (!name.endsWith(".json")) continue;
            try {
                tables.put(name.substring(0, name.length() - 5), I18n.parseTable(Text.read(assets.open("i18n/" + name))));
            } catch (JsonException e) {
                if ("en.json".equals(name)) throw new IOException("i18n/en.json: " + e.getMessage());
            }
        }
        if (!tables.containsKey(I18n.FALLBACK)) throw new IOException("i18n/en.json is missing");
        return tables;
    }
}
