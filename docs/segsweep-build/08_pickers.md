# Stage 08 - The pickers

Lift knee detection and IoU stability, preserving the active correctness fixes and adapting the
inputs to component-tree query results.

## Why this stage exists

This is the differentiator. The plugin does not just montage settings; it reports two objective
criteria - the knee of the object-count curve and mean-neighbour-IoU stability - without
hand-drawn ground truth. The component tree removes several old cost and range defects, but the
remaining picker semantics still need to be explicit and tested.

## Prerequisites

- `05_parameter-model`, `06_crop-and-token`, `07_executor-and-result` complete.

May run in parallel with stage 09 after stage 07.

## Read first

- `docs/segsweep-build/00_overview.md` - defect-ledger consequences
- `docs/segsweep-build/03_component-tree.md` - tree query and lazy label semantics
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - defects **D1** and **D4**,
  plus the historical D2/D3/D5 context
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - D2/D3/D5 are
  moot/eliminated by whole-space computation
- `../../../ImageJ Plugins/Object Segmentation Sweep/00_CASE.md` - the risk that "objective" is a
  strong claim
- Parent files under
  `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\analysis\`:
  - `KneeDetector.java` (195)
  - `IouStability.java` (286)
  - `LabelIou.java` (99), `HistogramShapeStability.java` (279)
- Parent test: `KneeDetectorTest` if present; there is no `IouStabilityTest`

## Scope

- Lift `KneeDetector`, `IouStability`, `LabelIou`, `HistogramShapeStability` into
  `segsweep.sweep.analysis`.
- **Fix D1** - boundary bias in `IouStability`: only eligible interior combinations can win, and
  eligibility is reported.
- **Fix D4** - typed knee outcomes instead of bare `OptionalInt.empty()`.
- Preserve the D5 consequence without implementing the old range fix: knee values are reported in
  parameter units, but the root cause is eliminated because the tree computes the full threshold
  axis once and public ranges are display windows.
- Represent D2/D3 as typed outcomes where needed, but do not keep the old 5-second budget or
  `>2 axes` silent failure as work items.
- Write `PickResult` carrying both criteria, their agreement and provenance.
- Adapt stability scoring to operate on component-tree query/node information when available.
  Materialise labels only in tests that specifically exercise `LabelIou`; production scoring must
  not require retained labels for every combination.
- Keep `HistogramShapeStability` compiled and tested but unexposed.

## Out of scope

- Running a sweep - stages 07 and 09.
- Badging picks in the grid - stage 11.
- A randomisation null model - v0.2.0.
- Clicks as a third criterion - v0.2.0 at earliest.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/analysis/KneeDetector.java` | NEW | Lift + D4 typed outcomes |
| `src/main/java/segsweep/sweep/analysis/KneeOutcome.java` | NEW | D4 and reported parameter units |
| `src/main/java/segsweep/sweep/analysis/IouStability.java` | NEW | Lift + D1, tree-query inputs |
| `src/main/java/segsweep/sweep/analysis/StabilityOutcome.java` | NEW | Typed refusals/eligibility |
| `src/main/java/segsweep/sweep/analysis/LabelIou.java` | NEW | Lift for tests and fallback comparison |
| `src/main/java/segsweep/sweep/analysis/HistogramShapeStability.java` | NEW | Lift, unexposed |
| `src/main/java/segsweep/sweep/analysis/PickResult.java` | NEW | Both criteria + agreement |
| `src/test/java/segsweep/sweep/analysis/*Test.java` | NEW | Acceptance tests + unit tests |

## Implementation sketch

D4 typed knee outcomes:

```java
public final class KneeOutcome {
    public enum Kind { KNEE_AT, ALL_PLATEAU, TOO_FEW_POINTS, NO_BEND, DEGENERATE_RANGE }

    public Kind kind();
    public int index();              // valid only for KNEE_AT
    public double parameterValue();
    public double rangeMin();        // displayed range, for reporting
    public double rangeMax();
    public double step();
    public String explanation();
}
```

The knee is computed over the full available threshold axis for classical. The displayed range is
recorded with the outcome because it is what the user reviewed and what appears in the methods file.
Do not reintroduce a range-normalised compute budget.

D1 boundary eligibility:

```java
public final class StabilityOutcome {
    public enum Kind { STABLE_AT, NO_ELIGIBLE_COMBINATIONS, TOO_MANY_AXES, ABORTED }
    public Kind kind();
    public int index();
    public double meanNeighbourIou();
    public int eligibleCount();
    public String explanation();
}
```

Only combinations with a full neighbour complement across varying axes are eligible. A one-axis
sweep of three values has one eligible point; two values have none. `Stability_Eligible` in the
sweep table comes from this topology, not from display styling.

When stage 09 supplies component-tree results, compute neighbour agreement from tree node/object
membership directly. `LabelIou` remains valuable for unit tests and for any future non-tree engine
that can only provide labels, but v0.1 classical must not materialise all displayed labels just to
score stability.

`PickResult`:

```java
public final class PickResult {
    public KneeOutcome knee();
    public StabilityOutcome stability();
    public boolean criteriaAgree();
    public SweepProvenance provenance();
}
```

Neither criterion overrides the other. When they disagree, both are reported and `criteriaAgree()`
is false.

## Exit gate

1. `mvn test` passes.
2. `KneeOutcomeTypedTest` produces `ALL_PLATEAU`, `TOO_FEW_POINTS`, `NO_BEND` and
   `DEGENERATE_RANGE`, all distinguishable from `KNEE_AT`.
3. `KneeWholeAxisTest` proves changing the public display window does not change the full-axis
   count curve used for the classical knee.
4. `IouStabilityBoundaryTest` - a 1-D sweep of 7 values and the same sweep extended to 9 displayed
   values return the same picked parameter value when the underlying full-axis data are unchanged.
5. `IouStabilityEligibilityTest` reports no eligible combinations for a two-value sweep and one
   eligible combination for a three-value sweep.
6. `IouStabilityThreeAxisTest` returns an explicit typed outcome if the v0.1 UI cap is exceeded,
   not a silent empty.
7. `LabelIouTest` - identical label maps give 1.0; disjoint give 0.0; different dimensions throw
   `IllegalArgumentException`.
8. `PickResultTest` - disagreement yields `criteriaAgree() == false` with both outcomes populated.
9. `grep -rn "IJ.log" src/main/java/segsweep/sweep/analysis/` returns nothing.
10. Grep confirms classical picker tests do not depend on a materialised label stack per displayed
    combination.
11. Every class in this package has a Javadoc paragraph stating assumptions and known limitations,
    including the absence of a null model until v0.2.0.

## Known risks

- **`IouStability` has no parent test.** Write the tests first here.
- **Interior-only eligibility can empty the candidate set.** Handle it explicitly and surface it in
  tables and UI.
- **Tree-based IoU semantics need care.** If node-membership IoU diverges from label-map IoU, add a
  test documenting the exact equivalence or the exact limitation.
- **Do not oversell in user-facing strings.** No "best", "optimal" or "correct"; say what was
  computed and over what.
