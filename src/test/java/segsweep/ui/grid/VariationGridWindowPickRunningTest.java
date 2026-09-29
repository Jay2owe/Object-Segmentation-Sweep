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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Found in review of 0.2.0: selecting another tile re-enabled Pick selected
 * while a pick was still running, so a tile's Pick pill could start a second
 * pick into the same output folder.
 */
public class VariationGridWindowPickRunningTest {

    @Test
    public void pickStaysDisabledWhileAPickRuns() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = new VariationGridWindow(null, "pick",
                        GridTestFixtures.oneAxisSweep(10, 20),
                        GridTestFixtures.image("source", 0));
                try {
                    final AtomicInteger picks = new AtomicInteger();
                    window.attachPickSelectedActionListener(new ActionListener() {
                        @Override public void actionPerformed(ActionEvent e) {
                            picks.incrementAndGet();
                        }
                    });
                    window.setResult(GridTestFixtures.result(GridTestFixtures.combo(10)));
                    window.setResult(GridTestFixtures.result(GridTestFixtures.combo(20)));
                    VariationCellPanel first = window.cellForComboForTest(GridTestFixtures.combo(10));
                    VariationCellPanel second = window.cellForComboForTest(GridTestFixtures.combo(20));

                    first.commitPickForTest();
                    assertEquals(1, picks.get());

                    window.setPickRunning(true);
                    second.commitPickForTest();
                    assertEquals("no second pick while one runs", 1, picks.get());
                    assertFalse(window.pickSelectedButtonForTest().isEnabled());
                    window.setResult(GridTestFixtures.result(GridTestFixtures.combo(20)));
                    assertFalse(window.pickSelectedButtonForTest().isEnabled());

                    window.setPickRunning(false);
                    assertTrue(window.pickSelectedButtonForTest().isEnabled());
                    second.commitPickForTest();
                    assertEquals(2, picks.get());
                } finally {
                    window.dispose();
                }
            }
        });
    }
}
