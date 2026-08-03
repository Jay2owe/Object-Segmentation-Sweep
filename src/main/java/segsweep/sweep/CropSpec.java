/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CropSpec implements Serializable {

    public enum Mode {
        FULL,
        CENTRE_256,
        CUSTOM
    }

    private static final long serialVersionUID = 1L;
    private static final int CENTRE_SIZE = 256;

    private final Mode mode;
    private final Rectangle bounds;

    private CropSpec(Mode mode, Rectangle bounds) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        this.mode = mode;
        this.bounds = bounds == null ? null : new Rectangle(bounds);
        if (mode == Mode.CUSTOM) {
            if (this.bounds == null || this.bounds.width <= 0 || this.bounds.height <= 0) {
                throw new IllegalArgumentException("custom crop bounds must be positive");
            }
        }
    }

    public static CropSpec full() {
        return new CropSpec(Mode.FULL, null);
    }

    public static CropSpec centre256() {
        return new CropSpec(Mode.CENTRE_256, null);
    }

    public static CropSpec custom(Rectangle bounds) {
        return new CropSpec(Mode.CUSTOM, bounds);
    }

    public Mode mode() {
        return mode;
    }

    public Mode getMode() {
        return mode;
    }

    public Rectangle bounds() {
        return bounds == null ? null : new Rectangle(bounds);
    }

    public Rectangle getBounds() {
        return bounds();
    }

    public ImagePlus apply(ImagePlus source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (mode == Mode.FULL) {
            return source;
        }
        Rectangle resolved = boundsFor(source);
        ImageStack input = source.getStack();
        ImageStack output = new ImageStack(resolved.width, resolved.height);
        for (int i = 1; i <= input.getSize(); i++) {
            ImageProcessor processor = input.getProcessor(i).duplicate();
            processor.setRoi(resolved.x, resolved.y, resolved.width, resolved.height);
            output.addSlice(input.getSliceLabel(i), processor.crop());
        }
        ImagePlus cropped = new ImagePlus(source.getTitle(), output);
        if (source.getCalibration() != null) {
            cropped.setCalibration(source.getCalibration().copy());
        }
        cropped.setDimensions(1, output.getSize(), 1);
        cropped.setOpenAsHyperStack(output.getSize() > 1);
        return cropped;
    }

    /**
     * Crops every plane while preserving the source channel/slice/frame dimensions.
     * The per-plane crop avoids IJ1 duplicator shared state and preserves parent
     * ownership semantics: full mode returns the input unchanged.
     */
    public ImagePlus applyMultiChannel(ImagePlus source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (mode == Mode.FULL) {
            return source;
        }
        Rectangle resolved = boundsFor(source);
        ImageStack input = source.getStack();
        ImageStack output = new ImageStack(resolved.width, resolved.height);
        for (int i = 1; i <= input.getSize(); i++) {
            ImageProcessor processor = input.getProcessor(i).duplicate();
            processor.setRoi(resolved.x, resolved.y, resolved.width, resolved.height);
            output.addSlice(input.getSliceLabel(i), processor.crop());
        }
        ImagePlus cropped = new ImagePlus(source.getTitle(), output);
        if (source.getCalibration() != null) {
            cropped.setCalibration(source.getCalibration().copy());
        }
        cropped.setDimensions(source.getNChannels(), source.getNSlices(), source.getNFrames());
        cropped.setOpenAsHyperStack(output.getSize() > 1);
        return cropped;
    }

    public Rectangle boundsFor(ImagePlus source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return boundsFor(source.getWidth(), source.getHeight());
    }

    public Rectangle boundsFor(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("source dimensions must be positive");
        }
        Rectangle imageBounds = new Rectangle(0, 0, width, height);
        Rectangle requested;
        if (mode == Mode.FULL) {
            requested = imageBounds;
        } else if (mode == Mode.CENTRE_256) {
            int cropWidth = Math.min(CENTRE_SIZE, width);
            int cropHeight = Math.min(CENTRE_SIZE, height);
            int x = Math.max(0, (width - cropWidth) / 2);
            int y = Math.max(0, (height - cropHeight) / 2);
            requested = new Rectangle(x, y, cropWidth, cropHeight);
        } else {
            requested = new Rectangle(bounds);
        }
        Rectangle clipped = requested.intersection(imageBounds);
        if (clipped.width <= 0 || clipped.height <= 0) {
            throw new IllegalArgumentException("crop bounds do not overlap the source image");
        }
        return clipped;
    }

    public LinkedHashMap<String, Object> toCanonicalObject() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("mode", mode.name());
        if (mode == Mode.CUSTOM && bounds != null) {
            root.put("height", Integer.valueOf(bounds.height));
            root.put("width", Integer.valueOf(bounds.width));
            root.put("x", Integer.valueOf(bounds.x));
            root.put("y", Integer.valueOf(bounds.y));
        }
        return root;
    }

    public String toCanonicalJson() {
        return CanonicalJson.write(toCanonicalObject());
    }

    public static CropSpec fromCanonicalObject(Map<String, Object> root) {
        if (root == null) {
            throw new IllegalArgumentException("crop JSON object must not be null");
        }
        Object rawMode = root.get("mode");
        if (!(rawMode instanceof String)) {
            throw new IllegalArgumentException("crop mode is missing");
        }
        Mode parsedMode = Mode.valueOf(((String) rawMode).trim());
        if (parsedMode != Mode.CUSTOM) {
            return new CropSpec(parsedMode, null);
        }
        return custom(new Rectangle(
                intValue(root.get("x"), "x"),
                intValue(root.get("y"), "y"),
                intValue(root.get("width"), "width"),
                intValue(root.get("height"), "height")));
    }

    private static int intValue(Object value, String field) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("crop " + field + " is missing");
        }
        long parsed = ((Number) value).longValue();
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("crop " + field + " is out of integer range");
        }
        return (int) parsed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CropSpec)) return false;
        CropSpec other = (CropSpec) obj;
        if (mode != other.mode) return false;
        return bounds == null ? other.bounds == null : bounds.equals(other.bounds);
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + (bounds == null ? 0 : bounds.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return toCanonicalJson();
    }
}
