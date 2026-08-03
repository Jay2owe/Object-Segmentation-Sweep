# Stage 07 — The pickers

Lift knee detection and IoU stability, and fix the five correctness defects that stand between a
preview hint and a citable recommendation.

## Why this stage exists

**This is the differentiator.** Everything else in the plugin has a rough equivalent somewhere —
Auto Threshold montages results, other tools sweep parameters. Nothing in the ImageJ ecosystem, and
nothing outside it that runs in Fiji, tells you which parameter value to use without hand-drawn
ground truth. That capability is why this plugin scored 28 in the candidate ranking and why it is
worth building at all.

It is also the stage where the parent's code is least fit for purpose. FLASH's versions are
interactive hints: they may silently decline, they may change their answer when you widen the sweep,
and they time out based on how fast your computer is. Each of those is fine for a hint and fatal for
a recommendation that ends up in a methods section. **Five of the ledger's blocking defects live in
these two files.**

## Prerequisites

- `03_parameter-model` complete.

May run in parallel with stages 05, 06 and 08.

## Read first

- `docs/segsweep-build/00_overview.md` — house rules 4 and 5 especially
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — defects **D1, D2, D3, D4, D5**, and the "Choosing a value" reasoning
- `../../../ImageJ Plugins/Object Segmentation Sweep/00_CASE.md` — the "Risks" section on "objective is a strong claim"
- Parent files under
  `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\analysis\`:
  - `KneeDetector.java` (195) — **read all of it.** `findKneeIndex` 17-82, normalisation 37-53,
    flatness guard 64-66, `findPlateauRange` 84-126, `steepestTransitionKnee` 147-162
  - `IouStability.java` (286) — **read all of it.** Time budget 20, `findMostStable` 25-36,
    `score` 45-113, `Topology.from` 156-205 (note the two-axis guard at 182),
    `neighboursOf`/`collectNeighbours` 207-247
  - `LabelIou.java` (99), `HistogramShapeStability.java` (279)
- Parent test: `KneeDetectorTest` if present; there is no `IouStabilityTest` — that absence is part
  of why D1 survived

## Scope

- Lift all four classes into `segsweep.sweep.analysis`.
- **Fix D1** — boundary bias in `IouStability`.
- **Fix D2** — the hard-coded 5-second budget.
- **Fix D3** — the silent refusal above two axes.
- **Fix D4** — typed knee outcomes instead of bare `OptionalInt.empty()`.
- **Fix D5** — knee reported in parameter units with its range attached.
- Write a `PickResult` type that carries both criteria, their agreement, and their provenance.
- Write the five acceptance tests, one per defect. **These tests are the deliverable as much as the
  code is** — they are what stops the defects returning.
- Keep `HistogramShapeStability` compiled and tested but unexposed (open question in
  `00_overview.md`).

## Out of scope

- Anything that runs a sweep — stages 06 and 08. These classes take a finished `List<ParameterCombo>`
  and `List<ImagePlus>` and score them.
- Badging picks in the grid — stage 10.
- The randomisation null model that would let the plugin say whether a knee differs from chance —
  **v0.2.0**, explicitly. Note it in the class Javadoc as the known limitation.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/analysis/KneeDetector.java` | NEW | Lift + D4, D5 |
| `src/main/java/segsweep/sweep/analysis/KneeOutcome.java` | **NEW** | The D4 fix |
| `src/main/java/segsweep/sweep/analysis/IouStability.java` | NEW | Lift + D1, D2, D3 |
| `src/main/java/segsweep/sweep/analysis/StabilityOutcome.java` | **NEW** | Typed refusals |
| `src/main/java/segsweep/sweep/analysis/LabelIou.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/analysis/HistogramShapeStability.java` | NEW | Lift, unexposed |
| `src/main/java/segsweep/sweep/analysis/PickResult.java` | **NEW** | Both criteria + agreement |
| `src/test/java/segsweep/sweep/analysis/*Test.java` | NEW | Five acceptance tests + unit tests |

## Implementation sketch

### D4 — typed knee outcomes

The parent returns `OptionalInt.empty()` for at least four distinct situations
(`KneeDetector.java:20, 29, 40, 65`), and one of them — a curve that is flat throughout — is a
*useful answer* meaning "any value in this range works":

```java
public final class KneeOutcome {
    public enum Kind { KNEE_AT, ALL_PLATEAU, TOO_FEW_POINTS, NO_BEND, DEGENERATE_RANGE }

    public Kind kind();
    public int index();              // valid only for KNEE_AT
    public double parameterValue();  // D5 — the knee in the units of the swept parameter
    public double rangeMin();        // D5 — the range it was computed over
    public double rangeMax();
    public double step();
    public String explanation();     // human-readable, for the UI and the report
}
```

Map the parent's early returns: line 20 → `DEGENERATE_RANGE`; line 29 (`points.size() < 4`) →
`TOO_FEW_POINTS`; line 40 (zero span) → `DEGENERATE_RANGE`; line 65 (`maxDifference - minDifference
< FLAT_DIFFERENCE_RANGE`) → `NO_BEND`. `findPlateauRange` returning non-null across the whole sweep
→ `ALL_PLATEAU`.

### D5 — range-relative normalisation

`KneeDetector.java:37-53` normalises x and y to the sweep's own min and max. The knee is therefore a
function of the range the user happened to type: widen the sweep and the knee moves. The algorithm
itself is fine — the reporting is not.

**Do not try to make the index range-invariant; it cannot be.** Instead:

1. Always report `parameterValue()` alongside `index()`, converted from the normalised position back
   into the swept parameter's units.
2. Always carry `rangeMin`, `rangeMax` and `step` on the outcome.
3. Add a comparison guard used by stage 13's batch aggregation:

```java
/** Two knees are comparable only when computed over the same range and step. */
public static boolean comparable(KneeOutcome a, KneeOutcome b);
```

The acceptance test asserts that the reported **parameter value** is stable when the range is
widened symmetrically, and documents that the **index** is not. That documented instability is the
honest position; hiding it is not.

### D1 — boundary bias

`IouStability.score` (`IouStability.java:66-111`) computes, for every combination, the mean IoU to
its existing neighbours. In a 1-D sweep interior points have two neighbours and the endpoints have
one. Under any monotone drift in IoU, the endpoints are systematically favoured or penalised — so
**the "most stable" pick changes when the range is extended by one step**, which destroys the exact
reproducibility the feature exists to provide.

Fix: only combinations with a full neighbour complement are eligible.

```java
// In Topology: a combination is eligible when it has the maximum possible
// neighbour count for its position in the lattice — i.e. it is interior on
// every varying axis.
boolean isInterior(int index);
```

```java
public final class StabilityOutcome {
    public enum Kind { STABLE_AT, NO_ELIGIBLE_COMBINATIONS, TOO_FEW_AXES, TOO_MANY_AXES, ABORTED }
    public Kind kind();
    public int index();
    public double meanNeighbourIou();
    public int eligibleCount();      // how many combinations were scored
    public String explanation();
}
```

A 1-D sweep of three values has exactly one interior point, which is a degenerate but honest answer;
below that, return `NO_ELIGIBLE_COMBINATIONS` and say the sweep is too short. **Surface eligibility
in the results table** (`Stability_Eligible`, per `02_CONTRACT.md`) so the user can see which cells
were even in the running.

### D2 — the time budget

`IouStability.java:20` hard-codes five seconds and, on expiry, logs to the ImageJ log window and
returns empty (lines 28-31, 77-79). The picker therefore depends on machine speed: the same sweep
answers on a fast box and stays silent on a slow one.

```java
public static StabilityOutcome findMostStable(List<ParameterCombo> combos,
                                              List<ImagePlus> labels,
                                              long budgetNanos);   // 0 == unlimited
```

Default to unlimited in the headless and API paths; the dialog may pass a budget. On expiry return
`Kind.ABORTED` **on the outcome object** — never `IJ.log`, per house rule 7. Exposed as the
`stability_budget_ms` macro option, default `0`.

### D3 — more than two axes

`Topology.from` returns `null` — and therefore silence — when `axes.size() > 2`
(`IouStability.java:182`). The neighbour recursion at lines 217-247 is already N-dimensional; only
the guard blocks it. v0.1.0's UI caps at two axes anyway, so **the fix here is to make the refusal
explicit** (`Kind.TOO_MANY_AXES` with an explanation) rather than to generalise. Leave a Javadoc note
that removing the guard is expected to work.

### PickResult

```java
public final class PickResult {
    public KneeOutcome knee();
    public StabilityOutcome stability();
    public boolean criteriaAgree();       // both resolved to the same combination
    public SweepProvenance provenance();
}
```

**Neither criterion overrides the other.** When they disagree, both are reported and `criteriaAgree`
is false; the UI shows both badges and the results table records both. Arbitrating between them
would be inventing a confidence the methods do not have.

## Exit gate

1. `mvn test` passes.
2. `KneeOutcomeTypedTest` — `ALL_PLATEAU`, `TOO_FEW_POINTS`, `NO_BEND` and `DEGENERATE_RANGE` are
   each produced by a purpose-built curve and are all distinguishable from `KNEE_AT`.
3. `KneeRangeInvarianceTest` — a synthetic count curve with a knee at threshold 32 reports
   `parameterValue() == 32` when swept 10–60 step 5 **and** when swept 5–80 step 5; the test also
   asserts the returned `index()` differs between the two, documenting D5 rather than hiding it.
4. `IouStabilityBoundaryTest` — a 1-D sweep of 7 values and the same sweep extended to 9 values
   (one extra step at each end) return **the same picked parameter value**. This is the test the
   parent would have failed.
5. `IouStabilityBudgetTest` — with `budgetNanos == 0` the picker always returns a resolved outcome;
   with a deliberately tiny budget it returns `Kind.ABORTED`; **no test triggers output in the
   ImageJ log** — assert by capturing `IJ.log` output and requiring it empty.
6. `IouStabilityThreeAxisTest` — a three-axis sweep returns `Kind.TOO_MANY_AXES` with a non-empty
   `explanation()`, not an empty optional.
7. `LabelIouTest` — identical label maps give 1.0; disjoint give 0.0; different dimensions throw
   `IllegalArgumentException` rather than returning a misleading number.
8. `PickResultTest` — a curve where knee and stability disagree yields `criteriaAgree() == false`
   with both outcomes populated, and neither silently wins.
9. `grep -rn "IJ.log" src/main/java/segsweep/sweep/analysis/` returns nothing.
10. Every class in this package has a Javadoc paragraph stating what the criterion assumes and its
    known limitation — including the absence of a null model, deferred to v0.2.0.

## Known risks

- **`IouStability` has no parent test.** That absence is why D1 survived in FLASH. Write the tests
  first here; do not port-then-test.
- **"Interior only" can empty the candidate set.** A 1-D sweep of three values leaves one eligible
  combination; two values leave none. Handle both explicitly and make the dialog's minimum sweep
  length reflect it (stage 12).
- **IoU cost.** Every adjacent pair is an O(voxels) comparison — roughly 2× the sweep cost for a 1-D
  sweep, 4× for 2-D. This is *why* the parent had a 5-second budget. Removing the budget without a
  progress signal will look like a hang; stage 10 must show progress during scoring, not only during
  dispatch.
- **The reviewer objection this stage cannot yet answer.** Without a null model, "the knee is real
  structure" and "the knee is where noise stopped being counted" are indistinguishable. Document it
  in the Javadoc and in stage 14's README rather than letting a user discover it in review.
- **Do not oversell in user-facing strings.** `explanation()` text ends up in the UI and the saved
  report. No "best", "optimal" or "correct" — say what was computed and over what.
