# Stage 10 — The grid reviewer

Lift the grid window and cell panel: one cell per combination, synced scrolling, hold-to-peek,
shift-click compare, and pick-selected.

## Why this stage exists

This is what the user actually looks at, and it is the reason the plugin is a plugin rather than a
macro. Twenty-five label maps laid out side by side, all scrolling together through a z-stack, with
the two suggested picks badged in place — that is a thing you can make a decision from in ten
seconds. The same twenty-five results as a stack of windows is not.

**This is the largest single lift in the plan and the one with the most unknown risk.**
`VariationCellPanel` is 2,737 lines whose cleanliness was established by import scan, not by reading
it. Budget accordingly.

## Prerequisites

- `06_executor-and-result`, `07_pickers`, `09_render-stack` complete.

## Read first

- `docs/segsweep-build/00_overview.md` — and its "honest unknown" note
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "Reviewing the grid" behaviour list
- Parent files under `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\`:
  - `VariationGridWindow.java` (716) — **read it all.** Constructors 85-91, `setSliceMax` 167,
    `setCompletedCount` 176, the `attach*ActionListener` block 188-242, `setActionStatus` 243,
    `setPickSelectedEnabled` 255, test accessors 267+
  - `VariationCellPanel.java` (2,737) — **read it all before writing anything.** This is the risk
  - `SyncedSliceController.java` (72), `ValueChipPanel.java` (346)
  - `VariationComparisonSelection.java` (83)
- Parent tests, all worth porting: `VariationGridWindowLayoutTest`, `…ObjectToolbarTest`,
  `…ProgressTest`, `…WheelScrollTest`, `VariationCellPanelBaselineTest`, `…FallbackCleanupTest`,
  `…HoldToPeekTest`, `…ObjectOverlayTest`, `…OverlayPaintTest`, `…ShiftClickTest`,
  `…TerminalCleanupTest`, `SyncedSliceControllerTest`

## Scope

- Lift `VariationGridWindow`, `VariationCellPanel`, `SyncedSliceController`, `ValueChipPanel`,
  `VariationComparisonSelection` into `segsweep.ui.grid`.
- Repoint them at stage 09's `segsweep.ui.render` classes and `SegSweepTheme`.
- Strip the parent's FLASH-only toolbar actions: the deconvolution and spectral overlay toggles, and
  anything referencing `ConfigQcContext`.
- **Badge the picks** — mark the knee cell and the stability cell distinctly, and when
  `PickResult.criteriaAgree()` is false show both badges without implying one wins.
- Show progress during **scoring** as well as dispatch — stage 07 removed the 5-second budget (D2),
  so IoU scoring on a large sweep can take a while and must not look like a hang.
- Wire the "Save variations cache" action to `VariationCache.snapshotResultsToDisk`.
- Port the twelve parent tests.

## Out of scope

- The setup dialog that launches a sweep — stage 12.
- Batch UI — stage 13.
- `PreviewPairPanel`-based two-up comparison — shift-click compare uses the grid's own cells; the
  parent's 2,629-line pair panel is not lifted (stage 09 out-of-scope note).
- Deciding *what* the picks mean. Stage 07 owns the criteria; this stage only displays them.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/ui/grid/VariationGridWindow.java` | NEW | Lift, toolbar trimmed |
| `src/main/java/segsweep/ui/grid/VariationCellPanel.java` | NEW | Lift — the large one |
| `src/main/java/segsweep/ui/grid/SyncedSliceController.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/grid/ValueChipPanel.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/grid/VariationComparisonSelection.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/grid/PickBadge.java` | **NEW** | Knee / stability badging |
| `src/test/java/segsweep/ui/grid/*Test.java` | NEW | Twelve ported |

## Implementation sketch

`VariationGridWindow`'s public surface is a listener-attachment API — preserve it, because it is what
makes the window testable without a display:

```java
public VariationGridWindow(Window owner, ...);

public void setSliceMax(int sliceMax);
public void setCompletedCount(int completed, int total, int failed);
public void setScoringProgress(int scored, int total);      // NEW — D2 has no budget now
public void attachObjectOverlayActionListener(ActionListener l);
public void attachLutToggleActionListener(ActionListener l);
public void attachBrightnessActionListener(ActionListener l);
public void attachPickSelectedActionListener(ActionListener l);
public void attachSaveCacheActionListener(ActionListener l);
public void setPickSelectedEnabled(boolean enabled);
public void setActionStatus(String text);

// Test accessors — the parent has these and they are why the grid is testable. Keep them.
JToolBar toolBarForTest();
JCheckBox otsuOverlayCheckBoxForTest();
```

Badging:

```java
public final class PickBadge {
    public enum Kind { KNEE, STABILITY, BOTH }
    // BOTH only when PickResult.criteriaAgree(); otherwise two cells get one badge each.
}
```

When the criteria disagree, **two cells are badged and neither is styled as the winner**. The status
line says so plainly — something like `Knee: threshold 32. Stability: threshold 28. Criteria
disagree.` Do not add a tiebreak.

Cells arrive out of index order — `SweepDispatchOrder` renders the middle of the range first — so
place cells by combination identity, never by arrival index.

**Cache `get` cost.** Stage 05 made `VariationCache.get` return a defensive duplicate (D9). If that
proves too slow with many cells, switch to a read-only wrapper — **do not revert to sharing the
mutable instance**, which is the defect. Measure before changing anything.

## Exit gate

1. `mvn test` passes with all twelve ported tests green.
2. Layout: a 3×4 sweep produces 12 cells in the documented order; a 1×7 sweep produces one row.
3. Synced scrolling: scrolling one cell's z-position moves every cell, asserted through
   `SyncedSliceController` without a display.
4. Hold-to-peek, shift-click compare, LUT toggle and brightness each covered by their ported test.
5. Badging: a `PickResult` where both criteria agree badges one cell `BOTH`; where they disagree,
   two cells are badged and the status text names both values. **No cell is styled as "best".**
6. Progress: `setScoringProgress` is called during IoU scoring, and a sweep large enough to take
   several seconds to score never leaves the window without a progress indication.
7. Pick-selected returns the chosen `ParameterCombo` to its listener and is disabled until at least
   one result has arrived.
8. Cells arriving out of dispatch order land in the correct grid positions — test by publishing
   results in reverse.
9. No open `ImagePlus` leaks after the window closes — the ported cleanup tests
   (`…FallbackCleanupTest`, `…TerminalCleanupTest`) cover this; do not skip them.
10. `grep -rni "flash\|ConfigQcContext\|deconv\|spectral" src/main/java/segsweep/ui/grid/` returns
    nothing.
11. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`VariationCellPanel` at 2,737 lines is the plan's biggest unknown.** Its imports are clean, but a
  class that size usually carries assumptions the imports do not reveal — about its parent
  container, about FLASH's event ordering, about image ownership. Read it fully before editing. If
  it turns out to be entangled, the fallback is to lift only its painting and interaction core and
  rebuild the container wiring, which is a bigger job than this stage assumes — **flag it rather
  than absorbing it silently**.
- **Cleanup tests exist for a reason.** Three of the twelve ported tests are about not leaking
  images. A grid holding fifty full stacks that never releases them will exhaust memory on the
  second sweep of a session.
- **Headless CI.** Same constraint as stage 09: skip cleanly with a stated reason, never pass while
  testing nothing.
- **Scoring progress needs the executor's cooperation.** Stage 06's `SweepProgress` covers dispatch;
  scoring happens after. If stage 07's API does not report incremental progress, this stage needs a
  small callback added there — do that rather than leaving the UI silent.
