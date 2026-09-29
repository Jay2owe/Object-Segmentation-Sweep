package segsweep.ui.render;

public final class PreviewDisplaySettings {

    public enum LutMode {
        GREY,
        CHANNEL
    }

    private final double displayMin;
    private final double displayMax;
    private final LutMode lutMode;
    private final String channelLutName;

    private PreviewDisplaySettings(double displayMin, double displayMax,
                                   LutMode lutMode, String channelLutName) {
        this.displayMin = displayMin;
        this.displayMax = displayMax;
        this.lutMode = lutMode == null ? LutMode.CHANNEL : lutMode;
        this.channelLutName = normalizeLutName(channelLutName);
    }

    public static PreviewDisplaySettings of(double displayMin, double displayMax,
                                            LutMode lutMode, String channelLutName) {
        return new PreviewDisplaySettings(displayMin, displayMax, lutMode, channelLutName);
    }

    public static PreviewDisplaySettings defaultFor(String channelLutName) {
        return new PreviewDisplaySettings(Double.NaN, Double.NaN, LutMode.CHANNEL, channelLutName);
    }

    public boolean hasDisplayRange() {
        return Double.isFinite(displayMin) && Double.isFinite(displayMax) && displayMax > displayMin;
    }

    public double getDisplayMin() {
        return displayMin;
    }

    public double getDisplayMax() {
        return displayMax;
    }

    public LutMode getLutMode() {
        return lutMode;
    }

    public String getChannelLutName() {
        return channelLutName;
    }

    public String effectiveLutName() {
        return lutMode == LutMode.GREY ? "Grays" : channelLutName;
    }

    public PreviewDisplaySettings withChannelLutName(String channelLutName) {
        return new PreviewDisplaySettings(displayMin, displayMax, lutMode, channelLutName);
    }

    /**
     * The name of {@code image}'s colour table when it is one this renderer can
     * reproduce (grey or a primary/secondary colour ramp), else "Grays". Used
     * to seed the grid's display settings, which used to pair a coloured
     * channel with the name "Grays" and turned tiles grey on the first edit.
     */
    public static String lutNameOf(ij.ImagePlus image) {
        if (image == null) return "Grays";
        try {
            java.awt.image.ColorModel model = image.isComposite()
                    ? ((ij.CompositeImage) image).getChannelLut()
                    : image.getProcessor().getColorModel();
            if (!(model instanceof java.awt.image.IndexColorModel)) return "Grays";
            java.awt.image.IndexColorModel lut = (java.awt.image.IndexColorModel) model;
            if (lut.getMapSize() < 256) return "Grays";
            if (lut.getRed(0) > 8 || lut.getGreen(0) > 8 || lut.getBlue(0) > 8) return "Grays";
            boolean r = lut.getRed(255) > 200;
            boolean g = lut.getGreen(255) > 200;
            boolean b = lut.getBlue(255) > 200;
            boolean rOff = lut.getRed(255) < 56;
            boolean gOff = lut.getGreen(255) < 56;
            boolean bOff = lut.getBlue(255) < 56;
            if (r && g && b) return "Grays";
            if (r && gOff && bOff) return "Red";
            if (g && rOff && bOff) return "Green";
            if (b && rOff && gOff) return "Blue";
            if (g && b && rOff) return "Cyan";
            if (r && b && gOff) return "Magenta";
            if (r && g && bOff) return "Yellow";
            return "Grays";
        } catch (RuntimeException ex) {
            return "Grays";
        }
    }

    static String normalizeLutName(String value) {
        if (value == null) return "Grays";
        String normalized = value.trim().toLowerCase();
        if (normalized.isEmpty()) return "Grays";
        if ("gray".equals(normalized) || "grey".equals(normalized)
                || "grays".equals(normalized) || "greys".equals(normalized)) {
            return "Grays";
        }
        if ("red".equals(normalized)) return "Red";
        if ("green".equals(normalized)) return "Green";
        if ("blue".equals(normalized)) return "Blue";
        if ("cyan".equals(normalized)) return "Cyan";
        if ("magenta".equals(normalized)) return "Magenta";
        if ("yellow".equals(normalized)) return "Yellow";
        return value.trim();
    }
}
