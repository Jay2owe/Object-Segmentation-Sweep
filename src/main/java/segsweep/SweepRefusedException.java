/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

/**
 * Public typed refusal for sweeps that are valid but cannot safely run.
 */
public final class SweepRefusedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SweepRefusedException(String message) {
        super(message == null || message.trim().isEmpty()
                ? "Parameter sweep was refused." : message.trim());
    }

    public SweepRefusedException(String message, Throwable cause) {
        super(message == null || message.trim().isEmpty()
                ? "Parameter sweep was refused." : message.trim(), cause);
    }
}
