package net.cumba.datatable.provider.define.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import net.cumba.cdisc.define.ClassDef;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.ItemGroupDef;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.ODM;
import net.cumba.cdisc.define.Study;
import net.cumba.cdisc.define.SubClassDef;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.Test;

/**
 * Fix #119: {@link DefineMetadataLibrary} exposes the effective Define-XML class (2.0 attribute or
 * 2.1 element) via {@link IDataTableMetadata#getClassName()} and the declared 2.1
 * {@code <def:SubClass>} names via {@link IDataTableMetadata#getSubClassNames()}.
 */
class DefineMetadataLibraryClassSubclassTest
{

    private static IMetadataLibrary buildLibrary(ItemGroupDef aGroup)
    {
        MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.001").name("V1")
                .itemGroupDefs(List.of(aGroup)).itemDefs(List.of()).build();
        Study study = Study.builder().oid("STUDY.001").metaDataVersions(List.of(mdv)).build();
        ODM odm = ODM.builder().fileOID("def-001").fileType("Snapshot").odmVersion("1.3.2")
                .studies(List.of(study)).build();
        DefineSupport ds = new DefineSupport(URI.create("file:///test/define.xml"), odm);
        return DefineMetadataLibrary.from(ds);
    }


    private static IDataTableMetadata table(IMetadataLibrary aLibrary, String aName)
    {
        return aLibrary.getDataTable(aName).orElseThrow();
    }


    @Test
    void attributeForm_classNameOnly()
    {
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.AE").name("AE").clazz("EVENTS").build();
        IDataTableMetadata meta = table(buildLibrary(group), "AE");
        assertEquals("EVENTS", meta.getClassName());
        assertTrue(meta.getSubClassNames().isEmpty());
    }


    @Test
    void elementForm_classNameAndSubClasses()
    {
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.ADTTE").name("ADTTE")
                .classElement(ClassDef.builder().name("BASIC DATA STRUCTURE")
                        .subClasses(List.of(SubClassDef.builder().name("TIME-TO-EVENT").build()))
                        .build())
                .build();
        IDataTableMetadata meta = table(buildLibrary(group), "ADTTE");
        assertEquals("BASIC DATA STRUCTURE", meta.getClassName());
        assertEquals(List.of("TIME-TO-EVENT"), meta.getSubClassNames());
    }


    @Test
    void elementFormWinsOverAttribute()
    {
        ItemGroupDef group = ItemGroupDef.builder().oid("IG.X").name("ADX").clazz("EVENTS")
                .classElement(ClassDef.builder().name("OCCURRENCE DATA STRUCTURE").build()).build();
        IDataTableMetadata meta = table(buildLibrary(group), "ADX");
        assertEquals("OCCURRENCE DATA STRUCTURE", meta.getClassName());
    }

}
