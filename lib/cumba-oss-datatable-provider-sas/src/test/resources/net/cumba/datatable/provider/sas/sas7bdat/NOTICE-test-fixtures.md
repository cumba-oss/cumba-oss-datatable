# Third-party notice for the fixtures in this directory

Every `.sas7bdat` file in this directory is third-party test data. Each is used unchanged as an
input fixture of this module's SAS7BDAT tests and is not part of any shipped artifact; the
attribution manifest of this module covers shipped sources only, which is why the notice lives
beside the files instead. Each file is byte-identical to the upstream file named below.

| fixture | upstream file | origin | licence |
|---|---|---|---|
| `charset_wlt1.sas7bdat` | `charset_wlt1.sas7bdat` | EPAM **parso** 2.0.14 test data | Apache-2.0 |
| `comp_deleted_4x3.sas7bdat` | `comp_deleted.sas7bdat` | EPAM **parso** 2.0.14 test data | Apache-2.0 |
| `data_page_with_deleted_3x997.sas7bdat` | `data_page_with_deleted.sas7bdat` | EPAM **parso** 2.0.14 test data | Apache-2.0 |
| `doubles_12x25.sas7bdat` | `doubles.sas7bdat` | EPAM **parso** 2.0.14 test data | Apache-2.0 |
| `int_only_25x52.sas7bdat` | `int_only.sas7bdat` | EPAM **parso** 2.0.14 test data | Apache-2.0 |
| `mix_data_misc_14x12288.sas7bdat` | `mix_data_misc.sas7bdat` | EPAM **parso** 2.0.14 test data | Apache-2.0 |
| `mixed4_13x15564.sas7bdat` | `src/test/resources/org/thshsh/sas/bdat/mixed4_13x15564.sas7bdat` | **theshoeshiner/sas-utils** test data | Apache-2.0 |
| `numeric1_15x4733.sas7bdat` | `src/test/resources/org/thshsh/sas/bdat/numeric1_15x4733.sas7bdat` | **theshoeshiner/sas-utils** test data | Apache-2.0 |

- EPAM parso: https://github.com/epam/parso. The parso files carry the parso names in this stack's
  `cumba-parso` module. The `<name>_<columns>x<rows>` names used here are the ones
  theshoeshiner/sas-utils gives the same bytes in its own test resources (git blob ids compared
  against its commit `f447f5213e`).
- theshoeshiner/sas-utils: https://github.com/theshoeshiner/sas-utils. The licence is the one its
  `pom.xml` declares; the repository carries no `LICENSE` file. Where the two sas-utils files were
  made before they entered that repository is not known.
