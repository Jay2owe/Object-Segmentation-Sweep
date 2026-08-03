/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CanonicalScaleValueTest {

    @Test
    public void labelsRoundTripCaseInsensitively() {
        assertEquals(CanonicalScale.SMALL, CanonicalScale.fromLabel("small"));
        assertEquals(CanonicalScale.MEDIUM, CanonicalScale.fromLabel("MEDIUM"));
        assertEquals(CanonicalScale.LARGE, CanonicalScale.fromLabel("Large"));
    }

    @Test
    public void numericFormattingCollapsesEquivalentInputForms() {
        assertEquals("10", CanonicalScale.formatNumber(Integer.valueOf(10)));
        assertEquals("10", CanonicalScale.formatNumber(Double.valueOf(10.0d)));
        assertEquals("10", CanonicalScale.formatNumber(Double.valueOf(1.0e1d)));
        assertEquals("42.5", CanonicalScale.formatNumber(Double.valueOf(42.5000d)));
        assertEquals("0", CanonicalScale.formatNumber(Double.valueOf(-0.0d)));
    }

    @Test
    public void scaleValueKeepsParameterAndCanonicalValueText() {
        CanonicalScale.ScaleValue value = CanonicalScale.ScaleValue.of("sigma", 1.5000d);

        assertEquals("sigma", value.paramKey());
        assertEquals(1.5d, value.value(), 0.0001d);
        assertEquals("sigma=1.5", value.toString());
        assertTrue(CanonicalScale.ScaleValue.none().isNone());
        assertSame(CanonicalScale.ScaleValue.none(), CanonicalScale.ScaleValue.of("", 3.0d));
    }
}
