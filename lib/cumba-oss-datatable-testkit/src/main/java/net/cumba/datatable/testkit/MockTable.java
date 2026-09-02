package net.cumba.datatable.testkit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Builds a mock IDataTable for testing. Column data is String-based by default.
 *
 * <p>
 * Numeric columns can be added via {@link #colLong(String, Long...)} and
 * {@link #colDouble(String, Double...)}; the resulting cells return {@link DataValueType#LONG} /
 * {@link DataValueType#DOUBLE} from {@code getType()} and the boxed numeric from
 * {@code getValue()}. Used by Fix #19's polymorphic date comparison tests so the LHS dispatch sees
 * numeric SAS dates as numeric.
 * </p>
 */
// Five static Mockito imports (mock / lenient / eq / anyLong / anyString) exceed PMD's default
// TooManyStaticImports threshold of 4. They are the idiomatic Mockito spelling and the whole point
// of a mock-building helper; spelling them Mockito.mock(...) throughout would be strictly less
// readable. ⚠ Only visible since this class moved from a TEST tree to a MAIN one -- PMD, SpotBugs
// and NullAway all analyse src/main only.
@SuppressWarnings("PMD.TooManyStaticImports")
public final class MockTable
{

    private final Map<String, String[]> columns = new LinkedHashMap<>();

    private final Map<String, Long[]> longColumns = new LinkedHashMap<>();

    private final Map<String, Double[]> doubleColumns = new LinkedHashMap<>();

    private final Map<String, String[]> sasMissingColumns = new LinkedHashMap<>();

    private final Map<String, String> colLabels = new LinkedHashMap<>();

    private final Map<String, Integer> colLengths = new LinkedHashMap<>();

    private final Map<String, String> colFormats = new LinkedHashMap<>();

    private @Nullable String tableName;

    private boolean caseInsensitiveColumns;

    private @Nullable String tableLabel;

    private @Nullable String tableUri;

    private final Map<String, Object> metaValues = new LinkedHashMap<>();

    public static MockTable of()
    {
        return new MockTable();
    }


    /**
     * Creates an IDataTable with the given column names, each with a single empty-string row.
     * Useful for tests that only need column metadata.
     */
    public static IDataTable withColumns(String... columnNames)
    {
        MockTable mt = new MockTable();
        for (String name : columnNames)
        {
            mt.col(name, "");
        }
        return mt.build();
    }


    public MockTable col(String name, String... values)
    {
        columns.put(name, values);
        return this;
    }


    /**
     * Adds a typed numeric column whose cells report {@link DataValueType#LONG} from
     * {@code getType()}. Fix #19 polymorphic-date tests use this to trigger the numeric branch on
     * the LHS.
     *
     * <p>
     * A {@code null} entry models a <b>missing numeric</b> exactly as a real buffer does:
     * {@code isMissingOrInvalid() == true}, {@code getValue()} answers
     * {@link net.cumba.datatable.values.MissingValue#MIS}, {@code getValueAsString()} renders
     * {@code "."} and {@code getValueAsDouble()} is {@code NaN}. There is no separate "null but not
     * missing" state, because a real typed buffer cannot represent one &mdash;
     * {@code DataBufferInt} stores a {@code MISSING_SENTINEL} and answers {@code MissingValue.MIS}.
     * </p>
     */
    public MockTable colLong(String name, Long... values)
    {
        longColumns.put(name, values);
        return this;
    }


    /**
     * Adds a typed numeric column whose cells report {@link DataValueType#DOUBLE} from
     * {@code getType()}. A {@code null} entry is a missing numeric, with the same shape
     * {@link #colLong} documents.
     */
    public MockTable colDouble(String name, Double... values)
    {
        doubleColumns.put(name, values);
        return this;
    }


    /**
     * Adds a column whose {@code null} entries model a <b>real SAS missing marker</b>: they report
     * {@code isMissingOrInvalid() == true} <em>and</em> render as {@code "."} from
     * {@code getValueAsString()}, exactly as {@code DataValueMissing(MissingValue.MIS)} does.
     *
     * <p>
     * ⚑ <b>The hazard this method used to exist to route around is now fixed at the root
     * (2026-09-01).</b> {@link #colLong}'s missing cell used to render {@code ""} &mdash; which is
     * also what a <em>fold</em> produces, so a fold and a non-fold were indistinguishable through
     * it and any such assertion was <b>vacuous</b>. That was not hypothetical: the grouped-key
     * lockstep test passed against deliberately broken code until it was moved onto this column
     * type. {@code colLong} / {@link #colDouble} now render {@code "."} and answer
     * {@code MissingValue.MIS} like a real numeric buffer, so <b>either</b> column type is safe for
     * fold detection. Kept, and the history kept with it, because a passing test is not evidence
     * the trap is gone.
     * </p>
     *
     * <p>
     * What still distinguishes this method is its <em>input</em>: it takes {@code String} values,
     * whereas {@link #colLong} takes boxed {@code Long}s. For a <b>missing</b> cell the two are now
     * observationally identical.
     * </p>
     *
     * <p>
     * ⚑ <b>The column's TYPE follows its DATA (fixed 2026-09-02).</b> If every present value parses
     * as a {@code long} the column is {@link DataValueType#LONG} and present cells answer a boxed
     * {@code Long}; otherwise it is {@link DataValueType#STRING} and they answer the
     * {@code String}. That is how a real table decides at load: a buffer is numeric or character,
     * never a {@code LONG} column handing back {@code String}s.
     * </p>
     *
     * <p>
     * It used to be exactly that: typed {@code LONG} at both the cell and the column metadata while
     * {@code getValue()} returned the raw {@code String}. The consequence was a silent join failure
     * &mdash; {@code KeyHashing.KeyMatcher} compares keys with {@code Objects.equals}, and a
     * {@code STRING} {@code "5"} does not equal a {@code LONG} {@code 5L}, so a
     * {@code colSasMissing} column never matched a {@link #colLong} column where two real numeric
     * columns would. Pinned now by
     * {@code MockTableTest.sasMissingNumericColumnJoinsAgainstAColLongColumn}.
     * </p>
     *
     * <p>
     * ⚠ A {@code LONG} column renders the <b>canonical</b> form of its value, as a real numeric
     * buffer does: {@code colSasMissing("K", "007")} answers {@code 7L} from {@code getValue()} and
     * {@code "7"} &mdash; not {@code "007"} &mdash; from {@code getValueAsString()}. Two rows
     * spelled {@code "007"} and {@code "7"} are one cell to an index, so they must not render
     * differently. A {@code STRING} column keeps the raw text verbatim.
     * </p>
     *
     * <p>
     * ⚠ A <b>missing</b> cell stays {@code MissingValue.MIS} rendering {@code "."} in BOTH cases,
     * numeric and character. That is deliberate and is the whole point of this column kind: it is
     * what makes a missing key distinguishable from a folded one. A character column in a real SAS
     * extract would carry {@code ""} there, and using that here would re-open the vacuous
     * fold-detection hazard described above.
     * </p>
     */
    public MockTable colSasMissing(String name, String... values)
    {
        sasMissingColumns.put(name, values);
        return this;
    }


    /**
     * Sets optional column metadata — declared label, length, and display format — for an existing
     * column. Used by the metadata-accessor ({@code var_*}) tests. A {@code null} label/format or a
     * non-positive length leaves the corresponding mock getter at its default (null / 0).
     */
    public MockTable colMeta(String name, String label, int length, String format)
    {
        if (label != null)
        {
            colLabels.put(name, label);
        }
        if (length > 0)
        {
            colLengths.put(name, length);
        }
        if (format != null)
        {
            colFormats.put(name, format);
        }
        return this;
    }


    public MockTable name(String name)
    {
        this.tableName = name;
        return this;
    }


    /**
     * Makes {@code getColumnIndex} / {@code getOptionalColumn} resolve case-insensitively, which is
     * what real tables do by default ({@code DataTableMeta.columnNameCaseSensitive} is {@code
     * false}). Opt-in, so every existing test keeps the stricter exact-match stubbing.
     */
    public MockTable caseInsensitiveColumnNames()
    {
        this.caseInsensitiveColumns = true;
        return this;
    }


    public MockTable label(String label)
    {
        this.tableLabel = label;
        return this;
    }


    /** Sets the dataset's source-file URI, backing {@code getMetaData().getTableURI()}. */
    public MockTable uri(String uri)
    {
        this.tableUri = uri;
        return this;
    }


    /** Sets a generic dataset-metadata value, backing {@code getMetaData().getMetaData(key)}. */
    public MockTable metaValue(String key, Object value)
    {
        this.metaValues.put(key, value);
        return this;
    }


    public IDataTable build()
    {
        // Unified column registry: name -> per-row IDataValue + raw Object + column-meta type,
        // preserving insertion order across the three typed maps.
        // Name + insertion order only. The per-column Object[] this map used to carry was
        // read by exactly one stub (table.getValue(r, colIdx) -> raw[r]); that stub is gone,
        // replaced by an answer over the IDataValue[] so the table and column views cannot
        // disagree. Building the arrays again here would be dead work in a helper whose own
        // comments call out stubbing cost as a measured concurrency hazard.
        LinkedHashSet<String> colOrder = new LinkedHashSet<>();
        LinkedHashMap<String, IDataValue[]> dvByName = new LinkedHashMap<>();
        LinkedHashMap<String, DataValueType> colTypeByName = new LinkedHashMap<>();
        int rowCount = -1;

        for (Map.Entry<String, String[]> e : columns.entrySet())
        {
            String[] data = e.getValue();
            if (rowCount < 0)
            {
                rowCount = data.length;
            }
            IDataValue[] dvs = new IDataValue[data.length];
            for (int r = 0; r < data.length; r++)
            {
                dvs[r] = mockDataValue(data[r]);
            }
            colOrder.add(e.getKey());
            dvByName.put(e.getKey(), dvs);
            colTypeByName.put(e.getKey(), DataValueType.STRING);
        }
        for (Map.Entry<String, String[]> e : sasMissingColumns.entrySet())
        {
            String[] data = e.getValue();
            if (rowCount < 0)
            {
                rowCount = data.length;
            }
            // ⚑ The column's TYPE follows its DATA, exactly as a real table decides at load: a
            // buffer is numeric or character, never a LONG column handing back Strings. Until
            // 2026-09-02 every colSasMissing column was typed LONG while its present cells
            // answered the raw String -- a shape no buffer can produce, and one that silently
            // breaks joins, because KeyHashing.KeyMatcher compares with Objects.equals and a
            // STRING "5" does not equal a LONG 5L.
            DataValueType colType = allParseAsLong(data) ? DataValueType.LONG
                    : DataValueType.STRING;
            IDataValue[] dvs = new IDataValue[data.length];
            for (int r = 0; r < data.length; r++)
            {
                dvs[r] = mockSasMissingDataValue(data[r], colType);
            }
            colOrder.add(e.getKey());
            dvByName.put(e.getKey(), dvs);
            colTypeByName.put(e.getKey(), colType);
        }
        for (Map.Entry<String, Long[]> e : longColumns.entrySet())
        {
            Long[] data = e.getValue();
            if (rowCount < 0)
            {
                rowCount = data.length;
            }
            IDataValue[] dvs = new IDataValue[data.length];
            for (int r = 0; r < data.length; r++)
            {
                dvs[r] = mockNumericDataValue(data[r], DataValueType.LONG);
            }
            colOrder.add(e.getKey());
            dvByName.put(e.getKey(), dvs);
            colTypeByName.put(e.getKey(), DataValueType.LONG);
        }
        for (Map.Entry<String, Double[]> e : doubleColumns.entrySet())
        {
            Double[] data = e.getValue();
            if (rowCount < 0)
            {
                rowCount = data.length;
            }
            IDataValue[] dvs = new IDataValue[data.length];
            for (int r = 0; r < data.length; r++)
            {
                dvs[r] = mockNumericDataValue(data[r], DataValueType.DOUBLE);
            }
            colOrder.add(e.getKey());
            dvByName.put(e.getKey(), dvs);
            colTypeByName.put(e.getKey(), DataValueType.DOUBLE);
        }

        if (colOrder.isEmpty())
        {
            throw new IllegalStateException("Need at least one column");
        }
        String[] colNames = colOrder.toArray(String[]::new);
        final int rc = rowCount;

        IDataTable table = mock(IDataTable.class);
        DataTableMeta meta = mock(DataTableMeta.class);
        lenient().when(table.getMetaData()).thenReturn(meta);
        lenient().when(table.getRowCount()).thenReturn((long) rc);
        // MockTable represents a non-filtered/non-sorted base table — display row index
        // equals real row index. RuleRunner now emits Violation rows via getRealRowIndex
        // (commit 9b7bf4b3f), so the unstubbed default of 0 would mask the actual row.
        lenient().when(table.getRealRowIndex(anyLong())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(meta.getColumnCount()).thenReturn(colNames.length);
        lenient().when(meta.getName()).thenReturn(tableName);
        lenient().when(meta.getLabel()).thenReturn(tableLabel);
        lenient().when(meta.getTableURI())
                .thenReturn(tableUri == null ? null : java.net.URI.create(tableUri));
        metaValues.forEach((k, v) -> lenient().when(meta.getMetaData(k)).thenReturn(v));

        // Default: column not found
        lenient().when(meta.getOptionalColumn(anyString())).thenReturn(null);
        lenient().when(meta.getColumnIndex(anyString())).thenReturn(-1);

        for (int c = 0; c < colNames.length; c++)
        {
            String colName = colNames[c];
            IDataValue[] dvs = java.util.Objects.requireNonNull(dvByName.get(colName));
            final int colIdx = c;

            DataTableColumnMeta colMeta = mock(DataTableColumnMeta.class);
            lenient().when(colMeta.getName()).thenReturn(colName);
            lenient().when(colMeta.getIndex()).thenReturn(colIdx);
            DataValueType colType = colTypeByName.get(colName);
            lenient().when(colMeta.getType()).thenReturn(colType);
            lenient().when(colMeta.getLabel()).thenReturn(colLabels.get(colName));
            lenient().when(colMeta.getDisplayFormat()).thenReturn(colFormats.get(colName));
            Integer declaredLen = colLengths.get(colName);
            lenient().when(colMeta.getLength()).thenReturn(declaredLen != null ? declaredLen : 0);

            lenient().when(meta.getOptionalColumn(colName)).thenReturn(colMeta);
            lenient().when(meta.getColumnIndex(colName)).thenReturn(colIdx);
            lenient().when(meta.getColumn(colIdx)).thenReturn(colMeta);
            if (caseInsensitiveColumns)
            {
                for (String variant : new String[]
                {
                        colName.toUpperCase(java.util.Locale.ROOT),
                        colName.toLowerCase(java.util.Locale.ROOT)
                })
                {
                    lenient().when(meta.getOptionalColumn(variant)).thenReturn(colMeta);
                    lenient().when(meta.getColumnIndex(variant)).thenReturn(colIdx);
                }
            }

            IDataTableColumn col = mock(IDataTableColumn.class);
            lenient().when(col.getRowCount()).thenReturn((long) rc);
            lenient().when(table.getColumn(colIdx)).thenReturn(col);

            for (int r = 0; r < rc; r++)
            {
                lenient().when(col.getDataValue(r)).thenReturn(dvs[r]);
                lenient().when(table.getDataValue(r, colIdx)).thenReturn(dvs[r]);
            }

            // ⚠ The RAW accessor and the blankness fast paths must be stubbed too, or the mock is
            // not a faithful column: a real IDataTableColumn answers getValue(row) and
            // getDataValue(row) about the same cell, and its isMissingOrNull / isEmptyOrMissing
            // defaults are computed from getValue. Mockito does NOT run an unstubbed default
            // method — it answers false — so leaving these out makes any production code that
            // takes the allocation-free path (which is the point of those methods) read every
            // cell as a populated blank. Measured: it turned WildcardValueCollectionTest's
            // collected values from [1, 3] into ["", ""].
            //
            // ⚠⚠ Stubbed ONCE PER COLUMN with an answer, not once per (row, method) like the
            // block above. Mockito stubbing is not free: doing these four per cell added ~8k
            // stubbings to a 1024-row two-column table, and JoinCacheConcurrencyTest — which
            // rebuilds such tables on every worker thread behind a hard 60 s latch — went from
            // green to a reproducible "workers did not finish" timeout. Prefer an answer over a
            // per-row stub for anything added here.
            final IDataValue[] cells = dvs;
            // ⚠⚠ table.getValue(row, col) and col.getValue(row) MUST answer from the same
            // source. In a real table they are literally the same call —
            // ColumnCachedDataTable.getValue(row, col) is `return getColumn(col).getValue(row)`,
            // so the two views cannot disagree by construction. Stubbing table.getValue from a
            // separate raw Object[] (as this class did until 2026-09-01) reintroduced exactly
            // the disagreement a mock exists to rule out: for a SAS-missing cell the raw array
            // held a literal null while the IDataValue held MissingValue.MIS, so the two
            // accessors answered differently about the same cell.
            lenient().when(col.getValue(anyLong())).thenAnswer(inv -> cellAt(cells, inv));
            lenient().when(table.getValue(anyLong(), eq(colIdx)))
                    .thenAnswer(inv -> cellAt(cells, inv));
            // ⚠ hashCodeAt is a DEFAULT method on both IDataTable and IDataTableColumn, computed
            // from getValue -- and Mockito does not run an unstubbed default method, it answers
            // 0. Left unstubbed, every cell of every MockTable hashed to 0, so
            // KeyHashing.computeKeyHashSafe returned a constant for every row and every
            // HashLookup bucket degenerated. Nothing broke (hash and equals stayed consistent,
            // and KeyMatcher does the real comparison), but no MockTable-based test could ever
            // detect a hashCodeAt / getValue inconsistency -- the exact contract the interface
            // javadoc spells out. Stubbed here to reproduce the default's logic over the same
            // cellAt source the value accessors use, so all three views agree by construction.
            lenient().when(table.hashCodeAt(anyLong(), eq(colIdx)))
                    .thenAnswer(inv -> hashOf(cellAt(cells, inv)));
            lenient().when(col.hashCodeAt(anyLong())).thenAnswer(inv -> hashOf(cellAt(cells, inv)));
            lenient().when(col.isMissingOrNull(anyLong()))
                    .thenAnswer(inv -> isMissingOrNull(cellAt(cells, inv)));
            lenient().when(col.isEmptyOrMissing(anyLong()))
                    .thenAnswer(inv -> isBlank(cellAt(cells, inv)));
            lenient().when(table.isMissingOrNull(anyLong(), eq(colIdx)))
                    .thenAnswer(inv -> isMissingOrNull(cellAt(cells, inv)));
            lenient().when(table.isEmptyOrMissing(anyLong(), eq(colIdx)))
                    .thenAnswer(inv -> isBlank(cellAt(cells, inv)));
        }

        return table;
    }


    /**
     * The raw stored object behind the cell a stubbed accessor was asked for, taken from the same
     * {@link IDataValue} the {@code getDataValue} stubs return so the two views cannot disagree.
     * Out-of-range rows answer {@code null}, which reads as blank — the same shape a real column's
     * short-buffer edge case produces.
     */
    private static @Nullable Object cellAt(IDataValue[] aCells,
            org.mockito.invocation.InvocationOnMock aInv)
    {
        long row = aInv.getArgument(0);
        if (row < 0 || row >= aCells.length)
        {
            return null;
        }
        IDataValue dv = aCells[(int) row];
        return dv != null ? dv.getValue() : null;
    }


    /** Mirrors {@code IDataTableColumn.isMissingOrNull}'s default over a raw stored value. */
    private static boolean isMissingOrNull(@Nullable Object aCell)
    {
        return aCell == null || aCell instanceof MissingValue;
    }


    /** Mirrors {@code IDataTableColumn.isEmptyOrMissing}'s default over a raw stored value. */
    private static boolean isBlank(@Nullable Object aCell)
    {
        return isMissingOrNull(aCell) || (aCell instanceof String s && s.isEmpty());
    }


    /**
     * A cell modelling a real SAS missing marker: missing-or-invalid, and rendering {@code "."}
     * rather than {@code ""}. See {@link #colSasMissing} for why the distinction matters.
     */
    private static IDataValue mockSasMissingDataValue(@Nullable String raw, DataValueType aColType)
    {
        IDataValue dv = mock(IDataValue.class);
        if (raw == null)
        {
            lenient().when(dv.isMissingOrInvalid()).thenReturn(true);
            lenient().when(dv.getValue()).thenReturn(MissingValue.MIS);
            lenient().when(dv.getValueAsString()).thenReturn(".");
            lenient().when(dv.getValueAsDouble()).thenReturn(Double.NaN);
            lenient().when(dv.getType()).thenReturn(DataValueType.MISSING);
            return dv;
        }
        lenient().when(dv.isMissingOrInvalid()).thenReturn(false);
        // ⚠ Parse BEFORE opening any stub, for the reason spelled out below: a throw inside
        // when(...) leaves Mockito with an unfinished stubbing that surfaces in an unrelated
        // later test. allParseAsLong classified this column, so parseLong cannot throw here --
        // but keeping the parse outside the stub means a future divergence between the two
        // fails on THIS line instead of somewhere else entirely.
        Long asLong = aColType == DataValueType.LONG ? Long.valueOf(raw) : null;
        // A present cell answers a value whose TYPE matches the column's, so the cell and the
        // column metadata cannot disagree: a LONG column hands back a boxed Long, a STRING
        // column hands back the String.
        lenient().when(dv.getValue()).thenReturn(asLong != null ? (Object) asLong : raw);
        // A LONG column renders the CANONICAL form of its value, as a real numeric buffer does:
        // "007" and "7" are the same cell and must not render differently, or two rows the index
        // folds into one block (Objects.equals + equal hashes) would show two key renderings.
        lenient().when(dv.getValueAsString())
                .thenReturn(asLong != null ? Long.toString(asLong) : raw);
        lenient().when(dv.getType()).thenReturn(aColType);
        // ⚠ Parse BEFORE opening the stub. Calling Double.parseDouble inside when(...) leaves
        // Mockito with an unfinished stubbing when it throws, which surfaces as a confusing
        // UnfinishedStubbingException in an unrelated later test.
        double asDouble;
        try
        {
            asDouble = Double.parseDouble(raw);
        }
        catch (NumberFormatException _)
        {
            asDouble = Double.NaN;
        }
        lenient().when(dv.getValueAsDouble()).thenReturn(asDouble);
        return dv;
    }


    private static IDataValue mockNumericDataValue(@Nullable Number raw, DataValueType type)
    {
        IDataValue dv = mock(IDataValue.class);
        if (raw == null)
        {
            // A real typed numeric column cannot represent "null" separately from "missing":
            // DataBufferInt/Long/Double store a MISSING_SENTINEL and getValue answers
            // MissingValue.MIS, which renders "." (MissingValue.MIS displayString). Answering
            // null / "" here was a shape no real numeric column produces, and it is the exact
            // unfaithfulness colSasMissing's javadoc used to route around.
            lenient().when(dv.isMissingOrInvalid()).thenReturn(true);
            lenient().when(dv.getValue()).thenReturn(MissingValue.MIS);
            lenient().when(dv.getValueAsString()).thenReturn(".");
            lenient().when(dv.getValueAsDouble()).thenReturn(Double.NaN);
            lenient().when(dv.getType()).thenReturn(DataValueType.MISSING);
            return dv;
        }
        lenient().when(dv.isMissingOrInvalid()).thenReturn(false);
        lenient().when(dv.getValue()).thenReturn(raw);
        lenient().when(dv.getValueAsString()).thenReturn(raw.toString());
        lenient().when(dv.getValueAsDouble()).thenReturn(raw.doubleValue());
        lenient().when(dv.getType()).thenReturn(type);
        return dv;
    }


    private static IDataValue mockDataValue(@Nullable String raw)
    {
        IDataValue dv = mock(IDataValue.class);
        if (raw == null)
        {
            lenient().when(dv.isMissingOrInvalid()).thenReturn(true);
            lenient().when(dv.getValue()).thenReturn(null);
            lenient().when(dv.getValueAsString()).thenReturn("");
            lenient().when(dv.getValueAsDouble()).thenReturn(Double.NaN);
            lenient().when(dv.getType()).thenReturn(DataValueType.MISSING);
            return dv;
        }
        lenient().when(dv.isMissingOrInvalid()).thenReturn(false);
        lenient().when(dv.getValue()).thenReturn(raw);
        lenient().when(dv.getValueAsString()).thenReturn(raw);
        lenient().when(dv.getType()).thenReturn(DataValueType.STRING);
        try
        {
            double d = Double.parseDouble(raw);
            lenient().when(dv.getValueAsDouble()).thenReturn(d);
        }
        catch (NumberFormatException _)
        {
            lenient().when(dv.getValueAsDouble()).thenReturn(Double.NaN);
        }
        return dv;
    }


    /**
     * True when every <em>present</em> value of a {@link #colSasMissing} column parses as a
     * {@code long}, i.e. the column is numeric. An all-missing column counts as numeric, matching
     * the SAS {@code .} marker this column kind exists to model.
     */
    private static boolean allParseAsLong(String[] aValues)
    {
        for (String v : aValues)
        {
            if (v == null)
            {
                continue;
            }
            try
            {
                Long.parseLong(v);
            }
            catch (NumberFormatException _)
            {
                return false;
            }
        }
        return true;
    }


    /**
     * The stable hash of a raw cell value, mirroring the {@code hashCodeAt} default on
     * {@link net.cumba.datatable.IDataTable} and {@link net.cumba.datatable.IDataTableColumn}:
     * {@link MissingValue#hashCodeStable()} for a missing payload, the boxed value's own hash
     * otherwise, and {@code 0} for {@code null}.
     */
    private static int hashOf(@Nullable Object aCell)
    {
        if (aCell instanceof MissingValue mv)
        {
            return mv.hashCodeStable();
        }
        return aCell != null ? aCell.hashCode() : 0;
    }

}
