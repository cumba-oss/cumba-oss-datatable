package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** DF2: the engine's define-name → type rule (plan §3.4 step 1, review L6 spellings). */
class DictionaryTypesTest
{

    @ParameterizedTest
    @CsvSource(
    {
            "MedDRA, meddra", "MEDDRA, meddra", "meddra, meddra", "Med DRA, meddra",
            "WHODRUG, whodrug", "WHODrug, whodrug", "WHO Drug, whodrug", "WHO-Drug, whodrug",
            "LOINC, loinc", "MED-RT, medrt", "MedRT, medrt", "UNII, unii", "SNOMED, snomed",
            "neoplasm, neoplasm", "Neoplasm, neoplasm"
    })
    void theEnginesSpellingsResolve(String aDictionary, String aType)
    {
        assertEquals(aType, DictionaryTypes.typeOf(aDictionary));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "SNOMED CT", "WHODD", "WHO-DD", "WHODrug Global", "ISO 3166-1 Alpha-3",
            "ISO 21090 NullFlavor", "", "  ", "MedDRA 26.0"
    })
    void everythingElseIsNotAStoreDictionary_f1SpellingsIncluded(String aDictionary)
    {
        assertNull(DictionaryTypes.typeOf(aDictionary));
    }


    @Test
    void nullIsNoDictionary_andTheSingleLevelTypeIsLoinc()
    {
        assertNull(DictionaryTypes.typeOf(null));
        assertEquals("LOINC", DictionaryTypes.singleLevelOf("loinc"));
        assertNull(DictionaryTypes.singleLevelOf("meddra"));
        assertEquals(7, DictionaryTypes.IDS.size());
    }
}
