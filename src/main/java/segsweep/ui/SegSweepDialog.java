/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import segsweep.SegSweepMacroOptions;
import segsweep.SegSweepMacroOptionsParser;
import segsweep.SegSweepParameters;
import segsweep.SegSweepResult;
import segsweep.SweepStateStore;
import segsweep.sweep.CropSpec;
import segsweep.sweep.ParameterId;
import segsweep.sweep.ParameterSweep;
import segsweep.sweep.ParameterValueList;
import segsweep.sweep.RangeSuggester;
import segsweep.sweep.ResourceGuard;
import segsweep.sweep.SourceImageView;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compact three-section dialog for Object Segmentation Sweep.
 */
public final class SegSweepDialog {

    private static final Color BG_COLOR = new Color(245, 245, 245);
    private static final Color HEADER_COLOR = new Color(55, 71, 79);
    private static final Color LABEL_COLOR = new Color(33, 33, 33);
    private static final Color WARNING_COLOR = new Color(160, 74, 0);
    private static final String NONE = "(none)";

    private final ImagePlus activeImage;

    public SegSweepDialog(ImagePlus activeImage) {
        this.activeImage = activeImage;
    }

    public SegSweepMacroOptions showDialog() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Interactive SegSweepDialog cannot be shown in headless mode.");
        }
        final DialogState state = new DialogState(activeImage);
        final JDialog dialog = new JDialog((Frame) null, "Object Segmentation Sweep", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setLayout(new BorderLayout());
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_COLOR);
        content.setBorder(new EmptyBorder(15, 20, 10, 20));
        dialog.getContentPane().add(content, BorderLayout.CENTER);

        addInputSection(content, state);
        addAnalysisSection(content, state);
        addOutputSection(content, state);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setBackground(BG_COLOR);
        JButton cancel = new JButton("Cancel");
        JButton run = new JButton("Run");
        buttons.add(cancel);
        buttons.add(run);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);

        final SegSweepMacroOptions[] accepted = new SegSweepMacroOptions[1];
        cancel.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        run.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                try {
                    SegSweepMacroOptions options = state.optionsFromFields();
                    ImagePlus image = selectedImage(state);
                    if (!confirmFeasible(dialog, image, options)) {
                        return;
                    }
                    SweepStateStore.save(options);
                    accepted[0] = options;
                    dialog.dispose();
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(),
                            "Object Segmentation Sweep", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        applyRestoredOptions(state, SweepStateStore.restoreFor(selectedImage(state)));

        state.refreshCostLine();
        dialog.pack();
        Dimension pref = dialog.getPreferredSize();
        dialog.setSize(Math.max(560, pref.width), Math.min(620, Math.max(460, pref.height)));
        dialog.setLocationRelativeTo(null);
        try {
            dialog.setVisible(true);
            return accepted[0];
        } finally {
            state.disposeBrowsedImage();
        }
    }

    public static SegSweepMacroOptions defaults() {
        return SegSweepMacroOptions.defaults();
    }

    /**
     * Decides whether Run may proceed, offering an override where one is honest.
     *
     * <p>Two different things used to arrive at the user as the same error box.
     * A cell-count ceiling is a default about how long a wait is reasonable and
     * how many previews a window should hold; someone sweeping a deliberately
     * large grid on a big machine is entitled to overrule it. A memory-budget
     * refusal is arithmetic about heap that is not there, and overruling it
     * only converts a clear message into an OutOfMemoryError partway through.
     * So only the first is offered as a choice, and taking it is logged.</p>
     */
    private static boolean confirmFeasible(JDialog parent,
                                           ImagePlus image,
                                           SegSweepMacroOptions options) {
        ResourceGuard.Feasibility feasibility =
                feasibility(image, options, options.limits());
        if (feasibility.isOk()) {
            return true;
        }
        if (!feasibility.overridable()) {
            JOptionPane.showMessageDialog(parent, feasibility.getMessage(),
                    "Object Segmentation Sweep", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        int choice = JOptionPane.showConfirmDialog(parent,
                feasibility.getMessage() + "\n\nRun it anyway?",
                "Object Segmentation Sweep",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return false;
        }
        ResourceGuard.Feasibility lifted =
                feasibility(image, options, ResourceGuard.Limits.withoutCountLimits());
        if (!lifted.isOk()) {
            // The count ceiling was hiding a memory shortfall behind it.
            JOptionPane.showMessageDialog(parent, lifted.getMessage(),
                    "Object Segmentation Sweep", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        // Recorded on the options so the analysis honours it and the macro
        // string reproduces the run without stopping at the same refusal.
        options.setAllowOversizedSweep(true);
        IJ.log("Object Segmentation Sweep: proceeding past a default limit at the "
                + "user's request. " + feasibility.getMessage());
        return true;
    }

    /**
     * Seeds the already-built widgets from remembered settings.
     *
     * <p>Runs after every section exists, so it is one place to look for what is
     * and is not carried between sessions rather than a restore rule buried in
     * each widget's construction. The image is never restored, and the ROI
     * region is offered back only when the image in front of the user actually
     * has an ROI — the sweep re-reads the live selection at run time, so
     * selecting the region without one would only produce an error on Run.</p>
     *
     * <p>Package-visible and free of Swing look-ups beyond the state object so
     * the restore rules can be tested without showing a dialog.</p>
     */
    static void applyRestoredOptions(DialogState state, SegSweepMacroOptions restored) {
        if (state == null || restored == null) {
            return;
        }
        SegSweepMacroOptions.AxisSpec primary = restored.primaryAxis();
        if (primary != null && !primary.hasExplicitValues()) {
            selectItem(state.axisChoice, primary.id().stableKey());
            setText(state.fromField, format(primary.from()));
            setText(state.toField, format(primary.to()));
            setText(state.stepField, format(primary.step()));
        }
        SegSweepMacroOptions.AxisSpec secondary = restored.secondaryAxis();
        if (secondary != null && !secondary.hasExplicitValues()) {
            selectItem(state.axis2Choice, secondary.id().stableKey());
            setText(state.from2Field, format(secondary.from()));
            setText(state.to2Field, format(secondary.to()));
            setText(state.step2Field, format(secondary.step()));
        } else {
            selectItem(state.axis2Choice, NONE);
        }
        setText(state.channelField, Integer.toString(Math.max(1, restored.channel())));
        selectItem(state.pickChoice,
                restored.pickCriterion().name().toLowerCase(Locale.ROOT));
        if (state.showGrid != null) state.showGrid.setSelected(restored.showGrid());
        if (state.showTables != null) state.showTables.setSelected(restored.showTables());
        String autosave = restored.autosave();
        setText(state.autosaveField, autosave == null || autosave.trim().isEmpty()
                ? SegSweepMacroOptions.AUTOSAVE_ALONGSIDE_INPUT
                : autosave.trim());
        ImagePlus image = selectedImage(state);
        boolean roiAvailable = image != null && image.getRoi() != null;
        boolean wantsRoi = restored.crop() != null
                && restored.crop().mode() == CropSpec.Mode.CUSTOM;
        selectItem(state.cropChoice,
                wantsRoi && roiAvailable ? "Sweep in ROI" : "Whole image");
    }

    private static void selectItem(JComboBox<String> choice, String value) {
        if (choice == null || value == null) {
            return;
        }
        for (int i = 0; i < choice.getItemCount(); i++) {
            if (value.equals(choice.getItemAt(i))) {
                choice.setSelectedIndex(i);
                return;
            }
        }
    }

    private static void setText(JTextField field, String value) {
        if (field != null && value != null) {
            field.setText(value);
        }
    }

    public static String costEstimateText(ImagePlus image, SegSweepMacroOptions options) {
        ResourceGuard.Feasibility feasibility = feasibility(image, options);
        long combinations = combinationCount(options);
        boolean gridRequested = options == null
                || (!options.hideDisplay() && options.showGrid());
        String prefix = combinations + (gridRequested
                ? " combinations displayed; "
                : " combinations computed without a grid; ")
                + "classical computes the crop once. ";
        ResourceGuard.Estimate estimate = feasibility.estimate();
        String memory = estimate == null
                ? ""
                : "Estimated tree memory ~" + formatMb(estimate.totalBytes()) + ". ";
        return feasibility.isOk()
                ? prefix + memory + "This display window can run."
                : prefix + feasibility.getMessage();
    }

    public static ResourceGuard.Feasibility feasibility(ImagePlus image,
                                                        SegSweepMacroOptions options) {
        return feasibility(image, options, ResourceGuard.Limits.defaults());
    }

    /** Feasibility under caller-supplied limits; see {@link ResourceGuard.Limits}. */
    public static ResourceGuard.Feasibility feasibility(ImagePlus image,
                                                        SegSweepMacroOptions options,
                                                        ResourceGuard.Limits limits) {
        if (image == null) {
            return ResourceGuard.assessFeasibility(null, null, limits);
        }
        SegSweepMacroOptions safe = options == null ? defaults() : options;
        Map<ParameterId, ParameterValueList> axes =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        SegSweepMacroOptions.AxisSpec primary = safe.primaryAxis();
        if (primary == null) {
            primary = defaults().primaryAxis();
        }
        axes.put(primary.id(), primary.valueList());
        if (safe.secondaryAxis() != null) {
            axes.put(safe.secondaryAxis().id(), safe.secondaryAxis().valueList());
        }
        ParameterSweep sweep = new ParameterSweep(ParameterSweep.Method.CLASSICAL,
                axes, safe.crop(), "C" + safe.channel());
        return safe.hideDisplay() || !safe.showGrid()
                ? ResourceGuard.assessMontageFeasibility(sweep, image, limits)
                : ResourceGuard.assessFeasibility(sweep, image, limits);
    }

    public static SegSweepMacroOptions applySuggestedRange(ImagePlus image,
                                                           SegSweepMacroOptions options,
                                                           ParameterId axis) {
        if (image == null) {
            throw new IllegalArgumentException("An image is required to suggest a display range.");
        }
        ParameterId safeAxis = axis == null ? ParameterId.THRESHOLD : axis;
        if (!supportsRangeSuggestion(safeAxis)) {
            throw new IllegalArgumentException("Automatic range suggestion is not available for "
                    + safeAxis.displayLabel() + ". Enter From, To, and Step explicitly.");
        }
        SegSweepMacroOptions safeOptions = options == null ? defaults() : options;
        ImagePlus analysed = SourceImageView.selectedChannelAndCrop(
                image, safeOptions.channel(), safeOptions.crop());
        ParameterValueList suggested;
        try {
            suggested = safeAxis == ParameterId.THRESHOLD
                    ? RangeSuggester.suggestThresholdDisplayWindow(analysed, CropSpec.full())
                    : RangeSuggester.suggestSizeDisplayWindow(analysed, CropSpec.full());
        } finally {
            analysed.changes = false;
            analysed.close();
            analysed.flush();
        }
        SegSweepMacroOptions out = copyOf(safeOptions);
        out.setPrimaryAxis(SegSweepMacroOptions.AxisSpec.values(safeAxis, suggested));
        return out;
    }

    static boolean supportsRangeSuggestion(ParameterId axis) {
        return axis == ParameterId.THRESHOLD
                || axis == ParameterId.MIN_SIZE
                || axis == ParameterId.MAX_SIZE;
    }

    private static void updateSuggestButton(JButton button, ParameterId axis) {
        boolean supported = supportsRangeSuggestion(axis);
        button.setEnabled(supported);
        button.setToolTipText(supported
                ? "Suggest a range from the selected image and crop."
                : "Automatic suggestion is available only for threshold, min size, and max size.");
    }

    public static String warningsStatusText(SegSweepResult result) {
        if (result == null || result.warnings().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Warnings: ");
        List<String> warnings = result.warnings();
        for (int i = 0; i < warnings.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(warnings.get(i));
        }
        return sb.toString();
    }

    public static String[] analysisExplanationSentences() {
        return new String[] {
                "Choose one displayed parameter window, optionally add one second displayed window, and the grid shows every combination in that window.",
                "Classical segmentation still builds the component tree for the crop once; narrowing From/To/Step changes what you inspect and report, not the compute budget."
        };
    }

    public static String killCriterionRecord() {
        String[] sentences = analysisExplanationSentences();
        return "Analysis section is one compact screenshot: Engine, Display, optional Also sweep, Pick, and cost line. "
                + sentences[0] + " " + sentences[1];
    }

    static long combinationCount(SegSweepMacroOptions options) {
        if (options == null || options.primaryAxis() == null) {
            return 0L;
        }
        long count = options.primaryAxis().valueList().size();
        if (options.secondaryAxis() != null) {
            count *= options.secondaryAxis().valueList().size();
        }
        return count;
    }

    private static SegSweepMacroOptions copyOf(SegSweepMacroOptions source) {
        return SegSweepMacroOptionsParser.parse(source.toMacroOptions());
    }

    private static void addInputSection(final JPanel content, final DialogState state) {
        addHeader(content, "INPUT");
        JPanel imageRow = row("Image:");
        state.imageChoice = new JComboBox<String>(imageTitles());
        state.imageChoice.setSelectedItem(defaultImageTitle());
        state.imageChoice.setMaximumSize(new Dimension(300, 24));
        imageRow.add(state.imageChoice);
        JButton browse = new JButton("Browse...");
        state.browseButton = browse;
        browse.setToolTipText("Choose an image file without opening it in ImageJ first.");
        imageRow.add(browse);
        content.add(imageRow);
        state.channelRow = row("Channel:");
        state.channelField = new JTextField("1", 4);
        state.channelRow.add(state.channelField);
        content.add(state.channelRow);
        JPanel calibrationRow = row("Calibration:");
        state.calibrationLabel = new JLabel();
        state.calibrationLabel.setForeground(LABEL_COLOR);
        calibrationRow.add(state.calibrationLabel);
        content.add(calibrationRow);
        state.cropChoice = addChoice(content, "Region:", new String[] { "Whole image", "Sweep in ROI" },
                activeRoiExists(state.image) ? "Sweep in ROI" : "Whole image");
        browse.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose image for Object Segmentation Sweep");
                chooser.setFileFilter(new FileNameExtensionFilter(
                        "Image files", "tif", "tiff", "png", "jpg", "jpeg", "gif", "bmp"));
                if (state.browsedFile != null) chooser.setSelectedFile(state.browsedFile);
                if (chooser.showOpenDialog(content) != JFileChooser.APPROVE_OPTION) return;
                try {
                    state.selectBrowsedFile(chooser.getSelectedFile());
                    state.cropChoice.setSelectedItem("Whole image");
                    state.refreshInputMetadata();
                    state.refreshCostLine();
                } catch (RuntimeException ex) {
                    JOptionPane.showMessageDialog(content, ex.getMessage(),
                            "Object Segmentation Sweep", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        state.imageChoice.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                state.clearSuggestedPrimaryValues();
                state.releaseBrowsedImageIfUnselected();
                state.refreshInputMetadata();
                state.refreshCostLine();
            }
        });
        state.cropChoice.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                state.clearSuggestedPrimaryValues();
                state.refreshCostLine();
            }
        });
        installSuggestionDependencyRefresh(state.channelField, state);
        state.refreshInputMetadata();
    }

    private static void addAnalysisSection(JPanel content, final DialogState state) {
        addHeader(content, "ANALYSIS");
        state.engineChoice = addChoice(content, "Engine:", new String[] { "Classical" }, "Classical");
        state.axisChoice = addChoice(content, "Display:", axisNames(), "threshold");
        JPanel range = row("From:");
        state.fromField = new JTextField("10", 5);
        state.toField = new JTextField("60", 5);
        state.stepField = new JTextField("5", 5);
        JButton suggest = new JButton("Suggest range");
        state.suggestButton = suggest;
        range.add(state.fromField);
        range.add(new JLabel("To:"));
        range.add(state.toField);
        range.add(new JLabel("Step:"));
        range.add(state.stepField);
        range.add(suggest);
        content.add(range);
        state.explicitValuesLabel = addMessage(content, "");
        state.explicitValuesLabel.setForeground(LABEL_COLOR);
        state.explicitValuesLabel.setVisible(false);
        state.axis2Choice = addChoice(content, "Also sweep:",
                secondaryAxisNames(), NONE);
        JPanel range2 = row("From:");
        state.from2Field = new JTextField("", 5);
        state.to2Field = new JTextField("", 5);
        state.step2Field = new JTextField("", 5);
        range2.add(state.from2Field);
        range2.add(new JLabel("To:"));
        range2.add(state.to2Field);
        range2.add(new JLabel("Step:"));
        range2.add(state.step2Field);
        content.add(range2);
        state.pickChoice = addChoice(content, "Choose value by:",
                new String[] { "both", "knee", "stability", "none" }, "both");
        state.costLine = addMessage(content, " ");
        state.costLine.setForeground(WARNING_COLOR);
        suggest.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                ImagePlus image = selectedImage(state);
                if (image == null) return;
                try {
                    SegSweepMacroOptions current = state.optionsFromFields();
                    ParameterId axis = ParameterId.fromStableKey((String) state.axisChoice.getSelectedItem());
                    SegSweepMacroOptions suggested = applySuggestedRange(image, current, axis);
                    ParameterValueList list = suggested.primaryAxis().valueList();
                    state.suggestedPrimaryAxis = axis;
                    state.suggestedPrimaryValues = list;
                    state.showSuggestedPrimaryValues();
                    state.applyingSuggestedPrimaryValues = true;
                    try {
                        state.fromField.setText(format(list.get(0)));
                        state.toField.setText(format(list.get(list.size() - 1)));
                        state.stepField.setText(list.size() > 1
                                ? format(stepBetween(list))
                                : "1");
                    } finally {
                        state.applyingSuggestedPrimaryValues = false;
                    }
                    state.refreshCostLine();
                } catch (RuntimeException ex) {
                    IJ.error("Object Segmentation Sweep", ex.getMessage());
                }
            }
        });
        ActionListener refresh = new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                ParameterId axis = ParameterId.fromStableKey(
                        (String) state.axisChoice.getSelectedItem());
                updateSuggestButton(suggest, axis);
                state.refreshCostLine();
            }
        };
        state.axisChoice.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                state.clearSuggestedPrimaryValues();
                ParameterId axis = ParameterId.fromStableKey(
                        (String) state.axisChoice.getSelectedItem());
                updateSuggestButton(suggest, axis);
                state.refreshCostLine();
            }
        });
        state.axis2Choice.addActionListener(refresh);
        state.pickChoice.addActionListener(refresh);
        updateSuggestButton(suggest, ParameterId.THRESHOLD);
        installPrimaryRangeRefresh(state.fromField, state);
        installPrimaryRangeRefresh(state.toField, state);
        installPrimaryRangeRefresh(state.stepField, state);
        installTextRefresh(state.from2Field, state);
        installTextRefresh(state.to2Field, state);
        installTextRefresh(state.step2Field, state);
    }

    private static void addOutputSection(JPanel content, DialogState state) {
        addHeader(content, "OUTPUT");
        state.showGrid = addToggle(content, "Show grid", true);
        state.showTables = addToggle(content, "Show results tables", true);
        state.autosaveField = addField(content, "Save to:",
                SegSweepMacroOptions.AUTOSAVE_ALONGSIDE_INPUT, 22);
        state.showGrid.addChangeListener(new Runnable() {
            @Override public void run() {
                state.refreshCostLine();
            }
        });
    }

    private static void addHeader(JPanel content, String text) {
        content.add(Box.createVerticalStrut(10));
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 13f));
        label.setForeground(HEADER_COLOR);
        content.add(label);
    }

    private static JComboBox<String> addChoice(JPanel content, String label,
                                               String[] items, String selected) {
        JPanel row = row(label);
        JComboBox<String> combo = new JComboBox<String>(items);
        combo.setSelectedItem(selected);
        combo.setMaximumSize(new Dimension(260, 24));
        row.add(combo);
        content.add(row);
        return combo;
    }

    private static JTextField addField(JPanel content, String label,
                                       String value, int columns) {
        JPanel row = row(label);
        JTextField field = new JTextField(value, columns);
        row.add(field);
        content.add(row);
        return field;
    }

    private static ToggleSwitch addToggle(JPanel content, String label, boolean selected) {
        JPanel row = row(label);
        ToggleSwitch toggle = new ToggleSwitch(selected);
        row.add(toggle);
        content.add(row);
        return toggle;
    }

    private static JLabel addMessage(JPanel content, String text) {
        JLabel label = new JLabel("<html><body style='width:430px;'>" + text + "</body></html>");
        label.setForeground(LABEL_COLOR);
        content.add(label);
        return label;
    }

    private static void installTextRefresh(JTextField field, final DialogState state) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                state.refreshCostLine();
            }

            @Override public void removeUpdate(DocumentEvent e) {
                state.refreshCostLine();
            }

            @Override public void changedUpdate(DocumentEvent e) {
                state.refreshCostLine();
            }
        });
    }

    private static void installPrimaryRangeRefresh(JTextField field,
                                                   final DialogState state) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (!state.applyingSuggestedPrimaryValues) {
                    state.clearSuggestedPrimaryValues();
                }
                state.refreshCostLine();
            }

            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
    }

    private static void installSuggestionDependencyRefresh(JTextField field,
                                                            final DialogState state) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                state.clearSuggestedPrimaryValues();
                state.refreshCostLine();
            }

            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
    }

    private static JPanel row(String labelText) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setForeground(LABEL_COLOR);
        label.setPreferredSize(new Dimension(112, 22));
        row.add(label);
        return row;
    }

    private static String[] imageTitles() {
        int[] ids = WindowManager.getIDList();
        if (ids == null || ids.length == 0) {
            return new String[] { NONE };
        }
        String[] titles = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            ImagePlus image = WindowManager.getImage(ids[i]);
            titles[i] = image == null ? NONE : image.getTitle();
        }
        return titles;
    }

    private static String defaultImageTitle() {
        ImagePlus image = WindowManager.getCurrentImage();
        return image == null ? NONE : image.getTitle();
    }

    private static ImagePlus selectedImage(DialogState state) {
        if (state == null || state.imageChoice == null) return state == null ? null : state.image;
        Object selected = state.imageChoice.getSelectedItem();
        if (selected == null || NONE.equals(selected.toString())) {
            return state.image;
        }
        String value = selected.toString();
        ImagePlus openImage = WindowManager.getImage(value);
        if (openImage != null) return openImage;
        File selectedFile = new File(value);
        if (selectedFile.isFile()) {
            return state.browsedImage(selectedFile);
        }
        return null;
    }

    private static boolean activeRoiExists(ImagePlus image) {
        return image != null && image.getRoi() != null;
    }

    static String[] axisNames() {
        return new String[] { "threshold", "min_size", "max_size", "volume",
                "mean_intensity", "max_intensity", "elongation", "surface_area",
                "sphericity", "compactness", "feret_diameter_max" };
    }

    static DialogState inputStateForTest(ImagePlus image) {
        DialogState state = new DialogState(image);
        addInputSection(new JPanel(), state);
        return state;
    }

    static DialogState analysisStateForTest(ImagePlus image) {
        DialogState state = new DialogState(image);
        JPanel content = new JPanel();
        addInputSection(content, state);
        addAnalysisSection(content, state);
        return state;
    }

    private static String[] secondaryAxisNames() {
        String[] axes = axisNames();
        String[] out = new String[axes.length + 1];
        out[0] = NONE;
        System.arraycopy(axes, 0, out, 1, axes.length);
        return out;
    }

    private static double stepBetween(ParameterValueList list) {
        if (list == null || list.size() < 2) return 1.0d;
        return numeric(list.get(1)) - numeric(list.get(0));
    }

    private static double numeric(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue()
                : Double.parseDouble(String.valueOf(value));
    }

    private static String format(Object value) {
        if (value instanceof Number) {
            return format(((Number) value).doubleValue());
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", Double.valueOf(value))
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatMb(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB",
                Double.valueOf(bytes / (1024.0d * 1024.0d)));
    }

    static final class DialogState {
        final ImagePlus image;
        File browsedFile;
        ImagePlus browsedImage;
        JComboBox<String> imageChoice;
        JButton browseButton;
        JButton suggestButton;
        JPanel channelRow;
        JTextField channelField;
        JLabel calibrationLabel;
        JLabel explicitValuesLabel;
        JComboBox<String> cropChoice;
        JComboBox<String> engineChoice;
        JComboBox<String> axisChoice;
        JTextField fromField;
        JTextField toField;
        JTextField stepField;
        JComboBox<String> axis2Choice;
        JTextField from2Field;
        JTextField to2Field;
        JTextField step2Field;
        JComboBox<String> pickChoice;
        JLabel costLine;
        ToggleSwitch showGrid;
        ToggleSwitch showTables;
        JTextField autosaveField;
        ParameterId suggestedPrimaryAxis;
        ParameterValueList suggestedPrimaryValues;
        boolean applyingSuggestedPrimaryValues;

        DialogState(ImagePlus image) {
            this.image = image;
        }

        void selectBrowsedFile(File file) {
            if (file == null || !file.isFile()) {
                throw new IllegalArgumentException("Selected image file does not exist: " + file);
            }
            File absolute = file.getAbsoluteFile();
            ImagePlus opened = IJ.openImage(absolute.getAbsolutePath());
            if (opened == null) {
                throw new IllegalArgumentException("ImageJ could not open: "
                        + absolute.getAbsolutePath());
            }
            disposeBrowsedImage();
            browsedFile = absolute;
            browsedImage = opened;
            String path = absolute.getAbsolutePath();
            boolean present = false;
            for (int i = 0; i < imageChoice.getItemCount(); i++) {
                if (path.equals(imageChoice.getItemAt(i))) {
                    present = true;
                    break;
                }
            }
            if (!present) imageChoice.addItem(path);
            imageChoice.setSelectedItem(path);
        }

        ImagePlus browsedImage(File file) {
            File absolute = file.getAbsoluteFile();
            if (browsedImage != null && absolute.equals(browsedFile)) return browsedImage;
            selectBrowsedFile(absolute);
            return browsedImage;
        }

        void releaseBrowsedImageIfUnselected() {
            if (browsedFile == null || imageChoice == null) return;
            Object selected = imageChoice.getSelectedItem();
            if (selected == null
                    || !browsedFile.getAbsolutePath().equals(selected.toString())) {
                disposeBrowsedImage();
            }
        }

        void disposeBrowsedImage() {
            if (browsedImage != null) {
                browsedImage.changes = false;
                browsedImage.close();
                browsedImage.flush();
            }
            browsedImage = null;
            browsedFile = null;
        }

        SegSweepMacroOptions optionsFromFields() {
            SegSweepMacroOptions options = new SegSweepMacroOptions();
            String selectedImage = (String) imageChoice.getSelectedItem();
            if (selectedImage != null && !NONE.equals(selectedImage)) {
                options.setImage(selectedImage);
            }
            options.setChannel(Integer.parseInt(channelField.getText().trim()));
            ParameterId primaryAxis = ParameterId.fromStableKey(
                    (String) axisChoice.getSelectedItem());
            if (suggestedPrimaryValues != null && primaryAxis == suggestedPrimaryAxis) {
                options.setPrimaryAxis(SegSweepMacroOptions.AxisSpec.values(
                        primaryAxis, suggestedPrimaryValues));
            } else {
                options.setPrimaryAxis(SegSweepMacroOptions.AxisSpec.range(
                        primaryAxis,
                        Double.parseDouble(fromField.getText().trim()),
                        Double.parseDouble(toField.getText().trim()),
                        Double.parseDouble(stepField.getText().trim())));
            }
            String axis2 = (String) axis2Choice.getSelectedItem();
            if (axis2 != null && !NONE.equals(axis2)) {
                options.setSecondaryAxis(SegSweepMacroOptions.AxisSpec.range(
                        ParameterId.fromStableKey(axis2),
                        Double.parseDouble(from2Field.getText().trim()),
                        Double.parseDouble(to2Field.getText().trim()),
                        Double.parseDouble(step2Field.getText().trim())));
            }
            options.setPickCriterion(SegSweepParameters.PickCriterion.valueOf(
                    ((String) pickChoice.getSelectedItem()).toUpperCase(Locale.ROOT)));
            ImagePlus chosenImage = selectedImage(this);
            if (cropChoice != null && "Sweep in ROI".equals(cropChoice.getSelectedItem())) {
                if (chosenImage == null || chosenImage.getRoi() == null) {
                    throw new IllegalArgumentException(
                            "The selected image does not have an ROI to sweep.");
                }
                options.setCrop(CropSpec.custom(chosenImage.getRoi().getBounds()));
            }
            if (autosaveField != null && autosaveField.getText().trim().length() > 0
                    && !SegSweepMacroOptions.AUTOSAVE_ALONGSIDE_INPUT.equalsIgnoreCase(
                    autosaveField.getText().trim())) {
                options.setAutosave(autosaveField.getText().trim());
            }
            if (showGrid != null) options.setShowGrid(showGrid.isSelected());
            if (showTables != null) options.setShowTables(showTables.isSelected());
            options.validate();
            return options;
        }

        void clearSuggestedPrimaryValues() {
            if (applyingSuggestedPrimaryValues) return;
            suggestedPrimaryAxis = null;
            suggestedPrimaryValues = null;
            if (explicitValuesLabel != null) {
                explicitValuesLabel.setText("");
                explicitValuesLabel.setVisible(false);
            }
        }

        void showSuggestedPrimaryValues() {
            if (explicitValuesLabel == null || suggestedPrimaryValues == null) return;
            explicitValuesLabel.setText("<html><body style='width:430px;'><b>Using explicit values:</b> "
                    + suggestedPrimaryValues.toCanonicalJson()
                    + ". From/To/Step summarize the list; editing any field switches to a regular range."
                    + "</body></html>");
            explicitValuesLabel.setVisible(true);
        }

        void refreshCostLine() {
            if (costLine == null) return;
            try {
                costLine.setText("<html><body style='width:430px;'>"
                        + costEstimateText(selectedImage(this), optionsFromFields())
                        + "</body></html>");
            } catch (RuntimeException e) {
                costLine.setText("<html><body style='width:430px;'>" + e.getMessage()
                        + "</body></html>");
            }
        }

        void refreshInputMetadata() {
            ImagePlus chosen = selectedImage(this);
            int channels = chosen == null ? 1 : Math.max(1, chosen.getNChannels());
            boolean showChannel = chosen != null && channels > 1;
            if (channelRow != null) channelRow.setVisible(showChannel);
            if (channelField != null) {
                channelField.setEnabled(showChannel);
                int selectedChannel = 1;
                try {
                    selectedChannel = Integer.parseInt(channelField.getText().trim());
                } catch (RuntimeException ignored) {
                    // Reset malformed or stale channel text when the input changes.
                    selectedChannel = 0;
                }
                if (selectedChannel < 1 || selectedChannel > channels) {
                    channelField.setText("1");
                }
            }
            if (calibrationLabel != null) {
                calibrationLabel.setText(calibrationReadout(chosen));
            }
        }
    }

    private static String calibrationReadout(ImagePlus image) {
        if (image == null) return "No image selected";
        Calibration calibration = image.getCalibration();
        if (calibration == null) return "Uncalibrated (pixel units)";
        boolean validX = Double.isFinite(calibration.pixelWidth)
                && calibration.pixelWidth > 0.0d;
        boolean validY = Double.isFinite(calibration.pixelHeight)
                && calibration.pixelHeight > 0.0d;
        boolean validZ = Double.isFinite(calibration.pixelDepth)
                && calibration.pixelDepth > 0.0d;
        String unit = calibration.getUnit();
        if (unit == null || unit.trim().isEmpty()) unit = "pixel";
        boolean needsZ = image.getNSlices() > 1;
        if (!validX || !validY || (needsZ && !validZ)) {
            return "Invalid spacing; density will be uncalibrated";
        }
        String spacing = format(calibration.pixelWidth) + " x "
                + format(calibration.pixelHeight);
        if (needsZ) spacing += " x " + format(calibration.pixelDepth);
        if ("pixel".equalsIgnoreCase(unit) || "pixels".equalsIgnoreCase(unit)) {
            return spacing + " pixel spacing (uncalibrated)";
        }
        return spacing + " " + unit + "/pixel";
    }
}
