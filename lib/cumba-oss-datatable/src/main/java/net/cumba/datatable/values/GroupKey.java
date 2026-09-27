package net.cumba.datatable.values;

// OSS-IDENTITY datatable-key-identity: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

import org.jspecify.annotations.Nullable;

/**
 * The <b>composite group key</b>: the {@link GroupKeyPolicy.KeyPart#identity() identity objects} of
 * a row's grouping columns, as one hashable value ({@code PLAN-grouping-key-identity}).
 *
 * <p>
 * ⭐ <b>Why it exists.</b> A grouped operation forms its groups on the datatable index and hands
 * each row the value of <em>its</em> group. Until this type the two jobs used two key identities:
 * the groups were formed on the raw cell values, but each group's result was stored, and every
 * row's lookup was made, under the <b>rendered text</b> of the key. Two groups that render alike —
 * {@code 4.9999999999994} and {@code 5.0} both render {@code "5"} — wrote one map key, the later
 * group overwrote the earlier, and the earlier group's rows silently read the other group's value.
 * Grouping and lookup now key on one identity, the {@code KeyPart} identity, so they cannot
 * disagree:
 * </p>
 * <ul>
 * <li>two exact-distinct numbers are two keys — register {@code D64h}: <i>"key identity always
 * exact"</i>;</li>
 * <li>{@code -0.0} and {@code 0.0} are one key — register {@code D84} (<i>"we use own tolerance and
 * will always work on the real value"</i>) and owner 2026-09-27: <i>"-0 and 0 are to be treated as
 * one key everywhere"</i>;</li>
 * <li>a {@code LONG} {@code 2} and a {@code DOUBLE} {@code 2.0} are one key (plan decision
 * {@code D99b} of {@code PLAN-typed-expression-engine}), while a {@code LONG} beyond 2^53 keeps its
 * exact value;</li>
 * <li>{@code MIS}, {@code MIS_A} and {@code ""} are three keys — register {@code JKM R5}.</li>
 * </ul>
 *
 * <p>
 * <b>Shape.</b> {@link #of(Object...)} returns the <b>bare identity</b> for one component (no
 * wrapper — a {@code String}, a canonical {@code Double}, a {@code Long} or a {@link MissingValue}
 * constant), and a {@code GroupKey} holding the identities and a cached hash for two or more.
 * Equality is component-wise ({@link Arrays#equals(Object[], Object[])}), so it is exactly the
 * {@link GroupKeyPolicy.KeyPart} equality of every component. An absent grouping column contributes
 * {@code ""} on both the building and the probing side (the engine's EC-44 contract).
 * </p>
 *
 * <p>
 * ⛔ A {@code GroupKey} is built only from identity objects and is <b>never parsed from text</b>.
 * {@link #toString()} is for debugging; the report renders a group's key from its cells, not from
 * this object.
 * </p>
 */
public final class GroupKey
{

    /** The key of a grouping over no columns at all — one group over the whole table. */
    private static final GroupKey NONE = new GroupKey(new Object[0]);

    private final Object[] identities;

    private final int hash;

    private GroupKey(Object[] aIdentities)
    {
        identities = aIdentities;
        hash = Arrays.hashCode(aIdentities);
    }


    /**
     * The group key over {@code identities}, one per grouping column in column order.
     *
     * <p>
     * ⚠ The array is <b>adopted, not copied</b>: this is built once per group and once per probed
     * row, so a defensive copy would be a second allocation on the hottest path of every grouped
     * lookup. The caller hands over a fresh array and never writes to it again — the varargs call
     * shape does that by construction.
     * </p>
     *
     * @param identities
     *            the components' identity objects ({@link GroupKeyPolicy#keyIdentity}): a
     *            {@code String}, a {@code Double}, a {@code Long} or a {@link MissingValue}; an
     *            absent column is {@code ""}
     * @return the bare identity for one component, else a {@code GroupKey}
     * @throws IllegalArgumentException
     *             for a component that is not an identity object — loud beats a key that silently
     *             never meets its counterpart
     */
    public static Object of(Object... identities)
    {
        for (Object id : identities)
        {
            requireIdentity(id);
        }
        if (identities.length == 1)
        {
            return identities[0];
        }
        return identities.length == 0 ? NONE : new GroupKey(identities);
    }


    private static void requireIdentity(Object aIdentity)
    {
        if (!(aIdentity instanceof String || aIdentity instanceof Double
                || aIdentity instanceof Long || aIdentity instanceof MissingValue))
        {
            throw new IllegalArgumentException("not a key identity: "
                    + Objects.requireNonNull(aIdentity, "a key identity is never null").getClass()
                            .getName());
        }
    }


    /**
     * The number of components — always two or more, or zero for the whole-table key.
     *
     * @return the number of components
     */
    public int size()
    {
        return identities.length;
    }


    /**
     * One component's identity object, e.g. to render a group's key for a report or a test.
     *
     * @param aIndex
     *            the component, in grouping-column order
     * @return its identity object
     * @throws IndexOutOfBoundsException
     *             for an index outside {@code [0, size())}
     */
    public Object get(int aIndex)
    {
        return identities[aIndex];
    }


    @Override
    public boolean equals(@Nullable Object aOther)
    {
        return this == aOther || (aOther instanceof GroupKey other && hash == other.hash
                && Arrays.equals(identities, other.identities));
    }


    @Override
    public int hashCode()
    {
        return hash;
    }


    /**
     * A debugging rendering, e.g. {@code GroupKey[S1, 5.0]}. ⛔ Never parsed back and never used as
     * a key.
     */
    @Override
    public String toString()
    {
        StringJoiner sj = new StringJoiner(", ", "GroupKey[", "]");
        for (Object id : identities)
        {
            sj.add(String.valueOf(id));
        }
        return sj.toString();
    }

}
