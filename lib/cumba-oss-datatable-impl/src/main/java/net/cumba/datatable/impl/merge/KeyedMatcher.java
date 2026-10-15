package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.CardinalityViolationException;
import net.cumba.datatable.merge.DegenerateJoinKeyException;
import net.cumba.datatable.merge.JoinKeyTypeMismatchException;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyDropWarning;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MalformedSidedKeyException;
import net.cumba.datatable.merge.UnresolvedQualifiedKeyException;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.GroupKeyPolicy;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * The keyed matching of one partner ({@code PLAN-merged-evaluation-table} §3.4): the key's shape
 * decided once from the two tables' metadata, the partner indexed once ({@link KeyMatchIndex}),
 * every primary row probed without allocation, and the declared {@link Cardinality} checked over
 * the participating rows of each side (P-16). The engine's {@code KeyMatchRowExpander.keySpec} and
 * probe, moved into the datatable layer so the engine and the data browser match through one
 * implementation.
 *
 * <p>
 * ⭐ <b>{@code REG-Q3} lives here</b> (owner, 2026-10-09; it replaced {@code JKM R7}'s one-side type
 * default). A key component joins only when <b>both</b> sides carry it: present on one side only it
 * leaves the key (logged at DEBUG with the {@link KeyDropWarning} wording — the WARNING is the
 * caller's, which knows the rule), absent on both it leaves the key silently, and with no component
 * left the merge is refused ({@link DegenerateJoinKeyException}). A qualified left name
 * ({@code DM.RPATHCD}) the primary does not carry is a chained key the caller composes (§4) and is
 * refused here; one the primary carries — a nested merged primary that already holds
 * {@code DM.RPATHCD} — is an ordinary column and keys like any other (W1 review M3).
 * </p>
 *
 * <p>
 * ⚠ <b>Thread confinement (T2 review M1).</b> The index is immutable once built, but the probes
 * that take no key array — {@link #keyIdOf(int)}, {@link #firstMatchOf(int)}, {@code matchesOf} —
 * fill ONE scratch key array owned by the matcher, so they are confined to one thread at a time
 * (the merge builder's expansion pass). A reader that may be probed concurrently — the
 * {@link KeyMatchLazyView}, read from the EDT beside an export or by the remote backend — passes
 * its own array to {@link #keyIdOf(int, Object[])} / {@link #firstMatchOf(int, Object[])}, one per
 * probe from {@link #newKey()}; the readers it shares hold no state a probe depends on (a
 * {@link KeyCellReader.Direct} counts its fall-backs for tests only; a {@link KeyCellReader.Union}
 * builds its member readers idempotently).
 * </p>
 *
 * <p>
 * Keys are held and probed as <b>key identities</b> ({@link KeyCellReader#read}, ruling
 * {@code SKI-P-R3}), never as {@code KeyPart}s: a text probe allocates nothing, and a stored text
 * key is the column's own {@code String}. A {@code Join_As_String} key is the identity's text
 * projection ({@link GroupKeyPolicy#asTextIdentity}), exactly {@code KeyPart.asText()}'s identity.
 * </p>
 */
public final class KeyedMatcher
{

    private static final Logger LOGGER = System.getLogger(KeyedMatcher.class.getName());

    private final IDataTable primary;

    private final IDataTable partner;

    private final String dataset;

    private final List<KeyColumn> keys;

    private final int[] recordColIds;

    private final boolean[] active;

    private final int nActive;

    private final int singleActive;

    private final boolean keepMissings;

    private final boolean asString;

    private final @Nullable BitSet mask;

    private final KeyMatchIndex index;

    private final @Nullable KeyCellReader[] recordReaders;

    private final Object @Nullable [] scratch;

    private KeyedMatcher(IDataTable aPrimary, IDataTable aPartner, KeyedMergeSpec aSpec,
            int[] aRecordColIds, int[] aPartnerColIds, boolean[] aActive)
    {
        primary = aPrimary;
        partner = aPartner;
        dataset = aSpec.datasetName();
        keys = aSpec.getKeys();
        recordColIds = aRecordColIds;
        active = aActive;
        keepMissings = aSpec.isKeepMissings();
        asString = aSpec.isJoinAsString();
        mask = aSpec.getPartnerMask();
        int count = 0;
        int first = -1;
        for (int i = 0; i < aActive.length; i++)
        {
            if (aActive[i])
            {
                count++;
                first = first < 0 ? i : first;
            }
        }
        nActive = count;
        singleActive = first;
        index = buildIndex(aPartner, aPartnerColIds);
        recordReaders = KeyCellReader.of(aPrimary, aRecordColIds);
        scratch = nActive > 1 ? new Object[nActive] : null;
    }


    /**
     * Decides the key's shape and indexes the partner.
     *
     * @param aPrimary
     *            the primary.
     * @param aPartner
     *            the partner.
     * @param aSpec
     *            the merge spec.
     * @return the matcher.
     * @throws MalformedSidedKeyException
     *             for an empty key.
     * @throws UnresolvedQualifiedKeyException
     *             for a qualified left name the primary does not carry.
     * @throws JoinKeyTypeMismatchException
     *             when a component is numeric on one side and character on the other (D4) and the
     *             spec neither declares {@code joinAsString} nor excludes the type check.
     * @throws DegenerateJoinKeyException
     *             when no component is present on both sides.
     */
    public static KeyedMatcher of(IDataTable aPrimary, IDataTable aPartner, KeyedMergeSpec aSpec)
    {
        String dataset = aSpec.datasetName();
        List<KeyColumn> keys = aSpec.getKeys();
        if (keys.isEmpty())
        {
            throw new MalformedSidedKeyException(dataset, "no key column is declared");
        }
        DataTableMeta pm = aPrimary.getMetaData();
        DataTableMeta cm = aPartner.getMetaData();
        int n = keys.size();
        int[] recordColIds = new int[n];
        int[] partnerColIds = new int[n];
        boolean[] active = new boolean[n];
        int nActive = 0;
        for (int i = 0; i < n; i++)
        {
            KeyColumn key = keys.get(i);
            recordColIds[i] = pm.getColumnIndex(key.left());
            if (key.leftQualifier() != null && recordColIds[i] < 0)
            {
                throw new UnresolvedQualifiedKeyException(dataset, key.left(),
                        "a chained key reads an earlier merged part; the caller composes it");
            }
            partnerColIds[i] = cm.getColumnIndex(key.right());
            boolean inPrimary = recordColIds[i] >= 0;
            boolean inPartner = partnerColIds[i] >= 0;
            // REG-Q3: a component joins only when both sides carry it; on one side only it
            // leaves the key (the caller WARNs, naming the rule), on neither it leaves silently
            active[i] = inPrimary && inPartner;
            if (!active[i])
            {
                if (inPrimary || inPartner)
                {
                    LOGGER.log(Level.DEBUG, KeyDropWarning.TEMPLATE, dataset,
                            KeyDropWarning.keyText(key.left(), key.right()),
                            inPrimary ? nameOf(aPrimary) : nameOf(aPartner));
                }
                continue;
            }
            nActive++;
            DataValueType primaryType = pm.getColumn(recordColIds[i]).getType();
            DataValueType partnerType = cm.getColumn(partnerColIds[i]).getType();
            if (aSpec.isTypeChecked() && !aSpec.isJoinAsString())
            {
                // D4-R1/R2: the join-key type identity, decided here because this is the only
                // site holding both sides' declared types. A kind of null (BOOLEAN, MISSING, ...)
                // is never a mismatch (D4-R6a: an all-NA R column is logical).
                ColumnKind primaryKind = ColumnKind.of(primaryType);
                ColumnKind partnerKind = ColumnKind.of(partnerType);
                if (primaryKind != null && partnerKind != null && primaryKind != partnerKind)
                {
                    throw new JoinKeyTypeMismatchException(dataset, key.left(), key.right(),
                            primaryKind.text(), partnerKind.text());
                }
            }
        }
        if (nActive == 0)
        {
            throw new DegenerateJoinKeyException(dataset,
                    keys.stream().map(KeyColumn::left).toList());
        }
        return new KeyedMatcher(aPrimary, aPartner, aSpec, recordColIds, partnerColIds, active);
    }


    /**
     * The partner's key index: one reader per key column, built once; a row an authored
     * {@code keepMissings: false} drops is not indexed.
     */
    private KeyMatchIndex buildIndex(IDataTable aPartner, int[] aColIds)
    {
        int rows = Math.toIntExact(aPartner.getRowCount());
        @Nullable
        KeyCellReader[] readers = KeyCellReader.of(aPartner, aColIds);
        return KeyMatchIndex.build(rows, row ->
        {
            if (!keepMissings && anyActiveKeyBlank(aPartner, aColIds, row))
            {
                return null;
            }
            if (nActive == 1)
            {
                return keyIdentity(readers, singleActive, row);
            }
            Object[] parts = new Object[nActive];
            fillKeyIdentities(readers, row, parts);
            return new KeyMatchIndex.CompositeKey(parts);
        });
    }


    /**
     * The partner's index.
     *
     * @return the index.
     */
    public KeyMatchIndex index()
    {
        return index;
    }


    /**
     * Whether a partner row takes part (its mask bit, when a mask is set).
     *
     * @param aRow
     *            the partner row.
     * @return whether it may be a partner.
     */
    public boolean partnerAllowed(int aRow)
    {
        return mask == null || mask.get(aRow);
    }


    /**
     * A key array for the probes that take one: a fresh array of the active components, or
     * {@code null} when the key has a single component and no array is needed.
     *
     * @return the array, or {@code null}.
     */
    public Object @Nullable [] newKey()
    {
        return nActive > 1 ? new Object[nActive] : null;
    }


    /**
     * The key id of a primary row in the partner's index, or {@link KeyMatchIndex#NOT_FOUND} when
     * the row matches nothing — including a row an authored {@code keepMissings: false} drops.
     * Fills the matcher's own scratch key: confined to one thread at a time.
     *
     * @param aRow
     *            the primary row.
     * @return the key id or {@code NOT_FOUND}.
     */
    public int keyIdOf(int aRow)
    {
        return keyIdOf(aRow, scratch);
    }


    /**
     * {@link #keyIdOf(int)} filling the caller's key array — the probe a concurrent reader makes.
     *
     * @param aRow
     *            the primary row.
     * @param aKey
     *            the caller's array from {@link #newKey()} ({@code null} for a single-component
     *            key).
     * @return the key id or {@code NOT_FOUND}.
     */
    public int keyIdOf(int aRow, Object @Nullable [] aKey)
    {
        if (!keepMissings && anyActiveKeyBlank(primary, recordColIds, aRow))
        {
            return KeyMatchIndex.NOT_FOUND;
        }
        if (aKey == null)
        {
            return index.find(keyIdentity(recordReaders, singleActive, aRow));
        }
        fillKeyIdentities(recordReaders, aRow, aKey);
        return index.findComposite(aKey);
    }


    /**
     * The partner rows matching a primary row, in ascending partner order, the mask applied.
     *
     * @param aRow
     *            the primary row.
     * @param aOut
     *            the list to fill (cleared first).
     */
    void matchesOf(int aRow, IntList aOut)
    {
        aOut.clear();
        int id = keyIdOf(aRow);
        if (id == KeyMatchIndex.NOT_FOUND)
        {
            return;
        }
        for (int pos = index.start(id), end = index.end(id); pos < end; pos++)
        {
            int r = index.row(pos);
            if (partnerAllowed(r))
            {
                aOut.add(r);
            }
        }
    }


    /**
     * The first partner row matching a primary row, the mask applied, or {@code -1} when none does
     * — the one row an {@code n:1} merge binds. Fills the matcher's own scratch key: confined to
     * one thread at a time.
     *
     * @param aRow
     *            the primary row.
     * @return the partner row or {@code -1}.
     */
    public int firstMatchOf(int aRow)
    {
        return firstMatchOf(aRow, scratch);
    }


    /**
     * {@link #firstMatchOf(int)} filling the caller's key array — the probe a concurrent reader
     * makes.
     *
     * @param aRow
     *            the primary row.
     * @param aKey
     *            the caller's array from {@link #newKey()} ({@code null} for a single-component
     *            key).
     * @return the partner row or {@code -1}.
     */
    public int firstMatchOf(int aRow, Object @Nullable [] aKey)
    {
        int id = keyIdOf(aRow, aKey);
        if (id == KeyMatchIndex.NOT_FOUND)
        {
            return -1;
        }
        for (int pos = index.start(id), end = index.end(id); pos < end; pos++)
        {
            int r = index.row(pos);
            if (partnerAllowed(r))
            {
                return r;
            }
        }
        return -1;
    }


    /**
     * Checks the declared cardinality over each side's participating rows (P-16): on the partner, a
     * key whose posting holds more than one allowed row; on the primary, a primary-side index of
     * the same key shape.
     *
     * @param aCardinality
     *            the declared cardinality.
     * @throws CardinalityViolationException
     *             naming the dataset, the key and the first two duplicate rows.
     */
    public void checkCardinality(Cardinality aCardinality)
    {
        if (aCardinality.rightUnique())
        {
            for (int id = 0; id < index.keyCount(); id++)
            {
                int first = -1;
                for (int pos = index.start(id), end = index.end(id); pos < end; pos++)
                {
                    int r = index.row(pos);
                    if (!partnerAllowed(r))
                    {
                        continue;
                    }
                    if (first >= 0)
                    {
                        throw new CardinalityViolationException(aCardinality, nameOf(partner),
                                keyText(index.key(id), false), first, r);
                    }
                    first = r;
                }
            }
        }
        if (aCardinality.leftUnique())
        {
            KeyMatchIndex primaryIndex = buildIndex(primary, recordColIds);
            for (int id = 0; id < primaryIndex.keyCount(); id++)
            {
                if (primaryIndex.end(id) - primaryIndex.start(id) > 1)
                {
                    int start = primaryIndex.start(id);
                    throw new CardinalityViolationException(aCardinality, nameOf(primary),
                            keyText(primaryIndex.key(id), true), primaryIndex.row(start),
                            primaryIndex.row(start + 1));
                }
            }
        }
    }


    /**
     * {@code (COL=value, ...)} over the active components, named by the given side. A stored key is
     * its identity, which is what the message always rendered ({@code KeyPart.identity()}).
     */
    private String keyText(Object aKey, boolean aLeftNames)
    {
        Object[] parts = aKey instanceof KeyMatchIndex.CompositeKey composite ? composite.parts()
                : new Object[]
                {
                        aKey
                };
        StringBuilder sb = new StringBuilder("(");
        int j = 0;
        for (int i = 0; i < keys.size(); i++)
        {
            if (!active[i])
            {
                continue;
            }
            if (j > 0)
            {
                sb.append(", ");
            }
            sb.append(aLeftNames ? keys.get(i).left() : keys.get(i).right()).append('=')
                    .append(parts[j]);
            j++;
        }
        return sb.append(')').toString();
    }


    static String nameOf(IDataTable aTable)
    {
        String name = aTable.getMetaData().getName();
        return name == null ? "?" : name;
    }


    private Object keyIdentity(@Nullable KeyCellReader[] aReaders, int aComponent, long aRow)
    {
        // an active component is present on both sides (REG-Q3), so its reader exists
        Object identity = Objects.requireNonNull(aReaders[aComponent]).read(aRow);
        // Join_As_String (D4-R3): exactly KeyPart.asText()'s identity -- only a present number is
        // projected to its text
        return asString ? GroupKeyPolicy.asTextIdentity(identity) : identity;
    }


    private void fillKeyIdentities(@Nullable KeyCellReader[] aReaders, long aRow, Object[] aOut)
    {
        int j = 0;
        for (int i = 0; i < aReaders.length; i++)
        {
            if (active[i])
            {
                aOut[j++] = keyIdentity(aReaders, i, aRow);
            }
        }
    }


    private boolean anyActiveKeyBlank(IDataTable aTable, int[] aColIds, long aRow)
    {
        for (int i = 0; i < aColIds.length; i++)
        {
            if (active[i] && keyBlankAt(aTable, aColIds[i], aRow))
            {
                return true;
            }
        }
        return false;
    }


    private static boolean keyBlankAt(IDataTable aTable, int aColId, long aRow)
    {
        if (aColId < 0 || aTable.isMissingOrNull(aRow, aColId))
        {
            return true;
        }
        IDataValue dv = aTable.getColumn(aColId).getDataValue(aRow);
        // An empty string is not "missing" to isMissingOrNull but IS blank for a keep/drop-missing
        // policy (D34 #6 rules "" and every MissingValue together): the identity of a part that is
        // not present() is "" (EMPTY) or a MissingValue (Missing), and no other identity is.
        Object identity = GroupKeyPolicy.KEEP_MISSING_KEYS.keyIdentity(dv);
        return "".equals(identity) || identity instanceof MissingValue;
    }


    /**
     * The datasets whose names this matcher reports — for messages.
     *
     * @return the partner's dataset name as the spec names it.
     */
    String dataset()
    {
        return dataset;
    }


    /**
     * The active key components, for messages.
     *
     * @return the components, left names.
     */
    List<String> activeKeyNames()
    {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++)
        {
            if (active[i])
            {
                out.add(keys.get(i).left());
            }
        }
        return out;
    }
}
