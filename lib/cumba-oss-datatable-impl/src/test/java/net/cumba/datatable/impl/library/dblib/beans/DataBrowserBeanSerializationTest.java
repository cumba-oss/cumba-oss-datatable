package net.cumba.datatable.impl.library.dblib.beans;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataBrowserBeanSerializationTest
{

    private ObjectMapper mapper;

    @BeforeEach
    void setUp()
    {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // --- DataBrowserSourceBean ---


    @Test
    void sourceBean_roundTrip() throws Exception
    {
        DataBrowserSourceBean original = DataBrowserSourceBean.builder().uri("file:///data/dm.csv")
                .name("DM").label("Demographics").build();

        String json = mapper.writeValueAsString(original);
        DataBrowserSourceBean result = mapper.readValue(json, DataBrowserSourceBean.class);

        assertEquals("file:///data/dm.csv", result.getUri());
        assertEquals("DM", result.getName());
        assertEquals("Demographics", result.getLabel());
    }


    @Test
    void sourceBean_minimalFields() throws Exception
    {
        DataBrowserSourceBean original = DataBrowserSourceBean.builder().uri("file:///data/")
                .build();

        String json = mapper.writeValueAsString(original);
        DataBrowserSourceBean result = mapper.readValue(json, DataBrowserSourceBean.class);

        assertEquals("file:///data/", result.getUri());
        assertNull(result.getName());
        assertNull(result.getLabel());
    }

    // --- DataBrowserValueFormatBean ---


    @Test
    void valueFormatBean_roundTrip() throws Exception
    {
        Map<String, String> valueMap = new LinkedHashMap<>();
        valueMap.put("M", "Male");
        valueMap.put("F", "Female");

        DataBrowserValueFormatBean original = DataBrowserValueFormatBean.builder().name("SEX")
                .label("Sex Format").valueMap(valueMap).build();

        String json = mapper.writeValueAsString(original);
        DataBrowserValueFormatBean result = mapper.readValue(json,
                DataBrowserValueFormatBean.class);

        assertEquals("SEX", result.getName());
        assertEquals("Sex Format", result.getLabel());
        assertEquals(2, result.getValueMap().size());
        assertEquals("Male", result.getValueMap().get("M"));
        assertEquals("Female", result.getValueMap().get("F"));
    }

    // --- DataBrowserMemberMetaBean ---


    @Test
    void memberMetaBean_roundTrip() throws Exception
    {
        Map<String, String> attrs = Map.of("domain", "DM", "purpose", "Tabulation");

        DataBrowserMemberMetaBean original = DataBrowserMemberMetaBean.builder().uri("dm.sas7bdat")
                .label("Demographics").attributes(attrs).build();

        String json = mapper.writeValueAsString(original);
        DataBrowserMemberMetaBean result = mapper.readValue(json, DataBrowserMemberMetaBean.class);

        assertEquals("dm.sas7bdat", result.getUri());
        assertEquals("Demographics", result.getLabel());
        assertNotNull(result.getAttributes());
        assertEquals("DM", result.getAttributes().get("domain"));
    }


    @Test
    void memberMetaBean_minimalFields() throws Exception
    {
        DataBrowserMemberMetaBean original = DataBrowserMemberMetaBean.builder().uri("dm.csv")
                .build();

        String json = mapper.writeValueAsString(original);
        DataBrowserMemberMetaBean result = mapper.readValue(json, DataBrowserMemberMetaBean.class);

        assertEquals("dm.csv", result.getUri());
        assertNull(result.getLabel());
        assertNull(result.getAttributes());
    }

    // --- DataBrowserColumnMetaBean ---


    @Test
    void columnMetaBean_roundTrip() throws Exception
    {
        Map<String, String> attrs = Map.of("origin", "CRF");

        DataBrowserColumnMetaBean original = DataBrowserColumnMetaBean.builder().uri("dm.sas7bdat")
                .name("AGE").label("Age in Years").type("DOUBLE").format("8.").key(1)
                .attributes(attrs).build();

        String json = mapper.writeValueAsString(original);
        DataBrowserColumnMetaBean result = mapper.readValue(json, DataBrowserColumnMetaBean.class);

        assertEquals("dm.sas7bdat", result.getUri());
        assertEquals("AGE", result.getName());
        assertEquals("Age in Years", result.getLabel());
        assertEquals("DOUBLE", result.getType());
        assertEquals("8.", result.getFormat());
        assertEquals(1, result.getKey());
        assertEquals("CRF", result.getAttributes().get("origin"));
    }


    @Test
    void columnMetaBean_minimalFields() throws Exception
    {
        DataBrowserColumnMetaBean original = DataBrowserColumnMetaBean.builder().uri("dm.csv")
                .name("SUBJID").build();

        String json = mapper.writeValueAsString(original);
        DataBrowserColumnMetaBean result = mapper.readValue(json, DataBrowserColumnMetaBean.class);

        assertEquals("dm.csv", result.getUri());
        assertEquals("SUBJID", result.getName());
        assertNull(result.getLabel());
        assertNull(result.getType());
    }

    // --- DataBrowserLibraryBean ---


    @Test
    void libraryBean_roundTrip_allFields() throws Exception
    {
        DataBrowserSourceBean source = DataBrowserSourceBean.builder().uri("file:///data/dm.csv")
                .name("DM").build();

        DataBrowserValueFormatBean format = DataBrowserValueFormatBean.builder().name("SEX")
                .valueMap(Map.of("M", "Male")).build();

        DataBrowserMemberMetaBean memberMeta = DataBrowserMemberMetaBean.builder().uri("dm.csv")
                .label("Demographics").build();

        DataBrowserColumnMetaBean columnMeta = DataBrowserColumnMetaBean.builder().uri("dm.csv")
                .name("AGE").label("Age").build();

        DataBrowserLibraryBean original = DataBrowserLibraryBean.builder().name("TestLib")
                .label("Test Library").sources(new DataBrowserSourceBean[]
                {
                        source
                }).formatCatalogUris(new String[]
                {
                        "file:///formats.sas7bcat"
                }).formats(new DataBrowserValueFormatBean[]
                {
                        format
                }).memberMetaTableUris(new String[]
                {
                        "file:///meta.csv"
                }).columnMetaTableUris(new String[]
                {
                        "file:///colmeta.csv"
                }).memberMeta(new DataBrowserMemberMetaBean[]
                {
                        memberMeta
                }).columnMeta(new DataBrowserColumnMetaBean[]
                {
                        columnMeta
                }).attributes(Map.of("study", "ABC-123", "sponsor", "Acme")).build();

        String json = mapper.writeValueAsString(original);
        DataBrowserLibraryBean result = mapper.readValue(json, DataBrowserLibraryBean.class);

        assertEquals("TestLib", result.getName());
        assertEquals("Test Library", result.getLabel());
        assertNotNull(result.getSources());
        assertEquals(1, result.getSources().length);
        assertEquals("DM", result.getSources()[0].getName());
        assertArrayEquals(new String[]
        {
                "file:///formats.sas7bcat"
        }, result.getFormatCatalogUris());
        assertNotNull(result.getFormats());
        assertEquals(1, result.getFormats().length);
        assertEquals("SEX", result.getFormats()[0].getName());
        assertArrayEquals(new String[]
        {
                "file:///meta.csv"
        }, result.getMemberMetaTableUris());
        assertArrayEquals(new String[]
        {
                "file:///colmeta.csv"
        }, result.getColumnMetaTableUris());
        assertNotNull(result.getMemberMeta());
        assertEquals(1, result.getMemberMeta().length);
        assertNotNull(result.getColumnMeta());
        assertEquals(1, result.getColumnMeta().length);
        assertNotNull(result.getAttributes());
        assertEquals("ABC-123", result.getAttributes().get("study"));
        assertEquals("Acme", result.getAttributes().get("sponsor"));
    }


    @Test
    void libraryBean_minimalFields() throws Exception
    {
        DataBrowserLibraryBean original = DataBrowserLibraryBean.builder().name("EMPTY").build();

        String json = mapper.writeValueAsString(original);
        DataBrowserLibraryBean result = mapper.readValue(json, DataBrowserLibraryBean.class);

        assertEquals("EMPTY", result.getName());
        assertNull(result.getLabel());
        assertNull(result.getSources());
        assertNull(result.getFormats());
    }
}
