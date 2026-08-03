/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.strategy;

import ij.ImagePlus;
import org.junit.Test;
import segsweep.SegSweepLabeller;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.SweepRefusedException;
import segsweep.sweep.VariationResult;
import segsweep.token.MorphPredicate;
import segsweep.tree.ComponentTree;
import segsweep.tree.ComponentTreeQuery;
import segsweep.tree.ComponentTreeResult;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TreeBuildOnceTest {

    @Test
    public void oneTreeBuildFeedsManyDisplayedQueries() throws Exception {
        CountingFactory factory = new CountingFactory();
        List<VariationResult> results = new ArrayList<VariationResult>();

        new SegSweepClassicalStrategy(ClassicalStrategyLazyLabelTest.source(), CropSpec.full(),
                SegSweepLabeller.Connectivity.SIX, null, null,
                ClassicalStrategyLazyLabelTest.permitGuard(), factory,
                ClassicalStrategyLazyLabelTest.noopCloser())
                .dispatch(ClassicalStrategyLazyLabelTest.thresholdSweep(), results::add, null, null);

        assertEquals(1, factory.builds.get());
        assertEquals(5, factory.queries.get());
        assertEquals(5, results.size());
    }

    @Test
    public void resourceGuardRefusesBeforeTreeConstructionStarts() throws Exception {
        CountingFactory factory = new CountingFactory();

        try {
            new SegSweepClassicalStrategy(ClassicalStrategyLazyLabelTest.source(),
                    CropSpec.full(), SegSweepLabeller.Connectivity.SIX, null, null,
                    refusingGuard(), factory, ClassicalStrategyLazyLabelTest.noopCloser())
                    .dispatch(ClassicalStrategyLazyLabelTest.thresholdSweep(),
                            new ArrayList<VariationResult>()::add, null, null);
            fail("Expected resource guard refusal.");
        } catch (SweepRefusedException expected) {
            assertTrue(expected.getMessage().contains("too large"));
        }

        assertEquals(0, factory.builds.get());
        assertEquals(0, factory.queries.get());
    }

    @Test
    public void cancellationBeforeCropDoesNotBuildOrCloseUserImage() throws Exception {
        CountingFactory factory = new CountingFactory();
        CountingCloser closer = new CountingCloser();

        new SegSweepClassicalStrategy(ClassicalStrategyLazyLabelTest.source(),
                CropSpec.custom(new Rectangle(0, 0, 16, 16)),
                SegSweepLabeller.Connectivity.SIX, null, null,
                ClassicalStrategyLazyLabelTest.permitGuard(), factory, closer)
                .dispatch(ClassicalStrategyLazyLabelTest.thresholdSweep(),
                        new ArrayList<VariationResult>()::add, null,
                        new java.util.function.BooleanSupplier() {
                            @Override public boolean getAsBoolean() {
                                return true;
                            }
                        });

        assertEquals(0, factory.builds.get());
        assertEquals(0, factory.queries.get());
        assertEquals(0, closer.closed.get());
    }

    @Test
    public void cancellationDuringTreeBuildClosesOwnedCropBeforeReturning() throws Exception {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        CountingFactory factory = new CountingFactory() {
            @Override public SegSweepClassicalStrategy.TreeHandle build(
                    ImagePlus cropped, SegSweepLabeller.Connectivity connectivity) {
                SegSweepClassicalStrategy.TreeHandle handle = super.build(cropped, connectivity);
                cancelled.set(true);
                return handle;
            }
        };
        CountingCloser closer = new CountingCloser();

        new SegSweepClassicalStrategy(ClassicalStrategyLazyLabelTest.source(),
                CropSpec.custom(new Rectangle(0, 0, 16, 16)),
                SegSweepLabeller.Connectivity.SIX, null, null,
                ClassicalStrategyLazyLabelTest.permitGuard(), factory, closer)
                .dispatch(ClassicalStrategyLazyLabelTest.thresholdSweep(),
                        new ArrayList<VariationResult>()::add, null,
                        new java.util.function.BooleanSupplier() {
                            @Override public boolean getAsBoolean() {
                                return cancelled.get();
                            }
                        });

        assertEquals(1, factory.builds.get());
        assertEquals(0, factory.queries.get());
        assertEquals(1, closer.closed.get());
    }

    @Test
    public void cancellationDuringQueryClosesOwnedCropAndPublishesNothing() throws Exception {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        CountingFactory factory = new CountingFactory() {
            @Override SegSweepClassicalStrategy.TreeHandle wrap(final ComponentTree tree) {
                return new SegSweepClassicalStrategy.TreeHandle() {
                    @Override public ComponentTreeResult query(ComponentTreeQuery query) {
                        queries.incrementAndGet();
                        cancelled.set(true);
                        return tree.query(query);
                    }
                };
            }
        };
        CountingCloser closer = new CountingCloser();
        List<VariationResult> results = new ArrayList<VariationResult>();

        new SegSweepClassicalStrategy(ClassicalStrategyLazyLabelTest.source(),
                CropSpec.custom(new Rectangle(0, 0, 16, 16)),
                SegSweepLabeller.Connectivity.SIX, null, null,
                ClassicalStrategyLazyLabelTest.permitGuard(), factory, closer)
                .dispatch(ClassicalStrategyLazyLabelTest.thresholdSweep(), results::add, null,
                        new java.util.function.BooleanSupplier() {
                            @Override public boolean getAsBoolean() {
                                return cancelled.get();
                            }
                        });

        assertEquals(1, factory.builds.get());
        assertEquals(1, factory.queries.get());
        assertEquals(0, results.size());
        assertEquals(1, closer.closed.get());
    }

    @Test
    public void comboAndTokenMorphologyPredicatesReachTreeQuery() throws Exception {
        CountingFactory factory = new CountingFactory();
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10));
        values.put(ParameterId.VOLUME, ParameterValueList.ofInts(2));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                values, CropSpec.full(), "DAPI");

        new SegSweepClassicalStrategy(ClassicalStrategyLazyLabelTest.source(),
                CropSpec.full(), SegSweepLabeller.Connectivity.SIX, null,
                Collections.singletonList(MorphPredicate.parse("sphericity>=0.1")),
                ClassicalStrategyLazyLabelTest.permitGuard(), factory,
                ClassicalStrategyLazyLabelTest.noopCloser())
                .dispatch(sweep, new ArrayList<VariationResult>()::add, null, null);

        assertEquals(1, factory.seenQueries.size());
        List<segsweep.tree.MorphologyPredicate> predicates =
                factory.seenQueries.get(0).predicates();
        assertEquals(2, predicates.size());
        assertEquals("sphericity>=0.1", predicates.get(0).format());
        assertEquals("volume>=2.0", predicates.get(1).format());
    }

    private static SegSweepClassicalStrategy.TreeMemoryGuard refusingGuard() {
        return new SegSweepClassicalStrategy.TreeMemoryGuard() {
            @Override public SegSweepClassicalStrategy.GuardVerdict assess(
                    ParameterSweep displayWindow, ImagePlus cropped) {
                return SegSweepClassicalStrategy.GuardVerdict.deny("tree too large");
            }
        };
    }

    static class CountingFactory implements SegSweepClassicalStrategy.TreeFactory {
        final AtomicInteger builds = new AtomicInteger();
        final AtomicInteger queries = new AtomicInteger();
        final List<ComponentTreeQuery> seenQueries = new ArrayList<ComponentTreeQuery>();

        @Override public SegSweepClassicalStrategy.TreeHandle build(
                ImagePlus cropped, SegSweepLabeller.Connectivity connectivity) {
            builds.incrementAndGet();
            return wrap(ComponentTree.build(cropped, connectivity));
        }

        SegSweepClassicalStrategy.TreeHandle wrap(final ComponentTree tree) {
            return new SegSweepClassicalStrategy.TreeHandle() {
                @Override public ComponentTreeResult query(ComponentTreeQuery query) {
                    queries.incrementAndGet();
                    seenQueries.add(query);
                    return tree.query(query);
                }
            };
        }
    }

    private static final class CountingCloser implements SegSweepClassicalStrategy.ImageCloser {
        final AtomicInteger closed = new AtomicInteger();

        @Override public void close(ImagePlus image) {
            if (image != null) {
                closed.incrementAndGet();
                image.changes = false;
                image.close();
                image.flush();
            }
        }
    }
}
