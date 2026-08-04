/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.ImagePlus;
import ij.io.FileSaver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import segsweep.sweep.ParameterId;
import segsweep.sweep.SweepProvenance;
import segsweep.token.SegmentationMethod;
import segsweep.token.SegmentationTokenParser;
import segsweep.tree.LazyLabelMap;

import java.io.File;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AutoSaveWriterTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writesExactTreeAndMaterialisesOnlyPickedLabelMap() throws Exception {
        File input = tmp.newFile("Exp1-A01_LH_CTX.tif");
        SegSweepResult result = runPickedResult(SegSweepAnalysisTest.designedKneeStack(true));
        LazyLabelMap picked = result.pickedLabelMap();
        assertEquals(0, picked.materializationCount());

        File output = AutoSaveWriter.write(input, result);

        assertTrue(new File(output, "sweep_results.csv").isFile());
        assertTrue(new File(output, "pick_summary.csv").isFile());
        assertTrue(new File(output, "picked_settings.txt").isFile());
        assertTrue(new File(output, "grid.png").isFile());
        assertTrue(new File(output, "labels/Exp1-A01_LH_CTX_picked.tif").isFile());
        assertTrue(new File(output, "labels/README.txt").isFile());
        assertTrue(new File(output, "README.txt").isFile());
        assertEquals(1, picked.materializationCount());
        BufferedImage montage = ImageIO.read(new File(output, "grid.png"));
        assertNotNull(montage);
        assertTrue("Expected distinct per-object overlay colours inside the montage cells",
                distinctOverlayHueCount(montage) >= 2);

        String readme = text(new File(output, "README.txt"));
        assertTrue(readme.contains("displayed range"));
        assertTrue(readme.contains("picked_settings.txt"));
    }

    @Test
    public void pickedSettingsTokenRoundTripsAndRecordsCrop() throws Exception {
        File input = tmp.newFile("crop-test.tif");
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(SegSweepAnalysisTest.designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 60, 10)
                .crop(segsweep.sweep.CropSpec.custom(new java.awt.Rectangle(0, 0, 20, 8)))
                .pickCriterion(SegSweepParameters.PickCriterion.KNEE)
                .build());

        File output = AutoSaveWriter.write(input, result);
        String settings = text(new File(output, "picked_settings.txt"));
        SegmentationMethod parsed = SegmentationTokenParser.parseLenient(lineValue(settings, "settings"));
        assertTrue(parsed.isClassical());
        SweepProvenance provenance = SweepProvenance.fromCanonicalJson(lineValue(settings, "provenance"));
        assertEquals(result.provenance().crop(), provenance.crop());
        assertEquals(result.provenance().fullWidth(), provenance.fullWidth());
        assertEquals(result.provenance().fullHeight(), provenance.fullHeight());
        assertTrue(provenance.displayedRanges().containsKey(ParameterId.THRESHOLD));
        assertTrue(settings.contains("region\tx=0 y=0 w=20 h=8"));
    }

    @Test
    public void existingOutputFolderIsVersionedNotOverwritten() throws Exception {
        File input = tmp.newFile("versioned.tif");
        File first = new File(tmp.getRoot(), AutoSaveWriter.OUTPUT_FOLDER);
        assertTrue(first.mkdirs());
        Files.write(new File(first, "existing.txt").toPath(),
                "keep".getBytes(StandardCharsets.UTF_8));

        File output = AutoSaveWriter.write(input,
                runPickedResult(SegSweepAnalysisTest.designedKneeStack(true)));

        assertNotEquals(first.getAbsolutePath(), output.getAbsolutePath());
        assertTrue(new File(first, "existing.txt").isFile());
        assertTrue(new File(output, "picked_settings.txt").isFile());
    }

    @Test
    public void noPickOmitsFalseSettingsAndLeavesOnlyLabelsReadme() throws Exception {
        File input = tmp.newFile("none.tif");
        SegSweepResult result = SegSweep.run(SegSweepParameters.builder()
                .image(SegSweepAnalysisTest.designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 30, 10)
                .pickCriterion(SegSweepParameters.PickCriterion.NONE)
                .build());

        File output = AutoSaveWriter.write(input, result);

        File[] labels = new File(output, "labels").listFiles();
        assertNotNull(labels);
        assertEquals(1, labels.length);
        assertEquals("README.txt", labels[0].getName());
        for (int i = 0; i < result.results().size(); i++) {
            assertEquals(0, result.results().get(i).labelMap().materializationCount());
        }
        assertFalse(new File(output, "labels/none_picked.tif").exists());
        assertFalse(new File(output, "picked_settings.txt").exists());
    }

    @Test
    public void macroAutosaveWritesRequestedDirectory() throws Exception {
        File input = new File(tmp.getRoot(), "macro-source.tif");
        ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
        assertTrue(new FileSaver(image).saveAsTiff(input.getAbsolutePath()));
        image.close();
        File output = new File(tmp.getRoot(), "requested-output");
        String options = "image=[" + slash(input) + "] sweep=threshold from=10 to=60 step=10 "
                + "pick=knee autosave=[" + slash(output) + "] hide_display";

        SegSweepResult result = new SegSweep_().runFromMacro(options);

        assertNotNull(result);
        assertNull("plugin-owned source should be flushed after headless use",
                result.parameters().image().getProcessor());
        assertTrue(new File(output, "sweep_results.csv").isFile());
        assertTrue(new File(output, "picked_settings.txt").isFile());
        assertTrue(new File(output, "grid.png").isFile());
    }

    @Test
    public void macroWithoutAutosaveWritesAlongsideInputByDefault() throws Exception {
        File inputFolder = tmp.newFolder("default-save");
        File input = new File(inputFolder, "macro-source.tif");
        ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
        assertTrue(new FileSaver(image).saveAsTiff(input.getAbsolutePath()));
        image.close();
        String options = "image=[" + slash(input) + "] sweep=threshold from=10 to=60 step=10 "
                + "pick=knee hide_display";

        SegSweepResult result = new SegSweep_().runFromMacro(options);

        File output = new File(inputFolder, AutoSaveWriter.OUTPUT_FOLDER);
        assertNotNull(result);
        assertTrue(new File(output, "sweep_results.csv").isFile());
        assertTrue(new File(output, "pick_summary.csv").isFile());
        assertTrue(new File(output, "grid.png").isFile());
    }

    @Test
    public void suppliedReviewedGridIsWrittenWithoutSynthesisingAnotherView() throws Exception {
        File input = tmp.newFile("reviewed.tif");
        File requested = new File(tmp.getRoot(), "reviewed-output");
        BufferedImage reviewed = new BufferedImage(7, 5, BufferedImage.TYPE_INT_RGB);
        reviewed.setRGB(3, 2, 0x00ff00ff);

        File output = AutoSaveWriter.writeTo(requested, input,
                runPickedResult(SegSweepAnalysisTest.designedKneeStack(true)), reviewed);

        BufferedImage written = ImageIO.read(new File(output, "grid.png"));
        assertEquals(7, written.getWidth());
        assertEquals(5, written.getHeight());
        assertEquals(0x00ff00ff, written.getRGB(3, 2) & 0x00ffffff);
    }

    @Test
    public void unicodeCalibrationUnitIsPreservedAsUtf8() throws Exception {
        File input = tmp.newFile("unicode.tif");
        ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
        image.getCalibration().setUnit("μm");

        File output = AutoSaveWriter.write(input, runPickedResult(image));

        byte[] bytes = Files.readAllBytes(new File(output, "picked_settings.txt").toPath());
        String settings = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(settings.contains("unit=μm"));
        assertTrue(settings.contains("\"calibrationUnit\":\"μm\""));
        assertTrue(containsBytes(bytes, new byte[] { (byte) 0xce, (byte) 0xbc }));
    }

    private static SegSweepResult runPickedResult(ImagePlus image) {
        return SegSweep.run(SegSweepParameters.builder()
                .image(image)
                .axis(ParameterId.THRESHOLD, 10, 60, 10)
                .pickCriterion(SegSweepParameters.PickCriterion.KNEE)
                .build());
    }

    private static String text(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String lineValue(String text, String key) {
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(key + "\t")) {
                return lines[i].substring(key.length() + 1);
            }
        }
        throw new AssertionError("Missing " + key + " line in:\n" + text);
    }

    private static String slash(File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }

    private static int distinctOverlayHueCount(BufferedImage image) {
        java.util.Set<Integer> hues = new java.util.HashSet<Integer>();
        int maxX = Math.min(image.getWidth() - 1, 205);
        int maxY = Math.min(image.getHeight() - 1, 150);
        for (int y = 15; y <= maxY; y++) {
            for (int x = 15; x <= maxX; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int high = Math.max(r, Math.max(g, b));
                int low = Math.min(r, Math.min(g, b));
                if (high - low > 35) {
                    float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                    hues.add(Integer.valueOf((int) Math.floor(hsb[0] * 12.0f)));
                }
            }
        }
        return hues.size();
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            int matched = 0;
            while (matched < needle.length && haystack[i + matched] == needle[matched]) {
                matched++;
            }
            if (matched == needle.length) return true;
        }
        return false;
    }
}
