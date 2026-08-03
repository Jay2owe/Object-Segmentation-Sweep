/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import java.io.File;

/**
 * Immutable input bundle for Object Segmentation Sweep folder batch processing.
 */
public final class SegSweepBatchParameters {
    private final File inputFolder;
    private final String filenameRegex;
    private final int varyingGroup;
    private final boolean recursive;
    private final SegSweepMacroOptions analysisOptions;
    private final boolean autoSave;
    private final File saveDir;
    private final boolean hideDisplay;

    private SegSweepBatchParameters(Builder builder) {
        this.inputFolder = builder.inputFolder;
        this.filenameRegex = builder.filenameRegex;
        this.varyingGroup = builder.varyingGroup;
        this.recursive = builder.recursive;
        this.analysisOptions = builder.analysisOptions;
        this.autoSave = builder.autoSave;
        this.saveDir = builder.saveDir;
        this.hideDisplay = builder.hideDisplay;
    }

    public static Builder builder(File inputFolder, String filenameRegex, int varyingGroup) {
        return new Builder(inputFolder, filenameRegex, varyingGroup);
    }

    public File inputFolder() {
        return inputFolder;
    }

    public File getInputFolder() {
        return inputFolder;
    }

    public String filenameRegex() {
        return filenameRegex;
    }

    public String getFilenameRegex() {
        return filenameRegex;
    }

    public int varyingGroup() {
        return varyingGroup;
    }

    public int getVaryingGroup() {
        return varyingGroup;
    }

    public boolean recursive() {
        return recursive;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public SegSweepMacroOptions analysisOptions() {
        return analysisOptions;
    }

    public SegSweepMacroOptions getAnalysisOptions() {
        return analysisOptions;
    }

    public boolean autoSave() {
        return autoSave;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public File saveDir() {
        return saveDir;
    }

    public File getSaveDir() {
        return saveDir;
    }

    public boolean hideDisplay() {
        return hideDisplay;
    }

    public boolean isHideDisplay() {
        return hideDisplay;
    }

    public static final class Builder {
        private final File inputFolder;
        private final String filenameRegex;
        private final int varyingGroup;
        private boolean recursive = true;
        private SegSweepMacroOptions analysisOptions = SegSweepMacroOptions.defaults();
        private boolean autoSave = true;
        private File saveDir;
        private boolean hideDisplay = true;

        private Builder(File inputFolder, String filenameRegex, int varyingGroup) {
            this.inputFolder = inputFolder;
            this.filenameRegex = filenameRegex;
            this.varyingGroup = varyingGroup;
        }

        public Builder recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        public Builder analysisOptions(SegSweepMacroOptions analysisOptions) {
            this.analysisOptions = analysisOptions == null
                    ? SegSweepMacroOptions.defaults() : analysisOptions;
            return this;
        }

        public Builder autoSave(boolean autoSave) {
            this.autoSave = autoSave;
            return this;
        }

        public Builder saveDir(File saveDir) {
            this.saveDir = saveDir;
            return this;
        }

        public Builder hideDisplay(boolean hideDisplay) {
            this.hideDisplay = hideDisplay;
            return this;
        }

        public SegSweepBatchParameters build() {
            return new SegSweepBatchParameters(this);
        }
    }
}
