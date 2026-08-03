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
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import segsweep.LabelResult;
import segsweep.SegSweepLabeller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class ComponentTreeOracleFixtures {
    private ComponentTreeOracleFixtures() {}

    public static ImagePlus equivalenceStack() {
        ImagePlus image = emptyStack(8, 7, 4);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 0.25;
        calibration.pixelHeight = 0.5;
        calibration.pixelDepth = 1.5;
        calibration.setUnit("micron");
        image.setCalibration(calibration);

        set(image, 0, 0, 0, 45);
        set(image, 7, 6, 3, 35);

        set(image, 2, 0, 0, 65);
        set(image, 3, 0, 0, 65);
        set(image, 4, 0, 0, 65);

        set(image, 0, 5, 0, 55);
        set(image, 1, 4, 1, 55);

        set(image, 1, 2, 2, 80);
        set(image, 2, 2, 2, 25);
        set(image, 3, 2, 2, 70);

        for (int z = 1; z <= 2; z++) {
            for (int y = 4; y <= 5; y++) {
                for (int x = 4; x <= 5; x++) {
                    set(image, x, y, z, 50);
                }
            }
        }
        set(image, 6, 4, 1, 30);
        return image;
    }

    public static ImagePlus morphologyStack() {
        ImagePlus image = emptyStack(8, 6, 3);
        set(image, 0, 0, 0, 85);

        set(image, 2, 0, 0, 60);
        set(image, 3, 0, 0, 60);
        set(image, 4, 0, 0, 60);

        for (int z = 0; z <= 1; z++) {
            for (int y = 2; y <= 3; y++) {
                for (int x = 0; x <= 1; x++) {
                    set(image, x, y, z, 45);
                }
            }
        }

        set(image, 5, 3, 0, 35);
        set(image, 5, 4, 0, 35);
        set(image, 6, 4, 0, 35);
        set(image, 5, 4, 1, 35);

        set(image, 3, 3, 2, 50);
        set(image, 4, 3, 2, 40);
        set(image, 5, 3, 2, 30);
        return image;
    }

    public static ImagePlus nonIncreasingAttributeStack() {
        ImagePlus image = emptyStack(6, 4, 3);
        for (int z = 0; z <= 1; z++) {
            for (int y = 0; y <= 1; y++) {
                for (int x = 0; x <= 1; x++) {
                    set(image, x, y, z, 60);
                }
            }
        }
        set(image, 2, 0, 0, 20);
        set(image, 3, 0, 0, 20);
        set(image, 4, 0, 0, 20);
        return image;
    }

    public static ImagePlus emptyStack(int width, int height, int depth) {
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < depth; z++) {
            stack.addSlice("z" + (z + 1), new ShortProcessor(width, height));
        }
        return new ImagePlus("component-tree oracle fixture", stack);
    }

    public static void set(ImagePlus image, int x, int y, int z, int value) {
        image.getStack().getProcessor(z + 1).set(x, y, value);
    }

    public static void assertEquivalentToOracle(ImagePlus source,
                                                ComponentTreeResult treeResult,
                                                LabelResult oracle) {
        assertEquals(oracle.objectCount(), treeResult.objectCount());
        ImagePlus treeLabels = treeResult.labelMap().get();
        assertLabelGeometry(source, oracle.labels(), treeLabels);
        assertEquals(oracle.objectCount(), objectVoxelSets(oracle.labels()).size());
        assertEquals(treeResult.objectCount(), objectVoxelSets(treeLabels).size());
        assertEquals("Labels are matched by object voxel set, not by raw label id.",
                objectVoxelSets(oracle.labels()), objectVoxelSets(treeLabels));
        assertArrayEquals(sortedObjectSizes(oracle.labels()), sortedObjectSizes(treeLabels));
    }

    public static List<List<Integer>> oracleSurvivingVoxelSets(ImagePlus source,
                                                               int threshold,
                                                               int minSize,
                                                               int maxSize,
                                                               SegSweepLabeller.Connectivity connectivity,
                                                               MorphologyPredicate... predicates) {
        LabelResult oracle = SegSweepLabeller.label(source, threshold, minSize, maxSize, connectivity);
        List<FeatureObject> objects = featureObjects(source, oracle.labels());
        List<List<Integer>> surviving = new ArrayList<List<Integer>>();
        for (int i = 0; i < objects.size(); i++) {
            FeatureObject object = objects.get(i);
            if (matchesAll(object, predicates)) {
                surviving.add(object.voxels);
            }
        }
        return canonicalize(surviving);
    }

    public static List<List<Integer>> treeVoxelSets(ComponentTreeResult result) {
        return objectVoxelSets(result.labelMap().get());
    }

    public static double[] finiteOracleValues(ImagePlus source,
                                              int threshold,
                                              MorphologyAttribute attribute,
                                              SegSweepLabeller.Connectivity connectivity) {
        LabelResult oracle = SegSweepLabeller.label(source, threshold, 1, Integer.MAX_VALUE, connectivity);
        List<FeatureObject> objects = featureObjects(source, oracle.labels());
        List<Double> values = new ArrayList<Double>();
        for (int i = 0; i < objects.size(); i++) {
            double value = objects.get(i).attribute(attribute);
            if (Double.isFinite(value)) {
                values.add(Double.valueOf(value));
            }
        }
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i).doubleValue();
        }
        Arrays.sort(out);
        return out;
    }

    public static List<List<Integer>> objectVoxelSets(ImagePlus labels) {
        return canonicalize(rawObjectVoxelSets(labels));
    }

    private static void assertLabelGeometry(ImagePlus source, ImagePlus oracleLabels, ImagePlus treeLabels) {
        assertEquals(source.getWidth(), treeLabels.getWidth());
        assertEquals(source.getHeight(), treeLabels.getHeight());
        assertEquals(source.getStackSize(), treeLabels.getStackSize());
        assertEquals(oracleLabels.getWidth(), treeLabels.getWidth());
        assertEquals(oracleLabels.getHeight(), treeLabels.getHeight());
        assertEquals(oracleLabels.getStackSize(), treeLabels.getStackSize());
        assertEquals(16, treeLabels.getBitDepth());
        assertEquals(oracleLabels.getCalibration().pixelWidth, treeLabels.getCalibration().pixelWidth, 0.0);
        assertEquals(oracleLabels.getCalibration().pixelHeight, treeLabels.getCalibration().pixelHeight, 0.0);
        assertEquals(oracleLabels.getCalibration().pixelDepth, treeLabels.getCalibration().pixelDepth, 0.0);
        assertEquals(oracleLabels.getCalibration().getUnit(), treeLabels.getCalibration().getUnit());
    }

    private static boolean matchesAll(FeatureObject object, MorphologyPredicate... predicates) {
        for (int i = 0; i < predicates.length; i++) {
            MorphologyPredicate predicate = predicates[i];
            if (!predicate.matches(object.attribute(predicate.attribute()))) {
                return false;
            }
        }
        return true;
    }

    private static int[] sortedObjectSizes(ImagePlus labels) {
        List<List<Integer>> sets = objectVoxelSets(labels);
        int[] sizes = new int[sets.size()];
        for (int i = 0; i < sets.size(); i++) {
            sizes[i] = sets.get(i).size();
        }
        Arrays.sort(sizes);
        return sizes;
    }

    private static List<List<Integer>> rawObjectVoxelSets(ImagePlus labels) {
        int width = labels.getWidth();
        int height = labels.getHeight();
        int plane = width * height;
        int maxLabel = 0;
        ImageStack stack = labels.getStack();
        for (int z = 0; z < stack.getSize(); z++) {
            ImageProcessor processor = stack.getProcessor(z + 1);
            for (int i = 0; i < processor.getPixelCount(); i++) {
                int value = processor.get(i);
                if (value > maxLabel) {
                    maxLabel = value;
                }
            }
        }
        List<List<Integer>> byLabel = new ArrayList<List<Integer>>();
        for (int i = 0; i <= maxLabel; i++) {
            byLabel.add(new ArrayList<Integer>());
        }
        for (int z = 0; z < stack.getSize(); z++) {
            ImageProcessor processor = stack.getProcessor(z + 1);
            for (int i = 0; i < processor.getPixelCount(); i++) {
                int value = processor.get(i);
                if (value > 0) {
                    byLabel.get(value).add(Integer.valueOf(z * plane + i));
                }
            }
        }
        List<List<Integer>> out = new ArrayList<List<Integer>>();
        for (int label = 1; label < byLabel.size(); label++) {
            if (!byLabel.get(label).isEmpty()) {
                out.add(byLabel.get(label));
            }
        }
        return out;
    }

    private static List<List<Integer>> canonicalize(List<List<Integer>> sets) {
        List<List<Integer>> out = new ArrayList<List<Integer>>();
        for (int i = 0; i < sets.size(); i++) {
            List<Integer> copy = new ArrayList<Integer>(sets.get(i));
            Collections.sort(copy);
            out.add(copy);
        }
        Collections.sort(out, new Comparator<List<Integer>>() {
            @Override public int compare(List<Integer> a, List<Integer> b) {
                int size = Math.min(a.size(), b.size());
                for (int i = 0; i < size; i++) {
                    int cmp = a.get(i).compareTo(b.get(i));
                    if (cmp != 0) return cmp;
                }
                return Integer.compare(a.size(), b.size());
            }
        });
        return out;
    }

    private static List<FeatureObject> featureObjects(ImagePlus source, ImagePlus labels) {
        List<List<Integer>> sets = rawObjectVoxelSets(labels);
        List<FeatureObject> objects = new ArrayList<FeatureObject>();
        for (int i = 0; i < sets.size(); i++) {
            objects.add(new FeatureObject(source, sets.get(i)));
        }
        return objects;
    }

    private static final class FeatureObject {
        final ImagePlus source;
        final List<Integer> voxels;
        final int width;
        final int height;
        final int plane;
        final int volume;
        double intensitySum;
        double maxIntensity = Double.NEGATIVE_INFINITY;
        double surfaceArea;
        double xSum;
        double ySum;
        double zSum;
        double xxSum;
        double yySum;
        double zzSum;
        double xySum;
        double xzSum;
        double yzSum;
        boolean[] mask;

        FeatureObject(ImagePlus source, List<Integer> voxels) {
            this.source = source;
            this.voxels = new ArrayList<Integer>(voxels);
            this.width = source.getWidth();
            this.height = source.getHeight();
            this.plane = width * height;
            this.volume = voxels.size();
            this.mask = new boolean[width * height * source.getStackSize()];
            for (int i = 0; i < voxels.size(); i++) {
                mask[voxels.get(i).intValue()] = true;
            }
            accumulate();
        }

        double attribute(MorphologyAttribute attribute) {
            if (attribute == MorphologyAttribute.VOLUME) return volume;
            if (attribute == MorphologyAttribute.MEAN_INTENSITY) return intensitySum / (double) volume;
            if (attribute == MorphologyAttribute.MAX_INTENSITY) return maxIntensity;
            if (attribute == MorphologyAttribute.ELONGATION) return elongation();
            if (attribute == MorphologyAttribute.SURFACE_AREA) return surfaceArea;
            if (attribute == MorphologyAttribute.SPHERICITY) return sphericity();
            if (attribute == MorphologyAttribute.COMPACTNESS) return compactness();
            if (attribute == MorphologyAttribute.FERET_DIAMETER_MAX) return feret();
            return Double.NaN;
        }

        private void accumulate() {
            for (int i = 0; i < voxels.size(); i++) {
                int voxel = voxels.get(i).intValue();
                int z = voxel / plane;
                int rem = voxel - z * plane;
                int y = rem / width;
                int x = rem - y * width;
                double value = source.getStack().getProcessor(z + 1).getf(x, y);
                intensitySum += value;
                if (value > maxIntensity) {
                    maxIntensity = value;
                }
                xSum += x;
                ySum += y;
                zSum += z;
                xxSum += x * x;
                yySum += y * y;
                zzSum += z * z;
                xySum += x * y;
                xzSum += x * z;
                yzSum += y * z;
                surfaceArea += exposedFaces(x, y, z);
            }
        }

        private int exposedFaces(int x, int y, int z) {
            int exposed = 0;
            if (!contains(x - 1, y, z)) exposed++;
            if (!contains(x + 1, y, z)) exposed++;
            if (!contains(x, y - 1, z)) exposed++;
            if (!contains(x, y + 1, z)) exposed++;
            if (!contains(x, y, z - 1)) exposed++;
            if (!contains(x, y, z + 1)) exposed++;
            return exposed;
        }

        private boolean contains(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0
                    || x >= width || y >= height || z >= source.getStackSize()) {
                return false;
            }
            return mask[z * plane + y * width + x];
        }

        private double sphericity() {
            if (volume <= 0 || surfaceArea <= 0.0) return Double.NaN;
            return Math.pow(Math.PI, 1.0 / 3.0)
                    * Math.pow(6.0 * volume, 2.0 / 3.0)
                    / surfaceArea;
        }

        private double compactness() {
            if (volume <= 0 || surfaceArea <= 0.0) return Double.NaN;
            return (36.0 * Math.PI * volume * volume)
                    / (surfaceArea * surfaceArea * surfaceArea);
        }

        private double elongation() {
            if (volume <= 1) return Double.NaN;
            double inv = 1.0 / (double) volume;
            double cx = xSum * inv;
            double cy = ySum * inv;
            double cz = zSum * inv;
            double cxx = xxSum * inv - cx * cx;
            double cyy = yySum * inv - cy * cy;
            double czz = zzSum * inv - cz * cz;
            double cxy = xySum * inv - cx * cy;
            double cxz = xzSum * inv - cx * cz;
            double cyz = yzSum * inv - cy * cz;
            double[] eigenvalues = eigenvalues(cxx, cxy, cxz, cyy, cyz, czz);
            Arrays.sort(eigenvalues);
            double smallest = zeroIfTiny(eigenvalues[0]);
            double largest = zeroIfTiny(eigenvalues[2]);
            if (largest <= 0.0 || smallest <= 0.0) return Double.NaN;
            return Math.sqrt(largest / smallest);
        }

        private double feret() {
            if (voxels.size() <= 1) {
                return 0.0;
            }
            double max = 0.0;
            for (int i = 0; i < voxels.size(); i++) {
                int a = voxels.get(i).intValue();
                int az = a / plane;
                int ar = a - az * plane;
                int ay = ar / width;
                int ax = ar - ay * width;
                for (int j = i + 1; j < voxels.size(); j++) {
                    int b = voxels.get(j).intValue();
                    int bz = b / plane;
                    int br = b - bz * plane;
                    int by = br / width;
                    int bx = br - by * width;
                    double dx = ax - bx;
                    double dy = ay - by;
                    double dz = az - bz;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > max) {
                        max = distance;
                    }
                }
            }
            return max;
        }

        private static double[] eigenvalues(double cxx,
                                            double cxy,
                                            double cxz,
                                            double cyy,
                                            double cyz,
                                            double czz) {
            double p1 = cxy * cxy + cxz * cxz + cyz * cyz;
            if (p1 == 0.0) {
                return new double[] { cxx, cyy, czz };
            }
            double q = (cxx + cyy + czz) / 3.0;
            double axx = cxx - q;
            double ayy = cyy - q;
            double azz = czz - q;
            double p2 = axx * axx + ayy * ayy + azz * azz + 2.0 * p1;
            double p = Math.sqrt(p2 / 6.0);
            if (!Double.isFinite(p) || p <= 0.0) {
                return new double[] { cxx, cyy, czz };
            }
            double bxx = axx / p;
            double byy = ayy / p;
            double bzz = azz / p;
            double bxy = cxy / p;
            double bxz = cxz / p;
            double byz = cyz / p;
            double determinant = bxx * (byy * bzz - byz * byz)
                    - bxy * (bxy * bzz - byz * bxz)
                    + bxz * (bxy * byz - byy * bxz);
            double r = determinant / 2.0;
            double phi;
            if (r <= -1.0) {
                phi = Math.PI / 3.0;
            } else if (r >= 1.0) {
                phi = 0.0;
            } else {
                phi = Math.acos(r) / 3.0;
            }
            double largest = q + 2.0 * p * Math.cos(phi);
            double smallest = q + 2.0 * p * Math.cos(phi + (2.0 * Math.PI / 3.0));
            double middle = 3.0 * q - largest - smallest;
            return new double[] { largest, middle, smallest };
        }

        private static double zeroIfTiny(double value) {
            if (!Double.isFinite(value)) return Double.NaN;
            return Math.abs(value) <= 1.0e-12 ? 0.0 : value;
        }
    }
}
