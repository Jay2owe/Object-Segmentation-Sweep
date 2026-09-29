# Changelog

All notable changes to Object Segmentation Sweep are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project follows
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- `Object Segmentation Sweep Batch` runs from a macro or headless with `folder`, `regex`, `group`,
  `output` and `recursive` plus the analysis options, and batch runs are recorded.
- Macro, headless, grid-off and batch runs show progress in the status bar and stop on Escape.
- The review grid closes on Escape (asking first, and cancelling, while a sweep runs), and Left and
  Right step through Z.

### Changed

- Macro options without `sweep`, `from`, `to` or `step` now take the documented defaults
  (threshold, 10, 60, 5) instead of failing. Macros that relied on that error now run a sweep.
- Backslashes in `image` and `autosave` paths are read as forward slashes instead of refused.
- `image=` prefers an open window with that title over a same-named file.
- A macro or headless error now stops the calling macro with a one-line message.
- Batch analysis options refuse `image`, `autosave` and display flags instead of ignoring them.
- The Pick pill on a tile now picks that tile (select, then Pick selected) instead of only
  selecting it.
- grid.png is now always rendered at 100% zoom, one image per page, with the pages of a paged
  sweep stacked top to bottom. If the grid cannot be captured, grid.png falls back to the montage.
- A one-axis sweep wraps its tiles into rows that fit the screen instead of one long row.
- The "Filtered/Raw image" chooser is hidden: for the classical engine both are the same crop.
- Clicking a tile selects it on release; holding to peek no longer selects it.

### Fixed

- `allow_oversized` was ignored by the macro, autosave and batch feasibility checks, so a recorded
  "run anyway" macro failed on replay.
- A macro or headless run on an image with no file location and no `autosave` returned nothing
  although the sweep succeeded; autosave is now skipped with a Log line. The grid no longer shows a
  modal error for this after every run.
- The recorder also wrote a bare `run("Object Segmentation Sweep");` after the full call.
- Unreadable files in a batch no longer raise ImageJ's own error dialog per file.
- An image title containing `[`, `]` or `"` stopped the dialog settings being remembered.
- Cancel is now honoured inside a flat intensity level, during label-map materialisation and
  during voxel traversal, where it could previously be missed.
- The memory guard now accounts for bit depth; 32-bit images with many distinct values could pass
  the guard and then run out of memory.
- Histogram bin counts saturate instead of overflowing on very large stacks.
- A remembered channel that does not exist in the chosen image is reset to 1; Run used to fail with
  the channel field hidden.
- "Sweep in ROI" clips the selection to the image and says that the bounding box of a
  non-rectangular selection is used.
- Letters in a number field give a message naming the field.
- The grid is built on the Swing event thread; Suggest range, Pick selected and autosave run on
  workers, so Fiji stays responsive on large stacks.
- The first LUT toggle or brightness edit no longer turns coloured channels grey.
- While a sweep runs, the grid's overlay, LUT, brightness and Pick controls are disabled instead of
  doing nothing.
- Capturing the grid for autosave could run out of memory at high zoom and left the grid resized.
- After holding a tile to peek, the next click on it was ignored.
- Shift-click compare never opened outside tests, and its prompts were not shown.
- Moving through Z replaced the pick summary and warnings with "Materialising labels".
- Badged tiles repainted 30 times a second while idle.
- A result whose values differed only in number type (3 vs 3.0) could leave its tile "pending".
- Failed tiles were never counted, so "(n failed)" never appeared.
- Empty tiles said "No image selected"; they now say "Waiting", "Failed" or "Cancelled".
- A pick badge on another page of a paged grid now switches to that page.

### Removed

- The unused `CustomCropPicker` dialog.

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
