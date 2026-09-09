package net.cumba.datatable.provider.define.library;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.CustomLog;
import net.cumba.cdisc.define.DefineCache;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.DocumentRef;
import net.cumba.cdisc.define.Leaf;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.SupplementalDoc;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.AbstractLibraryProvider;
import net.cumba.datatable.impl.library.dblib.DataBrowserLibrary;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserSourceBean;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.provider.define.metadata.DefineMetadataLibrary;
import org.jspecify.annotations.Nullable;

@CustomLog
public class DefineXmlLibraryProvider extends AbstractLibraryProvider
{

    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return Arrays.asList(DefineXmlLibrarySupplier.FI_DEFINE_XML);
    }


    @Override
    public List<Property> getProviderProperties(URI aUri, @Nullable FileInfo aFileInfo)
    {
        String studyDefault = computeStudyDefaultName(aUri);
        return List.of(ILibraryProvider.libraryNameProperty(aUri, aFileInfo, studyDefault));
    }


    @Override
    public IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        return provide(aUri, aFileInfo, Map.of());
    }


    @Override
    public IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        DefineSupport define = DefineCache.sharedInstance().getOrLoad(aUri);
        IMetadataLibrary metadata = DefineMetadataLibrary.from(define);

        // Build sources from metadata tables, skipping those without a data file URI
        // and those marked as having no data.
        DataBrowserSourceBean[] sources = buildSources(metadata);

        String studyDefault = buildStudyName(metadata);
        String libraryName = ILibraryProvider.resolveLibraryName(aUri, aFileInfo, aProperties,
                studyDefault);

        // Discover validation report URI from the Define-XML
        Map<String, String> attributes = new HashMap<>();
        String reportUri = findValidationReportUri(define);
        if (reportUri != null)
        {
            attributes.put(ATTRIBUTE_VALIDATION_REPORT, reportUri);
        }

        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder()//
                .name(libraryName)//
                .sources(sources)//
                .attributes(attributes.isEmpty() ? null : attributes)//
                .build();

        DataBrowserLibrary lib = new DataBrowserLibrary(aUri, bean);
        lib.setMetadata(metadata);
        return lib;
    }


    @Override
    public Stream<? extends ILibraryMember> provideLibraryMembers(IDataTableLibrary aLibrary)
        throws IOException
    {
        return Stream.empty();
    }


    @Override
    public Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(ILibraryMember aMember)
        throws IOException
    {
        return Stream.empty();
    }


    @Override
    public @Nullable Object getLibraryAttribute(IDataTableLibrary aLibrary,
            @Nullable String aAttributeKey)
    {
        if (aLibrary instanceof DataBrowserLibrary dbLib)
        {
            Map<String, String> attributes = dbLib.getBean().getAttributes();
            if (attributes != null)
            {
                return attributes.get(aAttributeKey);
            }
        }
        return null;
    }


    /**
     * Attempts to compute the "StudyName - StandardName Version" default by loading the Define-XML
     * via {@link DefineCache}. Returns {@code null} on any failure so callers fall back to the
     * URI-based default.
     */
    private static @Nullable String computeStudyDefaultName(URI aUri)
    {
        try
        {
            DefineSupport define = DefineCache.sharedInstance().getOrLoad(aUri);
            IMetadataLibrary metadata = DefineMetadataLibrary.from(define);
            return buildStudyName(metadata);
        }
        catch (IOException | RuntimeException ex)
        {
            // F-dtcdisc-05: the javadoc above promises "null on any failure", but only IOException
            // was caught. Define-XML parsing and DefineMetadataLibrary.from(...) can raise
            // unchecked failures (InvalidPathException, JAXB/DOM RuntimeExceptions, an NPE on a
            // structurally odd document), and those aborted provide() entirely — the library would
            // not open at all — where the documented behaviour is the URI-based fallback name.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to pre-load Define-XML for default library name: {0}", aUri, ex);
            return null;
        }
    }


    /**
     * Builds the display name "StudyName - StandardName Version" from the metadata library. Returns
     * the plain study name if the standard info is missing.
     */
    private static String buildStudyName(IMetadataLibrary aMetadata)
    {
        String name = aMetadata.getName();
        String libraryName = name != null ? name : "";
        String stdName = aMetadata.getMetaValue("StandardName")//
                .map(Object::toString).orElse(null);
        String stdVersion = aMetadata.getMetaValue("StandardVersion")//
                .map(Object::toString).orElse(null);
        if (!CDT.isBlankOrNull(stdName))
        {
            libraryName = libraryName + " - " + stdName;
            if (!CDT.isBlankOrNull(stdVersion))
            {
                libraryName = libraryName + " " + stdVersion;
            }
        }
        return libraryName;
    }


    /**
     * Builds the {@link DataBrowserSourceBean} array from the metadata library's data tables.
     *
     * <p>
     * F-E26: defensive {@code filter(Objects::nonNull)} guards against a future upstream regression
     * where a null entry could appear in the data tables list. Today the contract forbids it (the
     * implementing class in {@code DefineMetadataLibrary} maps from {@code ItemGroupDef}s, never
     * producing nulls), but the cost of the guard is one virtual call per entry and the alternative
     * is an NPE in the second-stage filter.
     * </p>
     *
     * <p>
     * Package-private for testing.
     * </p>
     */
    static DataBrowserSourceBean[] buildSources(IMetadataLibrary aMetadata)
    {
        return aMetadata.getDataTables().stream()//
                .filter(Objects::nonNull)//
                .filter(dt -> dt.getTableURI() != null)//
                .filter(dt -> !isNoData(dt))//
                // getTableURI() is non-null here (filtered above); NullAway can't track
                // the invariant across stream stages, so assert it explicitly.
                .map(dt -> DataBrowserSourceBean.builder()//
                        .uri(Objects.requireNonNull(dt.getTableURI()).toString())//
                        .name(dt.getName())//
                        .label(dt.getLabel())//
                        .build())//
                .toArray(DataBrowserSourceBean[]::new);
    }


    /**
     * Checks if the given data table metadata is marked as having no data.
     */
    private static boolean isNoData(IDataTableMetadata aDt)
    {
        return aDt.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_NO_DATA)//
                .filter(v -> "Yes".equalsIgnoreCase(String.valueOf(v)))//
                .isPresent();
    }


    /**
     * Scans the Define-XML for a validation report reference in supplemental documents. Looks for
     * document references with leaf IDs that start with {@code validation_report} or
     * {@code validationreport} (case-insensitive, optional separator). Real-world IDs that are
     * accepted include {@code validation_report-2025}, {@code ValidationReport_v1},
     * {@code validationreport-2025-04-15-rev2}.
     *
     * @param aDefine
     *            the define support to search.
     * @return the resolved validation report URI string, or {@code null} if not found.
     */
    private static @Nullable String findValidationReportUri(DefineSupport aDefine)
    {
        List<MetaDataVersion> mdvs = aDefine.getMetaDataVersions().toList();
        for (MetaDataVersion mdv : mdvs)
        {
            SupplementalDoc sd = mdv.getSupplementalDoc();
            if (sd == null)
            {
                continue;
            }
            List<DocumentRef> docRefs = sd.getDocumentRefs();
            if (CDT.isEmptyOrNull(docRefs))
            {
                continue;
            }
            for (DocumentRef dr : docRefs)
            {
                String leafId = dr.getLeafID();
                if (leafId == null)
                {
                    continue;
                }
                // F-E19: relaxed from a 3-segment dash-delimited regex to a permissive prefix
                // match. The previous pattern rejected "validation_report-2025" (underscore),
                // "ValidationReport_v1" (camelCase + 1 segment), etc.
                if (isValidationReportLeafId(leafId))
                {
                    Leaf lf = findLeafForId(mdv, leafId);
                    if (lf != null && lf.getHref() != null)
                    {
                        return aDefine.getUri().resolve(lf.getHref()).toString();
                    }
                }
            }
        }
        return null;
    }


    /**
     * F-E19: relaxed predicate for validation-report leaf IDs. Matches case-insensitively any leaf
     * ID that starts with {@code validation}, an optional {@code -} or {@code _} separator, and
     * {@code report}. Examples that match: {@code validation_report-2025},
     * {@code ValidationReport_v1}, {@code validationreport-2025-04-15-rev2}.
     *
     * <p>
     * Package-private for testing.
     * </p>
     */
    static boolean isValidationReportLeafId(String aLeafId)
    {
        if (aLeafId == null)
        {
            return false;
        }
        String lower = aLeafId.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("validationreport") || lower.startsWith("validation-report")
                || lower.startsWith("validation_report");
    }


    /**
     * Finds a leaf element by ID within the given metadata version.
     */
    private static @Nullable Leaf findLeafForId(MetaDataVersion aMDV, String aLeafId)
    {
        List<Leaf> leafs = aMDV.getLeaves();
        if (CDT.isEmptyOrNull(leafs))
        {
            return null;
        }
        for (Leaf leaf : leafs)
        {
            if (Objects.equals(aLeafId, leaf.getId()))
            {
                return leaf;
            }
        }
        return null;
    }

}
