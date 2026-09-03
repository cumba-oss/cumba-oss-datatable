# Cumba OSS Datatable

The table model at the heart of the Cumba OSS stack — an `IDataTable` abstraction with sort/filter/merge views, a pluggable provider SPI for reading clinical datasets, a Define-XML object model, and a local-filesystem dataset manager.

Licensed under the **GNU Affero General Public License v3.0 only**
(see [`LICENSE`](LICENSE)).

## Provenance

Every module in this repository is **original work**. No module is derived
from, ported from, or adapted from third-party source, so no upstream
attribution or licence-retention obligation applies to the code here.

Third-party code is consumed only as ordinary **Maven dependencies**
(`carpet-record`, `univocity-parsers`, `excel-streaming-reader`, Jackson,
Lombok, JSpecify); those carry no obligation beyond not misrepresenting
them, and their licences are aggregated by `license-maven-plugin`
(`mvn license:add-third-party`).

> One point of possible confusion: `cumba-oss-datatable-provider-parquet`'s
> README notes its Java package was `…provider.parquet2` "upstream". That
> refers to the internal pre-migration Cumba codebase, not an external
> project.

Depends on **`cumba-oss-commons`** (for `cumba-oss-help`) and **`cumba-oss-formats`** (for `cumba-oss-sas-utils` and `cumba-oss-cdisc-dsj`). Both must be installed first.

## Modules

| Module | Java package | Purpose |
|---|---|---|
| [`cumba-oss-datatable`](lib/cumba-oss-datatable/README.md) | `net.cumba.datatable` | Contracts only: `IDataTable`, `IDataValue`, `ICondition`, format catalogs, and the custom SPI mechanism (`GenericServiceFactory` / `GenericProviderFactory`). |
| [`cumba-oss-datatable-impl`](lib/cumba-oss-datatable-impl/README.md) | `net.cumba.datatable.impl` | Concrete bases: `AbstractDataTable`, the columnar `IDataBuffer` family, sort / filter / merge views, the SPI factories. |
| [`cumba-oss-cdisc-define`](lib/cumba-oss-cdisc-define/README.md) | `net.cumba.cdisc.define` | Define-XML object model — parser, builder, and disambiguation modules. |
| [`cumba-oss-datatable-provider-cdt`](lib/cumba-oss-datatable-provider-cdt/README.md) | `net.cumba.datatable.provider.cdt` | CDT (Cumba Data Table) text format, single- and multi-dataset. |
| [`cumba-oss-datatable-provider-csv`](lib/cumba-oss-datatable-provider-csv/README.md) | `net.cumba.datatable.provider.csv` | CSV, built on univocity-parsers. |
| [`cumba-oss-datatable-provider-dsj`](lib/cumba-oss-datatable-provider-dsj/README.md) | `net.cumba.datatable.provider.dsj` | Dataset-JSON v1.1 (DSJ2), via `cumba-oss-cdisc-dsj`. |
| [`cumba-oss-datatable-provider-parquet`](lib/cumba-oss-datatable-provider-parquet/README.md) | `net.cumba.datatable.provider.parquet` | Parquet, via the Carpet library. |
| [`cumba-oss-datatable-provider-sas`](lib/cumba-oss-datatable-provider-sas/README.md) | `net.cumba.datatable.provider.sas` | XPT and SAS7BDAT, via `cumba-oss-sas-utils`. |
| [`cumba-oss-datatable-provider-xlsx`](lib/cumba-oss-datatable-provider-xlsx/README.md) | `net.cumba.datatable.provider.xlsx` | Excel, via Apache POI and excel-streaming-reader. |
| [`cumba-oss-datatable-provider-define`](lib/cumba-oss-datatable-provider-define/README.md) | `net.cumba.datatable.provider.define` | Exposes a parsed Define-XML document as a metadata library and format catalog. |
| [`cumba-oss-datatable-manager-local`](lib/cumba-oss-datatable-manager-local/README.md) | `net.cumba.datatable.manager.local` | Local filesystem manager — browse a directory of CDISC datasets without writing manager code. |

Each module has its own `README.md` with coordinates and dependency detail.

## The Cumba OSS repositories

```
cumba-oss-commons     help · web-api · cdisc-library · bootstrap
      ▲
cumba-oss-datatable   datatable · impl · cdisc-define · providers · manager-local
      ▲
cumba-oss-formats     sas-utils · datasetjson            (independent leaf)
```

Dependencies run in one direction only. Build order is
`cumba-oss-commons` → `cumba-oss-formats` → `cumba-oss-datatable`.

## Quick start

```bash
mvn -T1C clean install          # dev profile (default)
```

Artifacts are published under groupId `net.cumba` with the module's
artifactId, e.g.:

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable</artifactId>
    <version>${revision}</version>
</dependency>
```

## Build profiles

| Profile             | Active when               | Purpose                                       |
|---------------------|---------------------------|-----------------------------------------------|
| `PMD`               | unless `-DskipPmd`        | runs PMD at `verify`, **report-only** by default |
| `SpotBugs`          | unless `-DskipSpotbugs`   | runs SpotBugs at `verify`, **report-only** by default |
| `Pitest`            | `-P Pitest` or `-Dpitest.enabled=true` | **opt-in** mutation testing at `verify`, **report-only** by default |
| `ecj`               | `-P ecj`                  | second ECJ compile at `verify`                |
| `ErrPrn`            | `-P ErrPrn`               | Error Prone as a javac plugin                 |
| `spotless-check-mode` | `-Dspotless.check=true` | swaps Spotless from `apply` to `check`        |
| `pitest-fail-on-error` | `-Dpitest.failOnError=true` | promotes per-module `pitest.*.target` to the effective threshold (no-op without `Pitest` active) |
| `spotbugs-module-ignore` | per-module file `spotbugs_ignore.xml` exists | layers per-module SpotBugs filter |

## Static-analysis fail toggles

Every always-on check (Spotless, SpotBugs, PMD, Error Prone) runs
by default but produces a **report only** — they do not block the
build on findings. Pitest is **opt-in** (mutation testing is too slow
for inner-loop builds) and likewise report-only once activated. The
CI gate is opt-in:

| Property                       | Default | When set to `true`                                |
|--------------------------------|---------|---------------------------------------------------|
| `-Dspotbugs.failOnError=true`  | `false` | SpotBugs findings fail the build                  |
| `-Dpmd.failOnViolation=true`   | `false` | PMD findings fail the build                       |
| `-Derrprn.failOnWarning=true`  | `false` | Error Prone findings fail the build (requires `-P ErrPrn`) |
| `-Dspotless.check=true`        | `false` | Spotless switches to `check` mode; unformatted files fail the build (does not rewrite) |
| `-Dpitest.failOnError=true`    | `false` | Pitest mutation/coverage/test-strength thresholds are promoted from each module's `pitest.*.target` and enforced (requires the Pitest profile — see below) |

And the disable / opt-in switches:

| Property                    | Effect                                            |
|-----------------------------|---------------------------------------------------|
| `-DskipPmd`                 | skip the PMD profile entirely                     |
| `-DskipSpotbugs`            | skip the SpotBugs profile entirely                |
| `-Dpitest.enabled=true`     | opt in to the Pitest profile (equivalent to `-P Pitest`) |
| `-Derrprn.extraArgs=...`    | append args to Error Prone, e.g. enable NullAway   |

> **Pitest opt-in:** the `Pitest` profile is dormant by default.
> Activate it with `-P Pitest` (manual profile selection) or
> `-Dpitest.enabled=true` (property activation). `-Dpitest.failOnError=true`
> is a no-op on its own — it only promotes the thresholds inside the
> `pitest-fail-on-error` sub-profile, and without `Pitest` active the
> plugin doesn't run, so no thresholds are evaluated. Always combine,
> e.g. `mvn -P Pitest verify -Dpitest.failOnError=true`.

> **Pitest threshold gotcha:** do **not** pass
> `-Dpitest.mutation.threshold=…` on the command line — same trap as
> `-Djacoco.line.coverage`: a CLI `-D` clobbers every per-module
> override at once. Tune per-module by setting
> `<pitest.mutation.target>` (and `<pitest.coverage.target>`,
> `<pitest.test.strength.target>`) in the module's `pom.xml`, then
> let `-Dpitest.failOnError=true` promote them.

> **Fixed (was: "always pass `-P dev` or `-P main`").** The reactor used
> to live in two `activeByDefault` profiles, `dev` and `main`, carrying
> identical module lists. Maven deactivates *every* `activeByDefault`
> profile the moment any other profile activates — and `PMD`, `SpotBugs`
> and `NullAway` self-activate unconditionally via `!property` rules — so
> the profile was never actually active. Measured: `mvn validate` with no
> `-P` built the parent pom **alone** (1 module) while `-P main` built 13.
> CI escaped only because it always passed `-P main`, exercising a
> configuration no developer ever ran.
>
> `<modules>` is now declared at **top level**, where profile activation
> cannot switch it off, and the strict `-Xlint:all` that `dev` carried (and
> which had therefore never run) is unconditional. No `-P` is needed for
> anything; a stale `-P main` is just a Maven warning.

## Build commands

```bash
mvn -T1C clean install                            # full build, report-only checks
mvn -T1C test                                     # all tests
mvn -T1C verify -Dspotless.check=true             # CI: verify formatting without rewriting
mvn -T1C verify -Dspotbugs.failOnError=true -Dpmd.failOnViolation=true   # CI: hard gate
mvn -T1C -P Pitest verify                         # opt in to pitest, report-only
mvn -T1C -P Pitest verify -Dpitest.failOnError=true  # CI: pitest + enforce mutation/coverage targets
mvn -T1C verify -DskipPmd -DskipSpotbugs          # quick build, no static analysis (pitest already off)
mvn -T1C initialize sonar:sonar                   # SonarQube (initialize is required
                                                  # so sonar-exclusions.properties loads)
```

Standalone `mvn sonar:sonar` does **not** trigger `initialize`, so the
suppression file never loads — always invoke as
`mvn -T1C initialize sonar:sonar` (or any lifecycle command that
already includes the `initialize` phase, e.g.
`mvn -T1C verify sonar:sonar`).

## Build conventions

- **Java 25** (set via `<java.version>` and `maven.compiler.release`).
- **`<revision>` + `flatten-maven-plugin`** for CI-friendly versioning.
- **Lombok** as compile-time annotation processor; `@CustomLog`
  injects a `java.lang.System.Logger` field named `LOGGER` (see
  `lombok.config`).
- **Strict lint:** `failOnWarning=true` plus `-Xlint:all` (unconditional,
  in `maven-compiler-plugin`'s `pluginManagement` config) makes any javac
  warning a build failure.
- **Spotless** reformats Java sources in-place at `process-sources`
  (before compile) using the Eclipse JDT formatter and
  `eclipse-formatter.xml`. Imports are sorted, unused imports
  removed, trailing whitespace stripped. `mvn install` modifies your
  working tree as a side-effect; CI gates with `-Dspotless.check=true`
  to fail rather than rewrite.
- **SpotBugs** runs at `verify`, layered with
  `spotbugs_project_filter.xml` (always) plus the module's
  `spotbugs_ignore.xml` (auto-activated when present). **Report-only
  by default**; CI flips with `-Dspotbugs.failOnError=true`.
- **Surefire test-CWD isolation.** The forked test JVM's working
  directory is pinned to `${project.build.directory}/test-cwd` (i.e.
  `target/test-cwd/`). A test that resolves a relative path
  (`new File("foo")`, `Files.write(Path.of("out.txt"), …)`, …) lands
  inside `target/` and gets wiped by `mvn clean` instead of polluting
  the repo checkout. Tests that legitimately need the module root or
  the multi-module root read them from system properties Surefire
  exposes per fork: `System.getProperty("projectBasedir")` (the
  module's `${project.basedir}`) and `System.getProperty("repoRoot")`
  (`${maven.multiModuleProjectDirectory}`, i.e. the reactor root).
- **JaCoCo** enforces a per-module line-coverage minimum.
  `<jacoco.line.coverage>` defaults to `0.80` (80%). Override
  per-module by setting the property in the module's pom, or globally
  on the CLI with `-Djacoco.line.coverage=0.0`. Greenfield projects
  typically start at 0 and raise the bar as the test suite matures.
- **Pitest** mutation testing is **opt-in** (`-P Pitest` or
  `-Dpitest.enabled=true`) because mutation analysis is too slow for
  the inner loop. Once active it runs at `verify`, report-only by
  default; pair the opt-in with `-Dpitest.failOnError=true` to
  promote each module's `pitest.mutation.target` /
  `pitest.coverage.target` to the effective thresholds. Incremental
  analysis is enabled via the OSS `io.github.mibimiflo:pitest-history`
  SPI plugin (pitest 1.17+ removed its built-in OSS history reader);
  per-module history is written to
  `.pitest-history/<artifactId>/history.bin` at the **repo root**
  (outside any module's `target/`, so `mvn clean` does not wipe it),
  and the CI workflow caches the directory so warm-cache runs reuse it.
- **License aggregation** via `license-maven-plugin`. Run
  `mvn license:add-third-party` to generate `src/license/THIRD-PARTY.txt`.
  The plugin is wired into `pluginManagement` but no licenseUrl
  rewrites are configured by default — add them as needed.

## Adding a new module

1. Create a directory under `lib/`, named exactly as the artifactId.
2. Add a `pom.xml` with `<parent>` pointing at this root pom.
3. Add the directory to the top-level `<modules>` list.
4. Add a `<dependency>` entry for it in the parent `<dependencyManagement>`.
5. Add a `<dependency>` for it in `coverage/pom.xml` — that list *is* the
   aggregate coverage report. The `coverage-covers-every-module` enforcer
   rule fails the build if you forget.
