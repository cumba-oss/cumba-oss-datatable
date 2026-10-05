package net.cumba.datatable.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.RelrecLinks.Link;
import net.cumba.datatable.merge.RelrecLinks.Links;
import net.cumba.datatable.merge.RelrecLinks.RelrecScan;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/** The shared reading of RELREC: grouping, link classification, the scan, SEND (OD-3). */
class RelrecLinksTest
{

    private static final String HEADER = "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,RELID";

    private static IDataTable relrec(String... aRows)
    {
        return new ListTable("RELREC", HEADER, aRows);
    }


    @Test
    void sdtm_groupsByStudySubjectRelid_andLinksThePrimaryToEveryOtherDomainOfTheGroup()
    {
        Links links = RelrecLinks.collect(relrec("S1,AE,01-001,AESEQ,1,R1",
                "S1,CM,01-001,CMSEQ,2,R1", "S1,LB,01-001,LBSEQ,3,R1", "S1,AE,01-002,AESEQ,1,R1",
                "S1,CM,01-002,CMSEQ,9,R1", "S1,AE,01-001,AESEQ,1,R2", "S1,AE,01-001,AESEQ,2,R2"),
                "AE", null);
        assertFalse(links.send());
        assertEquals(3, links.relationshipCount());
        assertEquals(List.of("CM", "LB"), links.selectableDomains());
        assertTrue(links.mixedDomains().isEmpty());
        assertEquals(2, links.domains().get(0).links().size(), "CM: one link per subject group");
        Link cm = links.domains().get(0).links().get(0);
        assertEquals("CM", cm.rdomain());
        assertEquals("R1", cm.relid());
        assertEquals(0, cm.relationship());
        assertTrue(cm.recordLevel());
        assertEquals("S1", cm.study());
        assertEquals("S1", cm.studyKey());
        assertEquals("01-001", cm.srcSubject());
        assertEquals("01-001", cm.tgtSubjectKey());
        assertFalse(cm.srcPooled());
        assertFalse(cm.tgtPooled());
        assertEquals("AESEQ", cm.srcIdvar());
        assertEquals("1", cm.srcIdvarval());
        assertEquals("CMSEQ", cm.tgtIdvar());
        assertEquals("2", cm.tgtIdvarval());
        assertEquals(0, cm.leftRow());
        assertEquals(1, cm.rightRow());
        assertEquals(1, links.domains().get(0).links().get(1).relationship(),
                "the second subject's R1 is another relationship");
        // the R2 group holds AE rows only: no link to another domain
        assertEquals(List.of("CM", "LB"),
                links.domains().stream().map(RelrecLinks.DomainLinks::rdomain).toList());
    }


    @Test
    void datasetLevel_isABlankIdvarvalOnEitherSide_mixedDomainsAreReported()
    {
        Links links = RelrecLinks.collect(relrec("S1,AE,,AELNKID,,R1", "S1,FA,,FALNKGRP,,R1",
                "S1,AE,01-001,AESEQ,1,R2", "S1,FA,01-001,FASEQ,1,R2", "S1,AE,01-001,AESEQ,1,R3",
                "S1,CM,01-001,CMSEQ,,R3"), "AE", null);
        assertEquals(List.of("FA"), links.mixedDomains());
        assertEquals(List.of("CM"), links.selectableDomains());
        Link cm = links.domains().get(1).links().get(0);
        assertFalse(cm.recordLevel(), "one blank IDVARVAL makes the link dataset-level");
        assertEquals("", cm.tgtIdvarval());
        RelrecScan scan = RelrecLinks.scan(relrec("S1,AE,,AELNKID,,R1", "S1,FA,,FALNKGRP,,R1",
                "S1,AE,01-001,AESEQ,1,R2", "S1,FA,01-001,FASEQ,1,R2"), "ae");
        assertEquals(List.of(), scan.selectable());
        assertEquals(List.of("FA"), scan.mixedSkipped());
    }


    @Test
    void selection_keepsOnlyTheNamedDomains()
    {
        Links links = RelrecLinks.collect(relrec("S1,AE,01-001,AESEQ,1,R1",
                "S1,CM,01-001,CMSEQ,2,R1", "S1,LB,01-001,LBSEQ,3,R1"), "AE", Set.of("LB"));
        assertEquals(List.of("LB"), links.selectableDomains());
    }


    @Test
    void send_groupsByStudyAndRelid_eachRowNamesASubjectOrAPool()
    {
        IDataTable rr = new ListTable("RELREC",
                "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,RELID", "S1,CL,A1,,CLSEQ,1,R1",
                "S1,LB,,P1,LBSEQ,1,R1", "S1,LB,,,LBSEQ,2,R1");
        Links links = RelrecLinks.collect(rr, "CL", null);
        assertTrue(links.send());
        assertEquals(1, links.relationshipCount());
        List<Link> lb = links.domains().get(0).links();
        assertEquals(2, lb.size());
        assertEquals("A1", lb.get(0).srcSubject());
        assertFalse(lb.get(0).srcPooled());
        assertEquals("P1", lb.get(0).tgtSubject());
        assertTrue(lb.get(0).tgtPooled());
        assertEquals("", lb.get(1).tgtSubject(), "neither USUBJID nor POOLID: no subject named");
        assertFalse(lb.get(1).tgtPooled());
    }


    @Test
    void keyIdentity_aMissingCellIsItsMarker_anAbsentColumnTheEmptyString()
    {
        IDataTable rr = relrec("<MIS>,AE,01-001,AESEQ,1,R1", "<MIS>,CM,01-001,CMSEQ,1,R1",
                ",AE,01-001,AESEQ,1,R2", ",CM,01-001,CMSEQ,1,R2", "S1,AE,<MIS_A>,AESEQ,1,R3",
                "S1,CM,<MIS_A>,CMSEQ,1,R3");
        Links links = RelrecLinks.collect(rr, "AE", null);
        List<Link> cm = links.domains().get(0).links();
        assertEquals(3, cm.size());
        assertEquals(MissingValue.MIS, cm.get(0).studyKey());
        assertNull(cm.get(0).study());
        assertEquals("", cm.get(1).studyKey());
        assertEquals("", cm.get(1).study());
        assertEquals(MissingValue.MIS_A, cm.get(2).srcSubjectKey());
        assertNull(cm.get(2).srcSubject());
        IDataTable noStudy = new ListTable("RELREC", "RDOMAIN,USUBJID,IDVAR,IDVARVAL,RELID",
                "AE,01-001,AESEQ,1,R1", "CM,01-001,CMSEQ,1,R1");
        assertEquals("",
                RelrecLinks.collect(noStudy, "AE", null).domains().get(0).links().get(0).studyKey(),
                "JKM R7: an absent column is the character default");
        assertEquals("", RelrecLinks.keyCell(noStudy, -1, 0));
        assertNull(RelrecLinks.cell(noStudy, -1, 0));
        assertEquals("AE", RelrecLinks.cell(noStudy, 0, 0));
        assertTrue(RelrecLinks.isNonBlank("x"));
        assertFalse(RelrecLinks.isNonBlank(""));
        assertFalse(RelrecLinks.isNonBlank(null));
    }


    @Test
    void rowsThatCannotLink_areSkipped()
    {
        // missing RELID; missing IDVAR on one side; a group with no primary row; the primary
        // domain on both sides only
        Links links = RelrecLinks.collect(relrec("S1,AE,01-001,AESEQ,1,<MIS>",
                "S1,CM,01-001,CMSEQ,1,<MIS>", "S1,AE,01-001,<MIS>,1,R1", "S1,CM,01-001,CMSEQ,1,R1",
                "S1,CM,01-001,CMSEQ,1,R2", "S1,LB,01-001,LBSEQ,1,R2", "S1,AE,01-001,AESEQ,1,R3",
                "S1,AE,01-001,AESEQ,2,R3", "S1,<MIS>,01-001,XSEQ,1,R3"), "AE", null);
        assertTrue(links.domains().isEmpty(), links.toString());
        assertEquals(3, links.relationshipCount());
    }


    @Test
    void withoutTheColumnsItReads_thereAreNoLinks()
    {
        for (String header : List.of("STUDYID,USUBJID,IDVAR,IDVARVAL,RELID",
                "STUDYID,RDOMAIN,USUBJID,IDVARVAL,RELID", "STUDYID,RDOMAIN,USUBJID,IDVAR,RELID",
                "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL"))
        {
            IDataTable rr = new ListTable("RELREC", header, "a,b,c,d,e");
            assertEquals(Links.empty(), RelrecLinks.collect(rr, "AE", null), header);
        }
        assertEquals(RelrecScan.empty(), RelrecLinks.scan(
                new ListTable("RELREC", "STUDYID,USUBJID,IDVAR,IDVARVAL,RELID", "a,b,c,d,e"),
                "AE"));
        assertEquals(RelrecScan.empty(), RelrecLinks.scan(relrec(), null));
        assertEquals(List.of("CM"),
                RelrecLinks.scan(relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,1,R1"), "AE")
                        .selectable());
    }


    @Test
    void theRecordsAreImmutableCopies()
    {
        Links links = RelrecLinks
                .collect(relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,2,R1"), "AE", null);
        assertThrows(UnsupportedOperationException.class, () -> links.domains().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> links.domains().get(0).links().clear());
        assertThrows(UnsupportedOperationException.class, () -> links.mixedDomains().clear());
        RelrecScan scan = RelrecLinks
                .scan(relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-001,CMSEQ,2,R1"), "AE");
        assertThrows(UnsupportedOperationException.class, () -> scan.selectable().clear());
        assertThrows(UnsupportedOperationException.class, () -> scan.mixedSkipped().clear());
    }


    /**
     * M1 / L3 (W1 review): an explicit standard decides the grouping, not the presence of a
     * {@code POOLID} column. SDTM with a (permissible, empty) POOLID groups by subject; SEND
     * without one still groups by (STUDYID, RELID).
     */
    @Test
    void anExplicitStandard_overridesThePoolidInference()
    {
        IDataTable withPool = new ListTable("RELREC",
                "STUDYID,RDOMAIN,USUBJID,POOLID,IDVAR,IDVARVAL,RELID", "S1,AE,01-001,,AESEQ,1,R1",
                "S1,CM,01-002,,CMSEQ,1,R1");
        assertTrue(RelrecLinks.collect(withPool, "AE", null).send(), "UNKNOWN: inferred");
        Links sdtm = RelrecLinks.collect(withPool, "AE", null, MergeStandard.SDTM);
        assertFalse(sdtm.send());
        assertTrue(sdtm.domains().isEmpty(), "SDTM: two subjects, two relationships, no link");
        IDataTable noPool = relrec("S1,AE,01-001,AESEQ,1,R1", "S1,CM,01-002,CMSEQ,1,R1");
        assertFalse(RelrecLinks.collect(noPool, "AE", null).send());
        Links send = RelrecLinks.collect(noPool, "AE", null, MergeStandard.SEND);
        assertTrue(send.send());
        assertEquals(List.of("CM"), send.selectableDomains(), "SEND: one relationship by RELID");
        assertEquals(List.of("CM"),
                RelrecLinks.scan(noPool, "AE", MergeStandard.SEND).selectable());
        assertTrue(RelrecLinks.scan(noPool, "AE", MergeStandard.SDTM).selectable().isEmpty());
    }


    /** OD-5: a dataset-level link states the IG's cardinality of each side through RELTYPE. */
    @Test
    void reltype_statesTheIgCardinalityOfADatasetLevelLink()
    {
        IDataTable rr = new ListTable("RELREC",
                "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,RELTYPE,RELID", "S1,TU,,TULNKID,,one ,R1",
                "S1,TR,,TRLNKID,,MANY,R1", "S1,TU,,TULNKID,,ONE,R2", "S1,RS,,RSLNKID,,ONE,R2",
                "S1,TU,,TULNKID,,,R3", "S1,PR,,PRLNKID,,ONE,R3", "S1,TU,01-001,TUSEQ,1,,R4",
                "S1,TR,01-001,TRSEQ,1,,R4");
        Links links = RelrecLinks.collect(rr, "TU", null);
        Link tr = links.domains().get(0).links().get(0);
        assertEquals("ONE", tr.srcReltype(), "stripped, upper case");
        assertEquals("MANY", tr.tgtReltype());
        assertEquals(Cardinality.ONE_TO_MANY, tr.igCardinality());
        assertEquals(Cardinality.ONE_TO_ONE, links.domains().get(1).links().get(0).igCardinality());
        Link pr = links.domains().get(2).links().get(0);
        assertNull(pr.srcReltype());
        assertEquals(Cardinality.MANY_TO_ONE, pr.igCardinality());
        Link recordLevel = links.domains().get(0).links().get(1);
        assertTrue(recordLevel.recordLevel());
        assertEquals(Cardinality.MANY_TO_MANY, recordLevel.igCardinality());
        Link plain = RelrecLinks
                .collect(relrec("S1,AE,01-001,AESEQ,,R1", "S1,CM,01-001,CMSEQ,,R1"), "AE", null)
                .domains().get(0).links().get(0);
        assertNull(plain.tgtReltype(), "no RELTYPE column");
        assertFalse(plain.recordLevel());
        assertEquals(Cardinality.MANY_TO_MANY, plain.igCardinality());
    }
}
