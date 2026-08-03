/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import javax.swing.SwingWorker;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class VariationExecutor extends SwingWorker<Void, VariationResult> {
    private final ParameterSweep sweep;
    private final VariationStrategy strategy;
    private final BiConsumer<VariationResult, Integer> onResult;
    private final Consumer<SweepProgress> onProgress;
    private final Set<VariationResult> acceptedResults =
            Collections.newSetFromMap(new IdentityHashMap<VariationResult, Boolean>());
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger lastProgressCompleted = new AtomicInteger();
    private final AtomicInteger lastProgressFailed = new AtomicInteger();
    private final int total;
    private int delivered;

    public VariationExecutor(ParameterSweep sweep,
                             VariationStrategy strategy,
                             BiConsumer<VariationResult, Integer> onResult,
                             Consumer<SweepProgress> onProgress) {
        if (sweep == null) {
            throw new IllegalArgumentException("sweep must not be null");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        this.sweep = sweep;
        this.strategy = strategy;
        this.onResult = onResult;
        this.onProgress = onProgress;
        long cellCount = sweep.cellCount();
        this.total = cellCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cellCount;
    }

    @Override
    protected Void doInBackground() throws Exception {
        emitProgress(new SweepProgress(0, total, 0, null,
                "starting", "Starting parameter sweep."));
        try {
            strategy.dispatch(sweep, this::publishResult, this::publishProgress,
                    this::isCancellationRequested);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancel(false);
        } catch (Throwable failure) {
            if (isFatal(failure)) {
                throwFailure(failure);
            }
            if (!isCancellationRequested()) {
                int done = completed.get();
                emitProgress(new SweepProgress(done, total, failed.get(), null,
                        "failed", safeMessage(failure)));
                throwFailure(failure);
            }
        }
        if (!isCancellationRequested()) {
            emitProgress(new SweepProgress(completed.get(), total, failed.get(), null,
                    "complete", "Parameter sweep complete."));
        }
        return null;
    }

    public void publishProgress(SweepProgress progress) {
        if (progress == null || isCancellationRequested()) {
            return;
        }
        emitProgress(progress);
    }

    void publishResult(VariationResult result) {
        if (result == null) {
            return;
        }
        synchronized (acceptedResults) {
            if (!acceptedResults.add(result)) {
                return;
            }
        }
        if (isCancellationRequested()) {
            VariationCleanupSupport.disposeProducerOwnedRejectedResult(result);
            return;
        }
        if (result.hasError()) {
            failed.incrementAndGet();
        }
        int done = completed.incrementAndGet();
        emitProgress(new SweepProgress(Math.min(done, total), total, failed.get(),
                result.combo(), "querying", "Published parameter result."));
        try {
            publish(result);
        } catch (Throwable failure) {
            VariationCleanupSupport.disposeProducerOwnedRejectedResult(result);
            throwFailure(failure);
        }
    }

    @Override
    protected void process(List<VariationResult> chunks) {
        if (chunks == null) {
            return;
        }
        for (int i = 0; i < chunks.size(); i++) {
            VariationResult result = chunks.get(i);
            if (result == null) {
                continue;
            }
            if (isCancellationRequested() || onResult == null) {
                VariationCleanupSupport.disposeProducerOwnedRejectedResult(result);
                continue;
            }
            try {
                onResult.accept(result, Integer.valueOf(delivered));
                result.transferOwnership();
            } catch (Throwable failure) {
                if (isFatal(failure)) {
                    VariationCleanupSupport.retainRejectedResultAfterFatal(result);
                    cancel(false);
                    throwFailure(failure);
                }
                VariationCleanupSupport.disposeProducerOwnedRejectedResult(result);
            } finally {
                delivered++;
            }
        }
    }

    @Override
    protected void done() {
        if (isCancellationRequested()) {
            emitProgress(new SweepProgress(completed.get(), total, failed.get(),
                    null, "cancelled", "Parameter sweep cancelled."));
        }
    }

    private boolean isCancellationRequested() {
        return isCancelled() || Thread.currentThread().isInterrupted();
    }

    private void emitProgress(SweepProgress progress) {
        if (onProgress != null) {
            int completedValue = advanceMax(lastProgressCompleted, progress.completed());
            int failedValue = advanceMax(lastProgressFailed, progress.failed());
            if (completedValue != progress.completed()
                    || failedValue != progress.failed()
                    || progress.total() != total) {
                progress = new SweepProgress(completedValue, total,
                        Math.min(failedValue, completedValue),
                        progress.current(), progress.phase(), progress.message());
            }
            onProgress.accept(progress);
        }
    }

    private static int advanceMax(AtomicInteger target, int candidate) {
        for (;;) {
            int current = target.get();
            if (candidate <= current) {
                return current;
            }
            if (target.compareAndSet(current, candidate)) {
                return candidate;
            }
        }
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof ThreadDeath || failure instanceof VirtualMachineError;
    }

    private static void throwFailure(Throwable failure) {
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
        throw new IllegalStateException("Parameter variation execution failed.", failure);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getSimpleName()
                : message.trim();
    }
}
