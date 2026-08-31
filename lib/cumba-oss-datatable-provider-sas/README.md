# cumba-oss-datatable-provider-sas

Read-only SAS provider for the Cumba OSS data-table SPI — XPT transport
files (single dataset and multi-member libraries) and SAS7BDAT data sets —
built on `cumba-oss-sas-utils`.

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
