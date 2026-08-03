/*
 * Copyright (c) 2026 Jamie Malcolm
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterKey;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IouStabilityTreeIntegrationTest {
    @Test
    public void growingTreeObjectHasPositiveVoxelAgreement() {
        ImagePlus image = new ImagePlus("growing",
                new FloatProcessor(3, 1, new float[] { 3.0f, 2.0f, 1.0f }));
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);
        double[] thresholds = { 2.5d, 1.5d, 0.5d };
        List<ParameterCombo> combos = new ArrayList<ParameterCombo>();
        List<IouStability.IouSource> sources = new ArrayList<IouStability.IouSource>();
        for (int i = 0; i < thresholds.length; i++) {
            LinkedHashMap<ParameterKey, Object> values = new LinkedHashMap<ParameterKey, Object>();
            values.put(ParameterId.THRESHOLD, Double.valueOf(thresholds[i]));
            combos.add(new ParameterCombo(values));
            sources.add(IouStability.IouSource.fromTreeResult(tree.query(
                    ComponentTreeQuery.builder().threshold(thresholds[i]).build())));
        }

        StabilityOutcome outcome = IouStability.score(combos, sources);

        assertEquals(StabilityOutcome.Kind.STABLE_AT, outcome.kind());
        assertEquals(1, outcome.index());
        assertTrue(outcome.meanNeighbourIou() > 0.0d);
    }
}
