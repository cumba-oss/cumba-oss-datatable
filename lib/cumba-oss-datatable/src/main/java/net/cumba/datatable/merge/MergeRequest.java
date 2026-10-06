package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import net.cumba.datatable.IDataTable;
import org.jspecify.annotations.Nullable;

/**
 * Everything a {@link MergedTable} is built from: the primary, its declared merges, the resolver
 * that supplies the datasets they name, and the naming rule. Each merge kind builds its own part;
 * the parts are laid out as primary, keyed partners (in order, each followed by its
 * {@code _matched_} flag when {@code matchedFlags} is set), child parents, RELREC related domains
 * (with their flags), then the primary's supplemental qualifiers.
 *
 * <ul>
 * <li>{@code primaryDomain} — the primary's domain for the {@code RDOMAIN} comparisons, or
 * {@code null} to read its {@code DOMAIN} column (first row), falling back to the dataset
 * name.</li>
 * <li>{@code matchedFlags} — whether every keyed, child and RELREC part publishes a
 * {@code <qualifier>._matched_} column (the engine), or none does (the browser).</li>
 * <li>{@code standard} — the standard the {@code SUPP--} and {@code RELREC} datasets are read under
 * ({@link MergeStandard}); {@link MergeStandard#UNKNOWN} by default, which infers SEND from a
 * {@code POOLID} column.</li>
 * </ul>
 */
@Value
@Builder
public class MergeRequest
{

    @NonNull
    IDataTable primary;

    @Nullable
    String primaryDomain;

    @NonNull
    @Singular("keyed")
    List<KeyedMergeSpec> keyed;

    @Nullable
    ChildMergeSpec child;

    @Nullable
    RelrecPartSpec relrec;

    @Nullable
    SuppMergeSpec supp;

    @NonNull
    DatasetResolver resolver;

    @NonNull
    @Builder.Default
    MergeNaming naming = MergeNaming.QUALIFIED;

    boolean matchedFlags;

    @NonNull
    @Builder.Default
    MergeStandard standard = MergeStandard.UNKNOWN;
}
