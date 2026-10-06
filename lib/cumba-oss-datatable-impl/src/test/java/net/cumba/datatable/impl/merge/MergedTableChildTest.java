package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static net.cumba.datatable.impl.merge.MergeFixtures.UNSUPPLIED;
import static net.cumba.datatable.impl.merge.MergeFixtures.column;
import static net.cumba.datatable.impl.merge.MergeFixtures.columnNames;
import static net.cumba.datatable.impl.merge.MergeFixtures.request;
import static net.cumba.datatable.impl.merge.MergeFixtures.row;
import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.CardinalityViolationException;
import net.cumba.datatable.merge.ChildMergeSpec;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.MergeColumnClashException;
import net.cumba.datatable.merge.MergeIssue;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.merge.MergeStandard;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTables;
import net.cumba.datatable.merge.UnresolvedDataset;
import org.junit.jupiter.api.Test;

/**
 * The {@code Child: true} parent parts — {@code PLAN-merged-evaluation-table} §3.7, MQ1: one column
 * set per parent domain, supplied only on the rows whose parent is that domain; P-13: a grouping
 * pointer may address many parent records.
 */
class MergedTableChildTest
{

    private static IDataTable suppRows()
    {
        // the §3.7 table: row 0 points at AE seq 1, row 1 at CM seq 4
        return table("SUPPX", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,AE,01-001,AESEQ,1,Q,a", "S1,CM,01-001,CMSEQ,4,Q,b");
    }


    private static IDataTable ae()
    {
        return table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N,AESMIE", "S1,AE,01-001,1,N",
                "S1,AE,01-001,2,Y");
    }


    private static IDataTable cm()
    {
        return table("CM", "STUDYID,DOMAIN,USUBJID,CMSEQ:N,CMTRT", "S1,CM,01-001,4,ASPIRIN");
    }


    @Test
    void perRowParentDomain_oneColumnSetPerParent_suppliedOnItsRowsOnly()
    {
        MergedTable t = MergedTables.build(request(suppRows(), ae(), cm())
                .child(ChildMergeSpec.builder().build()).matchedFlags(true).build());
        assertEquals(2, t.getRowCount());
        assertEquals(List.of("1", UNSUPPLIED), column(t, "AE.AESEQ"));
        assertEquals(List.of(UNSUPPLIED, "4"), column(t, "CM.CMSEQ"));
        assertEquals(List.of("N", UNSUPPLIED), column(t, "AE.AESMIE"));
        assertEquals(List.of("true", "false"), column(t, "AE._matched_"));
        assertEquals(List.of("false", "true"), column(t, "CM._matched_"));
        assertFalse(t.isRowMapped());
        assertEquals(MergeKind.CHILD, t.partOf(t.getMetaData().getColumnIndex("AE.AESEQ")).kind());
        assertEquals("AE", t.partOf(t.getMetaData().getColumnIndex("AE.AESEQ")).qualifier());
        assertEquals("CHILD",
                t.getMetaData().getColumn("CM.CMTRT").getMetaData(MergeMeta.MERGE_KIND));
        assertTrue(t.issues().isEmpty());
    }


    @Test
    void fixedParent_everyRowPointsAtIt_innerDropsTheRest()
    {
        MergedTable t = MergedTables.build(request(suppRows(), ae(), cm())
                .child(ChildMergeSpec.builder().parentDataset("AE").build()).build());
        assertEquals(1, t.getRowCount(), "inner: the CM-pointing row has no AE parent");
        assertEquals(List.of("N"), column(t, "AE.AESMIE"));
        assertEquals(0, t.getRealRowIndex(0));
        assertFalse(columnNames(t).contains("CM.CMTRT"));
    }


    @Test
    void p13_aGroupingPointer_expandsIntoEveryParentRecord()
    {
        IDataTable co = table("CO", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,COVAL",
                "S1,AE,01-001,AEGRPID,G1,comment");
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N,AEGRPID", "S1,AE,01-001,1,G1",
                "S1,AE,01-001,2,G1", "S1,AE,01-001,3,G2");
        MergedTable t = MergedTables
                .build(request(co, ae).child(ChildMergeSpec.builder().build()).build());
        assertEquals(2, t.getRowCount());
        assertTrue(t.isRowMapped());
        assertEquals(List.of("1", "2"), column(t, "AE.AESEQ"));
        assertEquals(List.of(0L, 0L), List.of(t.getRealRowIndex(0), t.getRealRowIndex(1)));
        assertEquals(List.of("comment", "1"), row(t, 0, "COVAL", "AE.AESEQ"));
    }


    @Test
    void subjectKeys_presentOnBothSidesOnly_textCompareOnThePointer()
    {
        IDataTable supp = table("SUPPAE", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,AE,01-001,AESEQ,01.0,Q,a", "S1,AE,01-002,AESEQ,1,Q,b",
                "S2,AE,01-001,AESEQ,1,Q,c");
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-002,1");
        MergedTable t = MergedTables.build(request(supp, ae)
                .child(ChildMergeSpec.builder().joinType(JoinType.LEFT).build()).build());
        assertEquals(List.of("01-001", "01-002", UNSUPPLIED), column(t, "AE.USUBJID"));
        // the parent lacking POOLID: that subject component is simply not used
        assertTrue(t.issues().isEmpty());
    }


    @Test
    void aPointerColumnTheParentLacks_isFlagged_aMissingParentIsListed()
    {
        IDataTable supp = table("SUPPAE", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,AE,01-001,AEGRPID,G1,Q,a", "S1,LB,01-001,LBSEQ,1,Q,b", "S1,,01-001,,,Q,c");
        MergedTable t = MergedTables.build(request(supp, ae())
                .child(ChildMergeSpec.builder().joinType(JoinType.LEFT).build()).build());
        assertEquals(3, t.getRowCount());
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED, UNSUPPLIED), column(t, "AE.AESEQ"));
        assertEquals(List.of(new UnresolvedDataset(MergeKind.CHILD, "LB")), t.unresolvedDatasets());
        assertEquals(1, t.issues().size());
        assertEquals("pointer column AEGRPID is not a column of parent AE",
                t.issues().get(0).message());
        assertEquals(List.of(0L), t.issues().get(0).sourceRows());
        assertEquals(t.issues().get(0).message(),
                t.getMetaData().getColumn("AE.AESEQ").getMetaData(MergeMeta.MERGE_ERROR));
    }


    @Test
    void aPrimaryWithoutThePointerColumns_bindsNothing()
    {
        IDataTable co = table("CO", "STUDYID,USUBJID,COVAL", "S1,01-001,x");
        MergedTable t = MergedTables.build(request(co, ae())
                .child(ChildMergeSpec.builder().parentDataset("AE").joinType(JoinType.LEFT).build())
                .build());
        assertEquals(List.of(UNSUPPLIED), column(t, "AE.AESEQ"));
        MergedTable inner = MergedTables.build(request(co, ae())
                .child(ChildMergeSpec.builder().parentDataset("AE").build()).build());
        assertEquals(0, inner.getRowCount());
    }


    @Test
    void aChildParentAndAKeyedPartnerOfOneQualifier_clash()
    {
        assertThrows(MergeColumnClashException.class,
                () -> MergedTables.build(request(suppRows(), ae(), cm())
                        .child(ChildMergeSpec.builder().parentDataset("AE").build())
                        .keyed(net.cumba.datatable.merge.KeyedMergeSpec.builder().qualifier("AE")
                                .key(net.cumba.datatable.merge.KeyColumn.of("USUBJID")).build())
                        .build()));
    }


    /**
     * M4 (W1 review): an authored {@code Cardinality} on a {@code Child} entry was a silent no-op.
     * {@code n:1} (each primary row has at most one parent record) is checked over the parent
     * postings, like a keyed partner (P-16): {@code AEGRPID G1} addresses two AE records.
     */
    @Test
    void m4_authoredNto1_aPostingWithTwoParents_errors()
    {
        IDataTable co = table("CO", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,COVAL",
                "S1,AE,01-001,AESEQ,3,single");
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N,AEGRPID", "S1,AE,01-001,1,G1",
                "S1,AE,01-001,2,G1", "S1,AE,01-001,3,G2");
        // the AESEQ pointer addresses one record each: n:1 holds
        MergedTable ok = MergedTables.build(request(co, ae)
                .child(ChildMergeSpec.builder().cardinality(Cardinality.MANY_TO_ONE).build())
                .build());
        assertEquals(List.of("3"), column(ok, "AE.AESEQ"));
        IDataTable grouped = table("CO", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,COVAL",
                "S1,AE,01-001,AEGRPID,G1,comment");
        CardinalityViolationException ex = assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(grouped, ae).child(
                        ChildMergeSpec.builder().cardinality(Cardinality.MANY_TO_ONE).build())
                        .build()));
        assertEquals("AE", ex.dataset());
        assertEquals(List.of(0L, 1L), List.of(ex.firstRow(), ex.secondRow()));
        assertEquals("Cardinality n:1: AE has duplicate key (AEGRPID=G1) at rows 1 and 2",
                ex.getMessage());
        // n:m authored is the IG's own value: checked as nothing, and no WARNING
        try (var logs = LogCapture.of(ChildMatcher.class))
        {
            MergedTable nm = MergedTables.build(request(grouped, ae)
                    .child(ChildMergeSpec.builder().cardinality(Cardinality.MANY_TO_MANY).build())
                    .build());
            assertEquals(2, nm.getRowCount());
            assertEquals(0, logs.count(java.util.logging.Level.WARNING));
        }
        try (var logs = LogCapture.of(ChildMatcher.class))
        {
            MergedTables.build(request(co, ae)
                    .child(ChildMergeSpec.builder().cardinality(Cardinality.MANY_TO_ONE).build())
                    .build());
            assertTrue(logs.anyMessageContains("disagrees with the IG's n:m"),
                    logs.records().toString());
        }
    }


    /**
     * L1 (T2 review): the Child part published only the FIRST issue of a parent — a second pointer
     * column the parent lacks was lost with its rows. Every issue of the parent is joined with
     * {@code "; "} and the rows concatenated, as the RELREC part does.
     */
    @Test
    void l1_everyPointerColumnTheParentLacks_isPublished_notOnlyTheFirst()
    {
        IDataTable co = table("CO", "STUDYID,USUBJID,RDOMAIN,IDVAR,IDVARVAL,COVAL",
                "S,U1,AE,AESEQ,1,ok", "S,U1,AE,AEGRPID,G1,c2", "S,U1,AE,AELNKID,L1,c3");
        IDataTable ae = table("AE", "STUDYID,USUBJID,DOMAIN,AESEQ:N", "S,U1,AE,1");
        MergedTable t = MergedTables.build(request(co, ae)
                .child(ChildMergeSpec.builder().joinType(JoinType.LEFT).build()).build());
        assertEquals(List.of("1", UNSUPPLIED, UNSUPPLIED), column(t, "AE.AESEQ"));
        assertEquals(1, t.issues().size(), t.issues().toString());
        MergeIssue issue = t.issues().get(0);
        assertEquals("pointer column AEGRPID is not a column of parent AE; "
                + "pointer column AELNKID is not a column of parent AE", issue.message());
        assertEquals(List.of(1L, 2L), issue.sourceRows());
        assertEquals("AE", issue.column(), "the parent: every column of its part carries it");
        assertEquals(issue.message(),
                t.getMetaData().getColumn("AE.AESEQ").getMetaData(MergeMeta.MERGE_ERROR));
    }


    /**
     * FDA-SD0077's shape: {@code Keys [USUBJID, IDVAR, IDVARVAL]}, so subject keys
     * {@code [USUBJID]}.
     */
    private static ChildMergeSpec sd0077()
    {
        return ChildMergeSpec.builder().subjectKeys(List.of("USUBJID")).joinType(JoinType.LEFT)
                .build();
    }


    /** SEND CL: two subjects, two pools, all with CLSEQ 1; subject A1 also CLSEQ 2. */
    private static IDataTable sendCl()
    {
        return table("CL", "STUDYID,DOMAIN,USUBJID,POOLID,CLSEQ:N", "S1,CL,A1,,1", "S1,CL,A2,,1",
                "S1,CL,,P1,1", "S1,CL,,P2,1", "S1,CL,A1,,2");
    }


    /** A study-wide row (no subject, no pool), a pool P1 row, a subject A1 row. */
    private static IDataTable sendSuppcl()
    {
        return table("SUPPCL", "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,CL,,,CLSEQ,1,X,study-wide", "S1,CL,,P1,CLSEQ,1,X,pool",
                "S1,CL,A1,,CLSEQ,2,X,subject");
    }


    private static List<Long> realRows(MergedTable aTable)
    {
        List<Long> out = new java.util.ArrayList<>();
        for (long r = 0; r < aTable.getRowCount(); r++)
        {
            out.add(aTable.getRealRowIndex(r));
        }
        return out;
    }


    /**
     * M2 (T2 review), OD-2 for the reverse direction (MET #19): under SEND a Child row naming
     * neither subject nor pool is study-wide — it points at every parent record with that
     * {@code IDVAR} = {@code IDVARVAL} (SENDIG 3.1.1 sec. 8.3) — and {@code POOLID} joins the
     * subject key when both sides carry it, so a pool's row finds its pool's record only. The
     * standard comes from the request; {@code UNKNOWN} infers SEND from the child's {@code POOLID}.
     */
    @Test
    void m2_send_aSubjectLessChildRow_isStudyWide_andPoolidJoinsTheSubjectKey()
    {
        MergedTable t = MergedTables.build(request(sendSuppcl(), sendCl())
                .standard(MergeStandard.SEND).child(sd0077()).build());
        // row 0: every CL record with CLSEQ 1 (A1, A2, P1, P2); row 1: pool P1's; row 2: A1 seq 2
        assertEquals(List.of("A1", "A2", "", "", "", "A1"), column(t, "CL.USUBJID"));
        assertEquals(List.of("", "", "P1", "P2", "P1", ""), column(t, "CL.POOLID"));
        assertEquals(List.of("1", "1", "1", "1", "1", "2"), column(t, "CL.CLSEQ"));
        assertEquals(List.of(0L, 0L, 0L, 0L, 1L, 2L), realRows(t));
        assertTrue(t.issues().isEmpty());
        MergedTable inferred = MergedTables
                .build(request(sendSuppcl(), sendCl()).child(sd0077()).build());
        assertEquals(column(t, "CL.POOLID"), column(inferred, "CL.POOLID"),
                "UNKNOWN + a POOLID column on the child ⇒ SEND");
        assertEquals(realRows(t), realRows(inferred));
    }


    /**
     * M2, the SDTM control (explicit, and inferred from a child without {@code POOLID}): a blank
     * subject matches a blank subject only, and {@code POOLID} stays out of the key — the ruled IG
     * shift of the W4 record — so nothing here moves.
     */
    @Test
    void m2_sdtm_control_aBlankSubjectMatchesABlankSubjectOnly_poolidStaysOutOfTheKey()
    {
        MergedTable t = MergedTables.build(request(sendSuppcl(), sendCl())
                .standard(MergeStandard.SDTM).child(sd0077()).build());
        // rows 0 and 1 (USUBJID blank) both find the two pool records (USUBJID blank), never a
        // subject's; row 2 finds A1 seq 2
        assertEquals(List.of("P1", "P2", "P1", "P2", ""), column(t, "CL.POOLID"));
        assertEquals(List.of(0L, 0L, 1L, 1L, 2L), realRows(t));
        IDataTable noPool = table("SUPPCL", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,CL,,CLSEQ,1,X,blank", "S1,CL,A1,CLSEQ,2,X,subject");
        MergedTable inferred = MergedTables
                .build(request(noPool, sendCl()).child(sd0077()).build());
        assertEquals(List.of("P1", "P2", ""), column(inferred, "CL.POOLID"),
                "UNKNOWN without POOLID ⇒ SDTM: the blank-subject row finds the blank subjects");
        assertEquals(List.of(0L, 0L, 1L), realRows(inferred));
    }


    /**
     * M4: {@code 1:n} on a {@code Child} entry — two primary rows carrying one pointer are a
     * duplicate on the primary side.
     */
    @Test
    void m4_authored1toN_twoPrimaryRowsWithOnePointer_error()
    {
        IDataTable co = table("CO", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,COVAL",
                "S1,AE,01-001,AESEQ,1,a", "S1,AE,01-001,AESEQ,01,b", "S1,AE,01-001,AESEQ,2,c");
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-001,2");
        CardinalityViolationException ex = assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(co, ae).child(
                        ChildMergeSpec.builder().cardinality(Cardinality.ONE_TO_MANY).build())
                        .build()));
        assertEquals("CO", ex.dataset());
        assertEquals(List.of(0L, 1L), List.of(ex.firstRow(), ex.secondRow()));
    }
}
