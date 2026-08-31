# cumba-oss-datatable-provider-define

Adapter that exposes a parsed Define-XML document as a Cumba OSS
`IMetadataLibrary`. Bridges `cumba-oss-cdisc-define` and the
`cumba-oss-datatable` SPI so loading Define-XML through the manager works
identically to loading any other library.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-provider-define</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.provider.define.*` — parser → metadata library
  adapter (e.g. `DefineMetadataLibrary`)

## SPI registrations

- `ILibrarySupplier` → `net.cumba.datatable.provider.define.library.DefineXmlLibrarySupplier`,
  via `META-INF/services/net.cumba.datatable.library.ILibrarySupplier`,
  picked up by `LibraryProviderFactory` at runtime.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | the library / member SPI |
| `cumba-oss-datatable-impl` | compile | base class utilities |
| `cumba-oss-help` | compile | URI / string helpers |
| `cumba-oss-cdisc-define` | compile | the Define-XML parsed model |

## Notes


See the root [README](../../README.md) for project-wide context.
