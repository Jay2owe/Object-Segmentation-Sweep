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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParameterValueList {

    private static final int MAX_RANGE_VALUES = 1000000;

    private final List<Object> values;
    private final String note;

    public ParameterValueList(List<?> values) {
        this(values, null);
    }

    /**
     * @param note optional caveat about how these display-window values were derived.
     */
    public ParameterValueList(List<?> values, String note) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        List<Object> copy = new ArrayList<Object>(values.size());
        for (int i = 0; i < values.size(); i++) {
            copy.add(normalizeValue(values.get(i)));
        }
        this.values = Collections.unmodifiableList(copy);
        this.note = note == null || note.trim().isEmpty() ? null : note.trim();
    }

    /**
     * Builds the public display/reporting window from {@code from}, {@code to} and
     * {@code step}. Classical segmentation still computes the whole component tree.
     */
    public static ParameterValueList fromRange(double from, double to, double step) {
        if (!Double.isFinite(from) || !Double.isFinite(to) || !Double.isFinite(step)) {
            throw new IllegalArgumentException("range values must be finite");
        }
        if (step == 0.0d) {
            throw new IllegalArgumentException("step must not be zero");
        }
        if ((to > from && step < 0.0d) || (to < from && step > 0.0d)) {
            throw new IllegalArgumentException("step direction must reach the end value");
        }
        BigDecimal start = BigDecimal.valueOf(from);
        BigDecimal end = BigDecimal.valueOf(to);
        BigDecimal increment = BigDecimal.valueOf(step);
        BigDecimal distance = end.subtract(start).abs();
        BigDecimal stepMagnitude = increment.abs();
        BigInteger intervals = distance.divideToIntegralValue(stepMagnitude).toBigIntegerExact();
        BigInteger countValue = intervals.add(BigInteger.ONE);
        if (countValue.compareTo(BigInteger.valueOf(MAX_RANGE_VALUES)) > 0) {
            throw new IllegalArgumentException("range produces too many values");
        }
        int count = countValue.intValue();
        List<Object> out = new ArrayList<Object>(count);
        double previous = Double.NaN;
        for (int i = 0; i < count; i++) {
            double value = start.add(increment.multiply(BigDecimal.valueOf(i))).doubleValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("range value is outside the finite double domain");
            }
            if (i > 0 && (step > 0.0d ? value <= previous : value >= previous)) {
                throw new IllegalArgumentException(
                        "step is too small to produce distinct values at this numeric scale");
            }
            out.add(Double.valueOf(value));
            previous = value;
        }
        return new ParameterValueList(out);
    }

    public static ParameterValueList of(List<?> values) {
        return new ParameterValueList(values);
    }

    public static ParameterValueList ofDoubles(double... values) {
        List<Object> out = new ArrayList<Object>();
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                out.add(Double.valueOf(values[i]));
            }
        }
        return new ParameterValueList(out);
    }

    public static ParameterValueList ofInts(int... values) {
        List<Object> out = new ArrayList<Object>();
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                out.add(Integer.valueOf(values[i]));
            }
        }
        return new ParameterValueList(out);
    }

    public static ParameterValueList ofStrings(String... values) {
        List<Object> out = new ArrayList<Object>();
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                out.add(values[i]);
            }
        }
        return new ParameterValueList(out);
    }

    public String note() {
        return note;
    }

    public boolean hasNote() {
        return note != null;
    }

    public List<Object> values() {
        return values;
    }

    public List<Object> getValues() {
        return values;
    }

    public Object get(int index) {
        return values.get(index);
    }

    public int size() {
        return values.size();
    }

    public String toCanonicalJson() {
        return CanonicalJson.write(values);
    }

    static Object normalizeValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("parameter values must not contain null");
        }
        if (value instanceof String) {
            return value;
        }
        if (value instanceof Integer) {
            return value;
        }
        if (value instanceof Short || value instanceof Byte) {
            return Integer.valueOf(((Number) value).intValue());
        }
        if (value instanceof Long) {
            long longValue = ((Long) value).longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return Integer.valueOf((int) longValue);
            }
            return Long.valueOf(longValue);
        }
        if (value instanceof Float || value instanceof Double) {
            double doubleValue = ((Number) value).doubleValue();
            if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
                throw new IllegalArgumentException("parameter numeric values must be finite");
            }
            return Double.valueOf(doubleValue);
        }
        throw new IllegalArgumentException("unsupported parameter value type: "
                + value.getClass().getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ParameterValueList)) return false;
        ParameterValueList other = (ParameterValueList) obj;
        return values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
