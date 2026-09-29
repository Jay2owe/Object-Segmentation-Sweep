# Versioning

This project uses semantic versioning:

- patch releases fix behavior without intentionally changing outputs;
- minor releases add backward-compatible measurements or options;
- major releases may change defaults, column meaning, file layout, or APIs.

During development, Maven builds use `-SNAPSHOT`. A release removes that suffix,
updates `CHANGELOG.md` and `CITATION.cff`, and tags the matching version.

Scientific output changes must be called out explicitly, including threshold
semantics, column definitions, and any change that can alter object counts.
