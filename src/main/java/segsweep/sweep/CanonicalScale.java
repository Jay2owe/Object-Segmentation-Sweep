/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import java.math.BigDecimal;
import java.util.Locale;

public enum CanonicalScale {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    private final String label;

    CanonicalScale(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CanonicalScale fromLabel(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (CanonicalScale scale : values()) {
            if (scale.label.equals(normalized)
                    || scale.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return scale;
            }
        }
        return null;
    }

    public static String format(double value) {
        return formatNumber(Double.valueOf(value));
    }

    public static String formatNumber(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("number must not be null");
        }
        if (value instanceof Float || value instanceof Double) {
            double d = value.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("number must be finite");
            }
            if (d == 0.0d) {
                return "0";
            }
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return String.valueOf(value.longValue());
        }
        return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    }

    public static final class ScaleValue {
        private static final ScaleValue NONE = new ScaleValue("", 0.0d, true);

        private final String paramKey;
        private final double value;
        private final boolean none;

        private ScaleValue(String paramKey, double value, boolean none) {
            this.paramKey = paramKey == null ? "" : paramKey;
            this.value = value;
            this.none = none;
        }

        public static ScaleValue of(String paramKey, double value) {
            String key = paramKey == null ? "" : paramKey.trim();
            if (key.isEmpty()) {
                return none();
            }
            return new ScaleValue(key, value, false);
        }

        public static ScaleValue none() {
            return NONE;
        }

        public boolean isNone() {
            return none;
        }

        public boolean hasValue() {
            return !none;
        }

        public String paramKey() {
            return paramKey;
        }

        public double value() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ScaleValue)) return false;
            ScaleValue other = (ScaleValue) obj;
            if (none != other.none) return false;
            if (none) return true;
            return paramKey.equals(other.paramKey)
                    && Double.compare(value, other.value) == 0;
        }

        @Override
        public int hashCode() {
            int result = paramKey.hashCode();
            long bits = Double.doubleToLongBits(value);
            result = 31 * result + (int) (bits ^ (bits >>> 32));
            result = 31 * result + (none ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return none ? "default" : paramKey + "=" + format(value);
        }
    }
}
