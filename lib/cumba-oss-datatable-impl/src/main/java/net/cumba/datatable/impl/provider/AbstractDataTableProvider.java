package net.cumba.datatable.impl.provider;

import static net.cumba.datatable.help.AsyncSupport.joinOrThrowIO;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.List;
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
import net.cumba.datatable.io.Property;
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
    public @Nullable IDataTable provide(@NonNull ILibraryMember aMember,
            @Nullable FileInfo aFileInfo)
        throws IOException, NullPointerException
    {
        return provide(aMember.getUri(), aFileInfo);
    }


    /**
     * Retrieve the value of the given property as String.
     *
     * @param aProperty
     *            the property to be handled.
     * @param aValue
     *            the value to access. This might be null to retrieve the default value.
     * @return the given value if this is not null or the default value.
     */
    protected String getStringValue(Property aProperty, @Nullable String aValue)
    {
        if (aValue != null)
        {
            return aValue;
        }
        return aProperty.defaultValue();
    }


    /**
     * Retrieve the value of the given property as boolean.
     *
     * @param aProperty
     *            the property to be handled.
     * @param aValue
     *            the value to access as boolean. This might be null or any string as given by the
     *            user.
     * @return the boolean representation of the property.
     */
    protected boolean getBooleanValue(Property aProperty, @Nullable String aValue)
    {
        if (aValue != null && !aValue.isBlank())
        {
            if (aValue.equalsIgnoreCase("true"))
            {
                return true;
            }
            if (aValue.equalsIgnoreCase("false"))
            {
                return false;
            }
        }
        return Boolean.parseBoolean(aProperty.defaultValue());
    }


    /**
     * Retrieve the value of the given property as a list of strings. The value is split by comma.
     *
     * @param aProperty
     *            the property to be handled.
     * @param aValue
     *            the value to access as list. This might be null or any comma-separated string as
     *            given by the user.
     * @return the list representation of the property.
     */
    protected List<String> getListValue(Property aProperty, @Nullable String aValue)
    {
        if (aValue != null && !aValue.isBlank())
        {
            String[] vals = aValue.split(",");
            return Arrays.asList(vals);
        }
        return Arrays.asList(aProperty.defaultValue().split(","));
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
