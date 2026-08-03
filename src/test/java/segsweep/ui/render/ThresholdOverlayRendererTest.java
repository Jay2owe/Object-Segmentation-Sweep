package segsweep.ui.render;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThresholdOverlayRendererTest {

    @Test
    public void maskModeReturnsBinaryForegroundWithinThresholdRange() {
        ImagePlus source = image(0, 25, 75, 100);

        ImageProcessor mask = ThresholdOverlayRenderer.render(source, 20.0, 80.0,
                ThresholdOverlayRenderer.MODE_MASK).getProcessor();

        assertEquals(0, mask.get(0, 0));
        assertEquals(255, mask.get(1, 0));
        assertEquals(255, mask.get(2, 0));
        assertEquals(0, mask.get(3, 0));
    }

    @Test
    public void redOverlayTintsOnlyForegroundPixels() {
        ImagePlus source = image(0, 100);

        ImageProcessor overlay = ThresholdOverlayRenderer.render(source, 50.0, 150.0,
                ThresholdOverlayRenderer.MODE_RED_OVERLAY).getProcessor();

        assertFalse(redTinted(overlay.getPixel(0, 0)));
        assertTrue(redTinted(overlay.getPixel(1, 0)));
    }

    @Test
    public void otsuThresholdReturnsTypedEmptyForMissingImage() {
        ThresholdOverlayRenderer.OtsuThreshold threshold =
                ThresholdOverlayRenderer.otsuThreshold(null);

        assertFalse(threshold.hasValues());
    }

    @Test
    public void otsuThresholdFindsForegroundLowerBound() {
        ImagePlus source = image(0, 0, 255, 255);

        ThresholdOverlayRenderer.OtsuThreshold threshold =
                ThresholdOverlayRenderer.otsuThreshold(source);

        assertTrue(threshold.hasValues());
        assertTrue(threshold.getLower() > 0.0);
        assertEquals(255.0, threshold.getUpper(), 0.0001);
    }

    private static ImagePlus image(int... values) {
        ByteProcessor processor = new ByteProcessor(values.length, 1);
        for (int i = 0; i < values.length; i++) {
            processor.set(i, 0, values[i]);
        }
        return new ImagePlus("threshold", processor);
    }

    private static boolean redTinted(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return r > g && r > b;
    }
}
