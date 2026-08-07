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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Batch processing dialog and grouping helpers.
 */
public final class SegSweepBatch {
    private SegSweepBatch() {
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
                "(.+?)-(.+?)_(.+)\\.tif");
        final JTextField groupField = addField(content, c, 2, "Capture group", "1");
        final JCheckBox recursiveBox = addCheck(content, c, 3, "Include subfolders", true);
        final JTextField analysisField = addField(content, c, 4, "Analysis options",
                SegSweepDialog.defaults().toMacroOptions());
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
                        .analysisOptions(SegSweepMacroOptionsParser.parse(
                                analysisField.getText().trim()));
                if (autosaveField.getText().trim().length() > 0) {
                    builder.saveDir(new File(autosaveField.getText().trim()));
                }
                new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            SegSweepBatchResult result = SegSweepBatchRunner.run(builder.build());
                            IJ.log("Object Segmentation Sweep batch complete: "
                                    + result.processedImages() + " processed, "
                                    + result.failedImages() + " failed.");
                        } catch (Exception ex) {
                            IJ.error("Object Segmentation Sweep Batch", ex.getMessage());
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
