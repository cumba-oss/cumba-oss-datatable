package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Boundary and fallback cases of {@link RelrecPairer} that the main suite does not pin: a required
 * RELREC column sitting at index 0 (the {@code < 0} presence checks), rows with a missing RELID,
 * and the indexed dataset-level fast path (blank RELREC USUBJID).
 */
class RelrecPairerBoundaryTest
{

    private static IDataTable table(String aName, String[] aCols, Object[][] aData)
    {
        DataValueType[] types = new DataValueType[aCols.length];
        java.util.Arrays.fill(types, DataValueType.STRING);
        return table(aName, aCols, types, aData);
    }


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


    private static IDataTable primaryAe()
    {
        return table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ", "AETERM"
        }, new Object[][]
        {
                {
                        "S1", "U1", "1", "HEADACHE"
                }
        });
    }


    private static IDataTable relatedFa()
    {
        return table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ", "FAOBJ"
        }, new Object[][]
        {
                {
                        "S1", "U1", "9", "OBJ"
                }
        });
    }


    private static Function<String, IDataTable> resolver(IDataTable aFa)
    {
        Map<String, IDataTable> map = Map.of("FA", aFa);
        return map::get;
    }


    /**
     * One record-level RELREC link, but with the RELREC's column order rotated so that the named
     * required column sits at model index 0. The presence checks are {@code < 0}; an off-by-one
     * ({@code <= 0}) silently drops every relationship whenever that column happens to be first.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "RDOMAIN", "IDVAR", "IDVARVAL", "RELID", "USUBJID", "STUDYID"
    })
    void requiredRelrecColumnAtIndexZero_stillResolves(String aFirstColumn)
    {
        List<String> cols = new java.util.ArrayList<>(
                List.of("STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"));
        cols.remove(aFirstColumn);
        cols.add(0, aFirstColumn);

        Object[] aeRow = new Object[6];
        Object[] faRow = new Object[6];
        for (int i = 0; i < cols.size(); i++)
        {
            aeRow[i] = switch (cols.get(i))
            {
            case "STUDYID" -> "S1";
            case "USUBJID" -> "U1";
            case "RDOMAIN" -> "AE";
            case "IDVAR" -> "AESEQ";
            case "IDVARVAL" -> "1";
            default -> "R1";
            };
            faRow[i] = switch (cols.get(i))
            {
            case "STUDYID" -> "S1";
            case "USUBJID" -> "U1";
            case "RDOMAIN" -> "FA";
            case "IDVAR" -> "FASEQ";
            case "IDVARVAL" -> "9";
            default -> "R1";
            };
        }
        IDataTable relrec = table("RELREC", cols.toArray(String[]::new), new Object[][]
        {
                aeRow, faRow
        });

        var pairing = RelrecPairerCompat.resolve(relrec, primaryAe(), "AE", resolver(relatedFa()),
                null);
        assertEquals(1, pairing.pairs().size(),
                "the AE->FA link must be found with " + aFirstColumn + " at index 0");
        assertEquals(0L, pairing.pairs().get(0).primaryRow());
        assertEquals(0L, pairing.pairs().get(0).relatedRow());
    }


    @Test
    void rowsWithMissingRelid_areIgnored_notGrouped()
    {
        // Two rows that would form an AE->FA link if their MISSING (NaN) RELIDs were treated as
        // a group key, plus one valid pair under RELID 1. Only the real link may come out, and
        // it must carry the real RELID.
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.STRING,
                DataValueType.STRING, DataValueType.STRING, DataValueType.DOUBLE
        }, new Object[][]
        {
                {
                        "S1", "U1", "AE", "AESEQ", "1", Double.NaN
                },
                {
                        "S1", "U1", "FA", "FASEQ", "9", Double.NaN
                },
                {
                        "S1", "U1", "AE", "AESEQ", "1", 1.0
                },
                {
                        "S1", "U1", "FA", "FASEQ", "9", 1.0
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, primaryAe(), "AE", resolver(relatedFa()),
                null);
        assertEquals(1, pairing.pairs().size(),
                "rows with a missing RELID must not silently form a relationship group");
        assertNotNull(pairing.pairs().get(0).relid(),
                "the surviving pair must be the one with the REAL RELID");
    }


    @Test
    void groupKey_scopesByStudyid_whenTheColumnIsPresent()
    {
        // STUDYID at model index 0, and the AE/FA legs of the "same" RELID sit in DIFFERENT
        // studies: they must land in different (STUDYID, USUBJID, RELID) groups and produce no
        // pair. Dropping STUDYID from the group key (or an off-by-one on its column check)
        // would join records across studies.
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "AE", "AESEQ", "1", "R1"
                },
                {
                        "S2", "U1", "FA", "FASEQ", "9", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, primaryAe(), "AE", resolver(relatedFa()),
                null);
        assertTrue(pairing.pairs().isEmpty(),
                "RELREC rows of different studies must not group into one relationship");
    }


    @Test
    void datasetLevel_blankUsubjid_usesTheStudySubjectIndex()
    {
        // Dataset-level (blank IDVARVAL) and blank USUBJID on the RELREC rows: the indexed
        // (STUDYID, USUBJID, key) fast path joins each subject's rows.
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AESEQ"
        }, new Object[][]
        {
                {
                        "S1", "U1", "1"
                },
                {
                        "S1", "U2", "2"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FASEQ"
        }, new Object[][]
        {
                {
                        "S1", "U2", "2"
                },
                {
                        "S1", "U1", "1"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "S1", "", "AE", "AESEQ", "", "R1"
                },
                {
                        "S1", "", "FA", "FASEQ", "", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        // AESEQ joins FASEQ per (STUDYID, USUBJID, value): U1/1 -> row 1, U2/2 -> row 0 — and
        // the pairing is SORTED by (primaryRow, ordinal, relatedRow), not emission order.
        var got = pairing.pairs().stream().map(p -> p.primaryRow() + ">" + p.relatedRow())
                .collect(Collectors.toList());
        assertEquals(List.of("0>1", "1>0"), got,
                "the dataset-level fast path must join within each study/subject, sorted");
    }


    @Test
    void datasetLevel_targetLacksTheIdvarColumn_yieldsNoPairs()
    {
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FAOBJ"
        }, new Object[][]
        {
                {
                        "S1", "U1", "OBJ"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "S1", "", "AE", "AESEQ", "", "R1"
                },
                {
                        "S1", "", "FA", "FASEQ", "", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, primaryAe(), "AE", resolver(fa), null);
        assertTrue(pairing.pairs().isEmpty(),
                "a related table without the declared IDVAR column has nothing to join on");
    }


    @Test
    void scanFallback_studyidAtIndexZero_joinsPerStudy()
    {
        // Subject-scoped dataset-level link (non-blank USUBJID) -> full-scan fallback. STUDYID
        // sits at model index 0 in both datasets and the "same" key value exists in TWO studies:
        // the scan keys on (STUDYID, USUBJID, value), so only the same-study row may join.
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "L1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, new Object[][]
        {
                {
                        "S2", "U1", "L1"
                },
                {
                        "S1", "U1", "L1"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "AE", "AELNKID", "", "R1"
                },
                {
                        "S1", "U1", "FA", "FALNKGRP", "", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, pairing.pairs().size(),
                "only the same-study FA row joins through the scan");
        assertEquals(1L, pairing.pairs().get(0).relatedRow(),
                "the S1 row (index 1), never the S2 row");
    }


    @Test
    void scanFallback_usubjidAndIdvarAtIndexZero_stillJoin()
    {
        // Subject-scoped dataset-level link -> full scan. The primary's IDVAR column and the
        // target's USUBJID column sit at model index 0: the "column present" checks are "< 0".
        IDataTable ae = table("AE", new String[]
        {
                "AELNKID", "USUBJID", "STUDYID"
        }, new Object[][]
        {
                {
                        "L1", "U1", "S1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "USUBJID", "FALNKGRP", "STUDYID"
        }, new Object[][]
        {
                {
                        "U1", "L1", "S1"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "AE", "AELNKID", "", "R1"
                },
                {
                        "S1", "U1", "FA", "FALNKGRP", "", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, pairing.pairs().size());
    }


    @Test
    void scanFallback_idvarAtZeroInTarget_usubjidAtZeroInPrimary_stillJoin()
    {
        IDataTable ae = table("AE", new String[]
        {
                "USUBJID", "AELNKID", "STUDYID"
        }, new Object[][]
        {
                {
                        "U1", "L1", "S1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "FALNKGRP", "USUBJID", "STUDYID"
        }, new Object[][]
        {
                {
                        "L1", "U1", "S1"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "AE", "AELNKID", "", "R1"
                },
                {
                        "S1", "U1", "FA", "FALNKGRP", "", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, pairing.pairs().size());
    }


    @Test
    void fastPath_idvarColumnAtIndexZero_indexesIt()
    {
        // Record-level fast path (non-blank USUBJID everywhere) with the join column at model
        // index 0 in both tables: TableIndex.build's "colIdx < 0" check must accept index 0.
        IDataTable ae = table("AE", new String[]
        {
                "AESEQ", "STUDYID", "USUBJID"
        }, new Object[][]
        {
                {
                        "1", "S1", "U1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "FASEQ", "STUDYID", "USUBJID"
        }, new Object[][]
        {
                {
                        "9", "S1", "U1"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "AE", "AESEQ", "1", "R1"
                },
                {
                        "S1", "U1", "FA", "FASEQ", "9", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        assertEquals(1, pairing.pairs().size(),
                "a join column at model index 0 must be indexed, not treated as missing");
    }


    /**
     * Record-level fast path with USUBJID at model index 0 in both datasets, and the "same" FA
     * record in TWO studies for one subject. The fast path joins within the RELREC row's study
     * (PLAN-relrec-studyid-link (b)+(c)), so exactly the S1 row pairs — which also pins that
     * {@code hasUsubj} accepts index 0. ⛔ Until 2026-09-26 this test asserted that the fast path
     * joined the SOMETHING_ELSE row "regardless of the dataset STUDYID": it pinned a mutant-killing
     * shape, not a design (USUBJID never scopes the study, SDTMIG 3.4 §4.2.3).
     */
    @Test
    void recordLevel_usubjidAtIndexZero_fastPathJoinsWithinTheRelrecStudy()
    {
        IDataTable ae = table("AE", new String[]
        {
                "USUBJID", "STUDYID", "AESEQ"
        }, new Object[][]
        {
                {
                        "U1", "S1", "1"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "USUBJID", "STUDYID", "FASEQ"
        }, new Object[][]
        {
                {
                        "U1", "SOMETHING_ELSE", "9"
                },
                {
                        "U1", "S1", "9"
                }
        });
        IDataTable relrec = table("RELREC", new String[]
        {
                "USUBJID", "STUDYID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
        }, new Object[][]
        {
                {
                        "U1", "S1", "AE", "AESEQ", "1", "R1"
                },
                {
                        "U1", "S1", "FA", "FASEQ", "9", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, ae, "AE", resolver(fa), null);
        var got = pairing.pairs().stream().map(p -> p.primaryRow() + ">" + p.relatedRow())
                .collect(Collectors.toList());
        assertEquals(List.of("0>1"), got,
                "only the S1 FA row (index 1) is in the RELREC row's study; never SOMETHING_ELSE");
    }

    // ---- STUDYID is part of the link identity (PLAN-relrec-studyid-link, T1-1..T1-3) ----

    private static final String[] AE_COLS =
    {
            "STUDYID", "USUBJID", "AESEQ", "AETERM"
    };

    private static final String[] FA_COLS =
    {
            "STUDYID", "USUBJID", "FASEQ", "FAOBJ"
    };

    private static final String[] RELREC_COLS =
    {
            "STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELID"
    };

    /** FA holds the "same" record for U1 in S1 (index 0) and in S2 (index 1). */
    private static IDataTable pooledFa()
    {
        return table("FA", FA_COLS, new Object[][]
        {
                {
                        "S1", "U1", "9", "X"
                },
                {
                        "S2", "U1", "9", "Y"
                }
        });
    }


    private static IDataTable recordLevelRelrec(Object aStudy)
    {
        return table("RELREC", RELREC_COLS, new Object[][]
        {
                {
                        aStudy, "U1", "AE", "AESEQ", "1", "R1"
                },
                {
                        aStudy, "U1", "FA", "FASEQ", "9", "R1"
                }
        });
    }


    private static List<String> pairsOf(RelrecPairerCompat.RelrecPairing aPairing)
    {
        return aPairing.pairs().stream().map(p -> p.primaryRow() + ">" + p.relatedRow())
                .collect(Collectors.toList());
    }


    @Test
    void recordLevel_pooledSameUsubjid_pairsOnlyTheRelrecStudy()
    {
        // The §4.2.3 pattern: one person, one USUBJID, two trials, the same --SEQ in both. The S1
        // RELREC row names the S1 record only.
        var pairing = RelrecPairerCompat.resolve(recordLevelRelrec("S1"), primaryAe(), "AE",
                resolver(pooledFa()), null);
        assertEquals(List.of("0>0"), pairsOf(pairing));
    }


    @Test
    void recordLevel_blankRelrecStudyid_stillRequiresOneStudy()
    {
        // T1-3 (i): a blank RELREC STUDYID names no study; (b) alone keeps S1 with S1.
        var pairing = RelrecPairerCompat.resolve(recordLevelRelrec(""), primaryAe(), "AE",
                resolver(pooledFa()), null);
        assertEquals(List.of("0>0"), pairsOf(pairing));
    }


    @Test
    void recordLevel_missingRelrecStudyid_sameAsBlank()
    {
        // A missing (NaN) RELREC STUDYID is "no study named" exactly like a blank one.
        IDataTable relrec = table("RELREC", RELREC_COLS, new DataValueType[]
        {
                DataValueType.DOUBLE, DataValueType.STRING, DataValueType.STRING,
                DataValueType.STRING, DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        Double.NaN, "U1", "AE", "AESEQ", "1", "R1"
                },
                {
                        Double.NaN, "U1", "FA", "FASEQ", "9", "R1"
                }
        });
        var pairing = RelrecPairerCompat.resolve(relrec, primaryAe(), "AE", resolver(pooledFa()),
                null);
        assertEquals(List.of("0>0"), pairsOf(pairing));
    }


    private static IDataTable pooledAeLinkGroup()
    {
        return table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "L1"
                },
                {
                        "S2", "U1", "L1"
                }
        });
    }


    private static IDataTable pooledFaLinkGroup()
    {
        return table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, new Object[][]
        {
                {
                        "S1", "U1", "L1"
                },
                {
                        "S2", "U1", "L1"
                }
        });
    }


    private static IDataTable datasetLevelRelrec(String aStudy)
    {
        return table("RELREC", RELREC_COLS, new Object[][]
        {
                {
                        aStudy, "", "AE", "AELNKID", "", "G1"
                },
                {
                        aStudy, "", "FA", "FALNKGRP", "", "G1"
                }
        });
    }


    @Test
    void datasetLevel_relrecStudy_restrictsToThatStudy()
    {
        var pairing = RelrecPairerCompat.resolve(datasetLevelRelrec("S1"), pooledAeLinkGroup(),
                "AE", resolver(pooledFaLinkGroup()), null);
        assertEquals(List.of("0>0"), pairsOf(pairing), "only the S1 pair, never S2's");
    }


    @Test
    void datasetLevel_blankRelrecStudy_pairsWithinEachStudy()
    {
        // Control (T1-3 (i)): with no study named each study pairs with itself, never across.
        var pairing = RelrecPairerCompat.resolve(datasetLevelRelrec(""), pooledAeLinkGroup(), "AE",
                resolver(pooledFaLinkGroup()), null);
        assertEquals(List.of("0>0", "1>1"), pairsOf(pairing));
    }


    private static IDataTable faWithoutStudyid()
    {
        return table("FA", new String[]
        {
                "USUBJID", "FASEQ", "FAOBJ"
        }, new Object[][]
        {
                {
                        "U1", "9", "OBJ"
                }
        });
    }


    @Test
    void fastPath_targetWithoutStudyidColumn_populatedRelrecStudy_noPair()
    {
        // JKM R7: an absent STUDYID column is present-but-empty, and "" is not "S1". The
        // record-level fast path used to key on (USUBJID, value) alone and paired this.
        var pairing = RelrecPairerCompat.resolve(recordLevelRelrec("S1"), primaryAe(), "AE",
                resolver(faWithoutStudyid()), null);
        assertTrue(pairing.pairs().isEmpty(), pairing.pairs().toString());
    }


    private static IDataTable aeWithoutStudyid()
    {
        return table("AE", new String[]
        {
                "USUBJID", "AESEQ", "AETERM"
        }, new Object[][]
        {
                {
                        "U1", "1", "HEADACHE"
                }
        });
    }


    private static IDataTable scanRelrec(String aStudy)
    {
        // Blank RELREC USUBJID on a record-level link: the full-scan path.
        return table("RELREC", RELREC_COLS, new Object[][]
        {
                {
                        aStudy, "", "AE", "AESEQ", "1", "R1"
                },
                {
                        aStudy, "", "FA", "FASEQ", "9", "R1"
                }
        });
    }


    @Test
    void scan_neitherDatasetHasStudyid_populatedRelrecStudy_noPair()
    {
        // Both sides read "" (JKM R7), which equals each other (b) but not the named "S1" (c).
        var pairing = RelrecPairerCompat.resolve(scanRelrec("S1"), aeWithoutStudyid(), "AE",
                resolver(faWithoutStudyid()), null);
        assertTrue(pairing.pairs().isEmpty(), pairing.pairs().toString());
    }


    @Test
    void scan_neitherDatasetHasStudyid_blankRelrecStudy_pairs()
    {
        // Control: "" = "" and no study is named, so the pair is made.
        var pairing = RelrecPairerCompat.resolve(scanRelrec(""), aeWithoutStudyid(), "AE",
                resolver(faWithoutStudyid()), null);
        assertEquals(List.of("0>0"), pairsOf(pairing));
    }

    // ---- key text beyond 2^53 (PLAN-relrec-idvar-key-precision, T1-1 (a); RRK E2) ----


    private static IDataTable aeSeq(String aSeq)
    {
        return table("AE", AE_COLS, new Object[][]
        {
                {
                        "S1", "U1", aSeq, "HEADACHE"
                }
        });
    }


    private static IDataTable faSeq(String aSeq)
    {
        return table("FA", FA_COLS, new Object[][]
        {
                {
                        "S1", "U1", aSeq, "OBJ"
                }
        });
    }


    private static IDataTable idvarvalRelrec(String aAeIdvarval, String aFaIdvarval)
    {
        return table("RELREC", RELREC_COLS, new Object[][]
        {
                {
                        "S1", "U1", "AE", "AESEQ", aAeIdvarval, "R1"
                },
                {
                        "S1", "U1", "FA", "FASEQ", aFaIdvarval, "R1"
                }
        });
    }


    @Test
    void recordLevel_idvarvalBeyond2p53_isNotItsNeighbour()
    {
        // 9007199254740993 and 9007199254740992 are one double: the IDVAR == IDVARVAL filter
        // compares exact decimal text, so the RELREC value names neither the AE nor the FA row.
        var pairing = RelrecPairerCompat.resolve(
                idvarvalRelrec("9007199254740993", "9007199254740993"), aeSeq("9007199254740992"),
                "AE", resolver(faSeq("9007199254740992")), null);
        assertTrue(pairing.pairs().isEmpty(), pairing.pairs().toString());
    }


    @Test
    void recordLevel_idvarvalBeyond2p53_matchesItsOwnDigits()
    {
        // Control: the same 16-digit value pairs.
        var pairing = RelrecPairerCompat.resolve(
                idvarvalRelrec("9007199254740993", "9007199254740993"), aeSeq("9007199254740993"),
                "AE", resolver(faSeq("9007199254740993")), null);
        assertEquals(List.of("0>0"), pairsOf(pairing));
        // Re-pinned by the T2 fold (L1): the columns are character, so "…993.0" is another text
        // and names neither row (before, every numeric-looking link value was canonicalised).
        var folded = RelrecPairerCompat.resolve(
                idvarvalRelrec("9007199254740993", "9007199254740993.0"), aeSeq("9007199254740993"),
                "AE", resolver(faSeq("9007199254740993")), null);
        assertTrue(folded.pairs().isEmpty(), folded.pairs().toString());
    }


    @Test
    void datasetLevel_linkValuesBeyond2p53_areTwoKeys()
    {
        IDataTable ae = table("AE", new String[]
        {
                "STUDYID", "USUBJID", "AELNKID"
        }, new Object[][]
        {
                {
                        "S1", "U1", "9007199254740993"
                }
        });
        IDataTable fa = table("FA", new String[]
        {
                "STUDYID", "USUBJID", "FALNKGRP"
        }, new Object[][]
        {
                {
                        "S1", "U1", "9007199254740992"
                }
        });
        var pairing = RelrecPairerCompat.resolve(datasetLevelRelrec("S1"), ae, "AE", resolver(fa),
                null);
        assertTrue(pairing.pairs().isEmpty(),
                "two integers that differ beyond 2^53 must not fold onto one key");
    }
}
