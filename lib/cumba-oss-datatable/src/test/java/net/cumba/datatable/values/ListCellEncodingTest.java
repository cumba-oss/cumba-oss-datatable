package net.cumba.datatable.values;

// OSS-IDENTITY datatable-list-cell: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The documented list encoding (Q38-sep / Q39): {@code ", "}, no quoting, no escaping. */
class ListCellEncodingTest
{

    @Test
    void emptyOneAndManyElements()
    {
        assertEquals("", ListCellEncoding.join(List.of()));
        assertEquals("a", ListCellEncoding.join(List.of("a")));
        assertEquals("a, b, c", ListCellEncoding.join(List.of("a", "b", "c")));
    }


    @Test
    void elementsAreWrittenAsTheirTextUnquoted()
    {
        assertEquals("say \"hi\", 7, true, 2.5",
                ListCellEncoding.join(List.of("say \"hi\"", 7, true, 2.5)));
    }


    @Test
    void aNullElementIsTheEmptyElement()
    {
        assertEquals("a, , b", ListCellEncoding.join(Arrays.asList("a", null, "b")));
        assertEquals("", ListCellEncoding.join(Arrays.asList((Object) null)));
    }


    @Test
    void theOrderIsTheIterationOrder()
    {
        Set<String> sorted = new TreeSet<>(List.of("QB", "QA"));
        assertEquals("QA, QB", ListCellEncoding.join(sorted));
    }


    /** The named gap: no escaping, so an element holding the separator is ambiguous. */
    @Test
    void anElementHoldingTheSeparatorIsAmbiguousByDesign()
    {
        assertEquals(ListCellEncoding.join(List.of("a", "b")),
                ListCellEncoding.join(List.of("a, b")));
    }
}
