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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import segsweep.sweep.ParameterId;
import segsweep.sweep.SweepProvenance;
import segsweep.token.SegmentationMethod;
import segsweep.token.SegmentationTokenParser;
import segsweep.tree.LazyLabelMap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
        assertTrue(new File(output, "README.txt").isFile());
        assertEquals(1, picked.materializationCount());

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
}
