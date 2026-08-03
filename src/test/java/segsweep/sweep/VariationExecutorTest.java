/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import ij.ImagePlus;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.SegSweepLabellerFixtures;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.LazyLabelMap;

import java.awt.EventQueue;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VariationExecutorTest {
    @Test
    public void stubStrategyDrivesThreeByFourWindowToCompletion() throws Exception {
        final CountDownLatch delivered = new CountDownLatch(12);
        final List<VariationResult> results =
                Collections.synchronizedList(new ArrayList<VariationResult>());
        final List<Integer> indexes =
                Collections.synchronizedList(new ArrayList<Integer>());
        final List<SweepProgress> progress =
                Collections.synchronizedList(new ArrayList<SweepProgress>());

        VariationExecutor executor = new VariationExecutor(threeByFourSweep(),
                new StubStrategy(), (result, index) -> {
            results.add(result);
            indexes.add(index);
            delivered.countDown();
        }, progress::add);

        executor.execute();

        assertTrue(delivered.await(5L, TimeUnit.SECONDS));
        executor.get(5L, TimeUnit.SECONDS);
        flushEdt();

        assertEquals(12, results.size());
        assertEquals(Integer.valueOf(0), indexes.get(0));
        assertEquals(Integer.valueOf(11), indexes.get(11));
        for (int i = 0; i < results.size(); i++) {
            VariationResult result = results.get(i);
            assertTrue(result.provenance() != null);
            assertTrue(result.hasLabelMap());
            assertEquals(0, result.labelMap().materializationCount());
        }
        assertMonotonic(progress);
    }

    @Test
    public void cancellationStopsPromptlyWithoutMaterialisingQueuedLabels()
            throws Exception {
        final CountDownLatch firstDelivered = new CountDownLatch(1);
        final CountDownLatch cancellationObserved = new CountDownLatch(1);
        final AtomicInteger published = new AtomicInteger();
        final AtomicInteger delivered = new AtomicInteger();
        final AtomicReference<LazyLabelMap> firstLabelMap =
                new AtomicReference<LazyLabelMap>();
        final AtomicReference<VariationExecutor> executorRef =
                new AtomicReference<VariationExecutor>();

        VariationStrategy strategy = new VariationStrategy() {
            @Override public void dispatch(ParameterSweep displayWindow,
                                           Consumer<VariationResult> publisher,
                                           Consumer<SweepProgress> progress,
                                           BooleanSupplier cancelCheck)
                    throws Exception {
                for (ParameterCombo combo : displayWindow.combos()) {
                    if (cancelCheck.getAsBoolean()) {
                        cancellationObserved.countDown();
                        return;
                    }
                    VariationResult result = result(combo);
                    if (published.get() == 0) {
                        firstLabelMap.set(result.labelMap());
                    }
                    published.incrementAndGet();
                    publisher.accept(result);
                    if (published.get() == 1) {
                        assertTrue(firstDelivered.await(5L, TimeUnit.SECONDS));
                    }
                }
            }
        };
        VariationExecutor executor = new VariationExecutor(threeByFourSweep(), strategy,
                (result, index) -> {
                    delivered.incrementAndGet();
                    executorRef.get().cancel(false);
                    firstDelivered.countDown();
                }, null);
        executorRef.set(executor);

        executor.execute();

        assertTrue(cancellationObserved.await(5L, TimeUnit.SECONDS));
        try {
            executor.get(5L, TimeUnit.SECONDS);
            fail("Expected cancellation to surface from SwingWorker.get().");
        } catch (CancellationException expected) {
            // Expected.
        }
        flushEdt();

        assertEquals(1, published.get());
        assertEquals(1, delivered.get());
        assertEquals(0, firstLabelMap.get().materializationCount());
    }

    @Test
    public void executorSourceDoesNotUseIjLog() throws Exception {
        Path path = Paths.get("src/main/java/segsweep/sweep/VariationExecutor.java");
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertFalse(source.contains("IJ.log"));
        assertFalse(source.contains("import ij.IJ"));
    }

    private static void assertMonotonic(List<SweepProgress> progress) {
        assertFalse(progress.isEmpty());
        int previousCompleted = 0;
        for (int i = 0; i < progress.size(); i++) {
            SweepProgress record = progress.get(i);
            assertTrue(record.completed() >= previousCompleted);
            assertEquals(12, record.total());
            assertTrue(record.failed() <= record.completed());
            previousCompleted = record.completed();
        }
        assertEquals(12, previousCompleted);
    }

    private static ParameterSweep threeByFourSweep() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20, 30));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(1, 2, 3, 4));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.custom(new Rectangle(0, 0, 5, 2)), "DAPI");
    }

    private static VariationResult result(ParameterCombo combo) {
        return VariationResult.success(combo, labelMap(), 2, 7L, null,
                provenance());
    }

    private static LazyLabelMap labelMap() {
        ImagePlus image = SegSweepLabellerFixtures.points(5, 2, 1,
                new int[][] { { 0, 0, 0 }, { 4, 0, 0 } });
        return ComponentTree.build(image, SegSweepLabeller.Connectivity.SIX)
                .query(ComponentTreeQuery.builder().threshold(10).build())
                .labelMap();
    }

    private static SweepProvenance provenance() {
        Map<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        ranges.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20, 30));
        ranges.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(1, 2, 3, 4));
        return new SweepProvenance(CropSpec.custom(new Rectangle(0, 0, 5, 2)),
                5, 2, 1, ranges, "micron", 0.024d);
    }

    private static void flushEdt() throws Exception {
        EventQueue.invokeAndWait(new Runnable() {
            @Override public void run() {
            }
        });
    }

    private static final class StubStrategy implements VariationStrategy {
        @Override public void dispatch(ParameterSweep displayWindow,
                                       Consumer<VariationResult> publisher,
                                       Consumer<SweepProgress> progress,
                                       BooleanSupplier cancelCheck) {
            List<ParameterCombo> ordered = SweepDispatchOrder.order(displayWindow);
            for (int i = 0; i < ordered.size(); i++) {
                if (cancelCheck.getAsBoolean()) {
                    return;
                }
                ParameterCombo combo = ordered.get(i);
                progress.accept(new SweepProgress(i, ordered.size(), 0, combo,
                        "querying", "Dispatching stub result."));
                publisher.accept(result(combo));
            }
        }
    }
}
