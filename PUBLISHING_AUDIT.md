# Publishing Audit — Object Segmentation Sweep 0.2.0

Audit date: 2026-08-04

## Verdict

The repository is ready as a source release for version 0.2.0. The README deliberately does not
claim a live ImageJ update site, wiki listing, archived DOI, or published GitHub release; those are
separate distribution actions and are not prerequisites for the correctness of this source tree.

## Source-release checks

| Check | Status | Evidence |
| --- | --- | --- |
| License | PASS | Root BSD 3-Clause `LICENSE`, Maven metadata, and Java headers agree. |
| Version | PASS | Maven, changelog, README, and citation metadata use `0.2.0`. |
| Runtime dependencies | PASS | ImageJ (`net.imagej:ij`) is the only compile/runtime dependency. |
| Build workflow | PASS | Maven wrapper and GitHub Actions Java 8 build are present. |
| Plugin entry points | PASS | Single-image and batch commands are registered in `plugins.config`. |
| Public documentation | PASS | README covers install, usage, outputs, API, limitations, citation, license, and acknowledgements. |
| Citation metadata | PASS | CFF 1.2 metadata identifies author, ORCID, affiliation, license, version, source, CPC, and 3D Objects Counter+. |
| Changelog | PASS | The initial release and its deliberate deferrals are documented. |
| Output documentation | PASS | Auto-save writes root and labels-folder README files. |
| Secret/private-data scan | PASS | No credentials, private datasets, or user-specific paths are required by the source or documentation. |
| Claims audit | PASS | README describes only the Classical v0.2.0 behavior implemented and tested in this tree. |

## Reproducibility and safety decisions

- No executable picked-settings artifact is written when no displayed combination was selected.
- Picker outputs retain the crop, displayed axes, calibration, and actual knee computation range.
- Per-combination supported refusals are recorded as `FAILED` rows without aborting other cells.
- Stability budget and cancellation checks run inside voxel-membership and IoU loops.
- Existing non-empty output directories are not overwritten; a versioned sibling is created.
- Text artifacts are UTF-8 and source images opened by the plugin have explicit ownership cleanup.

## Distribution boundary

Publishing a GitHub release, minting an archival DOI, activating an ImageJ update site, submitting
an ImageJ wiki page, and adding the central update-site listing are intentionally outside this
repository audit. They must be performed only after their respective remote workflows verify the
released artifact. No placeholder DOI or false live-site claim is present here.
