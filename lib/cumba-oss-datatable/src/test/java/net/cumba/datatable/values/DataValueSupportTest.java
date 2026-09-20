package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataValueSupportTest
{

    // getAsDataValueLong tests
    @Test
    void testGetAsDataValueLongWithNumber()
    {
        IDataValue result = DataValueSupport.getAsDataValueLong(42);
        assertInstanceOf(DataValueLong.class, result);
        assertEquals(42L, ((DataValueLong) result).getLong());
    }


    @Test
    void testGetAsDataValueLongWithDouble()
    {
        IDataValue result = DataValueSupport.getAsDataValueLong(3.7);
        assertInstanceOf(DataValueLong.class, result);
        assertEquals(3L, ((DataValueLong) result).getLong());
    }


    @Test
    void testGetAsDataValueLongWithString()
    {
        IDataValue result = DataValueSupport.getAsDataValueLong("123");
        assertInstanceOf(DataValueLong.class, result);
        assertEquals(123L, ((DataValueLong) result).getLong());
    }


    @Test
    void testGetAsDataValueLongWithInvalidString()
    {
        IDataValue result = DataValueSupport.getAsDataValueLong("not a number");
        assertNull(result);
    }


    @Test
    void testGetAsDataValueLongWithMissingValueString()
    {
        IDataValue result = DataValueSupport.getAsDataValueLong("MIS");
        assertNull(result);
    }


    // getAsDataValueDouble tests
    @Test
    void testGetAsDataValueDoubleWithNumber()
    {
        IDataValue result = DataValueSupport.getAsDataValueDouble(3.14);
        assertInstanceOf(DataValueDouble.class, result);
        assertEquals(3.14, ((DataValueDouble) result).getValueAsDouble(), 0.0001);
    }


    @Test
    void testGetAsDataValueDoubleWithString()
    {
        IDataValue result = DataValueSupport.getAsDataValueDouble("2.718");
        assertInstanceOf(DataValueDouble.class, result);
        assertEquals(2.718, ((DataValueDouble) result).getValueAsDouble(), 0.0001);
    }


    @Test
    void testGetAsDataValueDoubleWithInvalidString()
    {
        IDataValue result = DataValueSupport.getAsDataValueDouble("invalid");
        assertNull(result);
    }


    @Test
    void testGetAsDataValueDoubleWithMissingValueString()
    {
        IDataValue result = DataValueSupport.getAsDataValueDouble("NA");
        assertNull(result);
    }


    // getAsDataValueBoolean tests
    @Test
    void testGetAsDataValueBooleanWithBoolean()
    {
        IDataValue resultTrue = DataValueSupport.getAsDataValueBoolean(true);
        IDataValue resultFalse = DataValueSupport.getAsDataValueBoolean(false);

        assertInstanceOf(DataValueBoolean.class, resultTrue);
        assertInstanceOf(DataValueBoolean.class, resultFalse);
        assertTrue(((DataValueBoolean) resultTrue).getBoolean());
    }


    @Test
    void testGetAsDataValueBooleanWithNumber()
    {
        IDataValue resultNonZero = DataValueSupport.getAsDataValueBoolean(1);
        IDataValue resultZero = DataValueSupport.getAsDataValueBoolean(0);

        assertTrue(((DataValueBoolean) resultNonZero).getBoolean());
        assertTrue(!((DataValueBoolean) resultZero).getBoolean());
    }


    @Test
    void testGetAsDataValueBooleanWithString()
    {
        IDataValue resultTrue = DataValueSupport.getAsDataValueBoolean("true");
        IDataValue resultFalse = DataValueSupport.getAsDataValueBoolean("FALSE");

        assertTrue(((DataValueBoolean) resultTrue).getBoolean());
        assertTrue(!((DataValueBoolean) resultFalse).getBoolean());
    }


    @Test
    void testGetAsDataValueBooleanWithNumericString()
    {
        IDataValue result = DataValueSupport.getAsDataValueBoolean("1");
        assertInstanceOf(DataValueBoolean.class, result);
        assertTrue(((DataValueBoolean) result).getBoolean());
    }


    @Test
    void testGetAsDataValueBooleanWithMissingValueString()
    {
        IDataValue result = DataValueSupport.getAsDataValueBoolean("MIS_A");
        assertNull(result);
    }


    @Test
    void testGetAsDataValueBooleanWithInvalidString()
    {
        IDataValue result = DataValueSupport.getAsDataValueBoolean("maybe");
        assertNull(result);
    }


    // getAsDataValueMissing tests
    @Test
    void testGetAsDataValueMissingWithMissingValue()
    {
        DataValueMissing result = DataValueSupport.getAsDataValueMissing(MissingValue.MIS_Z);
        assertSame(MissingValue.MIS_Z, result.getValue());
    }


    @Test
    void testGetAsDataValueMissingWithNumber()
    {
        DataValueMissing result = DataValueSupport.getAsDataValueMissing(64); // MIS = 64
        assertSame(MissingValue.MIS, result.getValue());
    }


    @Test
    void testGetAsDataValueMissingWithString()
    {
        DataValueMissing result = DataValueSupport.getAsDataValueMissing("NA");
        assertSame(MissingValue.NA, result.getValue());
    }


    @Test
    void testGetAsDataValueMissingWithInvalidString()
    {
        DataValueMissing result = DataValueSupport.getAsDataValueMissing("invalid");
        assertNull(result);
    }


    // getAsDataValueString tests
    @Test
    void testGetAsDataValueStringWithValue()
    {
        DataValueString result = DataValueSupport.getAsDataValueString("hello");
        assertEquals("hello", result.getValue());
    }


    @Test
    void testGetAsDataValueStringWithNull()
    {
        DataValueString result = DataValueSupport.getAsDataValueString(null);
        assertNull(result);
    }


    @Test
    void testGetAsDataValueStringWithNumber()
    {
        DataValueString result = DataValueSupport.getAsDataValueString(42);
        assertEquals("42", result.getValue());
    }


    // getAsDataValue with type tests
    @Test
    void testGetAsDataValueWithTypeLong()
    {
        IDataValue result = DataValueSupport.getAsDataValue(42, DataValueType.LONG);
        assertInstanceOf(DataValueLong.class, result);
    }


    @Test
    void testGetAsDataValueWithTypeDouble()
    {
        IDataValue result = DataValueSupport.getAsDataValue(3.14, DataValueType.DOUBLE);
        assertInstanceOf(DataValueDouble.class, result);
        assertEquals(3.14, ((DataValueDouble) result).getValueAsDouble(), 0.0001);
    }


    @Test
    void testGetAsDataValueWithTypeString()
    {
        IDataValue result = DataValueSupport.getAsDataValue("test", DataValueType.STRING);
        assertInstanceOf(DataValueString.class, result);
    }


    @Test
    void testGetAsDataValueWithTypeBoolean()
    {
        IDataValue result = DataValueSupport.getAsDataValue(true, DataValueType.BOOLEAN);
        assertInstanceOf(DataValueBoolean.class, result);
    }


    @Test
    void testGetAsDataValueWithTypeMissing()
    {
        IDataValue result = DataValueSupport.getAsDataValue(MissingValue.MIS,
                DataValueType.MISSING);
        assertInstanceOf(DataValueMissing.class, result);
    }


    @Test
    void testGetAsDataValueWithMissingValueObject()
    {
        IDataValue result = DataValueSupport.getAsDataValue(MissingValue.NA, DataValueType.OTHER);
        // ⭐ Rewritten, not deleted (PLAN-cdt-char-missing-contract phase 6). It used to assert
        // DataValueOther, because OTHER wrapped any non-null value before the fallback could see
        // that it was a MissingValue — its own comment ("the fallback handles MissingValue")
        // recorded that the ordering was odd. The missing-value check is now resolved ABOVE the
        // type switch, so a MissingValue keeps its identity whatever type was asked for. The
        // ANSWER never moved here: DataValueOther wrapping a MissingValue already reported
        // isMissingOrInvalid() true; only the class does.
        assertInstanceOf(DataValueMissing.class, result);
        assertSame(MissingValue.NA, result.getValue());
        assertTrue(result.isMissingOrInvalid());
    }


    @Test
    void testGetAsDataValueWithTypeOther()
    {
        Object obj = new Object();
        IDataValue result = DataValueSupport.getAsDataValue(obj, DataValueType.OTHER);
        assertInstanceOf(DataValueOther.class, result);
    }


    @Test
    void testGetAsDataValueWithNull()
    {
        IDataValue result = DataValueSupport.getAsDataValue(null, DataValueType.STRING);
        assertInstanceOf(DataValueMissing.class, result);
        assertSame(MissingValue.MIS, result.getValue());
    }

    // getAsDoubleCleaned tests


    @Test
    void testCleanedTrailing9sSmallValue()
    {
        // The originally reported bug: 13 sig digits ending in 9-run
        assertEquals(-0.07758, DataValueSupport.getAsDoubleCleaned(-0.07757999999999));
        assertEquals(0.07758, DataValueSupport.getAsDoubleCleaned(0.07757999999999));
    }


    @Test
    void testCleanedTrailing9sInteger()
    {
        assertEquals(3.0, DataValueSupport.getAsDoubleCleaned(2.9999999999999));
        assertEquals(100.0, DataValueSupport.getAsDoubleCleaned(99.999999999999));
    }


    @Test
    void testCleanedExactValuesUnchanged()
    {
        assertEquals(0.1, DataValueSupport.getAsDoubleCleaned(0.1));
        assertEquals(123.456, DataValueSupport.getAsDoubleCleaned(123.456));
        assertEquals(-3.14, DataValueSupport.getAsDoubleCleaned(-3.14));
        assertEquals(1.0, DataValueSupport.getAsDoubleCleaned(1.0));
        assertEquals(0.0, DataValueSupport.getAsDoubleCleaned(0.0));
    }


    @Test
    void testCleanedSpecialValues()
    {
        assertEquals(Double.NaN, DataValueSupport.getAsDoubleCleaned(Double.NaN));
        assertEquals(Double.POSITIVE_INFINITY,
                DataValueSupport.getAsDoubleCleaned(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY,
                DataValueSupport.getAsDoubleCleaned(Double.NEGATIVE_INFINITY));
    }


    @Test
    void testCleanedNearZero()
    {
        // values below EPSILON (1e-13) should become 0
        assertEquals(0.0, DataValueSupport.getAsDoubleCleaned(1e-14));
        assertEquals(0.0, DataValueSupport.getAsDoubleCleaned(-1e-14));
    }


    @Test
    void testCleanedNoFalsePositive()
    {
        // value with no 9/0-run should not be changed
        double val = 1.23456789012;
        assertEquals(val, DataValueSupport.getAsDoubleCleaned(val));
    }


    @Test
    void testCleanedFallbackFor12DigitValue()
    {
        // 12 sig digits with 9-run — first pass (MC_RND=12) doesn't change it,
        // fallback detects the run
        // ⚠ REVERSED 2026-09-14 (Q23): MIN_CLEAN_DIGITS 10 → 13. This value has 11 significant
        // digits, inside the 10–12 band that can only hold REAL data — genuine float→double
        // noise lands at 14–15. It is no longer cleaned, and that is the point.
        assertEquals(0.077599999999, DataValueSupport.getAsDoubleCleaned(0.077599999999));
    }

    // ==================== compare(IDataValue, IDataValue) ====================


    @Test
    void testCompareBothNull()
    {
        DataValueSupport dvs = new DataValueSupport();
        assertEquals(0, dvs.compare(null, null));
    }


    @Test
    void testCompareFirstNull()
    {
        DataValueSupport dvs = new DataValueSupport();
        assertEquals(1, dvs.compare(null, new DataValueLong(1L)));
    }


    @Test
    void testCompareSecondNull()
    {
        DataValueSupport dvs = new DataValueSupport();
        assertEquals(-1, dvs.compare(new DataValueLong(1L), null));
    }


    @Test
    void testCompareSameTypeDouble()
    {
        DataValueSupport dvs = new DataValueSupport();
        assertTrue(dvs.compare(new DataValueDouble(1.0), new DataValueDouble(2.0)) < 0);
        assertTrue(dvs.compare(new DataValueDouble(2.0), new DataValueDouble(1.0)) > 0);
        assertEquals(0, dvs.compare(new DataValueDouble(1.0), new DataValueDouble(1.0)));
    }


    @Test
    void testCompareSameTypeLong()
    {
        DataValueSupport dvs = new DataValueSupport();
        assertTrue(dvs.compare(new DataValueLong(1L), new DataValueLong(2L)) < 0);
        assertTrue(dvs.compare(new DataValueLong(2L), new DataValueLong(1L)) > 0);
        assertEquals(0, dvs.compare(new DataValueLong(7L), new DataValueLong(7L)));
    }


    @Test
    void testCompareSameTypeString()
    {
        DataValueSupport dvs = new DataValueSupport();
        assertTrue(dvs.compare(new DataValueString("abc"), new DataValueString("xyz")) < 0);
        assertTrue(dvs.compare(new DataValueString("xyz"), new DataValueString("abc")) > 0);
        assertEquals(0, dvs.compare(new DataValueString("foo"), new DataValueString("foo")));
    }


    @Test
    void testCompareDifferentTypesBothNumeric()
    {
        DataValueSupport dvs = new DataValueSupport();
        // Long vs Double - both numeric, compare as double
        assertTrue(dvs.compare(new DataValueLong(1L), new DataValueDouble(2.0)) < 0);
        assertTrue(dvs.compare(new DataValueDouble(3.0), new DataValueLong(2L)) > 0);
    }


    @Test
    void testCompareDifferentTypesFallbackToString()
    {
        DataValueSupport dvs = new DataValueSupport();
        // String vs Long - falls back to string compare
        // "abc" vs "1" -> "abc" is greater than "1"
        assertTrue(dvs.compare(new DataValueString("abc"), new DataValueLong(1L)) > 0);
    }

    // ==================== getAsDataValue(Object, DataValueType) ====================


    @Test
    void testGetAsDataValueWithLongType()
    {
        IDataValue v = DataValueSupport.getAsDataValue(42, DataValueType.LONG);
        assertTrue(v instanceof DataValueLong);
        assertEquals(42L, ((DataValueLong) v).getLong());
    }


    @Test
    void testGetAsDataValueWithDoubleType()
    {
        IDataValue v = DataValueSupport.getAsDataValue(3.14, DataValueType.DOUBLE);
        assertTrue(v instanceof DataValueDouble);
    }


    @Test
    void testGetAsDataValueWithStringType()
    {
        IDataValue v = DataValueSupport.getAsDataValue("hi", DataValueType.STRING);
        assertTrue(v instanceof DataValueString);
        assertEquals("hi", v.getValueAsString());
    }


    @Test
    void testGetAsDataValueWithBooleanType()
    {
        IDataValue v = DataValueSupport.getAsDataValue(true, DataValueType.BOOLEAN);
        assertTrue(v instanceof DataValueBoolean);
    }


    @Test
    void testGetAsDataValueWithMissingType()
    {
        IDataValue v = DataValueSupport.getAsDataValue(MissingValue.MIS, DataValueType.MISSING);
        assertTrue(v instanceof DataValueMissing);
    }


    @Test
    void testGetAsDataValueWithOtherType()
    {
        Object data = new java.util.ArrayList<>();
        IDataValue v = DataValueSupport.getAsDataValue(data, DataValueType.OTHER);
        assertTrue(v instanceof DataValueOther);
    }


    @Test
    void testGetAsDataValueNullValue_returnsMissing()
    {
        IDataValue v = DataValueSupport.getAsDataValue(null, DataValueType.LONG);
        assertTrue(v instanceof DataValueMissing);
    }


    @Test
    void testGetAsDataValueMissingValue_keepsMissing()
    {
        IDataValue v = DataValueSupport.getAsDataValue(MissingValue.MIS, DataValueType.LONG);
        // a MissingValue is resolved above the type switch and keeps its identity for every type
        assertTrue(v instanceof DataValueMissing);
    }


    @Test
    void testGetAsDataValueStringForLongType_unparseableFallsToString()
    {
        IDataValue v = DataValueSupport.getAsDataValue("not-a-number", DataValueType.LONG);
        // can not parse as long -> falls back to string wrapping
        assertTrue(v instanceof DataValueString);
    }


    @Test
    void testGetAsDataValueStringForMissingValueName()
    {
        IDataValue v = DataValueSupport.getAsDataValue("MIS", DataValueType.LONG);
        // "MIS" can be parsed as MissingValue → DataValueMissing
        assertTrue(v instanceof DataValueMissing);
    }

    // ------------------------------------------------------------------------------------------
    // ⭐ PLAN-cdt-char-missing-contract phase 6 — a stored MissingValue is missing on EVERY column
    // type. Before the hoist the STRING arm of the type switch stringified it via toString(), so a
    // missing character cell answered isMissingOrInvalid() == false — the whole defect. These are
    // the contract tests the class never had; they red if the null-check / instanceof-MissingValue
    // blocks are moved back below the switch.
    // ------------------------------------------------------------------------------------------

    /**
     * The flavours worth spanning: the plain SAS dot, the underscore form, a lettered special
     * missing, the R null, and the two internal sentinels.
     */
    private static final MissingValue[] MISSING_FLAVOURS =
    {
            MissingValue.MIS, MissingValue.MIS__, MissingValue.MIS_A, MissingValue.MIS_Z,
            MissingValue.NA, MissingValue.MIS_UNKNOWN, MissingValue.MIS_ERROR
    };

    @Test
    void missingValueKeepsItsIdentityForEveryTargetType()
    {
        for (MissingValue mv : MISSING_FLAVOURS)
        {
            for (DataValueType type : DataValueType.values())
            {
                IDataValue v = DataValueSupport.getAsDataValue(mv, type);
                String where = mv + " as " + type;
                assertInstanceOf(DataValueMissing.class, v, where);
                assertSame(mv, v.getValue(), where);
                assertTrue(v.isMissingOrInvalid(), where);
            }
        }
    }


    /**
     * The no-visible-change pin. Nothing displayed or exported may move: the rendering of a missing
     * cell is its {@link MissingValue#toString()} either way, which is what it was when the STRING
     * arm produced a {@link DataValueString} of the same text.
     */
    @Test
    void missingValueRenderingIsUnchangedForEveryTargetType()
    {
        for (MissingValue mv : MISSING_FLAVOURS)
        {
            for (DataValueType type : DataValueType.values())
            {
                IDataValue v = DataValueSupport.getAsDataValue(mv, type);
                assertEquals(mv.toString(), v.getValueAsString(), mv + " as " + type);
            }
        }
    }


    /**
     * ⛔ The {@code MissingValue.valueOf(str)} rescue below the switch is a DIFFERENT mechanism and
     * must stay where it is: it converts an enum NAME that arrived as parsed text, never a display
     * string, and only when the requested type could not be produced. A STRING column asking for
     * the text "NA" still gets the text.
     */
    @Test
    void enumNameRescueForParsedTextIsUnaffectedByTheHoist()
    {
        IDataValue rescued = DataValueSupport.getAsDataValue("NA", DataValueType.LONG);
        assertInstanceOf(DataValueMissing.class, rescued);
        assertSame(MissingValue.NA, rescued.getValue());

        IDataValue asText = DataValueSupport.getAsDataValue("NA", DataValueType.STRING);
        assertInstanceOf(DataValueString.class, asText);
        assertEquals("NA", asText.getValueAsString());

        // A DISPLAY string is not a name, and never was: ".A" stays ordinary text.
        IDataValue display = DataValueSupport.getAsDataValue(".A", DataValueType.STRING);
        assertInstanceOf(DataValueString.class, display);
        assertFalse(display.isMissingOrInvalid());
    }


    /**
     * The parse side of the helper is untouched: a String never matches
     * {@code instanceof MissingValue}, so every caller that turns user or parser text into a typed
     * value answers exactly as before.
     */
    @Test
    void parsedTextStillResolvesThroughTheTypeSwitch()
    {
        assertInstanceOf(DataValueLong.class,
                DataValueSupport.getAsDataValue("42", DataValueType.LONG));
        assertInstanceOf(DataValueDouble.class,
                DataValueSupport.getAsDataValue("1.5", DataValueType.DOUBLE));
        assertInstanceOf(DataValueString.class,
                DataValueSupport.getAsDataValue("abc", DataValueType.STRING));
        assertInstanceOf(DataValueOther.class,
                DataValueSupport.getAsDataValue(new Object(), DataValueType.OTHER));
    }


    /**
     * null keeps its own answer, and it is resolved above the switch for the same reason: the
     * STRING arm already returned null for it, so this is a hoist with no behaviour change at all.
     */
    @Test
    void nullIsMissingForEveryTargetType()
    {
        for (DataValueType type : DataValueType.values())
        {
            IDataValue v = DataValueSupport.getAsDataValue(null, type);
            assertInstanceOf(DataValueMissing.class, v, "null as " + type);
            assertSame(MissingValue.MIS, v.getValue(), "null as " + type);
        }
    }
}
