package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Byte-level tests of {@link CdtWriter} against the format spec in this module's README. Per the
 * campaign's oracle rule, none of these compare the writer's output to what {@link CdtParser} would
 * read back - CDT is this product's own format, so "our reader agrees with our writer" would only
 * prove the two halves agree with each other, not that either is right. Every assertion here checks
 * literal output text against the grammar and quoting rules the README documents.
 */
class CdtWriterSpecTest
{

    // ---- helpers --------------------------------------------------------------------------

    private static IDataTable oneColumnTable(String aTableName, DataTableColumnMeta aColumn,
            Object aValue)
    {
        DataTableMeta md = DataTableMeta.builder().name(aTableName).rowCount(1).totalRowCount(1)
                .setColumns(aColumn).build();
        CachedDataTableColumn col = new CachedDataTableColumn(0,
                aColumn.getType() != null ? aColumn.getType() : DataValueType.STRING);
        col.addElement(aValue);
        col.complete();
        return new ColumnCachedDataTable(md, col);
    }


    private static DataTableColumnMeta charColumn(String aColName)
    {
        return DataTableColumnMeta.builder().index(0).name(aColName).type(DataValueType.STRING)
                .build();
    }


    private static IDataTable oneCharColumnTable(String aTableName, String aColName, String aValue)
    {
        return oneColumnTable(aTableName, charColumn(aColName), aValue);
    }


    /**
     * The single data row line (no trailing newline) of a one-row, one-column {@code .cdt} block.
     */
    private static String dataLineOf(String aCdtText)
    {
        String[] lines = aCdtText.split("\n", -1);
        // dataset line, col line, opening fence, DATA ROW, closing fence, ...
        return lines[3];
    }


    /** The single {@code col ...} declaration line for the column at the given index. */
    private static String colLineOf(String aCdtText, int aColIndex)
    {
        String[] lines = aCdtText.split("\n", -1);
        return lines[1 + aColIndex];
    }

    // ---- quoteFieldIfNeeded (data field quoting) -------------------------------------------


    @Test
    void plainDataValueIsNotQuoted()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", "plain"));
        assertEquals("plain", dataLineOf(text));
    }


    @Test
    void dataValueStartingWithHashIsQuoted()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", "#001"));
        assertEquals("\"#001\"", dataLineOf(text));
    }


    @Test
    void dataValueStartingWithPipeIsQuoted()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", "|pipe-prefix"));
        assertEquals("\"|pipe-prefix\"", dataLineOf(text));
    }


    @Test
    void dataValueStartingWithWhitespaceIsQuoted()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", " leading"));
        assertEquals("\" leading\"", dataLineOf(text));
    }


    @Test
    void dataValueContainingEmbeddedPipeIsQuoted()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", "mid|pipe"));
        assertEquals("\"mid|pipe\"", dataLineOf(text));
    }


    @Test
    void dataValueContainingQuoteIsQuotedAndEscaped()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", "has\"quote"));
        assertEquals("\"has\\\"quote\"", dataLineOf(text));
    }


    @Test
    void dataValueContainingBackslashIsQuotedAndEscaped()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", "back\\slash"));
        assertEquals("\"back\\\\slash\"", dataLineOf(text));
    }


    /** A literal single-dot value must be quoted so it is not mistaken for the missing sentinel. */
    @Test
    void literalDotCharValueIsQuoted()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", "."));
        assertEquals("\".\"", dataLineOf(text));
    }


    /**
     * F-prov-cdt-03: a single-column value that is itself a fence line (three or more dashes and
     * nothing else) must be quoted - unquoted, it is byte-identical to the line that closes the
     * data block, and {@link CdtFence#matches} would treat it as the closer on read.
     */
    @Test
    void singleColumnFenceLookalikeValueIsQuoted()
    {
        assertEquals("\"---\"",
                dataLineOf(CdtWriter.toString(oneCharColumnTable("T", "X", "---"))));
        assertEquals("\"-----\"",
                dataLineOf(CdtWriter.toString(oneCharColumnTable("T", "X", "-----"))));
        // Two dashes is not a fence (README: minimum length three) - no reason to quote it.
        assertEquals("--", dataLineOf(CdtWriter.toString(oneCharColumnTable("T", "X", "--"))));
    }


    /**
     * F-prov-cdt-02: a single-column table's row whose one field renders empty must not be written
     * as a bare blank line - {@link CdtParser} skips blank lines everywhere, so it would silently
     * disappear on read. (Not exercised by reading it back here - per the oracle rule, this asserts
     * the writer's own literal output line, which is what the marker's whole purpose is about: not
     * being an empty line.)
     *
     * <p>
     * ⚠ Changed 2026-09-17: the marker used to be the {@code .} row sentinel, and can no longer be.
     * {@code .} now means {@link net.cumba.datatable.values.MissingValue#MIS} in every column type,
     * so writing it for an EMPTY STRING char cell would turn {@code ""} into missing on read. The
     * quoted empty field says "one field, empty" and claims nothing about missingness.
     * </p>
     */
    @Test
    void singleColumnEmptyCharRowIsWrittenAsQuotedEmptyNotBlankLine()
    {
        String text = CdtWriter.toString(oneCharColumnTable("T", "X", ""));
        assertEquals("\"\"", dataLineOf(text));
    }


    /**
     * The other half of the pair: a single-column row holding a genuine missing value is written as
     * its bare sentinel - which is already a non-blank line, so it needs no marker of its own, and
     * must NOT be quoted (quoting is exactly how it would read back as a literal dot).
     */
    @Test
    void singleColumnMissingCharRowIsWrittenAsTheBareSentinel()
    {
        assertEquals(".", dataLineOf(CdtWriter.toString(oneColumnTable("T", charColumn("X"),
                net.cumba.datatable.values.MissingValue.MIS))));
        assertEquals(".A", dataLineOf(CdtWriter.toString(oneColumnTable("T", charColumn("X"),
                net.cumba.datatable.values.MissingValue.MIS_A))));
    }


    /**
     * A literal value shaped like a SPECIAL missing must be quoted too, for the same reason a
     * literal {@code .} is - {@link #literalDotCharValueIsQuoted}.
     */
    @Test
    void literalSpecialMissingShapedCharValueIsQuoted()
    {
        assertEquals("\"._\"", dataLineOf(CdtWriter.toString(oneCharColumnTable("T", "X", "._"))));
        assertEquals("\".A\"", dataLineOf(CdtWriter.toString(oneCharColumnTable("T", "X", ".A"))));
        // .a is not a SAS form, so it is ordinary data and needs no quoting.
        assertEquals(".a", dataLineOf(CdtWriter.toString(oneCharColumnTable("T", "X", ".a"))));
    }

    // ---- quoteIfNeeded (header attribute quoting) ------------------------------------------


    @Test
    void headerAttributeValueWithSpaceIsQuoted(@TempDir Path tmp) throws IOException
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset ADSL class=ADSL standard="SDTMIG 3.4"
                col USUBJID type=Char
                ---
                S1
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertTrue(text.contains("standard=\"SDTMIG 3.4\""), text);
    }


    @Test
    void emptyHeaderAttributeValueIsQuotedAsEmptyQuotes()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset DM emptyattr=""
                col X type=Char
                ---
                v
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertTrue(text.contains(" emptyattr=\"\""), text);
    }

    // ---- writeDatasetHeader: label fallback, class, generic-attr filtering ----------------


    @Test
    void datasetLabelIsOmittedWhenMetaHasNoLabelAtAll()
    {
        // Built directly (not through CdtTableBuilder, which would already fall back the label
        // to the name) so meta.getLabel() is genuinely null - exercises the aLabel == null arm.
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("X")
                .type(DataValueType.STRING).build();
        IDataTable t = oneColumnTable("NOLABEL", cm, "v");
        assertEquals(null, t.getMetaData().getLabel());
        String text = CdtWriter.toString(t);
        assertFalse(text.contains(" label="), text);
    }


    @Test
    void datasetLabelIsOmittedWhenItEqualsTheName()
    {
        // No explicit label declared: CdtTableBuilder.buildMeta falls the label back to the name,
        // so the writer must not re-emit a redundant "label=DM" that equals the dataset name.
        CdtDataset ds = CdtParser.parseFirst("""
                dataset DM
                col X type=Char
                ---
                v
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        assertEquals("DM", t.getMetaData().getLabel());
        String text = CdtWriter.toString(t);
        assertFalse(text.contains(" label="), text);
    }


    /**
     * Exercises the whole generic dataset-attribute filter in one pass: {@code class} is emitted
     * once via its own dedicated path (not doubled through the generic loop), a plain lowercase key
     * is emitted, and {@code dataset_class} / a dotted (column-qualified) key / an uppercase
     * (presentation) key are all suppressed.
     */
    @Test
    void datasetHeaderEmitsClassOnceAndFiltersReservedDottedAndUppercaseKeys()
    {
        CdtDataset ds = CdtParser.parseFirst(
                """
                        dataset ADSL class=ADSL standard="SDTMIG 3.4" dataset_class=ADSL col.attr=x SITEID=01 .leading=y
                        col A type=Char
                        ---
                        x
                        ---
                        """,
                "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        String headerLine = text.split("\n", -1)[0];

        assertEquals(1, headerLine.split("class=ADSL", -1).length - 1,
                "class=ADSL must appear exactly once: " + headerLine);
        assertTrue(headerLine.contains("standard=\"SDTMIG 3.4\""), headerLine);
        assertFalse(headerLine.contains("dataset_class="), headerLine);
        assertFalse(headerLine.contains("col.attr="), headerLine);
        assertFalse(headerLine.contains("SITEID="), headerLine);
        // The dot can be the very first character of the key (index 0) - boundary case for the
        // "key.indexOf('.') >= 0" check.
        assertFalse(headerLine.contains(".leading="), headerLine);
    }


    /**
     * ⭐⭐ Q31: a hand-authored camelCase attribute must survive write-back. Before the fix
     * {@code hasUpperCase} dropped any key containing a capital anywhere, so
     * {@code dataset DM studyOID=CDISC01} lost {@code studyOID} silently — against the README's
     * "any other key=value pair is stored on the dataset for round-trip".
     *
     * <p>
     * The Dataset-JSON document identifiers are the same shape and were lost the same way, which is
     * why they are pinned here together rather than as one example: they are real data a loader
     * puts on the table, not decoration.
     * </p>
     */
    @Test
    void camelCaseAttributesRoundTripThroughTheWriter()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset DM studyOID=CDISC01 fileOID=F1 itemGroupOID=IG.DM datasetJSONVersion=1.1
                col A type=Char
                ---
                x
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String headerLine = CdtWriter.toString(t).split("\n", -1)[0];

        assertTrue(headerLine.contains("studyOID=CDISC01"), headerLine);
        assertTrue(headerLine.contains("fileOID=F1"), headerLine);
        assertTrue(headerLine.contains("itemGroupOID=IG.DM"), headerLine);
        assertTrue(headerLine.contains("datasetJSONVersion=1.1"), headerLine);
    }


    /**
     * The other half of Q31's rule, and the reason it is a FIRST-character test rather than no test
     * at all: the presentation keys the SAS and Dataset-JSON loaders put on a dataset are still
     * suppressed, so the fix did not trade a silent data loss for a 10x file.
     *
     * <p>
     * ⚠ {@code StudyOID} is in the suppressed list on purpose. It is the residual hole the ruling
     * names: a hand-authored PascalCase key is indistinguishable from {@code Comment} under a
     * capitalisation rule and is still dropped. Pinned as MEASURED, not as intended — if the filter
     * is ever given an explicit presentation-key list this assertion is the one that must flip, and
     * it should flip deliberately.
     * </p>
     */
    @Test
    void presentationKeysAreStillSuppressedAndPascalCaseIsTheResidualHole()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset DM Comment=c Standard=SDTM Purpose=Tabulation SASDatasetName=DM StudyOID=S1
                col A type=Char
                ---
                x
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String headerLine = CdtWriter.toString(t).split("\n", -1)[0];

        assertFalse(headerLine.contains("Comment="), headerLine);
        assertFalse(headerLine.contains("Standard="), headerLine);
        assertFalse(headerLine.contains("Purpose="), headerLine);
        assertFalse(headerLine.contains("SASDatasetName="), headerLine);
        assertFalse(headerLine.contains("StudyOID="),
                headerLine + " -- Q31's known residual hole, pinned as measured");
    }


    /**
     * The positive case the other two {@code label} tests don't cover: a label that is both
     * non-blank AND distinct from the name must actually be emitted. Together the three pin all
     * three sub-conditions of the
     * {@code aLabel != null && !aLabel.isBlank() && !aLabel.equals(aName)} guard.
     */
    @Test
    void datasetLabelIsEmittedWhenPresentAndDistinctFromName()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset DM label=Demographics
                col X type=Char
                ---
                v
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertTrue(text.split("\n", -1)[0].contains(" label=Demographics"), text);
    }

    // ---- safeIdent -------------------------------------------------------------------------


    @Test
    void safeIdentMapsEmptyOrNullNameToUnderscore()
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("")
                .type(DataValueType.STRING).build();
        IDataTable t = oneColumnTable("T", cm, "v");
        assertTrue(colLineOf(CdtWriter.toString(t), 0).startsWith("col _"),
                colLineOf(CdtWriter.toString(t), 0));
    }

    // ---- renderNumber: non-Double Number -----------------------------------------------


    /**
     * A {@code Long} value populated into a Num (DOUBLE) column still renders as a plain integer,
     * not a fraction.
     *
     * <p>
     * This does NOT reach {@code renderNumber}'s "{@code instanceof Number} but not {@code Double}"
     * arm, or any of the generic {@code aValue.toString()} fallbacks in {@code renderNumber}/{@code
     * renderDate}/{@code renderTime}/{@code renderDateTime} - verified by inspection and by this
     * very test: {@code CachedDataTableColumn}'s {@code DataBufferDouble} normalises every stored
     * element to a primitive {@code double}, so {@code getValue()} always hands the writer back a
     * boxed {@code Double}, never the original {@code Long} (or a {@code String}, which
     * {@code DataBufferDouble} rejects outright at {@code addElement} time). Those fallback
     * branches are reachable only through a caller-supplied {@link IDataTable} implemented from
     * scratch, bypassing this module's storage layer entirely - the same shape of cost as the
     * {@code extractRaw}/{@code IDataValue} gap below. DEFERRED for that reason.
     * </p>
     */
    @Test
    void renderNumberOfALongValueRendersAsPlainInteger()
    {
        DataTableColumnMeta numCol = DataTableColumnMeta.builder().index(0).name("N")
                .type(DataValueType.DOUBLE).build();
        IDataTable t = oneColumnTable("T", numCol, 42L);
        assertEquals("42", dataLineOf(CdtWriter.toString(t)));
    }

    // ---- writeColumns: type / length / format / nativeType / generic metadata -------------


    @Test
    void columnTypeAttrOmittedWhenColumnTypeIsNull()
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("X").build();
        IDataTable t = oneColumnTable("T", cm, "v");
        String text = CdtWriter.toString(t);
        assertFalse(colLineOf(text, 0).contains("type="), colLineOf(text, 0));
        // renderValue's aType == null arm renders via plain toString(), not one of the typed
        // kind-specific renderers.
        assertEquals("v", dataLineOf(text));
    }


    @Test
    void columnLengthOmittedWhenZeroEmittedWhenPositive()
    {
        // Column A's value is empty (missing) so reconcileLengths has nothing to widen: a
        // non-empty value would force its own length in, defeating the point of this test.
        CdtDataset ds = CdtParser.parseFirst("""
                dataset T
                col A type=Char
                col B type=Char length=5
                ---
                | y
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertFalse(colLineOf(text, 0).contains("length="), colLineOf(text, 0));
        assertTrue(colLineOf(text, 1).contains("length=5"), colLineOf(text, 1));
    }


    @Test
    void columnFormatOmittedWhenAbsentEmittedWhenPresent()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset T
                col A type=Char
                col B type=Num format=BEST12.
                ---
                x | 5
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertFalse(colLineOf(text, 0).contains("format="), colLineOf(text, 0));
        assertTrue(colLineOf(text, 1).contains("format=BEST12."), colLineOf(text, 1));
    }


    @Test
    void columnNativeTypeOmittedWhenAbsentOrRedundantEmittedWhenDistinct()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset T
                col A type=Char
                col B type=Char nativeType=Char
                col C type=Num nativeType=BEST
                ---
                x | y | 5
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertFalse(colLineOf(text, 0).contains("nativeType="), colLineOf(text, 0));
        assertFalse(colLineOf(text, 1).contains("nativeType="),
                "nativeType equal to the kind token is redundant and must be suppressed: "
                        + colLineOf(text, 1));
        assertTrue(colLineOf(text, 2).contains("nativeType=BEST"), colLineOf(text, 2));
    }


    @Test
    void columnGenericMetadataIsEmittedAsKeyValueAttributes()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset T
                col A type=Char codelist=C66731 role=Covariate
                ---
                x
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertTrue(colLineOf(text, 0).contains("codelist=C66731"), colLineOf(text, 0));
        assertTrue(colLineOf(text, 0).contains("role=Covariate"), colLineOf(text, 0));
    }

    // ---- classifyKind ------------------------------------------------------------------


    @Test
    void classifyKindTokensCoverEveryColumnType()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset T
                col A type=Char
                col B type=Num
                col C type=Date
                col D type=Time
                col E type=DateTime
                ---
                x | 1 | 2020-01-01 | 12:00:00 | 2020-01-01T12:00:00
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertTrue(colLineOf(text, 0).contains("type=Char"), colLineOf(text, 0));
        assertTrue(colLineOf(text, 1).contains("type=Num"), colLineOf(text, 1));
        assertTrue(colLineOf(text, 2).contains("type=Date"), colLineOf(text, 2));
        assertTrue(colLineOf(text, 3).contains("type=Time"), colLineOf(text, 3));
        assertTrue(colLineOf(text, 4).contains("type=DateTime"), colLineOf(text, 4));
    }


    /**
     * A column type outside the CHAR/LONG/DOUBLE family (e.g. BOOLEAN, from a provider CdtWriter
     * did not originate) must still classify as CHAR, not fall through to the numeric branch. Not
     * reachable through this module's own parser (which only ever produces STRING or DOUBLE column
     * types) - CdtWriter is a general {@code IDataTable} serialiser reused outside this module (see
     * README: "the rule-test corpus uses it directly"), so an arbitrary caller's BOOLEAN column is
     * a real input, built directly here.
     */
    @Test
    void nonNumericNonStringColumnTypeClassifiesAsChar()
    {
        // ⛔ ADAPTED for this repository: the internal twin uses DataValueType.BOOLEAN here.
        // It cannot be used downstream, and the reason is the buffer design rather than this
        // writer -- DefaultDataBufferFactory maps BOOLEAN to DataBufferInt (int[], MIN_VALUE
        // missing), which rejects a non-numeric cell outright ("Invalid value: Y"), whereas the
        // internal factor-backed buffer accepts it. OTHER is equally non-numeric and
        // non-STRING, maps to DataBufferObject, and exercises exactly the classification arm
        // this test is about, so the claim under test is unchanged.
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("FLAG")
                .type(DataValueType.OTHER).build();
        IDataTable t = oneColumnTable("T", cm, "Y");
        String text = CdtWriter.toString(t);
        assertTrue(colLineOf(text, 0).contains("type=Char"), colLineOf(text, 0));
    }


    /**
     * The DATE-arm prefix chain is nine {@code startsWith} disjuncts long; the parser only ever
     * auto-derives the plain {@code DATE9.} default, so every other SAS date-format alias
     * (DDMMYY/MMDDYY/YYMMDD/E8601DA/IS8601DA/B8601DA/MONYY/WEEKDATE) went completely unexecuted.
     * These two pick the first and last alias in the chain, which between them execute (not
     * necessarily kill - see the module's pom notes) every disjunct at least once.
     */
    @Test
    void dateFormatAliasDdmmyyClassifiesAsDate()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset T
                col D type=Date format=DDMMYY10.
                ---
                2020-01-15
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertTrue(colLineOf(text, 0).contains("type=Date"), colLineOf(text, 0));
    }


    @Test
    void dateFormatAliasWeekdateClassifiesAsDate()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset T
                col D type=Date format=WEEKDATE9.
                ---
                2020-01-15
                ---
                """, "t");
        IDataTable t = CdtTableBuilder.build(ds, java.net.URI.create("test:t"));
        String text = CdtWriter.toString(t);
        assertTrue(colLineOf(text, 0).contains("type=Date"), colLineOf(text, 0));
    }

    // ---- renderNumber / renderTime boundary completions ------------------------------------


    @Test
    void integralDoubleAtNegativeTwoPow63IsNotSaturated()
    {
        // -2^63 IS exactly representable as a long (Long.MIN_VALUE) - must take the long path.
        // 0x1p63 is the hex spelling of 2^63; the decimal one is the same double but trips
        // [FloatingPointLiteralPrecision], which reads the shortest round-trip form.
        String text = CdtWriter.toString(oneColumnTable("T",
                DataTableColumnMeta.builder().index(0).name("N").type(DataValueType.DOUBLE).build(),
                -0x1p63));
        assertEquals("-9223372036854775808", dataLineOf(text));
    }


    @Test
    void integralDoubleJustBelowNegativeTwoPow63IsNotSaturated()
    {
        double justBelow = -9.223372036854778E18; // < -2^63, not representable as a long
        String text = CdtWriter.toString(oneColumnTable("T",
                DataTableColumnMeta.builder().index(0).name("N").type(DataValueType.DOUBLE).build(),
                justBelow));
        assertFalse(dataLineOf(text).contains("9223372036854775808"), dataLineOf(text));
        assertEquals(Double.toString(justBelow), dataLineOf(text));
    }


    /**
     * F-prov-cdt-05: a DATE value so far outside the representable range that
     * {@code LocalDate.plusDays} itself cannot compute it must fail with a descriptive
     * {@link IOException} - the deliberate export-validation failure this class already uses for
     * TIME (F-prov-04) and control characters - not an unchecked
     * {@link java.time.DateTimeException}.
     */
    @Test
    void extremeDateValueThrowsIOExceptionNotDateTimeException(@TempDir Path tmp) throws IOException
    {
        // Built directly, not through CdtParser: the text format's ISO date syntax cannot even
        // express a value this far out of range, so the only way to reach renderDate's overflow
        // path at all is a table constructed outside this module's own reader.
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("D")
                .type(DataValueType.DOUBLE).displayFormat("DATE9.").build();
        IDataTable t = oneColumnTable("T", cm, 1.0e18);
        Path out = tmp.resolve("d.cdt");
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> CdtWriter.write(t, out));
        assertTrue(ex.getMessage().contains("D"), ex.getMessage());
    }


    @Test
    void extremeDateTimeValueThrowsIOExceptionNotDateTimeException(@TempDir Path tmp)
        throws IOException
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("DT")
                .type(DataValueType.DOUBLE).displayFormat("DATETIME20.").build();
        IDataTable t = oneColumnTable("T", cm, 1.0e18);
        Path out = tmp.resolve("dt.cdt");
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> CdtWriter.write(t, out));
        assertTrue(ex.getMessage().contains("DT"), ex.getMessage());
    }

    // ---- F-prov-cdt-06: CdtLibraryProvider.mapColumns must apply generic attrs too --------


    /**
     * A generic {@code col}-line attribute must be visible the same way regardless of whether the
     * file is opened as a single dataset ({@link CdtTableBuilder}) or as a library
     * ({@link net.cumba.datatable.provider.cdt.library.CdtLibraryProvider}) - both map the same
     * parsed {@link CdtDataset} and must agree. {@code displayFormat} is a typed-field route;
     * {@code role} has no typed field and must land in custom metadata.
     */
    @Test
    void libraryProviderColumnsCarryGenericAttrsLikeTheTableBuilderDoes(@TempDir Path tmp)
        throws IOException
    {
        Path file = write(tmp, "lib.cdt", """
                dataset T
                col AGE type=Num displayFormat=BEST8. role=Covariate
                ---
                5
                ---
                """);
        net.cumba.datatable.provider.cdt.library.CdtLibraryProvider p = new net.cumba.datatable.provider.cdt.library.CdtLibraryProvider();
        net.cumba.datatable.library.IDataTableLibrary lib = p.provide(file.toUri(),
                CdtProviderSupplier.FI_CDT,
                java.util.Map.<net.cumba.datatable.io.Property, String> of());
        net.cumba.datatable.library.ILibraryMember m = p.provideLibraryMembers(lib).findFirst()
                .orElseThrow();
        DataTableColumnMeta col = p.provideLibraryMemberColumns(m).findFirst().orElseThrow();
        assertEquals("BEST8.", col.getDisplayFormat(), "displayFormat is a typed field");
        assertEquals("Covariate", col.getMetaData("role"), "unrecognised keys land in metadata");
    }


    /**
     * ⭐⭐ Q35/G4 — ONE test that kills the whole date-alias OR-chain, instead of nine fixtures.
     *
     * <p>
     * {@code classifyKind}'s DATE arm is {@code if (DATE || DDMMYY || MMDDYY || YYMMDD || E8601DA
     * || IS8601DA || B8601DA || MONYY || WEEKDATE)}. An input matching one alias SHORT-CIRCUITS, so
     * every later arm goes unevaluated — which is why one fixture per alias leaves the rest of the
     * chain untested and why six of these mutants survived. An <b>all-false</b> input is the only
     * input that evaluates EVERY arm, and negating any single arm then flips the whole disjunction
     * to true and returns {@code Date} instead of {@code Num}. So one test covers the entire chain,
     * and it gets stronger as arms are added rather than weaker.
     * </p>
     *
     * <p>
     * ⚠ <b>This is pitest-required and must not be "simplified" into a matching fixture.</b> Its
     * value is precisely that no alias matches. The same shape guards the DATETIME and TIME arms
     * above it — reaching the DATE arm at all requires failing both of those, so this input
     * exercises all three chains in one pass.
     * </p>
     */
    @Test
    void anOrdinaryNumericFormatMatchesNoDateAliasAndStaysNum()
    {
        for (String plainNumeric : java.util.List.of("BEST12.", "F8.2", "COMMA10.2", "PERCENT8.1",
                "Z5.", "DOLLAR9.2"))
        {
            DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("AVAL")
                    .type(DataValueType.DOUBLE).displayFormat(plainNumeric).build();
            String text = CdtWriter.toString(oneColumnTable("T", cm, 1.0));
            String colLine = text.split("\n", -1)[1];
            assertTrue(colLine.contains("type=Num"),
                    plainNumeric + " must classify as Num: " + colLine);
            assertFalse(colLine.contains("type=Date"),
                    plainNumeric + " must not be claimed by a date alias: " + colLine);
        }
    }


    /**
     * The counterpart to the all-false test above, and it pins a DIFFERENT property. The all-false
     * input kills the chain's mutants; it cannot notice an alias being <b>deleted</b> from the
     * chain, because no mutator removes a disjunct. This loop asserts each alias still classifies
     * as {@code Date} — one test over nine aliases, not nine near-identical fixtures.
     *
     * <p>
     * ⚠ {@code DATEAMPM} is deliberately absent: it starts with {@code DATE} but is a SAS
     * <em>datetime</em> format and is claimed by the arm above (F-prov-05). It is asserted here as
     * DateTime precisely because the prefix overlap is the trap.
     * </p>
     */
    @Test
    void everyDateAliasStillClassifiesAsDate()
    {
        for (String alias : java.util.List.of("DATE9.", "DDMMYY10.", "MMDDYY10.", "YYMMDD10.",
                "E8601DA10.", "IS8601DA10.", "B8601DA10.", "MONYY7.", "WEEKDATE29."))
        {
            DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("D")
                    .type(DataValueType.DOUBLE).displayFormat(alias).build();
            String colLine = CdtWriter.toString(oneColumnTable("T", cm, 0.0)).split("\n", -1)[1];
            assertTrue(colLine.contains("type=Date"), alias + " must stay a date: " + colLine);
        }
        DataTableColumnMeta ampm = DataTableColumnMeta.builder().index(0).name("D")
                .type(DataValueType.DOUBLE).displayFormat("DATEAMPM13.").build();
        String ampmLine = CdtWriter.toString(oneColumnTable("T", ampm, 0.0)).split("\n", -1)[1];
        assertTrue(ampmLine.contains("type=DateTime"),
                "DATEAMPM starts with DATE but is a datetime format: " + ampmLine);
    }


    private static Path write(Path aDir, String aName, String aContent) throws IOException
    {
        Path p = aDir.resolve(aName);
        Files.writeString(p, aContent, StandardCharsets.UTF_8);
        return p;
    }


    @Test
    void timeOfExactlyOneDayIsRefusedAtTheUpperBoundary(@TempDir Path tmp) throws IOException
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("ELAPSED")
                .type(DataValueType.DOUBLE).displayFormat("TIME8.").build();
        IDataTable t = oneColumnTable("T", cm, 86400.0);
        Path out = tmp.resolve("t.cdt");
        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> CdtWriter.write(t, out));
        assertTrue(ex.getMessage().contains("86400"), ex.getMessage());
    }

    // ---- write(...): append mode ----------------------------------------------------------


    @Test
    void appendToExistingFileInsertsBlankLineSeparatorAndKeepsBothBlocks(@TempDir Path tmp)
        throws IOException
    {
        Path out = tmp.resolve("multi.cdt");
        CdtWriter.write(oneCharColumnTable("A", "X", "a"), out);
        CdtWriter.write(oneCharColumnTable("B", "Y", "b"), null, null, null, out, true);

        String content = Files.readString(out, StandardCharsets.UTF_8);
        String expected = "dataset A\ncol X type=Char\n---\na\n---\n" + "\n"
                + "dataset B\ncol Y type=Char\n---\nb\n---\n";
        assertEquals(expected, content);
    }


    @Test
    void appendFlagWithNoExistingFileBehavesLikeAFreshWrite(@TempDir Path tmp) throws IOException
    {
        Path out = tmp.resolve("fresh.cdt");
        CdtWriter.write(oneCharColumnTable("A", "X", "a"), null, null, null, out, true);
        String content = Files.readString(out, StandardCharsets.UTF_8);
        assertEquals("dataset A\ncol X type=Char\n---\na\n---\n", content);
    }


    @Test
    void appendFalseOverwritesAnExistingFileInsteadOfAppending(@TempDir Path tmp) throws IOException
    {
        Path out = tmp.resolve("overwrite.cdt");
        CdtWriter.write(oneCharColumnTable("A", "X", "a"), out);
        CdtWriter.write(oneCharColumnTable("B", "Y", "b"), out);
        String content = Files.readString(out, StandardCharsets.UTF_8);
        assertEquals("dataset B\ncol Y type=Char\n---\nb\n---\n", content);
    }
}
