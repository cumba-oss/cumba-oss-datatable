package net.cumba.cdisc.define;

/**
 * Thrown when a Define-XML version conversion cannot be performed: the input version cannot be
 * determined and was not supplied, a downgrade was requested, no conversion path exists, or a
 * fidelity warning was raised while strict ({@code failOnWarning}) mode is active.
 */
public class DefineConversionException extends RuntimeException
{

    private static final long serialVersionUID = 1L;

    public DefineConversionException(String message)
    {
        super(message);
    }


    public DefineConversionException(String message, Throwable cause)
    {
        super(message, cause);
    }

}
