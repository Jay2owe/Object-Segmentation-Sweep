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
import ij.measure.ResultsTable;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.ResourceGuard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Public Java facade for folder batch processing.
 */
public final class SegSweepBatchRunner {
    private SegSweepBatchRunner() {
    }

    /** Told before each image is opened; {@code index} is one-based. */
    public interface ProgressListener {
        void imageStarting(int index, int total, File file);
    }

    public static String preview(SegSweepBatchParameters parameters) {
        CompiledBatch compiled = compile(parameters);
        Map<String, Map<String, List<File>>> groups = discover(parameters, compiled.pattern);
        return SegSweepBatch.previewNestedGroups(groups);
    }

    public static SegSweepBatchResult run(SegSweepBatchParameters parameters) {
        return run(parameters, null, null);
    }

    /**
     * Runs the batch, reporting each image before it is opened and polling
     * {@code cancelCheck} between and during images. On cancel the images
     * already finished are still written to the roll-up, then a
     * {@link CancellationException} names how far the batch got.
     */
    public static SegSweepBatchResult run(SegSweepBatchParameters parameters,
                                          ProgressListener progress,
                                          BooleanSupplier cancelCheck) {
        CompiledBatch compiled = compile(parameters);
        Map<String, Map<String, List<File>>> groups = discover(parameters, compiled.pattern);
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("No matching files found in: "
                    + parameters.inputFolder());
        }

        File outputRoot = null;
        AutoSaveWriter.DirectoryReservation outputReservation = null;
        if (parameters.autoSave()) {
            File root = parameters.saveDir() == null
                    ? parameters.inputFolder()
                    : parameters.saveDir();
            try {
                outputReservation = AutoSaveWriter.reserveDirectory(
                        new File(root, AutoSaveWriter.OUTPUT_FOLDER));
                outputRoot = outputReservation.directory;
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }

        try {
            return runGroups(parameters, groups, outputRoot, progress, cancelCheck);
        } finally {
            if (outputReservation != null) outputReservation.release();
        }
    }

    private static SegSweepBatchResult runGroups(
            SegSweepBatchParameters parameters,
            Map<String, Map<String, List<File>>> groups,
            File outputRoot,
            ProgressListener progress,
            BooleanSupplier cancelCheck) {
        List<SegSweepBatchResult.ImageResult> imageResults =
                new ArrayList<SegSweepBatchResult.ImageResult>();
        List<SegSweepBatchResult.BatchFailure> failures =
                new ArrayList<SegSweepBatchResult.BatchFailure>();
        int total = 0;
        int processed = 0;
        int fileCount = countFiles(groups);
        boolean cancelled = false;

        outer:
        for (Map.Entry<String, Map<String, List<File>>> folderEntry : groups.entrySet()) {
            String relativeFolder = folderEntry.getKey();
            for (Map.Entry<String, List<File>> groupEntry : folderEntry.getValue().entrySet()) {
                String groupKey = groupEntry.getKey();
                List<File> files = groupEntry.getValue();
                for (int i = 0; i < files.size(); i++) {
                    if (isCancelled(cancelCheck)) {
                        cancelled = true;
                        break outer;
                    }
                    total++;
                    File file = files.get(i);
                    if (progress != null) progress.imageStarting(total, fileCount, file);
                    ImagePlus image = null;
                    try {
                        String[] openError = new String[1];
                        image = QuietImageOpener.open(file.getAbsolutePath(), openError);
                        if (image == null) {
                            throw new IOException(openError[0]);
                        }
                        if (parameters.autoSave()) {
                            ResourceGuard.Feasibility feasibility =
                                    ResourceGuard.assessMontageFeasibility(
                                            displayWindow(parameters.analysisOptions()), image,
                                            parameters.analysisOptions().limits());
                            if (!feasibility.isOk()) {
                                throw new SweepRefusedException(feasibility.getMessage());
                            }
                        }
                        SegSweepResult result = SegSweepAnalysis.run(
                                parameters.analysisOptions().toParameters(image),
                                null, cancelCheck);
                        File imageOutputDir = null;
                        if (parameters.autoSave()) {
                            AutoSaveWriter.DirectoryReservation imageReservation =
                                    AutoSaveWriter.reserveDirectory(
                                            new File(outputRoot, safeFolderName(file)));
                            imageOutputDir = imageReservation.directory;
                            try {
                                AutoSaveWriter.writeToDirectory(imageOutputDir, file, result,
                                        null, cancelCheck);
                            } finally {
                                imageReservation.release();
                            }
                        }
                        imageResults.add(new SegSweepBatchResult.ImageResult(file,
                                relativeFolder, groupKey, result.compactForBatch(), imageOutputDir));
                        processed++;
                    } catch (CancellationException e) {
                        total--;
                        cancelled = true;
                        break outer;
                    } catch (Exception e) {
                        failures.add(new SegSweepBatchResult.BatchFailure(file,
                                relativeFolder, groupKey, failureMessage(e)));
                    } finally {
                        if (image != null) {
                            image.changes = false;
                            image.close();
                            image.flush();
                        }
                    }
                }
            }
        }

        SegSweepBatchResult result = new SegSweepBatchResult(total, processed,
                failures.size(), outputRoot, imageResults, failures);
        if (parameters.autoSave()) {
            writeBatchRollup(outputRoot, result);
        }
        if (cancelled) {
            throw new CancellationException("Batch cancelled after " + total + " of "
                    + fileCount + " images; finished images were kept.");
        }
        return result;
    }

    private static boolean isCancelled(BooleanSupplier cancelCheck) {
        return Thread.currentThread().isInterrupted()
                || (cancelCheck != null && cancelCheck.getAsBoolean());
    }

    private static int countFiles(Map<String, Map<String, List<File>>> groups) {
        int count = 0;
        for (Map<String, List<File>> folder : groups.values()) {
            for (List<File> files : folder.values()) {
                count += files.size();
            }
        }
        return count;
    }

    private static ParameterSweep displayWindow(SegSweepMacroOptions options) {
        Map<ParameterId, ParameterValueList> axes =
                new java.util.LinkedHashMap<ParameterId, ParameterValueList>();
        axes.put(options.primaryAxis().id(), options.primaryAxis().valueList());
        if (options.secondaryAxis() != null) {
            axes.put(options.secondaryAxis().id(), options.secondaryAxis().valueList());
        }
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, axes,
                options.crop(), "C" + options.channel());
    }

    private static Map<String, Map<String, List<File>>> discover(
            SegSweepBatchParameters parameters, Pattern pattern) {
        return SegSweepBatch.findGroupsRecursive(
                parameters.inputFolder(), pattern,
                parameters.varyingGroup(), parameters.recursive(),
                outputExclusions(parameters));
    }

    /**
     * Gives the shared traversal the exact output trees it can know about.
     * The adapter also retains the plugin's name-based result filter for older
     * versioned outputs found deeper in a recursively scanned input tree.
     */
    private static Set<File> outputExclusions(SegSweepBatchParameters parameters) {
        HashSet<File> excluded = new HashSet<File>();
        File base = parameters.saveDir() == null
                ? parameters.inputFolder() : parameters.saveDir();
        if (base == null) return excluded;
        excluded.add(new File(base, AutoSaveWriter.OUTPUT_FOLDER));
        File[] children = base.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                if (SegSweepBatch.isGeneratedOutputDirectory(children[i])) {
                    excluded.add(children[i]);
                }
            }
        }
        return excluded;
    }

    private static void writeBatchRollup(File outputRoot, SegSweepBatchResult result) {
        if (outputRoot == null) return;
        try {
            saveTable(result.batchPicksTable(), new File(outputRoot, "batch_picks.csv"));
            saveTable(result.batchFailuresTable(), new File(outputRoot, "batch_failures.csv"));
            writeRootReadme(outputRoot, result);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write batch roll-up: "
                    + e.getMessage(), e);
        }
    }

    private static void saveTable(ResultsTable table, File file) throws IOException {
        try {
            table.save(file.getAbsolutePath());
        } catch (Exception e) {
            throw new IOException("Could not save " + file.getName() + ": "
                    + e.getMessage(), e);
        }
    }

    private static void writeRootReadme(File outputRoot,
                                        SegSweepBatchResult result) throws IOException {
        java.io.Writer writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(new File(outputRoot, "README.txt")),
                java.nio.charset.StandardCharsets.UTF_8);
        try {
            writer.write("Object Segmentation Sweep batch output.\n\n"
                    + "Each image subfolder contains one single-image autosave tree.\n"
                    + "batch_picks.csv lists one pick row per successful image plus one comparable-set summary per source folder.\n"
                    + "batch_failures.csv lists images that failed while later images continued.\n"
                    + "Within each source folder, comparable summaries require matching provenance and knee display-range conditions.\n"
                    + "No mean or median pick is reported for incomparable settings.\n\n"
                    + "Processed images: " + result.processedImages() + "\n"
                    + "Failed images: " + result.failedImages() + "\n");
        } finally {
            writer.close();
        }
    }

    private static CompiledBatch compile(SegSweepBatchParameters parameters) {
        validate(parameters);
        try {
            Pattern pattern = Pattern.compile(parameters.filenameRegex());
            int groupCount = pattern.matcher("").groupCount();
            if (parameters.varyingGroup() > groupCount) {
                throw new IllegalArgumentException("Capture group "
                        + parameters.varyingGroup() + " was requested, but the filename regex has "
                        + groupCount + " capture group" + (groupCount == 1 ? "." : "s."));
            }
            return new CompiledBatch(pattern);
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("Invalid filename regex: "
                    + ex.getMessage(), ex);
        }
    }

    private static void validate(SegSweepBatchParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Batch parameters must not be null.");
        }
        if (parameters.inputFolder() == null || !parameters.inputFolder().isDirectory()) {
            throw new IllegalArgumentException("Input folder does not exist: "
                    + parameters.inputFolder());
        }
        if (parameters.filenameRegex() == null
                || parameters.filenameRegex().trim().isEmpty()) {
            throw new IllegalArgumentException("Filename regex must not be empty.");
        }
        if (parameters.varyingGroup() < 0) {
            throw new IllegalArgumentException("Capture group must be 0 or greater.");
        }
        if (parameters.analysisOptions() == null) {
            throw new IllegalArgumentException("Analysis options must not be null.");
        }
        parameters.analysisOptions().validate();
        if (parameters.saveDir() != null && parameters.saveDir().exists()
                && !parameters.saveDir().isDirectory()) {
            throw new IllegalArgumentException("Save path is not a directory: "
                    + parameters.saveDir());
        }
    }

    private static String failureMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        return message;
    }

    private static String safeFolderName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static final class CompiledBatch {
        final Pattern pattern;

        CompiledBatch(Pattern pattern) {
            this.pattern = pattern;
        }
    }
}
