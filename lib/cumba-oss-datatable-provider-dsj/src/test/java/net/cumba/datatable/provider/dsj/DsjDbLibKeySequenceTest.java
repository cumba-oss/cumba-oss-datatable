package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.metadata.dblib.DataBrowserMetadataLibrary;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A Dataset-JSON member wrapped by a data-browser library (`.dblib`) whose column metadata declares
 * a {@code key}: {@code DataTableMetaSupport.addColumn} copies the library's {@code KeySequence}
 * entry first, so the provider must not append its own second {@code KeySequence} entry. The
 * library's metadata overrides the file (first entry wins in {@link DataTableColumnMeta}); the
 * column lists the key once.
 */
class DsjDbLibKeySequenceTest
{

    private static final String KEYS = """
            {
              "datasetJSONCreationDateTime": "2026-10-16T10:00:00",
              "datasetJSONVersion": "1.1.0",
              "itemGroupOID": "IG.KEYS",
              "name": "KEYS",
              "label": "a dblib-wrapped member",
              "records": 1,
              "columns": [
                {"itemOID": "IT.K1", "name": "K1", "label": "key", "dataType": "string", "keySequence": 1},
                {"itemOID": "IT.PLAIN", "name": "PLAIN", "label": "file key only", "dataType": "string", "keySequence": 2}
              ],
              "rows": [
                ["a", "b"]
              ]
            }
            """;

    private static long count(List<String> aKeys, String aKey)
    {
        return aKeys.stream().filter(aKey::equals).count();
    }


    @Test
    void theLibraryKeySequenceIsListedOnceAndWins(@TempDir Path aDir) throws IOException
    {
        Path file = aDir.resolve("keys.json");
        Files.writeString(file, KEYS, StandardCharsets.UTF_8);
        URI uri = file.toUri();

        DataBrowserColumnMetaBean k1 = DataBrowserColumnMetaBean.builder().uri("keys.json")
                .name("K1").type("STRING").key(3).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        k1
                }).build();

        DsjTableProvider provider = new DsjTableProvider();
        provider.setMetadata(
                DataBrowserMetadataLibrary.from(bean, aDir.resolve("lib.dblib").toUri()));
        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        String ks = DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE;
        DataTableColumnMeta key = table.getMetaData().getColumn("K1");
        assertEquals(1L, count(List.copyOf(key.getMetaDataKeys()), ks),
                "KeySequence listed once: " + key.getMetaDataKeys());
        assertEquals(3, key.getMetaData(ks), "the library's key overrides the file's keySequence");

        DataTableColumnMeta plain = table.getMetaData().getColumn("PLAIN");
        assertEquals(1L, count(List.copyOf(plain.getMetaDataKeys()), ks));
        assertEquals(2, plain.getMetaData(ks), "no library key: the file's keySequence stays");
    }
}
