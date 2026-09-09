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
import java.util.List;
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
            parser.beginParsing(new InputStreamReader(bin, getCharset()));

            try
            {
                String[] firstRow = parser.parseNext();
                if (firstRow == null)
                {
                    throw new IOException("CSV file is empty (no header row).");
                }
                CsvRecord headRow = new CsvRecord(firstRow);

                List<CsvRecord> headRowBlock = new ArrayList<>(guessingRowCount);
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

                for (int i = 0; i < colCount; i++)
                {
                    String name = headRow.getValue(i);
                    if (name.isBlank())
                    {
                        String fallback = "V" + (i + 1);
                        LOGGER.log(System.Logger.Level.WARNING,
                                "CSV column %d has blank header; using fallback '%s' (uri=%s)"
                                        .formatted(i, fallback, aURI));
                        name = fallback;
                    }
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

            parser.beginParsing(new InputStreamReader(bin, getCharset()));

            // head row contains column names
            String[] firstRow = parser.parseNext();
            if (firstRow == null)
            {
                throw new IOException("CSV file is empty (no header row).");
            }
            CsvRecord headRow = new CsvRecord(firstRow);

            // the first #guessingRowCount# rows are used to determine column types

            List<CsvRecord> headRowBlock = new ArrayList<>(guessingRowCount);

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

            for (int i = 0; i < colCount; i++)
            {
                String name = headRow.getValue(i);
                if (name.isBlank())
                {
                    String fallback = "V" + (i + 1);
                    LOGGER.log(System.Logger.Level.WARNING,
                            "CSV column %d has blank header; using fallback '%s' (uri=%s)"
                                    .formatted(i, fallback, aURI));
                    name = fallback;
                }
                DataValueType type = dataTypes[i];
                dtms.addColumn(name, type);
            }

            CsvTableDataParser tblParser = new CsvTableDataParser(this,
                    dtms.getTableMeta().build());

            for (CsvRecord csvRow : headRowBlock)
            {
                tblParser.addDataRow(csvRow);
            }

            while ((row = parser.parseNext()) != null)
            {
                CsvRecord csvRow = new CsvRecord(row);
                tblParser.addDataRow(csvRow);
            }
            parser.stopParsing();

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

        if (CDT.isBlankOrNull(getDelimiter()))
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
            ps.getFormat().setDelimiter(getDelimiter());
        }

        if (CDT.isBlankOrNull(getLineSeparator()))
        {
            ps.setLineSeparatorDetectionEnabled(true);
        }
        else
        {
            ps.getFormat().setLineSeparator(getLineSeparator());
        }

        ps.getFormat().setQuote(getQuote());
        ps.getFormat().setQuoteEscape(getQuoteEscape());

        return ps;
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
                for (int rowIdx = 0; rowIdx < aRowBlock.size(); rowIdx++)
                {
                    CsvRecord row = aRowBlock.get(rowIdx);
                    if (possiblyDouble && !row.isDoubleOrMissing(i))
                    {
                        possiblyDouble = false;
                    }
                    if (!possiblyDouble)
                    {
                        break;
                    }
                }

                if (possiblyDouble)
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


    private char findDelimChar(String aLine) throws IOException
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
                    // Ragged row: this record has fewer fields than the header declares, so the
                    // cell is absent rather than present-and-empty.
                    //
                    // Fix #161: a blank cell must not depend on the file format. The ordinary
                    // (non-ragged) CSV path below already yields "" for a blank CHARACTER cell,
                    // as do SAS7BDAT, XPT, Dataset-JSON, CDT, XLSX and Parquet, per the house
                    // contract in AbstractDataBuffer.createDataValue ("for STRING we map from
                    // null to empty string"). Mapping the ragged case to MissingValue regardless
                    // of type made the very same blank cell report differently depending only on
                    // whether its row happened to be short.
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
