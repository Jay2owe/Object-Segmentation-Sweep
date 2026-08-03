# Stage 04 — Crop, provenance and the settings token

Lift the crop model and resource guard, lift the reproducible settings token, and write the
`SweepProvenance` record that fixes defect D6.

## Why this stage exists

This is where the plugin's actual product gets defined. A sweep produces a recommended setting, and
a recommendation is worthless unless you know what it was conditional on — which region of the image
it was computed on and over what range of values. FLASH never records either, because a human chose
the crop and remembers. Here, nobody remembers. `SweepProvenance` is the small class that turns a
preview hint into something a methods section can cite, and every later stage threads it through.

## Prerequisites

- `03_parameter-model` complete.

## Read first

- `docs/segsweep-build/00_overview.md` — house rule 5
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — defects **D6** and **D7**, and the Outputs section describing
  `picked_settings.txt`
- Parent files under `Experiments\FLASH\src\main\java\flash\pipeline\`:
  - `ui/variations/CropSpec.java` (170) — `full()`, `apply(ImagePlus)`, `toCanonicalJson()`
  - `ui/variations/CustomCropPicker.java` (288)
  - `ui/variations/ResourceGuard.java` (97)
  - `segmentation/SegmentationMethod.java` (290) — `Engine` enum at line 12; the accessor pattern
    throughout
  - `segmentation/SegmentationTokenCodec.java` (50), `SegmentationTokenParser.java` (405),
    `MorphPredicate.java` (117)
- Parent tests: `ui/variations/CropSpecTest`, `CropSpecMultiChannelTest`, `ResourceGuardTest`,
  `segmentation/SegmentationTokenParserTest`, `SegmentationTokenParserNestedBaseTest`

## Scope

- Lift `CropSpec` and `CustomCropPicker` into `segsweep.sweep`.
- Lift `ResourceGuard` into `segsweep.sweep`, and **extend it to account for the labeller's
  per-voxel `int` provisional-label array** measured in stage 02 — currently it only counts image
  bytes, and stage 02's Javadoc records the real figure.
- Lift the token classes into `segsweep.token`: `SegmentationMethod` (trimmed to `CLASSICAL`, with
  the other `Engine` values retained for v0.2.0 exactly as in stage 03), `SegmentationTokenCodec`,
  `SegmentationTokenParser`, `MorphPredicate`.
- **Write `SweepProvenance`** — new, the D6 fix.
- **Write `SettingsTokenWriter`** — produces the `picked_settings.txt` content: the token plus its
  provenance, in a format a human can read and the parser can round-trip.
- Port the five parent tests, plus new provenance tests.

## Out of scope

- Actually *populating* provenance from a running sweep — stage 06 puts it in `VariationResult`,
  stage 11 puts it in the result bundle, stage 13 writes the file.
- The crop-fraction **warning** in the dialog — stage 12. This stage supplies
  `belowMinimumFraction(double)`; the UI decides what to do about it.
- Density-per-volume computation (D7) — stage 06, where the object count lives.
- `RangeSuggester` — stage 08.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/CropSpec.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/CustomCropPicker.java` | NEW | Lift |
| `src/main/java/segsweep/sweep/ResourceGuard.java` | NEW | Lift + extend for labeller memory |
| `src/main/java/segsweep/sweep/SweepProvenance.java` | **NEW — written fresh** | The D6 fix |
| `src/main/java/segsweep/token/SegmentationMethod.java` | NEW | Lift, trimmed |
| `src/main/java/segsweep/token/SegmentationTokenCodec.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/token/SegmentationTokenParser.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/token/MorphPredicate.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/token/SettingsTokenWriter.java` | **NEW — written fresh** | `picked_settings.txt` |
| `src/test/java/segsweep/sweep/*Test.java`, `src/test/java/segsweep/token/*Test.java` | NEW | Ported + new |

## Implementation sketch

`SweepProvenance` — immutable, serialisable, and carried by every number the plugin reports:

```java
package segsweep.sweep;

public final class SweepProvenance {

    private final CropSpec crop;
    private final int fullWidth, fullHeight, fullDepth;
    private final Map<ParameterId, ParameterValueList> sweptRanges;
    private final String sourceImageHash;
    private final String calibrationUnit;   // empty when uncalibrated
    private final double voxelVolume;       // 0 when uncalibrated

    /** Fraction of the full image the sweep actually ran on. 1.0 for a full-image sweep. */
    public double cropFraction();

    /** True when the sweep ran on less than {@code minimum} of the image. */
    public boolean belowMinimumFraction(double minimum);

    /** True when this provenance and {@code other} used the same crop and the same ranges. */
    public boolean comparableWith(SweepProvenance other);

    public String toCanonicalJson();
    public static SweepProvenance fromCanonicalJson(String json);
}
```

`comparableWith` is the method that enforces house rule 5. Stage 07's knee reporting and stage 13's
per-folder aggregation both call it before putting two results side by side; when it returns false
they must say so rather than averaging.

`SettingsTokenWriter` output format — human-readable, machine-parseable, and the file that goes in a
methods section:

```
# Object Segmentation Sweep 0.1.0
# Written 2026-08-01T14:22:11Z

settings    classical;thresh=32;minSize=50;maxSize=2147483647
engine      Classical
criterion   knee
knee        32 (threshold units), computed over 10-60 step 5
stability   28 (mean neighbour IoU 0.91)
agreement   criteria disagree

image       Example-001.tif
channel     1
region      x=512 y=512 w=512 h=512 (25.0% of image)
calibration 0.325 x 0.325 x 1.000 micron
```

The `settings` line is `SegmentationTokenCodec`'s output, unchanged, so it round-trips through
`SegmentationTokenParser`. Everything else is provenance. **The `region` line is the whole point of
this stage** — without it, the token is a number with no conditions attached.

`ResourceGuard` extension. The parent estimates from image bytes only. Stage 02 established that the
labeller also allocates one `int` per voxel for provisional labels, on top of the image and the
output:

```java
// bytes per combination ≈ crop voxels × (input bpp + 4 provisional + 2 output)
long perCombination = cropVoxels * (bytesPerPixel + 4L + 2L);
long total = perCombination * Math.min(parallelism, comboCount)
           + cacheBudgetBytes;   // stage 05 owns the cache budget
```

Use the figure recorded in stage 02's commit message rather than re-deriving it.

`SegmentationMethod` trimming follows stage 03's rule exactly: `Engine.CLASSICAL` is live;
`ENHANCED_CLASSICAL`, `STARDIST`, `CELLPOSE` and `TRAINED_RF` stay declared and unreachable, because
`SegmentationTokenParser` must keep parsing tokens that name them — a user may hold a token written
by FLASH, and failing to parse it is worse than declining to run it.

## Exit gate

1. `mvn test` passes with the five ported tests green.
2. `SweepProvenanceTest` asserts: `cropFraction()` of a full-image sweep is exactly 1.0; a 512×512
   crop of a 1024×1024 image is 0.25; `belowMinimumFraction(0.05)` fires correctly at the boundary;
   `comparableWith` returns false for the same crop with a different range **and** for the same
   range with a different crop.
3. `SweepProvenance` round-trips through `toCanonicalJson`/`fromCanonicalJson` with byte-identical
   output.
4. `SettingsTokenWriterTest` asserts the `settings` line round-trips through
   `SegmentationTokenParser.parseLenient` to an equal `SegmentationMethod`, and that the `region`
   line is present and correct for both a full-image and a cropped sweep.
5. A token naming an engine this version cannot run (`stardist;prob=0.5`) **parses** and reports the
   engine, rather than throwing.
6. `ResourceGuard` refuses a synthetic 2048×2048×40 sweep of 49 combinations and permits a
   512×512×10 sweep of 9, with the refusal carrying a readable reason string — not a bare boolean.
7. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`CropSpec.apply` ownership semantics.** The parent's `ClassicalSweep.java:174-179` has a
  `closeCroppedIfOwned` dance because `apply` sometimes returns the input unchanged and sometimes a
  new image. Preserve that contract exactly and document it, or stage 08 will leak images or close
  the user's original.
- **`CropSpecMultiChannelTest` exists for a reason.** Cropping a hyperstack has channel/slice/frame
  edge cases. Port that test before writing anything new against `CropSpec`.
- **Do not let `SweepProvenance` become optional.** The temptation in later stages will be to make
  it nullable "for now". Make the field final and non-null from the start; a nullable provenance is
  the same defect D6 wearing a different hat.
- **Timestamp in `SettingsTokenWriter`.** It makes the file non-reproducible byte-for-byte. Keep it,
  but exclude it from any equality test, and put it on a `#` comment line so parsers skip it.
