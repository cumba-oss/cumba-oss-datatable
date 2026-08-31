package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.provider.cdt.library.CdtLibraryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * F-C8: a UTF-8 BOM ({@code U+FEFF}) at the very start of a CDT file must be stripped before
 * parsing, otherwise the first token of the first line ({@code "dataset"}) gets corrupted into
 * {@code "﻿dataset"} and {@link CdtParser} reports a bogus error.
 */
class CdtBomStrippingTest
{

    private static final String DM_BODY = """
            dataset DM
            col USUBJID type=Char
            col AGE type=Num
            ---
            S001 | 42
            S002 | 37
            ---
            """;

    private static Path writeWithBom(Path aDir, String aName, String aBody) throws IOException
    {
        Path p = aDir.resolve(aName);
        Files.writeString(p, "﻿" + aBody, StandardCharsets.UTF_8);
        return p;
    }


    /** Pure-string helper: BOM at the head is removed, everything else untouched. */
    @Test
    void stripBomRemovesLeadingBom()
    {
        assertEquals("dataset DM", CdtTableProvider.stripBom("﻿dataset DM"));
    }


    /** Pure-string helper: no BOM, no change — same instance is returned. */
    @Test
    void stripBomLeavesBomFreeContentUntouched()
    {
        String s = "dataset DM";
        assertSame(s, CdtTableProvider.stripBom(s));
    }


    /** Pure-string helper: BOM only in the middle is left alone. */
    @Test
    void stripBomDoesNotTouchBomInMiddle()
    {
        String s = "dataset ﻿DM";
        assertSame(s, CdtTableProvider.stripBom(s));
    }


    /** Pure-string helper: handles null and empty input gracefully. */
    @Test
    void stripBomHandlesNullAndEmpty()
    {
        assertNull(CdtTableProvider.stripBom(null));
        assertEquals("", CdtTableProvider.stripBom(""));
    }


    /** F-C8 integration: a BOM-prefixed CDT file parses cleanly through the table provider. */
    @Test
    void cdtTableProviderHandlesBomPrefix(@TempDir Path tmp) throws IOException
    {
        Path p = writeWithBom(tmp, "dm.cdt", DM_BODY);
        IDataTable t = new CdtTableProvider().provide(p.toUri(), CdtProviderSupplier.FI_CDT);
        assertNotNull(t);
        assertEquals("DM", t.getMetaData().getName());
        assertEquals(2L, t.getRowCount());
        assertEquals("S001", t.getValue(0, 0));
    }


    /**
     * F-C8 integration: a BOM-prefixed CDT file is also accepted by the library provider, so a UI
     * walking a directory of mixed BOM / non-BOM exports stays consistent.
     */
    @Test
    void cdtLibraryProviderHandlesBomPrefix(@TempDir Path tmp) throws IOException
    {
        Path p = writeWithBom(tmp, "lib.cdt", DM_BODY);
        CdtLibraryProvider lp = new CdtLibraryProvider();
        IDataTableLibrary lib = lp.provide(p.toUri(), CdtProviderSupplier.FI_CDT);
        ILibraryMember[] members = lp.provideLibraryMembers(lib).toArray(ILibraryMember[]::new);
        assertEquals(1, members.length);
        assertEquals("DM", members[0].getName());
    }


    /**
     * F-C8 integration: non-file-scheme URIs (http stand-in via {@code jar:}-style) — guard via the
     * helper directly.
     */
    @Test
    void stripBomCalledOnTwoByteUnrelatedPrefixDoesNotMatch()
    {
        // U+FEFE looks similar but is a different codepoint; must not be stripped.
        String s = "﻾dataset";
        assertSame(s, CdtTableProvider.stripBom(s));
    }


    /**
     * F-C8 integration: BOM-prefixed file accessed via a fragment URI ({@code #DM}) also parses,
     * because the strip happens before parsing and the parser sees the canonical header.
     */
    @Test
    void cdtTableProviderHandlesBomWithFragment(@TempDir Path tmp) throws IOException
    {
        Path p = writeWithBom(tmp, "dm.cdt", DM_BODY);
        URI withFragment = URI.create(p.toUri() + "#DM");
        IDataTable t = new CdtTableProvider().provide(withFragment, CdtProviderSupplier.FI_CDT);
        assertEquals("DM", t.getMetaData().getName());
        assertEquals(2L, t.getRowCount());
    }
}
