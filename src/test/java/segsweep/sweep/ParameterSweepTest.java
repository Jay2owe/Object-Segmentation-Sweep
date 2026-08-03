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
import static org.junit.Assert.fail;

public class ParameterSweepTest {

    @Test
    public void combosBuildCartesianProductInDeterministicOrder() {
        ParameterSweep sweep = sweepWithScrambledInputOrder();

        List<ParameterCombo> combos = sweep.combos();

        assertEquals(6L, sweep.cellCount());
        assertEquals(6, combos.size());
        assertEquals(Integer.valueOf(1), combos.get(0).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(10), combos.get(0).get(ParameterId.MIN_SIZE));
        assertEquals(Integer.valueOf(100), combos.get(0).get(ParameterId.MAX_SIZE));
        assertEquals(Integer.valueOf(1), combos.get(1).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(20), combos.get(1).get(ParameterId.MIN_SIZE));
        assertEquals(Integer.valueOf(2), combos.get(2).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(3), combos.get(5).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(20), combos.get(5).get(ParameterId.MIN_SIZE));
    }

    @Test
    public void combosDoNotDependOnMapInsertionOrder() {
        ParameterSweep first = sweepWithScrambledInputOrder();

        Map<ParameterId, ParameterValueList> values = new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(1, 2, 3));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(10, 20));
        values.put(ParameterId.MAX_SIZE, ParameterValueList.ofInts(100));
        ParameterSweep second = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, "DAPI");

        assertEquals(first.combos(), second.combos());
    }

    @Test
    public void canonicalJsonUsesStableKeysAndDisplayWindowRole() {
        ParameterSweep sweep = sweepWithScrambledInputOrder();

        String json = sweep.toCanonicalJson();

        assertEquals(json, sweep.toCanonicalJson());
        assertTrue(json.contains("\"method\":\"classical\""));
        assertTrue(json.contains("\"valueRole\":\"display_window\""));
        assertTrue(json.contains("\"threshold\""));
        assertTrue(json.contains("\"min_size\""));
        assertTrue(json.contains("\"max_size\""));
        assertTrue(sweep.valuesSelectDisplayWindow());
    }

    @Test
    public void twoAxisCanonicalJsonIsByteIdenticalAcrossEquivalentRuns() {
        Map<ParameterId, ParameterValueList> firstValues =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        firstValues.put(ParameterId.THRESHOLD, ParameterValueList.fromRange(10.0d, 30.0d, 10.0d));
        firstValues.put(ParameterId.SPHERICITY, ParameterValueList.ofDoubles(0.5d, 0.75d));

        Map<ParameterId, ParameterValueList> secondValues =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        secondValues.put(ParameterId.SPHERICITY, ParameterValueList.ofDoubles(0.50d, 0.7500d));
        secondValues.put(ParameterId.THRESHOLD, ParameterValueList.ofDoubles(1.0e1d, 20.0d, 30.000d));

        String firstJson = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                firstValues, "DAPI").toCanonicalJson();
        String secondJson = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                secondValues, "DAPI").toCanonicalJson();

        assertEquals(firstJson, secondJson);
    }

    @Test
    public void cellCountSaturatesWhenCartesianProductWouldOverflowLong() {
        Map<ParameterKey, ParameterValueList> values =
                new LinkedHashMap<ParameterKey, ParameterValueList>();
        ParameterValueList tenValues = ParameterValueList.ofInts(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        for (int i = 0; i < 19; i++) {
            values.put(new TestKey("k" + i), tenValues);
        }
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, "DAPI");

        assertEquals(Long.MAX_VALUE, sweep.cellCount());
        try {
            sweep.combos();
            fail("Expected oversized sweep to be rejected before allocation.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("too many parameter combinations"));
        }
    }

    private static ParameterSweep sweepWithScrambledInputOrder() {
        Map<ParameterId, ParameterValueList> values = new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.MAX_SIZE, ParameterValueList.ofInts(100));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(10, 20));
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(1, 2, 3));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values, "DAPI");
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
