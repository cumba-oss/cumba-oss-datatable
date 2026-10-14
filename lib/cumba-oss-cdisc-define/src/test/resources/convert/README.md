# Define-XML conversion fixtures — provenance and licence

The three `define-v*-sdtm.xml` files here are third-party CDISC example Define-XML documents (example metadata, no
study data), used as input for the conversion tests. Per the project owner (2026-10-14), the third-party test files of this project were taken from three repositories:
`cdisc-org/DataExchange-DatasetJson`, `cdisc-org/sdtm-adam-pilot-project` and `phuse-org/phuse-scripts`.

md5 is of the file as committed. **identical** = byte-identical to the named source file; **derived** = made from
the source content, as described; **not identified** = the exact source could not be shown. Comparisons were made on
2026-10-14 against the GitHub copies at the commits named; "(inference)" marks what was not measured.

| Path | md5 | Relationship to the source |
|---|---|---|
| `define-v10-sdtm.xml` | `487a6d98c8bc99e6fdcea26040dbad22` | **derived** from `updated-pilot-submission-package/900172/m5/datasets/cdiscpilot01/tabulations/sdtm/define.xml` (`sdtm-adam-pilot-project`, md5 `ecbefff4cf1879b6ebc606e76706fc87`): pruned to the six domains AE, DM, DS, LB, RELREC and TA with their item, code-list, method and comment definitions, re-serialised (attribute order, layout), and the six archive-location leaves renamed from `<domain>.xpt` to `<domain>.dsjc`. Every one of its 208 identified elements is present in the source; 194 are equal in attributes and content, the other 14 differ only by those leaf names (measured, element-wise). Same bytes as `cumba-corej-rules` `src/test/resources/testdata_study/sdtm/define.xml`, which `PruneDefineXmlDriver` there produces from the full pilot define (inference: this copy was taken from it) |
| `define-v20-sdtm.xml` | `b0f0b6f02168481569a0b9a91189ee4e` | **not identified**: the CDISC Define-XML 2.0 SDTM example (header: *"File: define2-0-0-example-sdtm.xml, Author: CDISC XML Technologies Team"*). The closest published copy is `phuse-2018/define.cdisc.sdtm.xml` in `cdisc-org/define-xml-2.0-stylesheets` (commit `e94be4ec`, file last changed `34ec2a9b`, 2022-03-15; MIT, *"Copyright (c) 2020 Lex Jansen"*); this file differs from it in 80 / 21 lines (measured `diff`): AETERM has a value-level list with two where clauses and length 200 (25 there), the TS dataset declares 11 variables with roles (6 there; added TSGRPID, TSVALNF, TSVALCD, TSVCDREF, TSVCDVER), the TS leaf names `ts.xml` (`ts.xpt` there) and the stylesheet reference differs. Not found in the three repositories named by the owner: `phuse-scripts` `development/R/data/define2_sdtm.xml` carries the same header but is a 16 809-byte cut-down (measured, commit `398a6d33`), and no other file there or in the other two is this example; it may come from the CDISC Define-XML 2.0 release package (inference; those terms were not read) |
| `define-v21-sdtm.xml` | `cba534dfd19fc379fe40050fbc47824e` | **derived** from `examples/sdtm/define.xml` as of commits `925754def9` / `de5dc15f` (md5 `e695e993…`): 57 of 11 789 lines differ (measured `diff`): the 28 `def:leaf` archive locations and titles name `<dataset>.xml` instead of `<dataset>.json` (56 lines), and `IT.EX.EXROUTE` carries an added `def:CommentOID="COM.DTC1"`. Against HEAD (`.xpt` leaves since `bd3a79ad`) it also differs in `CreationDateTime`/`AsOfDateTime` and `def:Context` (`Other` here, `Submission` there). The `.xml` leaves are the original MSG v2 (Dataset-XML) naming, so this may be an earlier release of the same CDISC example (inference); no published copy with these exact bytes was found |

## Sources

### `cdisc-org/sdtm-adam-pilot-project`

- **Repository:** <https://github.com/cdisc-org/sdtm-adam-pilot-project>; HEAD of `master` at retrieval was
  `667511d4b183871d74392ba691c935c38d431d39` (committed 2024-12-20), retrieved 2026-10-14. The files used were last
  changed in commit `2f1a3eb6` (2019-05-23).
- **Content:** the CDISC SDTM/ADaM pilot submission package, study `CDISCPILOT01` (example data, not real study data).
- **Terms:** the repository has **no** `LICENSE` file. Its `README.md` ("DISCLAIMER AND LIABILITY", "Terms of Use")
  says, in the part bearing on redistribution: *"By downloading / using this data, you agree to the Terms of use. …
  You shall not (and will not allow or assist any third party to), under any circumstances: … (b) disassemble,
  reverse engineer, decompile, modify, or alter any part of the Data; … (e) sublicense or distribute the data for a
  fee, or (f) sublicense or distribute the data without an attribution back to CDISC."* This section is that
  attribution: the data is CDISC's, from the CDISC SDTM/ADaM pilot project (study `CDISCPILOT01`).

### `cdisc-org/DataExchange-DatasetJson`

- **Repository:** <https://github.com/cdisc-org/DataExchange-DatasetJson>; HEAD of `master` at retrieval was
  `a379f49a3f43c2aaed63bdeca761bdb7140df2c3` (committed 2025-12-06), retrieved 2026-10-14.
- **File:** the CDISC Metadata Submission Guidelines v2 example Define-XML 2.1 (header: *"File: defineV21-MSGv2.xml,
  Author: CDISC MSG v2 Team"*, `Originator="CDISC MSG Team"`). It entered the repository in commit `925754def9`
  (2021-06-25) as `examples/Metadata_Submission_Guide_2_0/Jozef/define.xml` (md5 `e695e993c264aef6edf6e07dc022ef16`),
  was moved unchanged to `examples/sdtm/define.xml` in commit `de5dc15f` (2022-04-01), and was edited in commit
  `bd3a79ad` (2023-06-28; md5 at HEAD `1d20c8a430457af2c50a820b22e40e1e`).
- **Licence:** the repository's `LICENSE`, the MIT License with the copyright line *"Copyright (c) 2022 cdisc"*,
  reproduced in full below. Nothing in that repository excludes `examples/` from it (inference: the repository-wide
  licence covers the example files).

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
derived from this repository.
