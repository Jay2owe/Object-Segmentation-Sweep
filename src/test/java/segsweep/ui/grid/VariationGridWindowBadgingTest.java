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
import segsweep.sweep.analysis.IouStability;
import segsweep.sweep.analysis.KneeOutcome;
import segsweep.sweep.analysis.PickResult;
import segsweep.sweep.analysis.StabilityOutcome;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VariationGridWindowBadgingTest {
    @Test
    public void agreementBadgesOneCellAsBoth() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = window();
                try {
                    window.setPickResult(new PickResult(
                            KneeOutcome.kneeAt(1, 20.0, 10.0, 30.0, 10.0, ""),
                            stableAtOne(),
                            GridTestFixtures.provenance()));

                    assertEquals(PickBadge.Kind.BOTH,
                            window.cellsForTest().get(1).badgeForTest().kind());
                    assertTrue(window.statusLabelForTest().getText()
                            .contains("Criteria agree"));
                } finally {
                    window.dispose();
                }
            }
        });
    }

    @Test
    public void disagreementBadgesTwoCellsWithoutBestWinner() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                VariationGridWindow window = window();
                try {
                    window.setPickResult(new PickResult(
                            KneeOutcome.kneeAt(0, 10.0, 10.0, 30.0, 10.0, ""),
                            stableAtOne(),
                            GridTestFixtures.provenance()));

                    assertEquals(PickBadge.Kind.KNEE,
                            window.cellsForTest().get(0).badgeForTest().kind());
                    assertEquals(PickBadge.Kind.STABILITY,
                            window.cellsForTest().get(1).badgeForTest().kind());
                    assertTrue(window.statusLabelForTest().getText()
                            .contains("Criteria disagree"));
                    assertFalse(window.statusLabelForTest().getText()
                            .toLowerCase(java.util.Locale.ROOT).contains("best"));
                } finally {
                    window.dispose();
                }
            }
        });
    }

    private static VariationGridWindow window() {
        return new VariationGridWindow(null,
                "Object Segmentation Sweep",
                GridTestFixtures.oneAxisSweep(10, 20, 30),
                GridTestFixtures.image("source", 0));
    }

    private static StabilityOutcome stableAtOne() {
        List<ParameterCombo> combos = new ArrayList<ParameterCombo>();
        combos.add(GridTestFixtures.combo(10));
        combos.add(GridTestFixtures.combo(20));
        combos.add(GridTestFixtures.combo(30));
        List<IouStability.IouSource> sources =
                new ArrayList<IouStability.IouSource>();
        sources.add(IouStability.IouSource.fromObjectIds(
                java.util.Arrays.asList(Integer.valueOf(1), Integer.valueOf(2))));
        sources.add(IouStability.IouSource.fromObjectIds(
                java.util.Arrays.asList(Integer.valueOf(1), Integer.valueOf(2))));
        sources.add(IouStability.IouSource.fromObjectIds(
                java.util.Arrays.asList(Integer.valueOf(1), Integer.valueOf(2))));
        return IouStability.score(combos, sources);
    }
}
