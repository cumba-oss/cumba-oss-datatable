package net.cumba.datatable.impl.merge;

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
import net.cumba.datatable.merge.ChildMergeSpec;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.MergeColumnClashException;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTables;
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
        assertFalse(t.isExpanded());
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
        assertTrue(t.isExpanded());
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
        assertEquals(List.of("LB"), t.unresolvedDatasets());
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
}
