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

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CropProvenanceTest {
    @Test
    public void fullImageResultCarriesFullCropProvenance() {
        SweepProvenance provenance = provenance(CropSpec.full(), 20, 10, 3);
        VariationResult result = VariationResult.success(combo(), labelMap(),
                1, 0L, null, provenance);

        assertEquals(new Rectangle(0, 0, 20, 10),
                result.provenance().crop().boundsFor(20, 10));
        assertEquals(1.0d, result.provenance().cropFraction(), 0.0d);
    }

    @Test
    public void croppedResultCarriesCropBoundsAndFraction() {
        SweepProvenance provenance = provenance(
                CropSpec.custom(new Rectangle(5, 2, 4, 5)), 20, 10, 3);
        VariationResult result = VariationResult.success(combo(), labelMap(),
                1, 0L, null, provenance);

        assertEquals(new Rectangle(5, 2, 4, 5),
                result.provenance().crop().boundsFor(20, 10));
        assertEquals(0.1d, result.provenance().cropFraction(), 1.0e-12);
    }

    @Test
    public void resultCannotBeConstructedWithoutProvenance() {
        try {
            VariationResult.success(combo(), labelMap(), 1, 0L, null, null);
            fail("Expected null provenance to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertEquals("provenance must not be null", expected.getMessage());
        }
    }

    private static ParameterCombo combo() {
        return ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Integer.valueOf(10))
                .build();
    }

    private static LazyLabelMap labelMap() {
        ImagePlus image = SegSweepLabellerFixtures.points(3, 3, 1,
                new int[][] { { 1, 1, 0 } });
        return ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .labelMap();
    }

    private static SweepProvenance provenance(CropSpec crop,
                                              int width,
                                              int height,
                                              int depth) {
        Map<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        ranges.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10));
        return new SweepProvenance(crop, width, height, depth,
                ranges, "micron", 0.024d);
    }
}
