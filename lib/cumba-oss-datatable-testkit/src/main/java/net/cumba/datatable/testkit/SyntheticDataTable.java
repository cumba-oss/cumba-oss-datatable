package net.cumba.datatable.testkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * A fast, <b>real</b> (non-Mockito) {@link IDataTable} over plain arrays — for harnesses that build
 * thousands of small tables, or one table of millions of rows, where {@link MockTable}'s Mockito
 * stubbing is too slow. An empty-string / {@code null} cell reads as missing.
 *
 * <h2>Why it lives here</h2>
 * <p>
 * Until 2026-09-14 this class existed as <b>two copies</b> named {@code SyntheticStringTable}, one
 * in {@code cumba-corej-core}'s test tree and one in {@code cumba-corej-rules}', both
 * package-private in a corej package. The copy was made deliberately when the rules module dropped
 * its {@code test-jar} dependency on the engine, and its header recorded the trade: <i>"the two
 * copies can drift. That is tolerable for a test stub. <b>If this class ever starts carrying real
 * behaviour, revisit.</b>"</i> Column typing (below) is exactly that, so it was revisited: owner
 * ruling 2026-09-14, one copy, here beside {@link MockTable}.
 * </p>
 * <p>
 * ⚠ It implements {@link IDataTable} <b>directly</b> rather than extending
 * {@code net.cumba.datatable.impl.AbstractDataTable}, which is what the corej copies did. That is
 * deliberate and load-bearing: this testkit depends on {@code cumba-datatable} (the API) and
 * <b>not</b> on {@code cumba-datatable-impl}, so every consumer stays off the implementation
 * module. {@code IDataTable} carries 27 {@code default} methods, so only a handful need bodies.
 * </p>
 *
 * <h2>Column types</h2>
 * <p>
 * ⭐ The original was <b>string-only</b> — every column declared {@link DataValueType#STRING}
 * whatever the variable actually was — and that is why it is renamed. A fixture that declares every
 * column {@code Char} is a fixture lying about the standard, and once
 * {@code PLAN-column-type-conformance}'s column-type gate landed it stopped being harmless:
 * measured 2026-09-14, <b>78</b> correctly-authored rules began erroring in one corpus harness
 * because {@code AGE}, {@code EXDOSE}, {@code PCLLOQ} and friends were being handed to them as
 * text. Those rules then dropped out of the harness's own coverage count, silently, under a floor
 * with enough headroom to absorb the loss.
 * </p>
 */
public final class SyntheticDataTable implements IDataTable
{

    /**
     * The string values cycled into {@code STRING} columns. ⚠ Private, not public: an array
     * constant is mutable, so exposing it would let any caller rewrite every other caller's fixture
     * (SpotBugs {@code MS_PKGPROTECT}). Callers that want their own values pass them to the
     * {@code String[] aValueCycle} constructor.
     */
    private static final String[] DEFAULT_TEXT_CYCLE =
    {
            "A", "", "1", "2024-01-01", "Y"
    };

    /**
     * The values cycled into numeric columns. A {@code null} is the missing marker, mirroring the
     * empty string in {@link #DEFAULT_TEXT_CYCLE} so both kinds of column exercise a missing cell.
     */
    private static final @Nullable Double[] DEFAULT_NUMERIC_CYCLE =
    {
            1d, null, 2d, 42d, 7d
    };

    private final DataTableMeta meta;

    private final @Nullable Object[][] data; // [col][row]

    private final int rowCount;

    /**
     * All-{@code STRING} table — the original behaviour, kept for callers that do not care about
     * types.
     *
     * @param aName
     *            the dataset name.
     * @param aColumns
     *            the column names, in order.
     * @param aValueCycle
     *            the values cycled across rows and columns.
     * @param aRows
     *            the row count.
     */
    public SyntheticDataTable(String aName, List<String> aColumns, String[] aValueCycle, int aRows)
    {
        this(aName, allString(aColumns), aRows, aValueCycle);
    }


    /**
     * Typed table: each column is declared as the caller says the standard declares it, and is fed
     * values of that kind.
     *
     * @param aName
     *            the dataset name.
     * @param aColumnTypes
     *            column name → declared type, in iteration order.
     * @param aRows
     *            the row count.
     */
    public SyntheticDataTable(String aName, Map<String, DataValueType> aColumnTypes, int aRows)
    {
        this(aName, aColumnTypes, aRows, DEFAULT_TEXT_CYCLE);
    }


    private SyntheticDataTable(String aName, Map<String, DataValueType> aColumnTypes, int aRows,
            String[] aTextCycle)
    {
        rowCount = aRows;
        int n = aColumnTypes.size();
        DataTableColumnMeta[] colMetas = new DataTableColumnMeta[n];
        data = new Object[n][aRows];
        int c = 0;
        for (Map.Entry<String, DataValueType> e : aColumnTypes.entrySet())
        {
            DataValueType type = e.getValue();
            colMetas[c] = DataTableColumnMeta.builder().name(e.getKey()).index(c).type(type)
                    .build();
            boolean numeric = type == DataValueType.DOUBLE || type == DataValueType.LONG;
            for (int r = 0; r < aRows; r++)
            {
                // Offset by column so different columns hold different values on the same row.
                int i = (r + c) % (numeric ? DEFAULT_NUMERIC_CYCLE.length : aTextCycle.length);
                if (numeric)
                {
                    Double d = DEFAULT_NUMERIC_CYCLE[i];
                    data[c][r] = d == null ? null
                            : type == DataValueType.LONG ? (Object) Long.valueOf(d.longValue())
                                    : (Object) d;
                }
                else
                {
                    data[c][r] = aTextCycle[i];
                }
            }
            c++;
        }
        meta = DataTableMeta.builder().name(aName).label(aName).rowCount(aRows).totalRowCount(aRows)
                .columns(colMetas).build();
    }


    private static Map<String, DataValueType> allString(List<String> aColumns)
    {
        Map<String, DataValueType> m = new LinkedHashMap<>();
        for (String col : aColumns)
        {
            m.put(col, DataValueType.STRING);
        }
        return m;
    }


    /**
     * Convenience for a caller that knows only <em>which</em> columns are numeric.
     *
     * @param aName
     *            the dataset name.
     * @param aColumns
     *            the column names, in order.
     * @param aNumericColumns
     *            the subset to declare {@link DataValueType#DOUBLE}; everything else is
     *            {@link DataValueType#STRING}.
     * @param aRows
     *            the row count.
     * @return the table.
     */
    public static SyntheticDataTable withNumeric(String aName, List<String> aColumns,
            java.util.Set<String> aNumericColumns, int aRows)
    {
        Map<String, DataValueType> m = new LinkedHashMap<>();
        for (String col : aColumns)
        {
            m.put(col, aNumericColumns.contains(col) ? DataValueType.DOUBLE : DataValueType.STRING);
        }
        return new SyntheticDataTable(aName, m, aRows);
    }


    @Override
    public long getRowCount()
    {
        return rowCount;
    }


    @Override
    public DataTableMeta getMetaData()
    {
        return meta;
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        if (aRow < 0 || aRow >= rowCount)
        {
            throw new IndexOutOfBoundsException("row " + aRow + " outside [0, " + rowCount + ")");
        }
        return data[aColumn][(int) aRow];
    }


    /** @return the column names, in declaration order. */
    public List<String> columnNames()
    {
        List<String> names = new ArrayList<>();
        meta.getAllColumns().forEach(cm -> names.add(cm.getName()));
        return names;
    }
}
