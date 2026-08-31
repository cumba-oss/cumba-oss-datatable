package net.cumba.datatable.impl.library;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.cumba.datatable.library.ILibraryProvider;

// name/description are non-null logical properties populated by subclasses through the protected
// Lombok setters after construction; NullAway cannot see that deferred init, so suppress its check.
@SuppressWarnings("NullAway.Init")
public abstract class AbstractLibraryProvider implements ILibraryProvider
{

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String name;

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String description;
}
