package com.lenspreset.core.i18n;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.profile.ProfileParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nTest {
    private static final Map<String, Map<String, String>> TABLES = Fixtures.languageTables();
    private static final Map<String, String> EN = TABLES.get("en");

    /** Key prefixes the code builds at run time, with the values it appends. */
    private static List<String> dynamicKeys() {
        List<String> keys = new ArrayList<String>();
        for (String f : ProfileParser.FRAMINGS) keys.add("hint.distance." + f);
        for (String m : ProfileParser.METERING) if (!"multi".equals(m)) keys.add("hud.metering." + m);
        for (String d : ProfileParser.DRIVES) if (!"single".equals(d)) keys.add("hud." + d);
        for (String s : ProfileParser.CREATIVE_STYLES) keys.add("style." + s);
        for (Preset p : Fixtures.bundled().presets()) for (String h : p.hints) keys.add("hint." + h);
        for (String s : Arrays.asList("verified", "community_tested", "untested")) keys.add("status." + s);
        keys.add("ui.adjust." + Preset.Adjust.ROWS);
        keys.add("ui.adjust." + Preset.Adjust.SELF_TIMER);
        keys.addAll(applyItemKeys());
        return keys;
    }

    /** Every item key SonyCamera reports, shown as {@code apply.item.<key>}. */
    private static List<String> applyItemKeys() {
        String src = Fixtures.read(new File(Fixtures.root(),
                "camera-adapter/src/main/java/com/lenspreset/adapter/SonyCamera.java"));
        List<String> keys = new ArrayList<String>();
        Matcher m = Pattern.compile("(?:report\\.add\\(|\\(report, )\"([a-z_]+)\"|\"(aperture|shutter)\" : \"(shutter)\"").matcher(src);
        while (m.find()) {
            for (int g = 1; g <= 3; g++) if (m.group(g) != null) keys.add("apply.item." + m.group(g));
        }
        assertTrue(keys.size() > 10, "found " + keys);
        return keys;
    }

    @Test
    void englishAndVietnameseAreBundled() {
        assertTrue(TABLES.containsKey("en"));
        assertTrue(TABLES.containsKey("vi"));
    }

    @Test
    void everyTableHasEveryEnglishKeyWithTheSamePlaceholders() {
        for (Map.Entry<String, Map<String, String>> t : TABLES.entrySet()) {
            for (Map.Entry<String, String> e : EN.entrySet()) {
                String text = t.getValue().get(e.getKey());
                assertTrue(text != null, t.getKey() + " is missing " + e.getKey());
                assertEquals(I18n.placeholders(e.getValue()), I18n.placeholders(text),
                        t.getKey() + " placeholders of " + e.getKey());
            }
            for (String key : t.getValue().keySet())
                assertTrue(EN.containsKey(key), t.getKey() + " has " + key + " which English does not");
        }
    }

    @Test
    void everyKeyTheCodeUsesExists() throws IOException {
        Pattern literal = Pattern.compile("\"((?:hint|notice|hud|menu|ui|apply|probe|style|lens|status)\\.[a-z0-9_.]*[a-z0-9_])\"");
        TreeSet<String> missing = new TreeSet<String>();
        for (String dir : Arrays.asList("core/src/main/java", "camera-adapter/src/main/java", "app/src")) {
            Path root = new File(Fixtures.root(), dir).toPath();
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path p : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    Matcher m = literal.matcher(new String(Files.readAllBytes(p), "UTF-8"));
                    while (m.find()) {
                        String key = m.group(1);
                        // Prefixes that get a suffix appended at run time are covered by dynamicKeys().
                        if (key.endsWith(".")) continue;
                        if (!EN.containsKey(key) && !isPrefix(key)) missing.add(key + " (" + p.getFileName() + ")");
                    }
                }
            }
        }
        for (String k : dynamicKeys()) if (!EN.containsKey(k)) missing.add(k);
        assertTrue(missing.isEmpty(), "no English text for " + missing);
    }

    private static boolean isPrefix(String key) {
        for (String k : EN.keySet()) if (k.startsWith(key + ".")) return true;
        return false;
    }

    @Test
    void fallsBackToEnglishThenTheKey() {
        Map<String, Map<String, String>> tables = new LinkedHashMap<String, Map<String, String>>();
        Map<String, String> en = new HashMap<String, String>();
        en.put("a", "A {n}");
        en.put("b", "B");
        Map<String, String> vi = new HashMap<String, String>();
        vi.put("a", "Á {n}");
        tables.put("en", en);
        tables.put("vi", vi);
        I18n i = I18n.of(tables, "vi");
        Map<String, String> args = new HashMap<String, String>();
        args.put("n", "3");
        assertEquals("Á 3", i.t("a", args));
        assertEquals("B", i.t("b"));
        assertEquals("c", i.t("c"));
        assertEquals("en", I18n.of(tables, "fr").language());
    }

    @Test
    void unknownPlaceholdersAreLeftAlone() {
        Map<String, String> args = new HashMap<String, String>();
        args.put("a", "1");
        assertEquals("1 {b} {", I18n.format("{a} {b} {", args));
    }

    @Test
    void resolvesTheLanguageChoice() {
        List<String> langs = Arrays.asList("en", "vi");
        assertEquals("vi", I18n.resolve("vi", "en", langs));
        assertEquals("vi", I18n.resolve("auto", "vi", langs));
        assertEquals("en", I18n.resolve("auto", "ja", langs));
        assertEquals("en", I18n.resolve("de", null, langs));
    }

    @Test
    void picksLocalizedProfileText() {
        I18n vi = Fixtures.i18n("vi");
        Map<String, String> name = new LinkedHashMap<String, String>();
        name.put("en", "Group");
        assertEquals("Group", vi.pick(name));
        name.put("vi", "Nhóm");
        assertEquals("Nhóm", vi.pick(name));
        assertEquals("Tiếng Việt", vi.languageName("vi"));
        assertEquals("English", vi.languageName("en"));
    }
}
