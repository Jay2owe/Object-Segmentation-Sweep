/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import org.junit.Test;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.VariationResult;

import java.util.EnumSet;

import static org.junit.Assert.assertFalse;

public class VariationCellPanelFailureTest {

    @Test
    public void excessLabelFailureCannotBeAccepted() {
        ParameterCombo combo = GridTestFixtures.combo(10);
        VariationResult failure = VariationResult.failure(combo,
                new IllegalStateException("more than 65535 labels"),
                GridTestFixtures.provenance(),
                EnumSet.of(VariationResult.Flag.TOO_MANY_LABELS), 70000);
        VariationCellPanel cell = new VariationCellPanel(combo, null, null, null);

        cell.setResult(failure);

        assertFalse(cell.isAcceptEnabledForTest());
        assertFalse(cell.isPickPillVisibleForTest());
        cell.disposeImages();
    }
}
