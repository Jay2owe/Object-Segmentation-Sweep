/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import ij.measure.Calibration;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Compact immutable node-ID selection shared by one query result's consumers.
 * Component-node views are reconstructed only for callers that explicitly ask
 * for them; lazy labels and IoU operate directly on the shared ID bitset.
 */
public final class ComponentSelection {
    private final ComponentTree tree;
    private final BitSet nodeIds;
    private final int size;

    ComponentSelection(ComponentTree tree, List<ComponentTree.NodeData> selected) {
        if (tree == null) {
            throw new IllegalArgumentException("tree must not be null");
        }
        this.tree = tree;
        this.nodeIds = new BitSet();
        if (selected != null) {
            for (ComponentTree.NodeData node : selected) {
                if (node != null) nodeIds.set(node.id);
            }
        }
        this.size = nodeIds.cardinality();
    }

    public int size() {
        return size;
    }

    /** Materialises lightweight public node views on demand. */
    public List<ComponentNode> selectedNodes() {
        List<ComponentNode> nodes = new ArrayList<ComponentNode>(size);
        for (int id = nodeIds.nextSetBit(0); id >= 0; id = next(id)) {
            nodes.add(tree.nodeView(id));
        }
        return Collections.unmodifiableList(nodes);
    }

    /** Reconstructs the selected foreground footprint without a label image. */
    public BitSet foregroundVoxels(BooleanSupplier cancelCheck) {
        BitSet foreground = new BitSet();
        for (int id = nodeIds.nextSetBit(0); id >= 0; id = next(id)) {
            checkCancelled(cancelCheck);
            int[] voxels = tree.voxelsByNodeId(id, cancelCheck);
            for (int i = 0; i < voxels.length; i++) {
                if ((i & 1023) == 0) checkCancelled(cancelCheck);
                int voxel = voxels[i];
                if (voxel >= 0) foreground.set(voxel);
            }
        }
        return foreground;
    }

    /** Returns one reconstructed voxel-index array per selected object. */
    public int[][] objectVoxelIndices() {
        int[][] objects = new int[size][];
        int at = 0;
        for (int id = nodeIds.nextSetBit(0); id >= 0; id = next(id)) {
            objects[at++] = tree.voxelsByNodeId(id, null);
        }
        return objects;
    }

    int firstNodeId() {
        return nodeIds.nextSetBit(0);
    }

    int nextNodeId(int current) {
        return next(current);
    }

    int[] voxelIndices(int nodeId) {
        return tree.voxelsByNodeId(nodeId, null);
    }

    Calibration calibrationCopy() {
        return tree.calibrationCopy();
    }

    private int next(int current) {
        return current == Integer.MAX_VALUE ? -1 : nodeIds.nextSetBit(current + 1);
    }

    private static void checkCancelled(BooleanSupplier cancelCheck) {
        if (Thread.currentThread().isInterrupted()
                || (cancelCheck != null && cancelCheck.getAsBoolean())) {
            throw new CancellationException("Component selection traversal was cancelled.");
        }
    }
}
