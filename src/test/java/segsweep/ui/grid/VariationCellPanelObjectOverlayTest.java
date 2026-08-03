/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

import ij.ImagePlus;
import org.junit.Test;
import segsweep.ui.render.PreviewDisplaySettings;

import javax.swing.SwingUtilities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VariationCellPanelObjectOverlayTest {
    @Test
    public void overlayToggleSwitchesBetweenOverlayAndBareLabelMap() throws Exception {
        final ImagePlus filtered = GridTestFixtures.image("filtered", 10);
        final ImagePlus raw = GridTestFixtures.image("raw", 100);
        final VariationCellPanel cell = new VariationCellPanel(
                GridTestFixtures.combo(10), filtered, null, null);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                cell.setObjectRawCrop(raw);
                cell.setLabel(GridTestFixtures.labels(), null, 2, 5L);
            }
        });

        assertTrue(cell.objectOverlayEnabledForTest());
        assertTrue(cell.currentPreviewImageForTest().getTitle().contains("filtered"));

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                cell.setObjectOverlayEnabled(false);
            }
        });
        assertFalse(cell.objectOverlayEnabledForTest());
        assertTrue(cell.currentPreviewImageForTest().getTitle()
                .startsWith("Object label preview"));

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                cell.setObjectOverlayEnabled(true);
                cell.setObjectOverlaySourceRaw(true);
            }
        });
        assertTrue(cell.objectOverlaySourceRawForTest());
        assertTrue(cell.currentPreviewImageForTest().getTitle().contains("raw"));
    }

    @Test
    public void displaySettingsAreRetainedForOverlayBackground() throws Exception {
        final VariationCellPanel cell = new VariationCellPanel(
                GridTestFixtures.combo(10), GridTestFixtures.image("filtered", 10),
                null, null);
        final PreviewDisplaySettings grey = PreviewDisplaySettings.of(
                0.0, 255.0, PreviewDisplaySettings.LutMode.GREY, "Red");

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                cell.setObjectDisplaySettings(grey);
                cell.setLabel(GridTestFixtures.labels(), null, 2, 5L);
            }
        });

        assertTrue(cell.objectDisplaySettingsForTest() == grey);
        assertTrue(cell.currentPreviewImageForTest().getTitle().startsWith("Object overlay"));
    }
}
