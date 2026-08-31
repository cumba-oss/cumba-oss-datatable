# cumba-oss-datatable-provider-csv

Read-only CSV provider for the Cumba OSS data-table SPI. Built on
[univocity-parsers](https://github.com/uniVocity/univocity-parsers)
(declared as a transitive dependency).

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-provider-csv</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.provider.csv.*`

## SPI registrations

- `IProviderSupplier` → `net.cumba.datatable.provider.csv.CsvProviderSupplier`,
  via `META-INF/services/net.cumba.datatable.provider.IProviderSupplier`,
  picked up by `DataTableProviderFactory.getFactory()` at runtime.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | the provider SPI contract |
| `cumba-oss-datatable-impl` | compile | `CachedDataTableColumn` / `ColumnCachedDataTable` |

## Notes


See the root [README](../../README.md) for project-wide context.
