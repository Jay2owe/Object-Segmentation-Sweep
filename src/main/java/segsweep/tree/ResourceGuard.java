/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

public final class ResourceGuard {
    private ResourceGuard() {}

    public static Estimate estimateTreeMemory(int width, int height, int depth) {
        long voxels = multiply(multiply(Math.max(0, width), Math.max(0, height)), Math.max(0, depth));
        long unionFind = multiply(voxels, 9L); // int parent + byte rank + active flag.
        long nodeArrays = multiply(voxels, 40L); // parent, children refs, level, voxel range/index data.
        long attributes = multiply(voxels, 128L); // moments, bounds, intensity and surface attributes.
        long oneLazyLabelMap = multiply(voxels, 2L);
        long total = saturatingAdd(saturatingAdd(unionFind, nodeArrays),
                saturatingAdd(attributes, oneLazyLabelMap));
        return new Estimate(voxels, unionFind, nodeArrays, attributes, oneLazyLabelMap, total);
    }

    public static Decision checkTreeMemory(int width, int height, int depth, long maxBytes) {
        Estimate estimate = estimateTreeMemory(width, height, depth);
        if (estimate.totalBytes() > maxBytes) {
            return Decision.refused(estimate, "Estimated component-tree memory "
                    + estimate.totalBytes() + " bytes exceeds limit " + maxBytes + " bytes.");
        }
        return Decision.permitted(estimate);
    }

    private static long multiply(long a, long b) {
        if (a == 0L || b == 0L) return 0L;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static long saturatingAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }

    public static final class Estimate {
        private final long voxels;
        private final long unionFindBytes;
        private final long nodeArrayBytes;
        private final long attributeBytes;
        private final long oneLazyLabelMapBytes;
        private final long totalBytes;

        private Estimate(long voxels,
                         long unionFindBytes,
                         long nodeArrayBytes,
                         long attributeBytes,
                         long oneLazyLabelMapBytes,
                         long totalBytes) {
            this.voxels = voxels;
            this.unionFindBytes = unionFindBytes;
            this.nodeArrayBytes = nodeArrayBytes;
            this.attributeBytes = attributeBytes;
            this.oneLazyLabelMapBytes = oneLazyLabelMapBytes;
            this.totalBytes = totalBytes;
        }

        public long voxels() {
            return voxels;
        }

        public long unionFindBytes() {
            return unionFindBytes;
        }

        public long nodeArrayBytes() {
            return nodeArrayBytes;
        }

        public long attributeBytes() {
            return attributeBytes;
        }

        public long oneLazyLabelMapBytes() {
            return oneLazyLabelMapBytes;
        }

        public long totalBytes() {
            return totalBytes;
        }
    }

    public static final class Decision {
        public enum Status { PERMITTED, REFUSED }

        private final Status status;
        private final String reason;
        private final Estimate estimate;

        private Decision(Status status, String reason, Estimate estimate) {
            this.status = status;
            this.reason = reason == null ? "" : reason;
            this.estimate = estimate;
        }

        static Decision permitted(Estimate estimate) {
            return new Decision(Status.PERMITTED, "", estimate);
        }

        static Decision refused(Estimate estimate, String reason) {
            return new Decision(Status.REFUSED, reason, estimate);
        }

        public Status status() {
            return status;
        }

        public String reason() {
            return reason;
        }

        public Estimate estimate() {
            return estimate;
        }
    }
}
