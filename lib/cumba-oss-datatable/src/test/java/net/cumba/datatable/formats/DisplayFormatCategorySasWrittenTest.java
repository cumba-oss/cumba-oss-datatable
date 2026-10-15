package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-temporal-classifier: byte-identical in cumba-datatable and
// cumba-oss-datatable. Edit in cumba-datatable, then copy; check_oss_identity.py fails on any
// divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The SAS-written oracle of PLAN-date-format-classifier-agreement (C5, SC5): what SAS itself stored
 * under each format. The two files are upstream parso test material (Apache-2.0) in
 * {@code cumba-formats/lib/cumba-parso/src/test/resources/sas7bdat/}; the table below is their
 * (format, stored value) census, read on 2026-10-15 with {@code pyreadstat}
 * ({@code disable_datetime_conversion=True}), one row each — no binary is copied here (owner's
 * fixture-source policy).
 *
 * <ul>
 * <li>{@code date_formats.sas7bdat}, SAS {@code 9.0401M4X64_7PRO}, sha256
 * {@code bb9f0c7cf470fd4d8276d36e577acc613319edcb19395f78d83cbc367aa73621}: 49 columns hold
 * <b>20892</b> (the day count of 2017-03-14) and 18 hold <b>1805125016.546</b> (the seconds of
 * 2017-03-14T15:36:56.546 since 1960).</li>
 * <li>{@code time_formats.sas7bdat}, SAS {@code 9.0401M6Linux}, sha256
 * {@code 363614e3faa5a6142dda2554c42b48e32a2d3e8cd1e4e5822c2387f7d2b813ee}: 7 columns hold
 * <b>36610</b> (10:10:10 as seconds of the day).</li>
 * </ul>
 *
 * <p>
 * The claim: a column whose value is the day count classifies {@code DATE_SAS}, the
 * seconds-since-1960 value {@code DATETIME_SAS}, the seconds-of-day value {@code TIME_SAS} — SAS
 * stored a day count under every format the reference calls a date format ({@code MONNAME},
 * {@code MONTH}, {@code JULIAN}, {@code YEAR}, the separator variants included) and seconds since
 * 1960 under every datetime format ({@code DTDATE}, {@code E8601DN}, {@code MDYAMPM} included). The
 * pinned exceptions: {@code TOD} — SAS stored a datetime under it, and it classifies
 * {@code TIME_SAS} by the owner's ruling SC4 (the format writes the time of day of either value) —
 * and {@code E8601LX} / {@code B8601LX}, under which SAS 9.4M4 also stored a datetime (their
 * entries write <i>"datetime values as local time"</i> in the form yyyy-mm-ddThh:mm:ss+hh:mm), yet
 * SAS's own {@code FMTINFO} category is {@code time} and wins (SC14, owner SAS 9.4M9 FMTINFO run,
 * 2026-10-15): they classify {@code TIME_SAS}, so a Dataset-JSON export writes their time of day
 * (one WARNING per column). Recorded here as a disagreement between two SAS sources, not hidden.
 * </p>
 */
class DisplayFormatCategorySasWrittenTest
{

    private static final double DAY_COUNT = 20892;

    private static final double SECONDS_SINCE_1960 = 1805125016.546;

    private static final double SECONDS_OF_DAY = 36610;

    /** The formats SAS stored a datetime under that classify as time: *LX (SC14), TOD (SC4). */
    private static final List<String> TIME_BY_RULING_UNDER_A_DATETIME = List.of("E8601LX",
            "B8601LX", "TOD");

    /** A column of the SAS-written file: its format name and the value SAS stored. */
    record Witness(String format, double stored)
    {
    }

    /** {@code date_formats.sas7bdat}, column order of the file. */
    static final List<Witness> DATE_FORMATS = List.of(new Witness("B8601DA", 20892),
            new Witness("E8601DA", 20892), new Witness("DATE", 20892), new Witness("DAY", 20892),
            new Witness("DDMMYY", 20892), new Witness("DDMMYYB", 20892),
            new Witness("DDMMYYC", 20892), new Witness("DDMMYYD", 20892),
            new Witness("DDMMYYN", 20892), new Witness("DDMMYYP", 20892),
            new Witness("DDMMYYS", 20892), new Witness("WEEKDATE", 20892),
            new Witness("WEEKDATX", 20892), new Witness("WEEKDAY", 20892),
            new Witness("DOWNAME", 20892), new Witness("WORDDATE", 20892),
            new Witness("WORDDATX", 20892), new Witness("YYMM", 20892), new Witness("YYMMC", 20892),
            new Witness("YYMMD", 20892), new Witness("YYMMN", 20892), new Witness("YYMMP", 20892),
            new Witness("YYMMS", 20892), new Witness("YYMMDD", 20892),
            new Witness("YYMMDDB", 20892), new Witness("YYMMDDC", 20892),
            new Witness("YYMMDDD", 20892), new Witness("YYMMDDN", 20892),
            new Witness("YYMMDDP", 20892), new Witness("YYMMDDS", 20892),
            new Witness("YYMON", 20892), new Witness("YEAR", 20892), new Witness("JULDAY", 20892),
            new Witness("JULIAN", 20892), new Witness("MMDDYY", 20892),
            new Witness("MMDDYYC", 20892), new Witness("MMDDYYD", 20892),
            new Witness("MMDDYYN", 20892), new Witness("MMDDYYP", 20892),
            new Witness("MMDDYYS", 20892), new Witness("MMYY", 20892), new Witness("MMYYC", 20892),
            new Witness("MMYYD", 20892), new Witness("MMYYN", 20892), new Witness("MMYYP", 20892),
            new Witness("MMYYS", 20892), new Witness("MONNAME", 20892), new Witness("MONTH", 20892),
            new Witness("MONYY", 20892), new Witness("E8601DN", 1805125016.546),
            new Witness("E8601DT", 1805125016.546), new Witness("E8601DX", 1805125016.546),
            new Witness("E8601DZ", 1805125016.546), new Witness("E8601LX", 1805125016.546),
            new Witness("B8601DN", 1805125016.546), new Witness("B8601DT", 1805125016.546),
            new Witness("B8601DX", 1805125016.546), new Witness("B8601DZ", 1805125016.546),
            new Witness("B8601LX", 1805125016.546), new Witness("DATEAMPM", 1805125016.546),
            new Witness("DATETIME", 1805125016.546), new Witness("DTDATE", 1805125016.546),
            new Witness("DTMONYY", 1805125016.546), new Witness("DTWKDATX", 1805125016.546),
            new Witness("DTYEAR", 1805125016.546), new Witness("TOD", 1805125016.546),
            new Witness("MDYAMPM", 1805125016.546));

    /** {@code time_formats.sas7bdat}, column order of the file. */
    static final List<Witness> TIME_FORMATS = List.of(new Witness("E8601LZ", 36610),
            new Witness("E8601TM", 36610), new Witness("HHMM", 36610), new Witness("HOUR", 36610),
            new Witness("MMSS", 36610), new Witness("TIME", 36610), new Witness("TIMEAMPM", 36610));

    static Stream<Witness> witnesses()
    {
        return Stream.concat(DATE_FORMATS.stream(), TIME_FORMATS.stream());
    }


    /** The kind the stored value proves, with the one pinned exception ({@code TOD}, SC4). */
    private static DisplayFormatCategory kindOf(Witness aWitness)
    {
        if (TIME_BY_RULING_UNDER_A_DATETIME.contains(aWitness.format()))
        {
            return DisplayFormatCategory.TIME_SAS;
        }
        if (aWitness.stored() == DAY_COUNT)
        {
            return DisplayFormatCategory.DATE_SAS;
        }
        if (aWitness.stored() == SECONDS_SINCE_1960)
        {
            return DisplayFormatCategory.DATETIME_SAS;
        }
        assertEquals(SECONDS_OF_DAY, aWitness.stored(), aWitness.format());
        return DisplayFormatCategory.TIME_SAS;
    }


    @ParameterizedTest
    @MethodSource("witnesses")
    void classifiesAsTheValueKindSasStored(Witness aWitness)
    {
        assertEquals(kindOf(aWitness), DisplayFormatCategory.classify(aWitness.format() + "."),
                aWitness.format() + " holds " + aWitness.stored());
    }


    /**
     * The census is the whole file: 49 + 18 and 7 columns, and TOD is the only datetime-valued
     * time.
     */
    @Test
    void censusCoversBothFilesWhole()
    {
        assertEquals(67, DATE_FORMATS.size());
        assertEquals(49, DATE_FORMATS.stream().filter(w -> w.stored() == DAY_COUNT).count());
        assertEquals(18,
                DATE_FORMATS.stream().filter(w -> w.stored() == SECONDS_SINCE_1960).count());
        assertEquals(7, TIME_FORMATS.size());
        assertTrue(TIME_FORMATS.stream().allMatch(w -> w.stored() == SECONDS_OF_DAY));
        assertEquals(TIME_BY_RULING_UNDER_A_DATETIME,
                witnesses()
                        .filter(w -> w.stored() == SECONDS_SINCE_1960 && DisplayFormatCategory
                                .classify(w.format() + ".") == DisplayFormatCategory.TIME_SAS)
                        .map(Witness::format).toList(),
                "SC4 / SC14: the formats SAS stored a datetime under that classify as time");
    }
}
