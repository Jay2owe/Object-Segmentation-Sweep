/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ShortProcessor;

public final class LazyLabelMap {
    private final int width;
    private final int height;
    private final int depth;
    private final ComponentSelection selection;
    private int materializationCount;

    LazyLabelMap(int width,
                 int height,
                 int depth,
                 ComponentSelection selection) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        if (selection == null) {
            throw new IllegalArgumentException("selection must not be null");
        }
        this.selection = selection;
    }

    public ImagePlus get() {
        materializationCount++;
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice("z" + (z + 1), new ShortProcessor(width, height));
        }
        int plane = width * height;
        int label = 1;
        for (int nodeId = selection.firstNodeId(); nodeId >= 0;
             nodeId = selection.nextNodeId(nodeId)) {
            int[] voxels = selection.voxelIndices(nodeId);
            for (int i = 0; i < voxels.length; i++) {
                int voxel = voxels[i];
                int z = voxel / plane;
                int indexInPlane = voxel - z * plane;
                ((ShortProcessor) stack.getProcessor(z + 1)).set(indexInPlane, label);
            }
            label++;
        }
        ImagePlus image = new ImagePlus("Object Segmentation Sweep labels", stack);
        Calibration calibration = selection.calibrationCopy();
        if (calibration != null) {
            image.setCalibration(calibration);
        }
        return image;
    }

    /** Materialises one Z plane without allocating the full label stack. */
    public ImagePlus getSlice(int oneBasedZ) {
        int z = Math.max(1, Math.min(depth, oneBasedZ)) - 1;
        materializationCount++;
        ShortProcessor processor = new ShortProcessor(width, height);
        int plane = width * height;
        int label = 1;
        for (int nodeId = selection.firstNodeId(); nodeId >= 0;
             nodeId = selection.nextNodeId(nodeId)) {
            int[] voxels = selection.voxelIndices(nodeId);
            for (int i = 0; i < voxels.length; i++) {
                int voxel = voxels[i];
                int voxelZ = voxel / plane;
                if (voxelZ == z) {
                    processor.set(voxel - voxelZ * plane, label);
                }
            }
            label++;
        }
        ImagePlus image = new ImagePlus("Object Segmentation Sweep labels z" + (z + 1), processor);
        Calibration calibration = selection.calibrationCopy();
        if (calibration != null) image.setCalibration(calibration);
        return image;
    }

    public int depth() {
        return depth;
    }

    public int materializationCount() {
        return materializationCount;
    }
}
