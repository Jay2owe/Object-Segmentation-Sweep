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
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;

public class SourceImageViewTest {

    @Test
    public void selectedChannelIsCopiedDirectlyIntoCropSizedPlanes() {
        ImageStack stack = new ImageStack(5, 4);
        for (int index = 1; index <= 4; index++) {
            DuplicateForbiddenProcessor plane = new DuplicateForbiddenProcessor(5, 4);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 5; x++) {
                    plane.set(x, y, index * 20 + y * 5 + x);
                }
            }
            stack.addSlice("plane-" + index, plane);
        }
        ImagePlus source = new ImagePlus("two-channel", stack);
        source.setDimensions(2, 2, 1);

        ImagePlus view = SourceImageView.selectedChannelAndCrop(
                source, 2, CropSpec.custom(new Rectangle(1, 1, 2, 2)));

        assertEquals(2, view.getWidth());
        assertEquals(2, view.getHeight());
        assertEquals(2, view.getStackSize());
        assertEquals(46, view.getStack().getProcessor(1).get(0, 0));
        assertEquals(86, view.getStack().getProcessor(2).get(0, 0));
        assertEquals("plane-2", view.getStack().getSliceLabel(1));
        assertEquals("plane-4", view.getStack().getSliceLabel(2));
    }

    private static final class DuplicateForbiddenProcessor extends ByteProcessor {
        DuplicateForbiddenProcessor(int width, int height) {
            super(width, height);
        }

        @Override
        public ImageProcessor duplicate() {
            throw new AssertionError("A full source plane must not be duplicated before cropping.");
        }
    }
}
