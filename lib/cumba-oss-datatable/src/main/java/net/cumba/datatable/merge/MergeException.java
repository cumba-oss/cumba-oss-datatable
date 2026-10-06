package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.Serial;

/**
 * The root of the datatable-level merge exception family: a merge that cannot be built as
 * specified. Every subclass is a distinct, caller-mappable failure (the engine maps each back to
 * its own rule-ERROR classification; the data browser reports the message), so a caller catches by
 * type, never by message.
 */
public class MergeException extends RuntimeException
{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param aMessage
     *            the message.
     */
    public MergeException(String aMessage)
    {
        super(aMessage);
    }


    /**
     * @param aMessage
     *            the message.
     * @param aCause
     *            what made the merge impossible.
     */
    public MergeException(String aMessage, Throwable aCause)
    {
        super(aMessage, aCause);
    }
}
