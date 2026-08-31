package net.cumba.cdisc.define;

import java.util.List;

/**
 * Shared state handed to each {@link ConversionStep}: the progress log and fidelity-warning sinks,
 * the user-selected options that affect transforms, and a document-scoped {@link OidMinter} for
 * synthesising collision-free OIDs.
 */
final class ConversionContext
{

    private final List<String> log;

    private final List<String> warnings;

    private final boolean keepLegacyStandardAttributes;

    private final String context;

    private final OidMinter oids;

    ConversionContext(List<String> log, List<String> warnings, boolean keepLegacyStandardAttributes,
            String context, OidMinter oids)
    {
        this.log = log;
        this.warnings = warnings;
        this.keepLegacyStandardAttributes = keepLegacyStandardAttributes;
        this.context = context;
        this.oids = oids;
    }


    void log(String message)
    {
        log.add(message);
    }


    void warn(String message)
    {
        warnings.add(message);
    }


    boolean keepLegacyStandardAttributes()
    {
        return keepLegacyStandardAttributes;
    }


    /** The value to write into the v2.1 {@code def:Context} attribute (Submission/Other). */
    String context()
    {
        return context;
    }


    OidMinter oids()
    {
        return oids;
    }

}
