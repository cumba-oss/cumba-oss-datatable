package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes house-format fixtures into a {@code @TempDir} store: the shapes the engine's installer
 * writes (versioned {@code <type>/<version>/<type>.json[.gz]}) and the flat dummy layout.
 */
public final class TestStore
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestStore()
    {
    }

    /** A house-format document under construction. */
    public static final class House
    {

        private final ObjectNode root = MAPPER.createObjectNode();

        private final ObjectNode levels = root.putObject("levels");

        private final ObjectNode hierarchy = root.putObject("hierarchy");

        private final ObjectNode pairs = root.putObject("pairs");

        private final ObjectNode attributes = root.putObject("attributes");

        private House(String aType)
        {
            root.put("type", aType);
        }


        public static House of(String aType)
        {
            return new House(aType);
        }


        public House version(String aVersion)
        {
            root.put("version", aVersion);
            return this;
        }


        public House retrieved(String aDate)
        {
            root.put("retrieved", aDate);
            return this;
        }


        public House houseFormat(int aFormat)
        {
            root.put("house_format", aFormat);
            return this;
        }


        /** A name level: every value under its upper-cased key (the installer's contract). */
        public House nameLevel(String aLevel, String... aPreferred)
        {
            ObjectNode l = levels.putObject(aLevel);
            for (String p : aPreferred)
            {
                l.put(p.toUpperCase(java.util.Locale.ROOT), p);
            }
            return this;
        }


        /** A code level: code → code. */
        public House codeLevel(String aLevel, String... aCodes)
        {
            ObjectNode l = levels.putObject(aLevel);
            for (String c : aCodes)
            {
                l.put(c, c);
            }
            return this;
        }


        /** A level with explicit key → value entries (to break the contract on purpose). */
        public House rawLevel(String aLevel, Map<String, String> aEntries)
        {
            ObjectNode l = levels.putObject(aLevel);
            aEntries.forEach(l::put);
            return this;
        }


        public House pairs(String aRegistry, Map<String, String> aEntries)
        {
            ObjectNode r = pairs.putObject(aRegistry);
            aEntries.forEach(r::put);
            return this;
        }


        public House attributes(String aName, Map<String, String> aEntries)
        {
            ObjectNode a = attributes.putObject(aName);
            aEntries.forEach(a::put);
            return this;
        }


        public House decodes(String aLevel, Map<String, String> aEntries)
        {
            ObjectNode d = root.has("decodes") ? (ObjectNode) root.get("decodes")
                    : root.putObject("decodes");
            ObjectNode l = d.putObject(aLevel);
            aEntries.forEach(l::put);
            return this;
        }


        public House hierarchy(String aTerm, List<String> aAncestors)
        {
            var arr = hierarchy.putArray(aTerm);
            aAncestors.forEach(arr::add);
            return this;
        }


        public House hierarchyByLevel(String aLevel, String aTerm,
                Map<String, List<String>> aAncestors)
        {
            ObjectNode hbl = root.has("hierarchy_by_level")
                    ? (ObjectNode) root.get("hierarchy_by_level")
                    : root.putObject("hierarchy_by_level");
            ObjectNode level = hbl.has(aLevel) ? (ObjectNode) hbl.get(aLevel)
                    : hbl.putObject(aLevel);
            ObjectNode term = level.putObject(aTerm);
            aAncestors.forEach((l, list) ->
            {
                var arr = term.putArray(l);
                list.forEach(arr::add);
            });
            return this;
        }


        /** Any extra top-level field (an unknown key, a mis-shaped section). */
        public House raw(String aField, Object aValue)
        {
            root.set(aField, MAPPER.valueToTree(aValue));
            return this;
        }


        public String json()
        {
            try
            {
                return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * The engine's dummy MedDRA shape (names sentence-case, codes), with
     * {@code hierarchy_by_level}.
     */
    public static House meddra(String aVersion, boolean aWithByLevel)
    {
        House h = House.of("meddra").version(aVersion).retrieved("2026-10-01")
                .nameLevel("PT", "Headache", "Nausea", "Diarrhoea")
                .codeLevel("PTCD", "10019211", "10028813", "10012735")
                .nameLevel("LLT", "Headache", "Nausea", "Diarrhoea")
                .codeLevel("LLTCD", "10019211", "10028813", "10012735")
                .nameLevel("HLT", "Headaches NEC", "Nausea and vomiting symptoms")
                .codeLevel("HLTCD", "10019231", "10028817")
                .nameLevel("HLGT", "Headaches", "Gastrointestinal signs and symptoms")
                .codeLevel("HLGTCD", "10019233", "10018012")
                .nameLevel("SOC", "Nervous system disorders", "Gastrointestinal disorders")
                .codeLevel("SOCCD", "10029205", "10017947")
                .hierarchy("Headaches NEC", List.of("Headaches", "Nervous system disorders"))
                .hierarchy("Headaches", List.of("Nervous system disorders"));
        if (aWithByLevel)
        {
            h.hierarchyByLevel("PT", "Headache", Map.of("HLT", List.of("Headaches NEC"), "HLGT",
                    List.of("Headaches"), "SOC", List.of("Nervous system disorders")));
            h.hierarchyByLevel("PTCD", "10019211", Map.of("HLTCD", List.of("10019231"), "HLGTCD",
                    List.of("10019233"), "SOCCD", List.of("10029205")));
        }
        return h;
    }


    public static House whodrug(String aVersion)
    {
        return House.of("whodrug").version(aVersion).retrieved("2026-10-02")
                .nameLevel("PT", "Aspirin", "Ibuprofen", "Paracetamol")
                .nameLevel("ATC", "Analgesics", "Antiinflammatory and antirheumatic products")
                .codeLevel("ATCCD", "N02", "M01A").pairs("whodrug", Map.of("ASPIRIN TABLETS",
                        "Aspirin", "PANADOL EXTRA", "Paracetamol", "Paracetamol", "Paracetamol"));
    }


    /** LOINC: a code with a long common name, one without. */
    public static House loinc(String aVersion)
    {
        return House.of("loinc").version(aVersion).retrieved("2026-10-03")
                .codeLevel("LOINC", "1558-6", "2345-7", "9999-9")
                .pairs("loinc", Map.of("1558-6", "Fasting glucose", "2345-7", "Glucose"));
    }


    public static House unii(String aVersion)
    {
        return House.of("unii").version(aVersion).retrieved("2026-10-04")
                .codeLevel("UNII", "R16CO5Y76E", "WK2XYI10QM")
                .nameLevel("SRS", "ASPIRIN", "IBUPROFEN")
                .pairs("unii", Map.of("R16CO5Y76E", "ASPIRIN", "WK2XYI10QM", "IBUPROFEN"));
    }


    public static House snomed(String aVersion)
    {
        return House.of("snomed").version(aVersion).retrieved("2026-10-05")
                .nameLevel("SNOMED", "Headache", "Nausea")
                .codeLevel("SNOMEDCD", "25064002", "422587007")
                .pairs("snomed", Map.of("25064002", "Headache", "422587007", "Nausea"));
    }


    /** MED-RT with the base registry that must never serve a decode. */
    public static House medrt(String aVersion)
    {
        Map<String, String> base = new LinkedHashMap<>();
        base.put("N0000000181", "Cyclooxygenase Inhibitors [MoA] (base)");
        base.put("N0000175553", "Adrenergic beta-Antagonists [EPC] (base)");
        return House.of("medrt").version(aVersion).retrieved("2026-10-06")
                .nameLevel("MEDRT", "Cyclooxygenase Inhibitors", "Adrenergic beta-Antagonists")
                .codeLevel("MEDRTCD", "N0000000181", "N0000175553", "N0000009999")
                .pairs("medrt", Map.of("N0000000181", "Cyclooxygenase Inhibitors", "N0000175553",
                        "Adrenergic beta-Antagonists"))
                .pairs("medrt-base", base);
    }


    /** Writes {@code <store>/<type>/<version>/<type>.json} and returns the file. */
    public static Path versioned(Path aStore, String aType, String aVersion, House aHouse)
        throws IOException
    {
        Path file = aStore.resolve(aType).resolve(aVersion).resolve(aType + ".json");
        write(file, aHouse.json(), false);
        return file;
    }


    /** Writes {@code <store>/<type>/<version>/<type>.json.gz} and returns the file. */
    public static Path versionedGz(Path aStore, String aType, String aVersion, House aHouse)
        throws IOException
    {
        Path file = aStore.resolve(aType).resolve(aVersion).resolve(aType + ".json.gz");
        write(file, aHouse.json(), true);
        return file;
    }


    /** Writes {@code <store>/<type>.json} (the flat layout) and returns the file. */
    public static Path flat(Path aStore, String aType, House aHouse) throws IOException
    {
        Path file = aStore.resolve(aType + ".json");
        write(file, aHouse.json(), false);
        return file;
    }


    /** Writes arbitrary text as a file of the store (a corrupt file, a stray file). */
    public static Path text(Path aFile, String aText) throws IOException
    {
        write(aFile, aText, false);
        return aFile;
    }


    private static void write(Path aFile, String aJson, boolean aGzip) throws IOException
    {
        Files.createDirectories(aFile.getParent());
        byte[] bytes = aJson.getBytes(StandardCharsets.UTF_8);
        if (aGzip)
        {
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(aFile)))
            {
                out.write(bytes);
            }
        }
        else
        {
            Files.write(aFile, bytes);
        }
    }
}
