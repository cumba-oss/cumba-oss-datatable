# Test data

Fixtures read by this repository's tests.

## Provenance and licence of third-party test data

The CDISC example content in this folder comes from CDISC's Dataset-JSON repository:

- **Source:** `cdisc-org/DataExchange-DatasetJson`, <https://github.com/cdisc-org/DataExchange-DatasetJson>,
  folders `examples/adam/` and `examples/sdtm/`.
- **Compared against:** commit `a379f49a3f43c2aaed63bdeca761bdb7140df2c3` (HEAD of `master`, committed 2025-12-06),
  retrieved 2026-10-14. `examples/adam/adsl.xpt` was added in commit `8bb999d8` (2022-07-08).
- **Content:** CDISC's ADaM and SDTM example datasets, labelled study `CDISCPILOT01` (CDISC pilot study data as
  updated by the CDISC teams for these examples). They are example data, not real study data.
- **Licence:** the repository's `LICENSE`, the MIT License with the copyright line *"Copyright (c) 2022 cdisc"*,
  reproduced in full below. Nothing in that repository excludes `examples/` from it (inference: the repository-wide
  licence covers the example data).

```text
MIT License

Copyright (c) 2022 cdisc

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The MIT condition requires this notice in all copies or substantial portions; this section carries it for the files
listed below.

`github.com/phuse-org/phuse-scripts` holds sibling copies of the same pilot data (same data records, different file
headers). It is not the source of these files.

### Files

Paths are relative to this folder; md5 is of the file as committed. **identical** = byte-identical to the named source
file; **derived** = made from the source content, as described; **not identified** = the exact source could not be
shown. Value comparisons were made on 2026-10-14 by reading both files; "(inference)" marks what was not measured.

| Path | md5 | Relationship to the source |
|---|---|---|
| `xpt/01_plain/adsl.xpt` | `84d3f699a12934ffdeac0f90087330d5` | **identical** to `examples/adam/adsl.xpt` |
| `xpt/01_plain/adsl_no_attribs.xpt` | `476896d3d351fd2787b869ac21c12eec` | **derived**: the first 10 rows of `examples/adam/adsl.xpt` with labels and formats removed, re-exported by SAS 9.4; its data records are an exact prefix of the source's (measured) |
| `xpt/05_multiple/sdtm.xpt` | `b884d0d2779800db31aee94a5584d19c` | **derived**: one SAS transport library holding the 28 SDTM example datasets `examples/sdtm/<member>.xpt` (AE … VS); for every member the variable descriptors and the data records are byte-identical to the source file's, only the library and member headers differ (measured) |
| `sas7bdat/01_plain/adsl.sas7bdat` | `e5932a42b8aaa7d73b7f9ff217fb3043` | **not identified**: CDISCPILOT01 ADSL content; exact source not identified. All 254 rows × 49 columns hold the same values as `examples/adam/adsl.xpt` (measured), so it is a SAS7BDAT made from that file (inference) |
