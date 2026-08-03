# Stage 14 — Release furniture

Add the repo documentation, citation metadata and publishing audit, deploy locally, and confirm the
plugin is ready to hand to the publishing skills.

## Why this stage exists

The plan exists because FLASH contains more capability than CPC and is adopted far less. The
difference is not code — it is that CPC installs cleanly, explains itself in one sentence, is
citable, and cross-links to the things its users need next. This stage is that difference, and
skipping it produces a good plugin nobody installs, which is the exact outcome the whole exercise
was set up to avoid.

## Prerequisites

- All of stages 01–13 complete.

## Read first

- `docs/segsweep-build/00_overview.md`
- `../../../ImageJ Plugins/Object Segmentation Sweep/drafts/README.md` — the repo README, already drafted; **update it against what was actually
  built**, do not paste it unchanged
- `../../../ImageJ Plugins/Object Segmentation Sweep/drafts/wiki-page.md` and `../../../ImageJ Plugins/Object Segmentation Sweep/drafts/sites-yml-entry.md` — drafted, and both stay drafts
  until this stage's exit gate passes
- `../../../ImageJ Plugins/Object Segmentation Sweep/03_BUILD_PLAN.md` — the "Publishing pathway" and "Adoption plan" sections
- `Experiments\Plugin-Publishing-Pathway\CHECKLIST.md` and steps `01`, `04`, `05`, `06`
- `Experiments\CPC\CITATION.cff`, `CHANGELOG.md`, `PUBLISHING_AUDIT.md` — the models

## Scope

- `README.md` — from `drafts/README.md`, corrected against the built plugin. Every claim must be
  true of the code as shipped; delete any feature that did not land.
- `CITATION.cff` — modelled on CPC's, cross-citing CPC and 3D Objects Counter+.
- `CHANGELOG.md` — `0.1.0` with the real feature list.
- `PUBLISHING_AUDIT.md` — from CPC's, worked through.
- Acknowledgements and funders block, copied from CPC (pathway step 04).
- Version `0.1.0-SNAPSHOT` → `0.1.0` in `pom.xml`.
- Local deploy and a real-image test.
- **The cross-links that constitute the adoption plan** — two lines in CPC's README and wiki page,
  reciprocal links with Macro Builder, a link from 3D Objects Counter+.
- Reconcile the three `drafts/` files with what shipped, and lift the DRAFT markers only where the
  content is now true.

## Out of scope

- Creating the GitHub remote and pushing — `push-public`, after `plugin-publish-audit`.
- Uploading to the update site — `imagej-update-site-release`.
- Submitting the wiki page — `imagej-wiki-update-site`.
- The central-list PR — `imagej-update-site-pr`, and **not until the exit gate below passes.**
- The bioRxiv preprint — deferred to v0.2.0, because its claim depends on the null model.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `README.md` | NEW | From the draft, corrected against reality |
| `CITATION.cff` | NEW | From CPC's |
| `CHANGELOG.md` | NEW | 0.1.0 |
| `PUBLISHING_AUDIT.md` | NEW | From CPC's, completed |
| `pom.xml` | MODIFY | Version to `0.1.0` |
| `../../../ImageJ Plugins/Object Segmentation Sweep/drafts/*.md` | MODIFY | Reconcile with what shipped |
| `Experiments\CPC\README.md` | MODIFY | **The adoption cross-link** |
| `Experiments\Macro-Builder\README.md` | MODIFY | Reciprocal boundary link |
| `Experiments\3DObjectsCounterPlus\README.md` | MODIFY | Cross-link |

## Implementation sketch

**The CPC cross-link is the single highest-yield action in this stage.** CPC's pitch is that
segmentation is decoupled — so every CPC user has already made a segmentation decision with no tool
to make it with. Under CPC's Features list:

> Choosing the segmentation settings that produce those label images: see
> [Object Segmentation Sweep](https://github.com/Jay2owe/Object-Segmentation-Sweep).

Same sentence on CPC's wiki page. Two lines, in a repo that already has users.

**Macro Builder, reciprocal, settling the boundary in public** so nobody has to ask why there are
two sweep plugins:

> For sweeping *segmentation* settings rather than preprocessing filter chains, see Object
> Segmentation Sweep.

and in this repo's README, pointing back:

> For sweeping *preprocessing filter chains* rather than segmentation settings, see Macro Builder.

**The methods sentence**, in the README, the wiki page and `CITATION.cff` — the thing that drives
citations to two plugins at once:

> Segmentation thresholds were selected using Object Segmentation Sweep (v0.1.0, DOI …), taking the
> value at the knee of the object-count curve over the range 10–60 in steps of 5; object-based
> colocalization was measured with CPC (v1.4.0, DOI …).

**Correct the README against reality.** The draft was written before any code existed. Walk its
Features list item by item against the built plugin and delete anything that did not land — a README
promising a feature that is not there is worse than a shorter README. In particular check: whether
the second sweep axis survived stage 12's kill-criterion check, and whether the connectivity default
matches 3D Objects Counter+ or differs (stage 02) — if it differs, **say so in the README**, because
a silent difference in object counts between two of your own plugins is a bug report waiting to
happen.

**Known limitations section** — state plainly, because a reviewer will find these anyway:

- Knee and stability are heuristics, not proofs.
- There is no null model in v0.1.0, so the plugin cannot say whether a knee differs from chance.
  Deferred to v0.2.0.
- Results are conditional on the crop and range recorded in `picked_settings.txt`.
- Classical engine only; StarDist and Cellpose are v0.2.0.

## Exit gate

1. `bash mvnw clean package -Denforcer.skip=true` succeeds; version is `0.1.0`.
2. `mvn dependency:tree` — **`ij` is the only compile dependency.** The final check of the rule the
   whole plugin is built around.
3. Deployed to local Fiji via `/deploy`; **installs in a clean Fiji with no other update site
   enabled** and runs a sweep on a real image end to end.
4. Every claim in `README.md` is true of the built jar — verified item by item, not skimmed.
5. Known limitations section present and honest.
6. `CITATION.cff` validates and cross-cites CPC and 3D Objects Counter+.
7. `PUBLISHING_AUDIT.md` complete with no unresolved items; no local paths, Dropbox paths, LabAdmin
   paths, credentials or private data anywhere in the tree (`rg` for them).
8. The three cross-links are **committed in their own repos**, not merely planned.
9. `drafts/` reconciled: each file either matches what shipped or still carries an accurate DRAFT
   marker explaining what is outstanding.
10. `/plugin-publish-audit` run and clean.
11. **The readiness judgement, stated explicitly in the commit message:** can a stranger install this
    from the update site alone and use it without reading anything but the wiki page? Until that is
    yes, `imagej-update-site-pr` does not run.

## Known risks

- **Editing three other repos.** CPC, Macro-Builder and 3DObjectsCounterPlus each have their own git
  history and their own public branches. Commit the cross-links in each repo separately, and do not
  push any of them public as a side effect of this stage — that is `push-public`'s job, per repo.
- **The README drifting from reality.** It was drafted before the code. Rewriting it from the built
  plugin is faster and safer than diffing the draft.
- **Version bump timing.** `0.1.0-SNAPSHOT` → `0.1.0` before the jar is final means a rebuild; do it
  once, at the start of this stage, and rebuild.
- **Publishing eagerness.** The central-list PR puts this in front of every Fiji user. Exit gate 11
  is the gate; an active update site is not readiness.
- **Cross-links to a repo that does not exist yet.** The GitHub URLs in CPC's README will 404 until
  `push-public` runs. Either sequence this stage after the push, or use the update-site URL in the
  cross-links and add the GitHub link afterwards.
