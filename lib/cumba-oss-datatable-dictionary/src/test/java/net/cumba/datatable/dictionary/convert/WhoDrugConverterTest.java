package net.cumba.datatable.dictionary.convert;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.dictionary.house.HouseDictionaryReader;
import net.cumba.datatable.dictionary.house.HouseFormatValidator;
import net.cumba.datatable.dictionary.house.ValueMapDictionary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The WHODrug B3 converter, against a synthetic fixed-width fixture (no licensed data is present in
 * this repository). The properties that matter: {@code ATCCD} is the full {@code INA.txt} index,
 * {@code levels.PT} is preferred names only while {@code pairs.whodrug} answers for every reported
 * name — and a genuine <em>within-level</em> case conflict aborts the conversion, while the normal
 * cross-level divergence (upper-case B3 drug names against mixed-case ATC texts) is preserved (A4,
 * owner ruling).
 */
class WhoDrugConverterTest
{

    private final WhoDrugConverter converter = new WhoDrugConverter();

    @TempDir
    private Path raw;

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void convertsNamesAtcCodesAndTexts() throws IOException
    {
        writeDistribution();

        ObjectNode doc = converter.convert(raw);

        assertEquals("ASPIRIN", doc.path("levels").path("PT").path("ASPIRIN").asText());
        assertEquals("N02BA", doc.path("levels").path("ATCCD").path("N02BA").asText());
        assertEquals("Salicylic acid and derivatives",
                doc.path("levels").path("ATC").path("SALICYLIC ACID AND DERIVATIVES").asText(),
                "the vendor's case, verbatim");
    }

    // ------------------------------------------------------------------
    // ⛔ The hazard: building ATCCD from DDA.txt's licensee subset
    // ------------------------------------------------------------------


    @Test
    void atcCodesComeFromTheFullInaIndexNotFromDda() throws IOException
    {
        writeDistribution();

        JsonNode codes = converter.convert(raw).path("levels").path("ATCCD");

        assertTrue(codes.has("N02BA01"),
                "a lawful ATC code no local drug record uses — absent from DDA.txt — must "
                        + "still be a valid value");
        assertFalse(codes.has("X99XX99"),
                "DDA.txt's code does NOT appear in INA.txt, so its presence would prove the "
                        + "converter read the licensee subset after all");
        assertEquals(4, codes.size(), codes.toString());
    }

    // ------------------------------------------------------------------
    // ⛔ The hazard: trade names as preferred names, or a vacuous pairs registry
    // ------------------------------------------------------------------


    @Test
    void aNonPreferredNameIsExcludedFromPtButKeysAPair() throws IOException
    {
        writeDistribution();

        ObjectNode doc = converter.convert(raw);

        assertFalse(doc.path("levels").path("PT").has("ASPIRIN BAYER"),
                "a trade name is not a preferred name");
        assertEquals("ASPIRIN", doc.path("pairs").path("whodrug").path("ASPIRIN BAYER").asText(),
                "but a sponsor reporting it in CMTRT must find its decode");
        assertEquals("ASPIRIN", doc.path("pairs").path("whodrug").path("ASPIRIN").asText(),
                "and the preferred name decodes to itself");
    }


    /**
     * A10 — B3 identifies a product by DrugRecNo + Seq1 + Seq2, and trade names conventionally
     * share sequence 1 {@code 01} at sequence 2 {@code 002}+. A converter filtering on sequence 1
     * alone would publish this row as a preferred name.
     */
    @Test
    void aTradeNameSharingSequence1IsStillNotAPreferredName() throws IOException
    {
        writeDistribution();

        ObjectNode doc = converter.convert(raw);

        assertFalse(doc.path("levels").path("PT").has("ASPRO CLEAR"),
                "sequence 1 01 / sequence 2 002 is a trade name, not the preferred 01/001 row");
        assertEquals("ASPIRIN", doc.path("pairs").path("whodrug").path("ASPRO CLEAR").asText(),
                "but it still keys a pair to its record's preferred name");
        assertEquals(2, doc.path("levels").path("PT").size(),
                "exactly the two 01/001 rows are preferred: " + doc.path("levels").path("PT"));
    }


    @Test
    void aRecordWithoutAPreferredRowContributesNothing() throws IOException
    {
        writeDistribution();

        ObjectNode doc = converter.convert(raw);

        assertFalse(doc.path("levels").path("PT").has("ORPHAN TRADE NAME"));
        assertFalse(doc.path("pairs").path("whodrug").has("ORPHAN TRADE NAME"),
                "with no sequence-1 01 row there is no preferred name to decode to");
    }

    // ------------------------------------------------------------------
    // ⛔ The hazard: silently picking one of two vendor spellings — WITHIN a level
    // ------------------------------------------------------------------


    /**
     * A4 / owner ruling — WHO writes level-5 ATC substance texts lower/mixed case while B3 drug
     * names are upper-case, so the cross-level "collision" is the NORMAL case: with a global
     * preferred-form table WHODrug would never install for any licensee. The two levels must
     * coexist, and each level's rules answer with its own spelling.
     */
    @Test
    void theNormalCrossLevelCaseDivergenceIsPreservedNotAborted() throws IOException
    {
        writeDistribution();
        // The ATC substance text for ibuprofen, mixed-case, against the upper-case drug name.
        Files.writeString(raw.resolve("INA.txt"), inaRow("M01AE01", "5", "Ibuprofen") + "\n",
                StandardCharsets.UTF_8);

        ObjectNode doc = converter.convert(raw);

        assertEquals("IBUPROFEN", doc.path("levels").path("PT").path("IBUPROFEN").asText(),
                "the drug-name level keeps DD.txt's spelling");
        assertEquals("Ibuprofen", doc.path("levels").path("ATC").path("IBUPROFEN").asText(),
                "and the ATC-text level keeps INA.txt's — both rules answer correctly");
        doc.put("type", "whodrug"); // the installer stamps this; the converter must not
        assertEquals(List.of(), HouseFormatValidator.validate("whodrug", doc),
                "the per-level case contract accepts the divergence");
    }


    /** Two of the vendor's own ATC-text spellings folding together IS a conflict, and aborts. */
    @Test
    void aWithinLevelCaseConflictInTheAtcIndexAbortsTheConversion() throws IOException
    {
        writeDistribution();
        Files.writeString(raw.resolve("INA.txt"), inaRow("M01AE01", "5", "Ibuprofen") + "\n"
                + inaRow("M01AE51", "5", "IBUPROFEN") + "\n", StandardCharsets.UTF_8);

        IOException thrown = assertThrows(IOException.class, () -> converter.convert(raw));

        assertTrue(thrown.getMessage().contains("'Ibuprofen'"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("'IBUPROFEN'"), "both spellings are named");
        assertTrue(thrown.getMessage().contains("ATC texts"), "and the level");
    }


    /** Likewise two preferred drug names within DD.txt. */
    @Test
    void aWithinLevelCaseConflictAmongDrugNamesAbortsTheConversion() throws IOException
    {
        writeDistribution();
        Files.writeString(raw.resolve("DD.txt"), ddRow("000002", "01", "001", "IBUPROFEN") + "\n"
                + ddRow("000004", "01", "001", "Ibuprofen") + "\n", StandardCharsets.UTF_8);

        IOException thrown = assertThrows(IOException.class, () -> converter.convert(raw));

        assertTrue(thrown.getMessage().contains("drug names"), thrown.getMessage());
    }

    // ------------------------------------------------------------------
    // The document the installer will actually write
    // ------------------------------------------------------------------


    @Test
    void theConvertedDocumentPassesHouseFormatValidation() throws IOException
    {
        writeDistribution();

        ObjectNode doc = converter.convert(raw);
        doc.put("type", "whodrug"); // the installer stamps this; the converter must not

        assertEquals(List.of(), HouseFormatValidator.validate("whodrug", doc));
    }


    @Test
    void refusesADistributionWithoutTheDrugFile() throws IOException
    {
        writeDistribution();
        Files.delete(raw.resolve("DD.txt"));

        assertThrows(NoSuchFileException.class, () -> converter.convert(raw));
    }

    // ------------------------------------------------------------------
    // Version
    // ------------------------------------------------------------------


    @Test
    void versionIsDecodedFromTheTailOfTheVersionFile() throws IOException
    {
        writeDistribution();

        assertEquals("SEP_2020", converter.versionOf(raw));
    }


    @Test
    void reportsNoVersionWhenTheVersionFileIsAbsent() throws IOException
    {
        assertEquals("", converter.versionOf(raw));
    }

    // ------------------------------------------------------------------
    // House format 2 — ATCCD and DRUGCD (PLAN-dictionary-backed-formats B2, DF23, DF24)
    // ------------------------------------------------------------------


    /** {@code decodes.ATCCD} is exactly INA.txt's code → text, one line per code. */
    @Test
    void atcCodesDecodeToTheirIndexText() throws IOException
    {
        writeDistribution();

        JsonNode decodes = converter.convert(raw).path(ValueMapDictionary.DECODES).path("ATCCD");

        List<String> lines = Files.readAllLines(raw.resolve("INA.txt")).stream()
                .filter(l -> !l.isBlank()).toList();
        assertEquals(lines.size(), decodes.size());
        for (String line : lines)
        {
            assertEquals(line.substring(8), decodes.path(line.substring(0, 7).trim()).asText(),
                    line);
        }
    }


    /**
     * {@code levels.DRUGCD} holds every DD.txt row's [0:11]; {@code decodes.DRUGCD} maps each to
     * its OWN row's name (DF24 (a)) — a trade name for a trade-name code, the preferred name for a
     * {@code …01001} code — never to the record's preferred name.
     */
    @Test
    void everyDrugCodeIsAMemberAndDecodesToItsOwnRowsName() throws IOException
    {
        writeDistribution();

        ObjectNode doc = converter.convert(raw);
        JsonNode codes = doc.path("levels").path("DRUGCD");
        JsonNode decodes = doc.path(ValueMapDictionary.DECODES).path("DRUGCD");
        List<String> lines = Files.readAllLines(raw.resolve("DD.txt")).stream()
                .filter(l -> !l.isBlank()).toList();
        assertEquals(lines.size(), codes.size());
        assertEquals(lines.size(), decodes.size());
        for (String line : lines)
        {
            String code = line.substring(0, 11);
            assertEquals(code, codes.path(code).asText(), "member in its own form: " + code);
            assertEquals(line.substring(30), decodes.path(code).asText(), code);
        }
        assertEquals("ASPRO CLEAR", decodes.path("00000101002").asText(),
                "a trade-name code shows the product, not the generic name");
        assertEquals("ASPIRIN", decodes.path("00000101001").asText());
        assertEquals("ORPHAN TRADE NAME", decodes.path("00000302001").asText(),
                "a record without a preferred row still decodes each of its codes");
    }


    /**
     * The v2 file passes the v2 contract, decodes in both profiles, and the engine's
     * {@code hasDecode("whodrug", …)} — CDISC-CG0096 on {@code CMTRT} — cannot see an ATC code or a
     * drug code (B2 fall-through isolation).
     */
    @Test
    void aV2FileDecodes_andCg0096sFallThroughScanCannotSeeTheCodes() throws IOException
    {
        writeDistribution();
        ObjectNode doc = converter.convert(raw);
        doc.put("type", "whodrug");
        doc.put(ValueMapDictionary.HOUSE_FORMAT, ValueMapDictionary.HOUSE_FORMAT_2);
        assertEquals(List.of(), HouseFormatValidator.validate("whodrug", doc));
        assertEquals(List.of(), HouseFormatValidator.decodesGaps("whodrug", doc));

        ValueMapDictionary full = ValueMapDictionary.parse(doc);
        assertEquals("Acetylsalicylic acid", full.decode("ATCCD", "N02BA01"));
        assertEquals("ASPRO CLEAR", full.decode("DRUGCD", "00000101002"));
        assertFalse(full.hasDecode("whodrug", "N02BA01", true),
                "an ATC code is no reported drug name");
        assertFalse(full.hasDecode("whodrug", "00000101001", true), "nor is a drug code");
        assertTrue(full.hasDecode("whodrug", "ASPRO CLEAR", true),
                "the reported-name registry still answers CG0096");

        Path file = raw.resolve("whodrug.json");
        new ObjectMapper().writeValue(file.toFile(), doc);
        ValueMapDictionary format = HouseDictionaryReader.read(file);
        assertEquals("Acetylsalicylic acid", format.decode("ATCCD", "N02BA01"));
        assertEquals("IBUPROFEN", format.decode("DRUGCD", "00000201001"));
    }


    /**
     * Plan B2 / B-T review lane 1 MED-2: the v2 file answers every pre-v2 engine lookup exactly as
     * its v1 twin (no {@code decodes}, {@code house_format} or {@code levels.DRUGCD}) — with ONE
     * expected difference: a blank {@code dictionary_term_type} means "any level"
     * ({@code DictionaryFunctions.typeOf} gives {@code ""}), and {@code levels.DRUGCD} is a new
     * level, so {@code isValidTerm("", <drug code>)} is now true. The B0 census found no corpus
     * call that omits the term type, so no rule can reach it today.
     */
    @Test
    void aV2FileAnswersEveryPreV2LookupAsItsV1Twin_butABlankLevelSeesTheDrugCodes()
        throws IOException
    {
        writeDistribution();
        ObjectNode doc = converter.convert(raw);
        doc.put("type", "whodrug");
        doc.put(ValueMapDictionary.HOUSE_FORMAT, ValueMapDictionary.HOUSE_FORMAT_2);
        JsonNode drugCodes = doc.path("levels").path("DRUGCD");
        assertTrue(drugCodes.size() >= 4, "levels.DRUGCD: " + drugCodes);
        java.util.Set<String> expected = new java.util.LinkedHashSet<>();
        for (java.util.Iterator<String> it = drugCodes.fieldNames(); it.hasNext();)
        {
            String code = it.next();
            expected.add("isValidTerm(," + code + ")");
            expected.add("isValidTerm(null," + code + ")");
        }
        long[] probes = new long[1];
        assertEquals(new java.util.TreeSet<>(expected),
                new java.util.TreeSet<>(HouseFormatEquivalence.differences(doc, probes)));
        assertTrue(probes[0] > 300, "probes: " + probes[0]);
    }


    /**
     * A miniature B3 distribution: two drug records with preferred rows plus trade names — one
     * sharing the preferred row's sequence 1 at sequence 2 {@code 002}, one at sequence 1
     * {@code 02} — one record with no preferred row, the full four-code ATC index, and a
     * {@code DDA.txt} whose sole ATC code does <b>not</b> occur in {@code INA.txt} — precisely so
     * the converter can prove it never reads it.
     */
    private void writeDistribution() throws IOException
    {
        Files.writeString(raw.resolve("DD.txt"),
                String.join("\n", ddRow("000001", "01", "001", "ASPIRIN"),
                        ddRow("000001", "01", "002", "ASPRO CLEAR"),
                        ddRow("000001", "02", "001", "ASPIRIN BAYER"),
                        ddRow("000002", "01", "001", "IBUPROFEN"),
                        ddRow("000003", "02", "001", "ORPHAN TRADE NAME"), "") + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(raw.resolve("INA.txt"),
                String.join("\n", inaRow("N02", "1", "Analgesics"),
                        inaRow("N02BA", "4", "Salicylic acid and derivatives"),
                        inaRow("N02BA01", "5", "Acetylsalicylic acid"),
                        inaRow("M01A", "3", "Antiinflammatory and antirheumatic products"), "")
                        + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(raw.resolve("DDA.txt"), ddaRow("000001", "01", "X99XX99") + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(raw.resolve("version.txt"), "WHODrug GLOBALB3Sep20\n",
                StandardCharsets.UTF_8);
    }


    /** One fixed-width {@code DD.txt} row: record, sequences, check digit, filler, name at 30. */
    private static String ddRow(String aRecord, String aSequence1, String aSequence2, String aName)
    {
        return aRecord + aSequence1 + aSequence2 + "4" + " ".repeat(18) + aName;
    }


    /** One fixed-width {@code DDA.txt} row with the ATC code at {@code [12:19]}. */
    private static String ddaRow(String aRecord, String aSequence1, String aAtcCode)
    {
        return aRecord + aSequence1 + "001" + "4" + pad(aAtcCode);
    }


    /** One fixed-width {@code INA.txt} row: the code at {@code [0:7]}, level, text from 8. */
    private static String inaRow(String aCode, String aLevel, String aText)
    {
        return pad(aCode) + aLevel + aText;
    }


    private static String pad(String aCode)
    {
        return aCode + " ".repeat(7 - aCode.length());
    }

}
