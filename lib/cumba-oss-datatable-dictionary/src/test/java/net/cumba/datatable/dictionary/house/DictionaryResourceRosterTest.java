package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * B-T review lane 1 LOW-1: the module's resources are pinned byte for byte, in both twins.
 * {@code check_oss_identity.py}'s pinned set {@code datatable-dictionary} compares only marked
 * {@code .java} / {@code .xml} files, so the licence texts, the {@code dictionary-levels} table
 * (since B3 it drives both engines' MedDRA hierarchy suffix table) and the JSON test fixtures were
 * compared by nothing. This test is itself a pinned file, so the roster below is the same in
 * {@code cumba-datatable-dictionary} and {@code cumba-oss-datatable-dictionary}: a resource that
 * differs, appears or disappears on either side reds here. A deliberate change edits the roster in
 * both twins.
 * <p>
 * Line ends are compared LF-normalised (a Windows checkout may hold CR LF). Planted control: one
 * byte appended to {@code dictionary-levels.properties} reds this test.
 * </p>
 */
class DictionaryResourceRosterTest
{

    /** Every file under {@code src/main/resources} and {@code src/test/resources} → its SHA-256. */
    private static final Map<String, String> ROSTER = Map.ofEntries(
            Map.entry("main/resources/dictionary-licences/LOINC_short_license.txt",
                    "7d3d13706aa4c88f1c832c540dd3ab28a15f63013d7a54ad893d1120739753de"),
            Map.entry("main/resources/dictionary-licences/loinc.txt",
                    "0112c577e856fa313997f70187a3b5dbd580a8081de2ac404909eb83050b406d"),
            Map.entry("main/resources/dictionary-licences/meddra.txt",
                    "624f359b7fbc0f389912f1ebdbf9743c57761a093f8e51029e1c60491ad9f939"),
            Map.entry("main/resources/dictionary-licences/medrt.txt",
                    "9dee21ec10e0f48cc50eadfab542ee6f0fe8e8885b9edb95bfd01475d1ba81bf"),
            Map.entry("main/resources/dictionary-licences/neoplasm.txt",
                    "b51429c95ed2c4387009862538e3e58a3c61f3ede8d245106990231717f599d9"),
            Map.entry("main/resources/dictionary-licences/snomed.txt",
                    "57d1ab7ba4da454b9b3fd3027eb1577f62447bbad91436014737d20855663756"),
            Map.entry("main/resources/dictionary-licences/unii.txt",
                    "58c35740ff5c163461ca80830214e9b5e104e58ca5fa7b8f512fe91f8fa7f823"),
            Map.entry("main/resources/dictionary-licences/whodrug.txt",
                    "31515acd2fb7b41ac262877e7aaa9bd53a3b538833776e36a6acaf614daa57c7"),
            Map.entry(
                    "main/resources/net/cumba/datatable/dictionary/house/dictionary-levels.properties",
                    "770fbe5f90904aad0c76b8782b2906e4cc0b9c8a58b5ccc0381b3a35161232fe"),
            Map.entry("test/resources/engine-dictionaries/README.md",
                    "cc67110e5ad531a845b30238c1c73eb9d17fa308d7b226723c9671c7905ef3fb"),
            Map.entry("test/resources/engine-dictionaries/loinc.json",
                    "0e052fbe4617a05a1094b66953546bf9f16065e1d4e0b62f48760df6001c14c1"),
            Map.entry("test/resources/engine-dictionaries/meddra.json",
                    "6c521874f35f79dce5ec1b0a46cc526f519051f42ddbcc07b2ef694131ddfffd"),
            Map.entry("test/resources/engine-dictionaries/medrt.json",
                    "90cd7831e5b7492b06e7eb10ea7e795992e8a4d676be176a794b074742b10fa0"),
            Map.entry("test/resources/engine-dictionaries/neoplasm.json",
                    "d54bf71df9da2ac101535452556f63da399819256c0fae4273b296b73c8dfd7b"),
            Map.entry("test/resources/engine-dictionaries/snomed.json",
                    "c25958431250808b180cbddf1f821d26427e191c2233907b25578bb3627ae702"),
            Map.entry("test/resources/engine-dictionaries/unii.json",
                    "7805ee6db0da404ffb8bee84ef65d3427fab2700971d667ffe52660d053dc8e0"),
            Map.entry("test/resources/engine-dictionaries/whodrug.json",
                    "7ca3adc306c1ca49306be109c2d546970737ede6a2b43fa74281ac7aba029007"),
            Map.entry("test/resources/profile-fixtures/README.md",
                    "d38a77b6adea78f87cf09743024e81a78f4f1af332eb46664b57555f318f9111"),
            Map.entry("test/resources/profile-fixtures/loinc.json",
                    "8adb28d3c153744f7e90a8c1f81a06a919d859bb02265698a5e8a523c90f0c10"),
            Map.entry("test/resources/profile-fixtures/meddra-26.0.json",
                    "379679c6d2a9917166066e13db7891ad14fe0d223d8a48922a08fc9883a84936"),
            Map.entry("test/resources/profile-fixtures/meddra-contract-broken.json",
                    "49da5b711ac2bcd6808586bb45a955a556e8089b1832a14a0dcb6090ac94e046"),
            Map.entry("test/resources/profile-fixtures/meddra-no-hbl.json",
                    "f8f5677e2cdc18eda83ab125e042e3169822399cefc1a4a34023bd4e03418382"),
            Map.entry("test/resources/profile-fixtures/meddra-upper.json",
                    "6a4238fd7a817b7da041a744fb1400fdf626560ddd9be662bb01f9def82024ca"),
            Map.entry("test/resources/profile-fixtures/meddra-v2-decodes.json",
                    "770a896b48189f94ca310ee9bd63553f500eccd63458328c898b812c814afd34"),
            Map.entry("test/resources/profile-fixtures/medrt-with-base.json",
                    "85c548529db61142132416e0efa866fb0e53738865359a9a62f4ecc4ba6de444"),
            Map.entry("test/resources/profile-fixtures/snomed.json",
                    "35e44aef023a5f477647cfe49640d3f4618e29113f41f01f7530e04deef04d9c"),
            Map.entry("test/resources/profile-fixtures/unii-two-registries.json",
                    "3415dbbb27724509b02a5470f7eb0bfecabc1aa2429b1ebc2208eeb593479494"),
            Map.entry("test/resources/profile-fixtures/whodrug.json",
                    "1054e5ec5d7fe01110a1765cc6bba58d50d1ae82ad52b005c7961acfc33dfe52"));

    private static Path moduleSrc()
    {
        // surefire runs in target/test-cwd (the home redirect); walk up to the module directory
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
        {
            Path src = dir.resolve("src");
            if (Files.isDirectory(src.resolve(Path.of("main", "resources", "dictionary-licences"))))
            {
                return src;
            }
        }
        throw new AssertionError("module sources not found above " + Path.of("").toAbsolutePath());
    }


    /**
     * The bytes with every CR LF reduced to LF: a Windows checkout with {@code core.autocrlf}
     * rewrites the text resources' line ends, which is no divergence between the twins.
     */
    static byte[] lf(byte[] aBytes)
    {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(aBytes.length);
        for (int i = 0; i < aBytes.length; i++)
        {
            if (aBytes[i] != '\r' || i + 1 >= aBytes.length || aBytes[i + 1] != '\n')
            {
                out.write(aBytes[i]);
            }
        }
        return out.toByteArray();
    }


    @Test
    void everyResourceIsOnTheRosterWithItsSha256() throws IOException, NoSuchAlgorithmException
    {
        Path src = moduleSrc();
        Map<String, String> found = new TreeMap<>();
        for (String tree : new String[]
        {
                "main/resources", "test/resources"
        })
        {
            try (Stream<Path> files = Files.walk(src.resolve(tree)))
            {
                for (Path file : files.filter(Files::isRegularFile).toList())
                {
                    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(lf(Files.readAllBytes(file))));
                    found.put(src.relativize(file).toString().replace('\\', '/'), digest);
                }
            }
        }
        assertTrue(found.size() >= 28, "the roster read the resources: " + found.size());
        assertEquals(new TreeMap<>(ROSTER), found,
                "a resource changed, appeared or disappeared — edit the roster in BOTH twins");
    }
}
