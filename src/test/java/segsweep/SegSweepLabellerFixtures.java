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
import ij.process.ShortProcessor;

public final class SegSweepLabellerFixtures {
    public static final int FOREGROUND = 20;
    public static final int THRESHOLD = 10;

    private SegSweepLabellerFixtures() {}

    public static ImagePlus emptyStack(int width, int height, int depth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice("z" + (z + 1), new ShortProcessor(width, height));
        }
        return new ImagePlus("synthetic", stack);
    }

    public static ImagePlus calibratedEmptyStack(int width, int height, int depth) {
        ImagePlus image = emptyStack(width, height, depth);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.2;
        calibration.pixelHeight = 0.3;
        calibration.pixelDepth = 0.4;
        calibration.setUnit("micron");
        image.setCalibration(calibration);
        return image;
    }

    public static ImagePlus points(int width, int height, int depth, int[][] coordinates) {
        ImagePlus image = emptyStack(width, height, depth);
        for (int i = 0; i < coordinates.length; i++) {
            setVoxel(image, coordinates[i][0], coordinates[i][1], coordinates[i][2], FOREGROUND);
        }
        return image;
    }

    public static ImagePlus sizeRangeStack() {
        ImagePlus image = emptyStack(10, 3, 1);
        setVoxel(image, 0, 1, 0, FOREGROUND);
        setVoxel(image, 2, 1, 0, FOREGROUND);
        setVoxel(image, 3, 1, 0, FOREGROUND);
        setVoxel(image, 5, 1, 0, FOREGROUND);
        setVoxel(image, 6, 1, 0, FOREGROUND);
        setVoxel(image, 7, 1, 0, FOREGROUND);
        return image;
    }

    public static ImagePlus overLimitCheckerboard() {
        ImagePlus image = emptyStack(512, 512, 1);
        ShortProcessor processor = (ShortProcessor) image.getStack().getProcessor(1);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((x + y) & 1) == 0) {
                    processor.set(x, y, FOREGROUND);
                }
            }
        }
        return image;
    }

    public static ImagePlus largePerformanceStack() {
        ImagePlus image = emptyStack(512, 512, 20);
        int placed = 0;
        ShortProcessor processor = (ShortProcessor) image.getStack().getProcessor(10);
        for (int y = 4; y < 184 && placed < 2000; y += 4) {
            for (int x = 4; x < 184 && placed < 2000; x += 4) {
                processor.set(x, y, FOREGROUND);
                placed++;
            }
        }
        return image;
    }

    public static void setVoxel(ImagePlus image, int x, int y, int z, int value) {
        image.getStack().getProcessor(z + 1).set(x, y, value);
    }
}
