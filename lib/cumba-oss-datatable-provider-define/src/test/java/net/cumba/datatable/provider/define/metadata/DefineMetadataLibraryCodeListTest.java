package net.cumba.datatable.provider.define.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import net.cumba.cdisc.define.CodeList;
import net.cumba.cdisc.define.CodeListItem;
import net.cumba.cdisc.define.Decode;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.Description;
import net.cumba.cdisc.define.EnumeratedItem;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.ODM;
import net.cumba.cdisc.define.Study;
import net.cumba.cdisc.define.TranslatedText;
import net.cumba.datatable.metadata.ICodeList;
import net.cumba.datatable.metadata.ICodelistEntry;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@code DefineMetadataLibrary.DefineCodeList} and {@code DefineCodelistEntry}: covers
 * {@code getEntries()}, {@code buildFromCodeListItems()}, {@code buildFromEnumeratedItems()},
 * value-type mapping and metadata key/value lookups.
 */
class DefineMetadataLibraryCodeListTest
{

    private static final URI TEST_URI = URI.create("file:///test/define.xml");

    // ==================== Builders ====================

    private static Description description(String aText)
    {
        Description d = new Description();
        TranslatedText tt = new TranslatedText();
        tt.setValue(aText);
        d.setTranslatedTexts(List.of(tt));
        return d;
    }


    private static Decode decode(String aText)
    {
        Decode d = new Decode();
        TranslatedText tt = new TranslatedText();
        tt.setValue(aText);
        d.setTranslatedTexts(List.of(tt));
        return d;
    }


    private static CodeListItem item(String aCode, String aDecode)
    {
        return CodeListItem.builder().codedValue(aCode).decode(decode(aDecode)).build();
    }


    private static EnumeratedItem enumItem(String aCode)
    {
        return EnumeratedItem.builder().codedValue(aCode).build();
    }


    private static IMetadataLibrary buildLibrary(List<CodeList> aCodeLists)
    {
        MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.1").name("V1")
                .codeLists(aCodeLists).build();
        Study study = Study.builder().oid("S.1").metaDataVersions(List.of(mdv)).build();
        ODM odm = ODM.builder().fileOID("f1").studies(List.of(study)).build();
        DefineSupport support = new DefineSupport(TEST_URI, odm);
        return DefineMetadataLibrary.from(support);
    }

    // ==================== Codelist lookup / name conventions ====================


    @Test
    void textCodeList_namePrefixedWithDollar()
    {
        CodeList cl = CodeList.builder().oid("CL.1").name("RACE").dataType("text")
                .codeListItems(List.of(item("WHITE", "White"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("$RACE").orElseThrow();
        assertEquals("$RACE", found.getName());
        assertEquals(DataValueType.STRING, found.getValueType());
    }


    @Test
    void integerCodeList_nameUnchanged()
    {
        CodeList cl = CodeList.builder().oid("CL.2").name("YN").dataType("integer")
                .codeListItems(List.of(item("1", "Yes"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("YN").orElseThrow();
        assertEquals("YN", found.getName());
        assertEquals(DataValueType.LONG, found.getValueType());
    }


    @ParameterizedTest
    @CsvSource(
    {
            "float,DOUBLE", "integer,LONG", "text,STRING", "datetime,STRING"
    })
    void valueType_matchesDataType(String aDataType, DataValueType aExpected)
    {
        CodeList cl = CodeList.builder().oid("CL.X").name("X").dataType(aDataType)
                .codeListItems(List.of(item("1", "One"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelists().get(0);
        assertEquals(aExpected, found.getValueType());
    }


    @Test
    void codelistsLazyInitialised_secondCallReturnsSameList()
    {
        CodeList cl = CodeList.builder().oid("CL.K").name("K").dataType("integer").build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        List<ICodeList> first = lib.getCodelists();
        List<ICodeList> second = lib.getCodelists();
        assertEquals(first, second);
    }

    // ==================== buildFromCodeListItems ====================


    @Test
    void buildFromCodeListItems_entries_haveCodeAndDecode()
    {
        CodeList cl = CodeList.builder().oid("CL.1").name("RACE").dataType("text")
                .codeListItems(List.of(item("WHITE", "White"), item("BLACK", "Black"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("$RACE").orElseThrow();
        List<ICodelistEntry> entries = found.getEntries();
        assertEquals(2, entries.size());
        // first entry
        assertEquals("WHITE", entries.get(0).getCodeValue());
        assertEquals("White", entries.get(0).getDecodeValue());
    }


    @Test
    void getEntries_lazyAndCached()
    {
        CodeList cl = CodeList.builder().oid("CL.1").name("YN").dataType("integer")
                .codeListItems(List.of(item("1", "Yes"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("YN").orElseThrow();
        List<ICodelistEntry> first = found.getEntries();
        List<ICodelistEntry> second = found.getEntries();
        assertEquals(first, second);
    }


    @Test
    void itemWithoutCodedValue_isSkipped()
    {
        CodeList cl = CodeList.builder().oid("CL.S").name("S").dataType("integer")
                .codeListItems(
                        List.of(CodeListItem.builder().codedValue(null).decode(decode("X")).build(),
                                item("1", "Yes")))
                .build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        List<ICodelistEntry> entries = lib.getCodelist("S").orElseThrow().getEntries();
        assertEquals(1, entries.size());
        assertEquals("1", entries.get(0).getCodeValue());
    }


    @Test
    void itemWithoutDecode_isSkipped()
    {
        CodeList cl = CodeList.builder().oid("CL.T").name("T").dataType("integer")
                .codeListItems(List.of(CodeListItem.builder().codedValue("X").decode(null).build(),
                        item("1", "Yes")))
                .build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        assertEquals(1, lib.getCodelist("T").orElseThrow().getEntries().size());
    }


    @Test
    void itemWithEmptyTranslatedTexts_isSkipped()
    {
        Decode emptyDecode = new Decode();
        emptyDecode.setTranslatedTexts(List.of());
        CodeListItem broken = CodeListItem.builder().codedValue("X").decode(emptyDecode).build();
        CodeList cl = CodeList.builder().oid("CL.U").name("U").dataType("integer")
                .codeListItems(List.of(broken, item("1", "Yes"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        assertEquals(1, lib.getCodelist("U").orElseThrow().getEntries().size());
    }


    @Test
    void itemWithNullDecodedValueInTranslatedText_isSkipped()
    {
        TranslatedText nullText = new TranslatedText();
        nullText.setValue(null);
        Decode dec = new Decode();
        dec.setTranslatedTexts(List.of(nullText));
        CodeList cl = CodeList.builder().oid("CL.V").name("V").dataType("integer")
                .codeListItems(List.of(CodeListItem.builder().codedValue("X").decode(dec).build(),
                        item("1", "Yes")))
                .build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        assertEquals(1, lib.getCodelist("V").orElseThrow().getEntries().size());
    }

    // ==================== buildFromEnumeratedItems ====================


    @Test
    void enumItems_buildIdentityEntries()
    {
        CodeList cl = CodeList.builder().oid("CL.E").name("DAY").dataType("text")
                .enumeratedItems(List.of(enumItem("MON"), enumItem("TUE"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        List<ICodelistEntry> entries = lib.getCodelist("$DAY").orElseThrow().getEntries();
        assertEquals(2, entries.size());
        // code == decode
        for (ICodelistEntry e : entries)
        {
            assertEquals(e.getCodeValue(), e.getDecodeValue());
        }
    }


    @Test
    void enumItemsWithoutCodedValue_areSkipped()
    {
        CodeList cl = CodeList.builder().oid("CL.E2").name("EX").dataType("text")
                .enumeratedItems(
                        List.of(EnumeratedItem.builder().codedValue(null).build(), enumItem("OK")))
                .build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        assertEquals(1, lib.getCodelist("$EX").orElseThrow().getEntries().size());
    }

    // ==================== Empty codelist (no items, no enum items) ====================


    @Test
    void emptyCodelist_yieldsEmptyEntries()
    {
        CodeList cl = CodeList.builder().oid("CL.0").name("EMPTY").dataType("text").build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("$EMPTY").orElseThrow();
        assertEquals(0, found.getEntries().size());
    }

    // ==================== isExtensible / metadata ====================


    @Test
    void isExtensible_returnsFalse()
    {
        CodeList cl = CodeList.builder().oid("CL.F").name("F").dataType("integer").build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("F").orElseThrow();
        assertEquals(false, found.isExtensible());
    }


    @Test
    void getMetaKeys_includesLabel_whenDescriptionPresent()
    {
        CodeList cl = CodeList.builder().oid("CL.L").name("L").dataType("text")
                .description(description("Label-Text")).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("$L").orElseThrow();
        assertTrue(found.getMetaKeys().contains("label"));
        Optional<Object> v = found.getMetaValue("label");
        assertEquals("Label-Text", v.orElseThrow());
    }


    @Test
    void getMetaKeys_isEmptyWhenNoDescription()
    {
        CodeList cl = CodeList.builder().oid("CL.NL").name("NL").dataType("text").build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("$NL").orElseThrow();
        assertEquals(0, found.getMetaKeys().size());
        assertTrue(found.getMetaValue("label").isEmpty());
    }


    @Test
    void getMetaValue_unknownKey_returnsEmpty()
    {
        CodeList cl = CodeList.builder().oid("CL.X").name("X").dataType("text")
                .description(description("X-Label")).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodeList found = lib.getCodelist("$X").orElseThrow();
        assertTrue(found.getMetaValue("unknown-key").isEmpty());
    }

    // ==================== DefineCodelistEntry ====================


    @Test
    void codelistEntry_metaKeysAreEmpty()
    {
        CodeList cl = CodeList.builder().oid("CL.E").name("E").dataType("text")
                .codeListItems(List.of(item("A", "Alpha"))).build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        ICodelistEntry entry = lib.getCodelist("$E").orElseThrow().getEntries().get(0);
        assertNotNull(entry);
        assertEquals(0, entry.getMetaKeys().size());
        assertTrue(entry.getMetaValue("any").isEmpty());
    }

    // ==================== getCodelist on absent / null lookup ====================


    @Test
    void getCodelist_byUnknownName_returnsEmpty()
    {
        CodeList cl = CodeList.builder().oid("CL.A").name("A").dataType("integer").build();
        IMetadataLibrary lib = buildLibrary(List.of(cl));

        assertTrue(lib.getCodelist("NOT_HERE").isEmpty());
    }
}
