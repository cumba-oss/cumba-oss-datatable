package net.cumba.datatable.manager.local;

import java.net.URI;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.manager.ILibraryMemberRef;
import org.jspecify.annotations.Nullable;

/**
 * Local implementation of {@link ILibraryMemberRef} that directly holds a reference to the
 * {@link ILibraryMember} and the {@link LocalDataTableManager} it belongs to.
 */
@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class LocalLibraryMemberRef implements ILibraryMemberRef
{

    @Getter
    @NonNull
    @EqualsAndHashCode.Include
    private final LocalDataTableManager manager;

    @Getter
    @NonNull
    @EqualsAndHashCode.Include
    @ToString.Include
    private final ILibraryMember member;

    @Override
    public String getName()
    {
        return member.getName();
    }


    // ILibraryMember.getLabel() is @Nullable and is forwarded verbatim, matching the @Nullable
    // ILibraryMemberRef.getLabel() contract.
    @Override
    public @Nullable String getLabel()
    {
        return member.getLabel();
    }


    // A member without a backing URI legitimately has none, matching the @Nullable
    // ILibraryMemberRef.getUri() contract.
    @Override
    public @Nullable String getUri()
    {
        URI uri = member.getUri();
        return uri != null ? uri.toString() : null;
    }
}
