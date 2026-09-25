package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers numeric-parsing and defensive-fallback code paths in
 * {@code DsjTableProvider.DsjTableDataParser.addData2Column} / {@code addParsedDoubleOrError} that
 * had zero mutation coverage, plus two confirmed-and-fixed defects an adversarial review of this
 * class turned up while investigating them:
 *
 * <ul>
 * <li><b>Non-finite numeric values must not be stored as real data (FIXED).</b> A raw JSON number
 * token can legitimately overflow {@code double} (e.g. {@code 1e400}, which {@code
 * JsonParser.getDoubleValue()} silently turns into {@code Double.POSITIVE_INFINITY} with no
 * exception) and several {@code IDataTypeMapper} implementations return {@code Double.NaN} as their
 * internal "could not map this value" sentinel. The Number-branch used to guard only against
 * {@code NaN}, storing an overflowed {@code Infinity} as a literal value; it now checks
 * {@code !Double.isFinite(dbl)} and reports {@link MissingValue#MIS_ERROR} (a value was present but
 * could not be represented) rather than {@link MissingValue#MIS} (the spec's "missing" is a real
 * JSON {@code null}, handled earlier, before any mapper runs) -- so a mapper's NaN sentinel for a
 * genuine data error (e.g. an unparseable decimal string) is no longer indistinguishable from a
 * real missing value. {@code addParsedDoubleOrError} (the lenient, no-{@code
 * targetDataType} numeric-string path) had the same hole for a "NaN"/"Infinity"/overflowing numeric
 * string and is fixed the same way.</li>
 * <li><b>A raw JSON number in a nominally STRING column is now normalised to text (FIXED, LOW).</b>
 * A non-conformant document can carry an unquoted JSON number in a {@code
 * dataType="string"} column; the Number-branch used to store the raw {@code Number} object
 * directly, so the same logical value split into two RAW-VALUE-unequal cells depending on whether
 * it happened to arrive quoted or bare -- exactly the raw-identity hazard the null-vs-"" note
 * elsewhere in this class already documents (merge/join keys, by-group hashing, frequency buckets).
 * Fixed to store {@code num.toString()}.</li>
 * <li><b>Thousand-separator decimal strings — BOTH paths, 2026-09-21.</b> The spec's
 * {@code dataType} note: "When a thousand separator is used in a decimal represented as string, the
 * comma is used." A comma is therefore a THOUSANDS separator and never a decimal separator, and one
 * that is not in a valid grouping position is a format violation.
 * <p>
 * ⛔⛔ <b>This bullet used to say the opposite of all four of its own claims, and review round 2
 * caught it 20 lines above the assertion that contradicts it.</b> It read <i>"fix, lenient path
 * only"</i>, <i>"now strips commas before parsing"</i>, <i>"{@code "1,234.5"} there currently still
 * silently becomes {@code Double.NaN}"</i> and <i>"this lane reports rather than fixes it
 * there"</i>. As of {@code plans/PLAN-dsj-thousand-separator.md}: the fix covers BOTH paths; the
 * lenient path no longer strips unconditionally (that WAS the defect — {@code "1,5"} became 15.0, a
 * ten-fold error in a clinical value with no failure signal); {@code DecimalMapper} answers 1234.5
 * for {@code "1,234.5"} where it used to degrade to {@link MissingValue#MIS_ERROR}; and that module
 * was fixed in the same change, not reported.
 * </p>
 * <p>
 * ⚑ Kept as a worked example rather than replaced silently: prose describing a defect outlives the
 * defect, and a stale NOTE in a file the same diff edits is indistinguishable from a live one.
 * </p>
 * </li>
 * <li>The decimal-as-string representation for a properly-mapped {@code targetDataType=decimal}
 * column (CDISC Dataset-JSON v1.1 "Decimal Variables") -- confirmed via review that this actually
 * routes through {@code DataTypeMapperFactory}'s {@code DecimalMapper}, which converts the JSON
 * string to a {@code Double} BEFORE {@code addData2Column} ever sees it, so it exercises the
 * Number-branch, not {@code addParsedDoubleOrError} (that method is reached only via {@code
 * NoMapper}, i.e. no {@code targetDataType} -- a leniency path for non-conformant files).</li>
 * <li>The defensive "unexpected value" branch — the spec's "Supported Column Data Type
 * Combinations" note states the row-level JSON data type is only ever
 * {@code string, integer, boolean, number, null} — "It does not include array or object" — but the
 * parser's own {@code parseAsValue}/{@code parseValueToSetter} dispatch a {@code START_OBJECT} /
 * {@code START_ARRAY} token through {@code parseObject}/{@code parseArray} rather than rejecting
 * them, so a non-conformant document CAN legitimately hand this provider a nested object/array cell
 * value. The provider's fallback must degrade to {@link MissingValue#MIS_UNKNOWN} rather than
 * throwing or corrupting the column.</li>
 * </ul>
 */
class DsjTableProviderNumericAndMalformedValueTest
{

    private static java.net.URI writeJson(Path aDir, String aName, String aJson) throws IOException
    {
        Path f = aDir.resolve(aName);
        Files.writeString(f, aJson, StandardCharsets.UTF_8);
        return f.toUri();
    }

    // ---- targetDataType=decimal: routed through DecimalMapper, exercises the Number-branch --


    @Test
    void mappedDecimalAsStringIsParsedWithoutError(@TempDir Path tmp) throws IOException
    {
        // Fixture mirrors the spec's own "Decimal Variables" example verbatim (BMIBL column).
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.DEC",
                  "name": "DEC",
                  "label": "Decimal-as-string",
                  "records": 4,
                  "columns": [
                    {"itemOID": "IT.ADSL.BMIBL", "name": "BMIBL", "label": "Baseline BMI (kg/m^2)",
                     "dataType": "decimal", "targetDataType": "decimal", "length": 16}
                  ],
                  "rows": [
                    ["30.8983333232059"],
                    ["28.977529926378"],
                    ["1,234.5"],
                    ["1,5"]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "decimal.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(4, table.getRowCount());
        assertEquals(30.8983333232059, (double) table.getValue(0, 0), 1e-12);
        assertEquals(28.977529926378, (double) table.getValue(1, 0), 1e-12);
        // ⭐ Defect B, end to end. The mapper unit test in cumba-cdisc-dsj pins the conversion;
        // this pins that a spec-legal thousands separator survives the WHOLE provider path on a
        // targetDataType=decimal column. Review round 1: the accept direction existed nowhere in
        // either datatable repo, while the reject direction was covered only by accident.
        assertEquals(1234.5, (double) table.getValue(2, 0), 1e-12);
        // ⭐ and the STRICT REJECT direction, directly rather than transitively (review round 2):
        // "1,5" on a targetDataType=decimal column. It was covered only as a composition of the
        // cdisc-dsj unit test ("1,5" -> NaN) and mappedUnparseableDecimalStringBecomesMisError
        // (NaN -> MIS_ERROR). Both links held, but this is the clinically important direction.
        assertEquals(MissingValue.MIS_ERROR, table.getValue(3, 0),
                "a misplaced thousands separator must not survive the strict path as a number");
    }


    /**
     * A boolean column is backed by a {@code DataBufferInt}. Before
     * PLAN-oss-numeric-buffer-missing-identity every missing it held read back as
     * {@link MissingValue#MIS}; each now keeps its identity. An integer cell is a type violation in
     * a boolean column and reads {@link MissingValue#MIS_ERROR}
     * (PLAN-oss-dsj-malformed-boolean-cell; it read as a present {@code Integer.MIN_VALUE} before
     * that plan).
     */
    @Test
    void booleanColumnKeepsEachMissingsIdentity(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-25T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BOO",
                  "name": "BOO",
                  "label": "Boolean column with malformed cells",
                  "records": 5,
                  "columns": [
                    {"itemOID": "IT.BOO.FLAG", "name": "FLAG", "label": "Flag",
                     "dataType": "boolean"}
                  ],
                  "rows": [
                    [true],
                    [1e400],
                    [[1, 2]],
                    [-2147483648],
                    [null]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "boolean.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(5, table.getRowCount());
        assertEquals(1L, table.getValue(0, 0));
        // 1e400 overflows to Infinity: a present value that cannot be represented.
        assertEquals(MissingValue.MIS_ERROR, table.getValue(1, 0));
        // A nested array is outside the spec's row types: the defensive fallback.
        assertEquals(MissingValue.MIS_UNKNOWN, table.getValue(2, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(3, 0));
        assertEquals(MissingValue.MIS, table.getValue(4, 0));
    }


    /**
     * A cell whose JSON type violates its column type is {@link MissingValue#MIS_ERROR} in that
     * cell, and the rest of the dataset loads (owner ruling 2026-09-25,
     * PLAN-oss-dsj-malformed-boolean-cell). The spec: "The boolean data type in JSON only supports
     * true and false" -- a "Y"/"N" flag belongs in a string column, so no leniency. Before the fix
     * the first bad cell made the whole load fail with an IllegalStateException.
     */
    @Test
    void booleanColumnViolationsAreMisErrorNotALoadFailure(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-25T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BOOV",
                  "name": "BOOV",
                  "label": "Boolean column with type violations",
                  "records": 7,
                  "columns": [
                    {"itemOID": "IT.BOOV.FLAG", "name": "FLAG", "label": "Flag",
                     "dataType": "boolean"}
                  ],
                  "rows": [
                    ["Y"],
                    ["true"],
                    [0.5],
                    [3000000000],
                    [1],
                    [true],
                    [null]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "booleanviolations.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(7, table.getRowCount());
        for (int row = 0; row < 5; row++)
        {
            assertEquals(MissingValue.MIS_ERROR, table.getValue(row, 0), "row " + row);
        }
        assertEquals(true, table.getDataValue(5, 0).getValue());
        assertEquals(MissingValue.MIS, table.getValue(6, 0));
    }


    /**
     * A decimal (DOUBLE) column: a JSON boolean is a type violation, and a JSON integer a double
     * cannot hold exactly is a present value that cannot be represented -- both MIS_ERROR, the same
     * outcome as an overflowing number. Before the fix the boolean failed the load in both twins,
     * and 2^53 + 1 failed the OSS load and was silently rounded internally.
     */
    @Test
    void doubleColumnViolationsAreMisErrorNotALoadFailure(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-25T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.DBLV",
                  "name": "DBLV",
                  "label": "Double column with type violations",
                  "records": 9,
                  "columns": [
                    {"itemOID": "IT.DBLV.VAL", "name": "VAL", "label": "Value",
                     "dataType": "double"}
                  ],
                  "rows": [
                    [true],
                    [9007199254740993],
                    [9007199254740992],
                    [1.5],
                    [9223372036854775807],
                    [-9007199254740993],
                    [99999999999999999999],
                    [100000000000000000000],
                    [1180591620717411303424]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "doubleviolations.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(9, table.getRowCount());
        assertEquals(MissingValue.MIS_ERROR, table.getValue(0, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(1, 0));
        assertEquals(9007199254740992.0, table.getDataValue(2, 0).getValueAsDouble());
        assertEquals(1.5, table.getDataValue(3, 0).getValueAsDouble());
        // Long.MAX_VALUE converts to 2^63 -- the (long) saturation would call it exact.
        assertEquals(MissingValue.MIS_ERROR, table.getValue(4, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(5, 0));
        // Beyond long range the parser hands over a BigInteger: inexact is MIS_ERROR, exact is
        // kept.
        assertEquals(MissingValue.MIS_ERROR, table.getValue(6, 0));
        assertEquals(1e20, table.getDataValue(7, 0).getValueAsDouble());
        // 2^70 is exact in a double, but its shortest decimal form is not: the comparison must use
        // the double's exact binary value.
        assertEquals(0x1p70, table.getDataValue(8, 0).getValueAsDouble());
    }


    /**
     * A JSON null in a targetDataType=decimal column is MIS, not MIS_ERROR: DecimalMapper answers
     * NaN for null, and the numeric arm read that NaN as an unrepresentable value -- a genuine
     * missing reported as an error. And a JSON integer the double cannot hold is MIS_ERROR here
     * too, although DecimalMapper hands back an already-rounded Double.
     */
    @Test
    void decimalTargetColumnNullIsMisAndInexactIntegerIsMisError(@TempDir Path tmp)
        throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-25T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.DECN",
                  "name": "DECN",
                  "label": "Decimal target with null",
                  "records": 4,
                  "columns": [
                    {"itemOID": "IT.DECN.V", "name": "V", "label": "V",
                     "dataType": "decimal", "targetDataType": "decimal"}
                  ],
                  "rows": [
                    [null],
                    ["1.5"],
                    ["not-a-number"],
                    [9007199254740993]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "decimalnull.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(MissingValue.MIS, table.getValue(0, 0));
        assertEquals(1.5, table.getDataValue(1, 0).getValueAsDouble());
        assertEquals(MissingValue.MIS_ERROR, table.getValue(2, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(3, 0));
    }


    /**
     * A date column mapped to a SAS date: a present value the mapper cannot read is a format
     * violation (MIS_ERROR); a blank string and a null stay missing (MIS).
     */
    @Test
    void unmappableDateIsMisErrorAndBlankStaysMissing(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-25T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.DTV",
                  "name": "DTV",
                  "label": "Date column with violations",
                  "records": 5,
                  "columns": [
                    {"itemOID": "IT.DTV.DT", "name": "DT", "label": "Date",
                     "dataType": "date", "targetDataType": "integer"}
                  ],
                  "rows": [
                    ["1960-01-02"],
                    ["2025-13-45"],
                    [""],
                    [null],
                    [123]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "dateviolations.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(5, table.getRowCount());
        assertEquals(1.0, table.getDataValue(0, 0).getValueAsDouble());
        assertEquals(MissingValue.MIS_ERROR, table.getValue(1, 0));
        assertEquals(MissingValue.MIS, table.getValue(2, 0));
        assertEquals(MissingValue.MIS, table.getValue(3, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(4, 0));
    }


    /** An integer beyond long range in a string column is its text, not a failed load. */
    @Test
    void integerBeyondLongInAStringColumnIsItsText(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-25T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BIGS",
                  "name": "BIGS",
                  "label": "Big integer in a string column",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.BIGS.ID", "name": "ID", "label": "Id", "dataType": "string"}
                  ],
                  "rows": [
                    [99999999999999999999]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "bigstring.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals("99999999999999999999", table.getValue(0, 0));
    }


    @Test
    void mappedUnparseableDecimalStringBecomesMisError(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.DEC2",
                  "name": "DEC2",
                  "label": "Unparseable decimal string",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.ADSL.BMIBL", "name": "BMIBL", "label": "Baseline BMI (kg/m^2)",
                     "dataType": "decimal", "targetDataType": "decimal", "length": 16}
                  ],
                  "rows": [
                    ["not-a-number"]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "decimalbad.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        // Before the Number-branch fix, DecimalMapper's NaN sentinel for "could not parse" was
        // folded into MissingValue.MIS (indistinguishable from a genuine null) -- it must now
        // read as MIS_ERROR (a real data error, distinct from a declared missing value).
        assertInstanceOf(MissingValue.class, table.getValue(0, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(0, 0));
    }

    // ---- dataType=decimal, NO targetDataType: the lenient NoMapper path (addParsedDoubleOrError)


    @Test
    void lenientDecimalStringWithoutTargetDataTypeIsParsed(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.LEN",
                  "name": "LEN",
                  "label": "Lenient decimal string, no targetDataType",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.VAL", "name": "VAL", "label": "Value", "dataType": "decimal"}
                  ],
                  "rows": [
                    ["1234.5"]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "lenient.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(1234.5, (double) table.getValue(0, 0), 1e-12);
    }


    @Test
    void thousandSeparatorInLenientDecimalStringIsAccepted(@TempDir Path tmp) throws IOException
    {
        // Spec "dataType" note: "When a thousand separator is used in a decimal represented as
        // string, the comma is used."
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.LEN2",
                  "name": "LEN2",
                  "label": "Thousand separator",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.VAL", "name": "VAL", "label": "Value", "dataType": "decimal"}
                  ],
                  "rows": [
                    ["1,234.5"]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "thousands.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(1234.5, (double) table.getValue(0, 0), 1e-12);
    }


    /**
     * ⛔⛔ DEFECT A, closed 2026-09-21 — the reason this whole plan exists.
     *
     * <p>
     * This path used to strip commas <b>unconditionally</b>, so {@code "1,5"} parsed as
     * {@code 15.0}: a <b>ten-fold</b> error in a clinical value, with no failure signal at all. Not
     * a {@code MissingValue}, not an exception — a plausible number that nothing downstream could
     * detect. A comma that is not a valid thousands separator is a format violation and takes the
     * same {@code MIS_ERROR} outcome as any other unreadable value (owner, 2026-09-21).
     * </p>
     *
     * <p>
     * ⚠ It is emphatically NOT read as a European decimal comma either: the answer is neither
     * {@code 15.0} nor {@code 1.5}. The engine does not guess at intent.
     * </p>
     *
     * @param tmp
     *            a temporary directory.
     * @throws IOException
     *             if the fixture cannot be written.
     */
    @Test
    void misplacedThousandSeparatorInLenientDecimalStringBecomesMisError(@TempDir Path tmp)
        throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.LEN3",
                  "name": "LEN3",
                  "label": "Misplaced separator",
                  "records": 6,
                  "columns": [
                    {"itemOID": "IT.VAL", "name": "VAL", "label": "Value", "dataType": "decimal"}
                  ],
                  "rows": [
                    ["1,5"],
                    ["1,23"],
                    ["1,2345"],
                    ["1.234,5"],
                    ["0,123"],
                    ["1,234.5,6"]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "misplaced.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        for (int row = 0; row < 6; row++)
        {
            assertEquals(MissingValue.MIS_ERROR, table.getValue(row, 0),
                    "row " + row + " must be MIS_ERROR, never a guessed number");
        }
    }


    @Test
    void unparseableLenientDecimalStringBecomesMisError(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.LEN3",
                  "name": "LEN3",
                  "label": "Unparseable, no targetDataType",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.VAL", "name": "VAL", "label": "Value", "dataType": "decimal"}
                  ],
                  "rows": [
                    ["not-a-number"]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "lenientbad.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(MissingValue.MIS_ERROR, table.getValue(0, 0));
    }


    @Test
    void nonFiniteLenientNumericStringBecomesMisErrorNotARawValue(@TempDir Path tmp)
        throws IOException
    {
        // "NaN"/"Infinity" both parse successfully via Double.parseDouble -- they must not be
        // stored as literal non-finite data. This is deliberately NOT a claim about what a
        // Dataset-JSON WRITER should emit for a missing value (open question, out of scope for
        // this module) -- it is a READER-side robustness fix: neither string represents real
        // numeric data, so neither may be treated as though it does, independent of why the
        // string is there.
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.LEN4",
                  "name": "LEN4",
                  "label": "Non-finite numeric strings",
                  "records": 2,
                  "columns": [
                    {"itemOID": "IT.VAL", "name": "VAL", "label": "Value", "dataType": "decimal"}
                  ],
                  "rows": [
                    ["NaN"],
                    ["Infinity"]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "nonfinite.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(MissingValue.MIS_ERROR, table.getValue(0, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(1, 0));
    }

    // ---- A syntactically valid but out-of-range JSON number token (Number-branch, not string) -


    @Test
    void overflowingJsonNumberTokenBecomesMisErrorNotLiteralInfinity(@TempDir Path tmp)
        throws IOException
    {
        // 1e400 is a perfectly legal JSON number; Jackson's getDoubleValue() silently returns
        // Double.POSITIVE_INFINITY for it (no exception) -- it must not be stored as a literal
        // Infinity value.
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.OVF",
                  "name": "OVF",
                  "label": "Overflowing JSON number",
                  "records": 2,
                  "columns": [
                    {"itemOID": "IT.VAL", "name": "VAL", "label": "Value", "dataType": "double"}
                  ],
                  "rows": [
                    [1e400],
                    [-1e400]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "overflow.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(MissingValue.MIS_ERROR, table.getValue(0, 0));
        assertEquals(MissingValue.MIS_ERROR, table.getValue(1, 0));
    }

    // ---- A raw JSON number in a STRING column must be normalised to text -------------------


    @Test
    void rawJsonNumberInStringColumnIsNormalizedToItsTextForm(@TempDir Path tmp) throws IOException
    {
        // Non-conformant (the spec's JSON data type for a "string" dataType is always "string"),
        // but the parser accepts a bare number here too. The stored raw value must be the String
        // "56", not the Long 56 -- otherwise this cell is unequal, by raw value, to a row where
        // the same logical value arrived correctly quoted, silently splitting merge/join keys.
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.NUMSTR",
                  "name": "NUMSTR",
                  "label": "Bare number in a string column",
                  "records": 2,
                  "columns": [
                    {"itemOID": "IT.CODE", "name": "CODE", "label": "Code", "dataType": "string"}
                  ],
                  "rows": [
                    ["56"],
                    [56]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "numstr.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals("56", table.getValue(0, 0));
        assertEquals("56", table.getValue(1, 0));
        assertEquals(table.getValue(0, 0), table.getValue(1, 0),
                "a quoted and an unquoted \"56\" must be the SAME raw value in a string column");
    }

    // ---- Nested object/array cell values (non-conformant, but the parser accepts them) ------


    @Test
    void nestedObjectCellValueBecomesMisUnknown(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BAD",
                  "name": "BAD",
                  "label": "Nested object cell",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"},
                    {"itemOID": "IT.B", "name": "B", "label": "B", "dataType": "double"}
                  ],
                  "rows": [
                    ["x", {"nested": 1}]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "nested.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(1, table.getRowCount());
        assertEquals("x", table.getValue(0, 0));
        assertInstanceOf(MissingValue.class, table.getValue(0, 1));
        assertEquals(MissingValue.MIS_UNKNOWN, table.getValue(0, 1));
    }


    @Test
    void nestedArrayCellValueBecomesMisUnknown(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BAD2",
                  "name": "BAD2",
                  "label": "Nested array cell",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "double"}
                  ],
                  "rows": [
                    [[1, 2, 3]]
                  ]
                }
                """;
        java.net.URI uri = writeJson(tmp, "nestedarr.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(1, table.getRowCount());
        assertInstanceOf(MissingValue.class, table.getValue(0, 0));
        assertEquals(MissingValue.MIS_UNKNOWN, table.getValue(0, 0));
    }

}
