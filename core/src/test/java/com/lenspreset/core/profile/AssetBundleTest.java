package com.lenspreset.core.profile;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetBundleTest {
    private static byte[] bundle(String... kindNameText) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 2 < kindNameText.length; i += 3) {
            byte[] body = kindNameText[i + 2].getBytes(StandardCharsets.UTF_8);
            sb.append("@@ ").append(kindNameText[i]).append(' ').append(kindNameText[i + 1]).append(' ')
                    .append(body.length).append('\n').append(kindNameText[i + 2]).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readsFilesWithMultiByteText() throws ProfileException {
        List<AssetBundle.Item> items = AssetBundle.parse(bundle(
                "i18n", "vi.json", "{\"a\": \"Tiếng Việt\"}\n",
                "presets", "group.json", "{}"));
        assertEquals(2, items.size());
        assertEquals("i18n", items.get(0).kind);
        assertEquals("vi.json", items.get(0).name);
        assertEquals("{\"a\": \"Tiếng Việt\"}\n", items.get(0).text);
        assertEquals("{}", items.get(1).text);
    }

    @Test
    void emptyBundleIsEmpty() throws ProfileException {
        assertEquals(0, AssetBundle.parse(new byte[0]).size());
    }

    @Test
    void rejectsDamage() {
        assertThrows(ProfileException.class, () -> AssetBundle.parse("@@ presets a.json 10\n{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProfileException.class, () -> AssetBundle.parse("@@ presets a.json\n{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ProfileException.class, () -> AssetBundle.parse("garbage".getBytes(StandardCharsets.UTF_8)));
    }
}
