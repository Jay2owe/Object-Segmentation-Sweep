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
import ij.ImageStack;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterKey;
import segsweep.sweep.ParameterLabels;
import segsweep.sweep.VariationResult;
import segsweep.ui.SegSweepTheme;
import segsweep.ui.render.ImagePreviewPanel;
import segsweep.ui.render.ObjectOverlayRenderer;
import segsweep.ui.render.PreviewDisplaySettings;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class VariationCellPanel extends JPanel {

    public enum BorderHint {
        NONE,
        KNEE,
        STABLE,
        STABILITY,
        BOTH
    }

    private static final Color CARD_BACKGROUND = new Color(0x26, 0x2A, 0x2E);
    private static final Color FOOTER_STRIP = new Color(0, 0, 0, 145);
    private static final Color FOOTER_TEXT = Color.WHITE;
    private static final Color COMPARE_BORDER = new Color(0x56, 0xB4, 0xE9);
    private static final int CELL_SIZE = 260;
    private static final int FOOTER_HEIGHT = 24;
    private static final int PICK_PILL_WIDTH = 50;
    private static final int PICK_PILL_HEIGHT = 22;
    private static final int PICK_PILL_INSET = 10;
    private static final int PEEK_DELAY_MS = 120;
    private static final int PEEK_DRAG_CANCEL_PX = 4;

    private final ParameterCombo combo;
    private final ImagePlus croppedSource;
    private final Consumer<ParameterCombo> onAccept;
    private final BiConsumer<ParameterCombo, VariationCellPanel> onCompare;
    private final int placeholderIndex;
    private final ImagePreviewPanel preview = new ImagePreviewPanel("Variation");
    private final List<ParameterKey> footerParameterKeys =
            new ArrayList<ParameterKey>();
    private final Timer haloTimer;
    private final Timer peekDelayTimer;

    private Consumer<ParameterCombo> onPickCommit;
    private Consumer<ParameterCombo> selectionListener;
    private Runnable materialisationListener;
    private VariationResult result;
    private ImagePlus cachedLabel;
    private boolean cachedLabelOwned;
    private ImagePlus displayedPreviewImage;
    private ImagePlus displayedPreviewOwned;
    private ImagePlus currentPreviewImage;
    private ImagePlus rawSourceImage;
    private ImagePlus objectRawCrop;
    private boolean objectOverlayEnabled = true;
    private boolean objectOverlaySourceRaw;
    private PreviewDisplaySettings objectDisplaySettings;
    private int objectCount = -1;
    private long durationMs = -1L;
    private double iouToNeighbours = Double.NaN;
    private String stateText = "pending";
    private String errorText = "";
    private PickBadge badge;
    private boolean selectedForCompare;
    private boolean acceptEnabled;
    private boolean baseline;
    private boolean errorState;
    private boolean hover;
    private boolean peeking;
    private boolean suppressNextClick;
    private boolean disposed;
    private Point pressPoint;

    public VariationCellPanel(ParameterCombo combo,
                              ImagePlus croppedSource,
                              Consumer<ParameterCombo> onAccept,
                              BiConsumer<ParameterCombo, VariationCellPanel> onCompare) {
        this(combo, croppedSource, onAccept, onCompare, 0);
    }

    public VariationCellPanel(ParameterCombo combo,
                              ImagePlus croppedSource,
                              Consumer<ParameterCombo> onAccept,
                              BiConsumer<ParameterCombo, VariationCellPanel> onCompare,
                              int placeholderIndex) {
        super(new BorderLayout(0, 0));
        this.combo = combo == null ? ParameterCombo.builder().build() : combo;
        this.croppedSource = croppedSource;
        this.onAccept = onAccept;
        this.onCompare = onCompare;
        this.placeholderIndex = placeholderIndex;
        this.haloTimer = new Timer(33, e -> repaint());
        this.haloTimer.setInitialDelay(0);
        this.peekDelayTimer = new Timer(PEEK_DELAY_MS, e -> beginPeek());
        this.peekDelayTimer.setRepeats(false);

        setOpaque(false);
        setBackground(CARD_BACKGROUND);
        setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
        setBorder(BorderFactory.createEmptyBorder());
        preview.setSlim(true);
        preview.setZRowVisible(false);
        preview.setChromeless(true);
        add(preview, BorderLayout.CENTER);
        installMouseHandlers();
        refreshTooltip();
    }

    public static VariationCellPanel baseline(ImagePlus croppedSource) {
        VariationCellPanel cell = new VariationCellPanel(
                ParameterCombo.builder().build(), croppedSource, null, null, -1);
        cell.markAsBaseline(croppedSource);
        return cell;
    }

    public ParameterCombo combo() {
        return combo;
    }

    public void setOnPickCommit(Consumer<ParameterCombo> onPickCommit) {
        this.onPickCommit = onPickCommit;
    }

    void setSelectionListener(Consumer<ParameterCombo> selectionListener) {
        this.selectionListener = selectionListener;
    }

    void setMaterialisationListener(Runnable listener) {
        this.materialisationListener = listener;
    }

    Dimension sourceImageSize() {
        ImagePlus src = croppedSource;
        if (src == null) {
            return null;
        }
        return new Dimension(Math.max(1, src.getWidth()),
                Math.max(1, src.getHeight()));
    }

    int sliceCountForSync() {
        int previewSlices = Math.max(1, preview.getSliceCount());
        if (currentPreviewImage != null || previewSlices > 1) {
            return previewSlices;
        }
        return sliceCount(croppedSource);
    }

    public ImagePreviewPanel preview() {
        return preview;
    }

    public void setFooterParameterKeys(final List<ParameterKey> keys) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                footerParameterKeys.clear();
                if (keys != null) {
                    for (int i = 0; i < keys.size(); i++) {
                        ParameterKey key = keys.get(i);
                        if (key != null && !footerParameterKeys.contains(key)) {
                            footerParameterKeys.add(key);
                        }
                    }
                }
                refreshTooltip();
                repaint();
            }
        });
    }

    public void setRawSource(final ImagePlus src) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                rawSourceImage = src;
                if (src == null) {
                    cancelPeek(true);
                }
            }
        });
    }

    public void setState(final String state) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                releaseOwnedImages();
                result = null;
                cachedLabel = null;
                cachedLabelOwned = false;
                objectCount = -1;
                durationMs = -1L;
                iouToNeighbours = Double.NaN;
                errorState = false;
                errorText = "";
                acceptEnabled = false;
                stateText = state == null || state.trim().isEmpty() ? "pending" : state;
                showPreviewImage(null);
                refreshTooltip();
                repaint();
            }
        });
    }

    public void setResult(final VariationResult next) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                if (disposed || next == null) {
                    if (next != null) {
                        next.dispose();
                    }
                    return;
                }
                releaseOwnedImages();
                result = next;
                cachedLabel = null;
                cachedLabelOwned = false;
                objectCount = next.objectCount();
                durationMs = next.durationMs();
                iouToNeighbours = next.meanNeighbourIou();
                errorState = next.hasError();
                errorText = errorState ? errorDetails(next.error()) : "";
                acceptEnabled = !errorState;
                stateText = errorState ? "failed" : String.valueOf(objectCount);
                showPreviewImage(null);
                refreshTooltip();
                repaint();
            }
        });
    }

    public void setLabel(ImagePlus label, ResultsTable stats) {
        setLabel(label, stats, stats == null ? -1 : stats.size(), -1L);
    }

    public void setLabel(final ImagePlus label,
                         final ResultsTable stats,
                         final int nObjects,
                         final long durationMs) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                releaseOwnedImages();
                result = null;
                cachedLabel = label == null ? createPlaceholderLabel() : label;
                cachedLabelOwned = label == null;
                objectCount = Math.max(0, nObjects);
                VariationCellPanel.this.durationMs = durationMs;
                errorState = false;
                errorText = "";
                acceptEnabled = true;
                stateText = String.valueOf(objectCount);
                setDisplayedPreviewImage(renderObjectPreview(), true);
                refreshTooltip();
                repaint();
            }
        });
    }

    public ImagePlus materialiseForDisplay() {
        if (SwingUtilities.isEventDispatchThread()) {
            return materialiseForDisplayOnEdt();
        }
        final ImagePlus[] out = new ImagePlus[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    out[0] = materialiseForDisplayOnEdt();
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Could not materialise labels.", e);
        }
        return out[0];
    }

    private ImagePlus materialiseForDisplayOnEdt() {
        if (cachedLabel != null || result == null || result.hasError()) {
            return cachedLabel;
        }
        ImagePlus label = result.labelMap().get();
        cachedLabel = label;
        cachedLabelOwned = true;
        setDisplayedPreviewImage(renderObjectPreview(), true);
        if (materialisationListener != null) {
            materialisationListener.run();
        }
        refreshTooltip();
        repaint();
        return label;
    }

    public void setObjectOverlayEnabled(final boolean enabled) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                objectOverlayEnabled = enabled;
                refreshObjectOverlay();
            }
        });
    }

    public void setObjectOverlaySourceRaw(final boolean raw) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                objectOverlaySourceRaw = raw;
                refreshObjectOverlay();
            }
        });
    }

    public void setObjectRawCrop(final ImagePlus rawCrop) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                objectRawCrop = rawCrop;
                refreshObjectOverlay();
            }
        });
    }

    public void setObjectDisplaySettings(final PreviewDisplaySettings settings) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                objectDisplaySettings = settings;
                refreshObjectOverlay();
            }
        });
    }

    public void setZ(final int z) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                preview.setCurrentZ(z);
            }
        });
    }

    public void setIouToNeighbours(final double iouToNeighbours) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                VariationCellPanel.this.iouToNeighbours = iouToNeighbours;
                refreshTooltip();
                repaint();
            }
        });
    }

    public void setKneeWinner(final boolean winner) {
        setPickBadge(winner ? new PickBadge(PickBadge.Kind.KNEE) : null);
    }

    public void setStabilityWinner(final boolean winner) {
        setPickBadge(winner ? new PickBadge(PickBadge.Kind.STABILITY) : null);
    }

    public void setBorderHint(BorderHint hint) {
        if (hint == null || hint == BorderHint.NONE) {
            setPickBadge(null);
        } else if (hint == BorderHint.KNEE) {
            setPickBadge(new PickBadge(PickBadge.Kind.KNEE));
        } else if (hint == BorderHint.BOTH) {
            setPickBadge(new PickBadge(PickBadge.Kind.BOTH));
        } else {
            setPickBadge(new PickBadge(PickBadge.Kind.STABILITY));
        }
    }

    public void setPickBadge(final PickBadge nextBadge) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                badge = nextBadge;
                if (badge == null) {
                    haloTimer.stop();
                } else if (!haloTimer.isRunning()) {
                    haloTimer.start();
                }
                refreshTooltip();
                repaint();
            }
        });
    }

    void setSelectedForCompare(final boolean selected) {
        runOnEdt(new Runnable() {
            @Override public void run() {
                selectedForCompare = selected;
                repaint();
            }
        });
    }

    boolean hasCachedLabel() {
        return cachedLabel != null;
    }

    ImagePlus cachedLabel() {
        return cachedLabel;
    }

    void disposeImages() {
        if (SwingUtilities.isEventDispatchThread()) {
            disposeImagesOnEdt();
            return;
        }
        final RuntimeException[] failure = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override public void run() {
                    try {
                        disposeImagesOnEdt();
                    } catch (RuntimeException e) {
                        failure[0] = e;
                    }
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Could not dispose grid cell images.", e);
        }
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    static void disposeAllImages(List<VariationCellPanel> cells) {
        if (cells == null) {
            return;
        }
        for (int i = 0; i < cells.size(); i++) {
            VariationCellPanel cell = cells.get(i);
            if (cell != null) {
                cell.disposeImages();
            }
        }
    }

    private void disposeImagesOnEdt() {
        if (disposed) {
            return;
        }
        disposed = true;
        cancelPeek(false);
        haloTimer.stop();
        releaseOwnedImages();
        cachedLabel = null;
        displayedPreviewImage = null;
        currentPreviewImage = null;
        result = null;
        preview.setImage(null);
    }

    private void refreshObjectOverlay() {
        if (baseline || errorState || cachedLabel == null) {
            return;
        }
        setDisplayedPreviewImage(renderObjectPreview(), true);
    }

    private ImagePlus renderObjectPreview() {
        ImagePlus label = cachedLabel;
        if (label == null) {
            return null;
        }
        ImagePlus source = objectOverlayEnabled ? objectOverlaySource() : null;
        ImagePlus rendered = null;
        if (source != null && dimensionsMatch(source, label)) {
            rendered = ObjectOverlayRenderer.renderFiltered(source, label, null, true,
                    objectDisplaySettings);
        }
        if (rendered == null) {
            rendered = ObjectOverlayRenderer.renderLabelMap(label, Math.max(0, objectCount));
        }
        return rendered == null ? label : rendered;
    }

    private ImagePlus objectOverlaySource() {
        ImagePlus raw = objectRawCrop;
        ImagePlus filtered = croppedSource;
        if (objectOverlaySourceRaw) {
            return raw != null ? raw : filtered;
        }
        return filtered != null ? filtered : raw;
    }

    private void markAsBaseline(ImagePlus source) {
        baseline = true;
        acceptEnabled = false;
        errorState = false;
        stateText = "Original";
        cachedLabel = null;
        cachedLabelOwned = false;
        setDisplayedPreviewImage(source, false);
        refreshTooltip();
        repaint();
    }

    private void setDisplayedPreviewImage(ImagePlus image, boolean owned) {
        ImagePlus previous = displayedPreviewOwned;
        displayedPreviewImage = image;
        displayedPreviewOwned = owned && image != null && image != cachedLabel ? image : null;
        if (previous != null && previous != displayedPreviewOwned) {
            closeImage(previous);
        }
        if (!peeking) {
            showPreviewImage(image);
        }
    }

    private void showPreviewImage(ImagePlus image) {
        currentPreviewImage = image;
        preview.setImage(image);
    }

    private void releaseOwnedImages() {
        ImagePlus ownedPreview = displayedPreviewOwned;
        displayedPreviewOwned = null;
        if (ownedPreview != null) {
            closeImage(ownedPreview);
        }
        if (cachedLabelOwned && cachedLabel != null) {
            closeImage(cachedLabel);
        }
        cachedLabelOwned = false;
    }

    private void closeImage(ImagePlus image) {
        if (image == null || image == croppedSource || image == rawSourceImage
                || image == objectRawCrop) {
            return;
        }
        image.close();
        image.flush();
    }

    private void installMouseHandlers() {
        MouseAdapter listener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e != null && SwingUtilities.isLeftMouseButton(e)
                        && !e.isShiftDown()
                        && onPickCommit != null
                        && hitPickPill(pointInCell(e))) {
                    e.consume();
                    cancelPeek(true);
                    pressPoint = null;
                    onPickCommit.accept(combo);
                    return;
                }
                handleMousePressed(e);
                if (suppressNextClick) {
                    suppressNextClick = false;
                    if (e != null) {
                        e.consume();
                    }
                    return;
                }
                if (e == null || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (e.isShiftDown()) {
                    if (onCompare != null) {
                        onCompare.accept(combo, VariationCellPanel.this);
                    }
                } else if (acceptEnabled) {
                    if (selectionListener != null) {
                        selectionListener.accept(combo);
                    }
                    if (onAccept != null) {
                        onAccept.accept(combo);
                    }
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                handleMouseReleased();
            }

            @Override public void mouseEntered(MouseEvent e) {
                hover = true;
                refreshTooltip();
                repaint();
            }

            @Override public void mouseExited(MouseEvent e) {
                hover = false;
                cancelPeek(true);
                pressPoint = null;
                refreshTooltip();
                repaint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }
        };
        installMouseHandler(this, listener);
        installMouseHandler(preview, listener);
    }

    private void installMouseHandler(Component component, MouseAdapter listener) {
        component.addMouseListener(listener);
        component.addMouseMotionListener(listener);
    }

    private void handleMousePressed(MouseEvent e) {
        cancelPeek(true);
        pressPoint = null;
        if (e == null || !SwingUtilities.isLeftMouseButton(e) || !canPeek()) {
            return;
        }
        pressPoint = pointInCell(e);
        peekDelayTimer.restart();
    }

    private void handleMouseReleased() {
        cancelPeek(true);
        pressPoint = null;
    }

    private void handleMouseDragged(MouseEvent e) {
        if (pressPoint == null || e == null) {
            return;
        }
        Point current = pointInCell(e);
        if (current == null) {
            return;
        }
        int dx = current.x - pressPoint.x;
        int dy = current.y - pressPoint.y;
        if (dx * dx + dy * dy > PEEK_DRAG_CANCEL_PX * PEEK_DRAG_CANCEL_PX) {
            cancelPeek(true);
            pressPoint = null;
        }
    }

    private boolean canPeek() {
        return rawSourceImage != null && displayedPreviewImage != null;
    }

    private void beginPeek() {
        peekDelayTimer.stop();
        if (pressPoint == null || !canPeek()) {
            return;
        }
        peeking = true;
        suppressNextClick = true;
        showPreviewImage(rawSourceImage);
        repaint();
    }

    private void cancelPeek(boolean restoreImage) {
        peekDelayTimer.stop();
        if (restoreImage && peeking) {
            peeking = false;
            showPreviewImage(displayedPreviewImage);
            repaint();
        } else if (!restoreImage) {
            peeking = false;
        }
    }

    private Point pointInCell(MouseEvent e) {
        Object source = e.getSource();
        if (source instanceof Component) {
            return SwingUtilities.convertPoint((Component) source, e.getPoint(), this);
        }
        return e.getPoint();
    }

    private boolean pickPillVisible() {
        return acceptEnabled && !baseline;
    }

    private Rectangle pickPillBounds() {
        int x = Math.max(PICK_PILL_INSET,
                getWidth() - PICK_PILL_WIDTH - PICK_PILL_INSET);
        int y = PICK_PILL_INSET;
        return new Rectangle(x, y, PICK_PILL_WIDTH, PICK_PILL_HEIGHT);
    }

    private boolean hitPickPill(Point p) {
        return !peeking && pickPillVisible() && p != null
                && pickPillBounds().contains(p);
    }

    @Override protected void paintComponent(Graphics g) {
        materialiseForDisplayOnEdt();
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD_BACKGROUND);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (selectedForCompare || baseline) {
                g2.setStroke(new BasicStroke(selectedForCompare ? 4f : 3f));
                g2.setColor(selectedForCompare ? COMPARE_BORDER : SegSweepTheme.SELECTION_BORDER);
                g2.drawRect(2, 2, Math.max(1, getWidth() - 4),
                        Math.max(1, getHeight() - 4));
            }
        } finally {
            g2.dispose();
        }
    }

    @Override protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (peeking) {
            return;
        }
        paintFooter(g);
        paintBadge(g);
        paintPickPill(g);
    }

    @Override public void removeNotify() {
        cancelPeek(true);
        haloTimer.stop();
        super.removeNotify();
    }

    private void paintFooter(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int y = Math.max(0, getHeight() - FOOTER_HEIGHT);
            g2.setColor(FOOTER_STRIP);
            g2.fillRect(0, y, getWidth(), FOOTER_HEIGHT);
            g2.setFont(SegSweepTheme.mono(11f).deriveFont(Font.BOLD));
            g2.setColor(FOOTER_TEXT);
            String text = footerText();
            FontMetrics fm = g2.getFontMetrics();
            int baselineY = y + (FOOTER_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(ellipsize(text, fm, Math.max(0, getWidth() - 16)),
                    8, baselineY);
        } finally {
            g2.dispose();
        }
    }

    private void paintBadge(Graphics g) {
        if (badge == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            String text = badge.label();
            g2.setFont(SegSweepTheme.bodyMedium().deriveFont(10f));
            FontMetrics fm = g2.getFontMetrics();
            int width = Math.max(38, fm.stringWidth(text) + 14);
            int height = 20;
            int x = 8;
            int y = 8;
            g2.setColor(badge.color());
            g2.fillRoundRect(x, y, width, height, 8, 8);
            g2.setColor(new Color(0, 0, 0, 100));
            g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
            g2.setColor(badge.kind() == PickBadge.Kind.KNEE
                    ? new Color(0x22, 0x22, 0x22)
                    : Color.WHITE);
            g2.drawString(text, x + 7,
                    y + (height - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }

    private void paintPickPill(Graphics g) {
        if (!pickPillVisible()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Rectangle pill = pickPillBounds();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover ? COMPARE_BORDER.brighter() : COMPARE_BORDER);
            g2.fillRoundRect(pill.x, pill.y, pill.width, pill.height, 8, 8);
            g2.setColor(Color.WHITE);
            g2.setFont(SegSweepTheme.bodyMedium().deriveFont(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("Pick",
                    pill.x + (pill.width - fm.stringWidth("Pick")) / 2,
                    pill.y + (pill.height - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }

    private String footerText() {
        if (errorState) {
            return "!";
        }
        String text = stateText;
        if (!Double.isNaN(iouToNeighbours)) {
            text += "   Overlap "
                    + String.format(Locale.ROOT, "%.2f",
                    Double.valueOf(iouToNeighbours));
        }
        return text;
    }

    private void refreshTooltip() {
        StringBuilder sb = new StringBuilder("<html>");
        if (baseline) {
            sb.append("Original source crop");
        } else {
            sb.append(html(combo.toCanonicalJson()));
            if (errorState) {
                sb.append("<br><b>Failed:</b> ").append(html(errorText));
            } else {
                sb.append("<br>").append(html(comboSummary()));
                if (objectCount >= 0) {
                    sb.append("<br>Objects: ").append(objectCount);
                }
                if (!Double.isNaN(iouToNeighbours)) {
                    sb.append("<br>Mean neighbour IoU: ")
                            .append(String.format(Locale.ROOT, "%.2f",
                                    Double.valueOf(iouToNeighbours)));
                }
                if (durationMs >= 0L) {
                    sb.append("<br>durationMs: ").append(durationMs).append(" ms");
                }
                if (badge != null) {
                    sb.append("<br>").append(html(badge.label())).append(" criterion");
                }
                if (hover && acceptEnabled) {
                    sb.append("<br>Click to pick this combo");
                }
            }
        }
        sb.append("</html>");
        setToolTipText(sb.toString());
        preview.setToolTipText(sb.toString());
    }

    private String comboSummary() {
        if (combo.values().isEmpty()) {
            return "";
        }
        List<ParameterKey> keys = footerParameterKeys.isEmpty()
                ? new ArrayList<ParameterKey>(combo.values().keySet())
                : footerParameterKeys;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            ParameterKey key = keys.get(i);
            if (key == null || !combo.contains(key)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(ParameterLabels.shortKey(key)).append("=")
                    .append(formatValue(combo.get(key)));
        }
        return sb.toString();
    }

    private ImagePlus createPlaceholderLabel() {
        int width = croppedSource == null ? 96 : Math.max(1, croppedSource.getWidth());
        int height = croppedSource == null ? 96 : Math.max(1, croppedSource.getHeight());
        int slices = croppedSource == null ? 1 : Math.max(1, croppedSource.getStackSize());
        int labelValue = Math.floorMod(placeholderIndex, 250) + 1;
        ImageStack stack = new ImageStack(width, height);
        for (int z = 0; z < slices; z++) {
            ByteProcessor bp = new ByteProcessor(width, height);
            bp.setValue(labelValue);
            bp.fill();
            stack.addSlice("z" + (z + 1), bp);
        }
        return new ImagePlus("placeholder-" + placeholderIndex, stack);
    }

    private static int sliceCount(ImagePlus image) {
        if (image == null) {
            return 1;
        }
        int slices = Math.max(1, image.getNSlices());
        if (slices <= 1) {
            slices = Math.max(1, image.getStackSize());
        }
        return slices;
    }

    private static boolean dimensionsMatch(ImagePlus source, ImagePlus label) {
        if (source == null || label == null) {
            return false;
        }
        if (source.getWidth() != label.getWidth()
                || source.getHeight() != label.getHeight()) {
            return false;
        }
        ImageProcessor sourceProcessor = source.getProcessor();
        ImageProcessor labelProcessor = label.getProcessor();
        return sourceProcessor != null && labelProcessor != null;
    }

    private static String formatValue(Object value) {
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Double.isFinite(number)
                    && Math.abs(number - Math.rint(number)) < 0.0000001d) {
                return String.valueOf((long) Math.rint(number));
            }
            String text = String.format(Locale.ROOT, "%.3f", Double.valueOf(number));
            return text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String ellipsize(String text, FontMetrics fm, int maxWidth) {
        String safe = text == null ? "" : text;
        if (fm == null || fm.stringWidth(safe) <= maxWidth) {
            return safe;
        }
        String suffix = "...";
        int end = safe.length();
        while (end > 0
                && fm.stringWidth(safe.substring(0, end)) + fm.stringWidth(suffix)
                > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : safe.substring(0, end) + suffix;
    }

    private static String errorDetails(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private static String html(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    boolean isSelectedForCompareForTest() {
        return selectedForCompare;
    }

    String footerTextForTest() {
        return footerText();
    }

    int currentZForTest() {
        return preview.getCurrentZ();
    }

    ImagePlus cachedLabelForTest() {
        return cachedLabel;
    }

    ImagePlus currentPreviewImageForTest() {
        return currentPreviewImage;
    }

    boolean objectOverlayEnabledForTest() {
        return objectOverlayEnabled;
    }

    boolean objectOverlaySourceRawForTest() {
        return objectOverlaySourceRaw;
    }

    PreviewDisplaySettings objectDisplaySettingsForTest() {
        return objectDisplaySettings;
    }

    boolean isPeekingForTest() {
        return peeking;
    }

    boolean isPeekDelayRunningForTest() {
        return peekDelayTimer.isRunning();
    }

    boolean suppressNextClickForTest() {
        return suppressNextClick;
    }

    boolean isBaselineForTest() {
        return baseline;
    }

    boolean isAcceptEnabledForTest() {
        return acceptEnabled;
    }

    boolean isHaloTimerRunningForTest() {
        return haloTimer.isRunning();
    }

    PickBadge badgeForTest() {
        return badge;
    }

    boolean isPickPillVisibleForTest() {
        return pickPillVisible();
    }

    boolean terminalCleanupComplete() {
        return disposed;
    }

    void firePeekDelayForTest() {
        beginPeek();
    }
}
