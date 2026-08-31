package net.cumba.datatable.manager.local;

import java.net.URI;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.manager.IDataTableRef;
import org.jspecify.annotations.Nullable;

/**
 * Local implementation of {@link IDataTableRef} that directly holds a reference to the
 * {@link IDataTable} and the {@link LocalDataTableManager} it belongs to.
 */
@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LocalDataTableRef implements IDataTableRef
{

    @Getter
    @NonNull
    @EqualsAndHashCode.Include
    private final LocalDataTableManager manager;

    @Getter
    @NonNull
    @EqualsAndHashCode.Include
    private final IDataTable table;

    // DataTableMeta exposes a @Nullable name (no name set ⇒ null). The ref simply forwards it,
    // matching the @Nullable IDataTableRef.getName() contract.
    @Override
    public @Nullable String getName()
    {
        return getMetaData().getName();
    }


    @Override
    public DataTableMeta getMetaData()
    {
        return table.getMetaData();
    }


    // A table without a backing URI legitimately has none, matching the @Nullable
    // IDataTableRef.getUri() contract.
    @Override
    public @Nullable String getUri()
    {
        URI uri = table.getMetaData().getTableURI();
        return uri != null ? uri.toString() : null;
    }


    // DataTableMeta.getName() is @Nullable; fall back to the empty string so toString never returns
    // null (Object.toString is @NonNull).
    @Override
    public String toString()
    {
        String name = table.getMetaData().getName();
        return name != null ? name : "";
    }
}
