package net.cumba.datatable;

import java.text.MessageFormat;
import java.util.Locale;

import net.cumba.datatable.help.CDT;

/**
 * A helper class that can be used to provide useful exception messages.
 */
public class ExMsgs
{

    private ExMsgs()
    {
        throw new UnsupportedOperationException("utility class");
    }


    public static String indexOutOfBounds(String aName, long aValue, long alowerLimit,
            long aUpperLimit)
    {
        if (CDT.isBlankOrNull(aName))
        {
            aName = "value";
        }
        String nameUc1 = aName.substring(0, 1).toUpperCase(Locale.ROOT) + aName.substring(1);
        return MessageFormat.format("{0} {2} is out of range {3} <= {1} < {4}", nameUc1, aName,
                aValue, alowerLimit, aUpperLimit);
    }


    public static String indexOutOfBounds(String aName, int aValue, int alowerLimit,
            int aUpperLimit)
    {
        if (CDT.isBlankOrNull(aName))
        {
            aName = "value";
        }
        String nameUc1 = aName.substring(0, 1).toUpperCase(Locale.ROOT) + aName.substring(1);
        return MessageFormat.format("{0} {2} is out of range {3} <= {1} < {4}", nameUc1, aName,
                aValue, alowerLimit, aUpperLimit);
    }
}
