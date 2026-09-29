/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.plugin.frame.Recorder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import segsweep.sweep.ParameterId;

import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression tests for the stage 04 macro, headless and batch fixes. */
public class MacroHardeningTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @After
    public void resetImageJState() {
        WindowManager.setTempCurrentImage(null);
        IJ.resetEscape();
    }

    /** Captures the entry point's reports instead of printing or opening dialogs. */
    static class CapturingSweep extends SegSweep_ {
        final List<String> logs = new ArrayList<String>();
        final List<String> errors = new ArrayList<String>();

        @Override void log(String message) {
            logs.add(message);
        }

        @Override void showError(String text) {
            errors.add(text);
        }

        /**
         * Every error report, wherever it went: the dialog when a display exists,
         * the Log (as "... ERROR: text") when headless, as on CI.
         */
        List<String> reports() {
            List<String> all = new ArrayList<String>(errors);
            for (String line : logs) {
                int at = line.indexOf(" ERROR: ");
                if (at >= 0) all.add(line.substring(at + " ERROR: ".length()));
            }
            return all;
        }
    }

    // ---- allow_oversized honoured by the macro and autosave checks ----

    @Test
    public void recordedRunAnywayOptionsReplayWithoutRefusal() throws Exception {
        File input = saveKneeImage("oversized.tif");
        File out = tmp.newFolder("oversized-out");
        String values = valuesOneTo(150); // 150 cells, above the 100-cell output limit
        String base = "image=[" + slashes(input) + "] sweep=threshold values=[" + values
                + "] pick=none hide_display autosave=[" + slashes(out) + "]";

        CapturingSweep refused = new CapturingSweep();
        assertNull(refused.runFromMacro(base));
        assertEquals(refused.reports().toString(), 1, refused.reports().size());
        assertTrue(refused.reports().get(0), refused.reports().get(0).contains("150 cells"));

        SegSweepMacroOptions recorded = SegSweepMacroOptionsParser.parse(base + " allow_oversized");
        String replay = recorded.toMacroOptions();
        assertTrue(replay, replay.contains("allow_oversized"));
        CapturingSweep replayed = new CapturingSweep();
        SegSweepResult result = replayed.runFromMacro(replay);
        assertNotNull("errors: " + replayed.errors, result);
        assertEquals(150, result.sweepTable().size());
        assertTrue(new File(out, "sweep_results.csv").isFile());
        assertTrue(new File(out, "grid.png").isFile());
    }

    // ---- backslash paths ----

    @Test
    public void windowsBackslashPathsAreNormalisedToForwardSlashes() {
        SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse(
                "image=[C:\\tmp\\x.tif] autosave=[D:\\out\\run 1]");
        assertEquals("C:/tmp/x.tif", options.image());
        assertEquals("D:/out/run 1", options.autosave());
        SegSweepMacroOptions bare = SegSweepMacroOptionsParser.parse("image=C:\\tmp\\x.tif");
        assertEquals("C:/tmp/x.tif", bare.image());
    }

    @Test
    public void batchRegexKeepsItsBackslashes() {
        SegSweepBatchParameters parameters = SegSweepBatch.parseMacroOptions(
                "folder=[C:\\data] regex=[(.*)_(A\\d+)\\.tif] group=2 output=[C:\\out] recursive");
        assertEquals("C:/data", parameters.inputFolder().getPath().replace('\\', '/'));
        assertEquals("(.*)_(A\\d+)\\.tif", parameters.filenameRegex());
        assertEquals(2, parameters.varyingGroup());
        assertTrue(parameters.recursive());
        assertEquals("C:/out", parameters.saveDir().getPath().replace('\\', '/'));
    }

    // ---- unsaved image with hide_display ----

    @Test
    public void hideDisplayOnUnsavedImageReturnsResultAndLogsSkippedAutosave() {
        ImagePlus unsaved = SegSweepAnalysisTest.designedKneeStack(true);
        unsaved.setTitle("unsaved-" + System.nanoTime());
        WindowManager.setTempCurrentImage(unsaved);
        CapturingSweep sweep = new CapturingSweep();

        SegSweepResult result = sweep.runFromMacro(
                "sweep=threshold from=10 to=30 step=10 pick=none hide_display");

        assertNotNull("errors: " + sweep.errors, result);
        assertTrue(sweep.errors.isEmpty());
        assertTrue(sweep.logs.toString(), sweep.logs.toString().contains("autosave skipped"));
    }

    @Test
    public void autosaveNeverFallsBackToTheWorkingDirectory() {
        ImagePlus titledLikeAFile = SegSweepAnalysisTest.designedKneeStack(false);
        titledLikeAFile.setTitle("pom.xml"); // exists in the test working directory
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        assertTrue(SegSweep_.autoSaveDestinationMissing(options, titledLikeAFile));
    }

    // ---- README defaults ----

    @Test
    public void optionsWithoutSweepUseTheReadmeDefaults() {
        SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse("pick=knee");
        assertEquals(ParameterId.THRESHOLD, options.primaryAxis().id());
        assertEquals(10.0d, options.primaryAxis().from(), 0.0d);
        assertEquals(60.0d, options.primaryAxis().to(), 0.0d);
        assertEquals(5.0d, options.primaryAxis().step(), 0.0d);
        assertEquals(11, options.primaryAxis().valueList().size());

        SegSweepMacroOptions partial = SegSweepMacroOptionsParser.parse("sweep=threshold from=20");
        assertEquals(20.0d, partial.primaryAxis().from(), 0.0d);
        assertEquals(60.0d, partial.primaryAxis().to(), 0.0d);

        SegSweepMacroOptions explicit = SegSweepMacroOptionsParser.parse("values=[5,7]");
        assertTrue(explicit.primaryAxis().hasExplicitValues());
    }

    // ---- macro abort ----

    @Test
    public void badOptionsAbortTheCallingMacroWithOneLineMessage() throws Exception {
        File corrupt = tmp.newFile("corrupt.tif");
        Files.write(corrupt.toPath(), "not a tif".getBytes(StandardCharsets.UTF_8));
        String[] cases = {
                "sweep=threshold from=1 to=3 step=1 bogus=1",
                "image=[" + slashes(corrupt) + "] sweep=threshold from=1 to=3 step=1"
        };
        for (final String options : cases) {
            final CapturingSweep sweep = new CapturingSweep();
            final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();
            Thread macro = new Thread(new Runnable() {
                @Override public void run() {
                    Macro.setOptions(options);
                    try {
                        sweep.run("");
                    } catch (Throwable t) {
                        thrown.set(t);
                    } finally {
                        Macro.setOptions(null);
                    }
                }
            }, "Run$_MacroHardeningTest_Macro$");
            macro.start();
            macro.join(60000L);

            List<String> reports = new ArrayList<String>(sweep.errors);
            reports.addAll(sweep.logs);
            assertEquals(options + " -> " + reports, 1, reports.size());
            assertFalse(reports.get(0), reports.get(0).contains("\n"));
            assertNotNull("macro should be aborted for " + options, thrown.get());
            assertEquals(Macro.MACRO_CANCELED, thrown.get().getMessage());
        }
    }

    /** The sweep a macro reaches through {@link #runFromInterpreter}; test-only. */
    static CapturingSweep interpreterSweep;

    /**
     * Called from a macro with {@code call()}: runs a capturing sweep with macro
     * options on the interpreter's own thread, as {@code run(...)} would.
     */
    public static String runFromInterpreter(String options) {
        Thread thread = Thread.currentThread();
        String name = thread.getName();
        thread.setName("Run$_" + name);
        Macro.setOptions(options);
        try {
            interpreterSweep.run("");
        } finally {
            Macro.setOptions(null);
            thread.setName(name);
        }
        return "";
    }

    /**
     * Regression: in Fiji ({@code -macro}, {@code IJ.runMacro}) the macro thread
     * is not named {@code ...Macro$}, so {@code Macro.abort()} only set a flag and
     * the macro carried on after "OBJECT SEGMENTATION SWEEP ERROR".
     */
    @Test
    public void errorStopsAMacroRunOnAnOrdinaryThread() throws Exception {
        final File after = new File(tmp.getRoot(), "after.txt");
        final String macro = "call(\"segsweep.MacroHardeningTest.runFromInterpreter\", "
                + "\"sweep=nonsense from=10 to=60 step=10\");\n"
                + "File.saveString(\"continued\", \"" + slashes(after) + "\");\n";
        interpreterSweep = new CapturingSweep();
        final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();
        Thread fijiMain = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    IJ.runMacro(macro);
                } catch (Throwable t) {
                    thrown.set(t);
                }
            }
        }, "main");
        fijiMain.start();
        fijiMain.join(60000L);

        assertFalse("macro thread finished", fijiMain.isAlive());
        assertNull(String.valueOf(thrown.get()), thrown.get());
        List<String> reports = new ArrayList<String>(interpreterSweep.errors);
        reports.addAll(interpreterSweep.logs);
        assertEquals(reports.toString(), 1, reports.size());
        assertTrue(reports.get(0), reports.get(0).contains("nonsense"));
        assertFalse("the macro must stop after the error", after.exists());
    }

    @Test
    public void numberErrorsNameTheRejectedValue() {
        try {
            SegSweepMacroOptionsParser.parse("sweep=threshold from=abc to=60 step=10");
            fail("from=abc must be refused");
        } catch (IllegalArgumentException expected) {
            assertEquals("from must be a finite number (got \"abc\").", expected.getMessage());
        }
    }

    @Test
    public void outsideAMacroErrorsReturnNullWithoutAborting() {
        CapturingSweep sweep = new CapturingSweep();
        assertNull(sweep.runFromMacro("sweep=threshold bogus=1"));
        assertEquals(sweep.reports().toString(), 1, sweep.reports().size());
    }

    // ---- recorder ----

    @Test
    public void recorderGetsExactlyOneRunLine() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Assume.assumeTrue(Recorder.getInstance() == null);
        Recorder recorder = new Recorder(false);
        try {
            SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse(
                    "sweep=threshold from=10 to=30 step=10");
            String line = "run(\"" + SegSweep_.COMMAND_NAME + "\", \""
                    + options.toMacroOptions() + "\");\n";

            // The bug: recording the call is not enough; ImageJ also records the
            // bare command when run() returns.
            Recorder.setCommand(SegSweep_.COMMAND_NAME);
            Recorder.recordString(line);
            Recorder.saveCommand();
            assertEquals(2, count(recorder.getText(), "run(\"" + SegSweep_.COMMAND_NAME + "\""));

            recorder.getText();
            Recorder.setCommand(SegSweep_.COMMAND_NAME);
            String recorded = SegSweep_.recordMacroCall(options);
            Recorder.saveCommand();
            assertEquals(line, recorded);
            assertEquals("the fixed path adds exactly one line", 3,
                    count(recorder.getText(), "run(\"" + SegSweep_.COMMAND_NAME + "\""));
            assertFalse(recorder.getText().endsWith("run(\"" + SegSweep_.COMMAND_NAME + "\");\n"));
        } finally {
            recorder.close();
        }
    }

    @Test
    public void titleWithBracketsIsNotRecordedButSettingsAreStillSaved() {
        SegSweepMacroOptions options = SegSweepMacroOptions.defaults();
        options.setImage("cells [1].tif");
        assertNotNull(SweepStateStore.serialise(options));
        try {
            options.toMacroOptions();
            fail("brackets cannot be carried by macro options");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("brackets"));
        }
    }

    // ---- image resolution ----

    @Test
    public void openWindowTitleWinsOverAFileWithTheSameName() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        File onDisk = saveKneeImage("same-name.tif");
        ImagePlus window = IJ.createImage("x", "8-bit black", 12, 12, 1);
        window.setTitle(onDisk.getAbsolutePath());
        window.show();
        try {
            SegSweep_.ImageLease lease = new SegSweep_().resolveImage(onDisk.getAbsolutePath());
            assertSame(window, lease.image());
        } finally {
            window.changes = false;
            window.close();
        }
    }

    // ---- escape ----

    @Test
    public void escapeCancelClearsAStalePressThenSeesANewOne() {
        IJ.setKeyDown(KeyEvent.VK_ESCAPE);
        IJ.setKeyUp(KeyEvent.VK_ESCAPE);
        BooleanSupplier cancel = SegSweep_.escapeCancel();
        assertFalse(cancel.getAsBoolean());
        IJ.setKeyDown(KeyEvent.VK_ESCAPE);
        IJ.setKeyUp(KeyEvent.VK_ESCAPE);
        assertTrue(cancel.getAsBoolean());
    }

    // ---- batch ----

    @Test
    public void batchCommandRunsFromMacroOptionsAndListsCorruptFilesQuietly() throws Exception {
        File folder = tmp.newFolder("batch-in");
        saveKneeImageTo(new File(folder, "Exp1-A01_CTX.tif"));
        saveKneeImageTo(new File(folder, "Exp1-A02_CTX.tif"));
        Files.write(new File(folder, "Exp1-A03_CTX.tif").toPath(),
                "not a tif".getBytes(StandardCharsets.UTF_8));
        File output = tmp.newFolder("batch-out");
        final String options = "folder=[" + folder.getAbsolutePath() + "] "
                + "regex=[Exp1-(A\\d+)_CTX\\.tif] group=1 output=[" + output.getAbsolutePath()
                + "] sweep=threshold from=10 to=30 step=10 pick=none";
        final CapturingSweep sweep = new CapturingSweep();
        final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();

        PrintStream original = System.out;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int windowsBefore = java.awt.Window.getWindows().length;
        System.setOut(new PrintStream(stdout, true, "UTF-8"));
        try {
            Thread macro = new Thread(new Runnable() {
                @Override public void run() {
                    Macro.setOptions(options);
                    try {
                        sweep.run("batch");
                    } catch (Throwable t) {
                        thrown.set(t);
                    } finally {
                        Macro.setOptions(null);
                    }
                }
            }, "Run$_MacroHardeningBatch_Macro$");
            macro.start();
            macro.join(120000L);
        } finally {
            System.setOut(original);
        }

        assertNull(String.valueOf(thrown.get()), thrown.get());
        assertTrue("errors: " + sweep.errors, sweep.errors.isEmpty());
        assertTrue(sweep.logs.toString(), sweep.logs.toString().contains("2 processed, 1 failed"));
        File root = new File(output, AutoSaveWriter.OUTPUT_FOLDER);
        String failures = new String(Files.readAllBytes(
                new File(root, "batch_failures.csv").toPath()), StandardCharsets.UTF_8);
        assertTrue(failures, failures.contains("Exp1-A03_CTX.tif"));
        String printed = new String(stdout.toByteArray(), StandardCharsets.UTF_8);
        assertFalse(printed, printed.contains("Unsupported format"));
        assertFalse(printed, printed.contains("Opener"));
        assertFalse(printed, printed.contains("Open TIFF"));
        assertEquals("no windows opened", windowsBefore, java.awt.Window.getWindows().length);
    }

    @Test
    public void batchRefusesOptionsItCannotHonour() {
        for (String option : new String[] {"image=[a.tif]", "autosave=[C:/out]", "hide_display",
                "show_grid", "hide_tables"}) {
            try {
                SegSweepBatch.parseMacroOptions("folder=[C:/data] sweep=threshold " + option);
                fail("expected refusal of " + option);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("cannot include"));
            }
        }
        try {
            SegSweepBatch.parseMacroOptions("regex=[x] sweep=threshold");
            fail("folder is required");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("folder"));
        }
    }

    @Test
    public void batchOptionsRoundTripThroughTheRecordedCall() {
        SegSweepBatchParameters parameters = SegSweepBatch.parseMacroOptions(
                "folder=[C:/data] regex=[(.*)_(A\\d+)] group=2 output=[C:/out] recursive "
                        + "sweep=min_size from=1 to=9 step=4 pick=knee allow_oversized");
        String text = SegSweepBatch.toMacroOptions(parameters);
        SegSweepBatchParameters again = SegSweepBatch.parseMacroOptions(text);
        assertEquals(parameters.filenameRegex(), again.filenameRegex());
        assertEquals(parameters.varyingGroup(), again.varyingGroup());
        assertEquals(parameters.recursive(), again.recursive());
        assertEquals(parameters.analysisOptions().toMacroOptions(),
                again.analysisOptions().toMacroOptions());
        String call = SegSweepBatch.recordedCall(parameters);
        assertTrue(call, call.contains("(A\\\\d+)"));
        assertTrue(call, call.startsWith("run(\"Object Segmentation Sweep Batch\", \""));
    }

    @Test
    public void batchHonoursAllowOversizedAndCancel() throws Exception {
        File folder = tmp.newFolder("batch-limits");
        for (int i = 1; i <= 3; i++) {
            saveKneeImageTo(new File(folder, "Exp1-A0" + i + "_CTX.tif"));
        }
        String analysis = "sweep=threshold values=[" + valuesOneTo(120) + "] pick=none";
        SegSweepBatchResult refused = SegSweepBatchRunner.run(SegSweepBatchParameters.builder(
                folder, "Exp1-(A\\d+)_CTX\\.tif", 1)
                .analysisOptions(SegSweepMacroOptionsParser.parse(analysis))
                .saveDir(tmp.newFolder("limits-out-1")).build());
        assertEquals(0, refused.processedImages());
        assertEquals(3, refused.failedImages());

        SegSweepBatchResult allowed = SegSweepBatchRunner.run(SegSweepBatchParameters.builder(
                folder, "Exp1-(A\\d+)_CTX\\.tif", 1)
                .analysisOptions(SegSweepMacroOptionsParser.parse(analysis + " allow_oversized"))
                .saveDir(tmp.newFolder("limits-out-2")).build());
        assertEquals(3, allowed.processedImages());

        final List<String> seen = new ArrayList<String>();
        File cancelOut = tmp.newFolder("limits-out-3");
        try {
            SegSweepBatchRunner.run(SegSweepBatchParameters.builder(
                    folder, "Exp1-(A\\d+)_CTX\\.tif", 1)
                    .analysisOptions(SegSweepMacroOptionsParser.parse(
                            "sweep=threshold from=10 to=30 step=10 pick=none"))
                    .saveDir(cancelOut).build(),
                    new SegSweepBatchRunner.ProgressListener() {
                        @Override public void imageStarting(int index, int total, File file) {
                            seen.add(index + "/" + total + " " + file.getName());
                        }
                    },
                    new BooleanSupplier() {
                        @Override public boolean getAsBoolean() {
                            return seen.size() >= 2;
                        }
                    });
            fail("expected cancel");
        } catch (CancellationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("of 3"));
        }
        assertEquals("1/3 Exp1-A01_CTX.tif", seen.get(0));
        String readme = new String(Files.readAllBytes(new File(new File(cancelOut,
                AutoSaveWriter.OUTPUT_FOLDER), "README.txt").toPath()), StandardCharsets.UTF_8);
        assertTrue(readme, readme.contains("Processed images: 1"));
    }

    // ---- helpers ----

    private File saveKneeImage(String name) throws Exception {
        File file = new File(tmp.getRoot(), name);
        saveKneeImageTo(file);
        return file;
    }

    private static void saveKneeImageTo(File file) {
        ImagePlus image = SegSweepAnalysisTest.designedKneeStack(true);
        assertTrue(new FileSaver(image).saveAsTiff(file.getAbsolutePath()));
        image.close();
    }

    private static String valuesOneTo(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            if (i > 1) sb.append(',');
            sb.append(i);
        }
        return sb.toString();
    }

    private static String slashes(File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }

    private static int count(String text, String needle) {
        int count = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            count++;
            at = text.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
