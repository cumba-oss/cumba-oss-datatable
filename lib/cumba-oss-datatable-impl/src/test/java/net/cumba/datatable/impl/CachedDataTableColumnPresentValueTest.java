package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.cumba.datatable.impl.databuffer.AbstractDataBuffer;
import net.cumba.datatable.impl.databuffer.AbstractNumericDataBuffer;
import net.cumba.datatable.impl.databuffer.DataBufferDouble;
import net.cumba.datatable.impl.databuffer.DataBufferInt;
import net.cumba.datatable.impl.databuffer.DataBufferLong;
import net.cumba.datatable.impl.databuffer.DataBufferObject;
import net.cumba.datatable.impl.databuffer.IDataBuffer;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueLong;
import net.cumba.datatable.values.DataValueString;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * {@link CachedDataTableColumn#presentStringValue(long)} and
 * {@link CachedDataTableColumn#presentNumericValue(long)} — the allocation-free reads the coreJ
 * engine uses for join keys (PLAN-identity-safe-join-caches D6).
 *
 * <p>
 * The load-bearing test is a DIFFERENTIAL grid: every cell of every column is read both ways and
 * any fast answer must agree exactly with {@code getDataValue(row)}. The columns are built through
 * the production path so the real factory picks every buffer class it can reach; the buffer classes
 * production never puts behind a column are injected. A non-vacuity check proves the fast path
 * actually fires for every handled buffer class, and a package scan fails when a new concrete
 * {@link IDataBuffer} appears that is neither handled nor knowingly excluded.
 * </p>
 */
class CachedDataTableColumnPresentValueTest
{

    /**
     * Buffer classes deliberately NOT on the fast path, with the reason. None in this repository:
     * all four concrete buffers are handled.
     */
    private static final Map<Class<?>, String> KNOWINGLY_EXCLUDED = Map.of();

    /** A NaN whose payload MissingValue.forValue recognises. */
    private static final double RECOGNISED_NAN = MissingValue.MIS_B.asDouble();

    /** A NaN whose payload MissingValue.forValue does not recognise. */
    private static final double UNRECOGNISED_NAN = Double.longBitsToDouble(0x7ff8000000000123L);

    private static final long ABOVE_2_53 = (1L << 53) + 1L;

    private static final Object OTHER_VALUE = Locale.ENGLISH;

    /** One column of the grid, with a label for the failure messages. */
    private record Case(String label, CachedDataTableColumn column)
    {
    }


    /** What one pass over the grid observed. */
    private static final class Observed
    {

        final Map<Class<?>, Integer> fastHitsByBuffer = new HashMap<>();

        final Set<String> fastArms = new TreeSet<>();

        final Set<Class<?>> buffersSeen = new HashSet<>();
    }

    private static CachedDataTableColumn column(DataValueType aType, Object... aValues)
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, aType);
        for (Object v : aValues)
        {
            col.addElement(v);
        }
        col.complete();
        return col;
    }


    private static CachedDataTableColumn inject(DataValueType aType, IDataBuffer aBuffer)
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, aType);
        col.dataBuffer = aBuffer;
        col.setTableRowCount(aBuffer.size());
        return col;
    }


    private static IDataBuffer filled(IDataBuffer aBuffer, Object... aValues)
    {
        for (int i = 0; i < aValues.length; i++)
        {
            aBuffer.setValue(i, aValues[i]);
        }
        return aBuffer;
    }


    private static Object[] repeat(int aTimes, Object... aValues)
    {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < aTimes; i++)
        {
            out.addAll(Arrays.asList(aValues));
        }
        return out.toArray();
    }


    private static Object[] concat(Object[] aFirst, Object... aSecond)
    {
        Object[] out = Arrays.copyOf(aFirst, aFirst.length + aSecond.length);
        System.arraycopy(aSecond, 0, out, aFirst.length, aSecond.length);
        return out;
    }


    private static Object[] uniqueDoubles(double aStart, double aStep, int aCount)
    {
        Object[] out = new Object[aCount];
        for (int i = 0; i < aCount; i++)
        {
            out[i] = aStart + aStep * i;
        }
        return out;
    }


    /**
     * The whole grid: every declared type crossed with every buffer class it can sit on. This
     * repository's factory picks the buffer by declared type alone (STRING and OTHER:
     * DataBufferObject, LONG: DataBufferLong, DOUBLE: DataBufferDouble, BOOLEAN: DataBufferInt) and
     * complete() never swaps it, so the other pairings are injected.
     */
    private static List<Case> grid()
    {
        List<Case> g = new ArrayList<>();

        // ---- STRING -------------------------------------------------------------------------
        Object[] awkward =
        {
                "", " ", ".", null, MissingValue.MIS, MissingValue.MIS_A, MissingValue.MIS_Z,
                RECOGNISED_NAN, UNRECOGNISED_NAN, 1.5, 42L, -0.0
        };
        g.add(new Case("STRING few-distinct",
                column(DataValueType.STRING, concat(repeat(20, "A", "B"), awkward))));
        g.add(new Case("STRING all-unique", column(DataValueType.STRING, concat(new Object[]
        {
                "u0", "u1", "u2", "u3", "u4", "u5"
        }, awkward))));
        g.add(new Case("STRING single", column(DataValueType.STRING, "only", "only", "only")));
        g.add(new Case("STRING single empty", column(DataValueType.STRING, "", "")));
        g.add(new Case("STRING single number", column(DataValueType.STRING, 7.5, 7.5)));
        CachedDataTableColumn shortString = column(DataValueType.STRING,
                concat(repeat(4, "A", "B"), "", MissingValue.MIS));
        shortString.setTableRowCount(shortString.getDataBuffer().size() + 3L);
        g.add(new Case("STRING short buffer", shortString));
        g.add(new Case("STRING on DataBufferLong",
                inject(DataValueType.STRING, filled(new DataBufferLong(), 1L, null, 3L))));
        g.add(new Case("STRING on DataBufferDouble", inject(DataValueType.STRING,
                filled(new DataBufferDouble(), 1.5, MissingValue.MIS, UNRECOGNISED_NAN))));

        // ---- LONG ---------------------------------------------------------------------------
        // DataBufferLong refuses a fractional, infinite or NaN number, so those only reach a LONG
        // column on an injected object buffer below.
        Object[] awkwardLong =
        {
                Long.MAX_VALUE, Long.MIN_VALUE + 1, ABOVE_2_53, -ABOVE_2_53, 0L, 3.0, -0.0, 0.0,
                null, MissingValue.MIS, MissingValue.MIS_D, MissingValue.MIS_Z
        };
        g.add(new Case("LONG few-distinct",
                column(DataValueType.LONG, concat(repeat(25, 1L, 2L), awkwardLong))));
        g.add(new Case("LONG all-unique", column(DataValueType.LONG, concat(new Object[]
        {
                10L, 11L, 12L, 13L, 14L
        }, awkwardLong))));
        g.add(new Case("LONG single", column(DataValueType.LONG, 5L, 5L, 5L)));
        g.add(new Case("LONG single MIS",
                column(DataValueType.LONG, MissingValue.MIS, MissingValue.MIS)));
        // Long.MIN_VALUE is the buffer's raw missing sentinel, but a stored Long.MIN_VALUE reads
        // back PRESENT (its code table says so), and each special missing keeps its identity.
        g.add(new Case("LONG sentinel", column(DataValueType.LONG, Long.MIN_VALUE, 1L)));
        g.add(new Case("LONG sentinel and special missings",
                column(DataValueType.LONG, Long.MIN_VALUE, null, MissingValue.MIS,
                        MissingValue.MIS_A, MissingValue.MIS_ERROR, Long.MIN_VALUE, 2L)));
        CachedDataTableColumn shortLong = column(DataValueType.LONG, repeat(4, 1L, 2L));
        shortLong.setTableRowCount(shortLong.getDataBuffer().size() + 3L);
        g.add(new Case("LONG short buffer", shortLong));
        // ⚠ The twin difference: this repository's LONG arm answers a PRESENT DataValueLong(0)
        // for a boxed NaN here; the fast read answers it the slow way.
        g.add(new Case("LONG on DataBufferObject",
                inject(DataValueType.LONG,
                        filled(new DataBufferObject(), 7L, 2.5, -0.0, Double.POSITIVE_INFINITY,
                                Double.NEGATIVE_INFINITY, RECOGNISED_NAN, UNRECOGNISED_NAN,
                                Double.NaN, null, MissingValue.MIS_K, "", "12", Long.MAX_VALUE,
                                Long.MIN_VALUE, ABOVE_2_53))));
        g.add(new Case("LONG on DataBufferInt",
                inject(DataValueType.LONG,
                        filled(new DataBufferInt(), 0L, -1L, (long) Integer.MAX_VALUE,
                                Integer.MIN_VALUE + 1L, (long) Integer.MIN_VALUE, null,
                                MissingValue.MIS_H, 4.0))));
        g.add(new Case("LONG on DataBufferDouble",
                inject(DataValueType.LONG,
                        filled(new DataBufferDouble(), 3.0, 3.5, -0.0, UNRECOGNISED_NAN,
                                RECOGNISED_NAN, MissingValue.MIS_E, Double.POSITIVE_INFINITY,
                                (double) ABOVE_2_53))));

        // ---- DOUBLE -------------------------------------------------------------------------
        // DataBufferDouble refuses null and a long it cannot hold exactly.
        Object[] awkwardDouble =
        {
                -0.0, 0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, RECOGNISED_NAN,
                UNRECOGNISED_NAN, Double.NaN, MissingValue.MIS, MissingValue.MIS_A,
                MissingValue.MIS_Z, 1e300, (double) ABOVE_2_53, Long.MAX_VALUE, 7L
        };
        g.add(new Case("DOUBLE few-distinct",
                column(DataValueType.DOUBLE, concat(repeat(25, 1.5, 2.5), awkwardDouble))));
        g.add(new Case("DOUBLE all-unique",
                column(DataValueType.DOUBLE, concat(uniqueDoubles(0.1, 0.1, 6), awkwardDouble))));
        g.add(new Case("DOUBLE single", column(DataValueType.DOUBLE, 3.25, 3.25, 3.25)));
        g.add(new Case("DOUBLE single negative zero", column(DataValueType.DOUBLE, -0.0, -0.0)));
        g.add(new Case("DOUBLE single unrecognised NaN",
                column(DataValueType.DOUBLE, UNRECOGNISED_NAN, UNRECOGNISED_NAN)));
        CachedDataTableColumn shortDouble = column(DataValueType.DOUBLE, repeat(4, 1.5, 2.5));
        shortDouble.setTableRowCount(shortDouble.getDataBuffer().size() + 3L);
        g.add(new Case("DOUBLE short buffer", shortDouble));
        // The sentinel of a long/int buffer reads as an ordinary number through getValueAsDouble,
        // so these two cases are what pin the isMissing test on the DOUBLE arm.
        g.add(new Case("DOUBLE on DataBufferLong",
                inject(DataValueType.DOUBLE, filled(new DataBufferLong(), 0L, -1L, Long.MAX_VALUE,
                        Long.MIN_VALUE, ABOVE_2_53, null, MissingValue.MIS_T))));
        g.add(new Case("DOUBLE on DataBufferInt",
                inject(DataValueType.DOUBLE, filled(new DataBufferInt(), 0L, 17L, -5L,
                        (long) Integer.MIN_VALUE, null, MissingValue.MIS_U))));
        g.add(new Case("DOUBLE on DataBufferObject",
                inject(DataValueType.DOUBLE,
                        filled(new DataBufferObject(), 1.5, 2L, "x", "", null, MissingValue.MIS_Q,
                                RECOGNISED_NAN, UNRECOGNISED_NAN, -0.0, Double.NEGATIVE_INFINITY,
                                ABOVE_2_53))));
        g.add(new Case("DOUBLE on an unanalysed subclass",
                inject(DataValueType.DOUBLE, filled(new DataBufferObject()
                {
                    // An anonymous subclass: exact-class dispatch must NOT treat it as handled.
                }, 1.5, 2.5))));

        // ---- OTHER and BOOLEAN: always slow ---------------------------------------------------
        g.add(new Case("OTHER few-distinct",
                column(DataValueType.OTHER, concat(repeat(10, OTHER_VALUE, "text", 4.5, 3L), "",
                        null, MissingValue.MIS, -0.0))));
        g.add(new Case("OTHER single", column(DataValueType.OTHER, "same", "same")));
        g.add(new Case("BOOLEAN few-distinct",
                column(DataValueType.BOOLEAN, concat(repeat(10, true, false), (Object) null))));
        return g;
    }


    private static Observed runGrid()
    {
        Observed obs = new Observed();
        for (Case c : grid())
        {
            checkColumn(c, obs);
        }
        return obs;
    }


    private static void checkColumn(Case aCase, Observed aObserved)
    {
        CachedDataTableColumn col = aCase.column();
        DataValueType type = col.getType();
        Class<?> bufferClass = col.getDataBuffer().getClass();
        aObserved.buffersSeen.add(bufferClass);
        boolean handled = CachedDataTableColumn.FAST_OBJECT_BUFFERS.contains(bufferClass)
                || CachedDataTableColumn.FAST_NUMERIC_BUFFERS.contains(bufferClass);
        long rows = col.getRowCount();
        assertTrue(rows > 0, aCase.label() + ": the grid column must not be empty");
        for (long row = 0; row < rows; row++)
        {
            String where = aCase.label() + " [" + bufferClass.getSimpleName() + "] row " + row;
            IDataValue slow = col.getDataValue(row);
            String fastString = col.presentStringValue(row);
            double fastNumber = col.presentNumericValue(row);
            boolean fired = false;

            if (fastString != null)
            {
                fired = true;
                assertEquals(DataValueType.STRING, type, where + ": fast string on a non-STRING");
                DataValueString s = assertInstanceOf(DataValueString.class, slow, where);
                assertEquals(fastString, s.getValue(), where);
                assertFalse(fastString.isEmpty(), where + ": an empty string is never fast");
                assertFalse(slow.isMissingOrInvalid(), where);
            }
            if (!Double.isNaN(fastNumber))
            {
                fired = true;
                Class<? extends IDataValue> expected = switch (type)
                {
                case LONG -> DataValueLong.class;
                case DOUBLE -> DataValueDouble.class;
                default -> throw new AssertionError(where + ": fast number on type " + type);
                };
                assertInstanceOf(expected, slow, where);
                assertFalse(slow.isMissingOrInvalid(), where);
                double slowNumber = ((Number) slow.getValue()).doubleValue();
                assertEquals(Double.doubleToRawLongBits(slowNumber),
                        Double.doubleToRawLongBits(fastNumber),
                        where + ": fast " + fastNumber + " vs slow " + slowNumber);
            }
            if (fired)
            {
                assertTrue(handled, where + ": an unhandled buffer class answered fast");
                aObserved.fastHitsByBuffer.merge(bufferClass, 1, Integer::sum);
                aObserved.fastArms.add(bufferClass.getSimpleName() + "x" + type);
            }
        }
        checkInvalidRows(aCase.label(), col);
    }


    private static void checkInvalidRows(String aLabel, CachedDataTableColumn aColumn)
    {
        long rows = aColumn.getRowCount();
        for (long row : new long[]
        {
                rows, rows + 7, -1L, Long.MIN_VALUE
        })
        {
            IndexOutOfBoundsException slow = assertThrows(IndexOutOfBoundsException.class,
                    () -> aColumn.getDataValue(row), aLabel);
            IndexOutOfBoundsException fastString = assertThrows(IndexOutOfBoundsException.class,
                    () -> aColumn.presentStringValue(row), aLabel);
            IndexOutOfBoundsException fastNumber = assertThrows(IndexOutOfBoundsException.class,
                    () -> aColumn.presentNumericValue(row), aLabel);
            assertEquals(slow.getClass(), fastString.getClass(), aLabel);
            assertEquals(slow.getClass(), fastNumber.getClass(), aLabel);
            assertEquals(slow.getMessage(), fastString.getMessage(), aLabel + " row " + row);
            assertEquals(slow.getMessage(), fastNumber.getMessage(), aLabel + " row " + row);
        }
    }


    @Test
    void everyFastAnswerAgreesExactlyWithGetDataValue()
    {
        Observed obs = runGrid();
        assertFalse(obs.fastHitsByBuffer.isEmpty(), "the grid never took the fast path");
    }


    @Test
    void theGridReachesEveryBufferClassTheColumnCanHold()
    {
        Observed obs = runGrid();
        Set<Class<?>> expected = new HashSet<>(CachedDataTableColumn.FAST_OBJECT_BUFFERS);
        expected.addAll(CachedDataTableColumn.FAST_NUMERIC_BUFFERS);
        expected.addAll(KNOWINGLY_EXCLUDED.keySet());
        assertTrue(obs.buffersSeen.containsAll(expected),
                "grid misses buffer classes: " + minus(expected, obs.buffersSeen));

        // The classes the production path CAN build must be reached through it, not injected
        // (every injected case is labelled "<TYPE> on <buffer>").
        Set<Class<?>> built = new HashSet<>();
        for (Case c : grid())
        {
            if (!c.label().contains(" on "))
            {
                built.add(c.column().getDataBuffer().getClass());
            }
        }
        assertEquals(Set.of(DataBufferObject.class, DataBufferLong.class, DataBufferDouble.class,
                DataBufferInt.class), built);
    }


    @Test
    void theFastPathFiresForEveryHandledBufferClassAndArm()
    {
        Observed obs = runGrid();
        Set<Class<?>> handled = new HashSet<>(CachedDataTableColumn.FAST_OBJECT_BUFFERS);
        handled.addAll(CachedDataTableColumn.FAST_NUMERIC_BUFFERS);
        assertEquals(Set.of(), minus(handled, obs.fastHitsByBuffer.keySet()),
                "handled buffer classes whose fast path never fired");
        // Every arm the implementation claims, by concrete class and declared type.
        Set<String> arms = Set.of("DataBufferObjectxSTRING", "DataBufferObjectxLONG",
                "DataBufferObjectxDOUBLE", "DataBufferDoublexLONG", "DataBufferDoublexDOUBLE",
                "DataBufferLongxLONG", "DataBufferLongxDOUBLE", "DataBufferIntxLONG",
                "DataBufferIntxDOUBLE");
        assertEquals(Set.of(), minusNames(arms, obs.fastArms), "arms that never fired");
    }


    @Test
    void bufferReassignmentIsSeenOnTheNextCall()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        for (Object v : new Object[]
        {
                "a", "b", "c", "d"
        })
        {
            col.addElement(v);
        }
        assertEquals("a", col.presentStringValue(0));
        col.dataBuffer = filled(new DataBufferObject(), "", "z", "y", "x");
        assertNull(col.presentStringValue(0), "a replaced buffer must be read, not a cached one");
        assertEquals("z", col.presentStringValue(1));
    }


    @Test
    void everyConcreteBufferClassIsHandledOrKnowinglyExcluded() throws Exception
    {
        Set<Class<?>> concrete = concreteBufferClasses();
        // Self-reach: the scan must see the classes we know exist, or it proves nothing.
        assertTrue(concrete.contains(DataBufferObject.class), "scan missed DataBufferObject");
        assertTrue(concrete.contains(DataBufferInt.class), "scan missed DataBufferInt");
        assertTrue(concrete.size() >= 4, "scan found only " + concrete);

        Set<Class<?>> accounted = new HashSet<>(CachedDataTableColumn.FAST_OBJECT_BUFFERS);
        accounted.addAll(CachedDataTableColumn.FAST_NUMERIC_BUFFERS);
        accounted.addAll(KNOWINGLY_EXCLUDED.keySet());
        assertEquals(Set.of(), minus(concrete, accounted),
                "new concrete IDataBuffer classes: derive a fast arm from their getDataValue path "
                        + "or add them to KNOWINGLY_EXCLUDED with the reason");
        assertEquals(Set.of(), minus(accounted, concrete),
                "handled/excluded classes that no longer exist in the package");
    }


    /**
     * The fast arms reproduce the value creation of each buffer's FAMILY base class. That holds
     * only while nothing between a listed class and its base overrides {@code getDataValue} or
     * {@code createDataValue} — a later override would change the slow answer and leave the fast
     * one behind, and the grid would see it only if it happened to hold a value the override treats
     * differently ({@code PLAN-identity-safe-join-caches} D6 review, LOW 3).
     */
    @Test
    void noListedBufferOverridesValueCreationBelowItsFamilyBase()
    {
        for (Class<? extends IDataBuffer> c : CachedDataTableColumn.FAST_OBJECT_BUFFERS)
        {
            assertNoOverrideBelow(c, AbstractDataBuffer.class);
        }
        for (Class<? extends IDataBuffer> c : CachedDataTableColumn.FAST_NUMERIC_BUFFERS)
        {
            assertNoOverrideBelow(c, AbstractNumericDataBuffer.class);
        }
    }


    private static void assertNoOverrideBelow(Class<?> aListed, Class<?> aBase)
    {
        assertTrue(aBase.isAssignableFrom(aListed),
                aListed.getSimpleName() + " is not in the " + aBase.getSimpleName() + " family");
        for (Class<?> c = aListed; c != aBase; c = c.getSuperclass())
        {
            for (java.lang.reflect.Method m : c.getDeclaredMethods())
            {
                boolean creates = (m.getName().equals("createDataValue")
                        || m.getName().equals("getDataValue")) && m.getParameterCount() == 2;
                assertFalse(creates,
                        c.getSimpleName() + " (in the chain of " + aListed.getSimpleName()
                                + ") declares its own " + m.getName()
                                + " — re-derive the fast arm before keeping it listed");
            }
        }
    }


    @Test
    void nonCharacterAndNonNumericColumnsAreAlwaysSlow()
    {
        CachedDataTableColumn other = column(DataValueType.OTHER, "text", 1.5, 2L, "text");
        CachedDataTableColumn number = column(DataValueType.LONG, 1L, 2L, 1L, 2L);
        CachedDataTableColumn text = column(DataValueType.STRING, "1", "2", "1", "2");
        for (long row = 0; row < 4; row++)
        {
            assertNull(other.presentStringValue(row));
            assertTrue(Double.isNaN(other.presentNumericValue(row)));
            assertNull(number.presentStringValue(row));
            assertTrue(Double.isNaN(text.presentNumericValue(row)));
            assertNotNull(text.presentStringValue(row));
        }
    }


    private static Set<Class<?>> minus(Set<? extends Class<?>> aLeft,
            Set<? extends Class<?>> aRight)
    {
        Set<Class<?>> out = new HashSet<>(aLeft);
        out.removeAll(aRight);
        return out;
    }


    private static Set<String> minusNames(Set<String> aLeft, Set<String> aRight)
    {
        Set<String> out = new TreeSet<>(aLeft);
        out.removeAll(aRight);
        return out;
    }


    /** Every concrete IDataBuffer in the databuffer package, read from the classpath or jar. */
    private static Set<Class<?>> concreteBufferClasses()
        throws IOException, URISyntaxException, ClassNotFoundException
    {
        String pkg = IDataBuffer.class.getPackageName();
        String dir = pkg.replace('.', '/');
        URL url = IDataBuffer.class.getResource("IDataBuffer.class");
        assertNotNull(url, "IDataBuffer.class not found on the classpath");
        List<String> classFiles = new ArrayList<>();
        if ("jar".equals(url.getProtocol()))
        {
            JarURLConnection conn = (JarURLConnection) url.openConnection();
            try (JarFile jar = conn.getJarFile())
            {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements())
                {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(dir + "/") && name.endsWith(".class")
                            && name.indexOf('/', dir.length() + 1) < 0)
                    {
                        classFiles.add(name.substring(dir.length() + 1));
                    }
                }
            }
        }
        else
        {
            Path folder = Path.of(url.toURI()).getParent();
            try (Stream<Path> files = Files.list(folder))
            {
                files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".class"))
                        .forEach(classFiles::add);
            }
        }
        Set<Class<?>> out = new HashSet<>();
        for (String file : classFiles)
        {
            String name = pkg + "." + file.substring(0, file.length() - ".class".length());
            Class<?> c = Class.forName(name, false, IDataBuffer.class.getClassLoader());
            if (IDataBuffer.class.isAssignableFrom(c) && !c.isInterface()
                    && !Modifier.isAbstract(c.getModifiers()))
            {
                out.add(c);
            }
        }
        return out;
    }
}
