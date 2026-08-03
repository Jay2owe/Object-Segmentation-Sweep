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

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertTrue;

public class VariationGridWindowObjectToolbarTest {
    @Test
    public void objectModeShowsOverlayLutBrightnessAndPickControls() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null,
                        "Object Segmentation Sweep",
                        GridTestFixtures.oneAxisSweep(10, 20),
                        GridTestFixtures.image("source", 0));
                try {
                    assertTrue(window.toolBarForTest()
                            .isAncestorOf(window.objectOverlayCheckBoxForTest()));
                    assertTrue(window.toolBarForTest()
                            .isAncestorOf(window.objectOverlaySourceChoiceForTest()));
                    assertTrue(window.toolBarForTest()
                            .isAncestorOf(window.lutToggleButtonForTest()));
                    assertTrue(window.toolBarForTest()
                            .isAncestorOf(window.brightnessButtonForTest()));
                    assertTrue(window.toolBarForTest()
                            .isAncestorOf(window.pickSelectedButtonForTest()));
                    assertTrue(window.isObjectOverlaySelected());
                } finally {
                    window.dispose();
                }
            }
        });
    }
}
