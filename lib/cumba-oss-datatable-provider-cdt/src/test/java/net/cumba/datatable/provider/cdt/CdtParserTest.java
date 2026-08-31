package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CdtParserTest
{

    @Test
    void singleDatasetWithoutClosingFence_backwardCompat()
    {
        String content = """
                dataset DM
                col USUBJID type=Char
                col AGE type=Num
                ---
                S001 | 42
                S002 | 37
                """;
        List<CdtDataset> all = CdtParser.parseAll(content, "t");
        assertEquals(1, all.size());
        CdtDataset ds = all.get(0);
        assertEquals("DM", ds.getName());
        assertEquals(2, ds.getDataRows().size());
        assertEquals(List.of("S001", "42"), ds.getDataRows().get(0));
        assertEquals("---", ds.getFence());
    }


    @Test
    void multipleDatasetsWithClosingFence()
    {
        String content = """
                dataset DM
                col USUBJID type=Char
                ---
                S001
                S002
                ---

                dataset AE
                col USUBJID type=Char
                col AETERM type=Char
                ---
                S001 | Headache
                S002 | Rash
                ---
                """;
        List<CdtDataset> all = CdtParser.parseAll(content, "t");
        assertEquals(2, all.size());
        assertEquals("DM", all.get(0).getName());
        assertEquals("AE", all.get(1).getName());
        assertEquals(2, all.get(1).getDataRows().size());
    }


    @Test
    void longerFenceAllowsLiteralDashesInData()
    {
        String content = """
                dataset TRIPLE
                col VAL type=Char
                -----
                ---
                ----
                -----
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("-----", ds.getFence());
        assertEquals(2, ds.getDataRows().size());
        assertEquals(List.of("---"), ds.getDataRows().get(0));
        assertEquals(List.of("----"), ds.getDataRows().get(1));
    }


    @Test
    void commentsAndBlanksAreIgnoredEverywhere()
    {
        String content = """
                # leading comment

                dataset DM
                # comment between header and columns
                col USUBJID type=Char

                ---
                # comment inside data
                S001

                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("DM", ds.getName());
        assertEquals(1, ds.getDataRows().size());
    }


    @Test
    void parseNamedCaseInsensitive()
    {
        String content = """
                dataset DM
                col USUBJID type=Char
                ---
                S001
                ---

                dataset AE
                col USUBJID type=Char
                ---
                S001
                ---
                """;
        CdtDataset dm = CdtParser.parseNamed(content, "t", "dm");
        assertNotNull(dm);
        assertEquals("DM", dm.getName());
        assertEquals("AE", CdtParser.parseNamed(content, "t", "AE").getName());
    }


    @Test
    void defaultFormatsDerivedForDateTimeDateTime()
    {
        String content = """
                dataset T
                col D type=Date
                col T type=Time
                col DT type=DateTime
                col N type=Num
                ---
                2021-01-01 | 12:30:00 | 2021-01-01T12:30:00 | 5
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("DATE9.", ds.getColumns().get(0).getFormat());
        assertEquals("TIME5.", ds.getColumns().get(1).getFormat());
        assertEquals("DATETIME20.", ds.getColumns().get(2).getFormat());
        // Num keeps no default format
        assertEquals(null, ds.getColumns().get(3).getFormat());
    }


    @Test
    void explicitFormatOverridesDefault()
    {
        String content = """
                dataset T
                col D type=Date format=DDMMYY10.
                ---
                2021-01-01
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("DDMMYY10.", ds.getColumns().get(0).getFormat());
    }


    @Test
    void columnLengthParsedAsInt()
    {
        String content = """
                dataset T
                col A type=Char length=20
                ---
                x
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals(Integer.valueOf(20), ds.getColumns().get(0).getLength());
    }


    @Test
    void datasetAttributesRetained()
    {
        String content = """
                dataset ADSL class=ADSL standard="SDTMIG 3.4"
                col USUBJID type=Char
                ---
                S1
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("ADSL", ds.getAttrs().get("class"));
        assertEquals("SDTMIG 3.4", ds.getAttrs().get("standard"));
    }


    static Stream<Arguments> invalidContentProvider()
    {
        return Stream.of(Arguments.of("invalidLengthValue", """
                dataset T
                col A type=Char length=NaN
                ---
                x
                ---
                """), Arguments.of("unknownColumnType", """
                dataset T
                col A type=Boolean
                ---
                true
                ---
                """), Arguments.of("emptyFile", ""), Arguments.of("commentsOnlyFile", """
                # only comments
                # nothing else
                """), Arguments.of("unterminatedQuotedDataValue", """
                dataset T
                col A type=Char
                ---
                "unterminated
                ---
                """), Arguments.of("contentAfterClosingQuoteBeforePipe", """
                dataset T
                col A type=Char
                col B type=Char
                ---
                "a" junk | b
                ---
                """), Arguments.of("unterminatedQuotedMetadataValue", """
                dataset T label="unterminated
                col A type=Char
                ---
                x
                ---
                """), Arguments.of("missingFence", """
                dataset DM
                col USUBJID type=Char
                S001
                """), Arguments.of("noColumns", """
                dataset DM
                ---
                S001
                """));
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContentProvider")
    void invalidContentThrows(String name, String content)
    {
        assertThrows(CdtParseException.class, () -> CdtParser.parseAll(content, "t"));
    }


    @Test
    void escapedQuoteInsideQuotedValue()
    {
        // Writer emits: label="a\"b" — reader must see: label = a"b
        String content = """
                dataset DM label="He said \\"hi\\""
                col USUBJID type=Char label="quoted \\"value\\""
                ---
                x
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("He said \"hi\"", ds.getLabel());
        assertEquals("quoted \"value\"", ds.getColumns().get(0).getLabel());
    }


    @Test
    void escapedBackslashInsideQuotedValue()
    {
        String content = """
                dataset DM label="a\\\\b"
                col X type=Char
                ---
                x
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("a\\b", ds.getLabel());
    }


    @Test
    void unknownColAttrIsKeptInAttrs()
    {
        String content = """
                dataset DM
                col AGE type=Num role=Covariate origin=Derived
                ---
                42
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        CdtColumn c = ds.getColumns().get(0);
        assertEquals("Covariate", c.getAttrs().get("role"));
        assertEquals("Derived", c.getAttrs().get("origin"));
    }


    static Stream<Arguments> singleValueDataFieldProvider()
    {
        return Stream.of(Arguments.of("unquotedDataFieldStripsBothSides", """
                dataset T
                col X type=Char
                ---
                   padded
                ---
                """, "padded"),
                Arguments.of("quotedDataFieldPreservesLeadingSpaceStripsTrailing", """
                        dataset T
                        col X type=Char
                        ---
                        "  leading   "
                        ---
                        """, "  leading"), Arguments.of("quotedPipeLeadingValueIsLiteralData", """
                        dataset T
                        col A type=Char
                        ---
                        "|pipe-prefix"
                        ---
                        """, "|pipe-prefix"), Arguments.of("quotedDotIsLiteralDotString", """
                        dataset T
                        col X type=Char
                        ---
                        "."
                        ---
                        """, "."));
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("singleValueDataFieldProvider")
    void singleValueDataFieldParsing(String name, String content, String expected)
    {
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals(expected, ds.getDataRows().get(0).get(0));
    }


    @Test
    void quotedDataFieldPreservesPipesAndQuotes()
    {
        String content = """
                dataset T
                col A type=Char
                col B type=Char
                ---
                "a | b" | "c\\"d"
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("a | b", ds.getDataRows().get(0).get(0));
        assertEquals("c\"d", ds.getDataRows().get(0).get(1));
    }


    @Test
    void hashCommentInsideDataBlockIsSkipped()
    {
        String content = """
                dataset T
                col A type=Char
                ---
                S001
                # disabled row
                S003
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals(2, ds.getDataRows().size());
        assertEquals("S001", ds.getDataRows().get(0).get(0));
        assertEquals("S003", ds.getDataRows().get(1).get(0));
    }


    @Test
    void quotedHashLeadingValueIsLiteralData()
    {
        // Without quotes, `#001` looks like a comment; the writer auto-quotes.
        String content = """
                dataset T
                col A type=Char
                ---
                "#001"
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals(1, ds.getDataRows().size());
        assertEquals("#001", ds.getDataRows().get(0).get(0));
    }


    @Test
    void dotSentinelInMultiColumnRow()
    {
        String content = """
                dataset T
                col A type=Char
                col B type=Char
                ---
                .
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals(List.of("", ""), ds.getDataRows().get(0));
    }


    @Test
    void metadataLineTrailingSpaceIsStripped()
    {
        String content = """
                dataset T label="trailing   "
                col X type=Char label="col   "
                ---
                x
                ---
                """;
        CdtDataset ds = CdtParser.parseFirst(content, "t");
        assertEquals("trailing", ds.getLabel());
        assertEquals("col", ds.getColumns().get(0).getLabel());
    }


    @Test
    void wrongFieldCountThrows()
    {
        String content = """
                dataset DM
                col A type=Char
                col B type=Char
                ---
                only-one-field
                """;
        assertThrows(CdtParseException.class, () -> CdtParser.parseAll(content, "t"));
    }
}
