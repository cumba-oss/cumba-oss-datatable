package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

// Description
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Description
{

    // Populated by Jackson XML binding; absent in the source XML => null.
    @JacksonXmlText
    private @Nullable String value;

    @JacksonXmlProperty(isAttribute = true, localName = "lang")
    private @Nullable String lang;

    @JacksonXmlProperty(localName = "TranslatedText")
    @JacksonXmlElementWrapper(useWrapping = false)
    private @Nullable List<TranslatedText> translatedTexts;
}
