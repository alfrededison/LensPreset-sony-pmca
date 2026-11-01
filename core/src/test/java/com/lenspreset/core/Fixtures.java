package com.lenspreset.core;

import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.CameraProfile;
import com.lenspreset.core.model.LensProfile;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.profile.ProfileException;
import com.lenspreset.core.profile.ProfileParser;
import com.lenspreset.core.profile.ProfileSet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the repository's own profiles and language tables. Tests run from the repository root. */
public final class Fixtures {
    private Fixtures() {
    }

    public static File root() {
        String prop = System.getProperty("repo.root");
        File dir = new File(prop != null ? prop : ".").getAbsoluteFile();
        if (!new File(dir, "profiles").isDirectory())
            throw new IllegalStateException("run the tests from the repository root (or set -Drepo.root)");
        return dir;
    }

    public static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<File> jsonFiles(String dir) {
        File[] files = new File(root(), dir).listFiles();
        List<File> out = new ArrayList<File>();
        if (files != null) for (File f : files) if (f.getName().endsWith(".json")) out.add(f);
        out.sort(null);
        return out;
    }

    public static ProfileSet bundled() {
        List<ProfileSet.Entry> entries = new ArrayList<ProfileSet.Entry>();
        for (File f : jsonFiles("profiles/cameras"))
            entries.add(new ProfileSet.Entry(ProfileSet.Kind.CAMERA, f.getName(), read(f), false));
        for (File f : jsonFiles("profiles/lenses"))
            entries.add(new ProfileSet.Entry(ProfileSet.Kind.LENS, f.getName(), read(f), false));
        for (File f : jsonFiles("profiles/presets"))
            entries.add(new ProfileSet.Entry(ProfileSet.Kind.PRESET, f.getName(), read(f), false));
        return ProfileSet.load(entries);
    }

    public static CameraProfile camera(String id) {
        return parse(ProfileSet.Kind.CAMERA, "profiles/cameras/" + id + ".json");
    }

    public static LensProfile lens(String id) {
        return parse(ProfileSet.Kind.LENS, "profiles/lenses/" + id + ".json");
    }

    public static Preset preset(String id) {
        return parse(ProfileSet.Kind.PRESET, "profiles/presets/" + id + ".json");
    }

    @SuppressWarnings("unchecked")
    private static <T> T parse(ProfileSet.Kind kind, String path) {
        String text = read(new File(root(), path));
        try {
            switch (kind) {
                case CAMERA: return (T) ProfileParser.camera(text);
                case LENS: return (T) ProfileParser.lens(text);
                default: return (T) ProfileParser.preset(text);
            }
        } catch (ProfileException e) {
            throw new AssertionError(path + ": " + e.getMessage());
        }
    }

    public static Map<String, Map<String, String>> languageTables() {
        Map<String, Map<String, String>> tables = new LinkedHashMap<String, Map<String, String>>();
        for (File f : jsonFiles("i18n")) {
            try {
                tables.put(f.getName().replace(".json", ""), I18n.parseTable(read(f)));
            } catch (Exception e) {
                throw new AssertionError(f.getName() + ": " + e.getMessage());
            }
        }
        return tables;
    }

    public static I18n i18n(String lang) {
        return I18n.of(languageTables(), lang);
    }

    /** A camera profile JSON with one field replaced, for testing feature fallbacks. */
    public static CameraProfile cameraWithFeatures(String id, String... pairs) {
        String text = read(new File(root(), "profiles/cameras/" + id + ".json"));
        StringBuilder features = new StringBuilder("\"features\": {");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (i > 0) features.append(", ");
            features.append('"').append(pairs[i]).append("\": \"").append(pairs[i + 1]).append('"');
        }
        features.append('}');
        String replaced = text.replaceFirst("(?s)\"features\": \\{[^}]*\\}", features.toString());
        try {
            return ProfileParser.camera(replaced);
        } catch (ProfileException e) {
            throw new AssertionError(e.getMessage() + " in " + Arrays.toString(pairs));
        }
    }
}
