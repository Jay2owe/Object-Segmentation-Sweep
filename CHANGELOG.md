# Changelog

All notable changes to Object Segmentation Sweep are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/) and the project follows
[Semantic Versioning](https://semver.org/).

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

[0.1.0]: https://github.com/Jay2owe/Object-Segmentation-Sweep/releases/tag/v0.1.0
