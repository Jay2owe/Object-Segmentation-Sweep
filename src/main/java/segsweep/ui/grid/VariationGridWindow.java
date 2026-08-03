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
import segsweep.sweep.ParameterCombo;
import segsweep.sweep.ParameterKey;
import segsweep.sweep.ParameterLabels;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.SweepProgress;
import segsweep.sweep.VariationResult;
import segsweep.sweep.analysis.KneeOutcome;
import segsweep.sweep.analysis.PickResult;
import segsweep.sweep.analysis.StabilityOutcome;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VariationGridWindow extends JDialog {

    private static final int CELL_GAP = 2;
    private static final int GRID_BORDER = 2;
    private static final int TOP_DECORATION = 72;
    private static final int SIDE_DECORATION = 24;
    private static final double MAX_ZOOM = 10.0;
    private static final double ZOOM_STEP = 1.15;
    private static final Color CANVAS_BACKGROUND = new Color(0x1E, 0x20, 0x24);

    private final SyncedSliceController controller = new SyncedSliceController();
    private final List<VariationCellPanel> cells =
            new ArrayList<VariationCellPanel>();
    private final Map<ParameterCombo, VariationCellPanel> cellsByCombo =
            new LinkedHashMap<ParameterCombo, VariationCellPanel>();
    private final String baseTitle;
    private final JToolBar toolBar = new JToolBar();
    private final JCheckBox objectOverlayCheckBox = new JCheckBox("Overlay objects");
    private final JComboBox<String> objectOverlaySourceChoice = new JComboBox<String>();
    private final JButton lutToggleButton = new JButton("Grey LUT");
    private final JButton brightnessButton = new JButton("Adjust Brightness/Contrast");
    private final JButton pickSelectedButton = new JButton("Pick selected");
    private final JButton cancelButton = new JButton("Cancel");
    private final ZoomableGrid gridPanel;
    private final JScrollPane gridScroll;
    private final JSlider zSlider = new JSlider(1, 1, 1);
    private final JLabel zSliceLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();

    private int completed;
    private int total;
    private int failed;
    private int materialised;
    private int materialisationRequests;
    private boolean updatingSlider;
    private boolean resultArrived;
    private ParameterCombo selectedCombo;
    private Dimension fitGridSize;
    private double zoom = 1.0;

    public VariationGridWindow(Window owner,
                               String title,
                               List<VariationCellPanel> sourceCells) {
        this(owner, title, sourceCells, null);
    }

    public VariationGridWindow(Window owner,
                               String title,
                               ParameterSweep displayWindow,
                               ImagePlus source) {
        this(owner, title, createCells(displayWindow, source), displayWindow);
    }

    private VariationGridWindow(Window owner,
                                String title,
                                List<VariationCellPanel> sourceCells,
                                ParameterSweep displayWindow) {
        super(owner, Dialog.ModalityType.MODELESS);
        setTitle(title == null || title.trim().length() == 0
                ? "Object Segmentation Sweep"
                : title.trim());
        this.baseTitle = getTitle();
        initialiseCells(sourceCells, displayWindow);

        configureToolBar();
        configureSlider();
        configureFooter();
        JPanel south = southPanel();

        Dimension imageSize = imageDimsFromCells();
        Rectangle desktop = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int toolBarHeight = toolBar.getPreferredSize().height;
        int southHeight = south.getPreferredSize().height;
        int availW = Math.max(1, desktop.width - SIDE_DECORATION);
        int availH = Math.max(1, desktop.height - toolBarHeight - southHeight
                - TOP_DECORATION);

        int[] dims = displayWindow == null
                ? (imageSize == null
                ? gridDimensions(cells.size())
                : optimalGrid(cells.size(), availW, availH,
                imageSize.width / (double) imageSize.height,
                CELL_GAP, GRID_BORDER))
                : gridDimensions(displayWindow);
        gridPanel = new ZoomableGrid(new GridLayout(dims[0], dims[1], CELL_GAP, CELL_GAP));
        gridPanel.setBackground(CANVAS_BACKGROUND);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(
                GRID_BORDER, GRID_BORDER, GRID_BORDER, GRID_BORDER));
        for (int i = 0; i < cells.size(); i++) {
            gridPanel.add(cells.get(i));
        }
        padGrid();
        gridScroll = new JScrollPane(gridPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        gridScroll.setBorder(BorderFactory.createEmptyBorder());
        gridScroll.getViewport().setBackground(CANVAS_BACKGROUND);
        gridScroll.setWheelScrollingEnabled(false);
        gridScroll.getVerticalScrollBar().setUnitIncrement(24);
        gridScroll.getHorizontalScrollBar().setUnitIncrement(24);
        installMouseWheelHandler();

        setLayout(new BorderLayout());
        add(toolBar, BorderLayout.NORTH);
        add(gridScroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        total = cells.size();
        setCompletedCount(0, total, 0);
        setSliceMax(controller.maxSlice());
        applySizeAndLocation(desktop, dims[0], dims[1], imageSize,
                toolBarHeight, southHeight, availW, availH);
    }

    public void setSliceMax(int sliceMax) {
        int max = Math.max(1, sliceMax);
        int value = clamp(zSlider.getValue(), 1, max);
        setSliderState(1, max, value);
        zSlider.setEnabled(max > 1);
        controller.setSlice(value);
        refreshStatus();
    }

    public void setCompletedCount(int completed, int total, int failed) {
        this.completed = Math.max(0, completed);
        this.total = Math.max(0, total);
        this.failed = Math.max(0, failed);
        progressBar.setMaximum(Math.max(1, this.total));
        progressBar.setValue(Math.min(this.completed, Math.max(1, this.total)));
        progressBar.setString(progressText());
        setTitle(baseTitle + " - " + cells.size()
                + " cells, " + this.completed + " complete");
        refreshStatus();
    }

    public void setTreeBuildProgress(int done, int total) {
        setPhaseProgress("Building tree", done, total);
    }

    public void setQueryProgress(int queried, int total) {
        setCompletedCount(queried, total, failed);
        setActionStatus("Querying component tree: " + queried + "/" + total);
    }

    public void setScoringProgress(int scored, int total) {
        setPhaseProgress("Scoring picks", scored, total);
    }

    public void setMaterialisationProgress(int realised, int requested) {
        materialised = Math.max(0, realised);
        materialisationRequests = Math.max(0, requested);
        setPhaseProgress("Materialising labels", materialised,
                Math.max(materialisationRequests, materialised));
    }

    public void applyProgress(SweepProgress progress) {
        if (progress == null) {
            return;
        }
        String phase = progress.phase().toLowerCase(Locale.ROOT);
        if (phase.contains("build")) {
            setTreeBuildProgress(progress.completed(), progress.total());
        } else if (phase.contains("scor")) {
            setScoringProgress(progress.completed(), progress.total());
        } else if (phase.contains("material")) {
            setMaterialisationProgress(progress.completed(), progress.total());
        } else {
            setQueryProgress(progress.completed(), progress.total());
        }
        if (progress.message().trim().length() > 0) {
            setActionStatus(progress.message());
        }
    }

    public void setResult(VariationResult result) {
        if (result == null) {
            return;
        }
        VariationCellPanel cell = cellsByCombo.get(result.combo());
        if (cell != null) {
            cell.setResult(result);
            resultArrived = true;
            setPickSelectedEnabled(selectedCombo != null);
        }
    }

    public void setPickResult(PickResult pickResult) {
        clearPickBadges();
        if (pickResult == null) {
            return;
        }
        KneeOutcome knee = pickResult.knee();
        StabilityOutcome stability = pickResult.stability();
        VariationCellPanel kneeCell = pickResult.kneeCombo() == null
                ? cellAt(knee.index()) : cellForCombo(pickResult.kneeCombo());
        VariationCellPanel stabilityCell = pickResult.stabilityCombo() == null
                ? cellAt(stability.index()) : cellForCombo(pickResult.stabilityCombo());
        if (pickResult.criteriaAgree() && kneeCell != null) {
            kneeCell.setPickBadge(new PickBadge(PickBadge.Kind.BOTH));
        } else {
            if (kneeCell != null) {
                kneeCell.setPickBadge(new PickBadge(PickBadge.Kind.KNEE));
            }
            if (stabilityCell != null) {
                stabilityCell.setPickBadge(new PickBadge(PickBadge.Kind.STABILITY));
            }
        }
        setActionStatus(pickStatusText(pickResult));
    }

    private VariationCellPanel cellForCombo(ParameterCombo combo) {
        if (combo == null) return null;
        VariationCellPanel exact = cellsByCombo.get(combo);
        if (exact != null) return exact;
        for (Map.Entry<ParameterCombo, VariationCellPanel> entry : cellsByCombo.entrySet()) {
            if (entry.getKey().hasSameCoordinates(combo)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void attachObjectOverlayActionListener(ActionListener listener) {
        objectOverlayCheckBox.addActionListener(listener);
    }

    public void attachObjectOverlaySourceActionListener(ActionListener listener) {
        objectOverlaySourceChoice.addActionListener(listener);
    }

    public void attachLutToggleActionListener(ActionListener listener) {
        lutToggleButton.addActionListener(listener);
    }

    public void attachBrightnessActionListener(ActionListener listener) {
        brightnessButton.addActionListener(listener);
    }

    public void attachPickSelectedActionListener(ActionListener listener) {
        pickSelectedButton.addActionListener(listener);
    }

    public void attachCancelActionListener(ActionListener listener) {
        cancelButton.addActionListener(listener);
    }

    public void setCancelEnabled(boolean enabled) {
        cancelButton.setEnabled(enabled);
    }

    public void setPickSelectedEnabled(boolean enabled) {
        pickSelectedButton.setEnabled(enabled);
    }

    public void setActionStatus(String text) {
        statusLabel.setText(text == null || text.trim().length() == 0 ? " " : text);
    }

    public boolean isObjectOverlaySelected() {
        return objectOverlayCheckBox.isSelected();
    }

    public boolean isObjectOverlaySourceRaw() {
        Object selected = objectOverlaySourceChoice.getSelectedItem();
        return selected != null && "Raw image".equals(selected.toString());
    }

    public void setObjectOverlaySourceEnabled(boolean enabled) {
        objectOverlaySourceChoice.setEnabled(enabled);
    }

    public void setObjectOverlayEnabledForAll(boolean enabled) {
        for (int i = 0; i < cells.size(); i++) cells.get(i).setObjectOverlayEnabled(enabled);
    }

    public void setObjectOverlaySourceRawForAll(boolean raw) {
        for (int i = 0; i < cells.size(); i++) cells.get(i).setObjectOverlaySourceRaw(raw);
    }

    public void setObjectDisplaySettingsForAll(
            segsweep.ui.render.PreviewDisplaySettings settings) {
        for (int i = 0; i < cells.size(); i++) cells.get(i).setObjectDisplaySettings(settings);
    }

    public void setLutToggleText(String text, String tooltip) {
        lutToggleButton.setText(text == null || text.trim().isEmpty() ? "Grey LUT" : text);
        lutToggleButton.setToolTipText(tooltip);
    }

    public ParameterCombo selectedCombo() {
        return selectedCombo;
    }

    @Override public void dispose() {
        VariationCellPanel.disposeAllImages(cells);
        super.dispose();
    }

    public JToolBar toolBarForTest() {
        return toolBar;
    }

    public JCheckBox objectOverlayCheckBoxForTest() {
        return objectOverlayCheckBox;
    }

    public JComboBox<String> objectOverlaySourceChoiceForTest() {
        return objectOverlaySourceChoice;
    }

    public JButton lutToggleButtonForTest() {
        return lutToggleButton;
    }

    public JButton brightnessButtonForTest() {
        return brightnessButton;
    }

    public JButton pickSelectedButtonForTest() {
        return pickSelectedButton;
    }

    public JButton cancelButtonForTest() {
        return cancelButton;
    }

    public JSlider zSliderForTest() {
        return zSlider;
    }

    public JLabel zSliceLabelForTest() {
        return zSliceLabel;
    }

    public JProgressBar progressBarForTest() {
        return progressBar;
    }

    public JLabel statusLabelForTest() {
        return statusLabel;
    }

    public JPanel gridPanelForTest() {
        return gridPanel;
    }

    public JScrollPane gridScrollForTest() {
        return gridScroll;
    }

    public double zoomForTest() {
        return zoom;
    }

    public List<VariationCellPanel> cellsForTest() {
        return new ArrayList<VariationCellPanel>(cells);
    }

    public SyncedSliceController controllerForTest() {
        return controller;
    }

    public VariationCellPanel cellForComboForTest(ParameterCombo combo) {
        return cellsByCombo.get(combo);
    }

    public ParameterCombo selectedComboForTest() {
        return selectedCombo;
    }

    private void initialiseCells(List<VariationCellPanel> sourceCells,
                                 ParameterSweep displayWindow) {
        if (sourceCells != null) {
            for (int i = 0; i < sourceCells.size(); i++) {
                final VariationCellPanel cell = sourceCells.get(i);
                if (cell != null) {
                    cell.setSelectionListener(new java.util.function.Consumer<ParameterCombo>() {
                        @Override public void accept(ParameterCombo combo) {
                            selectCombo(combo);
                        }
                    });
                    cell.setOnPickCommit(new java.util.function.Consumer<ParameterCombo>() {
                        @Override public void accept(ParameterCombo combo) {
                            selectCombo(combo);
                        }
                    });
                    cell.setMaterialisationListener(new Runnable() {
                        @Override public void run() {
                            setMaterialisationProgress(materialised + 1,
                                    Math.max(materialisationRequests, materialised + 1));
                        }
                    });
                    cells.add(cell);
                    cellsByCombo.put(cell.combo(), cell);
                    controller.register(cell);
                }
            }
        }
        if (displayWindow != null) {
            List<ParameterKey> keys = displayWindow.parameterKeys();
            for (int i = 0; i < cells.size(); i++) {
                cells.get(i).setFooterParameterKeys(keys);
            }
        }
    }

    private static List<VariationCellPanel> createCells(ParameterSweep sweep,
                                                        ImagePlus source) {
        if (sweep == null) {
            return new ArrayList<VariationCellPanel>();
        }
        List<ParameterCombo> combos = sweep.combos();
        final VariationComparisonSelection[] selection =
                new VariationComparisonSelection[1];
        final List<VariationCellPanel> cells =
                new ArrayList<VariationCellPanel>();
        selection[0] = new VariationComparisonSelection(null,
                new VariationComparisonSelection.Opener() {
                    @Override public void openComparison(VariationCellPanel left,
                                                         VariationCellPanel right) {
                        showComparison(left, right);
                    }
                });
        for (int i = 0; i < combos.size(); i++) {
            final VariationComparisonSelection compareSelection = selection[0];
            VariationCellPanel cell = new VariationCellPanel(combos.get(i), source,
                    null,
                    new java.util.function.BiConsumer<ParameterCombo, VariationCellPanel>() {
                        @Override public void accept(ParameterCombo combo,
                                                     VariationCellPanel cell) {
                            compareSelection.handleShiftClick(cell);
                        }
                    },
                    i);
            cell.setRawSource(source);
            cell.setObjectRawCrop(source);
            cells.add(cell);
        }
        return cells;
    }

    private static void showComparison(VariationCellPanel left, VariationCellPanel right) {
        if (left == null || right == null || GraphicsEnvironment.isHeadless()) return;
        ImagePlus leftPreview = left.previewImageForComparison();
        ImagePlus rightPreview = right.previewImageForComparison();
        if (leftPreview == null || rightPreview == null) return;
        JDialog dialog = new JDialog((Window) null, "Segmentation comparison",
                Dialog.ModalityType.MODELESS);
        JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0));
        pair.add(comparisonPanel(left, leftPreview));
        pair.add(comparisonPanel(right, rightPreview));
        dialog.add(pair);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setLocationByPlatform(true);
        dialog.setVisible(true);
    }

    private static JPanel comparisonPanel(VariationCellPanel cell, ImagePlus labels) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(formatCombo(cell.combo()), SwingConstants.CENTER), BorderLayout.NORTH);
        int slice = Math.max(1, (labels.getStackSize() + 1) / 2);
        java.awt.Image image = labels.getStack().getProcessor(slice).getBufferedImage();
        panel.add(new JScrollPane(new JLabel(new ImageIcon(image))), BorderLayout.CENTER);
        return panel;
    }

    private void configureToolBar() {
        toolBar.setFloatable(false);
        objectOverlayCheckBox.setOpaque(false);
        objectOverlayCheckBox.setSelected(true);
        objectOverlayCheckBox.setToolTipText("Draw segmented objects over the source image.");
        objectOverlaySourceChoice.addItem("Filtered image");
        objectOverlaySourceChoice.addItem("Raw image");
        objectOverlaySourceChoice.setToolTipText("Choose the image the objects are drawn over.");
        objectOverlaySourceChoice.setMaximumSize(objectOverlaySourceChoice.getPreferredSize());
        lutToggleButton.setToolTipText("Show the source in grey.");
        brightnessButton.setToolTipText("Adjust the source brightness/contrast for all tiles.");
        pickSelectedButton.setEnabled(false);
        pickSelectedButton.setToolTipText("Use the currently selected variation as the result.");
        toolBar.add(objectOverlayCheckBox);
        toolBar.add(new JLabel("over"));
        toolBar.add(objectOverlaySourceChoice);
        toolBar.addSeparator();
        toolBar.add(lutToggleButton);
        toolBar.add(brightnessButton);
        toolBar.addSeparator();
        cancelButton.setToolTipText("Cancel the running sweep.");
        toolBar.add(cancelButton);
        toolBar.add(pickSelectedButton);
    }

    private void configureSlider() {
        zSlider.addChangeListener(new ChangeListener() {
            @Override public void stateChanged(ChangeEvent e) {
                if (updatingSlider) {
                    return;
                }
                controller.setSlice(zSlider.getValue());
                refreshStatus();
            }
        });
    }

    private void configureFooter() {
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        progressBar.setStringPainted(true);
        progressBar.setMinimum(0);
    }

    private JPanel southPanel() {
        JPanel south = new JPanel(new BorderLayout());
        south.add(zRowPanel(), BorderLayout.NORTH);
        south.add(footerPanel(), BorderLayout.CENTER);
        return south;
    }

    private JPanel zRowPanel() {
        JPanel zRow = new JPanel(new BorderLayout(6, 0));
        zRow.setOpaque(false);
        zSliceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        zRow.add(new JLabel("Z:"), BorderLayout.WEST);
        zRow.add(zSlider, BorderLayout.CENTER);
        zRow.add(zSliceLabel, BorderLayout.EAST);
        return zRow;
    }

    private JPanel footerPanel() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                new Color(0xC0, 0xC0, 0xC0)));
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(progressBar, BorderLayout.EAST);
        return footer;
    }

    private void installMouseWheelHandler() {
        gridPanel.addMouseWheelListener(new MouseWheelListener() {
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    double factor = Math.pow(ZOOM_STEP, -e.getWheelRotation());
                    zoomBy(factor, e.getPoint());
                    return;
                }
                if (zoom > 1.0) {
                    panBy(e.getWheelRotation(), e.isShiftDown());
                    return;
                }
                if (!zSlider.isEnabled()) {
                    return;
                }
                int next = zSlider.getValue() + e.getWheelRotation();
                zSlider.setValue(clamp(next, zSlider.getMinimum(), zSlider.getMaximum()));
            }
        });
    }

    private void zoomBy(double factor, Point cursor) {
        JViewport viewport = gridScroll.getViewport();
        Dimension extent = viewport.getExtentSize();
        if (fitGridSize == null) {
            fitGridSize = new Dimension(Math.max(1, extent.width),
                    Math.max(1, extent.height));
        }
        double newZoom = Math.max(1.0, Math.min(MAX_ZOOM, zoom * factor));
        if (Math.abs(newZoom - zoom) < 1e-4) {
            return;
        }
        Point view = viewport.getViewPosition();
        Point anchor = cursor == null ? new Point(view.x, view.y) : cursor;
        int cursorVpX = anchor.x - view.x;
        int cursorVpY = anchor.y - view.y;
        double ratio = newZoom / zoom;
        zoom = newZoom;
        Dimension pref = new Dimension(
                Math.max(1, (int) Math.round(fitGridSize.width * zoom)),
                Math.max(1, (int) Math.round(fitGridSize.height * zoom)));
        gridPanel.setPreferredSize(pref);
        gridPanel.revalidate();
        gridScroll.validate();
        int targetX = (int) Math.round(anchor.x * ratio) - cursorVpX;
        int targetY = (int) Math.round(anchor.y * ratio) - cursorVpY;
        int maxX = Math.max(0, gridPanel.getWidth() - extent.width);
        int maxY = Math.max(0, gridPanel.getHeight() - extent.height);
        viewport.setViewPosition(new Point(
                clamp(targetX, 0, maxX), clamp(targetY, 0, maxY)));
    }

    private void panBy(int wheelRotation, boolean horizontal) {
        JViewport viewport = gridScroll.getViewport();
        Dimension extent = viewport.getExtentSize();
        Point view = viewport.getViewPosition();
        int delta = wheelRotation * 48;
        if (horizontal) {
            int maxX = Math.max(0, gridPanel.getWidth() - extent.width);
            view.x = clamp(view.x + delta, 0, maxX);
        } else {
            int maxY = Math.max(0, gridPanel.getHeight() - extent.height);
            view.y = clamp(view.y + delta, 0, maxY);
        }
        viewport.setViewPosition(view);
    }

    private void padGrid() {
        GridLayout layout = (GridLayout) gridPanel.getLayout();
        int capacity = layout.getRows() * layout.getColumns();
        while (gridPanel.getComponentCount() < capacity) {
            JPanel empty = new JPanel();
            empty.setBackground(CANVAS_BACKGROUND);
            gridPanel.add(empty);
        }
    }

    private void refreshStatus() {
        statusLabel.setText("Slice " + controller.currentSlice()
                + " / " + Math.max(1, controller.maxSlice())
                + "  |  Variants: " + cells.size()
                + "  |  " + completed + "/" + total + " complete"
                + (failed > 0 ? " (" + failed + " failed)" : ""));
        updateSliceLabel();
    }

    private void setSliderState(int minimum, int maximum, int value) {
        updatingSlider = true;
        try {
            zSlider.setMinimum(minimum);
            zSlider.setMaximum(maximum);
            zSlider.setValue(clamp(value, minimum, maximum));
        } finally {
            updatingSlider = false;
        }
    }

    private void updateSliceLabel() {
        int max = Math.max(1, zSlider.getMaximum());
        int current = clamp(controller.currentSlice(), 1, max);
        zSliceLabel.setText(current + " / " + max);
    }

    private String progressText() {
        String text = completed + "/" + total + " complete";
        if (failed > 0) {
            text += " (" + failed + " failed)";
        }
        return text;
    }

    private void setPhaseProgress(String phase, int done, int total) {
        int max = Math.max(1, total);
        progressBar.setMaximum(max);
        progressBar.setValue(clamp(done, 0, max));
        progressBar.setString(phase + ": " + Math.max(0, done) + "/" + Math.max(0, total));
        setActionStatus(phase + ": " + Math.max(0, done) + "/" + Math.max(0, total));
    }

    private Dimension imageDimsFromCells() {
        for (int i = 0; i < cells.size(); i++) {
            Dimension size = cells.get(i).sourceImageSize();
            if (size != null && size.width > 0 && size.height > 0) {
                return size;
            }
        }
        return null;
    }

    private void applySizeAndLocation(Rectangle desktop, int rows, int cols,
                                      Dimension imageSize, int toolBarHeight,
                                      int southHeight, int availW, int availH) {
        setMinimumSize(new Dimension(640, 480));
        if (imageSize == null) {
            int width = clamp((int) Math.round(desktop.width * 0.85d), 640, 1600);
            int height = clamp((int) Math.round(desktop.height * 0.85d), 480, 1200);
            setSize(width, height);
            setLocation(desktop.x + (desktop.width - width) / 2,
                    desktop.y + (desktop.height - height) / 2);
            return;
        }
        int[] cell = computeCellSize(rows, cols, availW, availH,
                imageSize.width, imageSize.height);
        int gridW = cols * cell[0] + (cols - 1) * CELL_GAP + 2 * GRID_BORDER;
        int gridH = rows * cell[1] + (rows - 1) * CELL_GAP + 2 * GRID_BORDER;
        gridPanel.setPreferredSize(new Dimension(gridW, gridH));
        fitGridSize = new Dimension(gridW, gridH);
        pack();
        Dimension packed = getSize();
        int width = Math.min(Math.max(packed.width, 640), desktop.width);
        int height = Math.min(Math.max(packed.height, 480), desktop.height);
        setSize(width, height);
        setLocation(desktop.x + Math.max(0, (desktop.width - width) / 2),
                desktop.y + Math.max(0, (desktop.height - height) / 2));
    }

    private void selectCombo(ParameterCombo combo) {
        selectedCombo = combo;
        setPickSelectedEnabled(selectedCombo != null);
        setActionStatus("Selected " + formatCombo(combo));
    }

    private void clearPickBadges() {
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).setPickBadge(null);
        }
    }

    private VariationCellPanel cellAt(int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : null;
    }

    private String pickStatusText(PickResult pick) {
        KneeOutcome knee = pick.knee();
        StabilityOutcome stability = pick.stability();
        String kneeText = "Knee: " + outcomeValue(knee, pick.kneeCombo());
        String stabilityText = "Stability: "
                + outcomeValue(stability, pick.stabilityCombo());
        return pick.criteriaAgree()
                ? kneeText + ". " + stabilityText + ". Criteria agree."
                : kneeText + ". " + stabilityText + ". Criteria disagree.";
    }

    private String outcomeValue(KneeOutcome knee, ParameterCombo combo) {
        if (knee == null || knee.kind() != KneeOutcome.Kind.KNEE_AT) {
            return knee == null ? "unavailable" : knee.kind().name().toLowerCase(Locale.ROOT);
        }
        return combo == null ? formatNumber(knee.parameterValue()) : formatCombo(combo);
    }

    private String outcomeValue(StabilityOutcome stability, ParameterCombo combo) {
        if (stability == null || stability.kind() != StabilityOutcome.Kind.STABLE_AT) {
            return stability == null
                    ? "unavailable"
                    : stability.kind().name().toLowerCase(Locale.ROOT);
        }
        return combo == null ? "index " + stability.index() : formatCombo(combo);
    }

    private static String formatCombo(ParameterCombo combo) {
        if (combo == null || combo.values().isEmpty()) {
            return "combination";
        }
        Map.Entry<ParameterKey, Object> first = combo.values().entrySet().iterator().next();
        return ParameterLabels.shortKey(first.getKey()) + " " + formatNumber(first.getValue());
    }

    private static String formatNumber(Object value) {
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

    private static int[] computeCellSize(int rows, int cols, int availW,
                                         int availH, int imageW, int imageH) {
        double cellAvailW = (availW - 2.0 * GRID_BORDER - (cols - 1) * CELL_GAP)
                / Math.max(1, cols);
        double cellAvailH = (availH - 2.0 * GRID_BORDER - (rows - 1) * CELL_GAP)
                / Math.max(1, rows);
        double scale = Math.min(cellAvailW / imageW, cellAvailH / imageH);
        if (!(scale > 0.0)) {
            scale = 1.0;
        }
        return new int[] {
                Math.max(1, (int) Math.floor(imageW * scale)),
                Math.max(1, (int) Math.floor(imageH * scale))
        };
    }

    static int[] optimalGrid(int count, int availW, int availH,
                             double imageAspect, int gap, int border) {
        int n = Math.max(1, count);
        if (!(imageAspect > 0.0) || availW <= 0 || availH <= 0) {
            return gridDimensions(n);
        }
        int bestCols = 1;
        double bestTileHeight = -1.0;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (int) Math.ceil(n / (double) cols);
            double cellW = (availW - 2.0 * border - (cols - 1) * gap) / cols;
            double cellH = (availH - 2.0 * border - (rows - 1) * gap) / rows;
            if (cellW <= 1.0 || cellH <= 1.0) {
                continue;
            }
            double tileHeight = Math.min(cellH, cellW / imageAspect);
            if (tileHeight > bestTileHeight) {
                bestTileHeight = tileHeight;
                bestCols = cols;
            }
        }
        int rows = (int) Math.ceil(n / (double) bestCols);
        return new int[] { rows, bestCols };
    }

    static int[] gridDimensions(int count) {
        int n = Math.max(1, count);
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil(n / (double) cols);
        return new int[] { rows, cols };
    }

    static int[] gridDimensions(ParameterSweep sweep) {
        if (sweep == null || sweep.valueLists().isEmpty()) {
            return new int[] { 1, 1 };
        }
        List<ParameterValueList> axes =
                new ArrayList<ParameterValueList>(sweep.valueLists().values());
        if (axes.size() == 1) {
            return new int[] { 1, Math.max(1, axes.get(0).size()) };
        }
        return new int[] {
                Math.max(1, axes.get(0).size()),
                Math.max(1, axes.get(1).size())
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class ZoomableGrid extends JPanel implements Scrollable {
        ZoomableGrid(GridLayout layout) {
            super(layout);
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override public int getScrollableUnitIncrement(Rectangle visible,
                                                        int orientation,
                                                        int direction) {
            return 24;
        }

        @Override public int getScrollableBlockIncrement(Rectangle visible,
                                                         int orientation,
                                                         int direction) {
            return orientation == SwingConstants.HORIZONTAL
                    ? Math.max(1, visible.width - 24)
                    : Math.max(1, visible.height - 24);
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return zoom <= 1.0;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return zoom <= 1.0;
        }
    }
}
