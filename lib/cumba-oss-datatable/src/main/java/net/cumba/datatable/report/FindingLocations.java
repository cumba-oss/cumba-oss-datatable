package net.cumba.datatable.report;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Helpers shared by every {@link ValidationFinding} producer (the Pinnacle 21 and CDISC CORE report
 * parsers and the in-app rule engine) for resolving a finding's {@link ValidationFindingLocation} —
 * i.e. the <em>real data-table columns</em> a finding sits on — out of the raw
 * {@code variable}/{@code value} tokens a report carries.
 *
 * <p>
 * The producers differ in their token conventions (P21 uses the literal {@code "VARIABLE"}; CORE
 * and the engine use {@code "variable_name"}), so this class exposes small composable primitives
 * rather than one monolithic resolver:
 * </p>
 * <ul>
 * <li>{@link #isNonColumnToken(String)} — recognise tokens that cannot be a plain column.</li>
 * <li>{@link #filterColumns(List)} — keep only the plausible column tokens.</li>
 * <li>{@link #markerValue(List, List, Set)} — resolve a {@code variable_name}-style marker to the
 * real column held in its paired value.</li>
 * <li>{@link #columnsFor(FindingScope, List, List, Set)} — the common composition used by all three
 * producers.</li>
 * </ul>
 */
public final class FindingLocations
{

    private FindingLocations()
    {
    }


    /**
     * Returns {@code true} when {@code aToken} cannot be a plain column of its own data set —
     * cross-dataset / derived / scalar / wildcard references and blanks.
     *
     * @param aToken
     *            the raw report token.
     * @return {@code true} when the token is not a plain column name.
     */
    public static boolean isNonColumnToken(@Nullable String aToken)
    {
        if (aToken == null || aToken.isBlank())
        {
            return true;
        }
        // SUB:DTHDTC and other prefixed derived refs.
        if (aToken.indexOf(':') >= 0)
        {
            return true;
        }
        // RELREC.**TERM, DM.DTHDTC and other dataset-qualified cross references.
        if (aToken.indexOf('.') >= 0)
        {
            return true;
        }
        // $scalar operation references.
        if (aToken.charAt(0) == '$')
        {
            return true;
        }
        // ** / -- wildcard placeholders that never name a concrete column.
        return aToken.indexOf('*') >= 0;
    }


    /**
     * Returns the plausible column tokens of {@code aTokens} — every entry that is not an
     * {@link #isNonColumnToken(String) non-column token} — de-duplicated (case-insensitively) and
     * preserving first-seen order. Original casing of the first occurrence is kept.
     *
     * @param aTokens
     *            the raw report tokens (may be {@code null}).
     * @return an immutable list of plausible column names; never {@code null}.
     */
    public static List<String> filterColumns(@Nullable List<String> aTokens)
    {
        if (aTokens == null || aTokens.isEmpty())
        {
            return List.of();
        }
        Set<String> seenUpper = new LinkedHashSet<>();
        List<String> out = new ArrayList<>(aTokens.size());
        for (String token : aTokens)
        {
            if (isNonColumnToken(token))
            {
                continue;
            }
            String key = token.toUpperCase(Locale.ROOT);
            if (seenUpper.add(key))
            {
                out.add(token);
            }
        }
        return List.copyOf(out);
    }


    /**
     * Resolves a {@code variable_name}-style marker to the real column name it points at: finds the
     * first entry of {@code aNames} that case-insensitively equals one of {@code aMarkers} and
     * returns the value at the same position in {@code aValues}.
     *
     * @param aNames
     *            the raw report variable tokens.
     * @param aValues
     *            the raw report values, positionally aligned to {@code aNames}.
     * @param aMarkers
     *            the marker tokens to look for (compared case-insensitively).
     * @return the real column name held in the marker's paired value, or {@code null} when no
     *         marker is present or its value is blank / not a plain column.
     */
    public static @Nullable String markerValue(@Nullable List<String> aNames,
            @Nullable List<String> aValues, @Nullable Set<String> aMarkers)
    {
        if (aNames == null || aMarkers == null || aMarkers.isEmpty())
        {
            return null;
        }
        int markerIdx = -1;
        for (int i = 0; i < aNames.size() && markerIdx < 0; i++)
        {
            String name = aNames.get(i);
            if (name != null && containsIgnoreCase(aMarkers, name))
            {
                markerIdx = i;
            }
        }
        if (markerIdx < 0)
        {
            return null;
        }
        String value = aValues != null && markerIdx < aValues.size() ? aValues.get(markerIdx)
                : null;
        return isNonColumnToken(value) ? null : value;
    }


    /**
     * The common location-column composition used by all three producers.
     * <ul>
     * <li>{@link FindingScope#DATASET}: empty.</li>
     * <li>Otherwise, when a {@code variable_name}-style marker is present, the single flagged
     * column held in its paired value (or empty when that value is blank / non-column).</li>
     * <li>Otherwise, the plausible column tokens of {@code aNames}
     * ({@link #filterColumns(List)}).</li>
     * </ul>
     *
     * @param aScope
     *            the finding's scope.
     * @param aNames
     *            the raw report variable tokens.
     * @param aValues
     *            the raw report values, positionally aligned to {@code aNames}.
     * @param aNameMarkers
     *            the {@code variable_name}-style marker tokens for this producer (e.g.
     *            {@code "variable_name"} or {@code "VARIABLE"}), compared case-insensitively; may
     *            be empty for producers that never use a marker (the engine, post-collapse).
     * @return an immutable list of real location columns; never {@code null}.
     */
    public static List<String> columnsFor(@Nullable FindingScope aScope,
            @Nullable List<String> aNames, @Nullable List<String> aValues,
            @Nullable Set<String> aNameMarkers)
    {
        if (aScope == FindingScope.DATASET)
        {
            return List.of();
        }
        String marker = markerValue(aNames, aValues, aNameMarkers);
        if (marker != null)
        {
            return List.of(marker);
        }
        return filterColumns(aNames);
    }


    private static boolean containsIgnoreCase(Set<String> aMarkers, String aName)
    {
        for (String marker : aMarkers)
        {
            if (marker.equalsIgnoreCase(aName))
            {
                return true;
            }
        }
        return false;
    }

}
