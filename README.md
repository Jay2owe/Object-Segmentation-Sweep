# Object Segmentation Sweep

[![Build](https://github.com/Jay2owe/Object-Segmentation-Sweep/actions/workflows/build-main.yml/badge.svg)](https://github.com/Jay2owe/Object-Segmentation-Sweep/actions/workflows/build-main.yml)
[![License: BSD-3-Clause](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](LICENSE)
[![JitPack](https://jitpack.io/v/Jay2owe/Object-Segmentation-Sweep.svg)](https://jitpack.io/#Jay2owe/Object-Segmentation-Sweep)

Object Segmentation Sweep is an ImageJ/Fiji plugin for choosing classical segmentation settings.
It builds a component tree once for an image crop, queries one or two parameter axes, and shows the
resulting label maps side by side. It reports the object-count knee and label-membership stability
independently, with the crop, displayed values, and actual knee computation range retained in the
outputs.

## Features

- Sweep one or two axes: threshold, minimum/maximum size, volume, mean/max intensity, elongation,
  surface area, sphericity, compactness, or maximum Feret diameter.
- Query one component tree for every displayed classical combination instead of relabelling the
  source image for each cell.
- Review per-object coloured overlays in a synchronized grid with raw-image peek, comparison,
  overlay-source, LUT, and brightness controls.
- Report knee and neighbour-IoU stability as independent typed recommendations. Disagreement is
  preserved rather than silently arbitrated.
- Run on a recorded crop, including crop bounds and fraction in tables and settings provenance.
- Suggest threshold ranges in source-image units and size ranges from 3D component voxel counts.
- Run macro/headless analyses through a pure Java API or process folders with regex capture-group
  preview and recursive batch discovery.
- Auto-save tables, a reviewed/deterministic grid image, an optional picked settings token, and an
  optional 16-bit picked label map with README files for both output folders.
- Depend only on ImageJ externally at runtime; the shared batch-discovery core is private inside
  the plugin JAR.

Classical segmentation uses the same 26-connected default as 3D Objects Counter+. Thresholding is
strictly `value > threshold`.

## Installation

### Manual JAR

Download `Object-Segmentation-Sweep-0.2.0.jar` from the project release and place it in Fiji's
`plugins/` directory, then restart Fiji. The commands appear as:

- **Plugins → Object Segmentation Sweep**
- **Plugins → Object Segmentation Sweep Batch**

Install only that JAR. `oc3d-core` is bundled and relocated under
`segsweep.internal.core`; it must not be copied into Fiji separately.

An ImageJ update site is planned separately; this source release does not claim that it is already
listed or live.

### Build from source

Java 8 or newer is required.

The build pins `io.github.jay2owe:oc3d-core:0.1.0`. Because the core is not read from a public
Maven repository, first build its immutable `v0.1.0` tag into the same Maven repository, then
verify the plugin:

```bash
git clone --branch v0.1.0 --depth 1 https://github.com/Jay2owe/oc3d-core.git
mvn -f oc3d-core/pom.xml clean install
bash mvnw clean verify
```

On Windows:

```powershell
git clone --branch v0.1.0 --depth 1 https://github.com/Jay2owe/oc3d-core.git
mvn -f oc3d-core/pom.xml clean install
.\mvnw.cmd clean verify
```

The main artifact is written to `target/Object-Segmentation-Sweep-0.2.0.jar`. It contains the
cycle-safe recursive traversal and regex grouping code under a private namespace while leaving
ImageJ to Fiji. `verify` also exercises that packaged path in an isolated class loader. GitHub
Actions performs the same exact-tag bootstrap from fresh checkouts.

## Usage

1. Open an 8-, 16-, or 32-bit grayscale image, or use **Browse…** in the plugin dialog.
2. Choose the channel for a multichannel Z hyperstack and optionally enter a crop.
3. Select one axis and its `From`, `To`, and `Step` values. Optionally enable a second axis.
4. Choose `knee`, `stability`, `both`, or `none`, then configure grid/table/save output.
5. Run the sweep and inspect the synchronized label-map grid.
6. If desired, select a completed cell and choose **Pick selected**.

Automatic picks are saved only when the selected criterion yields an actionable displayed
combination. When criteria disagree, a picker refuses, or `pick=none`, no executable
`picked_settings.txt` or picked label TIFF is invented. The pick summary still records the typed
outcomes and recommendations.

## Output

Auto-save writes a versioned folder instead of overwriting an existing non-empty result:

```text
Object Segmentation Sweep/
  sweep_results.csv
  pick_summary.csv
  picked_settings.txt          present only when a combination was picked
  grid.png
  README.txt
  labels/
    <image>_picked.tif         present only when a combination was picked
    README.txt
```

`sweep_results.csv` has one row per displayed combination, including object count, calibrated
`Objects_Per_mm3` (blank for 2D), optional `Objects_Per_mm2`, neighbour IoU, eligibility, duration,
crop fraction, and flags. A supported per-cell refusal becomes a `FAILED` row rather than aborting
the other combinations. `SATURATED` means the selected foreground covers the analysed crop.
`TIMED_OUT` is retained in the result schema for execution strategies that define a deadline;
the v0.2 Classical engine has no per-combination timeout setting.

`picked_settings.txt` is the reproducible methods artifact. It contains the classical settings
token, source identity/channel, crop, calibration, displayed axes, both picker reports, and the
actual full-axis range used for the knee computation.

## Macro

```javascript
run("Object Segmentation Sweep",
    "image=[C:/data/image.tif] channel=1 engine=classical " +
    "sweep=threshold from=10 to=60 step=5 pick=both hide_display");
```

Key options:

| Option | Default | Meaning |
| --- | --- | --- |
| `image` | active image | File path or open-window title |
| `channel` | `1` | One-based channel |
| `engine` | `classical` | The only executable v0.2.0 engine |
| `sweep`, `from`, `to`, `step` | threshold, 10, 60, 5 | Primary axis |
| `values` | — | Explicit comma-separated primary values |
| `sweep2`, `from2`, `to2`, `step2`, `values2` | — | Optional secondary axis |
| `crop` | full image | `x,y,width,height` |
| `pick` | `both` | `knee`, `stability`, `both`, or `none` |
| `min_crop_fraction` | `0.05` | Warn below this crop fraction |
| `stability_budget_ms` | `0` | Stability time budget; `0` is unlimited |
| `autosave` | beside the input | Explicit output folder |
| `hide_display` | off | Suppress all windows |
| `hide_grid`, `hide_tables` | off | Suppress selected interactive outputs |

## Java API

```java
SegSweepParameters parameters = SegSweepParameters.builder()
        .image(image)
        .channel(1)
        .axis(ParameterId.THRESHOLD, 10, 60, 5)
        .pickCriterion(SegSweepParameters.PickCriterion.BOTH)
        .build();

SegSweepResult result = SegSweep.run(parameters);
ResultsTable sweep = result.sweepTable();
if (result.pickedLabelMap() != null) {
    ImagePlus labels = result.pickedLabelMap().get();
}
```

`SegSweep.run` opens no dialogs, shows no windows, and writes no files. Pass the source
`ImagePlus` directly. `SegSweepBatchRunner` provides the folder-level API.

## Related plugins

Object Segmentation Sweep produces label images for downstream analysis:

- [Centre-Particle Coincidence (CPC)](https://github.com/Jay2owe/CPC) measures object-based
  colocalization from label images or ROI sets.
- [3D Objects Counter+](https://github.com/Jay2owe/3DObjectsCounterPlus) counts and measures 3D
  objects with matching morphology concepts.

For sweeping preprocessing filter chains rather than segmentation settings, see
[Macro Builder](https://sites.imagej.net/Macro-Builder/).

## Known limitations

- Knee and stability are heuristics, not proofs of an optimal segmentation.
- v0.2.0 has no randomization null model and cannot claim that a knee differs from chance.
- The classical engine is the only executable engine; StarDist and Cellpose are deferred.
- v0.2.0 rejects time-series inputs; split timepoints and analyse each frame separately.
- Knee scoring is one-dimensional and is explicitly refused for two varying axes.
- Exact Feret evaluation is bounded to 4096 voxels per candidate; affected combinations are
  reported as failed rows and remain available for manual diagnosis.
- Interactive grids and synthetic autosave montages are limited to 100 cells. Compute-only Java
  API runs may return larger result tables, but are refused above 10,000 combinations or 250
  million combination-voxel queries so pathological ranges fail before dispatch.
- Results are conditional on the recorded crop, displayed values, and knee computation domain.
- `Duration_ms` is wall-clock telemetry and can vary across otherwise identical runs.

## Citation

See [`CITATION.cff`](CITATION.cff). Until an archived DOI is minted, cite the version and source:

> Malcolm, J. (2026). Object Segmentation Sweep (v0.2.0) [Software]. GitHub.
> https://github.com/Jay2owe/Object-Segmentation-Sweep

Suggested methods wording:

> Segmentation thresholds were reviewed with Object Segmentation Sweep v0.2.0, recording the
> object-count knee and neighbour-IoU stability together with the crop and parameter range.

## License

BSD 3-Clause License. See [`LICENSE`](LICENSE).

## Acknowledgements

Developed by Jamie Malcolm in the [Brancaccio Lab](https://www.ukdri.ac.uk/labs/brancaccio-lab)
at the [UK Dementia Research Institute](https://ukdri.ac.uk/centres/imperial), Imperial College
London.

This work was supported by the UK Dementia Research Institute, which receives its core funding
from the UK Medical Research Council, Alzheimer's Society, and Alzheimer's Research UK.

Built on [Fiji](https://fiji.sc/) and [ImageJ](https://imagej.net/). Cite Fiji (Schindelin et al.,
2012), ImageJ (Schneider et al., 2012), and ImageJ2/SciJava (Rueden et al., 2017) as appropriate.
