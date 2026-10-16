package net.cumba.datatable.impl.library;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.library.ILibrarySupplier;
import org.jspecify.annotations.Nullable;

/**
 * Test-only {@link ILibrarySupplier}, registered in {@code src/test/resources/META-INF/services}
 * beside {@link B2ThrowingSecondLibrarySupplier}: both answer only the dedicated scheme
 * {@value #SCHEME} and both throw, so {@code LibraryProviderFactory.provide} must surface the first
 * failure with the second {@linkplain Throwable#addSuppressed suppressed} (B2-C4,
 * PLAN-bugfix-datatable-readers-views U12). It declares no {@link FileInfo}, so it is invisible to
 * every lookup by file name or extension.
 */
public class B2ThrowingFirstLibrarySupplier implements ILibrarySupplier
{

    /** The only scheme this supplier answers. */
    public static final String SCHEME = "b2-throwing-twice";

    /** The message of the exception this supplier's provider throws. */
    public static final String MESSAGE = "b2-throwing-twice: first";

    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return List.of();
    }


    @Override
    public boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return SCHEME.equals(aUri.getScheme());
    }


    @Override
    public ILibraryProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return new ILibraryProvider()
        {

            @Override
            public String getName()
            {
                return MESSAGE;
            }


            @Override
            public String getDescription()
            {
                return MESSAGE;
            }


            @Override
            public List<FileInfo> getSupportedFileInfos()
            {
                return List.of();
            }


            @Override
            public @Nullable IDataTableLibrary provide(URI aUri2, @Nullable FileInfo aFileInfo2)
                throws IOException
            {
                throw new IOException(MESSAGE);
            }


            @Override
            public Stream<? extends ILibraryMember> provideLibraryMembers(
                    IDataTableLibrary aLibrary)
            {
                return Stream.empty();
            }


            @Override
            public Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(
                    ILibraryMember aMember)
            {
                return Stream.empty();
            }
        };
    }
}
