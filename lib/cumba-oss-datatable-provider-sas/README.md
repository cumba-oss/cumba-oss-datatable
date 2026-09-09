# cumba-oss-datatable-provider-sas

Read-only SAS provider for the Cumba OSS data-table SPI — XPT transport
files (single dataset and multi-member libraries) and SAS7BDAT data sets —
built on `cumba-oss-sas-utils`.

## Attribution

`xpt/ObservationIteratorXpt.java` is **derived from
[theshoeshiner/sas-utils](https://github.com/theshoeshiner/sas-utils)** by **theshoeshiner**,
used and adapted under the **Apache License, Version 2.0**. It is a copy of that project's XPT
observation iterator, carried here so the provider can stream observations without going through
the reader library's own model; a sibling copy lives in the sas-utils reader module. A copy of the
licence sits alongside this README as [`LICENSE-APACHE-2.0.txt`](LICENSE-APACHE-2.0.txt), and
travels inside the jar at `META-INF/LICENSE-APACHE-2.0.txt`.

> Upstream carries no `LICENSE` file; the Apache-2.0 grant is declared in its `pom.xml`
> `<licenses>` block. No upstream revision is recorded, so attribution pins the literal
> **`unknown`** rather than inventing one — byte-identical to the sas-utils reader module's entry,
> so the About dialog renders one row and not two.

`src/main/resources/licenses.xml` carries the matching entry; that file is the only channel that
puts this attribution into the application's About dialog. `AttributionTest` pins the notice, the
licence files and the tuple.

⚠ **Every other source file in this module is P300's own** and is allow-listed as `NOT_DERIVED`
in `src/test/resources/attribution-manifest.tsv`. That allow-list is not a formality: a new file
with no row fails the build, which is what stops a second copied class arriving unattributed the
way this one did.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-provider-sas</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.provider.sas.sas7bdat.*`
- `net.cumba.datatable.provider.sas.xpt.*`
- `net.cumba.datatable.provider.sas.xpt.library.*`

## SPI registrations

- `IProviderSupplier` → `…sas.xpt.XptProviderSupplier` and
  `…sas.sas7bdat.BdatProviderSupplier` (both listed in the same services file).
- `ILibrarySupplier` → `…sas.xpt.library.XptLibrarySupplier`, so a
  multi-member XPT file is browsable as a library.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-sas-utils` | compile | the XPT / SAS7BDAT binary readers |
| `cumba-oss-datatable` | compile | the provider SPI contract |
| `cumba-oss-datatable-impl` | compile | cached column / table implementations |
| `cumba-oss-help` | compile | URI / string helpers |
| `org.projectlombok:lombok` | provided | `@CustomLog` |
| `org.jspecify:jspecify` | compile | nullability annotations (NullAway) |

## Notes

- No exporter and no SAS7BCAT format-catalog support are included.
- Three test classes read fixtures from the repository-root `testdata/`
  directory, resolved through the `repoRoot` system property that Surefire
  sets to `${maven.multiModuleProjectDirectory}`.

See the root [README](../../README.md) for project-wide context.
