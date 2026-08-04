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
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.sweep.strategy.SegSweepClassicalStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SweepIntegrationTest {

    @Test
    public void treeBackedClassicalSweepPublishesDisplayedGridResults() throws Exception {
        ImagePlus image = monotoneObjectStack();
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(20, 50, 70, 90, 110));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");
        List<VariationResult> results = new ArrayList<VariationResult>();

        new SegSweepClassicalStrategy(image, CropSpec.full(),
                SegSweepLabeller.Connectivity.SIX, null)
                .dispatch(sweep, results::add, null, null);

        assertEquals(5, results.size());
        Map<Integer, Integer> countsByThreshold = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; i < results.size(); i++) {
            VariationResult result = results.get(i);
            assertFalse(result.hasError());
            assertTrue(result.provenance() != null);
            assertTrue(Double.isFinite(result.objectsPerCalibratedVolume()));
            countsByThreshold.put(((Number) result.combo().get(ParameterId.THRESHOLD)).intValue(),
                    Integer.valueOf(result.objectCount()));
        }
        assertTrue(countsByThreshold.get(Integer.valueOf(50)).intValue()
                <= countsByThreshold.get(Integer.valueOf(20)).intValue());
        assertTrue(countsByThreshold.get(Integer.valueOf(70)).intValue()
                <= countsByThreshold.get(Integer.valueOf(50)).intValue());
        assertTrue(countsByThreshold.get(Integer.valueOf(90)).intValue()
                <= countsByThreshold.get(Integer.valueOf(70)).intValue());
        assertTrue(countsByThreshold.get(Integer.valueOf(110)).intValue()
                <= countsByThreshold.get(Integer.valueOf(90)).intValue());
    }

    static ImagePlus monotoneObjectStack() {
        // Keep the integration fixture deliberately small so the production memory
        // guard remains deterministic even in a memory-constrained test JVM.
        ImageStack stack = new ImageStack(80, 16);
        for (int z = 0; z < 3; z++) {
            stack.addSlice("z" + (z + 1), new ShortProcessor(80, 16));
        }
        ImagePlus image = new ImagePlus("stage-09-integration", stack);
        image.setDimensions(1, 3, 1);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 1.0;
        calibration.setUnit("micron");
        image.setCalibration(calibration);

        setBlock(image, 5, 5, 1, 4, 4, 2, 100);
        setBlock(image, 25, 5, 1, 4, 4, 2, 80);
        setBlock(image, 45, 5, 1, 4, 4, 2, 60);
        setBlock(image, 65, 5, 1, 4, 4, 2, 40);
        return image;
    }

    private static void setBlock(ImagePlus image, int x0, int y0, int z0,
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
}
