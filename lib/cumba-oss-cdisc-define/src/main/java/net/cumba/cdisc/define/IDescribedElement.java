package net.cumba.cdisc.define;

import org.jspecify.annotations.Nullable;

/**
 * A described element is an element that contains a {@link Description} that can be retrieved by
 * {@link #getDescription()}.
 */
public interface IDescribedElement
{

    /**
     * Returns the description (if available) or {@code null} if no description is available.
     *
     * @return the description (if available) or null if no description is available.
     */
    @Nullable
    Description getDescription();
}
