package net.cumba.datatable.impl.view;

/**
 * Memory-efficient hash lookup for merging two large tables. Uses open addressing (linear probing)
 * with only a single int[] array storing row indices. The hash determines the slot position
 * directly.
 * <p>
 * Designed for 50M+ entries with minimal memory footprint. At load factor 0.75 and 50M entries,
 * memory usage is ~256MB.
 * </p>
 */
public class HashLookup
{

    /**
     * Placeholder for an empty slot.
     */
    private static final int EMPTY = -1;

    /**
     * The hash table. This array contains the row indices as values at the places that are related
     * to the hash.
     */
    private final int[] table;

    /**
     * Parallel to {@link #table}: the (raw, un-mixed) 32-bit key hash stored when a row was
     * inserted. A lookup compares this against the probe hash first and only invokes the (boxing)
     * {@code matcher} when they are equal — so the costly key comparison runs at most once per true
     * hash collision, not on every slot walked during linear probing. Only read where
     * {@code table[slot] != EMPTY}, so the default-zero entries for empty slots are never observed.
     */
    private final int[] hashes;

    /**
     * The capacity (size) of the hash table {@link #table}.
     */
    private final int capacity;

    /**
     * The number of entries in the hash table {@link #table}.
     */
    private int size;

    /**
     * @param expectedEntries
     *            number of entries to be inserted
     * @param loadFactor
     *            target load factor (e.g. 0.75). Lower = fewer collisions, more memory.
     */
    public HashLookup(int expectedEntries, float loadFactor)
    {
        if (expectedEntries < 0)
        {
            throw new IllegalArgumentException("expectedEntries must be >= 0");
        }
        if (loadFactor <= 0f || loadFactor >= 1f)
        {
            throw new IllegalArgumentException("loadFactor must be in (0, 1)");
        }

        this.capacity = Math.max(16, (int) (expectedEntries / loadFactor) + 1);
        this.table = new int[capacity];
        this.hashes = new int[capacity];
        this.size = 0;
        java.util.Arrays.fill(table, EMPTY);
    }


    /**
     * Avalanche finalizer (murmur3 {@code fmix32}) applied to the caller-supplied key hash before
     * it selects a slot. The raw key hash here is built as {@code 31*h + columnHash}, which leaves
     * structured keys (small sequential integers, a constant domain column, formulaic subject ids)
     * clustered in a narrow band of the low bits. Open-addressing linear probing turns that into
     * pathological primary clustering — probe chains of thousands instead of a handful — whose cost
     * is acutely sensitive to the table capacity. Mixing the bits first spreads such keys uniformly
     * and removes the clustering (and the capacity-dependent cliff). Applied identically on
     * {@link #put} and {@link #get}, so insert and probe always agree on the slot.
     */
    private static int mix(int aHash)
    {
        int h = aHash;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }


    /**
     * Insert a row from table 1.
     *
     * @param hash32
     *            32-bit hash of the key columns
     * @param rowIndex
     *            row index in table 1 (must be >= 0)
     * @throws IllegalStateException
     *             if the table is already full, i.e. more entries are inserted than were declared
     *             via the constructor's {@code expectedEntries}. The table always keeps one empty
     *             slot so the linear probes in {@code put} and {@code get} terminate.
     */
    public void put(int hash32, int rowIndex)
    {
        if (size >= capacity - 1)
        {
            // F-dt-07: the probe loops terminate only on an EMPTY slot, so the table must always
            // keep at least one. Without this guard, one put past capacity spins forever, and a
            // get for an absent key in an exactly-full table does too. The capacity formula
            // (expectedEntries / loadFactor + 1, loadFactor < 1) guarantees room for
            // expectedEntries entries, so a caller that declared its count correctly never hits
            // this.
            throw new IllegalStateException("HashLookup is full: capacity " + capacity
                    + " cannot accept entry number " + (size + 1)
                    + " — more entries inserted than declared via expectedEntries");
        }

        int slot = Math.floorMod(mix(hash32), capacity);
        while (table[slot] != EMPTY)
        {
            slot = (slot + 1 == capacity) ? 0 : slot + 1;
        }
        table[slot] = rowIndex;
        hashes[slot] = hash32;
        size++;
    }


    /**
     * Look up the matching row from table 1 for a row in table 2.
     *
     * @param hash32
     *            32-bit hash of the key columns (same hash function as used in {@link #put})
     * @param matcher
     *            callback to confirm an exact key match between a candidate row from table 1 and
     *            the current row from table 2. Receives the row index from table 1, returns true if
     *            keys match.
     * @return the matching row index from table 1, or -1 if no match found
     */
    public int get(int hash32, RowMatcher matcher)
    {
        int slot = Math.floorMod(mix(hash32), capacity);
        while (table[slot] != EMPTY)
        {
            if (hashes[slot] == hash32 && matcher.matches(table[slot]))
            {
                return table[slot];
            }
            slot = (slot + 1 == capacity) ? 0 : slot + 1;
        }
        return -1;
    }


    /**
     * Look up the matching row from table 1 for a row in table 2.
     *
     * @param aRow2Index
     *            the index in table 2.
     * @param aHash32
     *            32-bit hash of the key columns (same hash function as used in {@link #put})
     * @param aMatcher
     *            callback to confirm an exact key match between a candidate row from table 1 and
     *            the given aRow2Index from table 2, returns true if keys match.
     * @return the matching row index from table 1, or -1 if no match found
     */
    public int get(int aRow2Index, int aHash32, BiRowMatcher aMatcher)
    {
        int slot = Math.floorMod(mix(aHash32), capacity);
        while (table[slot] != EMPTY)
        {
            if (hashes[slot] == aHash32 && aMatcher.matches(table[slot], aRow2Index))
            {
                return table[slot];
            }
            slot = (slot + 1 == capacity) ? 0 : slot + 1;
        }
        return -1;
    }


    /**
     * Returns the number of entries stored.
     *
     * @return number of entries stored
     */
    public int size()
    {
        return size;
    }


    /**
     * Returns the allocated capacity of the internal array.
     *
     * @return allocated capacity of the internal array
     */
    public int capacity()
    {
        return table.length;
    }

    /**
     * Callback interface for exact key matching.
     */
    @FunctionalInterface
    public interface RowMatcher
    {

        /**
         * Returns whether the keys of the given table 1 row match the current table 2 row.
         *
         * @param table1Row
         *            row index in table 1
         * @return true if the keys of table1Row and the current table 2 row match exactly
         */
        boolean matches(int table1Row);
    }


    /**
     * Callback interface for exact key matching.
     */
    @FunctionalInterface
    public interface BiRowMatcher
    {

        /**
         * Returns whether the keys of the given table 1 and table 2 rows match exactly.
         *
         * @param aTable1Row
         *            row index in table 1
         * @param aTable2Row
         *            row index in table 2
         * @return true if the keys of aTable1Row and the key of aTable2Row match exactly, false
         *         otherwise.
         */
        boolean matches(int aTable1Row, int aTable2Row);
    }

}
