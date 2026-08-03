# Stage 13 — Batch and auto-save

Add the batch dialog and folder runner from CPC's chassis, and write the auto-save tree including
`picked_settings.txt`.

## Why this stage exists

Two things arrive here. First, point 5 of the CPC standard — regex grouping, group preview,
recursive scan, per-folder summaries — lifts almost unchanged from `CPCBatch` and turns a
one-image tool into something usable on a real experiment. Second, per-folder aggregation of picks
makes **setting drift visible**: when the value chosen for image one stops suiting image forty, the
summary shows it. That was candidate C5's entire question, and it arrives here as a side effect.

Auto-save is also where the plugin's actual deliverable lands on disk. `picked_settings.txt` is what
goes in a methods section.

## Prerequisites

- `11_analysis-and-api`, `12_dialog-and-entry` complete.

## Read first

- `docs/segsweep-build/00_overview.md` — house rule 5
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "Outputs" section, the auto-save tree, and the
  `SettingsTokenWriter` format from stage 04
- CPC's models under `Experiments\CPC\src\main\java\cpc\`:
  `CPCBatch.java` (1,138) — **the batch dialog: regex, capture group, preview, recursive.** Adapt,
  do not rewrite; `CPCBatchRunner.java` (128), `CPCBatchParameters.java` (203),
  `CPCBatchResult.java` (62)
- `src/main/java/segsweep/token/SettingsTokenWriter.java` from stage 04

## Scope

- `SegSweepBatch` — the batch dialog, adapted from `CPCBatch`: folder picker, filename regex with a
  capture group, **group preview before anything runs**, recursive scan toggle.
- `SegSweepBatchParameters`, `SegSweepBatchRunner`, `SegSweepBatchResult` — CPC's shapes.
- Per-folder aggregation: every image's pick in one table, with a **comparability check** — two
  picks may only be summarised together when `SweepProvenance.comparableWith` and
  `KneeOutcome.comparable` both hold.
- The auto-save tree, with a `README.txt` in each folder.
- `picked_settings.txt` via `SettingsTokenWriter`.
- Batch continues past a failing image, recording the failure rather than aborting the folder.

## Out of scope

- A drift *alarm* — candidate C5 proper. This stage makes drift visible in a table; deciding what
  counts as drift and warning about it is deferred.
- Any second sweep axis behaviour that differs from single-image mode. Batch runs the same sweep on
  every image.
- Parallelism across images. Each image's sweep is already parallel internally; nesting pools is how
  you exhaust memory.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/SegSweepBatch.java` | NEW | From `CPCBatch`, ~1,140 lines |
| `src/main/java/segsweep/SegSweepBatchParameters.java` | NEW | From CPC |
| `src/main/java/segsweep/SegSweepBatchRunner.java` | NEW | From CPC |
| `src/main/java/segsweep/SegSweepBatchResult.java` | NEW | From CPC + aggregation |
| `src/main/java/segsweep/AutoSaveWriter.java` | **NEW** | The output tree |
| `src/test/java/segsweep/SegSweepBatchTest.java` | NEW | Grouping, recovery |
| `src/test/java/segsweep/AutoSaveWriterTest.java` | NEW | Tree and file contents |

## Implementation sketch

The auto-save tree, exactly as specified in `02_CONTRACT.md`:

```
<parent of input>/Object Segmentation Sweep/
  sweep_results.csv          the Sweep Results table
  pick_summary.csv           the Sweep Pick table
  picked_settings.txt        settings token + crop bounds + swept range
  grid.png                   the reviewer montage
  labels/<image>_picked.tif  the picked label map
  README.txt
```

In batch, add a folder-level roll-up beside the per-image folders:

```
  batch_picks.csv            one row per image: pick, criterion, agreement, comparability
  batch_failures.csv         images that failed, with the reason
```

**Comparability is enforced, not assumed.** House rule 5 in its most consequential form:

```java
// In SegSweepBatchResult
public boolean allComparable();     // every pick shares crop policy, range and step
public List<String> incomparableReasons();
```

When picks are not comparable — a per-image crop was used, or one image was swept over a different
range — `batch_picks.csv` still lists every row, but the summary line says the set is not
comparable and names why. **Do not compute a mean of incomparable picks.** A mean threshold across
images swept over different ranges is a number that looks meaningful and is not, and printing it is
how a batch mode becomes actively harmful.

`README.txt` content, written into every output folder — brief, and it should answer the question a
person has when they find this folder in a year:

```
Object Segmentation Sweep 0.1.0

sweep_results.csv   One row per combination of settings tried.
pick_summary.csv    The suggested value and how it was chosen.
picked_settings.txt The chosen settings, plus the image region and the range
                    they were chosen over. This is the file to quote in a
                    methods section.
grid.png            The grid as reviewed.
labels/             The label image for the picked settings.

Values in these files are conditional on the region and range recorded in
picked_settings.txt. Results from different regions or ranges are not directly
comparable.
```

Batch resilience — one bad file must not lose the folder:

```java
for (File file : files) {
    try {
        results.add(SegSweep.run(paramsFor(file)));
    } catch (Exception e) {
        failures.add(new BatchFailure(file, e.getMessage()));   // continue
    }
}
```

Adapt from `CPCBatch` rather than rewriting: the regex-with-capture-group grouping, the preview
table shown before running, and the recursive scan are all solved there and are 1,138 lines of
already-debugged behaviour.

## Exit gate

1. `mvn test` passes.
2. Grouping: a folder of `Exp1-A01_LH_CTX.tif`-style names with capture-group regex produces the
   expected groups, and the **preview shows them before anything runs**.
3. Recursive scan finds images in subfolders; non-recursive does not.
4. A folder of 5 images where image 3 is corrupt: the other 4 complete, `batch_failures.csv` names
   image 3 with a reason, and the run does not abort.
5. Auto-save tree matches the specification exactly — every named file present, `README.txt` in each
   folder, `labels/` populated.
6. `picked_settings.txt` round-trips: parsing its `settings` line yields an equal
   `SegmentationMethod`, and the `region` line matches the crop actually used.
7. **Comparability:** a batch where two images used different crops produces
   `allComparable() == false`, `incomparableReasons()` names the crop difference, and
   **no mean or median pick is written anywhere in the output**. Assert the absence.
8. A batch where all images used identical settings produces `allComparable() == true` and a
   per-folder summary.
9. `hide_display` batch runs end to end with no window opened.
10. Existing output is not silently overwritten — either versioned or refused with a message.
11. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`CPCBatch` is 1,138 lines of dialog.** Adapting it is mostly renaming, but its preview table and
  regex validation are subtle. Port its behaviour before changing anything, and check CPC for
  Dropbox conflicted-copy files before copying (stage 01's risk applies again).
- **Path length on Windows.** Nested output folders inside a long Dropbox path will exceed 260
  characters on some setups. Test with a deliberately deep path and fail with a clear message rather
  than an `IOException` from three layers down.
- **Memory across images.** Each image's sweep holds a cache. Clear it between images or the tenth
  image inherits nine images' worth of labels — stage 05's byte budget makes this survivable, but
  only if the cache is actually reset.
- **The temptation to average.** Reviewers of this plan, and future contributors, will want a "mean
  threshold across the folder". Exit gate 7 exists to stop it. If it is ever added, it must be
  gated on `allComparable()`.
