package net.cumba.datatable.metadata;

import java.util.Optional;
import java.util.Set;

/**
 * A generic element of metadata. All the other metadata interfaces extend this interface.
 */
public interface IMetadataElement
{

    /**
     * Returns a set of all keys available in this element.
     *
     * @return a set of all keys available in this element. This set might be empty, but never null.
     */
    Set<String> getMetaKeys();


    /**
     * Returns the value that is stored for the given key.
     *
     * @param aKey
     *            the key to retrieve the value for.
     * @return the value that is stored for the given key.<br/>
     *         If the given key is contained in {@link #getMetaKeys()}, it is ensured that a value
     *         is present.
     */
    Optional<Object> getMetaValue(String aKey);
}
