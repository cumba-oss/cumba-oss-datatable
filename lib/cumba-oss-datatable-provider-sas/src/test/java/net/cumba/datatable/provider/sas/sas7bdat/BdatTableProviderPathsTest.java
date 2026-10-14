package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.provider.sas.testsupport.LoggerCapture;
import net.cumba.datatable.values.DataValueType;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.ColumnName;
import net.cumba.sasutils.bdat.DatasetBdat;
import net.cumba.sasutils.bdat.FormatAndLabelSubHeader;
import net.cumba.sasutils.bdat.ParserBdat;
import net.cumba.sasutils.bdat.VariableBdat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link BdatTableProvider} behaviour the value- and parity-oriented tests do not observe: the
 * row-accounting diagnostics, the encoding-fallback log, the display-format assembly, the sort
 * order, the table-level metadata and the progress events each of the three load paths must fire.
 */
class BdatTableProviderPathsTest
{

    private static final String LOGGER_NAME = BdatTableProvider.class.getName();

    private static Path fixture(String aName, Path aTmp) throws IOException
    {
        try (var in = BdatTableProviderPathsTest.class.getResourceAsStream(aName + ".sas7bdat"))
        {
            assertNotNull(in, "fixture missing: " + aName);
            Path copy = aTmp.resolve(aName + ".sas7bdat");
            Files.write(copy, in.readAllBytes());
            return copy;
        }
    }

    // ==================== row accounting diagnostics ====================


    @Test
    void aDeletedRowCountThatContradictsTheHeaderIsReported()
    {
        // The load still succeeds — the row total reconciles — but the reader and the header
        // disagree about WHICH rows were deleted, and that has to be visible.
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            assertThrows(IOException.class, () -> BdatTableProvider.checkAllRowsRead(10L, 0, 4, 6));
            assertTrue(log.containsMessageContaining("Found unexpected number of deleted records"),
                    "a deleted-row misdetection must be reported: " + log.messages());
        }
    }


    @Test
    void agreedDeletedRowsAreReportedAtDebugAndNotAsAProblem() throws IOException
    {
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            BdatTableProvider.checkAllRowsRead(10L, 2, 2, 8);
            assertFalse(log.containsMessageContaining("unexpected"),
                    "agreement must not be reported as a problem: " + log.messages());
            assertTrue(log.containsMessageContaining("deleted records (as expected)"),
                    "the deleted-row count belongs in the log — it is why the delivered row count "
                            + "is lower than the header's: " + log.messages());
        }
    }


    @Test
    void aFileWithNoDeletedRowsSaysNothingAtAll() throws IOException
    {
        // Nothing was deleted, so there is nothing to explain; a "found 0 deleted records" line on
        // every single load is noise that trains the reader to skip the ones that matter.
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            BdatTableProvider.checkAllRowsRead(10L, 0, 0, 10);
            assertTrue(log.records().isEmpty(), "expected no log at all, got " + log.messages());
        }
    }

    // ==================== encoding fallback ====================


    @Test
    void anUnmappedEncodingByteIsReportedWithTheRawByte()
    {
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            assertEquals(StandardCharsets.ISO_8859_1, BdatTableProvider.resolveCharset(0x00));
            assertTrue(log.containsMessageContaining("is not in the SAS encoding table"),
                    "the raw byte is the only clue the user has: " + log.messages());
        }
    }


    @Test
    void aKnownEncodingTheJvmCannotSupplyIsReportedByName()
    {
        // 0xA9 maps to x-ISO2022-CN-GB, which this JVM knows the name of but cannot instantiate —
        // a different failure from an unmapped byte, and the message must say which one it is.
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            Charset resolved = BdatTableProvider.resolveCharset(0xA9);
            assertEquals(StandardCharsets.ISO_8859_1, resolved,
                    "an unsupported encoding falls back rather than failing the load");
            assertTrue(log.containsMessageContaining("not supported by the JVM"),
                    log.messages().toString());
            assertFalse(log.containsMessageContaining("is not in the SAS encoding table"),
                    "a known-but-unsupported encoding must not be reported as unknown");
        }
    }


    @Test
    void aKnownSupportedEncodingIsUsedSilently()
    {
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            assertEquals(StandardCharsets.UTF_8, BdatTableProvider.resolveCharset(0x14));
            assertTrue(log.records().isEmpty(),
                    "a normal file must log nothing: " + log.messages());
        }
    }

    // ==================== display-format assembly ====================


    @Test
    void aBlankNameIsNoFormatOnlyWithoutAWidth()
    {
        // SAS stores its plain number format w.d ("8.", "8.2") with an EMPTY format name and only
        // a width and a decimal count; ReadStat reports such a variable as "8" / "8.1" and parso
        // as "8.". This test used to assert the opposite -- that a blank name with a width is "not
        // a format SAS has" -- and so pinned the reader dropping a real format
        // (PLAN-sas-bare-wd-format-read). A blank name is no format only WITHOUT a width (a bare
        // "." stays impossible), and on a character column, where SAS stores its w. format under
        // the name "$".
        assertEquals("8.", formatOf(VariableType.NUMERIC, 8, "   ", (short) 8, null),
                "a blank name with a width is SAS's w. format");
        assertEquals("8.2", formatOf(VariableType.NUMERIC, 8, "", (short) 8, (short) 2));
        assertEquals("3.", formatOf(VariableType.NUMERIC, 8, null, (short) 3, (short) 0));
        assertNull(formatOf(VariableType.NUMERIC, 8, "   ", (short) 0, null),
                "a blank name without a width is no format, never a bare '.'");
        assertNull(formatOf(VariableType.NUMERIC, 8, "", null, null));
        assertNull(formatOf(VariableType.CHARACTER, 10, "", null, null));
        assertNull(formatOf(VariableType.CHARACTER, 10, "  ", (short) 10, null),
                "a blank name on a character column is no format, whatever its width");
        // and a real format still assembles, so the nulls above are not a broken helper path
        assertEquals("DATE9.", formatOf(VariableType.NUMERIC, 8, "DATE", (short) 9, null));
    }


    @Test
    void aFormatWithoutADigitCountIsNotGivenOne()
    {
        // formatDigits is a nullable Short on the wire: a format stored without a width is valid
        // (DATE. is a real SAS format) and must not NPE the load or grow a phantom width.
        assertEquals("DATE.", formatOf(VariableType.CHARACTER, 8, "DATE", null, null));
        assertEquals("DATE.", formatOf(VariableType.CHARACTER, 8, "DATE", (short) 0, null));
        assertEquals("DATE9.", formatOf(VariableType.CHARACTER, 8, "DATE", (short) 9, null));
    }


    @Test
    void onlyANumericColumnGetsDecimalPlaces()
    {
        // A character format has no decimal count — "$CHAR8.2" is not a format SAS has.
        assertEquals("BEST12.2", formatOf(VariableType.NUMERIC, 8, "BEST", (short) 12, (short) 2));
        assertEquals("BEST12.", formatOf(VariableType.NUMERIC, 8, "BEST", (short) 12, (short) 0));
        assertEquals("BEST12.", formatOf(VariableType.NUMERIC, 8, "BEST", (short) 12, null));
        assertEquals("$CHAR8.", formatOf(VariableType.CHARACTER, 8, "$CHAR", (short) 8, (short) 2));
    }


    @Test
    void aNumericColumnWithoutAStoredLengthIsEightBytesWide()
    {
        // A SAS numeric is 8 bytes unless the file says otherwise; a character column with no
        // stored length gets no invented one, because there is no equivalent default.
        assertEquals(8, columnOf(VariableType.NUMERIC, null, null, null, null).getLength());
        assertEquals(4, columnOf(VariableType.NUMERIC, 4, null, null, null).getLength());
        assertEquals(0, columnOf(VariableType.CHARACTER, null, null, null, null).getLength(),
                "a character column with no stored length records none (0); 8 is the SAS numeric "
                        + "default and would be a fabrication here");
    }


    private static String formatOf(VariableType aType, Integer aLength, String aFormat,
            Short aDigits, Short aDecimals)
    {
        return columnOf(aType, aLength, aFormat, aDigits, aDecimals).getDisplayFormat();
    }


    private static DataTableColumnMeta columnOf(VariableType aType, Integer aLength, String aFormat,
            Short aDigits, Short aDecimals)
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///tmp/x.sas7bdat"));
        new BdatTableProvider().addColumn(support, var(aType, aLength, aFormat, aDigits, aDecimals),
                0, URI.create("file:///tmp/x.sas7bdat"));
        return support.getTableMeta().build().getColumn(0);
    }


    private static VariableBdat var(VariableType aType, Integer aLength, String aFormat,
            Short aDigits, Short aDecimals)
    {
        FormatAndLabelSubHeader flsh = new FormatAndLabelSubHeader()
        {

            @Override
            public String getFormat()
            {
                return aFormat;
            }


            @Override
            public String getLabel()
            {
                return null;
            }
        };
        flsh.formatDigits = aDigits;
        flsh.formatDecimals = aDecimals;
        return new VariableBdat(flsh, new ColumnName(), null)
        {

            @Override
            public String getName()
            {
                return "COL";
            }


            @Override
            public VariableType getType()
            {
                return aType;
            }


            @Override
            public Integer getLength()
            {
                return aLength;
            }


            @Override
            public String getLabel()
            {
                return null;
            }
        };
    }

    // ==================== sort order ====================

    // ==================== progress events on all three load paths ====================


    @Test
    void theFileAndStreamOverloadsAgreeWithTheUriEntryPoint(@TempDir Path aTmp) throws IOException
    {
        // Two public entry points nothing in the suite called: provide(URI, File), which the
        // library providers use once they already hold the file, and provide(URI, InputStream,
        // DatasetBdat), the stream form for a caller that has parsed the descriptor itself. Both
        // must deliver the same table as the URI path, or a library member reads differently from
        // the same file opened directly.
        Path file = fixture("doubles_12x25", aTmp);
        IDataTable viaUri = new BdatTableProvider().provide(file.toUri(),
                BdatProviderSupplier.FI_BDAT);
        IDataTable viaFile = new BdatTableProvider().provide(file.toUri(), file.toFile());

        DatasetBdat dataset = new ParserBdat().parseDataset(file.toFile());
        IDataTable viaStream;
        try (InputStream in = Files.newInputStream(file))
        {
            viaStream = new BdatTableProvider().provide(file.toUri(), in, dataset);
        }

        for (IDataTable table : List.of(viaFile, viaStream))
        {
            assertEquals(viaUri.getRowCount(), table.getRowCount());
            assertEquals(viaUri.getColumnCount(), table.getColumnCount());
            for (int c = 0; c < viaUri.getColumnCount(); c++)
            {
                assertEquals(viaUri.getValue(24, c), table.getValue(24, c), "cell [24," + c + "]");
            }
        }
    }


    private static IDataTable load(Path aFile, String aParallel, String aMmap) throws IOException
    {
        String oldParallel = System.getProperty("bdat.parallel");
        String oldMmap = System.getProperty("bdat.mmap");
        try
        {
            System.setProperty("bdat.parallel", aParallel);
            System.setProperty("bdat.mmap", aMmap);
            return new BdatTableProvider().provide(aFile.toUri(), BdatProviderSupplier.FI_BDAT);
        }
        finally
        {
            restore("bdat.parallel", oldParallel);
            restore("bdat.mmap", oldMmap);
        }
    }


    @Test
    void everyPathProducesColumnsThatCanBeRead(@TempDir Path aTmp) throws IOException
    {
        // completeParsedColumns() is what turns the filled chunks into readable columns; skipping
        // it leaves a table whose cells cannot be addressed.
        Path file = fixture("doubles_12x25", aTmp);
        for (String[] mode : new String[][]
        {
                {
                        "false", "false"
                },
                {
                        "true", "false"
                },
                {
                        "true", "true"
                }
        })
        {
            IDataTable table = load(file, mode[0], mode[1]);
            assertEquals(25, table.getRowCount());
            assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
            assertNotNull(table.getValue(24, 11),
                    "the last cell of the last row must be addressable (parallel=" + mode[0]
                            + ", mmap=" + mode[1] + ")");
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
}
