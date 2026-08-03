package segsweep.ui.render;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RenderStackArtifactTest {

    @Test
    public void threeObjectLabelMapRendersDistinctOverlayAndRawReference() throws Exception {
        ImagePlus raw = rawImage();
        ImagePlus labels = threeObjectLabels();

        ImagePlus overlay = ObjectOverlayRenderer.renderOverlay(
                raw,
                labels,
                PreviewDisplaySettings.of(0.0, 255.0,
                        PreviewDisplaySettings.LutMode.GREY, "Grays"));
        ImagePreviewPanel rawPanel = new ImagePreviewPanel("Raw");
        rawPanel.setImage(raw);
        rawPanel.setDisplaySettings(PreviewDisplaySettings.of(0.0, 255.0,
                PreviewDisplaySettings.LutMode.GREY, "Grays"));

        ImageProcessor overlayProcessor = overlay.getProcessor();
        ImageProcessor rawProcessor = rawPanel.renderedProcessorForTest();

        Set<Integer> objectColours = new HashSet<Integer>();
        objectColours.add(Integer.valueOf(overlayProcessor.getPixel(1, 1) & 0xffffff));
        objectColours.add(Integer.valueOf(overlayProcessor.getPixel(3, 1) & 0xffffff));
        objectColours.add(Integer.valueOf(overlayProcessor.getPixel(5, 1) & 0xffffff));
        assertEquals(3, objectColours.size());
        assertEquals(0, rawProcessor.getPixel(0, 0));
        assertEquals(128, rawProcessor.getPixel(3, 1));
        assertEquals(255, rawProcessor.getPixel(6, 2));

        File outputDir = new File("target/render-stack-artifacts");
        assertTrue(outputDir.mkdirs() || outputDir.isDirectory());
        ImageIO.write(toBufferedImage(overlayProcessor), "png",
                new File(outputDir, "three-object-overlay.png"));
        ImageIO.write(toBufferedImage(rawProcessor), "png",
                new File(outputDir, "raw-preview.png"));
    }

    private static ImagePlus rawImage() {
        ByteProcessor raw = new ByteProcessor(7, 3);
        for (int y = 0; y < raw.getHeight(); y++) {
            for (int x = 0; x < raw.getWidth(); x++) {
                raw.set(x, y, (int) Math.round(255.0 * x / 6.0));
            }
        }
        return new ImagePlus("raw", raw);
    }

    private static ImagePlus threeObjectLabels() {
        ByteProcessor labels = new ByteProcessor(7, 3);
        labels.set(1, 1, 1);
        labels.set(3, 1, 2);
        labels.set(5, 1, 3);
        return new ImagePlus("labels", labels);
    }

    private static BufferedImage toBufferedImage(ImageProcessor processor) {
        BufferedImage image = new BufferedImage(processor.getWidth(), processor.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < processor.getHeight(); y++) {
            for (int x = 0; x < processor.getWidth(); x++) {
                int value = processor.getPixel(x, y);
                int rgb;
                if (processor instanceof ij.process.ColorProcessor) {
                    rgb = value & 0xffffff;
                } else {
                    int grey = Math.max(0, Math.min(255, value));
                    rgb = (grey << 16) | (grey << 8) | grey;
                }
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
}
