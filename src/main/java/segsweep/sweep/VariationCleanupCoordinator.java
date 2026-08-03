/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class VariationCleanupCoordinator {
    private static final Set<VariationResult> RESULTS =
            Collections.newSetFromMap(new IdentityHashMap<VariationResult, Boolean>());

    private VariationCleanupCoordinator() {
    }

    static synchronized void registerResult(VariationResult result) {
        if (result != null && result.pendingTransferredImages().length > 0) {
            RESULTS.add(result);
        }
    }

    static synchronized void registerResultFatal(VariationResult result) {
        registerResult(result);
    }

    static synchronized Throwable drainNowForTest() {
        Throwable failure = null;
        VariationResult[] snapshot = RESULTS.toArray(new VariationResult[RESULTS.size()]);
        for (int i = 0; i < snapshot.length; i++) {
            try {
                snapshot[i].releaseTransferredImages();
                if (snapshot[i].pendingTransferredImages().length == 0) {
                    RESULTS.remove(snapshot[i]);
                }
            } catch (Throwable cleanupFailure) {
                failure = VariationCleanupSupport.merge(failure, cleanupFailure);
            }
        }
        return failure;
    }

    static synchronized int pendingCountForTest() {
        return RESULTS.size();
    }

    static synchronized void resetForTest() {
        RESULTS.clear();
    }
}
