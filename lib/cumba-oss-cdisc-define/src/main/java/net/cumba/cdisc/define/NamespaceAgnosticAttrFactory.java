package net.cumba.cdisc.define;

import java.io.InputStream;
import java.io.Reader;

import javax.xml.namespace.QName;
import javax.xml.stream.EventFilter;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.transform.Source;
import org.jspecify.annotations.Nullable;

/**
 * {@link XMLInputFactory} that wraps every created {@link XMLStreamReader} so attribute namespace
 * information is hidden from the consumer. Attribute local names stay intact, but the namespace URI
 * and prefix are reported as empty. This lets Jackson XML bind both {@code def:StandardName}
 * (Define-XML 1.0) and {@code StandardName} (Define-XML 2.x) to the same
 * {@code @JacksonXmlProperty(localName = "StandardName")} field without per-attribute aliasing.
 *
 * <p>
 * Only attribute namespace reporting is stripped — element namespace handling is delegated
 * unchanged, so XML document semantics (element scoping, default namespaces) are preserved.
 */
public class NamespaceAgnosticAttrFactory extends XMLInputFactory
{

    private final XMLInputFactory delegate;

    public NamespaceAgnosticAttrFactory(XMLInputFactory aDelegate)
    {
        delegate = aDelegate;
    }


    private static @Nullable XMLStreamReader wrap(@Nullable XMLStreamReader aReader)
    {
        if (aReader == null)
        {
            return null;
        }
        return new StreamReaderDelegate(aReader)
        {

            // Stripped: attribute namespace URI is intentionally reported as absent.
            @Override
            public @Nullable String getAttributeNamespace(int aIndex)
            {
                return null;
            }


            @Override
            public String getAttributePrefix(int aIndex)
            {
                return "";
            }


            @Override
            public @Nullable QName getAttributeName(int aIndex)
            {
                QName q = super.getAttributeName(aIndex);
                return q == null ? null : new QName(q.getLocalPart());
            }
        };
    }


    @Override
    public @Nullable XMLStreamReader createXMLStreamReader(InputStream aStream)
        throws XMLStreamException
    {
        return wrap(delegate.createXMLStreamReader(aStream));
    }


    @Override
    public @Nullable XMLStreamReader createXMLStreamReader(InputStream aStream, String aEncoding)
        throws XMLStreamException
    {
        return wrap(delegate.createXMLStreamReader(aStream, aEncoding));
    }


    @Override
    public @Nullable XMLStreamReader createXMLStreamReader(Reader aReader) throws XMLStreamException
    {
        return wrap(delegate.createXMLStreamReader(aReader));
    }


    @Override
    public @Nullable XMLStreamReader createXMLStreamReader(String aSystemId, InputStream aStream)
        throws XMLStreamException
    {
        return wrap(delegate.createXMLStreamReader(aSystemId, aStream));
    }


    @Override
    public @Nullable XMLStreamReader createXMLStreamReader(String aSystemId, Reader aReader)
        throws XMLStreamException
    {
        return wrap(delegate.createXMLStreamReader(aSystemId, aReader));
    }


    @Override
    public @Nullable XMLStreamReader createXMLStreamReader(Source aSource) throws XMLStreamException
    {
        return wrap(delegate.createXMLStreamReader(aSource));
    }


    @Override
    public XMLEventReader createXMLEventReader(InputStream aStream) throws XMLStreamException
    {
        return delegate.createXMLEventReader(aStream);
    }


    @Override
    public XMLEventReader createXMLEventReader(InputStream aStream, String aEncoding)
        throws XMLStreamException
    {
        return delegate.createXMLEventReader(aStream, aEncoding);
    }


    @Override
    public XMLEventReader createXMLEventReader(Reader aReader) throws XMLStreamException
    {
        return delegate.createXMLEventReader(aReader);
    }


    @Override
    public XMLEventReader createXMLEventReader(String aSystemId, InputStream aStream)
        throws XMLStreamException
    {
        return delegate.createXMLEventReader(aSystemId, aStream);
    }


    @Override
    public XMLEventReader createXMLEventReader(String aSystemId, Reader aReader)
        throws XMLStreamException
    {
        return delegate.createXMLEventReader(aSystemId, aReader);
    }


    @Override
    public XMLEventReader createXMLEventReader(Source aSource) throws XMLStreamException
    {
        return delegate.createXMLEventReader(aSource);
    }


    @Override
    public XMLEventReader createXMLEventReader(XMLStreamReader aReader) throws XMLStreamException
    {
        return delegate.createXMLEventReader(aReader);
    }


    @Override
    public XMLEventReader createFilteredReader(XMLEventReader aReader, EventFilter aFilter)
        throws XMLStreamException
    {
        return delegate.createFilteredReader(aReader, aFilter);
    }


    @Override
    public XMLStreamReader createFilteredReader(XMLStreamReader aReader, StreamFilter aFilter)
        throws XMLStreamException
    {
        return delegate.createFilteredReader(aReader, aFilter);
    }


    @Override
    public XMLResolver getXMLResolver()
    {
        return delegate.getXMLResolver();
    }


    @Override
    public void setXMLResolver(XMLResolver aResolver)
    {
        delegate.setXMLResolver(aResolver);
    }


    @Override
    public XMLReporter getXMLReporter()
    {
        return delegate.getXMLReporter();
    }


    @Override
    public void setXMLReporter(XMLReporter aReporter)
    {
        delegate.setXMLReporter(aReporter);
    }


    @Override
    public void setProperty(String aName, Object aValue)
    {
        delegate.setProperty(aName, aValue);
    }


    @Override
    public Object getProperty(String aName)
    {
        return delegate.getProperty(aName);
    }


    @Override
    public boolean isPropertySupported(String aName)
    {
        return delegate.isPropertySupported(aName);
    }


    @Override
    public void setEventAllocator(XMLEventAllocator aAllocator)
    {
        delegate.setEventAllocator(aAllocator);
    }


    @Override
    public XMLEventAllocator getEventAllocator()
    {
        return delegate.getEventAllocator();
    }
}
