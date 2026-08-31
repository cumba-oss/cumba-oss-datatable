package net.cumba.datatable.metadata;

import java.util.List;

import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * A metadata object that contains metadata about a single codelist.
 */
public interface ICodeList extends IMetadataElement
{

    /**
     * Returns the name of the codelist.
     *
     * @return the name of the codelist.
     */
    String getName();


    /**
     * Returns the type of values that can be mapped by this codelist.
     *
     * @return the type of values that can be mapped by this codelist.
     */
    DataValueType getValueType();


    /**
     * Returns a list of codelist entries (mapping entries).
     *
     * @return a list of codelist entries (mapping entries).
     */
    List<ICodelistEntry> getEntries();


    /**
     * Returns whether this codelist is extensible. An extensible codelist allows values beyond
     * those defined in the codelist.
     *
     * @return {@code true} if extensible, {@code false} if non-extensible, or {@code null} if
     *         unknown.
     */
    default @Nullable Boolean isExtensible()
    {
        return null;
    }

}
