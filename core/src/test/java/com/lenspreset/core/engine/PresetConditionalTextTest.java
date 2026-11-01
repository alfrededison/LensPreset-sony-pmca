package com.lenspreset.core.engine;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.Preset;
import com.lenspreset.core.optics.Scales;
import com.lenspreset.core.profile.ProfileException;
import com.lenspreset.core.profile.ProfileParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetConditionalTextTest {
    private static String json(String distance, String warning, double depth) {
        return "{\"id\":\"group-test\",\"name\":\"Group\",\"exposure_mode\":\"aperture_priority\","
                + "\"af\":{\"mode\":\"single\"},\"aperture\":{\"strategy\":\"dof\",\"depth_m\":" + depth
                + (warning == null ? "" : ",\"dof_warning\":" + warning) + "},"
                + "\"subject\":{\"framing\":\"group\""
                + (distance == null ? "" : ",\"distance_hint\":" + distance) + "}}";
    }

    private static Recommendation compute(String json) throws ProfileException {
        return PresetEngine.compute(Fixtures.camera("ilce-5100"), Fixtures.lens("sigma-30-f14-dc-dn"),
                ProfileParser.preset(json), Conditions.none());
    }

    private static Message message(Recommendation r, String key) {
        return r.messages.stream().filter(m -> m.key.equals(key)).findFirst().orElseThrow();
    }

    @Test
    void presetDistanceTemplateUsesTheComputedDistanceAndChangesLanguage() throws ProfileException {
        Recommendation r = compute(json("{\"en\":\"Custom distance {m} m\",\"vi\":\"Khoảng cách riêng {m} m\"}", null, 0.5));
        Message distance = message(r, "hint.distance");
        String metres = Scales.formatMetres(r.subjectDistanceM);
        assertEquals("Custom distance " + metres + " m", distance.render(Fixtures.i18n("en")));
        assertEquals("Khoảng cách riêng " + metres + " m", distance.render(Fixtures.i18n("vi")));
        assertFalse(Fixtures.i18n("en").has("hint.distance.group"));
        assertEquals(metres, distance.args.get("m"));
    }

    @Test
    void warningIsOnlyShownWhenDepthCannotBeCoveredAndKeepsItsSeverity() throws ProfileException {
        String warning = "{\"en\":\"Custom warning\",\"vi\":\"Cảnh báo riêng\"}";
        assertFalse(compute(json(null, warning, 0.5)).hasMessage("notice.dof_insufficient"));
        Message message = message(compute(json(null, warning, 50)), "notice.dof_insufficient");
        assertEquals(Message.Level.WARN, message.level);
        assertEquals("Custom warning", message.render(Fixtures.i18n("en")));
        assertEquals("Cảnh báo riêng", message.render(Fixtures.i18n("vi")));
    }

    @Test
    void missingTranslationsFallBackToPresetEnglishWithoutLeakingToOtherPresets() throws ProfileException {
        for (String text : Arrays.asList("\"First {m}\"", "{\"en\":\"First {m}\"}")) {
            Recommendation first = compute(json(text, "\"First warning\"", 50));
            Recommendation second = compute(json("\"Second {m}\"", "\"Second warning\"", 50));
            I18n vi = Fixtures.i18n("vi");
            assertTrue(message(first, "hint.distance").render(vi).startsWith("First "));
            assertTrue(message(second, "hint.distance").render(vi).startsWith("Second "));
            assertEquals("First warning", message(first, "notice.dof_insufficient").render(vi));
            assertEquals("Second warning", message(second, "notice.dof_insufficient").render(vi));
        }
    }

    @Test
    void absentOverridesUseSharedGenericGuidance() throws ProfileException {
        Recommendation r = compute(json(null, null, 50));
        I18n vi = Fixtures.i18n("vi");
        assertEquals(vi.t("hint.distance", message(r, "hint.distance").args), message(r, "hint.distance").render(vi));
        assertEquals(vi.t("notice.dof_insufficient"), message(r, "notice.dof_insufficient").render(vi));
        Preset group = Fixtures.preset("group");
        assertTrue(group.subject.distanceHint.get("en").contains("fit the row"));
        assertTrue(group.aperture.dofWarning.get("en").contains("every row"));
        assertFalse(vi.t("notice.dof_insufficient").contains("hàng"));
    }

    @Test
    void invalidConditionalTextAndUnknownPlaceholdersAreRejected() {
        for (String text : Arrays.asList("null", "42", "[]", "{}", "\"\"", "{\"en\":\" \"}",
                "{\"vi\":\"Riêng\"}", "{\"en\":\"Text\",\"vietnamese\":\"Riêng\"}", "\"Unknown {typo}\"")) {
            ProfileException distance = assertThrows(ProfileException.class, () -> ProfileParser.preset(json(text, null, 0.5)));
            assertTrue(distance.getMessage().startsWith("subject.distance_hint"), distance.getMessage());
            ProfileException warning = assertThrows(ProfileException.class, () -> ProfileParser.preset(json(null, text, 0.5)));
            assertTrue(warning.getMessage().startsWith("aperture.dof_warning"), warning.getMessage());
        }
        String fixed = json(null, "\"Warning\"", 0.5).replace("\"strategy\":\"dof\"", "\"strategy\":\"fixed\",\"value\":2.8");
        assertTrue(assertThrows(ProfileException.class, () -> ProfileParser.preset(fixed))
                .getMessage().startsWith("aperture.dof_warning"));
    }
}
