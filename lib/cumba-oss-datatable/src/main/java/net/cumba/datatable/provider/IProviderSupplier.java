package net.cumba.datatable.provider;

import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.IGenericSupplier;
import net.cumba.datatable.library.ILibraryMember;
import org.jspecify.annotations.Nullable;

/**
 * Interface for a supplier that can supply {@link IDataTableProvider}s.
 */
public interface IProviderSupplier extends IGenericSupplier<IDataTableProvider>
{

    /**
     * Check if the supplier can provide a provider for the given library member. The default
     * implementation used the members {@link ILibraryMember#getUri()} and calls
     * {@link #canProvideFor(java.net.URI, FileInfo)}.
     *
     * @param aMember
     *            the member to check for.
     * @param aFileInfo
     *            the file info to be used to open. This might be null in what case the members
     *            default file info should be used.
     * @return true if a provider can be supplied, false otherwise. This method might return true
     *         even if a later call to {@link #getProvider(ILibraryMember, FileInfo)} returns null.
     */
    default boolean canProvideFor(ILibraryMember aMember, @Nullable FileInfo aFileInfo)
    {
        if (aMember == null)
        {
            return false;
        }
        FileInfo fi = aFileInfo != null ? aFileInfo : aMember.getFileInfo();
        return canProvideFor(aMember.getUri(), fi);
    }


    /**
     * Retrieve a provider for the given member.The default implementation used the members
     * {@link ILibraryMember#getUri()} and calls {@link #getProvider(java.net.URI, FileInfo)}.
     *
     * @param aMember
     *            the member to retrieve the provider for.
     * @param aFileInfo
     *            the file info to be used to open. This might be null in what case the members
     *            default file info should be used.
     * @return a provider for the given member or null if no provider can be supplied.
     */
    default @Nullable IDataTableProvider getProvider(ILibraryMember aMember,
            @Nullable FileInfo aFileInfo)
    {
        if (aMember == null)
        {
            return null;
        }
        FileInfo fi = aFileInfo != null ? aFileInfo : aMember.getFileInfo();

        return getProvider(aMember.getUri(), fi);
    }
}
