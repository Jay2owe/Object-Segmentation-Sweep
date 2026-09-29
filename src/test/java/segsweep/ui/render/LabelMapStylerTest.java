package segsweep.ui.render;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Test;

import java.awt.image.IndexColorModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LabelMapStylerTest {

    @Test
    public void labelsReceiveDistinctCategoricalColours() {
        int labelOne = LabelMapStyler.rgbForLabel(1);
        int labelTwo = LabelMapStyler.rgbForLabel(2);
        int labelThree = LabelMapStyler.rgbForLabel(3);

        assertNotEquals(labelOne, labelTwo);
        assertNotEquals(labelOne, labelThree);
        assertNotEquals(labelTwo, labelThree);
        assertEquals(0x000000, LabelMapStyler.rgbForLabel(0));
    }

    /**
     * Found by the GUI checks of 0.2.0: the picked label map opened with the
     * grey table, so a 16-bit map's labels 1..N were near-black. Shown maps now
     * carry the tile colours: label k is drawn exactly as its tile drew it.
     */
    @Test
    public void viewedSixteenBitLabelMapShowsTileColoursPerLabel() {
        ij.ImageStack stack = new ij.ImageStack(4, 1);
        for (int z = 0; z < 2; z++) {
            ij.process.ShortProcessor slice = new ij.process.ShortProcessor(4, 1);
            slice.set(0, 0, 1);
            slice.set(1, 0, 5);
            slice.set(2, 0, 9);
            stack.addSlice(slice);
        }
        ImagePlus labels = new ImagePlus("labels", stack);

        LabelMapStyler.styleForViewing(labels);

        assertEquals(0.0, labels.getDisplayRangeMin(), 0.0001);
        assertEquals(255.0, labels.getDisplayRangeMax(), 0.0001);
        for (int z = 1; z <= 2; z++) {
            labels.setSlice(z);
            java.awt.image.BufferedImage shown = labels.getProcessor().getBufferedImage();
            for (int x = 0; x < 3; x++) {
                int label = labels.getProcessor().get(x, 0);
                assertEquals("slice " + z + " label " + label,
                        LabelMapStyler.rgbForLabel(label), shown.getRGB(x, 0) & 0xffffff);
            }
            assertEquals(0x000000, shown.getRGB(3, 0) & 0xffffff);
        }
    }

    @Test
    public void viewedLabelMapWithMoreThan255LabelsKeepsEveryLabelInRange() {
        ij.process.ShortProcessor slice = new ij.process.ShortProcessor(2, 1);
        slice.set(0, 0, 1);
        slice.set(1, 0, 700);
        ImagePlus labels = new ImagePlus("many", slice);

        LabelMapStyler.styleForViewing(labels);

        assertEquals(700.0, labels.getDisplayRangeMax(), 0.0001);
        assertTrue(labels.getProcessor().getColorModel() instanceof IndexColorModel);
    }

    @Test
    public void applySetsCategoricalLutAndDisplayRangeFromLabels() {
        ByteProcessor labels = new ByteProcessor(3, 1);
        labels.set(0, 0, 1);
        labels.set(1, 0, 7);
        ImagePlus image = new ImagePlus("labels", labels);

        ImagePlus styled = LabelMapStyler.apply(image, 3);

        assertEquals(image, styled);
        assertEquals(0.0, styled.getDisplayRangeMin(), 0.0001);
        assertEquals(7.0, styled.getDisplayRangeMax(), 0.0001);
        ImageProcessor rendered = styled.getProcessor();
        assertTrue(rendered.getColorModel() instanceof IndexColorModel);
    }
}
