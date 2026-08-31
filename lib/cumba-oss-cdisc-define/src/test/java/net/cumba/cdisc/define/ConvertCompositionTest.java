package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase 4: composed 1.0 -> 2.1 conversion, file round-trips, and the static detect API. */
class ConvertCompositionTest
{

    private static byte[] resource(String name) throws Exception
    {
        try (InputStream in = ConvertCompositionTest.class.getResourceAsStream(name))
        {
            assertNotNull(in, "fixture " + name);
            return in.readAllBytes();
        }
    }


    @Test
    void composes10To21ThroughBothSteps() throws Exception
    {
        byte[] v10 = resource("/convert/define-v10-sdtm.xml");
        DefineXmlConverter c = DefineXmlConverter
                .forInputStream(new java.io.ByteArrayInputStream(v10)).to(Version.V2_1);
        byte[] bytes = c.convert().toByteArray();
        String out = new String(bytes, StandardCharsets.UTF_8);

        assertFalse(out.contains("def/v1.0"));
        assertFalse(out.contains("def/v2.0"));
        assertTrue(out.contains("def/v2.1"));
        assertTrue(out.contains("def:DefineVersion=\"2.1.0\""));
        assertTrue(out.contains("def:Context="));
        assertTrue(out.contains("<def:Standards>"), "standards synthesised at the 2.1 leg");
        assertTrue(out.contains("Source="), "origins gained a Source at the 2.1 leg");
        assertTrue(out.contains("<def:WhereClauseDef "),
                "where-clauses synthesised at the 1.0 leg");

        // warnings aggregate across both atomic steps
        assertTrue(c.getWarnings().stream().anyMatch(w -> w.contains("synthesised WC")));
        assertTrue(c.getWarnings().stream().anyMatch(w -> w.contains("CT")));

        ODM odm = new DefineXmlParser().parse(new java.io.ByteArrayInputStream(bytes));
        assertEquals("2.1.0",
                odm.getStudies().get(0).getMetaDataVersions().get(0).getDefineVersion());
    }


    @Test
    void fileRoundTrip(@TempDir Path dir) throws Exception
    {
        Path in = dir.resolve("in.xml");
        Path out = dir.resolve("out.xml");
        Files.write(in, resource("/convert/define-v20-sdtm.xml"));

        DefineXmlConverter c = DefineXmlConverter.forFile(in).to(Version.V2_1).convert();
        c.writeTo(out);

        assertTrue(Files.exists(out));
        String written = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(written.contains("def:DefineVersion=\"2.1.0\""));
        assertNotNull(c.getDocument());
        assertFalse(c.getLog().isEmpty());
    }


    @Test
    void staticDetectVersionApi() throws Exception
    {
        byte[] v21 = resource("/convert/define-v21-sdtm.xml");
        var doc = DefineDomIo.parse(new java.io.ByteArrayInputStream(v21));
        assertEquals(Version.V2_1, DefineXmlConverter.detectVersion(doc));
    }


    @Test
    void alreadyAtTargetIsCleanNoOp() throws Exception
    {
        byte[] v21 = resource("/convert/define-v21-sdtm.xml");
        DefineXmlConverter c = DefineXmlConverter
                .forInputStream(new java.io.ByteArrayInputStream(v21)).to(Version.V2_1).convert();
        assertTrue(c.getWarnings().isEmpty(), "no warnings for a same-version pass");
        String out = new String(c.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(out.contains("def/v2.1"));
    }

}
