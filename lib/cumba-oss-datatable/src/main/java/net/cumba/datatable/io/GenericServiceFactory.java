package net.cumba.datatable.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A generic implementation of a service factory that handles {@link IGenericSupplier}'s registered
 * as service.
 *
 * @param <S>
 *            the class of the {@link IGenericSupplier}.
 * @param <P>
 *            the class of the {@link IGenericProvider}.
 */
public class GenericServiceFactory<S, P>
{

    private static final Logger LOGGER = System.getLogger(GenericServiceFactory.class.getName());

    /**
     * The concrete interface class that is registered as a service class.
     */
    private final Class<S> supplierClass;

    /**
     * The cached list of suppliers.
     */
    private @Nullable List<S> suppliers;

    public GenericServiceFactory(@NonNull Class<S> aSupplierClass)
    {
        supplierClass = aSupplierClass;
    }


    /**
     * Returns a list of all registered suppliers.
     *
     * @return a list of all registered suppliers.
     */
    protected List<S> getSuppliers()
    {
        if (suppliers == null)
        {
            suppliers = loadSuppliers();
        }
        return suppliers;
    }


    /**
     * Load the available suppliers from service interface. This is only called once at the first
     * call to {@link #getSuppliers()}.
     *
     * @return a list of all available suppliers. This might be empty, but never null.
     */
    protected List<S> loadSuppliers()
    {
        LOGGER.log(Level.DEBUG, "Find all available suppliers.");

        List<S> res = new ArrayList<>();
        try
        {
            String supClsName = supplierClass.getName();
            String resourceName = "META-INF/services/%s".formatted(supClsName);
            Enumeration<URL> infoEnum = getClass().getClassLoader().getResources(resourceName);
            Set<String> clsNames = new LinkedHashSet<String>();
            while (infoEnum.hasMoreElements())
            {
                URL extUrl = infoEnum.nextElement();
                LOGGER.log(Level.DEBUG, "Found resource {0}", extUrl);

                List<String> elemClsNames = loadSupplierClassNames(extUrl);
                LOGGER.log(Level.DEBUG, "Found {0} supplier class names in {1}.",
                        elemClsNames.size(), extUrl);
                for (String clsName : elemClsNames)
                {
                    if (!clsNames.contains(clsName))
                    {
                        LOGGER.log(Level.DEBUG, "Found supplier class name: {0}.", clsName);
                        clsNames.add(clsName);
                    }
                }
            }

            for (String clsName : clsNames)
            {
                LOGGER.log(Level.DEBUG, "Will Create supplier from class {0}.", clsName);
                Optional<Class<? extends S>> oc = loadSupplierClassForClassName(clsName);
                if (oc.isPresent())
                {
                    Optional<? extends S> os = createSupplier(oc.get());
                    if (os.isPresent())
                    {
                        LOGGER.log(Level.DEBUG, "Supplier created from class {0}.", clsName);
                        res.add(os.get());
                    }
                }
            }
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);
        }

        return res;
    }


    /**
     * Load all supplier class names from the given resource.
     *
     * @param aURL
     *            the resource URL to load the supplier class names from.
     * @return the list of all supplier class names found.
     */
    private List<String> loadSupplierClassNames(URL aURL)
    {
        List<String> res = new ArrayList<>();
        try (InputStream in = aURL.openStream();
                InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader rd = new BufferedReader(isr);)
        {
            String line;
            while ((line = rd.readLine()) != null)
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                {
                    continue;
                }
                res.add(line);
            }
        }
        catch (IOException ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);
        }

        return res;
    }


    /**
     * Try to load a supplier class from the given class name.
     *
     * @param aClassName
     *            the full qualified class name of the class to load as supplier.
     * @return an optional that is either empty or contains the supplier class for the given name.
     */
    protected Optional<Class<? extends S>> loadSupplierClassForClassName(String aClassName)
    {
        try
        {
            Class<?> c = getClass().getClassLoader().loadClass(aClassName);
            if (supplierClass.isAssignableFrom(c))
            {
                return Optional.of(c.asSubclass(supplierClass));
            }
        }
        catch (ClassNotFoundException _)
        {
            LOGGER.log(Level.ERROR, "Class {0} not found.", aClassName);
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);
        }
        return Optional.empty();
    }


    /**
     * Create a supplier from the given class.
     *
     * @param aClass
     *            the class to create the supplier from.
     * @return an optional that is either empty or contains the created supplier.
     */
    protected Optional<? extends S> createSupplier(Class<? extends S> aClass)
    {
        try
        {
            return Optional.of(aClass.getConstructor().newInstance());
        }
        catch (NoSuchMethodException _)
        {
            String msg = MessageFormat.format(
                    "Class {0} does not define a no arguments constructor.", aClass.getName());
            LOGGER.log(Level.ERROR, msg);
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);
        }

        return Optional.empty();
    }

}
