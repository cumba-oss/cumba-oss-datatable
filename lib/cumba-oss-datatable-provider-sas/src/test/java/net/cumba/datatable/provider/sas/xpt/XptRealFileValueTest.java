package net.cumba.datatable.provider.sas.xpt;

// OSS-IDENTITY provider-sas: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.provider.sas.xpt.library.XptLibrary;
import net.cumba.datatable.provider.sas.xpt.library.XptLibraryMember;
import net.cumba.datatable.provider.sas.xpt.library.XptLibraryProvider;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * End-to-end reads of the SAS-written XPT fixtures, asserting the VALUES and the column metadata
 * the provider surfaces.
 *
 * <p>
 * <b>The expected values come from an EXTERNAL oracle, never from this stack.</b> Every cell of
 * every fixture under {@code testdata/xpt/01_plain} was cross-checked against
 * {@code pandas.read_sas(format="xport")} (pandas 3.0.1 — an independent XPORT implementation with
 * its own bit-exact IBM-hex-float decoder in {@code pandas/io/sas/sas_xport.py}), and the column
 * labels, stored lengths and stored format names against ReadStat via
 * {@code pyreadstat.read_xport}. The numbers below are what those report. Asserting against this
 * product's own XPT writer would only prove the two halves agree with each other.
 * </p>
 */
class XptRealFileValueTest
{

    private static File fixture(String aName)
    {
        File f = new File(System.getProperty("repoRoot"), "testdata/xpt/01_plain/" + aName);
        assertTrue(f.isFile(), () -> "expected fixture: " + f.getAbsolutePath());
        return f;
    }


    /** A member of a kind this provider does not know -- never an {@link XptLibraryMember}. */
    private static ILibraryMember memberOfUnknownKind(URI aUri)
    {
        return new ILibraryMember()
        {

            @Override
            public String getName()
            {
                return "X";
            }


            @Override
            public String getLabel()
            {
                return "label";
            }


            @Override
            public URI getUri()
            {
                return aUri;
            }


            @Override
            @SuppressWarnings("NullAway")
            public IDataTableLibrary getLibrary()
            {
                return null;
            }
        };
    }


    private static IDataTable read(String aName) throws IOException
    {
        IDataTable t = new XptTableProvider().provide(fixture(aName).toURI(),
                XptProviderSupplier.FI_XPT);
        assertNotNull(t);
        return t;
    }


    /**
     * The IBM-hex-float decoder over every stored width. {@code small_dif_num_len.xpt} holds one
     * value written as an 8-, 7-, 6-, 5-, 4- and 3-byte numeric, so it exercises
     * {@link XptVarParser#ibmToIeee}'s short-length arm — {@code mantissa << ((8 - aLength) * 8)} —
     * at every width SAS can write. pandas returns exactly these doubles for the same file.
     */
    @Test
    void truncatedIbmFloatsDecodeAtEveryStoredWidth() throws IOException
    {
        IDataTable t = read("small_dif_num_len.xpt");
        assertEquals(10L, t.getRowCount());
        DataTableMeta m = t.getMetaData();
        assertEquals(7, m.getColumnCount());

        // stored byte widths in order: the provider reports the NAMESTR length verbatim
        assertEquals(List.of(8, 8, 7, 6, 5, 4, 3), lengths(m));

        assertEquals(1.0, num(t, 0, 0));
        assertEquals(418.6594514176249, num(t, 0, 1));
        assertEquals(418.6594514176104, num(t, 0, 2));
        assertEquals(418.65945141389966, num(t, 0, 3));
        assertEquals(418.65945053100586, num(t, 0, 4));
        assertEquals(418.659423828125, num(t, 0, 5));
        assertEquals(418.625, num(t, 0, 6));

        // a second magnitude, so a wrong shift cannot coincide twice
        assertEquals(758.8070891797541, num(t, 1, 1));
        assertEquals(758.8070891797252, num(t, 1, 2));
        assertEquals(758.8070891723037, num(t, 1, 3));
        assertEquals(758.8070888519287, num(t, 1, 4));
        assertEquals(758.806640625, num(t, 1, 5));
        assertEquals(758.75, num(t, 1, 6));
    }


    /**
     * The sub-1 magnitudes, where the IBM exponent is below the bias and
     * {@code Math.pow(16, exponent - 64)} is a negative power. An exponent or mantissa mix-up that
     * happens to cancel at 418.66 does not cancel here.
     */
    @Test
    void smallMagnitudesDecodeAtEveryStoredWidth() throws IOException
    {
        IDataTable t = read("small_unprecise.xpt");
        assertEquals(4L, t.getRowCount());
        assertEquals(0.30000000000000004, num(t, 0, 1));
        assertEquals(0.29999999999999716, num(t, 0, 2));
        assertEquals(0.2999999999992724, num(t, 0, 3));
        assertEquals(0.2999999998137355, num(t, 0, 4));
        assertEquals(0.2999999523162842, num(t, 0, 5));
        assertEquals(0.29998779296875, num(t, 0, 6));
        assertEquals(0.09999999999999998, num(t, 1, 1));
        assertEquals(0.0999908447265625, num(t, 1, 6));
    }


    /**
     * Every SAS special missing value, end to end. ReadStat reads the same dataset as a plain
     * missing, then {@code _}, then {@code A}…{@code Z} in that row order
     * ({@code pyreadstat.read_sas7bdat(..., user_missing=True)} over the sibling
     * {@code missings.sas7bdat}, whose {@code missing_ranges} enumerate {@code A}…{@code Z} plus
     * {@code _}). A special missing collapsing to an ordinary {@code .} loses the recorded reason a
     * clinical value is absent.
     */
    @Test
    void everySasSpecialMissingSurvivesTheXptReadPath() throws IOException
    {
        IDataTable t = read("missings.xpt");
        assertEquals(28L, t.getRowCount());

        assertEquals(MissingValue.MIS, t.getValue(0, 0));
        assertEquals(MissingValue.MIS__, t.getValue(1, 0));
        assertEquals(".", t.getValue(0, 1));
        assertEquals("._", t.getValue(1, 1));

        for (int i = 0; i < 26; i++)
        {
            final int row = 2 + i;
            MissingValue expected = MissingValue.forValue(MissingValue.MIS_A.getValue() + i);
            assertEquals(expected, t.getValue(row, 0),
                    () -> "numeric special missing in row " + row);
            // the character column spells the same tag out as text
            assertEquals("." + (char) ('A' + i), t.getValue(row, 1));
        }
    }


    /**
     * The format string the provider assembles for a real stored format. ReadStat reports this
     * variable's stored format as {@code BEST} with no width, so SAS's own spelling is
     * {@code BEST.} — a width must not be invented, and the character column has no stored format
     * at all so no synthetic {@code $w.} may appear.
     */
    @Test
    void aStoredFormatWithoutAWidthRendersWithoutOne() throws IOException
    {
        DataTableMeta m = read("missings.xpt").getMetaData();
        assertEquals("BEST.", m.getColumn(0).getDisplayFormat());
        assertEquals("Value (N)", m.getColumn(0).getLabel());
        assertNull(m.getColumn(1).getDisplayFormat());
        assertEquals(DataValueType.STRING, m.getColumn(1).getType());
        assertEquals(3, m.getColumn(1).getLength());
    }


    /**
     * Column metadata for a real ADaM dataset. Names, types, stored lengths, labels and the
     * {@code DATE9.} display formats are exactly what ReadStat reports for the same file: its
     * {@code original_variable_types} gives {@code DATE9} for five variables and {@code None} for
     * the other 44, and {@code column_names_to_labels} gives the labels.
     */
    @Test
    void adamDatasetMetadataMatchesReadStat() throws IOException
    {
        IDataTable t = read("adsl.xpt");
        assertEquals(254L, t.getRowCount());
        DataTableMeta m = t.getMetaData();
        assertEquals(49, m.getColumnCount());

        assertEquals("STUDYID", m.getColumn(0).getName());
        assertEquals(DataValueType.STRING, m.getColumn(0).getType());
        assertEquals(12, m.getColumn(0).getLength());
        assertEquals("Study Identifier", m.getColumn(0).getLabel());
        assertNull(m.getColumn(0).getDisplayFormat());

        DataTableColumnMeta trtsdt = m.getColumn("TRTSDT");
        assertEquals(DataValueType.DOUBLE, trtsdt.getType());
        assertEquals(8, trtsdt.getLength());
        assertEquals("DATE9.", trtsdt.getDisplayFormat());
        assertEquals("Date of First Exposure to Treatment", trtsdt.getLabel());

        // exactly these five variables carry a display format, and no others
        List<String> formatted = new ArrayList<>();
        for (int c = 0; c < m.getColumnCount(); c++)
        {
            if (m.getColumn(c).getDisplayFormat() != null)
            {
                formatted.add(m.getColumn(c).getName());
            }
        }
        assertEquals(List.of("TRTSDT", "TRTEDT", "DISONSDT", "VISIT1DT", "RFENDT"), formatted);

        // row 0 values. SAS date numerics are handed on unconverted by design; 19725 is the cell
        // pandas renders as 2014-01-02 for this file.
        assertEquals("CDISCPILOT01", t.getValue(0, 0));
        assertEquals("01-701-1015", t.getValue(0, 1));
        assertEquals(19725.0, num(t, 0, m.getColumn("TRTSDT").getIndex()));
        assertEquals(19906.0, num(t, 0, m.getColumn("TRTEDT").getIndex()));
        assertEquals(63.0, num(t, 0, m.getColumn("AGE").getIndex()));
        assertEquals("Placebo", t.getValue(0, m.getColumn("ARM").getIndex()));
    }


    /**
     * The same dataset exported without variable attributes: no labels and no stored formats, so
     * the provider must produce neither — in particular it must not fall back to the column name.
     */
    @Test
    void aFileWithoutAttributesGetsNoLabelsAndNoFormats() throws IOException
    {
        DataTableMeta m = read("adsl_no_attribs.xpt").getMetaData();
        assertEquals(49, m.getColumnCount());
        for (int c = 0; c < m.getColumnCount(); c++)
        {
            DataTableColumnMeta col = m.getColumn(c);
            assertNull(col.getDisplayFormat(), () -> col.getName() + " display format");
            assertNull(col.getLabel(), () -> col.getName() + " label");
        }
    }


    /**
     * SAS's plain number format {@code w.d} is stored with an EMPTY format name and only a width
     * and a decimal count. The fixture is CDISC's {@code examples/adam/adcibc.xpt} from
     * {@code cdisc-org/DataExchange-DatasetJson} @ {@code a379f49a} (MIT; byte-identical copy, see
     * {@code testdata/README.md}), written by SAS. The expected formats are ReadStat's:
     * {@code pyreadstat.read_xport("adcibc.xpt", metadataonly=True)[1].original_variable_types}
     * (pyreadstat 1.3.6) gives {@code '8'} for {@code TRTPN}, {@code AGE}, {@code AGEGR1N},
     * {@code RACEN}, {@code ADY}, {@code PARAMN}, {@code '8.1'} for {@code AVISITN},
     * {@code 'DATE9'} for {@code TRTSDT}, {@code TRTEDT}, {@code ADT} and {@code None} for the
     * other 26 variables. SAS spells the first two {@code 8.} and {@code 8.1}, which is what the
     * provider must hand on (PLAN-sas-bare-wd-format-read); it used to drop both.
     */
    @Test
    void bareWdFormatsMatchReadStat() throws IOException
    {
        DataTableMeta m = read("adcibc.xpt").getMetaData();
        assertEquals(36, m.getColumnCount());
        assertEquals("8.", m.getColumn("AGE").getDisplayFormat());
        assertEquals("8.1", m.getColumn("AVISITN").getDisplayFormat());
        assertEquals("DATE9.", m.getColumn("TRTSDT").getDisplayFormat());
        assertNull(m.getColumn("USUBJID").getDisplayFormat());

        List<String> formatted = new ArrayList<>();
        for (int c = 0; c < m.getColumnCount(); c++)
        {
            DataTableColumnMeta col = m.getColumn(c);
            if (col.getDisplayFormat() != null)
            {
                formatted.add(col.getName() + "=" + col.getDisplayFormat());
            }
        }
        assertEquals(List.of("TRTSDT=DATE9.", "TRTEDT=DATE9.", "TRTPN=8.", "AGE=8.", "AGEGR1N=8.",
                "RACEN=8.", "AVISITN=8.1", "ADY=8.", "ADT=DATE9.", "PARAMN=8."), formatted);

        // the library view reads the same NAMESTR records through its own path
        try (IDataTableLibrary lib = new XptLibraryProvider().provide(fixture("adcibc.xpt").toURI(),
                XptProviderSupplier.FI_XPT))
        {
            DataTableColumnMeta[] viaLibrary = ((XptLibrary) lib).getMembers().toList().get(0)
                    .getColumns();
            for (int c = 0; c < viaLibrary.length; c++)
            {
                assertEquals(m.getColumn(c).getDisplayFormat(), viaLibrary[c].getDisplayFormat(),
                        viaLibrary[c].getName());
            }
        }
    }


    /**
     * XPT records no encoding at all, so the charset is the user's choice and the provider must
     * honour it. Reading the same bytes under two charsets must give different text — the only way
     * to show the charset property is load-bearing rather than decorative.
     *
     * <p>
     * Note which way round this goes: the fixture is <em>named</em> for the text it contains, not
     * for its encoding, and SAS wrote it with a UTF-8 session encoding. So UTF-8 is the reading
     * that is right and ISO-8859-1 is the one that produces mojibake — pandas, which has no charset
     * option and decodes as latin-1, reports the mojibake form for this file too.
     * </p>
     */
    @Test
    void theChosenCharsetDecidesTheDecodedText() throws IOException
    {
        File f = fixture("small_iso_8859_1.xpt");
        IDataTable asUtf8 = new XptTableProvider().provide(f.toURI(), XptProviderSupplier.FI_XPT,
                Map.of(XptTableProvider.PROP_CHARSET, StandardCharsets.UTF_8.name()));
        assertEquals("This is a ISO-8859-1 text: >\u00b5 \u00b6 \u00e4 \u00f6<",
                asUtf8.getValue(0, 0));

        IDataTable asLatin = new XptTableProvider().provide(f.toURI(), XptProviderSupplier.FI_XPT,
                Map.of(XptTableProvider.PROP_CHARSET, StandardCharsets.ISO_8859_1.name()));
        assertEquals("This is a ISO-8859-1 text: >\u00c2\u00b5 \u00c2\u00b6 \u00c3\u00a4 "
                + "\u00c3\u00b6<", asLatin.getValue(0, 0));
        assertFalse(String.valueOf(asLatin.getValue(0, 0)).equals(asUtf8.getValue(0, 0)),
                "the two charsets must not agree, or the property does nothing");
    }


    /**
     * A UTF-8 fixture whose rows carry multi-byte text and control characters, read with the
     * default charset. A character value is right-trimmed, but a CR, LF, NUL or ESC inside it is
     * data and must survive.
     */
    @Test
    void multiByteTextAndControlCharactersSurvive() throws IOException
    {
        IDataTable t = read("small_utf_8.xpt");
        assertEquals(4L, t.getRowCount());
        assertEquals("This is a ISO-8859-1 text: >\u00b5 \u00b6 \u00e4 \u00f6<", t.getValue(0, 0));
        assertEquals("This is a ISO-8859-8 text: >\u05d0 \u05d1 \u05d2 \u05d7<", t.getValue(1, 0));
        assertEquals("This is a SHIFT_JIS text: >\u3053 \u3093 \u306b \u3061 \u306f<",
                t.getValue(2, 0));

        // The byte values are read off the fixture with xxd rather than inferred from the text the
        // variable itself prints: what it labels "CR" is actually 0x13 (DC3) and it holds no NUL
        // byte at all, so an assertion written from the labels would have been wrong about the
        // file. pandas returns exactly this string, control bytes included.
        assertEquals("This is a text with non printable chars: CR=>\u0013< LF=>\n"
                + "< NULL=>< ESC=>\u001b< DEL=>\u007f<", t.getValue(3, 0));
    }


    /** A charset name the JVM does not know falls back to UTF-8 rather than failing the load. */
    @Test
    void anUnsupportedCharsetNameFallsBackToTheDefault() throws IOException
    {
        File f = fixture("small_utf_8.xpt");
        IDataTable t = new XptTableProvider().provide(f.toURI(), XptProviderSupplier.FI_XPT,
                Map.of(XptTableProvider.PROP_CHARSET, "NoSuchCharset-42"));
        assertEquals("This is a ISO-8859-1 text: >\u00b5 \u00b6 \u00e4 \u00f6<", t.getValue(0, 0));
    }


    /**
     * {@code provideMetaData} must describe the same file as {@code provide}, without reading it.
     */
    @Test
    void provideMetaDataAgreesWithProvideOnTheColumns() throws IOException
    {
        IDataTable t = read("adsl.xpt");
        DataTableMeta meta = new XptTableProvider().provideMetaData(fixture("adsl.xpt").toURI(),
                XptProviderSupplier.FI_XPT);
        assertEquals(t.getMetaData().getColumnCount(), meta.getColumnCount());
        for (int c = 0; c < meta.getColumnCount(); c++)
        {
            DataTableColumnMeta a = t.getMetaData().getColumn(c);
            DataTableColumnMeta b = meta.getColumn(c);
            assertEquals(a.getName(), b.getName());
            assertEquals(a.getType(), b.getType());
            assertEquals(a.getLabel(), b.getLabel());
            assertEquals(a.getDisplayFormat(), b.getDisplayFormat());
            assertEquals(a.getLength(), b.getLength());
        }
    }


    /** A URI naming a dataset the library does not hold must fail, not silently serve another. */
    @Test
    void anUnknownFragmentIsRejected()
    {
        File f = fixture("adsl.xpt");
        IOException ex = assertThrows(IOException.class, () -> new XptTableProvider()
                .provide(URI.create(f.toURI() + "#NOSUCHDATASET"), XptProviderSupplier.FI_XPT));
        assertTrue(ex.getMessage().contains("NOSUCHDATASET"), ex.getMessage());
    }


    /**
     * F-prov-16: a URI whose fragment is present but empty. {@code new File(URI)} rejects ANY URI
     * carrying a fragment component, so a blank one used to be classified as "no fragment" and then
     * handed to that constructor, which threw {@code IllegalArgumentException("URI has a fragment
     * component")} instead of opening the file's first dataset.
     */
    @Test
    void anEmptyFragmentReadsTheFirstDatasetRatherThanFailing() throws IOException
    {
        URI withEmptyFragment = URI.create(fixture("adsl.xpt").toURI() + "#");
        assertEquals("", withEmptyFragment.getFragment());

        IDataTable t = new XptTableProvider().provide(withEmptyFragment,
                XptProviderSupplier.FI_XPT);
        assertEquals(254L, t.getRowCount());
        assertEquals(49, t.getMetaData().getColumnCount());

        DataTableMeta meta = new XptTableProvider().provideMetaData(withEmptyFragment,
                XptProviderSupplier.FI_XPT);
        assertEquals(49, meta.getColumnCount());
    }


    /**
     * F-prov-15: a corrupt NAMESTR length must not decode to a plausible number. Beyond 8 bytes the
     * mantissa loop accumulates more than the 56 bits an IBM double has AND the promotion shift is
     * skipped, so the old code returned a finite, wrong value — the worst possible outcome for a
     * clinical measurement. The sibling {@code BdatVarParser.unpackRaw64} already range-checked.
     */
    @Test
    void anImpossibleNumericLengthIsRejectedRatherThanDecoded()
    {
        byte[] buf = new byte[16];
        buf[0] = 0x41;
        buf[1] = 0x10;
        assertThrows(IllegalArgumentException.class, () -> XptVarParser.ibmToIeee(buf, 0, 9));
        assertThrows(IllegalArgumentException.class, () -> XptVarParser.ibmToIeee(buf, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> XptVarParser.ibmToIeee(buf, 0, -1));
        // 1 and 8 are the inclusive bounds and must still decode
        assertEquals(1.0, XptVarParser.ibmToIeee(buf, 0, 8), 1e-12);
    }


    /**
     * A library member already carries the charset chosen when its library was opened, so the
     * member entry point must not re-prompt for one: {@code getProviderProperties(ILibraryMember,
     * …)} returns nothing for an {@link XptLibraryMember}, while the URI entry point still offers
     * the charset property. Getting this backwards asks the user the same question twice.
     */
    @Test
    void aLibraryMemberCarriesItsCharsetAndIsNotAskedAgain() throws IOException
    {
        XptTableProvider provider = new XptTableProvider();
        assertEquals(List.of(XptTableProvider.PROP_CHARSET), provider
                .getProviderProperties(fixture("adsl.xpt").toURI(), XptProviderSupplier.FI_XPT));

        try (IDataTableLibrary lib = new XptLibraryProvider()
                .provide(fixture("small_utf_8.xpt").toURI(), XptProviderSupplier.FI_XPT))
        {
            assertNotNull(lib);
            XptLibraryMember member = ((XptLibrary) lib).getMembers().toList().get(0);
            assertEquals(List.of(),
                    provider.getProviderProperties(member, XptProviderSupplier.FI_XPT));

            // a member of an unknown kind still gets asked
            ILibraryMember other = memberOfUnknownKind(fixture("adsl.xpt").toURI());
            assertEquals(List.of(XptTableProvider.PROP_CHARSET),
                    provider.getProviderProperties(other, XptProviderSupplier.FI_XPT));
        }
    }


    /**
     * The member's own charset wins over the property map, because the library was already opened
     * with it — and the member path must deliver the same rows as the URI path.
     */
    @Test
    void theMembersCharsetDecidesEvenWhenAPropertyIsPassed() throws IOException
    {
        try (IDataTableLibrary lib = new XptLibraryProvider().provide(
                fixture("small_iso_8859_1.xpt").toURI(), XptProviderSupplier.FI_XPT,
                Map.of(XptTableProvider.PROP_CHARSET, StandardCharsets.UTF_8.name())))
        {
            assertNotNull(lib);
            XptLibraryMember member = ((XptLibrary) lib).getMembers().toList().get(0);
            assertEquals(StandardCharsets.UTF_8, member.getCharset());

            IDataTable t = new XptTableProvider().provide(member, XptProviderSupplier.FI_XPT,
                    Map.of(XptTableProvider.PROP_CHARSET, StandardCharsets.ISO_8859_1.name()));
            assertNotNull(t);
            assertEquals("This is a ISO-8859-1 text: >\u00b5 \u00b6 \u00e4 \u00f6<",
                    t.getValue(0, 0), "the member's charset must win over the passed property");
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
