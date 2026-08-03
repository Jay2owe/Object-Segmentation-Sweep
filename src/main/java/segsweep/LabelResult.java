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

import java.util.Arrays;

public final class LabelResult {
    public enum Status { OK, EMPTY, TOO_MANY_LABELS }

    private final Status status;
    private final String reason;
    private final ImagePlus labels;
    private final int objectCount;
    private final int[] objectSizes;

    private LabelResult(Status status,
                        String reason,
                        ImagePlus labels,
                        int objectCount,
                        int[] objectSizes) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (labels == null) {
            throw new IllegalArgumentException("labels must not be null");
        }
        this.status = status;
        this.reason = reason == null ? "" : reason;
        this.labels = labels;
        this.objectCount = objectCount;
        this.objectSizes = objectSizes == null ? new int[0] : Arrays.copyOf(objectSizes, objectSizes.length);
    }

    static LabelResult ok(ImagePlus labels, int objectCount, int[] objectSizes) {
        return new LabelResult(Status.OK, "", labels, objectCount, objectSizes);
    }

    static LabelResult empty(ImagePlus labels, String reason) {
        return new LabelResult(Status.EMPTY, reason, labels, 0, new int[] { 0 });
    }

    static LabelResult tooManyLabels(ImagePlus labels, int objectCount) {
        return new LabelResult(Status.TOO_MANY_LABELS,
                "Final label count " + objectCount + " exceeds the 16-bit limit of 65535.",
                labels,
                objectCount,
                new int[0]);
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public ImagePlus labels() {
        return labels;
    }

    public int objectCount() {
        return objectCount;
    }

    public int[] objectSizes() {
        return Arrays.copyOf(objectSizes, objectSizes.length);
    }
}
