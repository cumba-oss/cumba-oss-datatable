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
import net.cumba.datatable.manager.RelrecMergeSpec;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.CardinalityViolationException;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MergeIssue;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.merge.MergeNaming;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTables;
import net.cumba.datatable.merge.RelrecLinks;
import net.cumba.datatable.merge.RelrecPartSpec;
import net.cumba.datatable.merge.UnresolvedDataset;
import org.junit.jupiter.api.Test;

/**
 * The RELREC part of the merged table — {@code PLAN-merged-evaluation-table} §3.6: the pairs
 * (§3.6.1, incl. SEND, OD-3), the rows (§3.6.2 Examples 1–2), per relationship (§3.6.3, OD-4), the
 * join type (§3.6.4), the missing related domain (P-11) and the missing {@code IDVAR} column (the
 * W0 case decided with the {@code MERGE_ERROR} channel, OD-7).
 */
class MergedTableRelrecTest
{

    private static final String RELREC_HEADER = "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,RELID";

    private static IDataTable ae()
    {
        return table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N,AETERM", "S1,AE,01-001,1,HEADACHE",
                "S1,AE,01-001,2,NAUSEA");
    }


    private static IDataTable cm()
    {
        return table("CM", "STUDYID,DOMAIN,USUBJID,CMSEQ:N,CMTRT", "S1,CM,01-001,1,ASPIRIN",
                "S1,CM,01-001,2,PARACETAMOL", "S1,CM,01-001,3,OMEPRAZOLE");
    }


    private static IDataTable lb()
    {
        return table("LB", "STUDYID,DOMAIN,USUBJID,LBSEQ:N,LBTESTCD", "S1,LB,01-001,1,ALT",
                "S1,LB,01-001,2,AST", "S1,LB,01-001,3,BILI");
    }


    private static IDataTable relrec(String... aRows)
    {
        return table("RELREC", RELREC_HEADER, aRows);
    }


    private static MergedTable merge(RelrecPartSpec aSpec, IDataTable aPrimary,
            IDataTable... aOthers)
    {
        return MergedTables.build(request(aPrimary, aOthers).relrec(aSpec).build());
    }


    private static RelrecPartSpec all()
    {
        return RelrecPartSpec.builder().build();
    }


    @Test
    void example1_oneCmAndThreeLb_threeRows()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,LB,01-001,LBSEQ,1,R1", "S1,LB,01-001,LBSEQ,2,R1", "S1,LB,01-001,LBSEQ,3,R1");
        MergedTable t = merge(all(), ae(), rr, cm(), lb());
        assertEquals(3, t.getRowCount(), "inner: AE seq 2 has no relationship");
        assertEquals(List.of("ASPIRIN", "ASPIRIN", "ASPIRIN"), column(t, "CM.CMTRT"));
        assertEquals(List.of("ALT", "AST", "BILI"), column(t, "LB.LBTESTCD"));
        assertEquals(List.of("1", "1", "1"), column(t, "AESEQ"));
        for (long r = 0; r < 3; r++)
        {
            assertEquals(0, t.getRealRowIndex(r));
        }
        assertTrue(t.isRowMapped());
        assertTrue(t.issues().isEmpty());
        assertEquals(MergeKind.RELREC, t.partOf(t.getMetaData().getColumnIndex("CM.CMTRT")).kind());
        assertEquals("CM",
                t.getMetaData().getColumn("CM.CMTRT").getMetaData(MergeMeta.MERGED_FROM));
        assertEquals("RELREC",
                t.getMetaData().getColumn("CM.CMTRT").getMetaData(MergeMeta.MERGE_KIND));
    }


    @Test
    void example2_twoCmAndThreeLb_sixRows_everyCombination()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,CM,01-001,CMSEQ,2,R1", "S1,LB,01-001,LBSEQ,1,R1", "S1,LB,01-001,LBSEQ,2,R1",
                "S1,LB,01-001,LBSEQ,3,R1");
        MergedTable t = merge(all(), ae(), rr, cm(), lb());
        assertEquals(6, t.getRowCount());
        assertEquals(List.of("ASPIRIN", "ASPIRIN", "ASPIRIN", "PARACETAMOL", "PARACETAMOL",
                "PARACETAMOL"), column(t, "CM.CMTRT"));
        assertEquals(List.of("ALT", "AST", "BILI", "ALT", "AST", "BILI"), column(t, "LB.LBTESTCD"));
    }


    @Test
    void perRelationship_od4_relationshipsAreSummedNeverCrossed()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,LB,01-001,LBSEQ,1,R1", "S1,AE,01-001,AESEQ,1,R2", "S1,CM,01-001,CMSEQ,3,R2");
        MergedTable t = merge(all(), ae(), rr, cm(), lb());
        assertEquals(2, t.getRowCount());
        assertEquals(List.of("ASPIRIN", "ALT"), row(t, 0, "CM.CMTRT", "LB.LBTESTCD"));
        assertEquals(List.of("OMEPRAZOLE", UNSUPPLIED), row(t, 1, "CM.CMTRT", "LB.LBTESTCD"));
        // a pair shared by two relationships is one row (the browser's rule, kept)
        IDataTable rr2 = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,AE,01-001,AESEQ,1,R2", "S1,CM,01-001,CMSEQ,1,R2");
        MergedTable t2 = merge(all(), ae(), rr2, cm());
        assertEquals(1, t2.getRowCount());
    }


    @Test
    void joinType_innerDropsUnrelated_leftKeepsThemWithFlags()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1");
        MergedTable inner = MergedTables
                .build(request(ae(), rr, cm()).relrec(all()).matchedFlags(true).build());
        assertEquals(1, inner.getRowCount());
        assertEquals(List.of("true"), column(inner, "CM._matched_"));
        assertEquals(List.of("true"), column(inner, "RELREC._matched_"));
        MergedTable left = MergedTables.build(request(ae(), rr, cm())
                .relrec(RelrecPartSpec.builder().joinType(JoinType.LEFT).build()).matchedFlags(true)
                .build());
        assertEquals(2, left.getRowCount());
        assertEquals(List.of("ASPIRIN", UNSUPPLIED), column(left, "CM.CMTRT"));
        assertEquals(List.of("true", "false"), column(left, "CM._matched_"));
        assertEquals(List.of("true", "false"), column(left, "RELREC._matched_"));
        assertEquals(1, left.getRealRowIndex(1));
        // RELREC._matched_ is "any relationship", CM._matched_ "this row carries a CM record"
        IDataTable rr2 = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,AE,01-001,AESEQ,2,R2", "S1,LB,01-001,LBSEQ,1,R2");
        MergedTable t = MergedTables
                .build(request(ae(), rr2, cm(), lb()).relrec(all()).matchedFlags(true).build());
        assertEquals(List.of("true", "false"), column(t, "CM._matched_"));
        assertEquals(List.of("false", "true"), column(t, "LB._matched_"));
        assertEquals(List.of("true", "true"), column(t, "RELREC._matched_"));
    }


    @Test
    void datasetLevelLink_joinsWithinTheSubjectOnEqualIdvarValues()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AELNKID,AETERM", "S1,AE,01-001,L1,HEAD",
                "S1,AE,01-001,L2,NAUSEA", "S1,AE,01-002,L1,RASH");
        IDataTable fa = table("FA", "STUDYID,DOMAIN,USUBJID,FALNKID,FAOBJ", "S1,FA,01-001,L1,A",
                "S1,FA,01-001,L1,B", "S1,FA,01-002,L1,C", "S1,FA,01-001,L9,Z");
        IDataTable rr = relrec("S1,AE,,AELNKID,,AEFA", "S1,FA,,FALNKID,,AEFA");
        MergedTable t = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT).build(), ae, rr, fa);
        assertEquals(4, t.getRowCount());
        assertEquals(List.of("A", "B", UNSUPPLIED, "C"), column(t, "FA.FAOBJ"));
        assertEquals(List.of(0L, 0L, 1L, 2L), List.of(t.getRealRowIndex(0), t.getRealRowIndex(1),
                t.getRealRowIndex(2), t.getRealRowIndex(3)));
    }


    @Test
    void send_od3_aRelationshipSpansSubjectsAndPools()
    {
        IDataTable cl = table("CL", "STUDYID,DOMAIN,USUBJID,POOLID,CLSEQ:N,CLTEST",
                "S1,CL,A1,,1,OBS", "S1,CL,,P1,1,POOLOBS");
        IDataTable lb = table("LB", "STUDYID,DOMAIN,USUBJID,POOLID,LBSEQ:N,LBTESTCD",
                "S1,LB,A1,,1,ALT", "S1,LB,,P1,1,AST", "S1,LB,,P2,1,BILI");
        // SEND RELREC carries POOLID: a relationship is (STUDYID, RELID); each row names its
        // record by USUBJID or POOLID
        IDataTable rr = table("RELREC", "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,RELID",
                "S1,CL,A1,,CLSEQ,1,R1", "S1,LB,,P1,LBSEQ,1,R1", "S1,LB,,P2,LBSEQ,1,R1",
                "S1,CL,,P1,CLSEQ,1,R2", "S1,LB,A1,,LBSEQ,1,R2");
        MergedTable t = merge(all(), cl, rr, lb);
        assertEquals(3, t.getRowCount());
        assertEquals(List.of("OBS", "OBS", "POOLOBS"), column(t, "CLTEST"));
        assertEquals(List.of("AST", "BILI", "ALT"), column(t, "LB.LBTESTCD"));
        // and the scan agrees
        RelrecLinks.Links links = RelrecLinks.collect(rr, "CL", null);
        assertTrue(links.send());
        assertEquals(2, links.relationshipCount());
        assertEquals(List.of("LB"), links.selectableDomains());
    }


    @Test
    void sdtm_groupsByStudySubjectAndRelid_soTwoSubjectsSharingARelidAreTwoRelationships()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N", "S1,AE,01-001,1",
                "S1,AE,01-002,1");
        IDataTable cm = table("CM", "STUDYID,DOMAIN,USUBJID,CMSEQ:N,CMTRT", "S1,CM,01-001,1,A",
                "S1,CM,01-002,1,B");
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,AE,01-002,AESEQ,1,R1", "S1,CM,01-002,CMSEQ,1,R1");
        MergedTable t = merge(all(), ae, rr, cm);
        assertEquals(List.of("A", "B"), column(t, "CM.CMTRT"));
        assertEquals(2, RelrecLinks.collect(rr, "AE", null).relationshipCount());
    }


    @Test
    void aMissingRelatedDomain_isReportedForTheCallerToDecide_p11()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,LB,01-001,LBSEQ,1,R1");
        MergedTable t = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT).build(), ae(), rr,
                cm());
        assertEquals(List.of(new UnresolvedDataset(MergeKind.RELREC, "LB")),
                t.unresolvedDatasets());
        assertEquals(List.of("ASPIRIN", UNSUPPLIED), column(t, "CM.CMTRT"));
        assertFalse(columnNames(t).contains("LB.LBTESTCD"), "no columns for an unknown schema");
        MergedTable none = merge(all(), ae());
        assertEquals(List.of(new UnresolvedDataset(MergeKind.RELREC, "RELREC")),
                none.unresolvedDatasets());
        assertEquals(2, none.getRowCount(), "no RELREC: nothing merged, nothing dropped");
    }


    @Test
    void aRelatedDomainLackingTheColumnRelrecNames_isFlagged_od7()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMGRPID,1,R1");
        MergedTable t = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT).build(), ae(), rr,
                cm());
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "CM.CMTRT"));
        MergeIssue issue = t.issues().get(0);
        assertEquals(MergeKind.RELREC, issue.kind());
        assertEquals("CM", issue.column());
        assertEquals("RELREC names column CMGRPID of related domain CM, which CM does not have"
                + " (RELREC rows 2)", issue.message());
        assertEquals(List.of(1L), issue.sourceRows());
        assertEquals(issue.message(),
                t.getMetaData().getColumn("CM.CMTRT").getMetaData(MergeMeta.MERGE_ERROR));
        // the primary lacking the column named for it is the same channel
        IDataTable rr2 = relrec("S1,AE,01-001,AEGRPID,1,R1", "S1,CM,01-001,CMSEQ,1,R1");
        MergedTable t2 = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT).build(), ae(), rr2,
                cm());
        assertTrue(t2.issues().get(0).message().contains("names column AEGRPID of AE"),
                t2.issues().get(0).message());
    }


    @Test
    void selection_restrictsToDomainsOrRelids_unselectedDomainsAreNeverResolved()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,LB,01-001,LBSEQ,1,R1", "S1,AE,01-001,AESEQ,2,R2", "S1,CM,01-001,CMSEQ,3,R2");
        RelrecMergeSpec cmOnly = RelrecMergeSpec.builder()
                .relationship(RelrecMergeSpec.Item.builder().rdomain("cm").build()).build();
        MergedTable t = merge(RelrecPartSpec.builder().selection(cmOnly).build(), ae(), rr, cm());
        assertEquals(List.of("ASPIRIN", "OMEPRAZOLE"), column(t, "CM.CMTRT"));
        assertFalse(columnNames(t).contains("LB.LBTESTCD"));
        assertTrue(t.unresolvedDatasets().isEmpty(), "LB was never asked for");
        RelrecMergeSpec r2 = RelrecMergeSpec.builder()
                .relationship(RelrecMergeSpec.Item.builder().rdomain("CM").relid("R2").build())
                .build();
        MergedTable t2 = merge(RelrecPartSpec.builder().selection(r2).build(), ae(), rr, cm());
        assertEquals(List.of("OMEPRAZOLE"), column(t2, "CM.CMTRT"));
        assertEquals(1, t2.getRealRowIndex(0));
    }


    /**
     * L4 (W1 review, re-pinned): a related domain whose links mix record- and dataset-level joins
     * still pairs nothing, but is no longer a silent INFO skip — its columns are present, not
     * supplied, and flagged.
     */
    @Test
    void mixedRecordAndDatasetLevelLinks_pairNothing_andFlagTheDomain()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N,AELNKID", "S1,AE,01-001,1,L1");
        IDataTable fa = table("FA", "STUDYID,DOMAIN,USUBJID,FASEQ:N,FALNKID,FAOBJ",
                "S1,FA,01-001,1,L1,A");
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,FA,01-001,FASEQ,1,R1",
                "S1,AE,,AELNKID,,R2", "S1,FA,,FALNKID,,R2");
        RelrecLinks.RelrecScan scan = RelrecLinks.scan(rr, "AE");
        assertEquals(List.of("FA"), scan.mixedSkipped());
        assertTrue(scan.selectable().isEmpty());
        MergedTable t = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT).build(), ae, rr, fa);
        assertEquals(1, t.getRowCount());
        assertEquals(List.of(UNSUPPLIED), column(t, "FA.FAOBJ"));
        MergeIssue issue = t.issues().get(0);
        assertEquals(1, t.issues().size());
        assertEquals("FA", issue.column());
        assertTrue(issue.message().contains("both record-level and dataset-level"),
                issue.message());
        assertEquals(List.of(1L, 3L), issue.sourceRows());
        assertEquals(issue.message(),
                t.getMetaData().getColumn("FA.FAOBJ").getMetaData(MergeMeta.MERGE_ERROR));
        // the browser's setting keeps the flagged part too (M2)
        MergedTable browser = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT)
                .includeUnmatchedDomains(false).build(), ae, rr, fa);
        assertEquals(List.of(UNSUPPLIED), column(browser, "FA.FAOBJ"));
    }


    /**
     * OD-5 (W1 review M4): unauthored, a dataset-level relationship's RELTYPE is its cardinality,
     * checked within that relationship — TU ONE to TR ONE, but one tumour links two results.
     */
    @Test
    void m4_reltypeIsTheIgDefault_perRelationship()
    {
        IDataTable tu = table("TU", "STUDYID,DOMAIN,USUBJID,TUSEQ:N,TULNKID", "S1,TU,01-001,1,T01",
                "S1,TU,01-001,2,T02");
        IDataTable tr = table("TR", "STUDYID,DOMAIN,USUBJID,TRSEQ:N,TRLNKID", "S1,TR,01-001,1,T01",
                "S1,TR,01-001,2,T01", "S1,TR,01-001,3,T02");
        IDataTable one = table("RELREC", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,RELTYPE,RELID",
                "S1,TU,,TULNKID,,ONE,R1", "S1,TR,,TRLNKID,,ONE,R1");
        CardinalityViolationException ex = assertThrows(CardinalityViolationException.class,
                () -> merge(all(), tu, one, tr));
        assertEquals("TR", ex.dataset());
        assertTrue(ex.getMessage().contains("in relationship R1"), ex.getMessage());
        assertTrue(ex.getMessage().startsWith("Cardinality 1:1"), ex.getMessage());
        IDataTable many = table("RELREC", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,RELTYPE,RELID",
                "S1,TU,,TULNKID,,ONE,R1", "S1,TR,,TRLNKID,,MANY,R1");
        MergedTable t = merge(all(), tu, many, tr);
        assertEquals(List.of("1", "2", "3"), column(t, "TR.TRSEQ"), "1:n holds");
        // the browser passes n:m (P-12): RELTYPE is not checked, the WARNING names the relationship
        try (var logs = LogCapture.of(MergeBuilder.class))
        {
            MergedTable browser = merge(
                    RelrecPartSpec.builder().cardinality(Cardinality.MANY_TO_MANY).build(), tu, one,
                    tr);
            assertEquals(3, browser.getRowCount());
            assertTrue(logs.anyMessageContains(
                    "disagrees with the 1:1 that RELTYPE states for" + " relationship R1 with TR"),
                    logs.records().toString());
        }
    }


    @Test
    void browserNaming_dropsStructuralKeys_renamesOnAClash()
    {
        IDataTable ae = table("AE", "STUDYID,DOMAIN,USUBJID,AESEQ:N,FAOBJ,FA_FAOBJ,FA_FAOBJ_2",
                "S1,AE,01-001,1,x,y,z");
        IDataTable fa = table("FA", "STUDYID,DOMAIN,USUBJID,FASEQ:N,FAOBJ", "S1,FA,01-001,1,OBJ");
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,FA,01-001,FASEQ,1,R1");
        MergedTable t = MergedTables.build(
                request(ae, rr, fa).relrec(RelrecPartSpec.builder().joinType(JoinType.LEFT).build())
                        .naming(MergeNaming.BROWSER).build());
        assertEquals(List.of("STUDYID", "DOMAIN", "USUBJID", "AESEQ", "FAOBJ", "FA_FAOBJ",
                "FA_FAOBJ_2", "FASEQ", "FA_FAOBJ_3"), columnNames(t));
        assertEquals("OBJ", MergeFixtures.text(t, 0, "FA_FAOBJ_3"));
    }


    @Test
    void includeUnmatchedDomains_false_leavesOutADomainNoRecordPairsWith()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,AE,01-001,AESEQ,9,R2", "S1,LB,01-001,LBSEQ,1,R2");
        MergedTable with = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT).build(), ae(), rr,
                cm(), lb());
        assertTrue(columnNames(with).contains("LB.LBTESTCD"));
        assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(with, "LB.LBTESTCD"));
        MergedTable without = merge(RelrecPartSpec.builder().joinType(JoinType.LEFT)
                .includeUnmatchedDomains(false).build(), ae(), rr, cm(), lb());
        assertFalse(columnNames(without).contains("LB.LBTESTCD"));
        assertEquals(List.of("ASPIRIN", UNSUPPLIED), column(without, "CM.CMTRT"));
    }


    @Test
    void cardinality_overThePairsOfEachDomain()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,CM,01-001,CMSEQ,2,R1");
        CardinalityViolationException ex = assertThrows(CardinalityViolationException.class,
                () -> merge(RelrecPartSpec.builder().cardinality(Cardinality.MANY_TO_ONE).build(),
                        ae(), rr, cm()));
        assertEquals("CM", ex.dataset());
        MergedTable ok = merge(
                RelrecPartSpec.builder().cardinality(Cardinality.ONE_TO_MANY).build(), ae(), rr,
                cm());
        assertEquals(2, ok.getRowCount());
        IDataTable rr2 = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,AE,01-001,AESEQ,2,R2", "S1,CM,01-001,CMSEQ,1,R2");
        assertThrows(CardinalityViolationException.class,
                () -> merge(RelrecPartSpec.builder().cardinality(Cardinality.ONE_TO_ONE).build(),
                        ae(), rr2, cm()));
    }


    @Test
    void relrecComposesWithAKeyedExpansion_mq11()
    {
        IDataTable dm = table("DM", "STUDYID,USUBJID,AGE:N", "S1,01-001,54", "S1,01-001,55");
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,CM,01-001,CMSEQ,2,R1");
        MergedTable t = MergedTables.build(request(ae(), rr, cm(), dm)
                .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("STUDYID"))
                        .key(KeyColumn.of("USUBJID")).build())
                .relrec(all()).build());
        assertEquals(4, t.getRowCount());
        assertEquals(List.of("54", "54", "55", "55"), column(t, "DM.AGE"));
        assertEquals(List.of("ASPIRIN", "PARACETAMOL", "ASPIRIN", "PARACETAMOL"),
                column(t, "CM.CMTRT"));
    }


    @Test
    void scanOffersExactlyWhatTheMergePairs()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,LB,01-001,LBSEQ,1,R1", "S1,AE,01-001,AESEQ,2,R2", "S1,EX,01-001,EXSEQ,1,R2");
        RelrecLinks.RelrecScan scan = RelrecLinks.scan(rr, "AE");
        assertEquals(List.of("CM", "LB", "EX"), scan.selectable());
        assertTrue(scan.mixedSkipped().isEmpty());
        assertEquals(RelrecLinks.RelrecScan.empty(), RelrecLinks.scan(rr, null));
        assertEquals(RelrecLinks.RelrecScan.empty(),
                RelrecLinks.scan(table("RELREC", "RDOMAIN,USUBJID", "AE,01-001"), "AE"));
        assertEquals(RelrecLinks.Links.empty(),
                RelrecLinks.collect(table("RELREC", "RDOMAIN,RELID", "AE,R1"), "AE", null));
    }


    /**
     * H1 (W1 review), the RELREC shape: inner (the default) — AE seq 1 pairs with two CM records,
     * AE seq 2 with none, so the row count stays 2 while row 1 is AE seq 1's second pair. Before
     * the fix part 0 read AE seq 2 on row 1 beside CM seq 2.
     */
    @Test
    void h1_inner_oneRecordDoubled_oneDropped_everyPartReadsOneRecord()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,CM,01-001,CMSEQ,2,R1");
        MergedTable t = merge(all(), ae(), rr, cm());
        assertEquals(2, t.getRowCount());
        assertEquals(List.of("1", "1"), column(t, "AESEQ"));
        assertEquals(List.of("HEADACHE", "HEADACHE"), column(t, "AETERM"));
        assertEquals(List.of("ASPIRIN", "PARACETAMOL"), column(t, "CM.CMTRT"));
        assertEquals(List.of(0L, 0L), List.of(t.getRealRowIndex(0), t.getRealRowIndex(1)));
        assertTrue(t.isRowMapped());
    }


    /**
     * M2 (W1 review): under the browser's setting ({@code includeUnmatchedDomains(false)}) a
     * related domain whose only link names a column it lacks has no pairs — its issue was dropped
     * with the part. It must still be published, and the domain's columns carry it so the browser
     * (which reads {@code MERGE_ERROR} from the column metadata) warns.
     */
    @Test
    void m2_aDomainWithAnIssueButNoPairs_surfacesItUnderTheBrowserSetting()
    {
        IDataTable rr = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMGRPID,1,R1");
        for (boolean includeUnmatched : new boolean[]
        {
                true, false
        })
        {
            MergedTable t = MergedTables
                    .build(request(ae(), rr, cm()).primaryDomain("AE").naming(MergeNaming.BROWSER)
                            .relrec(RelrecPartSpec.builder().joinType(JoinType.LEFT)
                                    .includeUnmatchedDomains(includeUnmatched).build())
                            .build());
            assertEquals(1, t.issues().size(), "includeUnmatched=" + includeUnmatched);
            assertTrue(t.issues().get(0).message().contains("CMGRPID"),
                    t.issues().get(0).message());
            assertEquals(List.of(UNSUPPLIED, UNSUPPLIED), column(t, "CMTRT"));
            assertEquals(t.issues().get(0).message(),
                    t.getMetaData().getColumn("CMTRT").getMetaData(MergeMeta.MERGE_ERROR));
        }
    }
}
