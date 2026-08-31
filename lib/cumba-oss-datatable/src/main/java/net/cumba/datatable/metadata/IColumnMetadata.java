package net.cumba.datatable.metadata;

import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * A metadata object that contains metadata about a single data table column.
 */
public interface IColumnMetadata extends IMetadataElement
{

    /**
     * Returns the name of the column.
     *
     * @return the name of the column.
     */
    String getName();


    /**
     * Returns the column label.
     *
     * @return the column label, or {@code null} if none is defined.
     */
    @Nullable
    String getLabel();


    /**
     * Returns the name of the format to be used for this column.
     *
     * @return the name of the format to be used for this column. This might either be a codelist or
     *         a name of a standard format. {@code null} if none is defined.
     */
    @Nullable
    String getDisplayFormat();


    /**
     * Returns the ordinal index of the column in the data table.
     *
     * @return the ordinal index of the column in the data table.
     */
    int getIndex();


    /**
     * Returns the type of values of this column.
     *
     * @return the type of values of this column.
     */
    DataValueType getType();


    /**
     * Returns the length of the column.
     *
     * @return the length of the column. If no length is specified 0 should be returned.
     */
    int getLength();


    /**
     * Returns a String that represents the native type of the column as provided by the metadata
     * source.
     *
     * @return a String that represents the native type of the column as provided by the metadata
     *         source.
     */
    @Nullable
    String getNativeType();


    /**
     * Returns the key sequence number of this column.
     *
     * @return the key sequence number of this column. If the column is not a key column, 0 should
     *         be returned. A value greater than 0 indicates that this column is part of the sort
     *         key, with the value indicating the position in the key sequence.
     */
    int getKeySequence();


    /**
     * Returns whether this column is a by-group column.
     *
     * @return true if this column is a by-group column, false otherwise. A by-group column is used
     *         to define groups within the data table for display purposes.
     */
    boolean isByGroup();


    /**
     * Returns the CDISC core designation for this column. Typical values are {@code "Req"}
     * (required), {@code "Exp"} (expected), and {@code "Perm"} (permissible).
     *
     * @return the core designation, or {@code null} if not available.
     */
    default @Nullable String getCore()
    {
        return null;
    }


    /**
     * Returns the role of this column (e.g., "Identifier", "Topic", "Timing", "Qualifier").
     *
     * @return the role, or {@code null} if not available.
     */
    default @Nullable String getRole()
    {
        return null;
    }


    /**
     * Returns the name of the codelist associated with this column.
     *
     * @return the codelist name, or {@code null} if no codelist is associated.
     */
    default @Nullable String getCodelist()
    {
        return null;
    }

}
