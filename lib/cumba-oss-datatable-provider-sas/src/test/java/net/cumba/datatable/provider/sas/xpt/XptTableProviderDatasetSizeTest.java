package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.provider.sas.xpt.library.XptLibrary;
import net.cumba.datatable.provider.sas.xpt.library.XptLibraryMember;
import net.cumba.datatable.provider.sas.xpt.library.XptLibraryProvider;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end cover for the {@code dataset_size} table-metadata key on the XPT path.
 *
 * <p>
 * The key backs the rule-engine {@code extract_metadata("dataset_size")} accessor, whose only
 * consumer in the shipping corpus is {@code PMDA-SD1142} ("the submitted dataset is larger than 5
 * GB"). Until a provider populated the key that rule read {@code null} and could never fire on a
 * dataset of any size, so these tests pin the value where it is produced. The absent-key cases
 * (remote URI, unreadable file) are covered in {@code DataTableMetaSupportTest}.
 * </p>
 */
class XptTableProviderDatasetSizeTest
{

    private static final String SINGLE = "testdata/xpt/01_plain/adsl.xpt";

    private static final String MULTI = "testdata/xpt/05_multiple/sdtm.xpt";

    private static @Nullable Object sizeOf(DataTableMeta aMeta)
    {
        return aMeta.getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE);
    }


    /**
     * A disposable copy of a committed fixture — these tests never hand the real file to a
     * provider.
     *
     * <p>
     * ⚠ This is not tidiness.
     * {@link XptTableProvider#provideMetaData(java.net.URI, net.cumba.datatable.io.FileInfo)}
     * resolves a {@code file:} URI to the <em>caller's own</em> {@link File} and guards its cleanup
     * with {@code if (downloaded)}; pitest flips that guard, the {@code finally} then runs
     * {@code Files.deleteIfExists} on the caller's file, and an earlier revision of this test —
     * which passed {@code testdata/} straight in — had the committed {@code adsl.xpt} DELETED out
     * of the working tree by the first Pitest pass. Any test handing a real path to this provider
     * must hand it a throw-away one.
     * </p>
     */
    private static File copyOf(String aFixture, Path aTmp) throws IOException
    {
        File src = new File(System.getProperty("repoRoot"), aFixture);
        assertTrue(src.isFile(), () -> "expected fixture: " + src.getAbsolutePath());
        Path copy = aTmp.resolve(src.getName());
        Files.copy(src.toPath(), copy);
        return copy.toFile();
    }


    @Test
    void provide_recordsTheRealFileSize(@TempDir Path aTmp) throws IOException
    {
        File f = copyOf(SINGLE, aTmp);

        IDataTable t = new XptTableProvider().provide(f.toURI(), XptProviderSupplier.FI_XPT);
        assertNotNull(t);

        assertEquals(f.length(), sizeOf(t.getMetaData()),
                "dataset_size must be the byte count of the parsed file");
    }


    @Test
    void provide_recordsSizeAsANumber(@TempDir Path aTmp) throws IOException
    {
        // The UI reads the value as a Number and drops anything else; the engine likewise compares
        // numerically when the operand is a Number.
        File f = copyOf(SINGLE, aTmp);

        IDataTable t = new XptTableProvider().provide(f.toURI(), XptProviderSupplier.FI_XPT);
        assertInstanceOf(Number.class, sizeOf(t.getMetaData()));
    }


    @Test
    void provideMetaData_recordsTheRealFileSize(@TempDir Path aTmp) throws IOException
    {
        // The metadata-only path reads no observations, but it is what the library navigator and a
        // validation run see, so it must carry the size too.
        File f = copyOf(SINGLE, aTmp);

        DataTableMeta m = new XptTableProvider().provideMetaData(f.toURI(),
                XptProviderSupplier.FI_XPT);
        assertEquals(f.length(), sizeOf(m));
    }


    @Test
    void provide_memberOfAMultiDatasetXptStillCarriesTheSize(@TempDir Path aTmp) throws IOException
    {
        // How the browser actually opens a dataset: through a library member, whose URI carries a
        // "#DM"-style fragment. The size reported is the container's, which is what the
        // dataset_size rules mean by the submitted dataset — and it must not be lost to the
        // fragment, which is what Path.of would choke on.
        File f = copyOf(MULTI, aTmp);

        try (IDataTableLibrary lib = new XptLibraryProvider().provide(f.toURI(),
                XptProviderSupplier.FI_XPT))
        {
            assertNotNull(lib);
            List<XptLibraryMember> members = ((XptLibrary) lib).getMembers().toList();
            assertTrue(members.size() > 1, "fixture should hold several datasets");

            for (XptLibraryMember member : members)
            {
                IDataTable t = new XptTableProvider().provide(member, XptProviderSupplier.FI_XPT);
                assertNotNull(t);
                assertEquals(f.length(), sizeOf(t.getMetaData()),
                        () -> "member " + member.getName() + " (uri=" + member.getUri() + ")");
            }
        }
    }

}
