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
import ij.process.ByteProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VariationCellPanelCleanupTest {
    @Test
    public void closingReleasesMaterialisedLabelOnce() {
        TrackingImage label = new TrackingImage("owned-label");
        VariationCellPanel cell = new VariationCellPanel(
                GridTestFixtures.combo(10), null, null, null);
        cell.setLabel(label, null, 1, 1L);

        cell.disposeImages();
        cell.disposeImages();

        assertTrue(cell.terminalCleanupComplete());
        assertNull(cell.cachedLabelForTest());
    }

    @Test
    public void cellOwnedPlaceholderIsClosedAndFlushed() {
        VariationCellPanel cell = new VariationCellPanel(
                GridTestFixtures.combo(10), null, null, null);
        cell.setLabel(null, null, 0, 1L);

        cell.disposeImages();

        assertTrue(cell.terminalCleanupComplete());
    }

    private static final class TrackingImage extends ImagePlus {
        int closeCalls;
        int flushCalls;

        TrackingImage(String title) {
            super(title, new ByteProcessor(1, 1));
        }

        @Override public void close() {
            closeCalls++;
            super.close();
        }

        @Override public void flush() {
            flushCalls++;
            super.flush();
        }
    }
}
