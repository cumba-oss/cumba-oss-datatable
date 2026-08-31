package net.cumba.datatable.impl.index;

import java.util.Arrays;
import java.util.stream.Stream;

import lombok.NonNull;
import net.cumba.datatable.index.IDataTableIndex;
import net.cumba.datatable.view.IDataTableView;

/**
 * A simple default implementation of a {@link IDataTableIndex}.
 */
public class DefaultDataTableIndex implements IDataTableIndex
{

    private IDataTableView[] blocks;

    public DefaultDataTableIndex(@NonNull IDataTableView[] aBlocks)
    {
        blocks = Arrays.copyOf(aBlocks, aBlocks.length);
    }


    @Override
    public long getBlockCount()
    {
        return blocks.length;
    }


    @Override
    public IDataTableView getBlock(long aIndex) throws IndexOutOfBoundsException
    {
        return blocks[Math.toIntExact(aIndex)];
    }


    @Override
    public Stream<IDataTableView> getBlocks()
    {
        return Arrays.stream(blocks);
    }
}
