package net.cumba.datatable.impl.databuffer;

import java.lang.System.Logger.Level;

import lombok.CustomLog;
import net.cumba.datatable.values.DataValueType;

/**
 * Factory for {@link IDataBuffer} instances. All buffer construction outside the
 * {@code net.cumba.datatable.impl.databuffer} package goes through this factory so the concrete
 * buffer types can be swapped wholesale by an alternative implementation.
 * <p>
 * Access via {@link #get()}; the singleton is initialised on first access and resolves to either
 * the class named by the {@link #FACTORY_CLASS_PROPERTY} system property or
 * {@link DefaultDataBufferFactory}. Tests may swap the factory via {@link #set(DataBufferFactory)}.
 */
public interface DataBufferFactory
{

    /**
     * System property whose value (a fully-qualified class name) overrides the default factory
     * implementation. The named class must implement {@link DataBufferFactory}, have a public
     * no-arg constructor, and be visible to the system classloader.
     */
    String FACTORY_CLASS_PROPERTY = "net.cumba.datatable.databuffer.factoryClass";

    /**
     * Return the active singleton factory.
     */
    static DataBufferFactory get()
    {
        return Holder.INSTANCE;
    }


    /**
     * Inject a factory. Startup-only — swap before any column or buffer is constructed. Tests that
     * override the factory must restore the previous value in {@code @AfterEach}.
     */
    static void set(DataBufferFactory aFactory)
    {
        Holder.INSTANCE = aFactory;
    }


    /**
     * Build the initial buffer for a column of the given declared type. Callers push values via
     * {@link IDataBuffer#setValue(int, Object)} during loading.
     */
    IDataBuffer createColumnBuffer(DataValueType aType);


    /**
     * Build a numeric buffer sized to store integer values in the inclusive range
     * {@code [aMin, aMax]}. The factory picks {@code int[]}-backed storage when the range fits in
     * {@code int} (above {@link Integer#MIN_VALUE}, below {@link Integer#MAX_VALUE}), else
     * {@code long[]}-backed. Both buffers reserve their {@code MIN_VALUE} sentinel for missing.
     */
    IDataBufferNumeric createForRange(long aMin, long aMax);

    /**
     * Singleton holder. Class init runs on first access of {@link DataBufferFactory#get()} and
     * resolves {@link DataBufferFactory#FACTORY_CLASS_PROPERTY} via reflection, falling back to
     * {@link DefaultDataBufferFactory} on any failure.
     */
    @CustomLog
    final class Holder
    {

        private static volatile DataBufferFactory INSTANCE = createDefault();

        private static DataBufferFactory createDefault()
        {
            return loadFromProperty(System.getProperty(FACTORY_CLASS_PROPERTY));
        }


        static DataBufferFactory loadFromProperty(String aClassName)
        {
            if (aClassName == null || aClassName.isBlank())
            {
                return DefaultDataBufferFactory.INSTANCE;
            }
            try
            {
                Object instance = Class.forName(aClassName).getDeclaredConstructor().newInstance();
                if (instance instanceof DataBufferFactory factory)
                {
                    return factory;
                }
                LOGGER.log(Level.WARNING,
                        "Class '" + aClassName + "' named by " + FACTORY_CLASS_PROPERTY
                                + " does not implement DataBufferFactory; using default.");
            }
            catch (ReflectiveOperationException e)
            {
                LOGGER.log(Level.WARNING, "Failed to load DataBufferFactory '" + aClassName
                        + "', falling back to default", e);
            }
            return DefaultDataBufferFactory.INSTANCE;
        }


        private Holder()
        {
        }
    }
}
