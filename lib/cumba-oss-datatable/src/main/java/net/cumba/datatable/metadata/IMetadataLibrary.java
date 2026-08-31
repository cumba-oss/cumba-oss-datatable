package net.cumba.datatable.metadata;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * This interface describes a metadata library. A metadata library contains metadata information
 * about a {@link net.cumba.datatable.library.IDataTableLibrary}.
 */

public interface IMetadataLibrary extends IMetadataElement
{

    /** Meta key for the CDISC standard name (e.g. "SDTM-IG", "ADaM-IG"). */
    String META_KEY_STANDARD_NAME = "StandardName";

    /** Meta key for the CDISC standard version (e.g. "3.4", "1.3"). */
    String META_KEY_STANDARD_VERSION = "StandardVersion";

    /** Meta key for the Define-XML version (e.g. "2.0", "2.1", "2.2"). */
    String META_KEY_DEFINE_VERSION = "DefineVersion";

    /**
     * Meta key for the set of CDISC Controlled-Terminology package ids referenced by this library,
     * encoded as a comma-separated list (e.g. "sdtmct-2022-03-25, adamct-2021-12-17").
     */
    String META_KEY_CT_PACKAGES = "CtPackages";

    /**
     * Meta key for the URI the metadata was loaded from (e.g. a {@code define.xml} URL). Only
     * populated by implementations that have a single, well-defined source — most notably
     * {@code DefineMetadataLibrary}. Implementations that aggregate multiple sources or are derived
     * from the data files themselves should leave this absent.
     */
    String META_KEY_SOURCE_URI = "SourceUri";

    /**
     * Returns the name of the metadata library.
     *
     * @return the name of the metadata library.
     */
    @Nullable
    String getName();


    /**
     * Returns the version of the metadata library.
     *
     * @return the version of the metadata library, or {@code null} if unknown.
     */
    @Nullable
    String getVersion();


    /**
     * Returns whether column names in this metadata library are case sensitive.
     *
     * @return true if column names in this metadata library are case sensitive, false otherwise.
     */
    boolean isColumnNameCaseSensitive();


    /**
     * Returns a list of all available data table metadata in this library.
     *
     * @return a list of all available data table metadata in this library.
     */
    List<IDataTableMetadata> getDataTables();


    /**
     * Returns the metadata for the data table with the given name.
     *
     * @param aDomainName
     *            the name of the data table to retrieve the metadata for.
     * @return the metadata for the data table with the given name.
     */
    Optional<IDataTableMetadata> getDataTable(String aDomainName);


    /**
     * Returns a list of all available codelists in this library.
     *
     * @return a list of all available codelists in this library.
     */
    List<ICodeList> getCodelists();


    /**
     * Returns the codelist for the given name.
     *
     * @param aName
     *            the name of the codelist to retrieve.
     * @return the codelist for the given name.
     */
    Optional<ICodeList> getCodelist(String aName);

}
