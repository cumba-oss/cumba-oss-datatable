# cumba-oss-datatable-provider-parquet

Read-only Parquet provider for the Cumba OSS data-table SPI, built on the
[Carpet](https://github.com/jerolba/parquet-carpet) / parquet-mr libraries.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-provider-parquet</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.provider.parquet.*`

## SPI registrations

- `IProviderSupplier` → `net.cumba.datatable.provider.parquet.ParquetProviderSupplier`,
  via `META-INF/services/net.cumba.datatable.provider.IProviderSupplier`.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | the provider SPI contract |
| `cumba-oss-datatable-impl` | compile | cached column / table implementations |
| `com.jerolba:carpet-record` | compile | Parquet reading |
| `com.fasterxml.jackson.core:jackson-databind` | compile | decoding the `cumba:meta` metadata blob |
| `org.projectlombok:lombok` | provided | `@CustomLog` |
| `org.jspecify:jspecify` | compile | nullability annotations (NullAway) |

## Notes

- No exporter and no jrdata-based R-metadata decoding are included.
  Cumba-native column labels are still read from the `cumba:meta` blob
  by `ParquetMetadataCodec`.
- The Java package is `…provider.parquet`; it was `…provider.parquet2`
  upstream and was renamed on migration so package and artifactId agree.

See the root [README](../../README.md) for project-wide context.
