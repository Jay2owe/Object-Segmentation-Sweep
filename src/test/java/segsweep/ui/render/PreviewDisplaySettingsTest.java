package segsweep.ui.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreviewDisplaySettingsTest {

    @Test
    public void normalizesCommonLutNames() {
        assertEquals("Grays", PreviewDisplaySettings.normalizeLutName("grey"));
        assertEquals("Grays", PreviewDisplaySettings.normalizeLutName("grays"));
        assertEquals("Red", PreviewDisplaySettings.normalizeLutName(" red "));
        assertEquals("Cyan", PreviewDisplaySettings.normalizeLutName("CYAN"));
    }

    @Test
    public void reportsUsableDisplayRangeOnlyWhenFiniteAndOrdered() {
        assertTrue(PreviewDisplaySettings.of(2.0, 8.0,
                PreviewDisplaySettings.LutMode.GREY, "Grays").hasDisplayRange());
        assertFalse(PreviewDisplaySettings.of(8.0, 2.0,
                PreviewDisplaySettings.LutMode.GREY, "Grays").hasDisplayRange());
        assertFalse(PreviewDisplaySettings.defaultFor("Red").hasDisplayRange());
    }

    @Test
    public void greyModeOverridesChannelLut() {
        PreviewDisplaySettings settings = PreviewDisplaySettings.of(0.0, 10.0,
                PreviewDisplaySettings.LutMode.GREY, "Red");

        assertEquals("Grays", settings.effectiveLutName());
        assertEquals("Red", settings.getChannelLutName());
    }
}
