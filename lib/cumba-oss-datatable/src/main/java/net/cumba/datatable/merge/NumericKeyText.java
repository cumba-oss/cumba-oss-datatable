package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The exact-decimal canonical text of a numeric join-key token — the ONE rule behind the numeric
 * arm of every text-carried foreign-key join: the RELREC {@code IDVAR} value (the record-level
 * {@code IDVAR == IDVARVAL} filter and the dataset-level equi-join), the {@code SUPP--} /
 * {@code CO} / {@code RELREC} child pointer, and the supplemental qualifier's {@code IDVARVAL} (MET
 * #11, #14). Ruled by {@code PLAN-relrec-idvar-key-precision} T1-1 (a), owner 2026-09-25: <i>"One
 * lossless rule for both sites"</i>; moved into the datatable layer by
 * {@code PLAN-merged-evaluation-table} W1 so the engine, the data browser and the shared matching
 * import one class instead of carrying mirrors. ⚠ This class decides only what a <b>numeric</b>
 * token canonicalises to; what a caller does with a token that is <b>not</b> a number is that
 * caller's rule.
 *
 * <p>
 * A token spelt with ASCII digits, an optional sign, an optional fraction and an optional exponent
 * ({@link BigDecimal#BigDecimal(String)}'s decimal grammar, nothing else — no {@code d}/{@code f}
 * suffix, no hexadecimal float, no {@code NaN}/{@code Infinity}, no non-ASCII digit; all of which
 * pass through as text) canonicalises to its exact value with the trailing fractional zeros
 * removed, in plain notation: {@code "1"}, {@code "1.0"}, {@code "01"} and {@code "1.00"} are
 * {@code "1"}; {@code "5.0E-4"} is {@code "0.0005"}; {@code "1e20"} is
 * {@code "100000000000000000000"}; {@code "-0"} is {@code "0"}. Every digit is kept, which is why
 * this replaced {@code Double.parseDouble}: {@code 9007199254740993} and {@code 9007199254740992}
 * are one {@code double} and used to be one key (two different values must not share a key). So
 * {@code "1"}, {@code "1.0"} and a numeric {@code 1} still join, and the canonical text of a DOUBLE
 * cell's own clean text is that text again, so a cell and an authored {@code IDVARVAL} meet in one
 * form.
 * </p>
 *
 * <p>
 * ⚠ Beyond a {@code |scale|} of {@link #MAX_PLAIN_SCALE} the canonical text is
 * {@link BigDecimal#toString()} (scientific) instead of plain: {@code "1E999999999"} in one cell
 * would otherwise build a string of up to {@code 2^31} characters. The bound lies past the
 * {@code double} range, and the mapping stays injective on the stripped value: both arms render the
 * EXACT decimal value, so two different stripped values never share a text. An exponent
 * {@code BigDecimal} itself cannot hold is text.
 * </p>
 */
public final class NumericKeyText
{

    /**
     * Beyond this {@code |scale|} the canonical text is scientific, never plain — and a canonical
     * integer longer than this takes the {@link BigDecimal} arm too, so that every spelling of one
     * value lands in the same arm.
     */
    public static final int MAX_PLAIN_SCALE = 400;

    private NumericKeyText()
    {
    }


    /**
     * Canonicalises one join-key token.
     *
     * @param aToken
     *            the token, already stripped of surrounding whitespace.
     * @return its canonical decimal text, or {@code null} when it is not a decimal number.
     */
    public static @Nullable String canonicalOrNull(String aToken)
    {
        if (aToken.length() <= MAX_PLAIN_SCALE && isCanonicalInteger(aToken))
        {
            // The common case (--SEQ values): already canonical, no BigDecimal. Bounded by the
            // plain limit, so an integer with more than MAX_PLAIN_SCALE trailing zeros takes the
            // scientific arm like every other spelling of its value ("1E401" and "1" followed by
            // 401 zeros are one key).
            return aToken;
        }
        if (!isDecimalSpelling(aToken))
        {
            // A text id with any character outside [0-9+-.eE] ("L1", "AE-001") never reaches the
            // parser; one that is spelt only of those ("1-2") still throws once per row -- an
            // accepted cost, such ids are rare.
            return null;
        }
        try
        {
            BigDecimal bd = new BigDecimal(aToken).stripTrailingZeros();
            return bd.scale() > MAX_PLAIN_SCALE || bd.scale() < -MAX_PLAIN_SCALE ? bd.toString()
                    : bd.toPlainString();
        }
        catch (NumberFormatException | ArithmeticException _)
        {
            return null; // not a decimal number, or an exponent BigDecimal cannot represent
        }
    }


    /**
     * ASCII digits, {@code +}, {@code -}, {@code .}, {@code e} and {@code E} only, at least one
     * character. A superset of the decimal grammar (the parser still decides), a strict subset of
     * what {@link BigDecimal} would accept: it also takes non-ASCII digits such as fullwidth
     * {@code "１２"}, which are not a decimal spelling of a key.
     *
     * @param aToken
     *            the token.
     * @return whether it is spelt of decimal characters only.
     */
    public static boolean isDecimalSpelling(String aToken)
    {
        int n = aToken.length();
        if (n == 0)
        {
            return false;
        }
        for (int j = 0; j < n; j++)
        {
            char c = aToken.charAt(j);
            if ((c < '0' || c > '9') && c != '+' && c != '-' && c != '.' && c != 'e' && c != 'E')
            {
                return false;
            }
        }
        return true;
    }


    /**
     * An optional {@code -}, then ASCII digits with no leading zero — or {@code "0"} alone.
     * Everything else that might still be a number ({@code "-0"}, {@code "01"}, a fraction, an
     * exponent) takes the {@link BigDecimal} path, which yields the same text this fast path
     * returns unchanged.
     */
    private static boolean isCanonicalInteger(String aToken)
    {
        int n = aToken.length();
        int i = n > 0 && aToken.charAt(0) == '-' ? 1 : 0;
        if (i >= n)
        {
            return false;
        }
        if (aToken.charAt(i) == '0')
        {
            return n == 1;
        }
        for (int j = i; j < n; j++)
        {
            char c = aToken.charAt(j);
            if (c < '0' || c > '9')
            {
                return false;
            }
        }
        return true;
    }
}
