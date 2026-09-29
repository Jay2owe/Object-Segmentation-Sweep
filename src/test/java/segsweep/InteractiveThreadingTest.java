/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.LUT;
import org.junit.Assume;
import org.junit.Test;
import segsweep.ui.grid.VariationGridWindow;
import segsweep.ui.render.PreviewDisplaySettings;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Stage 05 regression tests for the interactive run path. */
public class InteractiveThreadingTest {

    @Test
    public void gridWorkRunsOnTheEventThread() {
        Boolean onEdt = SegSweep_.onEdt(new Callable<Boolean>() {
            @Override public Boolean call() {
                return Boolean.valueOf(SwingUtilities.isEventDispatchThread());
            }
        });
        assertTrue(onEdt.booleanValue());
    }

    @Test
    public void eventThreadFailuresReachTheCaller() {
        try {
            SegSweep_.onEdt(new Callable<Object>() {
                @Override public Object call() {
                    throw new IllegalArgumentException("boom");
                }
            });
        } catch (IllegalArgumentException expected) {
            assertEquals("boom", expected.getMessage());
            return;
        }
        throw new AssertionError("expected the failure to propagate");
    }

    @Test
    public void colouredChannelSeedsItsOwnLutName() {
        ImagePlus red = new ImagePlus("red", new ByteProcessor(4, 4));
        red.getProcessor().setLut(LUT.createLutFromColor(Color.RED));
        assertEquals("Red", PreviewDisplaySettings.lutNameOf(red));
        assertEquals("Red", SegSweep_.initialDisplaySettings(red).getChannelLutName());

        ImagePlus magenta = new ImagePlus("magenta", new ByteProcessor(4, 4));
        magenta.getProcessor().setLut(LUT.createLutFromColor(Color.MAGENTA));
        assertEquals("Magenta", PreviewDisplaySettings.lutNameOf(magenta));

        assertEquals("Grays", PreviewDisplaySettings.lutNameOf(
                new ImagePlus("grey", new ByteProcessor(4, 4))));
        assertEquals("Grays", PreviewDisplaySettings.lutNameOf(null));
    }

    @Test
    public void workerHoldKeepsAnOwnedImageOpenUntilReleased() {
        ImagePlus image = new ImagePlus("owned", new ByteProcessor(4, 4));
        SegSweep_.ImageLease lease = SegSweep_.ImageLease.owned(image);
        lease.retain();
        lease.close(); // the grid window closes
        assertFalse(lease.isClosedForTest());
        assertTrue("pixels still readable by the worker", image.getProcessor() != null);
        lease.close(); // the worker finishes
        assertTrue(lease.isClosedForTest());
    }

    @Test
    public void progressGridDisablesReviewControlsAndPickPills() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                SegSweepResult finished = SegSweep.run(SegSweepParameters.builder()
                        .image(SegSweepAnalysisTest.designedKneeStack(true))
                        .axis(segsweep.sweep.ParameterId.THRESHOLD, 10, 30, 10)
                        .build());
                java.util.Map<segsweep.sweep.ParameterId, segsweep.sweep.ParameterValueList> axes =
                        new java.util.LinkedHashMap<segsweep.sweep.ParameterId,
                                segsweep.sweep.ParameterValueList>(finished.parameters().axes());
                VariationGridWindow grid = new VariationGridWindow(null, "progress",
                        new segsweep.sweep.ParameterSweep(
                                segsweep.sweep.ParameterSweep.Method.CLASSICAL, axes),
                        SegSweepAnalysisTest.designedKneeStack(true));
                try {
                    grid.setReviewControlsEnabled(false);
                    for (segsweep.sweep.VariationResult cell : finished.results()) {
                        grid.setResult(cell);
                    }
                    grid.setPickSelectedEnabled(true);
                    assertFalse(grid.pickSelectedButtonForTest().isEnabled());
                    assertFalse(grid.lutToggleButtonForTest().isEnabled());
                    assertFalse(grid.brightnessButtonForTest().isEnabled());
                    assertFalse(grid.objectOverlayCheckBoxForTest().isEnabled());
                    grid.setReviewControlsEnabled(true);
                    assertTrue(grid.lutToggleButtonForTest().isEnabled());
                } finally {
                    grid.dispose();
                }
            }
        });
    }
}
