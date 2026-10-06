package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * The two kinds a join key compares across (the engine's {@code ColumnTypeGate.kindOf}, the one
 * home of the numeric question, moved down for the shared matching): {@code STRING} is character,
 * {@code LONG} / {@code DOUBLE} numeric, anything else (BOOLEAN, MISSING, dates) is neither and is
 * never gated.
 */
public enum ColumnKind
{

    /** A numeric column ({@code LONG} or {@code DOUBLE}). */
    NUMERIC("Numeric"),

    /** A character column ({@code STRING}). */
    CHARACTER("Character");

    private final String text;

    ColumnKind(String aText)
    {
        text = aText;
    }


    /**
     * The kind's spelling in messages.
     *
     * @return {@code Numeric} or {@code Character}.
     */
    public String text()
    {
        return text;
    }


    /**
     * The kind of a declared column type.
     *
     * @param aType
     *            the type.
     * @return the kind, or {@code null} for a type that is neither.
     */
    public static @Nullable ColumnKind of(DataValueType aType)
    {
        if (aType == DataValueType.STRING)
        {
            return CHARACTER;
        }
        if (aType == DataValueType.LONG || aType == DataValueType.DOUBLE)
        {
            return NUMERIC;
        }
        return null;
    }


    /**
     * Whether a declared type is numeric.
     *
     * @param aType
     *            the type.
     * @return whether its kind is {@link #NUMERIC}.
     */
    public static boolean isNumeric(DataValueType aType)
    {
        return of(aType) == NUMERIC;
    }
}
