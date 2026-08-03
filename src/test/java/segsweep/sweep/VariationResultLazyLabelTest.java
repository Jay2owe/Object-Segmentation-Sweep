/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import ij.ImagePlus;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.SegSweepLabellerFixtures;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.LazyLabelMap;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class VariationResultLazyLabelTest {
    @Test
    public void resultKeepsLazyProviderAndDoesNotMaterialiseOnConstruction() {
        LazyLabelMap labelMap = labelMap();

        VariationResult result = VariationResult.success(combo(), labelMap,
                2, 5L, null, provenance());

        assertSame(labelMap, result.labelMap());
        assertEquals(0, labelMap.materializationCount());
        ImagePlus labels = result.labelMap().get();
        assertEquals(16, labels.getBitDepth());
        assertEquals(1, labelMap.materializationCount());
    }

    private static ParameterCombo combo() {
        return ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Integer.valueOf(10))
                .build();
    }

    private static LazyLabelMap labelMap() {
        ImagePlus image = SegSweepLabellerFixtures.points(5, 2, 1,
                new int[][] { { 0, 0, 0 }, { 4, 0, 0 } });
        return ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .labelMap();
    }

    private static SweepProvenance provenance() {
        Map<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        ranges.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10));
        return new SweepProvenance(CropSpec.full(), 5, 2, 1,
                ranges, "micron", 0.024d);
    }
}
