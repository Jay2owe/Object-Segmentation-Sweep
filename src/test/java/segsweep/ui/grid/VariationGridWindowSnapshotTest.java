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

import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VariationGridWindowSnapshotTest {

    @Test
    public void snapshotRendersTheCompleteCurrentGrid() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "snapshot",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 25));
                try {
                    for (ParameterCombo combo : GridTestFixtures.oneAxisSweep(10, 20, 30).combos()) {
                        window.setResult(GridTestFixtures.result(combo));
                    }
                    Dimension expected = window.gridPanelForTest().getPreferredSize();

                    BufferedImage snapshot = window.renderGridSnapshot();

                    assertNotNull(snapshot);
                    assertEquals(expected.width, snapshot.getWidth());
                    assertEquals(expected.height, snapshot.getHeight());
                } finally {
                    window.dispose();
                }
            }
        });
    }

    /**
     * Regression: the snapshot used to paint the live grid at the current zoom
     * (up to 10x), capture only the page on screen, and leave the grid resized.
     */
    @Test
    public void zoomedThreePageGridGivesThreeZoomOneImagesAndKeepsTheLiveZoom()
            throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                ParameterSweep sweep = threePageSweep();
                VariationGridWindow window = new VariationGridWindow(null, "snapshot",
                        sweep, GridTestFixtures.image("source", 25));
                try {
                    for (ParameterCombo combo : sweep.combos()) {
                        window.setResult(GridTestFixtures.result(combo));
                    }
                    window.setVisible(true);
                    Dimension zoomOne = window.gridPreferredSizeForTest();
                    window.zoomByForTest(3.0);
                    double liveZoom = window.zoomForTest();
                    Dimension livePreferred = window.gridPreferredSizeForTest();
                    int livePage = window.currentPageForTest();
                    assertTrue("zoomed in", liveZoom > 2.9);

                    List<BufferedImage> pages = window.renderGridSnapshots();

                    assertEquals(3, pages.size());
                    for (int i = 0; i < pages.size(); i++) {
                        assertEquals(zoomOne.width, pages.get(i).getWidth());
                        assertEquals(zoomOne.height, pages.get(i).getHeight());
                        // Off-screen pages are painted, not left as background.
                        assertTrue("page " + i + " has cell content",
                                distinctColours(pages.get(i)) > 2);
                    }
                    assertEquals(liveZoom, window.zoomForTest(), 0.0);
                    assertEquals(livePreferred, window.gridPreferredSizeForTest());
                    assertEquals(livePage, window.currentPageForTest());

                    BufferedImage stacked = window.renderGridSnapshot();
                    assertEquals(zoomOne.width, stacked.getWidth());
                    assertTrue(stacked.getHeight() >= 3 * zoomOne.height);
                } finally {
                    window.dispose();
                }
            }
        });
    }

    private static int distinctColours(BufferedImage image) {
        java.util.Set<Integer> colours = new java.util.HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                colours.add(Integer.valueOf(image.getRGB(x, y)));
                if (colours.size() > 8) return colours.size();
            }
        }
        return colours.size();
    }

    private static ParameterSweep threePageSweep() {
        Map<ParameterId, ParameterValueList> values =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        values.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20));
        values.put(ParameterId.MIN_SIZE, ParameterValueList.ofInts(1, 2));
        values.put(ParameterId.MAX_SIZE, ParameterValueList.ofInts(100, 200, 300));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, values);
    }
}
