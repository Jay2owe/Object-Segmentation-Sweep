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
import ij.gui.GenericDialog;
import ij.io.FileInfo;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ResourceGuard;
import segsweep.sweep.SourceImageView;
import segsweep.sweep.SweepProgress;
import segsweep.sweep.VariationResult;
import segsweep.sweep.analysis.PickResult;
import segsweep.token.SettingsTokenWriter;
import segsweep.ui.SegSweepDialog;
import segsweep.ui.grid.VariationGridWindow;
import segsweep.ui.render.PreviewDisplaySettings;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class SegSweep_ implements PlugIn {
    public static final String COMMAND_NAME = "Object Segmentation Sweep";

    @Override
    public void run(String arg) {
        if (hasText(arg) && "batch".equalsIgnoreCase(arg.trim())) {
            String batchOptions = Macro.getOptions();
            if (hasText(batchOptions) || GraphicsEnvironment.isHeadless()) {
                runBatchFromMacro(batchOptions);
                return;
            }
            // The batch dialog is modeless and records its own call on Run;
            // stop ImageJ also recording a bare run("... Batch") when run() returns.
            Recorder.disableCommandRecording();
            SegSweepBatch.showBatchDialog();
            return;
        }
        String macroOptions = Macro.getOptions();
        if (!hasText(macroOptions) && hasText(arg)) {
            macroOptions = arg;
        }
        if (hasText(macroOptions) || GraphicsEnvironment.isHeadless()) {
            runFromMacro(macroOptions);
            return;
        }
        runInteractive();
    }

    /**
     * The batch command's macro/headless path: parses folder, regex, group,
     * output and the per-image analysis options, runs the batch on the calling
     * thread with per-file status, and aborts the macro on failure.
     */
    SegSweepBatchResult runBatchFromMacro(String optionsText) {
        if (!hasText(optionsText)) {
            reportError(SegSweepBatch.COMMAND_NAME
                    + " macro/headless execution requires explicit macro options.");
            return null;
        }
        try {
            SegSweepBatchParameters parameters = SegSweepBatch.parseMacroOptions(optionsText);
            SegSweepBatchResult result = SegSweepBatchRunner.run(parameters,
                    batchStatus(), escapeCancel());
            log(SegSweepBatch.completionMessage(result));
            return result;
        } catch (Exception ex) {
            reportError(ex.getMessage());
            return null;
        } finally {
            IJ.showProgress(1.0d);
        }
    }

    SegSweepResult runFromMacro(String optionsText) {
        if (!hasText(optionsText)) {
            reportError("Object Segmentation Sweep macro/headless execution requires explicit macro options.");
            return null;
        }
        ImageLease lease = null;
        boolean retainedByGrid = false;
        try {
            SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse(optionsText);
            lease = resolveImage(options.image());
            ImagePlus image = lease == null ? null : lease.image();
            if (image == null) {
                throw new IllegalArgumentException("No source image was found. Provide image=[path or title] or open an image.");
            }
            ResourceGuard.Feasibility outputFeasibility = shouldAutoSaveImmediately(options)
                    ? ResourceGuard.assessMontageFeasibility(displayWindow(options), image,
                            options.limits())
                    : ResourceGuard.assessFeasibility(displayWindow(options), image,
                            options.limits());
            if (!outputFeasibility.isOk()) {
                throw new SweepRefusedException(outputFeasibility.getMessage());
            }
            BooleanSupplier cancel = escapeCancel();
            SegSweepResult result = SegSweepAnalysis.run(options.toParameters(image),
                    statusProgress(), cancel);
            if (shouldAutoSaveImmediately(options)) {
                autoSaveOrSkip(result, options, image, null, cancel);
            }
            retainedByGrid = showMacroResult(result, options, lease);
            return result;
        } catch (Exception ex) {
            reportError(ex instanceof CancellationException
                    ? "Sweep cancelled." : ex.getMessage());
            return null;
        } finally {
            IJ.showProgress(1.0d);
            if (lease != null && !retainedByGrid) {
                lease.close();
            }
        }
    }

    /** Escape cancels a macro, headless or grid-off run; cleared first so a stale press does not. */
    static BooleanSupplier escapeCancel() {
        IJ.resetEscape();
        return new BooleanSupplier() {
            @Override public boolean getAsBoolean() {
                return IJ.escapePressed();
            }
        };
    }

    /** Progress bar and status line for runs that have no grid to show progress in. */
    static Consumer<SweepProgress> statusProgress() {
        return new Consumer<SweepProgress>() {
            @Override public void accept(SweepProgress progress) {
                if (progress == null) return;
                int total = Math.max(1, progress.total());
                IJ.showProgress(Math.min(progress.completed(), total - 1), total);
                IJ.showStatus(COMMAND_NAME + ": " + progress.completed() + "/"
                        + progress.total() + " combinations (Esc to cancel)");
            }
        };
    }

    /** Per-file status for batch runs: {@code n/N <file>}. */
    static SegSweepBatchRunner.ProgressListener batchStatus() {
        return new SegSweepBatchRunner.ProgressListener() {
            @Override public void imageStarting(int index, int total, File file) {
                IJ.showProgress(index - 1, Math.max(1, total));
                IJ.showStatus(SegSweepBatch.COMMAND_NAME + ": " + index + "/" + total + " "
                        + (file == null ? "" : file.getName()) + " (Esc to cancel)");
            }
        };
    }

    private void runInteractive() {
        // This command records its own full call (recordMacroCall); without this
        // ImageJ would also record a bare run("Object Segmentation Sweep") when
        // run() returns, and on replay that line would open the dialog again.
        Recorder.disableCommandRecording();
        ImagePlus active = WindowManager.getCurrentImage();
        SegSweepDialog dialog = new SegSweepDialog(active);
        SegSweepMacroOptions options = dialog.showDialog();
        if (options == null) {
            return;
        }
        final ImageLease lease = resolveImage(options.image());
        ImagePlus image = lease == null ? null : lease.image();
        if (image == null) {
            IJ.error(COMMAND_NAME, "No source image was found.");
            return;
        }
        recordMacroCall(options);
        final SegSweepMacroOptions runOptions = options;
        final ImagePlus runImage = image;
        if (runOptions.showGrid()) {
            runInteractiveWithProgressGrid(runOptions, lease);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                boolean retainedByGrid = false;
                try {
                    IJ.showStatus(COMMAND_NAME + ": running sweep...");
                    BooleanSupplier cancel = escapeCancel();
                    SegSweepResult result = SegSweepAnalysis.run(
                            runOptions.toParameters(runImage), statusProgress(), cancel);
                    if (shouldAutoSaveImmediately(runOptions)) {
                        autoSaveOrSkip(result, runOptions, runImage, null, cancel);
                    }
                    retainedByGrid = showMacroResult(result, runOptions, lease);
                    IJ.showStatus(COMMAND_NAME + ": done.");
                } catch (CancellationException ex) {
                    IJ.showStatus(COMMAND_NAME + ": cancelled.");
                } catch (Exception ex) {
                    reportError(ex.getMessage());
                } finally {
                    IJ.showProgress(1.0d);
                    if (!retainedByGrid) {
                        lease.close();
                    }
                }
            }
        }, "SegSweep-Analysis").start();
    }

    private void runInteractiveWithProgressGrid(final SegSweepMacroOptions options,
                                                final ImageLease lease) {
        final ImagePlus image = lease.image();
        final ImagePlus progressSource = SourceImageView.selectedChannelAndCrop(
                image, options.channel(), options.crop());
        final VariationGridWindow progressGrid = new VariationGridWindow(
                null, COMMAND_NAME, displayWindow(options), progressSource);
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean finished = new AtomicBoolean();
        progressGrid.attachCancelActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                cancelled.set(true);
                progressGrid.setCancelEnabled(false);
                progressGrid.setActionStatus("Cancelling sweep...");
            }
        });
        progressGrid.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (!finished.get()) cancelled.set(true);
                progressSource.changes = false;
                progressSource.close();
                progressSource.flush();
            }
        });
        progressGrid.setVisible(true);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    IJ.showStatus(COMMAND_NAME + ": running sweep...");
                    SegSweepResult result = SegSweepAnalysis.run(options.toParameters(image),
                            new Consumer<SweepProgress>() {
                                @Override public void accept(final SweepProgress progress) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override public void run() {
                                            if (!cancelled.get()) progressGrid.applyProgress(progress);
                                        }
                                    });
                                }
                            }, new BooleanSupplier() {
                                @Override public boolean getAsBoolean() {
                                    return cancelled.get();
                                }
                            }, new Consumer<VariationResult>() {
                                @Override public void accept(final VariationResult result) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override public void run() {
                                            if (!cancelled.get()) progressGrid.setResult(result);
                                        }
                                    });
                                }
                            });
                    if (cancelled.get()) throw new CancellationException("Sweep cancelled.");
                    final SegSweepResult completed = result;
                    finished.set(true);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            progressGrid.setCancelEnabled(false);
                            progressGrid.dispose();
                            boolean retainedByGrid = false;
                            try {
                                if (shouldAutoSaveImmediately(options)) {
                                    autoSaveOrSkip(completed, options, image, null, null);
                                }
                                retainedByGrid = showMacroResult(completed, options, lease);
                                IJ.showStatus(COMMAND_NAME + ": done.");
                            } catch (Exception ex) {
                                reportError(ex.getMessage());
                            } finally {
                                if (!retainedByGrid) {
                                    lease.close();
                                }
                            }
                        }
                    });
                } catch (CancellationException ex) {
                    lease.close();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            progressGrid.setActionStatus("Sweep cancelled.");
                            progressGrid.setCancelEnabled(false);
                        }
                    });
                    IJ.showStatus(COMMAND_NAME + ": cancelled.");
                } catch (final Exception ex) {
                    lease.close();
                    finished.set(true);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() {
                            progressGrid.dispose();
                            reportError(ex.getMessage());
                        }
                    });
                }
            }
        }, "SegSweep-Analysis").start();
    }

    private boolean showMacroResult(final SegSweepResult result,
                                    SegSweepMacroOptions options,
                                    final ImageLease lease) {
        final ImagePlus image = lease.image();
        boolean display = !GraphicsEnvironment.isHeadless()
                && options != null && !options.hideDisplay();
        if (!display) {
            logWarnings(result);
            return false;
        }
        if (options.showTables() && result.sweepTable() != null) {
            result.sweepTable().show("Sweep Results");
        }
        if (options.showTables() && result.pickTable() != null && result.pickTable().size() > 0) {
            result.pickTable().show("Sweep Pick");
        }
        if (!options.showGrid()) {
            logWarnings(result);
            return false;
        }
        final ImagePlus displaySource = SourceImageView.selectedChannelAndCrop(
                image, result.parameters().channel(), result.parameters().crop());
        final VariationGridWindow grid = new VariationGridWindow(null, COMMAND_NAME,
                displayWindow(result), displaySource);
        grid.setCancelEnabled(false);
        grid.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                displaySource.changes = false;
                displaySource.close();
                displaySource.flush();
                lease.close();
            }
        });
        List<VariationResult> results = result.results();
        for (int i = 0; i < results.size(); i++) {
            grid.setResult(results.get(i));
        }
        grid.setCompletedCount(results.size(), results.size(), 0);
        grid.setPickResult(result.pick());
        String warnings = SegSweepDialog.warningsStatusText(result);
        if (warnings.length() > 0) {
            grid.setActionStatus(warnings);
        }
        final PreviewDisplaySettings[] displaySettings =
                new PreviewDisplaySettings[] { PreviewDisplaySettings.of(
                        displaySource.getDisplayRangeMin(), displaySource.getDisplayRangeMax(),
                        PreviewDisplaySettings.LutMode.CHANNEL, "Grays") };
        grid.attachObjectOverlayActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                boolean enabled = grid.isObjectOverlaySelected();
                grid.setObjectOverlaySourceEnabled(enabled);
                grid.setObjectOverlayEnabledForAll(enabled);
            }
        });
        grid.attachObjectOverlaySourceActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                boolean raw = grid.isObjectOverlaySourceRaw();
                grid.setObjectOverlaySourceRawForAll(raw);
            }
        });
        grid.attachLutToggleActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                PreviewDisplaySettings current = displaySettings[0];
                PreviewDisplaySettings.LutMode mode = current.getLutMode()
                        == PreviewDisplaySettings.LutMode.GREY
                        ? PreviewDisplaySettings.LutMode.CHANNEL
                        : PreviewDisplaySettings.LutMode.GREY;
                displaySettings[0] = PreviewDisplaySettings.of(
                        current.getDisplayMin(), current.getDisplayMax(), mode,
                        current.getChannelLutName());
                applyDisplaySettings(grid, displaySettings[0]);
                grid.setLutToggleText(mode == PreviewDisplaySettings.LutMode.GREY
                                ? "Channel LUT" : "Grey LUT",
                        "Toggle the source LUT for all tiles.");
            }
        });
        grid.attachBrightnessActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                PreviewDisplaySettings current = displaySettings[0];
                GenericDialog dialog = new GenericDialog("Sweep brightness/contrast");
                dialog.addNumericField("Minimum", current.getDisplayMin(), 3);
                dialog.addNumericField("Maximum", current.getDisplayMax(), 3);
                dialog.showDialog();
                if (dialog.wasCanceled()) return;
                double min = dialog.getNextNumber();
                double max = dialog.getNextNumber();
                if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) {
                    grid.setActionStatus("Brightness range requires a finite maximum above minimum.");
                    return;
                }
                displaySettings[0] = PreviewDisplaySettings.of(min, max,
                        current.getLutMode(), current.getChannelLutName());
                applyDisplaySettings(grid, displaySettings[0]);
            }
        });
        grid.attachPickSelectedActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                ParameterCombo selected = grid.selectedCombo();
                if (selected == null) {
                    grid.setActionStatus("Select a completed cell before picking it.");
                    return;
                }
                String token = settingsTokenForSelected(result, selected);
                SegSweepResult chosenResult = result.withPickedSelection(selected, token);
                IJ.log(COMMAND_NAME + ": picked " + selected);
                IJ.log(token);
                if (chosenResult.pickedLabelMap() != null) {
                    ImagePlus labels = chosenResult.pickedLabelMap().get();
                    labels.setTitle(COMMAND_NAME + " - picked labels");
                    labels.show();
                }
                if (autoSaveDestinationMissing(options, image)) {
                    grid.setActionStatus("Picked " + selected + "; not saved: "
                            + NO_FILE_LOCATION_HINT);
                    return;
                }
                try {
                    BufferedImage reviewedGrid = grid.renderGridSnapshot();
                    File output = autoSaveIfRequested(
                            chosenResult, options, image, reviewedGrid);
                    IJ.log(COMMAND_NAME + ": saved manual pick to " + output.getAbsolutePath());
                } catch (Exception ex) {
                    IJ.error(COMMAND_NAME, "Could not save manual pick: " + ex.getMessage());
                }
            }
        });
        grid.setVisible(true);
        if (shouldAutoSaveRenderedGrid(options)) {
            if (autoSaveDestinationMissing(options, image)) {
                // An unsaved image is normal in interactive use; say so once in
                // the grid rather than raising a modal error after every run.
                log(COMMAND_NAME + ": autosave skipped: " + NO_FILE_LOCATION_HINT);
                grid.setActionStatus("Not saved: " + NO_FILE_LOCATION_HINT);
            } else {
                try {
                    File output = autoSaveIfRequested(
                            result, options, image, grid.renderGridSnapshot());
                    log(COMMAND_NAME + ": saved initial reviewed grid to "
                            + output.getAbsolutePath());
                } catch (Exception ex) {
                    log(COMMAND_NAME + ": could not save sweep: "
                            + QuietImageOpener.oneLine(ex.getMessage()));
                    grid.setActionStatus("Could not save sweep: "
                            + QuietImageOpener.oneLine(ex.getMessage()));
                }
            }
        }
        return true;
    }

    private static ParameterSweep displayWindow(SegSweepResult result) {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>(result.parameters().axes());
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values,
                result.parameters().crop(), "C" + result.parameters().channel());
    }

    private static ParameterSweep displayWindow(SegSweepMacroOptions options) {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(options.primaryAxis().id(), options.primaryAxis().valueList());
        if (options.secondaryAxis() != null) {
            values.put(options.secondaryAxis().id(), options.secondaryAxis().valueList());
        }
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values,
                options.crop(), "C" + options.channel());
    }

    static String settingsTokenForSelected(SegSweepResult result, ParameterCombo selected) {
        return settingsTokenForSelected(result, selected, Instant.now());
    }

    static String settingsTokenForSelected(SegSweepResult result,
                                           ParameterCombo selected,
                                           Instant writtenAt) {
        PickResult automaticPick = result.pick();
        SettingsTokenWriter.PickSummary summary = SegSweepAnalysis.pickSummary(
                "manual", automaticPick,
                automaticPick == null
                        ? "manual grid pick"
                        : "manual grid pick; automatic criteria agree="
                        + automaticPick.criteriaAgree());
        return SettingsTokenWriter.write(
                SegSweepAnalysis.methodFor(result.parameters(), selected),
                result.provenance(), summary, writtenAt,
                imageIdentity(result.parameters().image()), result.parameters().channel());
    }

    static boolean shouldAutoSaveImmediately(SegSweepMacroOptions options) {
        return shouldAutoSaveImmediately(options, GraphicsEnvironment.isHeadless());
    }

    static boolean shouldAutoSaveImmediately(SegSweepMacroOptions options, boolean headless) {
        return options != null && (headless || options.hideDisplay() || !options.showGrid());
    }

    static boolean shouldAutoSaveRenderedGrid(SegSweepMacroOptions options) {
        return options != null && options.showGrid() && !options.hideDisplay();
    }

    private static void applyDisplaySettings(VariationGridWindow grid,
                                             PreviewDisplaySettings settings) {
        grid.setObjectDisplaySettingsForAll(settings);
    }

    private static String imageIdentity(ImagePlus image) {
        if (image == null) return "";
        FileInfo info = image.getOriginalFileInfo();
        if (info != null && hasText(info.fileName)) return info.fileName.trim();
        return hasText(image.getTitle()) ? image.getTitle().trim() : "";
    }

    File autoSaveIfRequested(SegSweepResult result,
                             SegSweepMacroOptions options,
                             ImagePlus image) throws java.io.IOException {
        return autoSaveIfRequested(result, options, image, null);
    }

    File autoSaveIfRequested(SegSweepResult result,
                             SegSweepMacroOptions options,
                             ImagePlus image,
                             BufferedImage reviewedGrid) throws java.io.IOException {
        if (result == null || options == null) {
            return null;
        }
        return autoSaveIfRequested(result, options, image, reviewedGrid, null);
    }

    File autoSaveIfRequested(SegSweepResult result,
                             SegSweepMacroOptions options,
                             ImagePlus image,
                             BufferedImage reviewedGrid,
                             BooleanSupplier cancel) throws java.io.IOException {
        if (result == null || options == null) {
            return null;
        }
        File inputFile = inputFileFor(options, image);
        if (hasText(options.autosave())) {
            return AutoSaveWriter.writeTo(
                    new File(options.autosave()), inputFile, result, reviewedGrid, cancel);
        }
        File existingInput = existingInputFileFor(options, image);
        if (existingInput == null) {
            throw new java.io.IOException(
                    "The source image has no file location. Choose an explicit Save to folder.");
        }
        return AutoSaveWriter.write(existingInput, result, reviewedGrid, cancel);
    }

    static final String NO_FILE_LOCATION_HINT = "the image has no file location. "
            + "Save it first or pass autosave=[folder].";

    /** True when there is neither an explicit autosave folder nor a saved source file. */
    static boolean autoSaveDestinationMissing(SegSweepMacroOptions options, ImagePlus image) {
        return options != null && !hasText(options.autosave())
                && existingInputFileFor(options, image) == null;
    }

    /**
     * Autosaves when there is somewhere to save; otherwise logs that autosave
     * was skipped. A new, duplicated or processed image has no file, and a
     * successful sweep must not be reported as a failure because of that.
     */
    File autoSaveOrSkip(SegSweepResult result,
                        SegSweepMacroOptions options,
                        ImagePlus image,
                        BufferedImage reviewedGrid,
                        BooleanSupplier cancel) throws java.io.IOException {
        if (autoSaveDestinationMissing(options, image)) {
            log(COMMAND_NAME + ": autosave skipped: " + NO_FILE_LOCATION_HINT);
            return null;
        }
        return autoSaveIfRequested(result, options, image, reviewedGrid, cancel);
    }

    /**
     * The saved source file, or null. Only an absolute location counts: a bare
     * title or a file name without a directory would resolve against Fiji's
     * working directory, and autosave must never write there by accident.
     */
    private static File existingInputFileFor(SegSweepMacroOptions options, ImagePlus image) {
        if (options != null && hasText(options.image())) {
            File explicit = new File(options.image());
            if (explicit.isAbsolute() && explicit.isFile()) return explicit;
        }
        if (image != null) {
            FileInfo info = image.getOriginalFileInfo();
            if (info != null && hasText(info.fileName) && hasText(info.directory)) {
                File file = new File(info.directory, info.fileName);
                if (file.isAbsolute() && file.isFile()) return file;
            }
        }
        return null;
    }

    /** Names the outputs; never used as a save location on its own. */
    private static File inputFileFor(SegSweepMacroOptions options, ImagePlus image) {
        File existing = existingInputFileFor(options, image);
        if (existing != null) return existing;
        if (image != null) {
            FileInfo info = image.getOriginalFileInfo();
            if (info != null && hasText(info.fileName)) {
                return new File(info.fileName);
            }
            if (hasText(image.getTitle())) {
                return new File(image.getTitle());
            }
        }
        return new File("image.tif");
    }

    /**
     * Resolves {@code image=}: an open window title first (the user named an
     * image they can see; a same-named file in Fiji's working directory must not
     * win), then an absolute or relative file path.
     */
    ImageLease resolveImage(String imageOption) {
        if (hasText(imageOption)) {
            String value = imageOption.trim();
            ImagePlus byTitle = WindowManager.getImage(value);
            if (byTitle != null) {
                return ImageLease.borrowed(byTitle);
            }
            File file = new File(value);
            if (file.exists()) {
                String[] error = new String[1];
                ImagePlus image = QuietImageOpener.open(file.getAbsolutePath(), error);
                if (image == null) {
                    throw new IllegalArgumentException(error[0] + " (" + value + ")");
                }
                return ImageLease.owned(image);
            }
            throw new IllegalArgumentException("Open image or file not found: " + value);
        }
        ImagePlus current = WindowManager.getCurrentImage();
        return current == null ? null : ImageLease.borrowed(current);
    }

    static final class ImageLease {
        private final ImagePlus image;
        private final boolean owned;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ImageLease(ImagePlus image, boolean owned) {
            this.image = image;
            this.owned = owned;
        }

        static ImageLease owned(ImagePlus image) {
            return new ImageLease(image, true);
        }

        static ImageLease borrowed(ImagePlus image) {
            return new ImageLease(image, false);
        }

        ImagePlus image() {
            return image;
        }

        void close() {
            if (!owned || image == null || !closed.compareAndSet(false, true)) {
                return;
            }
            image.changes = false;
            image.close();
            image.flush();
        }
    }

    /**
     * Records the full call, then clears ImageJ's pending command so it does
     * not also record a bare {@code run("Object Segmentation Sweep");} when
     * {@code run()} returns. Returns the recorded line, or null.
     */
    static String recordMacroCall(SegSweepMacroOptions options) {
        if (!Recorder.record || options == null) {
            return null;
        }
        String line;
        try {
            line = "run(\"" + COMMAND_NAME + "\", \"" + options.toMacroOptions() + "\");\n";
        } catch (IllegalArgumentException ex) {
            IJ.log(COMMAND_NAME + ": this run was not recorded: the image title or a path "
                    + "contains [, ] or \", which ImageJ macro options cannot carry. "
                    + "Rename the image or choose another folder to record it.");
            Recorder.disableCommandRecording();
            return null;
        }
        Recorder.recordString(line);
        Recorder.disableCommandRecording();
        return line;
    }

    private void logWarnings(SegSweepResult result) {
        if (result == null || result.warnings().isEmpty()) {
            return;
        }
        for (int i = 0; i < result.warnings().size(); i++) {
            log(COMMAND_NAME + " warning: " + result.warnings().get(i));
        }
    }

    /**
     * Reports an error on one line. In the GUI it is an ImageJ error dialog; in
     * a macro or headless run it also aborts the calling macro, which used to
     * carry on as if the sweep had succeeded.
     */
    void reportError(String message) {
        String text = hasText(message)
                ? QuietImageOpener.oneLine(message)
                : "Unknown Object Segmentation Sweep error.";
        boolean headless = GraphicsEnvironment.isHeadless();
        if (headless) {
            log(COMMAND_NAME.toUpperCase(Locale.ROOT) + " ERROR: " + text);
        } else {
            showError(text);
        }
        if (headless || inMacro()) {
            abortMacro();
        }
    }

    /** Test seam: IJ.log. */
    void log(String message) {
        IJ.log(message);
    }

    /** Test seam: the GUI error dialog (IJ.error also aborts a running macro). */
    void showError(String text) {
        IJ.error(COMMAND_NAME, text);
    }

    /** True when called from a macro {@code run(...)} with options. */
    boolean inMacro() {
        return Macro.getOptions() != null;
    }

    /**
     * Aborts the calling macro. {@code Macro.abort()} throws
     * {@code RuntimeException(Macro.MACRO_CANCELED)} on a macro thread, which
     * ImageJ treats as a quiet stop; elsewhere it only sets ImageJ's abort flag.
     */
    void abortMacro() {
        Macro.abort();
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }
}
