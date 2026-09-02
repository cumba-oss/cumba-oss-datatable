package net.cumba.datatable.impl.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.cumba.datatable.view.IDataTableView;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DefaultDataTableIndexTest
{

    private IDataTableView mockView()
    {
        return Mockito.mock(IDataTableView.class);
    }

    // --- Constructor ---


    @Test
    void testNullBlocksThrows()
    {
        assertThrows(NullPointerException.class, () -> new DefaultDataTableIndex(null));
    }


    /** Shared by the two tests below; a @Test method must never be invoked directly. */
    private static void assertEmptyIndexReportsNoBlocks()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[0]);
        assertEquals(0, index.getBlockCount());
    }


    @Test
    void testEmptyBlocksAllowed()
    {
        assertEmptyIndexReportsNoBlocks();
    }


    @Test
    void testDefensiveCopy()
    {
        IDataTableView view1 = mockView();
        IDataTableView view2 = mockView();
        IDataTableView[] blocks =
        {
                view1, view2
        };

        DefaultDataTableIndex index = new DefaultDataTableIndex(blocks);

        // mutating the original array should not affect the index
        blocks[0] = null;
        assertNotNull(index.getBlock(0));
        assertEquals(view1, index.getBlock(0));
    }

    // --- getBlockCount ---


    @Test
    void testBlockCountEmpty()
    {
        assertEmptyIndexReportsNoBlocks();
    }


    @Test
    void testBlockCountSingle()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                mockView()
        });
        assertEquals(1, index.getBlockCount());
    }


    @Test
    void testBlockCountMultiple()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                mockView(), mockView(), mockView()
        });
        assertEquals(3, index.getBlockCount());
    }

    // --- getBlock ---


    @Test
    void testGetBlockReturnsCorrectView()
    {
        IDataTableView v0 = mockView();
        IDataTableView v1 = mockView();
        IDataTableView v2 = mockView();
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                v0, v1, v2
        });

        assertEquals(v0, index.getBlock(0));
        assertEquals(v1, index.getBlock(1));
        assertEquals(v2, index.getBlock(2));
    }


    @Test
    void testGetBlockNegativeIndexThrows()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                mockView()
        });
        assertThrows(Exception.class, () -> index.getBlock(-1));
    }


    @Test
    void testGetBlockOutOfBoundsThrows()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                mockView()
        });
        assertThrows(Exception.class, () -> index.getBlock(1));
    }


    @Test
    void testGetBlockOnEmptyIndexThrows()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[0]);
        assertThrows(Exception.class, () -> index.getBlock(0));
    }

    // --- getBlocks stream ---


    @Test
    void testGetBlocksStreamEmpty()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[0]);
        List<IDataTableView> blocks = index.getBlocks().toList();
        assertEquals(0, blocks.size());
    }


    @Test
    void testGetBlocksStreamContainsAllBlocks()
    {
        IDataTableView v0 = mockView();
        IDataTableView v1 = mockView();
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                v0, v1
        });

        List<IDataTableView> blocks = index.getBlocks().toList();

        assertEquals(2, blocks.size());
        assertEquals(v0, blocks.get(0));
        assertEquals(v1, blocks.get(1));
    }


    @Test
    void testGetBlocksStreamPreservesOrder()
    {
        IDataTableView v0 = mockView();
        IDataTableView v1 = mockView();
        IDataTableView v2 = mockView();
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                v0, v1, v2
        });

        List<IDataTableView> blocks = index.getBlocks().toList();

        assertEquals(v0, blocks.get(0));
        assertEquals(v1, blocks.get(1));
        assertEquals(v2, blocks.get(2));
    }


    @Test
    void testGetBlocksStreamCount()
    {
        DefaultDataTableIndex index = new DefaultDataTableIndex(new IDataTableView[]
        {
                mockView(), mockView(), mockView(), mockView()
        });

        assertEquals(4, index.getBlocks().count());
    }
}
