# Golden outputs

Each `<fixture>__<sweep>.txt` file is the canonical text dump that
`segsweep.GoldenOutputTest` produces for one seeded synthetic image
(`segsweep.GoldenFixtures`) and one sweep shape:

- `threshold` - one threshold axis;
- `size` - a fixed threshold with a minimum-size axis;
- `threshold_x_size` - threshold by minimum size;
- `threshold_knee` - one threshold axis picked by the knee alone, so the
  settings token and picked label map are exercised.

All but `threshold_knee` use `pick=both`. A dump holds every sweep-table row
(without `Duration_ms`), the pick table, the picked settings token (with its
timestamp masked), the warnings, the SHA-256 of the picked label map, and the
SHA-256 of every displayed combination's label map (so a change in label
numbering is caught). Numbers are printed with `%.10g`.

## When a golden may change

Never silently. A failing `GoldenOutputTest` means an object count, pick,
token or label map changed. If, and only if, the change is intended:

1. Add a CHANGELOG line that states what changed and why.
2. Regenerate: `sh ./mvnw -B test -Dtest=GoldenOutputTest -Dsegsweep.golden.update=true`
   (the run rewrites these files and then fails on purpose as a reminder).
3. Rerun without the switch, check the diff, and commit the goldens together
   with the change and its justification.
