package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * One run property of a validation report as the source wrote it: the key (the report's own
 * spelling, e.g. {@code CT_Version}), a display label and the value as text. Carries every
 * conformance / summary entry a format reports that {@link ValidationRunInfo} has no typed field
 * for, in the order the source lists them.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ReportProperty
{

    /** The source's own key, verbatim. */
    private final String key;

    /** A display label; the key when the source has none. */
    private final @Nullable String label;

    /** The value as text; {@code null} when the source states the key without a value. */
    private final @Nullable String value;

    /**
     * A property whose label is its key.
     *
     * @param aKey
     *            the key.
     * @param aValue
     *            the value, may be {@code null}.
     * @return the property.
     */
    public static ReportProperty of(String aKey, @Nullable String aValue)
    {
        return ReportProperty.builder().key(aKey).label(aKey).value(aValue).build();
    }


    /**
     * Returns the label, falling back to the key.
     *
     * @return the label, never {@code null}.
     */
    public String getLabel()
    {
        return label != null ? label : key;
    }
}
