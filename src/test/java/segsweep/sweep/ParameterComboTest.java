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
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ParameterComboTest {

    @Test
    public void canonicalJsonIsStableAcrossEquivalentMapInsertionOrder() {
        Map<ParameterId, Object> firstValues = new LinkedHashMap<ParameterId, Object>();
        firstValues.put(ParameterId.THRESHOLD, Double.valueOf(42.5d));
        firstValues.put(ParameterId.MIN_SIZE, Integer.valueOf(10));
        firstValues.put(ParameterId.MAX_SIZE, Integer.valueOf(100));

        Map<ParameterId, Object> secondValues = new LinkedHashMap<ParameterId, Object>();
        secondValues.put(ParameterId.MAX_SIZE, Integer.valueOf(100));
        secondValues.put(ParameterId.THRESHOLD, Double.valueOf(42.5d));
        secondValues.put(ParameterId.MIN_SIZE, Integer.valueOf(10));

        ParameterCombo first = new ParameterCombo(firstValues);
        ParameterCombo second = new ParameterCombo(secondValues);

        assertEquals(first.toCanonicalJson(), second.toCanonicalJson());
        assertEquals("{\"max_size\":100,\"min_size\":10,\"threshold\":42.5}",
                first.toCanonicalJson());
    }

    @Test
    public void integralFloatingValuesUseCanonicalNumberText() {
        ParameterCombo intCombo = ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Integer.valueOf(10))
                .build();
        ParameterCombo doubleCombo = ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Double.valueOf(10.0d))
                .build();
        ParameterCombo scientificCombo = ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Double.valueOf(1.0e1d))
                .build();

        assertEquals(intCombo.toCanonicalJson(), doubleCombo.toCanonicalJson());
        assertEquals(intCombo.toCanonicalJson(), scientificCombo.toCanonicalJson());
        assertEquals("{\"threshold\":10}", intCombo.toCanonicalJson());
    }
}
