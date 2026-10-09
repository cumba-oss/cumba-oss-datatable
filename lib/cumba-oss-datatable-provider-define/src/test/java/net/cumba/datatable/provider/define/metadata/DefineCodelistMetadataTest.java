package net.cumba.datatable.provider.define.metadata;

// OSS-IDENTITY provider-define: byte-identical in cumba-datatable-cdisc and cumba-oss-datatable.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import net.cumba.cdisc.define.Alias;
import net.cumba.cdisc.define.CodeList;
import net.cumba.cdisc.define.CodeListItem;
import net.cumba.cdisc.define.CommentDef;
import net.cumba.cdisc.define.Decode;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.Description;
import net.cumba.cdisc.define.EnumeratedItem;
import net.cumba.cdisc.define.ExternalCodeList;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.ODM;
import net.cumba.cdisc.define.Standard;
import net.cumba.cdisc.define.Standards;
import net.cumba.cdisc.define.Study;
import net.cumba.cdisc.define.TranslatedText;
import org.junit.jupiter.api.Test;

/**
 * {@link DefineCodelistMetadata}: every field the define says about a codelist, keyed in display
 * order; the standard rendered from the resolved {@code def:Standard} (the raw OID when
 * unresolved); the dictionary and its version as two keys; extended values counted.
 */
class DefineCodelistMetadataTest
{

    private static final URI TEST_URI = URI.create("file:///test/define.xml");

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


    private static CodeListItem item(String aCode, String aDecode, boolean aExtended)
    {
        return CodeListItem.builder().codedValue(aCode).decode(decode(aDecode))
                .extendedValue(aExtended ? "Yes" : null).build();
    }


    private static Standard ct(String aOid)
    {
        return Standard.builder().oid(aOid).name("CDISC/NCI").type("CT").publishingSet("SDTM")
                .version("2023-12-15").status("Final").build();
    }


    private static DefineSupport define(List<CodeList> aCodeLists, List<Standard> aStandards,
            List<CommentDef> aComments)
    {
        MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.1").name("V1")
                .standards(Standards.builder().standards(aStandards).build()).commentDefs(aComments)
                .codeLists(aCodeLists).build();
        Study study = Study.builder().oid("S.1").metaDataVersions(List.of(mdv)).build();
        ODM odm = ODM.builder().fileOID("f1").studies(List.of(study)).build();
        return new DefineSupport(TEST_URI, odm);
    }


    private static CodeList fullCodelist()
    {
        return CodeList.builder().oid("CL.SEX").name("SEX").dataType("text").sasFormatName("$SEX")
                .standardOID("STD.CT").commentOID("COM.SEX").isNonStandard("Yes")
                .description(description("Sex"))
                .aliases(List.of(Alias.builder().context("other").name("x").build(),
                        Alias.builder().context("nci:ExtCodeID").name("C66731").build(),
                        Alias.builder().context("nci:ExtCodeID").name("C99999").build()))
                .codeListItems(List.of(item("M", "Male", false), item("F", "Female", false),
                        item("U", "Unknown", true), item("X", "Other", true)))
                .build();
    }


    @Test
    void everyKeyInDisplayOrder()
    {
        CommentDef comment = CommentDef.builder().oid("COM.SEX")
                .description(description("Sponsor-defined sex codes")).build();
        DefineCodelistMetadata md = DefineCodelistMetadata
                .of(define(List.of(fullCodelist()), List.of(ct("STD.CT")), List.of(comment)));

        Map<String, String> m = md.of(fullCodelist());

        assertEquals(
                List.of(DefineCodelistMetadata.DISPLAY_NAME, DefineCodelistMetadata.OID,
                        DefineCodelistMetadata.DATA_TYPE, DefineCodelistMetadata.SAS_FORMAT_NAME,
                        DefineCodelistMetadata.NCI_CODE, DefineCodelistMetadata.STANDARD,
                        DefineCodelistMetadata.STANDARD_OID, DefineCodelistMetadata.NON_STANDARD,
                        DefineCodelistMetadata.EXTENDED_VALUES, DefineCodelistMetadata.COMMENT),
                List.copyOf(m.keySet()));
        assertEquals("SEX", m.get(DefineCodelistMetadata.DISPLAY_NAME));
        assertEquals("CL.SEX", m.get(DefineCodelistMetadata.OID));
        assertEquals("text", m.get(DefineCodelistMetadata.DATA_TYPE));
        assertEquals("$SEX", m.get(DefineCodelistMetadata.SAS_FORMAT_NAME));
        assertEquals("C66731", m.get(DefineCodelistMetadata.NCI_CODE), "the first NCI alias");
        assertEquals("CDISC/NCI SDTM CT 2023-12-15", m.get(DefineCodelistMetadata.STANDARD));
        assertEquals("STD.CT", m.get(DefineCodelistMetadata.STANDARD_OID));
        assertEquals("Yes", m.get(DefineCodelistMetadata.NON_STANDARD));
        assertEquals("2", m.get(DefineCodelistMetadata.EXTENDED_VALUES));
        assertEquals("Sponsor-defined sex codes", m.get(DefineCodelistMetadata.COMMENT));
        assertThrows(UnsupportedOperationException.class, () -> m.put("x", "y"));
    }


    @Test
    void anUnresolvedStandardIsItsRawOid_andAnUnresolvedCommentIsAbsent()
    {
        DefineCodelistMetadata md = DefineCodelistMetadata
                .of(define(List.of(fullCodelist()), List.of(), List.of()));

        Map<String, String> m = md.of(fullCodelist());

        assertEquals("STD.CT", m.get(DefineCodelistMetadata.STANDARD));
        assertEquals("STD.CT", m.get(DefineCodelistMetadata.STANDARD_OID));
        assertFalse(m.containsKey(DefineCodelistMetadata.COMMENT));
    }


    @Test
    void aMinimalCodelistHasDisplayNameOidAndDataTypeOnly()
    {
        CodeList cl = CodeList.builder().oid("CL.YN").name("YN").dataType("integer")
                .sasFormatName("  ")
                .enumeratedItems(List.of(EnumeratedItem.builder().codedValue("1").build())).build();
        DefineCodelistMetadata md = DefineCodelistMetadata
                .of(define(List.of(cl), List.of(), List.of()));

        Map<String, String> m = md.of(cl);

        assertEquals(List.of(DefineCodelistMetadata.DISPLAY_NAME, DefineCodelistMetadata.OID,
                DefineCodelistMetadata.DATA_TYPE), List.copyOf(m.keySet()));
        assertEquals("integer", m.get(DefineCodelistMetadata.DATA_TYPE));
    }


    @Test
    void anExternalCodelistKeepsDictionaryAndVersionApart()
    {
        CodeList cl = CodeList.builder().oid("CL.AEDECOD").name("AEDECOD").dataType("text")
                .description(description("Dictionary-Derived Term"))
                .externalCodeList(ExternalCodeList.builder().dictionary("MedDRA").version("26.0")
                        .href("https://www.meddra.org").build())
                .build();
        DefineCodelistMetadata md = DefineCodelistMetadata
                .of(define(List.of(cl), List.of(), List.of()));

        Map<String, String> m = md.of(cl);

        assertEquals("MedDRA", m.get(DefineCodelistMetadata.DICTIONARY));
        assertEquals("26.0", m.get(DefineCodelistMetadata.DICTIONARY_VERSION));
        assertEquals(List.of(DefineCodelistMetadata.DISPLAY_NAME, DefineCodelistMetadata.OID,
                DefineCodelistMetadata.DATA_TYPE, DefineCodelistMetadata.DICTIONARY,
                DefineCodelistMetadata.DICTIONARY_VERSION), List.copyOf(m.keySet()));
    }


    @Test
    void extendedEnumeratedItemsAreCountedToo()
    {
        CodeList cl = CodeList.builder().oid("CL.X").name("X").dataType("text")
                .enumeratedItems(List.of(
                        EnumeratedItem.builder().codedValue("A").extendedValue("Yes").build(),
                        EnumeratedItem.builder().codedValue("B").build()))
                .build();
        DefineCodelistMetadata md = DefineCodelistMetadata
                .of(define(List.of(cl), List.of(), List.of()));

        assertEquals("1", md.of(cl).get(DefineCodelistMetadata.EXTENDED_VALUES));
    }


    @Test
    void prefixedKeysCarryThePrefixInTheSameOrder()
    {
        DefineCodelistMetadata md = DefineCodelistMetadata
                .of(define(List.of(fullCodelist()), List.of(ct("STD.CT")), List.of()));

        Map<String, String> plain = md.of(fullCodelist());
        Map<String, String> prefixed = md.prefixed(fullCodelist());

        assertEquals(plain.size(), prefixed.size());
        assertEquals(plain.keySet().stream().map(k -> DefineCodelistMetadata.PREFIX + k).toList(),
                List.copyOf(prefixed.keySet()));
        assertEquals(plain.get(DefineCodelistMetadata.STANDARD),
                prefixed.get(DefineCodelistMetadata.PREFIX + DefineCodelistMetadata.STANDARD));
    }


    @Test
    void renderStandardSkipsBlankPartsAndFallsBackToTheOid()
    {
        assertEquals("SDTMIG IG 3.4",
                DefineCodelistMetadata.renderStandard(Standard.builder().oid("STD.IG")
                        .name("SDTMIG").type("IG").version("3.4").publishingSet(" ").build()));
        assertEquals("STD.EMPTY",
                DefineCodelistMetadata.renderStandard(Standard.builder().oid("STD.EMPTY").build()));
    }


    @Test
    void theRosterIsEveryKeyInDisplayOrder()
    {
        List<String> keys = DefineCodelistMetadata.keys();

        assertEquals(12, keys.size());
        assertEquals(DefineCodelistMetadata.DISPLAY_NAME, keys.get(0));
        assertEquals(DefineCodelistMetadata.DICTIONARY_VERSION, keys.get(11));
        assertTrue(keys.containsAll(List.of(DefineCodelistMetadata.NCI_CODE,
                DefineCodelistMetadata.STANDARD, DefineCodelistMetadata.COMMENT)));
    }
}
