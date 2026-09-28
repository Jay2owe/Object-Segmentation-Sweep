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
import ij.text.TextWindow;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import segsweep.ui.grid.VariationGridWindow;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the macro/headless entry point {@link SegSweep_#runFromMacro(String)}:
 * the happy path through a file-backed image with autosave, and the error
 * paths that must return {@code null} instead of throwing.
 */
public class SegSweepEntryTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void emptyOptionsAreRefusedWithoutThrowing() {
        assertNull(new SegSweep_().runFromMacro(""));
        assertNull(new SegSweep_().runFromMacro(null));
    }

    @Test
    public void unknownOptionIsRefusedWithoutThrowing() {
        assertNull(new SegSweep_().runFromMacro("sweep=threshold from=1 to=3 step=1 bogus=1"));
    }

    @Test
    public void nonNumericRangeIsRefusedWithoutThrowing() {
        assertNull(new SegSweep_().runFromMacro("sweep=threshold from=abc to=3 step=1"));
    }

    @Test
    public void missingImageFileIsRefusedWithoutThrowing() {
        String missing = new File(tmp.getRoot(), "does-not-exist.tif").getAbsolutePath()
                .replace('\\', '/');
        assertNull(new SegSweep_().runFromMacro("image=[" + missing + "] sweep=threshold "
                + "from=10 to=30 step=10 hide_display"));
    }

    @Test
    public void fileBackedImageRunsAndAutosaves() throws Exception {
        File input = saveKneeImage("entry-input.tif");
        File out = tmp.newFolder("out");
        SegSweepResult result = new SegSweep_().runFromMacro("image=["
                + slashes(input) + "] sweep=threshold from=10 to=60 step=10 pick=knee "
                + "hide_display autosave=[" + slashes(out) + "]");

        assertNotNull(result);
        assertEquals(6, result.sweepTable().size());
        File[] written = out.listFiles();
        assertNotNull(written);
        assertTrue("autosave folder should be created", written.length > 0);
    }

    @Test
    public void runWithArgumentStringUsesItAsMacroOptions() throws Exception {
        File input = saveKneeImage("entry-arg.tif");
        File out = tmp.newFolder("out-arg");
        new SegSweep_().run("image=[" + slashes(input) + "] sweep=threshold from=10 to=30 "
                + "step=10 pick=none hide_display autosave=[" + slashes(out) + "]");
        File[] written = out.listFiles();
        assertNotNull(written);
        assertTrue(written.length > 0);
    }

    @Test
    public void timeSeriesIsRefusedWithoutThrowing() throws Exception {
        ImagePlus series = ij.IJ.createImage("series", "8-bit black", 16, 16, 1, 1, 3);
        File file = new File(tmp.getRoot(), "series.tif");
        assertTrue(new FileSaver(series).saveAsTiff(file.getAbsolutePath()));
        assertNull(new SegSweep_().runFromMacro("image=[" + slashes(file)
                + "] sweep=threshold from=10 to=30 step=10 hide_display"));
    }

    @Test
    public void displayedRunShowsGridAndSavesTheRenderedGrid() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        File input = saveKneeImage("entry-display.tif");
        File out = tmp.newFolder("out-display");
        try {
            SegSweepResult result = new SegSweep_().runFromMacro("image=[" + slashes(input)
                    + "] sweep=threshold from=10 to=60 step=10 pick=knee show_tables "
                    + "show_grid autosave=[" + slashes(out) + "]");
            assertNotNull(result);
            assertTrue("a grid window should be open", countGridWindows() > 0);
            assertTrue("the rendered grid should be autosaved",
                    containsFileNamed(out, "grid.png"));
        } finally {
            closeDisplayWindows();
        }
    }

    @Test
    public void displayedRunWithoutGridSavesImmediately() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        File input = saveKneeImage("entry-nogrid.tif");
        File out = tmp.newFolder("out-nogrid");
        try {
            SegSweepResult result = new SegSweep_().runFromMacro("image=[" + slashes(input)
                    + "] sweep=threshold from=10 to=60 step=10 pick=knee show_tables "
                    + "hide_grid autosave=[" + slashes(out) + "]");
            assertNotNull(result);
            assertEquals(0, countGridWindows());
            assertTrue(containsFileNamed(out, "sweep_results.csv"));
        } finally {
            closeDisplayWindows();
        }
    }

    @Test
    public void autosaveWithoutFileLocationOrFolderFails() throws Exception {
        ImagePlus unsaved = SegSweepAnalysisTest.designedKneeStack(true);
        unsaved.setTitle("never-saved-" + System.nanoTime());
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setHideDisplay(true);
        SegSweepResult result = SegSweep.run(options.toParameters(unsaved));
        try {
            new SegSweep_().autoSaveIfRequested(result, options, unsaved);
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("no file location"));
            return;
        }
        throw new AssertionError("Expected a missing-location failure.");
    }

    @Test
    public void manualPickTokenRecordsTheManualCriterion() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        SegSweepResult result = SegSweep.run(options.toParameters(
                SegSweepAnalysisTest.designedKneeStack(true)));
        String token = SegSweep_.settingsTokenForSelected(result,
                result.results().get(1).combo(), java.time.Instant.EPOCH);
        assertTrue(token.contains("# Written 1970-01-01T00:00:00Z"));
        assertTrue(token.contains("criterion\tmanual"));
    }

    @Test
    public void autosaveTimingFollowsDisplayChoices() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        assertFalse(SegSweep_.shouldAutoSaveImmediately(options, false));
        assertTrue(SegSweep_.shouldAutoSaveImmediately(options, true));
        assertTrue(SegSweep_.shouldAutoSaveRenderedGrid(options));
        options.setShowGrid(false);
        assertTrue(SegSweep_.shouldAutoSaveImmediately(options, false));
        assertFalse(SegSweep_.shouldAutoSaveRenderedGrid(options));
        options.setShowGrid(true);
        options.setHideDisplay(true);
        assertTrue(SegSweep_.shouldAutoSaveImmediately(options, false));
        assertFalse(SegSweep_.shouldAutoSaveImmediately(null, true));
    }

    private static int countGridWindows() {
        int count = 0;
        for (Window window : Window.getWindows()) {
            if (window instanceof VariationGridWindow && window.isDisplayable()) count++;
        }
        return count;
    }

    private static void closeDisplayWindows() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                for (Window window : Window.getWindows()) {
                    if (window instanceof VariationGridWindow || window instanceof TextWindow) {
                        window.dispose();
                    }
                }
            }
        });
    }

    private static boolean containsFileNamed(File root, String name) {
        File[] children = root.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile() && child.getName().equals(name)) return true;
            if (child.isDirectory() && containsFileNamed(child, name)) return true;
        }
        return false;
    }

    private File saveKneeImage(String name) {
        ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
        File file = new File(tmp.getRoot(), name);
        assertTrue(new FileSaver(image).saveAsTiff(file.getAbsolutePath()));
        image.close();
        return file;
    }

    private static String slashes(File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }
}
