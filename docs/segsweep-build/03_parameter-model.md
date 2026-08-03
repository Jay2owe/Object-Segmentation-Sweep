# Stage 03 — Parameter model and enumeration

Lift FLASH's parameter model: named axes, value lists, Cartesian enumeration into combinations, and
canonical serialisation.

## Why this stage exists

Everything downstream is expressed in these types. The executor dispatches `ParameterCombo`s, the
cache keys them by canonical JSON, the pickers navigate them as a lattice, and the grid renders one
cell per combination. All of it lifts from FLASH with **zero non-`ij` imports** — verified by grep,
not assumed — so this stage is mostly disciplined copying, and the discipline is the point.

## Prerequisites

- `01_repo-scaffold` complete.

May run in parallel with stages 02 and 09.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the transitive dependency table
- Parent files, all under `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\`:
  - `ParameterSweep.java` (305 lines) — `Method` enum at 11-30, constructors 43-91, accessors
    93-166, `cellCount()` 167, `combos()` 182, `toCanonicalJson()` 193
  - `ParameterCombo.java` (159), `ParameterId.java` (112 — `stableKey()` 37, `displayLabel()` 59),
    `ParameterKey.java` (15), `ParameterValueList.java` (127), `ParameterLabels.java` (77)
  - `CanonicalJson.java` (57), `CanonicalScale.java` (182)
- Parent tests to port, under `Experiments\FLASH\src\test\java\flash\pipeline\ui\variations\`:
  `ParameterSweepTest`, `ParameterComboTest`, `ParameterKeyCompatibilityTest`,
  `CanonicalScaleValueTest`, `StepsModeSweepBuildTest`, `PresetEnumeratorTest`

## Scope

- Copy the eight classes above into `segsweep.sweep`, changing package declarations only.
- **Trim `ParameterId`** to the identifiers v0.1.0 can actually sweep: `THRESHOLD`, `MIN_SIZE`,
  `MAX_SIZE`. Leave the StarDist and Cellpose identifiers (`PROB_THRESH`, `NMS_THRESH`, `DIAMETER`,
  `FLOW_THRESHOLD`, `CELLPROB_THRESHOLD`, `MODEL`, `AREA_MIN`, `AREA_MAX`, `QUALITY_MIN`,
  `INTENSITY_MIN`, `LINKING_MAX`, `GAP_CLOSING_MAX`, `FRAME_GAP`) **in place but marked
  `@since 0.2.0` and unreachable from the v0.1.0 UI** — removing and re-adding them would churn
  `stableKey()`, which the settings token and every cache key depend on.
- **Trim `ParameterSweep.Method`** the same way: `CLASSICAL` is live; `STARDIST` and `CELLPOSE` stay
  declared for v0.2.0; **delete `FILTER`, `DECONVOLUTION` and `SPECTRAL`** — those belong to Macro
  Builder and FLASH respectively, per the overlap ledger in `00_CASE.md`, and will never be in this
  plugin.
- Drop the `MacroVariationSet` coupling from `ParameterSweep` (`hasMacroVariationSet()`,
  `macroVariations()`, and the `MACRO` parameter path). Macro variations are Macro Builder's
  territory.
- Port the six parent tests, adjusted for the trimmed enums.

## Out of scope

- `CropSpec`, `ResourceGuard`, the settings token — stage 04.
- `VariationCache`, `VariationExecutor`, `VariationResult` — stages 05 and 06.
- `RangeSuggester` — stage 08; it produces `ParameterValueList`s but needs a histogram.
- Anything that touches an `ImagePlus`. These are value types.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/ParameterSweep.java` | NEW | Lift; trim `Method`, drop macro coupling |
| `src/main/java/segsweep/sweep/ParameterCombo.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/ParameterId.java` | NEW | Lift; mark v0.2.0 IDs |
| `src/main/java/segsweep/sweep/ParameterKey.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/ParameterValueList.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/ParameterLabels.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/CanonicalJson.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/sweep/CanonicalScale.java` | NEW | Lift verbatim |
| `src/test/java/segsweep/sweep/*Test.java` | NEW | Six ported tests |

## Implementation sketch

The surface that must survive the lift unchanged — later stages are written against it:

```java
public final class ParameterSweep {
    public enum Method { CLASSICAL, STARDIST, CELLPOSE }   // FILTER/DECONVOLUTION/SPECTRAL deleted

    public Method method();
    public Map<ParameterKey, ParameterValueList> valueLists();
    public CropSpec cropSpec();                 // stage 04 supplies the type
    public String channelName();
    public String sourceImageHash();            // see the D10 note below
    public String cacheNamespace();
    public List<ParameterKey> parameterKeys();
    public List<ParameterId> parameterIds();
    public long cellCount();
    public List<ParameterCombo> combos();       // the Cartesian product, ordered
    public String toCanonicalJson();
}
```

`ParameterId.stableKey()` is the string that ends up in cache keys and in the settings token
(`ParameterId.java:37-57`). **These strings are a persistence format — do not rename them**, even
where the name reads oddly:

```java
THRESHOLD  -> "threshold"
MIN_SIZE   -> "min_size"
MAX_SIZE   -> "max_size"
```

`CanonicalScale` exists so that `10`, `10.0` and `1e1` produce the same canonical text, which is
what makes cache keys stable across a dialog round-trip. Lift it verbatim; its test
(`CanonicalScaleValueTest`) is the specification.

**A note on `sourceImageHash()`, for stage 05.** The parent lets the *caller* supply this string and
never validates it against pixels — that is defect **D10**, and `VariationDiskCachePoisoningTest`
exists in FLASH because it has already caused a real bug. Leave the accessor in place at this stage,
but add a Javadoc note that stage 05 will make it internally computed. Do not design around
caller-supplied hashes in any new code.

**Ordering matters and is tested.** `combos()` must produce a deterministic order — the parent sorts
parameter keys through a comparator at `ParameterSweep.java:252`. Stage 07's lattice topology and
stage 10's grid layout both assume index *i* means the same combination every run.

## Exit gate

1. `mvn test` passes with all six ported tests green.
2. `grep -rn "^import" src/main/java/segsweep/sweep/` shows only `java.*` imports — **no `ij`, no
   anything else**, since these are pure value types.
3. No reference anywhere to `MacroVariationSet`, `MacroToken`, `FILTER`, `DECONVOLUTION` or
   `SPECTRAL` survives.
4. A round-trip test: build a two-axis sweep, `toCanonicalJson()`, and assert the string is byte-
   identical across two JVM runs with the same inputs — including for values entered as `10`,
   `10.0` and `1e1`.
5. `combos()` for a 3×4 sweep returns 12 combinations in a stable, documented order, asserted by
   test.
6. `ParameterId.stableKey()` values are asserted against literal strings in a test, so a future
   rename breaks the build rather than silently invalidating every cached result and settings token
   in the field.

## Known risks

- **`ParameterKey` versus `ParameterId`.** The parent has both, and `ParameterKeyCompatibilityTest`
  exists because the relationship is subtle — `ParameterKey` is the map key and carries more than
  the enum. Port that test first and let it tell you what the invariant is before touching either
  class.
- **Trimming enums breaks the ported tests.** Expect `ParameterSweepTest` to reference deleted
  `Method` values. Adjust the test rather than restoring the value; the deletions are deliberate
  boundary decisions from `00_CASE.md`.
- **`CanonicalScale` looks trivial and is not.** 182 lines of number-formatting edge cases. Resist
  simplifying it — every simplification is a future cache-key instability that will present as
  "the sweep re-ran everything for no reason".
