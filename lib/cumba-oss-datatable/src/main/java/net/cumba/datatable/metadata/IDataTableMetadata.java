package net.cumba.datatable.metadata;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A metadata object that contains metadata about a single data table.
 */
public interface IDataTableMetadata extends IMetadataElement
{

    /**
     * Returns the name of the metadata data table.
     *
     * @return the name of the metadata data table.
     */
    String getName();


    /**
     * Returns the label of the data table.
     *
     * @return the label of the data table, or {@code null} if none is defined.
     */
    @Nullable
    String getLabel();


    /**
     * Returns the URI of the data file this metadata refers to.
     *
     * @return the URI of the data file this metadata refers to, or {@code null} if no URI is
     *         available.
     */
    @Nullable
    URI getTableURI();


    /**
     * Returns a list of all available columns.
     *
     * @return a list of all available columns.
     */
    List<IColumnMetadata> getColumns();


    /**
     * Returns the metadata for the column with the given name.
     *
     * @param aColumnName
     *            the name of the column to retrieve the metadata for.
     * @return the metadata for the column with the given name.
     */
    Optional<IColumnMetadata> getColumn(String aColumnName);


    /**
     * Returns the observation class name for this dataset (e.g., "EVENTS", "FINDINGS", "SUBJECT
     * LEVEL ANALYSIS DATASET").
     *
     * @return the class name, or {@code null} if not available.
     */
    default @Nullable String getClassName()
    {
        return null;
    }


    /**
     * Returns the dataset structure description (e.g., "One record per subject", "One record per
     * subject per visit per test").
     *
     * @return the structure description, or {@code null} if not available.
     */
    default @Nullable String getStructure()
    {
        return null;
    }


    /**
     * Returns the dataset's declared subclass names (Define-XML 2.1 {@code <def:SubClass>}, e.g.
     * "TIME-TO-EVENT"), in declaration order.
     *
     * @return the declared subclass names, or an empty list when the metadata source declares none
     *         (always empty for sources without a subclass concept).
     */
    default List<String> getSubClassNames()
    {
        return List.of();
    }

}
