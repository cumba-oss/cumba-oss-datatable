# cumba-oss-datatable

Interface contracts for tabular data, values, conditions, metadata,
formats, indexes, reports, library / manager SPI, and data-buffer
storage. The foundational contract module — every other Cumba OSS module
depends on it.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.*` (root interfaces: `IDataTable`,
  `DataTableMeta`, `DataTableColumnMeta`, …)
- `net.cumba.datatable.formats.*` (format / catalog contracts)
- `net.cumba.datatable.index.*` (index SPI)
- `net.cumba.datatable.io.*` (`FileInfo`, `Property`, generic
  provider/supplier factories)
- `net.cumba.datatable.library.*` (library member SPI)
- `net.cumba.datatable.manager.*` (manager SPI: `IDataTableManager`,
  `IDataTableRef`, `IDataTableLibraryRef`, …)
- `net.cumba.datatable.metadata.*`, `net.cumba.datatable.order.*`,
  `net.cumba.datatable.provider.*`, `net.cumba.datatable.report.*`,
  `net.cumba.datatable.values.*`, `net.cumba.datatable.view.*`

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-help` | compile | string / URI / file helpers |

## Notes

Define new SPI here, implement in `cumba-oss-datatable-impl`.

See the root [README](../../README.md) for project-wide context.
