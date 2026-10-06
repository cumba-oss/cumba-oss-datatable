package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Locale;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * The CDISC standard a merge reads its {@code SUPP--} and {@code RELREC} datasets under
 * ({@code PLAN-merged-evaluation-table} §3.5, §3.6, W1 review M1 / L3). Two readings depend on it:
 *
 * <ul>
 * <li>a supplemental row naming no subject — {@code USUBJID} (and {@code POOLID}) blank — with an
 * {@code IDVAR}: under <b>SEND</b> it applies to every record with that {@code IDVAR} =
 * {@code IDVARVAL} (OD-2, SENDIG 3.1.1 sec. 8.3); under <b>SDTM</b> it qualifies nothing (JKM R6:
 * SDTM has no study-wide supplemental row);</li>
 * <li>a {@code RELREC} relationship: under <b>SEND</b> the rows sharing {@code (STUDYID, RELID)},
 * each naming its record by {@code USUBJID} or {@code POOLID} (OD-3, SENDIG 3.1.1 sec. 8.2); under
 * <b>SDTM</b> the rows sharing {@code (STUDYID, USUBJID, RELID)} (SDTMIG 3.4 sec. 8.2).</li>
 * </ul>
 *
 * <p>
 * {@link #UNKNOWN} keeps the inference W1 shipped: a dataset carrying a {@code POOLID} column is
 * read as SEND. A caller that knows the standard passes it, so the reading never depends on which
 * permissible columns a study happened to include.
 * </p>
 */
public enum MergeStandard
{

    /** SDTM (SDTMIG, its device and associated-persons supplements). */
    SDTM,

    /** SEND (SENDIG). */
    SEND,

    /** Not known: a dataset carrying {@code POOLID} is read as SEND. */
    UNKNOWN;

    /**
     * The standard a CDISC standard name selects — the run's standard as the engine derives it
     * ({@code sdtmig}, {@code sendig}, {@code adamig} …) or a Define-XML {@code def:Standards} name
     * ({@code SDTMIG}, {@code SENDIG}). {@code send…} is SEND, {@code sdtm…} is SDTM (the SDTMIG,
     * its device and associated-persons supplements); anything else — ADaM, a null or blank name —
     * is {@link #UNKNOWN}, which keeps the POOLID inference (T2 fold, H1).
     *
     * @param aStandardName
     *            the standard's name, any case; {@code null} when the caller knows none.
     * @return the standard the name selects, never null.
     */
    public static MergeStandard ofStandardName(@Nullable String aStandardName)
    {
        if (aStandardName == null)
        {
            return UNKNOWN;
        }
        String lower = aStandardName.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("send"))
        {
            return SEND;
        }
        if (lower.startsWith("sdtm"))
        {
            return SDTM;
        }
        return UNKNOWN;
    }


    /**
     * The standard a study's metadata library names — the one lookup every browser merge and the
     * RELREC dialog share ({@code PLAN-merge-followups} FU-2 / M1): the library's
     * {@link IMetadataLibrary#META_KEY_STANDARD_NAME} (a Define-XML's IG, {@code SDTMIG} /
     * {@code SENDIG}) through {@link #ofStandardName}; {@link #UNKNOWN} when no library is attached
     * or it names no standard, which keeps the POOLID inference.
     *
     * @param aLibrary
     *            the metadata library attached to the study's library, or {@code null} when none is
     *            ({@code IDataTableManager.getMetadataLibrary}).
     * @return the standard the library selects, never null.
     */
    public static MergeStandard ofLibrary(@Nullable IMetadataLibrary aLibrary)
    {
        if (aLibrary == null)
        {
            return UNKNOWN;
        }
        return ofStandardName(aLibrary.getMetaValue(IMetadataLibrary.META_KEY_STANDARD_NAME)
                .map(Object::toString).orElse(null));
    }


    /**
     * Whether a dataset is read as SEND.
     *
     * @param aCarriesPoolid
     *            whether the dataset carries a {@code POOLID} column — consulted for
     *            {@link #UNKNOWN} only.
     * @return {@code true} for SEND, {@code false} for SDTM.
     */
    public boolean isSend(boolean aCarriesPoolid)
    {
        return this == SEND || (this == UNKNOWN && aCarriesPoolid);
    }
}
