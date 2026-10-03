package net.cumba.datatable.library.folder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import net.cumba.datatable.library.ILibraryMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link FolderLibraryProvider}'s member-column channel and its properties, called directly. */
class FolderLibraryProviderDirectTest
{

    @TempDir
    File tempDir;

    @Test
    void foreignMember_answersNoColumns() throws IOException
    {
        assertEquals(0, new FolderLibraryProvider()
                .provideLibraryMemberColumns(mock(ILibraryMember.class)).count());
    }


    @Test
    void providerProperties_offerTheLibraryName()
    {
        var props = new FolderLibraryProvider().getProviderProperties(tempDir.toURI(), null);
        assertFalse(props.isEmpty(), "the library-name property must be offered");
        assertTrue(props.get(0).name().toLowerCase(java.util.Locale.ROOT).contains("name"),
                "property: " + props.get(0).name());
    }
}
