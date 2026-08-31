package net.cumba.datatable.impl.io;

import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.IGenericProvider;
import net.cumba.datatable.io.IGenericSupplier;
import org.jspecify.annotations.Nullable;

/**
 * An abstract implementation of the {@link IGenericProvider} interface that provides getter and
 * setter for name and description.
 *
 * @param <V>
 *            the type of value produced by this provider.
 */
// name/description are @NonNull (per IGenericProvider.getName/getDescription) but are populated by
// subclasses through the protected Lombok setters after construction; NullAway cannot see that
// deferred init, so its init check is suppressed here.
@SuppressWarnings("NullAway.Init")
public abstract class AbstractGenericProvider<V> implements IGenericProvider<V>
{

    /**
     * Optional field that contains the supplier that created this instance. This might be null and
     * not every provider is created by a supplier.
     */
    @Getter
    private final @Nullable IGenericSupplier<? extends IGenericProvider<? extends V>> supplier;

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String name;

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String description;

    protected AbstractGenericProvider()
    {
        this(null);
    }


    protected <S extends IGenericSupplier<? extends IGenericProvider<? extends V>>> AbstractGenericProvider(
            @Nullable S aSupplier)
    {
        supplier = aSupplier;
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return supplier != null ? supplier.getSupportedFileInfos() : Collections.emptyList();
    }
}
