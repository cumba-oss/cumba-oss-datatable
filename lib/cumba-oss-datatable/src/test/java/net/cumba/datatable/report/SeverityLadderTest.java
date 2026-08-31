package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Plan C ruling 1 — the four-rung ladder, {@code REJECT}, and the end of the silent downgrade. */
class SeverityLadderTest
{

    @Test
    @DisplayName("declaration order IS the ladder, strictest first, with NOTICE outside it")
    void declarationOrderIsTheLadder()
    {
        assertEquals(
                List.of(Severity.REJECT, Severity.ERROR, Severity.WARNING, Severity.INFO,
                        Severity.NOTICE),
                List.of(Severity.values()),
                "the ladder is written once, in declaration order, so no comparator can disagree");
        assertTrue(Severity.REJECT.compareTo(Severity.ERROR) < 0, "REJECT is strictest");
        assertTrue(Severity.ERROR.compareTo(Severity.WARNING) < 0);
        assertTrue(Severity.WARNING.compareTo(Severity.INFO) < 0);
    }


    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
    {
            "Reject, REJECT", "reject, REJECT", "REJECT, REJECT", "Error, ERROR", "error, ERROR",
            "Warning, WARNING", "WARNING, WARNING", "Info, INFO", "Notice, NOTICE"
    })
    @DisplayName("every value round-trips case-insensitively, REJECT included")
    void roundTrips(String raw, Severity expected)
    {
        assertEquals(expected, Severity.fromJson(raw));
        assertEquals(expected, Severity.parseOrNull(raw));
        assertEquals(expected.getJsonValue(), expected.getJsonValue());
    }


    @Test
    @DisplayName("⛔ an unknown value is an ERROR, not a silent WARNING")
    void unknownValueThrows()
    {
        // Before Plan C this returned WARNING, and the behaviour was documented rather than
        // accidental — so "Reject" silently downgraded on every rule that carried it, as did any
        // typo. Measured at the time: "Reject" appears ~70 times in tracked JSON data.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Severity.fromJson("nonsense"));
        assertTrue(e.getMessage().contains("nonsense"), "the message names the offending value");
        assertTrue(e.getMessage().contains("Reject"), "and lists what was expected");
        assertThrows(IllegalArgumentException.class, () -> Severity.fromJson("error "),
                "a stray space is exactly the typo that used to downgrade silently");
    }


    @Test
    @DisplayName("null stays null on both doors; only the lenient door tolerates an unknown")
    void nullAndLeniency()
    {
        assertNull(Severity.fromJson(null));
        assertNull(Severity.parseOrNull(null));
        assertNull(Severity.parseOrNull("nonsense"),
                "the authoring door returns null so the loader can report a per-rule load error");
    }

}
