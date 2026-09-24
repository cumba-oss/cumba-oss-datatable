package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Pins the read-only member API of {@link UnionDataTable} ({@code PLAN-identity-safe-join-caches}
 * D8): {@code memberOf}, {@code memberCount}, {@code member}, {@code memberRowStart} and
 * {@code memberColumnOf}, and the invariant that ties them to {@code getDataValue}.
 *
 * <p>
 * The CORE engine that reads a split domain's members directly is byte-identical across the
 * internal and OSS twins, so this API has one contract in both, and this test is kept identical in
 * both repositories except for the pass-through case, which only the internal class has. Unions are
 * built only through the split-domain constructor {@code UnionDataTable(String, IDataTable...)},
 * the one both twins share.
 * </p>
 */
class UnionDataTableMemberApiTest
{

    private static final String[] UNION_COLUMNS =
    {
            "USUBJID", "SEQ", "RES", "SPEC"
    };

    private static IDataTable table(String aName, String[] aColNames, DataValueType[] aColTypes,
            @Nullable Object[][] aData)
    {
        int colCount = aColNames.length;
        CachedDataTableColumn[] columns = new CachedDataTableColumn[colCount];
        DataTableColumnMeta[] colMetas = new DataTableColumnMeta[colCount];
        for (int c = 0; c < colCount; c++)
        {
            columns[c] = new CachedDataTableColumn(c, aColTypes[c]);
            colMetas[c] = DataTableColumnMeta.builder().index(c).name(aColNames[c])
                    .type(aColTypes[c]).build();
        }
        for (Object[] row : aData)
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
        DataTableMeta meta = DataTableMeta.builder().name(aName).columns(colMetas)
                .rowCount(aData.length).totalRowCount(aData.length).build();
        return new ColumnCachedDataTable(meta, columns);
    }

    private static final DataValueType S = DataValueType.STRING;

    private static final DataValueType L = DataValueType.LONG;

    /** Zero rows, leading: shares its start offset with the first member that has rows. */
    private final IDataTable zLead = table("zlead", new String[]
    {
            "USUBJID"
    }, new DataValueType[]
    {
            S
    }, new Object[0][]);

    /** Three rows, including a present-but-missing cell that must be delegated, not replaced. */
    private final IDataTable a = table("a", new String[]
    {
            "USUBJID", "SEQ", "RES"
    }, new DataValueType[]
    {
            S, L, S
    }, new Object[][]
    {
            {
                    "U1", 1L, "a-1"
            },
            {
                    "U1", 2L, null
            },
            {
                    "U2", 3L, "a-3"
            }
    });

    /** Zero rows, in the middle, columns in a different order from the union. */
    private final IDataTable zMid = table("zmid", new String[]
    {
            "SEQ", "USUBJID"
    }, new DataValueType[]
    {
            L, S
    }, new Object[0][]);

    /** One row; lacks SEQ; columns in a different order from the union. */
    private final IDataTable b = table("b", new String[]
    {
            "RES", "SPEC", "USUBJID"
    }, new DataValueType[]
    {
            S, S, S
    }, new Object[][]
    {
            {
                    "b-1", "SERUM", "U3"
            }
    });

    /** Two rows; lacks RES and SPEC; columns reversed against the union. */
    private final IDataTable c = table("c", new String[]
    {
            "SEQ", "USUBJID"
    }, new DataValueType[]
    {
            L, S
    }, new Object[][]
    {
            {
                    10L, "U4"
            },
            {
                    11L, "U5"
            }
    });

    /** Zero rows, trailing: its start offset equals the union's row count. */
    private final IDataTable zTail = table("ztail", new String[]
    {
            "SPEC"
    }, new DataValueType[]
    {
            S
    }, new Object[0][]);

    private UnionDataTable union()
    {
        return new UnionDataTable("LB", zLead, a, zMid, b, c, zTail);
    }


    @Test
    void unionColumnsAreFirstSeenOrdered()
    {
        UnionDataTable u = union();
        assertEquals(UNION_COLUMNS.length, u.getColumnCount());
        for (int col = 0; col < UNION_COLUMNS.length; col++)
        {
            assertEquals(UNION_COLUMNS[col], u.getMetaData().getColumn(col).getName());
        }
        assertEquals(6L, u.getRowCount());
    }


    @Test
    void memberCountAndMemberReturnTheConstructorArguments()
    {
        UnionDataTable u = union();
        IDataTable[] expected =
        {
                zLead, a, zMid, b, c, zTail
        };
        assertEquals(expected.length, u.memberCount());
        for (int m = 0; m < expected.length; m++)
        {
            assertSame(expected[m], u.member(m), "member " + m);
        }
    }


    @Test
    void memberRowStartIsTheRunningRowOffset()
    {
        UnionDataTable u = union();
        long[] expected =
        {
                0L, 0L, 3L, 3L, 4L, 6L
        };
        long[] actual = new long[u.memberCount()];
        for (int m = 0; m < actual.length; m++)
        {
            actual[m] = u.memberRowStart(m);
        }
        assertArrayEquals(expected, actual);
    }


    @Test
    void memberOfSkipsZeroRowMembersAtEveryBoundary()
    {
        UnionDataTable u = union();
        // Row 0 is the start of both zLead (0 rows) and a; row 3 of both zMid (0 rows) and b.
        int[] expected =
        {
                1, 1, 1, 3, 4, 4
        };
        int[] actual = new int[(int) u.getRowCount()];
        for (int r = 0; r < actual.length; r++)
        {
            actual[r] = u.memberOf(r);
        }
        assertArrayEquals(expected, actual);

        // First and last row of every member that has rows.
        assertEquals(1, u.memberOf(0));
        assertEquals(1, u.memberOf(2));
        assertEquals(3, u.memberOf(3));
        assertEquals(4, u.memberOf(4));
        assertEquals(4, u.memberOf(5));
    }


    @Test
    void memberColumnOfMapsByNameAndAnswersMinusOneForALackingMember()
    {
        UnionDataTable u = union();
        // Union columns: USUBJID, SEQ, RES, SPEC.
        int[][] expected =
        {
                {
                        0, -1, -1, -1
                }, // zLead: USUBJID
                {
                        0, 1, 2, -1
                }, // a: USUBJID, SEQ, RES
                {
                        1, 0, -1, -1
                }, // zMid: SEQ, USUBJID
                {
                        2, -1, 0, 1
                }, // b: RES, SPEC, USUBJID
                {
                        1, 0, -1, -1
                }, // c: SEQ, USUBJID
                {
                        -1, -1, -1, 0
                }, // zTail: SPEC
        };
        for (int m = 0; m < expected.length; m++)
        {
            for (int col = 0; col < UNION_COLUMNS.length; col++)
            {
                assertEquals(expected[m][col], u.memberColumnOf(m, col),
                        "member " + m + ", column " + UNION_COLUMNS[col]);
            }
        }
    }


    @Test
    void invariantHoldsForEveryRowAndColumn()
    {
        // 24 cells: a lacks SPEC (3 rows), b lacks SEQ (1 row), c lacks RES and SPEC (2 rows).
        assertEquals(8, assertInvariant(union()));
    }


    @Test
    void outOfRangeRowsThrowTheSameExceptionAsGetDataValue()
    {
        UnionDataTable u = union();
        for (long row : new long[]
        {
                -1L, 6L, Long.MAX_VALUE, Long.MIN_VALUE
        })
        {
            IndexOutOfBoundsException viaMemberOf = assertThrowsExactly(
                    IndexOutOfBoundsException.class, () -> u.memberOf(row));
            IndexOutOfBoundsException viaGet = assertThrowsExactly(IndexOutOfBoundsException.class,
                    () -> u.getDataValue(row, 0));
            assertEquals(viaGet.getMessage(), viaMemberOf.getMessage(), "row " + row);
        }
    }


    @Test
    void outOfRangeColumnsThrowTheSameExceptionAsGetDataValue()
    {
        UnionDataTable u = union();
        for (int col : new int[]
        {
                -1, UNION_COLUMNS.length, Integer.MAX_VALUE
        })
        {
            IndexOutOfBoundsException viaMemberColumnOf = assertThrowsExactly(
                    IndexOutOfBoundsException.class, () -> u.memberColumnOf(1, col));
            IndexOutOfBoundsException viaGet = assertThrowsExactly(IndexOutOfBoundsException.class,
                    () -> u.getDataValue(0, col));
            assertEquals(viaGet.getMessage(), viaMemberColumnOf.getMessage(), "column " + col);
        }
    }


    @Test
    void outOfRangeMembersThrowIndexOutOfBounds()
    {
        UnionDataTable u = union();
        for (int m : new int[]
        {
                -1, 6, Integer.MAX_VALUE
        })
        {
            assertThrowsExactly(IndexOutOfBoundsException.class, () -> u.member(m));
            assertThrowsExactly(IndexOutOfBoundsException.class, () -> u.memberRowStart(m));
            assertThrowsExactly(IndexOutOfBoundsException.class, () -> u.memberColumnOf(m, 0));
        }
    }


    @Test
    void aUnionOfOnlyZeroRowMembersHasNoRowToLocate()
    {
        UnionDataTable u = new UnionDataTable("LB", zLead, zMid);
        assertEquals(0L, u.getRowCount());
        assertEquals(2, u.memberCount());
        assertEquals(0L, u.memberRowStart(0));
        assertEquals(0L, u.memberRowStart(1));
        assertThrowsExactly(IndexOutOfBoundsException.class, () -> u.memberOf(0));
    }


    /**
     * Asserts the D8 invariant over every row and column of {@code aUnion}; also checks that the
     * missing-column value is one shared instance, and that at least one cell took the delegate arm
     * so the assertion cannot pass vacuously.
     *
     * @return the number of cells that took the missing-column arm, for the caller to pin.
     */
    static int assertInvariant(UnionDataTable aUnion)
    {
        int delegated = 0;
        int missing = 0;
        @Nullable
        IDataValue sharedMissing = null;
        for (long r = 0; r < aUnion.getRowCount(); r++)
        {
            int m = aUnion.memberOf(r);
            IDataTable member = aUnion.member(m);
            long rr = r - aUnion.memberRowStart(m);
            assertTrue(rr >= 0 && rr < member.getRowCount(), "row " + r + " inside member " + m);
            for (int col = 0; col < aUnion.getColumnCount(); col++)
            {
                String where = "row " + r + ", column " + col;
                int mc = aUnion.memberColumnOf(m, col);
                IDataValue actual = aUnion.getDataValue(r, col);
                if (mc >= 0)
                {
                    IDataValue expected = member.getDataValue(rr, mc);
                    assertEquals(expected.getType(), actual.getType(), where);
                    assertEquals(expected.getValue(), actual.getValue(), where);
                    assertEquals(expected.isMissingOrInvalid(), actual.isMissingOrInvalid(), where);
                    assertEquals(member.getValue(rr, mc), aUnion.getValue(r, col), where);
                    delegated++;
                }
                else
                {
                    assertTrue(actual.isMissingOrInvalid(), where);
                    assertEquals(DataValueType.MISSING, actual.getType(), where);
                    assertNull(aUnion.getValue(r, col), where);
                    if (sharedMissing == null)
                    {
                        sharedMissing = actual;
                    }
                    assertSame(sharedMissing, actual, where);
                    missing++;
                }
            }
        }
        assertTrue(delegated > 0, "no cell took the delegate arm");
        return missing;
    }

}
