package net.cumba.datatable.io;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * A Property is a configurable parameter that controls how a provider or exporter performs its
 * operation.<br/>
 * A property value is always transferred as string, so the only way to identify how the property
 * value is to be used is by providing a good {@link PropertyType}.
 *
 * @param name
 *            the property name. This is the name that should be displayed in the UI when asking for
 *            the value.
 * @param description
 *            an optional property description. This should be provided as tool tip in the UI.
 * @param type
 *            the property value type. This is a hint for the UI what types of values are allowed
 *            and possibly how to display it to the user.
 * @param defaultValue
 *            the default value that is used initially and as long as the user does not provide
 *            anything else.
 * @param allowedValues
 *            an optional list of possible values for {@link PropertyType#ONE_OF},
 *            {@link PropertyType#SOME_OF}, {@link PropertyType#ANY_OF}.
 * @param editable
 *            if {@code true} the {@link #allowedValues} list is a set of suggestions and the user
 *            may enter any other value. Currently only honoured for {@link PropertyType#ONE_OF}
 *            where the UI renders an editable combo box.
 * @param dependsOn
 *            optional name of a <b>controlling</b> property in the same property list. When set,
 *            the UI narrows this property's {@link #allowedValues} to
 *            {@link #conditionalAllowedValues}{@code .get(controllerValue)} whenever the
 *            controlling property's value changes. The fallback when the map has no entry for the
 *            current controller value is the plain {@link #allowedValues} list. A single controller
 *            is supported; multi-controller dependencies would require a richer key.
 * @param conditionalAllowedValues
 *            optional map keyed by the current value of the {@link #dependsOn} property, with the
 *            list of allowed values for this property under that controller value. Ignored when
 *            {@link #dependsOn} is blank.
 * @param dynamicResolverKey
 *            optional key identifying a backend-driven options resolver for
 *            {@link PropertyType#SOME_OR_ALL_OF}. The UI uses this key to ask the manager for the
 *            current universe of allowed values every time the picker is opened, passing the
 *            current values of all other properties in the dialog along with the key.
 * @param maxLength
 *            cap on the length of this property's <b>value</b>. Three-valued <b>on purpose</b>:
 *            {@code null} = the exporter did not declare one, {@code 0} = explicitly unbounded,
 *            {@code n > 0} = truncated at {@code n}. Declared by exporters whose file format
 *            truncates what the property carries — the XPT dataset-name property stores 8
 *            characters, the Excel sheet-name property 31, and the CDT one is explicitly unbounded.
 *            This makes the exporter the single authority on its own limits: a caller writing
 *            several tables into one file reads the budget from here rather than restating
 *            per-format knowledge, and must both fit the names into it and keep them unique within
 *            it (see {@code MemberNameUniquifier}) — otherwise two members collapse onto the same
 *            stored name and one of the datasets is silently lost.
 *            <p>
 *            {@code null} is what distinguishes <em>"unknown"</em> from <em>"uncapped"</em>, and
 *            that distinction is load-bearing across a client/backend version skew: this record
 *            crosses the JSON-RPC wire, and a peer that predates this component simply omits the
 *            field. Were the type primitive, the omission would arrive as {@code 0} — silently
 *            indistinguishable from "no limit" — and the caller would skip de-duplication entirely,
 *            losing a dataset with no diagnostic. With {@code Integer} the caller can detect the
 *            undeclared case and warn; see
 *            {@code MultiTableExportProperties#isMemberNameLimitDeclared}.
 *            </p>
 *            Set it with {@code Property.forString(...).withMaxLength(n)}.
 */
@Jacksonized
@Builder(toBuilder = true)
@With
public record Property(String name, String description, PropertyType type, String defaultValue,
        @Nullable List<String> allowedValues, boolean editable, @Nullable String dependsOn,
        @Nullable Map<String, List<String>> conditionalAllowedValues,
        @Nullable String dynamicResolverKey, @Nullable Integer maxLength)
{

    public static Property forString(String aName, String aDescription, String aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.STRING, aDefaultValue, null, false,
                null, null, null, null);
    }


    public static Property forBoolean(String aName, String aDescription, boolean aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.BOOLEAN,
                Boolean.toString(aDefaultValue), null, false, null, null, null, null);
    }


    public static Property forInteger(String aName, String aDescription, long aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.INTEGER, Long.toString(aDefaultValue),
                null, false, null, null, null, null);
    }


    public static Property forNumber(String aName, String aDescription, double aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.NUMBER,
                Double.toString(aDefaultValue), null, false, null, null, null, null);
    }


    public static Property forOneOf(String aName, String aDescription, String aDefaultValue,
            String... aValues)
    {
        return new Property(aName, aDescription, PropertyType.ONE_OF, aDefaultValue,
                List.of(aValues), false, null, null, null, null);
    }


    public static Property forOneOf(String aName, String aDescription, String aDefaultValue,
            List<String> aValues)
    {
        return new Property(aName, aDescription, PropertyType.ONE_OF, aDefaultValue, aValues, false,
                null, null, null, null);
    }


    /**
     * Create a {@link PropertyType#ONE_OF} property whose {@code aValues} list is a set of
     * suggestions only. The UI will render an editable combo box so the user can enter any custom
     * value in addition to the suggested ones.
     */
    public static Property forOneOfOrCustom(String aName, String aDescription, String aDefaultValue,
            String... aValues)
    {
        return new Property(aName, aDescription, PropertyType.ONE_OF, aDefaultValue,
                List.of(aValues), true, null, null, null, null);
    }


    public static Property forOneOfOrCustom(String aName, String aDescription, String aDefaultValue,
            List<String> aValues)
    {
        return new Property(aName, aDescription, PropertyType.ONE_OF, aDefaultValue, aValues, true,
                null, null, null, null);
    }


    public static Property forSomeOf(String aName, String aDescription, String aDefaultValue,
            String... aValues)
    {
        return new Property(aName, aDescription, PropertyType.SOME_OF, aDefaultValue,
                List.of(aValues), false, null, null, null, null);
    }


    public static Property forSomeOf(String aName, String aDescription, String aDefaultValue,
            List<String> aValues)
    {
        return new Property(aName, aDescription, PropertyType.SOME_OF, aDefaultValue, aValues,
                false, null, null, null, null);
    }


    public static Property forAnyOf(String aName, String aDescription, String aDefaultValue,
            String... aValues)
    {
        return new Property(aName, aDescription, PropertyType.ANY_OF, aDefaultValue,
                List.of(aValues), false, null, null, null, null);
    }


    public static Property forAnyOf(String aName, String aDescription, String aDefaultValue,
            List<String> aValues)
    {
        return new Property(aName, aDescription, PropertyType.ANY_OF, aDefaultValue, aValues, false,
                null, null, null, null);
    }


    public static Property forPassword(String aName, String aDescription, String aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.PASSWORD, aDefaultValue, null, false,
                null, null, null, null);
    }


    public static Property forDirectory(String aName, String aDescription, String aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.DIRECTORY, aDefaultValue, null, false,
                null, null, null, null);
    }


    /**
     * Create a {@link PropertyType#FILE} property. The value is a URI string pointing to a single
     * file; the UI renders a text field plus a file-chooser button.
     */
    public static Property forFile(String aName, String aDescription, String aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.FILE, aDefaultValue, null, false,
                null, null, null, null);
    }


    /**
     * Create a {@link PropertyType#FILES} property. The value is a comma-separated list of file
     * URIs; the UI renders a text field plus a picker dialog for managing the list.
     */
    public static Property forFiles(String aName, String aDescription, String aDefaultValue)
    {
        return new Property(aName, aDescription, PropertyType.FILES, aDefaultValue, null, false,
                null, null, null, null);
    }


    /**
     * Create a {@link PropertyType#SOME_OR_ALL_OF} property whose universe is resolved at runtime
     * through the given {@code aDynamicResolverKey}. Empty default = "all"; the UI's picker button
     * fetches the current universe every time it is opened and lets the user narrow the selection
     * to a subset.
     */
    public static Property forSomeOrAllOff(String aName, String aDescription, String aDefaultValue,
            String aDynamicResolverKey)
    {
        return new Property(aName, aDescription, PropertyType.SOME_OR_ALL_OF, aDefaultValue, null,
                false, null, null, aDynamicResolverKey, null);
    }
}
