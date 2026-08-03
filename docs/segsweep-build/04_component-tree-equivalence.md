# Stage 04 - Component-tree equivalence gate

Add `ComponentTreeEquivalenceTest` and keep it as a hard gate: for sampled parameter combinations,
the tree must match `SegSweepLabeller` before any downstream stage builds on the tree.

## Why this stage exists

The tree is accepted because it computes the whole threshold/size/morphology space once, not because
it is allowed to change answers. The plain labeller is simple enough to trust and slow enough to use
only as an oracle. This stage proves that the fast query path and the lazy label-map path agree with
that oracle.

No later stage should proceed while this file is still pending or while its tests are red.

## Prerequisites

- `02_labeller` complete.
- `03_component-tree` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `docs/segsweep-build/02_labeller.md`
- `docs/segsweep-build/03_component-tree.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - D8 and the active D6/D7
  consequences
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - the "required test"
  section and morphology notes
- `Experiments\3DObjectsCounterPlus\` - any morphology rule settled during stage 03

## Scope

- `ComponentTreeEquivalenceTest`: sample `(threshold, minSize, maxSize)` combinations across the
  full threshold axis and compare tree counts to `SegSweepLabeller.label(...)`.
- Extend the same equivalence suite per morphology predicate as predicates are added. v0.1 includes
  the morphology axes from `04_SWEEP_ENGINE.md`; every implemented predicate needs a positive,
  negative and boundary case.
- Compare lazy label maps against the plain labeller for selected combinations: dimensions,
  calibration, object count and object voxel sizes must match. Label numbers need not be identical
  if object identities are equivalent and contiguous; document the matching rule.
- Assert that widening the public `from`/`to` display window does not change the computed full-axis
  knee inputs. The tree computes the space once; ranges select which combinations to show.
- Add benchmark-style smoke coverage that records tree build cost once and query cost separately,
  without turning timing into a brittle pass/fail gate.

## Out of scope

- Public parameter model, token parser or dialog wording - later stages.
- Replacing the plain labeller. It remains in the codebase as oracle and fallback diagnostic tool.
- Full performance tuning. This stage proves equivalence; optimisation follows only if tests show
  the accepted design is too slow.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/test/java/segsweep/tree/ComponentTreeEquivalenceTest.java` | NEW | Hard count and lazy-label equivalence gate |
| `src/test/java/segsweep/tree/ComponentTreeMorphologyEquivalenceTest.java` | NEW | Predicate-by-predicate extension |
| `src/test/java/segsweep/tree/ComponentTreeDisplayWindowTest.java` | NEW | Public range is display window, not compute budget |
| `src/test/java/segsweep/tree/ComponentTreeBenchmarkSmokeTest.java` | NEW | Records build/query separation |
| `src/test/java/segsweep/tree/ComponentTreeOracleFixtures.java` | NEW | Shared synthetic stacks |

## Implementation sketch

Use fixtures that exercise the cases most likely to diverge:

- isolated single-voxel objects
- diagonal contacts that distinguish 6- from 26-connectivity
- objects that merge as threshold is lowered
- small components removed by `minSize`
- large components removed by `maxSize`
- components touching stack boundaries
- calibrated and uncalibrated inputs
- morphology predicates at exact threshold boundaries

For each fixture:

```java
ImagePlus cropped = fixture.image();
ComponentTree tree = ComponentTree.build(cropped, fixture.connectivity());

for (QueryCase c : fixture.queryCases()) {
    LabelResult oracle = SegSweepLabeller.label(
            cropped, c.threshold(), c.minSize(), c.maxSize(), fixture.connectivity());
    ComponentTreeResult fast = tree.query(c.toTreeQuery());

    assertEquals(oracle.objectCount(), fast.objectCount());
    assertEquivalentLabels(oracle.labels(), fast.labelMap().get());
}
```

`assertEquivalentLabels` should match objects by voxel set rather than by raw label id. The labeller
and the tree can legitimately assign different contiguous labels; they cannot disagree on which
voxels belong to surviving objects.

The display-window regression should build one tree over the fixture, then ask for counts and knees
through two different public windows. The underlying full-axis count vector must be identical. The
displayed subset may differ; the computed space must not.

## Exit gate

1. `mvn test` passes.
2. `ComponentTreeEquivalenceTest` covers at least 30 sampled base combinations across threshold,
   `minSize`, `maxSize` and both supported connectivity modes.
3. `ComponentTreeMorphologyEquivalenceTest` covers each v0.1 morphology predicate implemented in
   stage 03, including boundary values and at least one non-increasing attribute rule case.
4. Lazy materialisation matches the oracle on dimensions, calibration, object count and object
   voxel sets.
5. The tests assert the tree is built once per crop and queried many times; no production path calls
   `SegSweepLabeller.label(...)` once per displayed combination except inside oracle tests.
6. The display-window test proves `from`/`to`/`step` changes what is displayed, not what the
   classical engine computes.
7. `rg -n "sourceImageHash|cacheNamespace|cacheBudgetBytes|shared mutable.*cache" src/main/java src/test/java`
   returns nothing after the old files are absent.
8. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **Equivalent labels are not necessarily equal labels.** Compare object voxel sets, not label ids,
  unless both implementations deliberately share numbering.
- **Morphology equivalence depends on policy.** If 3D Objects Counter+ uses a rule that cannot be
  reproduced exactly, make that a documented, tested difference before proceeding.
- **Benchmarks are smoke signals.** Do not make wall-clock numbers the acceptance criterion; use
  instrumentation to prove one build and many queries.
