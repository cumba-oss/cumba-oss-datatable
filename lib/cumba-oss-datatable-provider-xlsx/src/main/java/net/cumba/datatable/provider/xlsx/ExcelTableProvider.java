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
import org.apache.poi.ss.util.CellReference;
import org.jspecify.annotations.Nullable;

@CustomLog
public class ExcelTableProvider extends AbstractDataTableProvider
{

    /**
     * The default number of rows to be used to guess the row types.
     */
    public static final int DEFAULT_GUESS_ROW_COUNT = 10_240;

    /**
     * The displayFormat token that marks a column as a date/datetime column (Excel-serial values
     * converted to SAS-datetime-seconds). Shared between {@link #inferDateFormats} (which decides
     * the column) and {@link ExcelTableDataParser#addData2Column} (which must then convert EVERY
     * numeric cell in that column the same way, not just the ones that happen to carry a
     * date-formatted cell style themselves — see {@link ExcelRow#getDateValue}).
     */
    private static final String DATE_DISPLAY_FORMAT = "E8601DT.";

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
        int[] columnIndices = headerColumns(aSheet, row);
        if (columnIndices.length == 0)
        {
            // F-nonblank-header-gap: see noHeaderColumns for what shapes land here and why this
            // must not be left to the downstream parser construction to report.
            throw noHeaderColumns(aSheet);
        }
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
        dtms.setDatasetSize(aUri);

        int colCount = headRow.getColumnCount();
        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        for (int i = 0; i < colCount; i++)
        {
            String name = headerName(headRow, i, columnIndices[i], aSheet.getSheetName(), aUri);
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

        int[] columnIndices = headerColumns(aSheet, row);
        if (columnIndices.length == 0)
        {
            throw noHeaderColumns(aSheet);
        }

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
        dtms.setDatasetSize(aUri);

        int colCount = headRow.getColumnCount();

        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        for (int i = 0; i < colCount; i++)
        {
            String name = headerName(headRow, i, columnIndices[i], aSheet.getSheetName(), aUri);
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
            String name = headerName(headRow, i, columnIndices[i], aSheet.getSheetName(), aUri);
            out[i] = DataTableColumnMeta.builder().index(i).name(name).label("").type(dataTypes[i])
                    .displayFormat(dateFormats[i]).build();
        }
        return out;
    }


    /**
     * The one error raised when the row the sheet iterator hands back as the header row carries no
     * usable header cell at all - e.g. a blank or title/spacer row above the real header, which is
     * common in exported clinical-trial reports, or a row element that carries only a row height
     * (ECMA-376 permits {@code <row r="1" ht="30" customHeight="1"/>} with no cells, and openpyxl
     * emits exactly that for a styled spacer row).
     * <p>
     * Built in one place so {@link #buildMeta(Sheet, URI)} and {@link #provide(Sheet, URI)} cannot
     * drift apart: before this existed only {@code buildMeta} raised it, and {@code provide} let
     * the downstream parser construction blow up with an unrelated
     * {@code IllegalArgumentException("At least 1 column must be specified!")} that named neither
     * the sheet nor the header row (F-nonblank-header-gap).
     */
    private static IOException noHeaderColumns(Sheet aSheet)
    {
        return new IOException("Sheet '" + aSheet.getSheetName() + "' header row has no columns!");
    }


    /**
     * {@link #determineColumns(Row)} for the two table entry points, which declare
     * {@code throws IOException}: a header cell whose stored content the streaming reader cannot
     * read at all (a {@code t="n"} cell whose {@code <v>} is not a number, as a hand-edited or
     * third-party export can leave behind) throws a {@code RuntimeException} out of
     * {@code Cell.getCellType()} itself, inside {@code isHeaderCell}. Unwrapped, that reached the
     * caller as a bare {@code NumberFormatException} naming neither the sheet nor the column — the
     * same shape as the {@code IllegalArgumentException("At least 1 column must be specified!")}
     * that {@link #noHeaderColumns} exists to replace.
     * <p>
     * ⚠ Deliberately NOT applied to {@link #inferColumns}: {@link ExcelLibraryProvider} catches a
     * {@code RuntimeException} per sheet there, so one unreadable sheet leaves the rest of the
     * library openable. Converting it to {@code IOException} there would abort the whole library
     * open instead.
     */
    private static int[] headerColumns(Sheet aSheet, Row aRow) throws IOException
    {
        try
        {
            return determineColumns(aRow);
        }
        catch (RuntimeException ex)
        {
            throw new IOException("Sheet '" + aSheet.getSheetName()
                    + "' header row could not be read: " + ex.getMessage(), ex);
        }
    }


    /**
     * The name of header column {@code aIndex}: the header cell's rendered text, or the positional
     * fallback {@code V<aIndex + 1>} when that text is blank, logged once at {@code WARNING} naming
     * the Excel column it applies to.
     * <p>
     * ⚠ <b>This fallback is REACHABLE and must not be deleted as dead code.</b> It is not merely
     * the mirror image of {@link #isHeaderCell(Cell)}'s blankness test: {@code isHeaderCell}
     * decides from the cell's TYPE (and, for a formula, its cached result type), while the name
     * comes from {@link ExcelRow#getStringValue}, which yields {@code ""} whenever
     * {@link ExcelRow#getCellValue} had to degrade the cell to {@code null}. A header cell can
     * therefore be accepted and still have no readable name. The shape that does it in practice, on
     * a workbook that is valid OOXML and that pandas/openpyxl read without complaint: a formula
     * header cell whose cached value element is EMPTY ({@code <f>…</f><v></v>}), which is what
     * openpyxl - and therefore {@code pandas.DataFrame.to_excel} - writes for every formula, since
     * neither evaluates formulas. The streaming reader reports that cell as {@code FORMULA} with
     * cached type {@code NUMERIC} (so it is a valid header) and then throws
     * {@code NumberFormatException("empty String")} from {@code getNumericCellValue()}, so the name
     * degrades to blank. Verified end to end through {@code StreamingReader} on an
     * openpyxl-produced file; plain {@code XSSFWorkbook} returns {@code 0.0} for the same cell
     * instead, which is why probing with non-streaming POI makes this path look unreachable.
     * <p>
     * Dropping the column instead of naming it positionally would silently discard a column that
     * has data. {@code pandas.read_excel} answers the same file the same way, naming that column
     * {@code Unnamed: 1} and keeping its values.
     *
     * @param aHeadRow
     *            the header row.
     * @param aIndex
     *            index of the column within the accepted header range (0-based).
     * @param aSheetColumnIndex
     *            the cell's own 0-based column index in the sheet, which is NOT {@code aIndex} when
     *            the header range does not start at column A - only used for the log message, so
     *            that it points at the cell the user has to look at.
     * @param aSheetName
     *            name of the sheet, for the log message: the URI usually carries no sheet fragment
     *            on the table paths (sheet 0 is resolved positionally), so without this the warning
     *            would not say which sheet of a multi-sheet workbook it is about.
     * @param aUri
     *            source URI, for log context.
     * @return a non-blank column name.
     */
    private static String headerName(ExcelRow aHeadRow, int aIndex, int aSheetColumnIndex,
            String aSheetName, URI aUri)
    {
        String name = aHeadRow.getStringValue(aIndex);
        if (!name.isBlank())
        {
            return name;
        }
        String fallback = "V" + (aIndex + 1);
        LOGGER.log(Level.WARNING,
                "Excel column %s of sheet '%s' has a blank name; using fallback '%s' (uri=%s)"
                        .formatted(CellReference.convertNumToColString(aSheetColumnIndex),
                                aSheetName, fallback, aUri));
        return fallback;
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
                // Defensive only, and deliberately kept: every ExcelRow in aRowBlock is built
                // from the same columnIndices array and aColumnCount is that array's length, so
                // the isNumberOrMissing lookups above cannot actually throw. If that ever stops
                // holding, one odd column degrades to STRING rather than failing the whole read.
                // Not coverable by any input (UNREACHABLE-BY-DESIGN).
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
                    res[i] = DATE_DISPLAY_FORMAT;
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

        /**
         * Parallel array: the SAS-datetime-seconds equivalent of this cell's raw Excel serial,
         * computed for EVERY {@code NUMERIC} cell regardless of that cell's own style — {@code NaN}
         * for a non-numeric cell, a missing cell, or one {@link Cell#getDateCellValue()} could not
         * convert.
         * <p>
         * Bug fix (H3, clinical-path hardening wave 3): a column is declared a date column when
         * {@code inferDateFormats} sees ANY sampled cell date-formatted, but the OLD code only
         * converted a cell to SAS-seconds when THAT cell's own style was date-formatted — a column
         * with inconsistent per-cell styling (a common real shape: some header/data cells lose
         * their date format after a copy/paste or a partial re-format) silently mixed raw Excel
         * serials with SAS-seconds under one {@code E8601DT.} column, misreading a raw serial as a
         * date up to ~63 years off. {@link Cell#getDateCellValue()} converts a {@code NUMERIC}
         * cell's value correctly regardless of that cell's OWN style (verified empirically), so
         * precomputing it here for every numeric cell — and having
         * {@link ExcelTableDataParser#addData2Column} choose this array over
         * {@link #getDoubleValue} once the COLUMN is decided to be a date column — makes the
         * conversion consistent for the whole column instead of per-cell.
         */
        private final double[] dateSasSeconds;

        ExcelRow(Row aRow, int[] aColumnIndices)
        {
            values = new Object[aColumnIndices.length];
            isDate = new boolean[aColumnIndices.length];
            dateSasSeconds = new double[aColumnIndices.length];
            java.util.Arrays.fill(dateSasSeconds, Double.NaN);
            for (int i = 0; i < aColumnIndices.length; i++)
            {
                int idx = aColumnIndices[i];
                Cell c = aRow.getCell(idx);
                // Bug fix (H4): a cell whose stored content the streaming reader cannot convert to
                // the type it claims (a numeric cell with a non-numeric cached `<v>`, or an unknown
                // `t` attribute from a third-party writer) can throw from getCellType() /
                // isCellDateFormatted() themselves — BEFORE getCellValue's own try/catch ever runs.
                // That let one bad cell anywhere in the sheet abort the WHOLE read, defeating the
                // per-cell degrade-to-missing design getCellValue exists for. Guard this probe with
                // its own try/catch, exactly like getCellValue's, so a bad cell degrades only
                // itself.
                try
                {
                    if (c != null && effectiveType(c) == CellType.NUMERIC)
                    {
                        if (DateUtil.isCellDateFormatted(c))
                        {
                            isDate[i] = true;
                        }
                        dateSasSeconds[i] = dateSecondsOf(c);
                    }
                }
                catch (RuntimeException ex)
                {
                    LOGGER.log(Level.DEBUG, "Cell type/date probe failed for column {0}: {1}", i,
                            ex.getMessage());
                }
                values[i] = getCellValue(c);
            }
        }


        /**
         * The cell type that decides how the VALUE is read: a {@code FORMULA} cell's own type is
         * always {@code FORMULA}, so the type that matters is its CACHED result type — which is
         * what {@link #getCellValue} already switches on.
         * <p>
         * Bug fix (H7, clinical-path hardening wave 3 continuation): the date probe in
         * {@link ExcelRow}'s constructor used to test {@code c.getCellType() == NUMERIC} directly,
         * so a formula cell with a cached NUMERIC result - {@code =BASELINE+7}, the ordinary way a
         * computed visit date exists in an exported workbook - skipped the probe entirely: its
         * {@code isDate} stayed false and its {@code dateSasSeconds} stayed {@code NaN}, while
         * {@link #getCellValue} read its raw Excel serial. Two silent misreads followed, both of
         * clinical values:
         * <ul>
         * <li>a date column mixing literal and computed dates got {@code E8601DT.} from the literal
         * ones, so {@link ExcelTableDataParser#addData2Column} took the (NaN) converted value for
         * the computed rows and reported a present, correct date as MISSING;</li>
         * <li>an all-computed date column had no date-flagged cell at all, so
         * {@link ExcelTableProvider#inferDateFormats} gave it no {@code E8601DT.} and the raw
         * serial (~45274) was handed to the user as a plain number instead of a date.</li>
         * </ul>
         * POI answers {@link DateUtil#isCellDateFormatted} and {@link Cell#getDateCellValue()}
         * correctly for such a cell; the provider simply never asked. This also restores the
         * invariant {@link #dateSasSeconds} documents - computed for EVERY cell whose value is
         * numeric, {@code NaN} only for a non-numeric cell, a missing cell, or one POI could not
         * convert.
         */
        private static CellType effectiveType(Cell aCell)
        {
            CellType t = aCell.getCellType();
            return t == CellType.FORMULA ? aCell.getCachedFormulaResultType() : t;
        }


        /**
         * Converts a {@code NUMERIC} cell's value to SAS-datetime-seconds regardless of that cell's
         * own style — see {@link #dateSasSeconds}. Returns {@code NaN} rather than throwing when
         * POI cannot convert the value (out-of-range serial, or any other
         * {@link RuntimeException}), so one bad cell degrades that cell to missing rather than
         * aborting the whole row — consistent with {@link #getCellValue}'s error handling.
         * <p>
         * ⚠ No input is known to enter that catch, for TWO reasons, and the second is the
         * load-bearing one: {@code getDateCellValue()} does not throw for any value the reader can
         * type as numeric (measured through {@code StreamingReader}: {@code 1e308} saturates at
         * year 5881510, a negative serial returns {@code null}); and a cell whose stored value
         * cannot be parsed at all makes {@link DateUtil#isCellDateFormatted} throw FIRST, one line
         * earlier, where the constructor's own probe catch takes it. Re-check both if that call
         * order ever changes.
         */
        // [JavaUtilDate] suppressed for the whole method, not the declaration: POI's
        // Cell.getDateCellValue() returns a java.util.Date and there is no java.time overload, so
        // both the call AND the getTime() that reads it are forced. The suppression used to sit on
        // the local variable, which left the getTime() on the next line reported.
        @SuppressWarnings("JavaUtilDate")
        private static double dateSecondsOf(Cell aCell)
        {
            try
            {
                java.util.Date d = aCell.getDateCellValue();
                return d != null ? (d.getTime() - SAS_DATETIME_EPOCH_MILLIS) / 1000.0 : Double.NaN;
            }
            catch (RuntimeException ex)
            {
                LOGGER.log(Level.DEBUG, "Date conversion error: {0}", ex.getMessage());
                return Double.NaN;
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


        /**
         * The SAS-datetime-seconds value of this cell, for a column {@code inferDateFormats} has
         * decided IS a date column — computed for every numeric cell regardless of that cell's own
         * style (see {@link #dateSasSeconds}), which is what keeps a whole date column internally
         * consistent even when not every cell in it carries the date cell style.
         */
        double getDateValue(int aColumn)
        {
            return dateSasSeconds[aColumn];
        }


        String getStringValue(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            return val != null ? val.toString() : "";
        }


        /**
         * Returns the numeric value of this cell for a column that {@link #isNumberOrMissing} has
         * classified as DOUBLE. Falls back to parsing the cell's text when it is not itself a
         * {@link Number} — {@code isNumberOrMissing} no longer lets a text cell make the column
         * DOUBLE in the first place, but the {@code guessingRowCount}-bounded sample can still miss
         * a text cell that appears only in a row past the sample: this parse-or-{@code NaN}
         * fallback is what keeps such a later row from throwing rather than silently reporting the
         * value as missing. See the fix note on {@link #isNumberOrMissing} for why the sample
         * itself must not rely on text parsing.
         */
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


        /**
         * Whether this cell can participate in a DOUBLE-typed column: it is missing ({@code null})
         * or the cell itself is Excel-{@code NUMERIC} (a real {@link Number}, from
         * {@link #rawNumeric}).
         * <p>
         * <b>Deliberately does NOT accept a numeric-looking text cell.</b> A cell Excel stored as
         * TEXT (e.g. a subject/site ID kept as text specifically to preserve a leading zero, such
         * as {@code "0001"}) must never promote a column to DOUBLE merely because its digits happen
         * to parse — doing so silently destroyed the very thing the producer used TEXT formatting
         * to protect: {@code determineTypes} classified an all-text {@code "0001"}/"0002"/"0010"}
         * column as DOUBLE, and {@link #getDoubleValue} then re-parsed each cell to
         * {@code 1.0}/{@code 2.0}/ {@code 10.0} — an irreversible data-fidelity bug, confirmed
         * against an independently (openpyxl-)produced fixture whose cells are genuinely text-typed
         * (not merely numeric-looking). A real Excel-NUMERIC cell is unaffected by this change;
         * only a cell the source workbook itself typed as text is now correctly kept out of DOUBLE
         * classification.
         */
        boolean isNumberOrMissing(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            return val == null || val instanceof Number;
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
                case NUMERIC -> rawNumeric(aCell);
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
                case NUMERIC -> rawNumeric(aCell);
                case BOOLEAN -> Boolean.toString(aCell.getBooleanCellValue());
                // Bug fix (H6): a FORMULA cell's own CellType is FORMULA, never ERROR, even when
                // its CACHED result is an error — errorToken()'s getErrorCellValue() rejects any
                // cell whose type isn't literally ERROR and throws for every one of these, so every
                // formula-cached error (by far the common way an error cell arises in a real
                // spreadsheet: =1/0, a broken VLOOKUP, a reference to a deleted column) silently
                // came back as the generic "#ERROR" fallback instead of the real code. Use
                // formulaErrorToken(), which reads the code from getStringCellValue() instead.
                case ERROR -> formulaErrorToken(aCell);
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
         * Map a directly ERROR-typed cell (a literal error, e.g. one authored via
         * {@code Cell.setCellErrorValue}) to the corresponding Excel error string (e.g.
         * {@code #DIV/0!}, {@code #N/A}, {@code #REF!}). Falls back to {@code "#ERROR"} if the POI
         * byte code is unknown. Using the actual Excel error code reduces collision risk with
         * user-authored text — a user would have to literally type one of Excel's reserved error
         * strings to be indistinguishable from a real error cell.
         * <p>
         * NOT used for a FORMULA cell whose cached result is an error — see
         * {@link #formulaErrorToken}, which is the dominant real-world case (a formula error, not a
         * literal one).
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
         * Map a FORMULA cell whose CACHED result is an error to the corresponding Excel error
         * string. This is the dominant real-world way an error cell arises — {@code =1/0}, a broken
         * {@code VLOOKUP}, a reference to a column someone deleted — nobody types a literal error
         * constant.
         * <p>
         * Bug fix (H6): a FORMULA cell's OWN {@link Cell#getCellType()} is {@code FORMULA}, never
         * {@code ERROR}, even when {@link Cell#getCachedFormulaResultType()} says {@code ERROR}.
         * {@link Cell#getErrorCellValue()} rejects any cell whose {@code getCellType()} isn't
         * literally {@code ERROR} and throws {@link IllegalStateException} for every one of these —
         * confirmed against the actual streaming reader this module reads through, not just plain
         * POI. {@link Cell#getStringCellValue()} is what works instead, returning POI's own label
         * for the cached error, {@code "ERROR:  #DIV/0!"}; strip that prefix and resolve the
         * remainder through {@link org.apache.poi.ss.usermodel.FormulaError#forString} so a label
         * POI doesn't recognise still falls back to the generic {@code "#ERROR"} rather than
         * surfacing raw, unvalidated text.
         */
        private static String formulaErrorToken(Cell aCell)
        {
            try
            {
                String raw = aCell.getStringCellValue();
                String code = raw.replaceFirst("(?i)^\\s*ERROR:\\s*", "").trim();
                return org.apache.poi.ss.usermodel.FormulaError.forString(code).getString();
            }
            catch (RuntimeException ex)
            {
                LOGGER.log(Level.DEBUG,
                        "Failed to resolve formula-cached error code: " + ex.getMessage(), ex);
                return "#ERROR";
            }
        }


        /**
         * Returns the RAW numeric value of a NUMERIC cell — the Excel serial, not converted even
         * when the cell happens to be date-formatted.
         * <p>
         * Bug fix (H3, clinical-path hardening wave 3): this method used to convert a
         * date-formatted cell to SAS-datetime-seconds right here, per cell. That made a date
         * COLUMN's values inconsistent whenever not every cell in it carried the date cell style (a
         * common real shape — a copy/paste or partial re-format loses the style on some cells while
         * the data stays numeric): the column-level {@code E8601DT.} displayFormat is decided from
         * "ANY sampled cell is date-formatted" ({@link ExcelTableProvider#inferDateFormats}), so a
         * non-date-styled straggler cell's RAW serial was read back by the format catalog as if it
         * WERE SAS-seconds — silently up to ~63 years wrong. The conversion now happens uniformly
         * at the column level instead: see {@link #dateSasSeconds} /
         * {@link ExcelTableDataParser#addData2Column}, which choose the converted value for every
         * cell in a column once that column is decided to be a date column, regardless of any one
         * cell's own style.
         */
        private @Nullable Object rawNumeric(Cell aCell)
        {
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
                    // Bug fix (H3): once the COLUMN is decided to be a date column, every numeric
                    // cell in it must be converted the same way — not just the ones whose own cell
                    // style happens to be date-formatted. See ExcelRow#dateSasSeconds.
                    boolean isDateColumn = DATE_DISPLAY_FORMAT
                            .equals(aMetaColumn.getDisplayFormat());
                    double dblVal = isDateColumn ? row.getDateValue(aColumnIndex)
                            : row.getDoubleValue(aColumnIndex);
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
