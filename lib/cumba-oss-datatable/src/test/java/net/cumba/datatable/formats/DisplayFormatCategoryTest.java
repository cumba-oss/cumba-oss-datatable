package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-temporal-classifier: byte-identical in cumba-datatable and
// cumba-oss-datatable. Edit in cumba-datatable, then copy; check_oss_identity.py fails on any
// divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DisplayFormatCategoryTest
{

    @ParameterizedTest
    @ValueSource(strings =
    {
            "DATE9.", "DATE.", "E8601DA.", "IS8601DA.", "B8601DA.", "DDMMYY10.", "MMDDYY10.",
            "YYMMDD10.", "YYMMDDD10.", "MONNAME9.", "MONTH.", "WEEKDATE.", "WORDDATE.", "JULIAN5.",
            "date9.", "  E8601DA.  "
    })
    void classifiesSasDateFormats(String fmt)
    {
        assertEquals(DisplayFormatCategory.DATE_SAS, DisplayFormatCategory.classify(fmt));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "DATETIME20.", "DATETIME.", "DATEAMPM.", "B8601DT.", "IS8601DT.", "E8601DT.",
            "datetime20.0", "DTDATE9.", "E8601DN10.", "MDYAMPM.", "NLDATM."
    })
    void classifiesSasDateTimeFormats(String fmt)
    {
        assertEquals(DisplayFormatCategory.DATETIME_SAS, DisplayFormatCategory.classify(fmt));
    }


    /**
     * {@code E8601TZ} / {@code IS8601TZ} are time formats (SC9: <i>"Adjusts time values to the
     * Coordinated Universal Time (UTC) and writes the time values …"</i>); until 2026-10-15 they
     * were filed under datetime, and a column was exported as a datetime on 1 January 1960.
     * {@code TOD} and {@code TIMEAMPM} are times by the owner's ruling SC4.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "TIME8.", "TIMEAMPM.", "HHMM.", "HOUR.", "IS8601TM.", "E8601TM.", "B8601TM.", "time8.",
            "E8601TZ.", "IS8601TZ.", "TOD8.", "MMSS.", "E8601LZ.", "E8601TX."
    })
    void classifiesSasTimeFormats(String fmt)
    {
        assertEquals(DisplayFormatCategory.TIME_SAS, DisplayFormatCategory.classify(fmt));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "Rdate", "Rdate.", "rdate", "RDATE.", "  Rdate  "
    })
    void classifiesRDateFormats(String fmt)
    {
        assertEquals(DisplayFormatCategory.DATE_UNIX, DisplayFormatCategory.classify(fmt));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "Rtimestamp", "Rtimestamp.", "rtimestamp", "Rtimestamp[UTC]", "Rtimestamp.[UTC]",
            "Rtimestamp[Europe/Berlin]", "Rtimestamp.[Europe/Berlin]", "RTIMESTAMP[Asia/Tokyo]"
    })
    void classifiesRTimestampFormats(String fmt)
    {
        assertEquals(DisplayFormatCategory.DATETIME_UNIX, DisplayFormatCategory.classify(fmt));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "BEST12.", "$CHAR20.", "DOLLAR12.2", "PERCENT8.", "F8.2", "BINARY8.", "HEX2.", "Rdates",
            // Rtimestamp prefix without proper bracket close
            "Rtimestamp[", "Rtimestampfoo"
    })
    void classifiesNonDateFormatsAsOther(String fmt)
    {
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory.classify(fmt));
    }


    @ParameterizedTest
    @CsvSource(
    {
            ", OTHER", "'', OTHER", "'   ', OTHER"
    })
    void classifiesNullAndBlankAsOther(String fmt, String expected)
    {
        assertEquals(DisplayFormatCategory.valueOf(expected), DisplayFormatCategory.classify(fmt));
    }


    @Test
    void epochHelpersAgree()
    {
        assertTrue(DisplayFormatCategory.DATE_SAS.isSasEpoch());
        assertTrue(DisplayFormatCategory.DATETIME_SAS.isSasEpoch());
        assertTrue(DisplayFormatCategory.TIME_SAS.isSasEpoch());
        assertFalse(DisplayFormatCategory.DATE_UNIX.isSasEpoch());

        assertTrue(DisplayFormatCategory.DATE_UNIX.isUnixEpoch());
        assertTrue(DisplayFormatCategory.DATETIME_UNIX.isUnixEpoch());
        assertFalse(DisplayFormatCategory.DATE_SAS.isUnixEpoch());

        assertFalse(DisplayFormatCategory.OTHER.isDateTimeKind());
        assertTrue(DisplayFormatCategory.DATE_SAS.isDateTimeKind());
    }

    // ---- the column rule (PLAN-date-format-classifier-agreement SC10, SC11, SC12) -----------

    /** Stands in for the Dataset-JSON provider's {@code ColumnDataType} constant. */
    private enum DsjType
    {
        STRING, INTEGER, DATE, DATETIME, TIME
    }

    private static DataTableColumnMeta.DataTableColumnMetaBuilder column(DataValueType aType,
            @Nullable String aFormat)
    {
        return DataTableColumnMeta.builder().index(0).name("C").type(aType).displayFormat(aFormat);
    }


    /**
     * Numeric-first: a column that is not LONG / DOUBLE is OTHER even with a preserved {@code date}
     * and a date format (its text is already ISO 8601) — the define and XLSX rule.
     */
    @Test
    void columnRuleIsNumericFirst()
    {
        assertEquals(DisplayFormatCategory.OTHER,
                DisplayFormatCategory.classify(column(DataValueType.STRING, "DATE9.")
                        .addMetaData("dataType", DsjType.DATE).build()));
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory.classify(
                column(DataValueType.OTHER, "DATE9.").addMetaData("dataType", "date").build()));
        assertEquals(DisplayFormatCategory.DATE_SAS,
                DisplayFormatCategory.classify(column(DataValueType.LONG, "DATE9.").build()));
        assertEquals(DisplayFormatCategory.DATETIME_SAS,
                DisplayFormatCategory.classify(column(DataValueType.DOUBLE, "DTDATE9.").build()));
        assertEquals(DisplayFormatCategory.OTHER,
                DisplayFormatCategory.classify(column(DataValueType.DOUBLE, "BEST12.").build()));
        assertEquals(DisplayFormatCategory.OTHER,
                DisplayFormatCategory.classify(column(DataValueType.DOUBLE, null).build()));
    }


    /**
     * SC10: a preserved Dataset-JSON {@code dataType} on a numeric column wins over the display
     * format and needs none — stored as the provider's enum constant or as text, either case.
     */
    @ParameterizedTest
    @CsvSource(
    {
            "DOUBLE, , date, DATE_SAS", //
            "DOUBLE, , datetime, DATETIME_SAS", //
            "DOUBLE, , time, TIME_SAS", //
            "LONG, , DATE, DATE_SAS", //
            "LONG, , Date, DATE_SAS", //
            "DOUBLE, BEST12., date, DATE_SAS", //
            "DOUBLE, DATE9., datetime, DATETIME_SAS", //
            "DOUBLE, DATE9., integer, DATE_SAS", //
            "DOUBLE, DATE9., string, DATE_SAS", //
            "DOUBLE, , integer, OTHER", //
            "DOUBLE, , '', OTHER", //
            "DOUBLE, , ' ', OTHER"
    })
    void preservedDatasetJsonTypeWinsOnANumericColumn(DataValueType aType, String aFormat,
            String aPreserved, String aExpected)
    {
        DataTableColumnMeta asText = column(aType, aFormat).addMetaData("dataType", aPreserved)
                .build();
        assertEquals(DisplayFormatCategory.valueOf(aExpected),
                DisplayFormatCategory.classify(asText), "text " + aPreserved);
        DsjType constant = switch (aPreserved.trim().toUpperCase(java.util.Locale.ROOT))
        {
        case "DATE" -> DsjType.DATE;
        case "DATETIME" -> DsjType.DATETIME;
        case "TIME" -> DsjType.TIME;
        case "INTEGER" -> DsjType.INTEGER;
        default -> DsjType.STRING;
        };
        DataTableColumnMeta asEnum = column(aType, aFormat).addMetaData("dataType", constant)
                .build();
        assertEquals(DisplayFormatCategory.valueOf(aExpected),
                DisplayFormatCategory.classify(asEnum), "constant " + constant);
    }


    /** {@code preservedKind} reads the preserved type of any column, a STRING column included. */
    @Test
    void preservedKindIgnoresTheStorageType()
    {
        assertEquals(DisplayFormatCategory.DATE_SAS, DisplayFormatCategory.preservedKind(
                column(DataValueType.STRING, null).addMetaData("dataType", DsjType.DATE).build()));
        assertEquals(DisplayFormatCategory.DATETIME_SAS, DisplayFormatCategory.preservedKind(
                column(DataValueType.STRING, null).addMetaData("dataType", "datetime").build()));
        assertEquals(DisplayFormatCategory.TIME_SAS, DisplayFormatCategory.preservedKind(
                column(DataValueType.STRING, null).addMetaData("dataType", "TIME").build()));
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory.preservedKind(
                column(DataValueType.STRING, "DATE9.").addMetaData("dataType", "string").build()));
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory
                .preservedKind(column(DataValueType.DOUBLE, "DATE9.").build()));
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory.preservedKind(
                column(DataValueType.DOUBLE, "DATE9.").addMetaData("dataType", 7).build()));
    }


    /**
     * Pre-go fold #2: a JDBC native type {@code DATE} / {@code Date} is not a Dataset-JSON token
     * and is never read as a preserved kind — the display format decides.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "DATE", "Date", "date", "TIMESTAMP", "TIME"
    })
    void nativeTypeIsNeverAPreservedKind(String aNativeType)
    {
        assertEquals(DisplayFormatCategory.DATETIME_SAS, DisplayFormatCategory.classify(
                column(DataValueType.DOUBLE, "E8601DT.").nativeType(aNativeType).build()));
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory
                .classify(column(DataValueType.DOUBLE, null).nativeType(aNativeType).build()));
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory
                .preservedKind(column(DataValueType.DOUBLE, null).nativeType(aNativeType).build()));
    }


    /** The {@code "displayFormat"} metadata entry is the fallback when the field is unset. */
    @Test
    void displayFormatMetadataIsTheFallback()
    {
        assertEquals(DisplayFormatCategory.DATE_SAS,
                DisplayFormatCategory.classify(column(DataValueType.DOUBLE, null)
                        .addMetaData("displayFormat", "YYMMDDN8.").build()));
        assertEquals(DisplayFormatCategory.DATE_SAS, DisplayFormatCategory.classify(
                column(DataValueType.DOUBLE, " ").addMetaData("displayFormat", "DATE9.").build()));
        assertEquals(DisplayFormatCategory.TIME_SAS,
                DisplayFormatCategory.classify(column(DataValueType.DOUBLE, "TOD8.")
                        .addMetaData("displayFormat", "DATE9.").build()),
                "the field wins over the metadata entry");
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory.classify(
                column(DataValueType.DOUBLE, null).addMetaData("displayFormat", 9).build()));
    }


    /** SC12: the R forms classify through the column as through the string. */
    @Test
    void rFormsClassifyThroughTheColumn()
    {
        assertEquals(DisplayFormatCategory.DATE_UNIX,
                DisplayFormatCategory.classify(column(DataValueType.DOUBLE, "Rdate").build()));
        assertEquals(DisplayFormatCategory.DATETIME_UNIX, DisplayFormatCategory
                .classify(column(DataValueType.DOUBLE, "Rtimestamp[Europe/Berlin]").build()));
        assertEquals(DisplayFormatCategory.OTHER,
                DisplayFormatCategory.classify(column(DataValueType.STRING, "Rdate").build()));
    }


    /** SC11: the four duration formats, and no other name. */
    @ParameterizedTest
    @CsvSource(
    {
            "TIME8., true", "time., true", "TIME12.2, true", "MMSS., true", "HHMM., true",
            "HOUR5., true", "HOUR., true", "TOD8., false", "E8601TM., false", "TIMEAMPM11., false",
            "E8601TZ., false", "NLTIME., false", "DATETIME20., false", "DATE9., false",
            "$TIME., false", "TIMEX., false", "BEST12., false", ", false", "'', false"
    })
    void isSasDurationNamesTheDurationFormats(String aFormat, boolean aExpected)
    {
        assertEquals(aExpected, DisplayFormatCategory.isSasDuration(aFormat),
                String.valueOf(aFormat));
    }
}
