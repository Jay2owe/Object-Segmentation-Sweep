# Stage 02 - The plain labeller oracle

Write `SegSweepLabeller`: an `ij`-only threshold + 3D connected-components + size-filter engine that
turns a raw image and three base parameters into a 16-bit label stack. In the accepted architecture
this is the **oracle**, not the fast shipping sweep path.

## Why this stage exists

The component tree in stage 03 is the v0.1 classical engine, but it needs a simple implementation to
prove against. FLASH's `ClassicalSweep` delegates to `ObjectsCounter3DWrapper`, which wraps 3D
Objects Counter+ and mcib3d. Depending on that would cost an extra update site and break the
zero-friction rule. This stage writes the small direct labeller first so `ComponentTreeEquivalenceTest`
can ask: "does the fast tree give the same answer as a real threshold run?"

## Prerequisites

- `01_repo-scaffold` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - the "three severances"
  section and defect **D8**
- `../../../ImageJ Plugins/Object Segmentation Sweep/03_BUILD_PLAN.md` - updated v0.1 scope
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - plain labeller first as
  oracle, tree second
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\config\ClassicalSegmentationStage.java:63-72`
  - the `PreviewAdapter` interface being reimplemented against
- `Experiments\FLASH\src\main\java\flash\pipeline\ui\variations\strategy\ClassicalSweep.java:110-152`
  - how the parent calls the old engine, and lines 216-259 for the empty-label and counting
  behaviour being replaced
- `Experiments\3DObjectsCounterPlus\` - read this to settle the connectivity default. Match whatever
  3D Objects Counter+ uses so the two plugins agree on the same image. Do not assume.

## Scope

- `SegSweepLabeller` - threshold, 3D connected-component labelling by union-find, min/max size
  filtering, contiguous relabelling, 16-bit `ShortProcessor` stack output.
- `LabelResult` carrying the label stack, the object count **computed during labelling** and
  per-object voxel counts.
- Calibration passthrough: the output stack carries the input's `Calibration`.
- Handle more than 65,535 provisional components during union-find without overflowing the 16-bit
  output; fail with a typed reason if the final label count exceeds 65,535.
- Empty-result handling: a correctly dimensioned, correctly calibrated all-zero stack, matching the
  parent's `emptyLabelMapLike` behaviour.
- Full unit test suite.

## Out of scope

- Component tree construction, morphology attributes and lazy label materialisation - stages 03 and
  04.
- Any sweep, executor, grid, public API or UI.
- StarDist, Cellpose, `TRAINED_RF` - v0.2.0 or not planned.
- Applying morphology predicates. The oracle covers base threshold/min/max size here; the tree
  equivalence suite is extended per morphology predicate in stage 04.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/SegSweepLabeller.java` | NEW | The oracle engine, ~250 lines |
| `src/main/java/segsweep/LabelResult.java` | NEW | Label stack + count + per-object sizes |
| `src/main/java/segsweep/SegSweepLabeller$Connectivity` | NEW | Enum, inside the labeller |
| `src/test/java/segsweep/SegSweepLabellerTest.java` | NEW | ~150 lines |
| `src/test/java/segsweep/SegSweepLabellerFixtures.java` | NEW | Synthetic stacks used by the tree gate |

## Implementation sketch

The interface shape to satisfy:

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
    public int objectCount();        // computed during labelling, not by re-walking pixels
    public int[] objectSizes();      // voxel count per label, index 0 unused
}
```

Algorithm:

1. Pass one, slice by slice, raster order. For each voxel above `threshold`, look at already visited
   neighbours. If none are labelled, mint a new provisional label. If several are, take the smallest
   and `union` the rest.
2. Flatten the union-find parent array with path compression, then build a map from provisional
   label to final contiguous label, counting voxels per component as you go.
3. Drop components outside `[minSize, maxSize]` during relabel-map construction.
4. Pass two: write final labels into a fresh `ShortProcessor` per slice.

Provisional labels use `int`, so the intermediate count is not bounded by 16 bits. Only the final
count must fit:

```java
if (finalCount > 65535) {
    return LabelResult.tooManyLabels(finalCount);
}
```

Thresholding is `value > threshold`, matching the parent's convention at
`ClassicalSweep.java:123-127`. Verify this before committing; an off-by-one here shifts every
equivalence test and every knee the plugin reports.

D8 is not "fixed later" by a faster count. This class already knows the count while labelling, and
the component tree later returns counts directly. Do not reproduce the parent's
`HashSet<Integer>` pixel walk anywhere outside tests.

## Exit gate

1. `mvn test` passes; `SegSweepLabellerTest` covers all of:
   - two diagonally touching voxels -> 1 object under 26-connectivity, 2 under 6-connectivity
   - two objects touching only across a slice boundary -> 1 object in 3D, not 2
   - an object flush against every stack face is found and not clipped
   - `minSize` and `maxSize` each exclude at both boundaries, tested for off-by-one
   - all voxels below threshold -> `Status.EMPTY`, correct dimensions, correct calibration
   - single-voxel objects survive when `minSize <= 1`
   - final label values are contiguous `1..n` with no gaps after size filtering
   - a synthetic image with >65,535 components returns `Status.TOO_MANY_LABELS` and does not throw
2. `objectCount()` equals the number of distinct non-zero values in the output, verified by an
   independent slow count **in the test only**.
3. Output stack is 16-bit and carries the input's calibration.
4. A 512x512x20 stack with ~2,000 objects labels in under one second on a development machine;
   record the actual figure so stage 04 can compare tree build/query costs against it.
5. `mvn dependency:tree` still shows `ij` as the only compile dependency.
6. Connectivity default is documented in class Javadoc with a note saying which 3D Objects Counter+
   behaviour it was matched against.

## Known risks

- **Connectivity disagreement with 3D Objects Counter+.** Settle this by reading the 3DOC+ source,
  not by picking a default.
- **Threshold comparison convention.** `>` versus `>=` shifts every reported knee by one step.
- **Memory on large stacks.** The provisional label array is one `int` per voxel. Stage 03's tree
  and stage 06's `ResourceGuard` govern shipping memory, but the oracle still needs clear refusal
  behaviour for pathological images.
- **Union-find growth.** Grow geometrically or size from a first-pass count of above-threshold
  voxels; fixed guesses are the usual performance trap.
