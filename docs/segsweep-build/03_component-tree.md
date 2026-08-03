# Stage 03 - Component tree

Write the `ComponentTree`: an `ij`-only max-tree over the cropped image that computes the classical
threshold/size/morphology space once, then answers combination queries without re-segmenting.

## Why this stage exists

`04_SWEEP_ENGINE.md` is accepted architecture. The classical engine is not a grid of independent
threshold runs. Threshold is a cut through a max-tree; min/max size and morphology constraints are
attribute predicates on the tree nodes. Building the tree once makes the public sweep range a
display window rather than a compute budget, brings morphology axes into v0.1.0, and removes the old
multi-label-stack cache architecture.

The plain labeller from stage 02 remains essential: it is the oracle this tree must match before any
downstream stage can rely on tree counts.

## Prerequisites

- `02_labeller` complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` - defect ledger consequences,
  especially D2, D3, D5, D8, D9, D10 and D11
- `../../../ImageJ Plugins/Object Segmentation Sweep/03_BUILD_PLAN.md` - v0.1.0 scope and
  file-by-file plan after the component-tree update
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - **read in full; this
  stage implements its accepted engine decision**
- `Experiments\3DObjectsCounterPlus\` - read the connectivity and morphology filtering behaviour
  needed to match 3D Objects Counter+ where possible

## Scope

- `ComponentTree` over one cropped `ImagePlus`, built by union-find over voxels in decreasing
  intensity order.
- `ComponentNode` / node table storage with parent, children, birth threshold, voxel count,
  intensity sum, max intensity, bounds and enough moments for morphology attributes.
- Incremental surface area using the accepted O(1) voxel-add rule:
  `dS = 6 - 2 * faceNeighboursAlreadyPresent`.
- Attribute calculations for v0.1.0 morphology axes described in `04_SWEEP_ENGINE.md`: `volume`,
  `mean_intensity`, `max_intensity`, `elongation`, `surface_area`, `sphericity`, `compactness`, plus
  a bounded exact `feret_diameter_max` path for surviving nodes only.
- Query API returning object counts and node identities for `(threshold, minSize, maxSize,
  morphology predicates)` without materialising an `ImagePlus`.
- Lazy label-map materialisation API for a specific query, used later by the grid and auto-save.
- `ResourceGuard` cost inputs for tree memory: crop voxels, node arrays, union-find arrays,
  attribute arrays and worst-case lazy materialisation of one 16-bit label map.
- Focused unit tests for construction invariants, attributes, connectivity and typed refusal on
  memory/label-count limits.

## Out of scope

- `ParameterSweep`, macro options and public axis names - stage 05 owns the user-facing parameter
  model.
- `CropSpec`, provenance and settings tokens - stage 06.
- Full equivalence against the plain labeller - stage 04 is the hard gate.
- Rendering, grid layout or output files. This stage only supplies lazy label maps when asked.
- StarDist, Cellpose and 3D Objects Counter+ as engines - v0.2.0 runtime-detected paths.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/tree/ComponentTree.java` | NEW | Max-tree build and query facade |
| `src/main/java/segsweep/tree/ComponentNode.java` | NEW | Immutable view over node attributes |
| `src/main/java/segsweep/tree/ComponentTreeBuilder.java` | NEW | Union-find construction |
| `src/main/java/segsweep/tree/ComponentTreeQuery.java` | NEW | Threshold, size and morphology predicates |
| `src/main/java/segsweep/tree/ComponentTreeResult.java` | NEW | Count, selected nodes, lazy label provider |
| `src/main/java/segsweep/tree/MorphologyAttribute.java` | NEW | v0.1 attribute identifiers |
| `src/main/java/segsweep/tree/MorphologyPredicate.java` | NEW | Direct predicate used by tree queries |
| `src/main/java/segsweep/tree/LazyLabelMap.java` | NEW | Materialises one 16-bit label map on demand |
| `src/test/java/segsweep/tree/*Test.java` | NEW | Construction, attributes, lazy labels |

## Implementation sketch

Build from the cropped image only. Convert each voxel to an intensity bucket/order key, sort or
bucket voxels in decreasing intensity, and activate one voxel at a time. For each activated voxel:

1. Create a singleton component with volume 1, intensity sum equal to the voxel value, max intensity
   equal to the voxel value, bounds set to the voxel coordinate, raw moment sums initialised, and
   surface area `6 - 2 * faceNeighboursAlreadyPresent`.
2. Union with already-active neighbours using the same connectivity as `SegSweepLabeller`.
3. When a union crosses an intensity level, create the parent max-tree node and attach the lower
   components as children.
4. Maintain additive attributes during union: volume, intensity sum, bounds and raw moments.
   Surface area is updated from voxel activation rather than by summing child surface areas.

Non-increasing attributes (`sphericity`, `elongation`, `compactness`) must follow the Salembier
filtering rule that best reproduces 3D Objects Counter+ behaviour. Do not choose on taste; read the
3DOC+ source and document the rule in class Javadocs. If exact equivalence is impossible, the later
equivalence gate must describe and test the documented difference.

`feret_diameter_max` is deliberately not maintained through construction. Use bounding-box diagonal
as a cheap upper bound during pruning and compute exact max pairwise distance only for surviving
nodes after cheaper predicates have pruned the candidate set.

Query shape:

```java
ComponentTree tree = ComponentTree.build(cropped, connectivity);
ComponentTreeQuery query = ComponentTreeQuery.builder()
        .threshold(32)
        .minSize(50)
        .maxSize(Integer.MAX_VALUE)
        .predicate(MorphologyAttribute.SPHERICITY, ">=", 0.7)
        .build();

ComponentTreeResult result = tree.query(query);
int count = result.objectCount();          // tree query, no label image
ImagePlus labels = result.labelMap().get(); // lazy; only when drawing/output needs it
```

The lazy label map must be a fresh 16-bit stack per materialisation request. It may cache within the
`LazyLabelMap` object while the grid cell owns it, but there is no global cache of one stack per
combination.

## Exit gate

1. `mvn test` passes.
2. `ComponentTreeConstructionTest` covers empty images, single objects, touching objects, components
   born at different thresholds, stack-face objects and both 6- and 26-connectivity.
3. `ComponentTreeAttributeTest` pins volume, mean intensity, max intensity, bounds, surface area,
   sphericity, compactness and elongation on small synthetic shapes.
4. `ComponentTreeLazyLabelMapTest` proves `query(...).objectCount()` does not materialise labels,
   and that calling `labelMap().get()` returns a calibrated 16-bit label stack with contiguous
   labels.
5. `ComponentTreeFeretTest` proves exact Feret calculation runs only after cheaper predicates
   prune, using instrumentation rather than timing.
6. `ResourceGuardTest` covers tree memory refusal and permits a small cropped stack. The estimate
   includes one lazy label map for active drawing/output, not retained labels for every displayed
   combination.
7. Grep confirms no production reference to the removed multi-label-stack cache or its old
   D9/D10/D11 acceptance tests.
8. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **Tree correctness has no parent test suite.** The next stage is mandatory because a fast sweep
  whose counts differ from a real threshold run is worse than no sweep.
- **Connectivity now governs two implementations.** The default must match the stage 02 labeller
  and 3D Objects Counter+ behaviour.
- **Non-increasing morphology attributes are policy-sensitive.** Document the rule, test it, and
  make any divergence from 3DOC+ explicit.
- **Memory can still be large.** A max-tree can approach one node per voxel. Crop-first and
  `ResourceGuard` refusal are part of the engine, not optional UI advice.
