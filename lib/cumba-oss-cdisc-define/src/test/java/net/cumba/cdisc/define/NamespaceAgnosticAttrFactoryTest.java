package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.namespace.QName;
import javax.xml.stream.EventFilter;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLReporter;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.XMLEventAllocator;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NamespaceAgnosticAttrFactory}. Verifies that the wrapper hides attribute
 * namespace information from consumers and that all delegate-only methods forward unchanged.
 */
class NamespaceAgnosticAttrFactoryTest
{

    private XMLInputFactory delegate;

    private NamespaceAgnosticAttrFactory factory;

    @BeforeEach
    void setUp()
    {
        delegate = mock(XMLInputFactory.class);
        factory = new NamespaceAgnosticAttrFactory(delegate);
    }

    // ==================== wrap(...) behaviour ====================


    @Test
    void wrappedReader_returnsNullAttributeNamespace() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        when(inner.getAttributeNamespace(anyInt())).thenReturn("http://example.com/ns");
        when(delegate.createXMLStreamReader(any(InputStream.class))).thenReturn(inner);

        XMLStreamReader wrapped = factory
                .createXMLStreamReader(new ByteArrayInputStream(new byte[0]));

        assertNull(wrapped.getAttributeNamespace(0));
    }


    @Test
    void wrappedReader_returnsEmptyAttributePrefix() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        when(inner.getAttributePrefix(anyInt())).thenReturn("def");
        when(delegate.createXMLStreamReader(any(InputStream.class))).thenReturn(inner);

        XMLStreamReader wrapped = factory
                .createXMLStreamReader(new ByteArrayInputStream(new byte[0]));

        assertEquals("", wrapped.getAttributePrefix(0));
    }


    @Test
    void wrappedReader_stripsAttributeQNameNamespace() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        when(inner.getAttributeName(anyInt()))
                .thenReturn(new QName("http://example.com/ns", "StandardName", "def"));
        when(delegate.createXMLStreamReader(any(InputStream.class))).thenReturn(inner);

        XMLStreamReader wrapped = factory
                .createXMLStreamReader(new ByteArrayInputStream(new byte[0]));

        QName q = wrapped.getAttributeName(0);
        assertEquals("StandardName", q.getLocalPart());
        assertEquals("", q.getNamespaceURI());
        assertEquals("", q.getPrefix());
    }


    @Test
    void wrappedReader_handlesNullAttributeName() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        when(inner.getAttributeName(anyInt())).thenReturn(null);
        when(delegate.createXMLStreamReader(any(InputStream.class))).thenReturn(inner);

        XMLStreamReader wrapped = factory
                .createXMLStreamReader(new ByteArrayInputStream(new byte[0]));

        assertNull(wrapped.getAttributeName(0));
    }


    @Test
    void wrap_nullInnerReader_returnsNull() throws XMLStreamException
    {
        when(delegate.createXMLStreamReader(any(InputStream.class))).thenReturn(null);

        XMLStreamReader wrapped = factory
                .createXMLStreamReader(new ByteArrayInputStream(new byte[0]));

        assertNull(wrapped);
    }

    // ==================== createXMLStreamReader overloads ====================


    @Test
    void createXMLStreamReader_inputStream_wrapped() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        when(delegate.createXMLStreamReader(is)).thenReturn(inner);

        XMLStreamReader wrapped = factory.createXMLStreamReader(is);

        assertNotNull(wrapped);
        verify(delegate, times(1)).createXMLStreamReader(is);
    }


    @Test
    void createXMLStreamReader_inputStreamWithEncoding_wrapped() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        when(delegate.createXMLStreamReader(is, "UTF-8")).thenReturn(inner);

        XMLStreamReader wrapped = factory.createXMLStreamReader(is, "UTF-8");

        assertNotNull(wrapped);
        verify(delegate, times(1)).createXMLStreamReader(is, "UTF-8");
    }


    @Test
    void createXMLStreamReader_reader_wrapped() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        Reader r = new StringReader("");
        when(delegate.createXMLStreamReader(r)).thenReturn(inner);

        XMLStreamReader wrapped = factory.createXMLStreamReader(r);

        assertNotNull(wrapped);
        verify(delegate, times(1)).createXMLStreamReader(r);
    }


    @Test
    void createXMLStreamReader_systemIdAndInputStream_wrapped() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        when(delegate.createXMLStreamReader("sid", is)).thenReturn(inner);

        XMLStreamReader wrapped = factory.createXMLStreamReader("sid", is);

        assertNotNull(wrapped);
        verify(delegate, times(1)).createXMLStreamReader("sid", is);
    }


    @Test
    void createXMLStreamReader_systemIdAndReader_wrapped() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        Reader r = new StringReader("");
        when(delegate.createXMLStreamReader("sid", r)).thenReturn(inner);

        XMLStreamReader wrapped = factory.createXMLStreamReader("sid", r);

        assertNotNull(wrapped);
        verify(delegate, times(1)).createXMLStreamReader("sid", r);
    }


    @Test
    void createXMLStreamReader_source_wrapped() throws XMLStreamException
    {
        XMLStreamReader inner = mock(XMLStreamReader.class);
        Source src = new StreamSource(new ByteArrayInputStream(new byte[0]));
        when(delegate.createXMLStreamReader(src)).thenReturn(inner);

        XMLStreamReader wrapped = factory.createXMLStreamReader(src);

        assertNotNull(wrapped);
        verify(delegate, times(1)).createXMLStreamReader(src);
    }

    // ==================== createXMLEventReader overloads (pure delegation) ====================


    @Test
    void createXMLEventReader_inputStream_delegated() throws XMLStreamException
    {
        XMLEventReader inner = mock(XMLEventReader.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        when(delegate.createXMLEventReader(is)).thenReturn(inner);

        XMLEventReader result = factory.createXMLEventReader(is);

        assertSame(inner, result);
    }


    @Test
    void createXMLEventReader_inputStreamWithEncoding_delegated() throws XMLStreamException
    {
        XMLEventReader inner = mock(XMLEventReader.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        when(delegate.createXMLEventReader(is, "UTF-8")).thenReturn(inner);

        XMLEventReader result = factory.createXMLEventReader(is, "UTF-8");

        assertSame(inner, result);
    }


    @Test
    void createXMLEventReader_reader_delegated() throws XMLStreamException
    {
        XMLEventReader inner = mock(XMLEventReader.class);
        Reader r = new StringReader("");
        when(delegate.createXMLEventReader(r)).thenReturn(inner);

        XMLEventReader result = factory.createXMLEventReader(r);

        assertSame(inner, result);
    }


    @Test
    void createXMLEventReader_systemIdAndInputStream_delegated() throws XMLStreamException
    {
        XMLEventReader inner = mock(XMLEventReader.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        when(delegate.createXMLEventReader("sid", is)).thenReturn(inner);

        XMLEventReader result = factory.createXMLEventReader("sid", is);

        assertSame(inner, result);
    }


    @Test
    void createXMLEventReader_systemIdAndReader_delegated() throws XMLStreamException
    {
        XMLEventReader inner = mock(XMLEventReader.class);
        Reader r = new StringReader("");
        when(delegate.createXMLEventReader("sid", r)).thenReturn(inner);

        XMLEventReader result = factory.createXMLEventReader("sid", r);

        assertSame(inner, result);
    }


    @Test
    void createXMLEventReader_source_delegated() throws XMLStreamException
    {
        XMLEventReader inner = mock(XMLEventReader.class);
        Source src = new StreamSource(new ByteArrayInputStream(new byte[0]));
        when(delegate.createXMLEventReader(src)).thenReturn(inner);

        XMLEventReader result = factory.createXMLEventReader(src);

        assertSame(inner, result);
    }


    @Test
    void createXMLEventReader_fromStreamReader_delegated() throws XMLStreamException
    {
        XMLEventReader inner = mock(XMLEventReader.class);
        XMLStreamReader sr = mock(XMLStreamReader.class);
        when(delegate.createXMLEventReader(sr)).thenReturn(inner);

        XMLEventReader result = factory.createXMLEventReader(sr);

        assertSame(inner, result);
    }

    // ==================== Filtered readers (delegation) ====================


    @Test
    void createFilteredReader_event_delegated() throws XMLStreamException
    {
        XMLEventReader source = mock(XMLEventReader.class);
        XMLEventReader filtered = mock(XMLEventReader.class);
        EventFilter f = mock(EventFilter.class);
        when(delegate.createFilteredReader(source, f)).thenReturn(filtered);

        XMLEventReader result = factory.createFilteredReader(source, f);

        assertSame(filtered, result);
    }


    @Test
    void createFilteredReader_stream_delegated() throws XMLStreamException
    {
        XMLStreamReader source = mock(XMLStreamReader.class);
        XMLStreamReader filtered = mock(XMLStreamReader.class);
        StreamFilter f = mock(StreamFilter.class);
        when(delegate.createFilteredReader(source, f)).thenReturn(filtered);

        XMLStreamReader result = factory.createFilteredReader(source, f);

        assertSame(filtered, result);
    }

    // ==================== Resolver / Reporter / property accessors ====================


    @Test
    void xmlResolver_getterAndSetter_delegate()
    {
        XMLResolver resolver = mock(XMLResolver.class);
        when(delegate.getXMLResolver()).thenReturn(resolver);

        factory.setXMLResolver(resolver);
        XMLResolver got = factory.getXMLResolver();

        assertSame(resolver, got);
        verify(delegate, times(1)).setXMLResolver(resolver);
        verify(delegate, times(1)).getXMLResolver();
    }


    @Test
    void xmlReporter_getterAndSetter_delegate()
    {
        XMLReporter reporter = mock(XMLReporter.class);
        when(delegate.getXMLReporter()).thenReturn(reporter);

        factory.setXMLReporter(reporter);
        XMLReporter got = factory.getXMLReporter();

        assertSame(reporter, got);
        verify(delegate, times(1)).setXMLReporter(reporter);
        verify(delegate, times(1)).getXMLReporter();
    }


    @Test
    void property_getSetAndIsSupported_delegate()
    {
        Object value = new Object();
        when(delegate.getProperty("p")).thenReturn(value);
        when(delegate.isPropertySupported("p")).thenReturn(true);

        factory.setProperty("p", value);
        Object got = factory.getProperty("p");
        boolean supported = factory.isPropertySupported("p");

        assertSame(value, got);
        assertTrue(supported);
        verify(delegate, times(1)).setProperty("p", value);
    }


    @Test
    void eventAllocator_getterAndSetter_delegate()
    {
        XMLEventAllocator allocator = mock(XMLEventAllocator.class);
        when(delegate.getEventAllocator()).thenReturn(allocator);

        factory.setEventAllocator(allocator);
        XMLEventAllocator got = factory.getEventAllocator();

        assertSame(allocator, got);
        verify(delegate, times(1)).setEventAllocator(allocator);
        verify(delegate, times(1)).getEventAllocator();
    }

    // ==================== Integration via parser ====================


    @Test
    void endToEnd_throughDefineXmlParser_stripsDefAttributeNamespace() throws Exception
    {
        // Mirrors DefineXml10NamespaceTest but goes through the factory we are unit-testing here.
        // Two distinct namespaces are present on the same logical attribute name across versions.
        String xml = """
                <ODM xmlns="http://www.cdisc.org/ns/odm/v1.2"
                     xmlns:def="http://www.cdisc.org/ns/def/v1.0"
                     FileOID="O" FileType="Snapshot" ODMVersion="1.2">
                  <Study OID="S">
                    <GlobalVariables>
                      <StudyName>n</StudyName><StudyDescription>d</StudyDescription>
                      <ProtocolName>p</ProtocolName>
                    </GlobalVariables>
                    <MetaDataVersion OID="MDV" Name="N" def:StandardName="CDISC"
                                     def:StandardVersion="3.2" def:DefineVersion="1.0.0" />
                  </Study>
                </ODM>
                """;
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        ODM odm = new DefineXmlParser().parse(is);

        MetaDataVersion mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);
        assertEquals("CDISC", mdv.getStandardName());
        assertEquals("3.2", mdv.getStandardVersion());
        assertEquals("1.0.0", mdv.getDefineVersion());
    }

}
