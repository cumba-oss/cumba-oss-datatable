package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The Define-XML {@code ExternalCodeList/@Dictionary} name → house dictionary type, with the
 * engine's rule (DF2, {@code OdmDefineXMLProvider.DICTIONARY_TYPES}): strip every non-alphanumeric
 * character, lower-case, and the result must equal one of the seven type ids — {@code MedDRA},
 * {@code MED-RT} and {@code WHO Drug} resolve; {@code SNOMED CT}, {@code WHODD} and
 * {@code WHODrug Global} do not (plan §9 F1: aliases are added in Phase B, where the engine reads
 * this table too). Nothing else is a store dictionary.
 */
public final class DictionaryTypes
{

    /** The seven house types, in the store's own spelling. */
    public static final List<String> IDS = List.of("meddra", "whodrug", "loinc", "unii", "snomed",
            "medrt", "neoplasm");

    private static final Set<String> ID_SET = Set.copyOf(IDS);

    private DictionaryTypes()
    {
    }


    /**
     * The house type of a define dictionary name.
     *
     * @param aDictionary
     *            the {@code Dictionary} attribute, may be {@code null}.
     * @return the type id, or {@code null} when the name is not a store dictionary.
     */
    public static @Nullable String typeOf(@Nullable String aDictionary)
    {
        if (aDictionary == null)
        {
            return null;
        }
        String key = aDictionary.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return ID_SET.contains(key) ? key : null;
    }


    /**
     * Whether the type has exactly one level, so every column bound to it resolves to that level
     * (DF22): LOINC, whose only level is the code level.
     *
     * @param aType
     *            the type id.
     * @return the single level, or {@code null} for a multi-level type.
     */
    public static @Nullable String singleLevelOf(String aType)
    {
        return DictionaryLevels.defaultLevelOf(aType);
    }
}
