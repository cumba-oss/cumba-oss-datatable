package net.cumba.datatable.provider.xlsx.library;

import com.github.pjfanning.xlsx.StreamingReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.xlsx.ExcelProviderSupplier;
import net.cumba.datatable.provider.xlsx.ExcelTableProvider;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jspecify.annotations.Nullable;

public class ExcelLibraryProvider implements ILibraryProvider
{

    private static final Logger LOGGER = System.getLogger(ExcelLibraryProvider.class.getName());

    /**
     * Number of data rows sampled per sheet during library open to infer column types. Kept low
     * (50) so opening a large multi-sheet workbook stays cheap; the full sheet is re-scanned with
     * {@link ExcelTableProvider#getGuessingRowCount()} when the user opens the sheet. F-B16.
     */
    private static final int LIBRARY_TYPE_SAMPLE_ROWS = 50;

    @Override
    public String getName()
    {
        return "ExcelLibraryProvider";
    }


    @Override
    public String getDescription()
    {
        return "Provides Excel libraries";
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return ExcelLibrarySupplier.FIS;
    }


    @Override
    public IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = aUri.toURL().openStream())
        {
            try (Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096)
                    .open(in))
            {
                // TODO: if aFileInfo is null derive correct FileInfo from extension
                FileInfo fi = aFileInfo != null ? aFileInfo : ExcelProviderSupplier.FI_XLSX;
                String libName = ILibraryProvider.resolveLibraryName(aUri, aFileInfo,
                        getLibraryNameFor(aUri));
                ExcelLibrary lib = new ExcelLibrary(libName, "", aUri, fi);

                int sheetCount = workbook.getNumberOfSheets();
                List<ExcelLibraryMember> mems = new ArrayList<>();
                for (int i = 0; i < sheetCount; i++)
                {
                    Sheet sheet = workbook.getSheetAt(i);
                    String name = sheet.getSheetName();
                    // F-B16: sample the header + first ~50 data rows to surface column names +
                    // best-guess types in the library tree before the user opens the sheet. The
                    // sample is small to keep multi-sheet workbooks cheap; types may be revised
                    // when the sheet is fully read (Excel doesn't store types — they're sniffed).
                    DataTableColumnMeta @Nullable [] columns;
                    try
                    {
                        columns = ExcelTableProvider.inferColumns(sheet,
                                URIHelper.replaceFragment(aUri, name), LIBRARY_TYPE_SAMPLE_ROWS);
                    }
                    catch (RuntimeException ex)
                    {
                        // Type inference for a single sheet must never abort the whole library
                        // open. Log and continue without cached columns; the user can still open
                        // the sheet, which will trigger a full read.
                        LOGGER.log(Level.WARNING,
                                ("Column inference failed for sheet '%s' in '%s'; "
                                        + "the sheet will still appear without cached columns: %s")
                                                .formatted(name, aUri, ex.getMessage()),
                                ex);
                        columns = null;
                    }

                    ExcelLibraryMember mem = ExcelLibraryMember.builder()//
                            .name(name)//
                            .label("")//
                            .library(lib)//
                            .uri(URIHelper.replaceFragment(aUri, name))//
                            .columns(columns)//
                            .build();
                    mems.add(mem);
                }
                lib.setMembers(mems);
                return lib;
            }
            catch (IOException ex)
            {
                throw ex;
            }
            catch (Exception ex)
            {
                LOGGER.log(Level.ERROR, ex.getMessage(), ex);
                throw new IOException(ex);
            }
        }
    }


    protected String getLibraryNameFor(URI aUri)
    {
        String name = aUri.getPath();
        if (name == null)
        {
            return "";
        }
        name = CDT.getAfterLast(name, '/');
        name = CDT.getBeforeLast(name, '.');
        return name.toUpperCase(Locale.ROOT);
    }


    @Override
    public Stream<? extends ILibraryMember> provideLibraryMembers(IDataTableLibrary aLibrary)
        throws IOException
    {
        if (aLibrary instanceof ExcelLibrary exlib)
        {
            return exlib.getMembers();
        }
        return Stream.empty();
    }


    @Override
    public Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(ILibraryMember aMember)
        throws IOException
    {
        // F-B16: return the columns cached at library open. If absent (e.g. inference failed for
        // this sheet, or the member was built without sampling), return an empty stream — the
        // caller will fall through to a full sheet open.
        if (aMember instanceof ExcelLibraryMember exMember)
        {
            DataTableColumnMeta[] cols = exMember.getColumns();
            if (cols != null)
            {
                return Stream.of(cols);
            }
        }
        return Stream.empty();
    }


    @Override
    public @Nullable Object getLibraryAttribute(IDataTableLibrary aLibrary,
            @Nullable String aAttributeKey)
    {
        return null;
    }

}
