# Stage 01 — Repo scaffold

Create the `Object-Segmentation-Sweep` repository from CPC's furniture, so that `mvn package`
produces a jar that Fiji loads and that shows an empty plugin entry in the menu.

## Why this stage exists

Every later stage adds code to a repo that must already build, test and package. Getting the
chassis, the Maven configuration and the plugin registration right once — by cloning a repo that is
already published and working — removes an entire class of problem from the other thirteen stages.
CPC is the reference because it is the plugin in this family that actually got adopted, and its
zero-dependency build is why.

## Prerequisites

None.

## Read first

- `docs/segsweep-build/00_overview.md` — especially the house rules
- `../../../ImageJ Plugins/Object Segmentation Sweep/01_NAMING.md` — the identity table is authoritative for every name below
- `../../../ImageJ Plugins/Object Segmentation Sweep/02_CONTRACT.md` — the "Inherited chassis" section
- `Experiments\CPC\pom.xml` — the model, in full
- `Experiments\CPC\src\main\resources\plugins.config`
- `Experiments\CPC\src\main\java\cpc\CPC_.java` — entry-class shape
- `Experiments\CPC\src\main\java\cpc\LabelUtils.java` and `ui\ToggleSwitch.java` — copied verbatim
- `Experiments\CPC\.github\workflows\build-main.yml`

Ignore CPC files with `(Jamie Malcolm's conflicted copy …)` in the name — they are sync-conflict
artefacts, not source.

## Scope

- Create the repo at `Experiments\Object-Segmentation-Sweep\`, `git init`, branch `main`.
- Clone and adapt CPC's `pom.xml`: parent `org.scijava:pom-scijava:43.0.0`, `groupId`
  `io.github.jay2owe`, `artifactId` `Object-Segmentation-Sweep`, version `0.1.0-SNAPSHOT` → set to
  `0.1.0` at release. **`ij` must be the only compile dependency**; JUnit test-scoped.
- Copy `mvnw`, `mvnw.cmd`, `.mvn/`, `.gitignore`, `.gitattributes`, `LICENSE` (BSD-3-Clause).
- Create the package tree: `segsweep`, `segsweep.sweep`, `segsweep.sweep.analysis`,
  `segsweep.token`, `segsweep.ui`, `segsweep.ui.grid`, `segsweep.ui.render`, `segsweep.util`.
- `src/main/resources/plugins.config` with the single entry.
- `segsweep/SegSweep_.java` — a stub `PlugIn` that shows an "under construction" message. Stage 13
  replaces it.
- Copy `LabelUtils.java` and `ui/ToggleSwitch.java` **verbatim**, changing only the package
  declaration.
- Clone the GitHub Actions build workflow.
- One smoke test so `mvn test` has something to run.

## Out of scope

- Any sweep, labelling, UI or analysis logic — stages 02 onward.
- `CITATION.cff`, `CHANGELOG.md`, `PUBLISHING_AUDIT.md`, the real `README.md` — stage 15.
- Creating the GitHub remote or the update site — stage 15 and the publishing skills.
- Moving `docs/segsweep-build/` into the new repo — do that at the end of this stage, and note it in
  the commit message so later stages know where to look.

## Files touched

| Path | Action | Reason |
|---|---|---|
| `pom.xml` | NEW | From CPC; identity fields per `01_NAMING.md` |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*` | NEW | Copied from CPC |
| `.gitignore`, `.gitattributes` | NEW | Copied from CPC |
| `LICENSE` | NEW | BSD-3-Clause, copied from CPC |
| `src/main/resources/plugins.config` | NEW | Menu registration |
| `src/main/java/segsweep/SegSweep_.java` | NEW | Stub entry class |
| `src/main/java/segsweep/LabelUtils.java` | NEW | Verbatim from CPC, package changed |
| `src/main/java/segsweep/ui/ToggleSwitch.java` | NEW | Verbatim from CPC, package changed |
| `src/test/java/segsweep/ScaffoldSmokeTest.java` | NEW | Proves the test harness runs |
| `.github/workflows/build-main.yml` | NEW | Copied from CPC |

## Implementation sketch

`src/main/resources/plugins.config` — exactly one line, and note the leading `Plugins,` with no
submenu (CPC uses `Plugins>CPC,` because it has several entries; this has one until v0.2.0):

```
Plugins, "Object Segmentation Sweep", segsweep.SegSweep_
```

`pom.xml` identity block:

```xml
<parent>
    <groupId>org.scijava</groupId>
    <artifactId>pom-scijava</artifactId>
    <version>43.0.0</version>
</parent>

<groupId>io.github.jay2owe</groupId>
<artifactId>Object-Segmentation-Sweep</artifactId>
<version>0.1.0-SNAPSHOT</version>
<name>Object Segmentation Sweep</name>
<description>Sweep segmentation settings across a range, review every result side by side as
label maps, and select a value from the object-count knee or parameter stability.</description>
```

Dependencies — this is the load-bearing part of the whole stage:

```xml
<dependencies>
    <dependency>
        <groupId>net.imagej</groupId>
        <artifactId>ij</artifactId>
    </dependency>
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Stub entry class:

```java
package segsweep;

import ij.IJ;
import ij.plugin.PlugIn;

public class SegSweep_ implements PlugIn {
    @Override
    public void run(String arg) {
        IJ.showMessage("Object Segmentation Sweep",
                "Under construction. See docs/segsweep-build/.");
    }
}
```

Build command, matching FLASH's known-good JDK setup:

```bash
export JAVA_HOME="/c/Users/Owner/OneDrive - Imperial College London/ImageJ/Experiments/First Experiment Round/Combined/Oracle_JDK-23"
bash mvnw clean package -Denforcer.skip=true
```

`-Denforcer.skip=true` is required, as in FLASH and CPC.

## Exit gate

1. `bash mvnw clean package -Denforcer.skip=true` succeeds from the repo root.
2. `target/Object-Segmentation-Sweep-0.1.0-SNAPSHOT.jar` exists.
3. `mvn dependency:tree` shows **no compile-scope dependency other than `ij` and its transitives** —
   grep the output for `mcib3d`, `sc.fiji`, `trove`, `smile`, `net.imagej.imagej` and confirm each
   returns nothing at compile scope.
4. `mvn test` runs and `ScaffoldSmokeTest` passes.
5. Copy the jar into the local Fiji `plugins/` folder, restart Fiji, and confirm
   **Plugins ▸ Object Segmentation Sweep** appears and shows the placeholder message.
6. `git log` shows one commit; the working tree is clean; branch is `main`.
7. `docs/segsweep-build/` is present inside the new repo.

## Known risks

- **`ij` version resolution.** `pom-scijava` manages the `ij` version; do not pin it. If the build
  fails on a missing `ij`, check that the parent POM resolved rather than adding a version by hand.
- **CPC's conflicted-copy files.** File synchronization has left `… (Jamie Malcolm's conflicted copy …).java` and
  `.xml` files in the CPC repo. Copying one of these instead of the real file will produce a build
  that looks fine and diverges silently. Check every source path before copying.
- **`plugins.config` encoding.** Must be plain ASCII with a trailing newline. A BOM makes Fiji skip
  the entry with no error message.
- **This repo lives inside a synchronized folder.** Run all git commands from
  the repo root — never from the synchronized parent folder, and be aware that the user profile is itself
  a git repo that will shadow if you get the working directory wrong.
