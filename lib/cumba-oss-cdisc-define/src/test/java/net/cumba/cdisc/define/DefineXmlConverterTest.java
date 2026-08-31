package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.ParserConfigurationException;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.SAXException;

/** Phase 1: version detection, no-op pass, downgrade/undeterminable guards, config validation. */
class DefineXmlConverterTest
{

    private static final String V10 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.2\""
            + " xmlns:def=\"http://www.cdisc.org/ns/def/v1.0\""
            + " ODMVersion=\"1.2\" FileType=\"Snapshot\" FileOID=\"X\">"
            + "<Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\""
            + " def:DefineVersion=\"1.0.0\" def:StandardName=\"CDISC SDTM\""
            + " def:StandardVersion=\"3.1.0\"/></Study></ODM>";

    private static final String V20 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.3\""
            + " xmlns:def=\"http://www.cdisc.org/ns/def/v2.0\""
            + " ODMVersion=\"1.3.2\" FileType=\"Snapshot\" FileOID=\"X\">"
            + "<Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\""
            + " def:DefineVersion=\"2.0.0\" def:StandardName=\"SDTM-IG\""
            + " def:StandardVersion=\"3.1.2\"/></Study></ODM>";

    private static final String V21 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.3\""
            + " xmlns:def=\"http://www.cdisc.org/ns/def/v2.1\""
            + " ODMVersion=\"1.3.2\" FileType=\"Snapshot\" FileOID=\"X\" def:Context=\"Submission\">"
            + "<Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\""
            + " def:DefineVersion=\"2.1.0\"/></Study></ODM>";

    private static final String GARBAGE = "<?xml version=\"1.0\"?><root><child a=\"1\"/></root>";

    private static DefineXmlConverter of(String xml)
        throws IOException, SAXException, ParserConfigurationException
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------- detection ----------


    @Test
    void detectsAllThreeViaDefineVersion() throws Exception
    {
        assertEquals(Version.V1_0, of(V10).detectInputVersion());
        assertEquals(Version.V2_0, of(V20).detectInputVersion());
        assertEquals(Version.V2_1, of(V21).detectInputVersion());
    }


    @Test
    void detectsViaDefNamespaceWhenNoDefineVersion() throws Exception
    {
        String noDv = V21.replace(" def:DefineVersion=\"2.1.0\"", "");
        assertEquals(Version.V2_1, of(noDv).detectInputVersion());
    }


    @Test
    void detectsV10ViaOdmVersionFallback() throws Exception
    {
        // strip DefineVersion and use a non-define def namespace so only ODMVersion=1.2 remains
        String odmOnly = "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.2\""
                + " ODMVersion=\"1.2\" FileType=\"Snapshot\" FileOID=\"X\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\"/></Study></ODM>";
        assertEquals(Version.V1_0, of(odmOnly).detectInputVersion());
    }


    @Test
    void detectsV20ViaStructuralFingerprint() throws Exception
    {
        // no DefineVersion, no def namespace, but a CommentDef present -> >= 2.0
        String struct = "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.3\""
                + " ODMVersion=\"1.3.2\" FileType=\"Snapshot\" FileOID=\"X\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\">"
                + "<CommentDef OID=\"C\"/></MetaDataVersion></Study></ODM>";
        assertEquals(Version.V2_0, of(struct).detectInputVersion());
    }


    @Test
    void returnsNullForUndeterminable() throws Exception
    {
        assertNull(of(GARBAGE).detectInputVersion());
    }

    // ---------- conversion guards ----------


    @Test
    void convertWithoutTargetThrows() throws Exception
    {
        DefineConversionException ex = assertThrows(DefineConversionException.class,
                () -> of(V20).convert());
        assertTrue(ex.getMessage().contains("target"));
    }


    @Test
    void undeterminableWithoutOverrideThrows() throws Exception
    {
        DefineConversionException ex = assertThrows(DefineConversionException.class,
                () -> of(GARBAGE).to(Version.V2_1).convert());
        assertTrue(ex.getMessage().contains("cannot determine"));
    }


    @Test
    void overrideBypassesDetection() throws Exception
    {
        // GARBAGE detects null, but an explicit from() + same target is a clean no-op.
        DefineXmlConverter c = of(GARBAGE).from(Version.V2_1).to(Version.V2_1).convert();
        assertTrue(c.getLog().stream().anyMatch(l -> l.contains("same version")));
        assertTrue(c.getWarnings().isEmpty());
    }


    @Test
    void downgradeThrows() throws Exception
    {
        DefineConversionException ex = assertThrows(DefineConversionException.class,
                () -> of(V21).to(Version.V2_0).convert());
        assertTrue(ex.getMessage().contains("downgrade"));
    }


    @ParameterizedTest
    @CsvSource(
    {
            "V2_0", "V2_1"
    })
    void sameVersionIsNoOpAndReserialises(Version v) throws Exception
    {
        String xml = v == Version.V2_0 ? V20 : V21;
        byte[] out = of(xml).to(v).convert().toByteArray();
        String s = new String(out, StandardCharsets.UTF_8);
        assertTrue(s.contains("def:DefineVersion=\"" + v.defineVersion() + "\"")
                || s.contains("DefineVersion=\"" + v.defineVersion() + "\""));
    }

    // ---------- config validation ----------


    @Test
    void contextRejectsInvalidValue() throws Exception
    {
        assertThrows(DefineConversionException.class, () -> of(V20).context("Nonsense"));
    }


    @Test
    void contextAcceptsSubmissionAndOther() throws Exception
    {
        of(V20).context("Submission").context("Other");
    }


    @Test
    void failOnWarningWithNoWarningsDoesNotThrow() throws Exception
    {
        // same-version conversion raises no warnings, so strict mode is fine
        DefineXmlConverter c = of(V21).failOnWarning(true).to(Version.V2_1).convert();
        assertFalse(c.getLog().isEmpty());
    }

    // ---------- Version.fromLabel ----------


    @ParameterizedTest
    @CsvSource(
    {
            "1.0,V1_0", "2.0,V2_0", "2.1,V2_1", "v2.1,V2_1", "2.1.7,V2_1", "2.0.0,V2_0"
    })
    void fromLabelParses(String label, Version expected)
    {
        assertEquals(expected, Version.fromLabel(label));
    }


    @Test
    void fromLabelRejectsUnknown()
    {
        assertThrows(DefineConversionException.class, () -> Version.fromLabel("3.0"));
    }


    @Test
    void versionMetadataAccessors()
    {
        assertEquals("2.1", Version.V2_1.label());
        assertEquals("2.1.0", Version.V2_1.defineVersion());
    }


    @Test
    void exceptionCarriesMessageAndCause()
    {
        Throwable cause = new IllegalStateException("boom");
        DefineConversionException ex = new DefineConversionException("msg", cause);
        assertEquals("msg", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

}
