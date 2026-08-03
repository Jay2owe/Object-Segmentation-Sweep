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
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
}
