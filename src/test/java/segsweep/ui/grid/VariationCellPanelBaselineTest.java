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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VariationCellPanelBaselineTest {
    @Test
    public void baselineShowsOriginalAndCannotBePicked() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ImagePlus source = GridTestFixtures.stack(3);
                VariationCellPanel cell = VariationCellPanel.baseline(source);
                SyncedSliceController controller = new SyncedSliceController();

                controller.register(cell);
                controller.setSlice(2);

                assertTrue(cell.isBaselineForTest());
                assertFalse(cell.isAcceptEnabledForTest());
                assertFalse(cell.isPickPillVisibleForTest());
                assertSame(source, cell.currentPreviewImageForTest());
                assertEquals("Original", cell.footerTextForTest());
                assertEquals(2, cell.currentZForTest());
            }
        });
    }
}
