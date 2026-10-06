package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import org.jspecify.annotations.Nullable;

/**
 * The text form two sides of a text-carried foreign-key join are compared in — the supplemental
 * qualifier's {@code IDVARVAL} against the parent column {@code IDVAR} names, the child pointer,
 * the RELREC link value (MET #11, #14; the engine's former
 * {@code ChildMatchIndex.normalizeJoinToken} and {@code RelrecRowExpander.normKey}, moved down by
 * {@code PLAN-merged-evaluation-table} W1).
 *
 * <p>
 * Both sides are {@link String#strip() stripped}; for a <b>numeric</b> parent variable both are
 * canonicalised by {@link NumericKeyText} ({@code "1"}, {@code "01"}, {@code "1.0"}, {@code "1E0"},
 * {@code "+1"} → {@code "1"}); a character parent compares the stripped text exactly. A token that
 * is not a number under a numeric parent keeps its stripped text, so it can only match itself. One
 * rule for the three joins: the RELREC link used to canonicalise any numeric-looking text whatever
 * the column's type ({@code relrecKey}, retired by the T2 fold, L1) — it now normalises by the
 * {@code IDVAR} column's type on each side like the other two.
 * </p>
 */
public final class JoinToken
{

    private JoinToken()
    {
    }


    /**
     * The comparison token of a raw cell text.
     *
     * @param aRaw
     *            the raw text, or {@code null} for a missing cell.
     * @param aNumeric
     *            whether the parent variable is numeric.
     * @return the token, or {@code null} for a missing input.
     */
    public static @Nullable String normalize(@Nullable String aRaw, boolean aNumeric)
    {
        if (aRaw == null)
        {
            return null;
        }
        String stripped = aRaw.strip();
        if (!aNumeric)
        {
            return stripped;
        }
        String canonical = NumericKeyText.canonicalOrNull(stripped);
        return canonical != null ? canonical : stripped;
    }
}
