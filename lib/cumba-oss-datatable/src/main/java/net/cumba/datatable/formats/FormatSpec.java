package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-temporal-classifier: byte-identical in cumba-datatable and
// cumba-oss-datatable. Edit in cumba-datatable, then copy; check_oss_identity.py fails on any
// divergence.

import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@CustomLog
@Getter
@Builder
@AllArgsConstructor
@ToString
public class FormatSpec
{

    private static final Pattern formatNamePattern = Pattern.compile(
            "(\\$?(([a-z_][a-z_0-9]*)?[a-z_])?)(\\d*)\\.(\\d*)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static @Nullable FormatSpec getFormatSpec(@Nullable String aFullName)
    {
        if (aFullName == null)
        {
            // null is null
            return null;
        }

        if (aFullName.indexOf('.') < 0)
        {
            // add a dot if not contained in format name
            aFullName = aFullName + ".";
        }

        Matcher m = formatNamePattern.matcher(aFullName);
        if (!m.matches())
        {
            return null;
        }

        String widthStr = m.group(4);
        String decimalsStr = m.group(5);

        String baseName = m.group(1);
        if (baseName == null)
        {
            baseName = "";
        }
        else
        {
            baseName = baseName.toUpperCase(Locale.ROOT);
        }

        int width = 0;
        int decimals = 0;
        try
        {
            width = Integer.parseInt(widthStr);
        }
        catch (Exception _)
        {
            LOGGER.log(Level.TRACE, "invalid format spec width: {0}", aFullName);
        }

        try
        {
            decimals = Integer.parseInt(decimalsStr);
        }
        catch (Exception _)
        {
            LOGGER.log(Level.TRACE, "invalid format spec decimals: {0}", aFullName);
        }
        return FormatSpec.builder().name(baseName).width(width).decimals(decimals).build();
    }


    /**
     * Whether a display format is a SAS character format: its name, leading white space ignored,
     * starts with {@code $} ({@code $CHAR20.}, {@code $8.}, {@code $SEX.}). A format without the
     * {@code $} is a numeric format in SAS's own spelling, whatever it names.
     *
     * @param aFormat
     *            the display format, may be null.
     * @return true for a {@code $} format; false for null, blank or any other name.
     */
    public static boolean isCharacterFormat(@Nullable String aFormat)
    {
        return aFormat != null && aFormat.strip().startsWith("$");
    }


    /**
     * Whether a display format fits the storage type of the column it is written with: a {@code $}
     * format on a character column, any other format on a numeric one; no format (null or blank)
     * fits every column. This is SAS's own type rule, which applies neither a numeric format such
     * as {@code DATE9.} to a character variable nor {@code $CHAR8.} to a numeric one.
     *
     * @param aFormat
     *            the display format, may be null.
     * @param aCharacterColumn
     *            true when the column is stored as character, false when numeric.
     * @return true when the format's kind matches the column's storage, or there is no format.
     */
    public static boolean fitsStorage(@Nullable String aFormat, boolean aCharacterColumn)
    {
        if (aFormat == null || aFormat.isBlank())
        {
            return true;
        }
        return isCharacterFormat(aFormat) == aCharacterColumn;
    }

    private final String name;

    private final int width;

    private final int decimals;
}
