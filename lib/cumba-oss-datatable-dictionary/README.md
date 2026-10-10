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
