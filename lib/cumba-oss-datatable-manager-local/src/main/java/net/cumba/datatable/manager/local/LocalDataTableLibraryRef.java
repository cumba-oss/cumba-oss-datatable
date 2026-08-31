package net.cumba.datatable.manager.local;

import java.net.URI;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.manager.IDataTableLibraryRef;
import org.jspecify.annotations.Nullable;

/**
 * Local implementation of {@link IDataTableLibraryRef} that directly holds a reference to the
 * {@link IDataTableLibrary} and the {@link LocalDataTableManager} it belongs to.
 */
@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class LocalDataTableLibraryRef implements IDataTableLibraryRef
{

    @NonNull
    @EqualsAndHashCode.Include
    private final LocalDataTableManager manager;

    @NonNull
    @EqualsAndHashCode.Include
    @ToString.Include
    private final IDataTableLibrary library;

    @Override
    public String getName()
    {
        return library.getName();
    }


    // IDataTableLibrary.getLabel() is @Nullable and is forwarded verbatim, matching the @Nullable
    // IDataTableLibraryRef.getLabel() contract.
    @Override
    public @Nullable String getLabel()
    {
        return library.getLabel();
    }


    // A library without a backing URI legitimately has none, matching the @Nullable
    // IDataTableLibraryRef.getUri() contract.
    @Override
    public @Nullable String getUri()
    {
        URI uri = library.getUri();
        return uri != null ? uri.toString() : null;
    }
}
