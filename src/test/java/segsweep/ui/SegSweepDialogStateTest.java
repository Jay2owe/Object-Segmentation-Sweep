/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import org.junit.Test;
import segsweep.SegSweepMacroOptions;
import segsweep.SegSweepParameters;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Stage 05 regression tests for dialog state that need no visible window. */
public class SegSweepDialogStateTest {

    @Test
    public void threeChannelStateRestoredOntoSingleChannelImageUsesChannelOne() {
        ImagePlus single = new ImagePlus("single", new ByteProcessor(16, 16));
        SegSweepDialog.DialogState state = SegSweepDialog.analysisStateForTest(single);
        SegSweepMacroOptions remembered = SegSweepMacroOptions.defaults();
        remembered.setChannel(3);

        SegSweepDialog.applyRestoredOptions(state, remembered);

        assertEquals("1", state.channelField.getText());
        assertFalse("channel row stays hidden for one channel", state.channelRow.isVisible());
        SegSweepMacroOptions options = state.optionsFromFields();
        assertEquals(1, options.channel());
        SegSweepParameters parameters = options.toParameters(single);
        assertNotNull(parameters);
    }

    @Test
    public void rememberedChannelThatExistsIsKept() {
        ImagePlus threeChannel = threeChannelImage();
        SegSweepDialog.DialogState state = SegSweepDialog.analysisStateForTest(threeChannel);
        SegSweepMacroOptions remembered = SegSweepMacroOptions.defaults();
        remembered.setChannel(3);
        SegSweepDialog.applyRestoredOptions(state, remembered);
        assertEquals("3", state.channelField.getText());
    }

    @Test
    public void nonRectangularRoiPastTheEdgeBecomesAClippedBoundingBox() {
        ImagePlus image = new ImagePlus("roi", new ByteProcessor(20, 20));
        image.setRoi(new OvalRoi(15, 12, 10, 10));

        Rectangle crop = SegSweepDialog.roiCropBounds(image);
        assertEquals(new Rectangle(15, 12, 5, 8), crop);
        String note = SegSweepDialog.roiCropNote(image);
        assertTrue(note, note.contains("bounding box of the selection is used"));
        assertTrue(note, note.contains("clipped"));

        SegSweepDialog.DialogState state = SegSweepDialog.analysisStateForTest(image);
        state.cropChoice.setSelectedItem("Sweep in ROI");
        SegSweepMacroOptions options = state.optionsFromFields();
        assertEquals(new Rectangle(15, 12, 5, 8), options.crop().bounds());
        assertNotNull(options.toParameters(image));
    }

    @Test
    public void rectangleInsideTheImageNeedsNoNote() {
        ImagePlus image = new ImagePlus("roi", new ByteProcessor(20, 20));
        image.setRoi(new Roi(2, 2, 5, 5));
        assertEquals("", SegSweepDialog.roiCropNote(image));
        image.setRoi(new OvalRoi(2, 2, 5, 5));
        assertTrue(SegSweepDialog.roiCropNote(image).contains("bounding box"));
    }

    @Test
    public void roiEntirelyOutsideTheImageIsRefusedClearly() {
        ImagePlus image = new ImagePlus("roi", new ByteProcessor(20, 20));
        image.setRoi(new Roi(2, 2, 5, 5));
        image.getRoi().setLocation(30, 30);
        try {
            SegSweepDialog.roiCropBounds(image);
            fail("expected refusal");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("outside the image"));
        }
    }

    @Test
    public void lettersInNumberFieldsNameTheField() {
        SegSweepDialog.DialogState state = SegSweepDialog.analysisStateForTest(
                new ImagePlus("x", new ByteProcessor(8, 8)));
        state.fromField.setText("ten");
        assertFieldError(state, "From must be a number (got \"ten\").");
        state.fromField.setText("10");
        state.stepField.setText("");
        assertFieldError(state, "Step must be a number.");
        state.stepField.setText("5");
        state.channelField.setText("1.5");
        assertFieldError(state, "Channel must be a whole number (got \"1.5\").");
    }

    private static void assertFieldError(SegSweepDialog.DialogState state, String message) {
        try {
            state.optionsFromFields();
            fail("expected " + message);
        } catch (IllegalArgumentException expected) {
            assertEquals(message, expected.getMessage());
        }
    }

    private static ImagePlus threeChannelImage() {
        ImageStack stack = new ImageStack(8, 8);
        for (int c = 0; c < 3; c++) stack.addSlice("c" + c, new ByteProcessor(8, 8));
        ImagePlus image = new ImagePlus("three", stack);
        image.setDimensions(3, 1, 1);
        return image;
    }
}
