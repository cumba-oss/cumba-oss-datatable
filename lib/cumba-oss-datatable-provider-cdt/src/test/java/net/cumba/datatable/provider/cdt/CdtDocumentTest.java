package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * {@link CdtDocument}: the text is kept byte for byte, a rewrite touches one block's {@code col}
 * lines and rows and nothing else, and every missing-value spelling of the 2026-09-17 sentinel
 * grammar survives a rewrite with its kind — the three ways the old editor's serializer corrupted
 * them ({@code PLAN-rule-authoring-api} §3.1).
 */
class CdtDocumentTest
{

    private static final String DOC = """
            #!RuleTest
            #test CDISC-XX0001 expect=violation domain=AE
            #define standard=sdtmig version=3-4
            #  a comment the editor must not lose

            dataset AE class=EVENTS label="Adverse Events"
            col STUDYID   type=Char label="Study Identifier"
            col AESEQ     type=Num
            col AESTDTC   type=Char
            ---
            S1 | 1 | 2024-01-01
            S1 | . | .A
            "S1" | ._ | "."
            ---

            # between the blocks
            dataset DM label=Demographics
            col STUDYID type=Char
            col RFSTDTC type=Char
            -----
            S1 | 2023-12-31
            -----
            """;

    @Test
    void theTextSurvivesByteForByte()
    {
        CdtDocument doc = CdtDocument.parse(DOC);
        assertEquals(DOC, doc.text());
        assertEquals(2, doc.datasets().size());
        assertEquals("AE", doc.datasets().get(0).name());
        assertEquals("DM", doc.datasets().get(1).name());
        assertNotNull(doc.dataset("ae"));
        assertNull(doc.dataset("LB"));
        CdtDocument.DatasetBlock ae = doc.datasets().get(0);
        assertEquals(5, ae.headerLine());
        assertEquals(9, ae.fenceLine());
        assertEquals(13, ae.closeLine());
        assertEquals(14, ae.end());
    }


    @Test
    void aNoOpRewriteChangesOnlyTheBlocksSpelling()
    {
        CdtDocument doc = CdtDocument.parse(DOC);
        CdtDataset ae = CdtParser.parseNamed(DOC, "doc", "AE");
        assertNotNull(ae);
        CdtDocument rewritten = doc.withDataset(ae);
        String text = rewritten.text();
        // everything outside the AE block is byte-identical
        String before = DOC.substring(0, DOC.indexOf("col STUDYID   type=Char"));
        assertTrue(text.startsWith(before), text);
        String after = DOC.substring(DOC.indexOf("---\n\n# between the blocks") + 4);
        assertTrue(text.endsWith(after), text);
        // the block reads back value-equal, missing kinds included
        CdtDataset again = CdtParser.parseNamed(text, "doc", "AE");
        assertNotNull(again);
        assertEquals(ae.getDataRows(), again.getDataRows());
        assertEquals(ae.getColumns(), again.getColumns());
        assertEquals(MissingValue.MIS,
                CdtValues.parseValue(again.getDataRows().get(1).get(1), CdtType.NUM));
        assertEquals(MissingValue.forValue(".A"),
                CdtValues.parseValue(again.getDataRows().get(1).get(2), CdtType.CHAR));
        assertEquals(MissingValue.forValue("._"),
                CdtValues.parseValue(again.getDataRows().get(2).get(1), CdtType.NUM));
        assertEquals(".", CdtValues.parseValue(again.getDataRows().get(2).get(2), CdtType.CHAR),
                "a quoted dot is the literal, not the sentinel");
        // the rewritten rows are the writer's spelling
        assertTrue(text.contains("S1 | . | .A\n"), text);
        assertTrue(text.contains("S1 | ._ | \".\"\n"), text);
    }


    @Test
    void aGridEditRewritesTheRows()
    {
        CdtDocument doc = CdtDocument.parse(DOC);
        CdtDataset dm = CdtParser.parseNamed(DOC, "doc", "DM");
        assertNotNull(dm);
        CdtDataset edited = dm.toBuilder().clearDataRows().dataRow(List.of("S1", "2024-02-02"))
                .dataRow(List.of("S2", ".")).build();
        String text = doc.withDataset(edited).text();
        assertTrue(text.contains("-----\nS1 | 2024-02-02\nS2 | .\n-----\n"), text);
        assertTrue(text.startsWith(DOC.substring(0, DOC.indexOf("col STUDYID type=Char"))),
                "the AE block and the prelude are untouched");
        assertNotEquals(DOC, text);
        assertThrows(IllegalArgumentException.class,
                () -> doc.withDataset(dm.toBuilder().name("LB").build()));
    }


    @Test
    void everySentinelSpellingRoundTrips()
    {
        String doc = """
                dataset X
                col C type=Char
                col N type=Num
                ---
                . | .
                .A | .Z
                ._ | ._
                "" | .
                "." | 1
                "\\." | 2
                "\\\\.A" | 3
                "a|b" | 4
                " lead" | 5
                "trail " | 6
                "say \\"hi\\"" | 7
                ---
                """;
        CdtDocument d = CdtDocument.parse(doc);
        CdtDataset x = CdtParser.parseFirst(doc, "doc");
        String text = d.withDataset(x).text();
        CdtDataset again = CdtParser.parseFirst(text, "doc");
        assertEquals(x.getDataRows(), again.getDataRows(), text);
        for (int r = 0; r < x.getDataRows().size(); r++)
        {
            assertEquals(CdtValues.parseValue(x.getDataRows().get(r).get(0), CdtType.CHAR),
                    CdtValues.parseValue(again.getDataRows().get(r).get(0), CdtType.CHAR),
                    "row " + r + " char");
            assertEquals(CdtValues.parseValue(x.getDataRows().get(r).get(1), CdtType.NUM),
                    CdtValues.parseValue(again.getDataRows().get(r).get(1), CdtType.NUM),
                    "row " + r + " num");
        }
    }


    @Test
    void theAllMissingRowAndTheEmptySingleFieldKeepTheirMeaning()
    {
        String doc = "dataset X\ncol C type=Char\n---\n.\n\"\"\n---\n";
        CdtDocument d = CdtDocument.parse(doc);
        CdtDataset x = CdtParser.parseFirst(doc, "doc");
        String text = d.withDataset(x).text();
        assertEquals("dataset X\ncol C type=Char\n---\n.\n\"\"\n---\n", text);
        assertEquals(x.getDataRows(), CdtParser.parseFirst(text, "doc").getDataRows());
    }


    @Test
    void aFileFinalBlockWithoutAClosingFenceStaysThatWay()
    {
        String doc = "dataset X\ncol C type=Char\n---\na\nb";
        CdtDocument d = CdtDocument.parse(doc);
        assertEquals(-1, d.datasets().getFirst().closeLine());
        CdtDataset x = CdtParser.parseFirst(doc, "doc");
        assertEquals(doc, d.withDataset(x).text());
    }


    @Test
    void crlfIsKept()
    {
        String doc = "dataset X\r\ncol C type=Char\r\n---\r\na\r\n---\r\n";
        CdtDocument d = CdtDocument.parse(doc);
        assertEquals(doc, d.text());
        assertEquals(doc, d.withDataset(CdtParser.parseFirst(doc, "doc")).text());
    }


    @Test
    void whatTheParserRefusesIsRefused()
    {
        assertThrows(CdtParseException.class, () -> CdtDocument.parse("dataset X\ncol C\n"));
        assertThrows(CdtParseException.class, () -> CdtDocument.parse("# only a comment\n"));
    }
}
