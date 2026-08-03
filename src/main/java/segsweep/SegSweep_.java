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
import segsweep.sweep.VariationResult;
import segsweep.token.SettingsTokenWriter;
import segsweep.ui.SegSweepDialog;
import segsweep.ui.grid.VariationGridWindow;
import segsweep.ui.render.PreviewDisplaySettings;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        try {
            SegSweepMacroOptions options = SegSweepMacroOptionsParser.parse(optionsText);
            ImagePlus image = resolveImage(options.image());
            if (image == null) {
                throw new IllegalArgumentException("No source image was found. Provide image=[path or title] or open an image.");
            }
            SegSweepResult result = SegSweep.run(options.toParameters(image));
            if (shouldAutoSaveImmediately(options)) {
                autoSaveIfRequested(result, options, image);
            }
            showMacroResult(result, options, image);
            return result;
        } catch (Exception ex) {
            reportError(ex.getMessage());
            return null;
        }
    }

    private void runInteractive() {
        ImagePlus active = WindowManager.getCurrentImage();
        SegSweepDialog dialog = new SegSweepDialog(active);
        SegSweepMacroOptions options = dialog.showDialog();
        if (options == null) {
            return;
        }
        ImagePlus image = resolveImage(options.image());
        if (image == null) {
            IJ.error(COMMAND_NAME, "No source image was found.");
            return;
        }
        recordMacroCall(options);
        final SegSweepMacroOptions runOptions = options;
        final ImagePlus runImage = image;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    IJ.showStatus(COMMAND_NAME + ": running sweep...");
                    SegSweepResult result = SegSweep.run(runOptions.toParameters(runImage));
                    if (shouldAutoSaveImmediately(runOptions)) {
                        autoSaveIfRequested(result, runOptions, runImage);
                    }
                    showMacroResult(result, runOptions, runImage);
                    IJ.showStatus(COMMAND_NAME + ": done.");
                } catch (Exception ex) {
                    reportError(ex.getMessage());
                }
            }
        }, "SegSweep-Analysis").start();
    }

    private void showMacroResult(final SegSweepResult result,
                                 SegSweepMacroOptions options,
                                 ImagePlus image) {
        boolean display = !GraphicsEnvironment.isHeadless()
                && options != null && !options.hideDisplay();
        if (!display) {
            logWarnings(result);
            return;
        }
        if (options.showTables() && result.sweepTable() != null) {
            result.sweepTable().show("Sweep Results");
        }
        if (options.showTables() && result.pickTable() != null && result.pickTable().size() > 0) {
            result.pickTable().show("Sweep Pick");
        }
        if (!options.showGrid()) {
            logWarnings(result);
            return;
        }
        final ImagePlus displaySource = SourceImageView.selectedChannelAndCrop(
                image, result.parameters().channel(), result.parameters().crop());
        final VariationGridWindow grid = new VariationGridWindow(null, COMMAND_NAME,
                displayWindow(result), displaySource);
        grid.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                displaySource.changes = false;
                displaySource.close();
                displaySource.flush();
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
                if (options != null && hasText(options.autosave())) {
                    try {
                        File output = AutoSaveWriter.writeTo(new File(options.autosave()),
                                inputFileFor(options, image), chosenResult);
                        IJ.log(COMMAND_NAME + ": saved manual pick to " + output.getAbsolutePath());
                    } catch (Exception ex) {
                        IJ.error(COMMAND_NAME, "Could not save manual pick: " + ex.getMessage());
                    }
                }
            }
        });
        grid.setVisible(true);
    }

    private static ParameterSweep displayWindow(SegSweepResult result) {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>(result.parameters().axes());
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values,
                result.parameters().crop(), "C" + result.parameters().channel());
    }

    static String settingsTokenForSelected(SegSweepResult result, ParameterCombo selected) {
        return settingsTokenForSelected(result, selected, Instant.now());
    }

    static String settingsTokenForSelected(SegSweepResult result,
                                           ParameterCombo selected,
                                           Instant writtenAt) {
        SettingsTokenWriter.PickSummary summary = SettingsTokenWriter.PickSummary.of(
                "manual",
                "",
                "",
                "manual grid pick");
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
        if (result == null || options == null || !hasText(options.autosave())) {
            return null;
        }
        return AutoSaveWriter.writeTo(new File(options.autosave()),
                inputFileFor(options, image), result);
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

    private ImagePlus resolveImage(String imageOption) {
        if (hasText(imageOption)) {
            String value = imageOption.trim();
            File file = new File(value);
            if (file.exists()) {
                ImagePlus image = IJ.openImage(file.getAbsolutePath());
                if (image == null) {
                    throw new IllegalArgumentException("Could not open image: " + value);
                }
                return image;
            }
            ImagePlus byTitle = WindowManager.getImage(value);
            if (byTitle != null) {
                return byTitle;
            }
            ImagePlus opened = IJ.openImage(value);
            if (opened != null) {
                return opened;
            }
            throw new IllegalArgumentException("Open image or file not found: " + value);
        }
        return WindowManager.getCurrentImage();
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
