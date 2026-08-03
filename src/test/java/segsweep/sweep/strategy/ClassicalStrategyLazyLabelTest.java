/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.strategy;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.VariationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ClassicalStrategyLazyLabelTest {

    @Test
    public void dispatchDoesNotMaterialiseEveryLabelMap() throws Exception {
        List<VariationResult> results = new ArrayList<VariationResult>();

        new SegSweepClassicalStrategy(source(), CropSpec.full(),
                SegSweepLabeller.Connectivity.SIX, null,
                null, permitGuard(), new TreeBuildOnceTest.CountingFactory(), noopCloser())
                .dispatch(thresholdSweep(), results::add, null, null);

        assertEquals(5, results.size());
        for (int i = 0; i < results.size(); i++) {
            assertEquals(0, results.get(i).labelMap().materializationCount());
        }

        results.get(2).labelMap().get();

        for (int i = 0; i < results.size(); i++) {
            assertEquals(i == 2 ? 1 : 0,
                    results.get(i).labelMap().materializationCount());
        }
    }

    static ParameterSweep thresholdSweep() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(20, 50, 70, 90, 110));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");
    }

    static ImagePlus source() {
        ImageStack stack = new ImageStack(32, 32);
        for (int z = 0; z < 4; z++) {
            stack.addSlice(new ShortProcessor(32, 32));
        }
        ImagePlus image = new ImagePlus("strategy-source", stack);
        setBlock(image, 2, 2, 0, 3, 3, 1, 100);
        setBlock(image, 10, 2, 0, 3, 3, 1, 80);
        setBlock(image, 18, 2, 0, 3, 3, 1, 60);
        setBlock(image, 26, 2, 0, 3, 3, 1, 40);
        return image;
    }

    static void setBlock(ImagePlus image, int x0, int y0, int z0,
                         int width, int height, int depth, int value) {
        for (int z = z0; z < z0 + depth; z++) {
            ImageProcessor processor = image.getStack().getProcessor(z + 1);
            for (int y = y0; y < y0 + height; y++) {
                for (int x = x0; x < x0 + width; x++) {
                    processor.set(x, y, value);
                }
            }
        }
    }

    static SegSweepClassicalStrategy.TreeMemoryGuard permitGuard() {
        return new SegSweepClassicalStrategy.TreeMemoryGuard() {
            @Override public SegSweepClassicalStrategy.GuardVerdict assess(
                    ParameterSweep displayWindow, ImagePlus cropped) {
                return SegSweepClassicalStrategy.GuardVerdict.allow();
            }
        };
    }

    static SegSweepClassicalStrategy.ImageCloser noopCloser() {
        return new SegSweepClassicalStrategy.ImageCloser() {
            @Override public void close(ImagePlus image) {
            }
        };
    }
}
