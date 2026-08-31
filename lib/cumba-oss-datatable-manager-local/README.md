# cumba-oss-datatable-manager-local

Local file-system implementation of the cumba-oss-datatable manager /
library SPI. Lets a CLI or embedder browse a directory of CDISC
datasets (CSV / XPT / DSJ / Define-XML / …) without writing manager
code.

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-manager-local</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.datatable.manager.local.*` — `LocalDataTableManager` and
  its collaborators (cache support, library load support, ref
  implementations)
- `net.cumba.datatable.library.folder.*` — folder library provider

## SPI registrations

- `ILibrarySupplier` → `net.cumba.datatable.library.folder.FolderLibrarySupplier`,
  via `META-INF/services/net.cumba.datatable.library.ILibrarySupplier` — makes a
  filesystem directory browsable as a library.

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | the manager / library SPI |
| `cumba-oss-datatable-impl` | compile | base table / column / view impls |
| `cumba-oss-help` | compile | URI / file helpers |
| `cumba-oss-cdisc-define` | compile | Define-XML model for metadata loading |
| `cumba-oss-datatable-provider-define` | compile | Define→IMetadataLibrary adapter |
| `cumba-oss-datatable-provider-csv` | test | CSV provider exercised by the manager tests |

## Notes

- Covers `net.cumba.datatable.manager.local.*` and
  `net.cumba.datatable.library.folder.*`. Deliberately **absent**:
  - the `manager/local/core/` engine-integration helpers
  - the deprecated `filesystem/jnr/` Win32 native bindings
- `IDataTableManager` was trimmed from 31 methods to 14 — only the
  surface the engine and CLI actually use survives. Edit / Calcite /
  CORE-check / format-modification methods are gone.
- **Per-module JaCoCo override**: `jacoco.line.coverage` is `0.40` (vs
  the project default of `0.80`) — several private collaborator
  helpers lost test coverage when the trimmed-interface tests were
  removed. Raise back to `0.80` once focused tests on the OSS-side
  public surface land.

See the root [README](../../README.md) for project-wide context.
