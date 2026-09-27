package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * ⭐ Owner ruling E5, 2026-09-25 (PLAN-bare-nan-is-mis): <i>"NaN should get mis"</i>, on the XPT
 * reader's Number arm ({@code XptTableProvider.XptTableDataParser.addData2Column}): a NaN is
 * decoded with {@code MissingValue.forNaN} &mdash; a bare NaN is {@code MIS} (it was
 * {@code MIS_UNKNOWN}), a coded payload keeps its constant, an unrecognised payload is
 * {@code MIS_UNKNOWN} (ruling N1). Latent for a real file: an IBM float cannot be an IEEE NaN, and
 * {@code XptVarParser} hands every missing out as a coded payload. This pins the arm itself, the
 * twin of {@code BdatTableProviderTest.testNumberBranchDecodesANaNWithForNaN}.
 */
class XptNumberArmNaNTest
{

    @Test
    void theNumberArmDecodesANaNWithForNaN() throws ReflectiveOperationException
    {
        assertEquals(MissingValue.MIS, addOne(Double.NaN), "bare NaN");
        assertEquals(MissingValue.MIS, addOne(Double.longBitsToDouble(0xFFF8_0000_0000_0000L)),
                "the arithmetic NaN, which carries the sign bit");
        assertEquals(MissingValue.MIS_A, addOne(MissingValue.MIS_A.asDouble()), "coded .A");
        assertEquals(MissingValue.MIS_UNKNOWN,
                addOne(Double.longBitsToDouble(0xFFFF_0000_0000_0000L)), "unrecognised payload");
        assertEquals(1.5, ((Number) addOne(1.5)).doubleValue(), 0.0, "a present number");
    }


    /**
     * Runs the private {@code addData2Column} over a one-row slice whose only cell is the given raw
     * value, and returns what the column then holds.
     */
    private static @Nullable Object addOne(Object aRawValue) throws ReflectiveOperationException
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("X")
                .type(DataValueType.DOUBLE).build();
        DataTableMeta tableMeta = DataTableMeta.builder().rowCount(1).totalRowCount(1)
                .setColumns(meta).build();
        Class<?> parserCls = Class.forName(
                "net.cumba.datatable.provider.sas.xpt.XptTableProvider$XptTableDataParser");
        Constructor<?> ctor = parserCls.getDeclaredConstructor(IDataTableProvider.class,
                DataTableMeta.class);
        ctor.setAccessible(true);
        Object parser = ctor.newInstance(new XptTableProvider(), tableMeta);

        XptObservation obs = new XptObservation(Collections.emptyList(), new byte[0])
        {

            @Override
            public Object getValue(int aIndex)
            {
                return aRawValue;
            }
        };
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        Method m = parserCls.getDeclaredMethod("addData2Column", List.class, int.class,
                DataTableColumnMeta.class, CachedDataTableColumn.class);
        m.setAccessible(true);
        m.invoke(parser, List.of(obs), 0, meta, col);
        col.complete();
        return col.getValue(0);
    }
}
