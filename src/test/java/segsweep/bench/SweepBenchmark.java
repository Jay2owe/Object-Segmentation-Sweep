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
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import segsweep.SegSweep;
import segsweep.SegSweepLabeller;
import segsweep.SegSweepParameters;
import segsweep.sweep.ParameterId;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.ComponentTreeResult;
import segsweep.tree.LazyLabelMap;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Timing harness (a {@code main}, not a JUnit test) for the component-tree
 * engine. Prints the median of five runs for each timed step.
 *
 * <pre>
 * sh ./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -Xmx3g -cp "target/classes;target/test-classes;$(cat target/cp.txt)" \
 *     segsweep.bench.SweepBenchmark [smooth16|unique32|iou|all] [width height depth]
 * </pre>
 *
 * <ul>
 *   <li>{@code smooth16}: box-smoothed seeded noise, 16-bit (default 128x128x64);</li>
 *   <li>{@code unique32}: 32-bit volume where every voxel value is unique
 *       (default 128x128x32);</li>
 *   <li>{@code iou}: a 5x5 threshold by minimum-size sweep through the public
 *       API, which includes neighbour-IoU stability scoring.</li>
 * </ul>
 */
public final class SweepBenchmark {
    private static final int RUNS = 5;
    private static final long SLOW_STEP_NANOS = 20_000_000_000L;

    private SweepBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        String which = args.length > 0 ? args[0] : "smooth16";
        int[] size = args.length >= 4
                ? new int[] { Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]) }
                : null;
        System.out.println("# SweepBenchmark java=" + System.getProperty("java.version")
                + " maxHeapMiB=" + (Runtime.getRuntime().maxMemory() >> 20)
                + " cpus=" + Runtime.getRuntime().availableProcessors());
        if ("smooth16".equals(which) || "all".equals(which)) {
            int[] s = size == null ? new int[] { 128, 128, 64 } : size;
            benchTree("smooth16", smoothNoise16(s[0], s[1], s[2], 1L), 1900, 2300, 11);
        }
        if ("unique32".equals(which) || "all".equals(which)) {
            int[] s = size == null ? new int[] { 128, 128, 32 } : size;
            ImagePlus image = unique32(s[0], s[1], s[2], 2L);
            int voxels = s[0] * s[1] * s[2];
            benchTree("unique32", image, voxels * 0.40, voxels * 0.60, 11);
        }
        if ("iou".equals(which) || "all".equals(which)) {
            int[] s = size == null ? new int[] { 128, 128, 16 } : size;
            benchIou(smoothNoise16(s[0], s[1], s[2], 3L));
        }
    }

    private static void benchTree(String name, ImagePlus image,
                                  double lowThreshold, double highThreshold, int steps) {
        long voxels = (long) image.getWidth() * image.getHeight() * image.getStackSize();
        System.out.println("## " + name + " " + image.getWidth() + "x" + image.getHeight()
                + "x" + image.getStackSize() + " " + image.getBitDepth() + "-bit ("
                + voxels + " voxels)");
        double[] thresholds = new double[steps];
        for (int i = 0; i < steps; i++) {
            thresholds[i] = lowThreshold + (highThreshold - lowThreshold) * i / (steps - 1);
        }
        long[] build = new long[RUNS];
        long heapBytes = 0L;
        ComponentTree tree = null;
        Runtime rt = Runtime.getRuntime();
        for (int run = 0; run < RUNS; run++) {
            tree = null;
            System.gc();
            long before = rt.totalMemory() - rt.freeMemory();
            long t0 = System.nanoTime();
            tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.TWENTY_SIX);
            build[run] = System.nanoTime() - t0;
            System.gc();
            long after = rt.totalMemory() - rt.freeMemory();
            heapBytes = Math.max(heapBytes, after - before);
        }
        final ComponentTree built = tree;
        final double[] cuts = thresholds;
        final ComponentTreeResult[] middle = new ComponentTreeResult[1];
        long[] query = timed(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < cuts.length; i++) {
                    ComponentTreeResult result = built.query(ComponentTreeQuery.builder()
                            .threshold(cuts[i]).minSize(2).build());
                    if (i == cuts.length / 2) middle[0] = result;
                }
            }
        });
        final LazyLabelMap labels = middle[0].labelMap();
        long[] get = timed(new Runnable() {
            @Override public void run() {
                labels.get().flush();
            }
        });
        long[] slices = timed(new Runnable() {
            @Override public void run() {
                for (int z = 1; z <= labels.depth(); z++) {
                    labels.getSlice(z).flush();
                }
            }
        });
        System.out.println(String.format(Locale.ROOT,
                "nodes=%d objectsAtMiddleThreshold=%d selectedVoxels=%d retainedHeapBytesPerVoxel=%.1f",
                built.nodes().size(), middle[0].objectCount(),
                middle[0].selection().selectedVoxelCount(), heapBytes / (double) voxels));
        print("tree_build_ms", build);
        print("query_sweep_ms(" + steps + " thresholds)", query);
        print("labelmap_get_ms", get);
        print("getSlice_all_z_ms", slices);
    }

    /**
     * Median-of-five timing, stopped early when one run exceeds
     * {@link #SLOW_STEP_NANOS} so a pathologically slow step still reports.
     */
    private static long[] timed(Runnable step) {
        long[] times = new long[RUNS];
        for (int run = 0; run < RUNS; run++) {
            long t0 = System.nanoTime();
            step.run();
            times[run] = System.nanoTime() - t0;
            if (times[run] > SLOW_STEP_NANOS) {
                return Arrays.copyOf(times, run + 1);
            }
        }
        return times;
    }

    private static void benchIou(ImagePlus image) {
        System.out.println("## iou " + image.getWidth() + "x" + image.getHeight() + "x"
                + image.getStackSize() + " 16-bit, 5x5 threshold x min_size, pick=stability");
        long[] times = new long[RUNS];
        for (int run = 0; run < RUNS; run++) {
            long t0 = System.nanoTime();
            SegSweep.run(SegSweepParameters.builder()
                    .image(image)
                    .axis(ParameterId.THRESHOLD, 1900, 2300, 100)
                    .axis(ParameterId.MIN_SIZE, 1, 41, 10)
                    .pickCriterion(SegSweepParameters.PickCriterion.STABILITY)
                    .build());
            times[run] = System.nanoTime() - t0;
        }
        print("iou_sweep_ms", times);
    }

    private static void print(String label, long[] nanos) {
        long[] sorted = Arrays.copyOf(nanos, nanos.length);
        Arrays.sort(sorted);
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < nanos.length; i++) {
            if (i > 0) all.append(',');
            all.append(String.format(Locale.ROOT, "%.1f", nanos[i] / 1.0e6));
        }
        System.out.println(String.format(Locale.ROOT, "%-34s median=%9.1f  runs=[%s]",
                label, sorted[sorted.length / 2] / 1.0e6, all));
    }

    /** Seeded uniform noise smoothed by a 5x5x3 box filter, scaled into 16-bit. */
    static ImagePlus smoothNoise16(int w, int h, int d, long seed) {
        Random random = new Random(seed);
        int plane = w * h;
        int[] noise = new int[plane * d];
        for (int i = 0; i < noise.length; i++) noise[i] = random.nextInt(4096);
        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < d; z++) {
            short[] pixels = new short[plane];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int sum = 0;
                    int count = 0;
                    for (int dz = -1; dz <= 1; dz++) {
                        int zz = z + dz;
                        if (zz < 0 || zz >= d) continue;
                        for (int dy = -2; dy <= 2; dy++) {
                            int yy = y + dy;
                            if (yy < 0 || yy >= h) continue;
                            for (int dx = -2; dx <= 2; dx++) {
                                int xx = x + dx;
                                if (xx < 0 || xx >= w) continue;
                                sum += noise[zz * plane + yy * w + xx];
                                count++;
                            }
                        }
                    }
                    pixels[y * w + x] = (short) Math.min(65535, sum / count);
                }
            }
            stack.addSlice("z" + (z + 1), new ShortProcessor(w, h, pixels, null));
        }
        return new ImagePlus("bench-smooth16", stack);
    }

    /** 32-bit volume holding a seeded permutation of 0..n-1: every value unique. */
    static ImagePlus unique32(int w, int h, int d, long seed) {
        int n = w * h * d;
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) permutation[i] = i;
        Random random = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = swap;
        }
        int plane = w * h;
        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < d; z++) {
            float[] pixels = new float[plane];
            for (int i = 0; i < plane; i++) pixels[i] = permutation[z * plane + i];
            stack.addSlice("z" + (z + 1), new FloatProcessor(w, h, pixels, null));
        }
        return new ImagePlus("bench-unique32", stack);
    }
}
