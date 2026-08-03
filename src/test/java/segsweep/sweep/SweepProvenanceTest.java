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

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SweepProvenanceTest {

    @Test
    public void fullImageCropFractionIsOne() {
        assertEquals(1.0d, fullProvenance().cropFraction(), 0.0d);
    }

    @Test
    public void squareCropFractionUsesFullImageArea() {
        SweepProvenance provenance = provenance(
                CropSpec.custom(new Rectangle(0, 0, 512, 512)),
                ranges(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20)));

        assertEquals(0.25d, provenance.cropFraction(), 0.0d);
    }

    @Test
    public void belowMinimumFractionHonoursBoundary() {
        SweepProvenance exactBoundary = provenance(
                CropSpec.custom(new Rectangle(0, 0, 500, 100)),
                1000, 1000,
                ranges(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20)));
        SweepProvenance justBelow = provenance(
                CropSpec.custom(new Rectangle(0, 0, 499, 100)),
                1000, 1000,
                ranges(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20)));

        assertFalse(exactBoundary.belowMinimumFraction(0.05d));
        assertTrue(justBelow.belowMinimumFraction(0.05d));
    }

    @Test
    public void comparableWithRejectsDifferentCropsOrDisplayedRanges() {
        SweepProvenance base = fullProvenance();
        SweepProvenance differentCrop = provenance(
                CropSpec.custom(new Rectangle(0, 0, 512, 512)),
                ranges(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20)));
        SweepProvenance differentRange = provenance(
                CropSpec.full(),
                ranges(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 30)));

        assertFalse(base.comparableWith(differentCrop));
        assertFalse(base.comparableWith(differentRange));
        assertTrue(base.comparableWith(fullProvenance()));
    }

    @Test
    public void canonicalJsonRoundTripsByteIdentically() {
        SweepProvenance provenance = provenance(
                CropSpec.custom(new Rectangle(2, 3, 512, 512)),
                ranges(ParameterId.SPHERICITY, ParameterValueList.ofDoubles(0.5d, 0.75d),
                        ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20, 30)));

        String json = provenance.toCanonicalJson();
        SweepProvenance parsed = SweepProvenance.fromCanonicalJson(json);

        assertEquals(json, parsed.toCanonicalJson());
        assertTrue(provenance.comparableWith(parsed));
    }

    @Test
    public void comparabilityIncludesIndividualSpacingsAndConnectivity() {
        Map<ParameterId, ParameterValueList> ranges = ranges(
                ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20));
        SweepProvenance base = new SweepProvenance(CropSpec.full(), 100, 100, 3,
                ranges, "micron", 1.0, 2.0, 3.0, "six");
        SweepProvenance swappedSpacing = new SweepProvenance(CropSpec.full(), 100, 100, 3,
                ranges, "micron", 2.0, 1.0, 3.0, "six");
        SweepProvenance otherConnectivity = new SweepProvenance(CropSpec.full(), 100, 100, 3,
                ranges, "micron", 1.0, 2.0, 3.0, "twenty_six");

        assertFalse(base.comparableWith(swappedSpacing));
        assertFalse(base.comparableWith(otherConnectivity));
        SweepProvenance parsed = SweepProvenance.fromCanonicalJson(base.toCanonicalJson());
        assertEquals(1.0, parsed.pixelWidth(), 0.0);
        assertEquals(2.0, parsed.pixelHeight(), 0.0);
        assertEquals(3.0, parsed.pixelDepth(), 0.0);
        assertEquals("six", parsed.connectivity());
        assertTrue(base.comparableWith(parsed));
    }

    private static SweepProvenance fullProvenance() {
        return provenance(CropSpec.full(),
                ranges(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20)));
    }

    private static SweepProvenance provenance(CropSpec crop,
                                              Map<ParameterId, ParameterValueList> ranges) {
        return provenance(crop, 1024, 1024, ranges);
    }

    private static SweepProvenance provenance(CropSpec crop,
                                              int fullWidth,
                                              int fullHeight,
                                              Map<ParameterId, ParameterValueList> ranges) {
        return new SweepProvenance(crop, fullWidth, fullHeight, 1, ranges, "micron", 0.105625d);
    }

    private static Map<ParameterId, ParameterValueList> ranges(ParameterId firstId,
                                                               ParameterValueList firstValues) {
        LinkedHashMap<ParameterId, ParameterValueList> out =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        out.put(firstId, firstValues);
        return out;
    }

    private static Map<ParameterId, ParameterValueList> ranges(ParameterId firstId,
                                                               ParameterValueList firstValues,
                                                               ParameterId secondId,
                                                               ParameterValueList secondValues) {
        LinkedHashMap<ParameterId, ParameterValueList> out =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        out.put(firstId, firstValues);
        out.put(secondId, secondValues);
        return out;
    }
}
