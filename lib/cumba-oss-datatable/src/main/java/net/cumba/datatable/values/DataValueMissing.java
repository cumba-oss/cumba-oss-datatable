package net.cumba.datatable.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

/**
 * A data value that is a missing value.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Jacksonized
public class DataValueMissing implements IDataValue
{

    /**
     * The byte that describes the missing value of this condition variable.
     *
     * <p>
     * ⭐ <b>Target invariant (null-free value channel):</b> an {@link IDataValue}'s payload is a
     * real value or a {@link MissingValue}, <b>never {@code null}</b>. Every other reference-typed
     * {@code IDataValue} implementation carries this annotation; this one did not, so
     * {@code new DataValueMissing((MissingValue) null)} minted a <em>null-carrying</em> value — the
     * one shape {@link IDataValue#getValue()}'s own contract forbids, and one no annotation
     * reaches, because {@code getValue()} is declared to return {@code Object}.
     * </p>
     *
     * <p>
     * ⚠ This turns a silent null into a thrown {@link NullPointerException} at construction, so it
     * is a <b>behaviour</b> change, not a cosmetic one. No production or test site passes a null
     * here (measured 2026-09-20 across this repository and the repositories that consume it: every
     * {@code new DataValueMissing(...)} construction passes either an enum constant, an
     * {@code instanceof MissingValue} pattern variable, a non-null parameter, or an {@code int},
     * and no {@code DataValueMissing.builder()} call omits {@code value}). The remaining reachable
     * path is Jackson: a {@code {"type":"missing"}} document with no {@code value} property now
     * fails loudly at {@code build()} — as a locatable {@code JsonMappingException} naming the
     * property, not an NPE escaping into the caller — instead of deserialising into a null-carrying
     * cell. {@code DataValueMissingTest} pins both halves.
     * </p>
     */
    @NonNull
    private final MissingValue value;

    public DataValueMissing(int aValue)
    {
        this(MissingValue.forValue(aValue));
    }


    @Override
    public MissingValue getValue()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public double getValueAsDouble()
    {
        return Double.NaN;
    }


    /**
     * Returns the missing value as byte without wrapping into an object.
     *
     * @return the missing value as byte without wrapping into an object.
     */
    @JsonIgnore
    public byte getByteValue()
    {
        return value.getValue();
    }


    @Override
    @JsonIgnore
    public DataValueType getType()
    {
        return DataValueType.MISSING;
    }


    @Override
    public String toString()
    {
        return value.toString();
    }
}
