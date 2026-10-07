package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import net.cumba.datatable.IDataTable;
import org.junit.jupiter.api.Test;

/**
 * {@link CdtWriter} never writes the {@code Encoding} / {@code EncodingSource} table-meta keys
 * (PLAN-provider-encoding-metadata, E7). {@link CdtTableBuilder} lets a declared {@code Encoding}
 * attribute win, so a writer that forwarded the pair would turn a sas7bdat exported to CDT into a
 * file that claims {@code windows-1252} when read back, and a plain .cdt into one that claims a
 * DECLARED UTF-8. It holds because the writer skips every key with an upper-case first character;
 * this pins that the two keys stay on the skipped side.
 */
class CdtWriterEncodingKeysTest
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


    @Test
    void aSimulatedEncodingIsNotWrittenBack()
    {
        IDataTable table = tableOf("dataset DM Encoding=windows-1252 EncodingSource=DECLARED");
        assertEquals("windows-1252", table.getMetaData().getMetaData("Encoding"));

        String cdt = CdtWriter.toString(table);

        assertFalse(cdt.contains("Encoding"), cdt);
    }


    @Test
    void theFormatsOwnPairIsNotWrittenEither()
    {
        // Read back, the file then reports UTF-8 / FORMAT again rather than a DECLARED UTF-8.
        IDataTable table = tableOf("dataset DM");
        String cdt = CdtWriter.toString(table);

        assertFalse(cdt.contains("Encoding"), cdt);
        IDataTable back = CdtTableBuilder.build(CdtParser.parseFirst(cdt, "t"),
                URI.create("test:dm"));
        assertEquals("FORMAT", back.getMetaData().getMetaData("EncodingSource"));
    }
}
