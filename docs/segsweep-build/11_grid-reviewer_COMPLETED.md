# Stage 11 - The grid reviewer

Lift the grid window and cell panel: one cell per displayed combination, synced scrolling,
hold-to-peek, shift-click compare, lazy label realization, and pick-selected.

## Why this stage exists

This is what the user actually looks at. Twenty-five label maps laid out side by side, all scrolling
together through a z-stack, with the two suggested picks badged in place, is a decision surface. The
same results as a stack of windows is not.

`VariationCellPanel` is the largest lift and the main unknown. The accepted component-tree
architecture changes where its images come from: each cell receives a lazy label provider and asks
for a label map only when it draws or when output needs the picked cell. It must not assume a
pre-existing retained stack for every combination.

## Prerequisites

- `07_executor-and-result`, `08_pickers`, `09_classical-strategy`, `10_render-stack` complete.

## Read first

- `docs/segsweep-build/00_overview.md` - honest unknown and lazy architecture
- `docs/segsweep-build/03_component-tree.md` - lazy label-map provider
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - "Reviewing the grid"
  behaviour list
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - grid stays as planned,
  but labels are materialised lazily
- Parent files under `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\`:
  - `VariationGridWindow.java` (716) - read all
  - `VariationCellPanel.java` (2,737) - read all before writing anything
  - `SyncedSliceController.java` (72), `ValueChipPanel.java` (346)
  - `VariationComparisonSelection.java` (83)
- Parent tests: `VariationGridWindowLayoutTest`, `...ObjectToolbarTest`, `...ProgressTest`,
  `...WheelScrollTest`, `VariationCellPanelBaselineTest`, `...FallbackCleanupTest`,
  `...HoldToPeekTest`, `...ObjectOverlayTest`, `...OverlayPaintTest`, `...ShiftClickTest`,
  `...TerminalCleanupTest`, `SyncedSliceControllerTest`

## Scope

- Lift `VariationGridWindow`, `VariationCellPanel`, `SyncedSliceController`, `ValueChipPanel`,
  `VariationComparisonSelection` into `segsweep.ui.grid`.
- Repoint them at stage 10's `segsweep.ui.render` classes and `SegSweepTheme`.
- Strip FLASH-only toolbar actions: deconvolution, spectral overlays and `ConfigQcContext`.
- Replace any assumption of eager label stacks with `LazyLabelMap`/provider access from
  `VariationResult`.
- Badge the picks: knee and stability distinctly; if `PickResult.criteriaAgree()` is false, show
  both badges without implying one wins.
- Show progress for tree build, query, scoring and lazy materialisation.
- Port the twelve parent tests and add lazy-realization coverage.

## Out of scope

- The setup dialog that launches a sweep - stage 13.
- Batch UI - stage 14.
- `PreviewPairPanel`-based two-up comparison - shift-click compare uses the grid's own cells.
- Any save action for a retained variations cache. The old cache architecture is removed.
- Deciding what the picks mean. Stage 08 owns the criteria; this stage only displays them.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/ui/grid/VariationGridWindow.java` | NEW | Lift, toolbar trimmed |
| `src/main/java/segsweep/ui/grid/VariationCellPanel.java` | NEW | Lift, lazy labels |
| `src/main/java/segsweep/ui/grid/SyncedSliceController.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/grid/ValueChipPanel.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/grid/VariationComparisonSelection.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/grid/PickBadge.java` | NEW | Knee / stability badging |
| `src/test/java/segsweep/ui/grid/*Test.java` | NEW | Twelve ported + lazy tests |

## Implementation sketch

Preserve the listener-attachment API that makes the parent testable:

```java
public VariationGridWindow(Window owner, ...);

public void setSliceMax(int sliceMax);
public void setCompletedCount(int completed, int total, int failed);
public void setScoringProgress(int scored, int total);
public void setMaterialisationProgress(int realised, int requested);
public void attachObjectOverlayActionListener(ActionListener l);
public void attachLutToggleActionListener(ActionListener l);
public void attachBrightnessActionListener(ActionListener l);
public void attachPickSelectedActionListener(ActionListener l);
public void setPickSelectedEnabled(boolean enabled);
public void setActionStatus(String text);
```

Badging:

```java
public final class PickBadge {
    public enum Kind { KNEE, STABILITY, BOTH }
}
```

When criteria disagree, two cells are badged and neither is styled as the winner. The status line
says plainly: `Knee: threshold 32. Stability: threshold 28. Criteria disagree.`

Cells arrive out of index order, so place cells by combination identity, not arrival order.

Lazy realization:

```java
ImagePlus labels = result.labelMap().get();  // called by paint/output paths only
```

The cell may keep the materialised image while visible, but cleanup must release it. Requesting a
second cell's labels must not mutate or reuse the first cell's `ImagePlus`.

## Exit gate

1. `mvn test` passes with all ported tests green.
2. Layout: a 3x4 display window produces 12 cells in the documented order; a 1x7 window produces one
   row.
3. Synced scrolling moves every cell through `SyncedSliceController`.
4. Hold-to-peek, shift-click compare, LUT toggle and brightness each have tests.
5. Badging: agreement badges one cell `BOTH`; disagreement badges two cells and status text names
   both values. No cell is styled as "best".
6. Progress covers tree build/query/scoring and label materialisation.
7. Pick-selected returns the chosen `ParameterCombo` and is disabled until at least one result has
   arrived.
8. Cells arriving out of dispatch order land in correct grid positions.
9. Lazy realization: constructing the grid does not materialise every label map; drawing one cell
   materialises one label map; closing releases it.
10. No open `ImagePlus` leaks after the window closes.
11. `grep -rni "flash\\|ConfigQcContext\\|deconv\\|spectral\\|VariationCache" src/main/java/segsweep/ui/grid/`
    returns nothing.
12. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`VariationCellPanel` at 2,737 lines is the plan's biggest unknown.** Read it fully before
  editing.
- **Cleanup tests exist for a reason.** A grid that materialises labels lazily but never releases
  them still exhausts memory.
- **Headless CI.** Skip cleanly with a stated reason, never pass while testing nothing.
- **Progress needs cooperation from lower layers.** Stage 07's `SweepProgress` should carry phases
  rich enough for this window.
