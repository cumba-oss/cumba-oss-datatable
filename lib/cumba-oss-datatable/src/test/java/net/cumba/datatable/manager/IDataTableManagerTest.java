package net.cumba.datatable.manager;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IDataTableManagerTest
{

    // ==================== Constants ====================

    @Test
    void testColumnNameColumnConstant()
    {
        assertEquals("Column", IDataTableManager.COLUMN_NAME_COLUMN);
    }


    @Test
    void testColumnNameCountConstant()
    {
        assertEquals("Count", IDataTableManager.COLUMN_NAME_COUNT);
    }


    @Test
    void testColumnNamePercentConstant()
    {
        assertEquals("Percent", IDataTableManager.COLUMN_NAME_PERCENT);
    }

    // ==================== Constants are final ====================


    @Test
    void testConstantsNotNull()
    {
        assertNotNull(IDataTableManager.COLUMN_NAME_COLUMN);
        assertNotNull(IDataTableManager.COLUMN_NAME_COUNT);
        assertNotNull(IDataTableManager.COLUMN_NAME_PERCENT);
    }
}
