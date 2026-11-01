package com.lenspreset.core.engine;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.profile.ProfileSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardPresetHintsTest {
    private static String cardJson(String id, String hint, String text) {
        return "{\"id\":\"" + id + "\",\"name\":\"Card preset\",\"exposure_mode\":\"program\","
                + "\"af\":{\"mode\":\"single\"},\"hints\":[\"" + hint + "\"],"
                + "\"hint_text\":{\"" + hint + "\":" + text + "}}";
    }

    private static ProfileSet.Entry card(String id, String hint, String text) {
        return new ProfileSet.Entry(ProfileSet.Kind.PRESET, "card:PRESETS/" + id + ".JSN",
                cardJson(id, hint, text), true);
    }

    private static Recommendation compute(Preset preset) {
        return PresetEngine.compute(Fixtures.camera("ilce-5100"), Fixtures.lens("sigma-30-f14-dc-dn"),
                preset, Conditions.none());
    }

    private static I18n withoutAppHintText() {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        tables.put("en", Collections.emptyMap());
        tables.put("vi", Collections.emptyMap());
        return I18n.of(tables, "vi");
    }

    @Test
    void cardHintRendersWithoutAnAppTranslationAndChangesLanguage() {
        ProfileSet set = ProfileSet.load(Arrays.asList(card("custom", "card_only",
                "{\"en\":\"Card hint\",\"vi\":\"Gợi ý trên thẻ\"}")));
        assertTrue(set.problems().isEmpty(), set.problems().toString());
        Recommendation r = compute(set.preset("custom"));
        I18n vi = withoutAppHintText();
        assertTrue(Hud.lines(r, vi).contains("Gợi ý trên thẻ"));
        assertTrue(Hud.lines(r, vi.withLanguage("en")).contains("Card hint"));
        assertTrue(r.hasMessage("hint.card_only"));
    }

    @Test
    void missingVietnameseFallsBackToEnglishIncludingPlainText() {
        ProfileSet set = ProfileSet.load(Arrays.asList(
                card("english", "local_hint", "{\"en\":\"English hint\"}"),
                card("plain", "local_hint", "\"Plain hint\"")));
        assertTrue(set.problems().isEmpty(), set.problems().toString());
        I18n vi = withoutAppHintText();
        assertTrue(Hud.lines(compute(set.preset("english")), vi).contains("English hint"));
        assertTrue(Hud.lines(compute(set.preset("plain")), vi).contains("Plain hint"));
    }

    @Test
    void sameHintIdDoesNotLeakTextAcrossPresets() {
        ProfileSet set = ProfileSet.load(Arrays.asList(
                card("first", "shared_hint", "\"First hint\""),
                card("second", "shared_hint", "\"Second hint\"")));
        assertTrue(set.problems().isEmpty(), set.problems().toString());
        I18n en = withoutAppHintText().withLanguage("en");
        assertTrue(Hud.lines(compute(set.preset("first")), en).contains("First hint"));
        assertTrue(Hud.lines(compute(set.preset("second")), en).contains("Second hint"));
        assertEquals("hint.shared_hint", en.t("hint.shared_hint"));
    }

    @Test
    void rejectsCardHintsWithoutPresetText() {
        String json = "{\"id\":\"legacy\",\"name\":\"Legacy card preset\",\"exposure_mode\":\"program\","
                + "\"af\":{\"mode\":\"single\"},\"hints\":[\"background_far\"]}";
        ProfileSet set = ProfileSet.load(Arrays.asList(new ProfileSet.Entry(ProfileSet.Kind.PRESET,
                "card:PRESETS/LEGACY.JSN", json, true)));
        assertEquals(Collections.singletonList(
                "card:PRESETS/LEGACY.JSN: hint_text.background_far: required text is missing"), set.problems());
        assertTrue(set.presets().isEmpty());
    }

    @Test
    void everyBundledPresetRendersItsHintsWithoutAppTranslations() {
        I18n vi = withoutAppHintText();
        for (Preset preset : Fixtures.bundled().presets()) {
            Recommendation r = compute(preset);
            for (String hint : preset.hints) {
                assertTrue(Hud.lines(r, vi).contains(preset.hintText.get(hint).get("vi")), preset.id + ": " + hint);
                assertTrue(Hud.lines(r, vi.withLanguage("en")).contains(preset.hintText.get(hint).get("en")),
                        preset.id + ": " + hint);
            }
        }
    }
}
