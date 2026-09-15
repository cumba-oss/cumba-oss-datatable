package net.cumba.datatable.testkit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
            rowCount = adoptRowCount(rowCount, e.getKey(), data.length);
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
            rowCount = adoptRowCount(rowCount, e.getKey(), data.length);
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
            rowCount = adoptRowCount(rowCount, e.getKey(), data.length);
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
            rowCount = adoptRowCount(rowCount, e.getKey(), data.length);
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

        // ⚠ A table-level accessor that Mockito answers 0 for. IDataTable.getColumnCount() is a
        // DEFAULT method (`return getMetaData().getColumnCount()`), so leaving it unstubbed made
        // every MockTable report ZERO columns through the table view while the meta view
        // reported the truth. That is not theoretical: LibraryValidator writes
        // table.getColumnCount() into every validation-report record it emits, so each one built
        // over a MockTable recorded 0 columns, silently and unassertably.
        lenient().when(table.getColumnCount()).thenReturn(colNames.length);

        // ⚠⚠ An out-of-range COLUMN index must throw, exactly as the row dimension does.
        // ColumnCachedDataTable.getColumn(int) and DataTableMeta.getColumn(int) both range-check
        // and raise IndexOutOfBoundsException. Registered BEFORE the per-column stubs so the
        // narrower eq(colIdx) ones, added later in the loop, win for the indices that exist --
        // Mockito lets the LAST matching stubbing answer.
        lenient().doAnswer(inv ->
        {
            throw outOfRangeColumn(inv.getArgument(1), colNames.length);
        }).when(table).getValue(anyLong(), anyInt());
        lenient().doAnswer(inv ->
        {
            throw outOfRangeColumn(inv.getArgument(1), colNames.length);
        }).when(table).getDataValue(anyLong(), anyInt());
        lenient().doAnswer(inv ->
        {
            throw outOfRangeColumn(inv.getArgument(1), colNames.length);
        }).when(table).hashCodeAt(anyLong(), anyInt());
        lenient().doAnswer(inv ->
        {
            throw outOfRangeColumn(inv.getArgument(1), colNames.length);
        }).when(table).isMissingOrNull(anyLong(), anyInt());
        lenient().doAnswer(inv ->
        {
            throw outOfRangeColumn(inv.getArgument(1), colNames.length);
        }).when(table).isEmptyOrMissing(anyLong(), anyInt());

        Map<String, DataTableColumnMeta> colMetaByName = new LinkedHashMap<>();
        Map<String, Integer> colIdxByName = new LinkedHashMap<>();
        List<IDataTableColumn> colsByIndex = new ArrayList<>();

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

            colMetaByName.put(colName, colMeta);
            colIdxByName.put(colName, colIdx);

            IDataTableColumn col = mock(IDataTableColumn.class);
            lenient().when(col.getRowCount()).thenReturn((long) rc);
            colsByIndex.add(col);

            // ⚠⚠ An ANSWER, not 2 x rc individual stubbings. Two reasons, and both are measured.
            // (1) Cost: the per-(row, method) spelling this replaced added 2 stubbings per cell,
            // and the sibling block below records what that did to JoinCacheConcurrencyTest.
            // (2) Bounds: a per-row stub answers Mockito's default -- null -- for any row outside
            // [0, rc), whereas a real column THROWS. CachedDataTableColumn.getDataValue starts
            // with ensureValidRow(aRow), so a null from a past-the-end read is a shape no real
            // column can produce, and a test that read one row too far passed here and would
            // have failed against a real table.
            //
            // ⚠⚠ Spelled doAnswer(...).when(mock).m(...), NOT when(mock.m(...)).thenAnswer(...).
            // The when(...) form really INVOKES the method on the mock to record the matchers,
            // with 0 for the row -- and since these answers now enforce a real column's bounds,
            // row 0 of a legitimately EMPTY table is out of range and the stubbing call itself
            // threw. doAnswer routes the answer in without an invocation, which is the idiom for
            // exactly this case, and it also removes one real mock call per stub per column.
            final IDataValue[] cells = dvs;
            // ⚠⚠ BOTH views, from the same `cells` array. IDataTable.getDataValue(row, col) is a
            // default method spelled `getColumn(aColumn).getDataValue(aRow)`, but Mockito does
            // NOT run an unstubbed default method -- it answers null -- so the table view must
            // be stubbed as well. ⚠ Do not "clean this up" on the strength of a NO_COVERAGE
            // report against the table-level answer: that reading is a lambda-attribution
            // artifact of pitest, and deleting the stub on it took 14 tests red. Measured
            // 2026-09-11.
            lenient().doAnswer(inv -> dvAt(cells, inv)).when(col).getDataValue(anyLong());
            lenient().doAnswer(inv -> dvAt(cells, inv)).when(table).getDataValue(anyLong(),
                    eq(colIdx));

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
            // ⚠⚠ table.getValue(row, col) and col.getValue(row) MUST answer from the same
            // source. In a real table they are literally the same call —
            // ColumnCachedDataTable.getValue(row, col) is `return getColumn(col).getValue(row)`,
            // so the two views cannot disagree by construction. Stubbing table.getValue from a
            // separate raw Object[] (as this class did until 2026-09-01) reintroduced exactly
            // the disagreement a mock exists to rule out: for a SAS-missing cell the raw array
            // held a literal null while the IDataValue held MissingValue.MIS, so the two
            // accessors answered differently about the same cell.
            lenient().doAnswer(inv -> cellAt(cells, inv)).when(col).getValue(anyLong());
            lenient().doAnswer(inv -> cellAt(cells, inv)).when(table).getValue(anyLong(),
                    eq(colIdx));
            // ⚠ hashCodeAt is a DEFAULT method on both IDataTable and IDataTableColumn, computed
            // from getValue -- and Mockito does not run an unstubbed default method, it answers
            // 0. Left unstubbed, every cell of every MockTable hashed to 0, so
            // KeyHashing.computeKeyHashSafe returned a constant for every row and every
            // HashLookup bucket degenerated. Nothing broke (hash and equals stayed consistent,
            // and KeyMatcher does the real comparison), but no MockTable-based test could ever
            // detect a hashCodeAt / getValue inconsistency -- the exact contract the interface
            // javadoc spells out. Stubbed here to reproduce the default's logic over the same
            // cellAt source the value accessors use, so all three views agree by construction.
            lenient().doAnswer(inv -> hashOf(cellAt(cells, inv))).when(table).hashCodeAt(anyLong(),
                    eq(colIdx));
            lenient().doAnswer(inv -> hashOf(cellAt(cells, inv))).when(col).hashCodeAt(anyLong());
            lenient().doAnswer(inv -> isMissingOrNull(cellAt(cells, inv))).when(col)
                    .isMissingOrNull(anyLong());
            lenient().doAnswer(inv -> isBlank(cellAt(cells, inv))).when(col)
                    .isEmptyOrMissing(anyLong());
            lenient().doAnswer(inv -> isMissingOrNull(cellAt(cells, inv))).when(table)
                    .isMissingOrNull(anyLong(), eq(colIdx));
            lenient().doAnswer(inv -> isBlank(cellAt(cells, inv))).when(table)
                    .isEmptyOrMissing(anyLong(), eq(colIdx));
        }

        // ⚠⚠ Name resolution is stubbed ONCE, over an answer, rather than per column plus a
        // hard-coded ALL-UPPER / all-lower pair. Two things were wrong with the pair: a real
        // table is case-insensitive through CDT.equalsIgnoreCase, so "UsubjId" resolves against
        // a USUBJID column and did NOT resolve here even with the opt-in on; and the three
        // contains*Column DEFAULT methods of IDataTableMeta -- which Mockito does not run --
        // answered FALSE for every column the table actually has.
        // ⚠⚠ Captured ONCE, here, not read from the field inside each answer. An answer runs at
        // CALL time, so reading this.caseInsensitiveColumns from inside one would make an
        // already-built table's name resolution change retroactively if the builder were mutated
        // afterwards -- a fixture that is not frozen by build(). Every other value these answers
        // close over (cells, colMetaByName, colIdxByName, colsByIndex, colNames) is build-local
        // and never mutated after registration; this field was the only late-bound one.
        final boolean ci = caseInsensitiveColumns;
        lenient().doAnswer(inv -> resolve(colMetaByName, inv.getArgument(0), ci)).when(meta)
                .getOptionalColumn(anyString());
        lenient().doAnswer(inv ->
        {
            Integer idx = resolve(colIdxByName, inv.getArgument(0), ci);
            return idx != null ? idx : -1;
        }).when(meta).getColumnIndex(anyString());
        // IDataTable.getColumnIndex is a DEFAULT method delegating to the meta, so unstubbed it
        // answered 0 -- i.e. "column 0" -- for a name the table does NOT have, contradicting the
        // -1 the meta view answers and disarming every `idx < 0` guard in IDataTable's own
        // by-name accessors.
        lenient().doAnswer(inv ->
        {
            Integer idx = resolve(colIdxByName, inv.getArgument(0), ci);
            return idx != null ? idx : -1;
        }).when(table).getColumnIndex(anyString());
        lenient().doAnswer(inv -> resolve(colMetaByName, inv.getArgument(0), ci) != null).when(meta)
                .containsColumn(anyString());
        lenient().doAnswer(inv ->
        {
            for (String cn : varargNames(inv))
            {
                if (cn == null || resolve(colMetaByName, cn, ci) == null)
                {
                    return false;
                }
            }
            return true;
        }).when(meta).containsAllColumns(any(String[].class));
        lenient().doAnswer(inv ->
        {
            for (String cn : varargNames(inv))
            {
                if (cn != null && resolve(colMetaByName, cn, ci) != null)
                {
                    return true;
                }
            }
            return false;
        }).when(meta).containsAnyColumn(any(String[].class));
        lenient().doAnswer(inv ->
        {
            int idx = inv.getArgument(0);
            if (idx < 0 || idx >= colNames.length)
            {
                throw outOfRangeColumn(idx, colNames.length);
            }
            return colMetaByName.get(colNames[idx]);
        }).when(meta).getColumn(anyInt());

        // ⚠⚠ The WHOLE by-name family, or none of it. Stubbing getOptionalColumn / getColumnIndex
        // / contains*Column and stopping there leaves the mock CONTRADICTING ITSELF: a production
        // path that asks containsColumn("A") (true) and then getColumn("A") took the happy branch
        // and got null. Everything below is either a DEFAULT method of IDataTableMeta /
        // IDataTable, which Mockito does not run, or a concrete method of the mocked
        // DataTableMeta class, which Mockito replaces -- so every one of them answered null or an
        // EMPTY Stream. getAllColumns alone is read at 63 main-code sites and the getColumns
        // overloads at 94, and an empty Stream there is the campaign's signature shape: an
        // operation that quietly finds nothing and reports success.
        DataTableColumnMeta[] colMetaArr = new DataTableColumnMeta[colNames.length];
        for (int c = 0; c < colNames.length; c++)
        {
            colMetaArr[c] = colMetaByName.get(colNames[c]);
        }
        // ⚠ A FRESH Stream per call -- a Stream is single-use, so answering one cached instance
        // would work once and throw IllegalStateException on the second reader.
        lenient().doAnswer(inv -> Arrays.stream(colMetaArr)).when(meta).getAllColumns();
        lenient().doAnswer(inv -> Arrays.copyOf(colMetaArr, colMetaArr.length)).when(meta)
                .getColumns();
        lenient().doAnswer(inv -> requireColumn(colMetaByName, inv.getArgument(0), ci)).when(meta)
                .getColumn(anyString());
        lenient().doAnswer(inv ->
        {
            List<DataTableColumnMeta> out = new ArrayList<>();
            for (String cn : varargNames(inv))
            {
                DataTableColumnMeta cm = cn != null ? resolve(colMetaByName, cn, ci) : null;
                if (cm != null)
                {
                    out.add(cm);
                }
            }
            return out.stream();
        }).when(meta).getOptionalColumns(any(String[].class));
        lenient().doAnswer(inv -> namedColumns(colMetaByName, varargNames(inv), ci)).when(meta)
                .getColumns(any(String[].class));
        lenient().doAnswer(inv ->
        {
            Collection<String> names = inv.getArgument(0);
            return namedColumns(colMetaByName, names.toArray(String[]::new), ci);
        }).when(meta).getColumns(anyCollection());
        lenient().doAnswer(inv ->
        {
            List<DataTableColumnMeta> out = new ArrayList<>();
            for (int idx : varargInts(inv))
            {
                if (idx < 0 || idx >= colMetaArr.length)
                {
                    throw outOfRangeColumn(idx, colMetaArr.length);
                }
                out.add(colMetaArr[idx]);
            }
            return out.stream();
        }).when(meta).getColumns(any(int[].class));

        // The table-side mirrors. IDataTable.getColumn(String) resolves through the META's index
        // and throws NoSuchElementException for an unknown name; the getColumns overloads map
        // that index onto this table's columns.
        lenient().doAnswer(inv ->
        {
            String name = inv.getArgument(0);
            Integer idx = resolve(colIdxByName, name, ci);
            if (idx == null)
            {
                throw new NoSuchElementException("No column found for name " + name);
            }
            return colsByIndex.get(idx);
        }).when(table).getColumn(anyString());
        lenient().doAnswer(inv -> colsByIndex.stream()).when(table).getColumns();
        lenient().doAnswer(inv -> tableColumns(colIdxByName, colsByIndex, varargNames(inv), ci))
                .when(table).getColumns(any(String[].class));
        lenient().doAnswer(inv ->
        {
            Collection<String> names = inv.getArgument(0);
            return tableColumns(colIdxByName, colsByIndex, names.toArray(String[]::new), ci);
        }).when(table).getColumns(anyCollection());
        lenient().doAnswer(inv ->
        {
            List<IDataTableColumn> out = new ArrayList<>();
            for (int idx : varargInts(inv))
            {
                if (idx < 0 || idx >= colsByIndex.size())
                {
                    throw outOfRangeColumn(idx, colsByIndex.size());
                }
                out.add(colsByIndex.get(idx));
            }
            return out.stream();
        }).when(table).getColumns(any(int[].class));
        lenient().doAnswer(inv ->
        {
            int idx = inv.getArgument(0);
            if (idx < 0 || idx >= colNames.length)
            {
                throw outOfRangeColumn(idx, colNames.length);
            }
            return colsByIndex.get(idx);
        }).when(table).getColumn(anyInt());

        return table;
    }


    /**
     * Looks a column name up, honouring {@link #caseInsensitiveColumnNames()}. Exact match first in
     * both modes, so a case-sensitive table behaves exactly as it always did.
     */
    private static <T> @Nullable T resolve(Map<String, T> aByName, String aName,
            boolean aIgnoreCase)
    {
        T exact = aByName.get(aName);
        if (exact != null || !aIgnoreCase)
        {
            return exact;
        }
        for (Map.Entry<String, T> e : aByName.entrySet())
        {
            if (e.getKey().equalsIgnoreCase(aName))
            {
                return e.getValue();
            }
        }
        return null;
    }


    /**
     * The column names of a {@code contains*Columns(String...)} invocation. Mockito hands a varargs
     * answer its arguments <b>expanded</b>, one element per slot &mdash; measured, not assumed: the
     * raw-array branch this method used to carry was reported NO_COVERAGE, so it was defensive code
     * for a form that never arrives. If that ever changes, the cast fails loudly here rather than
     * answering a wrong column set.
     */
    private static String[] varargNames(org.mockito.invocation.InvocationOnMock aInv)
    {
        Object[] args = aInv.getArguments();
        String[] names = new String[args.length];
        for (int i = 0; i < args.length; i++)
        {
            names[i] = (String) args[i];
        }
        return names;
    }


    /**
     * The column metadata for a name, or {@link NoSuchElementException} &mdash; mirroring
     * {@code IDataTableMeta.getColumn(String)}, which is never null and never silent.
     */
    private static DataTableColumnMeta requireColumn(Map<String, DataTableColumnMeta> aByName,
            String aName, boolean aIgnoreCase)
    {
        DataTableColumnMeta cm = resolve(aByName, aName, aIgnoreCase);
        if (cm == null)
        {
            throw new NoSuchElementException("No column available for name %s.".formatted(aName));
        }
        return cm;
    }


    /** {@code getColumns(String...)} over the meta view: every name must resolve. */
    private static java.util.stream.Stream<DataTableColumnMeta> namedColumns(
            Map<String, DataTableColumnMeta> aByName, String[] aNames, boolean aIgnoreCase)
    {
        List<DataTableColumnMeta> out = new ArrayList<>();
        for (String cn : aNames)
        {
            out.add(requireColumn(aByName, cn, aIgnoreCase));
        }
        return out.stream();
    }


    /** {@code getColumns(String...)} over the table view: every name must resolve. */
    private static java.util.stream.Stream<IDataTableColumn> tableColumns(
            Map<String, Integer> aIdxByName, List<IDataTableColumn> aColumns, String[] aNames,
            boolean aIgnoreCase)
    {
        List<IDataTableColumn> out = new ArrayList<>();
        for (String cn : aNames)
        {
            Integer idx = resolve(aIdxByName, cn, aIgnoreCase);
            if (idx == null)
            {
                throw new NoSuchElementException("No column found for name " + cn);
            }
            out.add(aColumns.get(idx));
        }
        return out.stream();
    }


    /**
     * The column indices of a {@code getColumns(int...)} invocation. Expanded, one element per
     * slot, for the reason {@link #varargNames} records &mdash; the raw-array branch this method
     * used to carry was reported NO_COVERAGE, i.e. it was defensive code for a form Mockito never
     * hands over.
     */
    private static int[] varargInts(org.mockito.invocation.InvocationOnMock aInv)
    {
        Object[] args = aInv.getArguments();
        int[] out = new int[args.length];
        for (int i = 0; i < args.length; i++)
        {
            out[i] = (Integer) args[i];
        }
        return out;
    }


    /** The exception a real table raises for a column index outside {@code [0, count)}. */
    private static IndexOutOfBoundsException outOfRangeColumn(int aIndex, int aCount)
    {
        return new IndexOutOfBoundsException(
                "column " + aIndex + " is out of bounds, valid range is [0, " + aCount + ")");
    }


    /**
     * The raw stored object behind the cell a stubbed accessor was asked for, taken from the same
     * {@link IDataValue} the {@code getDataValue} stubs return so the two views cannot disagree.
     *
     * <p>
     * ⚠⚠ <b>An out-of-range row THROWS {@link IndexOutOfBoundsException}, because that is what a
     * real column does</b> &mdash; every accessor of {@code CachedDataTableColumn} opens with
     * {@code ensureValidRow(aRow)}, and {@code ColumnCachedDataTable} re-checks {@code aRow}
     * against {@code getRowCount()} before delegating. Until 2026-09-11 this method answered
     * {@code null} for such a row, which read as <em>blank</em> through every fast path, and its
     * javadoc justified that as "the same shape a real column's short-buffer edge case produces".
     * <b>That was wrong twice over</b>: the short-buffer case is a row that IS in range, and what
     * it yields is {@code MissingValue.MIS_ERROR} (a {@code DataValueMissing} from
     * {@code getDataValue}), never {@code null} &mdash; so its stable hash is
     * {@code MIS_ERROR.hashCodeStable()}, not {@code 0}. A past-the-end read simply throws. A
     * downstream test that walked one row too far therefore passed against this mock and would have
     * failed against every real table.
     * </p>
     */
    private static @Nullable Object cellAt(IDataValue[] aCells,
            org.mockito.invocation.InvocationOnMock aInv)
    {
        IDataValue dv = dvAt(aCells, aInv);
        return dv != null ? dv.getValue() : null;
    }


    /**
     * The {@link IDataValue} a stubbed accessor was asked for, with the bounds semantics
     * {@link #cellAt} documents. The single source both the cell views and the value views read, so
     * they cannot disagree.
     */
    private static @Nullable IDataValue dvAt(IDataValue[] aCells,
            org.mockito.invocation.InvocationOnMock aInv)
    {
        long row = aInv.getArgument(0);
        if (row < 0 || row >= aCells.length)
        {
            throw new IndexOutOfBoundsException(
                    "row " + row + " is out of bounds, valid range is [0, " + aCells.length + ")");
        }
        return aCells[(int) row];
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
        // ⚠⚠ A LONG column answers the number; a STRING one answers NaN, because that is what
        // DataValueLong and DataValueString respectively do -- DataValueString overrides
        // getValueAsDouble with a hard `return Double.NaN`. Parsing the text for a character
        // cell (as this did until 2026-09-11) is a shape no real column produces; see
        // mockDataValue for what it did to ScalarSemantics' numeric branch.
        lenient().when(dv.getValueAsDouble())
                .thenReturn(asLong != null ? asLong.doubleValue() : Double.NaN);
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
        // ⚠⚠ NaN, NOT the parsed number. DataValueString -- the cell EVERY real character column
        // hands back (DataBufferString.getDataValue -> new DataValueString(...)) -- OVERRIDES the
        // IDataValue default with a hard `return Double.NaN`. So no real character column can
        // produce a STRING-typed cell whose getValueAsDouble() is finite, and this mock did:
        // a cell "007" answered 7.0, which made ScalarSemantics.equalsNumericAware take its
        // numeric branch and call "007" equal to 7L where the product falls back to a textual
        // fold and says NOT equal.
        //
        // ⚠ The product has BOTH shapes and this models the COLUMN-CELL one, which is the right
        // choice for a column fixture. corej-core's own DataValues builds an IDataValue that
        // reports STRING and DOES parse its text -- used for broadcast literals, $-op results and
        // joined-dataset string values -- and ScalarSemantics documents "1.0"/"01"/"1" all
        // matching member 1 as intended THERE. What no real column can hand back is a cell read
        // off a character buffer whose getValueAsDouble() is finite, and that is what this was.
        // Fixed 2026-09-11; see the lane report for the downstream impact.
        lenient().when(dv.getValueAsDouble()).thenReturn(Double.NaN);
        return dv;
    }


    /**
     * Adopts the row count of the first column declared, and <b>rejects a ragged table</b>: every
     * column must carry the same number of values.
     *
     * <p>
     * ⚠⚠ <b>Not because a real table cannot be ragged &mdash; it can.</b>
     * {@code ColumnCachedDataTable}'s constructor does no length validation, its
     * {@code getRowCount()} answers <em>column 0's</em> count, and
     * {@code CachedDataTableColumn.setTableRowCount(long)} exists precisely to raise a short
     * column, after which its short-buffer arms serve {@code MissingValue.MIS_ERROR}. So
     * production's answer to ragged input is <b>pad and serve MIS_ERROR</b>. A <em>fixture</em>
     * must not do that: the rows come from a literal the test author wrote, so quietly dropping one
     * makes the test assert over data it did not declare.
     *
     * <p>
     * Until 2026-09-11 exactly that happened. A <em>longer</em> later column was silently
     * <b>truncated</b> &mdash; its extra values were never stubbed, so the fixture held less data
     * than the test declared. A <em>shorter</em> one died deep inside the stubbing loop with a bare
     * {@code ArrayIndexOutOfBoundsException} naming nothing. The truncating half was not
     * hypothetical: it hid a stray second value in a downstream rule test for as long as that test
     * existed (see the lane report), and the test passed either way, which is precisely why it was
     * never noticed.
     * </p>
     *
     * @param aRowCount
     *            the count adopted so far, or {@code -1} before any column has been seen.
     * @param aColumnName
     *            the column being added, named in the failure message.
     * @param aLength
     *            that column's number of values.
     * @return the table's row count.
     * @throws IllegalStateException
     *             if this column's length disagrees with the count already adopted.
     */
    private static int adoptRowCount(int aRowCount, String aColumnName, int aLength)
    {
        if (aRowCount < 0)
        {
            return aLength;
        }
        if (aLength != aRowCount)
        {
            throw new IllegalStateException("Ragged MockTable: column '" + aColumnName + "' has "
                    + aLength + " values but the table already has " + aRowCount
                    + " rows. Every column of a FIXTURE must carry the same number of values:"
                    + " a real table pads a short column and serves MissingValue.MIS_ERROR, but"
                    + " here the values are a literal the test wrote, and silently dropping one"
                    + " makes the test assert over data it did not declare.");
        }
        return aRowCount;
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
