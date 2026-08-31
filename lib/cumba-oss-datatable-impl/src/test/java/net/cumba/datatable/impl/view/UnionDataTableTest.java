package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UnionDataTable}: offset arithmetic at member boundaries, first-seen column union
 * with missing-as-missing, the constructor guards (type clash, unknown row count, int ceiling,
 * case-sensitivity mismatch), member-local {@code getRealRowIndex}, and the {@code UNION_OF}
 * metadata.
 */
class UnionDataTableTest
{

    private static IDataTable table(String name, String[] colNames, DataValueType[] colTypes,
            Object[][] data)
    {
        return table(name, colNames, colTypes, data, false, null);
    }


    private static IDataTable table(String name, String[] colNames, DataValueType[] colTypes,
            Object[][] data, boolean caseSensitive, Object[] metaKv)
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
        for (Object[] row : data)
        {
            for (int c = 0; c < colCount; c++)
            {
                columns[c].addElement(row[c]);
            }
        }
        for (CachedDataTableColumn col : columns)
        {
            col.complete();
        }
        DataTableMeta.DataTableMetaBuilder mb = DataTableMeta.builder().name(name)//
                .columns(colMetas).rowCount(rowCount).totalRowCount(rowCount)//
                .columnNameCaseSensitive(caseSensitive);
        if (metaKv != null)
        {
            mb.addMetaData((String) metaKv[0], metaKv[1]);
        }
        return new ColumnCachedDataTable(mb.build(), columns);
    }


    private static IDataTable lbch()
    {
        return table("lbch", new String[]
        {
                "USUBJID", "LBSEQ", "LBORRES", "LBSPEC"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG, DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "U1", 1L, "res-ch-1", "SERUM"
                },
                {
                        "U1", 2L, "res-ch-2", "SERUM"
                }
        });
    }


    private static IDataTable lbhe()
    {
        // No LBSPEC column: reads of LBSPEC on lbhe rows must be missing, not an error.
        return table("lbhe", new String[]
        {
                "USUBJID", "LBSEQ", "LBORRES"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "U2", 9L, "res-he-9"
                }
        });
    }


    private static IDataTable lbur()
    {
        return table("lbur", new String[]
        {
                "USUBJID", "LBSEQ", "LBORRES", "LBMETHOD"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG, DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "U3", 4L, "res-ur-4", "DIPSTICK"
                },
                {
                        "U3", 5L, "res-ur-5", "DIPSTICK"
                }
        });
    }

    // ------------------------------------------------------------------ row/offset arithmetic


    @Test
    void twoMembers_rowsStackInMemberOrder()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch(), lbhe());
        assertEquals(3, union.getRowCount());
        int lborres = union.getMetaData().getColumnIndex("LBORRES");
        assertEquals("res-ch-1", union.getDataValue(0, lborres).getValueAsString());
        assertEquals("res-ch-2", union.getDataValue(1, lborres).getValueAsString());
        assertEquals("res-he-9", union.getDataValue(2, lborres).getValueAsString());
    }


    @Test
    void threeMembers_boundaryRowsResolveToTheRightMember()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch(), lbhe(), lbur());
        assertEquals(5, union.getRowCount());
        int usubjid = union.getMetaData().getColumnIndex("USUBJID");
        // First and last row of each member.
        assertEquals("U1", union.getDataValue(0, usubjid).getValueAsString());
        assertEquals("U1", union.getDataValue(1, usubjid).getValueAsString());
        assertEquals("U2", union.getDataValue(2, usubjid).getValueAsString());
        assertEquals("U3", union.getDataValue(3, usubjid).getValueAsString());
        assertEquals("U3", union.getDataValue(4, usubjid).getValueAsString());
    }


    @Test
    void memberOf_boundaries()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch(), lbhe(), lbur());
        assertEquals(0, union.memberOf(0));
        assertEquals(0, union.memberOf(1));
        assertEquals(1, union.memberOf(2));
        assertEquals(2, union.memberOf(3));
        assertEquals(2, union.memberOf(4));
    }


    @Test
    void zeroRowMember_isSkippedByTheOffsetSearch()
    {
        IDataTable empty = table("lbxx", new String[]
        {
                "USUBJID", "LBSEQ", "LBORRES"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG, DataValueType.STRING
        }, new Object[][] {});
        UnionDataTable union = new UnionDataTable("LB", lbch(), empty, lbhe());
        assertEquals(3, union.getRowCount());
        assertEquals(0, union.memberOf(1));
        // Row 2 sits on the empty member's (zero-width) offset — it belongs to lbhe.
        assertEquals(2, union.memberOf(2));
        int lborres = union.getMetaData().getColumnIndex("LBORRES");
        assertEquals("res-he-9", union.getDataValue(2, lborres).getValueAsString());
    }


    @Test
    void singleMember_returnsItsOwnValues()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch());
        assertEquals(2, union.getRowCount());
        int lborres = union.getMetaData().getColumnIndex("LBORRES");
        assertEquals("res-ch-2", union.getDataValue(1, lborres).getValueAsString());
        assertEquals(0, union.memberOf(1));
    }


    @Test
    void getRealRowIndex_isMemberLocal()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch(), lbhe());
        assertEquals(0, union.getRealRowIndex(0));
        assertEquals(1, union.getRealRowIndex(1));
        // lbhe's only row is its row 0 — member-local, not union-local (see the class Javadoc).
        assertEquals(0, union.getRealRowIndex(2));
    }

    // ------------------------------------------------------------------------- column union


    @Test
    void columnUnion_isFirstSeenOrder()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch(), lbhe(), lbur());
        DataTableMeta meta = union.getMetaData();
        assertEquals(5, meta.getColumnCount());
        assertEquals("USUBJID", meta.getColumn(0).getName());
        assertEquals("LBSEQ", meta.getColumn(1).getName());
        assertEquals("LBORRES", meta.getColumn(2).getName());
        assertEquals("LBSPEC", meta.getColumn(3).getName());
        assertEquals("LBMETHOD", meta.getColumn(4).getName());
    }


    @Test
    void columnMissingFromAMember_readsAsMissingNeverNullDataValue()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch(), lbhe());
        int lbspec = union.getMetaData().getColumnIndex("LBSPEC");
        // lbch rows carry the value.
        assertEquals("SERUM", union.getDataValue(0, lbspec).getValueAsString());
        // lbhe lacks LBSPEC: getDataValue is a MISSING IDataValue (never null), getValue is null.
        IDataValue dv = union.getDataValue(2, lbspec);
        assertTrue(dv.isMissingOrInvalid());
        assertNull(union.getValue(2, lbspec));
    }


    @Test
    void typeClash_failsTheUnion_namingColumnAndMembers()
    {
        IDataTable numeric = table("lbch", new String[]
        {
                "USUBJID", "LBSTRESN"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.LONG
        }, new Object[][]
        {
                {
                        "U1", 1L
                }
        });
        IDataTable text = table("lbhe", new String[]
        {
                "USUBJID", "LBSTRESN"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.STRING
        }, new Object[][]
        {
                {
                        "U2", "high"
                }
        });
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UnionDataTable("LB", numeric, text));
        assertTrue(ex.getMessage().contains("LBSTRESN"), ex.getMessage());
        assertTrue(ex.getMessage().contains("lbch"), ex.getMessage());
        assertTrue(ex.getMessage().contains("lbhe"), ex.getMessage());
        assertTrue(ex.getMessage().contains("LONG"), ex.getMessage());
        assertTrue(ex.getMessage().contains("STRING"), ex.getMessage());
    }

    // ------------------------------------------------------------------------------- guards


    @Test
    void outOfRangeRowAndColumn_throw()
    {
        UnionDataTable union = new UnionDataTable("LB", lbch(), lbhe());
        assertThrows(IndexOutOfBoundsException.class, () -> union.getDataValue(3, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> union.getDataValue(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> union.getDataValue(0, 99));
        assertThrows(IndexOutOfBoundsException.class, () -> union.getValue(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> union.memberOf(3));
        assertThrows(IndexOutOfBoundsException.class, () -> union.getRealRowIndex(-1));
    }


    @Test
    void emptyOrNullMemberArray_rejected()
    {
        assertThrows(IllegalArgumentException.class, () -> new UnionDataTable("LB"));
        assertThrows(IllegalArgumentException.class,
                () -> new UnionDataTable("LB", (IDataTable[]) null));
        assertThrows(IllegalArgumentException.class, () -> new UnionDataTable("LB", lbch(), null));
    }


    @Test
    void unknownRowCountMember_rejected()
    {
        IDataTable unknown = new RowCountOverride(lbch(), -1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UnionDataTable("LB", unknown, lbhe()));
        assertTrue(ex.getMessage().contains("unknown row count"), ex.getMessage());
    }


    @Test
    void rowCountSumPastIntRange_rejected()
    {
        IDataTable big1 = new RowCountOverride(lbch(), Integer.MAX_VALUE);
        IDataTable big2 = new RowCountOverride(lbhe(), Integer.MAX_VALUE);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UnionDataTable("LB", big1, big2));
        assertTrue(ex.getMessage().contains("int range"), ex.getMessage());
    }


    @Test
    void caseSensitivityMismatch_rejected()
    {
        IDataTable sensitive = table("lbhe", new String[]
        {
                "USUBJID"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "U2"
                }
        }, true, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new UnionDataTable("LB", lbch(), sensitive));
        assertTrue(ex.getMessage().contains("case-sensitivity"), ex.getMessage());
    }

    // ------------------------------------------------------------------------------ metadata


    @Test
    void metaData_namesTheDomainAndListsTheMembers_withoutInheritingMemberMeta()
    {
        IDataTable first = table("lbch", new String[]
        {
                "USUBJID"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[][]
        {
                {
                        "U1"
                }
        }, false, new Object[]
        {
                UnionDataTable.META_UNION_OF, "inherited-should-not-win"
        });
        UnionDataTable union = new UnionDataTable("LB", first, lbhe());
        DataTableMeta meta = union.getMetaData();
        assertEquals("LB", meta.getName());
        assertEquals("lbch,lbhe", meta.getMetaData(UnionDataTable.META_UNION_OF));
        assertNull(meta.getTableURI());
    }

    /** Delegates everything to a backing table but reports a fixed row count. */
    private static final class RowCountOverride implements IDataTable
    {

        private final IDataTable backing;

        private final long rowCount;

        RowCountOverride(IDataTable backing, long rowCount)
        {
            this.backing = backing;
            this.rowCount = rowCount;
        }


        @Override
        public DataTableMeta getMetaData()
        {
            return backing.getMetaData();
        }


        @Override
        public long getRowCount()
        {
            return rowCount;
        }


        @Override
        public Object getValue(long aRow, int aColumn)
        {
            return backing.getValue(aRow, aColumn);
        }

    }

}
