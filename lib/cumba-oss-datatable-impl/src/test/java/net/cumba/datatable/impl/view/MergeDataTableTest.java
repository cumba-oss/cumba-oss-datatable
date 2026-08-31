package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValueNumber;
import org.junit.jupiter.api.Test;

class MergeDataTableTest
{

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


    @Test
    void testMergeTwoTables()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "COL_A", "COL_B"
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
                        "C", 3L
                }
        });

        IDataTable table2 = createTable("T2", new String[]
        {
                "COL_C", "COL_D"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.DOUBLE
        }, new Object[][]
        {
                {
                        "X", 1.1
                },
                {
                        "Y", 2.2
                },
                {
                        "Z", 3.3
                }
        });

        MergeDataTable merged = new MergeDataTable(table1, table2);

        assertEquals(3, merged.getRowCount());
        assertEquals(4, merged.getColumnCount());

        // Check column names
        assertEquals("COL_A", merged.getMetaData().getColumn(0).getName());
        assertEquals("COL_B", merged.getMetaData().getColumn(1).getName());
        assertEquals("COL_C", merged.getMetaData().getColumn(2).getName());
        assertEquals("COL_D", merged.getMetaData().getColumn(3).getName());

        // Check values
        assertEquals("A", merged.getValue(0, 0));
        assertEquals(1L, merged.getValue(0, 1));
        assertEquals("X", merged.getValue(0, 2));
        assertEquals(1.1, merged.getValue(0, 3));

        assertEquals("C", merged.getValue(2, 0));
        assertEquals(3L, merged.getValue(2, 1));
        assertEquals("Z", merged.getValue(2, 2));
        assertEquals(3.3, merged.getValue(2, 3));
    }


    @Test
    void testMergeThreeTables()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "1"
                },
                {
                        "2"
                }
        });

        IDataTable table2 = createTable("T2", new String[]
        {
                "B"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "3"
                },
                {
                        "4"
                }
        });

        IDataTable table3 = createTable("T3", new String[]
        {
                "C"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "5"
                },
                {
                        "6"
                }
        });

        MergeDataTable merged = new MergeDataTable(table1, table2, table3);

        assertEquals(2, merged.getRowCount());
        assertEquals(3, merged.getColumnCount());

        assertEquals("1", merged.getValue(0, 0));
        assertEquals("3", merged.getValue(0, 1));
        assertEquals("5", merged.getValue(0, 2));
    }


    @Test
    void testMergeSingleTable()
    {
        IDataTable table = createTable("T1", new String[]
        {
                "COL_A"
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
                }
        });

        MergeDataTable merged = new MergeDataTable(table);

        assertEquals(2, merged.getRowCount());
        assertEquals(1, merged.getColumnCount());
        assertEquals("A", merged.getValue(0, 0));
    }


    @Test
    void testMergeWithDifferentRowCountThrowsException()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "COL_A"
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
                }
        });

        IDataTable table2 = createTable("T2", new String[]
        {
                "COL_B"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "X"
                },
                {
                        "Y"
                }
        }); // Only 2 rows!

        assertThrows(IllegalArgumentException.class, () -> new MergeDataTable(table1, table2));
    }


    @Test
    void testMergeWithDuplicateColumnNameThrowsException()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "COL_A", "COL_B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "A", "B"
                }
        });

        IDataTable table2 = createTable("T2", new String[]
        {
                "COL_A"
        }, // Duplicate column name!
                new DataValueType[]
                {
                        DataValueType.STRING
                }, new Object[][]
                {
                        {
                                "X"
                        }
                });

        assertThrows(IllegalArgumentException.class, () -> new MergeDataTable(table1, table2));
    }


    @Test
    void testMergeWithDuplicateColumnNameCaseInsensitive()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "COL_A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "A"
                }
        });

        IDataTable table2 = createTable("T2", new String[]
        {
                "col_a"
        }, // Same name, different case
                new DataValueType[]
                {
                        DataValueType.STRING
                }, new Object[][]
                {
                        {
                                "X"
                        }
                });

        // Should throw because column names are case-insensitive by default
        assertThrows(IllegalArgumentException.class, () -> new MergeDataTable(table1, table2));
    }


    @Test
    void testMergeWithNullTableThrowsException()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "COL_A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "A"
                }
        });

        assertThrows(IllegalArgumentException.class, () -> new MergeDataTable(table1, null));
    }


    @Test
    void testMergeWithNullFirstTableThrowsIllegalArgument()
    {
        IDataTable table2 = createTable("T2", new String[]
        {
                "COL_A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "A"
                }
        });

        // First element is null — should throw IllegalArgumentException, not NPE
        assertThrows(IllegalArgumentException.class, () -> new MergeDataTable(new IDataTable[]
        {
                null, table2
        }));
    }


    @Test
    void testMergeWithEmptyArrayThrowsException()
    {
        assertThrows(IllegalArgumentException.class, MergeDataTable::new);
    }


    @Test
    void testMergeWithNullArrayThrowsException()
    {
        assertThrows(IllegalArgumentException.class, () -> new MergeDataTable((IDataTable[]) null));
    }


    @Test
    void testGetDataValue()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "COL_A"
        }, new DataValueType[]
        {
                DataValueType.LONG
        }, new Object[][]
        {
                {
                        10L
                },
                {
                        20L
                }
        });

        IDataTable table2 = createTable("T2", new String[]
        {
                "COL_B"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "X"
                },
                {
                        "Y"
                }
        });

        MergeDataTable merged = new MergeDataTable(table1, table2);

        assertEquals(10L, ((IDataValueNumber) merged.getDataValue(0, 0)).getValueAsLong());
        assertEquals("X", merged.getDataValue(0, 1).getValueAsString());
    }


    @Test
    void testGetRealRowIndex()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "1"
                },
                {
                        "2"
                },
                {
                        "3"
                }
        });

        MergeDataTable merged = new MergeDataTable(table1);

        assertEquals(0, merged.getRealRowIndex(0));
        assertEquals(1, merged.getRealRowIndex(1));
        assertEquals(2, merged.getRealRowIndex(2));
    }


    @Test
    void testColumnIndexOutOfBounds()
    {
        IDataTable table = createTable("T1", new String[]
        {
                "A", "B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "1", "2"
                }
        });

        MergeDataTable merged = new MergeDataTable(table);

        assertThrows(IndexOutOfBoundsException.class, () -> merged.getValue(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> merged.getValue(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> merged.getDataValue(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> merged.getDataValue(0, 2));
    }


    @Test
    void testMergePreservesColumnIndices()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "A", "B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "1", "2"
                }
        });

        IDataTable table2 = createTable("T2", new String[]
        {
                "C"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "3"
                }
        });

        MergeDataTable merged = new MergeDataTable(table1, table2);

        // Column indices in merged table should be sequential
        assertEquals(0, merged.getMetaData().getColumn(0).getIndex());
        assertEquals(1, merged.getMetaData().getColumn(1).getIndex());
        assertEquals(2, merged.getMetaData().getColumn(2).getIndex());
    }


    @Test
    void testMergeWithEmptyTables()
    {
        IDataTable table1 = createTable("T1", new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][] {});

        IDataTable table2 = createTable("T2", new String[]
        {
                "B"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][] {});

        MergeDataTable merged = new MergeDataTable(table1, table2);

        assertEquals(0, merged.getRowCount());
        assertEquals(2, merged.getColumnCount());
    }
}
