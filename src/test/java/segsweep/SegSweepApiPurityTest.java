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
import org.junit.Test;
import segsweep.sweep.ParameterId;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SegSweepApiPurityTest {

    @Test
    public void publicRunOpensNoDialogNoWindowAndWritesNoFile() throws IOException {
        closeOpenImages();
        assertNull(WindowManager.getCurrentImage());
        int beforeWindows = openImageCount();
        File watched = Files.createTempDirectory("segsweep-api-purity").toFile();
        int beforeFiles = countFiles(watched);
        try {
            ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
            SegSweep.run(SegSweepParameters.builder()
                    .image(image)
                    .axis(ParameterId.THRESHOLD, 10, 60, 10)
                    .pickCriterion(SegSweepParameters.PickCriterion.KNEE)
                    .build());

            assertEquals(beforeWindows, openImageCount());
            assertNull(WindowManager.getCurrentImage());
            assertEquals(beforeFiles, countFiles(watched));
        } finally {
            deleteRecursively(watched);
            closeOpenImages();
        }
    }

    private static int openImageCount() {
        int[] ids = WindowManager.getIDList();
        return ids == null ? 0 : ids.length;
    }

    private static void closeOpenImages() {
        int[] ids = WindowManager.getIDList();
        if (ids == null) return;
        for (int i = 0; i < ids.length; i++) {
            ImagePlus image = WindowManager.getImage(ids[i]);
            if (image != null) {
                image.changes = false;
                image.close();
            }
        }
    }

    private static int countFiles(File root) {
        File[] files = root.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (int i = 0; i < files.length; i++) {
            count++;
            if (files[i].isDirectory()) {
                count += countFiles(files[i]);
            }
        }
        return count;
    }

    private static void deleteRecursively(File root) {
        if (root == null || !root.exists()) return;
        File[] files = root.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                deleteRecursively(files[i]);
            }
        }
        root.delete();
    }
}
