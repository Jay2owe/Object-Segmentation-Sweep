# Changelog

All notable changes to Object Segmentation Sweep are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project follows
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.0] - 2026-08-07

### Added

- Dialog settings are remembered between sessions (`SweepStateStore`). Ranges, axes, channel, pick
  criterion and output toggles are restored on open. The image is never restored, and a custom crop
  is restored only when it still fits the image in front of the user.
- Sweeps of three or more axes page the grid instead of mis-laying it out: the first two axes are
  the grid, each further combination is a page chosen from the toolbar. Previously a third axis
  produced a grid sized for two axes with every cell added to it.
- `ResourceGuard.Limits` separates the cell-count ceilings from the memory budget, and
  `Feasibility.refusalKind()` says which refused. A count-limit refusal now offers "run it anyway";
  a memory-budget refusal does not, because overruling it only converts a clear message into an
  `OutOfMemoryError` partway through.
- `allow_oversized` macro option and `SegSweepParameters.allowOversizedSweep`, so an accepted
  override reproduces from a macro rather than stopping at the refusal the user already answered.
- `oc3d-core` 0.1.0 now supplies regex grouping and cycle-safe recursive folder traversal. It is
  privately relocated to `segsweep.internal.core` in the single installable plugin JAR.
- Exact-core-tag CI bootstrapping and packaged-runtime checks verify the relocated core, plugin
  descriptor, licence, build provenance, and absence of bundled ImageJ classes.

### Changed

- A filename whose selected optional capture group did not participate is now skipped during
  discovery instead of aborting the entire batch preview. This is the shared core's explicit
  behavior; other matching files continue in deterministic order.

### Notes

- The absence of a per-combination label-stack cache is deliberate and unchanged. It is the fix for
  defects D9–D11 (shared mutable `ImagePlus` handed to every caller, caller-supplied source hashes,
  `IJ.saveAs` side effects on a shared image), and `RemovedCacheReferenceTest` keeps it out. Expensive
  engines in v0.2 get strategy-owned intermediates instead; see `docs/segsweep-build/00_overview.md`
  rule 9.

## [0.1.0] - 2026-08-04

### Added

- Classical 2D/3D component-tree segmentation for 8-, 16-, and 32-bit grayscale images.
- One- and two-axis parameter sweeps with crop/channel provenance and resource guarding.
- Threshold and 3D component-size range suggestions.
- Independent typed object-count-knee and neighbour-IoU-stability reports.
- Synchronized interactive grid review, manual cell picking, macro recording, and headless API.
- Regex-grouped recursive batch processing with preview, failures, and per-folder comparability.
- UTF-8 auto-save outputs containing tables, grid image, reproducible picked settings, optional
  16-bit label map, and per-folder README files.
- Cancellation and configurable stability time budgets throughout expensive tree and IoU work.

### Notes

- v0.1.0 executes the Classical engine only.
- StarDist, Cellpose, randomization/null-model claims, and multidimensional knee scoring are
  deferred.

[Unreleased]: https://github.com/Jay2owe/Object-Segmentation-Sweep/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Jay2owe/Object-Segmentation-Sweep/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Jay2owe/Object-Segmentation-Sweep/releases/tag/v0.1.0
