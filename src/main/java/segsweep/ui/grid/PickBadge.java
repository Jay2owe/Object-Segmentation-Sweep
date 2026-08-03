/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import java.awt.Color;

public final class PickBadge {
    public enum Kind {
        KNEE,
        STABILITY,
        BOTH
    }

    private final Kind kind;

    public PickBadge(Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    Color color() {
        if (kind == Kind.KNEE) {
            return new Color(0xF0, 0xE4, 0x42);
        }
        if (kind == Kind.STABILITY) {
            return new Color(0x56, 0xB4, 0xE9);
        }
        return new Color(0x00, 0x9E, 0x73);
    }

    String label() {
        if (kind == Kind.KNEE) {
            return "Knee";
        }
        if (kind == Kind.STABILITY) {
            return "Stability";
        }
        return "Both";
    }
}
