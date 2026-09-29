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
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Proves the current builder produces the same tree as the frozen pre-stage-08
 * builder ({@link LegacyComponentTreeBuilder}): same node IDs, levels, parents,
 * children, every attribute bit for bit, and the same voxel arrays in the same
 * order. Any difference would renumber labels or change measurements.
 */
public class ComponentTreeNodeEquivalenceTest {
    private static final int RANDOM_VOLUMES = 240;

    @Test
    public void seededRandomVolumesGiveIdenticalTrees() {
        Random random = new Random(20260929L);
        int[] perDepth = new int[3];
        int nonFinite = 0;
        for (int volume = 0; volume < RANDOM_VOLUMES; volume++) {
            int kind = volume % 3;
            int width = 1 + random.nextInt(14);
            int height = 1 + random.nextInt(14);
            int depth = random.nextInt(4) == 0 ? 1 : 1 + random.nextInt(6);
            ImagePlus image;
            if (kind == 0) {
                image = bytes(width, height, depth, random);
            } else if (kind == 1) {
                image = shorts(width, height, depth, random);
            } else {
                image = floats(width, height, depth, random);
                if (hasNonFinite(image)) nonFinite++;
            }
            perDepth[kind]++;
            SegSweepLabeller.Connectivity connectivity = random.nextBoolean()
                    ? SegSweepLabeller.Connectivity.SIX
                    : SegSweepLabeller.Connectivity.TWENTY_SIX;
            assertSameTree("volume " + volume + " (" + image.getBitDepth() + "-bit "
                    + width + "x" + height + "x" + depth + ", " + connectivity + ")",
                    image, connectivity);
        }
        assertTrue(perDepth[0] >= 80 && perDepth[1] >= 80 && perDepth[2] >= 80);
        assertTrue("non-finite values must be exercised", nonFinite >= 20);
    }

    @Test
    public void signedZeroNonFiniteAndIntegralFloatsAreHandledAsBefore() {
        float[] values = {
            0.0f, -0.0f, 0.0f, -0.0f, Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, 3.0f, 3.0f, -0.0f, 0.0f, 65535.0f,
            65536.0f, -1.0f, 1.5f, Float.NaN
        };
        for (SegSweepLabeller.Connectivity connectivity
                : SegSweepLabeller.Connectivity.values()) {
            assertSameTree("mixed specials " + connectivity,
                    floatImage(4, 2, 2, values), connectivity);
            // Integral floats inside 0..65535 take the counting-sort path.
            assertSameTree("integral floats " + connectivity,
                    floatImage(4, 2, 2, new float[] {
                        5, 5, 0, 7, 65535, 1, 1, 0, 5, 7, 7, 0, 2, 2, 2, 5 }), connectivity);
            assertSameTree("all NaN " + connectivity,
                    floatImage(2, 2, 1, new float[] {
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN }), connectivity);
            assertSameTree("only -0.0 " + connectivity,
                    floatImage(3, 1, 1, new float[] { -0.0f, -0.0f, -0.0f }), connectivity);
        }
    }

    @Test
    public void manyRootsInOneHashBucketKeepTheirNodeOrder() {
        // Fifty isolated voxels at indices k*128 share one HashMap bucket, so the
        // old per-level map turned that bucket into a tree. Node IDs must follow
        // the same iteration order.
        ByteProcessor processor = new ByteProcessor(64, 100);
        for (int k = 0; k < 50; k++) processor.set(0, 2 * k, 7);
        processor.set(10, 10, 7);
        processor.set(33, 51, 7);
        ImagePlus image = new ImagePlus("bucket", processor);
        for (SegSweepLabeller.Connectivity connectivity
                : SegSweepLabeller.Connectivity.values()) {
            assertSameTree("bucket " + connectivity, image, connectivity);
        }
    }

    @Test
    public void flatAndSingleVoxelImagesMatch() {
        assertSameTree("single voxel", new ImagePlus("one", new ByteProcessor(1, 1)),
                SegSweepLabeller.Connectivity.SIX);
        ShortProcessor flat = new ShortProcessor(9, 7);
        flat.set(1234);
        assertSameTree("flat", new ImagePlus("flat", flat),
                SegSweepLabeller.Connectivity.TWENTY_SIX);
        assertSameTree("oracle fixture", ComponentTreeOracleFixtures.equivalenceStack(),
                SegSweepLabeller.Connectivity.TWENTY_SIX);
    }

    static void assertSameTree(String name, ImagePlus image,
                               SegSweepLabeller.Connectivity connectivity) {
        List<ComponentTree.NodeData> expected = nodesOf(
                LegacyComponentTreeBuilder.build(image, connectivity));
        List<ComponentTree.NodeData> actual = nodesOf(
                ComponentTreeBuilder.build(image, connectivity));
        assertEquals(name + ": node count", expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            ComponentTree.NodeData e = expected.get(i);
            ComponentTree.NodeData a = actual.get(i);
            String at = name + ": node " + i + " ";
            assertEquals(at + "id", e.id, a.id);
            assertEquals(at + "level", Float.floatToRawIntBits(e.level),
                    Float.floatToRawIntBits(a.level));
            assertEquals(at + "parent", e.parentId, a.parentId);
            assertEquals(at + "children", e.childIds, a.childIds);
            assertEquals(at + "voxelCount", e.voxelCount, a.voxelCount);
            assertBits(at + "intensitySum", e.intensitySum, a.intensitySum);
            assertBits(at + "maxIntensity", e.maxIntensity, a.maxIntensity);
            assertArrayEquals(at + "box",
                    new int[] { e.minX, e.minY, e.minZ, e.maxX, e.maxY, e.maxZ },
                    new int[] { a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ });
            assertBits(at + "surfaceArea", e.surfaceArea, a.surfaceArea);
            assertBits(at + "xSum", e.xSum, a.xSum);
            assertBits(at + "ySum", e.ySum, a.ySum);
            assertBits(at + "zSum", e.zSum, a.zSum);
            assertBits(at + "xxSum", e.xxSum, a.xxSum);
            assertBits(at + "yySum", e.yySum, a.yySum);
            assertBits(at + "zzSum", e.zzSum, a.zzSum);
            assertBits(at + "xySum", e.xySum, a.xySum);
            assertBits(at + "xzSum", e.xzSum, a.xzSum);
            assertBits(at + "yzSum", e.yzSum, a.yzSum);
            assertArrayEquals(at + "voxels (in order)", e.voxels, a.voxels);
        }
    }

    private static List<ComponentTree.NodeData> nodesOf(ComponentTree tree) {
        int count = tree.nodes().size();
        java.util.ArrayList<ComponentTree.NodeData> data =
                new java.util.ArrayList<ComponentTree.NodeData>(count);
        for (int i = 0; i < count; i++) {
            data.add(tree.nodeData(i));
        }
        return data;
    }

    private static void assertBits(String message, double expected, double actual) {
        assertEquals(message, Double.doubleToRawLongBits(expected),
                Double.doubleToRawLongBits(actual));
    }

    /** 8-bit with a small value range so most levels hold ties. */
    private static ImagePlus bytes(int w, int h, int d, Random random) {
        int range = random.nextBoolean() ? 1 + random.nextInt(6) : 256;
        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < d; z++) {
            byte[] pixels = new byte[w * h];
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (byte) (range == 256 ? random.nextInt(256)
                        : 250 - 40 * random.nextInt(range));
            }
            stack.addSlice("z" + (z + 1), new ByteProcessor(w, h, pixels, null));
        }
        return new ImagePlus("bytes", stack);
    }

    /** 16-bit, sometimes spread over the full 0..65535 range. */
    private static ImagePlus shorts(int w, int h, int d, Random random) {
        int mode = random.nextInt(3);
        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < d; z++) {
            short[] pixels = new short[w * h];
            for (int i = 0; i < pixels.length; i++) {
                int value;
                if (mode == 0) value = 1000 + random.nextInt(4);
                else if (mode == 1) value = random.nextInt(65536);
                else value = random.nextBoolean() ? 65535 : random.nextInt(8);
                pixels[i] = (short) value;
            }
            stack.addSlice("z" + (z + 1), new ShortProcessor(w, h, pixels, null));
        }
        return new ImagePlus("shorts", stack);
    }

    /** 32-bit with ties, negative values, fractions, signed zeros and non-finite values. */
    private static ImagePlus floats(int w, int h, int d, Random random) {
        int mode = random.nextInt(4);
        float[] specials = {
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.0f, 0.0f,
            Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE, -Float.MIN_VALUE
        };
        float[] values = new float[w * h * d];
        for (int i = 0; i < values.length; i++) {
            float value;
            if (mode == 0) {
                value = random.nextInt(5) - 2.0f;
            } else if (mode == 1) {
                value = (float) (random.nextGaussian() * 100.0);
            } else if (mode == 2) {
                value = random.nextInt(4) * 0.25f;
            } else {
                value = random.nextInt(300);
            }
            if (mode != 3 && random.nextInt(6) == 0) {
                value = specials[random.nextInt(specials.length)];
            }
            values[i] = value;
        }
        return floatImage(w, h, d, values);
    }

    private static ImagePlus floatImage(int w, int h, int d, float[] values) {
        ImageStack stack = new ImageStack(w, h);
        int plane = w * h;
        for (int z = 0; z < d; z++) {
            float[] pixels = new float[plane];
            System.arraycopy(values, z * plane, pixels, 0, plane);
            stack.addSlice("z" + (z + 1), new FloatProcessor(w, h, pixels, null));
        }
        return new ImagePlus("floats", stack);
    }

    private static boolean hasNonFinite(ImagePlus image) {
        ImageStack stack = image.getStack();
        for (int z = 1; z <= stack.getSize(); z++) {
            float[] pixels = (float[]) stack.getPixels(z);
            for (int i = 0; i < pixels.length; i++) {
                if (!Float.isFinite(pixels[i])) return true;
            }
        }
        return false;
    }
}
