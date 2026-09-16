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
     * An all-blank format slice of non-zero length right-trims to whitespace; assembling it anyway
     * produced a bare {@code "."} (or {@code "8."}), which is not a format SAS has. The guard is
     * {@code !CDT.isBlankOrNull(dispFmt)}, not {@code dispFmt != null} - the XPT twin already
     * guarded it this way. Unguarded on both sides until now: reverting the guard to a null check
     * passed every existing test.
     */
    @Test
    void aBlankFormatYieldsNoDisplayFormatNotABareDot()
    {
        assertNull(formatOf(VariableType.NUMERIC, 8, "   ", (short) 8, null),
                "an all-blank stored format must yield NO display format, never a bare '.'");
        assertNull(formatOf(VariableType.CHARACTER, 10, "", null, null));
        // and a real format still assembles, so the null above is not a broken helper path
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
