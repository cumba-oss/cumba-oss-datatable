package net.cumba.datatable.provider.xlsx;

import com.github.pjfanning.xlsx.StreamingReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerArray;
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
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.datatable.values.TemporalOrigin;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
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
     * date-formatted cell style themselves — see {@link ExcelRow#dateCell}).
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

        try (InputStream in = URIHelper.openStream(aUri))
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
        try (InputStream in = URIHelper.openStream(aUri))
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
        BlankNumericProbe probe = new BlankNumericProbe();
        ExcelRow headRow = new ExcelRow(row, columnIndices, probe);

        List<ExcelRow> guessRows = new ArrayList<>();
        while (rows.hasNext())
        {
            guessRows.add(new ExcelRow(rows.next(), columnIndices, probe));
            if (guessRows.size() >= guessingRowCount)
            {
                break;
            }
        }

        dtms.setTable(aUri, aSheet.getSheetName());
        dtms.setFileFormat("XLSX", null);
        // Text in an XLSX package is UTF-8 XML (E4, owner ruling 2026-10-07).
        dtms.setEncoding(StandardCharsets.UTF_8.name(),
                DataTableMetaSupport.ENCODING_SOURCE_FORMAT);
        dtms.setDatasetSize(aUri);

        int colCount = headRow.getColumnCount();
        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        String[] names = headerNames(row, headRow, columnIndices, aSheet.getSheetName(), aUri);
        for (int i = 0; i < colCount; i++)
        {
            String name = names[i];
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

        BlankNumericProbe probe = new BlankNumericProbe();
        ExcelRow headRow = new ExcelRow(row, columnIndices, probe);

        List<ExcelRow> guessRows = new ArrayList<>();

        while (rows.hasNext())
        {
            guessRows.add(new ExcelRow(rows.next(), columnIndices, probe));
            if (guessRows.size() >= guessingRowCount)
            {
                break;
            }
        }

        dtms.setTable(aUri, aSheet.getSheetName());
        dtms.setFileFormat("XLSX", null);
        // Text in an XLSX package is UTF-8 XML (E4, owner ruling 2026-10-07).
        dtms.setEncoding(StandardCharsets.UTF_8.name(),
                DataTableMetaSupport.ENCODING_SOURCE_FORMAT);
        dtms.setDatasetSize(aUri);

        int colCount = headRow.getColumnCount();

        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        String[] names = headerNames(row, headRow, columnIndices, aSheet.getSheetName(), aUri);
        for (int i = 0; i < colCount; i++)
        {
            String name = names[i];
            DataValueType type = dataTypes[i];
            DataTableColumnMeta.DataTableColumnMetaBuilder b = dtms.addColumn(name, type);
            if (dateFormats[i] != null)
            {
                b.displayFormat(dateFormats[i]);
            }
        }

        ExcelTableDataParser tp = new ExcelTableDataParser(this, dtms.getTableMeta().build(),
                aSheet.getSheetName(), columnIndices, aUri);

        for (ExcelRow er : guessRows)
        {
            tp.addDataRow(er);
        }
        while (rows.hasNext())
        {
            tp.addDataRow(new ExcelRow(rows.next(), columnIndices, probe));
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
        BlankNumericProbe probe = new BlankNumericProbe();
        ExcelRow headRow = new ExcelRow(headerRow, columnIndices, probe);

        List<ExcelRow> guessRows = new ArrayList<>();
        while (rows.hasNext() && guessRows.size() < aSampleRowCount)
        {
            guessRows.add(new ExcelRow(rows.next(), columnIndices, probe));
        }

        int colCount = headRow.getColumnCount();
        DataValueType[] dataTypes = determineTypes(guessRows, colCount);
        String[] dateFormats = inferDateFormats(guessRows, colCount);

        DataTableColumnMeta[] out = new DataTableColumnMeta[colCount];
        String[] names = headerNames(headerRow, headRow, columnIndices, aSheet.getSheetName(),
                aUri);
        for (int i = 0; i < colCount; i++)
        {
            String name = names[i];
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
     * The names of the header columns: each header cell's rendered text, or the positional fallback
     * {@code V<index + 1>} when that text is blank or the cell is an error ({@code #REF!}), logged
     * once per column at {@code WARNING} naming the Excel column it applies to and why; a numeric
     * header whose rendered name is already taken gets {@code <name>_<k>} (see below).
     * <p>
     * ⚠ <b>This fallback is REACHABLE and must not be deleted as dead code.</b> It is not merely
     * the mirror image of {@link #isHeaderCell(Cell)}'s blankness test: {@code isHeaderCell}
     * decides from the cell's TYPE (and, for a formula, its cached result type), while the name
     * comes from {@link ExcelRow#getStringValue}, which yields {@code ""} whenever
     * {@link ExcelRow#getCellValue} has no value for the cell ({@code null} or an
     * {@link Unreadable}). A header cell can therefore be accepted and still have no readable name.
     * The shape that does it in practice, on a workbook that is valid OOXML and that
     * pandas/openpyxl read without complaint: a formula header cell whose cached value element is
     * EMPTY ({@code <f>…</f><v></v>}), which is what openpyxl - and therefore
     * {@code pandas.DataFrame.to_excel} - writes for every formula, since neither evaluates
     * formulas. The streaming reader reports that cell as {@code FORMULA} with cached type
     * {@code NUMERIC} (so it is a valid header) whose stored value is blank, and
     * {@link ExcelRow#isBlankNumeric} makes {@link ExcelRow#getCellValue} read such a cell as
     * {@link Unreadable#UNCALCULATED_FORMULA}, so the name degrades to blank. That guard is what
     * decides it, NOT the reader: excel-streaming-reader up to 5.3.0 threw
     * {@code NumberFormatException("empty String")} from {@code getNumericCellValue()} for a blank
     * stored value (and the provider degraded the cell), but 5.3.2 returns {@code 0.0}, as plain
     * {@code XSSFWorkbook} always did - without the guard every such header would be named
     * {@code "0.0"} and the second one would fail the whole sheet with a duplicate column name.
     * Verified end to end through {@code StreamingReader} on an openpyxl-produced file.
     * <p>
     * Dropping the column instead of naming it positionally would silently discard a column that
     * has data. {@code pandas.read_excel} answers the same file the same way, naming that column
     * {@code Unnamed: 1} and keeping its values.
     * <p>
     * An ERROR header cell - a literal {@code #REF!}, or a formula whose cached result is an error
     * - is named positionally as well, never after its error token (owner ruling Q37(ii),
     * 2026-10-12: <i>"I would like to get this column in with a temp name, so nothing is lost,
     * additionally log with warning."</i>).
     * <p>
     * A fallback never collides: a text header is never renamed (a duplicate text name fails the
     * whole sheet in {@code DataTableMetaSupport}), so a fallback that equals, ignoring case, a
     * header name of the sheet or an earlier fallback takes the next free {@code V<n>_<k>}
     * ({@code k = 1, 2, …}).
     * <p>
     * A NUMERIC header never collides either: one whose rendered name ({@link ExcelRow#numberText})
     * equals, ignoring case, a text header of the sheet - before or after it - or an earlier
     * numeric header takes the next free {@code <name>_<k>}, logged at {@code WARNING} (ECT review
     * ② M1, coordinator ruling (b), 2026-10-16). A number reads as its plain decimal digits since
     * ECT-Q2 ⓐ, so a numeric {@code 1} now spells a text header {@code "1"}, and {@code 0} and
     * {@code -0.0} spell the same {@code "0"}; while a number read {@link Double#toString}
     * ({@code "1.0"}, {@code "-0.0"}) those sheets loaded, and
     * {@code PLAN-bugfix-empty-column-type} promises that no sheet that loads today stops loading.
     * A text header always keeps its own name, and two text headers that spell the same name still
     * fail the sheet, as they always did.
     *
     * @param aRow
     *            the header row as the reader gives it, for the cell types.
     * @param aHeadRow
     *            the header row as read.
     * @param aSheetColumns
     *            per header column, the cell's own 0-based column index in the sheet, which is NOT
     *            the column's index when the header range does not start at column A - for the cell
     *            types and the log message, so that it points at the cell the user has to look at.
     * @param aSheetName
     *            name of the sheet, for the log message: the URI usually carries no sheet fragment
     *            on the table paths (sheet 0 is resolved positionally), so without this the warning
     *            would not say which sheet of a multi-sheet workbook it is about.
     * @param aUri
     *            source URI, for log context.
     * @return a non-blank name per column, unique as far as the fallbacks and the numeric headers
     *         go.
     */
    private static String[] headerNames(Row aRow, ExcelRow aHeadRow, int[] aSheetColumns,
            String aSheetName, URI aUri)
    {
        String[] names = new String[aSheetColumns.length];
        String[] whyFallback = new String[aSheetColumns.length];
        Set<String> used = new HashSet<>();
        for (int i = 0; i < names.length; i++)
        {
            String name = aHeadRow.getStringValue(i);
            if (isErrorCell(aRow.getCell(aSheetColumns[i])))
            {
                whyFallback[i] = "has an unreadable header (" + name + ")";
            }
            else if (name.isBlank())
            {
                whyFallback[i] = "has a blank name";
            }
            else if (!aHeadRow.isNumber(i))
            {
                names[i] = name;
                used.add(name.toLowerCase(Locale.ROOT));
            }
        }
        // a numeric header yields to every text header, wherever it stands (ECT review ② M1)
        for (int i = 0; i < names.length; i++)
        {
            if (whyFallback[i] != null || names[i] != null)
            {
                continue;
            }
            String name = aHeadRow.getStringValue(i);
            String unique = name;
            for (int k = 1; used.contains(unique.toLowerCase(Locale.ROOT)); k++)
            {
                unique = name + "_" + k;
            }
            used.add(unique.toLowerCase(Locale.ROOT));
            names[i] = unique;
            if (!unique.equals(name))
            {
                LOGGER.log(Level.WARNING,
                        "Excel column %s of sheet '%s' has the numeric header %s, a name already used; using '%s' (uri=%s)"
                                .formatted(CellReference.convertNumToColString(aSheetColumns[i]),
                                        aSheetName, name, unique, aUri));
            }
        }
        for (int i = 0; i < names.length; i++)
        {
            if (whyFallback[i] == null)
            {
                continue;
            }
            String fallback = "V" + (i + 1);
            for (int k = 1; used.contains(fallback.toLowerCase(Locale.ROOT)); k++)
            {
                fallback = "V" + (i + 1) + "_" + k;
            }
            used.add(fallback.toLowerCase(Locale.ROOT));
            names[i] = fallback;
            LOGGER.log(Level.WARNING,
                    "Excel column %s of sheet '%s' %s; using fallback '%s' (uri=%s)".formatted(
                            CellReference.convertNumToColString(aSheetColumns[i]), aSheetName,
                            whyFallback[i], fallback, aUri));
        }
        return names;
    }


    /**
     * Whether a header cell is an error - a literal one, or a formula whose cached result is one.
     * Its type was already read once by {@link #determineColumns}, which fails the sheet when it
     * cannot be.
     */
    private static boolean isErrorCell(@Nullable Cell aCell)
    {
        return aCell != null && ExcelRow.effectiveType(aCell) == CellType.ERROR;
    }


    /**
     * Determine the column indices of the header row. Treats any cell that is not blank as a header
     * — text, numeric, boolean, date, error, and every formula cell. The header range terminates at
     * the first blank or absent cell, mirroring how Excel itself treats a "used range" run of
     * header labels.
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
                // First blank cell terminates the contiguous header range.
                break;
            }
            headerIndices.add(idx);
        }
        return headerIndices.stream().mapToInt(Integer::intValue).toArray();
    }


    /**
     * A cell is considered a header unless it is absent, {@code BLANK}, or plain text that is
     * blank: those end the header range ({@link #determineColumns(Row)}).
     * <p>
     * Everything else is a header, even when it carries no usable name: an ERROR cell
     * ({@code #REF!}) and a FORMULA cell whatever its cached result - blank, an error, or blank
     * text ({@code =""}). Such a column is kept under its positional name (owner ruling Q37(ii),
     * 2026-10-12: <i>"I would like to get this column in with a temp name, so nothing is lost,
     * additionally log with warning."</i>); {@link #headerNames} names it and logs why. They used
     * to end the range, which silently dropped that column and every one after it.
     * <p>
     * ⚠ Decides from the TYPE only, never from the value: a numeric cell whose stored value is
     * blank ({@link ExcelRow#isBlankNumeric}) stays a header cell. A cell whose type the reader
     * cannot read at all throws here, and the sheet fails with one {@code IOException} naming it
     * ({@link #headerColumns}).
     */
    private static boolean isHeaderCell(@Nullable Cell aCell)
    {
        if (aCell == null)
        {
            return false;
        }
        CellType t = aCell.getCellType();
        if (t == CellType.BLANK)
        {
            return false;
        }
        if (t == CellType.STRING)
        {
            String s = aCell.getStringCellValue();
            return s != null && !s.isBlank();
        }
        // ERROR, FORMULA (any cached result), NUMERIC, BOOLEAN: a header, named by headerNames.
        return true;
    }


    /**
     * The type of each column, from the sampled rows: {@link DataValueType#DOUBLE} when no sampled
     * cell vetoes a number ({@link ExcelRow#isNumberOrMissing}) <b>and</b> at least one sampled
     * cell is a real number ({@link ExcelRow#isNumber}), otherwise {@link DataValueType#STRING}.
     * <p>
     * A column with no evidence either way - every sampled cell absent, {@code BLANK}, a numeric
     * cell whose stored value is blank, or {@link Unreadable}, and a header-only sheet - is
     * {@link DataValueType#STRING}, as the CSV provider types it (owner, 2026-10-16: <i>"agree to
     * Char as default type."</i>; ECT-C1 of {@code PLAN-bugfix-empty-column-type}). It used to be
     * {@code DOUBLE} on zero evidence, so the same data loaded with a different type from XLSX than
     * from CSV. Text is evidence of text as before ({@code ""} and {@code "."} included); a present
     * {@code 0}, even under a zero-hiding format, is evidence of a number.
     * <p>
     * The types come from the sample only and are never revised: a column empty for the whole
     * sample and numeric after it is {@code STRING}, its numbers read as text
     * ({@link ExcelRow#getStringValue}).
     */
    private static DataValueType[] determineTypes(List<ExcelRow> aRowBlock, int aColumnCount)
    {
        DataValueType[] res = new DataValueType[aColumnCount];
        for (int i = 0; i < aColumnCount; i++)
        {
            DataValueType dt = DataValueType.STRING;
            try
            {
                boolean possiblyDouble = true;
                boolean sawNumericEvidence = false;
                for (ExcelRow row : aRowBlock)
                {
                    if (!row.isNumberOrMissing(i))
                    {
                        possiblyDouble = false;
                        break;
                    }
                    sawNumericEvidence |= row.isNumber(i);
                }

                if (possiblyDouble && sawNumericEvidence)
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
     * The format half of {@link ExcelRow#isBlankNumeric}: whether a numeric cell whose display text
     * is empty and whose numeric value reads {@code 0.0} has a BLANK stored value, decided from the
     * cell's number format.
     * <p>
     * One instance per load ({@link #buildMeta}, {@link #provide(Sheet, URI)},
     * {@link #inferColumns}), used by one thread: {@link DataFormatter} is not thread-safe (it
     * caches parsed formats), so it is never shared between loads. The formatter is created on
     * first need only - the probe is reached only for a cell whose display text is blank and whose
     * value reads {@code 0.0}, so a sheet without such a cell never allocates one.
     */
    private static final class BlankNumericProbe
    {

        private @Nullable DataFormatter formatter;

        /**
         * Whether the empty display text of a numeric cell whose value reads {@code 0.0} means that
         * its stored value is blank, given the cell's style. Mirrors how excel-streaming-reader
         * builds the display text: no style, or a style whose format the reader cannot name, means
         * the text IS the stored text, so an empty text is a blank stored value; otherwise the text
         * is the formatted value, and only a format that renders {@code 0} as non-empty text proves
         * that the stored value was not a {@code 0}.
         *
         * @param aStyle
         *            the cell's style, {@code null} when the workbook has no styles table.
         * @return {@code true} when the stored value is blank (the cell is missing).
         */
        boolean emptyTextMeansBlankValue(@Nullable CellStyle aStyle)
        {
            if (aStyle == null)
            {
                return true;
            }
            String format = aStyle.getDataFormatString();
            if (format == null)
            {
                format = BuiltinFormats.getBuiltinFormat(aStyle.getDataFormat());
            }
            if (format == null)
            {
                return true;
            }
            if (formatter == null)
            {
                formatter = new DataFormatter();
            }
            boolean zeroRendersEmpty = formatter
                    .formatRawCellContents(0.0, aStyle.getDataFormat(), format).isBlank();
            // Owner ruling R8 (2026-10-14): a cell whose number format renders 0 as blank and
            // whose stored value reads 0.0 loads as a present 0, because the public API cannot
            // tell an uncalculated formula from a real 0 there (same empty text, same 0.0) and
            // real zeros under zero-hiding formats are the common case. Pinned by
            // testBlankStoredValueUnderAZeroHidingFormatReadsAsPresentZero.
            return !zeroRendersEmpty;
        }
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

    /**
     * A present cell that has no readable value - not a blank cell. It reads
     * {@link MissingValue#MIS_ERROR} in every column type (owner ruling Q37(i), 2026-10-12: <i>"a,
     * log and continue and read as MIS_ERROR."</i>; BNM-F7 for the reader failure), never
     * {@code MIS}, which would make it look absent. For column typing it is no evidence either way,
     * like a blank cell; as a header it has no name, so it gets the positional fallback.
     */
    private enum Unreadable
    {

        /**
         * A formula whose cached value is empty ({@code <f>…</f><v></v>}): what openpyxl - and
         * therefore {@code pandas.DataFrame.to_excel} - writes for every formula, since neither
         * evaluates formulas. A plain numeric cell with an empty value is NOT this: it is a blank
         * cell (owner ruling R1, 2026-10-14). Under a zero-hiding number format the shape reads as
         * a present 0 instead (owner ruling R8; see {@code BlankNumericProbe}).
         */
        UNCALCULATED_FORMULA("formula without a cached value"),

        /**
         * A cell the streaming reader throws on (a stored value it cannot convert to the type the
         * cell claims, e.g. {@code t="n"} with {@code <v>N/A</v>}).
         */
        READ_FAILED("cell the reader cannot read");

        private final String reason;

        Unreadable(String aReason)
        {
            reason = aReason;
        }
    }


    private static class ExcelRow
    {

        /**
         * The datetime epoch, 1960-01-01 00:00:00 UTC, in milliseconds-since-1970-01-01-UTC. The
         * format catalog renders {@code E8601DT.} as ISO datetime from this base; we convert
         * Excel-serial-derived, zone-free {@code LocalDateTime} values (anchored at UTC) into
         * SAS-datetime-seconds here so a date-formatted Excel column comes through as a
         * {@code DOUBLE} that displays as ISO.
         *
         * <p>
         * F-prov-14: delegates to {@link TemporalOrigin#ORIGIN_EPOCH_MILLI}, the one place the
         * origin is defined. The literal {@code -315619200000L} that used to stand here is now
         * derived from that single {@code LocalDate}.
         * </p>
         */
        private static final long SAS_DATETIME_EPOCH_MILLIS = TemporalOrigin.ORIGIN_EPOCH_MILLI;

        /**
         * The first Excel serial past the last date Excel can represent, 9999-12-31 (serial 2958465
         * in the 1900 date system). {@link #dateSecondsOf} treats a serial at or beyond it as not a
         * date: POI's {@code DateUtil.getLocalDateTime} overflows for a large enough serial and
         * answers a plausible but wrong {@code 1899-12-31}.
         */
        private static final double FIRST_SERIAL_PAST_EXCEL_MAX_DATE = 2_958_466.0;

        private final @Nullable Object[] values;

        /** The sheet row this row was read from, 0-based - for the {@link Unreadable} warning. */
        private final int rowNum;

        /** Parallel array: true when the source cell was numeric AND date-formatted. */
        private final boolean[] isDate;

        /**
         * Parallel array: the SAS-datetime-seconds equivalent of this cell's raw Excel serial,
         * computed for EVERY {@code NUMERIC} cell regardless of that cell's own style — {@code NaN}
         * for a non-numeric cell, a missing cell, or one {@link Cell#getLocalDateTimeCellValue()}
         * could not convert.
         * <p>
         * Bug fix (H3, clinical-path hardening wave 3): a column is declared a date column when
         * {@code inferDateFormats} sees ANY sampled cell date-formatted, but the OLD code only
         * converted a cell to SAS-seconds when THAT cell's own style was date-formatted — a column
         * with inconsistent per-cell styling (a common real shape: some header/data cells lose
         * their date format after a copy/paste or a partial re-format) silently mixed raw Excel
         * serials with SAS-seconds under one {@code E8601DT.} column, misreading a raw serial as a
         * date up to ~63 years off. {@link Cell#getLocalDateTimeCellValue()} converts a
         * {@code NUMERIC} cell's value correctly regardless of that cell's OWN style (verified
         * empirically), so precomputing it here for every numeric cell — and having
         * {@link ExcelTableDataParser#addData2Column} choose this array over {@link #numericCell}
         * once the COLUMN is decided to be a date column — makes the conversion consistent for the
         * whole column instead of per-cell.
         */
        private final double[] dateSasSeconds;

        ExcelRow(Row aRow, int[] aColumnIndices, BlankNumericProbe aProbe)
        {
            values = new Object[aColumnIndices.length];
            rowNum = aRow.getRowNum();
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
                    if (c != null && effectiveType(c) == CellType.NUMERIC
                            && !isBlankNumeric(c, aProbe))
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
                values[i] = getCellValue(c, aProbe);
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
         * POI answers {@link DateUtil#isCellDateFormatted} and
         * {@link Cell#getLocalDateTimeCellValue()} correctly for such a cell; the provider simply
         * never asked. This also restores the invariant {@link #dateSasSeconds} documents -
         * computed for EVERY cell whose value is numeric, {@code NaN} only for a non-numeric cell,
         * a missing cell, or one POI could not convert.
         */
        private static CellType effectiveType(Cell aCell)
        {
            CellType t = aCell.getCellType();
            return t == CellType.FORMULA ? aCell.getCachedFormulaResultType() : t;
        }


        /**
         * Whether the cell is NUMERIC by its {@link #effectiveType} - a plain numeric cell, or a
         * formula whose cached result is numeric - but its stored value is BLANK. Such a cell has
         * no value: it is never a number, never a date and never makes a column a date column.
         * <p>
         * The shapes: an uncalculated formula with an EMPTY cached value element
         * ({@code <f>…</f><v></v>}), which openpyxl - and therefore
         * {@code pandas.DataFrame.to_excel} - writes for EVERY formula, since neither evaluates
         * formulas; and a plain or {@code t="n"} cell with an empty value ({@code <c><v></v></c>},
         * {@code <c t="n"><v/></c>}). The plain shapes are blank cells and read {@code MIS} (R1:
         * behave as 5.3.0); the formula is {@link Unreadable#UNCALCULATED_FORMULA} and reads
         * {@code MIS_ERROR} (owner ruling Q37(i), 2026-10-12) - {@link #getCellValue} makes that
         * split on the cell's own type.
         * <p>
         * Why the provider decides this itself (owner ruling R1, 2026-10-14):
         * excel-streaming-reader up to 5.3.0 threw {@code NumberFormatException("empty String")}
         * from {@code getNumericCellValue()} for a blank stored value, and the provider degraded
         * the cell to missing; 5.3.2 returns {@code 0.0} instead, which would silently load an
         * unevaluated data formula as a present {@code 0} and a date-styled one as 1899-12-31.
         * <p>
         * The reader offers no public accessor for the stored text, so the check is built from
         * three conditions, cheapest first:
         * <ol>
         * <li>{@code getStringCellValue()} is blank. ⚠ For a numeric cell this is the FORMATTED
         * DISPLAY TEXT ({@code DataFormatter.formatRawCellContents} over the cell's number format),
         * not the stored value - when the cell has a number format and a non-empty stored value;
         * otherwise it is the stored text itself. A blank stored value therefore always gives a
         * blank text, but so does a real number under a format that renders it empty: a real
         * {@code 0} under {@code 0;-0;""}, {@code #,##0;-#,##0;""} or {@code [=0]"";General}, a
         * real {@code -5} under {@code 0;"";""}. Blank text alone is necessary, never sufficient.
         * Almost every real cell leaves here.</li>
         * <li>{@code getNumericCellValue()} is {@code 0.0}, which is what 5.3.2 answers for a blank
         * stored value. Any other number is a real value whose format hides it.</li>
         * <li>The cell's own number format renders {@code 0} as non-empty text - see
         * {@link BlankNumericProbe#emptyTextMeansBlankValue}. Then the empty text cannot come from
         * a stored {@code 0}, so the stored value is blank. Only cells that reach this point pay
         * for a format call.</li>
         * </ol>
         * ⚠ The one corner the public API cannot decide - a format that hides zero AND a blank
         * stored value - reads as a present {@code 0} (owner ruling R8, 2026-10-14); see that
         * branch in {@link BlankNumericProbe#emptyTextMeansBlankValue}.
         * <p>
         * A whitespace-only stored value ({@code <v> </v>}) is missing on both paths, but not the
         * same missing (a named gap; the owner rules whitespace-only values theoretical). When the
         * cell has a number format (any workbook with a styles table), {@code getCellType()} itself
         * throws for it, from the reader's formatter (5.3.0 and 5.3.2 alike), so it degrades to
         * {@link Unreadable#READ_FAILED} ({@code MIS_ERROR}) through the callers' {@code catch}.
         * Without a styles table the display text is the stored {@code " "}: {@code isBlank()}
         * holds, the value reads {@code 0.0}, and the probe's no-style branch makes it a blank
         * {@code MIS}.
         * <p>
         * Every numeric read goes through this check first: {@link #rawNumeric} and the date probe
         * in the constructor. ⚠ Call it only inside a {@code try}: {@code getCellType()} itself can
         * throw for a cell whose stored content the reader cannot convert (see the constructor's H4
         * note).
         */
        private static boolean isBlankNumeric(Cell aCell, BlankNumericProbe aProbe)
        {
            if (effectiveType(aCell) != CellType.NUMERIC || !aCell.getStringCellValue().isBlank())
            {
                return false;
            }
            // 5.3.2 answers 0.0 for a blank stored value; any other number is a real value.
            return aCell.getNumericCellValue() == 0.0
                    && aProbe.emptyTextMeansBlankValue(aCell.getCellStyle());
        }


        /**
         * Converts a {@code NUMERIC} cell's value to SAS-datetime-seconds regardless of that cell's
         * own style — see {@link #dateSasSeconds}. Returns {@code NaN} rather than throwing when
         * POI cannot convert the value (out-of-range serial, or any other
         * {@link RuntimeException}), so one bad cell degrades that cell to missing rather than
         * aborting the whole row — consistent with {@link #getCellValue}'s error handling.
         * <p>
         * ⚠ No input is known to enter that catch, for TWO reasons, and the second is the
         * load-bearing one: {@code getLocalDateTimeCellValue()} does not throw for any value the
         * reader can type as numeric (measured through {@code StreamingReader}: {@code 1e308}
         * overflows to 1899-12-31 instead of throwing - hence the range check in front of it, see
         * below - and a negative serial returns {@code null}); and a cell whose stored value cannot
         * be parsed at all makes {@link DateUtil#isCellDateFormatted} throw FIRST, one line
         * earlier, where the constructor's own probe catch takes it. Re-check both if that call
         * order ever changes.
         * <p>
         * Bug fix (TZ): this used {@code getDateCellValue()}, which places the serial in the JVM's
         * DEFAULT time zone, and then read the resulting instant back as UTC - so every date moved
         * by the machine's UTC offset (2023-03-15 read as 2023-03-14T23:00 in Berlin, as 05:00 in
         * New York; CI runs in UTC and could not see it). An Excel date is a wall-clock value with
         * no zone: {@code getLocalDateTimeCellValue()} keeps it one, the streaming cell applying
         * the same type check and the same 1904-date-system flag, and it is anchored at UTC here,
         * which is how the SAS datetime origin is defined. Pinned by
         * {@code testDateCellsReadTheSameInEveryDefaultTimeZone}.
         * <p>
         * The switch changed one edge, measured through {@code StreamingReader}: for a serial as
         * large as {@code 1e308} the old call saturated at year 5881510, but POI's
         * {@code DateUtil.getLocalDateTime} overflows its day count and answers {@code 1899-12-31}
         * - a plausible, wrong date. A serial past Excel's own last date (9999-12-31, serial
         * 2958465) is not a date Excel can hold, so it now reads as missing (see
         * {@link #FIRST_SERIAL_PAST_EXCEL_MAX_DATE}); a negative serial still answers {@code null}.
         */
        private static double dateSecondsOf(Cell aCell)
        {
            try
            {
                if (aCell.getNumericCellValue() >= FIRST_SERIAL_PAST_EXCEL_MAX_DATE)
                {
                    return Double.NaN;
                }
                LocalDateTime ldt = aCell.getLocalDateTimeCellValue();
                return ldt != null
                        ? (ldt.toInstant(ZoneOffset.UTC).toEpochMilli() - SAS_DATETIME_EPOCH_MILLIS)
                                / 1000.0
                        : Double.NaN;
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
         * The cell's text: {@code ""} for a cell with no value and for an {@link Unreadable} one,
         * so a header cell of either kind gets the positional fallback name. A number reads as
         * {@link #numberText}. It is both a Char column's cell and a header's name.
         */
        String getStringValue(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            if (val == null || val instanceof Unreadable)
            {
                return "";
            }
            return val instanceof Double d ? numberText(d) : val.toString();
        }


        /**
         * A number as text, the shortest plain decimal for a finite value: {@code 5} reads
         * {@code "5"}, {@code 0.1} reads {@code "0.1"}, {@code 1E7} reads {@code "10000000"} and
         * {@code -0.0} reads {@code "0"} (owner, 2026-10-16, ECT-Q2 ⓐ of
         * {@code PLAN-bugfix-empty-column-type}; the engine's {@code str(3.0)} reads {@code "3"}
         * the same way, and so does the CSV text of the same export). It used to be
         * {@link Double#toString} ({@code "5.0"}, {@code "1.0E7"}). A date-styled cell reads its
         * serial number. A non-finite value keeps {@link Double#toString} ({@code "Infinity"}):
         * {@link BigDecimal#valueOf(double)} throws for it, and a stored {@code 1E+400} reaches
         * here.
         * <p>
         * ⚠ It differs ON PURPOSE from {@code DataValueSupport.toPlainNumberText}, and only for an
         * integral value of magnitude from 2<sup>54</sup> up to 2<sup>63</sup>: there that method
         * prints the double's exact binary value, as a {@code long} would ({@code 2^60} reads
         * {@code "1152921504606846976"}), so that a DOUBLE key renders like the LONG of the same
         * value, while this one prints the shortest decimal that reads back as the same double
         * ({@code "1152921504606847000"}). Here the text IS the value of a Char cell or the name of
         * a header, so it keeps the digits the sheet's author typed - the fewest that identify the
         * stored double; the exact binary digits are an artefact nobody wrote, and a Char cell
         * takes part in no numeric key. Every other finite value reads the same through both (ECT
         * review ② L2).
         */
        static String numberText(double aValue)
        {
            return Double.isFinite(aValue)
                    ? BigDecimal.valueOf(aValue).stripTrailingZeros().toPlainString()
                    : Double.toString(aValue);
        }


        /** Whether the cell is present but has no readable value - see {@link Unreadable}. */
        boolean isUnreadable(int aColumn)
        {
            return values[aColumn] instanceof Unreadable;
        }


        /** Why the cell has no readable value, for a cell {@link #isUnreadable}. */
        String unreadableReason(int aColumn)
        {
            return values[aColumn] instanceof Unreadable u ? u.reason : "";
        }


        /**
         * The value this cell stores in a column that {@link #isNumberOrMissing} has classified as
         * {@code DOUBLE}: a {@link Double}, or the {@link MissingValue} it reads as. The sample
         * that typed the column is bounded ({@code guessingRowCount}), so a cell past it can be
         * anything:
         * <ul>
         * <li>BLANK - no value at all ({@code null}: absent, BLANK, a plain numeric cell whose
         * stored value is blank) or text that is empty or whitespace only - is
         * {@link MissingValue#MIS}.</li>
         * <li>A number is that number; text that parses is its number ({@code "NaN"} is
         * {@code MIS}, owner ruling BNM E5).</li>
         * <li>Anything else is a value present but not readable as a number, so
         * {@link MissingValue#MIS_ERROR} (owner ruling BNM-F7, 2026-10-12: <i>"same here, should
         * already be aligned if not, then yes MIS_ERROR."</i>): an {@link Unreadable} cell, text
         * that does not parse, an error cell ({@code "#N/A"}), a boolean, and a number whose value
         * is infinite - a stored {@code 1E+400} and text {@code "Infinity"} alike (owner,
         * 2026-10-12: <i>"Should CSV/XLSX text Infinity also become MIS_ERROR: yes."</i>).</li>
         * </ul>
         * Text past the sample that parses keeps its number: {@link #isNumberOrMissing} keeps a
         * text cell out of the sample's evidence (see there), but a column the sample did type
         * {@code DOUBLE} loses nothing a later text cell spells.
         */
        Object numericCell(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            if (val instanceof Unreadable)
            {
                return MissingValue.MIS_ERROR;
            }
            if (val == null || isBlankText(val))
            {
                return MissingValue.MIS;
            }
            double d;
            if (val instanceof Number num)
            {
                d = num.doubleValue();
            }
            else
            {
                try
                {
                    d = Double.parseDouble(val.toString());
                }
                catch (NumberFormatException _)
                {
                    return MissingValue.MIS_ERROR;
                }
            }
            if (Double.isNaN(d))
            {
                return MissingValue.MIS;
            }
            return Double.isInfinite(d) ? MissingValue.MIS_ERROR : d;
        }


        /**
         * The value this cell stores in a DATE column ({@code E8601DT.}, see
         * {@link #dateSasSeconds}): the converted SAS-datetime-seconds, {@link MissingValue#MIS}
         * for a blank cell, and {@link MissingValue#MIS_ERROR} for a present cell with no date - an
         * {@link Unreadable} cell, text, a serial past Excel's last date, a negative serial, a
         * failed conversion (BNM-F7). The split is decided on the RAW cell: the converted value is
         * {@code NaN} for all of them, the blank one included.
         */
        Object dateCell(int aColumn)
        {
            @Nullable
            Object val = values[aColumn];
            if (val instanceof Unreadable)
            {
                return MissingValue.MIS_ERROR;
            }
            if (val == null || isBlankText(val))
            {
                return MissingValue.MIS;
            }
            double d = dateSasSeconds[aColumn];
            return Double.isNaN(d) ? MissingValue.MIS_ERROR : d;
        }


        /**
         * Whether a present cell value is blank text, empty or whitespace only (a STRING cell is
         * right-trimmed on read, so whitespace-only text arrives as {@code ""}). A {@code null}
         * value - no value at all - is blank as well; the callers test it first.
         */
        private static boolean isBlankText(Object aValue)
        {
            return aValue instanceof String str && str.isBlank();
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
         * column as DOUBLE, and {@code getDoubleValue} then re-parsed each cell to
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
            // An Unreadable cell is no evidence either way, exactly like a blank one.
            return val == null || val instanceof Number || val instanceof Unreadable;
        }


        /**
         * Whether the cell is evidence of a number: an Excel-{@code NUMERIC} cell (or a formula
         * whose cached result is a number) with a stored value. A cell with no value and an
         * {@link Unreadable} one are evidence of nothing ({@link #isNumberOrMissing} lets them
         * through), so a column of only those is not typed {@code DOUBLE}.
         */
        boolean isNumber(int aColumn)
        {
            return values[aColumn] instanceof Number;
        }


        /**
         * The value of one cell: {@code null} for a cell with no value (absent, BLANK, or a plain
         * numeric cell whose stored value is blank - see {@link #isBlankNumeric}), an
         * {@link Unreadable} for a present cell that has no readable value, otherwise a
         * {@link Double} (a NUMERIC cell's raw value) or a {@link String}.
         */
        private @Nullable Object getCellValue(@Nullable Cell aCell, BlankNumericProbe aProbe)
        {
            try
            {
                if (aCell == null)
                {
                    return null;
                }

                return switch (aCell.getCellType())
                {
                case NUMERIC -> rawNumeric(aCell, aProbe);
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
                // Q37(i): an uncalculated formula is not a blank cell - see Unreadable.
                case NUMERIC -> isBlankNumeric(aCell, aProbe) ? Unreadable.UNCALCULATED_FORMULA
                        : aCell.getNumericCellValue();
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
                // A present cell the reader failed on is not a blank one (BNM-F7).
                LOGGER.log(Level.DEBUG, ex.getMessage(), ex);
                return Unreadable.READ_FAILED;
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
         * <p>
         * Returns {@code null} (missing) for a cell whose stored value is blank - see
         * {@link #isBlankNumeric}; excel-streaming-reader 5.3.2 would answer {@code 0.0} for it.
         */
        private @Nullable Object rawNumeric(Cell aCell, BlankNumericProbe aProbe)
        {
            return isBlankNumeric(aCell, aProbe) ? null : aCell.getNumericCellValue();
        }
    }


    private static class ExcelTableDataParser extends AbstractTableDataParser<ExcelRow>
    {

        /** The sheet's name, for the {@link Unreadable} warning. */
        private final String sheetName;

        /** Per table column, the sheet column it is read from (0-based). */
        private final int[] sheetColumns;

        /** Source URI, for log context. */
        private final URI uri;

        /** Per table column, 1 once its {@link Unreadable} warning was logged. */
        private final AtomicIntegerArray warned;

        ExcelTableDataParser(@NonNull IDataTableProvider aProvider, @NonNull DataTableMeta aMeta,
                String aSheetName, int[] aSheetColumns, URI aUri)
        {
            super(aProvider, aMeta);
            sheetName = aSheetName;
            sheetColumns = aSheetColumns.clone();
            uri = aUri;
            warned = new AtomicIntegerArray(aSheetColumns.length);
        }


        /**
         * Logs ONE {@code WARNING} per column per load, at its first {@link Unreadable} cell (owner
         * ruling Q37(i), 2026-10-12: <i>"a, log and continue and read as MIS_ERROR."</i>): it names
         * the sheet, the cell and why, and says that further such cells of the column are not
         * logged - a formula column of a million rows would otherwise flood the log.
         */
        private void warnUnreadableOnce(ExcelRow aRow, int aColumnIndex, String aColumnName)
        {
            if (!warned.compareAndSet(aColumnIndex, 0, 1))
            {
                return;
            }
            String cell = new CellReference(aRow.rowNum, sheetColumns[aColumnIndex])
                    .formatAsString();
            LOGGER.log(Level.WARNING, ("Excel cell %s of sheet '%s' (column '%s') is a %s; the cell"
                    + " reads as MIS_ERROR. Further such cells in this column are not logged"
                    + " (uri=%s)").formatted(cell, sheetName, aColumnName,
                            aRow.unreadableReason(aColumnIndex), uri));
        }


        @Override
        protected void addData2Column(List<ExcelRow> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            for (ExcelRow row : aRowSlice)
            {
                if (row.isUnreadable(aColumnIndex))
                {
                    warnUnreadableOnce(row, aColumnIndex, aMetaColumn.getName());
                }
                switch (aMetaColumn.getType())
                {
                case DOUBLE:
                {
                    // Bug fix (H3): once the COLUMN is decided to be a date column, every numeric
                    // cell in it must be converted the same way — not just the ones whose own cell
                    // style happens to be date-formatted. See ExcelRow#dateSasSeconds.
                    boolean isDateColumn = DATE_DISPLAY_FORMAT
                            .equals(aMetaColumn.getDisplayFormat());
                    aDataColumn.addElement(isDateColumn ? row.dateCell(aColumnIndex)
                            : row.numericCell(aColumnIndex));
                    break;
                }
                case STRING:
                default:
                    // Q37(i): an Unreadable cell reads MIS_ERROR in every column type.
                    aDataColumn.addElement(row.isUnreadable(aColumnIndex) ? MissingValue.MIS_ERROR
                            : row.getStringValue(aColumnIndex));
                    break;
                }
            }
        }

    }
}
