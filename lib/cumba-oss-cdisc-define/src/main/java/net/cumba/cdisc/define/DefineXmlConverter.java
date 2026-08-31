package net.cumba.cdisc.define;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Upgrades a CDISC Define-XML document to a newer version (2.0 or 2.1). Works on the raw DOM —
 * mirroring {@link DefineXmlPruner} — so comments, element ordering, and unknown content are
 * preserved on write, which a round-trip through the Jackson bean model could not guarantee.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * DefineXmlConverter.forFile(in).to(DefineXmlConverter.Version.V2_1).convert().writeTo(out);
 * }</pre>
 *
 * <p>
 * The input version is auto-detected ({@link #detectVersion(Document)}); call
 * {@link #from(Version)} to override when detection is not possible. Only upgrades are supported —
 * a downgrade request throws {@link DefineConversionException}. Same-version conversion is a
 * validated re-serialisation. 1.0 → 2.x is best-effort: constructs with no machine-readable v1.0
 * source (value-level conditions, comments, methods, OIDs) are synthesised and every inference is
 * recorded via {@link #getWarnings()}.
 */
public final class DefineXmlConverter
{

    static final String ODM_NS_12 = "http://www.cdisc.org/ns/odm/v1.2";

    static final String ODM_NS_13 = "http://www.cdisc.org/ns/odm/v1.3";

    static final String DEF_NS_10 = "http://www.cdisc.org/ns/def/v1.0";

    static final String DEF_NS_20 = "http://www.cdisc.org/ns/def/v2.0";

    static final String DEF_NS_21 = "http://www.cdisc.org/ns/def/v2.1";

    /**
     * Structural Define-XML versions. The whole 2.1.x maintenance line collapses to {@link #V2_1}.
     * Each carries an explicit {@link #rank()} defining the upgrade direction.
     */
    public enum Version
    {

        V1_0(0, "1.0", "1.0.0", ODM_NS_12, "1.2", DEF_NS_10),
        V2_0(1, "2.0", "2.0.0", ODM_NS_13, "1.3.2", DEF_NS_20),
        V2_1(2, "2.1", "2.1.0", ODM_NS_13, "1.3.2", DEF_NS_21);

        private final int rank;

        private final String label;

        private final String defineVersion;

        private final String odmNamespace;

        private final String odmVersion;

        private final String defNamespace;

        Version(int rank, String label, String defineVersion, String odmNamespace,
                String odmVersion, String defNamespace)
        {
            this.rank = rank;
            this.label = label;
            this.defineVersion = defineVersion;
            this.odmNamespace = odmNamespace;
            this.odmVersion = odmVersion;
            this.defNamespace = defNamespace;
        }


        /** Upgrade rank; higher means a newer version. */
        int rank()
        {
            return rank;
        }


        /** The next-higher structural version, or {@code null} if this is the newest. */
        @Nullable
        Version next()
        {
            return switch (this)
            {
            case V1_0 -> V2_0;
            case V2_0 -> V2_1;
            case V2_1 -> null;
            };
        }


        /** Short label, e.g. {@code "2.1"}. */
        public String label()
        {
            return label;
        }


        /** Value for the {@code def:DefineVersion} attribute, e.g. {@code "2.1.0"}. */
        public String defineVersion()
        {
            return defineVersion;
        }


        String odmNamespace()
        {
            return odmNamespace;
        }


        String odmVersion()
        {
            return odmVersion;
        }


        String defNamespace()
        {
            return defNamespace;
        }


        /**
         * Parse a user-supplied label ({@code "1.0"}, {@code "2.0"}, {@code "2.1"}; also accepts
         * {@code "2.1.0"}/{@code "2.1.7"} forms and a {@code "v"} prefix).
         *
         * @throws DefineConversionException
         *             if the label is not a recognised version
         */
        public static Version fromLabel(String raw)
        {
            if (raw == null)
            {
                throw new DefineConversionException("version is null");
            }
            String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (s.startsWith("v"))
            {
                s = s.substring(1);
            }
            if (s.equals("1.0") || s.startsWith("1.0."))
            {
                return V1_0;
            }
            if (s.equals("2.0") || s.startsWith("2.0."))
            {
                return V2_0;
            }
            if (s.equals("2.1") || s.startsWith("2.1."))
            {
                return V2_1;
            }
            throw new DefineConversionException("unrecognised Define-XML version: " + raw);
        }
    }

    private final Document doc;

    private @Nullable Version inputOverride;

    private @Nullable Version target;

    private boolean keepLegacyStandardAttributes;

    private String context = "Submission";

    private boolean failOnWarning;

    private final List<String> log = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    /** Atomic steps available to compose a conversion path. Adjacent versions only. */
    private final List<ConversionStep> steps = List.of(new Step10To20(), new Step20To21());

    private DefineXmlConverter(Document doc)
    {
        this.doc = doc;
    }

    // ========== Factories ==========


    public static DefineXmlConverter forFile(Path path)
        throws IOException, SAXException, ParserConfigurationException
    {
        return forFile(path.toFile());
    }


    public static DefineXmlConverter forFile(File file)
        throws IOException, SAXException, ParserConfigurationException
    {
        try (FileInputStream fin = new FileInputStream(file))
        {
            return forInputStream(fin);
        }
    }


    public static DefineXmlConverter forInputStream(InputStream is)
        throws IOException, SAXException, ParserConfigurationException
    {
        return new DefineXmlConverter(DefineDomIo.parse(is));
    }

    // ========== Detection ==========


    /**
     * Best-effort version detection, in priority order: {@code def:DefineVersion} on
     * MetaDataVersion, then the {@code def:} namespace URI, then {@code ODMVersion}, then
     * structural fingerprints. Returns {@code null} when the version cannot be determined.
     */
    public static @Nullable Version detectVersion(Document doc)
    {
        Element mdv = DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
        if (mdv != null)
        {
            String dv = DefineDomUtil.attrIgnoreNs(mdv, "DefineVersion");
            if (dv != null)
            {
                String t = dv.trim();
                if (t.startsWith("1.0"))
                {
                    return Version.V1_0;
                }
                if (t.startsWith("2.0"))
                {
                    return Version.V2_0;
                }
                if (t.startsWith("2.1"))
                {
                    return Version.V2_1;
                }
            }
        }
        if (DefineDomUtil.hasNamespace(doc, DEF_NS_21))
        {
            return Version.V2_1;
        }
        if (DefineDomUtil.hasNamespace(doc, DEF_NS_20))
        {
            return Version.V2_0;
        }
        if (DefineDomUtil.hasNamespace(doc, DEF_NS_10))
        {
            return Version.V1_0;
        }
        Element root = doc.getDocumentElement();
        if (root != null)
        {
            String ov = DefineDomUtil.attrIgnoreNs(root, "ODMVersion");
            if (ov != null && ov.trim().startsWith("1.2"))
            {
                return Version.V1_0;
            }
            if (DefineDomUtil.attrIgnoreNs(root, "Context") != null)
            {
                return Version.V2_1;
            }
        }
        if (DefineDomUtil.firstByLocalName(doc, "Standards") != null)
        {
            return Version.V2_1;
        }
        if (DefineDomUtil.firstByLocalName(doc, "WhereClauseDef") != null
                || DefineDomUtil.firstByLocalName(doc, "CommentDef") != null)
        {
            return Version.V2_0;
        }
        return null;
    }


    /** Detect the version of the document held by this converter. */
    public @Nullable Version detectInputVersion()
    {
        return detectVersion(doc);
    }

    // ========== Fluent config ==========


    /** Override version detection with an explicit input version. */
    public DefineXmlConverter from(Version explicitInput)
    {
        this.inputOverride = explicitInput;
        return this;
    }


    /** Set the (required) target version. */
    public DefineXmlConverter to(Version targetVersion)
    {
        this.target = targetVersion;
        return this;
    }


    /**
     * In 2.0 → 2.1, whether to keep the deprecated
     * {@code @def:StandardName}/{@code @def:StandardVersion} after synthesising
     * {@code def:Standards}. Default {@code false} (drop, for strict v2.1 conformance).
     */
    public DefineXmlConverter keepLegacyStandardAttributes(boolean keep)
    {
        this.keepLegacyStandardAttributes = keep;
        return this;
    }


    /**
     * Value for the required v2.1 {@code def:Context} on the root ODM. Default {@code Submission}.
     */
    public DefineXmlConverter context(String submissionOrOther)
    {
        if (!"Submission".equals(submissionOrOther) && !"Other".equals(submissionOrOther))
        {
            throw new DefineConversionException(
                    "def:Context must be 'Submission' or 'Other', was: " + submissionOrOther);
        }
        this.context = submissionOrOther;
        return this;
    }


    /** Treat any fidelity warning as a hard error. Default {@code false}. */
    public DefineXmlConverter failOnWarning(boolean strict)
    {
        this.failOnWarning = strict;
        return this;
    }

    // ========== Run ==========


    /**
     * Resolve the input version, validate the request, and apply the composed step chain in place.
     *
     * @throws DefineConversionException
     *             if no target is set, the input version is undeterminable and not overridden, a
     *             downgrade is requested, no step exists for a leg of the path, or a warning is
     *             raised while {@code failOnWarning} is set
     */
    public DefineXmlConverter convert()
    {
        if (target == null)
        {
            throw new DefineConversionException("target version not set; call to(Version)");
        }
        Version input = inputOverride != null ? inputOverride : detectInputVersion();
        if (input == null)
        {
            throw new DefineConversionException(
                    "cannot determine input Define-XML version; supply it explicitly via from(Version)");
        }
        log.add("input version: " + input.label()
                + (inputOverride != null ? " (override)" : " (detected)"));
        log.add("target version: " + target.label());
        if (target.rank() < input.rank())
        {
            throw new DefineConversionException(
                    "downgrade not supported: " + input.label() + " -> " + target.label());
        }

        OidMinter minter = new OidMinter(DefineDomUtil.collectExistingOids(doc));
        ConversionContext ctx = new ConversionContext(log, warnings, keepLegacyStandardAttributes,
                context, minter);
        for (ConversionStep step : planSteps(input, target))
        {
            log.add("applying step " + step.from().label() + " -> " + step.to().label());
            step.apply(doc, ctx);
        }
        if (input == target)
        {
            log.add("same version: no structural change, re-serialised");
        }
        if (failOnWarning && !warnings.isEmpty())
        {
            throw new DefineConversionException(warnings.size()
                    + " fidelity warning(s) under strict mode; first: " + warnings.get(0));
        }
        return this;
    }


    /** Compose the consecutive atomic steps from {@code from} up to {@code to}. */
    private List<ConversionStep> planSteps(Version from, Version to)
    {
        List<ConversionStep> path = new ArrayList<>();
        Version cur = from;
        while (cur != to)
        {
            Version next = cur.next();
            if (next == null)
            {
                throw new DefineConversionException(
                        "no conversion path " + from.label() + " -> " + to.label());
            }
            ConversionStep step = findStep(cur, next);
            if (step == null)
            {
                throw new DefineConversionException(
                        "no conversion step " + cur.label() + " -> " + next.label());
            }
            path.add(step);
            cur = next;
        }
        return path;
    }


    private @Nullable ConversionStep findStep(Version from, Version to)
    {
        for (ConversionStep step : steps)
        {
            if (step.from() == from && step.to() == to)
            {
                return step;
            }
        }
        return null;
    }

    // ========== Output ==========


    public DefineXmlConverter writeTo(Path path) throws IOException, TransformerException
    {
        return writeTo(path.toFile());
    }


    public DefineXmlConverter writeTo(File file) throws IOException, TransformerException
    {
        try (FileOutputStream fos = new FileOutputStream(file))
        {
            return writeTo(fos);
        }
    }


    public DefineXmlConverter writeTo(OutputStream os) throws TransformerException
    {
        DefineDomIo.write(doc, os);
        return this;
    }


    public byte[] toByteArray() throws TransformerException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeTo(baos);
        return baos.toByteArray();
    }

    // ========== Diagnostics ==========


    public Document getDocument()
    {
        return doc;
    }


    /** Progress trace of the conversion. */
    public List<String> getLog()
    {
        return Collections.unmodifiableList(log);
    }


    /** Fidelity/ambiguity warnings raised during conversion. */
    public List<String> getWarnings()
    {
        return Collections.unmodifiableList(warnings);
    }

}
