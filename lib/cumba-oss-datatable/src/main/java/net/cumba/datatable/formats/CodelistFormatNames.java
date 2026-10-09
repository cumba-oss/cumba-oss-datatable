package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-format-names: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

/**
 * The naming rule of a codelist format: the catalog key(s) a codelist (Define-XML {@code CodeList},
 * metadata {@code ICodeList}) is registered under, so that the display format of a column bound to
 * that codelist ({@code "$" + Name + "."} for a text codelist, {@code Name + "."} otherwise) finds
 * it.
 * <p>
 * Whether the name carries the {@code $} is decided by the <b>codelist's</b> data type, on the
 * column side and on the catalog side alike. A codelist {@code Name} may contain dots
 * ({@code QS.QSTESTCD}, {@code SUPPAE.QNAM}, a name ending in a period); the name is used as it is,
 * the lookup strips only the display format's trailing dot.
 * </p>
 * <p>
 * Define-XML 2.x also carries an optional {@code SASFormatName}; a usable one
 * ({@link #isUsableSasFormatName}) is a further catalog key ({@link #codelistAliases}), never the
 * primary one and never the column's display format.
 * </p>
 */
@CustomLog
public final class CodelistFormatNames
{

    /** The longest SAS format name, the {@code $} included. */
    static final int MAX_SAS_FORMAT_NAME_LENGTH = 32;

    /**
     * A SAS format name: an optional {@code $}, then a letter or underscore, then letters, digits
     * or underscores. Trailing digits ARE accepted: real defines carry {@code $FMT1} …
     * {@code $FMT147}, though SAS itself would read the digits as a width.
     */
    private static final Pattern SAS_FORMAT_NAME = Pattern.compile("\\$?[A-Za-z_][A-Za-z_0-9]*");

    private CodelistFormatNames()
    {
    }


    /**
     * The primary catalog name of a codelist format: {@code "$" + aName} for a text codelist,
     * {@code aName} otherwise. Idempotent on a leading {@code $}: a name that already carries it (a
     * metadata codelist named {@code $SEX}) is returned unchanged.
     *
     * @param aName
     *            the codelist's name, used as it is (dots and blanks included).
     * @param aText
     *            whether the codelist's data type is text.
     * @return the primary catalog name.
     */
    public static String codelistFormatName(String aName, boolean aText)
    {
        if (aText && !aName.startsWith("$"))
        {
            return "$" + aName;
        }
        return aName;
    }


    /**
     * The further catalog keys of a codelist format, beside its {@linkplain #codelistFormatName
     * primary name}: a {@linkplain #isUsableSasFormatName usable} {@code SASFormatName} that
     * differs (ignoring case) from the primary name. An unusable one is logged (INFO) and ignored.
     *
     * @param aName
     *            the codelist's name.
     * @param aSasFormatName
     *            the codelist's {@code SASFormatName}, {@code null} or blank if it has none.
     * @param aText
     *            whether the codelist's data type is text.
     * @return the alias keys, never {@code null}, possibly empty.
     */
    public static List<String> codelistAliases(String aName, @Nullable String aSasFormatName,
            boolean aText)
    {
        if (aSasFormatName == null || aSasFormatName.isBlank())
        {
            return List.of();
        }
        if (!isUsableSasFormatName(aSasFormatName, aText))
        {
            LOGGER.log(Level.INFO,
                    "SASFormatName \"{0}\" of codelist \"{1}\" ({2}) is not usable as a format name; "
                            + "the codelist is found under its name only",
                    aSasFormatName, aName, aText ? "text" : "not text");
            return List.of();
        }
        String primary = codelistFormatName(aName, aText);
        if (aSasFormatName.toUpperCase(Locale.ROOT).equals(primary.toUpperCase(Locale.ROOT)))
        {
            return List.of();
        }
        return List.of(aSasFormatName);
    }


    /**
     * Whether a {@code SASFormatName} is usable as a catalog key: a letter or underscore after an
     * optional {@code $}, then letters, digits or underscores; at most
     * {@value #MAX_SAS_FORMAT_NAME_LENGTH} characters, the {@code $} included; and a {@code $}
     * exactly when the codelist is text (an integer codelist carrying {@code $FMT59} is not
     * usable). Trailing digits are accepted.
     *
     * @param aSasFormatName
     *            the {@code SASFormatName}, may be {@code null}.
     * @param aText
     *            whether the codelist's data type is text.
     * @return {@code true} if the name can be registered as a catalog key.
     */
    public static boolean isUsableSasFormatName(@Nullable String aSasFormatName, boolean aText)
    {
        if (aSasFormatName == null || aSasFormatName.length() > MAX_SAS_FORMAT_NAME_LENGTH)
        {
            return false;
        }
        if (aSasFormatName.startsWith("$") != aText)
        {
            return false;
        }
        return SAS_FORMAT_NAME.matcher(aSasFormatName).matches();
    }
}
