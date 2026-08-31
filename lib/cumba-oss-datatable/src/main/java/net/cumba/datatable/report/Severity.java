package net.cumba.datatable.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Severity of a {@link ValidationFinding}. Providers (engine, Pinnacle 21, CORE JSON) produce
 * severity strings with varying casing; {@link #fromJson(String)} maps them case-insensitively and
 * <b>throws</b> for anything else — there is no synonym mapping and no silent fallback.
 *
 * <h2>&#9873; The ladder</h2>
 * <p>
 * {@link #REJECT} &gt; {@link #ERROR} &gt; {@link #WARNING} &gt; {@link #INFO} is a <b>totally
 * ordered</b> ladder, strictest first, and <b>declaration order is that order</b> — so no
 * comparator can disagree with it. It is what a rule's per-level {@code Check} evaluation order and
 * the run's severity threshold both consume. {@link #NOTICE} sits outside the ladder: it is a
 * report-only kind, authored by no rule, and is deliberately declared last.
 * </p>
 *
 * <h2>&#9873;&#9873; Why an unknown value is an error, not a {@code WARNING}</h2>
 * <p>
 * This method used to end {@code return s != null ? s : WARNING}, and the behaviour was
 * <em>documented</em> rather than accidental. That silently downgraded every unrecognised spelling
 * — {@code "Reject"} before this enum carried it, but equally {@code "error "} with a stray space,
 * or a typo in a hand-edited rule. Rule {@code Severity} is authored data, so a value this enum
 * does not know is an authoring defect and must surface as one. Plan C ruling 1.
 * </p>
 */
@RequiredArgsConstructor
@Getter
public enum Severity
{

    REJECT("Reject"),

    ERROR("Error"),

    WARNING("Warning"),

    INFO("Info"),

    NOTICE("Notice");

    @JsonValue
    private final String jsonValue;

    private static final Map<String, Severity> LOOKUP = Stream.of(values()).collect(
            Collectors.toMap(s -> s.jsonValue.toLowerCase(Locale.ROOT), Function.identity()));

    /**
     * Lenient case-insensitive lookup: {@code null} for a null <b>or unrecognised</b> value, and
     * never throws.
     *
     * <p>
     * &#9873; This is the <b>authoring</b> door, and it exists so that a rule carrying an invalid
     * {@code Severity} fails as a clean per-rule <em>load error</em> — the loader keeps the raw
     * string and {@code RulePackageLoader.validateEnumFields} reports it with the rule's id —
     * rather than as a Jackson parse exception with no rule context. It is the same contract
     * {@code Sensitivity.fromJson} and {@code Executability.fromJson} already use. The strict
     * {@link #fromJson} stays the {@code @JsonCreator} for <em>report</em> deserialisation, where
     * there is no second validation pass and a silent downgrade would be invisible.
     * </p>
     *
     * @param value
     *            the authored spelling, in any casing
     * @return the matching constant, or {@code null} when absent or unrecognised
     */
    public static @Nullable Severity parseOrNull(@Nullable String value)
    {
        return value == null ? null : LOOKUP.get(value.toLowerCase(Locale.ROOT));
    }


    /**
     * Case-insensitive lookup. Returns {@code null} for null input — "missing" is a legitimate
     * state that callers resolve to their own default — and <b>throws</b> for an unrecognised
     * non-null value.
     *
     * @param value
     *            the authored or provider-supplied spelling, in any casing
     * @return the matching constant, or {@code null} when {@code value} is {@code null}
     * @throws IllegalArgumentException
     *             if {@code value} is non-null and matches no constant
     */
    @JsonCreator
    public static @Nullable Severity fromJson(@Nullable String value)
    {
        if (value == null)
        {
            return null;
        }
        Severity s = LOOKUP.get(value.toLowerCase(Locale.ROOT));
        if (s == null)
        {
            // values() renders the legal spellings in LADDER order (declaration order IS the
            // ladder) — LOOKUP is a HashMap, whose iteration order is arbitrary.
            throw new IllegalArgumentException(
                    "unknown severity: \""
                            + value + "\" (expected one of " + Stream.of(values())
                                    .map(Severity::getJsonValue).collect(Collectors.joining(", "))
                            + ")");
        }
        return s;
    }

}
