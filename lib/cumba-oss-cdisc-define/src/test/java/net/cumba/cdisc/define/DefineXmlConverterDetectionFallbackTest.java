package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DefineXmlConverter#detectVersion} beyond its happy path, and the write side of the fluent
 * chain.
 *
 * <p>
 * A real-world Define-XML is frequently missing {@code def:DefineVersion} — it is the attribute
 * most often dropped by a hand-edited or partially generated file — and detection then has to fall
 * back on the namespaces, on {@code ODMVersion}, on {@code def:Context} and finally on the presence
 * of elements that only one version has. Getting that wrong picks the wrong conversion chain and
 * emits a document labelled as a version it is not; answering {@code null} instead makes the
 * converter refuse the file with an actionable message. Every one of those fallback arms was
 * unreached by any test, so all of them were free to answer anything.
 * </p>
 */
class DefineXmlConverterDetectionFallbackTest
{

    private static final String ODM12 = "http://www.cdisc.org/ns/odm/v1.2";

    private static final String ODM13 = "http://www.cdisc.org/ns/odm/v1.3";

    private static DefineXmlConverter of(String aXml) throws Exception
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)));
    }


    /** No def:DefineVersion anywhere: the def namespace declaration is the next-best evidence. */
    @Test
    void theDefNamespaceDecidesWhenTheDefineVersionAttributeIsAbsent() throws Exception
    {
        assertEquals(Version.V2_1,
                of("<ODM xmlns=\"" + ODM13
                        + "\" xmlns:def=\"http://www.cdisc.org/ns/def/v2.1\" FileOID=\"X\"/>")
                                .detectInputVersion());
        assertEquals(Version.V2_0,
                of("<ODM xmlns=\"" + ODM13
                        + "\" xmlns:def=\"http://www.cdisc.org/ns/def/v2.0\" FileOID=\"X\"/>")
                                .detectInputVersion());
        assertEquals(Version.V1_0,
                of("<ODM xmlns=\"" + ODM12
                        + "\" xmlns:def=\"http://www.cdisc.org/ns/def/v1.0\" FileOID=\"X\"/>")
                                .detectInputVersion());
    }


    /** No def namespace at all: ODMVersion 1.2 can only be a Define-XML 1.0 document. */
    @Test
    void odmVersionOneTwoIsTheV10Marker() throws Exception
    {
        assertEquals(Version.V1_0,
                of("<ODM xmlns=\"" + ODM12 + "\" ODMVersion=\"1.2\" FileOID=\"X\"/>")
                        .detectInputVersion());
    }


    /** {@code def:Context} exists only from 2.1, so its bare presence is decisive. */
    @Test
    void aContextAttributeOnTheRootMeansV21() throws Exception
    {
        assertEquals(Version.V2_1,
                of("<ODM xmlns=\"" + ODM13 + "\" xmlns:d=\"urn:x\" ODMVersion=\"1.3.2\""
                        + " d:Context=\"Submission\" FileOID=\"X\"/>").detectInputVersion());
    }


    /** A def:Standards block likewise: 2.1 introduced it. */
    @Test
    void aStandardsElementMeansV21() throws Exception
    {
        assertEquals(Version.V2_1,
                of("<ODM xmlns=\"" + ODM13 + "\" xmlns:d=\"urn:x\" ODMVersion=\"1.3.2\""
                        + " FileOID=\"X\"><Study OID=\"S\"><MetaDataVersion OID=\"M\">"
                        + "<d:Standards/></MetaDataVersion></Study></ODM>").detectInputVersion());
    }


    /** A WhereClauseDef or a CommentDef without any 2.1 marker is the 2.0 shape. */
    @Test
    void aWhereClauseDefOrCommentDefAloneMeansV20() throws Exception
    {
        String shell = "<ODM xmlns=\"" + ODM13 + "\" xmlns:d=\"urn:x\" ODMVersion=\"1.3.2\""
                + " FileOID=\"X\"><Study OID=\"S\"><MetaDataVersion OID=\"M\">%s"
                + "</MetaDataVersion></Study></ODM>";

        assertEquals(Version.V2_0,
                of(shell.formatted("<d:WhereClauseDef OID=\"WC.1\"/>")).detectInputVersion());
        assertEquals(Version.V2_0,
                of(shell.formatted("<d:CommentDef OID=\"COM.1\"/>")).detectInputVersion());
    }


    /** Nothing recognisable: answer null, so the converter can refuse the file by name. */
    @Test
    void aDocumentWithNoMarkerAtAllIsUndetectable() throws Exception
    {
        assertNull(of("<ODM xmlns=\"" + ODM13 + "\" ODMVersion=\"1.3.2\" FileOID=\"X\"/>")
                .detectInputVersion());
    }


    /**
     * An unrecognised {@code def:DefineVersion} must not be read as the newest version the code
     * happens to test for last: a "3.0.0" document falls through to the namespace evidence, which
     * here says 2.0. Claiming 2.1 for it would run the 2.0→2.1 step over a document that is
     * neither.
     */
    @Test
    void anUnknownDefineVersionFallsThroughToTheNamespaceEvidence() throws Exception
    {
        assertEquals(Version.V2_0,
                of("<ODM xmlns=\"" + ODM13
                        + "\" xmlns:def=\"http://www.cdisc.org/ns/def/v2.0\" FileOID=\"X\">"
                        + "<Study OID=\"S\"><MetaDataVersion OID=\"M\""
                        + " def:DefineVersion=\"3.0.0\"/></Study></ODM>").detectInputVersion());
    }


    /** A 2.1.x maintenance label still collapses to V2_1, and is not mistaken for anything else. */
    @Test
    void aMaintenanceLabelStillReadsAsV21() throws Exception
    {
        assertEquals(Version.V2_1,
                of("<ODM xmlns=\"" + ODM13 + "\" xmlns:def=\"urn:x\" FileOID=\"X\">"
                        + "<Study OID=\"S\"><MetaDataVersion OID=\"M\""
                        + " def:DefineVersion=\"2.1.7\"/></Study></ODM>").detectInputVersion());
    }

    // ---------- the log, and the write side of the chain ----------


    /**
     * The conversion log says whether the input version was detected or supplied. An operator
     * reading "(detected)" on a run that was actually forced — or the reverse — is being told the
     * wrong thing about where the version came from, which is the first thing anyone checks when a
     * conversion produces something unexpected.
     */
    @Test
    void theLogDistinguishesADetectedVersionFromAnOverriddenOne() throws Exception
    {
        String v20 = "<ODM xmlns=\"" + ODM13
                + "\" xmlns:def=\"http://www.cdisc.org/ns/def/v2.0\" ODMVersion=\"1.3.2\""
                + " FileOID=\"X\"><Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\""
                + " def:DefineVersion=\"2.0.0\" def:StandardName=\"SDTM-IG\""
                + " def:StandardVersion=\"3.1.2\"/></Study></ODM>";

        assertTrue(of(v20).to(Version.V2_1).convert().getLog()
                .contains("input version: 2.0 (detected)"));
        assertTrue(of(v20).from(Version.V2_0).to(Version.V2_1).convert().getLog()
                .contains("input version: 2.0 (override)"));
    }


    /**
     * Each {@code writeTo} overload returns the converter so a conversion can end in a chain, as
     * the class is documented to be used. A null return turns the last link of that chain into a
     * NullPointerException with nothing in it to name the cause.
     */
    @Test
    void everyWriteToOverloadReturnsTheConverter(@TempDir Path aTempDir) throws Exception
    {
        String v21 = "<ODM xmlns=\"" + ODM13
                + "\" xmlns:def=\"http://www.cdisc.org/ns/def/v2.1\" ODMVersion=\"1.3.2\""
                + " FileOID=\"X\"><Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\""
                + " def:DefineVersion=\"2.1.0\"/></Study></ODM>";

        DefineXmlConverter c = of(v21).to(Version.V2_1).convert();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertSame(c, c.writeTo(bos));

        Path asPath = aTempDir.resolve("by-path.xml");
        assertSame(c, c.writeTo(asPath));
        assertTrue(Files.readString(asPath).contains("FileOID=\"X\""));

        java.io.File asFile = aTempDir.resolve("by-file.xml").toFile();
        assertSame(c, c.writeTo(asFile));
        assertTrue(Files.readString(asFile.toPath()).contains("FileOID=\"X\""));
    }
}
