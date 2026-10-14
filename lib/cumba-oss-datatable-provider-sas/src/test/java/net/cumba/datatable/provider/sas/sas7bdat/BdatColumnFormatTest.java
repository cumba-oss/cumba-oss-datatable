package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.ColumnName;
import net.cumba.sasutils.bdat.FormatAndLabelSubHeader;
import net.cumba.sasutils.bdat.VariableBdat;
import org.junit.jupiter.api.Test;

/**
 * Format assembly in {@link BdatTableProvider#addColumn}, driven through hand-built
 * {@link VariableBdat}s - the shapes a real header can carry but no committed fixture does.
 */
class BdatColumnFormatTest
{

    /**
     * SAS stores its plain number format {@code w.d} ({@code 8.}, {@code 8.2}) with an EMPTY format
     * name and only a width and a decimal count; ReadStat reports such a variable as {@code "8"} /
     * {@code "8.1"}. This test used to assert that a blank name with a width yields no format --
     * "not a format SAS has" -- and so pinned the reader dropping a real format
     * (PLAN-sas-bare-wd-format-read). A blank name is no format only WITHOUT a width (a bare
     * {@code "."} stays impossible), and on a character column, where SAS stores its {@code w.}
     * format under the name {@code $}.
     */
    @Test
    void aBlankNameIsNoFormatOnlyWithoutAWidth()
    {
        assertEquals("8.", formatOf(VariableType.NUMERIC, 8, "   ", (short) 8, null),
                "a blank name with a width is SAS's w. format");
        assertEquals("8.2", formatOf(VariableType.NUMERIC, 8, "", (short) 8, (short) 2));
        assertNull(formatOf(VariableType.NUMERIC, 8, "   ", (short) 0, null),
                "a blank name without a width is no format, never a bare '.'");
        assertNull(formatOf(VariableType.CHARACTER, 10, "", null, null));
        assertNull(formatOf(VariableType.CHARACTER, 10, "  ", (short) 10, null));
        // and a real format still assembles, so the nulls above are not a broken helper path
        assertEquals("DATE9.", formatOf(VariableType.NUMERIC, 8, "DATE", (short) 9, null));
    }


    private static String formatOf(VariableType aType, Integer aLength, String aFormat,
            Short aDigits, Short aDecimals)
    {
        return columnOf(aType, aLength, aFormat, aDigits, aDecimals).getDisplayFormat();
    }


    private static DataTableColumnMeta columnOf(VariableType aType, Integer aLength, String aFormat,
            Short aDigits, Short aDecimals)
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///tmp/x.sas7bdat"));
        new BdatTableProvider().addColumn(support, var(aType, aLength, aFormat, aDigits, aDecimals),
                0, URI.create("file:///tmp/x.sas7bdat"));
        return support.getTableMeta().build().getColumn(0);
    }


    private static VariableBdat var(VariableType aType, Integer aLength, String aFormat,
            Short aDigits, Short aDecimals)
    {
        FormatAndLabelSubHeader flsh = new FormatAndLabelSubHeader()
        {

            @Override
            public String getFormat()
            {
                return aFormat;
            }


            @Override
            public String getLabel()
            {
                return null;
            }
        };
        flsh.formatDigits = aDigits;
        flsh.formatDecimals = aDecimals;
        return new VariableBdat(flsh, new ColumnName(), null)
        {

            @Override
            public String getName()
            {
                return "COL";
            }


            @Override
            public VariableType getType()
            {
                return aType;
            }


            @Override
            public Integer getLength()
            {
                return aLength;
            }


            @Override
            public String getLabel()
            {
                return null;
            }
        };
    }

}
