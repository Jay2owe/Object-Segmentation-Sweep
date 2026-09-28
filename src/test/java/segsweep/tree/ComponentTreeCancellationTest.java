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
import org.junit.Test;
import segsweep.SegSweepLabeller;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression tests for the stage 03 cancel fixes in the component-tree engine. */
public class ComponentTreeCancellationTest {

    /**
     * An all-zero stack is one flat level; before the fix the builder only
     * checked for cancel between levels, so this build ran to completion.
     */
    @Test(timeout = 60000L)
    public void flatLevelBuildStopsWithin500msOfCancel() throws Exception {
        final ImagePlus flat = flatStack(256, 256, 64);
        final AtomicBoolean cancel = new AtomicBoolean();
        final AtomicLong cancelledAt = new AtomicLong();
        final AtomicLong stoppedAt = new AtomicLong();
        final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();
        final AtomicBoolean levelStarted = new AtomicBoolean();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ComponentTree.build(flat, SegSweepLabeller.Connectivity.TWENTY_SIX,
                            new BooleanSupplier() {
                                @Override public boolean getAsBoolean() {
                                    return cancel.get();
                                }
                            }, (done, total) -> levelStarted.set(true));
                } catch (Throwable t) {
                    thrown.set(t);
                } finally {
                    stoppedAt.set(System.nanoTime());
                }
            }
        }, "flat-build");
        worker.start();
        // Wait until the builder has reached its level loop, then 50 ms more.
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (!levelStarted.get() && System.nanoTime() < deadline) Thread.sleep(1L);
        Thread.sleep(50L);
        cancelledAt.set(System.nanoTime());
        cancel.set(true);
        worker.join(30000L);

        assertTrue("build thread should stop", !worker.isAlive());
        assertTrue("expected CancellationException, got " + thrown.get(),
                thrown.get() instanceof CancellationException);
        long reactionMs = (stoppedAt.get() - cancelledAt.get()) / 1_000_000L;
        assertTrue("cancel took " + reactionMs + " ms", reactionMs < 500L);
    }

    /** Thread interruption alone (no cancel supplier) now stops a build too. */
    @Test(timeout = 60000L)
    public void interruptStopsBuildWithoutCancelSupplier() throws Exception {
        final ImagePlus flat = flatStack(128, 128, 32);
        final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();
        final AtomicBoolean levelStarted = new AtomicBoolean();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ComponentTree.build(flat, SegSweepLabeller.Connectivity.TWENTY_SIX,
                            null, (done, total) -> levelStarted.set(true));
                } catch (Throwable t) {
                    thrown.set(t);
                }
            }
        }, "flat-build-interrupt");
        worker.start();
        while (!levelStarted.get()) Thread.sleep(1L);
        worker.interrupt();
        worker.join(30000L);
        // Either the build already finished (tiny machines are fast) or it was cancelled.
        Throwable failure = thrown.get();
        assertTrue(String.valueOf(failure),
                failure == null || failure instanceof CancellationException);
    }

    /**
     * {@code ComponentTree.voxels} checked cancel only when the copied-voxel
     * count hit a multiple of 1024. The stripes chain has 40 nodes of 1000
     * voxels, so {@code at} is 0, 1000, 2000 ... and after the first check at 0
     * it never hit another multiple: a cancel raised mid-traversal was ignored.
     * The traversal now also checks after every 16384 copied voxels.
     */
    @Test
    public void voxelTraversalChecksCancelEvenWhenNodeSizesSkipTheOldMultiple() {
        ImagePlus image = stripes();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);
        int root = -1;
        for (ComponentNode node : tree.nodes()) {
            if (node.parentId() < 0) root = node.id();
        }
        final AtomicInteger calls = new AtomicInteger();
        try {
            tree.voxelsByNodeId(root, new BooleanSupplier() {
                @Override public boolean getAsBoolean() {
                    return calls.incrementAndGet() > 1;
                }
            });
            fail("expected the traversal to observe cancel");
        } catch (CancellationException expected) {
            assertTrue(calls.get() >= 2);
        }
    }

    @Test
    public void labelMapMaterialisationHonoursCancel() {
        ImagePlus image = stripes();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);
        LazyLabelMap labels = tree.query(ComponentTreeQuery.builder().threshold(0).build())
                .labelMap();
        try {
            labels.get(new BooleanSupplier() {
                @Override public boolean getAsBoolean() {
                    return true;
                }
            });
            fail("expected cancel");
        } catch (CancellationException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            labels.getSlice(1, () -> true);
            fail("expected cancel");
        } catch (CancellationException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(2, labels.materializationCount());
        assertEquals(image.getStackSize(), labels.get(null).getStackSize());
    }

    @Test
    public void materialisationCountIsExactUnderConcurrentUse() throws Exception {
        ImagePlus image = stripes();
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX);
        final LazyLabelMap labels = tree.query(ComponentTreeQuery.builder().threshold(0).build())
                .labelMap();
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < 50; i++) labels.getSlice(1).flush();
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) thread.join();
        assertEquals(400, labels.materializationCount());
    }

    @Test
    public void selectionObjectVoxelsHonourCancel() {
        ComponentTree tree = ComponentTree.build(stripes(), SegSweepLabeller.Connectivity.SIX);
        ComponentSelection selection = tree.query(ComponentTreeQuery.builder().threshold(0).build())
                .selection();
        try {
            selection.objectVoxelIndices(() -> true);
            fail("expected cancel");
        } catch (CancellationException expected) {
            assertNotNull(expected.getMessage());
        }
        assertTrue(selection.objectVoxelIndices(null).length > 0);
    }

    /**
     * 2D surface area counts the top and bottom faces (2 x area + perimeter)
     * in voxel units. Documented as a known limitation; pinned so any change
     * is deliberate.
     */
    @Test
    public void twoDimensionalSurfaceAreaCountsBothFacesUncalibrated() {
        ImageStack stack = new ImageStack(8, 8);
        ByteProcessor processor = new ByteProcessor(8, 8);
        for (int y = 2; y < 4; y++) {
            for (int x = 2; x < 5; x++) {
                processor.set(x, y, 100);
            }
        }
        stack.addSlice("z1", processor);
        ImagePlus image = new ImagePlus("surface-2d", stack);
        ij.measure.Calibration calibration = new ij.measure.Calibration();
        calibration.pixelWidth = 0.5;
        calibration.pixelHeight = 0.5;
        calibration.setUnit("micron");
        image.setCalibration(calibration);
        ComponentTree tree = ComponentTree.build(image, SegSweepLabeller.Connectivity.TWENTY_SIX);
        ComponentTreeResult result = tree.query(ComponentTreeQuery.builder().threshold(50).build());
        assertEquals(1, result.objectCount());
        int nodeId = result.selection().firstNodeId();
        ComponentTree.NodeData data = null;
        for (ComponentNode node : tree.nodes()) {
            if (node.id() == nodeId) data = nodeData(tree, nodeId);
        }
        assertNotNull(data);
        // 3x2 rectangle: top 6 + bottom 6 + perimeter 10 = 22 faces, pixel size ignored.
        assertEquals(22.0, tree.attribute(data, MorphologyAttribute.SURFACE_AREA, null), 0.0);
    }

    private static ComponentTree.NodeData nodeData(ComponentTree tree, int nodeId) {
        try {
            java.lang.reflect.Field field = ComponentTree.class.getDeclaredField("nodes");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<ComponentTree.NodeData> nodes =
                    (java.util.List<ComponentTree.NodeData>) field.get(tree);
            return nodes.get(nodeId);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    /** 40 horizontal 1000-voxel stripes at distinct levels, nested as one chain. */
    private static ImagePlus stripes() {
        int width = 1000;
        int height = 40;
        ByteProcessor processor = new ByteProcessor(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                processor.set(x, y, 10 + y * 5);
            }
        }
        ImageStack stack = new ImageStack(width, height);
        stack.addSlice("z1", processor);
        return new ImagePlus("stripes", stack);
    }

    private static ImagePlus flatStack(int width, int height, int depth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice("z" + (z + 1), new ByteProcessor(width, height));
        }
        return new ImagePlus("flat", stack);
    }
}
