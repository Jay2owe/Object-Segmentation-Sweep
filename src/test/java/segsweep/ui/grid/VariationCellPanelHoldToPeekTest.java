/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import ij.ImagePlus;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VariationCellPanelHoldToPeekTest {
    @Test
    public void holdShowsRawPreviewUntilRelease() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ImagePlus raw = GridTestFixtures.image("raw", 200);
                VariationCellPanel cell = renderedCell(null);
                ImagePlus rendered = cell.currentPreviewImageForTest();
                cell.setRawSource(raw);

                press(cell, 8, 8);
                cell.firePeekDelayForTest();

                assertTrue(cell.isPeekingForTest());
                assertSame(raw, cell.currentPreviewImageForTest());

                release(cell, 8, 8);

                assertFalse(cell.isPeekingForTest());
                assertSame(rendered, cell.currentPreviewImageForTest());
            }
        });
    }

    @Test
    public void dragCancelsPendingPeek() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationCellPanel cell = renderedCell(null);
                ImagePlus rendered = cell.currentPreviewImageForTest();
                cell.setRawSource(GridTestFixtures.image("raw", 200));

                press(cell, 8, 8);
                drag(cell, 30, 8);
                cell.firePeekDelayForTest();

                assertFalse(cell.isPeekingForTest());
                assertFalse(cell.isPeekDelayRunningForTest());
                assertSame(rendered, cell.currentPreviewImageForTest());
            }
        });
    }

    @Test
    public void longHoldSuppressesFollowingClickOnce() throws Exception {
        final AtomicInteger accepts = new AtomicInteger();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationCellPanel cell = renderedCell(accepts);
                cell.setRawSource(GridTestFixtures.image("raw", 200));

                press(cell, 8, 8);
                cell.firePeekDelayForTest();
                release(cell, 8, 8);
                press(cell, 8, 8);
                release(cell, 8, 8);

                assertEquals(1, accepts.get());
                assertFalse(cell.suppressNextClickForTest());
            }
        });
    }

    private static VariationCellPanel renderedCell(final AtomicInteger accepts) {
        VariationCellPanel cell = new VariationCellPanel(GridTestFixtures.combo(10),
                GridTestFixtures.image("filtered", 10),
                new java.util.function.Consumer<segsweep.sweep.ParameterCombo>() {
                    @Override public void accept(segsweep.sweep.ParameterCombo combo) {
                        if (accepts != null) {
                            accepts.incrementAndGet();
                        }
                    }
                },
                null);
        cell.setLabel(GridTestFixtures.labels(), null, 2, 5L);
        return cell;
    }

    private static void press(VariationCellPanel cell, int x, int y) {
        fireMouse(cell, MouseEvent.MOUSE_PRESSED, x, y);
    }

    private static void release(VariationCellPanel cell, int x, int y) {
        fireMouse(cell, MouseEvent.MOUSE_RELEASED, x, y);
    }

    private static void drag(VariationCellPanel cell, int x, int y) {
        MouseEvent event = event(cell, MouseEvent.MOUSE_DRAGGED, x, y);
        MouseMotionListener[] listeners = cell.getMouseMotionListeners();
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].mouseDragged(event);
        }
    }

    private static void fireMouse(VariationCellPanel cell, int id, int x, int y) {
        MouseEvent event = event(cell, id, x, y);
        MouseListener[] listeners = cell.getMouseListeners();
        for (int i = 0; i < listeners.length; i++) {
            if (id == MouseEvent.MOUSE_PRESSED) {
                listeners[i].mousePressed(event);
            } else {
                listeners[i].mouseReleased(event);
            }
        }
    }

    private static MouseEvent event(VariationCellPanel cell, int id, int x, int y) {
        return new MouseEvent(cell, id, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1);
    }
}
