package net.cumba.datatable.impl.merge;

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
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MalformedSidedKeyException;
import net.cumba.datatable.merge.UnresolvedQualifiedKeyException;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.GroupKeyPolicy;
import net.cumba.datatable.values.GroupKeyPolicy.KeyPart;
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
 * ⭐ <b>JKM R7 lives here.</b> A key component absent on both sides leaves the key; absent on one
 * side it contributes the other side's type default (the empty string for a character column, a
 * present value by D34 #1; {@code MIS} for a numeric one, since a numeric cell cannot hold
 * {@code ""}); absent everywhere the merge is refused. A qualified left name ({@code DM.RPATHCD})
 * is a chained key the caller composes (§4) and is refused here.
 * </p>
 */
public final class KeyedMatcher
{

    private final IDataTable primary;

    private final IDataTable partner;

    private final String dataset;

    private final List<KeyColumn> keys;

    private final int[] recordColIds;

    private final KeyPart[] absentPart;

    private final boolean[] active;

    private final int nActive;

    private final int singleActive;

    private final boolean keepMissings;

    private final boolean asString;

    private final @Nullable BitSet mask;

    private final KeyMatchIndex index;

    private final @Nullable KeyCellReader[] recordReaders;

    private final KeyPart @Nullable [] scratch;

    private KeyedMatcher(IDataTable aPrimary, IDataTable aPartner, KeyedMergeSpec aSpec,
            int[] aRecordColIds, int[] aPartnerColIds, KeyPart[] aAbsentPart, boolean[] aActive)
    {
        primary = aPrimary;
        partner = aPartner;
        dataset = aSpec.datasetName();
        keys = aSpec.getKeys();
        recordColIds = aRecordColIds;
        absentPart = aAbsentPart;
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
        scratch = nActive > 1 ? new KeyPart[nActive] : null;
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
     *             for a qualified left name.
     * @throws JoinKeyTypeMismatchException
     *             when a component is numeric on one side and character on the other (D4) and the
     *             spec neither declares {@code joinAsString} nor excludes the type check.
     * @throws DegenerateJoinKeyException
     *             when no component is present on either side.
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
        KeyPart[] absentPart = new KeyPart[n];
        boolean[] active = new boolean[n];
        int nActive = 0;
        for (int i = 0; i < n; i++)
        {
            KeyColumn key = keys.get(i);
            if (key.leftQualifier() != null)
            {
                throw new UnresolvedQualifiedKeyException(dataset, key.left(),
                        "a chained key reads an earlier merged part; the caller composes it");
            }
            recordColIds[i] = pm.getColumnIndex(key.left());
            partnerColIds[i] = cm.getColumnIndex(key.right());
            boolean inPrimary = recordColIds[i] >= 0;
            boolean inPartner = partnerColIds[i] >= 0;
            active[i] = inPrimary || inPartner;
            absentPart[i] = KeyPart.EMPTY;
            if (!active[i])
            {
                continue;
            }
            nActive++;
            DataValueType primaryType = inPrimary ? pm.getColumn(recordColIds[i]).getType() : null;
            DataValueType partnerType = inPartner ? cm.getColumn(partnerColIds[i]).getType() : null;
            if (aSpec.isTypeChecked() && !aSpec.isJoinAsString() && primaryType != null
                    && partnerType != null)
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
            // The present side's declared type decides what the absent side contributes.
            DataValueType type = Objects.requireNonNull(inPrimary ? primaryType : partnerType);
            absentPart[i] = ColumnKind.isNumeric(type) ? KeyPart.missing(MissingValue.MIS)
                    : KeyPart.EMPTY;
        }
        if (nActive == 0)
        {
            throw new DegenerateJoinKeyException(dataset,
                    keys.stream().map(KeyColumn::left).toList());
        }
        return new KeyedMatcher(aPrimary, aPartner, aSpec, recordColIds, partnerColIds, absentPart,
                active);
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
                return keyPart(readers, singleActive, row);
            }
            KeyPart[] parts = new KeyPart[nActive];
            fillKeyParts(readers, row, parts);
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
     * The key id of a primary row in the partner's index, or {@link KeyMatchIndex#NOT_FOUND} when
     * the row matches nothing — including a row an authored {@code keepMissings: false} drops.
     *
     * @param aRow
     *            the primary row.
     * @return the key id or {@code NOT_FOUND}.
     */
    public int keyIdOf(int aRow)
    {
        if (!keepMissings && anyActiveKeyBlank(primary, recordColIds, aRow))
        {
            return KeyMatchIndex.NOT_FOUND;
        }
        if (scratch == null)
        {
            return index.find(keyPart(recordReaders, singleActive, aRow));
        }
        fillKeyParts(recordReaders, aRow, scratch);
        return index.find(scratch);
    }


    /**
     * The partner rows matching a primary row, in ascending partner order, the mask applied.
     *
     * @param aRow
     *            the primary row.
     * @param aOut
     *            the list to fill (cleared first).
     */
    public void matchesOf(int aRow, List<Integer> aOut)
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
     * — the one row an {@code n:1} merge binds.
     *
     * @param aRow
     *            the primary row.
     * @return the partner row or {@code -1}.
     */
    public int firstMatchOf(int aRow)
    {
        int id = keyIdOf(aRow);
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


    /** {@code (COL=value, ...)} over the active components, named by the given side. */
    private String keyText(Object aKey, boolean aLeftNames)
    {
        KeyPart[] parts = aKey instanceof KeyMatchIndex.CompositeKey composite ? composite.parts()
                : new KeyPart[]
                {
                        (KeyPart) aKey
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
                    .append(parts[j].identity());
            j++;
        }
        return sb.append(')').toString();
    }


    static String nameOf(IDataTable aTable)
    {
        String name = aTable.getMetaData().getName();
        return name == null ? "?" : name;
    }


    private KeyPart keyPart(@Nullable KeyCellReader[] aReaders, int aComponent, long aRow)
    {
        KeyCellReader reader = aReaders[aComponent];
        KeyPart part = reader == null ? absentPart[aComponent] : reader.read(aRow);
        return asString ? part.asText() : part;
    }


    private void fillKeyParts(@Nullable KeyCellReader[] aReaders, long aRow, KeyPart[] aOut)
    {
        int j = 0;
        for (int i = 0; i < aReaders.length; i++)
        {
            if (active[i])
            {
                aOut[j++] = keyPart(aReaders, i, aRow);
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
        // policy (D34 #6 rules "" and every MissingValue together).
        return !GroupKeyPolicy.KEEP_MISSING_KEYS.keyPart(dv).present();
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
