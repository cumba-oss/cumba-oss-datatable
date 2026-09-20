package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.provider.cdt.library.CdtLibraryProvider;
import net.cumba.datatable.provider.cdt.library.CdtLibrarySupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CdtRoundTripTest
{

    private static final String DM = """
            dataset DM label="Demographics"
            col USUBJID type=Char label="Subject ID"
            col AGE type=Num
            ---
            S001 | 42
            S002 | 37
            ---
            """;

    private static final String AE = """
            dataset AE
            col USUBJID type=Char
            col AETERM type=Char
            ---
            S001 | Headache
            S002 | Rash
            ---
            """;

    @Test
    void providerReturnsFirstDatasetWhenNoFragment(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "dm_ae.cdt", DM + "\n" + AE);
        CdtTableProvider prov = new CdtTableProvider();
        IDataTable t = prov.provide(p.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals("DM", t.getMetaData().getName());
        assertEquals(2L, t.getRowCount());
        assertEquals("S001", t.getValue(0, 0));
    }


    @Test
    void providerReturnsNamedDatasetByFragment(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "dm_ae.cdt", DM + "\n" + AE);
        URI fragUri = URI.create(p.toUri() + "#AE");
        CdtTableProvider prov = new CdtTableProvider();
        IDataTable t = prov.provide(fragUri, CdtProviderSupplier.FI_CDT);
        assertEquals("AE", t.getMetaData().getName());
        assertEquals("Headache", t.getValue(0, 1));
    }


    @Test
    void librarySupplierRejectsFragmentUri()
    {
        URI bare = URI.create("file:///tmp/foo.cdt");
        URI withFrag = URI.create("file:///tmp/foo.cdt#DM");
        CdtLibrarySupplier s = new CdtLibrarySupplier();
        assertTrue(s.canProvideFor(bare, CdtProviderSupplier.FI_CDT));
        assertTrue(!s.canProvideFor(withFrag, CdtProviderSupplier.FI_CDT));
    }


    @Test
    void libraryProviderReturnsAllMembers(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "lib.cdt", DM + "\n" + AE);
        IDataTableLibrary lib = new CdtLibraryProvider().provide(p.toUri(),
                CdtProviderSupplier.FI_CDT, Map.<Property, String> of());
        ILibraryMember[] members = new CdtLibraryProvider().provideLibraryMembers(lib)
                .toArray(ILibraryMember[]::new);
        assertEquals(2, members.length);
        assertEquals("DM", members[0].getName());
        assertEquals("AE", members[1].getName());
        assertEquals("DM", members[0].getUri().getFragment());
    }


    @Test
    void nativeTypeAttrGoesToTypedField(@TempDir Path tmp) throws IOException
    {
        String content = """
                dataset DM
                col AGE type=Num nativeType=BEST
                ---
                42
                ---
                """;
        Path src = write(tmp, "dm.cdt", content);
        IDataTable t = new CdtTableProvider().provide(src.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals("BEST", t.getMetaData().getColumn("AGE").getNativeType());
    }


    @Test
    void datasetAttributesPropagatedToTableMetadata(@TempDir Path tmp) throws IOException
    {
        String content = """
                dataset ADSL class=ADSL standard="SDTMIG 3.4"
                col USUBJID type=Char
                ---
                S001
                ---
                """;
        Path p = write(tmp, "ds.cdt", content);
        IDataTable t = new CdtTableProvider().provide(p.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals("ADSL", t.getMetaData().getMetaData("class"));
        assertEquals("SDTMIG 3.4", t.getMetaData().getMetaData("standard"));
    }


    @Test
    void singleDatasetBareUriViaLibraryYieldsOneMember(@TempDir Path tmp) throws IOException
    {
        // Backward-compat: a file with one dataset and no closing fence still reads as
        // a library with a single member.
        String content = """
                dataset ONLY
                col X type=Char
                ---
                x
                """;
        Path p = write(tmp, "only.cdt", content);
        IDataTableLibrary lib = new CdtLibraryProvider().provide(p.toUri(),
                CdtProviderSupplier.FI_CDT, Map.<Property, String> of());
        ILibraryMember[] members = new CdtLibraryProvider().provideLibraryMembers(lib)
                .toArray(ILibraryMember[]::new);
        assertEquals(1, members.length);
        assertEquals("ONLY", members[0].getName());
    }


    @Test
    void providerReturnsNullOrThrowsForMissingDataset(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "dm.cdt", DM);
        URI badFrag = URI.create(p.toUri() + "#NOSUCH");
        CdtTableProvider prov = new CdtTableProvider();
        assertThrows(IOException.class, () -> prov.provide(badFrag, CdtProviderSupplier.FI_CDT));
    }


    @Test
    void parseNamedReturnsNullForUnknownDataset(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "dm.cdt", DM);
        String content = Files.readString(p);
        assertNull(CdtParser.parseNamed(content, "t", "NOSUCH"));
        assertNotNull(CdtParser.parseNamed(content, "t", "DM"));
    }


    private static Path write(Path aDir, String aName, String aContent) throws IOException
    {
        Path p = aDir.resolve(aName);
        Files.writeString(p, aContent, StandardCharsets.UTF_8);
        return p;
    }

}
