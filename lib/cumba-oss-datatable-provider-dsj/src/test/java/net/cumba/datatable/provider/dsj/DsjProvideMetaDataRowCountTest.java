package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.io.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parameterised documentation of row-count expectations for
 * {@link DsjTableProvider#provideMetaData(URI, FileInfo)} across the three DSJ transport formats.
 * <p>
 * The dsj2 provider populates {@code rowCount}/{@code totalRowCount} from the {@code records}
 * header attribute — so all three formats should return a non-zero row count even though no row
 * data is materialised.
 * <p>
 * The table here doubles as authoritative documentation: if the expectation drifts for any format,
 * this test will pin the regression to the exact format. Per-provider row-count behaviour for
 * non-DSJ formats is asserted in each provider module's own MetaData test.
 */
class DsjProvideMetaDataRowCountTest
{

    private DsjTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new DsjTableProvider();
    }


    static Stream<Arguments> formats()
    {
        return Stream.of(
                Arguments.of("JSON", DsjProviderSupplier.FI_DSJ_JSON, ".json",
                        "rowcount-test-7rows.json", false),
                Arguments.of("NDJSON", DsjProviderSupplier.FI_DSJ_NDJSON, ".ndjson",
                        "rowcount-test-7rows.ndjson", false),
                Arguments.of("DSJC", DsjProviderSupplier.FI_DSJ_DSJC, ".dsjc",
                        "rowcount-test-7rows.json", true));
    }


    @ParameterizedTest(name = "{0} populates rowCount from header")
    @MethodSource("formats")
    void provideMetaData_populatesRowCount(String formatName, FileInfo fileInfo, String suffix,
            String fixtureName, boolean compress)
        throws Exception
    {
        int expected = 7;
        URI uri = materialiseFixture(fixtureName, suffix, compress);

        DataTableMeta meta = provider.provideMetaData(uri, fileInfo);

        assertEquals(expected, meta.getRowCount(), "rowCount for " + formatName);
        assertEquals(expected, meta.getTotalRowCount(), "totalRowCount for " + formatName);
    }


    /**
     * Copies a fixture from classpath resources to a temp file. If {@code compress} is set, the
     * fixture is zlib-compressed (yielding the DSJC on-disk shape).
     */
    private URI materialiseFixture(String fixtureName, String suffix, boolean compress)
        throws IOException
    {
        File tmp = File.createTempFile("dsj2rowcount", suffix);
        tmp.deleteOnExit();
        try (InputStream in = getClass().getResourceAsStream("/fixtures/dsj2/" + fixtureName))
        {
            assertNotNull(in, "fixture not found: " + fixtureName);
            if (compress)
            {
                try (Deflater df = new Deflater(Deflater.BEST_SPEED);
                        DeflaterOutputStream dos = new DeflaterOutputStream(
                                Files.newOutputStream(tmp.toPath()), df))
                {
                    in.transferTo(dos);
                }
            }
            else
            {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return tmp.toURI();
    }

}
