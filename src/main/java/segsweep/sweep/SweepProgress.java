/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

public final class SweepProgress {
    private final int completed;
    private final int total;
    private final int failed;
    private final ParameterCombo current;
    private final String phase;
    private final String message;

    public SweepProgress(int completed,
                         int total,
                         int failed,
                         ParameterCombo current,
                         String phase,
                         String message) {
        if (completed < 0) {
            throw new IllegalArgumentException("completed must not be negative");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        if (failed < 0) {
            throw new IllegalArgumentException("failed must not be negative");
        }
        if (completed > total) {
            throw new IllegalArgumentException("completed must not exceed total");
        }
        if (failed > completed) {
            throw new IllegalArgumentException("failed must not exceed completed");
        }
        this.completed = completed;
        this.total = total;
        this.failed = failed;
        this.current = current;
        this.phase = phase == null ? "" : phase;
        this.message = message == null ? "" : message;
    }

    public int completed() {
        return completed;
    }

    public int total() {
        return total;
    }

    public int failed() {
        return failed;
    }

    public ParameterCombo current() {
        return current;
    }

    public String phase() {
        return phase;
    }

    public String message() {
        return message;
    }
}
