package net.cumba.datatable.merge;

import java.util.Locale;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The naming rule applied when a merged table is built (MET #23): the shared matching produces the
 * row maps, the naming decides what the partner columns are called. Two rules exist, the engine's
 * {@link #QUALIFIED} and the data browser's {@link #BROWSER} ({@code PLAN-merged-evaluation-table}
 * §3.2):
 *
 * <table>
 * <caption>The two naming rules</caption>
 * <tr>
 * <th>source</th>
 * <th>{@code QUALIFIED}</th>
 * <th>{@code BROWSER}</th>
 * </tr>
 * <tr>
 * <td>keyed partner {@code Name: DM}</td>
 * <td>{@code DM.AGE}</td>
 * <td>{@code AGE}; a column the table already carries (the keys) is left out, as the browser's
 * key-table alignment always did; a clash with another merged part is refused</td>
 * </tr>
 * <tr>
 * <td>{@code Child} parent</td>
 * <td>{@code AE.AESEQ} (the parent's domain)</td>
 * <td>as the keyed partner</td>
 * </tr>
 * <tr>
 * <td>RELREC related domain</td>
 * <td>{@code LB.EPOCH}</td>
 * <td>{@code EPOCH}; {@code LB_EPOCH} (then {@code _2}, {@code _3}) on a clash; STUDYID / USUBJID /
 * DOMAIN dropped</td>
 * </tr>
 * <tr>
 * <td>SUPP qualifier of the primary</td>
 * <td>bare {@code QNAM}</td>
 * <td>bare {@code QNAM}</td>
 * </tr>
 * <tr>
 * <td>SUPP qualifier of a joined dataset (MQ5)</td>
 * <td>{@code DM.RACEOTH}</td>
 * <td>—</td>
 * </tr>
 * </table>
 */
public interface MergeNaming
{

    /**
     * The engine's naming: every merged name except a primary SUPP qualifier carries a {@code .},
     * and no CDISC variable name does, so a bare name can reach only the primary's own columns and
     * its SUPP qualifiers. A clash is refused ({@link MergeColumnClashException}): one qualifier
     * served by two sources is a declaration error (P-14).
     */
    MergeNaming QUALIFIED = new MergeNaming()
    {

        @Override
        public String columnName(MergeKind aKind, String aQualifier, String aSourceColumn,
                Predicate<String> aTaken)
        {
            return aQualifier + "." + aSourceColumn;
        }


        @Override
        public String suppColumnName(String aQualifier, boolean aPrimaryBase, String aQnam)
        {
            return aPrimaryBase ? aQnam : aQualifier + "." + aQnam;
        }


        @Override
        public String onClash(MergeKind aKind, String aQualifier, String aName,
                Predicate<String> aUsed)
        {
            throw new MergeColumnClashException(aName, aQualifier);
        }
    };

    /**
     * The data browser's naming, SAS / XPT-exportable: bare names, a keyed or supplemental clash
     * refused as {@code MergeDataTable} always did, the RELREC-only rename rule on a related
     * domain's clash, and the structural keys a related domain shares with the base dropped.
     */
    MergeNaming BROWSER = new MergeNaming()
    {

        @Override
        public @Nullable String columnName(MergeKind aKind, String aQualifier, String aSourceColumn,
                Predicate<String> aTaken)
        {
            if (aKind == MergeKind.RELREC)
            {
                return "STUDYID".equalsIgnoreCase(aSourceColumn)
                        || "USUBJID".equalsIgnoreCase(aSourceColumn)
                        || "DOMAIN".equalsIgnoreCase(aSourceColumn) ? null : aSourceColumn;
            }
            // Merge DM / ADSL: a partner column the table already carries (the keys first of
            // all) is left out, as alignKeyTableForMerge always did.
            return aTaken.test(aSourceColumn) ? null : aSourceColumn;
        }


        @Override
        public String suppColumnName(String aQualifier, boolean aPrimaryBase, String aQnam)
        {
            return aQnam;
        }


        @Override
        public String onClash(MergeKind aKind, String aQualifier, String aName,
                Predicate<String> aUsed)
        {
            if (aKind != MergeKind.RELREC)
            {
                throw new MergeColumnClashException(aName, aQualifier);
            }
            String prefixed = aQualifier + "_" + aName;
            if (!aUsed.test(prefixed))
            {
                return prefixed;
            }
            int k = 2;
            String candidate = prefixed + "_" + k;
            while (aUsed.test(candidate))
            {
                k++;
                candidate = prefixed + "_" + k;
            }
            return candidate;
        }
    };

    /**
     * The merged name of a partner, parent or related column, or {@code null} to leave the column
     * out of the merged table.
     *
     * @param aKind
     *            the part's kind ({@code KEYED}, {@code CHILD} or {@code RELREC}).
     * @param aQualifier
     *            the part's qualifier.
     * @param aSourceColumn
     *            the column's name in its source dataset.
     * @param aTaken
     *            whether a name is a column of the primary dataset itself (compared as the merged
     *            table compares names); a name taken by another merged part is a clash, decided by
     *            {@link #onClash}.
     * @return the merged name, or {@code null}.
     */
    @Nullable
    String columnName(MergeKind aKind, String aQualifier, String aSourceColumn,
            Predicate<String> aTaken);


    /**
     * The merged name of a supplemental qualifier.
     *
     * @param aQualifier
     *            the qualifier of the dataset the SUPP qualifies (the primary's dataset name, or a
     *            keyed entry's name).
     * @param aPrimaryBase
     *            whether the SUPP qualifies the primary (bare {@code QNAM}, MET #1).
     * @param aQnam
     *            the {@code QNAM}.
     * @return the merged name.
     */
    String suppColumnName(String aQualifier, boolean aPrimaryBase, String aQnam);


    /**
     * The name to use when the proposed merged name is already taken, or a
     * {@link MergeColumnClashException} when a clash is an error under this naming.
     *
     * @param aKind
     *            the part's kind.
     * @param aQualifier
     *            the part's qualifier.
     * @param aName
     *            the clashing name.
     * @param aUsed
     *            whether a candidate name is already taken (compared as the merged table compares
     *            names).
     * @return a free name.
     * @throws MergeColumnClashException
     *             when the clash is refused.
     */
    String onClash(MergeKind aKind, String aQualifier, String aName, Predicate<String> aUsed)
        throws MergeColumnClashException;


    /**
     * The name of a part's {@code _matched_} flag column.
     *
     * @param aQualifier
     *            the part's qualifier.
     * @return {@code <qualifier>._matched_}.
     */
    default String matchedFlagName(String aQualifier)
    {
        return aQualifier + "." + MergeMeta.MATCHED_FLAG;
    }


    /**
     * The key two names are compared under: lower-cased, since the merged table compares column
     * names case-insensitively (MET #6).
     *
     * @param aName
     *            a column name.
     * @return its comparison key.
     */
    static String nameKey(String aName)
    {
        return aName.toLowerCase(Locale.ROOT);
    }
}
