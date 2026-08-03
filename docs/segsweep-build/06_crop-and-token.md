# Stage 06 - Crop, provenance, token and resource guard

Lift the crop model, lift the reproducible settings token, write `SweepProvenance`, and adapt
`ResourceGuard` to the component-tree memory model.

## Why this stage exists

A sweep produces a recommended setting, and a recommendation is worthless unless you know what it was
conditional on: which region of the image was used, which display window was reviewed, and which
criteria were reported. This stage carries the active D6/D7 consequences forward while removing the
old cache-byte duty that `04_SWEEP_ENGINE.md` made obsolete.

## Prerequisites

- `05_parameter-model` complete.

## Read first

- `docs/segsweep-build/00_overview.md` - house rule 5 and defect-ledger consequences
- `docs/segsweep-build/03_component-tree.md` - tree memory model and morphology attributes
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - defects **D6** and **D7**,
  Outputs, Java API and macro option tables
- `../../../ImageJ Plugins/Object Segmentation Sweep/03_BUILD_PLAN.md` - updated v0.1 scope
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - `ResourceGuard` governs
  component-tree memory
- Parent files under `Experiments\FLASH\src\main\java\flash\pipeline\`:
  - `ui/variations/CropSpec.java` (170), `ui/variations/CustomCropPicker.java` (288)
  - `ui/variations/ResourceGuard.java` (97)
  - `segmentation/SegmentationMethod.java` (290)
  - `segmentation/SegmentationTokenCodec.java` (50), `SegmentationTokenParser.java` (405),
    `MorphPredicate.java` (117)
- Parent tests: `CropSpecTest`, `CropSpecMultiChannelTest`, `ResourceGuardTest`,
  `SegmentationTokenParserTest`, `SegmentationTokenParserNestedBaseTest`

## Scope

- Lift `CropSpec` and `CustomCropPicker` into `segsweep.sweep`.
- Lift `ResourceGuard` into `segsweep.sweep` and adapt it to estimate:
  - crop voxels and source bytes
  - component-tree node, parent, child and union-find arrays
  - incremental attribute arrays
  - one lazily materialised 16-bit label map for drawing/output
- Lift token classes into `segsweep.token`: `SegmentationMethod`, `SegmentationTokenCodec`,
  `SegmentationTokenParser`, `MorphPredicate`.
- Map token-level `MorphPredicate` to stage 03 tree predicates for v0.1 morphology axes.
- Write `SweepProvenance` - new, active D6 fix.
- Write `SettingsTokenWriter` - produces `picked_settings.txt`: token plus provenance.
- Port the five parent tests, plus provenance/resource tests.

## Out of scope

- Actually populating provenance from a running sweep - stage 07 puts it in `VariationResult`, stage
  12 puts it in the result bundle, stage 14 writes the file.
- Density-per-volume computation (D7) - stage 07, where the object count lives.
- Any retained per-combination label-stack cache. D9/D10/D11 are eliminated by the component tree;
  do not lift the old cache or its acceptance-test work.
- `RangeSuggester` - stage 09.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/CropSpec.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/CustomCropPicker.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/ResourceGuard.java` | NEW | Lift + component-tree memory estimate |
| `src/main/java/segsweep/sweep/SweepProvenance.java` | NEW | The D6 fix |
| `src/main/java/segsweep/token/SegmentationMethod.java` | NEW | Lift, trimmed |
| `src/main/java/segsweep/token/SegmentationTokenCodec.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/token/SegmentationTokenParser.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/token/MorphPredicate.java` | NEW | Lift + adapter to tree predicates |
| `src/main/java/segsweep/token/SettingsTokenWriter.java` | NEW | `picked_settings.txt` |
| `src/test/java/segsweep/sweep/*Test.java`, `src/test/java/segsweep/token/*Test.java` | NEW | Ported + new |

## Implementation sketch

`SweepProvenance` is immutable, serialisable and carried by every number the plugin reports:

```java
public final class SweepProvenance {
    private final CropSpec crop;
    private final int fullWidth, fullHeight, fullDepth;
    private final Map<ParameterId, ParameterValueList> displayedRanges;
    private final String calibrationUnit;
    private final double voxelVolume;

    public double cropFraction();
    public boolean belowMinimumFraction(double minimum);
    public boolean comparableWith(SweepProvenance other);
    public String toCanonicalJson();
    public static SweepProvenance fromCanonicalJson(String json);
}
```

`displayedRanges` is named deliberately. Classical computation covers the whole tree; the public
range records what the user displayed and reviewed.

`SettingsTokenWriter` output remains human-readable and machine-parseable:

```text
# Object Segmentation Sweep 0.1.0
# Written 2026-08-01T14:22:11Z

settings    classical;thresh=32;minSize=50;maxSize=2147483647
engine      Classical
criterion   knee
knee        32 (threshold units), displayed over 10-60 step 5
stability   28 (mean neighbour IoU 0.91)
agreement   criteria disagree

image       Example-001.tif
channel     1
region      x=512 y=512 w=512 h=512 (25.0% of image)
calibration 0.325 x 0.325 x 1.000 micron
```

`ResourceGuard` estimate:

```java
long treeBytes = cropVoxels * bytesPerVoxelForTreeNodesAndUnionFind;
long attributes = estimatedNodeCount * bytesPerNodeAttributes;
long oneLazyLabelMap = cropVoxels * 2L;
long total = sourceBytes + treeBytes + attributes + oneLazyLabelMap;
```

Do not multiply label-map memory by combination count. That was the old cache architecture and is
explicitly removed.

## Exit gate

1. `mvn test` passes with the five ported tests green.
2. `SweepProvenanceTest` asserts full-image fraction `1.0`, a 512x512 crop of a 1024x1024 image is
   `0.25`, `belowMinimumFraction(0.05)` fires correctly at the boundary, and `comparableWith`
   returns false for different crops or displayed ranges.
3. `SweepProvenance` round-trips through canonical JSON with byte-identical output.
4. `SettingsTokenWriterTest` asserts the `settings` line round-trips through
   `SegmentationTokenParser.parseLenient`, and that the `region` and displayed-range lines are
   present for full-image and cropped sweeps.
5. Tokens naming v0.2 engines parse and report the engine rather than throwing, but v0.1 execution
   declines them with a typed reason.
6. `ResourceGuard` refuses a synthetic full-stack tree build that exceeds memory and permits a
   small cropped tree. The refusal carries a readable reason string.
7. `rg -n "cacheBudgetBytes|sourceImageHash|cacheNamespace|shared mutable.*cache" src/main/java src/test/java`
   returns nothing.
8. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`CropSpec.apply` ownership semantics.** Preserve the parent contract exactly; sometimes it
  returns the input unchanged and sometimes a new image.
- **Hyperstack crop edge cases.** Port `CropSpecMultiChannelTest` before writing against `CropSpec`.
- **Do not make provenance nullable.** Nullable provenance recreates D6.
- **Timestamp in `SettingsTokenWriter`.** Keep it on a `#` comment line and exclude it from equality
  tests.
- **Tree memory estimates will need calibration.** Underestimating is worse than refusing early;
  document constants and adjust after stage 03 measurements.
