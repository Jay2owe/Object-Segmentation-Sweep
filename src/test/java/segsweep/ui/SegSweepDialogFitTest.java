/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui;

import ij.Prefs;
import org.junit.Assume;
import org.junit.Test;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Found by the GUI checks of 0.2.0: after Suggest range the dialog's fixed
 * height cut off the OUTPUT section on a 1536x864 screen, and at ImageJ's GUI
 * scale 1.5 its text stayed at the unscaled size.
 */
public class SegSweepDialogFitTest {

    /**
     * With no image open the cost line said "No parameter sweep was provided."
     * although a sweep was set; it now says an image is needed.
     */
    @Test
    public void costLineWithoutAnImageAsksForOne() {
        String text = SegSweepDialog.costEstimateText(null, SegSweepDialog.defaults());
        assertEquals(SegSweepDialog.NO_IMAGE_MESSAGE, text);
        assertTrue(text, text.startsWith("No image is selected"));
    }

    @Test
    public void fixedSizesFollowTheGuiScale() {
        double before = Prefs.getGuiScale();
        try {
            Prefs.setGuiScale(1.5);
            assertEquals(150, SegSweepDialog.scaled(100));
            Prefs.setGuiScale(1.0);
            assertEquals(100, SegSweepDialog.scaled(100));
        } finally {
            Prefs.setGuiScale(before);
        }
    }

    /**
     * The GUI check measured 12 pt text at GUI scale 1.5: ImageJ's
     * GUI.scale(Component) skips Swing controls. Each control is now scaled
     * once, including one that inherits its parent's font.
     */
    @Test
    public void fontsGrowOnceWithTheGuiScale() {
        double before = Prefs.getGuiScale();
        try {
            Prefs.setGuiScale(1.5);
            JPanel panel = new JPanel();
            panel.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
            javax.swing.JLabel own = new javax.swing.JLabel("Image:");
            own.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));
            javax.swing.JLabel inherits = new javax.swing.JLabel("From:");
            inherits.setFont(null);
            panel.add(own);
            panel.add(inherits);

            SegSweepDialog.scaleFonts(panel);

            assertEquals(18.0f, panel.getFont().getSize2D(), 0.01f);
            assertEquals(15.0f, own.getFont().getSize2D(), 0.01f);
            assertEquals(18.0f, inherits.getFont().getSize2D(), 0.01f);
        } finally {
            Prefs.setGuiScale(before);
        }
    }

    @Test
    public void tallContentScrollsInsideAWindowThatFitsTheScreen() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                JDialog dialog = new JDialog();
                try {
                    JPanel tall = new JPanel();
                    tall.setPreferredSize(new Dimension(500, 5000));
                    JScrollPane scroll = new JScrollPane(tall);
                    dialog.setContentPane(scroll);
                    dialog.pack();
                    dialog.setLocation(-4000, -4000);
                    SegSweepDialog.fitToScreen(dialog, scroll, true);

                    Rectangle screen = ij.gui.GUI.getMaxWindowBounds(dialog);
                    Rectangle bounds = dialog.getBounds();
                    assertTrue(bounds + " within " + screen, screen.contains(bounds));
                    assertEquals(screen.height, bounds.height);

                    // Later fits only grow: a shorter content keeps the size.
                    tall.setPreferredSize(new Dimension(500, 100));
                    SegSweepDialog.fitToScreen(dialog, scroll, false);
                    assertEquals(bounds.height, dialog.getHeight());
                } finally {
                    dialog.dispose();
                }
            }
        });
    }
}
