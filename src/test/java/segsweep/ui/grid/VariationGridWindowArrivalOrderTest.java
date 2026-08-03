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

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VariationGridWindowArrivalOrderTest {
    @Test
    public void outOfOrderResultsLandByComboIdentity() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null,
                        "Object Segmentation Sweep",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    ParameterCombo first = GridTestFixtures.combo(10);
                    ParameterCombo last = GridTestFixtures.combo(30);
                    window.setResult(GridTestFixtures.result(last));
                    window.setResult(GridTestFixtures.result(first));

                    List<VariationCellPanel> cells = window.cellsForTest();
                    assertEquals(first, cells.get(0).combo());
                    assertEquals(last, cells.get(2).combo());
                    assertTrue(window.cellForComboForTest(last).isAcceptEnabledForTest());
                    GridLayout layout = (GridLayout) window.gridPanelForTest().getLayout();
                    assertEquals(1, layout.getRows());
                    assertEquals(3, layout.getColumns());
                } finally {
                    window.dispose();
                }
            }
        });
    }
}
