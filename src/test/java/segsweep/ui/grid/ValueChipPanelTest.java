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
import segsweep.sweep.ParameterValueList;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ValueChipPanelTest {
    @Test
    public void editsPreserveCanonicalValueList() {
        ValueChipPanel panel = new ValueChipPanel(
                ParameterValueList.ofInts(1, 2), ValueChipPanel.intParser());

        panel.addValueForTest("3");
        panel.editValueForTest(0, "4");
        panel.removeValueForTest(1);

        assertEquals(Arrays.<Object>asList(Integer.valueOf(4), Integer.valueOf(3)),
                panel.currentValueList().values());
    }
}
