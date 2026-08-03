# Stage 05 - Parameter model and display-window enumeration

Lift FLASH's parameter model: named axes, value lists, Cartesian enumeration into combinations, and
canonical serialisation. In the component-tree architecture these combinations describe the public
display window, not the amount of classical segmentation work to perform.

## Why this stage exists

Everything downstream is expressed in these types. The tree computes the full classical space once,
but the dialog, macro options, grid, pick tables and settings token still need an ordered set of
`ParameterCombo`s to display and report. All core value types lift from FLASH with zero non-`ij`
imports, so this stage is mostly disciplined copying with the v0.1 axis set updated to include
morphology.

## Prerequisites

- `04_component-tree-equivalence` complete.

May run in parallel with stage 10 after the tree gate.

## Read first

- `docs/segsweep-build/00_overview.md`
- `docs/segsweep-build/03_component-tree.md`
- `docs/segsweep-build/04_component-tree-equivalence.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - transitive dependency table
- `../../../ImageJ Plugins/Object Segmentation Sweep/03_BUILD_PLAN.md` - v0.1 morphology scope
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - public range is a
  display window
- Parent files, all under `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\`:
  - `ParameterSweep.java` (305 lines) - constructors, accessors, `cellCount()`, `combos()`,
    `toCanonicalJson()`
  - `ParameterCombo.java`, `ParameterId.java`, `ParameterKey.java`, `ParameterValueList.java`,
    `ParameterLabels.java`
  - `CanonicalJson.java`, `CanonicalScale.java`
- Parent tests to port: `ParameterSweepTest`, `ParameterComboTest`,
  `ParameterKeyCompatibilityTest`, `CanonicalScaleValueTest`, `StepsModeSweepBuildTest`,
  `PresetEnumeratorTest`

## Scope

- Copy the eight core classes into `segsweep.sweep`, changing package declarations and trimming only
  where the plugin boundary requires it.
- `ParameterId` v0.1 live identifiers:
  `THRESHOLD`, `MIN_SIZE`, `MAX_SIZE`, `VOLUME`, `MEAN_INTENSITY`, `MAX_INTENSITY`,
  `ELONGATION`, `SURFACE_AREA`, `SPHERICITY`, `COMPACTNESS`, `FERET_DIAMETER_MAX`.
- StarDist and Cellpose identifiers stay declared for v0.2.0 and unreachable from the v0.1 UI.
- Delete `FILTER`, `DECONVOLUTION` and `SPECTRAL`; those belong to Macro Builder and FLASH.
- Drop `MacroVariationSet` coupling from `ParameterSweep`.
- Preserve stable canonical strings for every identifier; new morphology keys become a persistence
  format immediately.
- Add an explicit model/Javadoc note that `from`/`to`/`step` and explicit value lists select what
  is displayed and reported. Classical computes the whole tree regardless.
- Port the six parent tests, adjusted for the trimmed enums and added morphology identifiers.

## Out of scope

- `CropSpec`, `ResourceGuard`, provenance and the settings token - stage 06.
- `VariationExecutor` and `VariationResult` - stage 07.
- `RangeSuggester` - stage 09; it produces display-window `ParameterValueList`s from histograms.
- Anything that touches an `ImagePlus`. These are value types.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/ParameterSweep.java` | NEW | Lift; display-window semantics |
| `src/main/java/segsweep/sweep/ParameterCombo.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/ParameterId.java` | NEW | Lift; v0.1 morphology IDs, v0.2 engine IDs retained |
| `src/main/java/segsweep/sweep/ParameterKey.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/ParameterValueList.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/ParameterLabels.java` | NEW | Lift + morphology display labels |
| `src/main/java/segsweep/sweep/CanonicalJson.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/CanonicalScale.java` | NEW | Lift verbatim |
| `src/test/java/segsweep/sweep/*Test.java` | NEW | Six ported tests plus morphology-key assertions |

## Implementation sketch

Surface expected by later stages:

```java
public final class ParameterSweep {
    public enum Method { CLASSICAL, STARDIST, CELLPOSE }

    public Method method();
    public Map<ParameterKey, ParameterValueList> valueLists();
    public CropSpec cropSpec();                 // stage 06 supplies the type
    public String channelName();
    public List<ParameterKey> parameterKeys();
    public List<ParameterId> parameterIds();
    public long cellCount();
    public List<ParameterCombo> combos();       // ordered display combinations
    public String toCanonicalJson();
}
```

`ParameterId.stableKey()` values are persistence strings. Pin them with literal tests:

```java
THRESHOLD          -> "threshold"
MIN_SIZE           -> "min_size"
MAX_SIZE           -> "max_size"
VOLUME             -> "volume"
MEAN_INTENSITY     -> "mean_intensity"
MAX_INTENSITY      -> "max_intensity"
ELONGATION         -> "elongation"
SURFACE_AREA       -> "surface_area"
SPHERICITY         -> "sphericity"
COMPACTNESS        -> "compactness"
FERET_DIAMETER_MAX -> "feret_diameter_max"
```

`CanonicalScale` exists so that `10`, `10.0` and `1e1` produce the same canonical text. Lift it
verbatim; its test is the specification.

Ordering matters. `combos()` must produce a deterministic order; stage 08's lattice topology and
stage 11's grid layout both assume index `i` means the same combination every run.

## Exit gate

1. `mvn test` passes with all six ported tests green.
2. Import scan over `src/main/java/segsweep/sweep/` for these value classes shows only `java.*`
   imports, except references to local `CropSpec` once stage 06 lands.
3. No reference anywhere to `MacroVariationSet`, `MacroToken`, `FILTER`, `DECONVOLUTION` or
   `SPECTRAL` survives.
4. A round-trip test builds a two-axis sweep, calls `toCanonicalJson()`, and asserts the string is
   byte-identical across two JVM runs with the same inputs.
5. `combos()` for a 3x4 sweep returns 12 combinations in a stable, documented order.
6. `ParameterId.stableKey()` values, including morphology keys, are asserted against literal
   strings.
7. Tests assert `from`/`to`/`step` are display-window values for classical, not instructions to
   recompute only that window.

## Known risks

- **`ParameterKey` versus `ParameterId`.** Port `ParameterKeyCompatibilityTest` before touching
  either class.
- **Trimming enums breaks parent tests.** Adjust the tests rather than restoring out-of-scope values.
- **Canonical formatting looks trivial and is not.** Do not simplify `CanonicalScale`; instability
  here breaks tokens and persisted reports.
- **Morphology IDs are now public.** Once shipped, their stable keys cannot be renamed without
  breaking macro and token compatibility.
