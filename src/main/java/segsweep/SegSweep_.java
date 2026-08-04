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
            SegSweepResult result = SegSweep.run(options.toParameters(image));
            if (shouldAutoSaveImmediately(options)) {
                autoSaveIfRequested(result, options, image);
            }
            retainedByGrid = showMacroResult(result, options, lease);
            return result;
        } catch (Exception ex) {
            reportError(ex.getMessage());
            return null;
        } finally {
            if (lease != null && !retainedByGrid) {
                lease.close();
            }
        }
    }

    private void runInteractive() {
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
                    SegSweepResult result = SegSweep.run(runOptions.toParameters(runImage));
                    if (shouldAutoSaveImmediately(runOptions)) {
                        autoSaveIfRequested(result, runOptions, runImage);
                    }
                    retainedByGrid = showMacroResult(result, runOptions, lease);
                    IJ.showStatus(COMMAND_NAME + ": done.");
                } catch (Exception ex) {
                    reportError(ex.getMessage());
                } finally {
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
                                    autoSaveIfRequested(completed, options, image);
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
            try {
                File output = autoSaveIfRequested(
                        result, options, image, grid.renderGridSnapshot());
                IJ.log(COMMAND_NAME + ": saved initial reviewed grid to "
                        + output.getAbsolutePath());
            } catch (Exception ex) {
                IJ.error(COMMAND_NAME, "Could not save sweep: " + ex.getMessage());
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
        return options != null && options.showGrid() && !hasText(options.autosave());
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
        File inputFile = inputFileFor(options, image);
        if (hasText(options.autosave())) {
            return AutoSaveWriter.writeTo(
                    new File(options.autosave()), inputFile, result, reviewedGrid);
        }
        File existingInput = existingInputFileFor(options, image);
        if (existingInput == null) {
            throw new java.io.IOException(
                    "The source image has no file location. Choose an explicit Save to folder.");
        }
        return AutoSaveWriter.write(existingInput, result, reviewedGrid);
    }

    private static File existingInputFileFor(SegSweepMacroOptions options, ImagePlus image) {
        File input = inputFileFor(options, image);
        return input.isFile() ? input : null;
    }

    private static File inputFileFor(SegSweepMacroOptions options, ImagePlus image) {
        if (options != null && hasText(options.image())) {
            File explicit = new File(options.image());
            if (explicit.isFile()) return explicit;
        }
        if (image != null) {
            FileInfo info = image.getOriginalFileInfo();
            if (info != null && hasText(info.fileName)) {
                File directory = hasText(info.directory) ? new File(info.directory) : new File(".");
                return new File(directory, info.fileName);
            }
            if (hasText(image.getTitle())) {
                return new File(image.getTitle());
            }
        }
        return new File("image.tif");
    }

    private ImageLease resolveImage(String imageOption) {
        if (hasText(imageOption)) {
            String value = imageOption.trim();
            File file = new File(value);
            if (file.exists()) {
                ImagePlus image = IJ.openImage(file.getAbsolutePath());
                if (image == null) {
                    throw new IllegalArgumentException("Could not open image: " + value);
                }
                return ImageLease.owned(image);
            }
            ImagePlus byTitle = WindowManager.getImage(value);
            if (byTitle != null) {
                return ImageLease.borrowed(byTitle);
            }
            ImagePlus opened = IJ.openImage(value);
            if (opened != null) {
                return ImageLease.owned(opened);
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

    private void recordMacroCall(SegSweepMacroOptions options) {
        if (!Recorder.record || options == null) {
            return;
        }
        try {
            Recorder.recordString("run(\"" + COMMAND_NAME + "\", \""
                    + options.toMacroOptions() + "\");\n");
        } catch (IllegalArgumentException ex) {
            IJ.log(COMMAND_NAME + ": Could not record macro options: " + ex.getMessage());
        }
    }

    private static void logWarnings(SegSweepResult result) {
        if (result == null || result.warnings().isEmpty()) {
            return;
        }
        for (int i = 0; i < result.warnings().size(); i++) {
            IJ.log(COMMAND_NAME + " warning: " + result.warnings().get(i));
        }
    }

    private void reportError(String message) {
        String text = hasText(message) ? message : "Unknown Object Segmentation Sweep error.";
        if (GraphicsEnvironment.isHeadless()) {
            IJ.log(COMMAND_NAME.toUpperCase(Locale.ROOT) + " ERROR: " + text);
        } else {
            IJ.error(COMMAND_NAME, text);
        }
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }
}
