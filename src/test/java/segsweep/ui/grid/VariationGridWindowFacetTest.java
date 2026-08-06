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
import segsweep.sweep.ParameterKey;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A grid is two-dimensional; a sweep need not be. These pin the rule that the
 * first two axes lay out one page and every further axis pages the grid, so the
 * number of layout slots always matches the number of cells shown.
 */
public class VariationGridWindowFacetTest {

    private static ParameterSweep threeAxisSweep() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20, 30));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(1, 2, 3, 4));
        values.put(ParameterId.MAX_SIZE, ParameterValueList.ofInts(100, 200));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values);
    }

    @Test
    public void oneAndTwoAxisSweepsAreNotPaged() {
        assertEquals(1, VariationGridWindow.facetCount(
                GridTestFixtures.oneAxisSweep(1, 2, 3)));
        assertEquals(1, VariationGridWindow.facetCount(GridTestFixtures.twoAxisSweep()));
        assertTrue(VariationGridWindow.facetAxes(GridTestFixtures.twoAxisSweep()).isEmpty());
        assertEquals(1, VariationGridWindow.facetCount(null));
    }

    @Test
    public void aThirdAxisBecomesPagesRatherThanMoreCellsOnOnePage() {
        ParameterSweep sweep = threeAxisSweep();
        assertEquals(24L, sweep.cellCount());

        // One page per value of the third axis...
        assertEquals(2, VariationGridWindow.facetCount(sweep));
        // ...and a page is still the first two axes, so slots match its cells.
        assertArrayEquals(new int[] { 3, 4 }, VariationGridWindow.gridDimensions(sweep));
    }

    @Test
    public void pageCountIsTheProductOfEveryAxisBeyondTheSecond() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(1, 2));
        values.put(ParameterId.MAX_SIZE, ParameterValueList.ofInts(100, 200, 300));
        values.put(ParameterId.VOLUME, ParameterValueList.ofInts(5, 6));
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL, values);

        assertEquals(3 * 2, VariationGridWindow.facetCount(sweep));
        assertArrayEquals(new int[] { 2, 2 }, VariationGridWindow.gridDimensions(sweep));
    }

    @Test
    public void everyCellLandsOnExactlyOnePageAndPagesFillTheSweep() {
        ParameterSweep sweep = threeAxisSweep();
        List<ParameterKey> facetAxes = VariationGridWindow.facetAxes(sweep);
        int[] rowsCols = VariationGridWindow.gridDimensions(sweep);
        int perPage = rowsCols[0] * rowsCols[1];

        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        List<ParameterCombo> combos = sweep.combos();
        for (int i = 0; i < combos.size(); i++) {
            String key = VariationGridWindow.facetKeyFor(combos.get(i), facetAxes);
            Integer seen = counts.get(key);
            counts.put(key, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
        }

        assertEquals(VariationGridWindow.facetCount(sweep), counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            assertEquals("page " + entry.getKey() + " must fill its grid",
                    perPage, entry.getValue().intValue());
        }
    }

    @Test
    public void aThreeAxisWindowShowsOnePageAndStillHoldsEveryCell() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ParameterSweep sweep = threeAxisSweep();
                VariationGridWindow window = new VariationGridWindow(null,
                        "Object Segmentation Sweep", sweep,
                        GridTestFixtures.image("source", 0));
                try {
                    assertEquals(2, window.facetPageCountForTest());
                    // 24 combinations, but a 3x4 page: only 12 are attached.
                    assertEquals(12, window.visibleCellCountForTest());
                    // No padding: the layout has exactly as many slots as cells.
                    assertEquals(12, window.gridComponentCountForTest());

                    window.showFacetForTest(1);
                    assertEquals(12, window.visibleCellCountForTest());
                    assertEquals(12, window.gridComponentCountForTest());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void aTwoAxisWindowIsUnchangedAndShowsEveryCell() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null,
                        "Object Segmentation Sweep", GridTestFixtures.twoAxisSweep(),
                        GridTestFixtures.image("source", 0));
                try {
                    assertEquals(1, window.facetPageCountForTest());
                    assertEquals(12, window.visibleCellCountForTest());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void pageLabelsNameTheAxisAndValueTheyHold() {
        ParameterSweep sweep = threeAxisSweep();
        List<ParameterKey> facetAxes = VariationGridWindow.facetAxes(sweep);
        assertEquals(1, facetAxes.size());

        String label = VariationGridWindow.facetKeyFor(sweep.combos().get(0), facetAxes);
        assertTrue(label, label.contains("100"));
        assertEquals("", VariationGridWindow.facetKeyFor(null, facetAxes));
    }
}
