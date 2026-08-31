package net.cumba.datatable.provider.cdt;

import org.jspecify.annotations.Nullable;

/**
 * The column value types supported by the CDT text format.
 */
public enum CdtType
{

    CHAR, NUM, DATE, TIME, DATETIME;

    /**
     * Parse a type token as written on a {@code col} line (case-insensitive, with a few synonyms
     * accepted). Returns {@code null} if the token is unknown — callers decide how to report the
     * error.
     *
     * @param aToken
     *            the raw token from the file (e.g. "Char", "num", "DateTime").
     * @return the matching {@link CdtType}, or {@code null}.
     */
    public static @Nullable CdtType parse(@Nullable String aToken)
    {
        if (aToken == null)
        {
            return null;
        }
        return switch (aToken)
        {
        case "Char", "char", "CHAR", "String", "string" -> CHAR;
        case "Num", "num", "NUM", "Number", "number", "Numeric", "numeric" -> NUM;
        case "Date", "date", "DATE" -> DATE;
        case "Time", "time", "TIME" -> TIME;
        case "DateTime", "datetime", "DATETIME" -> DATETIME;
        default -> null;
        };
    }


    /**
     * The token written on the {@code col} line for this type.
     */
    public String token()
    {
        return switch (this)
        {
        case CHAR -> "Char";
        case NUM -> "Num";
        case DATE -> "Date";
        case TIME -> "Time";
        case DATETIME -> "DateTime";
        };
    }
}
