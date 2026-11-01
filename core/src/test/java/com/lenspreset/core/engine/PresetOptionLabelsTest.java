package com.lenspreset.core.engine;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.profile.ProfileException;
import com.lenspreset.core.profile.ProfileParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetOptionLabelsTest {
    private static String json(String label) {
        return "{\"id\":\"custom-group\",\"name\":\"Group\",\"exposure_mode\":\"program\","
                + "\"af\":{\"mode\":\"single\"},\"subject\":{\"framing\":\"group\"},\"adjust\":{"
                + "\"rows\":{" + (label == null ? "" : "\"label\":" + label + ",")
                + "\"min\":1,\"max\":4,\"default\":2},\"self_timer\":{\"values\":[0,2,10]}}}";
    }

    @Test
    void rowLabelComesFromPresetWhileTimerUsesSharedCameraText() throws ProfileException {
        Preset preset = ProfileParser.preset(json("{\"en\":\"Custom rows\",\"vi\":\"Hàng tùy chọn\"}"));
        for (String language : Arrays.asList("en", "vi")) {
            I18n i18n = Fixtures.i18n(language);
            String expected = language.equals("vi") ? "Hàng tùy chọn" : "Custom rows";
            assertFalse(i18n.has("ui.adjust.rows"));
            assertEquals(expected, Hud.adjustmentLabel(preset, "rows", i18n));
            assertEquals(expected, Hud.adjustments(preset, Collections.<String, Integer>emptyMap(), i18n).get(0).label);
            assertTrue(Hud.liveLegend(preset, "rows", false, i18n).contains(expected));
            assertEquals(i18n.t("ui.adjust.self_timer"), Hud.adjustmentLabel(preset, "self_timer", i18n));
            assertTrue(preset.adjust.get("self_timer").label.isEmpty());
        }
    }

    @Test
    void missingLanguageFallsBackToPresetEnglishIncludingPlainText() throws ProfileException {
        for (String label : Arrays.asList("\"Card rows\"", "{\"en\":\"Card rows\"}")) {
            Preset preset = ProfileParser.preset(json(label));
            assertEquals("Card rows", Hud.adjustmentLabel(preset, "rows", Fixtures.i18n("vi")));
        }
    }

    @Test
    void sameOptionKeyDoesNotLeakLabelsBetweenPresets() throws ProfileException {
        Preset first = ProfileParser.preset(json("\"First rows\""));
        Preset second = ProfileParser.preset(json("\"Second rows\"").replace("custom-group", "another-group"));
        I18n i18n = Fixtures.i18n("en");
        assertEquals("First rows", Hud.adjustmentLabel(first, "rows", i18n));
        assertEquals("Second rows", Hud.adjustmentLabel(second, "rows", i18n));
    }

    @Test
    void rejectsMissingOrInvalidPresetOptionLabels() {
        for (String label : Arrays.asList(null, "null", "42", "[]", "{}", "\"\"",
                "{\"vi\":\"Hàng\"}", "{\"en\":\"Rows\",\"vi\":\" \"}",
                "{\"en\":\"Rows\",\"vietnamese\":\"Hàng\"}")) {
            ProfileException error = assertThrows(ProfileException.class, () -> ProfileParser.preset(json(label)));
            assertTrue(error.getMessage().startsWith("adjust.rows.label"), error.getMessage());
        }
    }

    @Test
    void sharedTimerLabelCannotBeOverriddenByPreset() {
        String source = json("\"Rows\"").replace("\"self_timer\":{", "\"self_timer\":{\"label\":\"Other\",");
        ProfileException error = assertThrows(ProfileException.class, () -> ProfileParser.preset(source));
        assertEquals("adjust.self_timer.label: unknown key", error.getMessage());
    }

    @Test
    void bundledPresetLabelsIncludeEveryAppLanguage() {
        for (Preset preset : Fixtures.bundled().presets()) {
            for (Map.Entry<String, Preset.Adjust> option : preset.adjust.entrySet()) {
                if (Preset.Adjust.SELF_TIMER.equals(option.getKey())) continue;
                for (String language : Fixtures.languageTables().keySet())
                    assertTrue(option.getValue().label.containsKey(language), preset.id + ": " + language);
            }
        }
    }
}
