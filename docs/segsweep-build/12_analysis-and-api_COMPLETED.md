# Stage 12 - Analysis orchestration and the public API

Write `SegSweepAnalysis`, the class that runs the whole sweep operation, and the CPC-shaped
`Parameters` / `Result` / facade triad that exposes it headlessly.

## Why this stage exists

Up to now the pieces work individually. This stage makes them one operation with a single entry
point. Point 8 of the CPC standard - a public Java API that opens no dialogs, shows no windows and
writes no files - is what lets other plugins and scripts build on this one.

## Prerequisites

- `08_pickers`, `09_classical-strategy`, `11_grid-reviewer` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `docs/segsweep-build/09_classical-strategy.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - Outputs, Java API,
  Algorithm sketch, Sweep Results and Sweep Pick column tables
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - public range is a display
  window; classical computes the whole tree once
- CPC's triad as structural model, under `Experiments\CPC\src\main\java\cpc\`:
  `CPCParameters.java`, `CPCResult.java`, `CPC.java`, `CPCAnalysis.java`

## Scope

- `SegSweepParameters` - immutable builder: image, channel, engine, one or two display axes, crop,
  connectivity, pick criterion, minimum crop fraction, parallelism.
- `SegSweepResult` - sweep table, pick table, `PickResult`, picked `ParameterCombo`, lazy picked
  label-map provider, per-combination results, provenance, settings token and warnings.
- `SegSweepAnalysis` - orchestrates:
  `enumerate display window -> guard tree memory -> build/query -> measure -> pick -> assemble`.
- `SegSweep` - static facade, `run(SegSweepParameters)`.
- Validation with typed failures: no image, empty axis, `from > to`, zero step, crop outside image
  bounds, unsupported engine, unsupported axis combination.
- Unit tests over orchestration plus API purity.

## Out of scope

- Dialog, macro parsing, plugin entry - stage 13.
- Batch across a folder and auto-save to disk - stage 14.
- UI grid display. `SegSweep.run` returns data; callers decide whether to show it.
- Any production fallback that runs `SegSweepLabeller` once per displayed combination. The labeller
  is an oracle/test tool; classical execution uses the component tree.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/SegSweepParameters.java` | NEW | From `CPCParameters` |
| `src/main/java/segsweep/SegSweepResult.java` | NEW | From `CPCResult`, lazy labels |
| `src/main/java/segsweep/SegSweepAnalysis.java` | NEW | Orchestration |
| `src/main/java/segsweep/SegSweep.java` | NEW | From `CPC.java` |
| `src/main/java/segsweep/SweepRefusedException.java` | NEW | Typed refusal from the guard |
| `src/test/java/segsweep/SegSweepAnalysisTest.java` | NEW | Orchestration |
| `src/test/java/segsweep/SegSweepApiPurityTest.java` | NEW | CPC standard point 8 |

## Implementation sketch

```java
SegSweepParameters params = SegSweepParameters.builder()
        .image(imp)
        .channel(1)
        .engine(SegmentationMethod.Engine.CLASSICAL)
        .axis(ParameterId.THRESHOLD, 10, 60, 5)
        .axis(ParameterId.SPHERICITY, ParameterValueList.of(0.60, 0.70, 0.80))
        .crop(CropSpec.full())
        .connectivity(SegSweepLabeller.Connectivity.SIX)
        .pickCriterion(PickCriterion.BOTH)
        .minimumCropFraction(0.05)
        .build();

SegSweepResult result = SegSweep.run(params);
```

```java
public final class SegSweepResult {
    public ResultsTable sweepTable();
    public ResultsTable pickTable();
    public PickResult pick();
    public ParameterCombo pickedCombo();       // null when pick criterion is NONE
    public LazyLabelMap pickedLabelMap();      // materialised by caller only when needed
    public List<VariationResult> results();
    public SweepProvenance provenance();
    public String pickedSettingsToken();
    public List<String> warnings();
}
```

Column names are a public contract. `sweepTable()`:

`Combination`, one column per displayed axis named by `ParameterId.displayLabel()`, `Objects`,
`Objects_Per_mm3`, `Mean_Neighbour_IoU`, `Stability_Eligible`, `Duration_ms`, `Crop_Fraction`,
`Flags`.

`pickTable()`:

criterion, chosen combination index, parameter values, knee outcome kind, knee value in parameter
units, displayed range min/max/step, stability score, eligible count, crop bounds, crop fraction,
and whether the criteria agreed.

Blank cells rather than substituted values: uncalibrated images leave `Objects_Per_mm3` blank and
flag it; ineligible stability rows leave `Mean_Neighbour_IoU` blank and set `Stability_Eligible` to
`false`.

Orchestration:

```java
public static SegSweepResult run(SegSweepParameters params) {
    validate(params);
    ParameterSweep displayWindow = buildDisplayWindow(params);
    SweepProvenance prov = SweepProvenance.of(params, displayWindow);
    List<VariationResult> results = dispatchTreeBackedClassical(displayWindow, prov, params);
    PickResult pick = score(results, params);
    return assemble(results, pick, prov, params);
}
```

Warnings, not exceptions, for conditions that do not invalidate the run: crop below
`minimumCropFraction`, uncalibrated image, no eligible stability point, knee `NO_BEND`.

## Exit gate

1. `mvn test` passes.
2. `SegSweepAnalysisTest` - a synthetic stack with a designed knee at threshold 32, displayed
   10-60 step 5, produces the expected sweep rows and a pick table naming 32.
3. Column names are asserted against literal strings.
4. Uncalibrated input: `Objects_Per_mm3` is blank, `Flags` records it, and `warnings()` mentions it.
5. Cropped input below `minimumCropFraction` completes and `warnings()` names the fraction.
6. `SegSweepApiPurityTest` - `SegSweep.run` opens no dialog, creates no window and writes no file.
   Assert with `WindowManager` counts and a temp-directory watcher; run with no active image.
7. Validation failures are distinct and readable.
8. `PickCriterion.NONE` returns a populated sweep table with null `pickedCombo()` and an empty pick
   table, without error.
9. A test asserts `pickedLabelMap()` is not materialised until a caller requests it.
10. `rg -n "SegSweepLabeller\\.label\\(" src/main/java/segsweep` shows no classical production
    per-combination oracle path.
11. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`ResultsTable` column ordering.** IJ1 orders by first-set; build rows in a fixed sequence.
- **`Objects_Per_mm3` is wrong for 2D input if treated literally.** Decide whether 2D reports
  area-normalised counts separately and make the label honest.
- **Warnings are easy to drop.** Assert they survive to `SegSweepResult`; stage 13 asserts they reach
  users.
- **`CPCAnalysis` is a structural reference only.** Its subject matter is colocalization, not
  sweeping.
