package com.lenspreset.core.optics;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsoScaleTest {
    @Test
    void firmwareValuesAreSortedDeduplicatedAndFilteredToTheBodyRange() {
        assertEquals(Arrays.asList(0, 100, 200, 400), IsoScale.values(
                Arrays.asList("AUTO", 400, "100", 50, 200, 400, 51200, "bad", null), 100, 25600));
    }

    @Test
    void missingFirmwareUsesStandardThirdStopsAndTheFullManualRange() {
        List<Integer> values = IsoScale.values(null, 100, 25600);
        assertEquals(Arrays.asList(0, 100, 125, 160, 200), values.subList(0, 5));
        assertEquals(25600, values.get(values.size() - 1));
        assertTrue(values.contains(12800));
    }

    @Test
    void wheelCanEnterManualIsoAndReturnToAutoWithoutWrappingAtTheEnds() {
        List<Integer> values = IsoScale.values(null, 100, 6400);
        assertEquals(100, IsoScale.step(0, 1, values));
        assertEquals(125, IsoScale.step(100, 1, values));
        assertEquals(0, IsoScale.step(100, -1, values));
        assertEquals(0, IsoScale.step(0, -1, values));
        assertEquals(6400, IsoScale.step(6400, 1, values));
    }

    @Test
    void wheelSkipsLevelsMissingFromTheFirmwareIncludingAnOldSavedValue() {
        List<Integer> values = IsoScale.values(Arrays.asList(0, 100, 200, 400, 800), 100, 800);
        assertEquals(200, IsoScale.step(100, 1, values));
        assertEquals(100, IsoScale.step(125, -1, values));
        assertEquals(200, IsoScale.step(125, 1, values));
    }
}
