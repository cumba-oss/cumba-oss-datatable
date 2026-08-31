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

public class ValueListDefTest
{

    // ========================================================================
    // ValueListDef Tests
    // ========================================================================
    @Nested
    class ValueListDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.TEST").orderNumber(1).build();

            ValueListDef valueListDef = ValueListDef.builder().oid("VL.TEST").commentOID("COM.TEST")
                    .itemRefs(Collections.singletonList(itemRef)).build();

            assertEquals("VL.TEST", valueListDef.getOid());
            assertEquals("COM.TEST", valueListDef.getCommentOID());
            assertNotNull(valueListDef.getItemRefs());
            assertEquals(1, valueListDef.getItemRefs().size());
            assertEquals("IT.TEST", valueListDef.getItemRefs().get(0).getItemOID());
        }


        @Test
        void testBuilderWithNullOptionalFields()
        {
            ValueListDef valueListDef = ValueListDef.builder().oid("VL.MINIMAL").build();

            assertEquals("VL.MINIMAL", valueListDef.getOid());
            assertNull(valueListDef.getCommentOID());
            assertNull(valueListDef.getItemRefs());
        }


        @Test
        void testBuilderWithMultipleItemRefs()
        {
            ItemRef itemRef1 = ItemRef.builder().itemOID("IT.A").orderNumber(1).build();
            ItemRef itemRef2 = ItemRef.builder().itemOID("IT.B").orderNumber(2).build();
            ItemRef itemRef3 = ItemRef.builder().itemOID("IT.C").orderNumber(3).build();

            ValueListDef valueListDef = ValueListDef.builder().oid("VL.MULTI")
                    .itemRefs(Arrays.asList(itemRef1, itemRef2, itemRef3)).build();

            assertEquals(3, valueListDef.getItemRefs().size());
            assertEquals("IT.A", valueListDef.getItemRefs().get(0).getItemOID());
            assertEquals("IT.B", valueListDef.getItemRefs().get(1).getItemOID());
            assertEquals("IT.C", valueListDef.getItemRefs().get(2).getItemOID());
        }


        @Test
        void testBuilderWithEmptyItemRefList()
        {
            ValueListDef valueListDef = ValueListDef.builder().oid("VL.EMPTY")
                    .itemRefs(Collections.emptyList()).build();

            assertNotNull(valueListDef.getItemRefs());
            assertTrue(valueListDef.getItemRefs().isEmpty());
        }
    }


    // ========================================================================
    // ValueListRef Tests
    // ========================================================================
    @Nested
    class ValueListRefTests
    {

        @Test
        void testBuilderWithValueListOID()
        {
            ValueListRef valueListRef = ValueListRef.builder().valueListOID("VL.TEST.REF").build();

            assertEquals("VL.TEST.REF", valueListRef.getValueListOID());
        }


        @Test
        void testBuilderWithNullValueListOID()
        {
            ValueListRef valueListRef = ValueListRef.builder().build();

            assertNull(valueListRef.getValueListOID());
        }
    }


    // ========================================================================
    // WhereClauseDef Tests
    // ========================================================================
    @Nested
    class WhereClauseDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            CheckValue checkValue = new CheckValue();
            checkValue.setValue("TEST_VALUE");

            RangeCheck rangeCheck = RangeCheck.builder().comparator("EQ").softHard("Soft")
                    .itemOID("IT.TEST").checkValues(Collections.singletonList(checkValue)).build();

            WhereClauseDef whereClauseDef = WhereClauseDef.builder().oid("WC.TEST")
                    .commentOID("COM.WC").rangeChecks(Collections.singletonList(rangeCheck))
                    .build();

            assertEquals("WC.TEST", whereClauseDef.getOid());
            assertEquals("COM.WC", whereClauseDef.getCommentOID());
            assertNotNull(whereClauseDef.getRangeChecks());
            assertEquals(1, whereClauseDef.getRangeChecks().size());
            assertEquals("EQ", whereClauseDef.getRangeChecks().get(0).getComparator());
        }


        @Test
        void testBuilderWithNullOptionalFields()
        {
            WhereClauseDef whereClauseDef = WhereClauseDef.builder().oid("WC.MINIMAL").build();

            assertEquals("WC.MINIMAL", whereClauseDef.getOid());
            assertNull(whereClauseDef.getCommentOID());
            assertNull(whereClauseDef.getRangeChecks());
        }


        @Test
        void testBuilderWithMultipleRangeChecks()
        {
            RangeCheck rc1 = RangeCheck.builder().comparator("EQ").itemOID("IT.A").build();
            RangeCheck rc2 = RangeCheck.builder().comparator("NE").itemOID("IT.B").build();

            WhereClauseDef whereClauseDef = WhereClauseDef.builder().oid("WC.MULTI")
                    .rangeChecks(Arrays.asList(rc1, rc2)).build();

            assertEquals(2, whereClauseDef.getRangeChecks().size());
            assertEquals("EQ", whereClauseDef.getRangeChecks().get(0).getComparator());
            assertEquals("NE", whereClauseDef.getRangeChecks().get(1).getComparator());
        }
    }


    // ========================================================================
    // WhereClauseRef Tests
    // ========================================================================
    @Nested
    class WhereClauseRefTests
    {

        @Test
        void testBuilderWithWhereClauseOID()
        {
            WhereClauseRef whereClauseRef = WhereClauseRef.builder().whereClauseOID("WC.TEST.REF")
                    .build();

            assertEquals("WC.TEST.REF", whereClauseRef.getWhereClauseOID());
        }


        @Test
        void testBuilderWithNullWhereClauseOID()
        {
            WhereClauseRef whereClauseRef = WhereClauseRef.builder().build();

            assertNull(whereClauseRef.getWhereClauseOID());
        }
    }


    // ========================================================================
    // RangeCheck Tests
    // ========================================================================
    @Nested
    class RangeCheckTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            CheckValue checkValue = new CheckValue();
            checkValue.setValue("100");

            MeasurementUnitRef muRef = MeasurementUnitRef.builder().measurementUnitOID("MU.KG")
                    .build();

            RangeCheck rangeCheck = RangeCheck.builder().comparator("LE").softHard("Hard")
                    .itemOID("IT.WEIGHT").checkValues(Collections.singletonList(checkValue))
                    .measurementUnitRef(muRef).build();

            assertEquals("LE", rangeCheck.getComparator());
            assertEquals("Hard", rangeCheck.getSoftHard());
            assertEquals("IT.WEIGHT", rangeCheck.getItemOID());
            assertNotNull(rangeCheck.getCheckValues());
            assertEquals(1, rangeCheck.getCheckValues().size());
            assertEquals("100", rangeCheck.getCheckValues().get(0).getValue());
            assertNotNull(rangeCheck.getMeasurementUnitRef());
            assertEquals("MU.KG", rangeCheck.getMeasurementUnitRef().getMeasurementUnitOID());
        }


        @Test
        void testBuilderWithNullOptionalFields()
        {
            RangeCheck rangeCheck = RangeCheck.builder().comparator("EQ").itemOID("IT.STATUS")
                    .build();

            assertEquals("EQ", rangeCheck.getComparator());
            assertEquals("IT.STATUS", rangeCheck.getItemOID());
            assertNull(rangeCheck.getSoftHard());
            assertNull(rangeCheck.getCheckValues());
            assertNull(rangeCheck.getMeasurementUnitRef());
        }


        @Test
        void testBuilderWithMultipleCheckValues()
        {
            CheckValue cv1 = new CheckValue();
            cv1.setValue("A");
            CheckValue cv2 = new CheckValue();
            cv2.setValue("B");
            CheckValue cv3 = new CheckValue();
            cv3.setValue("C");

            RangeCheck rangeCheck = RangeCheck.builder().comparator("IN").itemOID("IT.CODE")
                    .checkValues(Arrays.asList(cv1, cv2, cv3)).build();

            assertEquals(3, rangeCheck.getCheckValues().size());
            assertEquals("A", rangeCheck.getCheckValues().get(0).getValue());
            assertEquals("B", rangeCheck.getCheckValues().get(1).getValue());
            assertEquals("C", rangeCheck.getCheckValues().get(2).getValue());
        }


        @Test
        void testDifferentComparatorValues()
        {
            List<String> comparators = Arrays.asList("EQ", "NE", "LT", "LE", "GT", "GE", "IN",
                    "NOTIN");

            for (String comparator : comparators)
            {
                RangeCheck rangeCheck = RangeCheck.builder().comparator(comparator)
                        .itemOID("IT.TEST").build();
                assertEquals(comparator, rangeCheck.getComparator());
            }
        }


        @Test
        void testSoftHardValues()
        {
            RangeCheck softCheck = RangeCheck.builder().comparator("EQ").softHard("Soft")
                    .itemOID("IT.TEST").build();
            assertEquals("Soft", softCheck.getSoftHard());

            RangeCheck hardCheck = RangeCheck.builder().comparator("EQ").softHard("Hard")
                    .itemOID("IT.TEST").build();
            assertEquals("Hard", hardCheck.getSoftHard());
        }
    }


    // ========================================================================
    // CheckValue Tests
    // ========================================================================
    @Nested
    class CheckValueTests
    {

        @Test
        void testDefaultConstructor()
        {
            CheckValue checkValue = new CheckValue();
            assertNull(checkValue.getValue());
        }


        @Test
        void testSetAndGetValue()
        {
            CheckValue checkValue = new CheckValue();
            checkValue.setValue("TEST_VALUE");
            assertEquals("TEST_VALUE", checkValue.getValue());
        }


        @Test
        void testSetValueToNull()
        {
            CheckValue checkValue = new CheckValue();
            checkValue.setValue("INITIAL");
            assertEquals("INITIAL", checkValue.getValue());
            checkValue.setValue(null);
            assertNull(checkValue.getValue());
        }


        @Test
        void testSetValueWithDifferentTypes()
        {
            CheckValue checkValue = new CheckValue();

            checkValue.setValue("STRING_VALUE");
            assertEquals("STRING_VALUE", checkValue.getValue());

            checkValue.setValue("123");
            assertEquals("123", checkValue.getValue());

            checkValue.setValue("45.67");
            assertEquals("45.67", checkValue.getValue());

            checkValue.setValue("");
            assertEquals("", checkValue.getValue());
        }
    }


    // ========================================================================
    // MeasurementUnitRef Tests
    // ========================================================================
    @Nested
    class MeasurementUnitRefTests
    {

        @Test
        void testBuilderWithMeasurementUnitOID()
        {
            MeasurementUnitRef muRef = MeasurementUnitRef.builder().measurementUnitOID("MU.KG")
                    .build();

            assertEquals("MU.KG", muRef.getMeasurementUnitOID());
        }


        @Test
        void testBuilderWithNullMeasurementUnitOID()
        {
            MeasurementUnitRef muRef = MeasurementUnitRef.builder().build();

            assertNull(muRef.getMeasurementUnitOID());
        }


        @Test
        void testDifferentMeasurementUnits()
        {
            List<String> units = Arrays.asList("MU.KG", "MU.LB", "MU.CM", "MU.IN", "MU.MG",
                    "MU.ML");

            for (String unit : units)
            {
                MeasurementUnitRef muRef = MeasurementUnitRef.builder().measurementUnitOID(unit)
                        .build();
                assertEquals(unit, muRef.getMeasurementUnitOID());
            }
        }
    }


    // ========================================================================
    // ItemRef Tests (supporting class)
    // ========================================================================
    @Nested
    class ItemRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            WhereClauseRef wcRef = WhereClauseRef.builder().whereClauseOID("WC.TEST").build();

            ItemRef itemRef = ItemRef.builder().itemOID("IT.SUBJID").orderNumber(1).mandatory("Yes")
                    .keySequence(1).methodOID("MT.DERIVE").role("Identifier")
                    .roleCodeListOID("CL.ROLE").hasNoData("No")
                    .whereClauseRefs(Collections.singletonList(wcRef)).build();

            assertEquals("IT.SUBJID", itemRef.getItemOID());
            assertEquals(Integer.valueOf(1), itemRef.getOrderNumber());
            assertEquals("Yes", itemRef.getMandatory());
            assertEquals(Integer.valueOf(1), itemRef.getKeySequence());
            assertEquals("MT.DERIVE", itemRef.getMethodOID());
            assertEquals("Identifier", itemRef.getRole());
            assertEquals("CL.ROLE", itemRef.getRoleCodeListOID());
            assertEquals("No", itemRef.getHasNoData());
            assertNotNull(itemRef.getWhereClauseRefs());
            assertEquals(1, itemRef.getWhereClauseRefs().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            ItemRef itemRef = ItemRef.builder().itemOID("IT.MINIMAL").build();

            assertEquals("IT.MINIMAL", itemRef.getItemOID());
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
        void testBuilderWithMultipleWhereClauseRefs()
        {
            WhereClauseRef wcRef1 = WhereClauseRef.builder().whereClauseOID("WC.A").build();
            WhereClauseRef wcRef2 = WhereClauseRef.builder().whereClauseOID("WC.B").build();

            ItemRef itemRef = ItemRef.builder().itemOID("IT.MULTI")
                    .whereClauseRefs(Arrays.asList(wcRef1, wcRef2)).build();

            assertEquals(2, itemRef.getWhereClauseRefs().size());
            assertEquals("WC.A", itemRef.getWhereClauseRefs().get(0).getWhereClauseOID());
            assertEquals("WC.B", itemRef.getWhereClauseRefs().get(1).getWhereClauseOID());
        }
    }


    // ========================================================================
    // Integration Tests - Testing nested structures
    // ========================================================================
    @Nested
    class IntegrationTests
    {

        @Test
        void testCompleteValueListDefStructure()
        {
            // Build a complete ValueListDef with nested structures
            WhereClauseRef wcRef = WhereClauseRef.builder().whereClauseOID("WC.PARAMCD.WEIGHT")
                    .build();

            ItemRef itemRef1 = ItemRef.builder().itemOID("IT.AVAL").orderNumber(1).mandatory("Yes")
                    .whereClauseRefs(Collections.singletonList(wcRef)).build();

            ItemRef itemRef2 = ItemRef.builder().itemOID("IT.AVALC").orderNumber(2).mandatory("No")
                    .build();

            ValueListDef valueListDef = ValueListDef.builder().oid("VL.AVAL").commentOID("COM.AVAL")
                    .itemRefs(Arrays.asList(itemRef1, itemRef2)).build();

            // Verify the structure
            assertEquals("VL.AVAL", valueListDef.getOid());
            assertEquals(2, valueListDef.getItemRefs().size());

            ItemRef firstItem = valueListDef.getItemRefs().get(0);
            assertEquals("IT.AVAL", firstItem.getItemOID());
            assertEquals(1, firstItem.getWhereClauseRefs().size());
            assertEquals("WC.PARAMCD.WEIGHT",
                    firstItem.getWhereClauseRefs().get(0).getWhereClauseOID());
        }


        @Test
        void testCompleteWhereClauseDefStructure()
        {
            // Build a complete WhereClauseDef with nested structures
            CheckValue cv1 = new CheckValue();
            cv1.setValue("WEIGHT");

            MeasurementUnitRef muRef = MeasurementUnitRef.builder().measurementUnitOID("MU.KG")
                    .build();

            RangeCheck rangeCheck = RangeCheck.builder().comparator("EQ").softHard("Soft")
                    .itemOID("IT.PARAMCD").checkValues(Collections.singletonList(cv1))
                    .measurementUnitRef(muRef).build();

            WhereClauseDef whereClauseDef = WhereClauseDef.builder().oid("WC.PARAMCD.WEIGHT")
                    .commentOID("COM.WC.WEIGHT").rangeChecks(Collections.singletonList(rangeCheck))
                    .build();

            // Verify the structure
            assertEquals("WC.PARAMCD.WEIGHT", whereClauseDef.getOid());
            assertEquals(1, whereClauseDef.getRangeChecks().size());

            RangeCheck rc = whereClauseDef.getRangeChecks().get(0);
            assertEquals("EQ", rc.getComparator());
            assertEquals("WEIGHT", rc.getCheckValues().get(0).getValue());
            assertEquals("MU.KG", rc.getMeasurementUnitRef().getMeasurementUnitOID());
        }


        @Test
        void testRangeCheckWithMultipleConditions()
        {
            CheckValue cv1 = new CheckValue();
            cv1.setValue("A");
            CheckValue cv2 = new CheckValue();
            cv2.setValue("B");
            CheckValue cv3 = new CheckValue();
            cv3.setValue("C");

            RangeCheck rangeCheck = RangeCheck.builder().comparator("IN").softHard("Hard")
                    .itemOID("IT.CATEGORY").checkValues(Arrays.asList(cv1, cv2, cv3)).build();

            WhereClauseDef whereClauseDef = WhereClauseDef.builder().oid("WC.CATEGORY")
                    .rangeChecks(Collections.singletonList(rangeCheck)).build();

            // Verify IN condition with multiple values
            RangeCheck rc = whereClauseDef.getRangeChecks().get(0);
            assertEquals("IN", rc.getComparator());
            assertEquals(3, rc.getCheckValues().size());
        }
    }
}
