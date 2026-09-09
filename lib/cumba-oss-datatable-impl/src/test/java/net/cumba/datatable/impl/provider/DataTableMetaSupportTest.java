package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataTableMetaSupportTest
{

    // ==================== Constructor ====================

    @Test
    void constructor_noMetadata()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        assertNull(support.getMetadata());
    }


    @Test
    void constructor_caseSensitive()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null, true);
        assertNull(support.getMetadata());
    }

    // ==================== setTable ====================


    @Test
    void setTable_basic()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        URI uri = URI.create("file:///path/to/dm.xpt");

        support.setTable(uri);

        // table name derived from URI path
        assertEquals(0, support.getTableColumnCount());
    }


    @Test
    void setTable_withName()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        URI uri = URI.create("file:///path/to/dm.xpt");

        support.setTable(uri, "DM");

        assertEquals(0, support.getTableColumnCount());
    }


    @Test
    void setTable_calledTwiceThrows()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        URI uri = URI.create("file:///data.csv");
        support.setTable(uri);

        assertThrows(IllegalStateException.class, () -> support.setTable(uri));
    }


    @Test
    void setTable_withFragment()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        URI uri = URI.create("file:///archive.zip#ADSL");

        support.setTable(uri);

        DataTableMetaBuilder meta = support.getTableMeta();
        assertNotNull(meta);
        assertEquals("ADSL", meta.build().getName());
    }


    @Test
    void setTable_uriOnly()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        URI uri = URI.create("file:///path/to/ae.sas7bdat");

        support.setTable(uri);

        DataTableMetaBuilder meta = support.getTableMeta();
        assertEquals("AE", meta.build().getName());
    }

    // ==================== addColumn ====================


    @Test
    void addColumn_single()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "TEST");

        support.addColumn("USUBJID", DataValueType.STRING);

        assertEquals(1, support.getTableColumnCount());
    }


    @Test
    void addColumn_multiple()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "TEST");

        support.addColumn("USUBJID", DataValueType.STRING);
        support.addColumn("AGE", DataValueType.DOUBLE);
        support.addColumn("SEX", DataValueType.STRING);

        assertEquals(3, support.getTableColumnCount());
    }


    @Test
    void addColumn_duplicateNameThrows()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "TEST");

        support.addColumn("COL", DataValueType.STRING);

        assertThrows(IllegalStateException.class,
                () -> support.addColumn("COL", DataValueType.STRING));
    }


    @Test
    void addColumn_duplicateNameCaseInsensitive()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null, false);
        support.setTable(URI.create("file:///test.csv"), "TEST");

        support.addColumn("Col", DataValueType.STRING);

        // case-insensitive mode: "col" should be considered duplicate of "Col"
        assertThrows(IllegalStateException.class,
                () -> support.addColumn("col", DataValueType.STRING));
    }


    @Test
    void addColumn_duplicateNameCaseSensitive()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null, true);
        support.setTable(URI.create("file:///test.csv"), "TEST");

        support.addColumn("Col", DataValueType.STRING);

        // case-sensitive: "col" is different from "Col"
        support.addColumn("col", DataValueType.STRING);

        assertEquals(2, support.getTableColumnCount());
    }


    @Test
    void addColumn_nullNameThrows()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "TEST");

        assertThrows(NullPointerException.class,
                () -> support.addColumn(null, DataValueType.STRING));
    }

    // ==================== applyColumns / getTableColumns ====================


    @Test
    void applyColumns_noTableThrows()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);

        assertThrows(IllegalStateException.class, support::applyColumns);
    }


    @Test
    void getTableColumns_returnsColumns()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "TEST");
        support.addColumn("A", DataValueType.STRING);
        support.addColumn("B", DataValueType.DOUBLE);

        DataTableColumnMeta[] cols = support.getTableColumns();

        assertEquals(2, cols.length);
        assertEquals("A", cols[0].getName());
        assertEquals(DataValueType.STRING, cols[0].getType());
        assertEquals("B", cols[1].getName());
        assertEquals(DataValueType.DOUBLE, cols[1].getType());
    }


    @Test
    void getTableColumns_indices()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "TEST");
        support.addColumn("X", DataValueType.STRING);
        support.addColumn("Y", DataValueType.STRING);
        support.addColumn("Z", DataValueType.STRING);

        DataTableColumnMeta[] cols = support.getTableColumns();

        assertEquals(0, cols[0].getIndex());
        assertEquals(1, cols[1].getIndex());
        assertEquals(2, cols[2].getIndex());
    }

    // ==================== getTableMeta ====================


    @Test
    void getTableMeta_basic()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "MYTABLE");
        support.addColumn("COL", DataValueType.STRING);

        DataTableMetaBuilder meta = support.getTableMeta();

        assertNotNull(meta);
        assertEquals("MYTABLE", meta.build().getName());
        assertEquals(1, meta.build().getColumnCount());
    }

    // ==================== getTableNameFor (via setTable) ====================


    @Test
    void tableNameFromPath()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///data/ae.sas7bdat"));

        assertEquals("AE", support.getTableMeta().build().getName());
    }


    @Test
    void tableNameFromFragment()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///data/archive.zip#ADLB"));

        assertEquals("ADLB", support.getTableMeta().build().getName());
    }


    @Test
    void tableNameExplicit()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///data/file.csv"), "CUSTOM");

        assertEquals("CUSTOM", support.getTableMeta().build().getName());
    }

    // ==================== Meta key constants ====================


    @Test
    void metaKeyConstants()
    {
        assertEquals("SASDatasetName", DataTableMetaSupport.META_KEY_SAS_DATASET_NAME);
        assertEquals("SASFieldName", DataTableMetaSupport.META_KEY_SAS_FIELD_NAME);
        assertEquals("Comment", DataTableMetaSupport.META_KEY_COMMENT);
        assertEquals("Method", DataTableMetaSupport.META_KEY_ITEM_METHOD);
        assertEquals("KeySequence", DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE);
        assertEquals("Mandatory", DataTableMetaSupport.META_KEY_ITEM_MANDATORY);
        assertEquals("OrderNumber", DataTableMetaSupport.META_KEY_ITEM_ORDER_NUMBER);
        assertEquals("NoData", DataTableMetaSupport.META_KEY_ITEM_NO_DATA);
        assertEquals("Role", DataTableMetaSupport.META_KEY_ITEM_ROLE);
        assertEquals("Origin", DataTableMetaSupport.META_KEY_ITEM_ORIGIN);
        assertEquals("SignificantDigits", DataTableMetaSupport.META_KEY_ITEM_SIGNIFICANT_DIGITS);
        assertEquals("Purpose", DataTableMetaSupport.META_KEY_PURPOSE);
        assertEquals("Standard", DataTableMetaSupport.META_KEY_STANDARD);
        assertEquals("Structure", DataTableMetaSupport.META_KEY_STRUCTURE);
        assertEquals("Repeating", DataTableMetaSupport.META_KEY_REPEATING);
        assertEquals("Created", DataTableMetaSupport.META_KEY_CREATED);
        assertEquals("Modified", DataTableMetaSupport.META_KEY_MODIFIED);
        assertEquals("Encoding", DataTableMetaSupport.META_KEY_ENCODING);
    }

    // ==================== F-A4 contract test ====================


    /**
     * Contract test: nullable column-meta fields (label, displayFormat, nativeType, length=0) MUST
     * be settable to null without `applyColumns()` blowing up at build time. Catches a future
     * contributor accidentally marking one of these `@NonNull` without checking the producer side.
     * If this test fails, the offending @NonNull field is the bug — fix it rather than the test.
     */
    @Test
    void addColumnTolerates_nullLabel()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("file:/test"), "t");
        s.addColumn("c", DataValueType.STRING).label(null);
        assertNotNull(s.applyColumns(), "applyColumns must succeed with null label");
    }


    @Test
    void addColumnTolerates_nullDisplayFormat()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("file:/test"), "t");
        s.addColumn("c", DataValueType.STRING).displayFormat(null);
        assertNotNull(s.applyColumns(), "applyColumns must succeed with null displayFormat");
    }


    @Test
    void addColumnTolerates_nullNativeType()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("file:/test"), "t");
        s.addColumn("c", DataValueType.STRING).nativeType(null);
        assertNotNull(s.applyColumns(), "applyColumns must succeed with null nativeType");
    }


    @Test
    void addColumnTolerates_zeroLength()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("file:/test"), "t");
        s.addColumn("c", DataValueType.STRING).length(0);
        assertNotNull(s.applyColumns(), "applyColumns must succeed with length=0");
    }


    @Test
    void addColumnTolerates_allNullableFieldsAtOnce()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("file:/test"), "t");
        s.addColumn("c", DataValueType.STRING)//
                .label(null)//
                .displayFormat(null)//
                .nativeType(null)//
                .length(0);
        DataTableColumnMeta[] cols = s.applyColumns();
        assertNotNull(cols);
        assertEquals(1, cols.length);
        assertEquals("c", cols[0].getName());
        assertNull(cols[0].getLabel());
        assertNull(cols[0].getDisplayFormat());
        assertNull(cols[0].getNativeType());
        assertEquals(0, cols[0].getLength());
    }

    // ==================== setDatasetSize ====================


    @Test
    void setDatasetSize_fromLocalFile(@TempDir Path aTmp) throws Exception
    {
        Path f = aTmp.resolve("lb.xpt");
        Files.write(f, new byte[1234]);

        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(f.toUri(), "LB");
        s.setDatasetSize(f.toUri());

        assertEquals(Long.valueOf(1234L),
                s.getTableMeta().build().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void setDatasetSize_parsedFileWinsOverUri(@TempDir Path aTmp) throws Exception
    {
        // The XPT/parquet case: the provider parsed a local file that a remote URI was
        // materialised into, so the file is authoritative and the unusable URI is ignored.
        Path parsed = aTmp.resolve("downloaded.xpt");
        Files.write(parsed, new byte[77]);

        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("https://example.org/lb.xpt"), "LB");
        s.setDatasetSize(parsed.toFile(), URI.create("https://example.org/lb.xpt"));

        assertEquals(Long.valueOf(77L),
                s.getTableMeta().build().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void setDatasetSize_remoteUriRecordsNothing()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("https://example.org/lb.xpt"), "LB");
        s.setDatasetSize(URI.create("https://example.org/lb.xpt"));

        // Absent, not a -1 sentinel: a consumer must be able to tell "unknown" from a real size.
        assertNull(
                s.getTableMeta().build().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void setDatasetSize_missingFileRecordsNothing(@TempDir Path aTmp)
    {
        URI gone = aTmp.resolve("nope.xpt").toUri();

        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(gone, "LB");
        s.setDatasetSize(gone);

        assertNull(
                s.getTableMeta().build().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void setDatasetSize_stripsFragmentOfContainerMember(@TempDir Path aTmp) throws Exception
    {
        // A member of a multi-dataset container is addressed as "file:/x/lb.xpt#DM"; Path.of
        // rejects a URI carrying a fragment, so the size would otherwise be lost entirely.
        Path f = aTmp.resolve("lb.xpt");
        Files.write(f, new byte[4096]);
        URI member = URI.create(f.toUri() + "#DM");

        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(member, "DM");
        s.setDatasetSize(member);

        assertEquals(Long.valueOf(4096L),
                s.getTableMeta().build().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void setDatasetSize_emptyFileIsARealZero(@TempDir Path aTmp) throws Exception
    {
        Path f = aTmp.resolve("empty.csv");
        Files.write(f, new byte[0]);

        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(f.toUri(), "E");
        s.setDatasetSize(f.toUri());

        assertEquals(Long.valueOf(0L),
                s.getTableMeta().build().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void setDatasetSize_negativeIsNotRecorded()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        s.setTable(URI.create("file:/test"), "t");
        s.setDatasetSize(-1L);

        assertNull(
                s.getTableMeta().build().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void setDatasetSize_beforeSetTableThrows()
    {
        DataTableMetaSupport s = new DataTableMetaSupport(null);
        assertThrows(IllegalStateException.class, () -> s.setDatasetSize(42L));
    }
}
