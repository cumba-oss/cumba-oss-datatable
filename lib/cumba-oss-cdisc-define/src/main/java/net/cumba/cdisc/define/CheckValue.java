package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

// CheckValue
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckValue
{

    // Populated by Jackson XML binding; absent in the source XML => null.
    @JacksonXmlText
    private @Nullable String value;
}
