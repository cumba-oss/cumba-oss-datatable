package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-format-names: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link CodelistFormatNames}: the primary catalog name of a codelist format, its
 * {@code SASFormatName} alias and when a {@code SASFormatName} is usable. The shapes are the real
 * ones: phuse {@code QS.QSTESTCD} (dotted, no {@code SASFormatName}), DataExchange {@code $FMT1} …
 * {@code $FMT147} (trailing digits, an integer codelist carrying {@code $FMT59}).
 */
class CodelistFormatNamesTest
{

    // ==================== codelistFormatName ====================

    @ParameterizedTest
    @CsvSource(delimiter = '|', value =
    {
            "QS.QSTESTCD|true|$QS.QSTESTCD", "SUPPAE.QNAM|true|$SUPPAE.QNAM", "NY|false|NY",
            "$SEX|true|$SEX", "SEX|true|$SEX", "$ODD|false|$ODD",
            "Relation to Reference Period, subset used for AE and CM.|true|"
                    + "$Relation to Reference Period, subset used for AE and CM.",
            "Age Group 12|true|$Age Group 12"
    })
    void codelistFormatName_dollarForTextOnly_idempotent(String aName, boolean aText,
            String aExpected)
    {
        assertEquals(aExpected, CodelistFormatNames.codelistFormatName(aName, aText));
    }

    // ==================== isUsableSasFormatName ====================


    @ParameterizedTest
    @CsvSource(delimiter = '|', value =
    {
            // usable: letter/_ after an optional $, trailing digits accepted, $ iff text
            "$FMT12|true|true", "$FMT1|true|true", "$FMT147|true|true", "FMT59|false|true",
            "_X1|false|true", "$_X|true|true",
            // the $ must agree with the codelist's data type
            "$FMT59|false|false", "AGEGRP|true|false",
            // not a SAS name
            "$1ABC|true|false", "1ABC|false|false", "$|true|false", "$FMT 1|true|false",
            "$FMT-1|true|false", "$FMT.1|true|false", "' $FMT1'|true|false"
    })
    void isUsableSasFormatName(String aName, boolean aText, boolean aExpected)
    {
        assertEquals(aExpected, CodelistFormatNames.isUsableSasFormatName(aName, aText));
    }


    @Test
    void isUsableSasFormatName_lengthLimitIncludesTheDollar()
    {
        String max = "$" + "A".repeat(CodelistFormatNames.MAX_SAS_FORMAT_NAME_LENGTH - 1);
        assertEquals(32, max.length());
        assertTrue(CodelistFormatNames.isUsableSasFormatName(max, true));
        assertFalse(CodelistFormatNames.isUsableSasFormatName(max + "B", true));
        assertTrue(CodelistFormatNames.isUsableSasFormatName("A".repeat(32), false));
        assertFalse(CodelistFormatNames.isUsableSasFormatName("A".repeat(33), false));
    }


    @Test
    void isUsableSasFormatName_nullAndEmpty()
    {
        assertFalse(CodelistFormatNames.isUsableSasFormatName(null, true));
        assertFalse(CodelistFormatNames.isUsableSasFormatName(null, false));
        assertFalse(CodelistFormatNames.isUsableSasFormatName("", false));
    }

    // ==================== codelistAliases ====================


    @Test
    void codelistAliases_usableSasFormatName_isTheAlias()
    {
        assertEquals(List.of("$FMT12"),
                CodelistFormatNames.codelistAliases("Age Group 12", "$FMT12", true));
        assertEquals(List.of("FMT7"), CodelistFormatNames.codelistAliases("Dose", "FMT7", false));
    }


    @Test
    void codelistAliases_noSasFormatName_noAlias()
    {
        try (Capture log = new Capture())
        {
            assertEquals(List.of(), CodelistFormatNames.codelistAliases("QS.QSTESTCD", null, true));
            assertEquals(List.of(), CodelistFormatNames.codelistAliases("QS.QSTESTCD", "", true));
            assertEquals(List.of(), CodelistFormatNames.codelistAliases("QS.QSTESTCD", "  ", true));
            // absence is not worth a log line
            assertEquals(List.of(), log.records);
        }
    }


    @Test
    void codelistAliases_sasFormatNameEqualToThePrimary_noAlias()
    {
        // "$sex" names the primary "$SEX" again (catalog keys ignore case): no second key
        assertEquals(List.of(), CodelistFormatNames.codelistAliases("SEX", "$sex", true));
        assertEquals(List.of(), CodelistFormatNames.codelistAliases("$SEX", "$SEX", true));
        assertEquals(List.of(), CodelistFormatNames.codelistAliases("NY", "NY", false));
    }


    @Test
    void codelistAliases_unusableSasFormatName_ignoredAndLoggedAtInfo()
    {
        try (Capture log = new Capture())
        {
            // DataExchange: an integer codelist carrying a $ name
            assertEquals(List.of(),
                    CodelistFormatNames.codelistAliases(
                            "Hamilton Depression Rating Scale - 17 Item - Question 1 Standardized",
                            "$FMT59", false));
            // longer than 32 characters
            assertEquals(List.of(), CodelistFormatNames.codelistAliases("Long",
                    "$" + "A".repeat(CodelistFormatNames.MAX_SAS_FORMAT_NAME_LENGTH), true));

            assertEquals(2, log.records.size());
            LogRecord first = log.records.get(0);
            assertEquals(Level.INFO, first.getLevel());
            assertEquals("$FMT59", first.getParameters()[0]);
            assertEquals("Hamilton Depression Rating Scale - 17 Item - Question 1 Standardized",
                    first.getParameters()[1]);
        }
    }

    /**
     * Captures the JUL records {@link CodelistFormatNames} logs through its {@link System.Logger},
     * restoring the logger on close.
     */
    private static final class Capture implements AutoCloseable
    {

        private final Logger logger = Logger.getLogger(CodelistFormatNames.class.getName());

        private final Level oldLevel = logger.getLevel();

        private final List<LogRecord> records = new ArrayList<>();

        private final Handler handler = new Handler()
        {

            @Override
            public void publish(LogRecord aRecord)
            {
                records.add(aRecord);
            }


            @Override
            public void flush()
            {
                // nothing buffered
            }


            @Override
            public void close()
            {
                // nothing to release
            }
        };

        Capture()
        {
            handler.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
        }


        @Override
        public void close()
        {
            logger.removeHandler(handler);
            logger.setLevel(oldLevel);
        }
    }
}
