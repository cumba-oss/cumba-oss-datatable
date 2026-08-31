package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * JUnit 5 tests for the define package bean classes: - ItemGroupDef (Dataset definition) - ItemDef
 * (Variable definition) - ItemRef (Reference)
 */
public class ItemGroupDefTest
{

    // ========================================================================
    // ItemGroupDef Tests
    // ========================================================================

    @Nested
    class ItemGroupDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Description description = new Description();
            description.setValue("Test Description");

            Leaf leaf = Leaf.builder().id("LEAF001").href("path/to/file.xpt").build();

            ItemRef itemRef = ItemRef.builder().itemOID("IT.VAR1").orderNumber(1).mandatory("Yes")
                    .build();

            Alias alias = Alias.builder().context("CDASH").name("AliasName").build();

            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.DM").name("DM")
                    .repeating("No").isReferenceData("No").sasDatasetName("DM").domain("DM")
                    .purpose("Tabulation").hasNoData("No").structure("One record per subject")
                    .clazz("Special Purpose").archiveLocationID("AL.DM").commentOID("COM.DM")
                    .label("Demographics").domainKeys("STUDYID, USUBJID")
                    .standardOID("STD.SDTM.3.2").description(description)
                    .itemRefs(Collections.singletonList(itemRef))
                    .aliases(Collections.singletonList(alias)).comment("Test comment").leaf(leaf)
                    .build();

            assertEquals("IG.DM", itemGroupDef.getOid());
            assertEquals("DM", itemGroupDef.getName());
            assertEquals("No", itemGroupDef.getRepeating());
            assertEquals("No", itemGroupDef.getIsReferenceData());
            assertEquals("DM", itemGroupDef.getSasDatasetName());
            assertEquals("DM", itemGroupDef.getDomain());
            assertEquals("Tabulation", itemGroupDef.getPurpose());
            assertEquals("No", itemGroupDef.getHasNoData());
            assertEquals("One record per subject", itemGroupDef.getStructure());
            assertEquals("Special Purpose", itemGroupDef.getClazz());
            assertEquals("AL.DM", itemGroupDef.getArchiveLocationID());
            assertEquals("COM.DM", itemGroupDef.getCommentOID());
            assertEquals("Demographics", itemGroupDef.getLabel());
            assertEquals("STUDYID, USUBJID", itemGroupDef.getDomainKeys());
            assertEquals("STD.SDTM.3.2", itemGroupDef.getStandardOID());
            assertNotNull(itemGroupDef.getDescription());
            assertEquals("Test Description", itemGroupDef.getDescription().getValue());
            assertEquals("Test comment", itemGroupDef.getComment());
            assertNotNull(itemGroupDef.getLeaf());
            assertEquals("LEAF001", itemGroupDef.getLeaf().getId());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.AE").name("AE").build();

            assertEquals("IG.AE", itemGroupDef.getOid());
            assertEquals("AE", itemGroupDef.getName());
            assertNull(itemGroupDef.getRepeating());
            assertNull(itemGroupDef.getIsReferenceData());
            assertNull(itemGroupDef.getSasDatasetName());
            assertNull(itemGroupDef.getDescription());
            assertNull(itemGroupDef.getItemRefs());
            assertNull(itemGroupDef.getAliases());
            assertNull(itemGroupDef.getLeaf());
        }


        @Test
        void testItemRefsList()
        {
            ItemRef itemRef1 = ItemRef.builder().itemOID("IT.STUDYID").orderNumber(1)
                    .mandatory("Yes").keySequence(1).build();

            ItemRef itemRef2 = ItemRef.builder().itemOID("IT.USUBJID").orderNumber(2)
                    .mandatory("Yes").keySequence(2).build();

            ItemRef itemRef3 = ItemRef.builder().itemOID("IT.AGE").orderNumber(3).mandatory("No")
                    .build();

            List<ItemRef> itemRefs = Arrays.asList(itemRef1, itemRef2, itemRef3);

            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.DM").name("DM")
                    .itemRefs(itemRefs).build();

            assertNotNull(itemGroupDef.getItemRefs());
            assertEquals(3, itemGroupDef.getItemRefs().size());
            assertEquals("IT.STUDYID", itemGroupDef.getItemRefs().get(0).getItemOID());
            assertEquals("IT.USUBJID", itemGroupDef.getItemRefs().get(1).getItemOID());
            assertEquals("IT.AGE", itemGroupDef.getItemRefs().get(2).getItemOID());
        }


        @Test
        void testAliasesList()
        {
            Alias alias1 = Alias.builder().context("CDASH").name("DemographicsForm").build();

            Alias alias2 = Alias.builder().context("SDTMIG").name("DM").build();

            List<Alias> aliases = Arrays.asList(alias1, alias2);

            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.DM").name("DM")
                    .aliases(aliases).build();

            assertNotNull(itemGroupDef.getAliases());
            assertEquals(2, itemGroupDef.getAliases().size());
            assertEquals("CDASH", itemGroupDef.getAliases().get(0).getContext());
            assertEquals("SDTMIG", itemGroupDef.getAliases().get(1).getContext());
        }


        @Test
        void testNestedLeafObject()
        {
            Title title = new Title();
            title.setValue("Demographics Dataset");

            Leaf leaf = Leaf.builder().id("LF.DM").href("dm.xpt").title(title).build();

            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.DM").name("DM").leaf(leaf)
                    .build();

            assertNotNull(itemGroupDef.getLeaf());
            assertEquals("LF.DM", itemGroupDef.getLeaf().getId());
            assertEquals("dm.xpt", itemGroupDef.getLeaf().getHref());
            assertNotNull(itemGroupDef.getLeaf().getTitle());
            assertEquals("Demographics Dataset", itemGroupDef.getLeaf().getTitle().getValue());
        }


        @Test
        void testNestedDescriptionObject()
        {
            Description description = new Description();
            description.setValue("Demographics domain");
            description.setLang("en");

            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.DM").name("DM")
                    .description(description).build();

            assertNotNull(itemGroupDef.getDescription());
            assertEquals("Demographics domain", itemGroupDef.getDescription().getValue());
            assertEquals("en", itemGroupDef.getDescription().getLang());
        }


        @Test
        void testEmptyLists()
        {
            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.EMPTY").name("EMPTY")
                    .itemRefs(Collections.emptyList()).aliases(Collections.emptyList()).build();

            assertNotNull(itemGroupDef.getItemRefs());
            assertTrue(itemGroupDef.getItemRefs().isEmpty());
            assertNotNull(itemGroupDef.getAliases());
            assertTrue(itemGroupDef.getAliases().isEmpty());
        }
    }

    // ========================================================================
    // ItemDef Tests
    // ========================================================================


    @Nested
    class ItemDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Description description = new Description();
            description.setValue("Subject Age");

            CodeListRef codeListRef = CodeListRef.builder().codeListOID("CL.SEX").build();

            ValueListRef valueListRef = ValueListRef.builder().valueListOID("VL.QVAL").build();

            Origin originElement = Origin.builder().type("Derived").source("Sponsor").build();

            Alias alias = Alias.builder().context("CDASH").name("AgeAlias").build();

            ItemDef itemDef = ItemDef.builder().oid("IT.AGE").name("AGE").dataType("integer")
                    .length(3).significantDigits(0).sasFieldName("AGE").displayFormat("3.")
                    .commentOID("COM.AGE").origin("CRF").standardOID("STD.SDTM.3.2").label("Age")
                    .description(description).codeListRef(codeListRef).valueListRef(valueListRef)
                    .originElement(originElement).aliases(Collections.singletonList(alias))
                    .comment("Age in years").build();

            assertEquals("IT.AGE", itemDef.getOid());
            assertEquals("AGE", itemDef.getName());
            assertEquals("integer", itemDef.getDataType());
            assertEquals(Integer.valueOf(3), itemDef.getLength());
            assertEquals(Integer.valueOf(0), itemDef.getSignificantDigits());
            assertEquals("AGE", itemDef.getSasFieldName());
            assertEquals("3.", itemDef.getDisplayFormat());
            assertEquals("COM.AGE", itemDef.getCommentOID());
            assertEquals("CRF", itemDef.getOrigin());
            assertEquals("STD.SDTM.3.2", itemDef.getStandardOID());
            assertEquals("Age", itemDef.getLabel());
            assertNotNull(itemDef.getDescription());
            assertEquals("Subject Age", itemDef.getDescription().getValue());
            assertNotNull(itemDef.getCodeListRef());
            assertEquals("CL.SEX", itemDef.getCodeListRef().getCodeListOID());
            assertNotNull(itemDef.getValueListRef());
            assertEquals("VL.QVAL", itemDef.getValueListRef().getValueListOID());
            assertNotNull(itemDef.getOriginElement());
            assertEquals("Derived", itemDef.getOriginElement().getType());
            assertNotNull(itemDef.getAliases());
            assertEquals(1, itemDef.getAliases().size());
            assertEquals("Age in years", itemDef.getComment());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            ItemDef itemDef = ItemDef.builder().oid("IT.VAR1").name("VAR1").dataType("text")
                    .build();

            assertEquals("IT.VAR1", itemDef.getOid());
            assertEquals("VAR1", itemDef.getName());
            assertEquals("text", itemDef.getDataType());
            assertNull(itemDef.getLength());
            assertNull(itemDef.getSignificantDigits());
            assertNull(itemDef.getSasFieldName());
            assertNull(itemDef.getCodeListRef());
            assertNull(itemDef.getValueListRef());
            assertNull(itemDef.getOriginElement());
        }


        @Test
        void testNumericDataType()
        {
            ItemDef itemDef = ItemDef.builder().oid("IT.WEIGHT").name("WEIGHT").dataType("float")
                    .length(8).significantDigits(2).sasFieldName("WEIGHT").displayFormat("8.2")
                    .label("Weight in kg").build();

            assertEquals("float", itemDef.getDataType());
            assertEquals(Integer.valueOf(8), itemDef.getLength());
            assertEquals(Integer.valueOf(2), itemDef.getSignificantDigits());
            assertEquals("8.2", itemDef.getDisplayFormat());
        }


        @Test
        void testCodeListRef()
        {
            CodeListRef codeListRef = CodeListRef.builder().codeListOID("CL.RACE").build();

            ItemDef itemDef = ItemDef.builder().oid("IT.RACE").name("RACE").dataType("text")
                    .length(50).codeListRef(codeListRef).build();

            assertNotNull(itemDef.getCodeListRef());
            assertEquals("CL.RACE", itemDef.getCodeListRef().getCodeListOID());
        }


        @Test
        void testValueListRef()
        {
            ValueListRef valueListRef = ValueListRef.builder().valueListOID("VL.LBTEST").build();

            ItemDef itemDef = ItemDef.builder().oid("IT.LBORRES").name("LBORRES").dataType("text")
                    .valueListRef(valueListRef).build();

            assertNotNull(itemDef.getValueListRef());
            assertEquals("VL.LBTEST", itemDef.getValueListRef().getValueListOID());
        }


        @Test
        void testOriginElement()
        {
            Description originDesc = new Description();
            originDesc.setValue("Calculated from birth date");

            Origin originElement = Origin.builder().type("Derived").source("Sponsor")
                    .commentOID("COM.ORIGIN.AGE").description(originDesc).build();

            ItemDef itemDef = ItemDef.builder().oid("IT.AGE").name("AGE").dataType("integer")
                    .originElement(originElement).build();

            assertNotNull(itemDef.getOriginElement());
            assertEquals("Derived", itemDef.getOriginElement().getType());
            assertEquals("Sponsor", itemDef.getOriginElement().getSource());
            assertEquals("COM.ORIGIN.AGE", itemDef.getOriginElement().getCommentOID());
            assertNotNull(itemDef.getOriginElement().getDescription());
        }


        @Test
        void testAliasesList()
        {
            Alias alias1 = Alias.builder().context("CDASH").name("SubjectAge").build();

            Alias alias2 = Alias.builder().context("SDTMIG").name("AGE").build();

            Alias alias3 = Alias.builder().context("nci:ExtCodeID").name("C25150").build();

            List<Alias> aliases = Arrays.asList(alias1, alias2, alias3);

            ItemDef itemDef = ItemDef.builder().oid("IT.AGE").name("AGE").dataType("integer")
                    .aliases(aliases).build();

            assertNotNull(itemDef.getAliases());
            assertEquals(3, itemDef.getAliases().size());
            assertEquals("CDASH", itemDef.getAliases().get(0).getContext());
            assertEquals("SubjectAge", itemDef.getAliases().get(0).getName());
            assertEquals("SDTMIG", itemDef.getAliases().get(1).getContext());
            assertEquals("nci:ExtCodeID", itemDef.getAliases().get(2).getContext());
        }


        @Test
        void testEmptyAliasesList()
        {
            ItemDef itemDef = ItemDef.builder().oid("IT.VAR1").name("VAR1").dataType("text")
                    .aliases(Collections.emptyList()).build();

            assertNotNull(itemDef.getAliases());
            assertTrue(itemDef.getAliases().isEmpty());
        }
    }

    // ========================================================================
    // ItemRef Tests
    // ========================================================================


    @Nested
    class ItemRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            WhereClauseRef whereClauseRef = WhereClauseRef.builder()
                    .whereClauseOID("WC.LBTESTCD.ALT").build();

            ItemRef itemRef = ItemRef.builder().itemOID("IT.LBORRES").orderNumber(5)
                    .mandatory("Yes").keySequence(3).methodOID("MT.LBORRES")
                    .role("Result Qualifier").roleCodeListOID("CL.ROLE").hasNoData("No")
                    .whereClauseRefs(Collections.singletonList(whereClauseRef)).build();

            assertEquals("IT.LBORRES", itemRef.getItemOID());
            assertEquals(Integer.valueOf(5), itemRef.getOrderNumber());
            assertEquals("Yes", itemRef.getMandatory());
            assertEquals(Integer.valueOf(3), itemRef.getKeySequence());
            assertEquals("MT.LBORRES", itemRef.getMethodOID());
            assertEquals("Result Qualifier", itemRef.getRole());
            assertEquals("CL.ROLE", itemRef.getRoleCodeListOID());
            assertEquals("No", itemRef.getHasNoData());
            assertNotNull(itemRef.getWhereClauseRefs());
            assertEquals(1, itemRef.getWhereClauseRefs().size());
            assertEquals("WC.LBTESTCD.ALT",
                    itemRef.getWhereClauseRefs().get(0).getWhereClauseOID());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.STUDYID").build();

            assertEquals("IT.STUDYID", itemRef.getItemOID());
            assertNull(itemRef.getOrderNumber());
            assertNull(itemRef.getMandatory());
            assertNull(itemRef.getKeySequence());
            assertNull(itemRef.getMethodOID());
            assertNull(itemRef.getRole());
            assertNull(itemRef.getRoleCodeListOID());
            assertNull(itemRef.getHasNoData());
            assertNull(itemRef.getWhereClauseRefs());
        }


        @Test
        void testKeyVariable()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.USUBJID").orderNumber(2)
                    .mandatory("Yes").keySequence(2).build();

            assertEquals("IT.USUBJID", itemRef.getItemOID());
            assertEquals(Integer.valueOf(2), itemRef.getOrderNumber());
            assertEquals("Yes", itemRef.getMandatory());
            assertEquals(Integer.valueOf(2), itemRef.getKeySequence());
        }


        @Test
        void testNonKeyVariable()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.AETERM").orderNumber(10).mandatory("No")
                    .build();

            assertEquals("IT.AETERM", itemRef.getItemOID());
            assertEquals(Integer.valueOf(10), itemRef.getOrderNumber());
            assertEquals("No", itemRef.getMandatory());
            assertNull(itemRef.getKeySequence());
        }


        @Test
        void testWhereClauseRefsList()
        {
            WhereClauseRef wcRef1 = WhereClauseRef.builder().whereClauseOID("WC.LBTESTCD.ALT")
                    .build();

            WhereClauseRef wcRef2 = WhereClauseRef.builder().whereClauseOID("WC.LBTESTCD.AST")
                    .build();

            WhereClauseRef wcRef3 = WhereClauseRef.builder().whereClauseOID("WC.LBTESTCD.BILI")
                    .build();

            List<WhereClauseRef> whereClauseRefs = Arrays.asList(wcRef1, wcRef2, wcRef3);

            ItemRef itemRef = ItemRef.builder().itemOID("IT.LBORRES").orderNumber(5)
                    .whereClauseRefs(whereClauseRefs).build();

            assertNotNull(itemRef.getWhereClauseRefs());
            assertEquals(3, itemRef.getWhereClauseRefs().size());
            assertEquals("WC.LBTESTCD.ALT",
                    itemRef.getWhereClauseRefs().get(0).getWhereClauseOID());
            assertEquals("WC.LBTESTCD.AST",
                    itemRef.getWhereClauseRefs().get(1).getWhereClauseOID());
            assertEquals("WC.LBTESTCD.BILI",
                    itemRef.getWhereClauseRefs().get(2).getWhereClauseOID());
        }


        @Test
        void testEmptyWhereClauseRefsList()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.VAR1")
                    .whereClauseRefs(Collections.emptyList()).build();

            assertNotNull(itemRef.getWhereClauseRefs());
            assertTrue(itemRef.getWhereClauseRefs().isEmpty());
        }


        @Test
        void testRoleProperties()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.DOMAIN").orderNumber(3).mandatory("Yes")
                    .role("Identifier").roleCodeListOID("CL.ROLE.IDENTIFIER").build();

            assertEquals("Identifier", itemRef.getRole());
            assertEquals("CL.ROLE.IDENTIFIER", itemRef.getRoleCodeListOID());
        }


        @Test
        void testMethodOID()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.AESTDY").orderNumber(15).mandatory("No")
                    .methodOID("MT.STUDYDAY").build();

            assertEquals("IT.AESTDY", itemRef.getItemOID());
            assertEquals("MT.STUDYDAY", itemRef.getMethodOID());
        }


        @Test
        void testHasNoData()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.OPTIONAL").orderNumber(20)
                    .mandatory("No").hasNoData("Yes").build();

            assertEquals("Yes", itemRef.getHasNoData());
        }
    }

    // ========================================================================
    // Supporting Class Tests
    // ========================================================================


    @Nested
    class SupportingClassTests
    {

        @Test
        void testAliasBuilder()
        {
            Alias alias = Alias.builder().context("CDASH").name("SubjectIdentifier").build();

            assertEquals("CDASH", alias.getContext());
            assertEquals("SubjectIdentifier", alias.getName());
        }


        @Test
        void testCodeListRefBuilder()
        {
            CodeListRef codeListRef = CodeListRef.builder().codeListOID("CL.SEX").build();

            assertEquals("CL.SEX", codeListRef.getCodeListOID());
        }


        @Test
        void testValueListRefBuilder()
        {
            ValueListRef valueListRef = ValueListRef.builder().valueListOID("VL.QVAL").build();

            assertEquals("VL.QVAL", valueListRef.getValueListOID());
        }


        @Test
        void testWhereClauseRefBuilder()
        {
            WhereClauseRef whereClauseRef = WhereClauseRef.builder()
                    .whereClauseOID("WC.LBTESTCD.ALT").build();

            assertEquals("WC.LBTESTCD.ALT", whereClauseRef.getWhereClauseOID());
        }


        @Test
        void testLeafBuilder()
        {
            Title title = new Title();
            title.setValue("Test Dataset");

            Leaf leaf = Leaf.builder().id("LF.TEST").href("test.xpt").title(title).build();

            assertEquals("LF.TEST", leaf.getId());
            assertEquals("test.xpt", leaf.getHref());
            assertNotNull(leaf.getTitle());
            assertEquals("Test Dataset", leaf.getTitle().getValue());
        }


        @Test
        void testOriginBuilder()
        {
            Description description = new Description();
            description.setValue("Origin description");

            Origin origin = Origin.builder().type("Derived").source("Sponsor")
                    .commentOID("COM.ORIGIN").description(description).build();

            assertEquals("Derived", origin.getType());
            assertEquals("Sponsor", origin.getSource());
            assertEquals("COM.ORIGIN", origin.getCommentOID());
            assertNotNull(origin.getDescription());
            assertEquals("Origin description", origin.getDescription().getValue());
        }


        @Test
        void testDescriptionWithLang()
        {
            Description description = new Description();
            description.setValue("Test description text");
            description.setLang("en");

            assertEquals("Test description text", description.getValue());
            assertEquals("en", description.getLang());
        }


        @Test
        void testTitleValue()
        {
            Title title = new Title();
            title.setValue("Dataset Title");

            assertEquals("Dataset Title", title.getValue());
        }
    }

    // ========================================================================
    // Integration Tests - Complex Nested Structures
    // ========================================================================


    @Nested
    class IntegrationTests
    {

        @Test
        void testCompleteItemGroupDefStructure()
        {
            // Create Description
            Description description = new Description();
            description.setValue("Adverse Events");
            description.setLang("en");

            // Create Title and Leaf
            Title title = new Title();
            title.setValue("Adverse Events Dataset");

            Leaf leaf = Leaf.builder().id("LF.AE").href("ae.xpt").title(title).build();

            // Create ItemRefs
            ItemRef itemRef1 = ItemRef.builder().itemOID("IT.STUDYID").orderNumber(1)
                    .mandatory("Yes").keySequence(1).build();

            ItemRef itemRef2 = ItemRef.builder().itemOID("IT.USUBJID").orderNumber(2)
                    .mandatory("Yes").keySequence(2).build();

            ItemRef itemRef3 = ItemRef.builder().itemOID("IT.AETERM").orderNumber(3)
                    .mandatory("Yes").role("Topic").build();

            // Create Aliases
            Alias alias1 = Alias.builder().context("CDASH").name("AdverseEvents").build();

            Alias alias2 = Alias.builder().context("SDTMIG").name("AE").build();

            // Build complete ItemGroupDef
            ItemGroupDef itemGroupDef = ItemGroupDef.builder().oid("IG.AE").name("AE")
                    .repeating("Yes").isReferenceData("No").sasDatasetName("AE").domain("AE")
                    .purpose("Tabulation").hasNoData("No")
                    .structure("One record per adverse event per subject").clazz("Events")
                    .archiveLocationID("AL.AE").commentOID("COM.AE").label("Adverse Events")
                    .domainKeys("STUDYID, USUBJID, AESEQ").standardOID("STD.SDTM.3.2")
                    .description(description).itemRefs(Arrays.asList(itemRef1, itemRef2, itemRef3))
                    .aliases(Arrays.asList(alias1, alias2)).comment("Adverse events domain")
                    .leaf(leaf).build();

            // Verify all properties
            assertEquals("IG.AE", itemGroupDef.getOid());
            assertEquals("AE", itemGroupDef.getName());
            assertEquals("Yes", itemGroupDef.getRepeating());
            assertEquals(3, itemGroupDef.getItemRefs().size());
            assertEquals(2, itemGroupDef.getAliases().size());

            // Verify nested structures
            assertNotNull(itemGroupDef.getDescription());
            assertEquals("Adverse Events", itemGroupDef.getDescription().getValue());

            assertNotNull(itemGroupDef.getLeaf());
            assertEquals("ae.xpt", itemGroupDef.getLeaf().getHref());
            assertEquals("Adverse Events Dataset", itemGroupDef.getLeaf().getTitle().getValue());

            // Verify ItemRefs
            assertEquals("IT.STUDYID", itemGroupDef.getItemRefs().get(0).getItemOID());
            assertEquals(Integer.valueOf(1), itemGroupDef.getItemRefs().get(0).getKeySequence());
            assertEquals("Topic", itemGroupDef.getItemRefs().get(2).getRole());
        }


        @Test
        void testCompleteItemDefStructure()
        {
            // Create Description
            Description description = new Description();
            description.setValue("Laboratory Result");

            // Create CodeListRef
            CodeListRef codeListRef = CodeListRef.builder().codeListOID("CL.LBTEST").build();

            // Create ValueListRef
            ValueListRef valueListRef = ValueListRef.builder().valueListOID("VL.LBORRES").build();

            // Create Origin
            Origin originElement = Origin.builder().type("CRF").source("Investigator").build();

            // Create Aliases
            Alias alias1 = Alias.builder().context("CDASH").name("LabResult").build();

            Alias alias2 = Alias.builder().context("nci:ExtCodeID").name("C25299").build();

            // Build complete ItemDef
            ItemDef itemDef = ItemDef.builder().oid("IT.LBORRES").name("LBORRES").dataType("text")
                    .length(200).sasFieldName("LBORRES").displayFormat("$200.")
                    .commentOID("COM.LBORRES").origin("CRF").standardOID("STD.SDTM.3.2")
                    .label("Result or Finding in Original Units").description(description)
                    .codeListRef(codeListRef).valueListRef(valueListRef)
                    .originElement(originElement).aliases(Arrays.asList(alias1, alias2))
                    .comment("Lab result value").build();

            // Verify all properties
            assertEquals("IT.LBORRES", itemDef.getOid());
            assertEquals("LBORRES", itemDef.getName());
            assertEquals("text", itemDef.getDataType());
            assertEquals(Integer.valueOf(200), itemDef.getLength());

            // Verify nested structures
            assertNotNull(itemDef.getDescription());
            assertEquals("Laboratory Result", itemDef.getDescription().getValue());

            assertNotNull(itemDef.getCodeListRef());
            assertEquals("CL.LBTEST", itemDef.getCodeListRef().getCodeListOID());

            assertNotNull(itemDef.getValueListRef());
            assertEquals("VL.LBORRES", itemDef.getValueListRef().getValueListOID());

            assertNotNull(itemDef.getOriginElement());
            assertEquals("CRF", itemDef.getOriginElement().getType());

            assertEquals(2, itemDef.getAliases().size());
        }


        @Test
        void testItemRefWithMultipleWhereClauseRefs()
        {
            WhereClauseRef wcRef1 = WhereClauseRef.builder().whereClauseOID("WC.PARAMCD.SYSBP")
                    .build();

            WhereClauseRef wcRef2 = WhereClauseRef.builder().whereClauseOID("WC.PARAMCD.DIABP")
                    .build();

            WhereClauseRef wcRef3 = WhereClauseRef.builder().whereClauseOID("WC.PARAMCD.PULSE")
                    .build();

            WhereClauseRef wcRef4 = WhereClauseRef.builder().whereClauseOID("WC.PARAMCD.HEIGHT")
                    .build();

            ItemRef itemRef = ItemRef.builder().itemOID("IT.AVAL").orderNumber(10).mandatory("Yes")
                    .methodOID("MT.AVAL.CALC").role("Record Qualifier").roleCodeListOID("CL.ROLE")
                    .whereClauseRefs(Arrays.asList(wcRef1, wcRef2, wcRef3, wcRef4)).build();

            assertNotNull(itemRef.getWhereClauseRefs());
            assertEquals(4, itemRef.getWhereClauseRefs().size());

            // Verify each where clause ref
            assertEquals("WC.PARAMCD.SYSBP",
                    itemRef.getWhereClauseRefs().get(0).getWhereClauseOID());
            assertEquals("WC.PARAMCD.DIABP",
                    itemRef.getWhereClauseRefs().get(1).getWhereClauseOID());
            assertEquals("WC.PARAMCD.PULSE",
                    itemRef.getWhereClauseRefs().get(2).getWhereClauseOID());
            assertEquals("WC.PARAMCD.HEIGHT",
                    itemRef.getWhereClauseRefs().get(3).getWhereClauseOID());
        }
    }
}
