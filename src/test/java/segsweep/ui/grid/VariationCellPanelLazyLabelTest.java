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
import segsweep.sweep.VariationResult;
import segsweep.tree.LazyLabelMap;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class VariationCellPanelLazyLabelTest {
    @Test
    public void setResultDoesNotMaterialiseUntilCellDraws() throws Exception {
        final LazyLabelMap labelMap = GridTestFixtures.labelMap();
        final VariationResult result = VariationResult.success(
                GridTestFixtures.combo(10), labelMap, 2, 5L, null,
                GridTestFixtures.provenance());
        final VariationCellPanel cell = new VariationCellPanel(
                GridTestFixtures.combo(10), GridTestFixtures.image("source", 0),
                null, null);
        final AtomicInteger materialised = new AtomicInteger();
        cell.setMaterialisationListener(new Runnable() {
            @Override public void run() {
                materialised.incrementAndGet();
            }
        });

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                cell.setResult(result);
            }
        });

        assertEquals(0, labelMap.materializationCount());
        assertNull(cell.cachedLabelForTest());

        paint(cell);

        assertEquals(1, labelMap.materializationCount());
        assertEquals(1, materialised.get());
        assertSame(cell.cachedLabelForTest(), cell.materialiseForDisplay());
        assertEquals(1, labelMap.materializationCount());
    }

    @Test
    public void secondCellGetsIndependentImagePlus() throws Exception {
        VariationCellPanel first = new VariationCellPanel(
                GridTestFixtures.combo(10), null, null, null);
        VariationCellPanel second = new VariationCellPanel(
                GridTestFixtures.combo(20), null, null, null);
        first.setResult(GridTestFixtures.result(GridTestFixtures.combo(10)));
        second.setResult(GridTestFixtures.result(GridTestFixtures.combo(20)));

        ImagePlus a = first.materialiseForDisplay();
        ImagePlus b = second.materialiseForDisplay();

        assertNotSame(a, b);
    }

    private static void paint(VariationCellPanel cell) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                cell.setSize(260, 260);
                BufferedImage image = new BufferedImage(260, 260,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    cell.paint(graphics);
                } finally {
                    graphics.dispose();
                }
            }
        });
    }
}
