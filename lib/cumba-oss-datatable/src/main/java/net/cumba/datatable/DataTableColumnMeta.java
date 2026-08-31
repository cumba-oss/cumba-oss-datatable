package net.cumba.datatable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * A default implementation of the {@link DataTableColumnMeta} interface.
 */
@Getter
@Builder(toBuilder = true, buildMethodName = "_build")
@AllArgsConstructor
@EqualsAndHashCode
@ToString(onlyExplicitlyIncluded = true)
@Jacksonized
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
// Lombok @Builder/@AllArgsConstructor all-args constructor trips NullAway's @NonNull-field init
// check (NullAway#917); the custom builder + @lombok.NonNull enforce required fields at build().
@SuppressWarnings("NullAway.Init")
public class DataTableColumnMeta implements IDataTableColumnMeta, Cloneable
{

    /**
     * Create a DefaultDataTableColumnMetaBuilder initialized from the given DataTableColumnMeta.
     * This is similar to {@code toBuilder()} but it allows to have any {@link DataTableColumnMeta}
     * as source.
     *
     * @param aMeta
     *            the column metadata to initialize the builder with.
     * @return the initialized builder.
     */
    public static DataTableColumnMetaBuilder builderFrom(DataTableColumnMeta aMeta)
    {
        return aMeta.toBuilder();
    }


    /**
     * Create a copy of the given {@link DataTableColumnMeta} as DefaultDataTableColumnMeta.
     *
     * @param aMeta
     *            the meta data to copy.
     * @return the copy of the given metadata.
     */
    public static DataTableColumnMeta cloneFrom(DataTableColumnMeta aMeta)
    {
        if (aMeta.getClass() == DataTableColumnMeta.class)
        {
            // this is immutable --> we do not need to copy
            return aMeta;
        }

        // we can clone
        return aMeta.clone();
    }

    /**
     * The index of the column.
     */
    @ToString.Include
    @With
    private final int index;

    /**
     * The name of the column.
     */
    @ToString.Include
    @With
    @NonNull
    private final String name;

    /**
     * The label of the column.
     */
    @With
    private final @Nullable String label;

    /**
     * The length of the column.
     */
    private final int length;

    /**
     * The native type of the column.
     */
    private final String nativeType;

    /**
     * The data type of the column.
     */
    private final DataValueType type;

    /**
     * The display format of the column.
     */
    @With
    private final @Nullable String displayFormat;

    /**
     * The custom metadata table of the column.
     */
    private final Object @Nullable [] metaTable;

    /**
     * Returns a <b>copy</b> of the internal custom metadata table. This is only for serialization.
     *
     * @return a <b>copy</b> of the internal custom metadata table. This is only for serialization.
     */
    public Object @Nullable [] getMetaTable()
    {
        if (metaTable == null)
        {
            return null;
        }
        return Arrays.copyOf(metaTable, metaTable.length);
    }


    @Override
    @JsonIgnore
    public @Nullable Object getMetaData(@NonNull String aKey)
    {
        if (metaTable == null)
        {
            return null;
        }

        for (int i = 0; i < metaTable.length - 1; i = i + 2)
        {
            if (aKey.equals(metaTable[i]))
            {
                return metaTable[i + 1];
            }
        }
        return null;
    }


    @Override
    public @Nullable Object getMetaData(@NonNull String aKey, @Nullable Object aDefault)
    {
        if (metaTable == null)
        {
            return aDefault;
        }

        for (int i = 0; i < metaTable.length - 1; i = i + 2)
        {
            if (aKey.equals(metaTable[i]))
            {
                return metaTable[i + 1];
            }
        }
        return aDefault;
    }


    @Override
    public Collection<String> getMetaDataKeys()
    {
        if (metaTable == null)
        {
            return Collections.emptyList();
        }

        List<String> res = new ArrayList<>();
        for (int i = 0; i < metaTable.length - 1; i = i + 2)
        {
            if (metaTable[i] instanceof String key)
            {
                res.add(key);
            }
        }
        return List.copyOf(res);
    }


    @Override
    public DataTableColumnMeta clone()
    {
        try
        {
            return (DataTableColumnMeta) super.clone();
        }
        catch (CloneNotSupportedException ex)
        {
            // should not occur
            throw new AssertionError(ex);
        }
    }

    /**
     * A custom builder implementation with additional methods.
     */
    public static class DataTableColumnMetaBuilder
    {

        public DataTableColumnMeta build()
        {
            DataTableColumnMeta res = _build();

            if (res.index < 0)
            {
                throw new IllegalStateException("Index must be defined >= 0.");
            }

            return res;

        }


        public DataTableColumnMetaBuilder index(int aIndex)
        {
            if (aIndex < 0)
            {
                throw new IllegalStateException("Index must be defined >= 0.");
            }
            index = aIndex;
            return this;
        }


        public DataTableColumnMetaBuilder name(String aName)
        {
            if (aName == null)
            {
                throw new IllegalStateException("Name must be defined.");
            }
            name = aName;
            return this;
        }


        public DataTableColumnMetaBuilder addMetaData(@NonNull String aKey, @Nullable Object aValue)
        {
            if (aValue == null)
            {
                // we never add null values
                return this;
            }

            if (metaTable == null)
            {
                metaTable = new Object[2];
            }
            else
            {
                metaTable = Arrays.copyOf(metaTable, metaTable.length + 2);
            }
            metaTable[metaTable.length - 2] = aKey;
            metaTable[metaTable.length - 1] = aValue;
            return this;
        }
    }
}
