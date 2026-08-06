/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Count ceilings are policy and may be overridden; the memory budget is
 * arithmetic and may not. These pin that difference, because collapsing the two
 * back into one boolean is what made both look equally final to the user.
 */
public class ResourceGuardLimitsTest {

    private static ImagePlus smallImage() {
        return new ImagePlus("guard", new ByteProcessor(16, 16));
    }

    private static ParameterSweep sweepOf(int cells) {
        int[] values = new int[cells];
        for (int i = 0; i < cells; i++) {
            values[i] = i;
        }
        Map<ParameterId, ParameterValueList> axes =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        axes.put(ParameterId.THRESHOLD, ParameterValueList.ofInts(values));
        return new ParameterSweep(ParameterSweep.Method.CLASSICAL, axes);
    }

    @Test
    public void aPermittedSweepReportsNoRefusal() {
        ResourceGuard.Feasibility feasibility =
                ResourceGuard.assessFeasibility(sweepOf(4), smallImage());
        assertTrue(feasibility.getMessage(), feasibility.isOk());
        assertEquals(ResourceGuard.RefusalKind.NONE, feasibility.refusalKind());
        assertFalse(feasibility.overridable());
    }

    @Test
    public void tooManyDisplayCellsIsACountLimitAndSaysItCanBeOverridden() {
        ResourceGuard.Feasibility feasibility = ResourceGuard.assessFeasibility(
                sweepOf((int) ResourceGuard.Limits.DEFAULT_MAX_DISPLAY_CELLS + 1),
                smallImage());

        assertFalse(feasibility.isOk());
        assertEquals(ResourceGuard.RefusalKind.COUNT_LIMIT, feasibility.refusalKind());
        assertTrue(feasibility.overridable());
        assertTrue(feasibility.getMessage(),
                feasibility.getMessage().contains("default limit"));
    }

    @Test
    public void liftingTheCountLimitsPermitsTheSameSweep() {
        ParameterSweep sweep =
                sweepOf((int) ResourceGuard.Limits.DEFAULT_MAX_DISPLAY_CELLS + 1);
        assertFalse(ResourceGuard.assessFeasibility(sweep, smallImage()).isOk());

        ResourceGuard.Feasibility lifted = ResourceGuard.assessFeasibility(
                sweep, smallImage(), ResourceGuard.Limits.withoutCountLimits());
        assertTrue(lifted.getMessage(), lifted.isOk());
    }

    @Test
    public void aRaisedCeilingIsHonouredWithoutLiftingEveryLimit() {
        ParameterSweep sweep = sweepOf(150);
        ResourceGuard.Limits limits =
                ResourceGuard.Limits.defaults().withMaxDisplayCells(200L);

        assertTrue(ResourceGuard.assessFeasibility(sweep, smallImage(), limits).isOk());
        assertFalse(limits.countLimitsLifted());
        assertEquals(ResourceGuard.Limits.DEFAULT_MAX_COMPUTE_CELLS,
                limits.maxComputeCells());
    }

    @Test
    public void anUnsupportedImageIsInvalidInputAndIsNotOverridable() {
        ImagePlus rgb = new ImagePlus("rgb", new ColorProcessor(8, 8));
        ResourceGuard.Feasibility feasibility =
                ResourceGuard.assessFeasibility(sweepOf(2), rgb);

        assertFalse(feasibility.isOk());
        assertEquals(ResourceGuard.RefusalKind.INVALID_INPUT, feasibility.refusalKind());
        assertFalse(feasibility.overridable());
        assertFalse(feasibility.getMessage(),
                feasibility.getMessage().contains("default limit"));
    }

    @Test
    public void aMemoryRefusalIsNeverOverridableAndNeverOffersOne() {
        // A budget of zero bytes cannot be argued with, however small the sweep.
        ResourceGuard.Feasibility feasibility =
                ResourceGuard.assessComputeFeasibilityForBudget(
                        sweepOf(2), smallImage(), 1, 0L);

        assertFalse(feasibility.isOk());
        assertEquals(ResourceGuard.RefusalKind.MEMORY_BUDGET, feasibility.refusalKind());
        assertFalse(feasibility.overridable());
        assertFalse(feasibility.getMessage(),
                feasibility.getMessage().contains("default limit"));
    }

    @Test
    public void liftedCountsStillCannotBuyMemoryThatIsNotThere() {
        ResourceGuard.Feasibility feasibility =
                ResourceGuard.assessComputeFeasibilityForBudget(
                        sweepOf(2), smallImage(), 1, 0L);
        assertEquals(ResourceGuard.RefusalKind.MEMORY_BUDGET, feasibility.refusalKind());

        // Same budget, ceilings lifted: still refused, and for the same reason.
        ResourceGuard.Feasibility lifted = ResourceGuard.assessFeasibility(
                sweepOf(2), smallImage(), ResourceGuard.Limits.withoutCountLimits());
        assertTrue(lifted.refusalKind() == ResourceGuard.RefusalKind.NONE
                || lifted.refusalKind() == ResourceGuard.RefusalKind.MEMORY_BUDGET);
    }

    @Test
    public void defaultsAreUnchangedFromTheShippedConstants() {
        ResourceGuard.Limits defaults = ResourceGuard.Limits.defaults();
        assertEquals(10000L, defaults.maxComputeCells());
        assertEquals(250000000L, defaults.maxComputeVoxelQueries());
        assertEquals(100L, defaults.maxDisplayCells());
        assertFalse(defaults.countLimitsLifted());
        assertTrue(ResourceGuard.Limits.withoutCountLimits().countLimitsLifted());
    }
}
