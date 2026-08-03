/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import segsweep.SegSweepLabeller;
import segsweep.SegSweepLabellerFixtures;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.SweepProvenance;
import segsweep.sweep.VariationResult;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.LazyLabelMap;

import java.util.LinkedHashMap;
import java.util.Map;

final class GridTestFixtures {
    private GridTestFixtures() {
    }

    static ParameterCombo combo(int threshold) {
        return ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Integer.valueOf(threshold))
                .build();
    }

    static ParameterSweep oneAxisSweep(int... thresholds) {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(thresholds));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values);
    }

    static ParameterSweep twoAxisSweep() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20, 30));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(1, 2, 3, 4));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values);
    }

    static ImagePlus image(String title, int value) {
        ByteProcessor processor = new ByteProcessor(8, 8);
        processor.setValue(value);
        processor.fill();
        return new ImagePlus(title, processor);
    }

    static ImagePlus stack(int slices) {
        ImageStack stack = new ImageStack(8, 8);
        for (int z = 1; z <= slices; z++) {
            ByteProcessor processor = new ByteProcessor(8, 8);
            processor.setValue(z * 20);
            processor.fill();
            stack.addSlice("z" + z, processor);
        }
        ImagePlus image = new ImagePlus("stack-" + slices, stack);
        image.setDimensions(1, slices, 1);
        return image;
    }

    static ImagePlus labels() {
        ByteProcessor processor = new ByteProcessor(8, 8);
        for (int y = 2; y < 4; y++) {
            for (int x = 2; x < 4; x++) {
                processor.set(x, y, 1);
            }
        }
        for (int y = 5; y < 7; y++) {
            for (int x = 5; x < 7; x++) {
                processor.set(x, y, 2);
            }
        }
        return new ImagePlus("labels", processor);
    }

    static VariationResult result(ParameterCombo combo) {
        return VariationResult.success(combo, labelMap(), 2, 7L,
                null, provenance());
    }

    static LazyLabelMap labelMap() {
        ImagePlus image = SegSweepLabellerFixtures.points(5, 2, 1,
                new int[][] { { 0, 0, 0 }, { 4, 0, 0 } });
        return ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .labelMap();
    }

    static LazyLabelMap labelMap(int slices) {
        return ComponentTree.build(stack(slices), SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .labelMap();
    }

    static SweepProvenance provenance() {
        Map<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        ranges.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20, 30));
        return new SweepProvenance(CropSpec.full(), 5, 2, 1,
                ranges, "micron", 0.024d);
    }
}
