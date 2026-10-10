package net.cumba.datatable.dictionary.convert;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.cumba.datatable.dictionary.house.ValueMapDictionary;

/**
 * Plan B2 / B-T review lane 1 MED-2, committed in-tree: a house-format-2 file answers every pre-v2
 * engine lookup exactly as its house-format-1 twin does. The FULL reader is the pre-switch engine
 * reader plus the {@code decodes} section (diffed at the B-T review), so the v1 twin — the same
 * document without {@code decodes}, {@code house_format} and {@code levels.DRUGCD} — read by the
 * FULL reader stands in for "the old reader"; no old jar is needed. Replaces the throw-away
 * {@code V2Gate} run (617 631 probes) as the committed check.
 */
final class HouseFormatEquivalence
{

    private HouseFormatEquivalence()
    {
    }


    /** The document as a house-format-1 installer would have written it. */
    static ObjectNode v1Twin(ObjectNode aV2)
    {
        ObjectNode v1 = aV2.deepCopy();
        v1.remove(ValueMapDictionary.DECODES);
        v1.remove(ValueMapDictionary.HOUSE_FORMAT);
        JsonNode levels = v1.path("levels");
        if (levels instanceof ObjectNode o)
        {
            o.remove("DRUGCD");
        }
        return v1;
    }


    /**
     * Every engine lookup that differs between the v2 document and its v1 twin, both read FULL:
     * {@code caseMatches} and {@code isValidTerm} at each v1 level, at the blank level and at no
     * level, over every term of every v2 level with case variants; {@code hasDecode} and
     * {@code codeDecodePair} (both case modes) over every term, every v2 decode and every pair;
     * {@code onHierarchyPath} / {@code onHierarchyPathAsBefore} over every level-scoped entry;
     * {@code hasContent}, {@code answersHierarchyAt} and {@code hasHierarchyByLevel}.
     *
     * @param aV2
     *            the house-format-2 document.
     * @param aProbes
     *            receives the number of probes compared (index 0).
     * @return {@code "lookup(args)"} per differing probe, in probe order, without duplicates.
     */
    static Set<String> differences(ObjectNode aV2, long[] aProbes)
    {
        ObjectNode twin = v1Twin(aV2);
        return differences(ValueMapDictionary.parse(twin), ValueMapDictionary.parse(aV2), aV2, twin,
                aProbes);
    }


    /**
     * LANE-dictionary-followup: every engine lookup that differs between the document read FULL and
     * read in the engine's profile ({@code ENGINE}, no {@code decodes}) — the same probes as
     * {@link #differences(ObjectNode, long[])}, at every level of the document ({@code DRUGCD}
     * included).
     *
     * @param aV2
     *            the house-format-2 document.
     * @param aProbes
     *            receives the number of probes compared (index 0).
     * @return {@code "lookup(args)"} per differing probe.
     */
    static Set<String> engineDifferences(ObjectNode aV2, long[] aProbes)
    {
        return differences(ValueMapDictionary.parse(aV2),
                ValueMapDictionary.parse(aV2, ValueMapDictionary.Profile.ENGINE), aV2, aV2,
                aProbes);
    }


    /**
     * The probes of {@link #differences(ObjectNode, long[])}: {@code aV1} and {@code aV2} read, the
     * terms drawn from {@code aV2}, the levels from {@code twin}.
     */
    private static Set<String> differences(ValueMapDictionary v1, ValueMapDictionary v2,
            ObjectNode aV2, ObjectNode twin, long[] aProbes)
    {
        Set<String> out = new LinkedHashSet<>();
        long probes = 0;
        List<String> v1Levels = new ArrayList<>();
        twin.path("levels").fieldNames().forEachRemaining(v1Levels::add);
        List<String> levelArgs = new ArrayList<>(v1Levels);
        levelArgs.add("");
        levelArgs.add(null);
        check(out, "hasContent()", v1.hasContent(), v2.hasContent());
        check(out, "hasHierarchyByLevel()", v1.hasHierarchyByLevel(), v2.hasHierarchyByLevel());
        for (String level : v1Levels)
        {
            check(out, "answersHierarchyAt(" + level + ")", v1.answersHierarchyAt(level),
                    v2.answersHierarchyAt(level));
        }
        for (Map.Entry<String, JsonNode> level : aV2.path("levels").properties())
        {
            for (Map.Entry<String, JsonNode> term : level.getValue().properties())
            {
                String v = term.getValue().asText();
                for (String p : List.of(v, v.toUpperCase(Locale.ROOT), v.toLowerCase(Locale.ROOT),
                        v + "x"))
                {
                    for (String l : levelArgs)
                    {
                        check(out, "caseMatches(" + l + "," + p + ")", v1.caseMatches(l, p),
                                v2.caseMatches(l, p));
                        check(out, "isValidTerm(" + l + "," + p + ")", v1.isValidTerm(l, p),
                                v2.isValidTerm(l, p));
                        probes += 2;
                    }
                    for (boolean cs : new boolean[]
                    {
                            true, false
                    })
                    {
                        check(out, "hasDecode(null," + p + "," + cs + ")",
                                v1.hasDecode(null, p, cs), v2.hasDecode(null, p, cs));
                        probes++;
                    }
                }
            }
        }
        for (String section : List.of(ValueMapDictionary.DECODES, "pairs", "attributes"))
        {
            for (Map.Entry<String, JsonNode> registry : aV2.path(section).properties())
            {
                for (Map.Entry<String, JsonNode> e : registry.getValue().properties())
                {
                    String code = e.getKey();
                    String decode = e.getValue().asText();
                    for (boolean cs : new boolean[]
                    {
                            true, false
                    })
                    {
                        for (String reg : new String[]
                        {
                                null, registry.getKey()
                        })
                        {
                            check(out,
                                    "codeDecodePair(" + reg + "," + code + "," + decode + "," + cs
                                            + ")",
                                    v1.codeDecodePair(reg, code, decode, cs),
                                    v2.codeDecodePair(reg, code, decode, cs));
                            check(out, "hasDecode(" + reg + "," + code + "," + cs + ")",
                                    v1.hasDecode(reg, code, cs), v2.hasDecode(reg, code, cs));
                            probes += 2;
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, JsonNode> level : aV2.path(ValueMapDictionary.HIERARCHY_BY_LEVEL)
                .properties())
        {
            for (Map.Entry<String, JsonNode> term : level.getValue().properties())
            {
                for (Map.Entry<String, JsonNode> parentLevel : term.getValue().properties())
                {
                    for (JsonNode parent : parentLevel.getValue())
                    {
                        for (boolean cs : new boolean[]
                        {
                                true, false
                        })
                        {
                            String args = level.getKey() + "," + term.getKey() + ","
                                    + parentLevel.getKey() + "," + parent.asText() + "," + cs;
                            check(out, "onHierarchyPath(" + args + ")",
                                    v1.onHierarchyPath(level.getKey(), term.getKey(),
                                            parentLevel.getKey(), parent.asText(), cs),
                                    v2.onHierarchyPath(level.getKey(), term.getKey(),
                                            parentLevel.getKey(), parent.asText(), cs));
                            check(out, "onHierarchyPathAsBefore(" + args + ")",
                                    v1.onHierarchyPathAsBefore(level.getKey(), term.getKey(),
                                            parentLevel.getKey(), parent.asText(), cs),
                                    v2.onHierarchyPathAsBefore(level.getKey(), term.getKey(),
                                            parentLevel.getKey(), parent.asText(), cs));
                            probes += 2;
                        }
                    }
                }
            }
        }
        aProbes[0] = probes;
        return out;
    }


    private static void check(Set<String> aOut, String aProbe, boolean aV1, boolean aV2)
    {
        if (aV1 != aV2)
        {
            aOut.add(aProbe);
        }
    }
}
