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

public class StepsModeSweepBuildTest {

    @Test
    public void fromToStepBuildsInclusiveDisplayWindowValues() {
        ParameterValueList values = ParameterValueList.fromRange(10.0d, 40.0d, 10.0d);

        assertEquals("[10,20,30,40]", values.toCanonicalJson());
        assertEquals(4, values.size());
    }

    @Test
    public void descendingRangeBuildsInclusiveDisplayWindowValues() {
        ParameterValueList values = ParameterValueList.fromRange(3.0d, 1.0d, -1.0d);

        assertEquals("[3,2,1]", values.toCanonicalJson());
    }

    @Test
    public void threeByFourSweepHasDocumentedStableOrder() {
        Map<ParameterId, ParameterValueList> axes =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        axes.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(10, 20, 30, 40));
        axes.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(1, 2, 3));

        List<ParameterCombo> combos = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                axes, "DAPI").combos();

        assertEquals(12, combos.size());
        assertEquals(Integer.valueOf(1), combos.get(0).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(10), combos.get(0).get(ParameterId.MIN_SIZE));
        assertEquals(Integer.valueOf(1), combos.get(3).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(40), combos.get(3).get(ParameterId.MIN_SIZE));
        assertEquals(Integer.valueOf(2), combos.get(4).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(10), combos.get(4).get(ParameterId.MIN_SIZE));
        assertEquals(Integer.valueOf(3), combos.get(11).get(ParameterId.THRESHOLD));
        assertEquals(Integer.valueOf(40), combos.get(11).get(ParameterId.MIN_SIZE));
        assertTrue(new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                axes, "DAPI").valuesSelectDisplayWindow());
    }
}
