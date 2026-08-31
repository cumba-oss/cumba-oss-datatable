package net.cumba.datatable.provider.cdt;

import java.util.Collections;
import java.util.Map;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import org.jspecify.annotations.Nullable;

/**
 * Descriptor for a single column as declared on a {@code col} line in a CDT file.
 *
 * <p>
 * Beyond the typed fields ({@link #type}, {@link #label}, {@link #length}, {@link #format},
 * {@link #codelist}) any additional {@code key=value} attributes written on the line are collected
 * in {@link #attrs}, in declared order. Consumers route these into either a corresponding
 * {@link net.cumba.datatable.DataTableColumnMeta} field (if one exists) or into the column's custom
 * metadata table.
 * </p>
 */
@Value
@Builder(toBuilder = true)
@SuppressWarnings("cast")
public class CdtColumn
{

    String name;

    CdtType type;

    @Nullable
    String label;

    @Nullable
    Integer length;

    @Nullable
    String format;

    @Nullable
    String codelist;

    /**
     * Extra {@code key=value} pairs on the {@code col} line that are not one of the named typed
     * fields above. Preserved in declaration order.
     */
    @Singular("attr")
    Map<String, String> attrs;

    public Map<String, String> getAttrs()
    {
        return attrs != null ? attrs : Collections.emptyMap();
    }
}
