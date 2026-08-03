/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParameterKeyCompatibilityTest {

    @Test
    public void parameterIdSweepStillBuildsExpectedCombos() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.MAX_SIZE, ParameterValueList.ofInts(100));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(10, 20));
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(1, 2, 3));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, "DAPI");

        List<ParameterCombo> combos = sweep.combos();

        assertEquals(6L, sweep.cellCount());
        assertEquals(6, combos.size());
        assertEquals(Integer.valueOf(1), combos.get(0).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(10), combos.get(0).get(ParameterId.MIN_SIZE));
        assertEquals(Integer.valueOf(100), combos.get(0).get(ParameterId.MAX_SIZE));
    }

    @Test
    public void customParameterKeysProduceCartesianProductAndStableJsonKeys() {
        ParameterKey alpha = new TestKey("alpha_value");
        ParameterKey beta = new TestKey("beta_value");
        Map<ParameterKey, ParameterValueList> values =
                new LinkedHashMap<ParameterKey, ParameterValueList>();
        values.put(beta, ParameterValueList.ofDoubles(4.0d, 5.0d));
        values.put(alpha, ParameterValueList.ofDoubles(1.0d, 2.0d, 3.0d));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, "DAPI");

        List<ParameterCombo> combos = sweep.combos();
        String json = sweep.toCanonicalJson();

        assertEquals(6L, sweep.cellCount());
        assertEquals(6, combos.size());
        assertTrue(json.contains("\"alpha_value\""));
        assertTrue(json.contains("\"beta_value\""));
    }

    @Test
    public void onlyClassicalAndDeferredEngineMethodsArePartOfTheModel() {
        assertEquals(3, ParameterSweep.Method.values().length);
        assertTrue(hasMethod(ParameterSweep.Method.CLASSICAL));
        assertTrue(hasMethod(ParameterSweep.Method.STARDIST));
        assertTrue(hasMethod(ParameterSweep.Method.CELLPOSE));
    }

    @Test
    public void liveIdentifierStableKeysArePinnedForPersistence() {
        assertEquals("threshold", ParameterId.THRESHOLD.stableKey());
        assertEquals("min_size", ParameterId.MIN_SIZE.stableKey());
        assertEquals("max_size", ParameterId.MAX_SIZE.stableKey());
        assertEquals("volume", ParameterId.VOLUME.stableKey());
        assertEquals("mean_intensity", ParameterId.MEAN_INTENSITY.stableKey());
        assertEquals("max_intensity", ParameterId.MAX_INTENSITY.stableKey());
        assertEquals("elongation", ParameterId.ELONGATION.stableKey());
        assertEquals("surface_area", ParameterId.SURFACE_AREA.stableKey());
        assertEquals("sphericity", ParameterId.SPHERICITY.stableKey());
        assertEquals("compactness", ParameterId.COMPACTNESS.stableKey());
        assertEquals("feret_diameter_max", ParameterId.FERET_DIAMETER_MAX.stableKey());
    }

    private static boolean hasMethod(ParameterSweep.Method expected) {
        for (ParameterSweep.Method method : ParameterSweep.Method.values()) {
            if (method == expected) {
                return true;
            }
        }
        return false;
    }

    private static final class TestKey implements ParameterKey {
        private final String key;

        TestKey(String key) {
            this.key = key;
        }

        @Override public String stableKey() {
            return key;
        }

        @Override public String displayLabel() {
            return key;
        }

        @Override public ValueKind valueKind() {
            return ValueKind.NUMBER;
        }
    }
}
