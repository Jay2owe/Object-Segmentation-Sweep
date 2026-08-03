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
import ij.WindowManager;
import ij.io.FileSaver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;

import java.awt.Rectangle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SegSweepBatchTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void captureGroupPreviewUsesExpectedGroupsBeforeRun() throws Exception {
        saveImage(new File(tmp.getRoot(), "Exp1-A01_LH_CTX.tif"));
        saveImage(new File(tmp.getRoot(), "Exp1-A02_LH_CTX.tif"));
        saveImage(new File(tmp.getRoot(), "Exp1-A03_RH_CTX.tif"));
        Pattern pattern = Pattern.compile("Exp1-(A\\d+)_(.+)_CTX\\.tif");

        Map<String, Map<String, List<File>>> groups =
                SegSweepBatch.findGroupsRecursive(tmp.getRoot(), pattern, 1, false);
        String preview = SegSweepBatch.previewNestedGroups(groups);

        assertTrue(groups.containsKey(""));
        assertTrue(groups.get("").containsKey("Exp1-*_LH_CTX.tif"));
        assertEquals(2, groups.get("").get("Exp1-*_LH_CTX.tif").size());
        assertTrue(preview.contains("Preview") || preview.contains("3 files"));
        assertTrue(preview.contains("Exp1-A01_LH_CTX.tif"));
        assertTrue(preview.contains("Exp1-*_RH_CTX.tif"));
    }

    @Test
    public void recursiveScanFindsSubfoldersAndNonRecursiveDoesNot() throws Exception {
        File sub = tmp.newFolder("sub");
        saveImage(new File(sub, "Exp1-A01_LH_CTX.tif"));
        Pattern pattern = Pattern.compile("Exp1-(A\\d+)_(.+)_CTX\\.tif");

        Map<String, Map<String, List<File>>> recursive =
                SegSweepBatch.findGroupsRecursive(tmp.getRoot(), pattern, 1, true);
        Map<String, Map<String, List<File>>> nonRecursive =
                SegSweepBatch.findGroupsRecursive(tmp.getRoot(), pattern, 1, false);

        assertTrue(recursive.containsKey("sub"));
        assertTrue(nonRecursive.isEmpty());
    }

    @Test
    public void corruptImageDoesNotAbortFolderAndFailureCsvNamesIt() throws Exception {
        for (int i = 1; i <= 5; i++) {
            File file = new File(tmp.getRoot(), "Exp1-A0" + i + "_LH_CTX.tif");
            if (i == 3) {
                Files.write(file.toPath(), "not a tif".getBytes(StandardCharsets.UTF_8));
            } else {
                saveImage(file);
            }
        }

        SegSweepBatchResult result = SegSweepBatchRunner.run(SegSweepBatchParameters.builder(
                tmp.getRoot(), "Exp1-(A\\d+)_(.+)_CTX\\.tif", 1)
                .analysisOptions(kneeOptions(CropSpec.full()))
                .hideDisplay(true)
                .build());

        assertEquals(5, result.totalImages());
        assertEquals(4, result.processedImages());
        assertEquals(1, result.failedImages());
        assertTrue(result.failures().get(0).image().getName().contains("A03"));
        File failures = new File(result.outputDirectory(), "batch_failures.csv");
        assertTrue(failures.isFile());
        assertTrue(text(failures).contains("Exp1-A03_LH_CTX.tif"));
    }

    @Test
    public void hideDisplayBatchOpensNoImageWindow() throws Exception {
        saveImage(new File(tmp.getRoot(), "Exp1-A01_LH_CTX.tif"));
        int before = openWindowCount();

        SegSweepBatchRunner.run(SegSweepBatchParameters.builder(
                tmp.getRoot(), "Exp1-(A\\d+)_(.+)_CTX\\.tif", 1)
                .analysisOptions(kneeOptions(CropSpec.full()))
                .hideDisplay(true)
                .autoSave(false)
                .build());

        assertEquals(before, openWindowCount());
    }

    @Test
    public void differentCropsAreIncomparableAndNoMeanOrMedianIsWritten() throws Exception {
        SegSweepResult full = runResult(CropSpec.full());
        SegSweepResult crop = runResult(CropSpec.custom(new Rectangle(0, 0, 20, 8)));
        List<SegSweepBatchResult.ImageResult> rows =
                new ArrayList<SegSweepBatchResult.ImageResult>();
        rows.add(new SegSweepBatchResult.ImageResult(new File("full.tif"), "", "all", full, null));
        rows.add(new SegSweepBatchResult.ImageResult(new File("crop.tif"), "", "all", crop, null));

        SegSweepBatchResult batch = new SegSweepBatchResult(2, 2, 0, null,
                rows, new ArrayList<SegSweepBatchResult.BatchFailure>());
        String picks = batch.batchPicksTable().toString().toLowerCase(java.util.Locale.ROOT);

        assertFalse(batch.allComparable());
        assertTrue(batch.incomparableReasons().get(0).toLowerCase(java.util.Locale.ROOT)
                .contains("crop"));
        assertFalse(picks.contains("mean"));
        assertFalse(picks.contains("median"));
    }

    @Test
    public void identicalSettingsAndProvenanceAreComparable() {
        SegSweepResult one = runResult(CropSpec.full());
        SegSweepResult two = runResult(CropSpec.full());
        List<SegSweepBatchResult.ImageResult> rows =
                new ArrayList<SegSweepBatchResult.ImageResult>();
        rows.add(new SegSweepBatchResult.ImageResult(new File("one.tif"), "", "all", one, null));
        rows.add(new SegSweepBatchResult.ImageResult(new File("two.tif"), "", "all", two, null));

        SegSweepBatchResult batch = new SegSweepBatchResult(2, 2, 0, null,
                rows, new ArrayList<SegSweepBatchResult.BatchFailure>());

        assertTrue(batch.allComparable());
        assertTrue(batch.batchPicksTable().toString().contains("Comparable"));
    }

    private static SegSweepResult runResult(CropSpec crop) {
        return SegSweep.run(SegSweepParameters.builder()
                .image(SegSweepAnalysisTest.designedKneeStack(true))
                .axis(ParameterId.THRESHOLD, 10, 60, 10)
                .crop(crop)
                .pickCriterion(SegSweepParameters.PickCriterion.KNEE)
                .build());
    }

    private static SegSweepMacroOptions kneeOptions(CropSpec crop) {
        SegSweepMacroOptions options = new SegSweepMacroOptions();
        options.setPrimaryAxis(SegSweepMacroOptions.AxisSpec.range(
                ParameterId.THRESHOLD, 10, 60, 10));
        options.setPickCriterion(SegSweepParameters.PickCriterion.KNEE);
        options.setCrop(crop);
        options.setHideDisplay(true);
        return options;
    }

    private static void saveImage(File file) {
        ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
        assertTrue(new FileSaver(image).saveAsTiff(file.getAbsolutePath()));
        image.close();
    }

    private static int openWindowCount() {
        int[] ids = WindowManager.getIDList();
        return ids == null ? 0 : ids.length;
    }

    private static String text(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
