package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RowFindingSlab} and its {@link RowFindingSlab.Builder}. Covers construction
 * invariants, lookup helpers, the {@code rowValues} name-mapping, and the builder's stable
 * insertion-order sort.
 */
class RowFindingSlabTest
{

    @Test
    void testEmptyConstantHasZeroRowsAndZeroVariables()
    {
        RowFindingSlab slab = RowFindingSlab.EMPTY;
        assertEquals(0, slab.rowCount());
        assertEquals(0, slab.variableCount());
        assertEquals(0, slab.rowIndices().length);
        assertEquals(0, slab.flatValues().length);
        assertEquals(0, slab.rowIndicesStream().count());
    }


    @Test
    void testDirectConstructorBuildsValidSlab()
    {
        int[] rows =
        {
                0, 2, 5
        };
        String[] values =
        {
                "a", "b", "c", "d", "e", "f"
        };
        RowFindingSlab slab = new RowFindingSlab(rows, values, 2);
        assertEquals(3, slab.rowCount());
        assertEquals(2, slab.variableCount());
        assertEquals("a", slab.valueAt(0, 0));
        assertEquals("f", slab.valueAt(2, 1));
        assertEquals(2, slab.rowIndexAt(1));
    }


    @Test
    void testConstructorRejectsInconsistentFlatValuesLength()
    {
        int[] rows =
        {
                0, 1
        };
        String[] values =
        {
                "a", "b", "c"
        }; // expected 4, given 3
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new RowFindingSlab(rows, values, 2));
        assertTrue(ex.getMessage().contains("flatValues length"));
    }


    @Test
    void testConstructorTreatsNullArraysAsEmpty()
    {
        // null arrays with variableCount=0 is equivalent to EMPTY
        RowFindingSlab slab = new RowFindingSlab(null, null, 0);
        assertEquals(0, slab.rowCount());
        assertEquals(0, slab.variableCount());
    }


    @Test
    void testPositionOfFindsExistingRow()
    {
        int[] rows =
        {
                1, 3, 7
        };
        String[] values =
        {
                "x", "y", "z"
        };
        RowFindingSlab slab = new RowFindingSlab(rows, values, 1);
        assertEquals(0, slab.positionOf(1));
        assertEquals(1, slab.positionOf(3));
        assertEquals(2, slab.positionOf(7));
    }


    @Test
    void testPositionOfReturnsMinusOneForMissingRow()
    {
        int[] rows =
        {
                1, 3, 7
        };
        String[] values =
        {
                "x", "y", "z"
        };
        RowFindingSlab slab = new RowFindingSlab(rows, values, 1);
        assertEquals(-1, slab.positionOf(0));
        assertEquals(-1, slab.positionOf(4));
        assertEquals(-1, slab.positionOf(99));
    }


    @Test
    void testContainsRow()
    {
        int[] rows =
        {
                2, 5
        };
        String[] values =
        {
                "a", "b"
        };
        RowFindingSlab slab = new RowFindingSlab(rows, values, 1);
        assertTrue(slab.containsRow(2));
        assertTrue(slab.containsRow(5));
        assertFalse(slab.containsRow(3));
    }


    @Test
    void testValueAtWithZeroVariableCount()
    {
        // When variableCount is 0, valueAt should return null regardless of position
        RowFindingSlab slab = new RowFindingSlab(new int[]
        {
                0, 1, 2
        }, new String[0], 0);
        assertNull(slab.valueAt(0, 0));
        assertNull(slab.valueAt(2, 0));
    }


    @Test
    void testRowValuesReturnsNameKeyedMap()
    {
        int[] rows =
        {
                0
        };
        String[] values =
        {
                "v1", "v2", "v3"
        };
        RowFindingSlab slab = new RowFindingSlab(rows, values, 3);

        Map<String, String> map = slab.rowValues(0, List.of("A", "B", "C"));
        assertEquals(3, map.size());
        assertEquals("v1", map.get("A"));
        assertEquals("v2", map.get("B"));
        assertEquals("v3", map.get("C"));
        // Preserves insertion order
        assertEquals(List.of("A", "B", "C"), List.copyOf(map.keySet()));
    }


    @Test
    void testRowValuesReturnsEmptyMapWhenNoVariables()
    {
        RowFindingSlab slab = RowFindingSlab.EMPTY;
        assertTrue(slab.rowValues(0, List.of()).isEmpty());
    }


    @Test
    void testRowValuesReturnsEmptyMapWhenVariableNamesNullOrEmpty()
    {
        int[] rows =
        {
                0
        };
        String[] values =
        {
                "v1", "v2"
        };
        RowFindingSlab slab = new RowFindingSlab(rows, values, 2);
        assertTrue(slab.rowValues(0, null).isEmpty());
        assertTrue(slab.rowValues(0, List.of()).isEmpty());
    }


    @Test
    void testEqualsAndHashCode()
    {
        int[] rows1 =
        {
                1, 2
        };
        String[] vals1 =
        {
                "a", "b"
        };
        RowFindingSlab slab1 = new RowFindingSlab(rows1, vals1, 1);

        int[] rows2 =
        {
                1, 2
        };
        String[] vals2 =
        {
                "a", "b"
        };
        RowFindingSlab slab2 = new RowFindingSlab(rows2, vals2, 1);

        assertEquals(slab1, slab2);
        assertEquals(slab1.hashCode(), slab2.hashCode());
        assertEquals(slab1, slab1);
    }


    @Test
    void testEqualsDifferentVariableCount()
    {
        RowFindingSlab slab1 = new RowFindingSlab(new int[]
        {
                0
        }, new String[]
        {
                "a", "b"
        }, 2);
        RowFindingSlab slab2 = new RowFindingSlab(new int[]
        {
                0, 1
        }, new String[]
        {
                "a", "b"
        }, 1);
        assertNotEquals(slab1, slab2);
    }


    @Test
    void testEqualsDifferentValues()
    {
        RowFindingSlab slab1 = new RowFindingSlab(new int[]
        {
                0
        }, new String[]
        {
                "a"
        }, 1);
        RowFindingSlab slab2 = new RowFindingSlab(new int[]
        {
                0
        }, new String[]
        {
                "b"
        }, 1);
        assertNotEquals(slab1, slab2);
    }


    @Test
    void testEqualsAgainstUnrelatedTypeIsFalse()
    {
        RowFindingSlab slab = RowFindingSlab.EMPTY;
        assertNotEquals("not-a-slab", slab);
        assertNotEquals(null, slab);
    }


    @Test
    void testToStringFormat()
    {
        RowFindingSlab slab = new RowFindingSlab(new int[]
        {
                0, 1
        }, new String[]
        {
                "a", "b"
        }, 1);
        String s = slab.toString();
        assertTrue(s.contains("rows=2"));
        assertTrue(s.contains("vars=1"));
    }

    // ==================== Builder ====================


    @Test
    void testBuilderRejectsNegativeVariableCount()
    {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RowFindingSlab.builder(-1));
        assertTrue(ex.getMessage().contains("variableCount"));
    }


    @Test
    void testBuilderEmptyReturnsSharedEmptyConstant()
    {
        RowFindingSlab slab = RowFindingSlab.builder(0).build();
        assertSame(RowFindingSlab.EMPTY, slab,
                "Builder with no rows AND no variables should return shared EMPTY");
    }


    @Test
    void testBuilderEmptyWithPositiveVariableCountReturnsEmptyOfThatShape()
    {
        RowFindingSlab slab = RowFindingSlab.builder(3).build();
        assertEquals(0, slab.rowCount());
        assertEquals(3, slab.variableCount());
    }


    @Test
    void testBuilderSortsByRowIndexAscending()
    {
        RowFindingSlab slab = RowFindingSlab.builder(1)//
                .addRow(5, new String[]
                {
                        "five"
                })//
                .addRow(1, new String[]
                {
                        "one"
                })//
                .addRow(3, new String[]
                {
                        "three"
                })//
                .build();

        assertArrayEquals(new int[]
        {
                1, 3, 5
        }, slab.rowIndices());
        assertEquals("one", slab.valueAt(0, 0));
        assertEquals("three", slab.valueAt(1, 0));
        assertEquals("five", slab.valueAt(2, 0));
    }


    @Test
    void testBuilderRowMustMatchVariableCount()
    {
        RowFindingSlab.Builder builder = RowFindingSlab.builder(2);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.addRow(0, new String[]
                {
                        "only-one"
                }));
        assertTrue(ex.getMessage().contains("variableCount"));
    }


    @Test
    void testBuilderRowNullForZeroVariableCount()
    {
        RowFindingSlab slab = RowFindingSlab.builder(0)//
                .addRow(2, null)//
                .addRow(0, null)//
                .build();
        assertEquals(2, slab.rowCount());
        assertArrayEquals(new int[]
        {
                0, 2
        }, slab.rowIndices());
    }


    @Test
    void testBuilderEnsureCapacityGrowsBackingArrays()
    {
        // Initial capacity is 8 — adding 10 rows must trigger an expansion.
        RowFindingSlab.Builder builder = RowFindingSlab.builder(1);
        for (int i = 0; i < 10; i++)
        {
            builder.addRow(i, new String[]
            {
                    "v" + i
            });
        }
        RowFindingSlab slab = builder.build();
        assertEquals(10, slab.rowCount());
        for (int i = 0; i < 10; i++)
        {
            assertEquals(i, slab.rowIndexAt(i));
            assertEquals("v" + i, slab.valueAt(i, 0));
        }
    }


    @Test
    void testRowIndicesStreamIsOrdered()
    {
        RowFindingSlab slab = RowFindingSlab.builder(1)//
                .addRow(9, new String[]
                {
                        "x"
                })//
                .addRow(0, new String[]
                {
                        "y"
                })//
                .addRow(4, new String[]
                {
                        "z"
                })//
                .build();
        int[] streamed = slab.rowIndicesStream().toArray();
        assertArrayEquals(new int[]
        {
                0, 4, 9
        }, streamed);
    }


    @Test
    void testGetVariableCountAccessor()
    {
        RowFindingSlab slab = new RowFindingSlab(new int[0], new String[0], 7);
        assertEquals(7, slab.getVariableCount());
    }
}
