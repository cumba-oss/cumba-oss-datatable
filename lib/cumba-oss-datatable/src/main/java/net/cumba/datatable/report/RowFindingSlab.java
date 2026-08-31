package net.cumba.datatable.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Columnar storage for the per-row payload of a {@link ValidationFinding}. The rule-level variable
 * schema (column names) is held once on the parent finding; the slab stores only the sorted row
 * indices and a flat row-major matrix of values.
 *
 * <h2>Layout</h2>
 *
 * <pre>
 *   rowIndices = [r0, r1, …, rN-1]        (sorted ascending, distinct)
 *   flatValues = [ v(0,0), v(0,1), …, v(0,C-1),
 *                  v(1,0), v(1,1), …, v(1,C-1),
 *                  …,
 *                  v(N-1,0), …, v(N-1,C-1) ]
 *   variableCount = C
 * </pre>
 *
 * <p>
 * {@code flatValues.length == rowIndices.length * variableCount} is an invariant enforced at
 * construction. Values may be {@code null} (for missing per-cell data).
 * </p>
 *
 * <p>
 * Instances are immutable. {@link #equals(Object)} and {@link #hashCode()} perform a content
 * comparison.
 * </p>
 */
public final class RowFindingSlab
{

    /** Zero-row, zero-column slab — shared across findings that carry no per-row data. */
    public static final RowFindingSlab EMPTY = new RowFindingSlab(new int[0], new String[0], 0);

    private final int[] rowIndices;

    private final @Nullable String[] flatValues;

    private final int variableCount;

    @JsonCreator
    public RowFindingSlab(@JsonProperty("rowIndices") int @Nullable [] aRowIndices,
            @JsonProperty("flatValues") @Nullable String @Nullable [] aFlatValues,
            @JsonProperty("variableCount") int aVariableCount)
    {
        this.rowIndices = aRowIndices != null ? aRowIndices : new int[0];
        this.flatValues = aFlatValues != null ? aFlatValues : new String[0];
        this.variableCount = aVariableCount;
        int expected = this.rowIndices.length * this.variableCount;
        if (this.flatValues.length != expected)
        {
            throw new IllegalArgumentException(
                    "flatValues length %d does not match rowIndices.length(%d) * variableCount(%d) = %d"
                            .formatted(this.flatValues.length, this.rowIndices.length,
                                    this.variableCount, expected));
        }
    }


    /**
     * Returns the number of row occurrences stored in this slab.
     *
     * @return the number of row occurrences stored in this slab.
     */
    public int rowCount()
    {
        return rowIndices.length;
    }


    /**
     * Returns the number of variable columns represented in this slab.
     *
     * @return the number of variable columns represented in this slab (equals the length of the
     *         parent finding's {@link ValidationFinding#getVariableNames()}).
     */
    public int variableCount()
    {
        return variableCount;
    }


    /**
     * Raw access to the (sorted, distinct) row indices. The returned array is the internal storage
     * — callers MUST NOT mutate it.
     */
    @JsonProperty("rowIndices")
    public int[] rowIndices()
    {
        return rowIndices;
    }


    /**
     * Raw access to the flat value matrix. The returned array is the internal storage — callers
     * MUST NOT mutate it.
     */
    @JsonProperty("flatValues")
    public @Nullable String[] flatValues()
    {
        return flatValues;
    }


    @JsonProperty("variableCount")
    public int getVariableCount()
    {
        return variableCount;
    }


    /**
     * Returns the row index (0-based) at slab position {@code aPos}.
     *
     * @return the row index (0-based) at slab position {@code aPos}.
     */
    public int rowIndexAt(int aPos)
    {
        return rowIndices[aPos];
    }


    /**
     * Returns the value at slab position {@code aPos} for variable column {@code aVarIdx}.
     *
     * @return the value at slab position {@code aPos} for variable column {@code aVarIdx}, or
     *         {@code null} if stored as absent.
     */
    public @Nullable String valueAt(int aPos, int aVarIdx)
    {
        if (variableCount == 0)
        {
            return null;
        }
        return flatValues[aPos * variableCount + aVarIdx];
    }


    /**
     * Binary search for the given 0-based row index.
     *
     * @return slab position of the matching row, or {@code -1} if the row is not covered.
     */
    public int positionOf(int aRowIndex)
    {
        int p = Arrays.binarySearch(rowIndices, aRowIndex);
        return p < 0 ? -1 : p;
    }


    /**
     * Returns {@code true} if at least one row in the slab matches {@code aRowIndex}.
     *
     * @return {@code true} if at least one row in the slab matches {@code aRowIndex}.
     */
    public boolean containsRow(int aRowIndex)
    {
        return positionOf(aRowIndex) >= 0;
    }


    /**
     * Returns a stream of the 0-based row indices covered by this slab, in ascending order.
     *
     * @return a stream of the 0-based row indices covered by this slab, in ascending order.
     */
    public IntStream rowIndicesStream()
    {
        return Arrays.stream(rowIndices);
    }


    /**
     * Return the values at the given slab position as a name-keyed map preserving the variable
     * order from {@code aVariableNames}. The map is an ordered (insertion-order) copy.
     *
     * @param aPos
     *            slab position.
     * @param aVariableNames
     *            the variable schema from the parent {@link ValidationFinding}. Its size must equal
     *            {@link #variableCount()}.
     * @return an immutable ordered (insertion-order) map of name → value, or an empty map when
     *         {@code variableCount == 0}.
     */
    public Map<String, @Nullable String> rowValues(int aPos, @Nullable List<String> aVariableNames)
    {
        if (variableCount == 0 || aVariableNames == null || aVariableNames.isEmpty())
        {
            return Collections.emptyMap();
        }
        Map<String, @Nullable String> out = LinkedHashMap.newLinkedHashMap(variableCount);
        int base = aPos * variableCount;
        for (int c = 0; c < variableCount; c++)
        {
            out.put(aVariableNames.get(c), flatValues[base + c]);
        }
        return Collections.unmodifiableMap(out);
    }

    // ==================== equals / hashCode ====================


    @Override
    public boolean equals(Object aObj)
    {
        if (this == aObj)
        {
            return true;
        }
        if (!(aObj instanceof RowFindingSlab other))
        {
            return false;
        }
        return variableCount == other.variableCount && Arrays.equals(rowIndices, other.rowIndices)
                && Arrays.equals(flatValues, other.flatValues);
    }


    @Override
    public int hashCode()
    {
        int h = variableCount;
        h = 31 * h + Arrays.hashCode(rowIndices);
        h = 31 * h + Arrays.hashCode(flatValues);
        return h;
    }


    @Override
    public String toString()
    {
        return "RowFindingSlab(rows=%d, vars=%d)".formatted(rowIndices.length, variableCount);
    }

    // ==================== Builder ====================


    /**
     * Mutable builder for constructing slabs incrementally. Callers add (rowIndex, values[]) tuples
     * in any order; {@link Builder#build()} sorts by row index (stable for ties).
     */
    public static Builder builder(int aVariableCount)
    {
        return new Builder(aVariableCount);
    }

    public static final class Builder
    {

        private final int variableCount;

        private int[] rowIndices = new int[8];

        private @Nullable String[] flatValues;

        private int size = 0;

        Builder(int aVariableCount)
        {
            if (aVariableCount < 0)
            {
                throw new IllegalArgumentException("variableCount must be >= 0");
            }
            this.variableCount = aVariableCount;
            this.flatValues = new String[8 * Math.max(1, aVariableCount)];
        }


        /**
         * Append a row. {@code aValues} must have length == {@code variableCount} (or be
         * {@code null} / empty for {@code variableCount == 0}).
         */
        public Builder addRow(int aRowIndex, @Nullable String[] aValues)
        {
            ensureCapacity();
            rowIndices[size] = aRowIndex;
            if (variableCount > 0)
            {
                if (aValues == null || aValues.length != variableCount)
                {
                    throw new IllegalArgumentException(
                            "values length must equal variableCount=" + variableCount);
                }
                System.arraycopy(aValues, 0, flatValues, size * variableCount, variableCount);
            }
            size++;
            return this;
        }


        private void ensureCapacity()
        {
            if (size < rowIndices.length)
            {
                return;
            }
            int newCap = rowIndices.length * 2;
            rowIndices = Arrays.copyOf(rowIndices, newCap);
            if (variableCount > 0)
            {
                flatValues = Arrays.copyOf(flatValues, newCap * variableCount);
            }
        }


        /**
         * Finalise and return the slab, sorted by row index ascending (stable for ties).
         */
        public RowFindingSlab build()
        {
            if (size == 0)
            {
                if (variableCount == 0)
                {
                    return EMPTY;
                }
                return new RowFindingSlab(new int[0], new String[0], variableCount);
            }

            // Index-sort by rowIndex (stable via insertion order tiebreak through Integer sort
            // preserving original index on equal keys via Integer[].sort being stable).
            Integer[] order = new Integer[size];
            for (int i = 0; i < size; i++)
            {
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> Integer.compare(rowIndices[a], rowIndices[b]));

            int[] outRows = new int[size];
            String[] outValues = new String[size * variableCount];
            for (int i = 0; i < size; i++)
            {
                int src = order[i];
                outRows[i] = rowIndices[src];
                if (variableCount > 0)
                {
                    System.arraycopy(flatValues, src * variableCount, outValues, i * variableCount,
                            variableCount);
                }
            }
            return new RowFindingSlab(outRows, outValues, variableCount);
        }
    }
}
