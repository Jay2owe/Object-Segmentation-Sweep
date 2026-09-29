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

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class LazyLabelMap {
    private final int width;
    private final int height;
    private final int depth;
    private final ComponentSelection selection;
    private final AtomicInteger materializationCount = new AtomicInteger();

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
        return get(null);
    }

    /**
     * Materialises the full label stack, polling {@code cancelCheck} (and the
     * thread's interrupt flag) so a long autosave or batch write can stop.
     */
    public ImagePlus get(BooleanSupplier cancelCheck) {
        materializationCount.incrementAndGet();
        ImageStack stack = new ImageStack(width, height);
        short[][] planes = new short[depth][];
        for (int z = 0; z < depth; z++) {
            stack.addSlice("z" + (z + 1), new ShortProcessor(width, height));
            planes[z] = (short[]) stack.getPixels(z + 1);
        }
        int plane = width * height;
        int label = 1;
        for (int nodeId = selection.firstNodeId(); nodeId >= 0;
             nodeId = selection.nextNodeId(nodeId)) {
            checkCancelled(cancelCheck);
            int[] voxels = selection.voxelIndices(nodeId, cancelCheck);
            short value = (short) label;
            for (int i = 0; i < voxels.length; i++) {
                if ((i & 0xFFFF) == 0xFFFF) checkCancelled(cancelCheck);
                int voxel = voxels[i];
                int z = voxel / plane;
                planes[z][voxel - z * plane] = value;
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
        return getSlice(oneBasedZ, null);
    }

    /** As {@link #getSlice(int)}, polling {@code cancelCheck} between objects. */
    public ImagePlus getSlice(int oneBasedZ, BooleanSupplier cancelCheck) {
        int z = Math.max(1, Math.min(depth, oneBasedZ)) - 1;
        materializationCount.incrementAndGet();
        ShortProcessor processor = new ShortProcessor(width, height);
        short[] pixels = (short[]) processor.getPixels();
        int label = 1;
        for (int nodeId = selection.firstNodeId(); nodeId >= 0;
             nodeId = selection.nextNodeId(nodeId)) {
            checkCancelled(cancelCheck);
            // Objects whose Z range misses this plane are skipped inside paintPlane.
            selection.paintPlane(nodeId, z, pixels, label, cancelCheck);
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
        return materializationCount.get();
    }

    private static void checkCancelled(BooleanSupplier cancelCheck) {
        if (Thread.currentThread().isInterrupted()
                || (cancelCheck != null && cancelCheck.getAsBoolean())) {
            throw new CancellationException("Label-map materialisation was cancelled.");
        }
    }
}
