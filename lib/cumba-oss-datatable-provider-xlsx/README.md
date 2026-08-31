# cumba-oss-datatable-provider-xlsx

Read-only Excel provider for the Cumba OSS data-table SPI, built on
[excel-streaming-reader](https://github.com/pjfanning/excel-streaming-reader)
and Apache POI.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-provider-xlsx</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.provider.xlsx.*`
- `net.cumba.datatable.provider.xlsx.library.*`

## SPI registrations

- `IProviderSupplier` → `net.cumba.datatable.provider.xlsx.ExcelProviderSupplier`.
- `ILibrarySupplier` → `net.cumba.datatable.provider.xlsx.library.ExcelLibrarySupplier`,
  so a workbook is browsable as a library of sheets.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | the provider SPI contract |
| `cumba-oss-datatable-impl` | compile | cached column / table implementations |
| `com.github.pjfanning:excel-streaming-reader` | compile | streaming XLSX reading (wraps Apache POI) |
| `org.projectlombok:lombok` | provided | `@CustomLog` |
| `org.jspecify:jspecify` | compile | nullability annotations (NullAway) |

## Notes

- No exporter is included.
- Streaming reader: a workbook is read sheet by sheet without loading the
  whole file into memory.

See the root [README](../../README.md) for project-wide context.
