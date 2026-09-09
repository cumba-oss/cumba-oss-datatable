package net.cumba.datatable.provider.cdt;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;

import org.jspecify.annotations.Nullable;

/**
 * Builds an {@link IDataTable} from a parsed {@link CdtDataset}. Shared by the provider, the
 * library provider, and any test-fixture loader that wants to produce a real table.
 */
public final class CdtTableBuilder
{

    /**
     * Custom column metadata key used to carry the CDT {@code codelist=...} attribute through the
     * column's metadata table.
     */
    public static final String COLUMN_META_CODELIST = "codelist";

    private CdtTableBuilder()
    {
    }


    /**
     * Build an {@link IDataTable} from the given dataset. The table's URI is set to the given URI
     * so downstream consumers can report the source.
     */
    public static IDataTable build(CdtDataset aDataset, URI aUri)
    {
        DataTableMeta meta = buildMeta(aDataset, aUri, aDataset.getDataRows().size());
        CachedDataTableColumn[] columns = createColumns(meta);
        populate(columns, aDataset);
        completeColumns(columns);
        DataTableColumnMeta @Nullable [] reconciled = reconcileLengths(meta, columns);
        DataTableMeta finalMeta = reconciled != null
                ? DataTableMeta.builderFrom(meta).columns(reconciled).build()
                : meta;
        return new ColumnCachedDataTable(finalMeta, columns);
    }


    /**
     * Build a {@link DataTableMeta} from the given dataset without reading any rows. The row count
     * is reported as the declared number of rows so metadata consumers can size tables up front.
     */
    public static DataTableMeta buildMeta(CdtDataset aDataset, URI aUri)
    {
        return buildMeta(aDataset, aUri, aDataset.getDataRows().size());
    }


    private static DataTableMeta buildMeta(CdtDataset aDataset, URI aUri, long aRowCount)
    {
        DataTableMetaBuilder b = DataTableMeta.builder()//
                .name(aDataset.getName())//
                .label(aDataset.getLabel() != null ? aDataset.getLabel() : aDataset.getName())//
                .rowCount(aRowCount)//
                .totalRowCount(aRowCount)//
                .tableURI(aUri);

        List<CdtColumn> cols = aDataset.getColumns();
        DataTableColumnMeta[] metaCols = new DataTableColumnMeta[cols.size()];
        for (int i = 0; i < cols.size(); i++)
        {
            CdtColumn c = cols.get(i);
            DataTableColumnMetaBuilder cb = DataTableColumnMeta.builder()//
                    .index(i)//
                    .name(c.getName())//
                    .type(CdtValues.toDataValueType(c.getType()))//
                    .nativeType(c.getType().token())//
                    .label(c.getLabel() != null ? c.getLabel() : c.getName())//
                    .displayFormat(c.getFormat());
            // Read once into a local: DataTableColumnMeta.length is a primitive int, so
            // calling getLength() a second time unboxes a value the null check never saw.
            Integer len = c.getLength();
            if (len != null)
            {
                cb.length(len);
            }
            if (c.getCodelist() != null)
            {
                cb.addMetaData(COLUMN_META_CODELIST, c.getCodelist());
            }
            applyGenericAttrs(cb, c);
            metaCols[i] = cb.build();
        }
        b.columns(metaCols);

        // Propagate extra dataset-level attributes (class, etc.) to table-level metadata.
        for (Map.Entry<String, String> e : aDataset.getAttrs().entrySet())
        {
            b.addMetaData(e.getKey(), e.getValue());
        }

        return b.build();
    }


    /**
     * Route generic {@code col}-line {@code key=value} attributes onto the column metadata. Keys
     * matching a {@link DataTableColumnMeta} typed field are passed to the matching setter;
     * everything else goes into the column's custom metadata table.
     */
    private static void applyGenericAttrs(DataTableColumnMetaBuilder aBuilder, CdtColumn aCol)
    {
        for (Map.Entry<String, String> e : aCol.getAttrs().entrySet())
        {
            String key = e.getKey();
            String value = e.getValue();
            switch (key)
            {
            case "nativeType" -> aBuilder.nativeType(value);
            case "displayFormat" -> aBuilder.displayFormat(value);
            case "index", "name" ->
            {
                // derived / already set — silently ignore
            }
            default -> aBuilder.addMetaData(key, value);
            }
        }
    }


    private static CachedDataTableColumn[] createColumns(DataTableMeta aMeta)
    {
        CachedDataTableColumn[] cols = new CachedDataTableColumn[aMeta.getColumnCount()];
        for (int i = 0; i < cols.length; i++)
        {
            cols[i] = new CachedDataTableColumn(i, aMeta.getColumn(i).getType());
        }
        return cols;
    }


    private static void populate(CachedDataTableColumn[] aColumns, CdtDataset aDataset)
    {
        List<CdtColumn> cdtCols = aDataset.getColumns();
        List<List<String>> rows = aDataset.getDataRows();
        for (int r = 0; r < rows.size(); r++)
        {
            List<String> row = rows.get(r);
            if (row.size() != aColumns.length)
            {
                throw new CdtParseException(String.format("row %d has %d columns, expected %d", r,
                        row.size(), aColumns.length));
            }
            for (int c = 0; c < aColumns.length; c++)
            {
                Object val = CdtValues.parseValue(row.get(c), cdtCols.get(c).getType());
                if (val instanceof String s)
                {
                    val = CDT.intern(s);
                }
                aColumns[c].addElement(val);
            }
        }
    }


    private static void completeColumns(CachedDataTableColumn[] aColumns)
    {
        CompletableFuture<?>[] fs = new CompletableFuture<?>[aColumns.length];
        for (int i = 0; i < aColumns.length; i++)
        {
            CachedDataTableColumn col = aColumns[i];
            fs[i] = CompletableFuture.runAsync(col::complete);
        }
        CompletableFuture.allOf(fs).join();
    }


    private static DataTableColumnMeta @Nullable [] reconcileLengths(DataTableMeta aMeta,
            CachedDataTableColumn[] aColumns)
    {
        int colCount = aMeta.getColumnCount();
        DataTableColumnMeta @Nullable [] out = null;
        for (int i = 0; i < colCount; i++)
        {
            DataTableColumnMeta cm = aMeta.getColumn(i);
            if (cm.getType() != DataValueType.STRING)
            {
                continue;
            }
            int declared = cm.getLength();
            int observed = aColumns[i].getMaxValueLength();
            if (observed <= declared)
            {
                continue;
            }
            if (out == null)
            {
                out = new DataTableColumnMeta[colCount];
                for (int j = 0; j < colCount; j++)
                {
                    out[j] = aMeta.getColumn(j);
                }
            }
            out[i] = cm.toBuilder().length(observed).build();
        }
        return out;
    }
}
