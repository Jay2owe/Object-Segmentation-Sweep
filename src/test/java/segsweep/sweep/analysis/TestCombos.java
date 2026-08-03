/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep.analysis;

import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.SweepProvenance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TestCombos {

    private TestCombos() {
    }

    static List<ParameterCombo> oneAxis(List<Integer> values) {
        List<ParameterCombo> combos = new ArrayList<ParameterCombo>();
        for (int i = 0; i < values.size(); i++) {
            combos.add(ParameterCombo.builder()
                    .put(ParameterId.THRESHOLD, values.get(i))
                    .build());
        }
        return combos;
    }

    static List<ParameterCombo> threeAxis(int a, int b, int c) {
        List<ParameterCombo> combos = new ArrayList<ParameterCombo>();
        for (int i = 0; i < a; i++) {
            for (int j = 0; j < b; j++) {
                for (int k = 0; k < c; k++) {
                    combos.add(ParameterCombo.builder()
                            .put(ParameterId.THRESHOLD, Integer.valueOf(i))
                            .put(ParameterId.MIN_SIZE, Integer.valueOf(j))
                            .put(ParameterId.MAX_SIZE, Integer.valueOf(k))
                            .build());
                }
            }
        }
        return combos;
    }

    static List<Integer> ids(int... ids) {
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < ids.length; i++) {
            out.add(Integer.valueOf(ids[i]));
        }
        return out;
    }

    static List<IouStability.IouSource> repeatedSources(int count, List<Integer> ids) {
        List<IouStability.IouSource> out = new ArrayList<IouStability.IouSource>();
        for (int i = 0; i < count; i++) {
            out.add(IouStability.IouSource.fromObjectIds(ids));
        }
        return out;
    }

    @SafeVarargs
    static List<IouStability.IouSource> sources(List<Integer>... ids) {
        List<IouStability.IouSource> out = new ArrayList<IouStability.IouSource>();
        for (int i = 0; i < ids.length; i++) {
            out.add(IouStability.IouSource.fromObjectIds(ids[i]));
        }
        return out;
    }

    static SweepProvenance provenance() {
        Map<ParameterId, ParameterValueList> ranges =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        ranges.put(ParameterId.THRESHOLD,
                new ParameterValueList(Arrays.asList(
                        Integer.valueOf(0), Integer.valueOf(10),
                        Integer.valueOf(20), Integer.valueOf(30))));
        return new SweepProvenance(CropSpec.full(), 10, 10, 1,
                ranges, "micron", 1.0d);
    }
}
