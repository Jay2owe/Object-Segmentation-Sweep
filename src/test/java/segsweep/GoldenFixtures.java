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
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Seeded synthetic images for {@link GoldenOutputTest}.
 *
 * <p>Only integer arithmetic and {@link Random} (whose sequence is fixed by
 * the Java specification) are used, so every JVM builds identical pixels.
 * No binary fixture files are stored.</p>
 */
final class GoldenFixtures {
    private GoldenFixtures() {
    }

    /** One named fixture plus the sweep ranges that suit its value domain. */
    static final class Fixture {
        final String name;
        final ImagePlus image;
        final double thresholdFrom;
        final double thresholdTo;
        final double thresholdStep;
        final double fixedThreshold;
        final int sizeFrom;
        final int sizeTo;
        final int sizeStep;

        Fixture(String name, ImagePlus image,
                double thresholdFrom, double thresholdTo, double thresholdStep,
                double fixedThreshold, int sizeFrom, int sizeTo, int sizeStep) {
            this.name = name;
            this.image = image;
            this.thresholdFrom = thresholdFrom;
            this.thresholdTo = thresholdTo;
            this.thresholdStep = thresholdStep;
            this.fixedThreshold = fixedThreshold;
            this.sizeFrom = sizeFrom;
            this.sizeTo = sizeTo;
            this.sizeStep = sizeStep;
        }
    }

    static List<Fixture> all() {
        List<Fixture> fixtures = new ArrayList<Fixture>();
        fixtures.add(new Fixture("blobs2d_8bit", blobs2d8Bit(64, 64, 11L, true),
                10, 60, 10, 20, 1, 21, 5));
        fixtures.add(new Fixture("touching3d_16bit", touching3d16Bit(),
                100, 1100, 200, 300, 1, 41, 10));
        fixtures.add(new Fixture("nonfinite_32bit", nonFinite32Bit(),
                0.5, 3.0, 0.5, 1.0, 1, 7, 2));
        fixtures.add(new Fixture("all_zero", allZero(),
                0, 20, 5, 0, 1, 5, 2));
        fixtures.add(new Fixture("single_object", singleObject(),
                50, 250, 50, 100, 1, 31, 10));
        fixtures.add(new Fixture("saturated", saturated(),
                0, 250, 50, 100, 1, 5, 2));
        fixtures.add(new Fixture("anisotropic_3d", anisotropic3d(),
                100, 700, 150, 250, 1, 25, 8));
        fixtures.add(new Fixture("uncalibrated_2d", blobs2d8Bit(48, 48, 23L, false),
                10, 60, 10, 20, 1, 21, 5));
        return Collections.unmodifiableList(fixtures);
    }

    /** 2D 8-bit pyramids with seeded peaks and +/-3 noise. */
    static ImagePlus blobs2d8Bit(int width, int height, long seed, boolean calibrated) {
        Random random = new Random(seed);
        int[] values = new int[width * height];
        for (int blob = 0; blob < 9; blob++) {
            int cx = 4 + random.nextInt(width - 8);
            int cy = 4 + random.nextInt(height - 8);
            int peak = 60 + random.nextInt(160);
            int slope = 8 + random.nextInt(12);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int v = peak - slope * (Math.abs(x - cx) + Math.abs(y - cy));
                    int i = y * width + x;
                    if (v > values[i]) values[i] = v;
                }
            }
        }
        ByteProcessor processor = new ByteProcessor(width, height);
        for (int i = 0; i < values.length; i++) {
            processor.set(i, clamp(values[i] + random.nextInt(7) - 3, 0, 255));
        }
        ImagePlus image = new ImagePlus("golden-blobs2d", processor);
        if (calibrated) {
            image.setCalibration(calibration(0.5, 0.5, 1.0, "micron"));
        }
        return image;
    }

    /** 48x48x12 16-bit stack whose pyramids overlap, so objects touch. */
    static ImagePlus touching3d16Bit() {
        int w = 48;
        int h = 48;
        int d = 12;
        int[] values = new int[w * h * d];
        Random random = new Random(37L);
        int[][] centres = {
                {12, 12, 5}, {20, 13, 6}, {34, 30, 4}, {36, 38, 7}, {10, 36, 6}, {28, 20, 8}
        };
        for (int b = 0; b < centres.length; b++) {
            int peak = 900 + random.nextInt(600);
            int slope = 90 + random.nextInt(40);
            addPyramid(values, w, h, d, centres[b][0], centres[b][1], centres[b][2],
                    peak, slope, 2);
        }
        ImagePlus image = shortStack("golden-touching3d", w, h, d, values, random, 20);
        image.setCalibration(calibration(1.0, 1.0, 2.0, "micron"));
        return image;
    }

    /** 32-bit volume with NaN, +Inf and -Inf voxels mixed into finite blobs. */
    static ImagePlus nonFinite32Bit() {
        int w = 32;
        int h = 32;
        int d = 4;
        Random random = new Random(59L);
        int[] values = new int[w * h * d];
        addPyramid(values, w, h, d, 8, 8, 1, 32, 4, 1);
        addPyramid(values, w, h, d, 22, 20, 2, 26, 3, 1);
        addPyramid(values, w, h, d, 10, 25, 1, 20, 4, 1);
        ImageStack stack = new ImageStack(w, h);
        int plane = w * h;
        for (int z = 0; z < d; z++) {
            FloatProcessor processor = new FloatProcessor(w, h);
            for (int i = 0; i < plane; i++) {
                int v = values[z * plane + i] + random.nextInt(3);
                // Exact binary fractions: every JVM stores the same float.
                processor.setf(i, v / 8.0f);
            }
            stack.addSlice("z" + (z + 1), processor);
        }
        float[] first = (float[]) stack.getPixels(1);
        float[] second = (float[]) stack.getPixels(2);
        first[8 * w + 8] = Float.NaN;
        first[3 * w + 30] = Float.POSITIVE_INFINITY;
        second[20 * w + 22] = Float.NEGATIVE_INFINITY;
        second[0] = Float.NaN;
        ((float[]) stack.getPixels(3))[25 * w + 10] = Float.POSITIVE_INFINITY;
        ((float[]) stack.getPixels(4))[5] = -0.0f;
        ImagePlus image = new ImagePlus("golden-nonfinite32", stack);
        image.setCalibration(calibration(0.4, 0.4, 0.8, "micron"));
        return image;
    }

    static ImagePlus allZero() {
        ImageStack stack = new ImageStack(32, 32);
        for (int z = 0; z < 3; z++) {
            stack.addSlice("z" + (z + 1), new ByteProcessor(32, 32));
        }
        ImagePlus image = new ImagePlus("golden-all-zero", stack);
        image.setCalibration(calibration(0.5, 0.5, 1.0, "micron"));
        return image;
    }

    static ImagePlus singleObject() {
        int w = 32;
        int h = 32;
        int[] values = new int[w * h];
        addPyramid(values, w, h, 1, 15, 17, 0, 300, 25, 1);
        ImagePlus image = shortStack("golden-single", w, h, 1, values, new Random(71L), 0);
        image.setCalibration(calibration(0.25, 0.25, 1.0, "micron"));
        return image;
    }

    static ImagePlus saturated() {
        ImageStack stack = new ImageStack(24, 24);
        for (int z = 0; z < 2; z++) {
            ByteProcessor processor = new ByteProcessor(24, 24);
            processor.setValue(255);
            processor.fill();
            stack.addSlice("z" + (z + 1), processor);
        }
        ImagePlus image = new ImagePlus("golden-saturated", stack);
        image.setCalibration(calibration(1.0, 1.0, 1.0, "micron"));
        return image;
    }

    static ImagePlus anisotropic3d() {
        int w = 40;
        int h = 40;
        int d = 8;
        int[] values = new int[w * h * d];
        Random random = new Random(83L);
        for (int b = 0; b < 5; b++) {
            int cx = 5 + random.nextInt(w - 10);
            int cy = 5 + random.nextInt(h - 10);
            int cz = 1 + random.nextInt(d - 2);
            addPyramid(values, w, h, d, cx, cy, cz, 600 + random.nextInt(300),
                    70 + random.nextInt(30), 3);
        }
        ImagePlus image = shortStack("golden-anisotropic", w, h, d, values, random, 10);
        image.setCalibration(calibration(0.2, 0.2, 1.0, "micron"));
        return image;
    }

    private static void addPyramid(int[] values, int w, int h, int d,
                                   int cx, int cy, int cz, int peak, int slope, int zWeight) {
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int distance = Math.abs(x - cx) + Math.abs(y - cy)
                            + zWeight * Math.abs(z - cz);
                    int v = peak - slope * distance;
                    int i = (z * h + y) * w + x;
                    if (v > values[i]) values[i] = v;
                }
            }
        }
    }

    private static ImagePlus shortStack(String title, int w, int h, int d, int[] values,
                                        Random random, int noise) {
        ImageStack stack = new ImageStack(w, h);
        int plane = w * h;
        for (int z = 0; z < d; z++) {
            ImageProcessor processor = new ShortProcessor(w, h);
            for (int i = 0; i < plane; i++) {
                int jitter = noise <= 0 ? 0 : random.nextInt(2 * noise + 1) - noise;
                processor.set(i, clamp(values[z * plane + i] + jitter, 0, 65535));
            }
            stack.addSlice("z" + (z + 1), processor);
        }
        return new ImagePlus(title, stack);
    }

    private static Calibration calibration(double pw, double ph, double pd, String unit) {
        Calibration calibration = new Calibration();
        calibration.pixelWidth = pw;
        calibration.pixelHeight = ph;
        calibration.pixelDepth = pd;
        calibration.setUnit(unit);
        return calibration;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
