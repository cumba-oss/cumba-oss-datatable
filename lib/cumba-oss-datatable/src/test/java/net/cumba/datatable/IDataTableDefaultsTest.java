package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests for the default methods on {@link IDataTable} and {@link IDataTableColumn}. These are
 * exercised via a minimal stub implementation that only provides {@link IDataTable#getMetaData()}
 * and {@link IDataTable#getValue(long, int)}.
 */
class IDataTableDefaultsTest
{

    private static DataTableColumnMeta col(int idx, String name, DataValueType type)
    {
        return DataTableColumnMeta.builder().index(idx).name(name).type(type).build();
    }

    /**
     * A simple, in-memory {@link IDataTable} backed by a 2-D Object array. Only the two abstract
     * methods are implemented; every other call exercises the interface defaults.
     */
    private static final class StubTable implements IDataTable
    {

        private final DataTableMeta meta;

        private final Object[][] values;

        StubTable(DataTableMeta aMeta, Object[][] aValues)
        {
            this.meta = aMeta;
            this.values = aValues;
        }


        @Override
        public DataTableMeta getMetaData()
        {
            return meta;
        }


        @Override
        public long getRowCount()
        {
            return values.length;
        }


        @Override
        public Object getValue(long aRow, int aColumn)
        {
            if (aRow < 0 || aRow >= values.length)
            {
                throw new IndexOutOfBoundsException("row " + aRow);
            }
            if (aColumn < 0 || aColumn >= values[(int) aRow].length)
            {
                throw new IndexOutOfBoundsException("col " + aColumn);
            }
            return values[(int) aRow][aColumn];
        }
    }

    private static StubTable threeRowTable()
    {
        DataTableMeta meta = DataTableMeta.builder().name("t")
                .setColumns(col(0, "S", DataValueType.STRING), col(1, "L", DataValueType.LONG))
                .rowCount(3).totalRowCount(3).build();
        Object[][] vals =
        {
                {
                        "a", 1L
                },
                {
                        "b", 2L
                },
                {
                        "c", 3L
                }
        };
        return new StubTable(meta, vals);
    }

    // ===== IDataTable.getColumnCount / getColumnIndex =====


    @Test
    void getColumnCount_delegatesToMetaData()
    {
        assertEquals(2, threeRowTable().getColumnCount());
    }


    @Test
    void getColumnIndex_byName_returnsIndexOrMinusOne()
    {
        IDataTable t = threeRowTable();
        assertEquals(0, t.getColumnIndex("S"));
        assertEquals(1, t.getColumnIndex("L"));
        assertEquals(-1, t.getColumnIndex("missing"));
    }

    // ===== row / column accessors =====


    @Test
    void getRowName_returns1BasedString()
    {
        IDataTable t = threeRowTable();
        assertEquals("1", t.getRowName(0));
        assertEquals("3", t.getRowName(2));
    }


    @Test
    void getRealRowIndex_inRange_returnsIndex()
    {
        IDataTable t = threeRowTable();
        assertEquals(0, t.getRealRowIndex(0));
        assertEquals(2, t.getRealRowIndex(2));
    }


    @Test
    void getRealRowIndex_outOfRange_throws()
    {
        IDataTable t = threeRowTable();
        assertThrows(IndexOutOfBoundsException.class, () -> t.getRealRowIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getRealRowIndex(99));
    }


    @Test
    void getDisplayRowIndex_inRange_returnsIndex()
    {
        IDataTable t = threeRowTable();
        assertEquals(0, t.getDisplayRowIndex(0));
        assertEquals(2, t.getDisplayRowIndex(2));
    }


    @Test
    void getDisplayRowIndex_outOfRange_returnsMinusOne()
    {
        IDataTable t = threeRowTable();
        assertEquals(-1, t.getDisplayRowIndex(-1));
        assertEquals(-1, t.getDisplayRowIndex(99));
    }


    @Test
    void getRow_returnsDefaultRowAndThrowsForBadIndex()
    {
        IDataTable t = threeRowTable();
        IDataTableRow r0 = t.getRow(0);
        assertNotNull(r0);
        assertEquals("a", r0.getValue(0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getRow(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getRow(99));
    }


    @Test
    void getRows_streamsEverythingByDefault()
    {
        IDataTable t = threeRowTable();
        List<IDataTableRow> all = t.getRows().toList();
        assertEquals(3, all.size());
    }


    @Test
    void getRows_rangeStreamsSubset()
    {
        IDataTable t = threeRowTable();
        List<IDataTableRow> mid = t.getRows(1, 3).toList();
        assertEquals(2, mid.size());
        assertEquals("b", mid.get(0).getValue(0));
        assertEquals("c", mid.get(1).getValue(0));
    }


    @Test
    void getRows_negativeEnd_treatsAsRowCount()
    {
        IDataTable t = threeRowTable();
        List<IDataTableRow> all = t.getRows(0, -1).toList();
        assertEquals(3, all.size());
    }


    @Test
    void getColumn_byIndex_returnsColumn()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        assertNotNull(c);
        assertEquals("a", c.getValue(0));
        assertEquals("c", c.getValue(2));
    }


    @Test
    void getColumn_byName_returnsColumn()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn("L");
        assertNotNull(c);
        assertEquals(1L, c.getValue(0));
    }


    @Test
    void getColumn_byUnknownName_throws()
    {
        IDataTable t = threeRowTable();
        assertThrows(java.util.NoSuchElementException.class, () -> t.getColumn("missing"));
    }


    @Test
    void getColumns_streamReturnsAllColumns()
    {
        IDataTable t = threeRowTable();
        assertEquals(2, t.getColumns().count());
    }


    @Test
    void getColumns_byNames_returnsMatchingColumnsInOrder()
    {
        IDataTable t = threeRowTable();
        List<IDataTableColumn> cols = t.getColumns("L", "S").toList();
        assertEquals(2, cols.size());
        assertEquals(1L, cols.get(0).getValue(0)); // L first
        assertEquals("a", cols.get(1).getValue(0)); // S second
    }


    @Test
    void getColumns_byIndices_returnsMatchingColumns()
    {
        IDataTable t = threeRowTable();
        List<IDataTableColumn> cols = t.getColumns(0, 1).toList();
        assertEquals(2, cols.size());
    }


    @Test
    void getColumns_byNameCollection_returnsMatchingColumns()
    {
        IDataTable t = threeRowTable();
        List<IDataTableColumn> cols = t.getColumns(List.of("S")).toList();
        assertEquals(1, cols.size());
    }

    // ===== value accessors =====


    @Test
    void getValue_byName_delegatesToIndex()
    {
        IDataTable t = threeRowTable();
        assertEquals(1L, t.getValue(0, "L"));
        assertEquals("c", t.getValue(2, "S"));
    }


    @Test
    void getValue_byUnknownName_throws()
    {
        IDataTable t = threeRowTable();
        assertThrows(java.util.NoSuchElementException.class, () -> t.getValue(0, "missing"));
    }


    @Test
    void getDataValue_byIndex_returnsTypedValue()
    {
        IDataTable t = threeRowTable();
        IDataValue v = t.getDataValue(0, 1);
        assertEquals(1L, v.getValueAsNumber().longValue());
    }


    @Test
    void getDataValue_byName_returnsTypedValue()
    {
        IDataTable t = threeRowTable();
        IDataValue v = t.getDataValue(2, "S");
        assertEquals("c", v.getValueAsString());
    }


    @Test
    void getDataValue_byNameUnknown_throws()
    {
        IDataTable t = threeRowTable();
        assertThrows(IllegalArgumentException.class, () -> t.getDataValue(0, "missing"));
    }

    // ===== isMissingOrNull / hashCodeAt =====


    @Test
    void isMissingOrNull_normalValue_returnsFalse()
    {
        IDataTable t = threeRowTable();
        assertFalse(t.isMissingOrNull(0, 0));
    }


    @Test
    void isMissingOrNull_missingValue_returnsTrue()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "S", DataValueType.STRING))
                .rowCount(1).totalRowCount(1).build();
        StubTable t = new StubTable(meta, new Object[][]
        {
                {
                        MissingValue.MIS
                }
        });
        assertTrue(t.isMissingOrNull(0, 0));
    }


    @Test
    void isMissingOrNull_nullValue_returnsTrue()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "S", DataValueType.STRING))
                .rowCount(1).totalRowCount(1).build();
        StubTable t = new StubTable(meta, new Object[][]
        {
                {
                        null
                }
        });
        assertTrue(t.isMissingOrNull(0, 0));
    }


    @Test
    void hashCodeAt_normalValue_matchesObjectHash()
    {
        IDataTable t = threeRowTable();
        assertEquals("a".hashCode(), t.hashCodeAt(0, 0));
    }


    @Test
    void hashCodeAt_missingValue_usesStableHash()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "S", DataValueType.STRING))
                .rowCount(1).totalRowCount(1).build();
        StubTable t = new StubTable(meta, new Object[][]
        {
                {
                        MissingValue.MIS
                }
        });
        assertEquals(MissingValue.MIS.hashCodeStable(), t.hashCodeAt(0, 0));
    }


    @Test
    void hashCodeAt_null_returnsZero()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "S", DataValueType.STRING))
                .rowCount(1).totalRowCount(1).build();
        StubTable t = new StubTable(meta, new Object[][]
        {
                {
                        null
                }
        });
        assertEquals(0, t.hashCodeAt(0, 0));
    }

    // ===== IDataTableColumn defaults (via DefaultDataTableColumn) =====


    @Test
    void column_getValues_streamsAll()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        List<@Nullable Object> all = c.getValues().toList();
        assertEquals(3, all.size());
        assertEquals("a", all.get(0));
        assertEquals("c", all.get(2));
    }


    @Test
    void column_getValues_range()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        List<Object> sub = c.getValues(1, 3).toList();
        assertEquals(2, sub.size());
        assertEquals("b", sub.get(0));
    }


    @Test
    void column_getValuesNegativeEnd_treatsAsRowCount()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        List<Object> all = c.getValues(0, -1).toList();
        assertEquals(3, all.size());
    }


    @Test
    void column_getValuesStartOutOfRange_throws()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        assertThrows(IndexOutOfBoundsException.class, () -> c.getValues(-1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> c.getValues(99, 100));
    }


    @Test
    void column_getValuesEndLessThanStart_throws()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        assertThrows(IllegalArgumentException.class, () -> c.getValues(2, 1));
    }


    @Test
    void column_getDataValues_streamsAll()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        Stream<IDataValue> s = c.getDataValues();
        assertEquals(3, s.count());
    }


    @Test
    void column_getDataValuesRange_streamsSubset()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        Stream<IDataValue> s = c.getDataValues(0, 2);
        assertEquals(2, s.count());
    }


    @Test
    void column_getDataValues_invalidRangeThrows()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        assertThrows(IndexOutOfBoundsException.class, () -> c.getDataValues(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> c.getDataValues(2, 1));
    }


    @Test
    void column_getValueList_returnsListView()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        List<@Nullable Object> view = c.getValueList();
        assertEquals(3, view.size());
        assertEquals("a", view.get(0));
        assertEquals("c", view.get(2));
    }


    @Test
    void column_getDataValueList_returnsListView()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        List<IDataValue> view = c.getDataValueList();
        assertEquals(3, view.size());
    }


    @Test
    void column_isMissingOrNull_works()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "S", DataValueType.STRING))
                .rowCount(2).totalRowCount(2).build();
        StubTable t = new StubTable(meta, new Object[][]
        {
                {
                        "x"
                },
                {
                        MissingValue.MIS
                }
        });
        IDataTableColumn c = t.getColumn(0);
        assertFalse(c.isMissingOrNull(0));
        assertTrue(c.isMissingOrNull(1));
    }


    @Test
    void column_hashCodeAt_works()
    {
        IDataTable t = threeRowTable();
        IDataTableColumn c = t.getColumn(0);
        assertEquals("a".hashCode(), c.hashCodeAt(0));
        assertEquals("c".hashCode(), c.hashCodeAt(2));
    }


    @Test
    void column_hashCodeAt_missing()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "S", DataValueType.STRING))
                .rowCount(1).totalRowCount(1).build();
        StubTable t = new StubTable(meta, new Object[][]
        {
                {
                        MissingValue.MIS
                }
        });
        IDataTableColumn c = t.getColumn(0);
        assertEquals(MissingValue.MIS.hashCodeStable(), c.hashCodeAt(0));
    }

    // ===== isEmptyOrMissing =====


    /**
     * A table carrying, in one character column, each of the four states the blankness predicate
     * has to separate: a populated value, an empty string, a {@link MissingValue}, and a raw
     * {@code null}.
     */
    private static StubTable blanknessTable()
    {
        DataTableMeta meta = DataTableMeta.builder().name("blank")
                .setColumns(col(0, "S", DataValueType.STRING), col(1, "L", DataValueType.LONG))
                .rowCount(4).totalRowCount(4).build();
        return new StubTable(meta, new Object[][]
        {
                {
                        "a", 1L
                },
                {
                        "", 2L
                },
                {
                        MissingValue.MIS, MissingValue.MIS
                },
                {
                        null, null
                }
        });
    }


    @Test
    void isEmptyOrMissing_foldsEmptyStringMissingAndNull()
    {
        IDataTable t = blanknessTable();

        assertFalse(t.isEmptyOrMissing(0, 0), "a populated value is not blank");
        assertTrue(t.isEmptyOrMissing(1, 0), "an empty string is blank");
        assertTrue(t.isEmptyOrMissing(2, 0), "a MissingValue is blank");
        assertTrue(t.isEmptyOrMissing(3, 0), "a null is blank");
    }


    @Test
    void isEmptyOrMissing_isWiderThanIsMissingOrNull()
    {
        IDataTable t = blanknessTable();

        // The empty string is the whole point: blank, but not missing.
        assertTrue(t.isEmptyOrMissing(1, 0));
        assertFalse(t.isMissingOrNull(1, 0));
    }


    @Test
    void isEmptyOrMissing_numericColumnIgnoresTheEmptyStringArm()
    {
        IDataTable t = blanknessTable();

        assertFalse(t.isEmptyOrMissing(0, 1));
        assertFalse(t.isEmptyOrMissing(1, 1), "a populated number is never blank");
        assertTrue(t.isEmptyOrMissing(2, 1));
        assertTrue(t.isEmptyOrMissing(3, 1));
    }


    @Test
    void isEmptyOrMissing_outOfRangeThrows()
    {
        IDataTable t = blanknessTable();

        assertThrows(IndexOutOfBoundsException.class, () -> t.isEmptyOrMissing(4, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.isEmptyOrMissing(0, 9));
    }


    @Test
    void column_isEmptyOrMissing_foldsEmptyStringMissingAndNull()
    {
        IDataTable t = blanknessTable();
        IDataTableColumn c = t.getColumn(0);

        assertFalse(c.isEmptyOrMissing(0));
        assertTrue(c.isEmptyOrMissing(1), "an empty string is blank");
        assertTrue(c.isEmptyOrMissing(2), "a MissingValue is blank");
        assertTrue(c.isEmptyOrMissing(3), "a null is blank");

        // A populated non-String cell: the empty-string arm must not swallow it.
        IDataTableColumn num = t.getColumn(1);
        assertFalse(num.isEmptyOrMissing(0));
        assertTrue(num.isEmptyOrMissing(2));
    }


    @Test
    void column_isEmptyOrMissing_matchesTheTableLevelAnswer()
    {
        StubTable t = blanknessTable();
        IDataTableColumn c = t.getColumn(0);

        for (long r = 0; r < 4; r++)
        {
            assertEquals(t.isEmptyOrMissing(r, 0), c.isEmptyOrMissing(r),
                    "table and column disagreed at row " + r);
        }
        assertFalse(c.isEmptyOrMissing(0));
        assertTrue(c.isEmptyOrMissing(1));
    }


    /**
     * The interface defaults and the value-level predicate agree on EVERY row of the fixture: a
     * populated value, an empty string, a {@link MissingValue} in a character column, and a raw
     * {@code null} ({@code DataValueSupport.getAsDataValue} wraps a null as
     * {@code DataValueMissing(MIS)}, so both levels call it blank).
     *
     * <p>
     * ⭐ Row 2 — the {@link MissingValue} in a <b>character</b> column — used to be excluded here,
     * because the value level saw the rendered {@code "."} while the column level saw the raw
     * sentinel. That disagreement was the defect PLAN-cdt-char-missing-contract phase 6 removed
     * (the missing-value check is now resolved above the type switch), so the loop covers all four
     * rows and the exclusion is gone.
     * </p>
     */
    @Test
    void column_isEmptyOrMissing_agreesWithTheValueLevel()
    {
        IDataTableColumn c = blanknessTable().getColumn(0);

        for (long row : new long[]
        {
                0, 1, 2, 3
        })
        {
            assertEquals(c.getDataValue(row).isEmptyOrMissing(), c.isEmptyOrMissing(row),
                    "column vs value level disagree at row " + row);
        }
    }


    /**
     * ⭐ <b>Rewritten, not deleted</b> — PLAN-cdt-char-missing-contract phase 6. This test used to
     * be called {@code getDataValue_rendersAMissingValueAsItsStringInACharacterColumn} and pinned
     * the OPPOSITE contract: that asking for a cell <em>as a</em> {@link DataValueType#STRING}
     * rendered a {@link MissingValue} into its display text, so the returned {@code IDataValue}
     * answered {@code isMissingOrInvalid() == false} and callers were told to ask blankness of the
     * table or column instead.
     *
     * <p>
     * That asymmetry was a defect, not a design: {@code DataValueSupport.getAsDataValue} resolved
     * its type switch before its {@code instanceof MissingValue} check, and only the STRING and
     * VARIABLE arms — which stringify anything non-null — swallowed the sentinel. Every provider
     * that stores a {@link MissingValue} in a character column (Dataset-JSON, Parquet, RDS, OSB,
     * XLSX, {@code .cdt}) was therefore answered "not missing" through the interface default, while
     * the buffer-backed override in {@code AbstractDataBuffer.createDataValue} — which has always
     * tested {@code instanceof MissingValue} first — said "missing" for the same cell.
     * </p>
     *
     * <p>
     * ⛔ The RENDERING did not change: a missing cell still displays as its display text
     * ({@code "."} for {@code MIS}), so nothing shown or exported moves. Only the answer to "is
     * this missing?" does.
     * </p>
     */
    @Test
    void getDataValue_keepsAMissingValueMissingInACharacterColumn()
    {
        IDataTable t = blanknessTable();

        IDataValue charCell = t.getDataValue(2, 0);
        assertEquals(".", charCell.getValueAsString(),
                "the rendering is unchanged — a missing cell still displays as its text");
        assertTrue(charCell.isMissingOrInvalid(),
                "a MissingValue in a CHARACTER column is missing, whatever type was asked for");
        assertTrue(charCell.isEmptyOrMissing());

        // The raw-value predicates were always correct, and still are.
        assertTrue(t.isEmptyOrMissing(2, 0));
        assertTrue(t.getColumn(0).isEmptyOrMissing(2));

        // A numeric column never had the problem: every numeric helper returns null for a
        // MissingValue, so control always reached the shared missing arm.
        IDataValue numCell = t.getDataValue(2, 1);
        assertTrue(numCell.isMissingOrInvalid(), "a MissingValue survives in a numeric column");
        assertTrue(numCell.isEmptyOrMissing());
    }
}
