package net.cumba.datatable.values;

/**
 * Type for data values. This type is used to identify the type of data values, for example in a
 * {@link IDataValue} or {@code DataTableColumn}.
 */
public enum DataValueType
{
    /**
     * Identifies a data value as a String value.
     */
    STRING,

    /**
     * Identifies a data value as a long value.
     */
    LONG,

    /**
     * Identifies a data value as a double value.
     */
    DOUBLE,

    /**
     * Identifies a data value as a boolean value.
     */
    BOOLEAN,

    /**
     * Identifies a data value as a missing value.
     */
    MISSING,

    /**
     * Identifies a data value as other value.<br/>
     * This is the type to be used for everything that does not fit into any other category. As the
     * dataType is mandatory this is also used in case no dataType is given (should not occur).
     */
    OTHER
}
