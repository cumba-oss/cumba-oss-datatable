# cumba-oss-datatable-provider-dsj

Dataset-JSON v1.1 (DSJ2) provider for the Cumba OSS data-table SPI. Reads
CDISC Dataset-JSON files into `IDataTable` instances using the
`cumba-oss-datasetjson` Jackson model.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-provider-dsj</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.provider.dsj.*`

## SPI registrations

- `IProviderSupplier` → `net.cumba.datatable.provider.dsj.DsjProviderSupplier`,
  via `META-INF/services/net.cumba.datatable.provider.IProviderSupplier`,
  picked up by `DataTableProviderFactory.getFactory()` at runtime.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | provider SPI |
| `cumba-oss-datatable-impl` | compile | cached column infra |
| `cumba-oss-datasetjson` | compile | Dataset-JSON DTO model |

## Notes

Only the Dataset-JSON v1.1 (DSJ2) reader ships; the older DSJ v1.0
provider is not part of this project.

See the root [README](../../README.md) for project-wide context.
