package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

class CdtValuesTest
{

    /**
     * <b>Deliberately still {@code null}, and not an oversight.</b> The house contract for a blank
     * cell is <em>missing ≡ empty</em>, spelled per the column's storage type — and for a character
     * column {@code null} <em>is</em> that spelling: a CHAR column is typed
     * {@link DataValueType#STRING}, its buffer stores the {@code null} without complaint, and
     * {@code AbstractDataBuffer.createDataValue} maps it back to the empty string ("for STRING we
     * map from null to empty string"). The blank character cell therefore already reads as
     * {@code ""} and never as missing, which is exactly what
     * {@code BlankCellFormatIndependenceTest} asserts for the CDT format today.
     * <p>
     * The numeric half below is different only because {@code DataBufferDouble} has no such
     * mapping: it rejects {@code null} outright. Returning {@code ""} here instead would change
     * nothing at the table level and would silently start writing an override for every blank
     * character cell in {@code CdtLoader}, so the char case is left exactly as it was.
     */
    @Test
    void charEmptyIsNullBecauseTheStringBufferMapsNullToEmpty()
    {
        assertNull(CdtValues.parseValue("", CdtType.CHAR));
    }


    /**
     * Changed from {@code assertNull}: a blank numeric cell used to yield {@code null}, which
     * {@code DataBufferDouble.setValue} rejects with
     * {@code IllegalArgumentException: Invalid value:
     * null} — so a {@code .cdt} carrying one could not be loaded through {@code CdtTableProvider}
     * at all. Missing in a numeric buffer is spelled {@link MissingValue}.
     */
    @Test
    void numericEmptyIsMissingValue()
    {
        assertEquals(MissingValue.MIS, CdtValues.parseValue("", CdtType.NUM));
        assertEquals(MissingValue.MIS, CdtValues.parseValue("", CdtType.DATE));
        assertEquals(MissingValue.MIS, CdtValues.parseValue("", CdtType.TIME));
        assertEquals(MissingValue.MIS, CdtValues.parseValue("", CdtType.DATETIME));
    }


    /** A {@code null} raw field is the same missing cell as an empty one. */
    @Test
    void numericNullRawIsMissingValue()
    {
        assertEquals(MissingValue.MIS, CdtValues.parseValue(null, CdtType.NUM));
        assertNull(CdtValues.parseValue(null, CdtType.CHAR));
    }


    /**
     * Changed from {@code assertNull} for the same reason as {@link #numericEmptyIsMissingValue()}.
     * {@code CdtParser.parseDataRow} already folds an unquoted {@code .} to {@code ""} before the
     * value ever reaches here, so this branch only fires for direct API callers — but it must agree
     * with the empty-string branch or the two disagree about what missing means.
     */
    @Test
    void numDotIsMissingValueSasConvention()
    {
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.NUM));
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.DATE));
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.TIME));
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.DATETIME));
    }


    /**
     * The type-driven guard itself: {@link CdtValues#missingFor(CdtType)} must agree with
     * {@link CdtValues#toDataValueType(CdtType)} for <em>every</em> {@link CdtType}, so a numeric
     * type added later cannot quietly go back to yielding {@code null} into a
     * {@code DataBufferDouble}. Iterating the enum is what makes this a guard rather than a
     * restatement of the four cases above.
     */
    @Test
    void everyTypeThatMapsToDoubleGetsAMissingValue()
    {
        for (CdtType t : CdtType.values())
        {
            Object missing = CdtValues.missingFor(t);
            if (CdtValues.toDataValueType(t) == DataValueType.DOUBLE)
            {
                assertEquals(MissingValue.MIS, missing,
                        t + " is stored in a numeric buffer, which rejects null");
            }
            else
            {
                assertNull(missing, t + " is not stored in a numeric buffer");
            }
            assertEquals(missing, CdtValues.parseValue("", t),
                    t + ": an empty field must be the type's missing representation");
        }
    }


    @Test
    void charDotIsLiteralDotNotNull()
    {
        // For Char, a single "." is a literal string value, NOT the null sentinel.
        assertEquals(".", CdtValues.parseValue(".", CdtType.CHAR));
    }


    @Test
    void numParsesInteger()
    {
        assertEquals(42.0, (Double) CdtValues.parseValue("42", CdtType.NUM));
    }


    @Test
    void numParsesDecimal()
    {
        assertEquals(3.14, (Double) CdtValues.parseValue("3.14", CdtType.NUM));
    }


    @Test
    void numParsesNegative()
    {
        assertEquals(-7.5, (Double) CdtValues.parseValue("-7.5", CdtType.NUM));
    }


    @Test
    void numParsesScientificNotation()
    {
        assertEquals(1.5e3, (Double) CdtValues.parseValue("1.5e3", CdtType.NUM));
    }


    @Test
    void numInvalidThrows()
    {
        assertThrows(CdtParseException.class,
                () -> CdtValues.parseValue("not-a-number", CdtType.NUM));
    }


    @Test
    void dateEpochIsZero()
    {
        // 1960-01-01 is the SAS epoch — zero days.
        assertEquals(0.0, (Double) CdtValues.parseValue("1960-01-01", CdtType.DATE));
    }


    @Test
    void datePostEpoch()
    {
        LocalDate d = LocalDate.of(2021, 1, 1);
        double expected = d.toEpochDay() - LocalDate.of(1960, 1, 1).toEpochDay();
        assertEquals(expected, (Double) CdtValues.parseValue("2021-01-01", CdtType.DATE));
    }


    @Test
    void datePreEpoch()
    {
        // SAS days can be negative for pre-1960 dates.
        assertEquals(-1.0, (Double) CdtValues.parseValue("1959-12-31", CdtType.DATE));
    }


    @Test
    void dateInvalidThrows()
    {
        assertThrows(CdtParseException.class,
                () -> CdtValues.parseValue("not-a-date", CdtType.DATE));
    }


    @Test
    void timeParsesSecondsSinceMidnight()
    {
        assertEquals(0.0, (Double) CdtValues.parseValue("00:00:00", CdtType.TIME));
        assertEquals(3600.0, (Double) CdtValues.parseValue("01:00:00", CdtType.TIME));
        assertEquals(86399.0, (Double) CdtValues.parseValue("23:59:59", CdtType.TIME));
    }


    @Test
    void timeInvalidThrows()
    {
        assertThrows(CdtParseException.class, () -> CdtValues.parseValue("25:00:00", CdtType.TIME));
    }


    @Test
    void dateTimeAtEpochIsZero()
    {
        assertEquals(0.0, (Double) CdtValues.parseValue("1960-01-01T00:00:00", CdtType.DATETIME));
    }


    @Test
    void dateTimeOneSecondPastEpoch()
    {
        assertEquals(1.0, (Double) CdtValues.parseValue("1960-01-01T00:00:01", CdtType.DATETIME));
    }


    @Test
    void dateTimeOneDayPastEpoch()
    {
        assertEquals(86400.0,
                (Double) CdtValues.parseValue("1960-01-02T00:00:00", CdtType.DATETIME));
    }


    @Test
    void typeMapping()
    {
        assertEquals(DataValueType.STRING, CdtValues.toDataValueType(CdtType.CHAR));
        assertEquals(DataValueType.DOUBLE, CdtValues.toDataValueType(CdtType.NUM));
        assertEquals(DataValueType.DOUBLE, CdtValues.toDataValueType(CdtType.DATE));
        assertEquals(DataValueType.DOUBLE, CdtValues.toDataValueType(CdtType.TIME));
        assertEquals(DataValueType.DOUBLE, CdtValues.toDataValueType(CdtType.DATETIME));
    }
}
