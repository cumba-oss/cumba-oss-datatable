package net.cumba.datatable.values;

import java.util.Objects;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * An enum that contains all known missing values.
 */
public enum MissingValue
{

    // R missing values
    /**
     * The R NA value
     */
    NA(10, "NA", "R NA (Not Available)"),

    // Python missing values

    /**
     * The Python None value for objects.
     */
    None(20, "None", "Python None"),

    /**
     * The Python np.nan value for float numbers.
     */
    NP_NAN(21, "np.nan", "Python NaN (from numpy)"),

    /**
     * The Python pd.NA value from pandas.
     */
    PD_NA(22, "pd.NA", "Pandas NA"),

    /**
     * The Python pd.NaT value from pandas.
     */
    PD_NAT(23, "pd.NaT", "Pandas NaT"),

    /**
     * The SAS special missing ._ &lt;dot&gt;&lt;underscore&gt;
     */
    MIS__(60, "._", "SAS Missing ._"),

    /**
     * The SAS default missing . &lt;dot&gt;&lt;space&gt;
     */
    MIS(64, ".", "SAS Missing ."),

    /**
     * The SAS special missing .A &lt;dot&gt;&lt;A&gt;
     */
    MIS_A(65, ".A", "SAS Missing .A"),

    /**
     * The SAS special missing .B &lt;dot&gt;&lt;B&gt;
     */
    MIS_B(66, ".B", "SAS Missing .B"),

    /**
     * The SAS special missing .C &lt;dot&gt;&lt;C&gt;
     */
    MIS_C(67, ".C", "SAS Missing .C"),

    /**
     * The SAS special missing .D &lt;dot&gt;&lt;D&gt;
     */
    MIS_D(68, ".D", "SAS Missing .D"),

    /**
     * The SAS special missing .E &lt;dot&gt;&lt;E&gt;
     */
    MIS_E(69, ".E", "SAS Missing .E"),

    /**
     * The SAS special missing .F &lt;dot&gt;&lt;F&gt;
     */
    MIS_F(70, ".F", "SAS Missing .F"),

    /**
     * The SAS special missing .G &lt;dot&gt;&lt;G&gt;
     */
    MIS_G(71, ".G", "SAS Missing .G"),

    /**
     * The SAS special missing .H &lt;dot&gt;&lt;H&gt;
     */
    MIS_H(72, ".H", "SAS Missing .H"),

    /**
     * The SAS special missing .I &lt;dot&gt;&lt;I&gt;
     */
    MIS_I(73, ".I", "SAS Missing .I"),

    /**
     * The SAS special missing .J &lt;dot&gt;&lt;J&gt;
     */
    MIS_J(74, ".J", "SAS Missing .J"),

    /**
     * The SAS special missing .K &lt;dot&gt;&lt;K&gt;
     */
    MIS_K(75, ".K", "SAS Missing .K"),

    /**
     * The SAS special missing .L &lt;dot&gt;&lt;L&gt;
     */
    MIS_L(76, ".L", "SAS Missing .L"),

    /**
     * The SAS special missing .M &lt;dot&gt;&lt;M&gt;
     */
    MIS_M(77, ".M", "SAS Missing .M"),

    /**
     * The SAS special missing .N &lt;dot&gt;&lt;N&gt;
     */
    MIS_N(78, ".N", "SAS Missing .N"),

    /**
     * The SAS special missing .O &lt;dot&gt;&lt;O&gt;
     */
    MIS_O(79, ".O", "SAS Missing .O"),

    /**
     * The SAS special missing .P &lt;dot&gt;&lt;P&gt;
     */
    MIS_P(80, ".P", "SAS Missing .P"),

    /**
     * The SAS special missing .Q &lt;dot&gt;&lt;Q&gt;
     */
    MIS_Q(81, ".Q", "SAS Missing .Q"),

    /**
     * The SAS special missing .R &lt;dot&gt;&lt;R&gt;
     */
    MIS_R(82, ".R", "SAS Missing .R"),

    /**
     * The SAS special missing .S &lt;dot&gt;&lt;S&gt;
     */
    MIS_S(83, ".S", "SAS Missing .S"),

    /**
     * The SAS special missing .T &lt;dot&gt;&lt;T&gt;
     */
    MIS_T(84, ".T", "SAS Missing .T"),

    /**
     * The SAS special missing .U &lt;dot&gt;&lt;U&gt;
     */
    MIS_U(85, ".U", "SAS Missing .U"),

    /**
     * The SAS special missing .V &lt;dot&gt;&lt;V&gt;
     */
    MIS_V(86, ".V", "SAS Missing .V"),

    /**
     * The SAS special missing .W &lt;dot&gt;&lt;W&gt;
     */
    MIS_W(87, ".W", "SAS Missing .W"),

    /**
     * The SAS special missing .X &lt;dot&gt;&lt;X&gt;
     */
    MIS_X(88, ".X", "SAS Missing .X"),

    /**
     * The SAS special missing .Y &lt;dot&gt;&lt;Y&gt;
     */
    MIS_Y(89, ".Y", "SAS Missing .Y"),

    /**
     * The SAS special missing .Z &lt;dot&gt;&lt;Z&gt;
     */
    MIS_Z(90, ".Z", "SAS Missing .Z"),

    /**
     * A missing value to be used for unexpected values / unknown missing. This should never be used
     * in normal conditions, but can signal an issue when retrieving data.
     */
    MIS_UNKNOWN(101, "<UKN>", "General unknown missing"),

    /**
     * A missing value that is to be used to signal an error condition.
     */
    MIS_ERROR(102, "<ERR>", "General error");

    /**
     * A byte value that is the missing code.
     */
    @Getter
    private final byte value;

    /**
     * The display string to be used to display the missing value.
     */
    @Getter
    private final String displayString;

    /**
     * A description that can be used to explain the missing value.
     */
    @Getter
    private final String description;

    /**
     * Internal constructor to create the enum values.
     *
     * @param aValue
     *            the byte value for the missing value.
     * @param aDisplayString
     *            the display string to be used to display the missing value.
     * @param aDescription
     *            a optional description of the missing value.
     */
    MissingValue(int aValue, String aDisplayString, String aDescription)
    {
        value = (byte) aValue;
        displayString = aDisplayString;
        description = aDescription;
    }

    /**
     * The bit shift used to position the missing-value byte inside a NaN mantissa. The byte is
     * stored in bits [50:43] — the highest 8 bits after the quiet-NaN flag (bit 51). This position
     * survives a {@code double→float→double} round-trip, because IEEE 754 float mantissa (23 bits)
     * maps to double mantissa bits [51:29].
     */
    private static final int NAN_PAYLOAD_SHIFT = 43;

    /**
     * Fast decode-free lookup for the NaN-payload hash path in numeric buffers. Indexed by the
     * unsigned byte value (0..255). Slot contains the pre-computed {@link #hashCodeStable()} for
     * known missing constants; 0 means "not a recognized missing payload" (safe sentinel because no
     * {@link MissingValue} constant has {@code value == 0} — lowest is {@link #NA} with 10).
     */
    static final int[] HASHES_BY_BYTE;

    /**
     * Precomputed lookup from the unsigned byte value (0..255) to the {@link MissingValue} constant
     * with that {@code value}, or {@code null} for unknown payloads. Used by
     * {@link #forValue(int, MissingValue)} to avoid a per-call {@code values()} allocation and
     * linear scan over every constant — this method sits on the per-cell numeric value path, so
     * shaving any overhead matters.
     */
    private static final MissingValue[] VALUES_BY_BYTE;
    static
    {
        HASHES_BY_BYTE = new int[256];
        VALUES_BY_BYTE = new MissingValue[256];
        for (MissingValue mv : values())
        {
            HASHES_BY_BYTE[mv.value & 0xFF] = mv.hashCodeStable();
            VALUES_BY_BYTE[mv.value & 0xFF] = mv;
        }
    }

    /**
     * Returns a double value that is a NaN and contains the encoded missing value.
     *
     * @return a double value that is a NaN and contains the encoded missing value.
     */
    public double asDouble()
    {
        long l = 0x7f_f8_00_00_00_00_00_00L | ((long) (value & 0xFF) << NAN_PAYLOAD_SHIFT);
        return Double.longBitsToDouble(l);
    }


    /**
     * A stable, deterministic hash for this missing-value constant. Derived from the
     * manually-assigned {@link #value} byte so it is robust against enum reordering and consistent
     * across JVM runs. Spread via a golden-ratio multiplier to avoid clustering with small
     * {@code long}/{@code int} hashes in a mixed-type hash space.
     *
     * @return the stable hash for this missing value. Never 0 for any existing constant (lowest
     *         value is {@link #NA} with {@code value = 10}).
     */
    public int hashCodeStable()
    {
        return (value & 0xFF) * 0x9E3779B1;
    }


    /**
     * Compute the stable hash for a NaN-encoded missing value without allocating or running a
     * linear enum scan. Extracts the byte payload from the NaN bits and looks it up in
     * {@link #HASHES_BY_BYTE}.
     *
     * @param aNan
     *            a NaN double whose payload should be decoded. Callers must have already checked
     *            {@link Double#isNaN(double)}.
     * @return the stable hash for the encoded missing value, or 0 if the payload does not match a
     *         known missing constant.
     */
    public static int hashCodeForNan(double aNan)
    {
        long bits = Double.doubleToRawLongBits(aNan);
        int byteVal = (int) ((bits >>> NAN_PAYLOAD_SHIFT) & 0xFF);
        return HASHES_BY_BYTE[byteVal];
    }


    @Override
    public String toString()
    {
        return displayString != null ? displayString : super.toString();
    }


    /**
     * Retrieve the missing value for the given byte value.
     *
     * @param aValue
     *            the value to retrieve the missing value for.
     * @return the missing value for the given byte value or {@link #MIS_ERROR} if the given value
     *         does not refer a valid missing value.
     */
    public static MissingValue forValue(int aValue)
    {
        // MIS_ERROR is non-null, so the overload never falls through to a null default here.
        return Objects.requireNonNull(forValue(aValue, MIS_ERROR));
    }


    /**
     * Retrieve the missing value for the given byte value.
     *
     * @param aValue
     *            the value to retrieve the missing value for.
     * @param aDefault
     *            the default value to be returned in case the given value is invalid.
     * @return the missing value for the given byte value or aDefault if the given value does not
     *         refer a valid missing value. If aDefault is null, this method might return null!
     */
    public static @Nullable MissingValue forValue(int aValue, @Nullable MissingValue aDefault)
    {
        MissingValue mv = VALUES_BY_BYTE[aValue & 0xFF];
        return mv != null ? mv : aDefault;
    }


    /**
     * Retrieve the missing value for the given double (if this is a NaN double with encoded missing
     * value) or return the given default.
     *
     * @param aDouble
     *            the double to retrieve the missing value from.
     * @param aDefault
     *            the default to be returned if the given double has not a valid missing value
     *            encoded.
     * @return the missing value that is encoded in the given double or the given missing value.
     */
    public static @Nullable MissingValue forValue(double aDouble, @Nullable MissingValue aDefault)
    {
        if (!Double.isNaN(aDouble))
        {
            return aDefault;
        }

        long bits = Double.doubleToRawLongBits(aDouble);
        int value = (int) ((bits >>> NAN_PAYLOAD_SHIFT) & 0xFF);
        return forValue(value, aDefault);
    }


    /**
     * Retrieve the instance for the given display string value.
     *
     * @param aValue
     *            the display string value to retrieve the missing value for.
     * @return the missing value for the given display string value or {@link #MIS_ERROR} if no
     *         matching missing value is found.
     */
    public static MissingValue forValue(String aValue)
    {
        return forValue(aValue, MIS_ERROR);
    }


    /**
     * Retrieve the instance for the given display string value.
     *
     * @param aValue
     *            the display string value to retrieve the missing value for.
     * @param aDefault
     *            the default to return in case no matching value is found.
     * @return the missing value for the given display string value or aDefault if no matching
     *         missing value is found.
     */
    public static MissingValue forValue(String aValue, MissingValue aDefault)
    {
        for (MissingValue mv : values())
        {
            if (Objects.equals(aValue, mv.displayString))
            {
                return mv;
            }
        }
        return aDefault;
    }
}
