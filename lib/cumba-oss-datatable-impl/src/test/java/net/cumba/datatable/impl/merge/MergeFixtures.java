package net.cumba.datatable.impl.merge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.merge.DatasetResolver;
import net.cumba.datatable.merge.MergeRequest;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Small typed tables from text, for the merged-table tests. A header names the columns; a
 * {@code :N} suffix makes a DOUBLE column, {@code :L} a LONG one, anything else is STRING. A cell
 * {@code <MIS>} is the missing value {@code MIS}, {@code <MIS_A>} the marker {@code MIS_A}; an
 * empty cell is {@code ""} in a STRING column and {@code MIS} in a numeric one.
 */
final class MergeFixtures
{

    /** The marker printed for a cell that is not supplied. */
    static final String UNSUPPLIED = "·";

    private MergeFixtures()
    {
    }


    static IDataTable table(String aName, String aHeader, String... aRows)
    {
        String[] heads = aHeader.split(",", -1);
        int colCount = heads.length;
        CachedDataTableColumn[] columns = new CachedDataTableColumn[colCount];
        DataTableColumnMeta[] metas = new DataTableColumnMeta[colCount];
        DataValueType[] types = new DataValueType[colCount];
        for (int c = 0; c < colCount; c++)
        {
            String h = heads[c].strip();
            DataValueType type = DataValueType.STRING;
            String name = h;
            if (h.endsWith(":N"))
            {
                type = DataValueType.DOUBLE;
                name = h.substring(0, h.length() - 2);
            }
            else if (h.endsWith(":L"))
            {
                type = DataValueType.LONG;
                name = h.substring(0, h.length() - 2);
            }
            types[c] = type;
            columns[c] = new CachedDataTableColumn(c, type);
            metas[c] = DataTableColumnMeta.builder().index(c).name(name).label(name + " label")
                    .type(type).length(type == DataValueType.STRING ? 40 : 8).build();
        }
        for (String row : aRows)
        {
            String[] cells = row.split(",", -1);
            if (cells.length != colCount)
            {
                throw new IllegalArgumentException(
                        "row '" + row + "' has " + cells.length + " cells, header " + colCount);
            }
            for (int c = 0; c < colCount; c++)
            {
                columns[c].addElement(cell(cells[c].strip(), types[c]));
            }
        }
        for (CachedDataTableColumn col : columns)
        {
            col.complete();
        }
        DataTableMeta meta = DataTableMeta.builder().name(aName).columns(metas)
                .rowCount(aRows.length).totalRowCount(aRows.length).build();
        return new ColumnCachedDataTable(meta, columns);
    }


    private static @Nullable Object cell(String aText, DataValueType aType)
    {
        if (aText.startsWith("<") && aText.endsWith(">"))
        {
            return MissingValue.valueOf(aText.substring(1, aText.length() - 1));
        }
        if (aType == DataValueType.STRING)
        {
            return aText;
        }
        if (aText.isEmpty())
        {
            return MissingValue.MIS;
        }
        return aType == DataValueType.LONG ? (Object) Long.valueOf(aText)
                : (Object) Double.valueOf(aText);
    }


    /** A resolver over the given tables, by name, case-insensitively. */
    static DatasetResolver resolver(IDataTable... aTables)
    {
        Map<String, IDataTable> byName = new LinkedHashMap<>();
        for (IDataTable t : aTables)
        {
            String name = t.getMetaData().getName();
            byName.put(name == null ? "" : name.toUpperCase(Locale.ROOT), t);
        }
        return name -> byName.get(name.toUpperCase(Locale.ROOT));
    }


    static MergeRequest.MergeRequestBuilder request(IDataTable aPrimary, IDataTable... aOthers)
    {
        return MergeRequest.builder().primary(aPrimary).resolver(resolver(aOthers));
    }


    /** The cell's text, {@link #UNSUPPLIED} when the cell is not supplied. */
    static String text(IDataTable aTable, long aRow, String aColumn)
    {
        int col = aTable.getMetaData().getColumnIndex(aColumn);
        if (col < 0)
        {
            throw new IllegalArgumentException("no column " + aColumn + " in " + aTable
                    .getMetaData().getAllColumns().map(DataTableColumnMeta::getName).toList());
        }
        if (!aTable.isSupplied(aRow, col))
        {
            return UNSUPPLIED;
        }
        IDataValue dv = aTable.getDataValue(aRow, col);
        return dv.isMissingOrInvalid() ? "<" + dv.getValueAsString() + ">" : dv.getValueAsString();
    }


    /** The row's cells for the given columns, as {@link #text}. */
    static List<String> row(IDataTable aTable, long aRow, String... aColumns)
    {
        List<String> out = new ArrayList<>();
        for (String c : aColumns)
        {
            out.add(text(aTable, aRow, c));
        }
        return out;
    }


    /** One column down every row, as {@link #text}. */
    static List<String> column(IDataTable aTable, String aColumn)
    {
        List<String> out = new ArrayList<>();
        for (long r = 0; r < aTable.getRowCount(); r++)
        {
            out.add(text(aTable, r, aColumn));
        }
        return out;
    }


    static List<String> columnNames(IDataTable aTable)
    {
        return aTable.getMetaData().getAllColumns().map(DataTableColumnMeta::getName).toList();
    }
}
