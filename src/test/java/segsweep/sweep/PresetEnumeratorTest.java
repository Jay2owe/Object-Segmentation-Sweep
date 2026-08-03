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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PresetEnumeratorTest {

    @Test
    public void explicitValueListsPreserveReadableOrderForDisplay() {
        ParameterValueList values = ParameterValueList.ofStrings(
                "Default", "High contrast", "Low contrast");

        assertEquals("Default", values.get(0));
        assertEquals("High contrast", values.get(1));
        assertEquals("Low contrast", values.get(2));
        assertEquals("[\"Default\",\"High contrast\",\"Low contrast\"]",
                values.toCanonicalJson());
    }

    @Test
    public void stableKeyLookupIncludesMorphologyAndDeferredEngines() {
        assertSame(ParameterId.SPHERICITY, ParameterId.fromStableKey("sphericity"));
        assertSame(ParameterId.COMPACTNESS, ParameterId.fromStableKey("compactness"));
        assertSame(ParameterId.FERET_DIAMETER_MAX,
                ParameterId.fromStableKey("feret_diameter_max"));
        assertSame(ParameterId.PROB_THRESH,
                ParameterId.fromStableKey("probability_threshold"));
        assertSame(ParameterId.CELLPROB_THRESHOLD,
                ParameterId.fromStableKey("cellprob_threshold"));
    }
}
