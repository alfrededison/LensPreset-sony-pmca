package com.lenspreset.core.engine;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.Preset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTest {
    @Test
    void onlyConfigurablePresetsAdvertiseLeftAndRight() {
        for (String language : Arrays.asList("en", "vi")) {
            I18n i18n = Fixtures.i18n(language);
            String keyLabel = language.equals("vi") ? "Trái/Phải" : "Left/Right";
            for (Preset preset : Fixtures.bundled().presets()) {
                String legend = Hud.liveLegend(preset, false, i18n);
                assertEquals(!preset.adjust.isEmpty(), legend.contains(keyLabel), preset.id);
                assertFalse(legend.contains("{"), legend);
                if (!preset.adjust.isEmpty())
                    assertTrue(legend.contains(Hud.adjustmentLabel(preset, preset.adjust.keySet().iterator().next(), i18n)));
            }
            assertFalse(Hud.liveLegend(null, false, i18n).contains(keyLabel));
        }
    }

    @Test
    void touchGuideIsOnlyShownForTouchscreenBodies() {
        I18n i18n = Fixtures.i18n("vi");
        Preset preset = Fixtures.preset("group");
        assertTrue(Hud.liveLegend(preset, true, i18n).contains(i18n.t("ui.legend.touch")));
        assertFalse(Hud.liveLegend(preset, false, i18n).contains(i18n.t("ui.legend.touch")));
    }

    @Test
    void groupDetailsShowDefaultsAndUpdateWithTheSelectedValues() {
        Preset group = Fixtures.preset("group");
        I18n vi = Fixtures.i18n("vi");
        assertEquals(Arrays.asList("Số hàng: 2", "Hẹn giờ: tắt"),
                summaries(Hud.adjustments(group, Collections.<String, Integer>emptyMap(), vi)));
        Map<String, Integer> selected = new HashMap<String, Integer>();
        selected.put("rows", group.adjust.get("rows").step(2, 1));
        selected.put("self_timer", 10);
        assertEquals(Arrays.asList("Số hàng: 3", "Hẹn giờ: 10 s"),
                summaries(Hud.adjustments(group, selected, vi)));
        assertEquals(Arrays.asList("Rows: 3", "Self-timer: 10 s"),
                summaries(Hud.adjustments(group, selected, vi.withLanguage("en"))));
    }

    @Test
    void configuredTimerAppearsOnlyOnceAmongAllLiveViewSettings() {
        Preset group = Fixtures.preset("group");
        for (String language : Arrays.asList("en", "vi")) {
            I18n i18n = Fixtures.i18n(language);
            for (int seconds : new int[]{0, 2, 10}) {
                Map<String, Integer> selected = Collections.singletonMap("self_timer", seconds);
                Recommendation rec = PresetEngine.compute(Fixtures.camera("ilce-5100"),
                        Fixtures.lens("sigma-30-f14-dc-dn"), group, new Conditions(null, null, selected));
                List<Hud.Chip> chips = Hud.chips(rec, group, selected, i18n);
                String timerLabel = i18n.t("ui.adjust.self_timer");
                assertEquals(1, chips.stream().filter(c -> c.label.equals(timerLabel)).count(), chips.toString());
                assertEquals(Hud.adjustmentValue("self_timer", seconds, i18n),
                        chips.stream().filter(c -> c.label.equals(timerLabel)).findFirst().get().value);
                assertTrue(chips.stream().filter(c -> c.label.equals(timerLabel)).findFirst().get().presetOption);
                assertEquals(group.adjust.size(), chips.stream().filter(c -> c.presetOption).count());
            }
        }
    }

    @Test
    void invalidSavedValuesDisplayThePresetDefaults() {
        Map<String, Integer> selected = new HashMap<String, Integer>();
        selected.put("rows", 99);
        selected.put("self_timer", 5);
        assertEquals(Arrays.asList("Số hàng: 2", "Hẹn giờ: tắt"),
                summaries(Hud.adjustments(Fixtures.preset("group"), selected, Fixtures.i18n("vi"))));
    }

    @Test
    void unconfigurablePresetsHaveNoAdjustmentDetails() {
        for (Preset preset : Fixtures.bundled().presets()) {
            if (preset.adjust.isEmpty())
                assertTrue(Hud.adjustments(preset, Collections.<String, Integer>emptyMap(),
                        Fixtures.i18n("vi")).isEmpty(), preset.id);
        }
        assertTrue(Hud.adjustments(null, Collections.<String, Integer>emptyMap(), Fixtures.i18n("vi")).isEmpty());
    }
    @Test
    void cardsHaveTranslatedLabelsAndKeepExposureAndFocusValues() {
        for (String language : Arrays.asList("en", "vi")) {
            I18n i18n = Fixtures.i18n(language);
            for (Preset preset : Fixtures.bundled().presets()) {
                Recommendation rec = PresetEngine.compute(Fixtures.camera("ilce-5100"),
                        Fixtures.lens("sigma-30-f14-dc-dn"), preset,
                        new Conditions(null, null, Collections.<String, Integer>emptyMap()));
                List<Hud.Chip> chips = Hud.chips(rec, preset, Collections.<String, Integer>emptyMap(), i18n);
                for (Hud.Chip chip : chips) {
                    assertFalse(chip.label.isEmpty(), preset.id);
                    assertFalse(chip.value.isEmpty(), preset.id);
                    assertFalse(chip.label.startsWith("hud."), chip.label);
                    assertFalse(chip.value.startsWith("hud."), chip.value);
                }
                assertEquals(preset.adjust.size(), chips.stream().filter(c -> c.presetOption).count(), preset.id);
                assertFalse(chips.stream().filter(c -> c.label.equals("ISO") || c.label.equals("EV"))
                        .anyMatch(c -> c.presetOption), preset.id);
                assertTrue(chips.stream().anyMatch(c -> c.label.equals(i18n.t("hud.label.mode"))
                        && c.value.equals(Hud.modeLetter(rec.exposureMode))), preset.id);
                assertTrue(chips.stream().anyMatch(c -> c.label.equals("ISO")
                        && (rec.isoAuto ? c.value.startsWith("AUTO") : c.value.equals(Integer.toString(rec.iso)))), preset.id);
                assertTrue(chips.stream().anyMatch(c -> c.label.equals("EV")
                        && c.value.equals(com.lenspreset.core.optics.Scales.formatEv(rec.ev))), preset.id);
                assertTrue(chips.stream().anyMatch(c -> c.label.equals(i18n.t("hud.label.focus"))
                        && c.value.startsWith(Hud.focusLabel(rec.focusMode))
                        && (!rec.faceDetection || c.value.contains(i18n.t("hud.face")))), preset.id);
            }
        }
    }

    private static List<String> summaries(List<Hud.Chip> chips) {
        List<String> out = new ArrayList<String>();
        for (Hud.Chip chip : chips) out.add(chip.label + ": " + chip.value);
        return out;
    }

}
