# Object Segmentation Sweep — v0.1.0 build

## End goal

A standalone ImageJ/Fiji plugin at `Plugins ▸ Object Segmentation Sweep` that takes one image,
sweeps a segmentation setting across a range of values, shows every result as a grid of coloured
label maps beside the raw image, and tells the user which value sits at the knee of the object-count
curve and which value agrees most closely with its neighbours. The user clicks a cell, presses
**Pick selected**, and gets a label image plus a text file recording the chosen setting, the image
region it was chosen on, and the range it was chosen over.

It installs with no dependency beyond ImageJ itself — no extra update site, no Python.

## Why we're doing this

Everybody who segments objects in Fiji picks a threshold by guessing. Auto Threshold's "Try all"
montages seventeen *methods* at one value each; nothing in Fiji sweeps a parameter's *values*, and
nothing anywhere picks a value for you without hand-drawn ground truth. FLASH already contains
working knee-detection and IoU-stability code that does exactly this, buried inside a pipeline
plugin nobody installs.

After this ships, the answer to "what threshold did you use?" stops being "0.5, it looked right" and
becomes a file you can put in a methods section.

## Architecture overview

A sweep runs in six steps, and the stages below follow them:

```
enumerate → guard → dispatch → measure → pick → render
```

`ParameterSweep` enumerates the Cartesian product of the swept axes into `ParameterCombo`s.
`ResourceGuard` estimates cost and refuses runs that would exhaust memory. `VariationExecutor`
dispatches combinations across a ForkJoinPool, each one going through `SegSweepClassicalStrategy` to
`SegSweepLabeller`, which thresholds, labels in 3D and size-filters. Results are cached by a hash of
the source pixels, the crop and the parameter values. `KneeDetector` and `IouStability` score the
finished set. `VariationGridWindow` lays the results out as cells, each `VariationCellPanel` drawing
a label map over the raw image. Around all of it sits the CPC chassis — dialog, macro options,
batch runner, headless Java facade, auto-save tree.

**Roughly 10,300 lines lift near-verbatim from FLASH with zero non-`ij` imports and ~45 portable
tests. The real work is ~1,200 fresh lines and ten defect fixes.** Do not rewrite what lifts.

## Stage map

| NN | Name | Goal | Size | Depends on |
|---|---|---|---|---|
| 01 | `repo-scaffold` | Create the repo from CPC's furniture; `mvn package` produces a jar Fiji loads | S | — |
| 02 | `labeller` | Fresh `ij`-only 3D connected-components labeller — the engine everything sweeps | M | 01 |
| 03 | `parameter-model` | Parameter axes, Cartesian enumeration, canonical serialisation | M | 01 |
| 04 | `crop-and-token` | Crop model, resource guard, provenance record, reproducible settings token | M | 03 |
| 05 | `cache-and-utils` | Result cache with internal source hashing and byte bounds; vendored histogram and IO | M | 03, 04 |
| 06 | `executor-and-result` | Parallel dispatch, cancellation, per-combination result carrying provenance | M | 03, 04, 05 |
| 07 | `pickers` | Knee detection and IoU stability, with the five correctness fixes that make them citable | **L — the differentiator** | 03 |
| 08 | `classical-strategy` | Wire labeller + cache + executor into a running sweep; histogram range suggestion | M | 02, 03, 04, 05, 06 |
| 09 | `render-stack` | Label-map and threshold overlay rendering, preview panel, Swing theme | M | 01 |
| 10 | `grid-reviewer` | The grid window and cell panel: synced scroll, peek, compare, pick | **L** | 06, 07, 09 |
| 11 | `analysis-and-api` | Orchestration and the public headless Java API | M | 07, 08 |
| 12 | `dialog-and-entry` | Three-section dialog, plugin entry, macro recording | M | 10, 11 |
| 13 | `batch-and-autosave` | Batch dialog, folder runner, auto-save tree | M | 11, 12 |
| 14 | `release-furniture` | README, citation, changelog, publishing audit, local deploy | S | all |

Stages **02, 03 and 09** can run in parallel once 01 is done. Stage **07** can run in parallel with
05, 06 and 08. Everything else is sequential.

## House rules

Every stage must respect these. They come from `02_CONTRACT.md` and the portfolio's CPC standard.

1. **`ij` is the only compile dependency.** No `mcib3d`, no `sc.fiji.*`, no `gnu.trove`, no
   `net.imagej`, no `smile`. If a lift needs one, the lift is wrong — sever it. This rule is half
   the reason the plugin exists.
2. **Copy from FLASH, never move.** FLASH must keep building throughout. Nothing in this plan edits
   the FLASH repo. Read parent files, copy the code, adjust.
3. **Java 8 source level.** Fiji compatibility. No `var`, no records, no switch expressions.
4. **No silent empties.** Every "I couldn't compute that" path returns a typed reason, not
   `Optional.empty()` or `null`. This is the single most common defect in the parent and it is what
   makes a batch run untrustworthy.
5. **Nothing is comparable unless its conditions are recorded.** Any number derived from a crop or a
   range carries that crop and that range with it. See the `SweepProvenance` record in stage 04.
6. **The public API opens no dialogs, shows no windows, writes no files** and does not require an
   active ImageJ window.
7. **`IJ.log` is not error handling.** Anything a caller needs to know goes in the returned object.
8. **Spelling:** the family standard is `-z-` `colocaliz*`. No colocalization surface in v0.1.0, but
   check anything lifted from FLASH before copying it through.

## Known open questions

Carried from `03_BUILD_PLAN.md`. None blocks stage 01.

| Question | Owner stage |
|---|---|
| When knee and stability disagree, what does the UI show? Intent: show both, refuse to arbitrate | 10, 12 |
| 6- or 26-connectivity as the labeller default? **Read 3D Objects Counter+ and match it — do not assume** | 02 |
| Does the crop warning threshold belong at 5%? | 04, empirical later |
| Is mean-neighbour-IoU defensible as a selection criterion, or does it need a citation? | 07 documents it; resolved before the v0.2.0 preprint |
| Does `HistogramShapeStability` earn UI space? Ship it unexposed and decide from use | 07 |

**Honest unknown:** `VariationCellPanel` is 2,737 lines whose cleanliness was established by import
scan, not by reading it. The imports are clean; a class that size usually has assumptions the
imports do not show. Stage 10 should budget for that being worse than it looks.

## Source documents

The canonical long-form record, not superseded by this folder:

- `../../../ImageJ Plugins/Object Segmentation Sweep/00_CASE.md` — why this plugin, the six candidates, ecosystem evidence, kill criteria
- `../../../ImageJ Plugins/Object Segmentation Sweep/01_NAMING.md` — identity table, collision checks, conventions
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — **dependency table, the 11-item defect ledger, I/O contract, algorithm
  sketch.** Read the defect ledger before any stage that touches a lifted file
- `../../../ImageJ Plugins/Object Segmentation Sweep/03_BUILD_PLAN.md` — the source plan this folder splits

FLASH parent: `Experiments\FLASH\src\main\java\flash\pipeline\`
CPC chassis: `Experiments\CPC\src\main\java\cpc\`

## How to run a stage

```
/do-step docs/segsweep-build/
```

Executes the lowest-numbered file without a `_COMPLETED` suffix, then renames it.
