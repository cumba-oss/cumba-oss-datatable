package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Arrays;
import java.util.function.IntFunction;

import org.jspecify.annotations.Nullable;

/**
 * The partner side of a keyed merge: every distinct join key, and for each key the partner rows
 * carrying it, in ascending row order. The engine's {@code KeyMatchIndex}, moved into the datatable
 * layer by {@code PLAN-merged-evaluation-table} W1 so the engine and the data browser match through
 * one implementation (MET #10).
 *
 * <p>
 * ⭐ <b>Shaped for memory, because it is shared.</b> One index may serve every rule that joins the
 * same partner table on the same key shape, across rules running in parallel, so it is built for a
 * small resident footprint rather than for convenience ({@code PLAN-keymatch-shared-join-index}
 * D7):
 * </p>
 * <ul>
 * <li>an <b>open-addressing</b> table maps a key to a dense key id — no entry objects, no boxing;
 * </li>
 * <li>the rows are held <b>CSR</b>-style: {@code rows} lists every participating child row grouped
 * by key id, and {@code offsets[id] .. offsets[id + 1]} is that key's slice. Within a slice the
 * rows are ascending, which is the order the previous {@code Map<List<KeyPart>, List<Long>>}
 * yielded;</li>
 * <li>a key is the component's <b>key identity</b> ({@code GroupKeyPolicy.keyIdentity}: the cell's
 * own {@code String}, {@code ""}, the {@code MissingValue} constant, a canonical {@code Double}, or
 * a {@code Long} no {@code double} holds) for a one-component join, and a {@link CompositeKey} of
 * identities for a multi-component one — never a {@code List}, and no {@code KeyPart} wrapper
 * ({@code PLAN-key-match-index-identity-rekey}, ruling {@code SKI-P-R3});</li>
 * <li>a lookup allocates nothing: {@link #findComposite(Object[])} compares a caller-owned scratch
 * array against the stored composite keys without building one.</li>
 * </ul>
 *
 * <p>
 * Key identity is exactly {@code KeyPart.equals}, component-wise — the identity the
 * {@code List<KeyPart>} key had: the identity object is in bijection with {@code KeyPart}
 * ({@code KeyPart.identity()} / {@code KeyPart.ofIdentity}, pinned by
 * {@code GroupKeyPolicyIdentityTest}), so {@code Object.equals} on identities is
 * {@code KeyPart.equals} on parts. ⚠ That holds only for CANONICAL identities — {@code -0.0} is
 * {@code 0.0}, never a {@code NaN} — which every producer guarantees ({@code GroupKeyPolicy}'s
 * {@code keyIdentity} / {@code numberKeyIdentity}); {@code Double.equals} compares bits. Row ids
 * are {@code int}: the key-match path assumes a table fits in an {@code int} row count and guards
 * that with {@link Math#toIntExact}.
 * </p>
 *
 * <p>
 * Immutable once built, and no array is ever exposed, so an index is safe to share between threads
 * once safely published (the cache publishes it through a {@code ConcurrentHashMap}).
 * </p>
 */
public final class KeyMatchIndex
{

    /** Returned by the {@code find} methods for a key the child does not carry. */
    public static final int NOT_FOUND = -1;

    private static final int MIN_CAPACITY = 16;

    /** Slot table: the key stored in each slot, {@code null} for an empty slot. */
    private final @Nullable Object[] slotKeys;

    /** Slot table: the dense key id of the key in the same slot. */
    private final int[] slotIds;

    private final int slotMask;

    /** {@code offsets[id] .. offsets[id + 1]} is key {@code id}'s slice of {@link #rows}. */
    private final int[] offsets;

    /** Every participating partner row, grouped by key id, ascending within a key. */
    private final int[] rows;

    private KeyMatchIndex(@Nullable Object[] aSlotKeys, int[] aSlotIds, int[] aOffsets, int[] aRows)
    {
        slotKeys = aSlotKeys;
        slotIds = aSlotIds;
        slotMask = aSlotKeys.length - 1;
        offsets = aOffsets;
        rows = aRows;
    }


    /**
     * Builds the index in two passes. The first computes each row's key <b>once</b>, assigns key
     * ids and counts the rows per key; the second lays the rows out by key. The only temporary is
     * one {@code int} per row.
     *
     * @param aRowCount
     *            the partner's row count.
     * @param aKeyOfRow
     *            the key of a row — a key identity or a {@link CompositeKey} of identities, the
     *            same kind for every row — or {@code null} when the row does not take part in the
     *            join. Called exactly once per row, in ascending row order.
     * @return the index, never {@code null}.
     */
    public static KeyMatchIndex build(int aRowCount, IntFunction<@Nullable Object> aKeyOfRow)
    {
        KeyTable table = new KeyTable();
        int[] rowKey = new int[aRowCount];
        for (int r = 0; r < aRowCount; r++)
        {
            Object key = aKeyOfRow.apply(r);
            rowKey[r] = key == null ? NOT_FOUND : table.idOf(key);
        }
        int keyCount = table.size;
        int[] offsets = new int[keyCount + 1];
        for (int id = 0; id < keyCount; id++)
        {
            offsets[id + 1] = offsets[id] + table.counts[id];
        }
        int[] rows = new int[offsets[keyCount]];
        int[] cursor = Arrays.copyOf(offsets, keyCount);
        for (int r = 0; r < aRowCount; r++)
        {
            int id = rowKey[r];
            if (id != NOT_FOUND)
            {
                rows[cursor[id]++] = r;
            }
        }
        return new KeyMatchIndex(table.keys, table.ids, offsets, rows);
    }


    /**
     * The id of a one-component key.
     *
     * @param aKey
     *            the key identity.
     * @return its key id, or {@link #NOT_FOUND}.
     */
    public int find(Object aKey)
    {
        for (int slot = spread(aKey.hashCode()) & slotMask;; slot = (slot + 1) & slotMask)
        {
            Object stored = slotKeys[slot];
            if (stored == null)
            {
                return NOT_FOUND;
            }
            if (stored.equals(aKey))
            {
                return slotIds[slot];
            }
        }
    }


    /**
     * The id of a multi-component key, given as a scratch array the caller owns and may reuse — no
     * {@link CompositeKey} is built for the lookup. Its own name, not an overload of
     * {@link #find(Object)}: an {@code Object[]} probe handed to {@code find} would silently take
     * the single-component path and answer {@link #NOT_FOUND} (PACK G 18 review).
     *
     * @param aParts
     *            the key's component identities, in key order.
     * @return its key id, or {@link #NOT_FOUND}.
     */
    public int findComposite(Object[] aParts)
    {
        int hash = Arrays.hashCode(aParts);
        for (int slot = spread(hash) & slotMask;; slot = (slot + 1) & slotMask)
        {
            Object stored = slotKeys[slot];
            if (stored == null)
            {
                return NOT_FOUND;
            }
            if (stored instanceof CompositeKey composite && composite.matches(hash, aParts))
            {
                return slotIds[slot];
            }
        }
    }


    /** Returns the first position of key {@code aId}'s slice, for {@link #row}. */
    public int start(int aId)
    {
        return offsets[aId];
    }


    /** Returns one past the last position of key {@code aId}'s slice. */
    public int end(int aId)
    {
        return offsets[aId + 1];
    }


    /** Returns the partner row at slice position {@code aPos}. */
    public int row(int aPos)
    {
        return rows[aPos];
    }


    /** Returns the number of distinct keys. */
    public int keyCount()
    {
        return offsets.length - 1;
    }


    /**
     * Returns the stored key of key {@code aId} — a key identity or a {@link CompositeKey} — by
     * scanning the slot table; for messages, never for a per-row path.
     *
     * @param aId
     *            a key id.
     * @return the key.
     */
    public Object key(int aId)
    {
        for (int slot = 0; slot < slotKeys.length; slot++)
        {
            Object stored = slotKeys[slot];
            if (stored != null && slotIds[slot] == aId)
            {
                return stored;
            }
        }
        throw new IllegalArgumentException("No key with id " + aId);
    }


    /**
     * Returns the number of participating partner rows (a row dropped by
     * {@code keepMissings: false} is not one).
     */
    public int rowCount()
    {
        return rows.length;
    }


    private static int spread(int aHash)
    {
        int h = aHash * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    /**
     * A multi-component join key: the component identities in key order and their hash, computed
     * once. The hash is {@link Arrays#hashCode(Object[])} of the components, so
     * {@link #findComposite(Object[])} can compute it from a scratch array. Equality is
     * component-wise {@code Object.equals} on the identities — {@code KeyPart.equals} by the
     * bijection.
     *
     * <p>
     * ⚠ A class and not a record: a record's generated {@code equals} would compare the array by
     * identity (Error Prone {@code ArrayRecordComponent}).
     * </p>
     */
    public static final class CompositeKey
    {

        private final Object[] parts;

        private final int hash;

        /**
         * @param aParts
         *            the component identities; the array is owned by the key from here on and must
         *            not be modified.
         */
        CompositeKey(Object[] aParts)
        {
            parts = aParts;
            hash = Arrays.hashCode(aParts);
        }


        private boolean matches(int aHash, Object[] aParts)
        {
            return hash == aHash && Arrays.equals(parts, aParts);
        }


        /**
         * The component identities, in key order — a copy, for messages.
         *
         * @return the identities.
         */
        public Object[] parts()
        {
            return parts.clone();
        }


        @Override
        public boolean equals(@Nullable Object aOther)
        {
            return aOther instanceof CompositeKey other && other.matches(hash, parts);
        }


        @Override
        public int hashCode()
        {
            return hash;
        }


        @Override
        public String toString()
        {
            return Arrays.toString(parts);
        }
    }


    /** The growing key table used while building; frozen into the index afterwards. */
    private static final class KeyTable
    {

        private @Nullable Object[] keys = new Object[MIN_CAPACITY];

        private int[] ids = new int[MIN_CAPACITY];

        private int[] counts = new int[MIN_CAPACITY];

        private int size;

        /** The key's id, assigning the next one on first sight, and counts one row for it. */
        private int idOf(Object aKey)
        {
            int mask = keys.length - 1;
            int slot = spread(aKey.hashCode()) & mask;
            while (true)
            {
                Object stored = keys[slot];
                if (stored == null)
                {
                    break;
                }
                if (stored.equals(aKey))
                {
                    counts[ids[slot]]++;
                    return ids[slot];
                }
                slot = (slot + 1) & mask;
            }
            int id = size++;
            keys[slot] = aKey;
            ids[slot] = id;
            if (id == counts.length)
            {
                counts = Arrays.copyOf(counts, counts.length * 2);
            }
            counts[id] = 1;
            if (size * 2 > keys.length)
            {
                grow();
            }
            return id;
        }


        /** Doubles the slot table, keeping the load factor at or below one half. */
        private void grow()
        {
            @Nullable
            Object[] oldKeys = keys;
            int[] oldIds = ids;
            keys = new Object[oldKeys.length * 2];
            ids = new int[oldKeys.length * 2];
            int mask = keys.length - 1;
            for (int i = 0; i < oldKeys.length; i++)
            {
                Object key = oldKeys[i];
                if (key == null)
                {
                    continue;
                }
                int slot = spread(key.hashCode()) & mask;
                while (keys[slot] != null)
                {
                    slot = (slot + 1) & mask;
                }
                keys[slot] = key;
                ids[slot] = oldIds[i];
            }
        }
    }
}
