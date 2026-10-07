package net.cumba.datatable.impl;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.cumba.datatable.ColumnContent;
import net.cumba.datatable.ColumnContentScan;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import org.jspecify.annotations.Nullable;

/**
 * An abstract implementation of the {@link IDataTable} interface. This implements most of the
 * required methods.
 */
// metaData is a required (@NonNull per IDataTable.getMetaData) field set by subclasses via the
// protected Lombok setter after construction, before any accessor use; NullAway cannot see that
// deferred init, so suppress its init check here.
@SuppressWarnings("NullAway.Init")
public abstract class AbstractDataTable implements IDataTable
{

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private DataTableMeta metaData;

    /** Guards {@link #ownContentGeneration} and {@link #contentCache}. */
    private final Object contentLock = new Object();

    /**
     * This table's own share of {@link #contentGeneration()}: bumped by
     * {@link #invalidateColumnContent()}.
     */
    private long ownContentGeneration;

    /**
     * The per-column {@link ColumnContent} cache ({@code PLAN-present-empty-column-fold} §4.1),
     * valid for {@link #contentCacheGeneration} only; a {@code null} entry is not yet scanned.
     */
    private @Nullable ColumnContent @Nullable [] contentCache;

    private long contentCacheGeneration;

    /**
     * {@inheritDoc}
     *
     * <p>
     * This table's own counter plus its {@linkplain #sourceContentGeneration() sources'}: a wrapper
     * above an editable table therefore moves with the edit it cannot see itself
     * ({@code PLAN-present-empty-column-fold} §9 V2).
     * </p>
     */
    @Override
    public long contentGeneration()
    {
        long own;
        synchronized (contentLock)
        {
            own = ownContentGeneration;
        }
        return own + sourceContentGeneration();
    }


    /**
     * The content generation of the tables this table serves cells from — {@code 0} for a table
     * that owns its cells. A subclass that reads through another table (a view, a filter, a union,
     * a merge, an overlay, an edit layer over a source) <b>must</b> override this with its sources'
     * {@link IDataTable#contentGeneration()} (summed), or its cache outlives an edit made below it.
     *
     * @return the sources' content generation
     */
    protected long sourceContentGeneration()
    {
        return 0L;
    }


    /**
     * Drops the cached column contents and bumps this table's own generation: called by a subclass
     * whenever the cells it serves may have changed (an edit, a structural change). A scan that
     * began before the call will not store its result afterwards (the generation it started under
     * is no longer current), which closes the clear-then-late-store race.
     */
    protected final void invalidateColumnContent()
    {
        synchronized (contentLock)
        {
            ownContentGeneration++;
            contentCache = null;
        }
    }


    /**
     * {@inheritDoc}
     *
     * <p>
     * Cached per instance and per column under the {@linkplain #contentGeneration() content
     * generation} the scan started under; a result is stored only while that generation is still
     * current. The answer itself comes from {@link #computeColumnContent(int)}.
     * </p>
     */
    @Override
    public ColumnContent getColumnContent(int aColumn) throws IndexOutOfBoundsException
    {
        long generation = contentGeneration();
        synchronized (contentLock)
        {
            @Nullable
            ColumnContent[] cache = contentCache;
            if (cache != null && contentCacheGeneration == generation && aColumn >= 0
                    && aColumn < cache.length)
            {
                ColumnContent cached = cache[aColumn];
                if (cached != null)
                {
                    return cached;
                }
            }
        }
        ColumnContent content = computeColumnContent(aColumn);
        // Store only under the generation the scan started with: an invalidation in between
        // (an edit's clear) must win over this result, never the other way round.
        if (contentGeneration() == generation)
        {
            synchronized (contentLock)
            {
                @Nullable
                ColumnContent[] cache = contentCache;
                if (cache == null || contentCacheGeneration != generation)
                {
                    cache = new @Nullable ColumnContent[getColumnCount()];
                    contentCache = cache;
                    contentCacheGeneration = generation;
                }
                if (aColumn >= 0 && aColumn < cache.length)
                {
                    cache[aColumn] = content;
                }
            }
        }
        return content;
    }


    /**
     * Computes one column's content status without consulting the cache: the
     * {@link ColumnContentScan} by default; a subclass whose storage knows the answer for free (a
     * constant-value buffer) overrides it.
     *
     * @param aColumn
     *            the 0-based column index
     * @return the column's content status
     * @throws IndexOutOfBoundsException
     *             when the column index is outside the table's columns
     */
    protected ColumnContent computeColumnContent(int aColumn) throws IndexOutOfBoundsException
    {
        return ColumnContentScan.scan(this, aColumn);
    }


    /**
     * A helper method that ensures that the given row number is a valid row index in this table.
     *
     * @param aRow
     *            the number to check as valid row number.
     * @return the given number (if it is valid).
     * @throws IndexOutOfBoundsException
     *             in case the given number is not a valid row index.
     */
    protected long ensureValidRow(long aRow) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || aRow >= rc)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRow, 0, rc));
        }
        return aRow;
    }

}
