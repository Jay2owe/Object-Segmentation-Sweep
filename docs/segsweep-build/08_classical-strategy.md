# Stage 08 — Classical strategy and range suggestion

Wire the labeller, cache and executor into a strategy that actually runs a sweep, and lift the
histogram-driven range suggester.

## Why this stage exists

Stages 02 to 06 built the pieces; this is where a sweep first runs end to end. It is also where the
last performance defect gets fixed and where the plugin gets its first genuinely helpful behaviour:
proposing a sensible range from the image itself, so a user who has no idea what thresholds to try
gets a usable starting point instead of a blank field.

## Prerequisites

- `02_labeller`, `03_parameter-model`, `04_crop-and-token`, `05_cache-and-utils`,
  `06_executor-and-result` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "three severances" section and defect **D8**
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\strategy\ClassicalSweep.java` —
  **the whole file, 260 lines.** It is the template. Note `dispatch` 57-108, `runOne` 110-152,
  `waitForTasks` 154-172, ownership helpers 174-191, `emptyLabelMapLike` 216-238, and
  `countLabels` 240-259 which is being deleted
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\RangeSuggester.java` (547) — note
  its imports of `bin.BinConfig`, `image.StackHistogram`, `ui.config.StarDistParameterStage` and
  `CellposeParameterStage`, all of which are severed here
- Parent test: `RangeSuggesterTest`

## Scope

- `SegSweepClassicalStrategy` implementing `VariationStrategy` — the parent's `ClassicalSweep`
  restructured to call `SegSweepLabeller` instead of `ObjectsCounter3DWrapper`.
- **Fix D8** — take the object count from `LabelResult.objectCount()`; delete `countLabels`
  entirely.
- Populate `SweepProvenance` on every published result (D6) and compute density (D7).
- Lift `RangeSuggester` into `segsweep.sweep`, severing `BinConfig` (drop — it is the
  `channel_config.json` coupling being escaped) and pointing it at the vendored `StackHistogram`.
  Inline the StarDist and Cellpose default constants rather than importing their parameter stages.
- Wire `ResourceGuard` into the dispatch path so an over-large sweep is refused before any work
  starts, with a readable reason.
- First end-to-end integration test: image in, grid of results out.

## Out of scope

- StarDist and Cellpose strategies — v0.2.0. `VariationStrategyChooser` returns the classical
  strategy only.
- Morphology-predicate filtering beyond min/max size — `MorphPredicate` is parsed in stage 04 but
  applying it is deferred; note it in the class Javadoc.
- Any UI. This stage is testable headlessly and must be.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/strategy/SegSweepClassicalStrategy.java` | NEW | From `ClassicalSweep`; D8 |
| `src/main/java/segsweep/sweep/RangeSuggester.java` | NEW | Lift, severed |
| `src/test/java/segsweep/sweep/RangeSuggesterTest.java` | NEW | Ported |
| `src/test/java/segsweep/sweep/SweepIntegrationTest.java` | **NEW** | First end-to-end run |
| `src/test/java/segsweep/sweep/LabelCountPerformanceTest.java` | **NEW** | D8 acceptance |

## Implementation sketch

The strategy, following the parent's shape with the engine call replaced:

```java
public final class SegSweepClassicalStrategy implements VariationStrategy {

    public SegSweepClassicalStrategy(ImagePlus source,
                                     CropSpec crop,
                                     VariationCache cache,
                                     SegSweepLabeller.Connectivity connectivity,
                                     SweepProvenance provenance,
                                     int parallelism);

    @Override
    public void dispatch(ParameterSweep sweep,
                         Consumer<VariationResult> publisher,
                         BooleanSupplier cancelCheck) throws Exception;
}
```

`runOne`, the important diff against `ClassicalSweep.java:110-152`:

```java
int threshold = intParameter(combo, ParameterId.THRESHOLD, 0);
int minSize   = intParameter(combo, ParameterId.MIN_SIZE, 0);
int maxSize   = intParameter(combo, ParameterId.MAX_SIZE, Integer.MAX_VALUE);

LabelResult labelled = SegSweepLabeller.label(cropped, threshold, minSize, maxSize, connectivity);

// D8: the count comes from the labeller. No pixel walk, no HashSet<Integer>.
publisher.accept(VariationResult.success(
        combo,
        labelled.labels(),
        labelled.objectCount(),
        durationMs,
        null,
        provenance));
```

Preserve verbatim from the parent, because each exists for a reason:

- the cache-hit fast path before submitting a task (`ClassicalSweep.java:83-89`)
- `effectiveParallelism` — `min(requested, cores - 1)`, never more (lines 193-199)
- the `task.get(50, MILLISECONDS)` polling loop so cancel is prompt (lines 154-172)
- `closeCroppedIfOwned` / `closeIfOwned` — `CropSpec.apply` may return its input (lines 174-186)
- `intParameter`'s tolerant parsing of `Number` or `String` (lines 201-214)

**Guard before dispatch**, which the parent does not do:

```java
ResourceGuard.Verdict verdict = guard.assess(sweep, cropped, parallelism);
if (!verdict.permitted()) {
    throw new SweepRefusedException(verdict.reason());   // readable, not a bare boolean
}
```

**`RangeSuggester` severance.** Drop the `BinConfig` parameter entirely and take the histogram from
the image:

```java
// was: suggestions driven by BinConfig channel settings
public static ParameterValueList suggestThresholdRange(ImagePlus source, CropSpec crop);
public static ParameterValueList suggestSizeRange(ImagePlus source, CropSpec crop);
```

Read `RangeSuggesterTest` first and keep whatever heuristic it pins — the suggestion quality is
FLASH's accumulated experience with real images and is not worth re-deriving. Inline the DL defaults
it referenced (StarDist prob 0.5 / NMS 0.4, Cellpose diameter 30 / flow 0.4) as constants marked
v0.2.0.

## Exit gate

1. `mvn test` passes; `RangeSuggesterTest` green after severance.
2. `SweepIntegrationTest` — a 128×128×10 synthetic stack with known objects, swept over 5 thresholds,
   produces 5 results with monotonically decreasing object counts, each carrying provenance and a
   finite density.
3. Cache hit path: running the same sweep twice does no second labelling — assert via a counting
   wrapper around the labeller, not by timing.
4. `LabelCountPerformanceTest` — a 512×512×20 label stack's count is obtained without a per-pixel
   boxed set. Assert structurally (`grep` for `HashSet<Integer>` in the strategy returns nothing) and
   by wall clock against stage 02's recorded baseline.
5. `ResourceGuard` refuses an oversized sweep **before** any labelling happens — assert the labeller
   was never called.
6. Cancellation mid-sweep leaves no open `ImagePlus` and no orphaned pool threads.
7. `grep -rn "ObjectsCounter3DWrapper\|ClassicalSegmentationStage\|BinConfig" src/main/java/`
   returns nothing.
8. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **Threshold convention drift.** Stage 02 pinned `>` versus `>=`. If this stage's `intParameter`
  rounding (`Math.round`, then `Math.max(0, ...)` at `ClassicalSweep.java:207`) shifts a value by
  one, the pinned convention silently breaks. Add a test that a threshold of `32` reaches the
  labeller as `32`.
- **`RangeSuggester` is 547 lines** and its heuristics are undocumented. Port the test first and
  treat it as the specification; resist rewriting the heuristic because it looks arbitrary.
- **Cache keys must include connectivity.** It is a new parameter the parent never had. If it is not
  in the key, switching connectivity serves stale labels. Add it to the namespace and add a test.
- **`emptyLabelMapLike` is now the labeller's job** (stage 02 owns `Status.EMPTY`). Make sure the
  strategy does not also synthesise one, or the two will drift.
