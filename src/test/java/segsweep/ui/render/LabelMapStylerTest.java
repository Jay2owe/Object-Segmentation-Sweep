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
