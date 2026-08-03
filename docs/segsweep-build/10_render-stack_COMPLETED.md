# Stage 10 - Rendering stack

Lift the label-map and threshold overlay renderers, the preview panel and the Swing theme.

## Why this stage exists

The grid in stage 11 is only useful because each cell shows a *label map* - objects individually
coloured over the raw image - rather than a binary mask. That distinction is the plugin's most
visible difference from Auto Threshold's "Try all", and it lives entirely in these five classes.
Lifting them separately keeps stage 11 focused on the grid's interaction logic instead of pixel
rendering. In v0.1 classical, those label maps are supplied lazily by the component tree.

## Prerequisites

- `01_repo-scaffold` complete.

May run in parallel with stages 02 and 03.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/04_SWEEP_ENGINE.md` - lazy label-map
  materialisation semantics
- Parent files under `Experiments\FLASH\src\main\java\flash\pipeline\`:
  - `ui/preview/LabelMapStyler.java` (69) — per-object colouring
  - `ui/preview/ObjectOverlayRenderer.java` (241)
  - `ui/preview/ThresholdOverlayRenderer.java` (310)
  - `ui/preview/PreviewDisplaySettings.java` (76)
  - `ui/preview/ImagePreviewPanel.java` (847) — the reusable panel
  - `ui/FlashTheme.java` (108)
- Parent tests: `Experiments\FLASH\src\test\java\flash\pipeline\ui\preview\` — 13 files, port the
  ones that apply

## Scope

- Lift all five preview classes into `segsweep.ui.render`, package declarations changed only.
- Lift `FlashTheme` as `segsweep.ui.SegSweepTheme`, renaming the class and any FLASH-branded colour
  or font constants.
- Port the applicable preview tests.
- Confirm by grep that the whole package imports only `ij`, `java.*` and `javax.swing.*`.

## Out of scope

- `LargePreviewDialog`, `ComparisonPreviewDialog`, `PreviewPairPanel` — the parent's two-up
  before/after viewer. Stage 11's shift-click comparison uses the grid's own cells; `PreviewPairPanel`
  is 2,629 lines serving a different workflow. **Do not lift it.**
- `HistogramPanel`, `MinMaxControlPanel`, `FijiStyleRangeSliderPanel`, `ThresholdControlPanel`,
  `ObjectSizeFilterPreview`, `PipelineFigureExporter` — FLASH's config-stage furniture, not needed
  here.
- The grid itself - stage 11.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `src/main/java/segsweep/ui/render/LabelMapStyler.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/render/ObjectOverlayRenderer.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/render/ThresholdOverlayRenderer.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/render/PreviewDisplaySettings.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/render/ImagePreviewPanel.java` | NEW | Lift verbatim |
| `src/main/java/segsweep/ui/SegSweepTheme.java` | NEW | Lift, renamed |
| `src/test/java/segsweep/ui/render/*Test.java` | NEW | Ported subset |

## Implementation sketch

This stage is deliberately near-mechanical. The one judgement call is the theme rename: strip FLASH
branding but keep the *values*, because they are what makes the grid legible against dark label maps
and they were tuned against real images.

```java
package segsweep.ui;

/** Swing colours, fonts and spacing. Values inherited from FLASH's theme. */
public final class SegSweepTheme { ... }
```

`LabelMapStyler` is the class that matters most — 69 lines that assign a distinguishable colour per
label value. Lift it exactly; do not substitute a "nicer" palette. Touching objects being visibly
separate is the entire visual argument against a binary-mask montage.

Headless safety: these are Swing classes, so any test that instantiates them needs
`java.awt.headless` handling. Follow whatever pattern the parent's preview tests already use rather
than inventing one — check `Experiments\FLASH\src\test\java\flash\pipeline\ui\preview\` first.

## Exit gate

1. `mvn test` passes with the ported preview tests green.
2. `grep -rn "^import" src/main/java/segsweep/ui/render/ src/main/java/segsweep/ui/SegSweepTheme.java`
   shows only `ij.*`, `java.*` and `javax.swing.*`.
3. `grep -rni "flash" src/main/java/segsweep/ui/` returns nothing.
4. A manual or automated render check: a 3-object synthetic label map renders with three visually
   distinct colours over the raw image, and the same map with `PreviewDisplaySettings` toggled to
   raw shows the underlying image. Save the two renders as test artefacts so stage 11 has a
   reference.
5. Tests run without a display (`-Djava.awt.headless=true`) or are correctly skipped with a stated
   reason — not silently passing.
6. `mvn dependency:tree` still shows `ij` as the only compile dependency.

## Known risks

- **`ImagePreviewPanel` is 847 lines** and may assume things about its container that only hold
  inside FLASH's config stages. It is the one class here worth reading rather than skimming.
- **Headless CI.** The GitHub Actions build from stage 01 has no display. If the ported preview
  tests need one, they must skip cleanly with a message, not fail the build and not pass while
  testing nothing.
- **Palette collisions.** `LabelMapStyler` on a stack with thousands of objects will reuse colours.
  That is acceptable and expected — do not "fix" it into a slow perceptual-distance algorithm.
