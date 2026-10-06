package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * The two format-flag accessors of {@link IDataTableColumnMeta} (the file is shared with the
 * internal datatable, pinned set {@code datatable-column-meta}). Nothing in this project sets
 * either key, so a loaded column answers {@code false}; the accessors read whatever a column
 * carries.
 */
class ColumnMetaFormatFlagsTest
{

    private static DataTableColumnMeta.DataTableColumnMetaBuilder col()
    {
        return DataTableColumnMeta.builder().index(0).name("C").type(DataValueType.STRING);
    }


    @Test
    void hasUnmappedFormatValuesReadsItsMetaKey()
    {
        assertFalse(col().build().hasUnmappedFormatValues());
        assertTrue(col().addMetaData(IDataTableColumnMeta.META_KEY_HAS_UNMAPPED_FORMAT_VALUES, true)
                .build().hasUnmappedFormatValues());
        assertFalse(
                col().addMetaData(IDataTableColumnMeta.META_KEY_HAS_UNMAPPED_FORMAT_VALUES, "true")
                        .build().hasUnmappedFormatValues(),
                "only a Boolean counts");
    }


    @Test
    void displayFormatUnavailableReadsItsMetaKey()
    {
        DataTableColumnMeta plain = col().build();
        assertFalse(plain.isDisplayFormatUnavailable(), "an absent key is not a flag");
        assertNull(plain.getDisplayFormatUnavailableReason());

        for (String reason : new String[]
        {
                IDataTableColumnMeta.DISPLAY_FORMAT_NO_CATALOG,
                IDataTableColumnMeta.DISPLAY_FORMAT_NOT_IN_CATALOG
        })
        {
            DataTableColumnMeta flagged = col()
                    .addMetaData(IDataTableColumnMeta.META_KEY_DISPLAY_FORMAT_UNAVAILABLE, reason)
                    .build();
            assertTrue(flagged.isDisplayFormatUnavailable(), reason);
            assertEquals(reason, flagged.getDisplayFormatUnavailableReason());
        }

        DataTableColumnMeta notAString = col()
                .addMetaData(IDataTableColumnMeta.META_KEY_DISPLAY_FORMAT_UNAVAILABLE, true)
                .build();
        assertFalse(notAString.isDisplayFormatUnavailable(), "only a String reason code is a flag");
        assertNull(notAString.getDisplayFormatUnavailableReason());
    }
}
