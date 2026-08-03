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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class VariationGridWindowLayoutTest {
    @Test
    public void displayWindowDimensionsFollowAxisShape() {
        assertArrayEquals(new int[] { 3, 4 },
                VariationGridWindow.gridDimensions(GridTestFixtures.twoAxisSweep()));
        assertArrayEquals(new int[] { 1, 7 },
                VariationGridWindow.gridDimensions(
                        GridTestFixtures.oneAxisSweep(1, 2, 3, 4, 5, 6, 7)));
    }

    @Test
    public void squareFallbackMatchesParentShape() {
        assertArrayEquals(new int[] { 1, 1 }, VariationGridWindow.gridDimensions(1));
        assertArrayEquals(new int[] { 1, 2 }, VariationGridWindow.gridDimensions(2));
        assertArrayEquals(new int[] { 2, 3 }, VariationGridWindow.gridDimensions(5));
        assertArrayEquals(new int[] { 3, 3 }, VariationGridWindow.gridDimensions(9));
    }

    @Test
    public void cellsPreferSquareOverlayTileSize() {
        VariationCellPanel cell = new VariationCellPanel(
                GridTestFixtures.combo(10), null, null, null);
        assertEquals(260, cell.getPreferredSize().width);
        assertEquals(260, cell.getPreferredSize().height);
    }
}
