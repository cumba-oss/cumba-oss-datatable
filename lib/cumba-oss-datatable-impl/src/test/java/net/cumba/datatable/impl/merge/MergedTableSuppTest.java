package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static net.cumba.datatable.impl.merge.MergeFixtures.UNSUPPLIED;
import static net.cumba.datatable.impl.merge.MergeFixtures.column;
import static net.cumba.datatable.impl.merge.MergeFixtures.columnNames;
import static net.cumba.datatable.impl.merge.MergeFixtures.request;
import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MalformedSidedKeyException;
import net.cumba.datatable.merge.MergeException;
import net.cumba.datatable.merge.MergeIssue;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.merge.MergeNaming;
import net.cumba.datatable.merge.MergeRequest;
import net.cumba.datatable.merge.MergeStandard;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTables;
import net.cumba.datatable.merge.SuppMergeSpec;
import net.cumba.datatable.merge.UnresolvedDataset;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * The supplemental part of the merged table — {@code PLAN-merged-evaluation-table} §3.5: the key
 * (§3.5.1, both examples), the compare (§3.5.2), the case table (§3.5.3, cases 1–9), scope
 * (§3.5.4), P-10 and the owner's release of the parse structure (2026-10-05).
 */
class MergedTableSuppTest
{

    private static final String SUPP_HEADER = "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QLABEL,QVAL";

    /** The base of the case table: AE rows (01-001, AESEQ 1) and (01-001, AESEQ 2). */
    private static IDataTable ae()
    {
        return table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N,AESPID", "S1,AE,01-001,1,A1",
                "S1,AE,01-001,2,A2");
    }


    private static IDataTable suppae(String... aRows)
    {
        return table("SUPPAE", SUPP_HEADER, aRows);
    }


    private static MergedTable merge(IDataTable aBase, IDataTable aSupp)
    {
        return MergedTables
                .build(request(aBase, aSupp).supp(SuppMergeSpec.builder().build()).build());
    }


    private static MergeIssue onlyIssue(MergedTable aTable)
    {
        assertEquals(1, aTable.issues().size(), aTable.issues().toString());
        return aTable.issues().get(0);
    }


    @Test
    void case1_oneRecordLevelRow_qualifiesItsRecordOnly()
    {
        MergedTable t = merge(ae(), suppae("S1,AE,01-001,AESEQ,1,AETRTEM,Treatment Emergent,Y"));
        assertEquals(List.of("Y", UNSUPPLIED), column(t, "AETRTEM"));
        assertTrue(t.issues().isEmpty());
        assertEquals(Set.of("AETRTEM"), t.suppQnams());
        // the VALUE at the marker is the present blank "" (D34 #3), the metadata the QLABEL
        int col = t.getMetaData().getColumnIndex("AETRTEM");
        assertEquals("", t.getDataValue(1, col).getValueAsString());
        assertEquals("", t.getValue(1, col));
        DataTableColumnMeta cm = t.getMetaData().getColumn(col);
        assertEquals("Treatment Emergent", cm.getLabel());
        assertEquals(DataValueType.STRING, cm.getType());
        assertEquals(40, cm.getLength(), "QVAL's length");
        assertEquals("SUPPAE", cm.getMetaData(MergeMeta.MERGED_FROM));
        assertEquals("AETRTEM", cm.getMetaData(MergeMeta.MERGED_SOURCE_COLUMN));
        assertEquals("SUPP", cm.getMetaData(MergeMeta.MERGE_KIND));
        assertNull(cm.getMetaData(MergeMeta.MERGE_ERROR));
        assertEquals(MergeKind.SUPP, t.partOf(col).kind());
        assertEquals("SUPPAE", t.partOf(col).source());
    }


    @Test
    void case2_duplicate_isAnErrorOnTheQualifierNamingTheRows()
    {
        MergedTable t = merge(ae(),
                suppae("S1,AE,01-001,AESEQ,1,AETRTEM,TE,Y", "S1,AE,01-001,AESEQ,1,AETRTEM,TE,N"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "AETRTEM"));
        MergeIssue issue = onlyIssue(t);
        assertEquals("AETRTEM", issue.column());
        assertEquals(MergeKind.SUPP, issue.kind());
        assertEquals("SUPPAE", issue.source());
        assertTrue(issue.message().contains("more than one row qualifies one record (rows 1, 2)"),
                issue.message());
        assertEquals(List.of(0L, 1L), issue.sourceRows());
        assertEquals(issue.message(),
                t.getMetaData().getColumn("AETRTEM").getMetaData(MergeMeta.MERGE_ERROR));
        // a clean record of the same qualifier stays supplied
        IDataTable ae3 = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-001,2", "S1,AE,01-001,3");
        MergedTable t3 = merge(ae3, suppae("S1,AE,01-001,AESEQ,1,AETRTEM,TE,Y",
                "S1,AE,01-001,AESEQ,1,AETRTEM,TE,N", "S1,AE,01-001,AESEQ,3,AETRTEM,TE,Y"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED, "Y"), column(t3, "AETRTEM"));
    }


    @Test
    void case3_subjectAndRecordLevelForOneSubject_errorsBothRowsOfTheSubject()
    {
        MergedTable t = merge(ae(),
                suppae("S1,AE,01-001,,,AETRTEM,TE,N", "S1,AE,01-001,AESEQ,1,AETRTEM,TE,Y"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "AETRTEM"));
        MergeIssue issue = onlyIssue(t);
        assertTrue(
                issue.message().contains(
                        "a subject-level and a record-level row qualify one subject (rows 1, 2)"),
                issue.message());
        // another subject's rows are untouched
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-002,1");
        MergedTable t2 = merge(ae, suppae("S1,AE,01-001,,,AETRTEM,TE,N",
                "S1,AE,01-001,AESEQ,1,AETRTEM,TE,Y", "S1,AE,01-002,,,AETRTEM,TE,N"));
        assertEquals(List.of(UNSUPPLIED, "N"), column(t2, "AETRTEM"));
    }


    @Test
    void case4_mixedIdvarsHittingDifferentRecords_bothSupplied()
    {
        MergedTable t = merge(ae(),
                suppae("S1,AE,01-001,AESEQ,1,AEX,X,Y", "S1,AE,01-001,AESPID,A2,AEX,X,N"));
        assertEquals(List.of("Y", "N"), column(t, "AEX"));
        assertTrue(t.issues().isEmpty());
    }


    @Test
    void case5_mixedIdvarsHittingOneRecord_isADuplicate()
    {
        MergedTable t = merge(ae(),
                suppae("S1,AE,01-001,AESEQ,1,AEX,X,Y", "S1,AE,01-001,AESPID,A1,AEX,X,N"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "AEX"));
        assertTrue(onlyIssue(t).message().contains("(rows 1, 2)"));
    }


    @Test
    void case6_idvarNamesAColumnTheBaseLacks_isAnErrorAndSuppliesNothing()
    {
        MergedTable t = merge(ae(), suppae("S1,AE,01-001,AEGRPID,G1,AEX,X,Y"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "AEX"));
        MergeIssue issue = onlyIssue(t);
        assertEquals("SUPPAE: qualifier AEX: IDVAR AEGRPID is not a column of AE (rows 1)",
                issue.message());
        assertEquals(List.of(0L), issue.sourceRows());
    }


    @Test
    void case7_anotherStudyOrDomain_noMatchNoError()
    {
        MergedTable t = merge(ae(),
                suppae("S2,AE,01-001,AESEQ,1,AETRTEM,TE,Y", "S1,CM,01-001,AESEQ,1,AETRTEM,TE,Y"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "AETRTEM"));
        assertTrue(t.issues().isEmpty());
        assertEquals(Set.of("AETRTEM"), t.suppQnams(), "the QNAM exists even unmatched");
    }


    @Test
    void case8_subjectLevelOnly_servesEveryRecordOfTheSubject()
    {
        MergedTable t = merge(ae(), suppae("S1,AE,01-001,,,AETRTEM,TE,N"));
        assertEquals(List.of("N", "N"), column(t, "AETRTEM"));
        assertTrue(t.issues().isEmpty());
    }


    @Test
    void case9_send_studyWideRow_servesEverySubjectAndPool_overlapIsAnError()
    {
        IDataTable cl = table("CL", "STUDYID,DOMAIN,USUBJID,POOLID,CLSEQ:N", "S1,CL,A1,,1",
                "S1,CL,,P1,1", "S1,CL,,P2,1", "S1,CL,A1,,2");
        IDataTable supp = table("SUPPCL", "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,CL,,,CLSEQ,1,X,Y");
        MergedTable t = merge(cl, supp);
        assertEquals(List.of("Y", "Y", "Y", UNSUPPLIED), column(t, "X"));
        assertTrue(t.issues().isEmpty());
        // the overlap: pool P1's record is also qualified by a pool-level row
        IDataTable supp2 = table("SUPPCL",
                "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,QNAM,QVAL", "S1,CL,,,CLSEQ,1,X,Y",
                "S1,CL,,P1,CLSEQ,1,X,N");
        MergedTable t2 = merge(cl, supp2);
        assertEquals(List.of("Y", UNSUPPLIED, "Y", UNSUPPLIED), column(t2, "X"));
        assertTrue(onlyIssue(t2).message().contains("more than one row qualifies one record"));
    }


    @Test
    void example_sendPools_eachPoolsRecordGetsItsOwnValue()
    {
        IDataTable cl = table("CL", "STUDYID,DOMAIN,USUBJID,POOLID,CLSEQ:N", "S1,CL,,P1,1",
                "S1,CL,,P2,1");
        IDataTable supp = table("SUPPCL", "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,CL,,P1,CLSEQ,1,CLX,A", "S1,CL,,P2,CLSEQ,1,CLX,B");
        MergedTable t = merge(cl, supp);
        assertEquals(List.of("A", "B"), column(t, "CLX"));
        assertTrue(t.issues().isEmpty());
    }


    @Test
    void example_studyidInTheKey_anotherStudysRowDoesNotMatch()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1");
        MergedTable t = merge(ae, suppae("S2,AE,01-001,AESEQ,1,AETRTEM,TE,Y"));
        assertEquals(List.of(UNSUPPLIED), column(t, "AETRTEM"));
    }


    @Test
    void compare_isByText_numericParentCanonicalised_characterParentExact()
    {
        IDataTable ae = ae();
        for (String spelling : List.of("01", "1.0", " 1", "1E0", "+1"))
        {
            MergedTable t = merge(ae, suppae("S1,AE,01-001,AESEQ," + spelling + ",Q,L,Y"));
            assertEquals(List.of("Y", UNSUPPLIED), column(t, "Q"), "IDVARVAL '" + spelling + "'");
        }
        for (String spelling : List.of("1d", "1.0000000000000001"))
        {
            MergedTable t = merge(ae, suppae("S1,AE,01-001,AESEQ," + spelling + ",Q,L,Y"));
            assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "Q"), spelling);
        }
        MergedTable stripped = merge(ae, suppae("S1,AE,01-001,AESPID,  A1,Q,L,Y"));
        assertEquals(List.of("Y", UNSUPPLIED), column(stripped, "Q"), "character: stripped");
        MergedTable exact = merge(ae, suppae("S1,AE,01-001,AESPID,01,Q,L,Y"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(exact, "Q"),
                "character: '1' is not '01'");
        // a missing parent cell and a blank IDVARVAL never match
        IDataTable aeMissing = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,");
        assertEquals(List.of(UNSUPPLIED),
                column(merge(aeMissing, suppae("S1,AE,01-001,AESEQ,1,Q,L,Y")), "Q"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED),
                column(merge(ae, suppae("S1,AE,01-001,AESEQ,,Q,L,Y")), "Q"));
    }


    @Test
    void p10_aQnamTheBaseCarries_isNotAColumn_caseCollisionIsAnError()
    {
        MergedTable t = merge(ae(), suppae("S1,AE,01-001,AESEQ,1,AESPID,Dup,Y",
                "S1,AE,01-001,AESEQ,1,Q,L,Y", "S1,AE,01-001,AESEQ,2,q,L,N"));
        assertEquals(List.of("STUDYID", "DOMAIN", "USUBJID", "AESEQ", "AESPID", "Q"),
                columnNames(t));
        assertEquals("A1", MergeFixtures.text(t, 0, "AESPID"), "the base's own column wins");
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "Q"));
        assertTrue(onlyIssue(t).message().contains("Q and q differ only in case"));
        assertEquals(Set.of("AESPID", "Q", "q"), t.suppQnams(), "every QNAM is still known");
    }


    @Test
    void columnsAppearInFirstSeenQnamOrder()
    {
        MergedTable t = merge(ae(), suppae("S1,AE,01-001,AESEQ,1,B,b,1",
                "S1,AE,01-001,AESEQ,1,A,a,2", "S1,AE,01-001,AESEQ,2,B,b,3"));
        assertEquals(List.of("STUDYID", "DOMAIN", "USUBJID", "AESEQ", "AESPID", "B", "A"),
                columnNames(t));
        assertEquals(List.of("B", "A"), List.copyOf(t.suppQnams()));
    }


    /**
     * P-7 in full (W1 review M5, re-pinned from "the split's SUPP first, then the domain's"): a
     * split dataset is qualified by its own SUPP <b>and</b> its domain's — both are fed; a record
     * both qualify for one QNAM is a duplicate (case 2), each row named by its own dataset.
     */
    @Test
    void splitDomain_bothSuppsAreFed_aRecordBothQualifyIsADuplicate()
    {
        IDataTable qs36 = table("QS36", "STUDYID,DOMAIN,USUBJID,QSSEQ:N", "S1,QS,01-001,1",
                "S1,QS,01-001,2");
        IDataTable suppqs36 = table("SUPPQS36", SUPP_HEADER, "S1,QS,01-001,QSSEQ,2,QX,x,split");
        IDataTable suppqs = table("SUPPQS", SUPP_HEADER, "S1,QS,01-001,QSSEQ,1,QX,x,domain",
                "S1,QS,01-001,QSSEQ,1,QY,y,only domain");
        MergedTable own = merge(qs36, suppqs36);
        assertEquals(List.of(UNSUPPLIED, "split"), column(own, "QX"));
        MergedTable t = MergedTables.build(
                request(qs36, suppqs36, suppqs).supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of("domain", "split"), column(t, "QX"), "each qualifies its record");
        assertEquals(List.of("only domain", UNSUPPLIED), column(t, "QY"));
        assertTrue(t.issues().isEmpty(), t.issues().toString());
        assertTrue(t.unresolvedDatasets().isEmpty());
        assertEquals("SUPPQS36+SUPPQS",
                t.getMetaData().getColumn("QX").getMetaData(MergeMeta.MERGED_FROM));
        IDataTable clash = table("SUPPQS36", SUPP_HEADER, "S1,QS,01-001,QSSEQ,1,QX,x,split");
        MergedTable dup = MergedTables
                .build(request(qs36, clash, suppqs).supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(dup, "QX"));
        MergeIssue issue = onlyIssue(dup);
        assertEquals("SUPPQS36+SUPPQS: qualifier QX: more than one row qualifies one record (rows"
                + " SUPPQS36 row 1, SUPPQS row 1)", issue.message());
        assertEquals(List.of(0L, 1L), issue.sourceRows(), "rows of the stacked sources");
        MergedTable fallback = MergedTables
                .build(request(qs36, suppqs).supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of("domain", UNSUPPLIED), column(fallback, "QX"), "SUPP<domain> alone");
        assertTrue(fallback.unresolvedDatasets().isEmpty(),
                "a split without its own SUPP is normal");
        MergedTable none = MergedTables
                .build(request(qs36).supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of(new UnresolvedDataset(MergeKind.SUPP, "SUPPQS36")),
                none.unresolvedDatasets());
        assertTrue(none.suppQnams().isEmpty());
        assertEquals(1, none.parts().size());
    }


    /** M5: an explicitly named source the resolver lacks is listed; the others are fed. */
    @Test
    void explicitSources_eachMissingOneIsListed_theOthersFed()
    {
        IDataTable qs36 = table("QS36", "STUDYID,DOMAIN,USUBJID,QSSEQ:N", "S1,QS,01-001,1");
        IDataTable suppqs = table("SUPPQS", SUPP_HEADER, "S1,QS,01-001,QSSEQ,1,QX,x,domain");
        MergedTable t = MergedTables.build(request(qs36, suppqs)
                .supp(SuppMergeSpec.builder().suppDataset("SUPPQS36").suppDataset("SUPPQS").build())
                .build());
        assertEquals(List.of("domain"), column(t, "QX"));
        assertEquals(List.of(new UnresolvedDataset(MergeKind.SUPP, "SUPPQS36")),
                t.unresolvedDatasets());
    }


    /** M5: two sources whose columns disagree in type cannot be stacked — a merge error. */
    @Test
    void twoSourcesOfDifferentShape_areRefused()
    {
        IDataTable qs36 = table("QS36", "STUDYID,DOMAIN,USUBJID,QSSEQ:N", "S1,QS,01-001,1");
        IDataTable suppqs36 = table("SUPPQS36",
                "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL:N,QNAM,QLABEL,QVAL",
                "S1,QS,01-001,QSSEQ,1,QX,x,split");
        IDataTable suppqs = table("SUPPQS", SUPP_HEADER, "S1,QS,01-001,QSSEQ,1,QY,y,domain");
        MergeException ex = assertThrows(MergeException.class, () -> MergedTables.build(
                request(qs36, suppqs36, suppqs).supp(SuppMergeSpec.builder().build()).build()));
        assertTrue(ex.getMessage().startsWith("SUPPQS36+SUPPQS: the supplemental datasets"),
                ex.getMessage());
        assertTrue(ex.getCause() instanceof IllegalArgumentException,
                String.valueOf(ex.getCause()));
    }


    /**
     * M1 (W1 review): the standard is explicit. Under SEND a record-level row naming no subject is
     * study-wide (OD-2) even without a POOLID column; under SDTM it qualifies nothing even with
     * one. UNKNOWN infers from the POOLID column (the {@code m1_sdtm} test and case 9).
     */
    @Test
    void m1_theExplicitStandardDecidesTheStudyWideRow()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-002,1");
        IDataTable supp = suppae("S1,AE,,AESEQ,1,X,x,Y");
        MergedTable send = MergedTables.build(request(ae, supp).standard(MergeStandard.SEND)
                .supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of("Y", "Y"), column(send, "X"));
        IDataTable cl = table("CL", "STUDYID,DOMAIN,USUBJID,POOLID,CLSEQ:N", "S1,CL,A1,,1",
                "S1,CL,,P1,1");
        IDataTable suppcl = table("SUPPCL",
                "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,QNAM,QLABEL,QVAL",
                "S1,CL,,,CLSEQ,1,X,x,Y");
        MergedTable sdtm = MergedTables.build(request(cl, suppcl).standard(MergeStandard.SDTM)
                .supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(sdtm, "X"));
        MergedTable inferred = MergedTables
                .build(request(cl, suppcl).supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of("Y", "Y"), column(inferred, "X"), "UNKNOWN + POOLID ⇒ SEND");
        // a key naming no subject component is the author's: study-wide under SDTM too
        MergedTable authored = MergedTables
                .build(request(ae, supp)
                        .standard(MergeStandard.SDTM).supp(
                                SuppMergeSpec.builder()
                                        .keys(List.of(KeyColumn.of("STUDYID"),
                                                KeyColumn.of("IDVAR"), KeyColumn.of("IDVARVAL")))
                                        .build())
                        .build());
        assertEquals(List.of("Y", "Y"), column(authored, "X"));
    }


    /**
     * L2 (T2 review, lane 5): whether a supplemental row names a subject was read off the ACTIVE
     * key — a {@code SUPPCL} carrying {@code POOLID} over a {@code CL} without it had
     * {@code POOLID} inactive, so a pool's row read as subject-less and, under SEND, qualified
     * every subject's record. The row's OWN subject columns decide: a pool's row names a pool.
     */
    @Test
    void l2_aPoolRowOverABaseWithoutPoolid_isNotStudyWide()
    {
        IDataTable cl = table("CL", "STUDYID,DOMAIN,USUBJID,CLSEQ:N", "S1,CL,A1,1", "S1,CL,A2,1");
        IDataTable pool = table("SUPPCL", "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,CL,,P1,CLSEQ,1,X,pool");
        MergedTable t = MergedTables.build(request(cl, pool).standard(MergeStandard.SEND)
                .supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "X"));
        assertTrue(t.issues().isEmpty());
        // the control: a row naming neither subject nor pool is study-wide (OD-2)
        IDataTable studyWide = table("SUPPCL",
                "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,QNAM,QVAL", "S1,CL,,,CLSEQ,1,X,all");
        MergedTable t2 = MergedTables.build(request(cl, studyWide).standard(MergeStandard.SEND)
                .supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of("all", "all"), column(t2, "X"));
    }


    /**
     * L2 (T2 review, lane 1): the primary's supplemental part read the base's domain from its
     * {@code DOMAIN} column, falling back to its NAME — ignoring the request's
     * {@code primaryDomain}. A split dataset without a {@code DOMAIN} column ({@code QSCG}, domain
     * {@code QS}) then compared {@code RDOMAIN} {@code QS} against {@code QSCG} and matched
     * nothing.
     */
    @Test
    void l2_theRequestsPrimaryDomain_isTheBaseDomainOfThePrimarysSupp()
    {
        IDataTable qs = table("QSCG", "STUDYID,USUBJID,QSSEQ:N", "S,U1,1");
        IDataTable supp = table("SUPPQSCG", SUPP_HEADER, "S,QS,U1,QSSEQ,1,QX,lab,v");
        MergedTable t = MergedTables.build(request(qs, supp).primaryDomain("QS")
                .supp(SuppMergeSpec.builder().suppDataset("SUPPQSCG").build()).build());
        assertEquals(List.of("v"), column(t, "QX"));
        assertTrue(t.issues().isEmpty());
        // the control: without primaryDomain the name stands in, and RDOMAIN QS is not QSCG
        MergedTable byName = MergedTables.build(request(qs, supp)
                .supp(SuppMergeSpec.builder().suppDataset("SUPPQSCG").build()).build());
        assertEquals(List.of(UNSUPPLIED), column(byName, "QX"));
    }


    /**
     * L2 (W1 review): an associated-persons dataset is qualified by {@code SQAP--} (SDTMIG-AP), and
     * {@code APID} is a subject key of the IG default.
     */
    @Test
    void l2_associatedPersons_sqapAndApid()
    {
        IDataTable apdm = table("APDM", "STUDYID,DOMAIN,APID,RSUBJID,SREL", "S1,APDM,AP1,01-001,M",
                "S1,APDM,AP2,01-001,F");
        IDataTable sqapdm = table("SQAPDM", "STUDYID,RDOMAIN,APID,IDVAR,IDVARVAL,QNAM,QLABEL,QVAL",
                "S1,APDM,AP2,,,APX,x,yes");
        MergedTable t = MergedTables
                .build(request(apdm, sqapdm).supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of(UNSUPPLIED, "yes"), column(t, "APX"), "keyed on APID");
        assertEquals("SQAPDM", t.getMetaData().getColumn("APX").getMetaData(MergeMeta.MERGED_FROM));
        MergedTable none = MergedTables.build(request(apdm, table("SUPPAPDM", SUPP_HEADER))
                .supp(SuppMergeSpec.builder().build()).build());
        assertEquals(List.of(new UnresolvedDataset(MergeKind.SUPP, "SQAPDM")),
                none.unresolvedDatasets());
    }


    @Test
    void explicitSuppDatasetAndKeys_theBrowsersSuppdmIntoEveryTable()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-002,1");
        IDataTable suppdm = table("SUPPDM", SUPP_HEADER, "S1,DM,01-001,,,SMOKER,Smoker,Y",
                "S1,DM,01-002,USUBJID,01-002,SMOKER,Smoker,N");
        MergedTable t = MergedTables.build(request(ae, suppdm)
                .supp(SuppMergeSpec.builder().suppDataset("SUPPDM")
                        .keys(List.of(KeyColumn.of("STUDYID"), KeyColumn.of("USUBJID"))).build())
                .naming(MergeNaming.BROWSER).build());
        assertEquals(List.of("Y", "N"), column(t, "SMOKER"),
                "without the IDVAR pair in the key every row is subject-level");
        assertEquals("SUPPDM",
                t.getMetaData().getColumn("SMOKER").getMetaData(MergeMeta.MERGED_FROM));
    }


    @Test
    void suppOfAJoinedDataset_isServedQualified_throughThePartnersRowMap()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-003,1");
        IDataTable dm = table("DM", "STUDYID,DOMAIN,USUBJID,AGE:N", "S1,DM,01-001,54",
                "S1,DM,01-002,61");
        IDataTable suppdm = table("SUPPDM", SUPP_HEADER, "S1,DM,01-001,,,RACEOTH,Race other,X",
                "S1,DM,01-002,,,RACEOTH,Race other,Y");
        MergedTable t = MergedTables.build(request(ae, dm, suppdm).keyed(KeyedMergeSpec.builder()
                .qualifier("DM").key(KeyColumn.of("STUDYID")).key(KeyColumn.of("USUBJID"))
                .joinType(net.cumba.datatable.merge.JoinType.LEFT)
                .supp(SuppMergeSpec.builder().build()).build()).build());
        assertEquals(List.of("X", UNSUPPLIED), column(t, "DM.RACEOTH"));
        assertEquals(List.of("54", UNSUPPLIED), column(t, "DM.AGE"));
        assertTrue(t.suppQnams().isEmpty(), "a partner's QNAMs are not the primary's");
        assertEquals("SUPPDM", t.partOf(t.getMetaData().getColumnIndex("DM.RACEOTH")).source());
        assertEquals("DM", t.partOf(t.getMetaData().getColumnIndex("DM.RACEOTH")).qualifier());
    }


    @Test
    void noSubjectKeyUsable_flagsEveryQualifier()
    {
        IDataTable ta = table("TA", "STUDYID,DOMAIN,ARMCD", "S1,TA,A");
        IDataTable suppta = table("SUPPTA", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                "S1,TA,,ARMCD,A,TX,Y");
        MergedTable t = merge(ta, suppta);
        assertEquals(List.of(UNSUPPLIED), column(t, "TX"));
        assertTrue(onlyIssue(t).message().contains("no subject key column"),
                onlyIssue(t).message());
        // a key that names no subject component at all is the author's choice and is not an error
        MergedTable authored = MergedTables.build(request(ta, suppta).supp(SuppMergeSpec.builder()
                .keys(List.of(KeyColumn.of("STUDYID"), KeyColumn.sided("DOMAIN", "RDOMAIN"),
                        KeyColumn.of("IDVAR"), KeyColumn.of("IDVARVAL")))
                .build()).build());
        assertEquals(List.of("Y"), column(authored, "TX"));
    }


    @Test
    void halfAPair_orAnEmptyKey_isMalformed()
    {
        IDataTable ae = ae();
        IDataTable supp = suppae("S1,AE,01-001,AESEQ,1,Q,L,Y");
        MalformedSidedKeyException ex = assertThrows(MalformedSidedKeyException.class,
                () -> MergedTables.build(request(ae, supp).supp(SuppMergeSpec.builder()
                        .keys(List.of(KeyColumn.of("USUBJID"), KeyColumn.of("IDVAR"))).build())
                        .build()));
        assertTrue(ex.getMessage().contains("found only IDVAR"), ex.getMessage());
        assertThrows(MalformedSidedKeyException.class, () -> MergedTables.build(
                request(ae, supp).supp(SuppMergeSpec.builder().keys(List.of()).build()).build()));
    }


    @Test
    void keyColumnOnOneSideOnly_isIgnored_domainConstantStandsInForAMissingDomainColumn()
    {
        // AE lacks DOMAIN: RDOMAIN is compared against the dataset's domain (its name here)
        IDataTable ae = table("AE", "STUDYID,USUBJID,AESEQ:N", "S1,01-001,1");
        MergedTable t = merge(ae,
                suppae("S1,AE,01-001,AESEQ,1,Q,L,Y", "S1,CM,01-001,AESEQ,1,R,L,Y"));
        assertEquals(List.of("Y"), column(t, "Q"));
        assertEquals(List.of(UNSUPPLIED), column(t, "R"));
        // SUPP lacks STUDYID: the component is ignored, the rows pair on the rest
        IDataTable supp = table("SUPPAE", "RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                "AE,01-001,AESEQ,1,Q,Y");
        assertEquals(List.of("Y", UNSUPPLIED), column(merge(ae(), supp), "Q"));
        // SUPP without IDVAR / IDVARVAL: every row is subject-level (P-15)
        IDataTable supp2 = table("SUPPAE", "STUDYID,RDOMAIN,USUBJID,QNAM,QVAL", "S1,AE,01-001,Q,Y");
        assertEquals(List.of("Y", "Y"), column(merge(ae(), supp2), "Q"));
        // SUPP without QNAM: nothing to merge, no qualifier at all
        IDataTable supp3 = table("SUPPAE", "STUDYID,RDOMAIN,USUBJID,QVAL", "S1,AE,01-001,Y");
        MergedTable t3 = merge(ae(), supp3);
        assertEquals(5, t3.getColumnCount());
        assertTrue(t3.suppQnams().isEmpty());
        // SUPP without QVAL: the qualifier exists, nothing can supply it
        IDataTable supp4 = table("SUPPAE", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM",
                "S1,AE,01-001,AESEQ,1,Q");
        MergedTable t4 = merge(ae(), supp4);
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t4, "Q"));
        assertTrue(onlyIssue(t4).message().contains("no QVAL column"));
    }


    @Test
    void rowsThatQualifyNothing_areDropped()
    {
        // blank QNAM; IDVAR set but IDVARVAL missing; subject-level naming no subject
        MergedTable t = merge(ae(),
                suppae("S1,AE,01-001,AESEQ,1,,L,Y", "S1,AE,01-001,AESEQ,,Q,L,Y", "S1,AE,,,,Q,L,N"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "Q"));
        assertTrue(t.issues().isEmpty());
        assertEquals(Set.of("Q"), t.suppQnams());
    }


    @Test
    void missingKeyCellsKeepTheirIdentity_jkmR5()
    {
        // a MIS STUDYID on the SUPP side never pairs an "" or a present STUDYID on the base
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", ",AE,01-001,1",
                "<MIS>,AE,01-001,2");
        MergedTable t = merge(ae,
                suppae("<MIS>,AE,01-001,AESEQ,1,Q,L,Y", "<MIS>,AE,01-001,AESEQ,2,Q,L,N"));
        assertEquals(List.of(UNSUPPLIED, "N"), column(t, "Q"));
    }


    @Test
    void suppOverAnExpandedPrimary_followsThePrimarysRowMap()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-001,2");
        IDataTable cm = table("CM", "STUDYID,USUBJID,CMTRT", "S1,01-001,A", "S1,01-001,B");
        MergedTable t = MergedTables.build(request(ae, cm, suppae("S1,AE,01-001,AESEQ,2,Q,L,Y"))
                .keyed(KeyedMergeSpec.builder().qualifier("CM").key(KeyColumn.of("STUDYID"))
                        .key(KeyColumn.of("USUBJID")).build())
                .supp(SuppMergeSpec.builder().build()).build());
        assertEquals(4, t.getRowCount());
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED, "Y", "Y"), column(t, "Q"));
        assertEquals(List.of("A", "B", "A", "B"), column(t, "CM.CMTRT"));
    }


    @Test
    void theParseStructureIsReleasedOnceTheRowMapsAreBuilt()
    {
        IDataTable ae = ae();
        IDataTable supp = suppae("S1,AE,01-001,AESEQ,1,AETRTEM,TE,Y");
        AtomicReference<WeakReference<SuppQnamIndex>> seen = new AtomicReference<>();
        MergeRequest req = request(ae, supp).supp(SuppMergeSpec.builder().build()).build();
        MergedTable t = new MergeBuilder(req, index -> seen.set(new WeakReference<>(index)))
                .build();
        assertNotNull(seen.get(), "the matcher parsed the SUPP through the index");
        assertEquals(List.of("Y", UNSUPPLIED), column(t, "AETRTEM"));
        assertEquals(Set.of("AETRTEM"), t.suppQnams());
        for (int i = 0; i < 20 && seen.get().get() != null; i++)
        {
            System.gc();
            try
            {
                Thread.sleep(20);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertNull(seen.get().get(),
                "the index must be unreachable from the merged table (owner 2026-10-05)");
        // the table still reads through its row map after the release
        assertEquals("Y", MergeFixtures.text(t, 0, "AETRTEM"));
        assertFalse(t.isSupplied(1, t.getMetaData().getColumnIndex("AETRTEM")));
    }


    @Test
    void sensitivityControl_aSabotagedRowMapWouldBeSeen()
    {
        // The case-table assertions are only meaningful if a wrong map changes a cell: swap the
        // two SUPP rows' QVALs and the read must move.
        MergedTable t = merge(ae(),
                suppae("S1,AE,01-001,AESEQ,1,Q,L,ONE", "S1,AE,01-001,AESEQ,2,Q,L,TWO"));
        assertEquals(List.of("ONE", "TWO"), column(t, "Q"));
        MergedTable swapped = merge(ae(),
                suppae("S1,AE,01-001,AESEQ,2,Q,L,ONE", "S1,AE,01-001,AESEQ,1,Q,L,TWO"));
        assertEquals(List.of("TWO", "ONE"), column(swapped, "Q"));
    }


    /**
     * M1 (W1 review): a record-level row naming no subject is study-wide only under SEND (OD-2,
     * SENDIG 3.1.1 sec. 8.3). An SDTM SUPP-- row with a blank USUBJID qualifies nothing (JKM R6);
     * W1 served it to every subject's AESEQ 1. Here the standard is inferred (no POOLID column ⇒
     * SDTM).
     */
    @Test
    void m1_sdtm_aBlankSubjectRecordLevelRow_qualifiesNothing()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-002,1", "S1,AE,01-003,2");
        MergedTable t = merge(ae, suppae("S1,AE,,AESEQ,1,AETRTEM,TE,Y"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED, UNSUPPLIED), column(t, "AETRTEM"));
        assertTrue(t.issues().isEmpty(), t.issues().toString());
        assertEquals(Set.of("AETRTEM"), t.suppQnams(), "the QNAM still exists");
    }
}
