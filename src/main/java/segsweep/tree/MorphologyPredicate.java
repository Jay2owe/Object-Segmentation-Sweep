/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

public final class MorphologyPredicate {
    public enum Operator {
        GE(">="),
        LE("<="),
        GT(">"),
        LT("<");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        static Operator fromSymbol(String symbol) {
            if (symbol == null) {
                throw new IllegalArgumentException("Predicate operator must not be null.");
            }
            for (Operator operator : values()) {
                if (operator.symbol.equals(symbol.trim())) {
                    return operator;
                }
            }
            throw new IllegalArgumentException("Unsupported predicate operator '" + symbol + "'.");
        }
    }

    private final MorphologyAttribute attribute;
    private final Operator operator;
    private final double value;

    public MorphologyPredicate(MorphologyAttribute attribute, Operator operator, double value) {
        if (attribute == null) {
            throw new IllegalArgumentException("Morphology predicate attribute must not be null.");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Morphology predicate operator must not be null.");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Morphology predicate value must be finite.");
        }
        this.attribute = attribute;
        this.operator = operator;
        this.value = value;
    }

    public static MorphologyPredicate of(MorphologyAttribute attribute, String operator, double value) {
        return new MorphologyPredicate(attribute, Operator.fromSymbol(operator), value);
    }

    public MorphologyAttribute attribute() {
        return attribute;
    }

    public Operator operator() {
        return operator;
    }

    public double value() {
        return value;
    }

    boolean matches(double observed) {
        if (!Double.isFinite(observed)) {
            return false;
        }
        if (operator == Operator.GE) return observed >= value;
        if (operator == Operator.LE) return observed <= value;
        if (operator == Operator.GT) return observed > value;
        if (operator == Operator.LT) return observed < value;
        return false;
    }

    public String format() {
        return attribute.token() + operator.symbol() + Double.toString(value);
    }
}
