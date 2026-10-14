package net.cumba.datatable.provider.sas;

// OSS-IDENTITY provider-sas: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Every row of the assembly rule in {@link SasFormatSpelling} (PLAN-sas-bare-wd-format-read §5).
 * The expected spellings are SAS's own: ReadStat reports the bare {@code w.d} format of a
 * SAS-written XPT as {@code "8"} / {@code "8.1"} (see {@code XptRealFileValueTest}), and SAS writes
 * the format as {@code 8.} / {@code 8.1}.
 */
class SasFormatSpellingTest
{

    /** One input of the rule and the spelling it must give. */
    private record Row(@Nullable String name, int width, int decimals, boolean numeric,
            @Nullable String expected)
    {
    }


    /** The assembly under test, as a function, so the planted control can swap it. */
    @FunctionalInterface
    private interface Assembly
    {

        @Nullable
        String assemble(@Nullable String aName, int aWidth, int aDecimals, boolean aNumeric);
    }

    private static final List<Row> ROWS = List.of(
            // a named format, numeric: name + width + "." + decimals
            new Row("BEST", 12, 2, true, "BEST12.2"), //
            new Row("BEST", 12, 0, true, "BEST12."), //
            new Row("BEST", 0, 0, true, "BEST."), //
            new Row("  DATE  ", 9, 0, true, "DATE9."), //
            new Row("DATE", 0, 0, true, "DATE."), //
            // a named format, character: decimals are never appended ("$CHAR8.2" is no SAS format)
            new Row("$CHAR", 8, 0, false, "$CHAR8."), //
            new Row("$CHAR", 8, 2, false, "$CHAR8."), //
            new Row("$", 20, 0, false, "$20."), //
            // a blank name with a width on a numeric column: SAS's w.d format -- the fix
            new Row("", 8, 0, true, "8."), //
            new Row("        ", 8, 1, true, "8.1"), //
            new Row(null, 3, 0, true, "3."), //
            new Row(null, 12, 2, true, "12.2"), //
            // a blank name without a width: no format, never a bare "."
            new Row(null, 0, 0, true, null), //
            new Row("", 0, 0, false, null), //
            new Row("   ", 0, 0, true, null), //
            // BW2 (owner 2026-10-12): width 0, decimals > 0 stays no format
            new Row("", 0, 3, true, null), //
            new Row(null, 0, 1, true, null), //
            // a blank name on a character column: no format, whatever its width
            new Row(null, 10, 0, false, null), //
            new Row("  ", 10, 2, false, null), //
            // a negative width or decimal count (a damaged header) is no width / no decimals
            new Row("", -8, 0, true, null), //
            new Row("BEST", -1, -2, true, "BEST."));

    /** The rows the assembly gets wrong, each as "input -> got, expected". */
    private static List<String> mismatches(Assembly aAssembly)
    {
        List<String> out = new ArrayList<>();
        for (Row r : ROWS)
        {
            String got = aAssembly.assemble(r.name(), r.width(), r.decimals(), r.numeric());
            if (!java.util.Objects.equals(r.expected(), got))
            {
                out.add(r + " -> " + got);
            }
        }
        return out;
    }


    @Test
    void everyRowOfTheRuleHolds()
    {
        assertEquals(List.of(), mismatches(SasFormatSpelling::assemble));
    }


    /**
     * Planted control: the rule the XPT readers had before this plan (a blank name is no format,
     * whatever the width; decimals appended on any column) must fail this table, and exactly on the
     * rows it got wrong -- the named character format with decimals and the four bare {@code w.d}
     * rows. If it passed, the table could not tell the fix from the defect.
     */
    @Test
    void theOldDropRuleFailsExactlyTheBareWdRows()
    {
        Assembly oldRule = (aName, aWidth, aDecimals, _) ->
        {
            if (aName == null || aName.isBlank())
            {
                return null;
            }
            return aName.trim() + (aWidth > 0 ? aWidth : "") + "."
                    + (aDecimals > 0 ? aDecimals : "");
        };
        List<String> wrong = mismatches(oldRule);
        assertEquals(5, wrong.size(), wrong::toString);
        assertEquals(List.of("$CHAR8.", "8.", "8.1", "3.", "12.2"), wrong.stream()
                .map(s -> s.substring(s.indexOf("expected=") + 9, s.indexOf(']'))).toList());
    }
}
