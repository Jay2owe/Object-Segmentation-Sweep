package segsweep.ui.render;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ObjectOverlayRendererTest {

    @Test
    public void objectOverlayUsesLightAlphaAndKeepsBackgroundVisible() {
        ByteProcessor sourceProcessor = new ByteProcessor(2, 1);
        sourceProcessor.set(0, 0, 0);
        sourceProcessor.set(1, 0, 255);
        ImagePlus source = new ImagePlus("source", sourceProcessor);

        ByteProcessor labelProcessor = new ByteProcessor(2, 1);
        labelProcessor.set(0, 0, 1);
        labelProcessor.set(1, 0, 0);
        ImagePlus labels = new ImagePlus("labels", labelProcessor);

        ImagePlus overlay = ObjectOverlayRenderer.renderOverlay(source, labels);
        ImageProcessor rendered = overlay.getProcessor();

        assertEquals(blend(0x000000, LabelMapStyler.rgbForLabel(1), 0.35),
                rendered.getPixel(0, 0) & 0xffffff);
        assertEquals(0xffffff, rendered.getPixel(1, 0) & 0xffffff);
    }

    @Test
    public void objectOverlayAppliesDisplayRangeOnlyToSourceBackground() {
        ByteProcessor sourceProcessor = new ByteProcessor(2, 1);
        sourceProcessor.set(0, 0, 100);
        sourceProcessor.set(1, 0, 100);
        ImagePlus source = new ImagePlus("source", sourceProcessor);

        ByteProcessor labelProcessor = new ByteProcessor(2, 1);
        labelProcessor.set(0, 0, 1);
        labelProcessor.set(1, 0, 0);
        ImagePlus labels = new ImagePlus("labels", labelProcessor);

        ImagePlus overlay = ObjectOverlayRenderer.renderOverlay(
                source,
                labels,
                PreviewDisplaySettings.of(100.0, 200.0,
                        PreviewDisplaySettings.LutMode.GREY, "Grays"));
        ImageProcessor rendered = overlay.getProcessor();

        assertEquals(blend(0x000000, LabelMapStyler.rgbForLabel(1), 0.35),
                rendered.getPixel(0, 0) & 0xffffff);
        assertEquals(0x000000, rendered.getPixel(1, 0) & 0xffffff);
    }

    @Test
    public void filteredLabelMapCanHideOrGhostRemovedLabels() {
        ByteProcessor labelProcessor = new ByteProcessor(2, 1);
        labelProcessor.set(0, 0, 1);
        labelProcessor.set(1, 0, 2);
        ImagePlus labels = new ImagePlus("labels", labelProcessor);
        Set<Integer> removed = new HashSet<Integer>();
        removed.add(Integer.valueOf(2));

        ImageProcessor hidden = ObjectOverlayRenderer.renderFiltered(
                null, labels, removed, false, null).getProcessor();
        ImageProcessor ghost = ObjectOverlayRenderer.renderFiltered(
                null, labels, removed, true, null).getProcessor();

        assertEquals(LabelMapStyler.rgbForLabel(1), hidden.getPixel(0, 0) & 0xffffff);
        assertEquals(0x000000, hidden.getPixel(1, 0) & 0xffffff);
        assertEquals(0x808080, ghost.getPixel(1, 0) & 0xffffff);
    }

    @Test
    public void labelsAboveTwoHundredFiftyFiveDoNotCollideWithRemovalSet() {
        ImagePlus labels = labelMapOneToTwoThousand();
        Set<Integer> removed = new HashSet<Integer>();
        removed.add(Integer.valueOf(260));

        ImageProcessor hidden = ObjectOverlayRenderer.renderFiltered(
                null, labels, removed, false, null).getProcessor();
        ImageProcessor ghost = ObjectOverlayRenderer.renderFiltered(
                null, labels, removed, true, null).getProcessor();

        assertEquals(LabelMapStyler.rgbForLabel(5), hidden.getPixel(4, 0) & 0xffffff);
        assertEquals(0x000000, hidden.getPixel(259, 0) & 0xffffff);
        assertEquals(LabelMapStyler.rgbForLabel(515), hidden.getPixel(514, 0) & 0xffffff);
        assertEquals(0x808080, ghost.getPixel(259, 0) & 0xffffff);
    }

    private static int blend(int base, int overlay, double alpha) {
        int br = (base >> 16) & 0xff;
        int bg = (base >> 8) & 0xff;
        int bb = base & 0xff;
        int or = (overlay >> 16) & 0xff;
        int og = (overlay >> 8) & 0xff;
        int ob = overlay & 0xff;
        int r = (int) Math.round(br * (1.0 - alpha) + or * alpha);
        int g = (int) Math.round(bg * (1.0 - alpha) + og * alpha);
        int b = (int) Math.round(bb * (1.0 - alpha) + ob * alpha);
        return (r << 16) | (g << 8) | b;
    }

    private static ImagePlus labelMapOneToTwoThousand() {
        ShortProcessor processor = new ShortProcessor(2000, 1);
        for (int x = 0; x < 2000; x++) {
            processor.set(x, 0, x + 1);
        }
        return new ImagePlus("labels", processor);
    }
}
