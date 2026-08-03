/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

public final class SweepRefusedException extends Exception {
    private static final long serialVersionUID = 1L;

    public SweepRefusedException(String message) {
        super(message == null || message.trim().isEmpty()
                ? "Parameter sweep was refused." : message.trim());
    }
}
