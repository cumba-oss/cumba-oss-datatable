package net.cumba.datatable.impl.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.impl.merge.RelrecPairerCompat.RelrecPair;
import net.cumba.datatable.impl.merge.RelrecPairerCompat.RelrecPairing;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/** Unit tests for the ported RELREC pairing logic in {@link RelrecPairer}. */
class RelrecPairerTest
{

    private static IDataTable table(String aName, String[] aCols, DataValueType[] aTypes,
            Object[][] aData)
    {
        int colCount = aCols.length;
        CachedDataTableColumn[] columns = new CachedDataTableColumn[colCount];
        DataTableColumnMeta[] metas = new DataTableColumnMeta[colCount];
        for (int c = 0; c < colCount; c++)
        {
            columns[c] = new CachedDataTableColumn(c, aTypes[c]);
            metas[c] = DataTableColumnMeta.builder().index(c).name(aCols[c]).type(aTypes[c])
                    .build();
        }
        for (Object[] row : aData)
        {
            for (int c = 0; c < colCount; c++)
            {
                columns[c].addElement(row[c]);
            }
        }
        for (CachedDataTableColumn col : columns)
        {
            col.complete();
        }
        DataTableMeta meta = DataTableMeta.builder().name(aName).columns(metas)
                .rowCount(aData.length).totalRowCount(aData.length).build();
        return new ColumnCachedDataTable(meta, columns);
    }

    private static final String[] RELREC_COLS =
    {
            "STUDYID", "RDOMAIN", "USUBJID", "IDVAR", "IDVARVAL", "RELID"
    };

    private static final DataValueType[] STR6 =
    {
            DataValueType.STRING, DataValueType.STRING, DataValueType.STRING, DataValueType.STRING,
            DataValueType.STRING, DataValueType.STRING
    };

    private static Function<String, IDataTable> resolver(IDataTable aFa)
    {
        Map<String, IDataTable> map = aFa == null ? Map.of() : Map.of("FA", aFa);
        return map::get;
    }


    @Test
    void datasetLevel_oneToMany()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ", "AELNKID"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.LONG, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "S01", "U01", 1L, 1L
                },
                {
                        "S01", "U01", 2L, 2L
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ", "FALNKGRP"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.LONG, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "S01", "U01", 1L, 1L
                },
                {
                        "S01", "U01", 2L, 1L
                },
                {
                        "S01", "U01", 3L, 2L
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "", "AEFA"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "AEFA"
                }
        });

        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, p.relatedTables().size());
        assertEquals(3, p.pairs().size());
        // AE row 0 (AELNKID=1) -> FA rows 0,1 ; AE row 1 (AELNKID=2) -> FA row 2.
        assertEquals(List.of(0L, 0L, 1L), p.pairs().stream().map(RelrecPair::primaryRow).toList());
        assertEquals(List.of(0L, 1L, 2L), p.pairs().stream().map(RelrecPair::relatedRow).toList());
        assertTrue(p.pairs().stream().allMatch(x -> "AEFA".equals(x.relid())));
        assertTrue(p.pairs().stream().allMatch(x -> "FA".equals(x.rdomain())));
    }


    @Test
    void recordLevel_filtersByIdvarval()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "S01", "U01", 1L
                },
                {
                        "S01", "U01", 2L
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "S01", "U01", 1L
                },
                {
                        "S01", "U01", 2L
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AESEQ", "1", "R1"
                },
                {
                        "S01", "FA", "U01", "FASEQ", "2", "R1"
                }
        });

        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, p.pairs().size());
        assertEquals(0L, p.pairs().get(0).primaryRow()); // AESEQ=1 -> AE row 0
        assertEquals(1L, p.pairs().get(0).relatedRow()); // FASEQ=2 -> FA row 1
    }


    @Test
    void datasetLevel_numericKeyCoercion()
    {
        // AE link "1" (String) vs FA link "1.0" (String) must still join via normKey.
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1.0"
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "", "AEFA"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "AEFA"
                }
        });

        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, p.pairs().size());
    }


    @Test
    void missingRelrecColumns_returnsEmpty()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "S01", "U01"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "S01", "U01"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(null), null);
        assertTrue(p.pairs().isEmpty());
    }


    @Test
    void unresolvedRelatedDomain_producesNoPairs()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1"
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "", "AEFA"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "AEFA"
                }
        });
        // resolver(null) cannot resolve FA -> no related tables, no pairs.
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(null), null);
        assertTrue(p.relatedTables().isEmpty());
        assertTrue(p.pairs().isEmpty());
    }


    @Test
    void nullRelrec_returnsEmpty()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "S01", "U01"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(null, ae, "AE", resolver(null), null);
        assertTrue(p.pairs().isEmpty());
        assertTrue(p.relatedTables().isEmpty());
    }


    @Test
    void mixedRecordAndDatasetLevel_skipsDomain()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ", "AELNKID"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.STRING,
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "S01", "U01", "1", "L1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ", "FALNKGRP"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.STRING,
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "S01", "U01", "1", "L1"
                }
        });
        // FA participates both dataset-level (blank IDVARVAL) and record-level (populated) -> skip.
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "", "G1"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "G1"
                },
                {
                        "S01", "AE", "U01", "AESEQ", "1", "G2"
                },
                {
                        "S01", "FA", "U01", "FASEQ", "1", "G2"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertTrue(p.pairs().isEmpty(), "mixed-mode domain is skipped");
        assertTrue(p.relatedTables().isEmpty());
    }


    @Test
    void mixedRecordAndDatasetLevel_leavesEvidenceWhenSkipping()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "L1", "G1"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "L1", "G1"
                },
                {
                        "S01", "AE", "U01", "AELNKID", "", "G2"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "G2"
                }
        });
        try (var logs = LogCapture.of(RelrecPairer.class))
        {
            RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
            assertEquals(0, p.pairs().size());
            assertTrue(logs.anyMessageContains("mixes record- and dataset-level links"),
                    "a skipped relationship must leave evidence, got: " + logs.records());
        }
    }


    @Test
    void usubjidBlankRecordLevel_joinsViaScanFallback()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1"
                },
                {
                        "S01", "U02", "1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1"
                },
                {
                        "S01", "U02", "1"
                }
        });
        // Record-level (IDVARVAL populated) but blank USUBJID -> scan fallback joins per
        // (study,usubj).
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "", "AESEQ", "1", "R1"
                },
                {
                        "S01", "FA", "", "FASEQ", "1", "R1"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(2, p.pairs().size());
        assertEquals(List.of(0L, 1L), p.pairs().stream().map(RelrecPair::primaryRow).toList());
        assertEquals(List.of(0L, 1L), p.pairs().stream().map(RelrecPair::relatedRow).toList());
    }


    @Test
    void recordLevelMultiRelid_setAndOrder()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "2"
                },
                {
                        "S01", "U01", "8"
                },
                {
                        "S01", "U02", "4"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1"
                },
                {
                        "S01", "U02", "1"
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AESEQ", "2", "U01-E1"
                },
                {
                        "S01", "FA", "U01", "FASEQ", "1", "U01-E1"
                },
                {
                        "S01", "AE", "U01", "AESEQ", "8", "U01-E2"
                },
                {
                        "S01", "FA", "U01", "FASEQ", "1", "U01-E2"
                },
                {
                        "S01", "AE", "U02", "AESEQ", "4", "U02-E1"
                },
                {
                        "S01", "FA", "U02", "FASEQ", "1", "U02-E1"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        // U01: AESEQ 2 (row0) & 8 (row1) -> FA U01 (row0). U02: AESEQ 4 (row2) -> FA U02 (row1).
        assertEquals(3, p.pairs().size());
        assertEquals(List.of(0L, 1L, 2L), p.pairs().stream().map(RelrecPair::primaryRow).toList());
        assertEquals(List.of(0L, 0L, 1L), p.pairs().stream().map(RelrecPair::relatedRow).toList());
    }


    @Test
    void selectionPushDown_buildsOnlySelectedDomain()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                }
        });
        IDataTable ds = table("DS", new String[]
        {
                "STUDYID", "USUBJID", "DSLNKID"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "", "G1"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "G1"
                },
                {
                        "S01", "AE", "U01", "AELNKID", "", "G2"
                },
                {
                        "S01", "DS", "U01", "DSLNKID", "", "G2"
                }
        });
        Function<String, IDataTable> res = Map.of("FA", fa, "DS", ds)::get;

        RelrecPairing all = RelrecPairerCompat.resolve(relrec, ae, "AE", res, null);
        assertEquals(2, all.relatedTables().size());

        RelrecPairing onlyFa = RelrecPairerCompat.resolve(relrec, ae, "AE", res, Set.of("FA"));
        assertEquals(1, onlyFa.relatedTables().size());
        assertTrue(onlyFa.pairs().stream().allMatch(x -> "FA".equals(x.rdomain())));
        assertFalse(onlyFa.pairs().isEmpty());
    }


    @Test
    void duplicateTriples_dedupedAcrossRelids()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                }
        });
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "", "G1"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "G1"
                },
                {
                        "S01", "AE", "U01", "AELNKID", "", "G2"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "G2"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        // ⚠ Reversed 2026-09-14 (review finding 3): this used to assert 1 — de-duplication ACROSS
        // RELIDs at resolver level. That tagged the shared (primary, related) pair with whichever
        // RELID came first, so selecting only the OTHER RELID in the merge dialog silently dropped
        // the pair. The resolver now keeps one pair per RELID; the per-row de-duplication happens
        // in ManagerSupportClinical.mergeRELREC AFTER the selection filter.
        assertEquals(2, p.pairs().size(), "one pair per RELID, so selection can match either");
        assertEquals(Set.of("G1", "G2"),
                p.pairs().stream().map(RelrecPairerCompat.RelrecPair::relid)
                        .collect(java.util.stream.Collectors.toSet()));
        for (RelrecPairerCompat.RelrecPair pair : p.pairs())
        {
            assertEquals(0L, pair.primaryRow());
            assertEquals(0L, pair.relatedRow());
        }
    }


    @Test
    void subjectScopedDatasetLevel_viaScanFallback()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                },
                {
                        "S01", "U02", "L1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "L1"
                },
                {
                        "S01", "U02", "L1"
                }
        });
        // Dataset-level (blank IDVARVAL) with a populated RELREC USUBJID -> scan fallback restricts
        // to that subject; U02 must not be pulled in even though its link value matches.
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "S01", "AE", "U01", "AELNKID", "", "G1"
                },
                {
                        "S01", "FA", "U01", "FALNKGRP", "", "G1"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, p.pairs().size());
        assertEquals(0L, p.pairs().get(0).primaryRow());
        assertEquals(0L, p.pairs().get(0).relatedRow());
    }


    @Test
    void recordLevel_relrecStudyidDiffersFromDataset_doesNotJoin()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ"
        }, STR3(), new Object[][]
        {
                {
                        "S01", "U01", "1"
                }
        });
        // RELREC names a study the data does not hold. A RELREC row names its record by STUDYID
        // too (SDTMIG 3.4 §8.2; PLAN-relrec-studyid-link T1-2 (c)), so no record is named and
        // nothing pairs. Until 2026-09-26 this test asserted the opposite, on parity with an
        // engine path that had lost its same-study check; parity is retired.
        IDataTable relrec = table("RELREC", RELREC_COLS, STR6, new Object[][]
        {
                {
                        "ZZZ", "AE", "U01", "AESEQ", "1", "R1"
                },
                {
                        "ZZZ", "FA", "U01", "FASEQ", "1", "R1"
                }
        });
        RelrecPairing p = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertTrue(p.pairs().isEmpty(),
                "a RELREC row in study ZZZ names no S01 record, so nothing may pair: " + p.pairs());
    }


    private static DataValueType[] STR3()
    {
        return new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.STRING
        };
    }
}
