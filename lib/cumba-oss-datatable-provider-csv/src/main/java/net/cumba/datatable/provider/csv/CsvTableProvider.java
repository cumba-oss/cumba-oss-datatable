package net.cumba.datatable.provider.csv;

import com.univocity.parsers.csv.CsvParserSettings;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.impl.provider.AbstractTableDataParser;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.ProbeFailures;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

@CustomLog
public class CsvTableProvider extends AbstractDataTableProvider
{

    /**
     * The default number of rows to be used to guess the row types.
     */
    public static final int DEFAULT_GUESS_ROW_COUNT = 10_240;

    /**
     * Charset to use when decoding the CSV file. Defined independently from the CSV exporter's
     * charset property so reader and writer can evolve separately.
     */
    public static final Property PROP_CHARSET = Property.forOneOfOrCustom(//
            "Charset", //
            "Character set used to decode the CSV file. The list is a set of common "
                    + "suggestions; any JVM-supported charset name may be entered.", //
            StandardCharsets.UTF_8.name(), //
            StandardCharsets.UTF_8.name(), //
            "US-ASCII", //
            "ISO-8859-1", //
            "windows-1252", //
            "windows-1250", //
            "windows-1251", //
            "Shift_JIS", //
            "GB2312", //
            "Big5", //
            "EUC-JP", //
            "EUC-KR");

    public static final Property PROP_DELIMITER = Property.forString(//
            "Value Delimiter", //
            "The character used to separate values within a record. Leave empty to auto-detect.", //
            "");

    public static final Property LINE_SEPARATOR = Property.forString(//
            "Record Delimiter", //
            "The character sequence used to separate records. Leave empty to auto-detect.", //
            "");

    public static final Property PROP_QUOTE = Property.forString(//
            "Quote", //
            "Character used for escaping values where the field delimiter is part of the value.", //
            "\"");

    public static final Property PROP_QUOTE_ESCAPE = Property.forString(//
            "Quote Escape", //
            "Character used for escaping quotes inside an already quoted value.", //
            "\"");

    public static final Property GUESS_ROW_COUNT = Property.forInteger(//
            "Guess Row Count", //
            "Number of data rows scanned to determine column data types.", //
            DEFAULT_GUESS_ROW_COUNT);

    private static final List<Property> FILE_PROPERTIES = List.of(PROP_CHARSET, PROP_DELIMITER,
            LINE_SEPARATOR, PROP_QUOTE, PROP_QUOTE_ESCAPE, GUESS_ROW_COUNT);

    /**
     * The number of rows to be used to guess the column types.
     */
    @Getter
    @Setter
    private int guessingRowCount = DEFAULT_GUESS_ROW_COUNT;

    /**
     * Charset used when reading the CSV file. Defaults to UTF-8 when no property is supplied.
     */
    @Getter
    @Setter
    private Charset charset = StandardCharsets.UTF_8;

    /**
     * Value delimiter. {@code null} or blank means auto-detect from the first line.
     */
    @Getter
    @Setter
    private @Nullable String delimiter;

    /**
     * Record separator. {@code null} or blank means auto-detect.
     */
    @Getter
    @Setter
    private @Nullable String lineSeparator;

    @Getter
    @Setter
    private char quote = '"';

    @Getter
    @Setter
    private char quoteEscape = '"';

    /**
     * Resolve a {@link Charset} from the given property map, falling back to UTF-8 if unset or
     * unparseable.
     */
    public static Charset resolveCharset(Map<Property, String> aProperties)
    {
        if (aProperties == null)
        {
            return StandardCharsets.UTF_8;
        }
        String requested = aProperties.get(PROP_CHARSET);
        // NullAway does not narrow through CDT.isBlankOrNull (it carries no @Contract), so the
        // non-blank arm is asserted explicitly; Property.defaultValue() is non-null by record
        // component.
        String name = CDT.isBlankOrNull(requested) ? PROP_CHARSET.defaultValue()
                : Objects.requireNonNull(requested);
        try
        {
            return Charset.forName(name);
        }
        catch (Exception _)
        {
            return StandardCharsets.UTF_8;
        }
    }


    /**
     * Records the charset this provider decodes with (E3, PLAN-provider-encoding-metadata): CSV
     * declares none, so it is {@code CONFIGURED} when it differs from the {@link #PROP_CHARSET}
     * default, else {@code DEFAULT} (an absent, blank or unusable request resolves to the default).
     */
    private void setEncodingOf(DataTableMetaSupport aSupport)
    {
        Charset effective = getCharset();
        aSupport.setEncoding(effective.name(), DataTableMetaSupport
                .requestedCharsetSource(effective, Charset.forName(PROP_CHARSET.defaultValue())));
    }


    /**
     * Apply all provider properties from the given map onto this provider's state. Missing or blank
     * entries leave the corresponding field at its default (auto-detect for delimiter and line
     * separator).
     */
    protected void applyProperties(Map<Property, String> aProperties)
    {
        setCharset(resolveCharset(aProperties));
        if (aProperties == null)
        {
            return;
        }

        // ⚠⚠ CDT.isBlankOrNull() delegates to String.isBlank(), which is true for a tab, a
        // space or a newline -- exactly the values a real TSV/whitespace-delimited or
        // explicit-CRLF configuration would supply. Treating them as "unset" silently fell
        // back to auto-detect, discarding the user's explicit choice with no warning: a
        // configured "\t" delimiter was ignored and a configured "\n"/"\r\n" line separator
        // could never even reach buildParserSettings' explicit branch. Only genuine emptiness
        // means "leave at auto-detect" -- matching the property's own description and the
        // quote/quoteEscape branches below, which already use isEmpty() for the same reason.
        String delim = aProperties.get(PROP_DELIMITER);
        setDelimiter(delim == null || delim.isEmpty() ? null : delim);

        String ls = aProperties.get(LINE_SEPARATOR);
        setLineSeparator(ls == null || ls.isEmpty() ? null : ls);

        String q = aProperties.get(PROP_QUOTE);
        if (q != null && !q.isEmpty())
        {
            setQuote(q.charAt(0));
        }
        else
        {
            setQuote(PROP_QUOTE.defaultValue().charAt(0));
        }

        String qe = aProperties.get(PROP_QUOTE_ESCAPE);
        if (qe != null && !qe.isEmpty())
        {
            setQuoteEscape(qe.charAt(0));
        }
        else
        {
            setQuoteEscape(PROP_QUOTE_ESCAPE.defaultValue().charAt(0));
        }

        String grc = aProperties.get(GUESS_ROW_COUNT);
        if (CDT.isBlankOrNull(grc))
        {
            // A reused provider must not keep a stale sample size from an earlier call --
            // the javadoc above promises the default for a missing/blank entry, and until now
            // this was the one field that silently broke that promise.
            setGuessingRowCount(DEFAULT_GUESS_ROW_COUNT);
        }
        else
        {
            // isBlankOrNull(grc) was false above, so grc is non-null here.
            String grcValue = Objects.requireNonNull(grc);
            try
            {
                int val = Integer.parseInt(grcValue.trim());
                setGuessingRowCount(val > 0 ? val : DEFAULT_GUESS_ROW_COUNT);
            }
            catch (NumberFormatException _)
            {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Invalid " + GUESS_ROW_COUNT.name() + " value: " + grc);
            }
        }
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return CsvProviderSupplier.FIS;
    }


    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    public List<Property> getProviderProperties(URI aUri, @Nullable FileInfo aFileInfo)
    {
        String firstLine = peekFirstLine(aUri);
        if (firstLine == null || firstLine.isEmpty())
        {
            return FILE_PROPERTIES;
        }

        Property delimiterProp = PROP_DELIMITER;
        try
        {
            char detected = findDelimChar(firstLine);
            delimiterProp = Property.forString(PROP_DELIMITER.name(), PROP_DELIMITER.description(),
                    Character.toString(detected));
        }
        catch (IOException _)
        {
            // first line could not be interpreted -- keep the static default
        }

        return List.of(PROP_CHARSET, delimiterProp, LINE_SEPARATOR, PROP_QUOTE, PROP_QUOTE_ESCAPE,
                GUESS_ROW_COUNT);
    }


    /**
     * Best-effort read of the first line of the given URI, using the currently configured
     * {@link #getCharset() charset}. Returns {@code null} on an I/O failure reading a local source
     * so the caller can fall back to static defaults; a remote ({@code http} / {@code https})
     * source that cannot be read aborts the open instead ({@link ProbeFailures}).
     *
     * @throws java.io.UncheckedIOException
     *             if a remote source cannot be read.
     */
    private @Nullable String peekFirstLine(URI aUri)
    {
        try (InputStream in = URIHelper.openStream(aUri);
                BufferedReader br = new BufferedReader(new InputStreamReader(in, getCharset())))
        {
            return br.readLine();
        }
        catch (IOException ex)
        {
            ProbeFailures.rethrowIfRemote(aUri, ex);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Unable to peek first line of " + aUri + ": " + ex.getMessage());
            return null;
        }
    }


    @Override
    public DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        applyProperties(aProperties);
        return provideMetaData(aUri, aFileInfo);
    }


    @Override
    public DataTableMeta provideMetaData(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        applyProperties(aProperties);
        return provideMetaData(aMember, aFileInfo);
    }


    @Override
    public IDataTable provide(URI aUri, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        applyProperties(aProperties);
        return provide(aUri, aFileInfo);
    }


    @Override
    public @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        applyProperties(aProperties);
        return provide(aMember, aFileInfo);
    }


    @Override
    public DataTableMeta provideMetaData(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = URIHelper.openStream(aURI);
                BufferedInputStream bin = new BufferedInputStream(in, 65_536))
        {
            CsvParserSettings ps = buildParserSettings(bin);

            CsvRecordReader parser = new CsvRecordReader(ps);

            try
            {
                parser.beginParsing(new InputStreamReader(bin, getCharset()));

                CsvRecord headRow = parser.next();
                if (headRow == null)
                {
                    throw new IOException("CSV file is empty (no header row).");
                }

                List<CsvRecord> headRowBlock = new ArrayList<>(
                        Math.min(guessingRowCount, DEFAULT_GUESS_ROW_COUNT));
                CsvRecord row;
                // K7c: every quoted cell of the sample is flagged -- any quoted value is text.
                while ((row = parser.nextWithQuoteFlags()) != null)
                {
                    headRowBlock.add(row);
                    if (headRowBlock.size() >= guessingRowCount)
                    {
                        break;
                    }
                }

                DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());
                dtms.setTable(aURI);
                dtms.setFileFormat("CSV", null);
                setEncodingOf(dtms);
                dtms.setDatasetSize(aURI);

                int colCount = headRow.getColumnCount();
                DataValueType[] dataTypes = determineTypes(headRowBlock, colCount);

                Set<String> usedNames = new HashSet<>();
                for (int i = 0; i < colCount; i++)
                {
                    String name = resolveColumnName(headRow.getValue(i), i, usedNames, aURI);
                    DataValueType type = dataTypes[i];
                    dtms.addColumn(name, type);
                }

                return dtms.getTableMeta().build();
            }
            finally
            {
                parser.stopParsing();
            }
        }
    }


    @Override
    public IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = URIHelper.openStream(aURI);
                BufferedInputStream bin = new BufferedInputStream(in, 65_536))
        {
            CsvParserSettings ps = buildParserSettings(bin);

            CsvRecordReader parser = new CsvRecordReader(ps);

            CsvTableDataParser tblParser;
            try
            {
                parser.beginParsing(new InputStreamReader(bin, getCharset()));

                // head row contains column names
                CsvRecord headRow = parser.next();
                if (headRow == null)
                {
                    throw new IOException("CSV file is empty (no header row).");
                }

                // the first #guessingRowCount# rows are used to determine column types

                List<CsvRecord> headRowBlock = new ArrayList<>(
                        Math.min(guessingRowCount, DEFAULT_GUESS_ROW_COUNT));

                CsvRecord row;
                // K7c: every quoted cell of the sample is flagged -- any quoted value is text.
                while ((row = parser.nextWithQuoteFlags()) != null)
                {
                    headRowBlock.add(row);
                    if (headRowBlock.size() >= guessingRowCount)
                    {
                        break;
                    }
                }

                DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());

                dtms.setTable(aURI);
                dtms.setFileFormat("CSV", null);
                setEncodingOf(dtms);
                dtms.setDatasetSize(aURI);

                int colCount = headRow.getColumnCount();

                DataValueType[] dataTypes = determineTypes(headRowBlock, colCount);

                Set<String> usedNames = new HashSet<>();
                for (int i = 0; i < colCount; i++)
                {
                    String name = resolveColumnName(headRow.getValue(i), i, usedNames, aURI);
                    DataValueType type = dataTypes[i];
                    dtms.addColumn(name, type);
                }

                tblParser = new CsvTableDataParser(this, dtms.getTableMeta().build());

                for (CsvRecord csvRow : headRowBlock)
                {
                    tblParser.addDataRow(csvRow);
                }

                while ((row = parser.next()) != null)
                {
                    tblParser.addDataRow(row);
                }
            }
            finally
            {
                parser.stopParsing();
            }

            return tblParser.completeTable();
        }
    }


    /**
     * Build a {@link CsvParserSettings} honouring the configured delimiter, line separator, quote
     * and quote-escape. Auto-detects the delimiter from the first line when no explicit delimiter
     * was supplied via {@link #PROP_DELIMITER}, and uses univocity's line separator detection when
     * no {@link #LINE_SEPARATOR} was supplied. The stream must support {@code mark}/{@code reset}.
     */
    protected CsvParserSettings buildParserSettings(BufferedInputStream aStream) throws IOException
    {
        CsvParserSettings ps = new CsvParserSettings();

        // ⚠⚠ Deliberately NOT CDT.isBlankOrNull(): a tab or a newline is a legitimate,
        // explicitly-configured delimiter / line separator (TSV, explicit CRLF), and
        // isBlankOrNull's String.isBlank() treats both as "unset". Only genuine absence means
        // "auto-detect" -- applyProperties already normalises anything blank-but-non-empty away,
        // so getDelimiter()/getLineSeparator() are either null or a real, non-empty value here.
        // ⚠ Read ONCE into a local. Null-checking one call and dereferencing a second is
        // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE: nothing guarantees the second call returns the
        // same non-null value, so the check protects nothing. The internal repository suppresses
        // that SpotBugs pattern project-wide (duplicate coverage of NullAway); the published twin
        // does not, so the old spelling is green in one tree and red in the other. Keep the
        // locals so both trees carry one spelling.
        String configuredDelimiter = getDelimiter();
        if (configuredDelimiter == null || configuredDelimiter.isEmpty())
        {
            // we allow a first line of up to 1MB
            aStream.mark(1_048_576);
            String line = new BufferedReader(new InputStreamReader(aStream, getCharset()))
                    .readLine();
            aStream.reset();
            ps.getFormat().setDelimiter(findDelimChar(line));
        }
        else
        {
            ps.getFormat().setDelimiter(configuredDelimiter);
        }

        String configuredLineSeparator = getLineSeparator();
        if (configuredLineSeparator == null || configuredLineSeparator.isEmpty())
        {
            ps.setLineSeparatorDetectionEnabled(true);
        }
        else
        {
            ps.getFormat().setLineSeparator(configuredLineSeparator);
        }

        ps.getFormat().setQuote(getQuote());
        ps.getFormat().setQuoteEscape(getQuoteEscape());

        // univocity's defaults (4096 chars/field, 512 columns) are comfortably exceeded by real
        // clinical exports: a free-text comment column longer than 4096 characters, or an
        // ADaM/EDC-style wide export with more than 512 variables, would otherwise fail the whole
        // load with an unchecked TextParsingException instead of a normal parse. Field length has
        // no natural CSV-imposed bound, so it is disabled outright; column count is raised
        // generously rather than disabled, since it also bounds an internal array allocation.
        ps.setMaxCharsPerColumn(-1);
        ps.setMaxColumns(4096);

        return ps;
    }


    /**
     * Resolve the display name for header column {@code aColumnIndex}: substitute the positional
     * fallback {@code "V" + (aColumnIndex + 1)} for a blank cell (logging a warning), then
     * disambiguate the result — fallback or real — against every name already assigned earlier in
     * this same header, case-insensitively, matching {@link DataTableMetaSupport#addColumn}'s own
     * comparison. Without this, a duplicate header (two real columns sharing a name, or a real
     * column colliding with an earlier fallback) escaped as an undocumented
     * {@link IllegalStateException} from a method whose contract promises only {@link IOException}.
     *
     * @param aRawName
     *            the header cell as read from the file.
     * @param aColumnIndex
     *            the (0-based) column index, used for the fallback name and the log message.
     * @param aUsedLowerCase
     *            the lower-cased names already assigned in this header; updated with the name this
     *            call resolves to.
     * @param aURI
     *            the table's source, for the blank-header log message.
     * @return a name that is guaranteed unique (case-insensitively) among everything added to
     *         {@code aUsedLowerCase} so far.
     */
    private String resolveColumnName(String aRawName, int aColumnIndex, Set<String> aUsedLowerCase,
            URI aURI)
    {
        String name = aRawName;
        if (name.isBlank())
        {
            name = "V" + (aColumnIndex + 1);
            LOGGER.log(System.Logger.Level.WARNING,
                    "CSV column %d has blank header; using fallback '%s' (uri=%s)"
                            .formatted(aColumnIndex + 1, name, aURI));
        }

        String candidate = name;
        int suffix = 2;
        while (!aUsedLowerCase.add(candidate.toLowerCase(Locale.ROOT)))
        {
            candidate = name + "_" + suffix;
            suffix++;
        }
        return candidate;
    }


    /**
     * Determine the column type.<br/>
     * For now we only support {@link DataValueType#DOUBLE} and {@link DataValueType#STRING}. A
     * column holding a quoted cell anywhere in the block is {@link DataValueType#STRING} (owner
     * ruling K7c, 2026-09-30: <i>"yes any quoted value is always a string."</i>); the block's
     * records must therefore come from {@link CsvRecordReader#nextWithQuoteFlags()}.
     *
     * @param aRowBlock
     *            the row block used to determine the column type of the columns.
     * @param aColumnCount
     *            the number of columns to determine the type for.
     * @return an array of DataValueType that contains the determined column type.<br/>
     *         For now we only support {@link DataValueType#DOUBLE} and
     *         {@link DataValueType#STRING}.
     */
    private DataValueType[] determineTypes(List<CsvRecord> aRowBlock, int aColumnCount)
    {
        DataValueType[] res = new DataValueType[aColumnCount];
        for (int i = 0; i < aColumnCount; i++)
        {
            DataValueType dt = DataValueType.STRING;
            try
            {
                boolean possiblyDouble = true;
                boolean sawNumericEvidence = false;
                for (CsvRecord row : aRowBlock)
                {
                    if (i >= row.getColumnCount())
                    {
                        // ⚠ Ragged row: this record does not reach column i at all. An ABSENT cell
                        // is evidence of nothing and must not veto a numeric column — exactly as
                        // isDoubleOrMissing already treats a blank field and a "." as compatible
                        // with DOUBLE, because a CSV cannot distinguish absent from blank.
                        //
                        // Skipping matters because isDoubleOrMissing THROWS
                        // IndexOutOfBoundsException here (its documented contract), which the
                        // catch below swallowed at DEBUG while leaving the column at its STRING
                        // default. One short row inside the sampled block therefore retyped every
                        // column beyond its width to character — however numeric the other
                        // thousands of rows were.
                        continue;
                    }
                    if (row.isQuoted(i))
                    {
                        // ⚠ K7c (owner, 2026-09-30): "yes any quoted value is always a string." A
                        // quoted cell -- a quoted number or a quoted empty value included -- is
                        // evidence of TEXT, so the column is character and every cell keeps its
                        // text. Unquoted cells keep the inference below.
                        possiblyDouble = false;
                        break;
                    }
                    String cell = row.getValue(i);
                    // ⚠⚠ A blank cell or a "." is evidence of NOTHING, exactly like an absent
                    // (ragged-row) cell above — isDoubleOrMissing answers true for all three, so
                    // relying on mere PRESENCE (the former `sawCell`) let a column that is
                    // present-but-empty in every sampled row be typed DOUBLE on zero real
                    // evidence. Only a cell that is neither absent, blank nor "." counts as real
                    // numeric evidence; such a column stays at the STRING default, exactly like a
                    // column no sampled row reaches at all.
                    //
                    // ⚠ K7 / K7b (owner, 2026-09-30): only an UNQUOTED missing sentinel -- ".",
                    // "._" or ".A".."Z" -- is that missing (a quoted one never gets here: K7c
                    // above).
                    boolean blankOrDot = CDT.isBlankOrNull(cell)
                            || row.getUnquotedMissing(i) != null;
                    if (!blankOrDot)
                    {
                        sawNumericEvidence = true;
                        if (possiblyDouble && !row.isDoubleOrMissing(i))
                        {
                            possiblyDouble = false;
                        }
                    }
                    if (!possiblyDouble)
                    {
                        break;
                    }
                }

                // ⚠ Concretely: a header declaring STUDYID,USUBJID,COMMENT whose first
                // `guessingRowCount` rows omit the trailing field entirely OR carry it
                // present-but-empty, with a comment appearing only later. Typing such a column
                // DOUBLE destroys that text with no warning: addData2Column parses it as a
                // double, gets NaN, and stores MissingValue.MIS. Types are fixed before the parser
                // runs and never revised. With no evidence either way, keep the STRING default.
                if (possiblyDouble && sawNumericEvidence)
                {
                    dt = DataValueType.DOUBLE;
                }
            }
            catch (Exception ex)
            {
                LOGGER.log(System.Logger.Level.DEBUG, "Error during type detection for column " + i,
                        ex);
            }
            res[i] = dt;
        }
        return res;
    }


    private char findDelimChar(@Nullable String aLine) throws IOException
    {
        if (aLine == null || aLine.isEmpty())
        {
            throw new IOException("CSV file is empty or contains no header line.");
        }
        int countSemi = 0;
        int countComma = 0;
        int countTab = 0;
        boolean inQuotes = false;
        for (int i = 0; i < aLine.length(); i++)
        {
            char c = aLine.charAt(i);
            if (c == '"')
            {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes)
            {
                continue;
            }
            if (c == ';')
            {
                countSemi++;
            }
            else if (c == ',')
            {
                countComma++;
            }
            else if (c == '\t')
            {
                countTab++;
            }
        }
        // Prefer comma (CSV default), then tab (TSV), then semicolon (regional variant)
        if (countComma >= countSemi && countComma >= countTab)
        {
            return ',';
        }
        if (countTab >= countSemi)
        {
            return '\t';
        }
        return ';';
    }

    private static class CsvTableDataParser extends AbstractTableDataParser<CsvRecord>
    {

        CsvTableDataParser(@NonNull IDataTableProvider aProvider, @NonNull DataTableMeta aMeta)
        {
            super(aProvider, aMeta);
        }


        @Override
        protected void addData2Column(List<CsvRecord> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            for (CsvRecord row : aRowSlice)
            {
                if (aColumnIndex >= row.getColumnCount())
                {
                    // A CSV cannot express a null: a short row's absent character field is
                    // indistinguishable from a present-but-empty one, and the ordinary path
                    // below already yields "" for the latter (tri(row.getValue(i), "")).
                    // Loading it as a MissingValue made the very same blank cell read
                    // differently depending only on whether its row happened to be short.
                    // A numeric column keeps MIS — there the value is genuinely absent, and
                    // "" is not a number.
                    aDataColumn.addElement(
                            aMetaColumn.getType() == DataValueType.STRING ? "" : MissingValue.MIS);
                    continue;
                }
                MissingValue missing = row.getUnquotedMissing(aColumnIndex);
                if (missing != null)
                {
                    // K7 / K7b (owner, 2026-09-30): an unquoted ".", "._" or ".A".."Z" is that SAS
                    // missing in EVERY column type -- its own identity, stored as CdtTableBuilder
                    // stores it, in a DOUBLE buffer too; a quoted one falls through (text in a
                    // STRING column, MIS in a numeric one -- no number to parse).
                    aDataColumn.addElement(missing);
                    continue;
                }
                // ⚠ determineTypes() only ever produces DataValueType.DOUBLE or .STRING (see
                // its own javadoc), and no metadata-library override changes a column's type
                // (DataTableMetaSupport.addColumn takes the type argument as authoritative) --
                // so a LONG (or any other) column can never actually reach this parser. A
                // dedicated LONG case was tried and removed: it was dead code with two
                // permanently NO_COVERAGE mutants and no seam to construct a test through this
                // provider's public API, since CsvTableDataParser is a private nested class.
                switch (aMetaColumn.getType())
                {
                case DOUBLE:
                {
                    double dblVal = row.getDoubleValue(aColumnIndex);
                    if (Double.isNaN(dblVal))
                    {
                        aDataColumn.addElement(MissingValue.MIS);
                    }
                    else
                    {
                        aDataColumn.addElement(dblVal);
                    }
                    break;
                }
                case STRING:
                default:
                    // Right-trim + intern like every other provider (XPT, SAS7BDAT, Parquet,
                    // XLSX, Dataset-JSON): the engine compares and groups string cells
                    // verbatim, so trailing padding has to be gone before the value reaches a
                    // column. getValue() never returns null (it folds null to ""), so the
                    // default is only there to satisfy the poly-null signature.
                    String val = tri(row.getValue(aColumnIndex), "");
                    aDataColumn.addElement(val);
                    break;
                }
            }
        }
    }
}
