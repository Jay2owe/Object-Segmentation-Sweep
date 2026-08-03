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
import ij.process.ShortProcessor;
import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class CropSpecMultiChannelTest {

    @Test
    public void customCropPreservesChannelAndSliceDimensions() {
        ImagePlus cropped = CropSpec.custom(new Rectangle(1, 1, 2, 2))
                .applyMultiChannel(hyperstack());

        assertEquals(2, cropped.getNChannels());
        assertEquals(2, cropped.getNSlices());
        assertEquals(1, cropped.getNFrames());
        assertEquals(2, cropped.getWidth());
        assertEquals(2, cropped.getHeight());
        assertEquals(10, cropped.getStack().getProcessor(1).get(0, 0));
        assertEquals(20, cropped.getStack().getProcessor(2).get(0, 0));
        assertEquals(11, cropped.getStack().getProcessor(3).get(0, 0));
        assertEquals(21, cropped.getStack().getProcessor(4).get(0, 0));
    }

    @Test
    public void fullModeReturnsSourceUnchanged() {
        ImagePlus source = hyperstack();

        assertSame(source, CropSpec.full().applyMultiChannel(source));
    }

    private static ImagePlus hyperstack() {
        ImageStack stack = new ImageStack(4, 4);
        stack.addSlice(constant(4, 4, 10));
        stack.addSlice(constant(4, 4, 20));
        stack.addSlice(constant(4, 4, 11));
        stack.addSlice(constant(4, 4, 21));
        ImagePlus image = new ImagePlus("hs", stack);
        image.setDimensions(2, 2, 1);
        return image;
    }

    private static ShortProcessor constant(int width, int height, int value) {
        short[] pixels = new short[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (short) value;
        }
        return new ShortProcessor(width, height, pixels, null);
    }
}
