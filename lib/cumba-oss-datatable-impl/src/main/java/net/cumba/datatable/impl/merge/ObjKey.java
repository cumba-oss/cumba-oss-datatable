package net.cumba.datatable.impl.merge;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * A composite key over identity objects (a {@link String} or a {@code MissingValue} per component,
 * as {@code RelrecLinks.keyCell} classifies a cell), its hash computed once and compared first. A
 * class and not a record: a record's generated {@code equals} would compare the array by identity.
 */
final class ObjKey
{

    private final Object[] parts;

    private final int hash;

    /**
     * @param aParts
     *            the components; owned by the key from here on.
     */
    ObjKey(Object[] aParts)
    {
        parts = aParts;
        hash = Arrays.hashCode(aParts);
    }


    /**
     * The component at the given position.
     *
     * @param aIndex
     *            the position.
     * @return the identity object.
     */
    Object part(int aIndex)
    {
        return parts[aIndex];
    }


    /**
     * The number of components.
     *
     * @return the size.
     */
    int size()
    {
        return parts.length;
    }


    @Override
    public boolean equals(@Nullable Object aOther)
    {
        return aOther instanceof ObjKey other && other.hash == hash
                && Arrays.equals(parts, other.parts);
    }


    @Override
    public int hashCode()
    {
        return hash;
    }


    @Override
    public String toString()
    {
        return Arrays.toString(parts);
    }
}
