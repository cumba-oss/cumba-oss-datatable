package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class AbstractDataTableProviderTest
{

    // ==================== Test implementation ====================

    private static class TestDataTableProvider extends AbstractDataTableProvider
    {

        private List<FileInfo> supportedFiles = List.of();

        private IDataTable tableToReturn = null;

        private void setSupportedFiles(List<FileInfo> files)
        {
            this.supportedFiles = files;
        }


        private void setTableToReturn(IDataTable table)
        {
            this.tableToReturn = table;
        }


        @Override
        public List<FileInfo> getSupportedFileInfos()
        {
            return supportedFiles;
        }


        @Override
        public IDataTable provide(URI aUri, FileInfo aFileInfo) throws IOException
        {
            return tableToReturn;
        }


        @Override
        public net.cumba.datatable.DataTableMeta provideMetaData(URI aUri, FileInfo aFileInfo)
            throws IOException
        {
            return tableToReturn != null ? tableToReturn.getMetaData() : null;
        }

        // Expose protected methods for testing


        private CachedDataTableColumn[] testCreateCachedColumns(DataTableMeta aMeta)
        {
            return createCachedColumns(aMeta);
        }


        private void testCompleteParsedColumns(
                java.util.stream.Stream<CachedDataTableColumn> aColumns)
            throws IOException
        {
            completeParsedColumns(aColumns);
        }


        private void testDebugCalcDataSize(IDataTable aTable)
        {
            debugCalcDataSize(aTable);
        }
    }

    // ==================== Name and Description ====================

    @Test
    void testGetSetName()
    {
        TestDataTableProvider provider = new TestDataTableProvider();

        assertNull(provider.getName());

        // Use reflection or internal access - setName is protected
        // We'll verify via getName
    }


    @Test
    void testGetSetDescription()
    {
        TestDataTableProvider provider = new TestDataTableProvider();

        assertNull(provider.getDescription());
    }

    // ==================== DefineXml ====================


    @Test
    void testGetSetMetadata()
    {
        TestDataTableProvider provider = new TestDataTableProvider();

        assertNull(provider.getMetadata());
        // setMetadata is available
        provider.setMetadata(null);
        assertNull(provider.getMetadata());
    }

    // ==================== provide with ILibraryMember ====================


    @Test
    void testProvideWithLibraryMember() throws IOException
    {
        TestDataTableProvider provider = new TestDataTableProvider();

        // Create a simple table to return
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("col0")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm).rowCount(0)
                .build();
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        ColumnCachedDataTable expectedTable = new ColumnCachedDataTable(meta, col);
        provider.setTableToReturn(expectedTable);

        // Create a stub library member
        URI testUri = URI.create("file:///test.csv");
        net.cumba.datatable.library.ILibraryMember member = new net.cumba.datatable.library.ILibraryMember()
        {

            @Override
            public String getName()
            {
                return "test";
            }


            @Override
            public String getLabel()
            {
                return "Test";
            }


            @Override
            public IDataTableLibrary getLibrary()
            {

                return null;
            }


            @Override
            public URI getUri()
            {
                return testUri;
            }

        };

        IDataTable result = provider.provide(member, null);

        assertSame(expectedTable, result);
    }


    @Test
    void provide_nullLibraryMemberThrowsNPE()
    {
        // F-A13: a null ILibraryMember used to dereference aMember.getUri() with an obscure
        // NPE stack trace. The @NonNull guard (project-standard pattern) raises a clear,
        // Lombok-generated NullPointerException naming the parameter at the entry point.
        TestDataTableProvider provider = new TestDataTableProvider();

        NullPointerException ex = org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> provider.provide((net.cumba.datatable.library.ILibraryMember) null, null));
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("amember"),
                ex.getMessage());
    }


    @Test
    void provideMetaData_nullLibraryMemberThrowsNPE()
    {
        // F-A13: same @NonNull guard on the IDataTableProvider.provideMetaData(ILibraryMember,
        // FileInfo) default method — picked up through the AbstractDataTableProvider inheritance
        // chain.
        TestDataTableProvider provider = new TestDataTableProvider();

        NullPointerException ex = org.junit.jupiter.api.Assertions
                .assertThrows(NullPointerException.class, () -> provider
                        .provideMetaData((net.cumba.datatable.library.ILibraryMember) null, null));
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("amember"),
                ex.getMessage());
    }

    // ==================== getSupportedFileInfos ====================


    @Test
    void testGetSupportedFileInfosFromSubclass()
    {
        TestDataTableProvider provider = new TestDataTableProvider();
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        provider.setSupportedFiles(List.of(csv));

        List<FileInfo> infos = provider.getSupportedFileInfos();

        assertEquals(1, infos.size());
        assertEquals(csv, infos.get(0));
    }

    // ==================== createCachedColumns ====================


    @Test
    void testCreateCachedColumns()
    {
        TestDataTableProvider provider = new TestDataTableProvider();

        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("STR")
                .type(DataValueType.STRING).build();
        DataTableColumnMeta cm1 = DataTableColumnMeta.builder().index(1).name("NUM")
                .type(DataValueType.DOUBLE).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm0, cm1).rowCount(0)
                .build();

        CachedDataTableColumn[] cols = provider.testCreateCachedColumns(meta);

        assertEquals(2, cols.length);
        assertNotNull(cols[0]);
        assertNotNull(cols[1]);
    }

    // ==================== completeParsedColumns ====================


    @Test
    void testCompleteParsedColumns() throws IOException
    {
        TestDataTableProvider provider = new TestDataTableProvider();

        CachedDataTableColumn col0 = new CachedDataTableColumn(0, DataValueType.STRING);
        col0.addElement("a");
        col0.addElement("b");

        CachedDataTableColumn col1 = new CachedDataTableColumn(1, DataValueType.DOUBLE);
        col1.addElement(1.0);
        col1.addElement(2.0);

        // Should not throw
        provider.testCompleteParsedColumns(Arrays.stream(new CachedDataTableColumn[]
        {
                col0, col1
        }));
    }

    // ==================== debugCalcDataSize ====================


    @Test
    void testDebugCalcDataSize()
    {
        TestDataTableProvider provider = new TestDataTableProvider();

        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("VAL")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm).rowCount(3)
                .totalRowCount(3).build();
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("hello");
        col.addElement("world");
        col.addElement("test");
        col.complete();
        ColumnCachedDataTable table = new ColumnCachedDataTable(meta, col);

        // Should not throw — just logs debug info
        provider.testDebugCalcDataSize(table);
    }
}
