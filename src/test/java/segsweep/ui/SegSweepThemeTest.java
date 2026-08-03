package segsweep.ui;

import org.junit.Test;

import javax.swing.border.EmptyBorder;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SegSweepThemeTest {

    @Test
    public void exposesInheritedSpacingAndTypographyWithoutFlashBranding() {
        EmptyBorder border = SegSweepTheme.pad(2, 4, 6, 8);
        Insets insets = border.getBorderInsets();

        assertEquals(2, insets.top);
        assertEquals(4, insets.left);
        assertEquals(6, insets.bottom);
        assertEquals(8, insets.right);
        assertEquals(new Dimension(22, 22), SegSweepTheme.helpButtonSize());
        assertTrue(SegSweepTheme.h1().isBold());
        assertEquals(Font.PLAIN, SegSweepTheme.body().getStyle());
    }

    @Test
    public void dialogExposesEveryClassicalSweepAxis() {
        assertEquals(Arrays.asList("threshold", "min_size", "max_size", "volume",
                        "mean_intensity", "max_intensity", "elongation", "surface_area",
                        "sphericity", "compactness", "feret_diameter_max"),
                Arrays.asList(SegSweepDialog.axisNames()));
    }
}
