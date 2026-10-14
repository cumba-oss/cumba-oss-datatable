# Define-XML conversion fixtures — provenance and licence

The three `define-v*-sdtm.xml` files here are CDISC example Define-XML documents (example metadata, no study data),
used as input for the conversion tests. All three come from one MIT-licensed CDISC example,
`cdisc-org/DataExchange-DatasetJson` `examples/sdtm/define.xml` (Define-XML 2.1): `define-v21-sdtm.xml` is an
earlier revision of it, and `define-v20-sdtm.xml` / `define-v10-sdtm.xml` are **down-converted** from it by
[`derive_define_fixtures.py`](derive_define_fixtures.py) beside this file (2026-10-14, `PLAN-mit-fixture-resourcing`;
they replace a Define-XML 1.0 file pruned from `cdisc-org/sdtm-adam-pilot-project`, whose terms forbid modification,
and a Define-XML 2.0 file whose source could not be identified).

md5 is of the file as committed. **identical** = byte-identical to the named source file; **derived** = made from
the source content, as described; **not identified** = the exact source could not be shown. Comparisons were made on
2026-10-14 against the GitHub copies at the commits named; "(inference)" marks what was not measured.

| Path | md5 | Relationship to the source |
|---|---|---|
| `define-v10-sdtm.xml` | `331ea9a47c5c4ffdbf6ffc9b52250061` | **derived** from `cdisc-org/DataExchange-DatasetJson` `examples/sdtm/define.xml` @ `a379f49a` (md5 `1d20c8a4…`), MIT, down-converted by `derive_define_fixtures.py` (2.1 → 2.0 → 1.0, the mappings listed in the script's header): Define-XML 1.0.0 / CRT-DDS, 28 datasets, 1.0 value-level metadata by the `ValueList.<DS>.<VAR>` / `<DS>.<VAR>.<value>` naming convention (10 value lists, 97 value-level items; where-clauses 1.0 cannot express — `IN`, `NE`, values with spaces — are dropped). Checked by the script: CDISC ODM 1.2.1 schema on the ODM core, structural checks S1–S6 on the 1.0 extension |
| `define-v20-sdtm.xml` | `8919c399b5130f4b820adecd3c74562b` | **derived** from `cdisc-org/DataExchange-DatasetJson` `examples/sdtm/define.xml` @ `a379f49a` (md5 `1d20c8a4…`), MIT, down-converted by `derive_define_fixtures.py` (2.1 → 2.0): Define-XML 2.0.0, 28 datasets (the source's 31 minus the three `def:HasNoData` datasets NV, SUPPNV, SUPPOE, which 2.0 cannot declare). Validated by the script with `xmllint --schema` against the CDISC Define-XML 2.0 schema vendored in `cumba-corej` (`cumba-corej-define-conformance` `xsd/define-2-0/`, referenced, not copied here) |
| `define-v21-sdtm.xml` | `cba534dfd19fc379fe40050fbc47824e` | **derived** from `examples/sdtm/define.xml` as of commits `925754def9` / `de5dc15f` (md5 `e695e993…`): 57 of 11 789 lines differ (measured `diff`): the 28 `def:leaf` archive locations and titles name `<dataset>.xml` instead of `<dataset>.json` (56 lines), and `IT.EX.EXROUTE` carries an added `def:CommentOID="COM.DTC1"`. Against HEAD (`.xpt` leaves since `bd3a79ad`) it also differs in `CreationDateTime`/`AsOfDateTime` and `def:Context` (`Other` here, `Submission` there). The `.xml` leaves are the original MSG v2 (Dataset-XML) naming, so this may be an earlier release of the same CDISC example (inference); no published copy with these exact bytes was found |

## Sources

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

## Regenerating `define-v20-sdtm.xml` and `define-v10-sdtm.xml`

[`derive_define_fixtures.py`](derive_define_fixtures.py) (Python ≥ 3.9, standard library only, plus `xmllint`) is a
hand-written down-converter, deliberately independent of the converter these files test (`Step10To20` /
`Step20To21`): running that converter backwards would make the fixtures confirm themselves. Its header lists every
mapping; it refuses any input whose md5 is not the `a379f49a` file's.

```bash
git clone https://github.com/cdisc-org/DataExchange-DatasetJson && git -C DataExchange-DatasetJson checkout a379f49a
python3 derive_define_fixtures.py --source DataExchange-DatasetJson/examples/sdtm/define.xml             # writes both
python3 derive_define_fixtures.py --source DataExchange-DatasetJson/examples/sdtm/define.xml --check     # compare only
python3 derive_define_fixtures.py --source DataExchange-DatasetJson/examples/sdtm/define.xml --self-test # plant defects
```

Nothing is written unless every oracle passes:

- **2.0:** `xmllint --schema` against CDISC's Define-XML 2.0 schema, found by walking up from the script to
  `cumba-corej` (or `cumba-oss-corej`) `…-define-conformance/src/main/resources/xsd/define-2-0/cdisc-define-2.0/
  define2-0-0.xsd` (MIT; provenance in that directory's `README.md`), or `--define20-xsd`. No copy is kept here.
- **1.0:** no CDISC-hosted Define-XML 1.0 schema under a usable licence was found (2026-10-14: `cdisc-org` has no
  repository carrying `define1-0-0.xsd`; the only public copy GitHub code search found sits inside another
  validation toolkit, which this project does not use). The 1.0 file is therefore checked (a) on its **ODM core** —
  every `def:` element and attribute stripped, the rest validated with `xmllint --schema` against CDISC's ODM 1.2.1
  schema (`cdisc-org/DataExchange-ODM` `archive/ODM1-2-1/ODM1-2-1-foundation.xsd` @ `6cb00a9c`, MIT,
  sha256-pinned, downloaded at run time unless `--odm121-foundation` names a local copy; not kept here) — and (b)
  **structurally** by checks S1–S6 written down in the script's `check_v10`: namespaces and versions; the `def:`
  vocabulary limited to 1.0 (what CDISC's 2.0 schema keeps from 1.0 or lists as deprecated 1.0 constructs); no 2.x
  construct left; every dataset with label, structure, class, domain keys naming its own variables and an archive
  location resolving to its leaf; every reference resolving and nothing orphaned; value lists following the 1.0
  naming convention.
- `--self-test` plants defects (three for 2.0, eight for 1.0 covering S1–S7) and fails unless each one
  is reported.
