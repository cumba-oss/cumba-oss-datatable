package net.cumba.datatable.metadata;

import org.jspecify.annotations.Nullable;

/**
 * A single mapping entry of a codelist.
 */
public interface ICodelistEntry extends IMetadataElement
{

    /**
     * Returns the code value as String.
     *
     * @return the code value as String.
     */
    String getCodeValue();


    /**
     * Returns the decode / label value as string.
     *
     * @return the decode / label value as string.
     */
    String getDecodeValue();


    /**
     * Returns the term's concept identifier (NCI C-code), or {@code null} when the source does not
     * model one. Backs the {@code var_codelist_coded_codes("LIBRARY")} accessor (ADaM code-bearing
     * variables). Default {@code null} so providers without a concept id are unaffected.
     *
     * @return the term concept id, or {@code null}.
     */
    default @Nullable String getConceptId()
    {
        return null;
    }

}
