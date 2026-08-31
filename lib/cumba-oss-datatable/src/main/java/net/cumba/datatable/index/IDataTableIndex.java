package net.cumba.datatable.index;

import java.util.stream.LongStream;
import java.util.stream.Stream;

import net.cumba.datatable.IDataTable;
import net.cumba.datatable.view.IDataTableView;

/**
 * A index of a {@link IDataTable}.<br/>
 * An index groups a data table (or a subset) into groups of {@link IDataTableView}s. Each group
 * shares the same values for the index columns.<br/>
 * The index (as view) does not save the groups in any special order by contract, the index creator
 * can still provide a special order when creating the index.
 */
public interface IDataTableIndex
{

    /**
     * Returns the number of index blocks.
     *
     * @return the number of index blocks.
     */
    long getBlockCount();


    /**
     * Retrieve a index block from this index.
     *
     * @param aIndex
     *            the (0-based) index of the block in the index.
     * @return the block at the given index.
     * @throws IndexOutOfBoundsException
     *             in case the given aIndex is outside of the valid bounds.
     */
    IDataTableView getBlock(long aIndex) throws IndexOutOfBoundsException;


    /**
     * Returns a stream of all blocks in the index.
     *
     * @return a stream of all blocks in the index.
     */
    default Stream<IDataTableView> getBlocks()
    {
        return LongStream.range(0, getBlockCount()).mapToObj(this::getBlock);
    }
}
