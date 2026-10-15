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

    private final String name;

    private final int width;

    private final int decimals;
}
