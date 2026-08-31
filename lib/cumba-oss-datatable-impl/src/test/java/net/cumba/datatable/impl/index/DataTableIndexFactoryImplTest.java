package net.cumba.datatable.impl.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.index.IDataTableIndex;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.view.IDataTableView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataTableIndexFactoryImplTest
{

    private DataTableIndexFactoryImpl factory;

    @BeforeEach
    void setUp()
    {
        factory = new DataTableIndexFactoryImpl();
    }


    private IDataTable createTable(String name, String[] colNames, DataValueType[] colTypes,
            Object[][] data)
    {
        int colCount = colNames.length;
        int rowCount = data.length;

        CachedDataTableColumn[] columns = new CachedDataTableColumn[colCount];
        DataTableColumnMeta[] colMetas = new DataTableColumnMeta[colCount];

        for (int c = 0; c < colCount; c++)
        {
            columns[c] = new CachedDataTableColumn(c, colTypes[c]);
            colMetas[c] = DataTableColumnMeta.builder().index(c).name(colNames[c]).type(colTypes[c])
                    .build();
        }

        for (int r = 0; r < rowCount; r++)
        {
            for (int c = 0; c < colCount; c++)
            {
                columns[c].addElement(data[r][c]);
            }
        }

        for (CachedDataTableColumn col : columns)
        {
            col.complete();
        }

        DataTableMeta meta = DataTableMeta.builder().name(name).columns(colMetas).rowCount(rowCount)
                .totalRowCount(rowCount).build();

        return new ColumnCachedDataTable(meta, columns);
    }

    // --- Validation tests ---


    @Test
    void testNullTableThrows()
    {
        assertThrows(IllegalArgumentException.class, () -> factory.createIndex(null, "COL"));
    }


    @Test
    void testNullColumnsThrows()
    {
        IDataTable table = createTable("T", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "x"
                }
        });
        assertThrows(IllegalArgumentException.class,
                () -> factory.createIndex(table, (String[]) null));
    }


    @Test
    void testEmptyColumnsThrows()
    {
        IDataTable table = createTable("T", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "x"
                }
        });
        assertThrows(IllegalArgumentException.class, () -> factory.createIndex(table));
    }


    @Test
    void testUnknownColumnThrows()
    {
        IDataTable table = createTable("T", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "x"
                }
        });
        assertThrows(IllegalArgumentException.class,
                () -> factory.createIndex(table, "NONEXISTENT"));
    }

    // --- Empty table ---


    @Test
    void testEmptyTableHashIndex()
    {
        IDataTable table = createTable("T", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[0][]);
        IDataTableIndex index = factory.createIndex(table, "A");
        assertNotNull(index);
        assertEquals(0, index.getBlockCount());
    }

    // --- Single group (all rows have same key) ---


    @Test
    void testSingleGroupHashIndex()
    {
        IDataTable table = createTable("T", new String[]
        {
                "GRP", "VAL"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "A", 1L
                },
                {
                        "A", 2L
                },
                {
                        "A", 3L
                }
        });

        IDataTableIndex index = factory.createIndex(table, "GRP");

        assertEquals(1, index.getBlockCount());
        IDataTableView block = index.getBlock(0);
        assertEquals(3, block.getRowCount(table));
    }

    // --- All unique rows ---


    @Test
    void testAllUniqueHashIndex()
    {
        IDataTable table = createTable("T", new String[]
        {
                "ID"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "A"
                },
                {
                        "B"
                },
                {
                        "C"
                },
                {
                        "D"
                }
        });

        IDataTableIndex index = factory.createIndex(table, "ID");

        assertEquals(4, index.getBlockCount());
        for (int i = 0; i < 4; i++)
        {
            assertEquals(1, index.getBlock(i).getRowCount(table));
        }
    }

    // --- Multiple groups ---


    @Test
    void testMultipleGroupsHashIndex()
    {
        IDataTable table = createTable("T", new String[]
        {
                "GRP", "VAL"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "A", 1L
                },
                {
                        "B", 2L
                },
                {
                        "A", 3L
                },
                {
                        "C", 4L
                },
                {
                        "B", 5L
                },
                {
                        "A", 6L
                }
        });

        IDataTableIndex index = factory.createIndex(table, "GRP");

        assertEquals(3, index.getBlockCount());
        assertTotalRowsCovered(index, table, 6);
        assertGroupContainsRows(index, table, "GRP", "A", new long[]
        {
                0, 2, 5
        });
        assertGroupContainsRows(index, table, "GRP", "B", new long[]
        {
                1, 4
        });
        assertGroupContainsRows(index, table, "GRP", "C", new long[]
        {
                3
        });
    }

    // --- Multi-column keys ---


    @Test
    void testMultiColumnKeyHashIndex()
    {
        IDataTable table = createTable("T", new String[]
        {
                "SITE", "SUBJ", "VAL"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "S1", "P1", 10L
                },
                {
                        "S1", "P2", 20L
                },
                {
                        "S2", "P1", 30L
                },
                {
                        "S1", "P1", 40L
                },
                {
                        "S2", "P1", 50L
                }
        });

        IDataTableIndex index = factory.createIndex(table, "SITE", "SUBJ");

        assertEquals(3, index.getBlockCount());
        assertTotalRowsCovered(index, table, 5);
    }

    // --- Single row ---


    @Test
    void testSingleRowHashIndex()
    {
        IDataTable table = createTable("T", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "X"
                }
        });

        IDataTableIndex index = factory.createIndex(table, "A");

        assertEquals(1, index.getBlockCount());
        assertEquals(1, index.getBlock(0).getRowCount(table));
        assertEquals(0, index.getBlock(0).getRealRow(table, 0));
    }

    // --- Null values in key columns ---


    @Test
    void testNullKeyValuesHashIndex()
    {
        IDataTable table = createTable("T", new String[]
        {
                "GRP", "VAL"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        null, 1L
                },
                {
                        "A", 2L
                },
                {
                        null, 3L
                },
                {
                        "A", 4L
                }
        });

        IDataTableIndex index = factory.createIndex(table, "GRP");

        assertEquals(2, index.getBlockCount());
        assertTotalRowsCovered(index, table, 4);
    }

    // --- Factory via abstract class ---


    @Test
    void testFactoryCreation()
    {
        var created = net.cumba.datatable.index.DataTableIndexFactory.createInstance();
        assertNotNull(created);
        assertTrue(created instanceof DataTableIndexFactoryImpl);
    }

    // --- Many groups (tests DataBufferXBit enlargement) ---


    @Test
    void testManyGroupsHashIndex()
    {
        int rowCount = 500;
        Object[][] data = new Object[rowCount][];
        for (int i = 0; i < rowCount; i++)
        {
            data[i] = new Object[]
            {
                    "G" + i, (long) i
            };
        }

        IDataTable table = createTable("T", new String[]
        {
                "GRP", "VAL"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, data);

        IDataTableIndex index = factory.createIndex(table, "GRP");

        assertEquals(rowCount, index.getBlockCount());
        assertTotalRowsCovered(index, table, rowCount);
    }

    // --- Streams ---


    @Test
    void testGetBlocksStream()
    {
        IDataTable table = createTable("T", new String[]
        {
                "GRP"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "A"
                },
                {
                        "B"
                },
                {
                        "A"
                }
        });

        IDataTableIndex index = factory.createIndex(table, "GRP");
        long totalRows = index.getBlocks().mapToLong(block -> block.getRowCount(table)).sum();
        assertEquals(3, totalRows);
    }

    // --- Helper methods ---


    /**
     * Assert that all rows of the table are covered exactly once across all blocks.
     */
    private void assertTotalRowsCovered(IDataTableIndex index, IDataTable table,
            int expectedRowCount)
    {
        Set<Long> allRows = new HashSet<>();
        long totalRows = 0;
        for (int b = 0; b < index.getBlockCount(); b++)
        {
            IDataTableView block = index.getBlock(b);
            long blockSize = block.getRowCount(table);
            totalRows += blockSize;
            for (long r = 0; r < blockSize; r++)
            {
                long realRow = block.getRealRow(table, r);
                assertTrue(allRows.add(realRow),
                        "Row %d appears in multiple blocks".formatted(realRow));
            }
        }
        assertEquals(expectedRowCount, totalRows);
        assertEquals(expectedRowCount, allRows.size());
    }


    /**
     * Assert that there is exactly one block containing all the expected real row indices for a
     * given key value.
     */
    private void assertGroupContainsRows(IDataTableIndex index, IDataTable table, String keyColName,
            Object keyValue, long[] expectedRows)
    {
        int colIdx = table.getMetaData().getColumnIndex(keyColName);
        Set<Long> expected = new HashSet<>();
        for (long r : expectedRows)
        {
            expected.add(r);
        }

        for (int b = 0; b < index.getBlockCount(); b++)
        {
            IDataTableView block = index.getBlock(b);
            long firstRow = block.getRealRow(table, 0);
            Object firstVal = table.getValue(firstRow, colIdx);

            if (java.util.Objects.equals(firstVal, keyValue))
            {
                assertEquals(expected.size(), block.getRowCount(table),
                        "Block for key '%s' has wrong size".formatted(keyValue));
                Set<Long> actual = new HashSet<>();
                for (long r = 0; r < block.getRowCount(table); r++)
                {
                    actual.add(block.getRealRow(table, r));
                }
                assertEquals(expected, actual,
                        "Block for key '%s' has wrong rows".formatted(keyValue));
                return;
            }
        }
        fail("No block found for key value '%s'".formatted(keyValue));
    }
}
