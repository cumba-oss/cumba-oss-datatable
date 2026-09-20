package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

class CdtValuesTest
{

    @Test
    void charEmptyIsEmptyString()
    {
        // SAS character semantics: a Char column has no missing sentinel — "" IS its
        // missing value. Never null; see CdtValues.missingFor.
        assertEquals("", CdtValues.parseValue("", CdtType.CHAR));
    }


    @Test
    void numEmptyIsMissing()
    {
        assertEquals(MissingValue.MIS, CdtValues.parseValue("", CdtType.NUM));
    }


    @Test
    void numDotIsMissingSasConvention()
    {
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.NUM));
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.DATE));
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.TIME));
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.DATETIME));
    }


    @Test
    void charDotIsMissingSinceTheSentinelExists()
    {
        // Changed 2026-09-17: "." is the missing sentinel for EVERY column type, Char included -
        // it is the only way .cdt can express a missing character value. A literal dot is written
        // quoted and reaches parseValue escaped; see charEscapedDotIsLiteralDot below and
        // CdtMissingValueTest for the whole contract.
        assertEquals(MissingValue.MIS, CdtValues.parseValue(".", CdtType.CHAR));
    }


    @Test
    void charEscapedDotIsLiteralDot()
    {
        // The escape hatch: a quoted "." field. CdtParser marks it via CdtValues.encodeField, so
        // a literal dot stays representable - see CdtRoundTripTest.quotingRoundTrips.
        assertEquals(".", CdtValues.parseValue(CdtValues.encodeField(".", true), CdtType.CHAR));
    }


    @Test
    void missingForFollowsSasCharacterSemantics()
    {
        assertEquals("", CdtValues.missingFor(CdtType.CHAR));
        assertEquals(MissingValue.MIS, CdtValues.missingFor(CdtType.NUM));
        assertEquals(MissingValue.MIS, CdtValues.missingFor(CdtType.DATE));
        assertEquals(MissingValue.MIS, CdtValues.missingFor(CdtType.TIME));
        assertEquals(MissingValue.MIS, CdtValues.missingFor(CdtType.DATETIME));
    }


    @Test
    void parseValueNeverReturnsNull()
    {
        for (CdtType t : CdtType.values())
        {
            assertNotNull(CdtValues.parseValue(null, t));
            assertNotNull(CdtValues.parseValue("", t));
        }
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
