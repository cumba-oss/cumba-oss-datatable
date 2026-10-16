package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.metadata.dblib.DataBrowserMetadataLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B2-C9 / U16 (PLAN-bugfix-datatable-readers-views, a side finding of the PACK G 6 review fold): a
 * data-browser library column attribute that happens to be named {@code dataType} (or
 * {@code targetDataType}) is copied into the column metadata <b>before</b> the provider appends its
 * own typed entries ({@code DataTableMetaSupport.addColumn}), so a lookup by key finds the
 * library's {@link String} first. {@code createDataTypeMapper} cast that first entry and the load
 * failed with a {@link ClassCastException}. The mapper must read the provider's own typed entry.
 */
class DsjDbLibDataTypeAttributeTest
{

    private static final String DSJ = """
            {
              "datasetJSONCreationDateTime": "2026-10-16T10:00:00",
              "datasetJSONVersion": "1.1.0",
              "itemGroupOID": "IG.DT",
              "name": "DT",
              "label": "data types",
              "records": 2,
              "columns": [
                {"itemOID": "IT.N", "name": "N", "label": "number", "dataType": "integer"},
                {"itemOID": "IT.S", "name": "S", "label": "text", "dataType": "string"}
              ],
              "rows": [
                [1, "a"],
                [2, "b"]
              ]
            }
            """;

    @Test
    void aLibraryAttributeNamedDataTypeDoesNotBreakTheLoad(@TempDir Path aDir) throws IOException
    {
        Path file = aDir.resolve("dt.json");
        Files.writeString(file, DSJ, StandardCharsets.UTF_8);

        DataBrowserColumnMetaBean n = DataBrowserColumnMetaBean.builder().uri("dt.json").name("N")
                .type("DOUBLE").attributes(Map.of("dataType", "a library note", "targetDataType",
                        "another library note"))
                .build();
        DataBrowserColumnMetaBean s = DataBrowserColumnMetaBean.builder().uri("dt.json").name("S")
                .type("STRING").attributes(Map.of("dataType", "free text")).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        n, s
                }).build();

        DsjTableProvider provider = new DsjTableProvider();
        provider.setMetadata(
                DataBrowserMetadataLibrary.from(bean, aDir.resolve("lib.dblib").toUri()));
        IDataTable table = provider.provide(file.toUri(), DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(2L, table.getRowCount());
        assertEquals(2L, ((Number) table.getValue(1, 0)).longValue(), "the integer mapper ran");
        assertEquals("b", table.getValue(1, 1));
        assertEquals("a library note", table.getMetaData().getColumn("N").getMetaData("dataType"),
                "the library's attribute is kept as it was given");
    }
}
