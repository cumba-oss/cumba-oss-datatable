package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Error-path and additional-overload tests for {@link DefineXmlParser}. The happy-path cases live
 * in {@link DefineXmlParserTest}; this class covers malformed XML inputs and the file/URI parse
 * overloads.
 */
class DefineXmlParserErrorTest
{

    private final DefineXmlParser parser = new DefineXmlParser();

    // ==================== Malformed XML — parameterised ====================

    @ParameterizedTest
    @ValueSource(strings =
    {
            "<ODM><unclosed>", "<ODM><Study></ODM>", "<ODM", "not xml at all",
            "<?xml version=\"1.0\"?><ODM><Study OID=&badref;/></ODM>",
            "<ODM><Study OID=\"missing-quote/></ODM>"
    })
    void parseMalformedXml_throwsIOException(String xml)
    {
        assertThrows(IOException.class, () -> parser.parse(xml));
    }


    @Test
    void parseEmptyInputStream_throws()
    {
        InputStream empty = new ByteArrayInputStream(new byte[0]);
        assertThrows(IOException.class, () -> parser.parse(empty));
    }


    @Test
    void parseEmptyString_throws()
    {
        assertThrows(IOException.class, () -> parser.parse(""));
    }

    // ==================== File and URI overloads ====================


    @Test
    void parseFile_readsMinimalDocument(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("minimal.xml");
        Files.writeString(file, "<ODM FileOID=\"f1\" />", StandardCharsets.UTF_8);

        ODM odm = parser.parse(file.toFile());

        assertNotNull(odm);
        assertEquals("f1", odm.getFileOID());
    }


    @Test
    void parseUri_readsMinimalDocument(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("via-uri.xml");
        Files.writeString(file, "<ODM FileOID=\"u1\" />", StandardCharsets.UTF_8);
        URI uri = file.toUri();

        ODM odm = parser.parse(uri);

        assertNotNull(odm);
        assertEquals("u1", odm.getFileOID());
    }


    @Test
    void parseFile_missingFile_throws(@TempDir Path tempDir)
    {
        File missing = tempDir.resolve("does-not-exist.xml").toFile();

        assertThrows(IOException.class, () -> parser.parse(missing));
    }


    @Test
    void parseUri_missingFile_throws(@TempDir Path tempDir)
    {
        URI missing = tempDir.resolve("does-not-exist.xml").toUri();

        assertThrows(IOException.class, () -> parser.parse(missing));
    }


    @Test
    void parseInputStream_malformed_throws()
    {
        byte[] bad = "<ODM><nope>".getBytes(StandardCharsets.UTF_8);
        InputStream is = new ByteArrayInputStream(bad);

        assertThrows(IOException.class, () -> parser.parse(is));
    }

    // ==================== Resilience: unknown attributes ignored ====================


    @Test
    void parseUnknownAttributesAndElements_doesNotThrow() throws IOException
    {
        String xml = """
                <ODM FileOID="ok" FuturisticAttribute="x">
                  <FutureElement/>
                  <Study OID="S1">
                    <MetaDataVersion OID="MDV1" Name="N" UnknownMdvAttr="y"/>
                  </Study>
                </ODM>
                """;

        ODM odm = parser.parse(xml);

        assertEquals("ok", odm.getFileOID());
        assertEquals(1, odm.getStudies().size());
    }
}
