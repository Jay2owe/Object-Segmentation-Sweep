# Object Segmentation Sweep - v0.1.0 build

## End goal

A standalone ImageJ/Fiji plugin at `Plugins > Object Segmentation Sweep` that takes one image,
computes the classical segmentation parameter space once, shows a chosen display window as a grid
of coloured label maps beside the raw image, and tells the user which value sits at the knee of the
object-count curve and which value agrees most closely with its neighbours. The user clicks a cell,
presses **Pick selected**, and gets a label image plus a text file recording the chosen setting, the
image region it was chosen on, and the range displayed during review.

It installs with no dependency beyond ImageJ itself - no extra update site, no Python.

## Why we're doing this

Everybody who segments objects in Fiji picks a threshold by guessing. Auto Threshold's "Try all"
montages seventeen methods at one value each; nothing in Fiji sweeps a parameter's values, and
nothing anywhere picks a value for you without hand-drawn ground truth. FLASH already contains
working knee-detection and IoU-stability code that does exactly this, buried inside a pipeline
plugin nobody installs.

After this ships, the answer to "what threshold did you use?" stops being "0.5, it looked right" and
becomes a file you can put in a methods section.

## Architecture overview

The accepted engine architecture is `04_SWEEP_ENGINE.md`: **compute the space, do not sample it**.
The plain `SegSweepLabeller` is still built first, but only as the simple, trustworthy oracle. The
shipping classical engine builds a `ComponentTree` once over the crop. Thresholds are cuts through
that tree; min/max size and morphology filters are attribute predicates on tree nodes. Counts for
any `(threshold, size, morphology)` combination are tree queries, and label maps are materialised
lazily only when a grid cell is drawn or an output file needs the picked labels.

A sweep runs in six conceptual steps:

```
enumerate display window -> guard tree memory -> build tree -> query/measure -> pick -> render lazily
```

`ParameterSweep` enumerates the public `from`/`to`/`step` or explicit value lists into
`ParameterCombo`s. Those values are a **display window**, not the compute budget. `ResourceGuard`
estimates component-tree memory and refuses runs that would exhaust memory. The classical strategy
builds the whole tree once, uses tree queries to populate `VariationResult`s with counts and
provenance, and exposes a lazy label-map provider for the grid and auto-save. `KneeDetector` and
`IouStability` score the finished set. `VariationGridWindow` lays the selected display window out as
cells, each `VariationCellPanel` asking the provider for a label map only when it must draw. Around
all of it sits the CPC chassis - dialog, macro options, batch runner, headless Java facade,
auto-save tree.

`VariationExecutor` remains in the v0.2 engine shape because StarDist and Cellpose will still have
expensive cached intermediates and dispatch work. Classical v0.1 does not retain the old
multi-label-stack `VariationCache` architecture.

## Defect-ledger consequences

Accepted from `04_SWEEP_ENGINE.md`:

| Defect | Status in this queue | Consequence |
|---|---|---|
| D1 | Active | Stability still scores only eligible interior combinations and reports eligibility. |
| D2 | Moot | The old 5-second IoU budget was a cost guard for label-stack comparisons; tree queries make it unnecessary, though UI progress still exists. |
| D3 | Moot | The old `>2 axes` silent refusal was a cost guard; query code must return typed refusals, while the v0.1 UI still caps at two axes. |
| D4 | Active | Knee detection still returns typed outcomes, never a bare empty. |
| D5 | Root cause eliminated | The full threshold axis is computed once; public ranges are display windows, so comparable knees are not normalised to a user-guessed compute range. |
| D6 | Active | Crop bounds, crop fraction and displayed range stay in every result and settings file. |
| D7 | Active | Counts per calibrated volume are still reported beside raw counts. |
| D8 | Eliminated | Counts come directly from tree queries; nothing re-walks pixels into a boxed label set. |
| D9 | Largely eliminated | There is no many-entry mutable label-stack cache; label maps are realised lazily and discarded unless they are selected for output. |
| D10, D11 | Eliminated with D9 | Source-hash poisoning and shared-instance disk-save side effects belonged to the removed label-stack cache. |

## Stage map

| NN | Name | Goal | Size | Depends on |
|---|---|---|---|---|
| 01 | `repo-scaffold` | Create the repo from CPC's furniture; `mvn package` produces a jar Fiji loads | S | - |
| 02 | `labeller` | Fresh `ij`-only 3D connected-components labeller, used as the oracle | M | 01 |
| 03 | `component-tree` | Fresh max-tree over the crop with incremental size, intensity, moment and surface attributes | L | 02 |
| 04 | `component-tree-equivalence` | Hard gate: prove tree counts and lazy labels match the plain labeller, extended per morphology predicate | M | 02, 03 |
| 05 | `parameter-model` | Parameter axes, including morphology axes, Cartesian enumeration, canonical serialisation | M | 04 |
| 06 | `crop-and-token` | Crop model, component-tree resource guard, provenance record, reproducible settings token | M | 05 |
| 07 | `executor-and-result` | v0.2-shaped executor/result plumbing; results carry provenance, density and lazy label access | M | 05, 06 |
| 08 | `pickers` | Knee detection and IoU stability, keeping active D1 and D4 fixes | L - the differentiator | 05, 06, 07 |
| 09 | `classical-strategy` | Build the tree once, query the displayed combinations, and suggest display ranges | M | 04, 05, 06, 07 |
| 10 | `render-stack` | Label-map and threshold overlay rendering, preview panel, Swing theme | M | 01 |
| 11 | `grid-reviewer` | The grid window and cell panel: synced scroll, peek, compare, pick, lazy realization | L | 07, 08, 09, 10 |
| 12 | `analysis-and-api` | Orchestration and the public headless Java API | M | 08, 09, 11 |
| 13 | `dialog-and-entry` | Three-section dialog, plugin entry, macro recording | M | 11, 12 |
| 14 | `batch-and-autosave` | Batch dialog, folder runner, auto-save tree | M | 12, 13 |
| 15 | `release-furniture` | README, citation, changelog, publishing audit, local deploy | S | all |

Stages **02** then **03** then **04** are intentionally sequential. Nothing downstream starts until
`ComponentTreeEquivalenceTest` is green. After stage 04, stages **05** and **10** can run in
parallel. Stage **08** can run in parallel with stage **09** once stages 05-07 are complete.

## House rules

Every stage must respect these. They come from `02_CONTRACT.md`, `04_SWEEP_ENGINE.md` and the
portfolio's CPC standard.

1. **`ij` is the only compile dependency.** No `mcib3d`, no `sc.fiji.*`, no `gnu.trove`, no
   `net.imagej`, no `smile`. If a lift needs one, the lift is wrong - sever it.
2. **Copy from FLASH, never move.** FLASH must keep building throughout. Nothing in this plan edits
   the FLASH repo. Read parent files, copy the code, adjust.
3. **Java 8 source level.** Fiji compatibility. No `var`, no records, no switch expressions.
4. **No silent empties.** Every "I couldn't compute that" path returns a typed reason, not
   `Optional.empty()` or `null`.
5. **Nothing is comparable unless its conditions are recorded.** Any number derived from a crop or a
   display range carries that crop and that range with it.
6. **The public API opens no dialogs, shows no windows, writes no files** and does not require an
   active ImageJ window.
7. **`IJ.log` is not error handling.** Anything a caller needs to know goes in the returned object.
8. **Spelling:** the family standard is `-z-` `colocaliz*`. No colocalization surface in v0.1.0, but
   check anything lifted from FLASH before copying it through.
9. **No old label-stack cache.** Do not reintroduce `VariationCache`, per-combination retained label
   stacks, caller-supplied source hashes, or disk snapshots of every combination.

## Known open questions

Carried from `03_BUILD_PLAN.md` and `04_SWEEP_ENGINE.md`. None blocks stage 01.

| Question | Owner stage |
|---|---|
| When knee and stability disagree, what does the UI show? Intent: show both, refuse to arbitrate | 11, 13 |
| 6- or 26-connectivity as the labeller and tree default? **Read 3D Objects Counter+ and match it - do not assume** | 02, 03, 04 |
| Which Salembier filtering rule reproduces 3D Objects Counter+ on non-increasing morphology attributes? | 03, 04 |
| Does the crop warning threshold belong at 5%? | 06, empirical later |
| Is mean-neighbour-IoU defensible as a selection criterion, or does it need a citation? | 08 documents it; resolved before the v0.2.0 preprint |
| Does `HistogramShapeStability` earn UI space? Ship it unexposed and decide from use | 08 |
| Does the whole-space computation kill the "suggest range" button? No - it becomes display convenience | 09, 13 |

**Honest unknown:** `VariationCellPanel` is 2,737 lines whose cleanliness was established by import
scan, not by reading it. The imports are clean; a class that size usually has assumptions the
imports do not show. Stage 11 should budget for that being worse than it looks.

## Source documents

The canonical long-form record, not superseded by this folder:

- `../../../ImageJ Plugins/Object Segmentation Sweep/00_CASE.md` - why this plugin, the six candidates, ecosystem evidence, kill criteria
- `../../../ImageJ Plugins/Object Segmentation Sweep/01_NAMING.md` - identity table, collision checks, conventions
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - dependency table, defect ledger, I/O contract, algorithm sketch
- `../../../ImageJ Plugins/Object Segmentation Sweep/03_BUILD_PLAN.md` - the source plan this folder splits
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - **accepted component-tree architecture; compute whole space once**

FLASH parent: `Experiments\FLASH\src\main\java\flash\pipeline\`
CPC chassis: `Experiments\CPC\src\main\java\cpc\`

## How to run a stage

```
/do-step docs/segsweep-build/
```

Executes the lowest-numbered file without a `_COMPLETED` suffix, then renames it.
