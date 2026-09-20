package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code .cdt} missing-value sentinel, added 2026-09-17.
 *
 * <p>
 * Before this, {@code .cdt} could not express a missing CHARACTER value at all (an unquoted
 * {@code .} was collapsed to {@code ""} for every column type) and could not express a SAS
 * <em>special</em> missing for ANY column type ({@code .A} was the literal two-character string on
 * a Char column and a hard parse error on a numeric one). The capability is <b>additive</b>:
 * </p>
 *
 * <ul>
 * <li>a <b>blank</b> field keeps its historical meaning exactly — {@code ""} for CHAR (SAS
 * character data has no missing value, only the empty string, and every authored fixture in the
 * corpora was written against that reading) and {@link MissingValue#MIS} for the numeric family;
 * <li>an <b>unquoted</b> {@code .}, {@code ._} or {@code .A}..{@code .Z} is the missing sentinel,
 * uniformly across all column types;
 * <li>a <b>quoted</b> {@code "."} / {@code "._"} / {@code ".A"} is the literal string — the escape
 * hatch, which is also what the writer emits for such a value.
 * </ul>
 */
class CdtMissingValueTest
{

    private static final URI URI_T = URI.create("test:t");

    /** A two-column (Char, Num) dataset whose single row holds {@code aField} in both columns. */
    private static IDataTable twoColumnRow(String aField)
    {
        return tableOf("""
                dataset T
                col C type=Char
                col N type=Num
                ---
                %s | %s
                ---
                """.formatted(aField, aField));
    }


    private static IDataTable tableOf(String aContent)
    {
        return CdtTableBuilder.build(CdtParser.parseFirst(aContent, "t"), URI_T);
    }


    /** A one-row table with one Char and one Num column holding the given values. */
    private static IDataTable rowTable(List<Object> aCharValues, List<Object> aNumValues)
    {
        DataTableColumnMeta c = DataTableColumnMeta.builder().index(0).name("C")
                .type(DataValueType.STRING).build();
        DataTableColumnMeta n = DataTableColumnMeta.builder().index(1).name("N")
                .type(DataValueType.DOUBLE).build();
        DataTableMeta md = DataTableMeta.builder().name("T").rowCount(aCharValues.size())
                .totalRowCount(aCharValues.size()).setColumns(c, n).build();
        CachedDataTableColumn cc = new CachedDataTableColumn(0, DataValueType.STRING);
        CachedDataTableColumn nc = new CachedDataTableColumn(1, DataValueType.DOUBLE);
        for (int i = 0; i < aCharValues.size(); i++)
        {
            cc.addElement(aCharValues.get(i));
            nc.addElement(aNumValues.get(i));
        }
        cc.complete();
        nc.complete();
        return new ColumnCachedDataTable(md, cc, nc);
    }

    // ---- the sentinel grammar -------------------------------------------------------------


    /**
     * Every one of the 28 SAS missing forms resolves, on a Char column <b>and</b> on a numeric one.
     * On Char every one of them used to be a literal string; on Num all but {@code .} used to be a
     * {@link CdtParseException}.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = MissingValue.class, names =
    {
            "MIS", "MIS__", "MIS_A", "MIS_B", "MIS_C", "MIS_D", "MIS_E", "MIS_F", "MIS_G", "MIS_H",
            "MIS_I", "MIS_J", "MIS_K", "MIS_L", "MIS_M", "MIS_N", "MIS_O", "MIS_P", "MIS_Q",
            "MIS_R", "MIS_S", "MIS_T", "MIS_U", "MIS_V", "MIS_W", "MIS_X", "MIS_Y", "MIS_Z"
    })
    void everySasMissingFormIsASentinelInEveryColumnType(MissingValue aMissing)
    {
        IDataTable t = twoColumnRow(aMissing.getDisplayString());
        assertEquals(aMissing, t.getValue(0, 0), "Char column");
        assertEquals(aMissing, t.getValue(0, 1), "Num column");

        assertEquals(aMissing, CdtValues.parseValue(aMissing.getDisplayString(), CdtType.CHAR));
        assertEquals(aMissing, CdtValues.parseValue(aMissing.getDisplayString(), CdtType.NUM));
        assertEquals(aMissing, CdtValues.parseValue(aMissing.getDisplayString(), CdtType.DATE));
        assertEquals(aMissing, CdtValues.parseValue(aMissing.getDisplayString(), CdtType.TIME));
        assertEquals(aMissing, CdtValues.parseValue(aMissing.getDisplayString(), CdtType.DATETIME));
    }


    /**
     * The sentinel grammar is {@code ^\.([_A-Z])?$} and the enum covers it exactly: 28 forms, no
     * gap that would fall through to "ordinary data" and no form outside it that
     * {@link CdtValues#isMissingSentinel} would accept.
     */
    @Test
    void theGrammarAndTheEnumAgreeExactly()
    {
        List<MissingValue> sentinels = new ArrayList<>();
        for (MissingValue mv : MissingValue.values())
        {
            if (CdtValues.isMissingSentinel(mv.getDisplayString()))
            {
                sentinels.add(mv);
            }
        }
        assertEquals(28, sentinels.size(), "1 (.) + 1 (._) + 26 (.A-.Z)");
        for (MissingValue mv : sentinels)
        {
            assertEquals(mv, CdtValues.missingSentinel(mv.getDisplayString()));
        }
    }


    /**
     * ⛔ THE TRAP. {@link MissingValue} also carries non-SAS display strings, and a bare
     * {@code MissingValue.forValue(raw)} would swallow legitimate data. {@code NA} in particular is
     * a real CDISC null-flavour code that occurs as character data in the rule corpus
     * ({@code PMDA-SD2017-valid-TS.cdt} has {@code TSVALNF = NA}); reading it as missing would
     * silently destroy it. None of these may be a sentinel.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "NA", "None", "np.nan", "pd.NA", "pd.NaT", "<UKN>", "<ERR>"
    })
    void nonSasMissingDisplayStringsAreOrdinaryCharacterData(String aValue)
    {
        assertFalse(CdtValues.isMissingSentinel(aValue), aValue + " must not be a sentinel");
        assertEquals(aValue, CdtValues.parseValue(aValue, CdtType.CHAR));
        assertEquals(aValue, tableOf("""
                dataset T
                col C type=Char
                ---
                %s
                ---
                """.formatted(aValue)).getValue(0, 0));
    }


    /**
     * Dot-shaped but not a SAS form: lower case, two letters, two dots, a trailing digit. All
     * ordinary character data, exactly as before the sentinel existed.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            ".a", ".AB", "..", ".1", ".Z9", "a.", "-."
    })
    void dotShapedNonSasTextIsOrdinaryCharacterData(String aValue)
    {
        assertFalse(CdtValues.isMissingSentinel(aValue), aValue + " must not be a sentinel");
        // Unquoted AND quoted: neither reading turns it into a missing value.
        assertEquals(aValue,
                CdtValues.parseValue(CdtValues.encodeField(aValue, false), CdtType.CHAR));
        assertEquals(aValue,
                CdtValues.parseValue(CdtValues.encodeField(aValue, true), CdtType.CHAR));
    }

    // ---- the blank field does NOT move ------------------------------------------------------


    /**
     * ⭐ The contract the owner pinned on 2026-09-17: a blank Char field is the EMPTY STRING, not
     * missing, because all rules run on SAS-based data which cannot express a missing character
     * value. ~2 640 blank-Char fixture cells depend on this and none of them was edited.
     */
    @Test
    void blankFieldKeepsItsHistoricalMeaning()
    {
        IDataTable t = tableOf("""
                dataset T
                col C type=Char
                col N type=Num
                ---
                 |
                ---
                """);
        assertEquals("", t.getValue(0, 0), "a blank Char field is the empty string, NOT missing");
        assertEquals(MissingValue.MIS, t.getValue(0, 1));

        assertEquals("", CdtValues.parseValue("", CdtType.CHAR));
        assertEquals("", CdtValues.missingFor(CdtType.CHAR));
        assertEquals(MissingValue.MIS, CdtValues.missingFor(CdtType.NUM));
    }


    /**
     * The empty string and the missing value are now DISTINCT on a Char column — which is the whole
     * point of the change, and was impossible to express before it.
     */
    @Test
    void emptyStringAndMissingAreDistinctOnACharColumn()
    {
        IDataTable t = tableOf("""
                dataset T
                col C type=Char
                ---
                .
                ""
                ---
                """);
        assertEquals(2L, t.getRowCount());
        assertEquals(MissingValue.MIS, t.getValue(0, 0));
        assertEquals("", t.getValue(1, 0));
    }

    // ---- the quoted escape hatch -------------------------------------------------------------


    @ParameterizedTest
    @ValueSource(strings =
    {
            ".", "._", ".A", ".Z"
    })
    void quotedSentinelIsTheLiteralString(String aValue)
    {
        IDataTable t = tableOf("""
                dataset T
                col C type=Char
                ---
                "%s"
                ---
                """.formatted(aValue));
        assertEquals(aValue, t.getValue(0, 0));
    }


    /**
     * The encoding that carries the "was it quoted?" bit through {@code List<List<String>>} is
     * injective, including for a literal that already starts with the escape character.
     */
    @Test
    void theFieldEncodingIsInjective()
    {
        assertEquals(".", CdtValues.encodeField(".", false), "an unquoted sentinel stays bare");
        assertEquals("\\.", CdtValues.encodeField(".", true), "a quoted sentinel is escaped");
        assertEquals("\\\\.", CdtValues.encodeField("\\.", true), "an escaped-looking literal too");
        assertEquals("plain", CdtValues.encodeField("plain", true), "ordinary data is untouched");
        assertEquals("\\x", CdtValues.encodeField("\\x", true),
                "a backslash that is not an escape");
        // ⭐ The two end-of-string boundaries of the escape-run scan (review N5). A lone backslash
        // has nothing after it, and a double backslash ends in a sentinel only after TWO strips —
        // both are exactly where an off-by-one in
        // `while (i < aText.length() && aText.charAt(i) == ESCAPE)` would live.
        assertEquals("\\", CdtValues.encodeField("\\", true),
                "a lone backslash is not an escape run");
        assertEquals("\\\\\\.", CdtValues.encodeField("\\\\.", true),
                "a double-backslash literal gains exactly one more");

        assertEquals(MissingValue.MIS,
                CdtValues.parseValue(CdtValues.encodeField(".", false), CdtType.CHAR));
        assertEquals(".", CdtValues.parseValue(CdtValues.encodeField(".", true), CdtType.CHAR));
        assertEquals("\\.", CdtValues.parseValue(CdtValues.encodeField("\\.", true), CdtType.CHAR));
        assertEquals("\\", CdtValues.parseValue(CdtValues.encodeField("\\", true), CdtType.CHAR),
                "a lone backslash survives the round trip unchanged");
        assertEquals("\\\\.",
                CdtValues.parseValue(CdtValues.encodeField("\\\\.", true), CdtType.CHAR),
                "and so does a double-backslash literal");
        assertEquals("\\x", CdtValues.parseValue(CdtValues.encodeField("\\x", true), CdtType.CHAR));
    }


    /** A quoted "." on a numeric column has no literal reading, so it stays missing as before. */
    @Test
    void quotedDotOnANumericColumnIsStillMissing()
    {
        assertEquals(MissingValue.MIS,
                CdtValues.parseValue(CdtValues.encodeField(".", true), CdtType.NUM));
    }

    // ---- the all-missing row sentinel ---------------------------------------------------------


    /**
     * A line that is a lone {@code .} is the all-missing row sentinel. It now means
     * {@link MissingValue#MIS} in every column type, Char included — it used to mean {@code ""}
     * there, which is the empty string, not missing. Measured 2026-09-17: ZERO {@code .cdt}
     * fixtures in the stack use it, so nothing moved.
     */
    @Test
    void theAllMissingRowSentinelIsMissingInEveryColumnType()
    {
        IDataTable t = tableOf("""
                dataset T
                col C type=Char
                col N type=Num
                ---
                .
                ---
                """);
        assertEquals(MissingValue.MIS, t.getValue(0, 0));
        assertEquals(MissingValue.MIS, t.getValue(0, 1));
    }

    // ---- round trip ---------------------------------------------------------------------------


    /**
     * write → read → identical, for the three things that were previously indistinguishable or
     * unrepresentable: the empty string, every SAS missing, and a literal dot-shaped string.
     *
     * <p>
     * ⚠ This is the one place a reader-writer agreement test is the right oracle: round-trip
     * fidelity IS the property under test, not a substitute for checking the writer's literal
     * output (which {@link CdtWriterSpecTest} does).
     * </p>
     */
    @Test
    void everyRepresentableCharValueRoundTrips()
    {
        List<Object> chars = new ArrayList<>(
                List.of("", "plain", ".", "._", ".A", "NA", "\\.", " lead"));
        for (MissingValue mv : MissingValue.values())
        {
            if (CdtValues.isMissingSentinel(mv.getDisplayString()))
            {
                chars.add(mv);
            }
        }
        List<Object> nums = new ArrayList<>();
        for (int i = 0; i < chars.size(); i++)
        {
            nums.add(i % 2 == 0 ? Double.valueOf(i) : MissingValue.MIS_B);
        }

        String text = CdtWriter.toString(rowTable(chars, nums));
        IDataTable back = tableOf(text);

        assertEquals(chars.size(), (int) back.getRowCount(), text);
        for (int i = 0; i < chars.size(); i++)
        {
            assertEquals(chars.get(i), back.getValue(i, 0), "row " + i + " of:\n" + text);
            assertEquals(nums.get(i), back.getValue(i, 1), "row " + i + " of:\n" + text);
        }
    }


    /**
     * A single-column table is the ambiguous case: its data line has no {@code " | "} separator, so
     * an empty field would be an empty line, which the parser skips. Both an empty string and a
     * missing value must survive it.
     */
    @Test
    void singleCharColumnRoundTripsBothEmptyStringAndMissing()
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("C")
                .type(DataValueType.STRING).build();
        DataTableMeta md = DataTableMeta.builder().name("T").rowCount(3).totalRowCount(3)
                .setColumns(cm).build();
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("");
        col.addElement(MissingValue.MIS);
        col.addElement(".");
        col.complete();

        String text = CdtWriter.toString(new ColumnCachedDataTable(md, col));
        IDataTable back = tableOf(text);
        assertEquals(3L, back.getRowCount(), text);
        assertEquals("", back.getValue(0, 0), text);
        assertEquals(MissingValue.MIS, back.getValue(1, 0), text);
        assertEquals(".", back.getValue(2, 0), text);
    }


    /**
     * A missing value in a STRING column survives the whole storage path — the factor buffer and
     * its {@code complete()} compression included. This is the path Dataset-JSON already uses
     * ({@code DsjTableProvider} stores {@link MissingValue#MIS} for a JSON {@code null} in a
     * character column); until now {@code .cdt} was the only provider that could not reach it.
     */
    @Test
    void aMissingCharValueSurvivesTheColumnStorage()
    {
        IDataTable t = tableOf("""
                dataset T
                col C type=Char
                ---
                .A
                ---
                """);
        assertTrue(t.getValue(0, 0) instanceof MissingValue,
                "a Char column must be able to hold a MissingValue, not stringify it");
        assertEquals(MissingValue.MIS_A, t.getValue(0, 0));
    }
}
