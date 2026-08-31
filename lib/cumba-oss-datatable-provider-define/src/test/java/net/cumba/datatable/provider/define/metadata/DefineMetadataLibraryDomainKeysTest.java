package net.cumba.datatable.provider.define.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.ItemDef;
import net.cumba.cdisc.define.ItemGroupDef;
import net.cumba.cdisc.define.ItemRef;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.ODM;
import net.cumba.cdisc.define.Study;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.Test;

/**
 * Tests for derivation of {@link IColumnMetadata#getKeySequence()} from {@code def:DomainKeys} in
 * {@link DefineMetadataLibrary}.
 */
class DefineMetadataLibraryDomainKeysTest
{

    private static IMetadataLibrary buildLibrary(ItemGroupDef aGroup, List<ItemDef> aItemDefs)
    {
        MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.001").name("V1")
                .itemGroupDefs(List.of(aGroup)).itemDefs(aItemDefs).build();
        Study study = Study.builder().oid("STUDY.001").metaDataVersions(List.of(mdv)).build();
        ODM odm = ODM.builder().fileOID("def-001").fileType("Snapshot").odmVersion("1.3.2")
                .studies(List.of(study)).build();
        DefineSupport ds = new DefineSupport(URI.create("file:///test/define.xml"), odm);
        return DefineMetadataLibrary.from(ds);
    }


    private static ItemDef itemDef(String aOid, String aName)
    {
        return ItemDef.builder().oid(aOid).name(aName).dataType("text").length(20).build();
    }


    private static ItemRef itemRef(String aItemOid, int aOrder)
    {
        return ItemRef.builder().itemOID(aItemOid).orderNumber(aOrder).mandatory("Yes").build();
    }


    private static IColumnMetadata columnByName(IDataTableMetadata aMeta, String aName)
    {
        return aMeta.getColumns().stream()//
                .filter(c -> c.getName().equalsIgnoreCase(aName))//
                .findFirst()//
                .orElseThrow(() -> new AssertionError("column not found: " + aName));
    }

    // ==================== getKeySequence() ====================


    @Test
    void domainKeys_derivesKeySequenceInOrder()
    {
        // TA-like: def:DomainKeys="STUDYID, ARMCD, TAETORD", no ItemRef KeySequence set
        List<ItemDef> defs = Arrays.asList(//
                itemDef("IT.STUDYID", "STUDYID"), //
                itemDef("IT.DOMAIN", "DOMAIN"), //
                itemDef("IT.ARMCD", "ARMCD"), //
                itemDef("IT.TAETORD", "TAETORD"));
        List<ItemRef> refs = Arrays.asList(//
                itemRef("IT.STUDYID", 1), //
                itemRef("IT.DOMAIN", 2), //
                itemRef("IT.ARMCD", 3), //
                itemRef("IT.TAETORD", 4));
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.TA").name("TA").domain("TA")
                .domainKeys("STUDYID, ARMCD, TAETORD").itemRefs(refs).build();

        IMetadataLibrary lib = buildLibrary(group, defs);
        IDataTableMetadata table = lib.getDataTable("TA").orElseThrow();

        assertEquals(1, columnByName(table, "STUDYID").getKeySequence());
        assertEquals(2, columnByName(table, "ARMCD").getKeySequence());
        assertEquals(3, columnByName(table, "TAETORD").getKeySequence());
        // non-key column
        assertEquals(0, columnByName(table, "DOMAIN").getKeySequence());
    }


    @Test
    void domainKeys_caseInsensitiveAndTrimmed()
    {
        List<ItemDef> defs = Arrays.asList(//
                itemDef("IT.STUDYID", "STUDYID"), //
                itemDef("IT.USUBJID", "USUBJID"));
        List<ItemRef> refs = Arrays.asList(//
                itemRef("IT.STUDYID", 1), //
                itemRef("IT.USUBJID", 2));
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.DM").name("DM").domain("DM")
                .domainKeys("  studyid , USUBJID  ").itemRefs(refs).build();

        IMetadataLibrary lib = buildLibrary(group, defs);
        IDataTableMetadata table = lib.getDataTable("DM").orElseThrow();

        assertEquals(1, columnByName(table, "STUDYID").getKeySequence());
        assertEquals(2, columnByName(table, "USUBJID").getKeySequence());
    }


    @Test
    void explicitItemRefKeySequence_winsOverDomainKeys()
    {
        // DomainKeys would put STUDYID at position 1 and USUBJID at 2,
        // but ItemRef sets explicit 5 on STUDYID — explicit must win.
        List<ItemDef> defs = Arrays.asList(//
                itemDef("IT.STUDYID", "STUDYID"), //
                itemDef("IT.USUBJID", "USUBJID"));
        List<ItemRef> refs = Arrays.asList(//
                ItemRef.builder().itemOID("IT.STUDYID").orderNumber(1).mandatory("Yes")
                        .keySequence(5).build(), //
                itemRef("IT.USUBJID", 2));
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.DM").name("DM").domain("DM")
                .domainKeys("STUDYID, USUBJID").itemRefs(refs).build();

        IMetadataLibrary lib = buildLibrary(group, defs);
        IDataTableMetadata table = lib.getDataTable("DM").orElseThrow();

        assertEquals(5, columnByName(table, "STUDYID").getKeySequence());
        assertEquals(2, columnByName(table, "USUBJID").getKeySequence());
    }


    @Test
    void nullDomainKeys_leavesItemRefKeySequenceIntact()
    {
        List<ItemDef> defs = Arrays.asList(itemDef("IT.USUBJID", "USUBJID"));
        List<ItemRef> refs = Arrays.asList(//
                ItemRef.builder().itemOID("IT.USUBJID").orderNumber(1).mandatory("Yes")
                        .keySequence(1).build());
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.DM").name("DM").domain("DM")
                .itemRefs(refs).build(); // no domainKeys at all

        IMetadataLibrary lib = buildLibrary(group, defs);
        IDataTableMetadata table = lib.getDataTable("DM").orElseThrow();

        assertEquals(1, columnByName(table, "USUBJID").getKeySequence());
    }


    @Test
    void blankDomainKeys_yieldsNoDerivedSequence()
    {
        List<ItemDef> defs = Arrays.asList(itemDef("IT.STUDYID", "STUDYID"));
        List<ItemRef> refs = Arrays.asList(itemRef("IT.STUDYID", 1));
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.X").name("X").domain("X")
                .domainKeys("   ").itemRefs(refs).build();

        IMetadataLibrary lib = buildLibrary(group, defs);
        IDataTableMetadata table = lib.getDataTable("X").orElseThrow();

        assertEquals(0, columnByName(table, "STUDYID").getKeySequence());
    }


    @Test
    void domainKeyNotInDataset_isIgnored()
    {
        // "FOO" is not a column — must not blow up, and must not skew positions
        List<ItemDef> defs = Arrays.asList(//
                itemDef("IT.STUDYID", "STUDYID"), //
                itemDef("IT.USUBJID", "USUBJID"));
        List<ItemRef> refs = Arrays.asList(//
                itemRef("IT.STUDYID", 1), //
                itemRef("IT.USUBJID", 2));
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.DM").name("DM").domain("DM")
                .domainKeys("STUDYID, FOO, USUBJID").itemRefs(refs).build();

        IMetadataLibrary lib = buildLibrary(group, defs);
        IDataTableMetadata table = lib.getDataTable("DM").orElseThrow();

        // STUDYID is at pos 1, USUBJID is at pos 3 (we preserve position per DomainKeys list)
        assertEquals(1, columnByName(table, "STUDYID").getKeySequence());
        assertEquals(3, columnByName(table, "USUBJID").getKeySequence());
    }

    // ==================== metaMap (UI visibility) ====================


    @Test
    void derivedKeySequence_isSurfacedInMetaMap()
    {
        List<ItemDef> defs = Arrays.asList(//
                itemDef("IT.STUDYID", "STUDYID"), //
                itemDef("IT.ARMCD", "ARMCD"));
        List<ItemRef> refs = Arrays.asList(//
                itemRef("IT.STUDYID", 1), //
                itemRef("IT.ARMCD", 2));
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.TA").name("TA").domain("TA")
                .domainKeys("STUDYID, ARMCD").itemRefs(refs).build();

        IMetadataLibrary lib = buildLibrary(group, defs);
        IDataTableMetadata table = lib.getDataTable("TA").orElseThrow();

        IColumnMetadata studyid = columnByName(table, "STUDYID");
        Optional<Object> ks = studyid.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE);
        assertTrue(ks.isPresent(), "KeySequence must be present in metaMap");
        assertEquals(1, ks.get());

        IColumnMetadata armcd = columnByName(table, "ARMCD");
        assertEquals(2,
                armcd.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE).orElseThrow());
    }

}
