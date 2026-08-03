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

public final class ComponentTreeQuery {
    private final int threshold;
    private final int minSize;
    private final int maxSize;
    private final List<MorphologyPredicate> predicates;

    private ComponentTreeQuery(Builder builder) {
        this.threshold = builder.threshold;
        this.minSize = Math.max(0, builder.minSize);
        this.maxSize = Math.max(0, builder.maxSize);
        this.predicates = Collections.unmodifiableList(
                new ArrayList<MorphologyPredicate>(builder.predicates));
    }

    public int threshold() {
        return threshold;
    }

    public int minSize() {
        return minSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public List<MorphologyPredicate> predicates() {
        return predicates;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int threshold;
        private int minSize;
        private int maxSize = Integer.MAX_VALUE;
        private final List<MorphologyPredicate> predicates = new ArrayList<MorphologyPredicate>();

        private Builder() {}

        public Builder threshold(int threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder minSize(int minSize) {
            this.minSize = minSize;
            return this;
        }

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder predicate(MorphologyAttribute attribute, String operator, double value) {
            return predicate(MorphologyPredicate.of(attribute, operator, value));
        }

        public Builder predicate(MorphologyPredicate predicate) {
            if (predicate == null) {
                throw new IllegalArgumentException("Morphology predicate must not be null.");
            }
            predicates.add(predicate);
            return this;
        }

        public ComponentTreeQuery build() {
            return new ComponentTreeQuery(this);
        }
    }
}
