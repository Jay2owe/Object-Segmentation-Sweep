/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentNode {
    private final ComponentTree tree;
    private final ComponentTree.NodeData data;

    ComponentNode(ComponentTree tree, ComponentTree.NodeData data) {
        this.tree = tree;
        this.data = data;
    }

    public int id() {
        return data.id;
    }

    public int parentId() {
        return data.parentId;
    }

    public List<Integer> childIds() {
        return Collections.unmodifiableList(new ArrayList<Integer>(data.childIds));
    }

    public float birthThreshold() {
        return data.level;
    }

    public int voxelCount() {
        return data.voxelCount;
    }

    public double intensitySum() {
        return data.intensitySum;
    }

    public double meanIntensity() {
        return data.voxelCount == 0 ? Double.NaN : data.intensitySum / (double) data.voxelCount;
    }

    public double maxIntensity() {
        return data.maxIntensity;
    }

    public int minX() {
        return data.minX;
    }

    public int minY() {
        return data.minY;
    }

    public int minZ() {
        return data.minZ;
    }

    public int maxX() {
        return data.maxX;
    }

    public int maxY() {
        return data.maxY;
    }

    public int maxZ() {
        return data.maxZ;
    }

    public int boundingWidth() {
        return data.voxelCount == 0 ? 0 : data.maxX - data.minX + 1;
    }

    public int boundingHeight() {
        return data.voxelCount == 0 ? 0 : data.maxY - data.minY + 1;
    }

    public int boundingDepth() {
        return data.voxelCount == 0 ? 0 : data.maxZ - data.minZ + 1;
    }

    public double surfaceArea() {
        return data.surfaceArea;
    }

    public double sphericity() {
        if (data.voxelCount <= 0 || data.surfaceArea <= 0.0) return Double.NaN;
        return Math.pow(Math.PI, 1.0 / 3.0)
                * Math.pow(6.0 * data.voxelCount, 2.0 / 3.0)
                / data.surfaceArea;
    }

    public double compactness() {
        if (data.voxelCount <= 0 || data.surfaceArea <= 0.0) return Double.NaN;
        return (36.0 * Math.PI * data.voxelCount * data.voxelCount)
                / (data.surfaceArea * data.surfaceArea * data.surfaceArea);
    }

    public double elongation() {
        return data.elongation();
    }

    public double feretDiameterMax() {
        return tree.feretDiameterMax(data);
    }

    public double attribute(MorphologyAttribute attribute) {
        if (attribute == MorphologyAttribute.VOLUME) return voxelCount();
        if (attribute == MorphologyAttribute.MEAN_INTENSITY) return meanIntensity();
        if (attribute == MorphologyAttribute.MAX_INTENSITY) return maxIntensity();
        if (attribute == MorphologyAttribute.ELONGATION) return elongation();
        if (attribute == MorphologyAttribute.SURFACE_AREA) return surfaceArea();
        if (attribute == MorphologyAttribute.SPHERICITY) return sphericity();
        if (attribute == MorphologyAttribute.COMPACTNESS) return compactness();
        if (attribute == MorphologyAttribute.FERET_DIAMETER_MAX) return feretDiameterMax();
        return Double.NaN;
    }

    int[] voxels() {
        return tree.voxels(data);
    }

    /** Returns the voxel indexes belonging to this component at its tree level. */
    public int[] voxelIndices() {
        return tree.voxels(data);
    }
}
