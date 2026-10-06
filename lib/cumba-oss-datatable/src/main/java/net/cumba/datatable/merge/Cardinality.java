package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Locale;

/**
 * The declared shape of a merge ({@code PLAN-merged-evaluation-table} §3.4, MET #12): left is the
 * primary, right the partner. A side declared {@code 1} must carry each participating key once; a
 * duplicate there is a {@link CardinalityViolationException}, never a silent expansion. A side
 * declared {@code n} may repeat its key, and a repeating partner key expands the primary record
 * into one merged row per partner row. The default, {@link #MANY_TO_MANY}, checks nothing.
 */
public enum Cardinality
{

    /** Both sides unique: 0 or 1 partner row per primary record. */
    ONE_TO_ONE("1:1", true, true),

    /** The primary is unique, the partner may repeat: the primary record expands. */
    ONE_TO_MANY("1:n", true, false),

    /** The partner is unique: 0 or 1 partner row per primary record, no expansion. */
    MANY_TO_ONE("n:1", false, true),

    /** Nothing is checked; every combination of equal keys is a merged row. */
    MANY_TO_MANY("n:m", false, false);

    private final String text;

    private final boolean leftUnique;

    private final boolean rightUnique;

    Cardinality(String aText, boolean aLeftUnique, boolean aRightUnique)
    {
        text = aText;
        leftUnique = aLeftUnique;
        rightUnique = aRightUnique;
    }


    /**
     * The authored spelling ({@code 1:1}, {@code 1:n}, {@code n:1}, {@code n:m}).
     *
     * @return the spelling.
     */
    public String text()
    {
        return text;
    }


    /**
     * Whether the primary side must carry each participating key once.
     *
     * @return {@code true} for {@code 1:1} and {@code 1:n}.
     */
    public boolean leftUnique()
    {
        return leftUnique;
    }


    /**
     * Whether the partner side must carry each participating key once.
     *
     * @return {@code true} for {@code 1:1} and {@code n:1}.
     */
    public boolean rightUnique()
    {
        return rightUnique;
    }


    /**
     * Parses an authored spelling, case-insensitively.
     *
     * @param aText
     *            one of {@code 1:1}, {@code 1:n}, {@code n:1}, {@code n:m}.
     * @return the cardinality.
     * @throws IllegalArgumentException
     *             for any other spelling.
     */
    public static Cardinality parse(String aText)
    {
        String t = aText.strip().toLowerCase(Locale.ROOT);
        for (Cardinality c : values())
        {
            if (c.text.equals(t))
            {
                return c;
            }
        }
        throw new IllegalArgumentException(
                "Unknown cardinality '" + aText + "' — expected 1:1, 1:n, n:1 or n:m");
    }
}
