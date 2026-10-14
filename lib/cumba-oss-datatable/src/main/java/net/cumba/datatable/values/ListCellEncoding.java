package net.cumba.datatable.values;

// OSS-IDENTITY datatable-list-cell: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import org.jspecify.annotations.Nullable;

/**
 * The one documented encoding of a LIST in a single text cell or metadata value - an OSB array
 * field, a CDISC Library "Value List" or synonym list, the supplemental qualifiers a merge skipped,
 * the datasets of a report row. Owner rulings Q38-sep and Q39 (2026-10-12): <i>"both as
 * recommended"</i> - one documented encoding, the spelling the stack already uses.
 *
 * <ul>
 * <li>The elements are joined with {@value #SEPARATOR} (comma, space), in order.</li>
 * <li>No quoting and no escaping: an element is written as its text.</li>
 * <li>An empty list is the empty text {@code ""}; a one-element list is that element alone.</li>
 * <li>A {@code null} element is the empty element: {@code ["a", null, "b"]} is
 * {@code "a, , b"}.</li>
 * </ul>
 * ⚠ Named gap: an element that itself contains {@value #SEPARATOR} makes the cell ambiguous -
 * {@code ["a, b"]} and {@code ["a", "b"]} encode alike. The rulings chose the stack's spelling, not
 * an escaping scheme, and this class does not invent one. The {@code "; "} joins of report
 * diagnostics are separators of free-text sentences, not list cells, and are not this encoding.
 */
public final class ListCellEncoding
{

    /** The separator between two elements. */
    public static final String SEPARATOR = ", ";

    private ListCellEncoding()
    {
    }


    /**
     * Encodes a list as one cell value.
     *
     * @param aElements
     *            the elements, in order; a {@code null} element is the empty element.
     * @return the elements' texts joined with {@value #SEPARATOR}; {@code ""} for no element.
     */
    public static String join(Iterable<? extends @Nullable Object> aElements)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object element : aElements)
        {
            if (!first)
            {
                sb.append(SEPARATOR);
            }
            first = false;
            if (element != null)
            {
                sb.append(element);
            }
        }
        return sb.toString();
    }
}
