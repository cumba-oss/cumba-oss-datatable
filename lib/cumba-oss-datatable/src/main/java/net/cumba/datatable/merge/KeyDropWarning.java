package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.text.MessageFormat;

/**
 * The one wording of a dropped key column — a key column present on <b>one</b> side of a join only
 * leaves the key (owner ruling {@code REG-Q3}, 2026-10-09; the SUPP merge's {@code MET #19} drop is
 * where the wording comes from), so one log search finds every drop: the SUPP merge, a
 * {@code Match_Datasets} key (qualified or not) and a {@code domain=} group key.
 *
 * <p>
 * {@link #TEMPLATE} is a {@link MessageFormat} pattern — {@code {0}} the partner dataset (the SUPP
 * merge: the supplemental dataset), {@code {1}} the key as authored ({@link #keyText}), {@code {2}}
 * the dataset that carries it. ⚠ It is a <b>compile-time constant</b> on purpose: {@code javac}
 * inlines it into every class that logs it, so a caller's own class file shows the wording.
 * </p>
 */
public final class KeyDropWarning
{

    /** The pattern every surface logs a one-side key drop with. */
    public static final String TEMPLATE = "{0}: key column {1} is present on one side only ({2})"
            + " and is ignored";

    private KeyDropWarning()
    {
    }


    /**
     * The key as the message names it: the one name, or {@code LEFT/RIGHT} when the two sides name
     * it differently (a sided key; a qualified one, {@code DM.RPATHCD/RPATHCD}).
     *
     * @param aLeft
     *            the record side's name
     * @param aRight
     *            the partner side's name
     * @return the key's text
     */
    public static String keyText(String aLeft, String aRight)
    {
        return aLeft.equals(aRight) ? aLeft : aLeft + "/" + aRight;
    }


    /**
     * {@link #TEMPLATE} filled in.
     *
     * @param aPartner
     *            the partner dataset
     * @param aKey
     *            the key as authored ({@link #keyText})
     * @param aCarrier
     *            the dataset that carries the column
     * @return the message
     */
    public static String format(String aPartner, String aKey, String aCarrier)
    {
        return MessageFormat.format(TEMPLATE, aPartner, aKey, aCarrier);
    }
}
