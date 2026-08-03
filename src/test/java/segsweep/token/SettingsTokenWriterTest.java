/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.token;

import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.SweepProvenance;

import org.junit.Test;

import java.awt.Rectangle;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SettingsTokenWriterTest {

    @Test
    public void settingsLineRoundTripsThroughLenientParser() {
        SegmentationMethod method = SegmentationTokenParser.parse(
                "classical;thresh=32;minSize=50;maxSize=2147483647");

        String text = SettingsTokenWriter.write(method, croppedProvenance(),
                SettingsTokenWriter.PickSummary.of("knee", "32 (threshold units)",
                        "28 (mean neighbour IoU 0.91)", "criteria disagree"),
                Instant.parse("2026-08-01T14:22:11Z"));

        String settingsLine = lineValue(text, "settings");
        SegmentationMethod parsed = SegmentationTokenParser.parseLenient(settingsLine);
        assertTrue(parsed.isClassical());
        assertEquals(32.0d, SegmentationMethod.threshold(parsed), 0.0d);
        assertEquals(50, SegmentationMethod.minSize(parsed));
        assertEquals(Integer.MAX_VALUE, SegmentationMethod.maxSize(parsed));
    }

    @Test
    public void fullImageSettingsContainRegionAndDisplayedRangeLines() {
        String text = SettingsTokenWriter.write(SegmentationMethod.classical("classical"),
                fullProvenance(), null, Instant.parse("2026-08-01T14:22:11Z"));

        assertTrue(text.contains("displayed_range\tthreshold=10,20,30"));
        assertTrue(text.contains("region\tx=0 y=0 w=1024 h=1024 (100.0% of image)"));
        assertTrue(text.contains("provenance\t{"));
    }

    @Test
    public void croppedSettingsContainRegionAndDisplayedRangeLines() {
        String text = SettingsTokenWriter.write(SegmentationMethod.classical("classical"),
                croppedProvenance(), null, Instant.parse("2026-08-01T14:22:11Z"));

        assertTrue(text.contains("displayed_range\tthreshold=10,20,30"));
        assertTrue(text.contains("region\tx=512 y=512 w=512 h=512 (25.0% of image)"));
    }

    private static String lineValue(String text, String key) {
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(key + "\t")) {
                return lines[i].substring(key.length() + 1);
            }
        }
        throw new AssertionError("Missing line " + key + " in:\n" + text);
    }

    private static SweepProvenance fullProvenance() {
        return new SweepProvenance(CropSpec.full(), 1024, 1024, 1,
                ranges(), "micron", 0.105625d);
    }

    private static SweepProvenance croppedProvenance() {
        return new SweepProvenance(CropSpec.custom(new Rectangle(512, 512, 512, 512)),
                1024, 1024, 1, ranges(), "micron", 0.105625d);
    }

    private static Map<ParameterId, ParameterValueList> ranges() {
        LinkedHashMap<ParameterId, ParameterValueList> out =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        out.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(10, 20, 30));
        return out;
    }
}
