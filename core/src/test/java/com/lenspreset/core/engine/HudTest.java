package com.lenspreset.core.engine;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.i18n.I18n;
import com.lenspreset.core.model.Preset;
import org.junit.jupiter.api.Test;

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
                    assertTrue(legend.contains(i18n.t("ui.adjust." + preset.adjust.keySet().iterator().next())));
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
        assertEquals(Arrays.asList("Số hàng người: 2", "Hẹn giờ: tắt"),
                Hud.adjustments(group, Collections.<String, Integer>emptyMap(), vi));
        Map<String, Integer> selected = new HashMap<String, Integer>();
        selected.put("rows", group.adjust.get("rows").step(2, 1));
        selected.put("self_timer", 10);
        assertEquals(Arrays.asList("Số hàng người: 3", "Hẹn giờ: 10 giây"),
                Hud.adjustments(group, selected, vi));
        assertEquals(Arrays.asList("Rows of people: 3", "Self-timer: 10 s"),
                Hud.adjustments(group, selected, vi.withLanguage("en")));
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
                List<String> chips = Hud.chips(rec, group, selected, i18n);
                String timerLabel = i18n.t("ui.adjust.self_timer");
                assertEquals(1, chips.stream().filter(s -> s.startsWith(timerLabel)).count(), chips.toString());
                assertTrue(chips.contains(timerLabel + ": " + Hud.adjustmentValue("self_timer", seconds, i18n)));
                assertFalse(chips.contains(i18n.t("hud.timer",
                        Collections.singletonMap("s", Integer.toString(seconds)))));
            }
        }
    }

    @Test
    void invalidSavedValuesDisplayThePresetDefaults() {
        Map<String, Integer> selected = new HashMap<String, Integer>();
        selected.put("rows", 99);
        selected.put("self_timer", 5);
        assertEquals(Arrays.asList("Số hàng người: 2", "Hẹn giờ: tắt"),
                Hud.adjustments(Fixtures.preset("group"), selected, Fixtures.i18n("vi")));
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
}
