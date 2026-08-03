/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utilities for converting between ROI sets and label images.
 */
public class LabelUtils {

    /**
     * Load ROIs from a .zip or single .roi file.
     */
    public static Roi[] loadRoiSet(String path) throws IOException {
        if (path.toLowerCase().endsWith(".roi")) {
            Roi roi = new RoiDecoder(path).getRoi();
            return roi != null ? new Roi[]{roi} : new Roi[0];
        }

        List<Roi> rois = new ArrayList<Roi>();
        byte[] buf = new byte[65536];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(path));
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".roi")) continue;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buf)) > 0) baos.write(buf, 0, n);
                Roi roi = new RoiDecoder(baos.toByteArray(), entry.getName()).getRoi();
                if (roi != null) rois.add(roi);
            }
        } finally {
            zis.close();
        }
        return rois.toArray(new Roi[0]);
    }

    /**
     * Convert an array of ROIs into a 16-bit label image.
     * Each ROI gets a unique label (1, 2, 3, ...).
     * ROIs with a z-position are drawn on that slice only;
     * ROIs without z-position are drawn on all slices.
     *
     * @param reference source image for dimensions and calibration
     * @param rois      the ROIs to convert
     * @return 16-bit label image
     */
    public static ImagePlus roiSetToLabelImage(ImagePlus reference, Roi[] rois) {
        int w = reference.getWidth();
        int h = reference.getHeight();
        int nSlices = Math.max(1, reference.getNSlices());

        ImageStack stack = new ImageStack(w, h);
        for (int z = 0; z < nSlices; z++) {
            stack.addSlice(new ShortProcessor(w, h));
        }

        for (int i = 0; i < rois.length; i++) {
            Roi roi = rois[i];
            int label = i + 1;
            int zPos = roi.getZPosition();

            if (zPos > 0 && zPos <= nSlices) {
                fillRoi(stack.getProcessor(zPos), roi, label);
            } else {
                for (int z = 1; z <= nSlices; z++) {
                    fillRoi(stack.getProcessor(z), roi, label);
                }
            }
        }

        ImagePlus result = new ImagePlus("Labels from ROIs", stack);
        if (reference.getCalibration() != null) {
            result.setCalibration(reference.getCalibration().copy());
        }
        return result;
    }

    private static void fillRoi(ImageProcessor ip, Roi roi, int label) {
        Rectangle bounds = roi.getBounds();
        ImageProcessor mask = roi.getMask();

        for (int y = 0; y < bounds.height; y++) {
            for (int x = 0; x < bounds.width; x++) {
                if (mask == null || mask.getPixel(x, y) > 0) {
                    int gx = bounds.x + x;
                    int gy = bounds.y + y;
                    if (gx >= 0 && gx < ip.getWidth() && gy >= 0 && gy < ip.getHeight()) {
                        ip.set(gx, gy, label);
                    }
                }
            }
        }
    }
}
