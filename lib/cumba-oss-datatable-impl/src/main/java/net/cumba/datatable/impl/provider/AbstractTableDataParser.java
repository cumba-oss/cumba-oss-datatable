package net.cumba.datatable.impl.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.AsyncSupport;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.provider.IDataTableProvider;
import org.jspecify.annotations.Nullable;

/**
 * An abstract table data parser that can be used to parse data from a table into
 * {@link CachedDataTableColumn}'s when the table is read row based. The table parser works in an
 * asynchronous multi-threaded way, but is not thread safe, the {@link #addDataRow(Object)} method
 * should only be called from one thread or must be synchronized externally.<br/>
 * The parser can be configured by {@link #setRowSliceSize(int)} and {@code setExecutor(Executor)}.
 *
 * @param <T>
 *            the object type that represents a data row.<br/>
 *            Reading from this class must be thread safe and each instance must cache its data as
 *            it will be cached until a configured number of rows is read and then passed to
 *            {@link #addData2Column(List, int, DataTableColumnMeta, CachedDataTableColumn)} in
 *            parallel for each column.
 */
public abstract class AbstractTableDataParser<T>
{

    /**
     * Handle the given String by performing the following tasks:
     * <ol>
     * <li>Call {@link CDT#trimRight(String)}
     * <li>Call {@link String#intern()}
     * </ol>
     * This should be called for all Strings parsed from a data table to reduce space consumption.
     *
     * @param aString
     *            the String to handle, this might be null.
     * @return if aString is null, null is returned, otherwise the handled version of the String.
     */
    public static @Nullable String tri(@Nullable String aString)
    {
        return tri(aString, null);
    }


    /**
     * Handle the given String by calling {@link CDT#tri(String)} if the given string is not null,
     * otherwise return default.
     *
     * @param aString
     *            the String to handle, this might be null.
     * @param aDefault
     *            the default value to return in case aString is null.
     * @return if aString is null, aDefault is returned, otherwise the handled version of the
     *         String.
     */
    public static @Nullable String tri(@Nullable String aString, @Nullable String aDefault)
    {
        if (aString == null)
        {
            return aDefault;
        }
        return CDT.tri(aString);
    }

    /**
     * The table metadata of the table to parse data.
     */
    protected DataTableMeta metaData;

    /**
     * Internal array of data columns for the table that is parsed.
     */
    protected CachedDataTableColumn[] dataColumns;

    /**
     * The current row slice. This is used to store rows until the slice has the configured batch
     * size.
     */
    private @Nullable List<T> rowSlice;

    /**
     * The number of rows to process in one batch. Always {@code >= 1}.
     */
    @Getter
    private int rowSliceSize = 10_000;

    /**
     * The optional executor to be used to process the row slice.
     */
    @Getter
    @Setter
    private @Nullable Executor executor;

    /**
     * The current future that might still be running to add data into the columns.
     */
    private @Nullable CompletableFuture<Void> currentFuture;

    /**
     * The number of rows that have been added to this data parser.
     */
    private long rowCount = 0;

    @SuppressWarnings("this-escape")
    protected AbstractTableDataParser(@NonNull IDataTableProvider aProvider,
            @NonNull DataTableMeta aMeta)
    {
        int colCount = aMeta.getColumnCount();

        if (colCount < 1)
        {
            throw new IllegalArgumentException("At least 1 column must be specified!");
        }

        metaData = aMeta;
        dataColumns = createCachedColumns(aMeta);

        rowSlice = new ArrayList<>(rowSliceSize);
    }


    /**
     * Set the number of rows to buffer before flushing a slice to the per-column workers.
     *
     * @param aSize
     *            the row-slice size. Must be {@code >= 1}.
     * @throws IllegalArgumentException
     *             when {@code aSize < 1}.
     */
    public void setRowSliceSize(int aSize)
    {
        if (aSize < 1)
        {
            throw new IllegalArgumentException("rowSliceSize must be >= 1");
        }
        this.rowSliceSize = aSize;
    }


    /**
     * Can be called to add a single row into the row slice buffer.
     *
     * @param aRow
     *            the row data object. Reading from this buffer must be thread safe and the instance
     *            must cache its data internally. Processing of this row is done delayed and async
     *            once the batch is full.
     */
    public void addDataRow(T aRow) throws IOException
    {
        if (rowSlice == null)
        {
            throw new IllegalStateException("Parser already completed.");
        }

        rowCount++;
        rowSlice.add(aRow);
        if (rowSlice.size() >= rowSliceSize)
        {
            List<T> curSlice = rowSlice;
            rowSlice = new ArrayList<>(rowSliceSize);
            processRowSlice(curSlice);
        }
    }


    /**
     * Complete the parsing of all still cached rows and create the final table.
     *
     * @return the data table that was parsed.
     */
    public IDataTable completeTable() throws IOException
    {
        if (rowSlice != null)
        {
            if (!rowSlice.isEmpty())
            {
                // we still have one or more rows in the buffer --> process them
                List<T> curSlice = rowSlice;
                rowSlice = null;
                processRowSlice(curSlice);
            }
            rowSlice = null;
        }

        // wait until the last buffer is finally processed.
        completeCurrentFuture();

        // complete all columns
        completeParsedColumns();

        // TODO: check all columns for correct row count (compare against rowCount field)

        // Reconcile declared column lengths with the actual max length observed in the parsed
        // values. Providers whose source file format allows unknown / missing length (e.g.
        // DataSet-JSON's sentinel -1, or formats that simply don't carry a length attribute) end
        // up with declared < observed; downstream consumers (rule engine, UI, exporters) rely on
        // the metadata length being an upper bound for the values.
        DataTableColumnMeta[] reconciledColumns = reconcileColumnLengths(metaData);

        DataTableMeta.DataTableMetaBuilder metaBuilder = DataTableMeta.builderFrom(metaData)
                .rowCount(rowCount).totalRowCount(rowCount);
        if (reconciledColumns != null)
        {
            metaBuilder.columns(reconciledColumns);
        }
        DataTableMeta tableMeta = metaBuilder.build();

        IDataTable table = new ColumnCachedDataTable(tableMeta, dataColumns);

        return table;

    }


    /**
     * Complete and optimize all parsed columns after all rows are parsed. This method executes the
     * optimization for each column in parallel.
     *
     * @throws IOException
     *             if any column-completion task failed with an {@link IOException}.
     */
    protected void completeParsedColumns() throws IOException
    {
        CompletableFuture<?>[] futures;
        if (executor == null)
        {
            futures = Arrays.stream(dataColumns)//
                    .map(c -> CompletableFuture.runAsync(c::complete))//
                    .toArray(CompletableFuture[]::new);
        }
        else
        {
            futures = Arrays.stream(dataColumns)//
                    .map(c -> CompletableFuture.runAsync(c::complete, executor))//
                    .toArray(CompletableFuture[]::new);
        }

        AsyncSupport.joinOrThrowIO(CompletableFuture.allOf(futures));
    }


    /**
     * Compares the declared length of every STRING column in {@code aMeta} against the
     * {@link CachedDataTableColumn}'s {@code getMaxValueLength()} (actual max observed length)
     * captured by {@link CachedDataTableColumn#complete()} and produces an updated column array
     * when any declared length is less than the observed length.
     *
     * <p>
     * Returns {@code null} when no column needs adjustment — callers should then keep the original
     * metadata untouched. Non-string columns are never adjusted.
     * </p>
     *
     * <p>
     * Rationale: downstream consumers (rule engine, UI, exporters) rely on the declared length
     * being an upper bound of the actual values. A declared length smaller than a stored value is a
     * data-quality defect that needs to be visible rather than silently trusted.
     * </p>
     */
    protected DataTableColumnMeta @Nullable [] reconcileColumnLengths(DataTableMeta aMeta)
    {
        int colCount = aMeta.getColumnCount();
        DataTableColumnMeta[] out = null;
        for (int i = 0; i < colCount; i++)
        {
            DataTableColumnMeta cm = aMeta.getColumn(i);
            if (cm.getType() != net.cumba.datatable.values.DataValueType.STRING)
            {
                continue;
            }
            int declared = cm.getLength();
            int observed = dataColumns[i].getMaxValueLength();
            if (observed <= declared)
            {
                // Declared length already covers all values — nothing to do for this column.
                continue;
            }
            if (out == null)
            {
                out = new DataTableColumnMeta[colCount];
                for (int j = 0; j < colCount; j++)
                {
                    out[j] = aMeta.getColumn(j);
                }
            }
            out[i] = cm.toBuilder().length(observed)._build();
        }
        return out;
    }


    /**
     * Create the array of cached data columns to be filled.
     *
     * @param aMeta
     *            the metadata to create the data columns for.
     * @return the array of data columns.
     */
    protected CachedDataTableColumn[] createCachedColumns(DataTableMeta aMeta)
    {
        CachedDataTableColumn[] cols = new CachedDataTableColumn[aMeta.getColumnCount()];

        for (int i = 0; i < cols.length; i++)
        {
            DataTableColumnMeta cm = aMeta.getColumn(i);
            cols[i] = new CachedDataTableColumn(i, cm.getType());
        }
        return cols;
    }


    /**
     * Called to ensure the currently running future is completed.
     */
    protected void completeCurrentFuture() throws IOException
    {
        if (currentFuture != null)
        {
            AsyncSupport.joinOrThrowIO(currentFuture);
            currentFuture = null;
        }
    }


    /**
     * Called to add the data of the given rows to the data columns. This method starts async tasks
     * to add the data in background and completes before all data is processed.
     *
     * @param aRowSlice
     *            the buffer row rows to be processed.
     * @see #completeCurrentFuture()
     */
    protected void processRowSlice(List<T> aRowSlice) throws IOException
    {
        completeCurrentFuture();

        IntFunction<CompletableFuture<Void>> ifnc = idx ->
        {

            Runnable r = () ->
            {
                DataTableColumnMeta mc = metaData.getColumn(idx);
                CachedDataTableColumn cc = dataColumns[idx];
                addData2Column(aRowSlice, idx, mc, cc);
            };

            if (executor != null)
            {
                return CompletableFuture.runAsync(r, executor);
            }
            else
            {
                return CompletableFuture.runAsync(r);
            }
        };

        CompletableFuture<?>[] futures = IntStream.range(0, metaData.getColumnCount())
                .mapToObj(ifnc).toArray(CompletableFuture[]::new);

        currentFuture = CompletableFuture.allOf(futures);

    }


    /**
     * Must be implemented to add data from the given list of rows into the given column. This
     * method is called in parallel for each column with the configured number of rows.
     *
     * @param aRowSlice
     *            the slice of rows to add to the column.
     * @param aColumnIndex
     *            the index of the column to process the data for.
     * @param aMetaColumn
     *            the metadata of the column to process.
     * @param aDataColumn
     *            the data column to add the data to.
     */
    protected abstract void addData2Column(List<T> aRowSlice, int aColumnIndex,
            DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn);

}
