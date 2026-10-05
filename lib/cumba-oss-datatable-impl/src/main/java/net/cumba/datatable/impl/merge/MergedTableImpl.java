package net.cumba.datatable.impl.merge;

import java.util.List;
import java.util.Set;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.view.MergeDataTable;
import net.cumba.datatable.merge.MergeIssue;
import net.cumba.datatable.merge.MergedPart;
import net.cumba.datatable.merge.MergedTable;

/**
 * The merged evaluation table: a {@link MergeDataTable} over the parts {@link MergeBuilder} built,
 * carrying the part descriptions, the issues and the unresolved datasets. Row provenance and the
 * supplied marker are the {@code MergeDataTable}'s: {@code getRealRowIndex} delegates to part 0
 * (the primary, row-mapped when expanded), {@code isSupplied} to the part holding the column.
 */
final class MergedTableImpl extends MergeDataTable implements MergedTable
{

    private final IDataTable primary;

    private final List<MergedPart> parts;

    private final List<MergeIssue> issues;

    private final List<String> unresolved;

    private final Set<String> suppQnams;

    private final boolean expanded;

    MergedTableImpl(IDataTable aPrimary, IDataTable[] aTables, List<MergedPart> aParts,
            List<MergeIssue> aIssues, List<String> aUnresolved, Set<String> aSuppQnams,
            boolean aExpanded)
    {
        super(null, aTables);
        primary = aPrimary;
        parts = List.copyOf(aParts);
        issues = List.copyOf(aIssues);
        unresolved = List.copyOf(aUnresolved);
        suppQnams = new java.util.LinkedHashSet<>(aSuppQnams);
        expanded = aExpanded;
    }


    @Override
    public IDataTable primary()
    {
        return primary;
    }


    @Override
    public List<MergedPart> parts()
    {
        return parts;
    }


    @Override
    public MergedPart partOf(int aColumn) throws IndexOutOfBoundsException
    {
        for (MergedPart p : parts)
        {
            if (p.contains(aColumn))
            {
                return p;
            }
        }
        throw new IndexOutOfBoundsException(
                ExMsgs.indexOutOfBounds("column", aColumn, 0, getColumnCount()));
    }


    @Override
    public boolean isExpanded()
    {
        return expanded;
    }


    @Override
    public List<MergeIssue> issues()
    {
        return issues;
    }


    @Override
    public List<String> unresolvedDatasets()
    {
        return unresolved;
    }


    @Override
    public Set<String> suppQnams()
    {
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(suppQnams));
    }
}
