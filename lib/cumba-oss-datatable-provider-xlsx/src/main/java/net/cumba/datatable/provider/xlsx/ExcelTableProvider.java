package net.cumba.datatable.provider.xlsx;

import com.github.pjfanning.xlsx.StreamingReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
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
import net.cumba.datatable.values.TemporalOrigin;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jspecify.annotations.Nullable;

@CustomLog
public class ExcelTableProvider extends AbstractDataTableProvider
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

    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return ExcelProviderSupplier.FIS;
    }


    @Override
    public IDataTable provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {

        try (InputStream in = aUri.toURL().openStream())
        {
            try (Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096)
                    .open(in))
            {
                Sheet sheet = resolveSheet(workbook, aUri);
                return provide(sheet, aUri);
            }
            catch (IOException ex)
            {
                throw ex;
            }
            catch (Exception ex)
            {
                LOGGER.log(Level.ERROR, ex.getMessage(), ex);
                throw new IOException(ex);
            }
        }

    }


    @Override
    public DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = aUri.toURL().openStream())
        {
            try (Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096)
                    .open(in))
            {
                Sheet sheet = resolveSheet(workbook, aUri);
                return buildMeta(sheet, aUri);
            }
            catch (IOException ex)
            {
                throw ex;
            }
            catch (Exception ex)
            {
                LOGGER.log(Level.ERROR, ex.getMessage(), ex);
                throw new IOException(ex);
            }
        }
    }


    private Sheet resolveSheet(Workbook aWorkbook, URI aUri) throws IOException
    {
        String sheetName = aUri.getFragment();
        if (CDT.isBlankOrNull(sheetName))
        {
            return aWorkbook.getSheetAt(0);
        }
        Sheet sheet = aWorkbook.getSheet(sheetName);
        if (sheet == null)
        {
            throw new IOException("Sheet '" + sheetName + "' not found in workbook!");
        }
        return sheet;
    }


    /**
     * Build a {@link DataTableMeta} from the given streaming sheet using the first
     * {@code guessingRowCount} rows to infer column types, without streaming the remaining row
     * data.
     */
    public DataTableMeta buildMeta(Sheet aSheet, URI aUri) throws IOException
    {
        DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());

        Iterator<Row> rows = aSheet.iterator();

        if (!rows.hasNext())
        {
            throw new IOException("Sheet '" + aSheet.getSheetName() + "' has no rows!");
        }

        Row row = rows.next();
        int[] columnIndices = determineColumns(row);
        ExcelRow headRow = new ExcelRow(row, columnIndices);

        List<ExcelRow> guessRows = new ArrayList<>();
        while (rows.hasNext())
        {
            guessRows.add(new ExcelRow(rows.next(), columnIndices));
            if (guessRows.size() >= guessingRowCount)
            {
                break;
            }
        }

        dtms.setTable(aUri, aSheet.getSheetName());
        dtms.setFileFormat("XLSX", null);

        int colCount = headRow.getColumnCount();
        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        for (int i = 0; i < colCount; i++)
        {
            String name = headRow.getStringValue(i);
            if (name.isBlank())
            {
                String fallback = "V" + (i + 1);
                LOGGER.log(Level.WARNING,
                        "Excel column %d has blank header; using fallback '%s' (uri=%s)"
                                .formatted(i, fallback, aUri));
                name = fallback;
            }
            DataValueType type = dataTypes[i];
            DataTableColumnMeta.DataTableColumnMetaBuilder b = dtms.addColumn(name, type);
            if (dateFormats[i] != null)
            {
                b.displayFormat(dateFormats[i]);
            }
        }

        return dtms.getTableMeta().build();
    }


    public IDataTable provide(Sheet aSheet, URI aUri) throws IOException
    {
        DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());

        Iterator<Row> rows = aSheet.iterator();

        if (!rows.hasNext())
        {
            throw new IOException("Sheet '" + aSheet.getSheetName() + "' has no rows!");
        }

        // head row contains column names
        Row row = rows.next();

        int[] columnIndices = determineColumns(row);

        ExcelRow headRow = new ExcelRow(row, columnIndices);

        List<ExcelRow> guessRows = new ArrayList<>();

        while (rows.hasNext())
        {
            guessRows.add(new ExcelRow(rows.next(), columnIndices));
            if (guessRows.size() >= guessingRowCount)
            {
                break;
            }
        }

        dtms.setTable(aUri, aSheet.getSheetName());
        dtms.setFileFormat("XLSX", null);

        int colCount = headRow.getColumnCount();

        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        for (int i = 0; i < colCount; i++)
        {
            String name = headRow.getStringValue(i);
            if (name.isBlank())
            {
                String fallback = "V" + (i + 1);
                LOGGER.log(Level.WARNING,
                        "Excel column %d has blank header; using fallback '%s' (uri=%s)"
                                .formatted(i, fallback, aUri));
                name = fallback;
            }
            DataValueType type = dataTypes[i];
            DataTableColumnMeta.DataTableColumnMetaBuilder b = dtms.addColumn(name, type);
            if (dateFormats[i] != null)
            {
                b.displayFormat(dateFormats[i]);
            }
        }

        ExcelTableDataParser tp = new ExcelTableDataParser(this, dtms.getTableMeta().build());

        for (ExcelRow er : guessRows)
        {
            tp.addDataRow(er);
        }
        while (rows.hasNext())
        {
            tp.addDataRow(new ExcelRow(rows.next(), columnIndices));
        }

        return tp.completeTable();
    }


    /**
     * Infer column metadata (names + types) for the given streaming {@link Sheet}, sampling up to
     * {@code aSampleRowCount} data rows for type inference. Used by the library tree (F-B16) so
     * column names + best-guess types are visible before the user opens a sheet. Mirrors the logic
     * of {@link #buildMeta(Sheet, URI)} but returns a flat array rather than a
     * {@link DataTableMeta} to avoid coupling library code to {@link DataTableMetaSupport}.
     * <p>
     * Returns an empty array if the sheet has no rows. Streaming readers cannot reset, so this is
     * intended to be called <em>instead of</em> reading the sheet's data — once the
     * {@link Iterator} has been advanced past the header + sample rows, the remaining row data is
     * lost from that {@link Sheet} handle.
     *
     * @param aSheet
     *            streaming sheet to inspect.
     * @param aUri
     *            source URI, used only for log context on blank-header fallback.
     * @param aSampleRowCount
     *            number of data rows to sample; must be ≥ 0.
     * @return inferred {@link DataTableColumnMeta} per detected column. Empty array if sheet is
     *         empty (no header row).
     */
    public static DataTableColumnMeta[] inferColumns(Sheet aSheet, URI aUri, int aSampleRowCount)
    {
        Iterator<Row> rows = aSheet.iterator();
        if (!rows.hasNext())
        {
            return new DataTableColumnMeta[0];
        }
        Row headerRow = rows.next();
        int[] columnIndices = determineColumns(headerRow);
        if (columnIndices.length == 0)
        {
            return new DataTableColumnMeta[0];
        }
        ExcelRow headRow = new ExcelRow(headerRow, columnIndices);

        List<ExcelRow> guessRows = new ArrayList<>();
        while (rows.hasNext() && guessRows.size() < aSampleRowCount)
        {
            guessRows.add(new ExcelRow(rows.next(), columnIndices));
        }

        int colCount = headRow.getColumnCount();
        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        DataTableColumnMeta[] out = new DataTableColumnMeta[colCount];
        for (int i = 0; i < colCount; i++)
        {
            String name = headRow.getStringValue(i);
            if (name.isBlank())
            {
                String fallback = "V" + (i + 1);
                LOGGER.log(Level.WARNING,
                        "Excel column %d has blank header; using fallback '%s' (uri=%s)"
                                .formatted(i, fallback, aUri));
                name = fallback;
            }
            out[i] = DataTableColumnMeta.builder().index(i).name(name).label("").type(dataTypes[i])
                    .displayFormat(dateFormats[i]).build();
        }
        return out;
    }


    /**
     * Determine the column indices of the header row. Treats any non-blank, non-error cell as a
     * header — numeric, boolean, date, and formula cells whose cached result is non-blank all
     * count. The header range terminates at the first blank/empty/error cell, mirroring how Excel
     * itself treats a "used range" run of header labels.
     */
    private static int[] determineColumns(Row aRow)
    {
        int firstIdx = aRow.getFirstCellNum();
        int lastIdx = aRow.getLastCellNum();
        if (firstIdx < 0 || lastIdx <= firstIdx)
        {
            return new int[0];
        }

        List<Integer> headerIndices = new ArrayList<>();
        for (int idx = firstIdx; idx < lastIdx; idx++)
        {
            if (!isHeaderCell(aRow.getCell(idx)))
            {
                // First blank/error cell terminates the contiguous header range.
                break;
            }
            headerIndices.add(idx);
        }
        return headerIndices.stream().mapToInt(Integer::intValue).toArray();
    }


    /**
     * A cell is considered a header if it is present, not blank, not an error, and (for formula
     * cells) the cached formula result is not blank or error. Used by
     * {@link #determineColumns(Row)} to decide where the header range stops.
     */
    private static boolean isHeaderCell(Cell aCell)
    {
        if (aCell == null)
        {
            return false;
        }
        CellType t = aCell.getCellType();
        if (t == CellType.BLANK || t == CellType.ERROR)
        {
            return false;
        }
        if (t == CellType.STRING)
        {
            String s = aCell.getStringCellValue();
            return s != null && !s.isBlank();
        }
        if (t == CellType.FORMULA)
        {
            CellType cached = aCell.getCachedFormulaResultType();
            if (cached == CellType.BLANK || cached == CellType.ERROR)
            {
                return false;
            }
            if (cached == CellType.STRING)
            {
                String s = aCell.getStringCellValue();
                return s != null && !s.isBlank();
            }
            // Numeric / boolean formula result is a valid header.
            return true;
        }
        // NUMERIC, BOOLEAN — present and not blank, accept as header.
        return true;
    }


    private static DataValueType[] determineTypes(List<ExcelRow> aRowBlock, int aColumnCount)
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
                    ExcelRow row = aRowBlock.get(rowIdx);
                    if (!row.isNumberOrMissing(i))
                    {
                        possiblyDouble = false;
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
                LOGGER.log(Level.DEBUG, "Type detection error for column {0}: {1}", i,
                        ex.getMessage());
            }
            res[i] = dt;
        }
        return res;
    }


    /**
     * F-B15: per-column displayFormat hint inferred from whether any sample cell in the column was
     * date-formatted in the source workbook. Excel cells store dates as numeric serials with a cell
     * style; we convert them to SAS-datetime-seconds at extraction time so the column stays numeric
     * (DOUBLE) and attach {@code E8601DT.} so the format catalog renders them as ISO datetimes.
     * <p>
     * Returns {@code null} per column where no date-formatted cell was observed. Non-date columns
     * are unaffected.
     */
    private static String[] inferDateFormats(List<ExcelRow> aRowBlock, int aColumnCount)
    {
        String[] res = new String[aColumnCount];
        for (int i = 0; i < aColumnCount; i++)
        {
            for (ExcelRow r : aRowBlock)
            {
                if (r.isDate(i))
                {
                    res[i] = "E8601DT.";
                    break;
                }
            }
        }
        return res;
    }

    private static class ExcelRow
    {

        /**
         * The datetime epoch, 1960-01-01 00:00:00 UTC, in milliseconds-since-1970-01-01-UTC. The
         * format catalog renders {@code E8601DT.} as ISO datetime from this base; we convert
         * Excel-serial-derived {@code java.util.Date} values into SAS-datetime-seconds here so a
         * date-formatted Excel column comes through as a {@code DOUBLE} that displays as ISO.
         *
         * <p>
         * F-prov-14: delegates to {@link TemporalOrigin#ORIGIN_EPOCH_MILLI}, the one place the
         * origin is defined. The literal {@code -315619200000L} that used to stand here is now
         * derived from that single {@code LocalDate}.
         * </p>
         */
        private static final long SAS_DATETIME_EPOCH_MILLIS = TemporalOrigin.ORIGIN_EPOCH_MILLI;

        private final @Nullable Object[] values;

        /** Parallel array: true when the source cell was numeric AND date-formatted. */
        private final boolean[] isDate;

        ExcelRow(Row aRow, int[] aColumnIndices)
        {
            values = new Object[aColumnIndices.length];
            isDate = new boolean[aColumnIndices.length];
            for (int i = 0; i < aColumnIndices.length; i++)
            {
                int idx = aColumnIndices[i];
                Cell c = aRow.getCell(idx);
                if (c != null && c.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(c))
                {
                    isDate[i] = true;
                }
                values[i] = getCellValue(c);
            }
        }


        int getColumnCount()
        {
            return values.length;
        }


        boolean isDate(int aColumn)
        {
            return isDate[aColumn];
        }


        String getStringValue(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            return val != null ? val.toString() : "";
        }


        @SuppressWarnings("PMD.EmptyCatchBlock")
        double getDoubleValue(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            if (val == null)
            {
                return Double.NaN;
            }
            if (val instanceof Number num)
            {
                return num.doubleValue();
            }

            try
            {
                return Double.parseDouble(val.toString());
            }
            catch (NumberFormatException _)
            {
                // not a number
            }
            return Double.NaN;
        }


        @SuppressWarnings("PMD.EmptyCatchBlock")
        boolean isNumberOrMissing(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            if (val == null)
            {
                return true;
            }

            if (val instanceof Number)
            {
                return true;
            }

            try
            {
                Double.parseDouble(val.toString());
                return true;
            }
            catch (NumberFormatException _)
            {
                // not a number
            }

            return false;
        }


        private @Nullable Object getCellValue(@Nullable Cell aCell)
        {
            try
            {
                if (aCell == null)
                {
                    return null;
                }

                return switch (aCell.getCellType())
                {
                case NUMERIC -> numericOrDate(aCell);
                case STRING -> CDT.tri(aCell.getStringCellValue());
                // F-B15: BOOLEAN as Boolean.toString — keeps boolean columns flagged as such
                // by isNumberOrMissing (parseable as 0/1 it is NOT, so they stay string-typed,
                // which matches the existing behaviour for mixed sheets).
                case BOOLEAN -> Boolean.toString(aCell.getBooleanCellValue());
                // F-B15: surface ERROR cells via POI's actual Excel error code string ("#DIV/0!",
                // "#N/A", "#REF!", …) rather than a generic stand-in. The error code is what
                // Excel itself displays and gives the user a real diagnosis hook instead of
                // a hand-rolled token; silent drop to null was the original bug.
                case ERROR -> errorToken(aCell);
                case FORMULA -> switch (aCell.getCachedFormulaResultType())
                {
                case STRING -> CDT.tri(aCell.getStringCellValue());
                case NUMERIC -> numericOrDate(aCell);
                case BOOLEAN -> Boolean.toString(aCell.getBooleanCellValue());
                case ERROR -> errorToken(aCell);
                default -> null;
                };
                default -> null;
                };
            }
            catch (Exception ex)
            {
                LOGGER.log(Level.DEBUG, ex.getMessage(), ex);
                return null;
            }
        }


        /**
         * Map an ERROR cell to the corresponding Excel error string (e.g. {@code #DIV/0!},
         * {@code #N/A}, {@code #REF!}). Falls back to {@code "#ERROR"} if the POI byte code is
         * unknown. Using the actual Excel error code reduces collision risk with user-authored text
         * — a user would have to literally type one of Excel's reserved error strings to be
         * indistinguishable from a real error cell.
         */
        private static String errorToken(Cell aCell)
        {
            try
            {
                byte code = aCell.getErrorCellValue();
                org.apache.poi.ss.usermodel.FormulaError fe = org.apache.poi.ss.usermodel.FormulaError
                        .forInt(code);
                return fe.getString();
            }
            catch (RuntimeException ex)
            {
                LOGGER.log(Level.DEBUG, "Failed to resolve POI error code: " + ex.getMessage(), ex);
                return "#ERROR";
            }
        }


        /**
         * Returns the numeric value of a NUMERIC cell. Date-formatted cells are converted from
         * their Excel-serial form into SAS-datetime-seconds (seconds since 1960-01-01 UTC) so the
         * column stays numeric ({@code DOUBLE}) while the column-level {@code E8601DT.}
         * displayFormat — applied at metadata-build time — renders the value as an ISO datetime.
         * Excel has no separate date type — dates are just numerics with a date-shaped cell style —
         * so detecting via {@link DateUtil#isCellDateFormatted} is the only signal. F-B15.
         * <p>
         * Time-only cells (formats like {@code h:mm:ss} with no date components) follow the same
         * code path; they convert into a negative SAS seconds value because Excel anchors them to
         * its 1899/1900 epoch. ISO rendering of such cells looks unusual ({@code 1899-12-30T…}) but
         * the column remains numeric and round-trippable; clinical-trial workbooks almost never use
         * time-only columns.
         */
        @SuppressWarnings("JavaUtilDate") // POI's Cell.getDateCellValue() returns java.util.Date
        private @Nullable Object numericOrDate(Cell aCell)
        {
            if (DateUtil.isCellDateFormatted(aCell))
            {
                java.util.Date d = aCell.getDateCellValue();
                return d != null ? (d.getTime() - SAS_DATETIME_EPOCH_MILLIS) / 1000.0 : null;
            }
            return aCell.getNumericCellValue();
        }
    }


    private static class ExcelTableDataParser extends AbstractTableDataParser<ExcelRow>
    {

        ExcelTableDataParser(@NonNull IDataTableProvider aProvider, @NonNull DataTableMeta aMeta)
        {
            super(aProvider, aMeta);
        }


        @Override
        protected void addData2Column(List<ExcelRow> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            for (int i = 0; i < aRowSlice.size(); i++)
            {
                ExcelRow row = aRowSlice.get(i);

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
                    aDataColumn.addElement(row.getStringValue(aColumnIndex));
                    break;
                }
            }
        }

    }
}
