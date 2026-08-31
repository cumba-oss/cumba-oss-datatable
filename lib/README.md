# `lib/` — library modules

One sub-directory per library artifact, named exactly as its
artifactId. Library modules produce a plain jar, have no `mainClass`,
and inherit from the project parent pom (`../../pom.xml`).

## Modules

- [`cumba-oss-datatable`](cumba-oss-datatable/README.md) — contracts + SPI
- [`cumba-oss-datatable-impl`](cumba-oss-datatable-impl/README.md) — implementations, views, SPI factories
- [`cumba-oss-cdisc-define`](cumba-oss-cdisc-define/README.md) — Define-XML object model
- [`cumba-oss-datatable-provider-cdt`](cumba-oss-datatable-provider-cdt/README.md) — CDT provider
- [`cumba-oss-datatable-provider-csv`](cumba-oss-datatable-provider-csv/README.md) — CSV provider
- [`cumba-oss-datatable-provider-dsj`](cumba-oss-datatable-provider-dsj/README.md) — Dataset-JSON provider
- [`cumba-oss-datatable-provider-parquet`](cumba-oss-datatable-provider-parquet/README.md) — Parquet provider
- [`cumba-oss-datatable-provider-sas`](cumba-oss-datatable-provider-sas/README.md) — XPT / SAS7BDAT provider
- [`cumba-oss-datatable-provider-xlsx`](cumba-oss-datatable-provider-xlsx/README.md) — Excel provider
- [`cumba-oss-datatable-provider-define`](cumba-oss-datatable-provider-define/README.md) — Define-XML metadata provider
- [`cumba-oss-datatable-manager-local`](cumba-oss-datatable-manager-local/README.md) — local filesystem manager

Modules are listed in the parent pom's `<modules>` in dependency order,
though Maven's reactor derives the real build order itself.

## Adding a new library module

1. Create `lib/<artifact-id>/` with a `pom.xml` whose `<parent>` points
   at `../../pom.xml` and whose `<artifactId>` matches the directory
   name.
2. Add the directory to the top-level `<modules>` list in the parent
   pom (there are no longer `dev` / `main` profiles).
3. Add a `<dependency>` entry for it in the parent
   `<dependencyManagement>` so consumers need no version.
4. Add a `<dependency>` for it in `coverage/pom.xml` — that list *is*
   the aggregate coverage report. The `coverage-covers-every-module`
   enforcer rule fails the build if you forget.
