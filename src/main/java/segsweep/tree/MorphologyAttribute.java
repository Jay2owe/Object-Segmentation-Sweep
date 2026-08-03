/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

public enum MorphologyAttribute {
    VOLUME("volume"),
    MEAN_INTENSITY("mean_intensity"),
    MAX_INTENSITY("max_intensity"),
    ELONGATION("elongation"),
    SURFACE_AREA("surface_area"),
    SPHERICITY("sphericity"),
    COMPACTNESS("compactness"),
    FERET_DIAMETER_MAX("feret_diameter_max");

    private final String token;

    MorphologyAttribute(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static MorphologyAttribute fromToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Morphology attribute token must not be null.");
        }
        String normalized = token.trim().toLowerCase(java.util.Locale.ROOT);
        for (MorphologyAttribute attribute : values()) {
            if (attribute.token.equals(normalized)) {
                return attribute;
            }
        }
        throw new IllegalArgumentException("Unknown morphology attribute '" + token + "'.");
    }
}
