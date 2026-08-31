package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

// Decode
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Decode
{

    // Populated by Jackson XML binding; absent in the source XML => null.
    @JacksonXmlProperty(localName = "TranslatedText")
    @JacksonXmlElementWrapper(useWrapping = false)
    private @Nullable List<TranslatedText> translatedTexts;
}
