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

import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;

public class SyncedSliceControllerTest {
    @Test
    public void setSliceClampsAndMovesEveryCell() throws Exception {
        final SyncedSliceController controller = new SyncedSliceController();
        final VariationCellPanel deep = cellWithSlices(5);
        final VariationCellPanel shallow = cellWithSlices(3);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                controller.register(deep);
                controller.register(shallow);
                controller.setSlice(4);
            }
        });

        assertEquals(3, controller.maxSlice());
        assertEquals(3, controller.currentSlice());
        assertEquals(3, deep.currentZForTest());
        assertEquals(3, shallow.currentZForTest());
    }

    @Test
    public void unregisterUpdatesControllerSize() {
        SyncedSliceController controller = new SyncedSliceController();
        VariationCellPanel first = new VariationCellPanel(
                GridTestFixtures.combo(1), null, null, null);
        VariationCellPanel second = new VariationCellPanel(
                GridTestFixtures.combo(2), null, null, null);

        controller.register(first);
        controller.register(second);
        controller.unregister(first);

        assertEquals(1, controller.size());
    }

    private static VariationCellPanel cellWithSlices(int slices) {
        VariationCellPanel cell = new VariationCellPanel(
                GridTestFixtures.combo(slices), null, null, null);
        cell.preview().setImage(GridTestFixtures.stack(slices));
        return cell;
    }
}
