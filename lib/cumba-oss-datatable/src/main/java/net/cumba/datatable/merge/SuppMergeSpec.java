package net.cumba.datatable.merge;

import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The supplemental-qualifier merge of a dataset ({@code PLAN-merged-evaluation-table} §3.5): one
 * column per {@code QNAM} of the {@code SUPP--} dataset, each row served by the one supplemental
 * row that qualifies it.
 *
 * <ul>
 * <li>{@code suppDataset} — the supplemental dataset's name, or {@code null} for the IG's
 * {@code SUPP<dataset>} (a split dataset's {@code SUPP<split>} first, then {@code SUPP<domain>},
 * P-7).</li>
 * <li>{@code keys} — the key in sided form ({@code Supp_Keys}), or {@code null} for the IG's
 * default: {@code STUDYID}, {@code {left: DOMAIN, right: RDOMAIN}}, {@code USUBJID}, {@code POOLID}
 * and the {@code IDVAR} / {@code IDVARVAL} pair ("the parent column IDVAR names equals IDVARVAL").
 * A component present on both sides is used, on one side only it is ignored with a log warning, on
 * neither it is ignored (MET #19). The pair is recognised by its right-side names and must be named
 * whole (P-15).</li>
 * </ul>
 */
@Value
@Builder
public class SuppMergeSpec
{

    /** The IG's default key (SDTMIG 3.4 §8.4.1, SENDIG 3.1.1 §8.3). */
    public static final List<KeyColumn> DEFAULT_KEYS = List.of(KeyColumn.of("STUDYID"),
            KeyColumn.sided("DOMAIN", "RDOMAIN"), KeyColumn.of("USUBJID"), KeyColumn.of("POOLID"),
            KeyColumn.of("IDVAR"), KeyColumn.of("IDVARVAL"));

    @Nullable
    String suppDataset;

    @Nullable
    List<KeyColumn> keys;

    /**
     * The keys to use: the authored ones, else {@link #DEFAULT_KEYS}.
     *
     * @return the keys.
     */
    public @NonNull List<KeyColumn> effectiveKeys()
    {
        return keys != null ? keys : DEFAULT_KEYS;
    }
}
