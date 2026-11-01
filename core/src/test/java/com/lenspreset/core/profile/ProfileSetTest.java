package com.lenspreset.core.profile;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.model.Preset;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileSetTest {
    private static List<ProfileSet.Entry> bundledPresets() {
        List<ProfileSet.Entry> entries = new ArrayList<ProfileSet.Entry>();
        for (File f : Fixtures.jsonFiles("profiles/presets"))
            entries.add(new ProfileSet.Entry(ProfileSet.Kind.PRESET, f.getName(), Fixtures.read(f), false));
        return entries;
    }

    private static final String CARD_GROUP = "{\"id\": \"group\", \"name\": \"My group\", \"exposure_mode\": \"program\","
            + " \"af\": {\"mode\": \"single\"}}";

    @Test
    void presetsSortByOrder() {
        List<Preset> presets = Fixtures.bundled().presets();
        assertEquals("group", presets.get(0).id);
        assertEquals("portrait-bokeh", presets.get(1).id);
        assertEquals("indoor-low-light", presets.get(presets.size() - 1).id);
    }

    @Test
    void cardFileReplacesABundledProfile() {
        List<ProfileSet.Entry> entries = bundledPresets();
        entries.add(new ProfileSet.Entry(ProfileSet.Kind.PRESET, "card:PRESETS/GROUP.JSN", CARD_GROUP, true));
        ProfileSet set = ProfileSet.load(entries);
        assertEquals("My group", set.preset("group").name.get("en"));
        assertEquals("[preset:group]", set.overridden().toString());
        assertTrue(set.problems().isEmpty());
    }

    @Test
    void duplicateIdsOnTheSameSideAreProblems() {
        List<ProfileSet.Entry> entries = bundledPresets();
        entries.add(new ProfileSet.Entry(ProfileSet.Kind.PRESET, "card:A.JSN", CARD_GROUP, true));
        entries.add(new ProfileSet.Entry(ProfileSet.Kind.PRESET, "card:B.JSN", CARD_GROUP.replace("My", "Other"), true));
        ProfileSet set = ProfileSet.load(entries);
        assertEquals("My group", set.preset("group").name.get("en"));
        assertEquals("[card:B.JSN: id group is already used by another file]", set.problems().toString());
    }

    @Test
    void badFilesAreSkippedWithTheReason() {
        List<ProfileSet.Entry> entries = bundledPresets();
        entries.add(new ProfileSet.Entry(ProfileSet.Kind.PRESET, "card:BAD.JSN", "{\"id\": \"bad\"}", true));
        ProfileSet set = ProfileSet.load(entries);
        assertEquals(1, set.problems().size());
        assertTrue(set.problems().get(0).startsWith("card:BAD.JSN: name:"), set.problems().get(0));
        assertEquals(entries.size() - 1, set.presets().size());
    }

    @Test
    void cardFileNames() {
        assertTrue(CardFiles.isProfileFile("MINE.JSN"));
        assertTrue(CardFiles.isProfileFile("mine.json"));
        assertTrue(CardFiles.isProfileFile("PORTRA~1.JSO"));
        assertTrue(!CardFiles.isProfileFile(".hidden.json"));
        assertTrue(!CardFiles.isProfileFile("NOTES.TXT"));
        assertTrue(!CardFiles.isProfileFile("._MINE.JSN"));
    }
}
