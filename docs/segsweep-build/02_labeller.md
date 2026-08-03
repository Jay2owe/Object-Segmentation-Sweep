# Stage 02 — The labeller

Write `SegSweepLabeller`: an `ij`-only threshold + 3D connected-components + size-filter engine that
turns a raw image and three parameters into a 16-bit label stack.

## Why this stage exists

This is the one piece of genuinely new code the plugin needs, and it is what keeps the jar
dependency-free. FLASH's `ClassicalSweep` delegates to `ObjectsCounter3DWrapper`, which wraps
3D Objects Counter+ and mcib3d — depending on it would cost an extra update site and break the
zero-friction rule that is half of why CPC was adopted. Every combination in every sweep goes
through this class, so it is also the hot path: get it right and fast here, once.

## Prerequisites

- `01_repo-scaffold` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "three severances" section, and defect **D8**
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\config\ClassicalSegmentationStage.java:63-72` —
  the `PreviewAdapter` interface being reimplemented against
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\strategy\ClassicalSweep.java:110-152`
  — how the parent calls the engine, and lines 216-259 for the empty-label and counting behaviour
  being replaced
- `Experiments\3DObjectsCounterPlus\` — **read this to settle the connectivity default.** Match
  whatever 3D Objects Counter+ uses so the two plugins agree on the same image. Do not assume.

## Scope

- `SegSweepLabeller` — threshold, 3D connected-component labelling by union-find, min/max size
  filtering, contiguous relabelling, 16-bit `ShortProcessor` stack output.
- A `LabelResult` value type carrying the label stack, the object count **computed during
  labelling** (never by re-walking pixels — D8), and per-object voxel counts.
- Calibration passthrough: the output stack carries the input's `Calibration` so downstream stages
  can report per-volume density.
- Handle >65,535 provisional components during union-find without overflowing the 16-bit output;
  fail loudly with a typed reason if the *final* label count exceeds 65,535.
- Empty-result handling: a correctly-dimensioned, correctly-calibrated all-zero stack, matching the
  parent's `emptyLabelMapLike` behaviour at `ClassicalSweep.java:216-238`.
- Full unit test suite.

## Out of scope

- Any sweep, cache, or parallelism — stages 05, 06, 08. This class segments **one** image with
  **one** set of parameters and returns.
- StarDist, Cellpose, `TRAINED_RF` — v0.2.0, not in this repo yet.
- Morphology predicates (`MorphPredicate`) — stage 04 lifts the parser; wiring them into filtering
  is stage 08.
- Any UI, any `IJ.log`, any window. This class is pure.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/SegSweepLabeller.java` | NEW | The engine, ~250 lines |
| `src/main/java/segsweep/LabelResult.java` | NEW | Label stack + count + per-object sizes |
| `src/main/java/segsweep/SegSweepLabeller$Connectivity` | NEW | Enum, inside the labeller |
| `src/test/java/segsweep/SegSweepLabellerTest.java` | NEW | ~150 lines |
| `src/test/java/segsweep/SegSweepLabellerFixtures.java` | NEW | Synthetic stacks used here and later |

## Implementation sketch

The interface shape to satisfy — the parent's `PreviewAdapter`, minus the FLASH-specific
`createRawSource`/`createFilteredSource`, and returning our own result type instead of
`ObjectsCounter3DWrapper.Result`:

```java
package segsweep;

import ij.ImagePlus;

public final class SegSweepLabeller {

    public enum Connectivity { SIX, TWENTY_SIX }

    /** Threshold, label in 3D, size-filter. Never returns null; never opens a window. */
    public static LabelResult label(ImagePlus source,
                                    int threshold,
                                    int minSize,
                                    int maxSize,
                                    Connectivity connectivity);
}
```

```java
public final class LabelResult {
    public enum Status { OK, EMPTY, TOO_MANY_LABELS }

    public Status status();
    public String reason();          // human-readable; empty when OK
    public ImagePlus labels();       // 16-bit, calibrated, never null
    public int objectCount();        // computed during labelling, NOT by re-walking pixels
    public int[] objectSizes();      // voxel count per label, index 0 unused
}
```

**Algorithm.** Two-pass union-find over the stack, which is the standard approach and avoids
recursion depth problems on large connected regions:

1. Pass one, slice by slice, raster order. For each voxel above `threshold`, look at already-visited
   neighbours (west, north, and the previous slice's neighbourhood for 26-connectivity). If none are
   labelled, mint a new provisional label. If several are, take the smallest and `union` the rest.
2. Flatten the union-find parent array with path compression, then build a map from provisional
   label → final contiguous label, counting voxels per component **as you go**.
3. Drop components outside `[minSize, maxSize]` during the relabel map construction, so they never
   reach the output.
4. Pass two: write final labels into a fresh `ShortProcessor` per slice.

Provisional labels use `int`, so the intermediate count is not bounded by 16 bits. Only the *final*
count must fit, and that is checked before pass two:

```java
if (finalCount > 65535) {
    return LabelResult.tooManyLabels(finalCount);   // typed reason — house rule 4
}
```

Union-find, sized to grow:

```java
private static int find(int[] parent, int x) {
    while (parent[x] != x) {
        parent[x] = parent[parent[x]];   // path halving
        x = parent[x];
    }
    return x;
}

private static void union(int[] parent, int[] rank, int a, int b) {
    int ra = find(parent, a), rb = find(parent, b);
    if (ra == rb) return;
    if (rank[ra] < rank[rb]) { parent[ra] = rb; }
    else if (rank[ra] > rank[rb]) { parent[rb] = ra; }
    else { parent[rb] = ra; rank[ra]++; }
}
```

**Thresholding** is `value > threshold`, matching the parent's convention at
`ClassicalSweep.java:123-127`. Verify this against `ObjectsCounter3DWrapper` before committing — an
off-by-one here shifts every knee the plugin ever reports.

**Empty result**, following `ClassicalSweep.java:216-238` — same dimensions, same calibration, same
hyperstack layout when the dimensions multiply out:

```java
ImageStack stack = new ImageStack(width, height);
for (int i = 0; i < stackSize; i++) {
    stack.addSlice("z" + (i + 1), new ShortProcessor(width, height));
}
ImagePlus label = new ImagePlus("Object label preview (no objects)", stack);
if (reference.getCalibration() != null) {
    label.setCalibration(reference.getCalibration().copy());
}
```

**D8 — the defect this stage exists to avoid.** The parent counts objects like this
(`ClassicalSweep.java:240-259`):

```java
Set<Integer> labels = new HashSet<Integer>();
for (int slice = 1; slice <= stack.getSize(); slice++) {
    ImageProcessor processor = stack.getProcessor(slice);
    for (int i = 0; i < processor.getPixelCount(); i++) {
        int value = Math.round(processor.getf(i));
        if (value > 0) labels.add(Integer.valueOf(value));
    }
}
return labels.size();
```

That is one boxed `Integer` and one hash lookup per voxel — roughly 168 million of each on a
2048×2048×40 stack, per grid cell. **Do not reproduce it.** `LabelResult.objectCount()` returns the
count that pass two already knows.

## Exit gate

1. `mvn test` passes; `SegSweepLabellerTest` covers all of:
   - two diagonally touching voxels → 1 object under 26-connectivity, 2 under 6-connectivity
   - two objects touching only across a slice boundary → 1 object in 3D, not 2
   - an object flush against every stack face (x=0, y=0, z=first, z=last) is found and not clipped
   - `minSize` and `maxSize` each exclude at both boundaries, tested for off-by-one
   - all voxels below threshold → `Status.EMPTY`, correct dimensions, correct calibration
   - single-voxel objects survive when `minSize <= 1`
   - final label values are contiguous `1..n` with no gaps after size filtering
   - a synthetic image with >65,535 components returns `Status.TOO_MANY_LABELS` and does not throw
2. `objectCount()` equals the number of distinct non-zero values in the output, verified by an
   independent slow count **in the test only**.
3. Output stack is 16-bit and carries the input's calibration.
4. A 512×512×20 stack with ~2,000 objects labels in under one second on a development machine —
   record the actual figure in the commit message as the baseline stage 08 will be measured against.
5. `mvn dependency:tree` still shows `ij` as the only compile dependency.
6. Connectivity default is documented in the class Javadoc **with a note saying which
   3D Objects Counter+ behaviour it was matched against**.

## Known risks

- **Connectivity disagreement with 3D Objects Counter+.** If the two plugins disagree on object
  count for the same image and threshold, users will notice and will report it as a bug in one of
  them. Settle this by reading the 3DOC+ source, not by picking a default. If they cannot be made to
  agree, say so explicitly in the Javadoc and in stage 14's README — a documented difference is
  survivable, a silent one is not.
- **Threshold comparison convention.** `>` versus `>=` shifts every reported knee by one step. Check
  against the parent before committing and add a test that pins the choice.
- **Memory on large stacks.** The provisional label array is one `int` per voxel — 640 MB on a
  2048×2048×40 stack, on top of the image itself. Stage 05's `ResourceGuard` is what stops that
  being discovered at runtime, but note the per-voxel cost in the Javadoc so the guard can be
  calibrated correctly.
- **Union-find growth.** Sizing the parent array by a fixed guess and reallocating badly is the
  usual performance trap here. Grow geometrically, or size it from a first-pass count of
  above-threshold voxels.
