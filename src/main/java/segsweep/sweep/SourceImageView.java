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
import ij.measure.Calibration;
import ij.process.ImageProcessor;

/** Creates an owned, single-channel, first-timepoint view of the analysed crop. */
public final class SourceImageView {
    private SourceImageView() {
    }

    public static ImagePlus selectedChannelAndCrop(ImagePlus source,
                                                   int channel,
                                                   CropSpec crop) {
        if (source == null || source.getStack() == null) {
            throw new IllegalArgumentException("source image must not be null");
        }
        int channels = Math.max(1, source.getNChannels());
        if (channel < 1 || channel > channels) {
            throw new IllegalArgumentException("channel must be in 1.." + channels);
        }
        int slices = Math.max(1, source.getNSlices());
        ImageStack selectedStack = new ImageStack(source.getWidth(), source.getHeight());
        for (int z = 1; z <= slices; z++) {
            int index = source.getStackIndex(channel, z, 1);
            ImageProcessor plane = source.getStack().getProcessor(index).duplicate();
            selectedStack.addSlice(source.getStack().getSliceLabel(index), plane);
        }
        ImagePlus selected = new ImagePlus(source.getTitle() + " C" + channel, selectedStack);
        Calibration calibration = source.getCalibration();
        if (calibration != null) selected.setCalibration(calibration.copy());
        selected.setDimensions(1, slices, 1);
        selected.setOpenAsHyperStack(slices > 1);

        CropSpec safeCrop = crop == null ? CropSpec.full() : crop;
        ImagePlus cropped = safeCrop.apply(selected);
        if (cropped != selected) {
            selected.changes = false;
            selected.close();
            selected.flush();
        }
        return cropped;
    }
}
