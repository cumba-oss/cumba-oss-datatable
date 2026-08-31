package net.cumba.cdisc.define;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mints collision-free OIDs for elements synthesised during conversion (e.g. {@code WC.1} for a
 * generated WhereClauseDef, {@code STD.1} for a Standard). Counter-based and deterministic so
 * converter output is reproducible and testable: given the same set of existing OIDs and the same
 * sequence of {@link #mint(String)} calls, the same OIDs are produced.
 */
final class OidMinter
{

    private final Set<String> used;

    private final Map<String, Integer> counters = new HashMap<>();

    /**
     * @param existingOids
     *            OIDs already present in the document; minted OIDs will avoid all of them
     */
    OidMinter(Set<String> existingOids)
    {
        this.used = new HashSet<>(existingOids);
    }


    /**
     * Return the next collision-free OID for the given prefix, e.g. {@code mint("WC")} → {@code
     * "WC.1"}, {@code "WC.2"}, … skipping any value already present. The returned OID is registered
     * as used so subsequent calls (for any prefix) never collide with it.
     */
    String mint(String prefix)
    {
        int n = counters.getOrDefault(prefix, 0);
        String oid;
        do
        {
            n++;
            oid = prefix + "." + n;
        }
        while (used.contains(oid));
        counters.put(prefix, n);
        used.add(oid);
        return oid;
    }


    /**
     * Register an externally chosen OID as used, so later mints avoid it. No-op for blank input.
     */
    void reserve(String oid)
    {
        if (oid != null && !oid.isBlank())
        {
            used.add(oid);
        }
    }


    boolean isUsed(String oid)
    {
        return used.contains(oid);
    }

}
