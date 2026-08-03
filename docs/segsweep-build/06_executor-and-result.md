# Stage 06 — Executor and result

Lift the parallel dispatch machinery and the per-combination result, extending the result to carry
provenance and calibrated density.

## Why this stage exists

This is the plumbing that turns a list of combinations into a list of finished label maps, with
cancellation, progress reporting and cache integration. It also owns the second half of the
provenance fix: `VariationResult` is where every downstream consumer — the grid, the pickers, the
tables, the saved report — reads its numbers from, so if crop and density are not on the result,
they are nowhere.

## Prerequisites

- `03_parameter-model`, `04_crop-and-token`, `05_cache-and-utils` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — defects **D6** and **D7**, and the "Sweep Results" table column list
- Parent files under `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\`:
  - `VariationExecutor.java` (741) — dispatch, cancellation, progress publication
  - `VariationResult.java` (698) — `success(...)`, `failure(...)`, accessors
  - `VariationStrategy.java` (10) — the interface
  - `strategy/SweepDispatchOrder.java` (86) — ordering so the informative middle renders first
  - `strategy/VariationStrategyChooser.java` (99)
  - `VariationCleanupCoordinator.java` (430), `VariationCleanupSupport.java` (163) — image lifecycle
- Parent tests: `VariationExecutorTest`, `VariationCleanupCoordinatorTestAccess`

## Scope

- Lift `VariationExecutor`, `VariationStrategy`, `SweepDispatchOrder`, `VariationStrategyChooser`,
  `VariationCleanupCoordinator`, `VariationCleanupSupport` into `segsweep.sweep`.
- Lift `VariationResult` and **extend it** with:
  - `SweepProvenance provenance()` — non-null, from stage 04 (**D6**)
  - `double objectsPerCalibratedVolume()` and `boolean calibrated()` (**D7**)
  - `Flags` — `EMPTY`, `SATURATED`, `TIMED_OUT`, `FAILED`, `TOO_MANY_LABELS`
- Trim `VariationStrategyChooser` to the classical strategy only; the StarDist and Cellpose branches
  are v0.2.0.
- Make progress reporting carry a typed record rather than log lines (house rule 7).
- Port `VariationExecutorTest`; add `CountDensityTest` and `CropProvenanceTest`.

## Out of scope

- The classical strategy implementation itself — stage 08. This stage defines the interface it
  implements and proves the executor works against a stub strategy.
- The pickers that consume the results — stage 07, running in parallel with this one.
- Any UI or progress bar — stage 10.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/VariationExecutor.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/VariationResult.java` | NEW | Lift + D6, D7 |
| `src/main/java/segsweep/sweep/VariationStrategy.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/SweepDispatchOrder.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/VariationStrategyChooser.java` | NEW | Lift, trimmed |
| `src/main/java/segsweep/sweep/VariationCleanupCoordinator.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/VariationCleanupSupport.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/SweepProgress.java` | **NEW** | Typed progress, replaces log lines |
| `src/test/java/segsweep/sweep/VariationExecutorTest.java` | NEW | Ported |
| `src/test/java/segsweep/sweep/CountDensityTest.java` | **NEW** | D7 acceptance |
| `src/test/java/segsweep/sweep/CropProvenanceTest.java` | **NEW** | D6 acceptance |

## Implementation sketch

The strategy interface, unchanged from the parent (`VariationStrategy.java`) except for the result
type it publishes:

```java
public interface VariationStrategy {
    void dispatch(ParameterSweep sweep,
                  Consumer<VariationResult> publisher,
                  BooleanSupplier cancelCheck) throws Exception;
}
```

`VariationResult` — the parent's `success`/`failure` factories gain provenance and density:

```java
public static VariationResult success(ParameterCombo combo,
                                      ImagePlus label,
                                      int objectCount,
                                      long durationMs,
                                      ResultsTable stats,
                                      SweepProvenance provenance);   // non-null — D6

public double objectsPerCalibratedVolume();   // D7
public boolean calibrated();                  // false → the density column is blank + flagged
public SweepProvenance provenance();
public EnumSet<Flag> flags();
```

**D7 — density.** The raw count is a count within the crop, so two crops of the same image give two
different curves. Density is what survives that:

```java
// voxelVolume comes from provenance; 0 when the image is uncalibrated
public double objectsPerCalibratedVolume() {
    double volume = provenance.voxelVolume() * croppedVoxelCount;
    return volume > 0 ? objectCount / volume : Double.NaN;
}
```

When the image is uncalibrated, return `NaN`, set `calibrated() == false`, and let the table render
a blank cell with a flag. **Do not silently substitute the raw count** — that is exactly the class of
substitution that makes a batch table look comparable when it is not.

`SweepProgress` replaces the parent's `IJ.log` reporting (house rule 7):

```java
public final class SweepProgress {
    public int completed();
    public int total();
    public int failed();
    public ParameterCombo current();
    public String message();
}
```

**Dispatch ordering.** `SweepDispatchOrder.order(sweep)` sorts combinations so the middle of the
range computes first — the user sees informative cells while the extremes are still running. Lift it
verbatim; stage 10's grid assumes results arrive out of index order and places them by combination
identity, not arrival.

**Cancellation.** The parent's pattern (`ClassicalSweep.java:154-172`) polls `task.get(50ms)` in a
loop so a cancel interrupts promptly rather than blocking on a long combination. Preserve it. Also
preserve the `ForkJoinPool` sizing at `effectiveParallelism` — cores minus one, never more.

**Image lifecycle.** `VariationCleanupCoordinator` (430 lines) exists because a sweep creates
hundreds of `ImagePlus` instances and IJ1 will happily leak every one. Lift it as-is and do not
simplify it; the `closeIfOwned(image, firstOwner, secondOwner)` pattern at `ClassicalSweep.java:181`
looks redundant and is not — `CropSpec.apply` sometimes returns its input.

## Exit gate

1. `mvn test` passes; `VariationExecutorTest` green.
2. A stub strategy that returns a fixed label map drives a 3×4 sweep to completion, publishing 12
   results, each with non-null provenance.
3. `CropProvenanceTest`: results from a cropped sweep carry the crop bounds and a `cropFraction`
   matching the crop; results from a full-image sweep carry `cropFraction == 1.0`. **A
   `VariationResult` cannot be constructed without provenance** — assert by compilation, and say so
   in the test.
4. `CountDensityTest`: the same synthetic objects at the same threshold, swept once on the full
   image and once on a half-image crop, give **different raw counts and equal density** within
   tolerance; an uncalibrated image gives `NaN` density and `calibrated() == false`.
5. Cancellation: a sweep of 20 slow combinations cancelled after 3 stops within 200 ms and leaks no
   open `ImagePlus` — assert via `WindowManager.getImageCount()` and the cleanup coordinator's own
   accounting.
6. Progress: every published `SweepProgress` has monotonically non-decreasing `completed()`, and
   nothing in the executor calls `IJ.log`.
7. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`VariationResult` is 698 lines** for what sounds like a value object. Read it before assuming
  the extension is a two-field addition — it likely carries display state the grid depends on.
- **Adding a required constructor argument breaks every call site** in the ported tests. That is
  intended (see exit gate 3), but budget time for it.
- **ForkJoinPool and `ImagePlus`.** IJ1 has thread-affinity assumptions in places. The parent gets
  away with it because the strategy only touches processors, not windows. Keep it that way: nothing
  inside a dispatched task may call `WindowManager` or `IJ.getImage()`.
- **Density units.** `Calibration.pixelDepth` is 1.0 on a 2D image, so the "volume" of a 2D image is
  really an area. Decide and document whether the column is per-volume or per-area for 2D input;
  reporting per-mm³ for a single slice would be wrong and nobody would notice.
