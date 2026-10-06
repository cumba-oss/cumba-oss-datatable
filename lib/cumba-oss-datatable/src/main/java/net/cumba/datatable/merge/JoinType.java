package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Locale;

/**
 * How a merged part treats a primary row that matches no partner record: {@link #INNER} drops the
 * row from the merged table, {@link #LEFT} keeps it with every partner column reading the
 * not-supplied marker ({@link net.cumba.datatable.IDataTable#isSupplied}). Orthogonal to
 * {@link Cardinality}.
 */
public enum JoinType
{

    /** Drop an unmatched primary row. */
    INNER("inner"),

    /** Keep an unmatched primary row; its partner columns are not supplied. */
    LEFT("left");

    private final String text;

    JoinType(String aText)
    {
        text = aText;
    }


    /**
     * The authored spelling ({@code inner} / {@code left}).
     *
     * @return the spelling.
     */
    public String text()
    {
        return text;
    }


    /**
     * Parses an authored spelling, case-insensitively.
     *
     * @param aText
     *            {@code inner} or {@code left}.
     * @return the join type.
     * @throws IllegalArgumentException
     *             for any other spelling.
     */
    public static JoinType parse(String aText)
    {
        String t = aText.strip().toLowerCase(Locale.ROOT);
        for (JoinType jt : values())
        {
            if (jt.text.equals(t))
            {
                return jt;
            }
        }
        throw new IllegalArgumentException(
                "Unknown join type '" + aText + "' — expected inner or left");
    }
}
