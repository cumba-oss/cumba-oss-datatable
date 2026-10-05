package net.cumba.datatable.impl.merge;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Captures JUL log records emitted through a {@link System.Logger} for one class, at all levels
 * (DEBUG/FINE included), for the lifetime of the capture. Use in a try-with-resources so the
 * logger's original level and handlers are restored.
 *
 * <p>
 * Exists because ~40 pitest survivors in this module were "removed call to System.Logger::log":
 * operational evidence (a failed catalog read, a transpose progress trail) that nothing asserted.
 * </p>
 */
public final class LogCapture implements AutoCloseable
{

    private final Logger logger;

    private final Level oldLevel;

    private final Handler handler;

    private final List<LogRecord> records = new ArrayList<>();

    private LogCapture(String aLoggerName)
    {
        logger = Logger.getLogger(aLoggerName);
        oldLevel = logger.getLevel();
        handler = new Handler()
        {

            @Override
            public void publish(LogRecord aRecord)
            {
                synchronized (records)
                {
                    records.add(aRecord);
                }
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
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
    }


    public static LogCapture of(Class<?> aClass)
    {
        return new LogCapture(aClass.getName());
    }


    public List<LogRecord> records()
    {
        synchronized (records)
        {
            return List.copyOf(records);
        }
    }


    /** Number of captured records at or above the given JUL level. */
    public long count(Level aMinLevel)
    {
        return records().stream().filter(r -> r.getLevel().intValue() >= aMinLevel.intValue())
                .count();
    }


    /** Whether any record's (parameter-substituted) message contains the given text. */
    public boolean anyMessageContains(String aText)
    {
        return records().stream().anyMatch(r -> formatted(r).contains(aText));
    }


    public long countMessagesContaining(String aText)
    {
        return records().stream().filter(r -> formatted(r).contains(aText)).count();
    }


    private static String formatted(LogRecord aRecord)
    {
        String msg = aRecord.getMessage();
        if (msg == null)
        {
            return "";
        }
        Object[] params = aRecord.getParameters();
        if (params != null && params.length > 0)
        {
            try
            {
                return MessageFormat.format(msg, params);
            }
            catch (IllegalArgumentException _)
            {
                return msg;
            }
        }
        return msg;
    }


    @Override
    public void close()
    {
        logger.removeHandler(handler);
        logger.setLevel(oldLevel);
    }
}
