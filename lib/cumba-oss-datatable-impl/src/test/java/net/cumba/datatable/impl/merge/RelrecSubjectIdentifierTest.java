package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.merge.MergeStandard;
import net.cumba.datatable.merge.RelrecLinks;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * RELREC relationships whose subject is not {@code USUBJID} ({@code PLAN-relrec-ap-device-subject},
 * ruling Q3-bis (a)): an associated person ({@code APID}) or a device ({@code SPDEVID}). Each
 * fixture is an in-code table whose shape an authority documents, and the expected pairs are read
 * from that authority's text (oracle first):
 *
 * <ul>
 * <li>F1 — SDTMIG-MD 1.1 section 4.1 Example 1 (pp. 42-45): a dataset-level NV / DU link,
 * {@code USUBJID} blank on RELREC;</li>
 * <li>F2 — SDTM v2.0 section 6.1 (p. 65), the {@code RELID} note: two devices, {@code USUBJID}
 * blank, each reusing {@code RELID 1};</li>
 * <li>F3 — SDTMIG-MD 1.1 DE assumption 7 (an AE related to a device event) and the {@code DESEQ}
 * definition (unique within every subject/device combination);</li>
 * <li>F4 — SDTMIG-AP 1.0 section 3.4 Example 1 (p. 25): {@code APID} the subject, {@code USUBJID}
 * blank, an extra {@code RSUBJID}, primaries without a {@code USUBJID} column;</li>
 * <li>F5 — a RELREC carrying {@code POOLID} read under {@link MergeStandard#UNKNOWN} is read as
 * SEND: the owner's ruling {@code PLAN-merge-followups} FU-2 (a), pinned;</li>
 * <li>F6 — a related domain or primary lacking a subject identifier column the RELREC row populates
 * is a {@code DomainIssue} (owner ruling G7-R1 (a), OD-7), not a silent miss.</li>
 * </ul>
 *
 * Every blank identifier is built as {@code ""} in a text column.
 */
class RelrecSubjectIdentifierTest
{

    private static IDataTable table(String aName, String[] aCols, Object[]... aRows)
    {
        int colCount = aCols.length;
        CachedDataTableColumn[] columns = new CachedDataTableColumn[colCount];
        DataTableColumnMeta[] metas = new DataTableColumnMeta[colCount];
        for (int c = 0; c < colCount; c++)
        {
            columns[c] = new CachedDataTableColumn(c, DataValueType.STRING);
            metas[c] = DataTableColumnMeta.builder().index(c).name(aCols[c])
                    .type(DataValueType.STRING).build();
        }
        for (Object[] row : aRows)
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
                .rowCount(aRows.length).totalRowCount(aRows.length).build();
        return new ColumnCachedDataTable(meta, columns);
    }


    private static String[] cols(String... aNames)
    {
        return aNames;
    }


    private static Object[] row(Object... aValues)
    {
        return aValues;
    }


    private static RelrecPairer.Pairing pair(IDataTable aRelrec, IDataTable aPrimary,
            String aPrimaryDomain, Map<String, IDataTable> aRelated)
    {
        RelrecLinks.Links links = RelrecLinks.collect(aRelrec, aPrimaryDomain, null,
                MergeStandard.SDTM);
        return RelrecPairer.resolve(links, aPrimary, aRelated::get);
    }


    /** The emitted pairs as {@code "primaryRow-relatedRow"}, in the pairing's order. */
    private static List<String> pairs(RelrecPairer.Pairing aPairing)
    {
        List<String> out = new ArrayList<>();
        for (RelrecPairer.Pair p : aPairing.pairs())
        {
            out.add(p.primaryRow() + "-" + p.relatedRow());
        }
        return out;
    }

    // ---- F1: SDTMIG-MD 1.1 section 4.1 Example 1 ----

    private static final String MD_SUBJ = "2324-P0001";

    private static IDataTable mdNv()
    {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 14; i++)
        {
            rows.add(row("STUDYX", "NV", MD_SUBJ, String.valueOf(i), i <= 7 ? "223-45" : "445-77",
                    i <= 7 ? "DUNV1" : "DUNV2"));
        }
        for (int i = 1; i <= 6; i++)
        {
            rows.add(row("STUDYX", "NV", "", String.valueOf(i), "4567-8901", "CALIBRATION"));
        }
        return table("NV", cols("STUDYID", "DOMAIN", "USUBJID", "NVSEQ", "NVREFID", "NVLNKID"),
                rows.toArray(Object[][]::new));
    }


    private static IDataTable mdDu()
    {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 14; i++)
        {
            rows.add(row("STUDYX", "DU", MD_SUBJ, "ABC174", String.valueOf(i),
                    i <= 7 ? "223-45" : "445-77", i <= 7 ? "DUNV1" : "DUNV2"));
        }
        return table("DU",
                cols("STUDYID", "DOMAIN", "USUBJID", "SPDEVID", "DUSEQ", "DUREFID", "DULNKID"),
                rows.toArray(Object[][]::new));
    }


    @Test
    void f1_mdDatasetLevelNvDu_pairsWithinTheSubjectAndLeavesCalibrationOut()
    {
        IDataTable relrec = table("RELREC",
                cols("STUDYID", "USUBJID", "RDOMAIN", "IDVAR", "IDVARVAL", "RELTYPE", "RELID"),
                row("STUDYX", "", "NV", "NVLNKID", "", "MANY", "NVDU1"),
                row("STUDYX", "", "DU", "DULNKID", "", "MANY", "NVDU1"));
        RelrecPairer.Pairing pairing = pair(relrec, mdNv(), "NV", Map.of("DU", mdDu()));

        List<String> expected = new ArrayList<>();
        for (int p = 0; p < 14; p++)
        {
            int from = p < 7 ? 0 : 7;
            for (int r = from; r < from + 7; r++)
            {
                expected.add(p + "-" + r);
            }
        }
        assertEquals(expected, pairs(pairing),
                "DUNV1 x DUNV1 and DUNV2 x DUNV2 (7 x 7 each); CALIBRATION has no subject");
        assertTrue(pairing.issues().isEmpty(), () -> "no issue expected: " + pairing.issues());
    }

    // ---- F2: two devices, USUBJID blank, RELID reused per device ----


    @Test
    void f2_twoDevicesReusingOneRelid_pairEachDeviceWithItsOwnRecords()
    {
        IDataTable de = table("DE",
                cols("STUDYID", "DOMAIN", "USUBJID", "SPDEVID", "DESEQ", "DETERM"),
                row("S1", "DE", "", "D1", "1", "FISSURE"), row("S1", "DE", "", "D2", "1", "CRACK"));
        IDataTable dt = table("DT",
                cols("STUDYID", "DOMAIN", "USUBJID", "SPDEVID", "DTSEQ", "DTTERM"),
                row("S1", "DT", "", "D1", "1", "RETURNED"),
                row("S1", "DT", "", "D2", "1", "DESTROYED"));
        IDataTable relrec = table("RELREC",
                cols("STUDYID", "RDOMAIN", "USUBJID", "SPDEVID", "IDVAR", "IDVARVAL", "RELTYPE",
                        "RELID"),
                row("S1", "DE", "", "D1", "DESEQ", "1", "", "1"),
                row("S1", "DT", "", "D1", "DTSEQ", "1", "", "1"),
                row("S1", "DE", "", "D2", "DESEQ", "1", "", "1"),
                row("S1", "DT", "", "D2", "DTSEQ", "1", "", "1"));
        RelrecPairer.Pairing pairing = pair(relrec, de, "DE", Map.of("DT", dt));

        assertEquals(List.of("0-0", "1-1"), pairs(pairing),
                "RELID is unique within the device: D1 with D1, D2 with D2 (SDTM v2.0 6.1)");
        assertTrue(pairing.issues().isEmpty(), () -> "no issue expected: " + pairing.issues());
    }

    // ---- F3: a subject's AE related to a device event of that subject ----


    @Test
    void f3_aeToDeviceEventOfTheSubject_pairsOnlyTheNamedDevicesRecord()
    {
        IDataTable ae = table("AE", cols("STUDYID", "DOMAIN", "USUBJID", "AESEQ", "AETERM"),
                row("S1", "AE", "U1", "3", "INCISION SITE INFLAMMATION"));
        IDataTable de = table("DE",
                cols("STUDYID", "DOMAIN", "USUBJID", "SPDEVID", "DESEQ", "DETERM"),
                row("S1", "DE", "U1", "D1", "1", "FISSURE"),
                row("S1", "DE", "U1", "D2", "1", "CRACK"),
                row("S1", "DE", "U2", "D1", "1", "FISSURE"));
        IDataTable relrec = table("RELREC",
                cols("STUDYID", "RDOMAIN", "USUBJID", "SPDEVID", "IDVAR", "IDVARVAL", "RELTYPE",
                        "RELID"),
                row("S1", "AE", "U1", "", "AESEQ", "3", "", "1"),
                row("S1", "DE", "U1", "D1", "DESEQ", "1", "", "1"));
        RelrecPairer.Pairing pairing = pair(relrec, ae, "AE", Map.of("DE", de));

        assertEquals(List.of("0-0"), pairs(pairing),
                "DESEQ is unique within the subject/device combination: only U1 / D1 / 1");
        assertTrue(pairing.issues().isEmpty(), () -> "no issue expected: " + pairing.issues());
    }

    // ---- F4: SDTMIG-AP 1.0 section 3.4 Example 1 ----

    private static final String[] APEX_COLS = cols("STUDYID", "DOMAIN", "APID", "EXSEQ", "RSUBJID",
            "SREL", "EXTRT");

    private static final String[] APAE_COLS = cols("STUDYID", "DOMAIN", "APID", "AESEQ", "RSUBJID",
            "SREL", "AETERM");

    private static final String[] AP_RELREC_COLS = cols("STUDYID", "RDOMAIN", "USUBJID", "APID",
            "RSUBJID", "IDVAR", "IDVARVAL", "RELTYPE", "RELID");

    @Test
    void f4_apExample_pairsTheAssociatedPersonsExposureWithItsAdverseEvent()
    {
        IDataTable apex = table("APEX", APEX_COLS,
                row("Y6AOF7", "APEX", "AP_01", "1", "ABC_123", "FAMILY MEMBER", "DRUG X"));
        IDataTable apae = table("APAE", APAE_COLS,
                row("Y6AOF7", "APAE", "AP_01", "1", "ABC_123", "FAMILY MEMBER", "Rash"));
        IDataTable relrec = table("RELREC", AP_RELREC_COLS,
                row("Y6AOF7", "APEX", "", "AP_01", "ABC_123", "EXTRT", "DRUG X", "", "1"),
                row("Y6AOF7", "APAE", "", "AP_01", "ABC_123", "AETERM", "Rash", "", "1"));
        RelrecPairer.Pairing pairing = pair(relrec, apex, "APEX", Map.of("APAE", apae));

        assertEquals(List.of("0-0"), pairs(pairing), "SDTMIG-AP 1.0 3.4 Example 1");
        assertTrue(pairing.issues().isEmpty(), () -> "no issue expected: " + pairing.issues());
    }


    @Test
    void f4b_twoAssociatedPersonsReusingRelidOne_pairEachPersonWithItsOwnRecords()
    {
        // Both associated persons relate to the same study subject (RSUBJID ABC_123): taking
        // RSUBJID as the RELREC subject would cross-pair them.
        IDataTable apex = table("APEX", APEX_COLS,
                row("Y6AOF7", "APEX", "AP_01", "1", "ABC_123", "FAMILY MEMBER", "DRUG X"),
                row("Y6AOF7", "APEX", "AP_02", "1", "ABC_123", "FAMILY MEMBER", "DRUG X"));
        IDataTable apae = table("APAE", APAE_COLS,
                row("Y6AOF7", "APAE", "AP_01", "1", "ABC_123", "FAMILY MEMBER", "Rash"),
                row("Y6AOF7", "APAE", "AP_02", "1", "ABC_123", "FAMILY MEMBER", "Rash"));
        IDataTable relrec = table("RELREC", AP_RELREC_COLS,
                row("Y6AOF7", "APEX", "", "AP_01", "ABC_123", "EXTRT", "DRUG X", "", "1"),
                row("Y6AOF7", "APAE", "", "AP_01", "ABC_123", "AETERM", "Rash", "", "1"),
                row("Y6AOF7", "APEX", "", "AP_02", "ABC_123", "EXTRT", "DRUG X", "", "1"),
                row("Y6AOF7", "APAE", "", "AP_02", "ABC_123", "AETERM", "Rash", "", "1"));
        RelrecPairer.Pairing pairing = pair(relrec, apex, "APEX", Map.of("APAE", apae));

        assertEquals(List.of("0-0", "1-1"), pairs(pairing),
                "RELID is unique within the associated person (SDTM v2.0 6.1)");
        assertTrue(pairing.issues().isEmpty(), () -> "no issue expected: " + pairing.issues());
    }

    // ---- F6: a table lacking a subject identifier column RELREC names (G7-R1 (a), OD-7) ----


    @Test
    void f6_relatedLackingTheNamedSubjectColumn_isADomainIssueNotASilentMiss()
    {
        IDataTable apex = table("APEX", APEX_COLS,
                row("Y6AOF7", "APEX", "AP_01", "1", "ABC_123", "FAMILY MEMBER", "DRUG X"));
        IDataTable apae = table("APAE", cols("STUDYID", "DOMAIN", "AESEQ", "AETERM"),
                row("Y6AOF7", "APAE", "1", "Rash"));
        IDataTable relrec = table("RELREC", AP_RELREC_COLS,
                row("Y6AOF7", "APEX", "", "AP_01", "ABC_123", "EXTRT", "DRUG X", "", "1"),
                row("Y6AOF7", "APAE", "", "AP_01", "ABC_123", "AETERM", "Rash", "", "1"));
        RelrecPairer.Pairing pairing = pair(relrec, apex, "APEX", Map.of("APAE", apae));

        assertEquals(List.of(), pairs(pairing));
        assertEquals(1, pairing.issues().size(), () -> "one issue expected: " + pairing.issues());
        RelrecPairer.DomainIssue issue = pairing.issues().get(0);
        assertEquals("APAE", issue.rdomain());
        assertTrue(issue.message().contains("column APID of related domain APAE"), issue::message);
        assertEquals(List.of(1L), issue.relrecRows());
    }


    @Test
    void f6b_primaryLackingTheSubjectColumnItsRowNames_isADomainIssueNotASilentMiss()
    {
        IDataTable de = table("DE", cols("STUDYID", "DOMAIN", "USUBJID", "DESEQ", "DETERM"),
                row("S1", "DE", "U1", "1", "FISSURE"));
        IDataTable ae = table("AE", cols("STUDYID", "DOMAIN", "USUBJID", "AESEQ", "AETERM"),
                row("S1", "AE", "U1", "3", "INCISION SITE INFLAMMATION"));
        IDataTable relrec = table("RELREC",
                cols("STUDYID", "RDOMAIN", "USUBJID", "SPDEVID", "IDVAR", "IDVARVAL", "RELTYPE",
                        "RELID"),
                row("S1", "AE", "U1", "", "AESEQ", "3", "", "1"),
                row("S1", "DE", "U1", "D1", "DESEQ", "1", "", "1"));
        RelrecPairer.Pairing pairing = pair(relrec, de, "DE", Map.of("AE", ae));

        assertEquals(List.of(), pairs(pairing));
        assertEquals(1, pairing.issues().size(), () -> "one issue expected: " + pairing.issues());
        assertTrue(pairing.issues().get(0).message().contains("column SPDEVID of DE"),
                () -> pairing.issues().get(0).message());
        assertEquals(List.of(1L), pairing.issues().get(0).relrecRows());
        assertTrue(pairing.addressed().isEmpty(), "the DE record of device D1 cannot be found");
    }

    // ---- F5: POOLID column under UNKNOWN is read as SEND (FU-2 (a), ruled) ----


    @Test
    void f5_relrecCarryingPoolidUnderUnknown_isReadAsSend()
    {
        IDataTable relrec = table("RELREC",
                cols("STUDYID", "RDOMAIN", "USUBJID", "POOLID", "IDVAR", "IDVARVAL", "RELTYPE",
                        "RELID"),
                row("S1", "AE", "U1", "", "AESEQ", "1", "", "1"),
                row("S1", "CM", "U1", "", "CMSEQ", "1", "", "1"),
                row("S1", "AE", "U2", "", "AESEQ", "1", "", "1"),
                row("S1", "CM", "U2", "", "CMSEQ", "1", "", "1"));
        RelrecLinks.Links unknown = RelrecLinks.collect(relrec, "AE", null, MergeStandard.UNKNOWN);
        RelrecLinks.Links sdtm = RelrecLinks.collect(relrec, "AE", null, MergeStandard.SDTM);

        assertTrue(unknown.send(), "FU-2 (a): a POOLID column under UNKNOWN reads as SEND");
        assertEquals(1, unknown.relationshipCount(), "SEND groups by (STUDYID, RELID) (OD-3)");
        assertEquals(2, sdtm.relationshipCount(), "SDTM groups by (STUDYID, USUBJID, RELID)");
        assertEquals(List.of(4, 2), Arrays.asList(unknown.domains().get(0).links().size(),
                sdtm.domains().get(0).links().size()));
    }
}
