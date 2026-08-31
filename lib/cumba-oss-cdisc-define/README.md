# cumba-oss-cdisc-define

Define-XML 2.x parser and DTO model. Reads Define-XML files into a Java
DTO tree (`DefineSupport`, `MetaDataVersion`, `ItemDef`, …) that
downstream modules consume as `IMetadataLibrary`.

Also provides `DefineXmlConverter`, a DOM-based version upgrader
(1.0→2.0, 2.0→2.1, and the composed 1.0→2.1) with content-based version
detection (`DefineXmlConverter.detectVersion`/`detectInputVersion`). It
preserves comments/ordering and reports best-effort 1.0→2.x inferences via
`getWarnings()`. Example:

```java
DefineXmlConverter.forFile(in)
        .to(DefineXmlConverter.Version.V2_1)
        .convert()
        .writeTo(out);
```

## Maven coordinates

```xml
<dependency>
    <groupId>net.cumba</groupId>
    <artifactId>cumba-oss-cdisc-define</artifactId>
    <version>${revision}</version>
</dependency>
```

## Java packages

- `net.cumba.cdisc.define.*`

## Dependencies

| Module | Scope | Why |
|---|---|---|
| `cumba-oss-help` | compile | CDT helpers + URI plumbing |

## Notes

- Pure parsing module — the SPI adapter that exposes the parsed model
  as `IMetadataLibrary` lives in `cumba-oss-datatable-provider-define`.

See the root [README](../../README.md) for project-wide context.
