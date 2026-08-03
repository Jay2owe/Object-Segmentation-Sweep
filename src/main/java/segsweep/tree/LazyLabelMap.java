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

import java.util.ArrayList;
import java.util.List;

public final class LazyLabelMap {
    private final int width;
    private final int height;
    private final int depth;
    private final Calibration calibration;
    private final List<ComponentNode> nodes;
    private int materializationCount;

    LazyLabelMap(int width,
                 int height,
                 int depth,
                 Calibration calibration,
                 List<ComponentNode> nodes) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.calibration = calibration == null ? null : calibration.copy();
        this.nodes = new ArrayList<ComponentNode>(nodes);
    }

    public ImagePlus get() {
        materializationCount++;
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice("z" + (z + 1), new ShortProcessor(width, height));
        }
        int plane = width * height;
        for (int label = 1; label <= nodes.size(); label++) {
            int[] voxels = nodes.get(label - 1).voxels();
            for (int i = 0; i < voxels.length; i++) {
                int voxel = voxels[i];
                int z = voxel / plane;
                int indexInPlane = voxel - z * plane;
                ((ShortProcessor) stack.getProcessor(z + 1)).set(indexInPlane, label);
            }
        }
        ImagePlus image = new ImagePlus("Object Segmentation Sweep labels", stack);
        if (calibration != null) {
            image.setCalibration(calibration.copy());
        }
        return image;
    }

    /** Materialises one Z plane without allocating the full label stack. */
    public ImagePlus getSlice(int oneBasedZ) {
        int z = Math.max(1, Math.min(depth, oneBasedZ)) - 1;
        materializationCount++;
        ShortProcessor processor = new ShortProcessor(width, height);
        int plane = width * height;
        for (int label = 1; label <= nodes.size(); label++) {
            int[] voxels = nodes.get(label - 1).voxels();
            for (int i = 0; i < voxels.length; i++) {
                int voxel = voxels[i];
                int voxelZ = voxel / plane;
                if (voxelZ == z) {
                    processor.set(voxel - voxelZ * plane, label);
                }
            }
        }
        ImagePlus image = new ImagePlus("Object Segmentation Sweep labels z" + (z + 1), processor);
        if (calibration != null) image.setCalibration(calibration.copy());
        return image;
    }

    public int depth() {
        return depth;
    }

    public int materializationCount() {
        return materializationCount;
    }
}
