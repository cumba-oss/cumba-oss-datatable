package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.ArrayList;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A minimal in-memory {@link IDataTable} for the API module's tests: STRING columns from text, a
 * {@code <MIS>} cell the missing value {@code MIS}, a {@code :N} header suffix a DOUBLE column.
 */
final class ListTable implements IDataTable
{

    private final DataTableMeta meta;

    private final List<@Nullable Object[]> rows = new ArrayList<>();

    ListTable(String aName, String aHeader, String... aRows)
    {
        String[] heads = aHeader.split(",", -1);
        DataTableColumnMeta[] metas = new DataTableColumnMeta[heads.length];
        DataValueType[] types = new DataValueType[heads.length];
        for (int c = 0; c < heads.length; c++)
        {
            String h = heads[c].strip();
            boolean numeric = h.endsWith(":N");
            types[c] = numeric ? DataValueType.DOUBLE : DataValueType.STRING;
            metas[c] = DataTableColumnMeta.builder().index(c)
                    .name(numeric ? h.substring(0, h.length() - 2) : h).type(types[c]).build();
        }
        for (String row : aRows)
        {
            String[] cells = row.split(",", -1);
            Object[] values = new Object[heads.length];
            for (int c = 0; c < heads.length; c++)
            {
                String t = cells[c].strip();
                if (t.startsWith("<") && t.endsWith(">"))
                {
                    values[c] = MissingValue.valueOf(t.substring(1, t.length() - 1));
                }
                else if (types[c] == DataValueType.DOUBLE)
                {
                    values[c] = t.isEmpty() ? MissingValue.MIS : Double.valueOf(t);
                }
                else
                {
                    values[c] = t;
                }
            }
            rows.add(values);
        }
        meta = DataTableMeta.builder().name(aName).columns(metas).rowCount(rows.size())
                .totalRowCount(rows.size()).build();
    }


    @Override
    public DataTableMeta getMetaData()
    {
        return meta;
    }


    @Override
    public long getRowCount()
    {
        return rows.size();
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        return rows.get((int) aRow)[aColumn];
    }
}
