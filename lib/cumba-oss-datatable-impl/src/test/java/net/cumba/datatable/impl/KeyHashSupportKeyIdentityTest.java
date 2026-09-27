package net.cumba.datatable.impl;

// OSS-IDENTITY datatable-index-identity: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.view.UnionDataTable;
import net.cumba.datatable.index.DataTableIndexFactory;
import net.cumba.datatable.index.IDataTableIndex;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.datatable.view.IDataTableView;
import org.junit.jupiter.api.Test;

/**
 * The datatable's key identity ({@code PLAN-grouping-key-identity}, design D-A): the key hash folds
 * a {@code -0.0} cell onto {@code 0.0}, {@link KeyHashSupport#keyEquals} makes the two zeros one
 * key and changes nothing else, and the hash index — the partition the conformance engine groups on
 * — therefore forms one block for the two zeros while keeping the noise pair and the {@code LONG}
 * pair beyond 2^53 apart.
 *
 * <p>
 * Byte-identical in both datatable twins and run against each twin's own
 * {@code DataTableIndexFactoryImpl}: the block assembly differs by twin, the identity must not.
 * </p>
 */
class KeyHashSupportKeyIdentityTest
{

    private static final long TWO_53 = 9_007_199_254_740_992L;

    /** {@code "polygenelubricants".hashCode()} is {@code Integer.MIN_VALUE}. */
    private static final String MIN_HASH_TEXT = "polygenelubricants";

    private static IDataTable table(DataValueType[] aTypes, Object[]... aRows)
    {
        String[] names = new String[aTypes.length];
        for (int c = 0; c < names.length; c++)
        {
            names[c] = "K" + c;
        }
        return named("T", names, aTypes, aRows);
    }


    private static IDataTable named(String aName, String[] aNames, DataValueType[] aTypes,
            Object[]... aRows)
    {
        DataTableColumnMeta[] metas = new DataTableColumnMeta[aTypes.length];
        CachedDataTableColumn[] columns = new CachedDataTableColumn[aTypes.length];
        for (int c = 0; c < aTypes.length; c++)
        {
            metas[c] = DataTableColumnMeta.builder().index(c).name(aNames[c]).type(aTypes[c])
                    .build();
            columns[c] = new CachedDataTableColumn(c, aTypes[c]);
        }
        for (Object[] row : aRows)
        {
            for (int c = 0; c < aTypes.length; c++)
            {
                columns[c].addElement(row[c]);
            }
        }
        for (CachedDataTableColumn column : columns)
        {
            column.complete();
        }
        DataTableMeta meta = DataTableMeta.builder().name(aName).columns(metas)
                .rowCount(aRows.length).totalRowCount(aRows.length).build();
        return new ColumnCachedDataTable(meta, columns);
    }


    private static IDataTable column(DataValueType aType, Object... aValues)
    {
        Object[][] rows = new Object[aValues.length][];
        for (int i = 0; i < aValues.length; i++)
        {
            rows[i] = new Object[]
            {
                    aValues[i]
            };
        }
        return table(new DataValueType[]
        {
                aType
        }, rows);
    }


    /** The hash index's blocks as lists of real rows, in block order. */
    private static List<List<Long>> blocks(IDataTable aTable, String... aColumns)
    {
        IDataTableIndex index = DataTableIndexFactory.createInstance().createIndex(aTable,
                aColumns);
        List<List<Long>> out = new ArrayList<>();
        for (long b = 0; b < index.getBlockCount(); b++)
        {
            IDataTableView block = index.getBlock(b);
            List<Long> rows = new ArrayList<>();
            for (long i = 0; i < block.getRowCount(aTable); i++)
            {
                rows.add(block.getRealRow(aTable, i));
            }
            out.add(rows);
        }
        return out;
    }


    private static int hash(IDataTable aTable, long aRow)
    {
        int[] all = new int[aTable.getMetaData().getColumnCount()];
        for (int c = 0; c < all.length; c++)
        {
            all[c] = c;
        }
        return KeyHashSupport.computeKeyHash(aTable, aRow, all);
    }


    @Test
    void theKeyHashFoldsANegativeZeroOntoZero()
    {
        IDataTable t = column(DataValueType.DOUBLE, -0.0, 0.0, 1.5);
        // the cell hashes differ -- the sign bit is Integer.MIN_VALUE -- so the fold is what
        // makes the key hashes agree
        assertEquals(Integer.MIN_VALUE, t.hashCodeAt(0, 0));
        assertEquals(0, t.hashCodeAt(1, 0));
        assertEquals(hash(t, 1), hash(t, 0));
        assertNotEquals(hash(t, 1), hash(t, 2));

        IDataTable two = table(new DataValueType[]
        {
                DataValueType.STRING, DataValueType.DOUBLE
        }, new Object[]
        {
                "S1", -0.0
        }, new Object[]
        {
                "S1", 0.0
        });
        assertEquals(hash(two, 1), hash(two, 0));
        assertEquals(31 * two.hashCodeAt(1, 0), hash(two, 0));
    }


    @Test
    void anyOtherCellHashingLikeANegativeZeroOnlySharesABucket()
    {
        assertEquals(Integer.MIN_VALUE, MIN_HASH_TEXT.hashCode());
        IDataTable t = column(DataValueType.STRING, MIN_HASH_TEXT, "", MIN_HASH_TEXT);
        // folded to 0, i.e. the never-zero 1 -- the same key hash as the empty string ...
        assertEquals(hash(t, 1), hash(t, 0));
        // ... and the equality keeps them two keys
        assertEquals(List.of(List.of(0L, 2L), List.of(1L)), blocks(t, "K0"));
    }


    @Test
    void keyEqualsIsExactExceptForTheZeros()
    {
        assertTrue(KeyHashSupport.keyEquals(-0.0, 0.0));
        assertTrue(KeyHashSupport.keyEquals(0.0, -0.0));
        assertTrue(KeyHashSupport.keyEquals(-0.0, -0.0));
        assertTrue(KeyHashSupport.keyEquals(-0.0f, 0.0f));
        assertTrue(KeyHashSupport.keyEquals(5.0, 5.0));
        assertTrue(KeyHashSupport.keyEquals("A", "A"));
        assertTrue(KeyHashSupport.keyEquals(null, null));
        assertTrue(KeyHashSupport.keyEquals(MissingValue.MIS_A, MissingValue.MIS_A));
        // a NaN keeps Double.equals' answer: equal to the same bits, nothing else
        assertTrue(KeyHashSupport.keyEquals(Double.NaN, Double.NaN));

        assertFalse(KeyHashSupport.keyEquals(4.9999999999994, 5.0));
        assertFalse(KeyHashSupport.keyEquals(0.0, Double.MIN_VALUE));
        assertFalse(KeyHashSupport.keyEquals(-0.0, -Double.MIN_VALUE));
        assertFalse(KeyHashSupport.keyEquals(TWO_53, TWO_53 + 1));
        // one column is one type: a Long never equals a Double, a Float never a Double, zeros
        // included
        assertFalse(KeyHashSupport.keyEquals(0L, 0.0));
        assertFalse(KeyHashSupport.keyEquals(5L, 5.0));
        assertFalse(KeyHashSupport.keyEquals(0.0f, 0.0));
        assertFalse(KeyHashSupport.keyEquals(-0.0, 0.0f));
        assertFalse(KeyHashSupport.keyEquals(MissingValue.MIS, MissingValue.MIS_A));
        assertFalse(KeyHashSupport.keyEquals(MissingValue.MIS, ""));
        assertFalse(KeyHashSupport.keyEquals(null, ""));
        assertFalse(KeyHashSupport.keyEquals(0.0, null));
        assertFalse(KeyHashSupport.keyEquals("0", 0.0));
    }


    @Test
    void theIndexFormsOneBlockForTheTwoZeros()
    {
        assertEquals(List.of(List.of(0L, 1L, 2L)),
                blocks(column(DataValueType.DOUBLE, -0.0, 0.0, 0.0), "K0"));
        assertEquals(List.of(List.of(0L, 1L), List.of(2L)), blocks(table(new DataValueType[]
        {
                DataValueType.STRING, DataValueType.DOUBLE
        }, new Object[]
        {
                "S1", 0.0
        }, new Object[]
        {
                "S1", -0.0
        }, new Object[]
        {
                "S2", 0.0
        }), "K0", "K1"));
    }


    @Test
    void theIndexKeepsTheNoisePairAndTheLongPairApart()
    {
        assertEquals(List.of(List.of(0L, 1L), List.of(2L)),
                blocks(column(DataValueType.DOUBLE, 4.9999999999994, 4.9999999999994, 5.0), "K0"));
        assertEquals(List.of(List.of(0L), List.of(1L), List.of(2L)),
                blocks(column(DataValueType.LONG, TWO_53, TWO_53 + 1, TWO_53 + 2), "K0"));
    }


    @Test
    void blocksAreInFirstOccurrenceOrderAndRowsAscendInsideABlock()
    {
        assertEquals(List.of(List.of(0L, 2L), List.of(1L, 3L), List.of(4L)),
                blocks(column(DataValueType.DOUBLE, 5.0, -0.0, 5.0, 0.0, 1.5), "K0"));
    }


    /**
     * A split-domain union: a member that lacks the column reads as {@code MIS} on BOTH channels
     * (register {@code D46}), so its rows group with a member's stored {@code MIS} — exactly as the
     * key identity, built from {@code getDataValue}, groups them. Review round 1 of
     * {@code PLAN-grouping-key-identity} (M1) measured three blocks here: the raw read answered
     * {@code null}.
     */
    @Test
    void aUnionMemberLackingTheColumnGroupsWithAStoredMissing()
    {
        IDataTable lbc1 = named("lbc1", new String[]
        {
                "USUBJID", "VISITNUM"
        }, new DataValueType[]
        {
                DataValueType.STRING, DataValueType.DOUBLE
        }, new Object[]
        {
                "S1", MissingValue.MIS
        }, new Object[]
        {
                "S1", 1.0
        });
        IDataTable lbc2 = named("lbc2", new String[]
        {
                "USUBJID"
        }, new DataValueType[]
        {
                DataValueType.STRING
        }, new Object[]
        {
                "S1"
        });
        UnionDataTable union = new UnionDataTable("LB", lbc1, lbc2);
        int visitnum = union.getMetaData().getColumnIndex("VISITNUM");
        assertEquals(MissingValue.MIS, union.getValue(2, visitnum));
        assertEquals(union.getDataValue(2, visitnum).getValue(), union.getValue(2, visitnum));
        assertEquals(List.of(List.of(0L, 2L), List.of(1L)), blocks(union, "VISITNUM"));
        assertEquals(List.of(List.of(0L, 2L), List.of(1L)), blocks(union, "USUBJID", "VISITNUM"));
    }


    @Test
    void theMatchersCompareThroughKeyEquals()
    {
        IDataTable left = table(new DataValueType[]
        {
                DataValueType.STRING, DataValueType.DOUBLE
        }, new Object[]
        {
                "S1", -0.0
        }, new Object[]
        {
                "S1", 5.0
        });
        IDataTable right = table(new DataValueType[]
        {
                DataValueType.STRING, DataValueType.DOUBLE
        }, new Object[]
        {
                "S1", 0.0
        }, new Object[]
        {
                "S1", 4.9999999999994
        });
        int[] cols =
        {
                0, 1
        };
        KeyHashSupport.KeyColumnMatcher cross = new KeyHashSupport.KeyColumnMatcher(left, cols,
                right, cols);
        assertTrue(cross.matches(0, 0));
        assertFalse(cross.matches(1, 1));
        assertFalse(cross.matches(0, 1));

        int[] reps =
        {
                1, 0
        };
        KeyHashSupport.RepRowMatcher rep = new KeyHashSupport.RepRowMatcher(left, cols, new int[]
        {
                0
        });
        rep.setGroupRepRow(reps);
        rep.setCurrentRow(0);
        assertFalse(rep.matches(0), "group 0's representative is row 1 (5.0), row 0 is -0.0");
        assertTrue(rep.matches(1));
        rep.setCurrentRow(1);
        assertTrue(rep.matches(0));
    }
}
