/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

public final class VariationCleanupSupport {
    private VariationCleanupSupport() {
    }

    public static Throwable disposeRejectedResult(VariationResult result) {
        if (result == null) {
            return null;
        }
        Throwable failure = null;
        result.transferOwnership();
        try {
            result.releaseTransferredImages();
        } catch (Throwable cleanupFailure) {
            failure = merge(failure, cleanupFailure);
        }
        if (result.pendingTransferredImages().length > 0) {
            if (isVmFatal(failure)) {
                VariationCleanupCoordinator.registerResultFatal(result);
            } else {
                VariationCleanupCoordinator.registerResult(result);
            }
        }
        return failure;
    }

    static void retainRejectedResultAfterFatal(VariationResult result) {
        if (result == null) {
            return;
        }
        result.transferOwnership();
        VariationCleanupCoordinator.registerResultFatal(result);
    }

    public static Throwable disposeProducerOwnedRejectedResult(VariationResult result) {
        if (result == null || !result.hasDirectOwnership()) {
            return null;
        }
        return disposeRejectedResult(result);
    }

    static Throwable merge(Throwable primary, Throwable additional) {
        if (additional == null) {
            return primary;
        }
        if (primary == null) {
            return additional;
        }
        if (isVmFatal(additional) && !isVmFatal(primary)) {
            addSuppressed(additional, primary);
            return additional;
        }
        addSuppressed(primary, additional);
        return primary;
    }

    static boolean isVmFatal(Throwable failure) {
        return failure instanceof ThreadDeath || failure instanceof VirtualMachineError;
    }

    static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof ThreadDeath) {
            throw (ThreadDeath) failure;
        }
        if (failure instanceof VirtualMachineError) {
            throw (VirtualMachineError) failure;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Could not dispose variation result.", failure);
    }

    private static void addSuppressed(Throwable primary, Throwable suppressed) {
        if (primary == suppressed) {
            return;
        }
        try {
            primary.addSuppressed(suppressed);
        } catch (RuntimeException ignored) {
            // Suppression is diagnostic only.
        }
    }
}
