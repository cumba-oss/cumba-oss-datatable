package net.cumba.datatable.manager;

import java.util.List;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * Selection of RELREC relationships to merge — the argument of the merged evaluation table's RELREC
 * part ({@code net.cumba.datatable.merge.RelrecPartSpec}); in the internal twin also the parameter
 * of the manager's {@code mergeRelrecRelationships}, which this twin does not carry.
 *
 * <p>
 * Immutable and JSON-serializable so it can cross the remote-manager wire as a single parameter.
 * The UI builds it from the (small) RELREC dataset it reads to populate the merge dialog; the
 * manager re-reads RELREC server-side and restricts the computed relationship pairs to the
 * {@code getRelationships()}-selected {@code (RDOMAIN, RELID)} items. An empty selection merges all
 * relationships available for the table's domain.
 * </p>
 */
@Value
@Builder
@Jacksonized
public class RelrecMergeSpec
{

    /**
     * The selected relationships to merge. Empty merges all relationships for the domain.
     */
    @Singular("relationship")
    List<Item> relationships;

    /**
     * One selected RELREC relationship, identified by the related domain and the relationship id.
     */
    @Value
    @Builder
    @Jacksonized
    public static class Item
    {

        /** The related domain ({@code RDOMAIN}) to merge in. */
        String rdomain;

        /**
         * The relationship id ({@code RELID}) to restrict to, or {@code null} to merge <b>all</b>
         * relationships of {@link #rdomain} for the primary domain. The interactive dialog selects
         * whole related domains and therefore leaves this {@code null}; the field is retained so a
         * caller can still target a single named relationship programmatically / over the wire.
         */
        @Nullable
        String relid;
    }
}
