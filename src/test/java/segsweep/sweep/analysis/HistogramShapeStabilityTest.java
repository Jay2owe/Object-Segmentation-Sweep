/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistogramShapeStabilityTest {

    @Test
    public void migratingHistogramsReturnTailPlateauWinner() {
        List<HistogramShapeStability.HistogramPoint> points =
                new ArrayList<HistogramShapeStability.HistogramPoint>();
        points.add(point(0.0d, migratingHistogram(0)));
        points.add(point(1.0d, migratingHistogram(80)));
        points.add(point(2.0d, migratingHistogram(160)));
        points.add(point(3.0d, migratingHistogram(200)));
        points.add(point(4.0d, migratingHistogram(200)));
        points.add(point(5.0d, migratingHistogram(200)));

        HistogramShapeStability.Result result =
                HistogramShapeStability.detect(points, ParameterId.THRESHOLD);

        assertTrue(result.hasPlateau());
        assertEquals(Double.valueOf(3.0d), result.plateauStartCombo.get(ParameterId.THRESHOLD));
        assertEquals(Double.valueOf(5.0d), result.plateauEndCombo.get(ParameterId.THRESHOLD));
        assertEquals(Double.valueOf(4.0d), result.winnerCombo.get(ParameterId.THRESHOLD));
    }

    @Test
    public void randomHistogramsReturnNoWinner() {
        int[] peaks = { 10, 200, 30, 220, 40, 180 };
        List<HistogramShapeStability.HistogramPoint> points =
                new ArrayList<HistogramShapeStability.HistogramPoint>();
        for (int i = 0; i < peaks.length; i++) {
            points.add(point(i, migratingHistogram(peaks[i])));
        }

        HistogramShapeStability.Result result =
                HistogramShapeStability.detect(points, ParameterId.THRESHOLD);

        assertFalse(result.hasPlateau());
    }

    private static HistogramShapeStability.HistogramPoint point(double value, int[] histogram) {
        ParameterCombo combo = ParameterCombo.builder()
                .put(ParameterId.THRESHOLD, Double.valueOf(value))
                .build();
        return new HistogramShapeStability.HistogramPoint(combo, value, histogram);
    }

    private static int[] migratingHistogram(int startBin) {
        int[] histogram = new int[256];
        int start = Math.max(0, Math.min(250, startBin));
        for (int i = 0; i < 6; i++) {
            histogram[start + i] = 100;
        }
        return histogram;
    }
}
