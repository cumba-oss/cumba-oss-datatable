package net.cumba.datatable.impl.databuffer;

import net.cumba.datatable.values.DataValueType;

/**
 * Default {@link DataBufferFactory}. Returns plain-array buffers — no compression, no factor
 * coding. Mapping (v1):
 * <ul>
 * <li>{@link DataValueType#DOUBLE} → {@link DataBufferDouble} (double[], NaN missing)</li>
 * <li>{@link DataValueType#LONG} → {@link DataBufferLong} (long[], MIN_VALUE missing)</li>
 * <li>{@link DataValueType#BOOLEAN} → {@link DataBufferInt} (int[], MIN_VALUE missing)</li>
 * <li>everything else → {@link DataBufferObject} (Object[], null missing)</li>
 * </ul>
 * Range-backed buffers ({@link #createForRange(long, long)}) pick int- vs long-backed storage from
 * the value range.
 */
public final class DefaultDataBufferFactory implements DataBufferFactory
{

    public static final DefaultDataBufferFactory INSTANCE = new DefaultDataBufferFactory();

    private DefaultDataBufferFactory()
    {
    }


    @Override
    public IDataBuffer createColumnBuffer(DataValueType aType)
    {
        if (aType == null)
        {
            return new DataBufferObject();
        }
        return switch (aType)
        {
        case DOUBLE -> new DataBufferDouble();
        case LONG -> new DataBufferLong();
        case BOOLEAN -> new DataBufferInt();
        default -> new DataBufferObject();
        };
    }


    @Override
    public IDataBufferNumeric createForRange(long aMin, long aMax)
    {
        if (aMax < aMin)
        {
            throw new IllegalArgumentException(
                    "aMax (" + aMax + ") must be >= aMin (" + aMin + ")");
        }
        // int[] suffices when both endpoints fit in int (above MIN_VALUE, which is the
        // missing sentinel, and at or below MAX_VALUE).
        if (aMin > Integer.MIN_VALUE && aMax <= Integer.MAX_VALUE)
        {
            return new DataBufferInt();
        }
        return new DataBufferLong();
    }
}
