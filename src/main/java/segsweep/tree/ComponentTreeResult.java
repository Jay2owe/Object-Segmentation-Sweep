/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import java.util.List;

public final class ComponentTreeResult {
    public enum Status { OK, EMPTY, TOO_MANY_LABELS }

    private final Status status;
    private final String reason;
    private final ComponentSelection selection;
    private final LazyLabelMap labelMap;
    private final int reportedObjectCount;

    ComponentTreeResult(Status status, String reason,
                        ComponentSelection selection, LazyLabelMap labelMap) {
        this(status, reason, selection, labelMap,
                selection == null ? 0 : selection.size());
    }

    ComponentTreeResult(Status status, String reason, ComponentSelection selection,
                        LazyLabelMap labelMap, int reportedObjectCount) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (labelMap == null) {
            throw new IllegalArgumentException("labelMap must not be null");
        }
        if (selection == null) {
            throw new IllegalArgumentException("selection must not be null");
        }
        this.status = status;
        this.reason = reason == null ? "" : reason;
        this.selection = selection;
        this.labelMap = labelMap;
        this.reportedObjectCount = Math.max(0, reportedObjectCount);
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public int objectCount() {
        return reportedObjectCount;
    }

    public List<ComponentNode> selectedNodes() {
        return selection.selectedNodes();
    }

    public ComponentSelection selection() {
        return selection;
    }

    public LazyLabelMap labelMap() {
        return labelMap;
    }
}
