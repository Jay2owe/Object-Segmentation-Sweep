/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentTreeResult {
    public enum Status { OK, EMPTY, TOO_MANY_LABELS }

    private final Status status;
    private final String reason;
    private final List<ComponentNode> nodes;
    private final LazyLabelMap labelMap;

    ComponentTreeResult(Status status, String reason, List<ComponentNode> nodes, LazyLabelMap labelMap) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (labelMap == null) {
            throw new IllegalArgumentException("labelMap must not be null");
        }
        this.status = status;
        this.reason = reason == null ? "" : reason;
        this.nodes = Collections.unmodifiableList(new ArrayList<ComponentNode>(
                nodes == null ? Collections.<ComponentNode>emptyList() : nodes));
        this.labelMap = labelMap;
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public int objectCount() {
        return nodes.size();
    }

    public List<ComponentNode> selectedNodes() {
        return nodes;
    }

    public LazyLabelMap labelMap() {
        return labelMap;
    }
}
