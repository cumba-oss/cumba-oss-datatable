package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import net.cumba.datatable.IDataTable;
import org.junit.jupiter.api.Test;

/**
 * Owner ruling Q31 ⓑ (2026-10-12): <i>"agree to b, but might already be closed."</i> -- an
 * identifier allowlist. The dataset header drops a key whose first character is upper case (the
 * presentation keys the SAS and Dataset-JSON loaders add), EXCEPT the Dataset-JSON document
 * identifiers, which survive whatever their capitalisation: matched ignoring case, written in the
 * key's own spelling. Copied byte-identical to the OSS twin, whose {@link CdtWriter} is pinned
 * identical.
 */
class CdtWriterIdentifierAllowlistTest
{

    private static IDataTable tableOf(String aHeader)
    {
        CdtDataset ds = CdtParser.parseFirst(aHeader + """

                col USUBJID type=Char
                ---
                A
                ---
                """, "t");
        return CdtTableBuilder.build(ds, URI.create("test:dm"));
    }


    private static String headerOf(IDataTable aTable)
    {
        return CdtWriter.toString(aTable).split("\n", -1)[0];
    }


    /** A PascalCase or upper-case identifier round-trips, value and spelling. */
    @Test
    void listedIdentifiersSurviveWhateverTheirCapitalisation()
    {
        IDataTable t = tableOf("dataset DM StudyOID=S1 FILEOID=F1 MetaDataVersionOID=MDV.1"
                + " ItemGroupOID=IG.DM DatasetJSONVersion=1.1");
        String header = headerOf(t);

        assertTrue(header.contains("StudyOID=S1"), header);
        assertTrue(header.contains("FILEOID=F1"), header);
        assertTrue(header.contains("MetaDataVersionOID=MDV.1"), header);
        assertTrue(header.contains("ItemGroupOID=IG.DM"), header);
        assertTrue(header.contains("DatasetJSONVersion=1.1"), header);

        IDataTable back = CdtTableBuilder.build(CdtParser.parseFirst(CdtWriter.toString(t), "t"),
                URI.create("test:dm"));
        assertEquals("S1", String.valueOf(back.getMetaData().getMetaData("StudyOID")));
    }


    /**
     * The allowlist fails closed: a presentation key keeps being dropped, and so does a key that
     * merely contains an identifier.
     */
    @Test
    void presentationKeysAreStillDropped()
    {
        String header = headerOf(
                tableOf("dataset DM Comment=c Class=EVENTS Structure=s StudyOIDs=x XStudyOID=y"));

        assertFalse(header.contains("Comment="), header);
        assertFalse(header.contains("Class="), header);
        assertFalse(header.contains("Structure="), header);
        assertFalse(header.contains("StudyOIDs="), header);
        assertFalse(header.contains("XStudyOID="), header);
    }
}
