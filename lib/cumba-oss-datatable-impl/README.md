# cumba-oss-datatable-impl

Concrete implementations on top of the `cumba-oss-datatable` contract:
`AbstractDataTable`, the plain `IDataBuffer` family (int / long / double
/ Object), sort / filter / merge views, and the SPI factories.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-impl</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.impl.*` (abstract base classes, view / merge /
  order / databuffer / index implementations, format catalog,
  cache + provider factories)

## SPI registrations

- `ILibrarySupplier` → `net.cumba.datatable.impl.library.dblib.DataBrowserLibrarySupplier`,
  via `META-INF/services/net.cumba.datatable.library.ILibrarySupplier` — makes
  the `.dblib` library format loadable through `LibraryProviderFactory`.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | the contract this module implements |
| `cumba-oss-help` | compile | CDT helpers used in impl code |

## Notes

- Does **not** carry the compression / dictionary-coding buffers retired in v1
  (`DataBufferFactor`, `DataBufferFactorDouble`, `DataBufferXBit`,
  `DataBuffer0Bit`, `AdjustingDataBuffer`, `DataBufferFloat`,
  `DataBufferString`, `FactorView`, `UnmappedValueScanner`).
- Buffer factory (`DefaultDataBufferFactory`) returns:
  - `DataBufferDouble` for `DOUBLE` value columns (NaN = missing)
  - `DataBufferLong` for `LONG` value columns (`Long.MIN_VALUE` =
    missing)
  - `DataBufferInt` for `BOOLEAN` value columns and small indexes
    (`Integer.MIN_VALUE` = missing)
  - `DataBufferObject` for `STRING` / `OTHER`
  - `IDataBufferNumeric createForRange(long min, long max)` picks int
    vs long backing.
- **Per-module JaCoCo override**: `jacoco.line.coverage` is `0.70` (vs
  the project default of `0.80`) until focused tests are added on the
  new plain `DataBufferInt` / `Long` / `Double` / `Object` impls.

See the root [README](../../README.md) for project-wide context.
