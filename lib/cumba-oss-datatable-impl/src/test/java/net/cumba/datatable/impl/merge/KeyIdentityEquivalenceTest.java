package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.cumba.datatable.AbstractDataTableColumn;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.GroupKeyPolicy;
import net.cumba.datatable.values.GroupKeyPolicy.KeyPart;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code PLAN-key-match-index-identity-rekey} §4.2 (1): the key-match index keyed on key
 * <b>identities</b> joins exactly the rows the {@link KeyPart} keys joined — two keys are one key
 * under the index <b>iff</b> their parts are equal, in both directions, single and composite.
 *
 * <p>
 * The oracle is the old definition, computed independently of the code under test: for every pair
 * of rows, {@code KEEP_MISSING_KEYS.keyPart} of both cells, projected by {@code asText()} under
 * {@code Join_As_String}, compared with {@code KeyPart.equals}; a row whose part is not
 * {@code present()} takes no part when {@code keepMissings} is off. The code under test is the real
 * path: {@link KeyCellReader#read} through {@link KeyedMatcher} and its {@link KeyMatchIndex}, over
 * every declared type × buffer arm the production loader chooses (the values are picked so the
 * factory lands them in its various buffers; this class names no buffer class, so it stays
 * byte-identical across the twins), a column of a class the reader does not know (the old path),
 * and a cell carrying a raw {@code -0.0}.
 * </p>
 */
class KeyIdentityEquivalenceTest
{

    private static final GroupKeyPolicy KEEP = GroupKeyPolicy.KEEP_MISSING_KEYS;

    /** An unrecognised NaN payload. */
    private static final double ODD_NAN = Double.longBitsToDouble(0x7ff8000000000123L);

    private static final long TWO_53 = 1L << 53;

    /** One key column: a name, a declared type and its cells. */
    private record Arm(String name, DataValueType type, List<@Nullable Object> values)
    {

        @Override
        public String toString()
        {
            return name;
        }
    }

    /**
     * Every declared type × buffer arm, with values the arms share across types ({@code 5},
     * {@code "5"}, {@code 5.0}, {@code 0}, {@code -0.0}, 2^53 and its neighbours) so the cross-type
     * and {@code Join_As_String} pairs really meet.
     */
    private static List<Arm> arms()
    {
        List<Arm> out = new ArrayList<>();
        List<@Nullable Object> manyStrings = new ArrayList<>();
        for (int i = 0; i < 40; i++)
        {
            manyStrings.add("S" + i);
        }
        manyStrings.addAll(Arrays.asList("", " ", ".", null, MissingValue.MIS_A, MissingValue.MIS,
                "NaN", "5", "0", "MIS_A", "\u0001MIS", "9007199254740993", "1.5", "S3"));
        out.add(new Arm("STRING unique", DataValueType.STRING, manyStrings));
        List<@Nullable Object> fewStrings = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            fewStrings.addAll(Arrays.asList("A", "5", "", null, MissingValue.MIS_B));
        }
        out.add(new Arm("STRING factor", DataValueType.STRING, fewStrings));
        out.add(new Arm("STRING single", DataValueType.STRING, Arrays.asList("5", "5", "5")));
        out.add(new Arm("STRING numbers", DataValueType.STRING,
                Arrays.asList(1.5, 7L, Double.NaN, ODD_NAN, 5L, 5.0, "5")));

        List<@Nullable Object> manyLongs = new ArrayList<>();
        for (long i = 0; i < 40; i++)
        {
            manyLongs.add(i * 1_000_003L);
        }
        // only values BOTH datatable twins' LONG buffers accept
        manyLongs.addAll(Arrays.asList(null, MissingValue.MIS_C, MissingValue.MIS, 7, 5L, -0.0,
                TWO_53 - 1, TWO_53, TWO_53 + 1, -(TWO_53 + 1), Long.MAX_VALUE, Long.MIN_VALUE + 1,
                1_000_003L));
        out.add(new Arm("LONG unique", DataValueType.LONG, manyLongs));
        List<@Nullable Object> fewLongs = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            fewLongs.addAll(Arrays.asList(1L, 5L, null, MissingValue.MIS, 0L));
        }
        out.add(new Arm("LONG factor", DataValueType.LONG, fewLongs));
        out.add(new Arm("LONG single", DataValueType.LONG, Arrays.asList(5L, 5L, 5L)));
        out.add(new Arm("LONG all-.A", DataValueType.LONG,
                Arrays.asList(MissingValue.MIS_A, MissingValue.MIS_A)));

        List<@Nullable Object> manyDoubles = new ArrayList<>();
        for (int i = 0; i < 40; i++)
        {
            manyDoubles.add(i * Math.PI);
        }
        manyDoubles.addAll(Arrays.asList(-0.0, 0.0, 5.0, 1.5, (double) TWO_53, 4.9999999999994,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, ODD_NAN,
                MissingValue.MIS_Z.asDouble(), MissingValue.MIS_A.asDouble(), MissingValue.MIS,
                MissingValue.MIS_D, 1e300, Double.MIN_VALUE));
        out.add(new Arm("DOUBLE unique", DataValueType.DOUBLE, manyDoubles));
        List<@Nullable Object> floats = new ArrayList<>();
        for (int i = 0; i < 40; i++)
        {
            floats.add(i + 0.5);
        }
        floats.addAll(Arrays.asList(-0.0, 5.0, Double.NaN, MissingValue.MIS, MissingValue.MIS_B));
        out.add(new Arm("DOUBLE float-exact", DataValueType.DOUBLE, floats));
        List<@Nullable Object> fewDoubles = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            fewDoubles.addAll(Arrays.asList(1.25, -0.0, 5.0, MissingValue.MIS, ODD_NAN));
        }
        out.add(new Arm("DOUBLE factor", DataValueType.DOUBLE, fewDoubles));
        out.add(new Arm("DOUBLE single", DataValueType.DOUBLE, Arrays.asList(5.0, 5.0)));
        out.add(new Arm("DOUBLE all-missing", DataValueType.DOUBLE,
                Arrays.asList(MissingValue.MIS, MissingValue.MIS)));
        out.add(new Arm("BOOLEAN", DataValueType.BOOLEAN,
                Arrays.asList(true, false, MissingValue.MIS, true)));
        return out;
    }


    /** A real one- or two-column table loaded the production way. */
    private static IDataTable table(String aName, Arm... aArms)
    {
        int rows = aArms[0].values().size();
        DataTableColumnMeta[] metas = new DataTableColumnMeta[aArms.length];
        CachedDataTableColumn[] columns = new CachedDataTableColumn[aArms.length];
        for (int c = 0; c < aArms.length; c++)
        {
            String name = c == 0 ? "K" : "J";
            metas[c] = DataTableColumnMeta.builder().index(c).name(name).label(name)
                    .type(aArms[c].type()).build();
            columns[c] = new CachedDataTableColumn(c, aArms[c].type());
            for (int r = 0; r < rows; r++)
            {
                columns[c].addElement(aArms[c].values().get(r % aArms[c].values().size()));
            }
            columns[c].complete();
        }
        return new ColumnCachedDataTable(DataTableMeta.builder().name(aName).label(aName)
                .columns(metas).rowCount(rows).totalRowCount(rows).build(), columns);
    }


    /**
     * A table whose key column is of a class the reader does not know — so every cell takes the old
     * path — answering the given data values as they are, a {@code -0.0} included (no buffer stores
     * one: {@code NZL O1}).
     */
    private static IDataTable raw(String aName, DataValueType aType, IDataValue... aCells)
    {
        return new ColumnCachedDataTable(
                DataTableMeta.builder().name(aName).label(aName).columns(new DataTableColumnMeta[]
                {
                        DataTableColumnMeta.builder().index(0).name("K").label("K").type(aType)
                                .build()
                }).rowCount(aCells.length).totalRowCount(aCells.length).build(),
                new RawColumn(aCells));
    }

    /** A small fake column: the cells it was given, unconverted. */
    private static final class RawColumn extends AbstractDataTableColumn
    {

        private final IDataValue[] cells;

        RawColumn(IDataValue... aCells)
        {
            super(0);
            cells = aCells.clone();
        }


        @Override
        public long getRowCount()
        {
            return cells.length;
        }


        @Override
        public Object getValue(long aRow)
        {
            return getDataValue(aRow).getValue();
        }


        @Override
        public IDataValue getDataValue(long aRow)
        {
            return cells[Math.toIntExact(aRow)];
        }
    }

    /** The oracle's key of one cell: the old part, projected under Join_As_String. */
    private static KeyPart part(IDataValue aCell, boolean aAsString)
    {
        KeyPart part = KEEP.keyPart(aCell);
        return aAsString ? part.asText() : part;
    }


    /**
     * The partner rows the OLD definition joins to each primary row, ascending — and the rows the
     * matcher under test joins, through its index.
     */
    private static void assertJoinsAsKeyPartsDid(String aLabel, IDataTable aPrimary,
            IDataTable aPartner, int aComponents, boolean aAsString, boolean aKeep)
    {
        KeyedMergeSpec.KeyedMergeSpecBuilder spec = KeyedMergeSpec.builder().qualifier("P")
                .joinAsString(aAsString).keepMissings(aKeep).typeChecked(false)
                .key(KeyColumn.of("K"));
        if (aComponents == 2)
        {
            spec.key(KeyColumn.of("J"));
        }
        KeyedMatcher matcher = KeyedMatcher.of(aPrimary, aPartner, spec.build());
        KeyMatchIndex index = matcher.index();
        int primaryRows = Math.toIntExact(aPrimary.getRowCount());
        int partnerRows = Math.toIntExact(aPartner.getRowCount());
        for (int a = 0; a < primaryRows; a++)
        {
            List<Integer> expected = new ArrayList<>();
            KeyPart[] probe = parts(aPrimary, a, aComponents, aAsString);
            if (aKeep || allPresent(probe))
            {
                for (int b = 0; b < partnerRows; b++)
                {
                    KeyPart[] stored = parts(aPartner, b, aComponents, aAsString);
                    if ((aKeep || allPresent(stored)) && Arrays.equals(probe, stored))
                    {
                        expected.add(b);
                    }
                }
            }
            List<Integer> actual = new ArrayList<>();
            int id = matcher.keyIdOf(a);
            if (id != KeyMatchIndex.NOT_FOUND)
            {
                for (int pos = index.start(id); pos < index.end(id); pos++)
                {
                    actual.add(index.row(pos));
                }
            }
            String where = aLabel + " asString=" + aAsString + " keep=" + aKeep + " row " + a + " "
                    + Arrays.toString(probe);
            assertEquals(expected, actual, where);
            // the concurrent probe (its own key array) answers the same id
            assertEquals(id, matcher.keyIdOf(a, matcher.newKey()), where + " (own key array)");
        }
    }


    private static KeyPart[] parts(IDataTable aTable, int aRow, int aComponents, boolean aAsString)
    {
        KeyPart[] out = new KeyPart[aComponents];
        for (int c = 0; c < aComponents; c++)
        {
            out[c] = part(aTable.getColumn(c).getDataValue(aRow), aAsString);
        }
        return out;
    }


    private static boolean allPresent(KeyPart[] aParts)
    {
        return Arrays.stream(aParts).allMatch(KeyPart::present);
    }


    /**
     * Every cell, through the reader: its identity is {@code keyIdentity}, and its
     * {@code Join_As_String} projection is {@code textKeyIdentity} — the two oracles the plan
     * names.
     */
    private static void assertReaderIdentities(String aLabel, IDataTable aTable)
    {
        KeyCellReader reader = KeyCellReader.of(aTable, 0);
        for (long r = 0; r < aTable.getRowCount(); r++)
        {
            IDataValue cell = aTable.getColumn(0).getDataValue(r);
            Object identity = reader.read(r);
            assertEquals(KEEP.keyIdentity(cell), identity, aLabel + " row " + r);
            assertEquals(KEEP.textKeyIdentity(cell), GroupKeyPolicy.asTextIdentity(identity),
                    aLabel + " row " + r + " (text)");
        }
    }


    @Test
    void everyArmPairJoinsExactlyAsTheKeyPartsDid()
    {
        List<Arm> arms = arms();
        for (Arm arm : arms)
        {
            assertReaderIdentities(arm.name(), table("T", arm));
        }
        for (Arm left : arms)
        {
            IDataTable primary = table("A", left);
            for (Arm right : arms)
            {
                IDataTable partner = table("P", right);
                for (boolean asString : new boolean[]
                {
                        false, true
                })
                {
                    for (boolean keep : new boolean[]
                    {
                            true, false
                    })
                    {
                        assertJoinsAsKeyPartsDid(left + " / " + right, primary, partner, 1,
                                asString, keep);
                    }
                }
            }
        }
    }


    /**
     * Two components, each of its own arm — a stored composite key against a probe array — on a
     * table joined with itself and with a rotated copy, so every component pair meets.
     */
    @Test
    void everyCompositeOfTwoArmsJoinsExactlyAsTheKeyPartsDid()
    {
        List<Arm> arms = arms();
        for (Arm first : arms)
        {
            for (Arm second : arms)
            {
                Arm rotated = rotate(second);
                IDataTable primary = table("A", first, second);
                IDataTable partner = table("P", first, rotated);
                for (boolean asString : new boolean[]
                {
                        false, true
                })
                {
                    for (boolean keep : new boolean[]
                    {
                            true, false
                    })
                    {
                        String label = first + " + " + second;
                        assertJoinsAsKeyPartsDid(label, primary, primary, 2, asString, keep);
                        assertJoinsAsKeyPartsDid(label + " (rotated)", primary, partner, 2,
                                asString, keep);
                    }
                }
            }
        }
    }


    private static Arm rotate(Arm aArm)
    {
        List<@Nullable Object> values = new ArrayList<>(aArm.values());
        Collections.rotate(values, -1);
        return new Arm(aArm.name() + " rotated", aArm.type(), values);
    }


    /**
     * The old path, and a raw signed zero: a column of an unknown class hands the reader a
     * {@code -0.0} no buffer stores. Its identity is the canonical {@code 0.0}, so it joins the
     * {@code 0.0}, the {@code LONG 0} and the factor's zero exactly as {@code PresentNumber} did —
     * and keyed by the raw bits it would not (the planted control of this test: an index that
     * compared {@code -0.0} and {@code 0.0} as different reds here).
     */
    @Test
    void aRawNegativeZeroIsTheZeroKey()
    {
        IDataTable signed = raw("Z", DataValueType.DOUBLE, new DataValueDouble(-0.0),
                new DataValueDouble(0.0), new DataValueDouble(5.0), new DataValueDouble(-0.0),
                new DataValueMissing(MissingValue.MIS_A), new DataValueDouble(1.5));
        // vacuity: the cell really carries the sign bit, else this test proves nothing
        Object raw = signed.getColumn(0).getDataValue(0).getValue();
        assertTrue(raw instanceof Double d && Double.doubleToRawLongBits(d) != 0L,
                "the fixture must hand the reader a raw -0.0: " + raw);
        assertReaderIdentities("raw", signed);
        for (Arm arm : arms())
        {
            IDataTable other = table("P", arm);
            for (boolean asString : new boolean[]
            {
                    false, true
            })
            {
                for (boolean keep : new boolean[]
                {
                        true, false
                })
                {
                    assertJoinsAsKeyPartsDid("raw / " + arm, signed, other, 1, asString, keep);
                    assertJoinsAsKeyPartsDid(arm + " / raw", other, signed, 1, asString, keep);
                }
            }
        }
        KeyedMatcher self = KeyedMatcher.of(signed, signed,
                KeyedMergeSpec.builder().qualifier("Z").key(KeyColumn.of("K")).build());
        int zero = self.keyIdOf(0);
        List<Integer> zeros = new ArrayList<>();
        for (int pos = self.index().start(zero); pos < self.index().end(zero); pos++)
        {
            zeros.add(self.index().row(pos));
        }
        assertEquals(List.of(0, 1, 3), zeros, "-0.0, 0.0 and -0.0 are one key");
    }


    /**
     * {@code keepMissings: false} drops exactly the rows whose key is {@code ""} or a missing value
     * — and keeps a present {@code "."}, a whitespace-only text, a zero and the text
     * {@code "MIS_A"} ({@code KeyedMatcher.keyBlankAt}, now asked of the identity).
     */
    @Test
    void dropMissingsDropsExactlyTheBlankIdentities()
    {
        IDataTable t = table("T", new Arm("mixed", DataValueType.STRING, Arrays.asList("", ".", " ",
                null, MissingValue.MIS_A, "MIS_A", "0", MissingValue.MIS)));
        KeyedMatcher matcher = KeyedMatcher.of(t, t, KeyedMergeSpec.builder().qualifier("T")
                .key(KeyColumn.of("K")).keepMissings(false).build());
        List<Integer> joined = new ArrayList<>();
        for (int r = 0; r < t.getRowCount(); r++)
        {
            if (matcher.keyIdOf(r) != KeyMatchIndex.NOT_FOUND)
            {
                joined.add(r);
            }
        }
        assertEquals(List.of(1, 2, 5, 6), joined);
        assertEquals(4, matcher.index().rowCount(), "the dropped partner rows are not indexed");
    }
}
