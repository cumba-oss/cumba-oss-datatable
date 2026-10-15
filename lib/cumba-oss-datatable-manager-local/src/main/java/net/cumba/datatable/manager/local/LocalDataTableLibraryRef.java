package net.cumba.datatable.manager.local;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p>
 * <b>Owning vs borrowed refs (Q44).</b> A cached {@link IDataTableLibrary} can be handed out
 * through several doors, and they do not all mean the same thing:
 * <ul>
 * <li><b>Owning</b> — produced by
 * {@link LocalDataTableManager#getLibraryRef(URI, net.cumba.datatable.io.FileInfo)}, the one and
 * only door that loads and caches a library. Such a ref carries an {@link Ownership} token, i.e. it
 * holds one reference count on the cached library, and its holder is expected to hand it back to
 * {@link LocalDataTableManager#releaseLibraryRef(IDataTableLibraryRef)} exactly once. The instance
 * is closed when the LAST owning ref on it is released (and evicted, if it is still the cached
 * one).</li>
 * <li><b>Borrowed</b> — every other ref: one built directly through this class's builder or
 * constructor, and the doors this manager does not expose (a library derived from one of its
 * members, an enumeration of the open libraries, a reference-library lookup). These are views onto
 * a library somebody else already opened; they carry no token, they do not keep it alive, and
 * releasing one is a programming error that is reported rather than obeyed.</li>
 * </ul>
 * Counting the borrowed doors as well was considered and rejected: their callers take a ref per
 * lookup and never release it, so counting them would mean no library is ever closed again — the "a
 * partial refcount converts a use-after-close into a leak" failure, merely in the other direction.
 * <p>
 * {@link Ownership} deliberately takes part in neither {@code equals} nor {@code hashCode}: an
 * owning and a borrowed ref for the same library stay equal, which is what callers comparing refs
 * for the same library rely on.
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

    /**
     * The single acquisition this ref owns, or {@code null} when this ref is a borrowed view. See
     * the class javadoc. Not part of {@code equals}/{@code hashCode}/{@code toString}.
     */
    private final @Nullable Ownership ownership;

    /**
     * Whether this ref holds a reference count on the cached library (and therefore must be
     * released exactly once), as opposed to being a borrowed view.
     *
     * @return {@code true} for an owning ref.
     */
    public boolean isOwning()
    {
        return ownership != null;
    }


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

    /**
     * One acquisition of one loaded library instance, identified by object identity.
     * <p>
     * Identity - rather than a plain counter - is what makes release exact: a release removes
     * exactly this token from the owner set of the instance it was acquired on, never a count that
     * belongs to somebody else. The token points at that instance's own set, so a release counts
     * the right instance down even after a reload put a successor under the same URI. It also
     * carries the URI the library was acquired (and cached) under, so the release keys the cache by
     * the SAME key the acquisition used rather than by {@code library.getUri()}, which a provider
     * is free to answer differently.
     * <p>
     * A token is released at most once: {@link #markReleased()} flips its flag on the first
     * release, and every later release of the same token is refused (and reported) before it can
     * count, close, evict or unlock anything.
     * <p>
     * The constructor is package-private on purpose: only {@link LocalCacheSupport} mints tokens,
     * so a token cannot be minted outside this package even though the builder property is public.
     * An existing token can be copied into another ref; the copy shares its released flag, so
     * releasing the copy is refused as a repeat.
     */
    public static final class Ownership
    {

        private final URI uri;

        private final LocalCacheSupport.InstanceOwners owners;

        private final AtomicBoolean released = new AtomicBoolean();

        Ownership(URI aUri, LocalCacheSupport.InstanceOwners aOwners)
        {
            uri = aUri;
            owners = aOwners;
        }


        URI getUri()
        {
            return uri;
        }


        LocalCacheSupport.InstanceOwners getOwners()
        {
            return owners;
        }


        /**
         * Mark this acquisition released.
         *
         * @return {@code true} on the first call only; {@code false} for a repeated release.
         */
        boolean markReleased()
        {
            return released.compareAndSet(false, true);
        }
    }
}
