package net.cumba.datatable;

import java.util.Collection;

import lombok.NonNull;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * Metadata about a column in a {@link IDataTable}.
 */
public interface IDataTableColumnMeta
{

    /**
     * Metadata key carrying a {@link Boolean} flag set to {@link Boolean#TRUE} when the column
     * contains at least one value that is not present in the key set of its attached categorical
     * display format. Populated by the data-table manager on table load.
     */
    String META_KEY_HAS_UNMAPPED_FORMAT_VALUES = "hasUnmappedFormatValues";

    /**
     * Returns the index of the column in the data table.
     *
     * @return the index of the column in the data table.<br/>
     *         <b>This must be <code>&gt;=0</code>!</b>
     */
    int getIndex();


    /**
     * Returns the name of the column.
     *
     * @return the name of the column.<br/>
     *         <b>This must not be <code>null</code>!</b>
     */
    String getName();


    /**
     * Returns an optional column label.
     *
     * @return an optional column label.
     */
    @Nullable
    String getLabel();


    /**
     * Returns the length of the column. If no length is specified 0 should be returned.
     *
     * @return the length of the column. If no length is specified 0 should be returned.
     */
    int getLength();


    /**
     * Returns a String that represents the native type of the column.
     *
     * @return a String that represents the native type of the column.
     */
    String getNativeType();


    /**
     * Returns the mapped type of the column.
     *
     * @return the mapped type of the column.
     */
    DataValueType getType();


    /**
     * Returns the optional name of an assigned display format.
     *
     * @return the optional name of an assigned display format.
     */
    @Nullable
    String getDisplayFormat();


    /**
     * Retrieve custom metadata by a key.
     *
     * @param aKey
     *            the key to retrieve metadata for.
     * @return the value for the given key or null if no value is available for the given key.
     */
    @Nullable
    Object getMetaData(@NonNull String aKey) throws NullPointerException;


    /**
     * Retrieve custom metadata by a key.
     *
     * @param aKey
     *            the key to retrieve metadata for.
     * @param aDefault
     *            the default value to be returned if the key is not found.
     * @return the value for the given key or aDefault if no value is available for the given key.
     */
    @Nullable
    Object getMetaData(@NonNull String aKey, @Nullable Object aDefault) throws NullPointerException;


    /**
     * Returns a collection of all available custom metadata keys.
     *
     * @return a collection of all available custom metadata keys.
     */
    Collection<String> getMetaDataKeys();


    /**
     * Convenience accessor for the {@link #META_KEY_HAS_UNMAPPED_FORMAT_VALUES} flag.
     *
     * @return true if this column has been flagged as containing at least one value that is not
     *         covered by its attached categorical display format's key set; false otherwise.
     */
    default boolean hasUnmappedFormatValues()
    {
        return Boolean.TRUE.equals(getMetaData(META_KEY_HAS_UNMAPPED_FORMAT_VALUES, false));
    }
}
