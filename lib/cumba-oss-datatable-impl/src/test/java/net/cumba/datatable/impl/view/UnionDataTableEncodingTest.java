package net.cumba.datatable.impl.view;

import static net.cumba.datatable.impl.provider.DataTableMetaSupport.ENCODING_MIXED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.ENCODING_SOURCE_CONFIGURED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.ENCODING_SOURCE_DECLARED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.ENCODING_SOURCE_DEFAULT;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.ENCODING_SOURCE_FORMAT;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_ENCODING;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_ENCODING_SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The {@code Encoding} / {@code EncodingSource} pair a split-domain {@link UnionDataTable} carries
 * (owner ruling B2-Q4 ⓐ, 2026-10-16): the members' agreed encoding, {@code MIXED} when they differ,
 * none when any member has none (both keys or neither); the source agreed, else the least certain
 * present ({@code DEFAULT} &lt; {@code CONFIGURED} &lt; {@code FORMAT} &lt; {@code DECLARED}).
 *
 * <p>
 * Why it matters: the rule engine evaluates sub-contexts over a split domain and counts bytes in
 * the evaluated table's {@code Encoding}, so a union without one counts nothing where its members
 * would. Shared byte for byte with the internal twin.
 * </p>
 */
class UnionDataTableEncodingTest
{

    /**
     * A one-row member with one STRING column {@code COL} and, when given, the encoding pair.
     */
    private static IDataTable member(String aName, String aColumn, @Nullable String aEncoding,
            @Nullable String aSource)
    {
        CachedDataTableColumn column = new CachedDataTableColumn(0, DataValueType.STRING);
        column.addElement("x");
        column.complete();
        DataTableMeta.DataTableMetaBuilder mb = DataTableMeta.builder().name(aName)//
                .columns(new DataTableColumnMeta[]
                {
                        DataTableColumnMeta.builder().index(0).name(aColumn)
                                .type(DataValueType.STRING).build()
                })//
                .rowCount(1).totalRowCount(1)//
                .addMetaData(META_KEY_ENCODING, aEncoding)//
                .addMetaData(META_KEY_ENCODING_SOURCE, aSource);
        return new ColumnCachedDataTable(mb.build(), new CachedDataTableColumn[]
        {
                column
        });
    }


    private static DataTableMeta union(IDataTable... aMembers)
    {
        return new UnionDataTable("LB", aMembers).getMetaData();
    }


    @Test
    void membersAgreeingOnEncodingAndSourceGiveTheUnionTheirPair()
    {
        DataTableMeta meta = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_FORMAT),
                member("lb2", "B", "UTF-8", ENCODING_SOURCE_FORMAT));

        assertEquals("UTF-8", meta.getMetaData(META_KEY_ENCODING));
        assertEquals(ENCODING_SOURCE_FORMAT, meta.getMetaData(META_KEY_ENCODING_SOURCE));
        assertEquals("lb1,lb2", meta.getMetaData(UnionDataTable.META_UNION_OF));
    }


    @Test
    void aSingleMemberGivesTheUnionItsPair()
    {
        DataTableMeta meta = union(member("lb1", "A", "windows-1252", ENCODING_SOURCE_DECLARED));

        assertEquals("windows-1252", meta.getMetaData(META_KEY_ENCODING));
        assertEquals(ENCODING_SOURCE_DECLARED, meta.getMetaData(META_KEY_ENCODING_SOURCE));
    }


    @Test
    void membersWithDifferentEncodingsGiveMixed()
    {
        DataTableMeta meta = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_DECLARED),
                member("lb2", "A", "windows-1252", ENCODING_SOURCE_DECLARED));

        assertEquals(ENCODING_MIXED, meta.getMetaData(META_KEY_ENCODING));
        assertEquals(ENCODING_SOURCE_DECLARED, meta.getMetaData(META_KEY_ENCODING_SOURCE));
    }


    @Test
    void aMixedMemberMakesTheUnionMixedEvenWhenTheOthersAgree()
    {
        DataTableMeta meta = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_DECLARED),
                member("lb2", "A", ENCODING_MIXED, ENCODING_SOURCE_DEFAULT),
                member("lb3", "A", "UTF-8", ENCODING_SOURCE_DECLARED));

        assertEquals(ENCODING_MIXED, meta.getMetaData(META_KEY_ENCODING));
        assertEquals(ENCODING_SOURCE_DEFAULT, meta.getMetaData(META_KEY_ENCODING_SOURCE));
    }


    @Test
    void differingSourcesGiveTheLeastCertainPresent()
    {
        // DEFAULT < CONFIGURED < FORMAT < DECLARED; no member is DEFAULT, so CONFIGURED wins
        // wherever it stands in member order.
        DataTableMeta meta = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_DECLARED),
                member("lb2", "A", "UTF-8", ENCODING_SOURCE_FORMAT),
                member("lb3", "A", "UTF-8", ENCODING_SOURCE_CONFIGURED),
                member("lb4", "A", "UTF-8", ENCODING_SOURCE_FORMAT));

        assertEquals("UTF-8", meta.getMetaData(META_KEY_ENCODING));
        assertEquals(ENCODING_SOURCE_CONFIGURED, meta.getMetaData(META_KEY_ENCODING_SOURCE));

        DataTableMeta withDefault = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_CONFIGURED),
                member("lb2", "A", "UTF-8", ENCODING_SOURCE_DEFAULT));
        assertEquals(ENCODING_SOURCE_DEFAULT, withDefault.getMetaData(META_KEY_ENCODING_SOURCE));
    }


    @Test
    void aSourceOutsideTheVocabularyRanksBelowDefaultAndIsCarriedAsItIs()
    {
        DataTableMeta meta = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_DEFAULT),
                member("lb2", "A", "UTF-8", "GUESSED"));

        assertEquals("GUESSED", meta.getMetaData(META_KEY_ENCODING_SOURCE));
    }


    @Test
    void aMemberWithoutAnEncodingLeavesTheUnionWithNeitherKey()
    {
        for (int absent = 0; absent < 2; absent++)
        {
            IDataTable[] members =
            {
                    member("lb1", "A", "UTF-8", ENCODING_SOURCE_FORMAT),
                    member("lb2", "A", "UTF-8", ENCODING_SOURCE_FORMAT)
            };
            members[absent] = member("lbX", "A", null, null);

            DataTableMeta meta = union(members);

            assertNull(meta.getMetaData(META_KEY_ENCODING), "absent member " + absent);
            assertNull(meta.getMetaData(META_KEY_ENCODING_SOURCE), "absent member " + absent);
        }
    }


    @Test
    void aMemberWithHalfThePairCountsAsHavingNone()
    {
        // Both keys or neither (E1): an Encoding without its source is not an encoding to agree on,
        // and neither is a source without an Encoding.
        DataTableMeta noSource = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_FORMAT),
                member("lb2", "A", "UTF-8", null));
        DataTableMeta noEncoding = union(member("lb1", "A", "UTF-8", ENCODING_SOURCE_FORMAT),
                member("lb2", "A", null, ENCODING_SOURCE_FORMAT));

        for (DataTableMeta meta : new DataTableMeta[]
        {
                noSource, noEncoding
        })
        {
            assertNull(meta.getMetaData(META_KEY_ENCODING));
            assertNull(meta.getMetaData(META_KEY_ENCODING_SOURCE));
        }
    }
}
