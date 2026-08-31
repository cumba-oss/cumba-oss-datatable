package net.cumba.datatable.impl.library;

import java.util.Comparator;

import net.cumba.datatable.library.ILibraryMember;

public class LibraryMemberComparator implements Comparator<ILibraryMember>
{

    @Override
    public int compare(ILibraryMember aMember1, ILibraryMember aMember2)
    {
        String name1 = (aMember1 != null) ? aMember1.getName() : null;
        String name2 = (aMember2 != null) ? aMember2.getName() : null;
        if (name1 == null)
        {
            return name2 == null ? 0 : 1;
        }
        if (name2 == null)
        {
            return -1;
        }
        return name1.compareToIgnoreCase(name2);
    }
}
