package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The import rule of the {@code house} package (plan §3.1, confirmation look B5): every main source
 * under {@code house/} (and, from Phase B on, {@code convert/}) imports only {@code java.*},
 * Jackson, JSpecify and the two packages themselves — never the {@code cumba-datatable} API — so
 * Phase B can copy them byte-identically into an OSS module that has no format API.
 * <p>
 * Planted control (A2): an {@code import net.cumba.datatable.formats.IDataFormat;} in a
 * {@code house} class reds this test.
 * </p>
 */
class ImportCensusTest
{

    /** A qualified {@code net.cumba.…} reference anywhere in a line (javadoc links included). */
    private static final Pattern QUALIFIED = Pattern.compile("net\\.cumba\\.[A-Za-z0-9_.]+");

    private static final List<String> ALLOWED = List.of("java.", "com.fasterxml.jackson.",
            "org.jspecify.", "net.cumba.datatable.dictionary.house.",
            "net.cumba.datatable.dictionary.convert.");

    private static Path sourceRoot()
    {
        // surefire runs in target/test-cwd (the home redirect); walk up to the module directory
        Path relative = Path.of("src", "main", "java", "net", "cumba", "datatable", "dictionary");
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent())
        {
            Path root = dir.resolve(relative);
            if (Files.isDirectory(root.resolve("house")))
            {
                return root;
            }
        }
        throw new AssertionError("module sources not found above " + Path.of("").toAbsolutePath());
    }


    @Test
    void houseAndConvertImportOnlyJavaJacksonJspecifyAndThemselves() throws IOException
    {
        Path root = sourceRoot();
        List<String> violations = new ArrayList<>();
        int sources = 0;
        for (String pkg : List.of("house", "convert"))
        {
            Path dir = root.resolve(pkg);
            if (!Files.isDirectory(dir))
            {
                continue;
            }
            try (Stream<Path> files = Files.list(dir))
            {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted()
                        .toList())
                {
                    sources++;
                    for (String line : Files.readAllLines(file))
                    {
                        String s = line.strip();
                        if (s.startsWith("import "))
                        {
                            String imported = s.substring("import ".length()).replace("static ", "")
                                    .replace(";", "").strip();
                            if (ALLOWED.stream().noneMatch(imported::startsWith))
                            {
                                violations.add(file.getFileName() + ": " + s);
                            }
                        }
                        // a fully qualified reference bypasses the import census (measured: the
                        // first planted control used one and stayed green), so every line is read
                        Matcher m = QUALIFIED.matcher(s);
                        while (m.find())
                        {
                            String ref = m.group() + ".";
                            if (ALLOWED.stream().noneMatch(ref::startsWith))
                            {
                                violations.add(file.getFileName() + ": " + s);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(sources >= 7, "the census read the house sources: " + sources);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }


    @Test
    void theFormatPackageIsTheOnlyOneTouchingTheDatatableApi() throws IOException
    {
        Path format = sourceRoot().resolve("format");
        // the OSS twin (cumba-oss-datatable-dictionary) withholds the format package: there the
        // house / convert census above is the whole claim
        assumeTrue(Files.isDirectory(format), "no format package in this module (the OSS twin)");
        boolean touches = false;
        try (Stream<Path> files = Files.list(format))
        {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList())
            {
                touches |= Files.readString(file).contains("import net.cumba.datatable.formats.");
            }
        }
        assertTrue(touches, "format is where the API is used");
        assertFalse(Files.exists(sourceRoot().resolve("house").resolve("DictionaryFormat.java")));
    }
}
