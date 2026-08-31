package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * JUnit 5 tests for the define package bean classes.
 */
public class CodeListTest
{

    // ========================================================================
    // TranslatedText Tests (@Data, @NoArgsConstructor)
    // ========================================================================

    @Nested
    class TranslatedTextTests
    {

        @Test
        void testNoArgsConstructor()
        {
            TranslatedText text = new TranslatedText();
            assertNotNull(text);
            assertNull(text.getValue());
            assertNull(text.getLang());
        }


        @Test
        void testSettersAndGetters()
        {
            TranslatedText text = new TranslatedText();
            text.setValue("Hello World");
            text.setLang("en");

            assertEquals("Hello World", text.getValue());
            assertEquals("en", text.getLang());
        }


        @Test
        void testEqualsAndHashCode()
        {
            TranslatedText text1 = new TranslatedText();
            text1.setValue("Test");
            text1.setLang("en");

            TranslatedText text2 = new TranslatedText();
            text2.setValue("Test");
            text2.setLang("en");

            assertEquals(text1, text2);
            assertEquals(text1.hashCode(), text2.hashCode());
        }


        @Test
        void testToString()
        {
            TranslatedText text = new TranslatedText();
            text.setValue("Test");
            text.setLang("en");

            String str = text.toString();
            assertNotNull(str);
            assertTrue(str.contains("Test"));
            assertTrue(str.contains("en"));
        }
    }

    // ========================================================================
    // Decode Tests (@Data, @NoArgsConstructor)
    // ========================================================================


    @Nested
    class DecodeTests
    {

        @Test
        void testNoArgsConstructor()
        {
            Decode decode = new Decode();
            assertNotNull(decode);
            assertNull(decode.getTranslatedTexts());
        }


        @Test
        void testSettersAndGetters()
        {
            Decode decode = new Decode();

            TranslatedText text1 = new TranslatedText();
            text1.setValue("Value 1");
            text1.setLang("en");

            TranslatedText text2 = new TranslatedText();
            text2.setValue("Value 2");
            text2.setLang("de");

            List<TranslatedText> texts = Arrays.asList(text1, text2);
            decode.setTranslatedTexts(texts);

            assertNotNull(decode.getTranslatedTexts());
            assertEquals(2, decode.getTranslatedTexts().size());
            assertEquals("Value 1", decode.getTranslatedTexts().get(0).getValue());
            assertEquals("de", decode.getTranslatedTexts().get(1).getLang());
        }


        @Test
        void testWithEmptyList()
        {
            Decode decode = new Decode();
            decode.setTranslatedTexts(new ArrayList<>());

            assertNotNull(decode.getTranslatedTexts());
            assertTrue(decode.getTranslatedTexts().isEmpty());
        }
    }

    // ========================================================================
    // Description Tests (@Data, @NoArgsConstructor)
    // ========================================================================


    @Nested
    class DescriptionTests
    {

        @Test
        void testNoArgsConstructor()
        {
            Description description = new Description();
            assertNotNull(description);
            assertNull(description.getValue());
            assertNull(description.getLang());
            assertNull(description.getTranslatedTexts());
        }


        @Test
        void testSettersAndGetters()
        {
            Description description = new Description();
            description.setValue("Description text");
            description.setLang("en");

            TranslatedText text = new TranslatedText();
            text.setValue("Translated");
            text.setLang("fr");
            description.setTranslatedTexts(Arrays.asList(text));

            assertEquals("Description text", description.getValue());
            assertEquals("en", description.getLang());
            assertNotNull(description.getTranslatedTexts());
            assertEquals(1, description.getTranslatedTexts().size());
        }


        @Test
        void testAllFieldsPopulated()
        {
            Description description = new Description();
            description.setValue("Main description");
            description.setLang("en");

            TranslatedText text1 = new TranslatedText();
            text1.setValue("French translation");
            text1.setLang("fr");

            TranslatedText text2 = new TranslatedText();
            text2.setValue("German translation");
            text2.setLang("de");

            description.setTranslatedTexts(Arrays.asList(text1, text2));

            assertEquals("Main description", description.getValue());
            assertEquals("en", description.getLang());
            assertEquals(2, description.getTranslatedTexts().size());
            assertEquals("French translation", description.getTranslatedTexts().get(0).getValue());
            assertEquals("German translation", description.getTranslatedTexts().get(1).getValue());
        }
    }

    // ========================================================================
    // ExternalCodeList Tests (@Value, @Builder)
    // ========================================================================


    @Nested
    class ExternalCodeListTests
    {

        @Test
        void testBuilder()
        {
            ExternalCodeList extCodeList = ExternalCodeList.builder().dictionary("MedDRA")
                    .version("23.1").ref("external-ref").href("http://example.com/codelist")
                    .build();

            assertNotNull(extCodeList);
            assertEquals("MedDRA", extCodeList.getDictionary());
            assertEquals("23.1", extCodeList.getVersion());
            assertEquals("external-ref", extCodeList.getRef());
            assertEquals("http://example.com/codelist", extCodeList.getHref());
        }


        @Test
        void testBuilderWithNullValues()
        {
            ExternalCodeList extCodeList = ExternalCodeList.builder().dictionary("TestDict")
                    .build();

            assertNotNull(extCodeList);
            assertEquals("TestDict", extCodeList.getDictionary());
            assertNull(extCodeList.getVersion());
            assertNull(extCodeList.getRef());
            assertNull(extCodeList.getHref());
        }


        @Test
        void testEqualsAndHashCode()
        {
            ExternalCodeList ext1 = ExternalCodeList.builder().dictionary("Dict1").version("1.0")
                    .build();

            ExternalCodeList ext2 = ExternalCodeList.builder().dictionary("Dict1").version("1.0")
                    .build();

            assertEquals(ext1, ext2);
            assertEquals(ext1.hashCode(), ext2.hashCode());
        }
    }

    // ========================================================================
    // CodeListRef Tests (@Value, @Builder)
    // ========================================================================


    @Nested
    class CodeListRefTests
    {

        @Test
        void testBuilder()
        {
            CodeListRef ref = CodeListRef.builder().codeListOID("CL.SEX").build();

            assertNotNull(ref);
            assertEquals("CL.SEX", ref.getCodeListOID());
        }


        @Test
        void testBuilderWithNullOID()
        {
            CodeListRef ref = CodeListRef.builder().build();

            assertNotNull(ref);
            assertNull(ref.getCodeListOID());
        }


        @Test
        void testEqualsAndHashCode()
        {
            CodeListRef ref1 = CodeListRef.builder().codeListOID("CL.RACE").build();

            CodeListRef ref2 = CodeListRef.builder().codeListOID("CL.RACE").build();

            assertEquals(ref1, ref2);
            assertEquals(ref1.hashCode(), ref2.hashCode());
        }


        @Test
        void testToString()
        {
            CodeListRef ref = CodeListRef.builder().codeListOID("CL.COUNTRY").build();

            String str = ref.toString();
            assertNotNull(str);
            assertTrue(str.contains("CL.COUNTRY"));
        }
    }

    // ========================================================================
    // Alias Tests (@Value, @Builder) - used by multiple classes
    // ========================================================================


    @Nested
    class AliasTests
    {

        @Test
        void testBuilder()
        {
            Alias alias = Alias.builder().context("CDASH").name("SEX").build();

            assertNotNull(alias);
            assertEquals("CDASH", alias.getContext());
            assertEquals("SEX", alias.getName());
        }


        @Test
        void testBuilderWithNullValues()
        {
            Alias alias = Alias.builder().build();

            assertNotNull(alias);
            assertNull(alias.getContext());
            assertNull(alias.getName());
        }


        @Test
        void testEqualsAndHashCode()
        {
            Alias alias1 = Alias.builder().context("SDTM").name("AGE").build();

            Alias alias2 = Alias.builder().context("SDTM").name("AGE").build();

            assertEquals(alias1, alias2);
            assertEquals(alias1.hashCode(), alias2.hashCode());
        }
    }

    // ========================================================================
    // EnumeratedItem Tests (@Value, @Builder)
    // ========================================================================


    @Nested
    class EnumeratedItemTests
    {

        @Test
        void testBuilder()
        {
            EnumeratedItem item = EnumeratedItem.builder().codedValue("M").rank(1.0).orderNumber(1)
                    .build();

            assertNotNull(item);
            assertEquals("M", item.getCodedValue());
            assertEquals(1.0, item.getRank());
            assertEquals(1, item.getOrderNumber());
        }


        @Test
        void testBuilderWithAliases()
        {
            Alias alias1 = Alias.builder().context("SDTM").name("MALE").build();

            Alias alias2 = Alias.builder().context("CDASH").name("M").build();

            EnumeratedItem item = EnumeratedItem.builder().codedValue("M").rank(1.0).orderNumber(1)
                    .aliases(Arrays.asList(alias1, alias2)).build();

            assertNotNull(item);
            assertNotNull(item.getAliases());
            assertEquals(2, item.getAliases().size());
            assertEquals("SDTM", item.getAliases().get(0).getContext());
            assertEquals("CDASH", item.getAliases().get(1).getContext());
        }


        @Test
        void testBuilderWithNullValues()
        {
            EnumeratedItem item = EnumeratedItem.builder().codedValue("F").build();

            assertNotNull(item);
            assertEquals("F", item.getCodedValue());
            assertNull(item.getRank());
            assertNull(item.getOrderNumber());
            assertNull(item.getAliases());
        }


        @Test
        void testEqualsAndHashCode()
        {
            EnumeratedItem item1 = EnumeratedItem.builder().codedValue("Y").orderNumber(1).build();

            EnumeratedItem item2 = EnumeratedItem.builder().codedValue("Y").orderNumber(1).build();

            assertEquals(item1, item2);
            assertEquals(item1.hashCode(), item2.hashCode());
        }
    }

    // ========================================================================
    // CodeListItem Tests (@Value, @Builder)
    // ========================================================================


    @Nested
    class CodeListItemTests
    {

        @Test
        void testBuilder()
        {
            CodeListItem item = CodeListItem.builder().codedValue("1").rank(1.5).orderNumber(1)
                    .extendedValue("Yes").build();

            assertNotNull(item);
            assertEquals("1", item.getCodedValue());
            assertEquals(1.5, item.getRank());
            assertEquals(1, item.getOrderNumber());
            assertEquals("Yes", item.getExtendedValue());
        }


        @Test
        void testBuilderWithDecode()
        {
            TranslatedText text = new TranslatedText();
            text.setValue("Yes");
            text.setLang("en");

            Decode decode = new Decode();
            decode.setTranslatedTexts(Arrays.asList(text));

            CodeListItem item = CodeListItem.builder().codedValue("Y").decode(decode).build();

            assertNotNull(item);
            assertNotNull(item.getDecode());
            assertEquals(1, item.getDecode().getTranslatedTexts().size());
            assertEquals("Yes", item.getDecode().getTranslatedTexts().get(0).getValue());
        }


        @Test
        void testBuilderWithAliases()
        {
            Alias alias = Alias.builder().context("nci:ExtCodeID").name("C12345").build();

            CodeListItem item = CodeListItem.builder().codedValue("ACTIVE")
                    .aliases(Arrays.asList(alias)).build();

            assertNotNull(item);
            assertNotNull(item.getAliases());
            assertEquals(1, item.getAliases().size());
            assertEquals("nci:ExtCodeID", item.getAliases().get(0).getContext());
        }


        @Test
        void testBuilderWithAllFields()
        {
            TranslatedText text = new TranslatedText();
            text.setValue("Male");
            text.setLang("en");

            Decode decode = new Decode();
            decode.setTranslatedTexts(Arrays.asList(text));

            Alias alias = Alias.builder().context("SDTM").name("M").build();

            CodeListItem item = CodeListItem.builder().codedValue("M").rank(1.0).orderNumber(1)
                    .extendedValue("No").decode(decode).aliases(Arrays.asList(alias)).build();

            assertNotNull(item);
            assertEquals("M", item.getCodedValue());
            assertEquals(1.0, item.getRank());
            assertEquals(1, item.getOrderNumber());
            assertEquals("No", item.getExtendedValue());
            assertNotNull(item.getDecode());
            assertNotNull(item.getAliases());
            assertEquals(1, item.getAliases().size());
        }


        @Test
        void testEqualsAndHashCode()
        {
            CodeListItem item1 = CodeListItem.builder().codedValue("TEST").orderNumber(5).build();

            CodeListItem item2 = CodeListItem.builder().codedValue("TEST").orderNumber(5).build();

            assertEquals(item1, item2);
            assertEquals(item1.hashCode(), item2.hashCode());
        }
    }

    // ========================================================================
    // CodeList Tests (@Value, @Builder)
    // ========================================================================


    @Nested
    class CodeListMainTests
    {

        @Test
        void testBuilder()
        {
            CodeList codeList = CodeList.builder().oid("CL.SEX").name("Sex").dataType("text")
                    .sasFormatName("SEX.").build();

            assertNotNull(codeList);
            assertEquals("CL.SEX", codeList.getOid());
            assertEquals("Sex", codeList.getName());
            assertEquals("text", codeList.getDataType());
            assertEquals("SEX.", codeList.getSasFormatName());
        }


        @Test
        void testBuilderWithDescription()
        {
            Description description = new Description();
            description.setValue("Code list for sex values");
            description.setLang("en");

            CodeList codeList = CodeList.builder().oid("CL.SEX").name("Sex")
                    .description(description).build();

            assertNotNull(codeList);
            assertNotNull(codeList.getDescription());
            assertEquals("Code list for sex values", codeList.getDescription().getValue());
        }


        @Test
        void testBuilderWithCodeListItems()
        {
            TranslatedText maleText = new TranslatedText();
            maleText.setValue("Male");
            maleText.setLang("en");

            Decode maleDecode = new Decode();
            maleDecode.setTranslatedTexts(Arrays.asList(maleText));

            CodeListItem maleItem = CodeListItem.builder().codedValue("M").orderNumber(1)
                    .decode(maleDecode).build();

            TranslatedText femaleText = new TranslatedText();
            femaleText.setValue("Female");
            femaleText.setLang("en");

            Decode femaleDecode = new Decode();
            femaleDecode.setTranslatedTexts(Arrays.asList(femaleText));

            CodeListItem femaleItem = CodeListItem.builder().codedValue("F").orderNumber(2)
                    .decode(femaleDecode).build();

            CodeList codeList = CodeList.builder().oid("CL.SEX").name("Sex").dataType("text")
                    .codeListItems(Arrays.asList(maleItem, femaleItem)).build();

            assertNotNull(codeList);
            assertNotNull(codeList.getCodeListItems());
            assertEquals(2, codeList.getCodeListItems().size());
            assertEquals("M", codeList.getCodeListItems().get(0).getCodedValue());
            assertEquals("F", codeList.getCodeListItems().get(1).getCodedValue());
        }


        @Test
        void testBuilderWithEnumeratedItems()
        {
            EnumeratedItem item1 = EnumeratedItem.builder().codedValue("YES").orderNumber(1)
                    .build();

            EnumeratedItem item2 = EnumeratedItem.builder().codedValue("NO").orderNumber(2).build();

            CodeList codeList = CodeList.builder().oid("CL.NY").name("No Yes Response")
                    .enumeratedItems(Arrays.asList(item1, item2)).build();

            assertNotNull(codeList);
            assertNotNull(codeList.getEnumeratedItems());
            assertEquals(2, codeList.getEnumeratedItems().size());
            assertEquals("YES", codeList.getEnumeratedItems().get(0).getCodedValue());
            assertEquals("NO", codeList.getEnumeratedItems().get(1).getCodedValue());
        }


        @Test
        void testBuilderWithExternalCodeList()
        {
            ExternalCodeList extCodeList = ExternalCodeList.builder().dictionary("MedDRA")
                    .version("23.1").build();

            CodeList codeList = CodeList.builder().oid("CL.MEDDRA").name("MedDRA Terms")
                    .externalCodeList(extCodeList).build();

            assertNotNull(codeList);
            assertNotNull(codeList.getExternalCodeList());
            assertEquals("MedDRA", codeList.getExternalCodeList().getDictionary());
            assertEquals("23.1", codeList.getExternalCodeList().getVersion());
        }


        @Test
        void testBuilderWithAliases()
        {
            Alias alias1 = Alias.builder().context("nci:ExtCodeID").name("C66731").build();

            Alias alias2 = Alias.builder().context("CDASH").name("SEX").build();

            CodeList codeList = CodeList.builder().oid("CL.SEX").name("Sex")
                    .aliases(Arrays.asList(alias1, alias2)).build();

            assertNotNull(codeList);
            assertNotNull(codeList.getAliases());
            assertEquals(2, codeList.getAliases().size());
            assertEquals("nci:ExtCodeID", codeList.getAliases().get(0).getContext());
            assertEquals("CDASH", codeList.getAliases().get(1).getContext());
        }


        @Test
        void testBuilderWithAllFields()
        {
            Description description = new Description();
            description.setValue("Complete code list");
            description.setLang("en");

            CodeListItem item = CodeListItem.builder().codedValue("VAL").orderNumber(1).build();

            EnumeratedItem enumItem = EnumeratedItem.builder().codedValue("ENUM").orderNumber(1)
                    .build();

            ExternalCodeList extList = ExternalCodeList.builder().dictionary("Test").version("1.0")
                    .build();

            Alias alias = Alias.builder().context("TEST").name("ALIAS").build();

            CodeList codeList = CodeList.builder().oid("CL.COMPLETE").name("Complete CodeList")
                    .dataType("text").sasFormatName("COMPLETE.").standardOID("STD.1")
                    .commentOID("COM.1").description(description).codeListItems(Arrays.asList(item))
                    .enumeratedItems(Arrays.asList(enumItem)).externalCodeList(extList)
                    .aliases(Arrays.asList(alias)).build();

            assertNotNull(codeList);
            assertEquals("CL.COMPLETE", codeList.getOid());
            assertEquals("Complete CodeList", codeList.getName());
            assertEquals("text", codeList.getDataType());
            assertEquals("COMPLETE.", codeList.getSasFormatName());
            assertEquals("STD.1", codeList.getStandardOID());
            assertEquals("COM.1", codeList.getCommentOID());
            assertNotNull(codeList.getDescription());
            assertNotNull(codeList.getCodeListItems());
            assertNotNull(codeList.getEnumeratedItems());
            assertNotNull(codeList.getExternalCodeList());
            assertNotNull(codeList.getAliases());
        }


        @Test
        void testBuilderWithNullValues()
        {
            CodeList codeList = CodeList.builder().build();

            assertNotNull(codeList);
            assertNull(codeList.getOid());
            assertNull(codeList.getName());
            assertNull(codeList.getDataType());
            assertNull(codeList.getSasFormatName());
            assertNull(codeList.getStandardOID());
            assertNull(codeList.getCommentOID());
            assertNull(codeList.getDescription());
            assertNull(codeList.getCodeListItems());
            assertNull(codeList.getEnumeratedItems());
            assertNull(codeList.getExternalCodeList());
            assertNull(codeList.getAliases());
        }


        @Test
        void testEqualsAndHashCode()
        {
            CodeList codeList1 = CodeList.builder().oid("CL.TEST").name("Test").dataType("text")
                    .build();

            CodeList codeList2 = CodeList.builder().oid("CL.TEST").name("Test").dataType("text")
                    .build();

            assertEquals(codeList1, codeList2);
            assertEquals(codeList1.hashCode(), codeList2.hashCode());
        }


        @Test
        void testToString()
        {
            CodeList codeList = CodeList.builder().oid("CL.SEX").name("Sex").build();

            String str = codeList.toString();
            assertNotNull(str);
            assertTrue(str.contains("CL.SEX"));
            assertTrue(str.contains("Sex"));
        }
    }

    // ========================================================================
    // Integration Tests - Building complex nested structures
    // ========================================================================


    @Nested
    class IntegrationTests
    {

        @Test
        void testCompleteCodeListStructure()
        {
            // Build a complete realistic CodeList structure

            // Create description with translated texts
            TranslatedText descTextEn = new TranslatedText();
            descTextEn.setValue("Code list for patient sex");
            descTextEn.setLang("en");

            TranslatedText descTextDe = new TranslatedText();
            descTextDe.setValue("Codeliste fuer Patientengeschlecht");
            descTextDe.setLang("de");

            Description description = new Description();
            description.setValue("Sex code list");
            description.setLang("en");
            description.setTranslatedTexts(Arrays.asList(descTextEn, descTextDe));

            // Create code list items with decodes
            TranslatedText maleTextEn = new TranslatedText();
            maleTextEn.setValue("Male");
            maleTextEn.setLang("en");

            TranslatedText maleTextDe = new TranslatedText();
            maleTextDe.setValue("Maennlich");
            maleTextDe.setLang("de");

            Decode maleDecode = new Decode();
            maleDecode.setTranslatedTexts(Arrays.asList(maleTextEn, maleTextDe));

            Alias maleAlias = Alias.builder().context("nci:ExtCodeID").name("C20197").build();

            CodeListItem maleItem = CodeListItem.builder().codedValue("M").rank(1.0).orderNumber(1)
                    .decode(maleDecode).aliases(Arrays.asList(maleAlias)).build();

            TranslatedText femaleTextEn = new TranslatedText();
            femaleTextEn.setValue("Female");
            femaleTextEn.setLang("en");

            Decode femaleDecode = new Decode();
            femaleDecode.setTranslatedTexts(Arrays.asList(femaleTextEn));

            CodeListItem femaleItem = CodeListItem.builder().codedValue("F").rank(2.0)
                    .orderNumber(2).decode(femaleDecode).build();

            // Create aliases for code list
            Alias clAlias1 = Alias.builder().context("nci:ExtCodeID").name("C66731").build();

            Alias clAlias2 = Alias.builder().context("CDASH").name("SEX").build();

            // Build the complete CodeList
            CodeList codeList = CodeList.builder().oid("CL.C66731.SEX").name("Sex").dataType("text")
                    .sasFormatName("SEX.").standardOID("STD.SDTM.3.2").description(description)
                    .codeListItems(Arrays.asList(maleItem, femaleItem))
                    .aliases(Arrays.asList(clAlias1, clAlias2)).build();

            // Verify the complete structure
            assertNotNull(codeList);
            assertEquals("CL.C66731.SEX", codeList.getOid());
            assertEquals("Sex", codeList.getName());
            assertEquals("text", codeList.getDataType());
            assertEquals("SEX.", codeList.getSasFormatName());
            assertEquals("STD.SDTM.3.2", codeList.getStandardOID());

            // Verify description
            assertNotNull(codeList.getDescription());
            assertEquals("Sex code list", codeList.getDescription().getValue());
            assertEquals(2, codeList.getDescription().getTranslatedTexts().size());

            // Verify code list items
            assertNotNull(codeList.getCodeListItems());
            assertEquals(2, codeList.getCodeListItems().size());

            // Verify male item
            CodeListItem male = codeList.getCodeListItems().get(0);
            assertEquals("M", male.getCodedValue());
            assertEquals(1.0, male.getRank());
            assertEquals(1, male.getOrderNumber());
            assertNotNull(male.getDecode());
            assertEquals(2, male.getDecode().getTranslatedTexts().size());
            assertNotNull(male.getAliases());
            assertEquals(1, male.getAliases().size());

            // Verify female item
            CodeListItem female = codeList.getCodeListItems().get(1);
            assertEquals("F", female.getCodedValue());
            assertEquals(2.0, female.getRank());
            assertNotNull(female.getDecode());

            // Verify code list aliases
            assertNotNull(codeList.getAliases());
            assertEquals(2, codeList.getAliases().size());
        }


        @Test
        void testCodeListWithExternalReference()
        {
            ExternalCodeList extCodeList = ExternalCodeList.builder().dictionary("MedDRA")
                    .version("24.0").ref("external-ref-123")
                    .href("http://www.meddra.org/codelist/24.0").build();

            Description description = new Description();
            description.setValue("MedDRA Preferred Terms");
            description.setLang("en");

            CodeList codeList = CodeList.builder().oid("CL.MEDDRA.PT")
                    .name("MedDRA Preferred Terms").dataType("text").description(description)
                    .externalCodeList(extCodeList).build();

            assertNotNull(codeList);
            assertEquals("CL.MEDDRA.PT", codeList.getOid());
            assertNotNull(codeList.getExternalCodeList());
            assertEquals("MedDRA", codeList.getExternalCodeList().getDictionary());
            assertEquals("24.0", codeList.getExternalCodeList().getVersion());
            assertEquals("external-ref-123", codeList.getExternalCodeList().getRef());
            assertEquals("http://www.meddra.org/codelist/24.0",
                    codeList.getExternalCodeList().getHref());
        }


        @Test
        void testCodeListWithEnumeratedItems()
        {
            Alias yesAlias = Alias.builder().context("nci:ExtCodeID").name("C49488").build();

            EnumeratedItem yesItem = EnumeratedItem.builder().codedValue("Y").rank(1.0)
                    .orderNumber(1).aliases(Arrays.asList(yesAlias)).build();

            Alias noAlias = Alias.builder().context("nci:ExtCodeID").name("C49487").build();

            EnumeratedItem noItem = EnumeratedItem.builder().codedValue("N").rank(2.0)
                    .orderNumber(2).aliases(Arrays.asList(noAlias)).build();

            CodeList codeList = CodeList.builder().oid("CL.NY").name("No Yes Response")
                    .dataType("text").enumeratedItems(Arrays.asList(yesItem, noItem)).build();

            assertNotNull(codeList);
            assertNotNull(codeList.getEnumeratedItems());
            assertEquals(2, codeList.getEnumeratedItems().size());

            EnumeratedItem yes = codeList.getEnumeratedItems().get(0);
            assertEquals("Y", yes.getCodedValue());
            assertEquals(1.0, yes.getRank());
            assertEquals(1, yes.getOrderNumber());
            assertNotNull(yes.getAliases());
            assertEquals("C49488", yes.getAliases().get(0).getName());
        }


        @Test
        void testMultipleCodeListReferences()
        {
            CodeListRef ref1 = CodeListRef.builder().codeListOID("CL.SEX").build();

            CodeListRef ref2 = CodeListRef.builder().codeListOID("CL.NY").build();

            CodeListRef ref3 = CodeListRef.builder().codeListOID("CL.RACE").build();

            List<CodeListRef> refs = Arrays.asList(ref1, ref2, ref3);

            assertEquals(3, refs.size());
            assertEquals("CL.SEX", refs.get(0).getCodeListOID());
            assertEquals("CL.NY", refs.get(1).getCodeListOID());
            assertEquals("CL.RACE", refs.get(2).getCodeListOID());
        }
    }
}
