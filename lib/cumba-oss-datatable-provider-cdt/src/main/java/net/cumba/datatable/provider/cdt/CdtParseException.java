package net.cumba.datatable.provider.cdt;

/**
 * Thrown by {@link CdtParser} when a {@code .cdt} file cannot be parsed.
 */
public class CdtParseException extends RuntimeException
{

    private static final long serialVersionUID = 1L;

    public CdtParseException(String aMessage)
    {
        super(aMessage);
    }


    public CdtParseException(String aMessage, Throwable aCause)
    {
        super(aMessage, aCause);
    }
}
