package net.cumba.datatable.merge;

import org.jspecify.annotations.Nullable;

/**
 * One component of a merge key in its sided form: the column read on the primary (left) side and
 * the column read on the partner (right) side. A bare key names the same column on both sides.
 *
 * @param left
 *            the primary-side column name.
 * @param right
 *            the partner-side column name.
 */
public record KeyColumn(String left, String right)
{

    /**
     * A bare key: the same column name on both sides.
     *
     * @param aName
     *            the column name.
     * @return the key component.
     */
    public static KeyColumn of(String aName)
    {
        return new KeyColumn(aName, aName);
    }


    /**
     * A sided key.
     *
     * @param aLeft
     *            the primary-side column name.
     * @param aRight
     *            the partner-side column name.
     * @return the key component.
     */
    public static KeyColumn sided(String aLeft, String aRight)
    {
        return new KeyColumn(aLeft, aRight);
    }


    /**
     * The qualifier of a dotted left name ({@code DM.RPATHCD} → {@code DM}), or {@code null} for a
     * bare one. A qualified left name reads the record side from an earlier merged part; the
     * datatable layer does not compose such chained keys itself
     * ({@code PLAN-merged-evaluation-table} §4: the caller composes them) and refuses them with
     * {@link UnresolvedQualifiedKeyException}.
     *
     * @return the qualifier or {@code null}.
     */
    public @Nullable String leftQualifier()
    {
        int dot = left.indexOf('.');
        return dot > 0 ? left.substring(0, dot) : null;
    }
}
