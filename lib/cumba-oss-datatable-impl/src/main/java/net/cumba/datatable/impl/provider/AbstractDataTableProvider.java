package net.cumba.datatable.impl.provider;

import static net.cumba.datatable.help.AsyncSupport.joinOrThrowIO;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.databuffer.IDataBuffer;
import net.cumba.datatable.impl.io.AbstractGenericProvider;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.provider.IDataTableProvider;
import org.jspecify.annotations.Nullable;

/**
 * An abstract implementation of a provider for data tables.
 */
// name/description/metadata are logical non-null properties populated through the
// (protected/public)
// Lombok setters after construction, before any accessor use; NullAway cannot see that deferred
// init, so its init check is suppressed here.
@SuppressWarnings("NullAway.Init")
@CustomLog
public abstract class AbstractDataTableProvider extends AbstractGenericProvider<IDataTable>
        implements
        IDataTableProvider
{

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String name;

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String description;

    @Getter
    @Setter
    protected IMetadataLibrary metadata;

    @Override
    public IDataTable provide(@NonNull ILibraryMember aMember, @Nullable FileInfo aFileInfo)
        throws IOException, NullPointerException
    {
        return provide(aMember.getUri(), aFileInfo);
    }


    protected CachedDataTableColumn[] createCachedColumns(DataTableMeta aMeta)
    {
        CachedDataTableColumn[] cols = new CachedDataTableColumn[aMeta.getColumnCount()];

        for (int i = 0; i < cols.length; i++)
        {
            DataTableColumnMeta cm = aMeta.getColumn(i);
            cols[i] = new CachedDataTableColumn(i, cm.getType());
        }
        return cols;
    }


    protected void completeParsedColumns(Stream<CachedDataTableColumn> aColumns) throws IOException
    {
        CompletableFuture<?>[] futures = aColumns//
                .map(c -> CompletableFuture.runAsync(c::complete))//
                .toArray(CompletableFuture[]::new);

        joinOrThrowIO(CompletableFuture.allOf(futures));
    }


    protected void debugCalcDataSize(IDataTable aTable)
    {
        long totalSize = 0;
        DataTableMeta m = aTable.getMetaData();
        for (int i = 0; i < aTable.getColumnCount(); i++)
        {
            IDataTableColumn c = aTable.getColumn(i);
            if (!(c instanceof CachedDataTableColumn col))
            {
                continue;
            }

            DataTableColumnMeta cm = m.getColumn(i);
            IDataBuffer db = col.getDataBuffer();
            long bytes = db.getEstimatedMemoryBytes();
            totalSize += bytes;

            LOGGER.log(Level.DEBUG,
                    "%s[%s]: %d bytes".formatted(cm.getName(), cm.getType(), bytes));
        }
        LOGGER.log(Level.DEBUG, "Calculated a total data size of: " + totalSize + " bytes!");
    }

}
