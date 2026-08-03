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
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import segsweep.sweep.ParameterId;
import segsweep.sweep.VariationResult;
import segsweep.tree.LazyLabelMap;
import segsweep.ui.render.LabelMapStyler;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Writes the single-image Object Segmentation Sweep deliverable tree.
 */
public final class AutoSaveWriter {
    public static final String OUTPUT_FOLDER = "Object Segmentation Sweep";

    private AutoSaveWriter() {
    }

    public static File write(File inputFile, SegSweepResult result) throws IOException {
        return write(inputFile, result, null);
    }

    public static File write(File inputFile, SegSweepResult result,
                             BufferedImage reviewedGrid) throws IOException {
        if (inputFile == null) {
            throw new IllegalArgumentException("inputFile must not be null.");
        }
        File parent = inputFile.getAbsoluteFile().getParentFile();
        if (parent == null) {
            parent = new File(".").getAbsoluteFile();
        }
        File outputDir = uniqueDirectory(new File(parent, OUTPUT_FOLDER));
        writeToDirectory(outputDir, inputFile, result, reviewedGrid);
        return outputDir;
    }

    /** Writes to an explicitly requested directory, versioning it rather than overwriting. */
    public static File writeTo(File desiredOutputDir, File inputFile,
                               SegSweepResult result) throws IOException {
        return writeTo(desiredOutputDir, inputFile, result, null);
    }

    public static File writeTo(File desiredOutputDir, File inputFile,
                               SegSweepResult result,
                               BufferedImage reviewedGrid) throws IOException {
        if (desiredOutputDir == null) {
            throw new IllegalArgumentException("desiredOutputDir must not be null.");
        }
        File outputDir = uniqueDirectory(desiredOutputDir);
        writeToDirectory(outputDir, inputFile, result, reviewedGrid);
        return outputDir;
    }

    static void writeToDirectory(File outputDir, File inputFile,
                                 SegSweepResult result) throws IOException {
        writeToDirectory(outputDir, inputFile, result, null);
    }

    static void writeToDirectory(File outputDir, File inputFile,
                                 SegSweepResult result,
                                 BufferedImage reviewedGrid) throws IOException {
        validate(outputDir, inputFile, result);
        mkdirs(outputDir);
        File labelsDir = new File(outputDir, "labels");
        mkdirs(labelsDir);

        saveTable(result.sweepTable(), new File(outputDir, "sweep_results.csv"));
        saveTable(result.pickTable(), new File(outputDir, "pick_summary.csv"));
        writeText(new File(outputDir, "picked_settings.txt"), result.pickedSettingsToken());
        File gridFile = new File(outputDir, "grid.png");
        if (reviewedGrid == null) {
            writeGridPng(gridFile, result);
        } else if (!ImageIO.write(reviewedGrid, "png", gridFile)) {
            throw new IOException("No PNG writer was available for "
                    + gridFile.getAbsolutePath());
        }
        writePickedLabels(new File(labelsDir, baseName(inputFile) + "_picked.tif"), result);
        writeText(new File(outputDir, "README.txt"), readmeText());
    }

    static File uniqueDirectory(File desired) throws IOException {
        File absolute = desired.getAbsoluteFile();
        if (!absolute.exists() || isEmptyDirectory(absolute)) {
            ensurePathReasonable(absolute);
            return absolute;
        }
        for (int i = 2; i < 1000; i++) {
            File candidate = new File(absolute.getParentFile(),
                    absolute.getName() + " " + i);
            if (!candidate.exists() || isEmptyDirectory(candidate)) {
                ensurePathReasonable(candidate);
                return candidate;
            }
        }
        throw new IOException("Could not create a versioned output folder beside "
                + absolute.getAbsolutePath());
    }

    private static void validate(File outputDir, File inputFile,
                                 SegSweepResult result) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir must not be null.");
        }
        if (inputFile == null) {
            throw new IllegalArgumentException("inputFile must not be null.");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null.");
        }
        ensurePathReasonable(outputDir.getAbsoluteFile());
    }

    private static void mkdirs(File dir) throws IOException {
        if (dir.exists() && !dir.isDirectory()) {
            throw new IOException("Output path is not a directory: " + dir.getAbsolutePath());
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create output directory: " + dir.getAbsolutePath());
        }
    }

    private static boolean isEmptyDirectory(File dir) {
        if (!dir.isDirectory()) return false;
        String[] children = dir.list();
        return children == null || children.length == 0;
    }

    private static void ensurePathReasonable(File dir) throws IOException {
        String path = dir.getAbsolutePath();
        if (path.length() > 240) {
            throw new IOException("Output path is too long for reliable Windows TIFF writing ("
                    + path.length() + " characters): " + path);
        }
    }

    private static void saveTable(ResultsTable table, File file) throws IOException {
        if (table == null) {
            writeText(file, "");
            return;
        }
        try {
            table.save(file.getAbsolutePath());
        } catch (Exception e) {
            throw new IOException("Could not save " + file.getName() + ": "
                    + e.getMessage(), e);
        }
    }

    private static void writePickedLabels(File file, SegSweepResult result) throws IOException {
        LazyLabelMap labelMap = result.pickedLabelMap();
        if (labelMap == null) {
            return;
        }
        ImagePlus labels = labelMap.get();
        try {
            labels.setTitle(baseName(file));
            FileSaver saver = new FileSaver(labels);
            boolean ok = labels.getStackSize() > 1
                    ? saver.saveAsTiffStack(file.getAbsolutePath())
                    : saver.saveAsTiff(file.getAbsolutePath());
            if (!ok) {
                throw new IOException("ImageJ FileSaver refused to write "
                        + file.getAbsolutePath());
            }
        } finally {
            labels.changes = false;
            labels.close();
            labels.flush();
        }
    }

    private static void writeGridPng(File file, SegSweepResult result) throws IOException {
        List<VariationResult> rows = result.results();
        int count = Math.max(1, rows.size());
        int cols = (int) Math.ceil(Math.sqrt(count));
        int cellW = 220;
        int cellH = 210;
        int rowsCount = (int) Math.ceil(count / (double) cols);
        BufferedImage image = new BufferedImage(cols * cellW, rowsCount * cellH,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xF5, 0xF5, 0xF5));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            for (int i = 0; i < count; i++) {
                int x = (i % cols) * cellW;
                int y = (i / cols) * cellH;
                g.setColor(new Color(0x24, 0x2A, 0x2E));
                g.fillRect(x, y, cellW - 2, cellH - 2);
                g.setColor(new Color(0x4D, 0xC3, 0xB2));
                g.drawRect(x + 5, y + 5, cellW - 12, cellH - 12);
                if (i < rows.size()) {
                    VariationResult row = rows.get(i);
                    BufferedImage preview = renderPreview(result, row);
                    if (preview != null) {
                        int availableW = cellW - 16;
                        int availableH = cellH - 58;
                        double scale = Math.min(availableW / (double) preview.getWidth(),
                                availableH / (double) preview.getHeight());
                        int drawW = Math.max(1, (int) Math.round(preview.getWidth() * scale));
                        int drawH = Math.max(1, (int) Math.round(preview.getHeight() * scale));
                        int drawX = x + (cellW - drawW) / 2;
                        int drawY = y + 8 + (availableH - drawH) / 2;
                        g.drawImage(preview, drawX, drawY, drawW, drawH, null);
                    }
                    g.setColor(Color.WHITE);
                    g.drawString("Combination " + (i + 1), x + 14, y + cellH - 38);
                    g.drawString("Objects: " + row.objectCount(), x + 14, y + cellH - 20);
                    Object threshold = row.combo().get(ParameterId.THRESHOLD);
                    if (threshold != null) {
                        g.drawString("Threshold: " + threshold, x + 108, y + cellH - 20);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG writer was available for " + file.getAbsolutePath());
        }
    }

    private static BufferedImage renderPreview(SegSweepResult result, VariationResult row) {
        if (result == null || result.parameters() == null || row == null) return null;
        ImagePlus source = result.parameters().image();
        if (source == null || source.getStack() == null || source.getStackSize() < 1) return null;
        Rectangle crop = result.parameters().crop().boundsFor(source);
        int width = crop.width;
        int height = crop.height;
        if (width <= 0 || height <= 0) return null;
        int slices = Math.max(1, source.getNSlices());
        int z = Math.max(1, (slices + 1) / 2);
        int channel = Math.max(1, Math.min(result.parameters().channel(),
                Math.max(1, source.getNChannels())));
        int stackIndex;
        try {
            stackIndex = source.getStackIndex(channel, z, 1);
        } catch (RuntimeException e) {
            stackIndex = Math.min(source.getStackSize(), z);
        }
        ImageProcessor processor = source.getStack().getProcessor(stackIndex);
        if (processor == null) return null;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = processor.getf(crop.x + x, crop.y + y);
                if (Float.isFinite(value)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = 0.0d;
            max = 1.0d;
        } else if (max <= min) {
            max = min + 1.0d;
        }

        int[] objectLabels = new int[width * height];
        int plane = width * height;
        int[][] objects = row.iouSource().objectVoxelIndices();
        for (int object = 0; object < objects.length; object++) {
            int[] voxels = objects[object];
            for (int i = 0; i < voxels.length; i++) {
                int voxelZ = voxels[i] / plane;
                if (voxelZ == z - 1) {
                    int pixel = voxels[i] - voxelZ * plane;
                    if (pixel >= 0 && pixel < objectLabels.length) {
                        objectLabels[pixel] = object + 1;
                    }
                }
            }
        }

        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = processor.getf(crop.x + x, crop.y + y);
                double scaled = Float.isFinite(value) ? (value - min) / (max - min) : 0.0d;
                int gray = (int) Math.round(Math.max(0.0d, Math.min(1.0d, scaled)) * 255.0d);
                int red = gray;
                int green = gray;
                int blue = gray;
                int label = objectLabels[y * width + x];
                if (label > 0) {
                    int colour = LabelMapStyler.rgbForLabel(label);
                    red = (int) Math.round(gray * 0.35d + ((colour >> 16) & 0xff) * 0.65d);
                    green = (int) Math.round(gray * 0.35d + ((colour >> 8) & 0xff) * 0.65d);
                    blue = (int) Math.round(gray * 0.35d + (colour & 0xff) * 0.65d);
                }
                preview.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
        return preview;
    }

    private static void writeText(File file, String text) throws IOException {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text == null ? "" : text);
        } finally {
            writer.close();
        }
    }

    private static String readmeText() {
        return "Object Segmentation Sweep output.\n\n"
                + "sweep_results.csv: one row per displayed parameter combination.\n"
                + "pick_summary.csv: the selected value and independent knee/stability reports.\n"
                + "picked_settings.txt: reproducible settings token plus crop bounds, crop fraction, calibration and displayed range.\n"
                + "grid.png: interactive saves capture the current reviewed grid; headless, hidden, and batch saves use a deterministic middle-slice montage.\n"
                + "labels/: contains only the picked label map materialised from the lazy result.\n\n"
                + "All values are conditional on the image region and displayed range recorded in picked_settings.txt.\n";
    }

    private static String baseName(File file) {
        String name = file == null ? "image" : file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
