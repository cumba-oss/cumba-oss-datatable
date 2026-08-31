package net.cumba.datatable.index;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;

import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;

/**
 * The generic abstract factory. This class only defines the contract for an index creating class
 * and allows loading the implementation.
 */
public abstract class DataTableIndexFactory
{

    private static final Logger LOGGER = System.getLogger(DataTableIndexFactory.class.getName());

    /**
     * Create a new instance of the index factory.
     *
     * @return the newly created factory instance.
     */
    public static DataTableIndexFactory createInstance()
    {
        String clsName = System.getProperty(DataTableIndexFactory.class.getName());
        if (CDT.isBlankOrNull(clsName))
        {
            clsName = "net.cumba.datatable.impl.index.DataTableIndexFactoryImpl";
        }

        LOGGER.log(Level.DEBUG, "Will use factory class {0}.", clsName);

        Class<?> cls;
        try
        {
            cls = DataTableIndexFactory.class.getClassLoader().loadClass(clsName);
        }
        catch (Exception ex)
        {
            String msg = MessageFormat.format("Factory class {0} can not be found!", clsName);
            LOGGER.log(Level.ERROR, msg, ex);
            throw new IllegalStateException(msg, ex);
        }

        if (!DataTableIndexFactory.class.isAssignableFrom(cls))
        {
            String msg = MessageFormat
                    .format("Factory class {0} is not a valid DataTableIndexFactory!", clsName);
            LOGGER.log(Level.ERROR, msg);
            throw new IllegalStateException(msg);
        }

        Class<? extends DataTableIndexFactory> clsDtf = cls.asSubclass(DataTableIndexFactory.class);

        Object inst;
        try
        {
            Constructor<? extends DataTableIndexFactory> c = clsDtf.getDeclaredConstructor();
            inst = c.newInstance();
        }
        catch (NoSuchMethodException ex)
        {
            String msg = MessageFormat.format(
                    "Factory class {0} does not have a default (no args) constructor!", clsName);
            LOGGER.log(Level.ERROR, msg, ex);
            throw new IllegalStateException(msg, ex);
        }
        catch (IllegalAccessException | InvocationTargetException | InstantiationException ex)
        {
            String msg = MessageFormat
                    .format("Can not call default constructor of factory class {0}!", clsName);
            LOGGER.log(Level.ERROR, msg, ex);
            throw new IllegalStateException(msg, ex);

        }

        if (!(inst instanceof DataTableIndexFactory factory))
        {
            // this should never happen
            String msg = MessageFormat.format(
                    "Instance of factory class {0} is not a valid DataTableIndexFactory!", clsName);
            LOGGER.log(Level.ERROR, msg);
            throw new IllegalStateException(msg);
        }
        return factory;

    }


    /**
     * Create a new data table index from the given table by using the given columns.<br/>
     * The index is created with blocks in no expected order.
     *
     * @param aTable
     *            the table to create the index from.
     * @param aColumns
     *            the columns to create the index for.
     * @return the index.
     */
    public abstract IDataTableIndex createIndex(IDataTable aTable, String... aColumns);
}
