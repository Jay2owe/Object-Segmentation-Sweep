# Stage 14 - Batch and auto-save

Add the batch dialog and folder runner from CPC's chassis, and write the auto-save tree including
`picked_settings.txt`.

## Why this stage exists

Batch mode turns a one-image tool into something usable on a real experiment. CPC already solved
regex grouping, capture-group preview, recursive scan and per-folder summaries; adapt that chassis.
Per-folder aggregation of picks makes setting drift visible, while comparability checks prevent
averaging values that were chosen under different crops or displayed ranges.

Auto-save is where the deliverable lands on disk. `picked_settings.txt` is what goes in a methods
section, and the picked label map is materialised lazily from the result only when it is written.

## Prerequisites

- `12_analysis-and-api`, `13_dialog-and-entry` complete.

## Read first

- `docs/segsweep-build/00_overview.md` - house rule 5
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - Outputs, auto-save tree and
  batch contract
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - lazy realisation and
  display-window semantics
- CPC models under `Experiments\CPC\src\main\java\cpc\`:
  `CPCBatch.java` (1,138), `CPCBatchRunner.java` (128), `CPCBatchParameters.java` (203),
  `CPCBatchResult.java` (62)
- `src/main/java/segsweep/token/SettingsTokenWriter.java` from stage 06

## Scope

- `SegSweepBatch` - the batch dialog, adapted from `CPCBatch`: folder picker, filename regex with a
  capture group, group preview before anything runs, recursive scan toggle.
- `SegSweepBatchParameters`, `SegSweepBatchRunner`, `SegSweepBatchResult` - CPC's shapes.
- Per-folder aggregation: every image's pick in one table, with a comparability check. Picks may
  only be summarised together when `SweepProvenance.comparableWith` and `KneeOutcome.comparable`
  both hold.
- Auto-save tree with a `README.txt` in each folder.
- `picked_settings.txt` via `SettingsTokenWriter`.
- Materialise only the picked label map for output; do not materialise every displayed cell.
- Batch continues past a failing image, recording the failure rather than aborting the folder.

## Out of scope

- A drift alarm - candidate C5 proper. This stage makes drift visible in a table only.
- Any second sweep axis behaviour that differs from single-image mode.
- Parallelism across images. Each image's tree build already consumes memory; nested pools are not
  part of v0.1.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/SegSweepBatch.java` | NEW | From `CPCBatch`, ~1,140 lines |
| `src/main/java/segsweep/SegSweepBatchParameters.java` | NEW | From CPC |
| `src/main/java/segsweep/SegSweepBatchRunner.java` | NEW | From CPC |
| `src/main/java/segsweep/SegSweepBatchResult.java` | NEW | From CPC + aggregation |
| `src/main/java/segsweep/AutoSaveWriter.java` | NEW | The output tree |
| `src/test/java/segsweep/SegSweepBatchTest.java` | NEW | Grouping, recovery |
| `src/test/java/segsweep/AutoSaveWriterTest.java` | NEW | Tree and file contents |

## Implementation sketch

Auto-save tree:

```text
<parent of input>/Object Segmentation Sweep/
  sweep_results.csv          the Sweep Results table
  pick_summary.csv           the Sweep Pick table
  picked_settings.txt        settings token + crop bounds + displayed range
  grid.png                   the reviewer montage
  labels/<image>_picked.tif  the picked label map
  README.txt
```

Batch roll-up beside per-image folders:

```text
  batch_picks.csv            one row per image: pick, criterion, agreement, comparability
  batch_failures.csv         images that failed, with the reason
```

Comparability is enforced, not assumed:

```java
public boolean allComparable();
public List<String> incomparableReasons();
```

When picks are not comparable, `batch_picks.csv` still lists every row, but the summary line says the
set is not comparable and names why. Do not compute a mean of incomparable picks.

`README.txt` should tell a future reader what each file is and that values are conditional on the
region and displayed range recorded in `picked_settings.txt`.

Batch resilience:

```java
for (File file : files) {
    try {
        results.add(SegSweep.run(paramsFor(file)));
    } catch (Exception e) {
        failures.add(new BatchFailure(file, e.getMessage()));
    } finally {
        releaseTreeAndMaterialisedLabels();
    }
}
```

Adapt from `CPCBatch` rather than rewriting: the regex-with-capture-group grouping, preview table and
recursive scan are already solved there.

## Exit gate

1. `mvn test` passes.
2. Grouping: `Exp1-A01_LH_CTX.tif`-style names with capture-group regex produce expected groups and
   the preview shows them before anything runs.
3. Recursive scan finds images in subfolders; non-recursive does not.
4. A folder of 5 images where image 3 is corrupt: the other 4 complete, `batch_failures.csv` names
   image 3 with a reason, and the run does not abort.
5. Auto-save tree matches the specification exactly; `labels/` is populated by materialising only
   the picked label map.
6. `picked_settings.txt` round-trips through the token parser and records the crop actually used.
7. Comparability: different crops produce `allComparable() == false`, reasons name the crop
   difference, and no mean or median pick is written anywhere.
8. Identical settings/provenance produce `allComparable() == true` and a per-folder summary.
9. `hide_display` batch runs end to end with no window opened.
10. Existing output is not silently overwritten - either versioned or refused with a message.
11. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`CPCBatch` is 1,138 lines of dialog.** Its preview table and regex validation are subtle; port
  behaviour before changing it.
- **Path length on Windows.** Test a deliberately deep path and fail with a clear message.
- **Memory across images.** Release component trees and materialised labels between images; do not
  keep intermediates for the whole folder.
- **The temptation to average.** Exit gate 7 exists to stop incomparable pick summaries.
