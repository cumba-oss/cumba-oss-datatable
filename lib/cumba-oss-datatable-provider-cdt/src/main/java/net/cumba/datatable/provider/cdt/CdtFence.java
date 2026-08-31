package net.cumba.datatable.provider.cdt;

/**
 * Helpers for the CDT dash-line fence. A fence is a line consisting of three or more dashes and
 * nothing else (after trimming surrounding whitespace). The same fence line is used to both open
 * and close a data block; two fences match only when their trimmed contents are byte-identical.
 *
 * <p>
 * The matching rule lets authors escape literal {@code ---} values in a single-column dataset by
 * opening with a longer fence such as {@code -----}: a data row of {@code ---} does not match the
 * opener and therefore stays as data.
 * </p>
 */
public final class CdtFence
{

    /** The shortest permitted fence. */
    public static final String DEFAULT = "---";

    private CdtFence()
    {
    }


    /**
     * Tests whether the given line is a fence.
     *
     * @param aTrimmedLine
     *            a line with surrounding whitespace already trimmed.
     * @return {@code true} if the line is three or more dashes and nothing else.
     */
    public static boolean isFence(String aTrimmedLine)
    {
        if (aTrimmedLine == null || aTrimmedLine.length() < 3)
        {
            return false;
        }
        for (int i = 0; i < aTrimmedLine.length(); i++)
        {
            if (aTrimmedLine.charAt(i) != '-')
            {
                return false;
            }
        }
        return true;
    }


    /**
     * Tests whether the given candidate line is a fence that matches a previously captured opener.
     * Both arguments must already be trimmed.
     *
     * @param aOpener
     *            the fence line that opened the data block.
     * @param aCandidate
     *            a candidate line inside the data block.
     * @return {@code true} if the candidate is a fence of the same length as the opener.
     */
    public static boolean matches(String aOpener, String aCandidate)
    {
        if (aOpener == null || aCandidate == null)
        {
            return false;
        }
        if (!isFence(aOpener) || !isFence(aCandidate))
        {
            return false;
        }
        return aOpener.equals(aCandidate);
    }
}
