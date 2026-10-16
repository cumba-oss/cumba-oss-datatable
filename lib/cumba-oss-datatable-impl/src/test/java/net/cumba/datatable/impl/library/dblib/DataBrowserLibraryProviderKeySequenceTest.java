package net.cumba.datatable.impl.library.dblib;

// OSS-IDENTITY datatable-dblib: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Q36(iii), owner ruling 2026-10-12: a column {@code key} declared by a data-browser library is
 * stored on every path as the stack's {@code KeySequence} column-meta entry, an {@link Integer}
 * &gt; 0, as the Dataset-JSON and Define-XML providers store it
 * (PLAN-compare-type-change-and-column-key).
 *
 * <ul>
 * <li>(b) the library's own {@code columnMeta} array: the bean's {@code key};</li>
 * <li>(a) an external column-metadata table: its {@code key} column, beside the raw {@code key}
 * entry it always gave;</li>
 * <li>(c′) the fallback to the library's metadata: {@code IColumnMetadata.getKeySequence()}.</li>
 * </ul>
 * Path (d), a loaded member's column meta, is covered by {@code DataBrowserMetadataLibraryTest}.
 * <p>
 * The external tables are built in memory and handed over through the package-private
 * {@code loadMetaTable} seam (the data-table provider SPI is not on this module's test classpath),
 * which also gives each {@code key} column an exact type.
 */
class DataBrowserLibraryProviderKeySequenceTest
{

    private static final String TABLE = "cols.tbl";

    /**
     * An external column-metadata table {@code uri, name, key}: the first two STRING, the key
     * column of the given type. Each row is {@code {uri, name, key}}.
     */
    private static IDataTable metaTable(DataValueType aKeyType, Object[]... aRows)
    {
        DataTableColumnMeta[] metas =
        {
                DataTableColumnMeta.builder().index(0).name("uri").type(DataValueType.STRING)
                        .build(),
                DataTableColumnMeta.builder().index(1).name("name").type(DataValueType.STRING)
                        .build(),
                DataTableColumnMeta.builder().index(2).name("key").type(aKeyType).build()
        };
        CachedDataTableColumn[] cols =
        {
                new CachedDataTableColumn(0, DataValueType.STRING),
                new CachedDataTableColumn(1, DataValueType.STRING),
                new CachedDataTableColumn(2, aKeyType)
        };
        for (Object[] row : aRows)
        {
            for (int c = 0; c < cols.length; c++)
            {
                cols[c].addElement(row[c]);
            }
        }
        for (CachedDataTableColumn c : cols)
        {
            c.complete();
        }
        DataTableMeta meta = DataTableMeta.builder().name("cols").rowCount(aRows.length)
                .totalRowCount(aRows.length).columns(metas).build();
        return new ColumnCachedDataTable(meta, cols);
    }


    /** A provider whose every external column-metadata table is {@code aTable}. */
    private static DataBrowserLibraryProvider providerReturning(@Nullable IDataTable aTable)
    {
        return new DataBrowserLibraryProvider()
        {

            @Override
            @Nullable
            IDataTable loadMetaTable(URI aTableUri)
            {
                return aTable;
            }
        };
    }


    private static List<? extends DataTableColumnMeta> columnsOf(
            DataBrowserLibraryProvider aProvider, URI aLibUri)
        throws IOException
    {
        try (DataBrowserLibrary lib = (DataBrowserLibrary) aProvider.provide(aLibUri, null))
        {
            DataBrowserMember member = lib.getMembers().findFirst().orElseThrow();
            return aProvider.provideLibraryMemberColumns(member).toList();
        }
    }


    private static List<? extends DataTableColumnMeta> columnsOf(URI aLibUri) throws IOException
    {
        return columnsOf(providerReturning(null), aLibUri);
    }


    private static DataTableColumnMeta column(List<? extends DataTableColumnMeta> aColumns,
            String aName)
    {
        return aColumns.stream().filter(c -> aName.equals(c.getName())).findFirst().orElseThrow();
    }


    private static long keySequenceEntries(DataTableColumnMeta aColumn)
    {
        return aColumn.getMetaDataKeys().stream().filter(META_KEY_ITEM_KEY_SEQUENCE::equals)
                .count();
    }

    // ============================================================
    // (b) the library's own columnMeta array
    // ============================================================


    @Test
    void testInternalKeyIsStoredAsKeySequenceInteger(@TempDir Path aDir) throws IOException
    {
        L2LibraryFixture fx = L2LibraryFixture.in(aDir);
        String member = fx.uriOf("dm.data").toString();
        URI lib = fx.member("DM", "dm.data", "x")//
                .columnMeta(DataBrowserColumnMetaBean.builder().uri(member).name("STUDYID").key(1)
                        .build())//
                .columnMeta(DataBrowserColumnMetaBean.builder().uri(member).name("USUBJID").key(2)
                        .attributes(Map.of("origin", "CRF")).build())//
                .columnMeta(
                        DataBrowserColumnMetaBean.builder().uri(member).name("AGE").key(0).build())//
                .writeUri();

        List<? extends DataTableColumnMeta> cols = columnsOf(lib);

        assertEquals(1, column(cols, "STUDYID").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        DataTableColumnMeta usubjid = column(cols, "USUBJID");
        assertEquals(2, usubjid.getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertEquals("CRF", usubjid.getMetaData("origin"));
        // key 0 means "not a key": nothing is stored
        DataTableColumnMeta age = column(cols, "AGE");
        assertNull(age.getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertTrue(age.getMetaDataKeys().isEmpty());
    }


    @Test
    void testInternalKeyWinsOverAKeySequenceAttribute(@TempDir Path aDir) throws IOException
    {
        L2LibraryFixture fx = L2LibraryFixture.in(aDir);
        String member = fx.uriOf("dm.data").toString();
        URI lib = fx.member("DM", "dm.data", "x")//
                .columnMeta(DataBrowserColumnMetaBean.builder().uri(member).name("USUBJID").key(2)
                        .attributes(Map.of(META_KEY_ITEM_KEY_SEQUENCE, "9", "origin", "CRF"))
                        .build())//
                .writeUri();

        DataTableColumnMeta usubjid = column(columnsOf(lib), "USUBJID");

        assertEquals(2, usubjid.getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertEquals(1, keySequenceEntries(usubjid));
        assertEquals("CRF", usubjid.getMetaData("origin"));
    }

    // ============================================================
    // (a) an external column-metadata table
    // ============================================================


    @Test
    void testNumericExternalKeyIsStoredAsKeySequenceInteger(@TempDir Path aDir) throws IOException
    {
        L2LibraryFixture fx = L2LibraryFixture.in(aDir);
        String member = fx.uriOf("dm.data").toString();
        URI lib = fx.member("DM", "dm.data", "x").columnMetaTableUri(fx.uriOf(TABLE).toString())
                .writeUri();
        IDataTable table = metaTable(DataValueType.DOUBLE, //
                new Object[]
                {
                        member, "STUDYID", 1.0d
                }, new Object[]
                {
                        member, "USUBJID", 2.0d
                }, new Object[]
                {
                        member, "AGE", 0.0d
                }, new Object[]
                {
                        member, "SEX", MissingValue.MIS
                });

        List<? extends DataTableColumnMeta> cols = columnsOf(providerReturning(table), lib);

        assertEquals(1, column(cols, "STUDYID").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertEquals(2, column(cols, "USUBJID").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        // 0 and a missing cell (its text is ".", which passes the blank check) mean "no key":
        // no KeySequence, silently
        assertNull(column(cols, "AGE").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertNull(column(cols, "SEX").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
    }


    @Test
    void testTextExternalKeyIsStoredAsKeySequenceBesideTheRawKey(@TempDir Path aDir)
        throws IOException
    {
        L2LibraryFixture fx = L2LibraryFixture.in(aDir);
        String member = fx.uriOf("dm.data").toString();
        URI lib = fx.member("DM", "dm.data", "x").columnMetaTableUri(fx.uriOf(TABLE).toString())
                .writeUri();
        IDataTable table = metaTable(DataValueType.STRING, //
                new Object[]
                {
                        member, "STUDYID", "1"
                }, new Object[]
                {
                        member, "USUBJID", " 2.0 "
                }, new Object[]
                {
                        member, "AGE", "0"
                }, new Object[]
                {
                        member, "SEX", ""
                });

        List<? extends DataTableColumnMeta> cols = columnsOf(providerReturning(table), lib);

        DataTableColumnMeta studyid = column(cols, "STUDYID");
        assertEquals(1, studyid.getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        // the raw "key" entry stays as it always was
        assertEquals("1", studyid.getMetaData("key"));
        assertEquals(2, column(cols, "USUBJID").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertNull(column(cols, "AGE").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertNull(column(cols, "SEX").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
    }


    /**
     * A key that is not a whole number &gt;= 0 is a corrupt file: it raises and names the value,
     * the column and the source, as an unknown {@code type} does (owner ruling Q42 2026-09-14;
     * coordinator ruling M1, D-b).
     */
    @Test
    void testMalformedExternalKeyRaises(@TempDir Path aDir) throws IOException
    {
        L2LibraryFixture fx = L2LibraryFixture.in(aDir);
        String member = fx.uriOf("dm.data").toString();
        URI lib = fx.member("DM", "dm.data", "x").columnMetaTableUri(fx.uriOf(TABLE).toString())
                .writeUri();
        Object[][] cases =
        {
                {
                        DataValueType.STRING, "Y"
                },
                {
                        DataValueType.STRING, "1.5"
                },
                {
                        DataValueType.STRING, "-1"
                },
                {
                        DataValueType.DOUBLE, 1.5d
                },
                {
                        DataValueType.DOUBLE, -1.0d
                }
        };
        for (Object[] c : cases)
        {
            IDataTable table = metaTable((DataValueType) c[0], new Object[]
            {
                    member, "STUDYID", c[1]
            });
            DataBrowserLibraryProvider provider = providerReturning(table);

            IOException ex = assertThrows(IOException.class, () -> columnsOf(provider, lib),
                    String.valueOf(c[1]));

            String msg = ex.getMessage();
            assertTrue(msg.startsWith("Malformed key '"), msg);
            assertTrue(msg.contains("'STUDYID'"), msg);
            assertTrue(msg.contains(TABLE), msg);
        }
    }


    @Test
    void testInternalKeyWinsOverAnExternalKey(@TempDir Path aDir) throws IOException
    {
        L2LibraryFixture fx = L2LibraryFixture.in(aDir);
        String member = fx.uriOf("dm.data").toString();
        URI lib = fx.member("DM", "dm.data", "x").columnMetaTableUri(fx.uriOf(TABLE).toString())
                .columnMeta(DataBrowserColumnMetaBean.builder().uri(member).name("USUBJID").key(2)
                        .build())//
                .columnMeta(DataBrowserColumnMetaBean.builder().uri(member).name("STUDYID").key(0)
                        .build())//
                .writeUri();
        IDataTable table = metaTable(DataValueType.STRING, //
                new Object[]
                {
                        member, "STUDYID", "1"
                }, new Object[]
                {
                        member, "USUBJID", "3"
                });

        List<? extends DataTableColumnMeta> cols = columnsOf(providerReturning(table), lib);

        // internal metadata overrides the external table ...
        DataTableColumnMeta usubjid = column(cols, "USUBJID");
        assertEquals(2, usubjid.getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertEquals(1, keySequenceEntries(usubjid));
        // ... but an internal key 0 declares nothing, so the external key stays
        assertEquals(1, column(cols, "STUDYID").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
    }

    // ============================================================
    // (c′) the fallback to the library's metadata
    // ============================================================


    @Test
    void testMetadataFallbackCarriesTheKeySequence(@TempDir Path aDir) throws IOException
    {
        // The column metadata names another file of the same table name, so no column meta
        // matches the member's URI and the listing falls back to the library's metadata.
        L2LibraryFixture fx = L2LibraryFixture.in(aDir);
        URI lib = fx.member("DM", "dm.data", "x")//
                .columnMeta(DataBrowserColumnMetaBean.builder().uri("elsewhere/dm.xpt")
                        .name("STUDYID").key(3).build())//
                .columnMeta(DataBrowserColumnMetaBean.builder().uri("elsewhere/dm.xpt").name("AGE")
                        .build())//
                .writeUri();

        List<? extends DataTableColumnMeta> cols = columnsOf(lib);

        assertEquals(2, cols.size());
        assertEquals(3, column(cols, "STUDYID").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
        assertNull(column(cols, "AGE").getMetaData(META_KEY_ITEM_KEY_SEQUENCE));
    }
}
