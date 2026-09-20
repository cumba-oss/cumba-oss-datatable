package net.cumba.datatable.impl.testsupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Capture what production code writes through {@link java.lang.System.Logger}.
 * <p>
 * The module declares its loggers through Lombok's {@code @CustomLog}, which expands to
 * {@code System.getLogger(<fully qualified class name>)}. On a standard JDK the default
 * {@code System.LoggerFinder} is backed by {@code java.util.logging}, so attaching a JUL
 * {@link Handler} to the logger of the same name sees every record the production code emits -
 * message, level and parameters - without a {@code LoggerFinder} service file, which would be
 * global to the whole test JVM.
 * <p>
 * This exists so that {@code removed call to java/lang/System$Logger::log} mutants are killable:
 * without an observation point a removed log call changes nothing a test can see. Log statements
 * that report a <em>computed</em> value (a duration, a size) additionally make the arithmetic that
 * feeds them observable through {@link #parametersOf(int)}.
 * <p>
 * Usage - always in try-with-resources, so the logger's level and handler are restored even when
 * the assertion fails:
 *
 * <pre>
 * try (LoggerCapture log = LoggerCapture.attach(StatisticsSupport.class.getName()))
 * {
 *     new StatisticsSupport().descriptiveStatistics(column, meta);
 *     assertTrue(log.containsMessageContaining("First round took"));
 * }
 * </pre>
 */
public final class LoggerCapture implements AutoCloseable
{

    private final Logger logger;

    private final Handler handler;

    private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());

    private final Level previousLevel;

    private LoggerCapture(Logger aLogger)
    {
        logger = aLogger;
        previousLevel = aLogger.getLevel();
        handler = new Handler()
        {

            @Override
            public void publish(LogRecord aRecord)
            {
                records.add(aRecord);
            }


            @Override
            public void flush()
            {
                // nothing buffered
            }


            @Override
            public void close()
            {
                // nothing to release
            }
        };
        handler.setLevel(Level.ALL);
        aLogger.addHandler(handler);
        aLogger.setLevel(Level.ALL);
    }


    /**
     * Start capturing everything logged to the logger of the given name.
     *
     * @param aLoggerName
     *            the logger name, which for {@code @CustomLog} classes is the fully qualified class
     *            name.
     * @return the capture; close it to restore the logger.
     */
    public static LoggerCapture attach(String aLoggerName)
    {
        return new LoggerCapture(Logger.getLogger(aLoggerName));
    }


    /**
     * Start capturing everything that reaches the root logger, whatever logger name it was written
     * to. Use this when the emitting class is not nameable from the test - a private nested holder,
     * for instance.
     *
     * @return the capture; close it to restore the root logger.
     */
    public static LoggerCapture attachRoot()
    {
        return new LoggerCapture(Logger.getLogger(""));
    }


    /**
     * The captured log records.
     *
     * @return the captured records, in the order they were logged.
     */
    public List<LogRecord> records()
    {
        return List.copyOf(records);
    }


    /**
     * The raw messages of the captured records.
     *
     * @return the raw message of every captured record - the pattern, not the formatted text, so a
     *         parameterised message reads as {@code "took {0}ms"}.
     */
    public List<String> messages()
    {
        return records().stream().map(LogRecord::getMessage).toList();
    }


    /**
     * Whether any captured message contains the given text fragment.
     *
     * @param aFragment
     *            the text to look for.
     * @return true when any captured message contains the given fragment.
     */
    public boolean containsMessageContaining(String aFragment)
    {
        return messages().stream().anyMatch(m -> m != null && m.contains(aFragment));
    }


    /**
     * The parameters of one captured record.
     *
     * @param aIndex
     *            the index of the record.
     * @return the parameter array of that record, never null - an empty array when the record
     *         carried none.
     */
    public Object[] parametersOf(int aIndex)
    {
        Object[] params = records().get(aIndex).getParameters();
        return params != null ? params : new Object[0];
    }


    /**
     * Find the first record whose message contains the given fragment.
     *
     * @param aFragment
     *            the text to look for.
     * @return the index of that record, or -1 when no record matches.
     */
    public int indexOfMessageContaining(String aFragment)
    {
        List<String> msgs = messages();
        for (int i = 0; i < msgs.size(); i++)
        {
            String m = msgs.get(i);
            if (m != null && m.contains(aFragment))
            {
                return i;
            }
        }
        return -1;
    }


    /**
     * Drop everything captured so far, so a later assertion is not confused by earlier records.
     */
    public void clear()
    {
        records.clear();
    }


    @Override
    public void close()
    {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
    }
}
