package net.cumba.datatable.provider.csv;

import com.univocity.parsers.csv.CsvParser;
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
import java.util.Set;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.impl.provider.AbstractTableDataParser;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
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

    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return CsvProviderSupplier.FIS;
    }


    @Override
    public DataTableMeta provideMetaData(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = aURI.toURL().openStream())
        {
            BufferedInputStream bin = new BufferedInputStream(in, 65_536);
            CsvParserSettings ps = buildParserSettings(bin);

            CsvParser parser = new CsvParser(ps);

            try
            {
                parser.beginParsing(new InputStreamReader(bin, getCharset()));

                String[] firstRow = parser.parseNext();
                if (firstRow == null)
                {
                    throw new IOException("CSV file is empty (no header row).");
                }
                CsvRecord headRow = new CsvRecord(firstRow);

                List<CsvRecord> headRowBlock = new ArrayList<>(
                        Math.min(guessingRowCount, DEFAULT_GUESS_ROW_COUNT));
                String[] row;
                while ((row = parser.parseNext()) != null)
                {
                    headRowBlock.add(new CsvRecord(row));
                    if (headRowBlock.size() >= guessingRowCount)
                    {
                        break;
                    }
                }

                DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());
                dtms.setTable(aURI);
                dtms.setFileFormat("CSV", null);
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
        try (InputStream in = aURI.toURL().openStream())
        {
            BufferedInputStream bin = new BufferedInputStream(in, 65_536);
            CsvParserSettings ps = buildParserSettings(bin);

            CsvParser parser = new CsvParser(ps);

            CsvTableDataParser tblParser;
            try
            {
                parser.beginParsing(new InputStreamReader(bin, getCharset()));

                // head row contains column names
                String[] firstRow = parser.parseNext();
                if (firstRow == null)
                {
                    throw new IOException("CSV file is empty (no header row).");
                }
                CsvRecord headRow = new CsvRecord(firstRow);

                // the first #guessingRowCount# rows are used to determine column types

                List<CsvRecord> headRowBlock = new ArrayList<>(
                        Math.min(guessingRowCount, DEFAULT_GUESS_ROW_COUNT));

                String[] row;
                while ((row = parser.parseNext()) != null)
                {
                    headRowBlock.add(new CsvRecord(row));
                    if (headRowBlock.size() >= guessingRowCount)
                    {
                        break;
                    }
                }

                DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());

                dtms.setTable(aURI);
                dtms.setFileFormat("CSV", null);
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

                while ((row = parser.parseNext()) != null)
                {
                    CsvRecord csvRow = new CsvRecord(row);
                    tblParser.addDataRow(csvRow);
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
     * was set via {@code setDelimiter(String)}, and uses univocity's line separator detection when
     * no line separator was set via {@code setLineSeparator(String)}. The stream must support
     * {@code mark}/{@code reset}.
     */
    protected CsvParserSettings buildParserSettings(BufferedInputStream aStream) throws IOException
    {
        CsvParserSettings ps = new CsvParserSettings();

        // ⚠⚠ Deliberately NOT CDT.isBlankOrNull(): a tab or a newline is a legitimate,
        // explicitly-configured delimiter / line separator (TSV, explicit CRLF), and
        // isBlankOrNull's String.isBlank() treats both as "unset". Only genuine absence means
        // "auto-detect".
        // ⚠ Read ONCE into a local. Null-checking one call and dereferencing a second is
        // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE: nothing guarantees the second call returns the
        // same non-null value, so the check would protect nothing. This repository does NOT
        // suppress that pattern, while the internal twin does suppress it project-wide -- so the
        // upstream spelling passes there and reds here. Keep the locals.
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
     * For now we only support {@link DataValueType#DOUBLE} and {@link DataValueType#STRING}.
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
                for (int rowIdx = 0; rowIdx < aRowBlock.size(); rowIdx++)
                {
                    CsvRecord row = aRowBlock.get(rowIdx);
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
                    String cell = row.getValue(i);
                    // ⚠⚠ A blank cell or a "." is evidence of NOTHING, exactly like an absent
                    // (ragged-row) cell above — isDoubleOrMissing answers true for all three, so
                    // relying on mere PRESENCE (the former `sawCell`) let a column that is
                    // present-but-empty in every sampled row be typed DOUBLE on zero real
                    // evidence. Only a cell that is neither absent, blank nor "." counts as real
                    // numeric evidence; such a column stays at the STRING default, exactly like a
                    // column no sampled row reaches at all.
                    boolean blankOrDot = CDT.isBlankOrNull(cell) || ".".equals(cell);
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
            for (int i = 0; i < aRowSlice.size(); i++)
            {
                CsvRecord row = aRowSlice.get(i);

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
                }
                else
                {
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
                    case LONG:
                    {
                        try
                        {
                            long lngVal = row.getLongValue(aColumnIndex);
                            aDataColumn.addElement(lngVal);
                        }
                        catch (Exception _)
                        {
                            aDataColumn.addElement(MissingValue.MIS);
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
}
