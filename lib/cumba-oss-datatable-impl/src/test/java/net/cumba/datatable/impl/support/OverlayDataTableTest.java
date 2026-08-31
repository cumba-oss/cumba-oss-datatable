package net.cumba.datatable.impl.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.AbstractDataTable;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueString;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OverlayDataTable}. Exercises the overlay-on-delegate contract: pass-through
 * reads when no override is set, override precedence per cell, column add/remove/rename with
 * override re-indexing, and metadata propagation including the delegate-order pruning behaviour.
 */
class OverlayDataTableTest
{

    // ---------- delegate stub ----------

    private static IDataTable stubTable(String aName, String[] aColNames, Object[][] aRows)
    {
        int colCount = aColNames.length;
        DataTableColumnMeta[] cols = new DataTableColumnMeta[colCount];
        for (int c = 0; c < colCount; c++)
        {
            cols[c] = DataTableColumnMeta.builder().index(c).name(aColNames[c]).label(aColNames[c])
                    .type(DataValueType.STRING).build();
        }
        DataTableMeta meta = DataTableMeta.builder().name(aName).label(aName).rowCount(aRows.length)
                .totalRowCount(aRows.length).columns(cols).build();

        return new AbstractDataTable()
        {

            @Override
            public long getRowCount()
            {
                return aRows.length;
            }


            @Override
            public Object getValue(long aRow, int aColumn)
            {
                return aRows[(int) aRow][aColumn];
            }


            @Override
            public DataTableMeta getMetaData()
            {
                return meta;
            }
        };
    }

    // ---------- pass-through ----------


    @Test
    void getValue_noOverride_returnsDelegateValue()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "USUBJID", "AGE"
        }, new Object[][]
        {
                {
                        "S1", "30"
                },
                {
                        "S2", "40"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        assertEquals("S1", overlay.getValue(0, 0));
        assertEquals("40", overlay.getValue(1, 1));
        assertEquals(2L, overlay.getRowCount());
    }

    // ---------- raw value overlay ----------


    @Test
    void setValue_byIndex_overridesDelegate()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "USUBJID"
        }, new Object[][]
        {
                {
                        "S1"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setValue(0, 0, "OVERLAID");

        assertEquals("OVERLAID", overlay.getValue(0, 0));
    }


    @Test
    void setValue_byColumnName_overridesDelegate()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "USUBJID"
        }, new Object[][]
        {
                {
                        "S1"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setValue(0L, "USUBJID", "OVERLAID");

        assertEquals("OVERLAID", overlay.getValue(0, 0));
    }


    @Test
    void setValue_unknownColumnName_isNoOp()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "USUBJID"
        }, new Object[][]
        {
                {
                        "S1"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setValue(0L, "DOES_NOT_EXIST", "X");

        assertEquals("S1", overlay.getValue(0, 0));
    }


    @Test
    void setValue_byColumnName_caseInsensitiveFallback()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "USUBJID"
        }, new Object[][]
        {
                {
                        "S1"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setValue(0L, "usubjid", "MIXED");

        assertEquals("MIXED", overlay.getValue(0, 0));
    }

    // ---------- IDataValue + Formatted overlays ----------


    @Test
    void setDataValue_overridesGetDataValue()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "AGE"
        }, new Object[][]
        {
                {
                        "30"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        IDataValue dv = new DataValueDouble(99.0);
        overlay.setDataValue(0, 0, dv);

        assertSame(dv, overlay.getDataValue(0, 0));
        assertEquals(99.0, overlay.getValue(0, 0));
    }


    @Test
    void setValue_thenSetDataValue_replacesRawOverlay()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "AGE"
        }, new Object[][]
        {
                {
                        "30"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setValue(0, 0, "RAW");
        overlay.setDataValue(0, 0, new DataValueString("VIA_DV"));

        assertEquals("VIA_DV", overlay.getValue(0, 0));
    }


    @Test
    void setDataValue_thenSetValue_replacesDataValueOverlay()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "AGE"
        }, new Object[][]
        {
                {
                        "30"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setDataValue(0, 0, new DataValueString("VIA_DV"));
        overlay.setValue(0, 0, "RAW");

        assertEquals("RAW", overlay.getValue(0, 0));
    }

    // ---------- column rename ----------


    @Test
    void renameColumn_byOldName_updatesLookupAndMeta()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "OLD"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        assertTrue(overlay.renameColumn("OLD", "NEW"));

        assertEquals("NEW", overlay.getMetaData().getColumn(0).getName());
        // Lookup by new name resolves
        overlay.setValue(0L, "NEW", "X");
        assertEquals("X", overlay.getValue(0, 0));
    }


    @Test
    void renameColumn_unknownName_returnsFalse()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        assertFalse(overlay.renameColumn("MISSING", "X"));
    }

    // ---------- column add ----------


    @Test
    void addColumn_newColumnHasNoDelegateValue_returnsNull()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        int newIdx = overlay.addColumn("ADDED");

        assertEquals(1, newIdx);
        assertEquals(2, overlay.getMetaData().getColumnCount());
        assertNull(overlay.getValue(0, newIdx));
    }


    @Test
    void addColumn_thenSetValue_overlayHoldsTheValue()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);
        int newIdx = overlay.addColumn("ADDED", DataValueType.DOUBLE, "Added Label");
        overlay.setValue(0, newIdx, 3.14);

        assertEquals(3.14, overlay.getValue(0, newIdx));
        DataTableColumnMeta cm = overlay.getMetaData().getColumn(newIdx);
        assertEquals("Added Label", cm.getLabel());
        assertEquals(DataValueType.DOUBLE, cm.getType());
    }

    // ---------- column remove + re-index ----------


    @Test
    void removeColumn_dropsColumnAndShiftsOverlayIndexes()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A", "B", "C"
        }, new Object[][]
        {
                {
                        "a1", "b1", "c1"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        // Overlay value on column C (index 2) before removing B
        overlay.setValue(0, 2, "C_OVERLAY");

        assertTrue(overlay.removeColumn("B"));

        // Column count drops to 2
        assertEquals(2, overlay.getMetaData().getColumnCount());
        // The C overlay should now be at index 1 (shifted from 2)
        assertEquals("C_OVERLAY", overlay.getValue(0, 1));
        // Column at index 0 is still A
        assertEquals("a1", overlay.getValue(0, 0));
    }


    @Test
    void removeColumn_unknownName_returnsFalse()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        assertFalse(overlay.removeColumn("MISSING"));
    }

    // ---------- column attribute overrides ----------


    @Test
    void setColumnLabel_updatesMetaForNamedColumn()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setColumnLabel("A", "New Label");

        assertEquals("New Label", overlay.getMetaData().getColumn(0).getLabel());
    }


    @Test
    void setColumnType_updatesMetaForNamedColumn()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setColumnType("A", DataValueType.LONG);

        assertEquals(DataValueType.LONG, overlay.getMetaData().getColumn(0).getType());
    }


    @Test
    void setColumnLength_updatesMetaForNamedColumn()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setColumnLength("A", 42);

        assertEquals(42, overlay.getMetaData().getColumn(0).getLength());
    }

    // ---------- table-level meta ----------


    @Test
    void setTableName_overridesMetaName()
    {
        IDataTable delegate = stubTable("ORIG", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setTableName("NEW");

        assertEquals("NEW", overlay.getMetaData().getName());
    }


    @Test
    void setTableLabel_overridesMetaLabel()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        overlay.setTableLabel("DEMOGRAPHICS");

        assertEquals("DEMOGRAPHICS", overlay.getMetaData().getLabel());
    }


    /**
     * A virtual table built by {@link OverlayDataTable#empty} has no source URI; setting one is
     * what lets a synthetic fixture serve URI-backed accessors such as
     * {@code extract_metadata("dataset_location")}.
     */
    @Test
    void setTableURI_overridesMetaUri()
    {
        OverlayDataTable overlay = OverlayDataTable.empty("BW", "Body Weight", 1);
        assertNull(overlay.getMetaData().getTableURI());

        overlay.setTableURI(URI.create("file:///study/send/BW.xpt"));

        assertEquals(URI.create("file:///study/send/BW.xpt"), overlay.getMetaData().getTableURI());
    }


    /** The override wins over a delegate that already carries a URI. */
    @Test
    void setTableURI_overridesDelegateUri()
    {
        OverlayDataTable base = OverlayDataTable.empty("BW", "Body Weight", 1);
        base.setTableURI(URI.create("file:///study/send/OLD.xpt"));
        OverlayDataTable overlay = new OverlayDataTable(base);

        assertEquals(URI.create("file:///study/send/OLD.xpt"), overlay.getMetaData().getTableURI());

        overlay.setTableURI(URI.create("file:///study/send/bw.xpt"));

        assertEquals(URI.create("file:///study/send/bw.xpt"), overlay.getMetaData().getTableURI());
    }

    // ---------- factory ----------


    @Test
    void empty_buildsZeroColumnTableWithGivenRowCount()
    {
        OverlayDataTable overlay = OverlayDataTable.empty("SS", "Subj Status", 5);

        assertEquals(5L, overlay.getRowCount());
        assertEquals(0, overlay.getMetaData().getColumnCount());
        assertEquals("SS", overlay.getMetaData().getName());
        assertEquals("Subj Status", overlay.getMetaData().getLabel());
    }


    @Test
    void empty_withNullLabel_fallsBackToName()
    {
        OverlayDataTable overlay = OverlayDataTable.empty("SS", null, 0);

        assertEquals("SS", overlay.getMetaData().getLabel());
    }

    // ---------- bounds ----------


    @Test
    void getValue_outOfRangeColumn_throws()
    {
        IDataTable delegate = stubTable("DM", new String[]
        {
                "A"
        }, new Object[][]
        {
                {
                        "v"
                }
        });
        OverlayDataTable overlay = new OverlayDataTable(delegate);

        assertThrows(IndexOutOfBoundsException.class, () -> overlay.getValue(0, 99));
    }
}
