/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.bench;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import segsweep.SegSweepLabeller;
import segsweep.tree.ComponentTree;

import java.util.Random;

/**
 * One component-tree build in a fresh JVM, used to find the smallest heap
 * that completes it ({@code -Xmx} bisection, see {@code oss_heap.py}).
 * Exits 0 on success and 3 on {@link OutOfMemoryError}.
 *
 * <pre>
 * java -Xmx64m -cp ... segsweep.bench.TreeHeapProbe unique32|smooth16|random16|noise8 W H D
 * </pre>
 */
public final class TreeHeapProbe {
    private TreeHeapProbe() {
    }

    public static void main(String[] args) {
        String kind = args[0];
        int w = Integer.parseInt(args[1]);
        int h = Integer.parseInt(args[2]);
        int d = Integer.parseInt(args[3]);
        ImagePlus image;
        if ("unique32".equals(kind)) {
            image = SweepBenchmark.unique32(w, h, d, 2L);
        } else if ("smooth16".equals(kind)) {
            image = SweepBenchmark.smoothNoise16(w, h, d, 1L);
        } else if ("random16".equals(kind)) {
            image = random16(w, h, d);
        } else {
            image = noise8(w, h, d);
        }
        try {
            ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.TWENTY_SIX);
            System.out.println("ok nodes=" + tree.nodes().size());
            System.exit(0);
        } catch (OutOfMemoryError oom) {
            System.out.println("oom");
            System.exit(3);
        }
    }

    /** 16-bit uniform noise over the full range: close to one level per voxel. */
    static ImagePlus random16(int w, int h, int d) {
        Random random = new Random(5L);
        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < d; z++) {
            short[] pixels = new short[w * h];
            for (int i = 0; i < pixels.length; i++) pixels[i] = (short) random.nextInt(65536);
            stack.addSlice("z" + (z + 1), new ij.process.ShortProcessor(w, h, pixels, null));
        }
        return new ImagePlus("probe-random16", stack);
    }

    /** 8-bit uniform noise: the densest node count an 8-bit image can produce. */
    static ImagePlus noise8(int w, int h, int d) {
        Random random = new Random(7L);
        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < d; z++) {
            byte[] pixels = new byte[w * h];
            random.nextBytes(pixels);
            stack.addSlice("z" + (z + 1), new ByteProcessor(w, h, pixels, null));
        }
        return new ImagePlus("probe-noise8", stack);
    }
}
