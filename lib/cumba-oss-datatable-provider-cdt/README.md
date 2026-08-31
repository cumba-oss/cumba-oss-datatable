# cumba-oss-datatable-provider-cdt

Reader for the `.cdt` (CDISC datatable text) format — the rule-test
corpus uses these as fixtures.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-provider-cdt</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.provider.cdt.*`

## SPI registrations

- `IProviderSupplier` → `net.cumba.datatable.provider.cdt.CdtProviderSupplier`.
- `ILibrarySupplier` → `net.cumba.datatable.provider.cdt.library.CdtLibrarySupplier`.

Both are declared under `META-INF/services/` and picked up by
`DataTableProviderFactory` / `LibraryProviderFactory` at runtime.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | provider SPI |
| `cumba-oss-datatable-impl` | compile | base classes / cached column infra |

## Notes

- Read-only — no `.cdt` writer paths are included (no engine code
  consumed them).
- **Per-module JaCoCo override**: `jacoco.line.coverage` is `0.55` (vs
  the project default of `0.80`) because the round-trip / coverage
  tests that exercised the write side were removed alongside the
  exporter.

See the root [README](../../README.md) for project-wide context.
