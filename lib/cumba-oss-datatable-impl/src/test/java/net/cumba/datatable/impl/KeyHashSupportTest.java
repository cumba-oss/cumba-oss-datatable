package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class KeyHashSupportTest
{

    private IDataTable createTable(String[] colNames, DataValueType[] colTypes, Object[][] data)
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

        DataTableMeta meta = DataTableMeta.builder().name("T").columns(colMetas).rowCount(rowCount)
                .totalRowCount(rowCount).build();

        return new ColumnCachedDataTable(meta, columns);
    }

    // --- computeKeyHash tests ---


    @Test
    void testHashNeverReturnsZero()
    {
        // All nulls would naturally hash to 0, but the method returns 1 instead
        IDataTable table = createTable(new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        null
                }
        });

        int hash = KeyHashSupport.computeKeyHash(table, 0, new int[]
        {
                0
        });
        assertNotEquals(0, hash);
        assertEquals(1, hash);
    }


    @Test
    void testHashSingleColumn()
    {
        IDataTable table = createTable(new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "hello"
                },
                {
                        "world"
                },
                {
                        "hello"
                }
        });

        int hash0 = KeyHashSupport.computeKeyHash(table, 0, new int[]
        {
                0
        });
        int hash1 = KeyHashSupport.computeKeyHash(table, 1, new int[]
        {
                0
        });
        int hash2 = KeyHashSupport.computeKeyHash(table, 2, new int[]
        {
                0
        });

        // same value -> same hash
        assertEquals(hash0, hash2);
        // different value -> likely different hash
        assertNotEquals(hash0, hash1);
    }


    @Test
    void testHashMultipleColumns()
    {
        IDataTable table = createTable(new String[]
        {
                "A", "B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "X", 1L
                },
                {
                        "X", 2L
                },
                {
                        "X", 1L
                }
        });

        int[] colIds =
        {
                0, 1
        };
        int hash0 = KeyHashSupport.computeKeyHash(table, 0, colIds);
        int hash1 = KeyHashSupport.computeKeyHash(table, 1, colIds);
        int hash2 = KeyHashSupport.computeKeyHash(table, 2, colIds);

        assertEquals(hash0, hash2);
        assertNotEquals(hash0, hash1);
    }


    @Test
    void testHashSubsetOfColumns()
    {
        IDataTable table = createTable(new String[]
        {
                "A", "B", "C"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "X", "Y", 1L
                },
                {
                        "X", "Z", 1L
                }
        });

        // hash over column 0 only — same for both rows
        int hashCol0Row0 = KeyHashSupport.computeKeyHash(table, 0, new int[]
        {
                0
        });
        int hashCol0Row1 = KeyHashSupport.computeKeyHash(table, 1, new int[]
        {
                0
        });
        assertEquals(hashCol0Row0, hashCol0Row1);

        // hash over columns 0 and 1 — different
        int hashCol01Row0 = KeyHashSupport.computeKeyHash(table, 0, new int[]
        {
                0, 1
        });
        int hashCol01Row1 = KeyHashSupport.computeKeyHash(table, 1, new int[]
        {
                0, 1
        });
        assertNotEquals(hashCol01Row0, hashCol01Row1);
    }


    @Test
    void testHashWithLongRowIndex()
    {
        // Verify the method accepts long row indices (even though our test table is small)
        IDataTable table = createTable(new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "val"
                }
        });

        int hash = KeyHashSupport.computeKeyHash(table, 0L, new int[]
        {
                0
        });
        assertNotEquals(0, hash);
    }

    // --- KeyColumnMatcher tests ---


    @Test
    void testMatcherSameTableSameRow()
    {
        IDataTable table = createTable(new String[]
        {
                "A", "B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "X", 1L
                },
                {
                        "Y", 2L
                }
        });

        int[] colIds =
        {
                0, 1
        };
        KeyHashSupport.KeyColumnMatcher matcher = new KeyHashSupport.KeyColumnMatcher(table, colIds,
                table, colIds);

        assertTrue(matcher.matches(0, 0));
        assertTrue(matcher.matches(1, 1));
    }


    @Test
    void testMatcherSameTableDifferentRows()
    {
        IDataTable table = createTable(new String[]
        {
                "A"
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
                },
                {
                        "X"
                }
        });

        int[] colIds =
        {
                0
        };
        KeyHashSupport.KeyColumnMatcher matcher = new KeyHashSupport.KeyColumnMatcher(table, colIds,
                table, colIds);

        assertTrue(matcher.matches(0, 2));
        assertFalse(matcher.matches(0, 1));
    }


    @Test
    void testMatcherDifferentTables()
    {
        IDataTable table1 = createTable(new String[]
        {
                "A", "B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "X", 1L
                },
                {
                        "Y", 2L
                }
        });

        IDataTable table2 = createTable(new String[]
        {
                "C", "D"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "Y", 2L
                },
                {
                        "X", 1L
                }
        });

        int[] colIds1 =
        {
                0, 1
        };
        int[] colIds2 =
        {
                0, 1
        };
        KeyHashSupport.KeyColumnMatcher matcher = new KeyHashSupport.KeyColumnMatcher(table1,
                colIds1, table2, colIds2);

        // table1 row 0 ("X",1) vs table2 row 1 ("X",1) -> match
        assertTrue(matcher.matches(0, 1));
        // table1 row 0 ("X",1) vs table2 row 0 ("Y",2) -> no match
        assertFalse(matcher.matches(0, 0));
        // table1 row 1 ("Y",2) vs table2 row 0 ("Y",2) -> match
        assertTrue(matcher.matches(1, 0));
    }


    @Test
    void testMatcherDifferentColumnMapping()
    {
        // Columns in different order between the two tables
        IDataTable table1 = createTable(new String[]
        {
                "A", "B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "X", 1L
                }
        });

        IDataTable table2 = createTable(new String[]
        {
                "B", "A"
        }, new DataValueType[]
        {
                DataValueType.LONG, DataValueType.STRING
        }, new Object[][]
        {
                {
                        1L, "X"
                }
        });

        // Map table1[A,B] -> indices [0,1], table2[A,B] -> indices [1,0]
        int[] colIds1 =
        {
                0, 1
        };
        int[] colIds2 =
        {
                1, 0
        };
        KeyHashSupport.KeyColumnMatcher matcher = new KeyHashSupport.KeyColumnMatcher(table1,
                colIds1, table2, colIds2);

        assertTrue(matcher.matches(0, 0));
    }


    @Test
    void testMatcherWithNullValues()
    {
        IDataTable table = createTable(new String[]
        {
                "A"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        null
                },
                {
                        null
                },
                {
                        "X"
                }
        });

        int[] colIds =
        {
                0
        };
        KeyHashSupport.KeyColumnMatcher matcher = new KeyHashSupport.KeyColumnMatcher(table, colIds,
                table, colIds);

        // null == null
        assertTrue(matcher.matches(0, 1));
        // null != "X"
        assertFalse(matcher.matches(0, 2));
    }


    @Test
    void testMatcherSingleColumn()
    {
        IDataTable table = createTable(new String[]
        {
                "A", "B"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "X", 1L
                },
                {
                        "X", 2L
                }
        });

        // Match only on column A (index 0), ignoring B
        int[] colIds =
        {
                0
        };
        KeyHashSupport.KeyColumnMatcher matcher = new KeyHashSupport.KeyColumnMatcher(table, colIds,
                table, colIds);

        // Both rows have "X" in column A
        assertTrue(matcher.matches(0, 1));
    }
}
