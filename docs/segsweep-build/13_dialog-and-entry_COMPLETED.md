# Stage 13 - Dialog, plugin entry and macro recording

Build the three-section dialog, replace the stub entry class, and make every option
macro-recordable.

## Why this stage exists

This is where the plugin becomes usable by someone who has not read any of this. It is also the
stage most likely to recreate the parent's failure: FLASH is unadopted because its capability is
reachable only through a large configuration surface. The kill criterion from `00_CASE.md` applies
directly here — **if the Analysis section cannot be explained in one screenshot and two sentences,
cut it to a single axis before release.**

## Prerequisites

- `11_grid-reviewer`, `12_analysis-and-api` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "Dialog" and "Macro options" sections; the option table is the spec
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` — `from`/`to`/`step` are
  display windows, not compute budgets
- `../../../ImageJ Plugins/Object Segmentation Sweep/00_CASE.md` — the "It could become FLASH in miniature" risk and the kill criteria
- CPC's models under `Experiments\CPC\src\main\java\cpc\`:
  `ui/CPCDialog.java` (460) — Input / Analysis / Output structure; **only the Analysis section
  changes**; `CPC_.java` (523) — macro-vs-interactive routing; `CPCMacroOptions.java` (336),
  `CPCMacroOptionsParser.java` (198)
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\SweepRangeEditor.java` (466) and
  `ParameterSweepEditor.java` (1,010) — **for reference only.** `ParameterSweepEditor` is FLASH's
  full editor and lifting it wholesale is precisely the failure mode this stage must avoid

## Scope

- `SegSweepDialog` — three sections, `ToggleSwitch` for booleans, CPC's spacing and header
  conventions.
- `SegSweep_` — replace stage 01's stub. Route macro options versus interactive; on completion open
  the grid from stage 11 and wire pick-selected back to the result.
- `SegSweepMacroOptions` and `SegSweepMacroOptionsParser` — the full option table from
  `02_CONTRACT.md`, with `hide_display`.
- Macro recorder integration: a recorded run replays with identical deterministic analysis fields.
- Surface `SegSweepResult.warnings()` in the dialog and in the grid's status line.
- The **"suggest range"** button, calling stage 09's `RangeSuggester`; label it as a display-range
  suggestion.
- A live combination count and cost estimate, with `ResourceGuard`'s refusal shown *before* the user
  presses Run.

## Out of scope

- Batch — stage 14 adds a separate dialog, not a tab.
- Auto-save — stage 14.
- Anything that adds a third sweep axis, a preset system, or a config file. Those are the parent's
  shape.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/ui/SegSweepDialog.java` | NEW | From `CPCDialog`, new Analysis section |
| `src/main/java/segsweep/SegSweep_.java` | MODIFY | Replace the stub |
| `src/main/java/segsweep/SegSweepMacroOptions.java` | NEW | From `CPCMacroOptions` |
| `src/main/java/segsweep/SegSweepMacroOptionsParser.java` | NEW | From `CPCMacroOptionsParser` |
| `src/test/java/segsweep/SegSweepMacroOptionsTest.java` | NEW | Round-trip |
| `src/test/java/segsweep/SegSweepDialogTest.java` | NEW | Headless-safe construction |

## Implementation sketch

**Dialog shape.** Three sections in CPC's order. Only the middle one is new:

```
INPUT
  Image:            [active image ▾]  or  [Browse…]
  Channel:          [1 ▾]                 (hyperstacks only)
  ( ) Whole image   (•) Sweep in region only     ← enabled only when an ROI exists

ANALYSIS
  Engine:           [Classical ▾]
  Display:          [Threshold ▾]   From [10]  To [60]  Step [5]   [Suggest range]
  Also sweep:       [none ▾]        From [  ]  To [  ]  Step [ ]
  Choose value by:  [Knee and stability ▾]
  → 11 combinations, ~340 MB, ~18 s estimated

OUTPUT
  [x] Show grid     [x] Show results tables
  Save to:          [alongside input                    ] [Browse…]
```

The cost line updates live and turns into `ResourceGuard`'s refusal reason when the tree build is
too large. It must not imply that narrowing `from`/`to` reduces classical compute cost; narrowing the
range reduces the displayed combinations, not the computed tree.

**Macro options** — implement exactly the table in `02_CONTRACT.md`:

```
run("Object Segmentation Sweep",
    "engine=classical sweep=threshold from=10 to=60 step=5 pick=both hide_display");
```

`image`, `channel`, `engine`, `sweep`, `from`, `to`, `step`, `values`, `sweep2`, `from2`, `to2`,
`step2`, `values2`, `crop`, `pick`, `min_crop_fraction`, `stability_budget_ms`, `autosave`,
`hide_display`.

The option names remain `sweep`, `from`, `to` and `step` for contract compatibility, but dialog help
and saved reports call them displayed ranges/windows.

`values` and `from`/`to`/`step` are mutually exclusive — reject both together with a readable
message rather than silently preferring one.

**`hide_display` is a real headless path**, not a cosmetic flag: no grid, no windows, no dialog. It
routes straight to `SegSweep.run` and then to stage 14's auto-save.

**Entry routing**, following `CPC_.java`:

```java
public void run(String arg) {
    String options = Macro.getOptions();
    if (options != null && !options.trim().isEmpty()) {
        runFromMacro(options);      // parse → SegSweepParameters → SegSweep.run
    } else {
        runInteractive();           // dialog → SegSweep.run → grid → pick
    }
}
```

**Warnings must reach the user.** `SegSweepResult.warnings()` — crop below minimum fraction,
uncalibrated image, stability aborted, no knee found — go in the grid's status line and, in
`hide_display` mode, to `IJ.log` (the one place a log line is the right answer, because there is no
UI). Assert this; warnings that die in a return value are the same defect class as D2.

## Exit gate

1. `mvn test` passes.
2. Macro round-trip: a recorded interactive run replays to identical deterministic
   `sweep_results.csv` fields. Exclude `Duration_ms` from this comparison because the contract
   requires it to record real wall-clock time. Assert for a one-axis sweep, a two-axis sweep,
   an explicit `values` list, and a cropped sweep.
3. Every option in the `02_CONTRACT.md` table parses, and an unknown option produces a readable
   error rather than being ignored.
4. `values` together with `from`/`to`/`step` is rejected with a message naming both.
5. `hide_display` opens no window and shows no dialog — reuse stage 12's purity assertions.
6. `ResourceGuard` refusal appears in the dialog before Run is pressed, with its reason text.
7. **Suggest range** populates From/To/Step from the image histogram and the resulting display
   window runs.
8. Warnings from `SegSweepResult` appear in the grid status line, and in `IJ.log` under
   `hide_display`.
9. Pick-selected in the grid returns a combination and the settings token reflects it.
10. **The kill-criterion check, done explicitly and recorded in the commit message:** screenshot the
    Analysis section and write the two sentences that explain it. If either the screenshot needs
    scrolling or the explanation needs a third sentence, cut the second axis from v0.1.0 and say so.
11. Manual: install into Fiji, open on a real image, run a sweep, pick a value. Record what broke.
12. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`ParameterSweepEditor` temptation.** FLASH's 1,010-line editor does everything: presets, chips,
  step swapping, chain ribbons. Lifting it would make the dialog powerful and unadoptable. Use
  `SweepRangeEditor` (466) as reference at most, and prefer four plain fields.
- **`PipelineDialog` versus `GenericDialog`.** FLASH uses its own `PipelineDialog`; CPC uses its own
  `CPCDialog`. Follow **CPC**, since this repo inherits CPC's chassis — do not import FLASH's dialog
  framework.
- **Macro recording of a modal grid.** The recorder must capture the sweep parameters, not the
  post-hoc pick. Decide and document whether the picked value is recorded as an option or left to
  the user; recording the pick would make replays non-reproducible in a way that looks reproducible.
- **Channel selection on hyperstacks** is a classic source of off-by-one between the dialog's 1-based
  display and IJ's internal indexing. Test with a 3-channel hyperstack specifically.
- **Range wording matters.** Do not tell users the public range limits computation for classical.
  It limits what they inspect and report; `ResourceGuard` governs the tree build.
