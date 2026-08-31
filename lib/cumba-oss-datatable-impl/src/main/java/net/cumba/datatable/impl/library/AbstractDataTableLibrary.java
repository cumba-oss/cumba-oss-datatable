package net.cumba.datatable.impl.library;

import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.report.ValidationFinding;
import org.jspecify.annotations.Nullable;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
public abstract class AbstractDataTableLibrary implements IDataTableLibrary
{

    /**
     * The library name. Mutable so that {@code IDataTableManager.renameLibrary} can update it in
     * place; {@code volatile} so that other threads see the new value without external
     * synchronisation. Excluded from {@link EqualsAndHashCode} — library identity is the URI.
     */
    private volatile String name;

    /**
     * Mutable for the same reason as {@link #name}; not part of equality.
     */
    private volatile @Nullable String label;

    @EqualsAndHashCode.Include
    private final URI uri;

    private final @Nullable FileInfo fileInfo;

    @Setter
    private @Nullable IMetadataLibrary metadata;

    @Getter(value = AccessLevel.PROTECTED)
    protected @Nullable Map<String, List<ValidationFinding>> memberIssues;

    protected AbstractDataTableLibrary(String aName, @Nullable String aLabel, URI aUri)
    {
        this(aName, aLabel, aUri, null);
    }


    protected AbstractDataTableLibrary(String aName, @Nullable String aLabel, URI aUri,
            @Nullable FileInfo aFileInfo)
    {
        name = aName;
        label = aLabel;
        uri = aUri;
        fileInfo = aFileInfo;
    }


    /**
     * Update the library's name and label. Intended to be called from
     * {@code IDataTableManager.renameLibrary}; not part of {@link IDataTableLibrary} so external
     * callers must go through the manager.
     *
     * @param aName
     *            the new name (must not be {@code null}).
     * @param aLabel
     *            the new label (may be {@code null}).
     */
    public void rename(String aName, @Nullable String aLabel)
    {
        name = aName;
        label = aLabel;
    }

}
