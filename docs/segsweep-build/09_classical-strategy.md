# Stage 09 - Classical strategy and range suggestion

Wire the component tree, parameter model and result plumbing into a running classical sweep, and
lift the histogram-driven range suggester.

## Why this stage exists

Stages 02-07 built the pieces. This is where a sweep first runs end to end under the accepted
engine shape: crop once, guard once, build one component tree, query displayed combinations, and
materialise label maps only when drawing or output asks for them. It also keeps the helpful
histogram-driven "suggest range" behaviour, now as a display-window convenience rather than a
compute-budget necessity.

## Prerequisites

- `04_component-tree-equivalence`, `05_parameter-model`, `06_crop-and-token`,
  `07_executor-and-result` complete.

May run in parallel with stage 08 after stage 07.

## Read first

- `docs/segsweep-build/00_overview.md`
- `docs/segsweep-build/03_component-tree.md`
- `docs/segsweep-build/04_component-tree-equivalence.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - active D6/D7 and
  eliminated D8/D9/D10/D11 context
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - compute whole space once;
  display range is not compute budget
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\strategy\ClassicalSweep.java` -
  read the whole file for ownership helpers and dispatch shape, not for the old per-combination
  engine call
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\RangeSuggester.java` (547)
- Parent test: `RangeSuggesterTest`

## Scope

- `SegSweepClassicalStrategy` implementing `VariationStrategy`:
  - apply crop once
  - run `ResourceGuard` before building the tree
  - build one `ComponentTree`
  - query every displayed `ParameterCombo`
  - publish `VariationResult`s with counts, provenance, density and lazy label providers
- Populate morphology predicates from `ParameterCombo`s and token-level `MorphPredicate`s.
- Use `ComponentTree` counts directly. D8 is eliminated; there is no label count scan.
- Do not retain a per-combination label cache. D9/D10/D11 are eliminated with that architecture.
- Lift `RangeSuggester` into `segsweep.sweep`, severing `BinConfig` and pointing it at a vendored
  `StackHistogram`.
- Vendor `StackHistogram` into `segsweep.util`.
- First end-to-end integration test: image in, displayed grid result set out, without materialising
  every label stack.

## Out of scope

- StarDist and Cellpose strategies - v0.2.0.
- Rendering and grid UI - stages 10 and 11.
- Batch mode - stage 14.
- A global save-cache action. The removed `VariationCache.snapshotResultsToDisk` is not part of the
  component-tree architecture.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/strategy/SegSweepClassicalStrategy.java` | NEW | Tree-backed classical strategy |
| `src/main/java/segsweep/sweep/RangeSuggester.java` | NEW | Lift, severed |
| `src/main/java/segsweep/util/StackHistogram.java` | NEW | Vendored for `RangeSuggester` |
| `src/test/java/segsweep/sweep/RangeSuggesterTest.java` | NEW | Ported |
| `src/test/java/segsweep/sweep/SweepIntegrationTest.java` | NEW | First end-to-end tree-backed run |
| `src/test/java/segsweep/sweep/ClassicalStrategyLazyLabelTest.java` | NEW | Proves lazy materialisation |
| `src/test/java/segsweep/sweep/TreeBuildOnceTest.java` | NEW | Proves one build, many queries |

## Implementation sketch

```java
public final class SegSweepClassicalStrategy implements VariationStrategy {

    public SegSweepClassicalStrategy(ImagePlus source,
                                     CropSpec crop,
                                     SegSweepLabeller.Connectivity connectivity,
                                     SweepProvenance provenance,
                                     ResourceGuard guard);

    @Override
    public void dispatch(ParameterSweep displayWindow,
                         Consumer<VariationResult> publisher,
                         Consumer<SweepProgress> progress,
                         BooleanSupplier cancelCheck) throws Exception;
}
```

Important flow:

```java
ImagePlus cropped = crop.apply(source);
ResourceGuard.Verdict verdict = guard.assessTree(displayWindow, cropped);
if (!verdict.permitted()) {
    throw new SweepRefusedException(verdict.reason());
}

ComponentTree tree = ComponentTree.build(cropped, connectivity);
for (ParameterCombo combo : SweepDispatchOrder.order(displayWindow)) {
    ComponentTreeQuery query = toTreeQuery(combo);
    ComponentTreeResult treeResult = tree.query(query);
    publisher.accept(VariationResult.success(
            combo,
            treeResult.labelMap(),       // lazy provider
            treeResult.objectCount(),    // count query, no pixel walk
            treeResult.queryDurationMs(),
            null,
            provenance));
}
```

Preserve ownership helpers from `ClassicalSweep`: `CropSpec.apply` may return the input unchanged,
so cleanup must not close the user's original image.

`RangeSuggester` severance:

```java
public static ParameterValueList suggestThresholdDisplayWindow(ImagePlus source, CropSpec crop);
public static ParameterValueList suggestSizeDisplayWindow(ImagePlus source, CropSpec crop);
```

It suggests what the user should inspect, not what the engine can afford to compute. For morphology
axes, provide conservative defaults only where `04_SWEEP_ENGINE.md` gives enough information; do not
invent biological thresholds.

## Exit gate

1. `mvn test` passes; `RangeSuggesterTest` green after severance.
2. `SweepIntegrationTest` - a 128x128x10 synthetic stack with known objects, displayed over five
   thresholds, produces five results with monotonically decreasing object counts, non-null
   provenance and finite density when calibrated.
3. `TreeBuildOnceTest` asserts one `ComponentTree.build(...)` call per crop and many query calls.
4. `ClassicalStrategyLazyLabelTest` asserts publishing results does not materialise all label maps;
   only selected draw/output requests call the lazy provider.
5. `ResourceGuard` refuses an oversized tree build before construction starts.
6. Cancellation before or during tree build/query leaves no open `ImagePlus`.
7. `grep -rn "ObjectsCounter3DWrapper\\|ClassicalSegmentationStage\\|BinConfig\\|sourceImageHash\\|cacheNamespace" src/main/java/`
   returns nothing.
8. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **Threshold convention drift.** Stage 02 pinned `>` versus `>=`; the tree query must match it.
- **`RangeSuggester` is 547 lines** and its heuristics are undocumented. Port tests first and treat
  them as specification.
- **Connectivity must be in query identity.** If connectivity changes, tree results change. Include
  it in provenance/report identity even though there is no label-stack cache key.
- **Lazy label ownership.** The grid and auto-save each need fresh label images or clear ownership;
  do not share mutable `ImagePlus` instances between cells.
