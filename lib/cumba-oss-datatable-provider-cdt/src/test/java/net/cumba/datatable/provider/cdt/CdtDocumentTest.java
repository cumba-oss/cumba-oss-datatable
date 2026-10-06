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
    void anUnterminatedFenceGetsATerminatorBeforeTheFirstRow()
    {
        // review M1: a file-final empty block whose fence has no newline used to become "---a"
        String doc = "dataset X\ncol C type=Char\n---";
        CdtDocument d = CdtDocument.parse(doc);
        CdtDataset x = CdtParser.parseFirst(doc, "doc");
        String text = d.withDataset(x.toBuilder().dataRow(List.of("a")).build()).text();
        assertEquals("dataset X\ncol C type=Char\n---\na", text);
        assertEquals(List.of(List.of("a")), CdtParser.parseFirst(text, "doc").getDataRows());
        String crlf = "dataset X\r\ncol C type=Char\r\n---";
        String crlfText = CdtDocument.parse(crlf).withDataset(CdtParser.parseFirst(crlf, "doc")
                .toBuilder().dataRow(List.of("a")).dataRow(List.of("b")).build()).text();
        assertEquals("dataset X\r\ncol C type=Char\r\n---\r\na\r\nb", crlfText);
        assertEquals(crlf,
                CdtDocument.parse(crlf).withDataset(CdtParser.parseFirst(crlf, "doc")).text(),
                "no rows: the fence keeps its no-final-newline shape");
    }


    @Test
    void anUnrepresentableCellIsRefused()
    {
        // review M2: the parser splits on \R before it sees a quote and strips trailing
        // whitespace inside quotes, so these cells cannot be written; a wrong field count
        // cannot be read back either
        String doc = "dataset X\ncol C\ncol D\n---\na | b\n---\n";
        CdtDocument d = CdtDocument.parse(doc);
        CdtDataset x = CdtParser.parseFirst(doc, "doc");
        for (String lineBreak : List.of("\n", "\r", "\f", "\u000B", "\u0085", "\u2028", "\u2029"))
        {
            CdtDataset bad = x.toBuilder().clearDataRows()
                    .dataRow(List.of("a" + lineBreak + "b", "c")).build();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> d.withDataset(bad), "line break U+" + (int) lineBreak.charAt(0));
            assertTrue(e.getMessage().contains("line break"), e.getMessage());
            CdtColumn labelled = x.getColumns().get(0).toBuilder().label("x" + lineBreak + "y")
                    .build();
            CdtDataset badLabel = x.toBuilder().clearColumns().column(labelled)
                    .column(x.getColumns().get(1)).build();
            assertThrows(IllegalArgumentException.class, () -> d.withDataset(badLabel),
                    "label line break U+" + (int) lineBreak.charAt(0));
        }
        for (String trailing : List.of("a ", "a\t", "a\u001F", " "))
        {
            CdtDataset bad = x.toBuilder().clearDataRows().dataRow(List.of(trailing, "c")).build();
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> d.withDataset(bad), "trailing whitespace: [" + trailing + "]");
            assertTrue(e.getMessage().contains("trailing whitespace"), e.getMessage());
        }
        CdtColumn trailingLabel = x.getColumns().get(0).toBuilder().label("Label ").build();
        assertThrows(IllegalArgumentException.class, () -> d.withDataset(x.toBuilder()
                .clearColumns().column(trailingLabel).column(x.getColumns().get(1)).build()));
        IllegalArgumentException count = assertThrows(IllegalArgumentException.class,
                () -> d.withDataset(x.toBuilder().clearDataRows().dataRow(List.of("a")).build()));
        assertTrue(count.getMessage().contains("1 field(s) but 2 column(s)"), count.getMessage());
        assertThrows(IllegalArgumentException.class, () -> d.withDataset(
                x.toBuilder().clearDataRows().dataRow(List.of("a", "b", "c")).build()));
        assertThrows(IllegalArgumentException.class,
                () -> d.withDataset(x.toBuilder().clearColumns().clearDataRows().build()),
                "no columns: the parser refuses the block");
        // the sensitivity arm: the same document takes representable cells
        String ok = d
                .withDataset(
                        x.toBuilder().clearDataRows().dataRow(List.of(" lead", "in\tside")).build())
                .text();
        assertEquals(List.of(List.of(" lead", "in\tside")),
                CdtParser.parseFirst(ok, "doc").getDataRows());
    }


    @Test
    void aControlCharacterAtAFieldEdgeIsQuotedSoTheRowTrimKeepsIt()
    {
        // the parser trims each row with String.trim(), which also drops non-whitespace
        // controls below U+0020 at the row's edges
        String doc = "dataset X\ncol C\ncol D\n---\na | b\n---\n";
        CdtDataset x = CdtParser.parseFirst(doc, "doc");
        List<String> row = List.of("\u0001a", "b\u0001");
        String text = CdtDocument.parse(doc)
                .withDataset(x.toBuilder().clearDataRows().dataRow(row).build()).text();
        assertEquals(List.of(row), CdtParser.parseFirst(text, "doc").getDataRows());
    }


    @Test
    void everyLineBreakTheParserKnowsSplitsTheDocument()
    {
        // review L1: the parser splits on \R; locating on \n / \r alone saw one long line
        for (String lineBreak : List.of("\u000B", "\f", "\u0085", "\u2028", "\u2029"))
        {
            String doc = "dataset X" + lineBreak + "col C type=Char" + lineBreak + "---" + lineBreak
                    + "a" + lineBreak + "---" + lineBreak;
            CdtDocument d = CdtDocument.parse(doc);
            String what = "U+" + Integer.toHexString(lineBreak.charAt(0));
            assertEquals(doc, d.text(), what);
            CdtDocument.DatasetBlock block = d.datasets().getFirst();
            assertEquals(2, block.fenceLine(), what);
            assertEquals(4, block.closeLine(), what);
            assertEquals(doc, d.withDataset(CdtParser.parseFirst(doc, "doc")).text(), what);
        }
        assertEquals(List.of("a\u2028", "b\r\n", "c"),
                CdtDocument.splitKeepingTerminators("a\u2028b\r\nc"));
    }


    @Test
    void quotedNamesAreReadUnquotedAndWrittenQuoted()
    {
        // review L2: dataset "DM" is the dataset DM; a column name with a space needs quotes
        String doc = "dataset \"DM\" label=Demographics\ncol \"A B\" type=Char\n---\nx\n---\n";
        CdtDocument d = CdtDocument.parse(doc);
        assertEquals("DM", d.datasets().getFirst().name());
        assertNotNull(d.dataset("dm"));
        CdtDataset dm = CdtParser.parseFirst(doc, "doc");
        assertEquals("A B", dm.getColumns().getFirst().getName());
        String text = d.withDataset(dm).text();
        assertEquals(doc, text);
        CdtColumn quoteInName = dm.getColumns().getFirst().toBuilder().name("say\"x").build();
        String rewritten = d.withDataset(dm.toBuilder().clearColumns().column(quoteInName).build())
                .text();
        assertEquals("say\"x",
                CdtParser.parseFirst(rewritten, "doc").getColumns().getFirst().getName(),
                rewritten);
        CdtColumn equalsInName = dm.getColumns().getFirst().toBuilder().name("a=b").build();
        assertThrows(IllegalArgumentException.class,
                () -> d.withDataset(dm.toBuilder().clearColumns().column(equalsInName).build()),
                "the parser reads a first token with '=' as a missing name");
    }


    @Test
    void whatTheParserRefusesIsRefused()
    {
        assertThrows(CdtParseException.class, () -> CdtDocument.parse("dataset X\ncol C\n"));
        assertThrows(CdtParseException.class, () -> CdtDocument.parse("# only a comment\n"));
    }
}
