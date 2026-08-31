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
 * The kind of a {@link ValidationFinding} — how it was produced, not what it classifies.
 * <ul>
 * <li>{@link #RULE_VIOLATION} — a rule fired and reported a concrete violation. Covers both
 * engine-generated and externally-parsed findings (Pinnacle 21, CDISC CORE JSON reports).</li>
 * <li>{@link #ENGINE_ERROR} — the rule engine failed to execute a rule (e.g. unresolved operation,
 * internal exception). The finding carries the diagnostic message.</li>
 * <li>{@link #LIBRARY_WARNING} — a library-level warning (e.g. mismatched standard between caller
 * and study metadata). Not attached to a specific dataset rule.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Getter
public enum FindingKind
{

    RULE_VIOLATION("RuleViolation"),

    ENGINE_ERROR("EngineError"),

    LIBRARY_WARNING("LibraryWarning");

    @JsonValue
    private final String jsonValue;

    private static final Map<String, FindingKind> LOOKUP = Stream.of(values())
            .collect(Collectors.toMap(FindingKind::getJsonValue, Function.identity()));

    @JsonCreator
    public static @Nullable FindingKind fromJson(@Nullable String value)
    {
        return LOOKUP.get(value);
    }

}
