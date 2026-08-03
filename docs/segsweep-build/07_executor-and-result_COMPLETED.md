# Stage 07 - Executor and result

Lift the v0.2-shaped dispatch machinery and the per-combination result type, extending results to
carry provenance, calibrated density and lazy label-map access.

## Why this stage exists

Classical v0.1 builds one component tree and queries it, so it no longer needs a ForkJoinPool task
per displayed combination. The executor still belongs in v0.1 because it defines the engine shape
needed for StarDist and Cellpose in v0.2: precompute an expensive intermediate once, realise/query
cheaply across parameter values, publish progress and cancellation through typed records.

`VariationResult` is where every downstream consumer - the grid, pickers, tables and saved report -
reads numbers from. If crop, density and lazy label access are not on the result, they are nowhere.

## Prerequisites

- `05_parameter-model`, `06_crop-and-token` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `docs/segsweep-build/03_component-tree.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - active defects **D6** and
  **D7**, and the "Sweep Results" table column list
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - `VariationExecutor`
  remains for the v0.2 engine shape
- Parent files under `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\`:
  - `VariationExecutor.java` (741) - dispatch, cancellation, progress publication
  - `VariationResult.java` (698) - `success(...)`, `failure(...)`, accessors
  - `VariationStrategy.java` (10)
  - `strategy/SweepDispatchOrder.java` (86)
  - `strategy/VariationStrategyChooser.java` (99)
  - `VariationCleanupCoordinator.java` (430), `VariationCleanupSupport.java` (163)
- Parent tests: `VariationExecutorTest`, `VariationCleanupCoordinatorTestAccess`

## Scope

- Lift `VariationExecutor`, `VariationStrategy`, `SweepDispatchOrder`,
  `VariationStrategyChooser`, `VariationCleanupCoordinator`, `VariationCleanupSupport` into
  `segsweep.sweep`.
- Trim `VariationStrategyChooser` to classical for v0.1, keeping the strategy interface compatible
  with v0.2 engines.
- Lift `VariationResult` and extend it with:
  - `SweepProvenance provenance()` - non-null (D6)
  - `double objectsPerCalibratedVolume()` and `boolean calibrated()` (D7)
  - `LazyLabelMap labelMap()` or equivalent provider from the component-tree result
  - `Flags` - `EMPTY`, `SATURATED`, `TIMED_OUT`, `FAILED`, `TOO_MANY_LABELS`, `UNCALIBRATED`
- Make progress reporting typed rather than log-line based.
- Port `VariationExecutorTest`; add `CountDensityTest`, `CropProvenanceTest` and lazy-label tests.

## Out of scope

- The classical component-tree strategy implementation - stage 09.
- Pickers - stage 08.
- UI progress bars - stage 11.
- Any per-combination retained label-stack cache. D9/D10/D11 were properties of the removed cache.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/VariationExecutor.java` | NEW | Lift, v0.2-shaped dispatch |
| `src/main/java/segsweep/sweep/VariationResult.java` | NEW | Lift + D6, D7 + lazy label access |
| `src/main/java/segsweep/sweep/VariationStrategy.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/SweepDispatchOrder.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/VariationStrategyChooser.java` | NEW | Lift, trimmed |
| `src/main/java/segsweep/sweep/VariationCleanupCoordinator.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/VariationCleanupSupport.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/SweepProgress.java` | NEW | Typed progress |
| `src/test/java/segsweep/sweep/VariationExecutorTest.java` | NEW | Ported |
| `src/test/java/segsweep/sweep/CountDensityTest.java` | NEW | D7 acceptance |
| `src/test/java/segsweep/sweep/CropProvenanceTest.java` | NEW | D6 acceptance |

## Implementation sketch

Keep the strategy surface small:

```java
public interface VariationStrategy {
    void dispatch(ParameterSweep displayWindow,
                  Consumer<VariationResult> publisher,
                  Consumer<SweepProgress> progress,
                  BooleanSupplier cancelCheck) throws Exception;
}
```

For classical, stage 09 can implement this by building one tree, querying all displayed
combinations, and publishing results. For v0.2 engines, the executor can still own task dispatch and
intermediate reuse.

`VariationResult` factories:

```java
public static VariationResult success(ParameterCombo combo,
                                      LazyLabelMap labelMap,
                                      int objectCount,
                                      long durationMs,
                                      ResultsTable stats,
                                      SweepProvenance provenance);

public double objectsPerCalibratedVolume();
public boolean calibrated();
public SweepProvenance provenance();
public LazyLabelMap labelMap();
public EnumSet<Flag> flags();
```

D7 density:

```java
double volume = provenance.voxelVolume() * croppedVoxelCount;
return volume > 0 ? objectCount / volume : Double.NaN;
```

When the image is uncalibrated, return `NaN`, set `calibrated() == false`, and flag the result. Do
not silently substitute raw counts into the density column.

`SweepProgress`:

```java
public final class SweepProgress {
    public int completed();
    public int total();
    public int failed();
    public ParameterCombo current();
    public String phase();      // e.g. "building tree", "querying", "scoring", "materialising"
    public String message();
}
```

## Exit gate

1. `mvn test` passes; `VariationExecutorTest` green against a stub strategy.
2. A stub strategy drives a 3x4 display window to completion, publishing 12 results, each with
   non-null provenance and a lazy label provider.
3. `CropProvenanceTest`: cropped and full-image runs carry correct crop bounds and fractions. A
   `VariationResult` cannot be constructed without provenance.
4. `CountDensityTest`: calibrated density is computed from crop volume; uncalibrated input gives
   `NaN`, `calibrated() == false` and an `UNCALIBRATED` flag.
5. Cancellation stops promptly and leaks no open `ImagePlus`.
6. Progress records are monotonic and no executor code calls `IJ.log`.
7. Grep confirms no production reference to the removed cache, source-hash plumbing or cache
   poisoning tests.
8. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`VariationResult` is large in the parent.** Read it before assuming the extension is a small
  value-object change.
- **ForkJoinPool and `ImagePlus`.** Nothing inside a dispatched task may call `WindowManager` or
  `IJ.getImage()`.
- **Density units.** Decide whether 2D input reports area-normalised counts separately from 3D
  volume-normalised counts, and make the table label honest.
- **Lazy providers need ownership rules.** A materialised label map must have one clear owner and be
  releasable by the grid cleanup path.
