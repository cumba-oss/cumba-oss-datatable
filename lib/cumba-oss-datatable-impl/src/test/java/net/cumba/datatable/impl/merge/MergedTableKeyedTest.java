package net.cumba.datatable.impl.merge;

import static net.cumba.datatable.impl.merge.MergeFixtures.UNSUPPLIED;
import static net.cumba.datatable.impl.merge.MergeFixtures.column;
import static net.cumba.datatable.impl.merge.MergeFixtures.columnNames;
import static net.cumba.datatable.impl.merge.MergeFixtures.request;
import static net.cumba.datatable.impl.merge.MergeFixtures.row;
import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static net.cumba.datatable.impl.merge.MergeFixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.CardinalityViolationException;
import net.cumba.datatable.merge.DegenerateJoinKeyException;
import net.cumba.datatable.merge.JoinKeyTypeMismatchException;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MalformedSidedKeyException;
import net.cumba.datatable.merge.MergeColumnClashException;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.merge.MergeNaming;
import net.cumba.datatable.merge.MergedPart;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTables;
import net.cumba.datatable.merge.UnresolvedQualifiedKeyException;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * The keyed part of the merged table — {@code PLAN-merged-evaluation-table} §3.4 (examples A–D,
 * {@code Cardinality}), §3.1 (the marker, the flag, provenance) and §3.2 (names).
 */
class MergedTableKeyedTest
{

    private static final String AE_HEADER = "STUDYID,DOMAIN,USUBJID,AESEQ:N,AETERM";

    private static IDataTable ae(String... aRows)
    {
        return table("AE", AE_HEADER, aRows);
    }


    private static IDataTable dm(String... aRows)
    {
        return table("DM", "STUDYID,USUBJID,AGE:N,SEX", aRows);
    }


    private static KeyedMergeSpec.KeyedMergeSpecBuilder dmJoin()
    {
        return KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("STUDYID"))
                .key(KeyColumn.of("USUBJID"));
    }


    @Test
    void exampleA_nToOneHolds_eachRowReadsItsSubjectsPartnerColumn()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE", "S1,AE,01-001,2,NAUSEA",
                "S1,AE,01-002,1,RASH");
        IDataTable dm = dm("S1,01-001,54,M", "S1,01-002,61,F");
        MergedTable t = MergedTables.build(request(ae, dm)
                .keyed(dmJoin().cardinality(Cardinality.MANY_TO_ONE).build()).build());
        assertEquals(3, t.getRowCount());
        assertEquals(List.of("54", "54", "61"), column(t, "DM.AGE"));
        assertEquals(List.of("M", "M", "F"), column(t, "DM.SEX"));
        assertFalse(t.isExpanded());
        assertEquals(List.of(0L, 1L, 2L),
                List.of(t.getRealRowIndex(0), t.getRealRowIndex(1), t.getRealRowIndex(2)));
        assertEquals(List.of("STUDYID", "DOMAIN", "USUBJID", "AESEQ", "AETERM", "DM.STUDYID",
                "DM.USUBJID", "DM.AGE", "DM.SEX"), columnNames(t));
        assertTrue(t.issues().isEmpty());
        assertTrue(t.unresolvedDatasets().isEmpty());
    }


    @Test
    void exampleB_nToOneViolated_isAnErrorNamingDatasetKeyAndRows()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE");
        IDataTable dm = dm("S1,01-001,54,M", "S1,01-002,61,F", "S1,01-003,33,F", "S1,01-004,40,M",
                "S1,01-001,55,M");
        CardinalityViolationException ex = assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(ae, dm)
                        .keyed(dmJoin().cardinality(Cardinality.MANY_TO_ONE).build()).build()));
        assertEquals("Cardinality n:1: DM has duplicate key (STUDYID=S1, USUBJID=01-001) at rows 1"
                + " and 5", ex.getMessage());
        assertEquals("DM", ex.dataset());
        assertEquals(0, ex.firstRow());
        assertEquals(4, ex.secondRow());
    }


    @Test
    void exampleB_pooledDm_isNotAViolationOnceTheKeyCarriesStudyid()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE", "S2,AE,01-001,1,FEVER");
        IDataTable dm = dm("S1,01-001,54,M", "S2,01-001,70,M");
        MergedTable t = MergedTables.build(request(ae, dm)
                .keyed(dmJoin().cardinality(Cardinality.MANY_TO_ONE).build()).build());
        assertEquals(List.of("54", "70"), column(t, "DM.AGE"));
        // keyed on USUBJID alone the same DM is a duplicate
        assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(ae, dm)
                        .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                                .cardinality(Cardinality.MANY_TO_ONE).build())
                        .build()));
    }


    @Test
    void exampleC_unauthoredIsNtoM_theRecordExpandsOncePerPartnerRow()
    {
        IDataTable sm = table("SM", "STUDYID,USUBJID,MIDSTYPE,SMSEQ:N", "S1,01-001,ECHO,1",
                "S1,01-001,MRI,2", "S1,01-002,ECHO,1");
        IDataTable tm = table("TM", "STUDYID,MIDSTYPE,TMDEF", "S1,ECHO,Echo 1", "S1,ECHO,Echo 2",
                "S1,MRI,Mri");
        MergedTable t = MergedTables.build(request(sm, tm).keyed(
                KeyedMergeSpec.builder().qualifier("TM").key(KeyColumn.of("MIDSTYPE")).build())
                .build());
        assertEquals(5, t.getRowCount());
        assertTrue(t.isExpanded());
        assertEquals(List.of("Echo 1", "Echo 2", "Mri", "Echo 1", "Echo 2"), column(t, "TM.TMDEF"));
        assertEquals(List.of("1", "1", "2", "1", "1"), column(t, "SMSEQ"));
        assertEquals(List.of(0L, 0L, 1L, 2L, 2L),
                List.of(t.getRealRowIndex(0), t.getRealRowIndex(1), t.getRealRowIndex(2),
                        t.getRealRowIndex(3), t.getRealRowIndex(4)));
        // with Cardinality n:1 the same data errors instead
        assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(sm, tm).keyed(
                        KeyedMergeSpec.builder().qualifier("TM").key(KeyColumn.of("MIDSTYPE"))
                                .cardinality(Cardinality.MANY_TO_ONE).build())
                        .build()));
    }


    @Test
    void oneToN_requiresThePrimaryUnique()
    {
        IDataTable sm = table("SM", "STUDYID,USUBJID,MIDSTYPE", "S1,01-001,ECHO", "S1,01-002,ECHO");
        IDataTable tm = table("TM", "STUDYID,MIDSTYPE,TMDEF", "S1,ECHO,Echo 1", "S1,ECHO,Echo 2");
        CardinalityViolationException ex = assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(sm, tm).keyed(
                        KeyedMergeSpec.builder().qualifier("TM").key(KeyColumn.of("MIDSTYPE"))
                                .cardinality(Cardinality.ONE_TO_MANY).build())
                        .build()));
        assertEquals("Cardinality 1:n: SM has duplicate key (MIDSTYPE=ECHO) at rows 1 and 2",
                ex.getMessage());
        // 1:1 checks both sides; a unique primary with a repeating partner fails on the partner
        IDataTable sm1 = table("SM", "STUDYID,USUBJID,MIDSTYPE", "S1,01-001,ECHO");
        CardinalityViolationException ex2 = assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(sm1, tm).keyed(
                        KeyedMergeSpec.builder().qualifier("TM").key(KeyColumn.of("MIDSTYPE"))
                                .cardinality(Cardinality.ONE_TO_ONE).build())
                        .build()));
        assertEquals("TM", ex2.dataset());
        // and passes when both are unique
        IDataTable tm1 = table("TM", "STUDYID,MIDSTYPE,TMDEF", "S1,ECHO,Echo 1");
        MergedTable t = MergedTables.build(request(sm1, tm1)
                .keyed(KeyedMergeSpec.builder().qualifier("TM").key(KeyColumn.of("MIDSTYPE"))
                        .cardinality(Cardinality.ONE_TO_ONE).build())
                .build());
        assertEquals(List.of("Echo 1"), column(t, "TM.TMDEF"));
    }


    @Test
    void exampleD_leftKeepsAnUnmatchedRowNotSupplied_innerDropsIt()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE", "S1,AE,01-003,1,RASH");
        IDataTable dm = dm("S1,01-001,54,M");
        MergedTable left = MergedTables.build(request(ae, dm)
                .keyed(dmJoin().joinType(JoinType.LEFT).build()).matchedFlags(true).build());
        assertEquals(2, left.getRowCount());
        assertEquals(List.of("54", UNSUPPLIED), column(left, "DM.AGE"));
        assertEquals(List.of("M", UNSUPPLIED), column(left, "DM.SEX"));
        assertEquals(List.of("true", "false"), column(left, "DM._matched_"));
        // the VALUE at the marker is the type default: MIS numeric, "" character (D72a-1, D34 #3)
        int age = left.getMetaData().getColumnIndex("DM.AGE");
        int sex = left.getMetaData().getColumnIndex("DM.SEX");
        assertTrue(left.getDataValue(1, age).isMissingOrInvalid());
        assertEquals("", left.getDataValue(1, sex).getValueAsString());
        assertEquals("", left.getValue(1, sex));
        assertFalse(left.isSupplied(1, age));
        assertTrue(left.isSupplied(0, age));
        assertTrue(left.isSupplied(1, 0), "a primary cell is always supplied");

        MergedTable inner = MergedTables
                .build(request(ae, dm).keyed(dmJoin().joinType(JoinType.INNER).build()).build());
        assertEquals(1, inner.getRowCount());
        assertEquals("HEADACHE", text(inner, 0, "AETERM"));
        assertEquals(0, inner.getRealRowIndex(0));
    }


    @Test
    void partsAndMetadataDescribeTheMergedColumns()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE");
        IDataTable dm = dm("S1,01-001,54,M");
        MergedTable t = MergedTables
                .build(request(ae, dm).keyed(dmJoin().build()).matchedFlags(true).build());
        assertEquals(2, t.parts().size());
        MergedPart primary = t.parts().get(0);
        assertEquals(MergeKind.PRIMARY, primary.kind());
        assertEquals(0, primary.firstColumn());
        assertEquals(5, primary.columnCount());
        assertEquals(-1, primary.matchedColumn());
        MergedPart dmPart = t.parts().get(1);
        assertEquals(MergeKind.KEYED, dmPart.kind());
        assertEquals("DM", dmPart.qualifier());
        assertEquals("DM", dmPart.source());
        assertEquals(5, dmPart.firstColumn());
        assertEquals(5, dmPart.columnCount());
        assertEquals(9, dmPart.matchedColumn());
        assertTrue(dmPart.contains(9));
        assertFalse(dmPart.contains(4));
        assertEquals(dmPart, t.partOf(7));
        assertEquals(primary, t.partOf(0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.partOf(10));
        assertEquals(ae, t.primary());

        DataTableColumnMeta age = t.getMetaData().getColumn("DM.AGE");
        assertEquals("DM", age.getMetaData(MergeMeta.MERGED_FROM));
        assertEquals("AGE", age.getMetaData(MergeMeta.MERGED_SOURCE_COLUMN));
        assertEquals("KEYED", age.getMetaData(MergeMeta.MERGE_KIND));
        assertEquals("AGE label", age.getLabel(), "the source column's label travels");
        assertEquals(DataValueType.DOUBLE, age.getType());
        assertNull(age.getMetaData(MergeMeta.MERGE_ERROR));
        assertNull(t.getMetaData().getColumn("AETERM").getMetaData(MergeMeta.MERGED_FROM),
                "an own column carries no merge metadata");
        DataTableColumnMeta flag = t.getMetaData().getColumn("DM._matched_");
        assertEquals(DataValueType.BOOLEAN, flag.getType());
        assertEquals("_matched_", flag.getMetaData(MergeMeta.MERGED_SOURCE_COLUMN));
        assertEquals("DM", flag.getMetaData(MergeMeta.MERGED_FROM));
        assertEquals("true", String.valueOf(t.getValue(0, 9)));
        assertTrue(t.isSupplied(0, 9));
    }


    @Test
    void lookupIsCaseInsensitiveOnBothHalves()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE");
        IDataTable dm = dm("S1,01-001,54,M");
        MergedTable t = MergedTables.build(request(ae, dm).keyed(dmJoin().build()).build());
        int idx = t.getMetaData().getColumnIndex("DM.AGE");
        assertEquals(idx, t.getMetaData().getColumnIndex("dm.age"));
        assertEquals(idx, t.getMetaData().getColumnIndex("Dm.Age"));
    }


    @Test
    void browserNamingIsBare_leavesOutColumnsTheTableHas_andRefusesAClashAcrossParts()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE", "S1,AE,01-003,1,RASH");
        IDataTable dm = dm("S1,01-001,54,M");
        MergedTable t = MergedTables.build(request(ae, dm).keyed(
                dmJoin().cardinality(Cardinality.MANY_TO_ONE).joinType(JoinType.LEFT).build())
                .naming(MergeNaming.BROWSER).build());
        assertEquals(List.of("STUDYID", "DOMAIN", "USUBJID", "AESEQ", "AETERM", "AGE", "SEX"),
                columnNames(t), "the keys the table already carries are left out");
        assertEquals(List.of("54", UNSUPPLIED), column(t, "AGE"));
        assertEquals("", t.getValue(1, t.getMetaData().getColumnIndex("SEX")));
        // two parts offering one bare name: refused
        IDataTable dm2 = table("DM2", "STUDYID,USUBJID,AGE:N", "S1,01-001,99");
        MergeColumnClashException ex = assertThrows(MergeColumnClashException.class,
                () -> MergedTables.build(request(ae, dm, dm2).keyed(dmJoin().build())
                        .keyed(dmJoin().qualifier("DM2").build()).naming(MergeNaming.BROWSER)
                        .build()));
        assertTrue(ex.getMessage().contains("AGE"), ex.getMessage());
    }


    @Test
    void jkmR7_absentOnOneSideReadsTheOtherSidesTypeDefault_absentOnBothLeavesTheKey()
    {
        // DM lacks STUDYID: AE's STUDYID must equal "" (the character default) to match — so a
        // populated AE STUDYID never pairs, a blank one does.
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE", ",AE,01-002,1,RASH");
        IDataTable dm = table("DM", "USUBJID,AGE:N", "01-001,54", "01-002,61");
        MergedTable t = MergedTables
                .build(request(ae, dm).keyed(dmJoin().joinType(JoinType.LEFT).build()).build());
        assertEquals(List.of(UNSUPPLIED, "61"), column(t, "DM.AGE"));
        // absent on BOTH sides: the component leaves the key, USUBJID alone pairs
        IDataTable ae2 = table("AE", "USUBJID,AESEQ:N", "01-001,1");
        MergedTable t2 = MergedTables.build(request(ae2, dm).keyed(dmJoin().build()).build());
        assertEquals(List.of("54"), column(t2, "DM.AGE"));
        // a numeric column absent on one side contributes MIS, which pairs a MIS cell only
        IDataTable ae3 = table("AE", "USUBJID,AGE:N", "01-001,", "01-002,54");
        IDataTable dm3 = table("DM", "USUBJID,SEX", "01-001,M", "01-002,F");
        MergedTable t3 = MergedTables.build(request(ae3, dm3)
                .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                        .key(KeyColumn.of("AGE")).joinType(JoinType.LEFT).build())
                .build());
        assertEquals(List.of("M", UNSUPPLIED), column(t3, "DM.SEX"));
    }


    @Test
    void degenerateKey_isRefused()
    {
        IDataTable ae = table("AE", "AETERM", "HEADACHE");
        IDataTable dm = table("DM", "SEX", "M");
        DegenerateJoinKeyException ex = assertThrows(DegenerateJoinKeyException.class,
                () -> MergedTables.build(request(ae, dm).keyed(dmJoin().build()).build()));
        assertTrue(ex.getMessage().contains("STUDYID, USUBJID"), ex.getMessage());
        assertThrows(MalformedSidedKeyException.class, () -> MergedTables.build(
                request(ae, dm).keyed(KeyedMergeSpec.builder().qualifier("DM").build()).build()));
    }


    @Test
    void d4_typeMismatch_isRefusedUnlessJoinAsStringOrUnchecked()
    {
        IDataTable ae = table("AE", "USUBJID,AESEQ:N,AETERM", "01-001,1,HEADACHE");
        IDataTable fa = table("FA", "USUBJID,FASEQ,FAOBJ", "01-001,1,OBJ");
        KeyedMergeSpec.KeyedMergeSpecBuilder join = KeyedMergeSpec.builder().qualifier("FA")
                .key(KeyColumn.of("USUBJID")).key(KeyColumn.sided("AESEQ", "FASEQ"));
        JoinKeyTypeMismatchException ex = assertThrows(JoinKeyTypeMismatchException.class,
                () -> MergedTables.build(request(ae, fa).keyed(join.build()).build()));
        assertTrue(ex.getMessage().contains("AESEQ is Numeric"), ex.getMessage());
        assertTrue(ex.getMessage().contains("FASEQ is Character"), ex.getMessage());
        MergedTable asText = MergedTables
                .build(request(ae, fa).keyed(join.joinAsString(true).build()).build());
        assertEquals(List.of("OBJ"), column(asText, "FA.FAOBJ"));
        // unchecked (a text-carried foreign key, D4-R5): the typed parts simply do not match
        MergedTable unchecked = MergedTables.build(request(ae, fa)
                .keyed(join.joinAsString(false).typeChecked(false).joinType(JoinType.LEFT).build())
                .build());
        assertEquals(List.of(UNSUPPLIED), column(unchecked, "FA.FAOBJ"));
    }


    @Test
    void keepMissingsFalse_dropsBlankKeyedRowsOnBothSides()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", ",HEADACHE", "01-001,RASH");
        IDataTable dm = table("DM", "USUBJID,SEX", ",X", "01-001,M");
        MergedTable keep = MergedTables
                .build(request(ae, dm).keyed(KeyedMergeSpec.builder().qualifier("DM")
                        .key(KeyColumn.of("USUBJID")).joinType(JoinType.LEFT).build()).build());
        assertEquals(List.of("X", "M"), column(keep, "DM.SEX"), "JKM R4: blanks pair by default");
        MergedTable drop = MergedTables.build(request(ae, dm)
                .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                        .joinType(JoinType.LEFT).keepMissings(false).build())
                .build());
        assertEquals(List.of(UNSUPPLIED, "M"), column(drop, "DM.SEX"));
        // a blank primary key also stays out of the cardinality check of the primary side
        MergedTable one = MergedTables.build(request(ae, dm).keyed(KeyedMergeSpec.builder()
                .qualifier("DM").key(KeyColumn.of("USUBJID")).joinType(JoinType.LEFT)
                .keepMissings(false).cardinality(Cardinality.ONE_TO_ONE).build()).build());
        assertEquals(2, one.getRowCount());
    }


    @Test
    void partnerMask_filtersRowsAtProbeTime_andInTheCardinalityCheck()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE");
        IDataTable dm = table("DM", "USUBJID,SEX", "01-001,M", "01-001,F");
        BitSet keep = new BitSet();
        keep.set(1);
        MergedTable t = MergedTables
                .build(request(ae, dm)
                        .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                                .partnerMask(keep).cardinality(Cardinality.MANY_TO_ONE).build())
                        .build());
        assertEquals(List.of("F"), column(t, "DM.SEX"));
        // without the mask the duplicate is a violation
        assertThrows(CardinalityViolationException.class,
                () -> MergedTables.build(request(ae, dm)
                        .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                                .cardinality(Cardinality.MANY_TO_ONE).build())
                        .build()));
        BitSet none = new BitSet();
        MergedTable empty = MergedTables.build(request(ae, dm)
                .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                        .partnerMask(none).joinType(JoinType.LEFT).build())
                .build());
        assertEquals(List.of(UNSUPPLIED), column(empty, "DM.SEX"));
    }


    @Test
    void sidedKeys_readDifferentNamesOnTheTwoSides_andAQualifiedLeftIsRefused()
    {
        IDataTable ae = table("AE", "USUBJID,AELNKID", "01-001,L1");
        IDataTable fa = table("FA", "USUBJID,FALNKID,FAOBJ", "01-001,L1,OBJ");
        MergedTable t = MergedTables.build(request(ae, fa)
                .keyed(KeyedMergeSpec.builder().qualifier("FA").key(KeyColumn.of("USUBJID"))
                        .key(KeyColumn.sided("AELNKID", "FALNKID")).build())
                .build());
        assertEquals(List.of("OBJ"), column(t, "FA.FAOBJ"));
        UnresolvedQualifiedKeyException ex = assertThrows(UnresolvedQualifiedKeyException.class,
                () -> MergedTables
                        .build(request(ae, fa)
                                .keyed(KeyedMergeSpec.builder().qualifier("FA")
                                        .key(KeyColumn.sided("DM.USUBJID", "USUBJID")).build())
                                .build()));
        assertTrue(ex.getMessage().contains("DM.USUBJID"), ex.getMessage());
    }


    @Test
    void anUnresolvedPartner_contributesNoPartAndIsListed()
    {
        IDataTable ae = ae("S1,AE,01-001,1,HEADACHE");
        MergedTable t = MergedTables
                .build(request(ae).keyed(dmJoin().dataset("DM").build()).build());
        assertEquals(List.of("DM"), t.unresolvedDatasets());
        assertEquals(1, t.parts().size());
        assertEquals(1, t.getRowCount());
    }


    @Test
    void twoPartnersCompose_theirExpansionsMultiply()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE");
        IDataTable cm = table("CM", "USUBJID,CMTRT", "01-001,ASPIRIN", "01-001,IBUPROFEN");
        IDataTable lb = table("LB", "USUBJID,LBTESTCD", "01-001,ALT", "01-001,AST", "01-001,BILI");
        MergedTable t = MergedTables.build(request(ae, cm, lb)
                .keyed(KeyedMergeSpec.builder().qualifier("CM").key(KeyColumn.of("USUBJID"))
                        .build())
                .keyed(KeyedMergeSpec.builder().qualifier("LB").key(KeyColumn.of("USUBJID"))
                        .build())
                .build());
        assertEquals(6, t.getRowCount(), "MQ11: several expansions compose as maps");
        assertEquals(
                List.of("ASPIRIN", "ASPIRIN", "ASPIRIN", "IBUPROFEN", "IBUPROFEN", "IBUPROFEN"),
                column(t, "CM.CMTRT"));
        assertEquals(List.of("ALT", "AST", "BILI", "ALT", "AST", "BILI"), column(t, "LB.LBTESTCD"));
        for (long r = 0; r < 6; r++)
        {
            assertEquals(0, t.getRealRowIndex(r));
        }
        assertEquals(List.of("HEADACHE", "ASPIRIN", "ALT"),
                row(t, 0, "AETERM", "CM.CMTRT", "LB.LBTESTCD"));
    }


    @Test
    void aQualifierServedTwice_isAClash()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE");
        IDataTable dm = table("DM", "USUBJID,SEX", "01-001,M");
        assertThrows(MergeColumnClashException.class,
                () -> MergedTables.build(request(ae, dm)
                        .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                                .build())
                        .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                                .build())
                        .build()));
    }


    @Test
    void largePartner_isIndexedOnceAndProbedPerRow()
    {
        int n = 20_000;
        String[] dmRows = new String[n];
        for (int i = 0; i < n; i++)
        {
            dmRows[i] = "S1,U" + i + "," + (20 + i % 60) + "," + (i % 2 == 0 ? "M" : "F");
        }
        String[] aeRows = new String[n];
        for (int i = 0; i < n; i++)
        {
            aeRows[i] = "S1,AE,U" + (n - 1 - i) + ",1,T";
        }
        MergedTable t = MergedTables.build(request(ae(aeRows), dm(dmRows))
                .keyed(dmJoin().cardinality(Cardinality.MANY_TO_ONE).build()).build());
        assertEquals(n, t.getRowCount());
        assertEquals(String.valueOf(20 + (n - 1) % 60), text(t, 0, "DM.AGE"));
        assertEquals("20", text(t, n - 1, "DM.AGE"));
    }
}
