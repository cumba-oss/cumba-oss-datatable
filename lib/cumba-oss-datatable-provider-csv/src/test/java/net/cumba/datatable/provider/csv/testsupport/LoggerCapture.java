package net.cumba.datatable.provider.csv.testsupport;

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
 * This module declares its loggers through Lombok's {@code @CustomLog}, which expands to
 * {@code System.getLogger(<fully qualified class name>)}. On a standard JDK the default
 * {@code System.LoggerFinder} is backed by {@code java.util.logging}, so attaching a JUL
 * {@link Handler} to the logger of the same name sees every record the production code emits -
 * message, level and parameters - without a {@code LoggerFinder} service file, which would be
 * global to the whole test JVM.
 * <p>
 * This exists so that {@code removed call to java/lang/System$Logger::log} mutants are killable:
 * without an observation point a removed log call changes nothing a test can see.
 * <p>
 * Copied verbatim from {@code cumba-datatable-impl}'s
 * {@code net.cumba.datatable.impl.testsupport.LoggerCapture} (test-scoped, not published as a
 * test-jar, so not reusable across modules) rather than depended on — the same convention
 * {@code cumba-datatable-provider-xlsx} already follows.
 *
 * <pre>
 * try (LoggerCapture log = LoggerCapture.attach(CsvTableProvider.class.getName()))
 * {
 *     // exercise the code
 *     assertTrue(log.containsMessageContaining("..."));
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
     * The records captured so far.
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
     * Whether any captured message contains the given fragment.
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
