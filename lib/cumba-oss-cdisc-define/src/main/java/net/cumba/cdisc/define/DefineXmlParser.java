package net.cumba.cdisc.define;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import javax.xml.stream.XMLInputFactory;

/**
 * Complete CDISC Define.xml parser using Jackson XML with immutable Lombok beans. Supports ODM
 * 1.3.2 with Define-XML 2.0/2.1 extensions.
 *
 * <p>
 * Attribute namespace information is stripped on the way in so namespaced attributes such as
 * Define-XML 1.0's {@code def:StandardName} bind to the same {@code @JacksonXmlProperty} as the
 * unqualified Define-XML 2.x {@code StandardName}. See {@link NamespaceAgnosticAttrFactory}.
 */
public class DefineXmlParser
{

    private final XmlMapper xmlMapper;

    public DefineXmlParser()
    {
        XmlFactory xmlFactory = XmlFactory.builder()//
                .xmlInputFactory(new NamespaceAgnosticAttrFactory(XMLInputFactory.newInstance()))//
                .build();
        this.xmlMapper = new XmlMapper(xmlFactory);
        // Ignore unknown properties for robustness
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Disambiguate the v2.0 Origin attribute from the v2.1 <def:Origin> element (same local
        // name) so both bind to their respective ItemDef properties. See
        // OriginDisambiguationModule.
        this.xmlMapper.registerModule(new OriginDisambiguationModule());
        // Same collision, same cure for ItemGroupDef's def:Class (v2.0 attribute vs v2.1 element
        // with SubClass children). See ItemGroupClassDisambiguationModule.
        this.xmlMapper.registerModule(new ItemGroupClassDisambiguationModule());
    }


    public ODM parse(File file) throws IOException
    {
        // Open the stream ourselves so the file handle is released on Windows.
        // XMLStreamReader.close() is not required to close the underlying source,
        // so relying on xmlMapper.readValue(File) can leave the file locked.
        try (InputStream in = new BufferedInputStream(new FileInputStream(file)))
        {
            return xmlMapper.readValue(in, ODM.class);
        }
    }


    public ODM parse(URI aURI) throws IOException
    {
        URL url = aURI.toURL();
        try (InputStream in = url.openStream())
        {
            return xmlMapper.readValue(in, ODM.class);
        }
    }


    public ODM parse(InputStream aStream) throws IOException
    {
        return xmlMapper.readValue(aStream, ODM.class);
    }


    public ODM parse(String xml) throws IOException
    {
        return xmlMapper.readValue(xml, ODM.class);
    }
}
