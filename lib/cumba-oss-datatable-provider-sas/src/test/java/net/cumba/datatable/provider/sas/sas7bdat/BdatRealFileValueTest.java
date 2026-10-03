package net.cumba.datatable.provider.sas.sas7bdat;

import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_CREATED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_ENCODING;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_MODIFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * End-to-end reads of the SAS-written SAS7BDAT fixtures, asserting the VALUES, the column metadata
 * and the table metadata the provider surfaces.
 *
 * <p>
 * <b>The expected values come from an EXTERNAL oracle, never from this stack.</b> Every cell of
 * every fixture under {@code testdata/sas7bdat} was cross-checked against
 * {@code pandas.read_sas(format="sas7bdat")} (pandas 3.0.1, which carries its own independent
 * SAS7BDAT decoder) — 4.77 million cells, with the only disagreements being the three deleted-row
 * fixtures, where pandas returns the rows SAS marks deleted and this provider correctly does not,
 * and the date-formatted numerics, which this provider hands on unconverted by design. The special
 * missing values and the column names were additionally checked against ReadStat
 * ({@code pyreadstat.read_sas7bdat(..., user_missing=True)}).
 * </p>
 */
class BdatRealFileValueTest
{

    private static File fixture(String aPath)
    {
        File f = new File(System.getProperty("repoRoot"), "testdata/sas7bdat/" + aPath);
        assertTrue(f.isFile(), () -> "expected fixture: " + f.getAbsolutePath());
        return f;
    }


    private static IDataTable read(String aPath) throws IOException
    {
        IDataTable t = new BdatTableProvider().provide(fixture(aPath).toURI(),
                BdatProviderSupplier.FI_BDAT);
        assertNotNull(t);
        return t;
    }


    /**
     * Table-level metadata for an uncompressed ADaM dataset. Every key the provider publishes is
     * asserted, because each one reaches the UI's dataset-properties panel and {@code dataset_size}
     * additionally backs the rule-engine accessor {@code PMDA-SD1142} reads.
     */
    @Test
    void tableMetadataOfAnUncompressedDataset() throws IOException
    {
        IDataTable t = read("01_plain/adsl.sas7bdat");
        assertEquals(254L, t.getRowCount());
        DataTableMeta m = t.getMetaData();

        assertEquals("SAS7BDAT", m.getMetaData("file_format"));
        assertEquals("NO", m.getMetaData("Compression"));
        assertEquals("UTF-8", m.getMetaData(META_KEY_ENCODING));
        assertEquals(440L, ((Number) m.getMetaData("Row Length")).longValue());
        assertEquals("2026-02-24T13:16:12", m.getMetaData(META_KEY_CREATED));
        assertEquals("2026-02-24T13:16:12", m.getMetaData(META_KEY_MODIFIED));
        assertEquals(262_144L, ((Number) m.getMetaData("dataset_size")).longValue());
        assertNull(m.getLabel(), "this fixture carries no dataset label");
        assertEquals(49, m.getColumnCount());
    }


    /**
     * The header's created/modified stamps are zone-free SAS wall-clock values and must reach the
     * metadata exactly as written, whatever the JVM default zone and locale. Under UTC+14 a one-way
     * conversion through the default zone moves the value by hours; under a Thai default locale a
     * locale-sensitive formatter (the old SimpleDateFormat path) prints Buddhist-era years (2569).
     */
    @Test
    void datasetTimestampsAreTheHeaderWallClockWhateverTheDefaultZone() throws IOException
    {
        TimeZone saved = TimeZone.getDefault();
        Locale savedLocale = Locale.getDefault();
        Locale savedFormat = Locale.getDefault(Locale.Category.FORMAT);
        Locale savedDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            Locale.setDefault(Locale.forLanguageTag("th-TH"));
            DataTableMeta m = read("01_plain/adsl.sas7bdat").getMetaData();
            assertEquals("2026-02-24T13:16:12", m.getMetaData(META_KEY_CREATED));
            assertEquals("2026-02-24T13:16:12", m.getMetaData(META_KEY_MODIFIED));
        }
        finally
        {
            TimeZone.setDefault(saved);
            Locale.setDefault(savedLocale);
            Locale.setDefault(Locale.Category.FORMAT, savedFormat);
            Locale.setDefault(Locale.Category.DISPLAY, savedDisplay);
        }
    }


    /**
     * The compression method is translated from the raw SAS marker to the name the UI shows:
     * {@code SASYZCRL} is RLE over character data and {@code SASYZCR2} is the binary variant. The
     * two fixtures differ only in their {@code COMPRESS=} option, so a mapping that returned the
     * raw marker, or the same answer for both, shows up here.
     */
    @Test
    void compressionMarkersAreTranslated() throws IOException
    {
        IDataTable chr = read("01_plain/adsl_compress_char.sas7bdat");
        assertEquals("CHAR", chr.getMetaData().getMetaData("Compression"));
        assertEquals(508L, chr.getRowCount());

        IDataTable bin = read("01_plain/adsl_compress_binary.sas7bdat");
        assertEquals("BINARY", bin.getMetaData().getMetaData("Compression"));
        assertEquals(508L, bin.getRowCount());

        // Decompression is asserted on the VALUES, by column NAME rather than by position: the
        // two fixtures were exported with different variable orders, so a positional comparison
        // between them would be comparing different variables. These cells are the ones pandas
        // returns for the same files.
        for (IDataTable t : List.of(chr, bin))
        {
            DataTableMeta m = t.getMetaData();
            assertEquals(49, m.getColumnCount());
            assertEquals("01-701-1015", t.getValue(0, m.getColumn("usubjid").getIndex()));
            assertEquals("CDISCPILOT01", t.getValue(0, m.getColumn("STUDYID").getIndex()));
            assertEquals("Placebo", t.getValue(0, m.getColumn("ARM").getIndex()));
            assertEquals(63.0,
                    ((Number) t.getValue(0, m.getColumn("AGE").getIndex())).doubleValue());
            // the last row too, so a decompressor that stops early cannot pass: STUDYID is the
            // same value in every row of this study, which is what makes it safe to assert at an
            // index the oracle dump was not inspected at.
            assertEquals("CDISCPILOT01",
                    t.getValue(t.getRowCount() - 1, m.getColumn("STUDYID").getIndex()));
        }
    }


    /**
     * A dataset label, end to end.
     *
     * <p>
     * This is the consumer side of the {@code cumba-sas-utils} ruling Q3: the label slice used to
     * arrive NUL-padded ({@code "Written by SAS\0\0"}) because {@code stripTrailing()} does not
     * treat U+0000 as whitespace, and the provider handed that straight to the UI and to every
     * export. The assertion is deliberately two-part — the exact label, and that no NUL survives
     * anywhere in it — so a regression on either side of that boundary is caught here rather than
     * in a screenshot.
     * </p>
     */
    @Test
    void theDatasetLabelArrivesTrimmedAndFreeOfNulPadding() throws IOException
    {
        DataTableMeta m = read("03_others/small_fl_a.sas7bdat").getMetaData();
        String label = m.getLabel();
        assertEquals("A Data Set label", label);
        assertEquals(-1, label.indexOf('\u0000'), "NUL padding must not reach the label");
        assertEquals(label.stripTrailing(), label, "the label must be right-trimmed");
    }


    /**
     * Rows SAS has marked deleted are not delivered, and the row count the table reports is the
     * live count. pandas returns 57 rows for this file because it hands back the deleted ones too;
     * SAS itself reports 48, and so must this provider — a deleted subject reappearing in an
     * analysis dataset is a data-integrity failure, not a cosmetic one.
     */
    @Test
    void deletedRowsAreNeitherDeliveredNorCounted() throws IOException
    {
        assertEquals(48L, read("01_plain/with_9_deleted.sas7bdat").getRowCount());
        assertEquals(900L, read("01_plain/long_100_deleted.sas7bdat").getRowCount());
        assertEquals(9000L, read("03_others/ds_big_deleted_rows.sas7bdat").getRowCount());
    }


    /**
     * The SAS7BDAT numeric decoder over every stored width. The fixture holds one value as an 8-,
     * 7-, 6-, 5-, 4- and 3-byte numeric, so it exercises {@code BdatVarParser.unpackRaw64}'s
     * short-length promotion {@code res << ((8 - aLength) * 8)} at every width SAS writes. pandas
     * returns exactly these doubles, and the XPT copy of the same study data decodes to the same
     * numbers through a completely different codec.
     */
    @Test
    void truncatedNumericsDecodeAtEveryStoredWidth() throws IOException
    {
        IDataTable t = read("01_plain/small_dif_num_len.sas7bdat");
        assertEquals(10L, t.getRowCount());
        assertEquals(List.of(8, 8, 7, 6, 5, 4, 3), lengths(t.getMetaData()));

        assertEquals(418.6594514176249, num(t, 0, 1));
        assertEquals(418.6594514176104, num(t, 0, 2));
        assertEquals(418.65945141389966, num(t, 0, 3));
        assertEquals(418.65945053100586, num(t, 0, 4));
        assertEquals(418.659423828125, num(t, 0, 5));
        assertEquals(418.625, num(t, 0, 6));

        assertEquals(758.8070891797541, num(t, 1, 1));
        assertEquals(758.8070891723037, num(t, 1, 3));
        assertEquals(758.806640625, num(t, 1, 5));
        assertEquals(758.75, num(t, 1, 6));
    }


    /**
     * Every SAS special missing value on the SAS7BDAT path. ReadStat reports this dataset's
     * {@code missing_ranges} as {@code A}…{@code Z} plus {@code _} in exactly this row order. The
     * tag is carried in the NaN payload, so a shift or mask slip turns every one of them into the
     * same value and nothing else in a read would notice.
     */
    @Test
    void everySasSpecialMissingSurvivesTheBdatReadPath() throws IOException
    {
        IDataTable t = read("01_plain/missings.sas7bdat");
        assertEquals(28L, t.getRowCount());

        assertEquals(MissingValue.MIS, t.getValue(0, 0));
        assertEquals(MissingValue.MIS__, t.getValue(1, 0));
        assertEquals(".", t.getValue(0, 1));
        assertEquals("._", t.getValue(1, 1));

        for (int i = 0; i < 26; i++)
        {
            final int row = 2 + i;
            assertEquals(MissingValue.forValue(MissingValue.MIS_A.getValue() + i),
                    t.getValue(row, 0), () -> "numeric special missing in row " + row);
            assertEquals("." + (char) ('A' + i), t.getValue(row, 1));
        }
    }


    /**
     * Unlike XPT, SAS7BDAT records its session encoding in the header, so the provider must decode
     * from the file rather than from a user choice — and must publish which encoding it used, since
     * that is the only way a user can diagnose mojibake.
     */
    @Test
    void theHeaderEncodingDecidesTheDecodedText() throws IOException
    {
        IDataTable latin = read("01_plain/small_iso_8859_1.sas7bdat");
        assertEquals("ISO-8859-1", latin.getMetaData().getMetaData(META_KEY_ENCODING));
        assertEquals("This is a ISO-8859-1 text: >\u00b5 \u00b6 \u00e4 \u00f6<",
                latin.getValue(0, 0));

        IDataTable hebrew = read("01_plain/small_iso_8859_8.sas7bdat");
        assertEquals("ISO-8859-8", hebrew.getMetaData().getMetaData(META_KEY_ENCODING));
        assertEquals("This is a ISO-8859-8 text: >\u05d0 \u05d1 \u05d2 \u05d7<",
                hebrew.getValue(0, 0));

        IDataTable sjis = read("01_plain/small_shift_jis.sas7bdat");
        assertEquals("Shift_JIS", sjis.getMetaData().getMetaData(META_KEY_ENCODING));
        assertEquals("This is a SHIFT_JIS text: >\u3053 \u3093 \u306b \u3061 \u306f<",
                sjis.getValue(0, 0));
    }


    /**
     * Column metadata for a real ADaM dataset: names, types, stored lengths and labels. The labels
     * are the ones ReadStat reports for the XPT export of the same study data.
     */
    @Test
    void columnMetadataOfAnAdamDataset() throws IOException
    {
        DataTableMeta m = read("01_plain/adsl.sas7bdat").getMetaData();
        assertEquals("STUDYID", m.getColumn(0).getName());
        assertEquals(DataValueType.STRING, m.getColumn(0).getType());
        assertEquals(12, m.getColumn(0).getLength());
        assertEquals("Study Identifier", m.getColumn(0).getLabel());
        assertEquals("CHARACTER", m.getColumn(0).getNativeType());

        DataTableColumnMeta age = m.getColumn("AGE");
        assertEquals(DataValueType.DOUBLE, age.getType());
        assertEquals(8, age.getLength());
        assertEquals("NUMERIC", age.getNativeType());
        assertEquals("Age", age.getLabel());
    }


    /**
     * The display format the provider assembles from the stored name, width and decimal count. The
     * fixture's numeric variable carries {@code BEST} with neither a width nor a decimal count, so
     * SAS's own spelling is {@code BEST.} — neither may be invented — and the character variable
     * carries no format at all.
     */
    @Test
    void displayFormatsAreAssembledLikeSasSpellsThem() throws IOException
    {
        DataTableMeta m = read("01_plain/missings.sas7bdat").getMetaData();
        assertEquals("BEST.", m.getColumn(0).getDisplayFormat());
        assertNull(m.getColumn(1).getDisplayFormat());
        assertEquals(3, m.getColumn(1).getLength());
    }


    /**
     * The three read paths — memory-mapped, parallel over the stream, and the single-threaded
     * fallback — must deliver the same table. They share only the metadata build, so nothing else
     * in the suite compares them, and the mmap path is the default for every local file.
     */
    @Test
    void theThreePipelinesDeliverIdenticalTables() throws IOException
    {
        String parallel = System.getProperty("bdat.parallel");
        String mmap = System.getProperty("bdat.mmap");
        try
        {
            System.clearProperty("bdat.parallel");
            System.clearProperty("bdat.mmap");
            IDataTable viaMmap = read("01_plain/adsl.sas7bdat");

            System.setProperty("bdat.mmap", "false");
            IDataTable viaStream = read("01_plain/adsl.sas7bdat");

            System.setProperty("bdat.parallel", "false");
            IDataTable viaSequential = read("01_plain/adsl.sas7bdat");

            assertEquals(254L, viaMmap.getRowCount());
            assertEquals(viaMmap.getRowCount(), viaStream.getRowCount());
            assertEquals(viaMmap.getRowCount(), viaSequential.getRowCount());

            int cols = viaMmap.getMetaData().getColumnCount();
            for (long r = 0; r < viaMmap.getRowCount(); r++)
            {
                for (int c = 0; c < cols; c++)
                {
                    Object expected = viaMmap.getValue(r, c);
                    assertEquals(expected, viaStream.getValue(r, c),
                            "stream pipeline, row " + r + " column " + c);
                    assertEquals(expected, viaSequential.getValue(r, c),
                            "sequential pipeline, row " + r + " column " + c);
                }
            }
        }
        finally
        {
            restore("bdat.parallel", parallel);
            restore("bdat.mmap", mmap);
        }
    }


    /**
     * {@code provideMetaData} must describe the same file as {@code provide} without reading a
     * single observation — including the row count, which it takes from the header.
     */
    @Test
    void provideMetaDataAgreesWithProvide() throws IOException
    {
        IDataTable t = read("01_plain/adsl.sas7bdat");
        DataTableMeta meta = new BdatTableProvider().provideMetaData(
                fixture("01_plain/adsl.sas7bdat").toURI(), BdatProviderSupplier.FI_BDAT);

        assertEquals(t.getMetaData().getColumnCount(), meta.getColumnCount());
        assertEquals(t.getRowCount(), meta.getRowCount());
        assertEquals(t.getMetaData().getMetaData("Compression"), meta.getMetaData("Compression"));
        assertEquals(t.getMetaData().getMetaData(META_KEY_ENCODING),
                meta.getMetaData(META_KEY_ENCODING));
        for (int c = 0; c < meta.getColumnCount(); c++)
        {
            assertEquals(t.getMetaData().getColumn(c).getName(), meta.getColumn(c).getName());
            assertEquals(t.getMetaData().getColumn(c).getLabel(), meta.getColumn(c).getLabel());
            assertEquals(t.getMetaData().getColumn(c).getDisplayFormat(),
                    meta.getColumn(c).getDisplayFormat());
        }
    }


    /**
     * F-prov-14: the metadata-only view must report the LIVE row count, the same figure
     * {@code provide} delivers. {@code DatasetBdat.getRowCount()} counts the rows SAS has marked
     * deleted as well — which is exactly why {@code provide} has to reconcile them in
     * {@code checkAllRowsRead} — so handing it on unadjusted made the library browser's row-count
     * column over-report by the deleted count and disagree with the table the user then opened.
     */
    @Test
    void provideMetaDataReportsTheLiveRowCountNotTheHeaderCount() throws IOException
    {
        for (String name : List.of("01_plain/with_9_deleted.sas7bdat",
                "01_plain/long_100_deleted.sas7bdat", "03_others/ds_big_deleted_rows.sas7bdat"))
        {
            DataTableMeta meta = new BdatTableProvider().provideMetaData(fixture(name).toURI(),
                    BdatProviderSupplier.FI_BDAT);
            assertEquals(read(name).getRowCount(), meta.getRowCount(),
                    () -> "metadata row count for " + name);
        }
    }


    private static void restore(String aKey, String aValue)
    {
        if (aValue == null)
        {
            System.clearProperty(aKey);
        }
        else
        {
            System.setProperty(aKey, aValue);
        }
    }


    private static List<Integer> lengths(DataTableMeta aMeta)
    {
        List<Integer> out = new ArrayList<>();
        for (int c = 0; c < aMeta.getColumnCount(); c++)
        {
            out.add(aMeta.getColumn(c).getLength());
        }
        return out;
    }


    private static double num(IDataTable aTable, long aRow, int aCol)
    {
        Object v = aTable.getValue(aRow, aCol);
        assertNotNull(v, () -> "row " + aRow + " column " + aCol);
        return ((Number) v).doubleValue();
    }
}
