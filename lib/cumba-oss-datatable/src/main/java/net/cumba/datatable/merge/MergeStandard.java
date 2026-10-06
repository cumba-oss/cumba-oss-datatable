package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

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
