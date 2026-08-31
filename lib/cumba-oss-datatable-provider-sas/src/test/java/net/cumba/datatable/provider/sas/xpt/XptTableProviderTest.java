package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.sasutils.xpt.VariableXpt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XptTableProviderTest
{

    private XptTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new XptTableProvider();
    }

    // --- getSupportedFileInfos ---


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = provider.getSupportedFileInfos();
        assertNotNull(infos);
        assertEquals(1, infos.size());
        assertEquals(XptProviderSupplier.FI_XPT, infos.get(0));
    }

    // --- createColumnFor ---


    @Test
    void testCreateColumnForDouble()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("AGE").label("Age")
                .type(DataValueType.DOUBLE).build();
        CachedDataTableColumn col = provider.createColumnFor(meta);
        assertNotNull(col);
        assertEquals(DataValueType.DOUBLE, col.getType());
    }


    @Test
    void testCreateColumnForString()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(1).name("NAME").label("Name")
                .type(DataValueType.STRING).build();
        CachedDataTableColumn col = provider.createColumnFor(meta);
        assertNotNull(col);
        assertEquals(DataValueType.STRING, col.getType());
    }

    // --- getFullFormatName ---


    @Test
    void testGetFullFormatNameWithFormatAndDecimals()
    {
        VariableXpt vxpt = createVar("X", (short) 1, (short) 8);
        vxpt.formatTypeString = "BEST";
        vxpt.formatLength = (short) 12;
        vxpt.formatDecimals = (short) 2;

        assertEquals("BEST12.2", provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameWithFormatNoDecimals()
    {
        VariableXpt vxpt = createVar("X", (short) 1, (short) 8);
        vxpt.formatTypeString = "BEST";
        vxpt.formatLength = (short) 12;
        vxpt.formatDecimals = (short) 0;

        assertEquals("BEST12.", provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameWithFormatNoWidth()
    {
        VariableXpt vxpt = createVar("X", (short) 1, (short) 8);
        vxpt.formatTypeString = "DATE";
        vxpt.formatLength = (short) 0;
        vxpt.formatDecimals = (short) 0;

        assertEquals("DATE.", provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameCharNoFormat()
    {
        VariableXpt vxpt = createVar("NAME", (short) 2, (short) 20);
        vxpt.formatTypeString = null;
        vxpt.formatLength = (short) 0;
        vxpt.formatDecimals = (short) 0;

        // No stored format → no display format (no synthetic "$w." default; consistent with the
        // library provider and the BDAT provider).
        assertNull(provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameCharBlankFormat()
    {
        VariableXpt vxpt = createVar("NAME", (short) 2, (short) 20);
        vxpt.formatTypeString = "   ";
        vxpt.formatLength = (short) 0;
        vxpt.formatDecimals = (short) 0;

        assertNull(provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameCharWithExplicitWidth()
    {
        VariableXpt vxpt = createVar("NAME", (short) 2, (short) 20);
        vxpt.formatTypeString = null;
        vxpt.formatLength = (short) 10;
        vxpt.formatDecimals = (short) 0;

        // A format length without a format name is not a format → null.
        assertNull(provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameNumericNoFormat()
    {
        VariableXpt vxpt = createVar("X", (short) 1, (short) 8);
        vxpt.formatTypeString = null;
        vxpt.formatLength = (short) 0;
        vxpt.formatDecimals = (short) 0;

        assertNull(provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameNumericBlankFormat()
    {
        VariableXpt vxpt = createVar("X", (short) 1, (short) 8);
        vxpt.formatTypeString = "";
        vxpt.formatLength = (short) 0;
        vxpt.formatDecimals = (short) 0;

        assertNull(provider.getFullFormatName(vxpt));
    }


    @Test
    void testGetFullFormatNameWithLeadingTrailingSpaces()
    {
        VariableXpt vxpt = createVar("X", (short) 1, (short) 8);
        vxpt.formatTypeString = "  DATE  ";
        vxpt.formatLength = (short) 9;
        vxpt.formatDecimals = (short) 0;

        assertEquals("DATE9.", provider.getFullFormatName(vxpt));
    }

    // --- addColumn null-name fallback ---


    @Test
    void testAddColumnSubstitutesFallbackWhenNameIsNull()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///tmp/anon.xpt"));

        VariableXpt vxpt = createVar(null, (short) 2, (short) 8);

        provider.addColumn(support, vxpt, 0, URI.create("file:///tmp/anon.xpt"));

        DataTableColumnMeta[] cols = support.getTableMeta().build().getColumns();
        assertEquals("V1", cols[0].getName());
        assertEquals(DataValueType.STRING, cols[0].getType());
    }

    // --- helpers ---


    private VariableXpt createVar(String name, short typeId, short length)
    {
        VariableXpt vxpt = new VariableXpt();
        vxpt.name = name;
        vxpt.variableTypeId = typeId;
        vxpt.length = length;
        vxpt.label = name;
        vxpt.formatTypeString = null;
        vxpt.formatLength = (short) 0;
        vxpt.formatDecimals = (short) 0;
        return vxpt;
    }

}
