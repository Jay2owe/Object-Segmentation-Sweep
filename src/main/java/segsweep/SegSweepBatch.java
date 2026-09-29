/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.IJ;
import ij.plugin.frame.Recorder;
import sc.fiji.oc3d.core.io.RegexGroupDiscovery;
import segsweep.ui.SegSweepDialog;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Batch processing dialog and grouping helpers.
 */
public final class SegSweepBatch {
    public static final String COMMAND_NAME = "Object Segmentation Sweep Batch";

    /** Batch-only {@code key=value} options, in README order. */
    public static final Set<String> VALUE_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("folder", "regex", "group", "output")));

    /** Batch-only flags, in README order. */
    public static final Set<String> FLAGS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("recursive")));

    /**
     * Single-image options that a batch cannot honour: the batch opens each
     * matching file itself, writes under {@code output=}, and never displays.
     * They used to be accepted and silently ignored.
     */
    static final Set<String> REFUSED_ANALYSIS_OPTIONS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "image", "autosave", "hide_display", "no_display", "show_display",
                    "hide_grid", "show_grid", "hide_tables", "show_tables")));

    static final String DEFAULT_REGEX = "(.+?)-(.+?)_(.+)\\.tif";

    private SegSweepBatch() {
    }

    /**
     * Parses the batch command's macro options:
     * {@code folder=[..] regex=[..] group=n [recursive] [output=[..]]} plus any
     * single-image analysis options except those in
     * {@link #REFUSED_ANALYSIS_OPTIONS}. Absent {@code recursive} means top
     * folder only, as for any ImageJ checkbox. {@code regex} keeps its
     * backslashes; path values have backslashes turned into forward slashes.
     */
    public static SegSweepBatchParameters parseMacroOptions(String optionsText) {
        List<String> tokens = SegSweepMacroOptionsParser.tokenize(
                optionsText == null ? "" : optionsText);
        Map<String, String> values = new LinkedHashMap<String, String>();
        Set<String> flags = new HashSet<String>();
        StringBuilder analysis = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            int eq = token.indexOf('=');
            String key = (eq >= 0 ? token.substring(0, eq) : token).trim()
                    .toLowerCase(Locale.ROOT);
            if (eq >= 0 && VALUE_KEYS.contains(key)) {
                if (values.containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate macro option: " + key);
                }
                values.put(key, SegSweepMacroOptionsParser.decodeValue(key,
                        token.substring(eq + 1).trim()));
            } else if (eq < 0 && FLAGS.contains(key)) {
                flags.add(key);
            } else {
                if (analysis.length() > 0) analysis.append(' ');
                analysis.append(token);
            }
        }
        String folder = values.get("folder");
        if (folder == null || folder.trim().isEmpty()) {
            throw new IllegalArgumentException("folder is required for " + COMMAND_NAME + ".");
        }
        String regex = values.containsKey("regex") ? values.get("regex") : DEFAULT_REGEX;
        int group = 1;
        if (values.containsKey("group")) {
            try {
                group = Integer.parseInt(values.get("group").trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("group must be an integer.");
            }
        }
        SegSweepBatchParameters.Builder builder = SegSweepBatchParameters.builder(
                new File(folder.trim()), regex, group)
                .recursive(flags.contains("recursive"))
                .hideDisplay(true)
                .analysisOptions(parseAnalysisOptions(analysis.toString()));
        String output = values.get("output");
        if (output != null && !output.trim().isEmpty()) {
            builder.saveDir(new File(output.trim()));
        }
        return builder.build();
    }

    /**
     * Parses per-image analysis options for a batch, refusing the options a
     * batch cannot honour with a message that says why.
     */
    public static SegSweepMacroOptions parseAnalysisOptions(String optionsText) {
        Set<String> names = SegSweepMacroOptionsParser.namesIn(optionsText);
        List<String> refused = new ArrayList<String>();
        for (String name : names) {
            if (REFUSED_ANALYSIS_OPTIONS.contains(name)) refused.add(name);
        }
        if (!refused.isEmpty()) {
            throw new IllegalArgumentException("Batch analysis options cannot include "
                    + refused + ": a batch opens each matching file in the folder, "
                    + "saves under the output folder, and never displays windows.");
        }
        return SegSweepMacroOptionsParser.parse(optionsText == null ? "" : optionsText);
    }

    /**
     * The macro call that reproduces {@code parameters}, or null when a value
     * cannot be written as an ImageJ macro option ([, ] or " in it).
     */
    static String toMacroOptions(SegSweepBatchParameters parameters) {
        List<String> tokens = new ArrayList<String>();
        String folder = bracket(slashes(parameters.inputFolder().getPath()));
        String regex = bracket(parameters.filenameRegex());
        if (folder == null || regex == null) return null;
        tokens.add("folder=" + folder);
        tokens.add("regex=" + regex);
        tokens.add("group=" + parameters.varyingGroup());
        if (parameters.recursive()) tokens.add("recursive");
        if (parameters.saveDir() != null) {
            String output = bracket(slashes(parameters.saveDir().getPath()));
            if (output == null) return null;
            tokens.add("output=" + output);
        }
        tokens.add(parameters.analysisOptions().toAnalysisMacroOptions());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    /** The line to append to the Recorder, with backslashes escaped for a macro string. */
    static String recordedCall(SegSweepBatchParameters parameters) {
        String options = toMacroOptions(parameters);
        if (options == null) return null;
        return "run(\"" + COMMAND_NAME + "\", \""
                + options.replace("\\", "\\\\") + "\");\n";
    }

    static String completionMessage(SegSweepBatchResult result) {
        return "Object Segmentation Sweep batch complete: "
                + result.processedImages() + " processed, "
                + result.failedImages() + " failed.";
    }

    private static String slashes(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private static String bracket(String value) {
        if (value == null || value.indexOf('[') >= 0 || value.indexOf(']') >= 0
                || value.indexOf('"') >= 0) {
            return null;
        }
        return "[" + value + "]";
    }

    public static void showBatchDialog() {
        final JDialog dialog = new JDialog((Frame) null,
                "Object Segmentation Sweep - Batch", false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 16, 10, 16));
        dialog.add(content, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        final JTextField folderField = addField(content, c, 0, "Folder", "");
        final JTextField regexField = addField(content, c, 1, "Filename regex",
                DEFAULT_REGEX);
        final JTextField groupField = addField(content, c, 2, "Capture group", "1");
        final JCheckBox recursiveBox = addCheck(content, c, 3, "Include subfolders", true);
        final JTextField analysisField = addField(content, c, 4, "Analysis options",
                SegSweepDialog.defaults().toAnalysisMacroOptions());
        analysisField.setToolTipText("Macro options: channel, sweep range(s), crop and pick criterion.");
        final JTextField autosaveField = addField(content, c, 5, "Save to", "");

        final JTextArea previewArea = new JTextArea(10, 48);
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewScroll.setPreferredSize(new Dimension(520, 180));
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 2;
        content.add(previewScroll, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton preview = new JButton("Preview Groups");
        JButton run = new JButton("Run");
        JButton close = new JButton("Close");
        buttons.add(preview);
        buttons.add(run);
        buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);

        preview.addActionListener(e -> {
            try {
                Pattern pattern = Pattern.compile(regexField.getText().trim());
                Map<String, Map<String, List<File>>> groups = findGroupsRecursive(
                        new File(folderField.getText().trim()), pattern,
                        Integer.parseInt(groupField.getText().trim()),
                        recursiveBox.isSelected());
                previewArea.setText(previewNestedGroups(groups));
            } catch (PatternSyntaxException ex) {
                previewArea.setText("Invalid regex: " + ex.getMessage());
            } catch (RuntimeException ex) {
                previewArea.setText(ex.getMessage());
            }
        });
        run.addActionListener(e -> {
            try {
                SegSweepBatchParameters.Builder builder = SegSweepBatchParameters.builder(
                        new File(folderField.getText().trim()),
                        regexField.getText().trim(),
                        Integer.parseInt(groupField.getText().trim()))
                        .recursive(recursiveBox.isSelected())
                        .hideDisplay(true)
                        .analysisOptions(parseAnalysisOptions(
                                analysisField.getText().trim()));
                if (autosaveField.getText().trim().length() > 0) {
                    builder.saveDir(new File(autosaveField.getText().trim()));
                }
                final SegSweepBatchParameters parameters = builder.build();
                recordBatchCall(parameters);
                new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            SegSweepBatchResult result = SegSweepBatchRunner.run(parameters,
                                    SegSweep_.batchStatus(), SegSweep_.escapeCancel());
                            IJ.log(completionMessage(result));
                            IJ.showStatus(COMMAND_NAME + ": done.");
                        } catch (CancellationException ex) {
                            IJ.log(COMMAND_NAME + ": " + ex.getMessage());
                            IJ.showStatus(COMMAND_NAME + ": cancelled.");
                        } catch (Exception ex) {
                            IJ.error(COMMAND_NAME, ex.getMessage());
                        } finally {
                            IJ.showProgress(1.0d);
                        }
                    }
                }, "SegSweep-Batch").start();
                dialog.dispose();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(),
                        "Object Segmentation Sweep Batch", JOptionPane.ERROR_MESSAGE);
            }
        });
        close.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private static void recordBatchCall(SegSweepBatchParameters parameters) {
        if (!Recorder.record) return;
        String line = recordedCall(parameters);
        if (line == null) {
            IJ.log(COMMAND_NAME + ": this run was not recorded: the folder, output or "
                    + "regex contains [, ] or \", which ImageJ macro options cannot carry.");
            return;
        }
        Recorder.recordString(line);
    }

    static Map<String, List<File>> findGroups(File folder, Pattern pattern,
                                              int varyingGroup) {
        return RegexGroupDiscovery.findGroups(
                folder, pattern, varyingGroup,
                RegexGroupDiscovery.GroupOrder.FILENAME);
    }

    static Map<String, Map<String, List<File>>> findGroupsRecursive(
            File rootFolder, Pattern pattern, int varyingGroup, boolean recursive) {
        return findGroupsRecursive(rootFolder, pattern, varyingGroup, recursive,
                Collections.<File>emptySet());
    }

    static Map<String, Map<String, List<File>>> findGroupsRecursive(
            File rootFolder, Pattern pattern, int varyingGroup, boolean recursive,
            Set<File> excludedDirectories) {
        Map<String, Map<String, List<File>>> discovered =
                RegexGroupDiscovery.findGroupsRecursive(
                        rootFolder, pattern, varyingGroup, recursive,
                        RegexGroupDiscovery.GroupOrder.FILENAME,
                        excludedDirectories);
        return withoutGeneratedOutputFolders(discovered);
    }

    private static Map<String, Map<String, List<File>>> withoutGeneratedOutputFolders(
            Map<String, Map<String, List<File>>> discovered) {
        Map<String, Map<String, List<File>>> filtered =
                new LinkedHashMap<String, Map<String, List<File>>>();
        for (Map.Entry<String, Map<String, List<File>>> entry : discovered.entrySet()) {
            if (!containsGeneratedOutputFolder(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static boolean containsGeneratedOutputFolder(String relativePath) {
        if (relativePath == null || relativePath.length() == 0) return false;
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (isGeneratedOutputName(parts[i])) return true;
        }
        return false;
    }

    static boolean isGeneratedOutputDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) return false;
        return isGeneratedOutputName(directory.getName());
    }

    private static boolean isGeneratedOutputName(String name) {
        return name != null && name.matches(
                "(?i)Object Segmentation Sweep(?: [0-9]+)?");
    }

    static String previewNestedGroups(Map<String, Map<String, List<File>>> nestedGroups) {
        if (nestedGroups == null || nestedGroups.isEmpty()) {
            return "No matching files found.";
        }
        int totalFolders = nestedGroups.size();
        int totalGroups = 0;
        int totalFiles = 0;
        for (Map<String, List<File>> fg : nestedGroups.values()) {
            totalGroups += fg.size();
            for (List<File> files : fg.values()) {
                totalFiles += files.size();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(totalFolders).append(" folder(s), ")
                .append(totalGroups).append(" group(s), ")
                .append(totalFiles).append(" files\n\n");
        for (Map.Entry<String, Map<String, List<File>>> folderEntry : nestedGroups.entrySet()) {
            String folder = folderEntry.getKey().length() == 0
                    ? "(root)" : folderEntry.getKey() + "/";
            sb.append(folder).append("\n");
            for (Map.Entry<String, List<File>> groupEntry : folderEntry.getValue().entrySet()) {
                sb.append("  ").append(groupEntry.getKey())
                        .append("  (").append(groupEntry.getValue().size()).append(")\n");
                for (File file : groupEntry.getValue()) {
                    sb.append("    ").append(file.getName()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static JTextField addField(JPanel panel, GridBagConstraints c,
                                       int row, String label, String value) {
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0.0;
        panel.add(new JLabel(label), c);
        JTextField field = new JTextField(value, 28);
        c.gridx = 1;
        c.weightx = 1.0;
        panel.add(field, c);
        return field;
    }

    private static JCheckBox addCheck(JPanel panel, GridBagConstraints c,
                                      int row, String label, boolean selected) {
        JCheckBox box = new JCheckBox(label, selected);
        c.gridx = 1;
        c.gridy = row;
        c.gridwidth = 1;
        panel.add(box, c);
        return box;
    }
}
