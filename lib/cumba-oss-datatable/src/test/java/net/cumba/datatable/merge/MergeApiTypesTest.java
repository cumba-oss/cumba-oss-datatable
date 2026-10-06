package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.manager.RelrecMergeSpec;
import org.junit.jupiter.api.Test;

/** The small API types of the merge package: enums, the key, the naming rules, the specs. */
class MergeApiTypesTest
{

    @Test
    void cardinality_spellingsAndUniqueness()
    {
        assertEquals(Cardinality.ONE_TO_ONE, Cardinality.parse("1:1"));
        assertEquals(Cardinality.ONE_TO_MANY, Cardinality.parse(" 1:N "));
        assertEquals(Cardinality.MANY_TO_ONE, Cardinality.parse("n:1"));
        assertEquals(Cardinality.MANY_TO_MANY, Cardinality.parse("N:M"));
        assertThrows(IllegalArgumentException.class, () -> Cardinality.parse("many"));
        assertTrue(Cardinality.ONE_TO_ONE.leftUnique());
        assertTrue(Cardinality.ONE_TO_ONE.rightUnique());
        assertTrue(Cardinality.ONE_TO_MANY.leftUnique());
        assertFalse(Cardinality.ONE_TO_MANY.rightUnique());
        assertFalse(Cardinality.MANY_TO_ONE.leftUnique());
        assertTrue(Cardinality.MANY_TO_ONE.rightUnique());
        assertFalse(Cardinality.MANY_TO_MANY.leftUnique());
        assertFalse(Cardinality.MANY_TO_MANY.rightUnique());
        assertEquals("n:1", Cardinality.MANY_TO_ONE.text());
    }


    @Test
    void joinType_spellings()
    {
        assertEquals(JoinType.INNER, JoinType.parse("inner"));
        assertEquals(JoinType.LEFT, JoinType.parse(" Left"));
        assertEquals("left", JoinType.LEFT.text());
        assertThrows(IllegalArgumentException.class, () -> JoinType.parse("outer"));
    }


    @Test
    void keyColumn_bareSidedAndQualified()
    {
        KeyColumn bare = KeyColumn.of("USUBJID");
        assertEquals("USUBJID", bare.left());
        assertEquals("USUBJID", bare.right());
        assertNull(bare.leftQualifier());
        KeyColumn sided = KeyColumn.sided("AELNKID", "FALNKID");
        assertEquals("FALNKID", sided.right());
        assertEquals("DM", KeyColumn.sided("DM.RPATHCD", "RPATHCD").leftQualifier());
        assertNull(KeyColumn.of(".X").leftQualifier());
        assertEquals(bare, KeyColumn.sided("USUBJID", "USUBJID"));
    }


    @Test
    void mergeMeta_keys()
    {
        assertEquals("MERGED_FROM", MergeMeta.MERGED_FROM);
        assertEquals("MERGED_SOURCE_COLUMN", MergeMeta.MERGED_SOURCE_COLUMN);
        assertEquals("MERGE_KIND", MergeMeta.MERGE_KIND);
        assertEquals("MERGE_ERROR", MergeMeta.MERGE_ERROR);
        assertEquals("_matched_", MergeMeta.MATCHED_FLAG);
        assertEquals(5, MergeKind.values().length);
    }


    @Test
    void qualifiedNaming_qualifiesEverythingButAPrimaryQnam_andRefusesAClash()
    {
        MergeNaming n = MergeNaming.QUALIFIED;
        Predicate<String> none = _ -> false;
        assertEquals("DM.AGE", n.columnName(MergeKind.KEYED, "DM", "AGE", none));
        assertEquals("DM.STUDYID", n.columnName(MergeKind.KEYED, "DM", "STUDYID", _ -> true));
        assertEquals("LB.EPOCH", n.columnName(MergeKind.RELREC, "LB", "EPOCH", none));
        assertEquals("AE.AESEQ", n.columnName(MergeKind.CHILD, "AE", "AESEQ", none));
        assertEquals("AETRTEM", n.suppColumnName("AE", true, "AETRTEM"));
        assertEquals("DM.RACEOTH", n.suppColumnName("DM", false, "RACEOTH"));
        assertEquals("DM._matched_", n.matchedFlagName("DM"));
        MergeColumnClashException ex = assertThrows(MergeColumnClashException.class,
                () -> n.onClash(MergeKind.KEYED, "DM", "DM.AGE", none));
        assertTrue(ex.getMessage().contains("DM.AGE"));
        assertThrows(MergeColumnClashException.class,
                () -> n.onClash(MergeKind.RELREC, "LB", "LB.EPOCH", none));
        assertEquals("dm.age", MergeNaming.nameKey("DM.AGE"));
    }


    @Test
    void browserNaming_bare_dropsWhatTheTableHas_renamesRelrecClashes()
    {
        MergeNaming n = MergeNaming.BROWSER;
        Predicate<String> has = s -> s.equals("STUDYID") || s.equals("USUBJID");
        assertEquals("AGE", n.columnName(MergeKind.KEYED, "DM", "AGE", has));
        assertNull(n.columnName(MergeKind.KEYED, "DM", "STUDYID", has));
        assertNull(n.columnName(MergeKind.CHILD, "AE", "USUBJID", has));
        assertEquals("EPOCH", n.columnName(MergeKind.RELREC, "LB", "EPOCH", has));
        assertNull(n.columnName(MergeKind.RELREC, "LB", "studyid", _ -> false));
        assertNull(n.columnName(MergeKind.RELREC, "LB", "USUBJID", _ -> false));
        assertNull(n.columnName(MergeKind.RELREC, "LB", "DOMAIN", _ -> false));
        assertEquals("LBSEQ", n.columnName(MergeKind.RELREC, "LB", "LBSEQ", _ -> true),
                "a related column is never dropped for being taken; the clash is renamed");
        assertEquals("AETRTEM", n.suppColumnName("AE", true, "AETRTEM"));
        assertEquals("RACEOTH", n.suppColumnName("DM", false, "RACEOTH"));
        assertThrows(MergeColumnClashException.class,
                () -> n.onClash(MergeKind.KEYED, "DM", "AGE", _ -> true));
        assertThrows(MergeColumnClashException.class,
                () -> n.onClash(MergeKind.SUPP, "AE", "AETRTEM", _ -> true));
        assertEquals("LB_EPOCH",
                n.onClash(MergeKind.RELREC, "LB", "EPOCH", s -> s.equals("EPOCH")));
        Predicate<String> taken = s -> List.of("EPOCH", "LB_EPOCH", "LB_EPOCH_2").contains(s);
        assertEquals("LB_EPOCH_3", n.onClash(MergeKind.RELREC, "LB", "EPOCH", taken));
        assertEquals("LB._matched_", n.matchedFlagName("LB"));
    }


    @Test
    void specs_carryTheirDefaults_andValueSemantics()
    {
        KeyedMergeSpec k = KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID"))
                .build();
        assertEquals("DM", k.datasetName());
        assertEquals(JoinType.INNER, k.getJoinType());
        assertEquals(Cardinality.MANY_TO_MANY, k.getCardinality());
        assertTrue(k.isKeepMissings());
        assertTrue(k.isTypeChecked());
        assertFalse(k.isJoinAsString());
        assertNull(k.getPartnerMask());
        assertNull(k.getSupp());
        KeyedMergeSpec k2 = KeyedMergeSpec.builder().qualifier("DM").dataset("DM_ALL")
                .keys(List.of(KeyColumn.of("USUBJID"))).joinType(JoinType.LEFT).joinAsString(true)
                .keepMissings(false).typeChecked(false).cardinality(Cardinality.MANY_TO_ONE)
                .partnerMask(new BitSet()).supp(SuppMergeSpec.builder().build()).build();
        assertEquals("DM_ALL", k2.datasetName());
        assertNotEquals(k, k2);
        assertEquals(k,
                KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("USUBJID")).build());
        assertEquals(k.hashCode(), KeyedMergeSpec.builder().qualifier("DM")
                .key(KeyColumn.of("USUBJID")).build().hashCode());
        assertTrue(k.toString().contains("DM"));
        assertThrows(NullPointerException.class, () -> KeyedMergeSpec.builder().build());

        ChildMergeSpec c = ChildMergeSpec.builder().build();
        assertNull(c.getParentDataset());
        assertEquals(List.of("STUDYID", "USUBJID", "POOLID"), c.getSubjectKeys());
        assertEquals("IDVAR", c.getIdvarColumn());
        assertEquals("IDVARVAL", c.getIdvarvalColumn());
        assertEquals("RDOMAIN", c.getRdomainColumn());
        assertEquals(JoinType.INNER, c.getJoinType());
        assertNull(c.getCardinality(), "unauthored: the IG's n:m, nothing checked (P-13)");
        ChildMergeSpec c2 = ChildMergeSpec.builder().parentDataset("AE").subjectKeys(List.of("X"))
                .idvarColumn("I").idvarvalColumn("V").rdomainColumn("R").joinType(JoinType.LEFT)
                .cardinality(Cardinality.ONE_TO_ONE).build();
        assertNotEquals(c, c2);
        assertEquals(c, ChildMergeSpec.builder().build());
        assertEquals(c.hashCode(), ChildMergeSpec.builder().build().hashCode());
        assertTrue(c2.toString().contains("AE"));

        SuppMergeSpec s = SuppMergeSpec.builder().build();
        assertTrue(s.getSuppDatasets().isEmpty());
        assertNull(s.getKeys());
        assertEquals(SuppMergeSpec.DEFAULT_KEYS, s.effectiveKeys());
        assertEquals(
                List.of(KeyColumn.of("STUDYID"), KeyColumn.sided("DOMAIN", "RDOMAIN"),
                        KeyColumn.of("USUBJID"), KeyColumn.of("POOLID"), KeyColumn.of("APID"),
                        KeyColumn.of("SPDEVID"), KeyColumn.of("IDVAR"), KeyColumn.of("IDVARVAL")),
                SuppMergeSpec.DEFAULT_KEYS);
        assertEquals(List.of("SUPPQS36", "SUPPQS"), SuppMergeSpec.defaultSuppNames("qs36", "QS"));
        assertEquals(List.of("SUPPAE"), SuppMergeSpec.defaultSuppNames("AE", "ae"));
        assertEquals(List.of("SUPPAE"), SuppMergeSpec.defaultSuppNames("AE", " "));
        assertEquals(List.of("SQAPDM"), SuppMergeSpec.defaultSuppNames("APDM", "APDM"),
                "SDTMIG-AP: an associated-persons dataset's supplemental dataset is SQAP--");
        assertEquals(List.of("SQAPMH1", "SQAPMH"), SuppMergeSpec.defaultSuppNames("APMH1", "APMH"));
        assertEquals("SUPPAP", SuppMergeSpec.suppNameOf("AP"), "AP alone is no AP-- dataset");
        assertEquals(List.of("SUPPQS36", "SUPPQS"), SuppMergeSpec.builder().suppDataset("SUPPQS36")
                .suppDataset("SUPPQS").build().getSuppDatasets());
        SuppMergeSpec s2 = SuppMergeSpec.builder().suppDataset("SUPPDM")
                .keys(List.of(KeyColumn.of("USUBJID"))).build();
        assertEquals(List.of(KeyColumn.of("USUBJID")), s2.effectiveKeys());
        assertNotEquals(s, s2);
        assertEquals(s, SuppMergeSpec.builder().build());
        assertEquals(s.hashCode(), SuppMergeSpec.builder().build().hashCode());
        assertTrue(s2.toString().contains("SUPPDM"));

        RelrecPartSpec r = RelrecPartSpec.builder().build();
        assertEquals("RELREC", r.getRelrecDataset());
        assertNull(r.getSelection());
        assertEquals(JoinType.INNER, r.getJoinType());
        assertNull(r.getCardinality(), "unauthored: RELTYPE's default per relationship (OD-5)");
        assertTrue(r.isIncludeUnmatchedDomains());
        RelrecPartSpec r2 = RelrecPartSpec.builder().relrecDataset("RR")
                .selection(RelrecMergeSpec.builder().build()).joinType(JoinType.LEFT)
                .cardinality(Cardinality.MANY_TO_ONE).includeUnmatchedDomains(false).build();
        assertNotEquals(r, r2);
        assertEquals(r, RelrecPartSpec.builder().build());
        assertEquals(r.hashCode(), RelrecPartSpec.builder().build().hashCode());
        assertTrue(r2.toString().contains("RR"));

        IDataTable primary = new ListTable("AE", "USUBJID", "01-001");
        DatasetResolver resolver = _ -> null;
        MergeRequest q = MergeRequest.builder().primary(primary).resolver(resolver).build();
        assertSame(primary, q.getPrimary());
        assertSame(resolver, q.getResolver());
        assertNull(q.getPrimaryDomain());
        assertTrue(q.getKeyed().isEmpty());
        assertNull(q.getChild());
        assertNull(q.getRelrec());
        assertNull(q.getSupp());
        assertSame(MergeNaming.QUALIFIED, q.getNaming());
        assertFalse(q.isMatchedFlags());
        assertSame(MergeStandard.UNKNOWN, q.getStandard());
        MergeRequest q2 = MergeRequest.builder().primary(primary).resolver(resolver)
                .primaryDomain("AE").keyed(k).child(c).relrec(r).supp(s).naming(MergeNaming.BROWSER)
                .matchedFlags(true).standard(MergeStandard.SEND).build();
        assertSame(MergeStandard.SEND, q2.getStandard());
        assertEquals(List.of(k), q2.getKeyed());
        assertNotEquals(q, q2);
        assertEquals(q, MergeRequest.builder().primary(primary).resolver(resolver).build());
        assertEquals(q.hashCode(),
                MergeRequest.builder().primary(primary).resolver(resolver).build().hashCode());
        assertTrue(q2.toString().contains("AE"));
        assertThrows(NullPointerException.class, () -> MergeRequest.builder().build());
    }


    @Test
    void mergeStandard_unknownInfersSendFromPoolid()
    {
        assertTrue(MergeStandard.SEND.isSend(false));
        assertTrue(MergeStandard.SEND.isSend(true));
        assertFalse(MergeStandard.SDTM.isSend(true), "SDTM never reads a POOLID column as SEND");
        assertFalse(MergeStandard.SDTM.isSend(false));
        assertTrue(MergeStandard.UNKNOWN.isSend(true));
        assertFalse(MergeStandard.UNKNOWN.isSend(false));
    }


    @Test
    void unresolvedDataset_record()
    {
        UnresolvedDataset u = new UnresolvedDataset(MergeKind.SUPP, "SUPPAE");
        assertEquals(MergeKind.SUPP, u.kind());
        assertEquals("SUPPAE", u.name());
        assertEquals(u, new UnresolvedDataset(MergeKind.SUPP, "SUPPAE"));
        assertNotEquals(u, new UnresolvedDataset(MergeKind.KEYED, "SUPPAE"));
    }


    @Test
    void issueAndPart_records()
    {
        MergeIssue i = new MergeIssue(MergeKind.SUPP, "SUPPAE", "AETRTEM", "dup",
                new java.util.ArrayList<>(List.of(1L, 2L)));
        assertEquals(List.of(1L, 2L), i.sourceRows());
        assertThrows(UnsupportedOperationException.class, () -> i.sourceRows().clear());
        MergedPart p = new MergedPart(MergeKind.KEYED, "DM", "DM", 5, 3, 7);
        assertTrue(p.contains(5));
        assertTrue(p.contains(7));
        assertFalse(p.contains(8));
        assertFalse(p.contains(4));
    }


    @Test
    void exceptions_carryTheirMessages()
    {
        assertTrue(new DegenerateJoinKeyException("DM", List.of("A", "B")).getMessage()
                .contains("(A, B)"));
        JoinKeyTypeMismatchException same = new JoinKeyTypeMismatchException("DM", "K", "K",
                "Numeric", "Character");
        assertTrue(same.getMessage()
                .contains("key K is Numeric in the primary dataset and Character in DM"));
        JoinKeyTypeMismatchException sided = new JoinKeyTypeMismatchException("DM", "A", "B",
                "Numeric", "Character");
        assertTrue(sided.getMessage().contains("its joined column B is Character"));
        assertTrue(new MalformedSidedKeyException("DM", "x").getMessage()
                .contains("malformed key — x"));
        assertTrue(new UnresolvedQualifiedKeyException("TP", "DM.X", "why").getMessage()
                .contains("DM.X cannot be resolved — why"));
        CardinalityViolationException cv = new CardinalityViolationException(
                Cardinality.MANY_TO_ONE, "DM", "(USUBJID=1)", 3, 8);
        assertEquals("Cardinality n:1: DM has duplicate key (USUBJID=1) at rows 4 and 9",
                cv.getMessage());
        assertEquals("DM", cv.dataset());
        assertEquals(3, cv.firstRow());
        assertEquals(8, cv.secondRow());
        assertTrue(new MergeColumnClashException("AGE", "DM").getMessage().contains("AGE from DM"));
        assertEquals("m", new MergeException("m").getMessage());
        IllegalStateException cause = new IllegalStateException("c");
        assertSame(cause, new MergeException("m", cause).getCause());
    }


    @Test
    void mergedTables_withoutAnImplementationOnTheClassPath_failsLoud()
    {
        IDataTable primary = new ListTable("AE", "USUBJID", "01-001");
        MergeRequest q = MergeRequest.builder().primary(primary).resolver(_ -> null).build();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> MergedTables.build(q));
        assertTrue(ex.getMessage().contains("cumba-datatable-impl"), ex.getMessage());
    }
}
