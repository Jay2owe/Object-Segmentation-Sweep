/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.measure.ResultsTable;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.SweepProvenance;
import segsweep.sweep.analysis.KneeOutcome;
import segsweep.sweep.analysis.PickResult;

import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Summary returned by the Object Segmentation Sweep batch runner.
 */
public final class SegSweepBatchResult {
    private final int totalImages;
    private final int processedImages;
    private final int failedImages;
    private final File outputDirectory;
    private final List<ImageResult> imageResults;
    private final List<BatchFailure> failures;

    SegSweepBatchResult(int totalImages,
                        int processedImages,
                        int failedImages,
                        File outputDirectory,
                        List<ImageResult> imageResults,
                        List<BatchFailure> failures) {
        this.totalImages = totalImages;
        this.processedImages = processedImages;
        this.failedImages = failedImages;
        this.outputDirectory = outputDirectory;
        this.imageResults = immutableImages(imageResults);
        this.failures = immutableFailures(failures);
    }

    public int totalImages() {
        return totalImages;
    }

    public int getTotalImages() {
        return totalImages;
    }

    public int processedImages() {
        return processedImages;
    }

    public int getProcessedImages() {
        return processedImages;
    }

    public int failedImages() {
        return failedImages;
    }

    public int getFailedImages() {
        return failedImages;
    }

    public File outputDirectory() {
        return outputDirectory;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public List<ImageResult> imageResults() {
        return imageResults;
    }

    public List<ImageResult> getImageResults() {
        return imageResults;
    }

    public List<BatchFailure> failures() {
        return failures;
    }

    public List<BatchFailure> getFailures() {
        return failures;
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public boolean allComparable() {
        return incomparableReasons().isEmpty();
    }

    public List<String> incomparableReasons() {
        List<String> reasons = new ArrayList<String>();
        for (Map.Entry<String, List<ImageResult>> entry : groupedByFolder().entrySet()) {
            List<String> folderReasons = incomparableReasons(entry.getValue());
            String folder = displayFolder(entry.getKey());
            for (int i = 0; i < folderReasons.size(); i++) {
                reasons.add(folder + ": " + folderReasons.get(i));
            }
        }
        return dedupe(reasons);
    }

    public ResultsTable batchPicksTable() {
        ResultsTable table = new ResultsTable();
        for (Map.Entry<String, List<ImageResult>> entry : groupedByFolder().entrySet()) {
            List<ImageResult> folderResults = entry.getValue();
            List<String> reasons = incomparableReasons(folderResults);
            boolean comparable = reasons.isEmpty();
            for (int i = 0; i < folderResults.size(); i++) {
                ImageResult rowResult = folderResults.get(i);
                SegSweepResult result = rowResult.result();
                int row = table.getCounter();
                table.incrementCounter();
                table.setValue("Folder", row, rowResult.relativeFolder());
                table.setValue("Group", row, rowResult.groupKey());
                table.setValue("Image", row, rowResult.image().getName());
                table.setValue("Criterion", row,
                        result.parameters().pickCriterion().name().toLowerCase(Locale.ROOT));
                ParameterCombo picked = result.pickedCombo();
                table.setValue("Picked", row, picked == null ? "" : formatPicked(picked));
                PickResult pick = result.pick();
                table.setValue("Knee_Outcome", row,
                        pick == null ? "" : pick.knee().kind().name());
                table.setValue("Criteria_Agree", row,
                        pick == null ? "" : String.valueOf(pick.criteriaAgree()));
                table.setValue("Comparable_Set", row, String.valueOf(comparable));
                table.setValue("Incomparable_Reasons", row,
                        comparable ? "" : join(reasons, "; "));
            }
            int summary = table.getCounter();
            table.incrementCounter();
            table.setValue("Folder", summary, displayFolder(entry.getKey()));
            table.setValue("Group", summary, "");
            table.setValue("Image", summary, "SUMMARY");
            table.setValue("Criterion", summary, "");
            table.setValue("Picked", summary, comparable
                    ? "Comparable picks: " + folderResults.size()
                    : "Not comparable");
            table.setValue("Knee_Outcome", summary, "");
            table.setValue("Criteria_Agree", summary, "");
            table.setValue("Comparable_Set", summary, String.valueOf(comparable));
            table.setValue("Incomparable_Reasons", summary,
                    comparable ? "" : join(reasons, "; "));
        }
        return table;
    }

    public ResultsTable batchFailuresTable() {
        ResultsTable table = new ResultsTable();
        for (int i = 0; i < failures.size(); i++) {
            BatchFailure failure = failures.get(i);
            int row = table.getCounter();
            table.incrementCounter();
            table.setValue("Folder", row, failure.relativeFolder());
            table.setValue("Group", row, failure.groupKey());
            table.setValue("Image", row, failure.image().getName());
            table.setValue("Reason", row, failure.reason());
        }
        return table;
    }

    private LinkedHashMap<String, List<ImageResult>> groupedByFolder() {
        LinkedHashMap<String, List<ImageResult>> grouped =
                new LinkedHashMap<String, List<ImageResult>>();
        for (int i = 0; i < imageResults.size(); i++) {
            ImageResult result = imageResults.get(i);
            List<ImageResult> folder = grouped.get(result.relativeFolder());
            if (folder == null) {
                folder = new ArrayList<ImageResult>();
                grouped.put(result.relativeFolder(), folder);
            }
            folder.add(result);
        }
        return grouped;
    }

    private static List<String> incomparableReasons(List<ImageResult> folderResults) {
        List<String> reasons = new ArrayList<String>();
        ImageResult first = firstPicked(folderResults);
        if (first == null) {
            reasons.add("No successful image picks are available.");
            return reasons;
        }
        for (int i = 0; i < folderResults.size(); i++) {
            ImageResult current = folderResults.get(i);
            if (current.result().pickedCombo() == null || current.result().pick() == null) {
                reasons.add(current.image().getName() + ": no picked combination was available.");
                continue;
            }
            collectProvenanceReasons(reasons, first, current);
            collectKneeReasons(reasons, first, current);
        }
        return dedupe(reasons);
    }

    private static ImageResult firstPicked(List<ImageResult> folderResults) {
        for (int i = 0; i < folderResults.size(); i++) {
            ImageResult result = folderResults.get(i);
            if (result.result().pickedCombo() != null && result.result().pick() != null) {
                return result;
            }
        }
        return null;
    }

    private static String displayFolder(String relativeFolder) {
        return relativeFolder == null || relativeFolder.isEmpty() ? "." : relativeFolder;
    }

    private static void collectProvenanceReasons(List<String> reasons,
                                                 ImageResult first,
                                                 ImageResult current) {
        SweepProvenance a = first.result().provenance();
        SweepProvenance b = current.result().provenance();
        if (a.comparableWith(b)) {
            return;
        }
        if (!a.crop().equals(b.crop())) {
            reasons.add(current.image().getName() + ": crop differs from "
                    + first.image().getName() + " (" + cropText(a) + " vs " + cropText(b) + ").");
        }
        if (a.fullWidth() != b.fullWidth() || a.fullHeight() != b.fullHeight()
                || a.fullDepth() != b.fullDepth()) {
            reasons.add(current.image().getName() + ": source dimensions differ.");
        }
        if (!a.displayedRanges().equals(b.displayedRanges())) {
            reasons.add(current.image().getName() + ": displayed range differs.");
        }
        if (!a.calibrationUnit().equals(b.calibrationUnit())
                || Double.compare(a.pixelWidth(), b.pixelWidth()) != 0
                || Double.compare(a.pixelHeight(), b.pixelHeight()) != 0
                || Double.compare(a.pixelDepth(), b.pixelDepth()) != 0) {
            reasons.add(current.image().getName() + ": calibration differs.");
        }
        if (!a.connectivity().equals(b.connectivity())) {
            reasons.add(current.image().getName() + ": connectivity differs.");
        }
    }

    private static void collectKneeReasons(List<String> reasons,
                                           ImageResult first,
                                           ImageResult current) {
        KneeOutcome a = first.result().pick().knee();
        KneeOutcome b = current.result().pick().knee();
        if (!a.comparable(b)) {
            reasons.add(current.image().getName() + ": knee outcome or displayed knee range differs.");
        }
    }

    private static String cropText(SweepProvenance provenance) {
        Rectangle bounds = provenance.crop().boundsFor(
                provenance.fullWidth(), provenance.fullHeight());
        return bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
    }

    private static String formatPicked(ParameterCombo picked) {
        Object threshold = picked.get(ParameterId.THRESHOLD);
        if (threshold != null) {
            return "threshold=" + String.valueOf(threshold);
        }
        return picked.toString();
    }

    private static List<String> dedupe(List<String> reasons) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < reasons.size(); i++) {
            String reason = reasons.get(i);
            if (!out.contains(reason)) {
                out.add(reason);
            }
        }
        return out;
    }

    private static String join(List<String> values, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static List<ImageResult> immutableImages(List<ImageResult> input) {
        if (input == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<ImageResult>(input));
    }

    private static List<BatchFailure> immutableFailures(List<BatchFailure> input) {
        if (input == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<BatchFailure>(input));
    }

    public static final class ImageResult {
        private final File image;
        private final String relativeFolder;
        private final String groupKey;
        private final SegSweepResult result;
        private final File outputDirectory;

        ImageResult(File image,
                    String relativeFolder,
                    String groupKey,
                    SegSweepResult result,
                    File outputDirectory) {
            this.image = image;
            this.relativeFolder = relativeFolder == null ? "" : relativeFolder;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.result = result;
            this.outputDirectory = outputDirectory;
        }

        public File image() {
            return image;
        }

        public String relativeFolder() {
            return relativeFolder;
        }

        public String groupKey() {
            return groupKey;
        }

        public SegSweepResult result() {
            return result;
        }

        public File outputDirectory() {
            return outputDirectory;
        }
    }

    public static final class BatchFailure {
        private final File image;
        private final String relativeFolder;
        private final String groupKey;
        private final String reason;

        BatchFailure(File image, String relativeFolder, String groupKey, String reason) {
            this.image = image;
            this.relativeFolder = relativeFolder == null ? "" : relativeFolder;
            this.groupKey = groupKey == null ? "" : groupKey;
            this.reason = reason == null || reason.trim().isEmpty()
                    ? "Unknown failure" : reason.trim();
        }

        public File image() {
            return image;
        }

        public String relativeFolder() {
            return relativeFolder;
        }

        public String groupKey() {
            return groupKey;
        }

        public String reason() {
            return reason;
        }
    }
}
