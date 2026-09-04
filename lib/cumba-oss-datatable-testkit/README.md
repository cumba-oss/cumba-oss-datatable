# cumba-oss-datatable-testkit

Test helpers for the [`cumba-oss-datatable`](../cumba-oss-datatable) contract, published as
ordinary **main** code so consumers depend on a normal jar instead of a `test-jar`.

| Class | What it builds |
|---|---|
| `MockTable` | a Mockito-backed `IDataTable` from a fluent column spec |
| `TestMetadataFixtures` | in-memory `IMetadataLibrary` / `IDataTableMetadata` / `IColumnMetadata` / `ICodeList` fixtures |

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-datatable-testkit</artifactId>
    <version>${revision}</version>
    <scope>test</scope>
</dependency>
```

## Java packages

- `net.cumba.datatable.testkit.*`

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-datatable` | compile | the `IDataTable` / `IMetadataLibrary` contracts the fixtures build against |
| `org.mockito:mockito-core` | compile | `MockTable` calls Mockito throughout — see rationale below |
| `org.jspecify:jspecify` | compile | nullability annotations (NullAway) |

Mockito is a **compile**-scope dependency here, deliberately. Consumers declare this testkit at
test scope, and a test-scoped dependency's transitives stay test-scoped, so compile-scope Mockito
cannot reach anyone's production classpath — the containment `provided` would buy is already
there. What `provided` would add is a worse failure mode: a consumer that forgets to declare
Mockito gets a `NoClassDefFoundError` at test *runtime* instead of a compile error.

## Why it exists

Both classes lived in `corej-core`'s **test** tree until 2026-09-01 and were reached across
the module boundary by a `<type>test-jar</type>` dependency. They name nothing from `cdisc-core` —
only `net.cumba.datatable.*`, Mockito, jspecify and `java.*` — so they belong to datatable, and a
published datatable artifact must not carry `net.cumba.corej.core.*` packages: that is a permanent
split package against `cdisc-core`'s own test tree, unfixable once on Central. Hence
`net.cumba.datatable.testkit`. Decided during the internal `PLAN-corej-restructure` migration §4
(that plan lives in the internal monorepo, not this repo).

## ⚠ Two things to know before writing a fixture

- **`MockTable` indexes columns by KIND, not declaration order** — `col` → `colSasMissing` →
  `colLong` → `colDouble`. Declaring `col("S",…).colLong("L",…).colDouble("D",…).colSasMissing("M",…)`
  yields indices `S=0, M=1, L=2, D=3`. Pinned by
  `MockTableTest.mixedColumnKindsAreIndexedByKIND_notByDeclarationOrder`.
- **Every column must declare the same row count.** The count is taken from whichever column
  `build()` drains first and used to index the rest; a shorter column throws
  `ArrayIndexOutOfBoundsException` rather than saying so.

`colSasMissing` types its column from its data: all-numeric present values give a `LONG` column
answering boxed `Long`s, anything else gives a `STRING` column. Missing cells are
`MissingValue.MIS` rendering `"."` either way — that is what keeps fold-detection assertions
non-vacuous.

See the root [README](../../README.md) for project-wide context.
