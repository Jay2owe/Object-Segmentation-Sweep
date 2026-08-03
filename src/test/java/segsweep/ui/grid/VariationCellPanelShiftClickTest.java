/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import org.junit.Test;
import segsweep.sweep.ParameterCombo;

import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VariationCellPanelShiftClickTest {
    @Test
    public void shiftClickTwoRenderedCellsOpensComparisonPair() throws Exception {
        final AtomicReference<String> status = new AtomicReference<String>();
        final AtomicReference<VariationCellPanel> openedLeft =
                new AtomicReference<VariationCellPanel>();
        final AtomicReference<VariationCellPanel> openedRight =
                new AtomicReference<VariationCellPanel>();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationComparisonSelection selection = new VariationComparisonSelection(
                        new java.util.function.Consumer<String>() {
                            @Override public void accept(String text) {
                                status.set(text);
                            }
                        },
                        new VariationComparisonSelection.Opener() {
                            @Override public void openComparison(VariationCellPanel left,
                                                                 VariationCellPanel right) {
                                openedLeft.set(left);
                                openedRight.set(right);
                            }
                        });
                VariationCellPanel first = renderedCell(GridTestFixtures.combo(1), selection);
                VariationCellPanel second = renderedCell(GridTestFixtures.combo(2), selection);

                click(first, true);
                assertTrue(first.isSelectedForCompareForTest());
                assertEquals("Shift-click a second tile to compare.", status.get());

                click(second, true);

                assertSame(first, openedLeft.get());
                assertSame(second, openedRight.get());
                assertFalse(first.isSelectedForCompareForTest());
            }
        });
    }

    @Test
    public void shiftClickPendingCellReportsWait() throws Exception {
        final AtomicReference<String> status = new AtomicReference<String>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationComparisonSelection selection = new VariationComparisonSelection(
                        new java.util.function.Consumer<String>() {
                            @Override public void accept(String text) {
                                status.set(text);
                            }
                        },
                        null);
                VariationCellPanel pending = new VariationCellPanel(
                        GridTestFixtures.combo(1), GridTestFixtures.image("source", 0),
                        null,
                        new java.util.function.BiConsumer<ParameterCombo, VariationCellPanel>() {
                            @Override public void accept(ParameterCombo combo,
                                                         VariationCellPanel cell) {
                                selection.handleShiftClick(cell);
                            }
                        });

                click(pending, true);

                assertFalse(pending.isSelectedForCompareForTest());
                assertEquals("Wait for both tiles to finish rendering.", status.get());
            }
        });
    }

    @Test
    public void pickPillCommitDoesNotUseBodySelection() throws Exception {
        final AtomicReference<ParameterCombo> selected =
                new AtomicReference<ParameterCombo>();
        final AtomicReference<ParameterCombo> committed =
                new AtomicReference<ParameterCombo>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                final ParameterCombo combo = GridTestFixtures.combo(7);
                VariationCellPanel cell = new VariationCellPanel(
                        combo, GridTestFixtures.image("source", 0),
                        new java.util.function.Consumer<ParameterCombo>() {
                            @Override public void accept(ParameterCombo combo) {
                                selected.set(combo);
                            }
                        },
                        null);
                cell.setOnPickCommit(new java.util.function.Consumer<ParameterCombo>() {
                    @Override public void accept(ParameterCombo combo) {
                        committed.set(combo);
                    }
                });
                cell.setLabel(GridTestFixtures.labels(), null, 2, 1L);
                cell.setSize(260, 260);

                clickAt(cell, 225, 21, false);

                assertSame(combo, committed.get());
                assertSame(null, selected.get());
            }
        });
    }

    private static VariationCellPanel renderedCell(
            final ParameterCombo combo,
            final VariationComparisonSelection selection) {
        VariationCellPanel cell = new VariationCellPanel(combo,
                GridTestFixtures.image("source", 0),
                null,
                new java.util.function.BiConsumer<ParameterCombo, VariationCellPanel>() {
                    @Override public void accept(ParameterCombo clickedCombo,
                                                 VariationCellPanel clickedCell) {
                        selection.handleShiftClick(clickedCell);
                    }
                });
        cell.setLabel(GridTestFixtures.labels(), null, 2, 1L);
        return cell;
    }

    private static void click(VariationCellPanel cell, boolean shiftDown) {
        clickAt(cell, 8, 8, shiftDown);
    }

    private static void clickAt(VariationCellPanel cell, int x, int y, boolean shiftDown) {
        int modifiers = shiftDown ? InputEvent.SHIFT_DOWN_MASK : 0;
        MouseEvent event = new MouseEvent(cell, MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), modifiers, x, y, 1, false,
                MouseEvent.BUTTON1);
        MouseListener[] listeners = cell.getMouseListeners();
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].mousePressed(event);
        }
    }
}
