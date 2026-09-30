package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link CsvRecord}.
 */
class CsvRecordTest
{

    @Test
    void testGetColumnCount()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "a", "b", "c"
        });
        assertEquals(3, csvRec.getColumnCount());
    }


    @Test
    void testGetColumnCountEmpty()
    {
        CsvRecord csvRec = new CsvRecord(new String[0]);
        assertEquals(0, csvRec.getColumnCount());
    }


    @Test
    void testGetValue()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "hello", "world"
        });
        assertEquals("hello", csvRec.getValue(0));
        assertEquals("world", csvRec.getValue(1));
    }


    @Test
    void testGetValueNullReturnsEmpty()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                null, "val"
        });
        assertEquals("", csvRec.getValue(0));
        assertEquals("val", csvRec.getValue(1));
    }


    @Test
    void testGetValueNegativeIndexThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "a"
        });
        assertThrows(IndexOutOfBoundsException.class, () -> csvRec.getValue(-1));
    }


    @Test
    void testGetValueIndexTooHighThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "a"
        });
        // ⚠ ArrayIndexOutOfBoundsException is ALSO an IndexOutOfBoundsException, so an
        // assertThrows(IndexOutOfBoundsException.class, ...) alone cannot tell CsvRecord's own
        // guard from a boundary bug that lets the call fall through into a raw array access
        // (aColumn > values.length instead of >=, which only misbehaves exactly at this index).
        // Asserting the exact class -- and the guard's own message -- pins the guard itself.
        IndexOutOfBoundsException ex = assertThrows(IndexOutOfBoundsException.class,
                () -> csvRec.getValue(1));
        assertEquals(IndexOutOfBoundsException.class, ex.getClass(),
                "must be CsvRecord's own guard, not a raw ArrayIndexOutOfBoundsException");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("1"),
                "message must name the out-of-range index: " + ex.getMessage());
    }


    @Test
    void testGetDoubleValueValid()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "3.14", "-1.5", "0"
        });
        assertEquals(3.14, csvRec.getDoubleValue(0), 0.001);
        assertEquals(-1.5, csvRec.getDoubleValue(1), 0.001);
        assertEquals(0.0, csvRec.getDoubleValue(2), 0.001);
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(strings =
    {
            "abc", ""
    })
    void testGetDoubleValueReturnsNaN(String aInput)
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                aInput
        });
        assertTrue(Double.isNaN(csvRec.getDoubleValue(0)));
    }


    @Test
    void testGetLongValueValid()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "42", "-7", "0"
        });
        assertEquals(42L, csvRec.getLongValue(0));
        assertEquals(-7L, csvRec.getLongValue(1));
        assertEquals(0L, csvRec.getLongValue(2));
    }


    @Test
    void testGetLongValueNonNumericThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "abc"
        });
        assertThrows(IllegalStateException.class, () -> csvRec.getLongValue(0));
    }


    @Test
    void testGetLongValueDecimalThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "3.14"
        });
        assertThrows(IllegalStateException.class, () -> csvRec.getLongValue(0));
    }


    @Test
    void testIsDoubleOrMissingNumeric()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "1.5", "42", "-3.14"
        });
        assertTrue(csvRec.isDoubleOrMissing(0));
        assertTrue(csvRec.isDoubleOrMissing(1));
        assertTrue(csvRec.isDoubleOrMissing(2));
    }


    @Test
    void testIsDoubleOrMissingEmpty()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "", null
        });
        assertTrue(csvRec.isDoubleOrMissing(0));
        assertTrue(csvRec.isDoubleOrMissing(1));
    }


    @Test
    void testIsDoubleOrMissingDot()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "."
        });
        assertTrue(csvRec.isDoubleOrMissing(0));
    }


    /** K7: a quoted "." is text -- neither a double nor a missing. */
    @Test
    void testIsDoubleOrMissingQuotedDotIsText()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                ".", "."
        }, new boolean[]
        {
                true, false
        });
        assertFalse(csvRec.isDoubleOrMissing(0));
        assertTrue(csvRec.isDoubleOrMissing(1));
    }


    /**
     * K7 / K7b: only an UNQUOTED sentinel -- {@code .}, {@code ._}, {@code .A}..{@code .Z} -- is a
     * SAS missing, each with its own identity; a quoted one, or any other text, is no missing.
     */
    @Test
    void testGetUnquotedMissing()
    {
        CsvRecord rec = new CsvRecord(new String[]
        {
                ".", ".", ".A", "._", ".Z", ".A", "x", " .", "", ".a", ".AB", "..", null
        }, new boolean[]
        {
                true, false, false, false, false, true, false, false, false, false, false, false,
                false
        });
        assertNull(rec.getUnquotedMissing(0), "quoted '.'");
        assertSame(MissingValue.MIS, rec.getUnquotedMissing(1));
        assertSame(MissingValue.MIS_A, rec.getUnquotedMissing(2));
        assertSame(MissingValue.MIS__, rec.getUnquotedMissing(3));
        assertSame(MissingValue.MIS_Z, rec.getUnquotedMissing(4));
        assertNull(rec.getUnquotedMissing(5), "quoted '.A'");
        for (int i = 6; i < rec.getColumnCount(); i++)
        {
            assertNull(rec.getUnquotedMissing(i), "not a sentinel: column " + i);
        }

        CsvRecord plain = new CsvRecord(new String[]
        {
                "._"
        });
        assertSame(MissingValue.MIS__, plain.getUnquotedMissing(0),
                "a record with no quote flags has no quoted cell");
        assertThrows(IndexOutOfBoundsException.class, () -> plain.getUnquotedMissing(1));
    }


    /** Every one of the 28 sentinels maps to the MissingValue whose display string it is. */
    @Test
    void testEverySentinelMapsToItsOwnMissingValue()
    {
        String[] sentinels = new String[28];
        sentinels[0] = ".";
        sentinels[1] = "._";
        for (char c = 'A'; c <= 'Z'; c++)
        {
            sentinels[2 + c - 'A'] = "." + c;
        }
        CsvRecord rec = new CsvRecord(sentinels);
        Set<MissingValue> seen = new HashSet<>();
        for (int i = 0; i < sentinels.length; i++)
        {
            assertTrue(CsvRecord.isMissingSentinel(sentinels[i]), sentinels[i]);
            MissingValue mv = rec.getUnquotedMissing(i);
            assertNotNull(mv, sentinels[i]);
            assertEquals(sentinels[i], mv.toString(), "identity of " + sentinels[i]);
            seen.add(mv);
        }
        assertEquals(28, seen.size());
        for (String s : new String[]
        {
                null, "", "a", ".a", ".z", ".AB", "..", ".@", ".[", ".^", ".`", "A.", "_"
        })
        {
            assertFalse(CsvRecord.isMissingSentinel(s), String.valueOf(s));
        }
    }


    @Test
    void testIsQuoted()
    {
        CsvRecord rec = new CsvRecord(new String[]
        {
                "a", "b"
        }, new boolean[]
        {
                false, true
        });
        assertFalse(rec.isQuoted(0));
        assertTrue(rec.isQuoted(1));
        assertFalse(new CsvRecord(new String[]
        {
                "a"
        }).isQuoted(0));
        IndexOutOfBoundsException hi = assertThrows(IndexOutOfBoundsException.class,
                () -> rec.isQuoted(2));
        assertEquals(IndexOutOfBoundsException.class, hi.getClass());
        assertThrows(IndexOutOfBoundsException.class, () -> rec.isQuoted(-1));
    }


    /**
     * {@code isQuoted} is exact only for sentinel cells and inside the typing sample, so it is no
     * general "was quoted" answer and stays package-private -- read only by this package's typing
     * code.
     */
    @Test
    void isQuotedIsNotPublicApi() throws NoSuchMethodException
    {
        int mods = CsvRecord.class.getDeclaredMethod("isQuoted", int.class).getModifiers();
        assertFalse(Modifier.isPublic(mods), "isQuoted must not be public");
        assertFalse(Modifier.isProtected(mods), "isQuoted must not be protected");
        assertFalse(Modifier.isPrivate(mods), "the typing code reads it");
    }


    /** K7b: a quoted special is text for type inference; an unquoted one is a missing. */
    @Test
    void testIsDoubleOrMissingSpecials()
    {
        CsvRecord rec = new CsvRecord(new String[]
        {
                ".A", ".A", "._", ".a"
        }, new boolean[]
        {
                true, false, false, false
        });
        assertFalse(rec.isDoubleOrMissing(0));
        assertTrue(rec.isDoubleOrMissing(1));
        assertTrue(rec.isDoubleOrMissing(2));
        assertFalse(rec.isDoubleOrMissing(3));
    }


    /**
     * The quote flags run parallel to the values: an array of any other length would either be read
     * past its end or silently ignore cells, so every constructor refuses it loudly.
     */
    @Test
    void testQuotedFlagsOfAnotherLengthIsRefused()
    {
        String[] two =
        {
                ".", "."
        };
        IllegalArgumentException shorter = assertThrows(IllegalArgumentException.class,
                () -> new CsvRecord(two, new boolean[]
                {
                        true
                }));
        assertTrue(
                String.valueOf(shorter.getMessage()).contains("1")
                        && String.valueOf(shorter.getMessage()).contains("2"),
                shorter.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new CsvRecord(two, new boolean[3]));
        assertThrows(IllegalArgumentException.class,
                () -> CsvRecord.builder().values(two).quoted(new boolean[0]).build());
    }


    /** Without quote flags (or with flags of the right length) every constructor accepts. */
    @Test
    void testQuotedFlagsAreOptionalAndLengthMatched()
    {
        String[] two =
        {
                ".", "."
        };
        assertSame(MissingValue.MIS, new CsvRecord(two, null).getUnquotedMissing(1));
        assertSame(MissingValue.MIS, CsvRecord.builder().values(two).build().getUnquotedMissing(0));
        boolean[] flags =
        {
                false, true
        };
        CsvRecord built = CsvRecord.builder().values(two).quoted(flags).build();
        flags[0] = true;
        assertSame(MissingValue.MIS, built.getUnquotedMissing(0), "the flags are copied");
        assertNull(built.getUnquotedMissing(1));
        assertThrows(NullPointerException.class, () -> CsvRecord.builder().build());
    }


    @Test
    void testIsDoubleOrMissingString()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "hello"
        });
        assertFalse(csvRec.isDoubleOrMissing(0));
    }


    @Test
    void testIsLongValueValid()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "42", "0", "-100"
        });
        assertTrue(csvRec.isLongValue(0));
        assertTrue(csvRec.isLongValue(1));
        assertTrue(csvRec.isLongValue(2));
    }


    @Test
    void testIsLongValueDecimal()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "3.14"
        });
        assertFalse(csvRec.isLongValue(0));
    }


    @Test
    void testIsLongValueString()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "abc"
        });
        assertFalse(csvRec.isLongValue(0));
    }


    @Test
    void testGetValuesReturnsDefensiveCopy()
    {
        String[] original =
        {
                "a", "b"
        };
        CsvRecord csvRec = new CsvRecord(original);
        String[] copy = csvRec.getValues();
        assertArrayEquals(original, copy);
        assertNotSame(original, copy);
        // mutating copy should not affect the record
        copy[0] = "mutated";
        assertEquals("a", csvRec.getValue(0));
    }


    @Test
    void testConstructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new CsvRecord(null));
    }

}
