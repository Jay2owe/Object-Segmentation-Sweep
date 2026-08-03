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
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RangeSuggesterTest {

    @Test
    public void classicalThresholdSuggestionsSpanAutoThresholdMethods() {
        ImagePlus source = noisyGradientStack();

        ParameterValueList thresholds =
                RangeSuggester.suggestThresholdDisplayWindow(source, CropSpec.full());

        assertNotNull(thresholds);
        assertCloseToAny(thresholds.values(),
                thresholdFor(source, AutoThresholder.Method.Otsu));
        assertCloseToAny(thresholds.values(),
                thresholdFor(source, AutoThresholder.Method.Li));
        assertCloseToAny(thresholds.values(),
                thresholdFor(source, AutoThresholder.Method.Triangle));
    }

    @Test
    public void stackAndProjectionThresholdsDisagreeOnThisImage() {
        ImagePlus source = noisyGradientStack();

        int fromStack = thresholdFor(source, AutoThresholder.Method.Otsu);
        int fromProjection = projectionThresholdFor(source, AutoThresholder.Method.Otsu);

        assertTrue("expected the projection to bias the threshold, got " + fromStack
                        + " and " + fromProjection,
                fromStack != fromProjection);
    }

    @Test
    public void sizeSuggestionUses3DComponentVoxels() {
        ImagePlus source = componentStack();

        ParameterValueList sizes = RangeSuggester.suggestSizeDisplayWindow(source, CropSpec.full());

        assertEquals(5, sizes.size());
        assertTrue(sizes.values().contains(Integer.valueOf(8)));
        assertTrue(maxValue(sizes.values()) > 9);
    }

    private static void assertCloseToAny(List<Object> suggestions, int expected) {
        for (int i = 0; i < suggestions.size(); i++) {
            int value = ((Number) suggestions.get(i)).intValue();
            double allowed = Math.max(1.0d, Math.abs(expected) * 0.05d);
            if (Math.abs(value - expected) <= allowed) {
                return;
            }
        }
        assertTrue("No suggestion within 5% of " + expected + ": " + suggestions, false);
    }

    private static int maxValue(List<Object> values) {
        int max = 0;
        for (int i = 0; i < values.size(); i++) {
            max = Math.max(max, ((Number) values.get(i)).intValue());
        }
        return max;
    }

    private static int thresholdFor(ImagePlus source, AutoThresholder.Method method) {
        int[] histogram = new int[256];
        int width = source.getWidth();
        int height = source.getHeight();
        for (int z = 1; z <= source.getStackSize(); z++) {
            ImageProcessor plane = source.getStack().getProcessor(z);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    histogram[plane.get(x, y) & 0xff]++;
                }
            }
        }
        return new AutoThresholder().getThreshold(method, histogram);
    }

    private static int projectionThresholdFor(ImagePlus source, AutoThresholder.Method method) {
        int[] histogram = new int[256];
        int width = source.getWidth();
        int height = source.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int max = 0;
                for (int z = 1; z <= source.getStackSize(); z++) {
                    int value = source.getStack().getProcessor(z).get(x, y) & 0xff;
                    if (value > max) {
                        max = value;
                    }
                }
                histogram[max]++;
            }
        }
        return new AutoThresholder().getThreshold(method, histogram);
    }

    private static ImagePlus noisyGradientStack() {
        int width = 128;
        int height = 128;
        int slices = 5;
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < slices; z++) {
            ByteProcessor processor = new ByteProcessor(width, height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int noise = (x * 17 + y * 31 + z * 43) % 23;
                    int value = Math.min(255, x * 2 + noise);
                    processor.set(x, y, value);
                }
            }
            stack.addSlice("z" + (z + 1), processor);
        }
        ImagePlus image = new ImagePlus("noisy-gradient", stack);
        image.setDimensions(1, slices, 1);
        return image;
    }

    private static ImagePlus componentStack() {
        ImageStack stack = new ImageStack(12, 12);
        for (int z = 0; z < 5; z++) {
            stack.addSlice(new ByteProcessor(12, 12));
        }
        ImagePlus image = new ImagePlus("components", stack);
        setBlock(image, 0, 0, 0, 1, 1, 1, 100);
        setBlock(image, 3, 0, 0, 2, 2, 2, 100);
        setBlock(image, 7, 0, 0, 3, 3, 3, 100);
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
