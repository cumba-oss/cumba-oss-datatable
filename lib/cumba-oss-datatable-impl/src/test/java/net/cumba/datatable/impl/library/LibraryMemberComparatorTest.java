package net.cumba.datatable.impl.library;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import org.junit.jupiter.api.Test;

class LibraryMemberComparatorTest
{

    private final LibraryMemberComparator comparator = new LibraryMemberComparator();

    private ILibraryMember createMember(String name)
    {
        return new ILibraryMember()
        {

            @Override
            public String getName()
            {
                return name;
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///test");
            }


            @Override
            public IDataTableLibrary getLibrary()
            {
                return null;
            }
        };
    }

    // ==================== Basic Comparison ====================


    @Test
    void testCompareEqual()
    {
        assertEquals(0, comparator.compare(createMember("alpha"), createMember("alpha")));
    }


    @Test
    void testCompareLessThan()
    {
        assertTrue(comparator.compare(createMember("alpha"), createMember("beta")) < 0);
    }


    @Test
    void testCompareGreaterThan()
    {
        assertTrue(comparator.compare(createMember("beta"), createMember("alpha")) > 0);
    }

    // ==================== Case Insensitivity ====================


    @Test
    void testCompareCaseInsensitive()
    {
        assertEquals(0, comparator.compare(createMember("Alpha"), createMember("alpha")));
    }


    @Test
    void testCompareCaseInsensitiveOrder()
    {
        assertTrue(comparator.compare(createMember("ALPHA"), createMember("BETA")) < 0);
    }

    // ==================== Null Handling ====================


    @Test
    void testCompareBothNull()
    {
        assertEquals(0, comparator.compare(null, null));
    }


    @Test
    void testCompareFirstNull()
    {
        // Null member sorts last (returns 1)
        assertTrue(comparator.compare(null, createMember("alpha")) > 0);
    }


    @Test
    void testCompareSecondNull()
    {
        // Non-null sorts before null (returns -1)
        assertTrue(comparator.compare(createMember("alpha"), null) < 0);
    }


    @Test
    void testCompareNullName()
    {
        // Member with null name sorts last
        assertTrue(comparator.compare(createMember(null), createMember("alpha")) > 0);
    }


    @Test
    void testCompareBothNullNames()
    {
        assertEquals(0, comparator.compare(createMember(null), createMember(null)));
    }


    @Test
    void testCompareSecondNullName()
    {
        assertTrue(comparator.compare(createMember("alpha"), createMember(null)) < 0);
    }

    // ==================== Sorting ====================


    @Test
    void testSortingOrder()
    {
        List<ILibraryMember> members = Arrays.asList(createMember("charlie"), createMember("alpha"),
                createMember("bravo"));

        members.sort(comparator);

        assertEquals("alpha", members.get(0).getName());
        assertEquals("bravo", members.get(1).getName());
        assertEquals("charlie", members.get(2).getName());
    }


    @Test
    void testSortingWithNullsLast()
    {
        List<ILibraryMember> members = Arrays.asList(createMember("beta"), null,
                createMember("alpha"));

        members.sort(comparator);

        assertEquals("alpha", members.get(0).getName());
        assertEquals("beta", members.get(1).getName());
        assertNull(members.get(2));
    }
}
