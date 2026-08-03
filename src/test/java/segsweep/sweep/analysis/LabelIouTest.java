/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import ij.ImagePlus;
import ij.process.ByteProcessor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LabelIouTest {

    @Test
    public void identicalLabelMapsGiveOne() {
        ImagePlus left = mask(4, 4, 1, 2, 6);
        ImagePlus right = mask(4, 4, 1, 2, 6);

        assertEquals(1.0d, LabelIou.foregroundIou(left, right), 0.000001d);
    }

    @Test
    public void disjointLabelMapsGiveZero() {
        ImagePlus left = mask(4, 4, 1, 2, 6);
        ImagePlus right = mask(4, 4, 8, 9, 10);

        assertEquals(0.0d, LabelIou.foregroundIou(left, right), 0.000001d);
    }

    @Test(expected = IllegalArgumentException.class)
    public void differentDimensionsThrow() {
        LabelIou.foregroundIou(mask(4, 4, 1), mask(5, 4, 1));
    }

    private static ImagePlus mask(int width, int height, int... activeIndexes) {
        byte[] pixels = new byte[width * height];
        for (int i = 0; i < activeIndexes.length; i++) {
            pixels[activeIndexes[i]] = (byte) 255;
        }
        return new ImagePlus("mask", new ByteProcessor(width, height, pixels, null));
    }
}
