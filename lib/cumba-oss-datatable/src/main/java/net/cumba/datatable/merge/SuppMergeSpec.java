package net.cumba.datatable.merge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The supplemental-qualifier merge of a dataset ({@code PLAN-merged-evaluation-table} §3.5): one
 * column per {@code QNAM} of the {@code SUPP--} dataset(s), each row served by the one supplemental
 * row that qualifies it.
 *
 * <ul>
 * <li>{@code suppDatasets} — the supplemental datasets to read, or empty for the IG's
 * ({@link #defaultSuppNames}): a split dataset is qualified by its own {@code SUPP<split>}
 * <b>and</b> by its domain's {@code SUPP<domain>} (P-7). Every source that resolves is fed; a
 * record two sources qualify for one {@code QNAM} is a duplicate (case 2), as two rows of one
 * source are.</li>
 * <li>{@code keys} — the key in sided form ({@code Supp_Keys}), or {@code null} for the IG's
 * default ({@link #DEFAULT_KEYS}): {@code STUDYID}, {@code {left: DOMAIN, right: RDOMAIN}}, the
 * subject keys {@code USUBJID}, {@code POOLID}, {@code APID}, {@code SPDEVID}, and the
 * {@code IDVAR} / {@code IDVARVAL} pair ("the parent column IDVAR names equals IDVARVAL"). A
 * component present on both sides is used, on one side only it is ignored with a log warning, on
 * neither it is ignored (MET #19). The pair is recognised by its right-side names and must be named
 * whole (P-15).</li>
 * </ul>
 */
@Value
@Builder
public class SuppMergeSpec
{

    /**
     * The IG's default key (SDTMIG 3.4 §8.4.1, SENDIG 3.1.1 §8.3, SDTMIG-AP: {@code APID},
     * SDTMIG-MD: {@code SPDEVID}). A subject component neither side carries drops out (MET #19).
     */
    public static final List<KeyColumn> DEFAULT_KEYS = List.of(KeyColumn.of("STUDYID"),
            KeyColumn.sided("DOMAIN", "RDOMAIN"), KeyColumn.of("USUBJID"), KeyColumn.of("POOLID"),
            KeyColumn.of("APID"), KeyColumn.of("SPDEVID"), KeyColumn.of("IDVAR"),
            KeyColumn.of("IDVARVAL"));

    @NonNull
    @Singular("suppDataset")
    List<String> suppDatasets;

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


    /**
     * The IG's supplemental dataset names of a dataset, most specific first: the dataset's own and,
     * for a split dataset (its name differs from its domain), the domain's (P-7, SDTMIG 3.4 sec.
     * 4.1.7). The prefix is {@code SUPP}; an associated-persons dataset ({@code AP--}) takes
     * {@code SQ} (SDTMIG-AP: {@code SQAPDM} qualifies {@code APDM}).
     *
     * @param aDataset
     *            the dataset's name.
     * @param aDomain
     *            its domain.
     * @return the names, without duplicates.
     */
    public static List<String> defaultSuppNames(String aDataset, String aDomain)
    {
        List<String> out = new ArrayList<>(2);
        out.add(suppNameOf(aDataset));
        if (!aDomain.isBlank() && !aDomain.equalsIgnoreCase(aDataset))
        {
            out.add(suppNameOf(aDomain));
        }
        return out;
    }


    /**
     * The supplemental dataset name of one dataset: {@code SQ<name>} for an associated-persons
     * dataset ({@code AP} followed by its parent's domain, SDTMIG-AP), {@code SUPP<name>}
     * otherwise.
     *
     * @param aDataset
     *            the dataset (or domain) name.
     * @return the supplemental dataset's name, upper case.
     */
    public static String suppNameOf(String aDataset)
    {
        String name = aDataset.strip().toUpperCase(Locale.ROOT);
        return (name.length() >= 4 && name.startsWith("AP") ? "SQ" : "SUPP") + name;
    }
}
