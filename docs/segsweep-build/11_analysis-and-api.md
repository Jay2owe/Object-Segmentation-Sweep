# Stage 11 — Analysis orchestration and the public API

Write `SegSweepAnalysis`, the class that runs the whole six-step sweep, and the CPC-shaped
`Parameters` / `Result` / facade triad that exposes it headlessly.

## Why this stage exists

Up to now the pieces work individually. This stage makes them one operation with a single entry
point, and gives the plugin its scriptable surface. Point 8 of the CPC standard — a public Java API
that opens no dialogs, shows no windows and writes no files — is what lets other plugins and scripts
build on this one, and it is the thing FLASH never had for its sweep.

## Prerequisites

- `07_pickers`, `08_classical-strategy` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "Outputs", "Java API" and "Algorithm sketch" sections in full
- CPC's triad as the structural model, under `Experiments\CPC\src\main\java\cpc\`:
  `CPCParameters.java` (162), `CPCResult.java` (99), `CPC.java` (110), `CPCAnalysis.java` (951)
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "Sweep Results" and "Sweep Pick" column tables. These column names are
  a public contract; get them right here and stages 12–14 inherit them

## Scope

- `SegSweepParameters` — immutable builder: image, channel, engine, one or two axes, crop,
  connectivity, pick criterion, stability budget, minimum crop fraction, parallelism.
- `SegSweepResult` — sweep table, pick table, `PickResult`, picked `ParameterCombo`, picked label
  map, per-combination results, provenance, settings token.
- `SegSweepAnalysis` — orchestrates enumerate → guard → dispatch → measure → pick, building both
  `ResultsTable`s.
- `SegSweep` — the static facade, `run(SegSweepParameters)`.
- Validation with typed failures: no image, empty axis, `from > to`, zero step, crop outside image
  bounds, sweep shorter than the pickers need.
- Unit tests over the orchestration, plus an API-purity test.

## Out of scope

- Dialog, macro parsing, plugin entry — stage 12.
- Batch across a folder — stage 13.
- Auto-save to disk — stage 13. **`SegSweep.run` writes no files**, and that is a hard guarantee, not
  a default.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/SegSweepParameters.java` | NEW | From `CPCParameters` |
| `src/main/java/segsweep/SegSweepResult.java` | NEW | From `CPCResult` |
| `src/main/java/segsweep/SegSweepAnalysis.java` | NEW | Orchestration, ~350 lines |
| `src/main/java/segsweep/SegSweep.java` | NEW | From `CPC.java` |
| `src/main/java/segsweep/SweepRefusedException.java` | NEW | Typed refusal from the guard |
| `src/test/java/segsweep/SegSweepAnalysisTest.java` | NEW | Orchestration |
| `src/test/java/segsweep/SegSweepApiPurityTest.java` | **NEW** | CPC standard point 8 |

## Implementation sketch

```java
SegSweepParameters params = SegSweepParameters.builder()
        .image(imp)
        .channel(1)
        .engine(SegmentationMethod.Engine.CLASSICAL)
        .axis(ParameterId.THRESHOLD, 10, 60, 5)
        .axis(ParameterId.MIN_SIZE, ParameterValueList.of(20, 50, 100))   // optional second
        .crop(CropSpec.full())
        .connectivity(SegSweepLabeller.Connectivity.SIX)
        .pickCriterion(PickCriterion.BOTH)
        .stabilityBudgetMillis(0)          // 0 == unlimited (D2)
        .minimumCropFraction(0.05)         // D6 warning threshold
        .build();

SegSweepResult result = SegSweep.run(params);
```

```java
public final class SegSweepResult {
    public ResultsTable sweepTable();          // one row per combination
    public ResultsTable pickTable();           // one row
    public PickResult pick();
    public ParameterCombo pickedCombo();       // null when pick criterion is NONE
    public ImagePlus pickedLabelMap();
    public List<VariationResult> results();
    public SweepProvenance provenance();
    public String pickedSettingsToken();       // SegmentationTokenCodec output
    public List<String> warnings();            // e.g. crop below minimum fraction
}
```

**Column names are a public contract.** From `02_CONTRACT.md`, exactly:

`sweepTable()` — `Combination`, one column per swept axis named by
`ParameterId.displayLabel()`, `Objects`, `Objects_Per_mm3`, `Mean_Neighbour_IoU`,
`Stability_Eligible`, `Duration_ms`, `Crop_Fraction`, `Flags`.

`pickTable()` — criterion, chosen combination index, its parameter values, knee outcome kind, knee
value in parameter units, knee range min/max/step, stability score, eligible count, crop bounds,
crop fraction, and whether the criteria agreed.

Blank cells rather than substituted values: when the image is uncalibrated, `Objects_Per_mm3` is
blank and `Flags` says so (D7). When a combination was ineligible for stability,
`Mean_Neighbour_IoU` is blank and `Stability_Eligible` is `false` (D1).

Orchestration, mirroring the algorithm sketch:

```java
public static SegSweepResult run(SegSweepParameters params) {
    validate(params);                                  // typed failures, never silent
    ParameterSweep sweep = buildSweep(params);
    SweepProvenance prov = SweepProvenance.of(params);
    guard.assessOrThrow(sweep, params);                // SweepRefusedException
    List<VariationResult> results = dispatch(sweep, prov, params);
    PickResult pick = score(results, params);          // KneeDetector + IouStability
    return assemble(results, pick, prov, params);
}
```

**Warnings, not exceptions,** for conditions the user should know about but that do not invalidate
the run: crop below `minimumCropFraction`, uncalibrated image, stability aborted by budget, knee
returned `NO_BEND`. They go on `SegSweepResult.warnings()` and stage 12 surfaces them.

## Exit gate

1. `mvn test` passes.
2. `SegSweepAnalysisTest` — a 128×128×10 synthetic stack with a designed knee at threshold 32,
   swept 10–60 step 5, produces a 11-row sweep table and a pick table naming 32.
3. Column names asserted against literal strings, so a rename breaks the build rather than silently
   breaking every downstream consumer and saved CSV.
4. Uncalibrated input: `Objects_Per_mm3` is blank, `Flags` records it, and `warnings()` mentions it.
5. Cropped input below `minimumCropFraction`: the run completes and `warnings()` names the fraction.
6. `SegSweepApiPurityTest` — **`SegSweep.run` opens no dialog, creates no window and writes no
   file.** Assert via `WindowManager.getImageCount()` unchanged, `ij.WindowManager.getWindowCount()`
   unchanged, and a temp-directory watcher showing no new files. Run the test with no active image
   to prove no active image is required.
7. Validation: each of no-image, empty axis, `from > to`, zero step, out-of-bounds crop, and a
   2-value sweep (too short for stability) produces a distinct, readable failure.
8. `PickCriterion.NONE` returns a populated sweep table with a null `pickedCombo()` and an empty pick
   table, without error.
9. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`ResultsTable` column ordering.** IJ1's `ResultsTable` orders by first-set, so build rows in a
  fixed sequence or the CSV column order will vary between runs with different flag states. Pin it
  and test it.
- **`Objects_Per_mm3` is wrong for 2D input.** Stage 06 flagged this: `pixelDepth` is 1.0 on a single
  slice, so the "volume" is an area. Decide here whether the column is renamed per-dimensionality or
  documented as area for 2D, and make the pick table say which.
- **Warnings are easy to drop.** They pass through three layers to reach the user. Assert they
  survive to `SegSweepResult` here, and stage 12 asserts they reach the dialog.
- **`CPCAnalysis` is 951 lines and is being replaced, not adapted.** Do not read it for
  implementation guidance beyond structure — its subject matter is colocalization, not sweeping.
