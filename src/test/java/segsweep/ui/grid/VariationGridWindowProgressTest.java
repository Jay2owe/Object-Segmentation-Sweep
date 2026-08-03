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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VariationGridWindowProgressTest {
    @Test
    public void progressCoversQueryScoringAndMaterialisation() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null,
                        "Object Segmentation Sweep",
                        GridTestFixtures.oneAxisSweep(10, 20, 30),
                        GridTestFixtures.image("source", 0));
                try {
                    window.setTreeBuildProgress(1, 2);
                    assertTrue(window.progressBarForTest().getString().contains("Building tree"));
                    window.setQueryProgress(2, 3);
                    assertEquals(2, window.progressBarForTest().getValue());
                    window.setScoringProgress(3, 3);
                    assertTrue(window.progressBarForTest().getString().contains("Scoring"));
                    window.setMaterialisationProgress(1, 2);
                    assertTrue(window.progressBarForTest().getString().contains("Materialising"));
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void pickSelectedButtonCanBeEnabledAndClicked() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null,
                        "Object Segmentation Sweep",
                        GridTestFixtures.oneAxisSweep(10),
                        GridTestFixtures.image("source", 0));
                try {
                    final AtomicBoolean fired = new AtomicBoolean();
                    window.attachPickSelectedActionListener(new ActionListener() {
                        @Override public void actionPerformed(ActionEvent e) {
                            fired.set(true);
                        }
                    });
                    window.setPickSelectedEnabled(true);
                    window.pickSelectedButtonForTest().doClick();
                    assertTrue(fired.get());
                } finally {
                    window.dispose();
                }
            }
        });
    }
}
