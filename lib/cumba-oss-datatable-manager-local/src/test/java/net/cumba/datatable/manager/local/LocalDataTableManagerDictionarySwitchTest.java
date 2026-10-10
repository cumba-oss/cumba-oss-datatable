package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * The internal manager's dictionary-formats switch is accepted for source compatibility of the
 * shared coreJ CLI / REST code; this manager resolves no dictionary formats either way.
 */
class LocalDataTableManagerDictionarySwitchTest
{

    @Test
    void noDictionaryFormatsWhateverTheSwitch()
    {
        assertFalse(new LocalDataTableManager().isResolvingDictionaryFormats());
        assertFalse(new LocalDataTableManager(true).isResolvingDictionaryFormats());
        LocalDataTableManager off = new LocalDataTableManager(false);
        assertFalse(off.isResolvingDictionaryFormats());
        assertEquals(new LocalDataTableManager().getSupportedDataLibraryInfos(),
                off.getSupportedDataLibraryInfos(), "an ordinary manager otherwise");
    }
}
