package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

// TranslatedText
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranslatedText
{

    // Populated by Jackson XML binding; absent in the source XML => null.
    @JacksonXmlText
    private @Nullable String value;

    @JacksonXmlProperty(isAttribute = true, localName = "lang")
    private @Nullable String lang;
}
