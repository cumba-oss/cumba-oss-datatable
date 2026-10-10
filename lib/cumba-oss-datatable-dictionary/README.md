# cumba-oss-datatable-dictionary

The external-dictionary code the OSS conformance engine reads and seeds its dictionary store with
(MedDRA, WHODrug, LOINC, UNII, SNOMED CT, MED-RT, the SEND neoplasm classes), shared by the engine
(`cumba-oss-corej-core`) and its command-line client (`cumba-oss-corej-cli`, `--install-dictionaries`).

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-dictionary</artifactId>
</dependency>
```

Depends on Jackson (`jackson-core`, `jackson-databind`) and JSpecify only.

## Packages

| package | what |
|---|---|
| `net.cumba.datatable.dictionary.house` | `ValueMapDictionary` — the immutable model of one house-format dictionary file (house formats 1 and 2) in two load profiles (`FULL`, everything the rule functions read; `FORMAT`, the streamed subset a format reader needs); `LevelScopedHierarchy`; `HouseDictionaryReader` (streaming); `DictionaryFileCache` (process-wide, size + mtime revalidated, LRU 16); `RuntimeDictionaryProvider` and `DictionaryStore` (the engine's provider and store binder); `HouseFormatValidator`; `DictionaryStoreReader`, `DictionaryVersions`, `DictionaryTypes`, `DictionaryLevels` |
| `net.cumba.datatable.dictionary.convert` | the seven converters, `DictionaryInstaller`, `InstallReport`, the sources (`LocalDictionarySource`, `HttpDictionarySource`), `RawDictionaryFiles`, `DictionaryLicences` (+ `/dictionary-licences/`), `DictionaryDirectoryResolver` |

Both packages are byte-identical with `cumba-datatable-dictionary`'s (guarded by the internal
`check_oss_identity.py`, PINNED set `datatable-dictionary`). That module's third package, `format`
(dictionary-backed formats for a data table's format catalog), is not carried here: this repository
has no format catalog.

## House format 2

The installer writes `"house_format": 2`. Its `decodes` section (level → code → decode) gives the
code levels of MedDRA (`LLTCD` … `SOCCD`) and WHODrug (`ATCCD`, and the drug codes of the new
`DRUGCD` level) their names. It lies outside `pairs` and `attributes`, so the pair lookups of the
rule functions cannot see it and no rule verdict depends on the format version. A reader of house
format 1 ignores the field and the section.

Installer behaviour (`convert.DictionaryInstaller`):

- The header is written **first** — `type`, `version`, `source`, `retrieved`, `house_format` — so a
  header read stops after those five fields.
- A `decodes` entry is **not content**: a file holding only `decodes` is treated as not loaded and
  its rules SKIP.
- Each house file is written to `<name>.tmp` and moved into place atomically; a failed write leaves
  the previous file intact.
- `DictionaryInstaller.Selection`: `KEEP` (default) leaves an existing `selected-versions.json`
  entry alone, `NEW_VERSION` rebinds only to a version not yet in the store, `ALWAYS` rebinds also
  to one already installed (the CLI's `--set-default`). The first install of a type always binds.
- An install may name the version it expects; the installer refuses, writing nothing, when its own
  version read of the distribution disagrees.
- A local distribution's files are digested as they are read; `SOURCES.md` records their SHA-256.
