package com.lenspreset.core.profile;

import com.lenspreset.core.Fixtures;
import com.lenspreset.core.model.LensProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatcherTest {
    private static final ProfileSet SET = Fixtures.bundled();

    @Test
    void cameraByModelName() {
        assertEquals("ilce-5100", Matcher.camera(SET, "ILCE-5100").id);
        assertEquals("ilce-5100", Matcher.camera(SET, "ilce 5100").id);
        assertEquals("generic", Matcher.camera(SET, "DSC-RX100M3").id);
        assertEquals("generic", Matcher.camera(SET, null).id);
    }

    @Test
    void lensByReportedName() {
        assertEquals("sigma-30-f14-dc-dn", Matcher.lens(SET, "30mm F1.4 DC DN | C 016", null).id);
        assertEquals("sony-e-pz-16-50-f35-56-oss", Matcher.lens(SET, "E PZ 16-50mm F3.5-5.6 OSS", 0L).id);
        assertNull(Matcher.lens(SET, "E 50mm F1.8 OSS", null));
        assertNull(Matcher.lens(SET, "", null));
        assertNull(Matcher.lens(SET, null, null));
    }

    @Test
    void lensByIdWinsOverName() throws ProfileException {
        List<ProfileSet.Entry> entries = new ArrayList<ProfileSet.Entry>();
        entries.add(new ProfileSet.Entry(ProfileSet.Kind.LENS, "a", "{\"id\": \"a\", \"name\": \"A\", \"mount\": \"E\","
                + " \"coverage\": \"APS-C\", \"type\": \"prime\", \"focal_min\": 35, \"focal_max\": 35,"
                + " \"aperture_max\": 1.8, \"aperture_min\": 22, \"match\": {\"ids\": [32790]}}", false));
        entries.add(new ProfileSet.Entry(ProfileSet.Kind.LENS, "b", "{\"id\": \"b\", \"name\": \"B\", \"mount\": \"E\","
                + " \"coverage\": \"APS-C\", \"type\": \"prime\", \"focal_min\": 35, \"focal_max\": 35,"
                + " \"aperture_max\": 1.8, \"aperture_min\": 22, \"match\": {\"names\": [\"35mm\"]}}", false));
        ProfileSet set = ProfileSet.load(entries);
        assertTrue(set.problems().isEmpty(), set.problems().toString());
        assertEquals("a", Matcher.lens(set, "35mm", 32790L).id);
        assertEquals("b", Matcher.lens(set, "35mm", 1L).id);
    }

    @Test
    void focalScaleIsInferredFromTheLensRange() {
        LensProfile kit = Fixtures.lens("sony-e-pz-16-50-f35-56-oss");
        assertEquals(35.0, Matcher.focalMm(35, kit));
        assertEquals(35.0, Matcher.focalMm(350, kit));
        assertEquals(16.0, Matcher.focalMm(1600, kit));
        assertNull(Matcher.focalMm(5, kit));
        assertNull(Matcher.focalMm(0, kit));
        assertNull(Matcher.focalMm(35, null));
    }
}
