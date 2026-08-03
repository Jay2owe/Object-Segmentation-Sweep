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
import segsweep.sweep.ParameterId;
import segsweep.sweep.VariationResult;
import segsweep.tree.LazyLabelMap;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
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
        if (inputFile == null) {
            throw new IllegalArgumentException("inputFile must not be null.");
        }
        File parent = inputFile.getAbsoluteFile().getParentFile();
        if (parent == null) {
            parent = new File(".").getAbsoluteFile();
        }
        File outputDir = uniqueDirectory(new File(parent, OUTPUT_FOLDER));
        writeToDirectory(outputDir, inputFile, result);
        return outputDir;
    }

    static void writeToDirectory(File outputDir, File inputFile,
                                 SegSweepResult result) throws IOException {
        validate(outputDir, inputFile, result);
        mkdirs(outputDir);
        File labelsDir = new File(outputDir, "labels");
        mkdirs(labelsDir);

        saveTable(result.sweepTable(), new File(outputDir, "sweep_results.csv"));
        saveTable(result.pickTable(), new File(outputDir, "pick_summary.csv"));
        writeText(new File(outputDir, "picked_settings.txt"), result.pickedSettingsToken());
        writeGridPng(new File(outputDir, "grid.png"), result);
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
            writeText(file, "");
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
        int cellW = 180;
        int cellH = 96;
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
                    g.setColor(Color.WHITE);
                    g.drawString("Combination " + (i + 1), x + 14, y + 26);
                    g.drawString("Objects: " + row.objectCount(), x + 14, y + 46);
                    Object threshold = row.combo().get(ParameterId.THRESHOLD);
                    g.drawString("Threshold: " + (threshold == null ? "" : threshold),
                            x + 14, y + 66);
                }
            }
        } finally {
            g.dispose();
        }
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG writer was available for " + file.getAbsolutePath());
        }
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
                + "grid.png: a static record of the reviewed display window.\n"
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
