package net.cumba.datatable.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Coarse highlighting scope of a {@link ValidationFinding} — determines where the UI should draw
 * attention for the finding:
 * <ul>
 * <li>{@link #DATASET} — the whole dataset (shown as a dataset-level annotation, e.g. banner or
 * tree-node indicator). No row or column is highlighted.</li>
 * <li>{@link #VARIABLE} — one or more columns (variable-level metadata issue). No specific row is
 * highlighted.</li>
 * <li>{@link #RECORD} — one or more specific rows / cells (value-level issue).</li>
 * </ul>
 * <p>
 * Scope describes <em>what the UI should highlight</em>. For an engine finding it is the projection
 * of the rule's evaluation domain ({@code {}} ⇒ DATASET, {@code {VAR}} ⇒ VARIABLE, a row-bearing
 * domain ⇒ RECORD); providers that know no domain (e.g. Pinnacle 21, external CORE reports)
 * populate it from the shape of the finding.
 * </p>
 */
@RequiredArgsConstructor
@Getter
public enum FindingScope
{

    DATASET("Dataset"),

    VARIABLE("Variable"),

    RECORD("Record");

    @JsonValue
    private final String jsonValue;

    private static final Map<String, FindingScope> LOOKUP = Stream.of(values())
            .collect(Collectors.toMap(FindingScope::getJsonValue, Function.identity()));

    @JsonCreator
    public static @Nullable FindingScope fromJson(@Nullable String value)
    {
        return LOOKUP.get(value);
    }

}
