# Stage 05 — Cache and vendored utilities

Lift `VariationCache` with three defect fixes, and vendor the two small helpers it and stage 08
need.

## Why this stage exists

A sweep re-runs the same combinations constantly — the user widens a range, changes one axis, comes
back tomorrow. The cache is what makes that bearable. But the parent's cache hands the same mutable
`ImagePlus` to every caller, bounds itself by entry count rather than bytes, and trusts a
caller-supplied hash to decide whether two images are the same. In FLASH those are survivable; in a
plugin that runs unattended batches they are how one image's labels end up attributed to another.

## Prerequisites

- `03_parameter-model`, `04_crop-and-token` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — defects **D9**, **D10**, **D11**
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\VariationCache.java` — read the
  whole file, it is only 274 lines. Key points: LRU map 23-29, `keyFor` 76-94, `get` 114-136,
  `put` 138-143, `writeToDisk` 152-175, `snapshotResultsToDisk` 183-203
- `Experiments\FLASH\src\main\java\flash\pipeline\image\StackHistogram.java` (209)
- `Experiments\FLASH\src\main\java\flash\pipeline\io\IoUtils.java` — **only** `moveReplacing` and its
  retry loop, roughly 40 lines. Do not lift the other 740
- Parent tests: `VariationCacheTest`, `VariationCacheKeyTest`, `VariationDiskCachePoisoningTest`

## Scope

- Lift `VariationCache` into `segsweep.sweep`, **deleting the `ConfigQcContext` constructor**
  (`VariationCache.java:31-33`). The `File`-taking constructor on line 35 already exists and becomes
  the only one.
- **Fix D10** — compute the source hash internally from pixel data plus calibration. Add
  `SourceImageHash.of(ImagePlus)` and make `keyFor` take the computed hash, never a caller string.
- **Fix D9** — `get` must not return a shared mutable instance, and the cache must be bounded by
  bytes, not by a 50-entry count.
- **Fix D11** — `writeToDisk` must not `IJ.saveAs` the shared instance.
- Vendor `StackHistogram` into `segsweep.util` (needed by stage 08's `RangeSuggester`).
- Vendor `IoUtils.moveReplacing` into `segsweep.util.IoUtils`, ~40 lines, nothing else.
- Port the three parent cache tests; add `CacheIsolationTest` and `CacheSourceHashTest`.

## Out of scope

- The executor that calls the cache — stage 06.
- `RangeSuggester` itself — stage 08.
- Deciding *when* to write to disk. The parent's rule (memory always, disk only on an explicit
  "Save variations cache" action, documented at `VariationCache.java:145-151`) is correct — preserve
  it. Wiring the button is stage 10.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/sweep/VariationCache.java` | NEW | Lift + D9, D10, D11 |
| `src/main/java/segsweep/sweep/SourceImageHash.java` | **NEW — written fresh** | The D10 fix |
| `src/main/java/segsweep/util/StackHistogram.java` | NEW | Vendored |
| `src/main/java/segsweep/util/IoUtils.java` | NEW | `moveReplacing` only |
| `src/test/java/segsweep/sweep/VariationCache*Test.java` | NEW | Three ported |
| `src/test/java/segsweep/sweep/CacheIsolationTest.java` | **NEW** | D9 acceptance |
| `src/test/java/segsweep/sweep/CacheSourceHashTest.java` | **NEW** | D10 acceptance |

## Implementation sketch

**D10 — internal source hashing.** The parent's key (`VariationCache.java:83`) begins with
`sweep.sourceImageHash()`, a string the caller supplies and nobody validates. Replace it:

```java
package segsweep.sweep;

public final class SourceImageHash {
    /**
     * SHA-256 over pixel data plus calibration and dimensions. Never accepts a
     * caller-supplied value: a stale or weak hash silently serves another
     * image's labels, which is what VariationDiskCachePoisoningTest exists for.
     */
    public static String of(ImagePlus imp);
}
```

Hash dimensions, bit depth, calibration (`pixelWidth`, `pixelHeight`, `pixelDepth`, `unit`) and then
the pixel bytes of every slice. On large stacks this is the cost of correctness — measure it, and if
it is material, sample deterministically (every *n*th voxel plus all dimensions and calibration) and
**document the sampling in the Javadoc** rather than quietly weakening the guarantee.

The rest of `keyFor` keeps the parent's shape (`VariationCache.java:83-93`), minus the macro
identity path deleted in stage 03:

```java
public static String keyFor(ParameterSweep sweep, ParameterCombo combo, String sourceHash) {
    String raw = sourceHash
            + ":" + sweep.method().label()
            + ":" + namespaceOrEmpty(sweep)
            + sweep.cropSpec().toCanonicalJson()
            + ":" + combo.toCanonicalJson();
    return sha256(raw).substring(0, 16);
}
```

**D9 — isolation and byte bounds.** Two changes to `get`/`put`:

```java
/** Returns a defensive duplicate. Callers may freely set slices, LUTs and overlays. */
public synchronized ImagePlus get(String key) { ... return cached.duplicate(); }
```

and replace the 50-entry `LinkedHashMap` eviction (`VariationCache.java:23-29`) with a byte budget:

```java
private long budgetBytes;      // set from ResourceGuard, not a constant
private long currentBytes;

private static long bytesOf(ImagePlus imp) {
    return (long) imp.getWidth() * imp.getHeight()
         * imp.getStackSize() * (imp.getBitDepth() / 8);
}
```

Evict in access order until `currentBytes <= budgetBytes`. Fifty full 16-bit stacks is many
gigabytes; an entry count is not a bound.

If `duplicate()` per `get` proves too expensive in stage 10's grid, the alternative is an immutable
read-only wrapper — but **do not** simply return the shared instance and document a "don't mutate
this" rule. That is the parent's behaviour and it is the defect.

**D11 — safe disk writes.** `VariationCache.java:168` calls `IJ.saveAs` on the shared instance,
which in IJ1 can retitle it and touch current-window state:

```java
// was: IJ.saveAs(image, "Tiff", temp.getAbsolutePath());
new FileSaver(image.duplicate()).saveAsTiff(temp.getAbsolutePath());
```

Keep the temp-file-then-atomic-move pattern (`VariationCache.java:161-174`, `moveIntoPlace` at 265)
— it exists because this folder is inside Dropbox and antivirus and cloud sync both hold transient
locks.

## Exit gate

1. `mvn test` passes; the three ported tests green, including `VariationDiskCachePoisoningTest`.
2. `CacheIsolationTest`: `put` an image, `get` it twice, mutate the first (set slice, add overlay,
   apply LUT), assert the second is unaffected **and** that the stored entry is unaffected.
3. `CacheIsolationTest`: with a 10 MB budget, storing twelve 1 MB stacks leaves at most ten and
   `currentBytes <= budgetBytes` holds after every insertion.
4. `CacheSourceHashTest`: two images with identical pixels but different calibration produce
   different hashes; two byte-identical images produce the same hash; **there is no public API path
   that lets a caller supply a hash string** — assert by reflection or by compilation, and state
   which in the test.
5. `CacheSourceHashTest`: a cache populated from image A returns nothing for a key computed from
   image B, even when every parameter and crop is identical.
6. Disk round-trip: `writeToDisk` then a fresh `VariationCache` over the same folder returns an
   equal image, and the source `ImagePlus` title is unchanged after the write (D11).
7. `mvn dependency:tree` still shows `ij` as the only compile dependency — confirm the vendored
   `IoUtils` did not drag anything in.

## Known risks

- **Hashing cost.** Full-pixel SHA-256 on a 2048×2048×40 stack is not free, and it happens once per
  sweep, not once per combination — confirm that is actually true in stage 06's dispatch loop, or
  the cost multiplies by the combination count. Cache the hash on the sweep object.
- **`duplicate()` cost in the grid.** Stage 10 renders many cells; if each `get` duplicates a full
  stack the grid will feel slow. Measure it there, and switch to a read-only wrapper if needed —
  but not back to the shared instance.
- **Vendoring `IoUtils` partially.** `moveReplacing` may call other private helpers in the 781-line
  original. Follow them and take only what is reachable; do not lift the file.
- **`StackHistogram` may have FLASH assumptions** despite being `ij`-only — check it does not expect
  a particular bit depth or a calibrated image before relying on it in stage 08.
