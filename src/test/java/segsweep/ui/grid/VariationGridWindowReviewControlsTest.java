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

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The progress grid used to show a Pick pill on every finished tile, although
 * nothing handled it until the final grid replaced the window.
 */
public class VariationGridWindowReviewControlsTest {

    @Test
    public void pickPillsStayHiddenWhileReviewControlsAreDisabled() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "progress",
                        GridTestFixtures.oneAxisSweep(10, 20),
                        GridTestFixtures.image("source", 0));
                try {
                    window.setReviewControlsEnabled(false);
                    window.setResult(GridTestFixtures.result(GridTestFixtures.combo(10)));
                    VariationCellPanel cell = window.cellForComboForTest(
                            GridTestFixtures.combo(10));
                    assertFalse(cell.isPickPillVisibleForTest());
                    window.setReviewControlsEnabled(true);
                    assertTrue(cell.isPickPillVisibleForTest());
                } finally {
                    window.dispose();
                }
            }
        });
    }
}
