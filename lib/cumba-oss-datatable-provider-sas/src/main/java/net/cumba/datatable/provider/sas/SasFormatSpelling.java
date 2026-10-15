package net.cumba.datatable.provider.sas;

// OSS-IDENTITY provider-sas: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import org.jspecify.annotations.Nullable;

/**
 * The one place the SAS readers spell a stored display format: the XPT table and library readers
 * (NAMESTR {@code nform} / {@code nfl} / {@code nfd}) and the SAS7BDAT reader (format subheader
 * name, digits, decimals) all hand their raw fields here.
 *
 * <table>
 * <caption>The assembly rule</caption>
 * <tr>
 * <th>stored name</th>
 * <th>width {@code w}</th>
 * <th>decimals {@code d}</th>
 * <th>column</th>
 * <th>result</th>
 * </tr>
 * <tr>
 * <td>non-blank</td>
 * <td>any</td>
 * <td>any</td>
 * <td>numeric</td>
 * <td>name + ({@code w > 0} ? {@code w}) + {@code .} + ({@code d > 0} ? {@code d}), e.g.
 * {@code BEST12.2}, {@code DATE9.}, {@code BEST.}</td>
 * </tr>
 * <tr>
 * <td>holds a {@code .}</td>
 * <td>0</td>
 * <td>0</td>
 * <td>any</td>
 * <td>the name unchanged, e.g. {@code 8.2}, {@code DATE9.} -- another writer stored the whole
 * spelling as the name (tolerance, see below)</td>
 * </tr>
 * <tr>
 * <td>non-blank</td>
 * <td>any</td>
 * <td>any</td>
 * <td>character</td>
 * <td>name + ({@code w > 0} ? {@code w}) + {@code .}, e.g. {@code $CHAR8.} -- no decimals: a
 * character format takes none</td>
 * </tr>
 * <tr>
 * <td>blank</td>
 * <td>&gt; 0</td>
 * <td>any</td>
 * <td>numeric</td>
 * <td>{@code w.} / {@code w.d}, e.g. {@code 8.}, {@code 8.1}</td>
 * </tr>
 * <tr>
 * <td>blank</td>
 * <td>0</td>
 * <td>any</td>
 * <td>any</td>
 * <td>{@code null} -- no format</td>
 * </tr>
 * <tr>
 * <td>blank</td>
 * <td>&gt; 0</td>
 * <td>any</td>
 * <td>character</td>
 * <td>{@code null}</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>Why a blank name with a width is a format.</b> SAS stores its plain number format {@code w.d}
 * ({@code 8.}, {@code 8.1}) with an EMPTY format name and only the width and the decimal count;
 * ReadStat reports such a variable as {@code "8"} / {@code "8.1"}, parso as {@code "8."}, and SAS's
 * {@code PROC CONTENTS} as {@code 8.} / {@code 8.1}. The readers used to drop it, so the format was
 * lost in the column metadata, the display, every export and the rules that read
 * {@code var_format()} (PLAN-sas-bare-wd-format-read). The spelling is SAS's own, {@code 8.}, not
 * {@code F8.}: the SAS writers parse it back to a blank name, so a round trip writes the bytes the
 * file held.
 * </p>
 *
 * <p>
 * A blank name without a width stays "no format" -- a bare {@code .} is not a SAS format, and a
 * width-0 entry with decimals only ({@code .3}, as ReadStat reports a few non-SAS-written files) is
 * not one either (owner ruling BW2, 2026-10-12). A blank name on a character column also stays "no
 * format": SAS stores the character {@code w.} format under the name {@code $}, so a blank name
 * with a width there is not a shape SAS writes. A negative width or decimal count (a damaged
 * header) counts as none.
 * </p>
 *
 * <p>
 * <b>A name that already holds the whole spelling passes through.</b> ReadStat's SAS7BDAT writer
 * (R's haven, pyreadstat) stores a format as its text -- {@code 8.2} or {@code DATE9.} as the name,
 * width and decimals 0 -- a shape SAS never writes; appending the {@code .} made it {@code 8.2.}
 * (PACK G fold, G11 / G12 review). Owner 2026-10-15: <i>"Accepting wrong input from other sources
 * is ok. Expecting it wrong is not ok. And writing it wrong is even worse."</i> So a non-blank name
 * holding a {@code .} with width and decimals 0 is spelled as stored, the same tolerance
 * {@code cumba-parso}'s {@code ColumnFormat.toString} applies; every SAS-written file (its names
 * never hold a {@code .}) reads exactly as before, and the writers still spell SAS's own form.
 * </p>
 */
public final class SasFormatSpelling
{

    private SasFormatSpelling()
    {
    }


    /**
     * Spell a stored SAS display format.
     *
     * @param aName
     *            the stored format name; {@code null} and blank both mean "no name". Surrounding
     *            blanks are dropped.
     * @param aWidth
     *            the stored width ({@code w}); {@code <= 0} means none.
     * @param aDecimals
     *            the stored decimal count ({@code d}); {@code <= 0} means none.
     * @param aNumeric
     *            whether the variable is numeric; decimals and the bare {@code w.d} format exist
     *            only there.
     * @return the format as SAS spells it, or {@code null} when the variable has no display format.
     */
    public static @Nullable String assemble(@Nullable String aName, int aWidth, int aDecimals,
            boolean aNumeric)
    {
        String name = aName == null ? "" : aName.trim();
        if (name.isEmpty() && (!aNumeric || aWidth <= 0))
        {
            return null;
        }
        if (aWidth <= 0 && aDecimals <= 0 && name.indexOf('.') >= 0)
        {
            // another writer stored the whole spelling as the name (ReadStat: "8.2", "DATE9.")
            return name;
        }

        StringBuilder sb = new StringBuilder(name);
        if (aWidth > 0)
        {
            sb.append(aWidth);
        }
        sb.append('.');
        if (aNumeric && aDecimals > 0)
        {
            sb.append(aDecimals);
        }
        return sb.toString();
    }
}
