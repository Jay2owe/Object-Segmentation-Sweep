/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import org.junit.Assume;
import org.junit.Test;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.VariationResult;
import segsweep.sweep.analysis.IouStability;
import segsweep.sweep.analysis.KneeOutcome;
import segsweep.sweep.analysis.PickResult;

import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Regression tests for the stage 06 review-grid fixes. */
public class VariationGridWindowReviewerTest {

    @Test
    public void oneAxisTwentyValueSweepWrapsIntoRows() {
        int[] values = new int[20];
        for (int i = 0; i < values.length; i++) values[i] = 10 + i;
        ParameterSweep sweep = GridTestFixtures.oneAxisSweep(values);

        int[] dims = VariationGridWindow.layoutDimensions(sweep, 20,
                new Dimension(8, 8), 1600, 900);

        assertTrue("rows " + dims[0], dims[0] >= 3);
        assertTrue(dims[0] * dims[1] >= 20);
        // Two-axis sweeps keep their axes as rows and columns.
        assertEquals(3, VariationGridWindow.layoutDimensions(
                GridTestFixtures.twoAxisSweep(), 12, new Dimension(8, 8), 1600, 900)[0]);
    }

    @Test
    public void oneAxisTwentyValueWindowIsLaidOutInAtLeastThreeRows() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                int[] values = new int[20];
                for (int i = 0; i < values.length; i++) values[i] = 10 + i;
                VariationGridWindow window = new VariationGridWindow(null, "rows",
                        GridTestFixtures.oneAxisSweep(values),
                        GridTestFixtures.image("source", 0));
                try {
                    GridLayout layout = (GridLayout) window.gridPanelForTest().getLayout();
                    assertTrue("rows " + layout.getRows(), layout.getRows() >= 3);
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void failedCellsAreCountedAndSurviveTheFinalCount() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "failed",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    window.setResult(GridTestFixtures.result(GridTestFixtures.combo(10)));
                    window.setResult(failure(GridTestFixtures.combo(20)));
                    window.setResult(failure(GridTestFixtures.combo(20)));
                    window.setResult(GridTestFixtures.result(GridTestFixtures.combo(30)));
                    // SegSweep_ reports the final count with failed = 0.
                    window.setCompletedCount(3, 3, 0);

                    assertEquals(1, window.failedCountForTest());
                    assertTrue(window.progressBarForTest().getString(),
                            window.progressBarForTest().getString().contains("(1 failed)"));
                    assertTrue(window.statusLabelForTest().getText().contains("1 failed"));
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void resultKeyedByDoubleLandsOnIntegerCell() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "coords",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    ParameterCombo asDouble = ParameterCombo.builder()
                            .put(ParameterId.THRESHOLD, Double.valueOf(20.0d)).build();
                    window.setResult(GridTestFixtures.result(asDouble));

                    assertTrue(window.cellsForTest().get(1).isAcceptEnabledForTest());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void shiftClickOpensCompareOnFinishedCellsAndReportsInTheGrid() throws Exception {
        final AtomicReference<String> status = new AtomicReference<String>();
        final AtomicReference<VariationCellPanel> opened =
                new AtomicReference<VariationCellPanel>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                final VariationComparisonSelection selection = new VariationComparisonSelection(
                        null,
                        new VariationComparisonSelection.Opener() {
                            @Override public void openComparison(VariationCellPanel left,
                                                                 VariationCellPanel right) {
                                opened.set(right);
                            }
                        });
                selection.setStatusSink(new java.util.function.Consumer<String>() {
                    @Override public void accept(String text) {
                        status.set(text);
                    }
                });
                VariationCellPanel first = finishedCell(GridTestFixtures.combo(10), selection);
                VariationCellPanel second = finishedCell(GridTestFixtures.combo(20), selection);
                // Finished results hold a lazy label map, never a built stack.
                assertNull(first.cachedLabelForTest());

                shiftClick(first);
                assertEquals("Shift-click a second tile to compare.", status.get());
                shiftClick(second);

                assertSame(second, opened.get());
            }
        });
    }

    @Test
    public void gridWiresTheCompareStatusIntoItsStatusLine() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "compare",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    ParameterCombo combo = GridTestFixtures.combo(10);
                    window.setResult(GridTestFixtures.result(combo));

                    shiftClick(window.cellForComboForTest(combo));

                    assertEquals("Shift-click a second tile to compare.",
                            window.statusLabelForTest().getText());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void pickPillFiresPickSelected() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        final AtomicInteger picks = new AtomicInteger();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "pill",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    window.attachPickSelectedActionListener(new java.awt.event.ActionListener() {
                        @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                            picks.incrementAndGet();
                        }
                    });
                    ParameterCombo combo = GridTestFixtures.combo(20);
                    window.setResult(GridTestFixtures.result(combo));

                    window.cellForComboForTest(combo).commitPickForTest();

                    assertEquals(combo, window.selectedComboForTest());
                    assertEquals(1, picks.get());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void badgeOnAnotherPageSwitchesToThatPage() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ParameterSweep sweep = twoPageSweep();
                VariationGridWindow window = new VariationGridWindow(null, "badge",
                        sweep, GridTestFixtures.image("source", 0));
                try {
                    assertEquals(0, window.currentPageForTest());
                    ParameterCombo onSecondPage = ParameterCombo.builder()
                            .put(ParameterId.THRESHOLD, Integer.valueOf(20))
                            .put(ParameterId.MIN_SIZE, Integer.valueOf(1))
                            .put(ParameterId.MAX_SIZE, Integer.valueOf(200))
                            .build();

                    window.setPickResult(new PickResult(
                            KneeOutcome.kneeAt(0, 20.0, 10.0, 30.0, 10.0, ""),
                            stability(),
                            GridTestFixtures.provenance(),
                            onSecondPage, null));

                    assertEquals(1, window.currentPageForTest());
                    assertEquals(PickBadge.Kind.KNEE,
                            window.cellForComboForTest(onSecondPage).badgeForTest().kind());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void escapeClosesAndConfirmsBeforeCancellingARun() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        final AtomicInteger cancels = new AtomicInteger();
        final AtomicInteger asks = new AtomicInteger();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "escape",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    window.attachCancelActionListener(new java.awt.event.ActionListener() {
                        @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                            cancels.incrementAndGet();
                        }
                    });
                    window.setVisible(true);
                    window.setEscapeConfirmForTest(answer(asks, false));
                    window.pressEscapeForTest();
                    assertEquals(1, asks.get());
                    assertEquals(0, cancels.get());
                    assertTrue(window.isDisplayable());

                    window.setEscapeConfirmForTest(answer(asks, true));
                    window.pressEscapeForTest();
                    assertEquals(1, cancels.get());
                    assertFalse(window.isDisplayable());
                } finally {
                    window.dispose();
                }

                VariationGridWindow finished = new VariationGridWindow(null, "escape",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    finished.setCancelEnabled(false);
                    finished.setVisible(true);
                    finished.setEscapeConfirmForTest(answer(asks, false));
                    finished.pressEscapeForTest();
                    assertEquals("no question once the run is over", 2, asks.get());
                    assertFalse(finished.isDisplayable());
                } finally {
                    finished.dispose();
                }
            }
        });
    }

    @Test
    public void leftAndRightStepThroughZ() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "keys",
                        GridTestFixtures.oneAxisSweep(10, 20),
                        GridTestFixtures.stack(4));
                try {
                    window.setSliceMax(4);
                    window.stepSliceForTest(1);
                    window.stepSliceForTest(1);
                    assertEquals(3, window.zSliderForTest().getValue());
                    window.stepSliceForTest(-1);
                    assertEquals(2, window.zSliderForTest().getValue());
                    window.stepSliceForTest(-5);
                    assertEquals(1, window.zSliderForTest().getValue());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void emptyTilesSayWhyTheyAreEmpty() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationCellPanel pending = new VariationCellPanel(
                        GridTestFixtures.combo(10), null, null, null);
                pending.setState("pending");
                assertEquals("Waiting", pending.preview().emptyText());

                VariationCellPanel failed = new VariationCellPanel(
                        GridTestFixtures.combo(20), null, null, null);
                failed.setResult(failure(GridTestFixtures.combo(20)));
                assertEquals("Failed", failed.preview().emptyText());

                VariationCellPanel cancelled = new VariationCellPanel(
                        GridTestFixtures.combo(30), null, null, null);
                cancelled.setResult(VariationResult.failure(GridTestFixtures.combo(30),
                        new CancellationException("cancelled"),
                        GridTestFixtures.provenance(),
                        EnumSet.noneOf(VariationResult.Flag.class), 0));
                assertEquals("Cancelled", cancelled.preview().emptyText());
            }
        });
    }

    private static BooleanSupplier answer(final AtomicInteger asks, final boolean yes) {
        return new BooleanSupplier() {
            @Override public boolean getAsBoolean() {
                asks.incrementAndGet();
                return yes;
            }
        };
    }

    private static VariationResult failure(ParameterCombo combo) {
        return VariationResult.failure(combo, new IllegalStateException("boom"),
                GridTestFixtures.provenance(),
                EnumSet.noneOf(VariationResult.Flag.class), 0);
    }

    private static VariationCellPanel finishedCell(ParameterCombo combo,
                                                   final VariationComparisonSelection selection) {
        VariationCellPanel cell = new VariationCellPanel(combo,
                GridTestFixtures.image("source", 0), null,
                new java.util.function.BiConsumer<ParameterCombo, VariationCellPanel>() {
                    @Override public void accept(ParameterCombo clicked,
                                                 VariationCellPanel clickedCell) {
                        selection.handleShiftClick(clickedCell);
                    }
                });
        cell.setResult(GridTestFixtures.result(combo));
        return cell;
    }

    private static void shiftClick(VariationCellPanel cell) {
        MouseEvent event = new MouseEvent(cell, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), InputEvent.SHIFT_DOWN_MASK, 8, 8, 1, false,
                MouseEvent.BUTTON1);
        MouseListener[] listeners = cell.getMouseListeners();
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].mousePressed(event);
        }
    }

    private static ParameterSweep twoPageSweep() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(1, 2));
        values.put(ParameterId.MAX_SIZE, ParameterValueList.ofInts(100, 200));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values);
    }

    private static segsweep.sweep.analysis.StabilityOutcome stability() {
        List<ParameterCombo> combos = new ArrayList<ParameterCombo>();
        combos.add(GridTestFixtures.combo(10));
        combos.add(GridTestFixtures.combo(20));
        combos.add(GridTestFixtures.combo(30));
        List<IouStability.IouSource> sources = new ArrayList<IouStability.IouSource>();
        for (int i = 0; i < 3; i++) {
            sources.add(IouStability.IouSource.fromObjectIds(
                    java.util.Arrays.asList(Integer.valueOf(1), Integer.valueOf(2))));
        }
        return IouStability.score(combos, sources);
    }
}
