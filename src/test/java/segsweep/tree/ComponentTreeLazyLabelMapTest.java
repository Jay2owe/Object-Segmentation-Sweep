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
import ij.process.ImageProcessor;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.SegSweepLabellerFixtures;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ComponentTreeLazyLabelMapTest {
    @Test
    public void objectCountDoesNotMaterialiseLabels() {
        ImagePlus image = SegSweepLabellerFixtures.points(5, 2, 1,
                new int[][] { { 0, 0, 0 }, { 4, 0, 0 } });
        ComponentTreeResult result = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build());

        assertEquals(2, result.objectCount());
        assertEquals(0, result.labelMap().materializationCount());
    }

    @Test
    public void materialisesCalibrated16BitContiguousLabelStack() {
        ImagePlus image = SegSweepLabellerFixtures.calibratedEmptyStack(5, 2, 1);
        SegSweepLabellerFixtures.setVoxel(image, 0, 0, 0, 20);
        SegSweepLabellerFixtures.setVoxel(image, 4, 0, 0, 20);
        ComponentTreeResult result = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build());

        ImagePlus labels = result.labelMap().get();

        assertEquals(1, result.labelMap().materializationCount());
        assertEquals(16, labels.getBitDepth());
        assertEquals(image.getCalibration().pixelWidth, labels.getCalibration().pixelWidth, 0.0);
        assertEquals(image.getCalibration().pixelDepth, labels.getCalibration().pixelDepth, 0.0);
        assertEquals(2, distinctNonZero(labels));
        assertEquals(1, labels.getStack().getProcessor(1).get(0, 0));
        assertEquals(2, labels.getStack().getProcessor(1).get(4, 0));
    }

    @Test
    public void emptySelectionDetachesFromTreeButKeepsBlankLabelMetadata() {
        ImagePlus image = SegSweepLabellerFixtures.calibratedEmptyStack(5, 3, 2);
        ComponentTreeResult result = ComponentTree.build(
                image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(1000).build());

        assertEquals(ComponentTreeResult.Status.EMPTY, result.status());
        assertFalse(result.selection().retainsTree());
        ImagePlus labels = result.labelMap().get();
        assertEquals(5, labels.getWidth());
        assertEquals(3, labels.getHeight());
        assertEquals(2, labels.getStackSize());
        assertEquals(image.getCalibration().pixelWidth,
                labels.getCalibration().pixelWidth, 0.0d);
        assertEquals(0, distinctNonZero(labels));
    }

    @Test
    public void everySliceAndTheFullStackMatchAVoxelByVoxelReference() {
        java.util.Random random = new java.util.Random(808L);
        for (int volume = 0; volume < 40; volume++) {
            int w = 3 + random.nextInt(10);
            int h = 3 + random.nextInt(10);
            int d = 1 + random.nextInt(7);
            ImagePlus image = SegSweepLabellerFixtures.calibratedEmptyStack(w, h, d);
            for (int z = 0; z < d; z++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        SegSweepLabellerFixtures.setVoxel(image, x, y, z, random.nextInt(40));
                    }
                }
            }
            ComponentTree tree = ComponentTree.build(image, random.nextBoolean()
                    ? SegSweepLabeller.Connectivity.SIX
                    : SegSweepLabeller.Connectivity.TWENTY_SIX);
            for (int threshold = 0; threshold < 40; threshold += 7) {
                ComponentTreeResult result = tree.query(ComponentTreeQuery.builder()
                        .threshold(threshold).minSize(1 + random.nextInt(3)).build());
                short[][] expected = referenceLabels(result.selection(), w, h, d);
                ImagePlus stack = result.labelMap().get();
                for (int z = 0; z < d; z++) {
                    String at = "volume " + volume + " threshold " + threshold + " z " + z;
                    org.junit.Assert.assertArrayEquals(at + " (stack)", expected[z],
                            (short[]) stack.getStack().getPixels(z + 1));
                    org.junit.Assert.assertArrayEquals(at + " (slice)", expected[z],
                            (short[]) result.labelMap().getSlice(z + 1).getProcessor().getPixels());
                }
            }
        }
    }

    /** Labels written voxel by voxel from each selected object's full voxel list. */
    private static short[][] referenceLabels(ComponentSelection selection, int w, int h, int d) {
        short[][] planes = new short[d][w * h];
        int[][] objects = selection.objectVoxelIndices();
        for (int label = 1; label <= objects.length; label++) {
            for (int voxel : objects[label - 1]) {
                planes[voxel / (w * h)][voxel % (w * h)] = (short) label;
            }
        }
        return planes;
    }

    private static int distinctNonZero(ImagePlus image) {
        Set<Integer> values = new HashSet<Integer>();
        ImageProcessor processor = image.getStack().getProcessor(1);
        for (int i = 0; i < processor.getPixelCount(); i++) {
            int value = processor.get(i);
            if (value > 0) {
                values.add(Integer.valueOf(value));
            }
        }
        return values.size();
    }
}
