package net.cumba.datatable.io;

/**
 * The type of a single {@link Property}. This is used by both provider and exporter properties to
 * hint the UI what types of values are allowed and how to display them to the user.
 */
public enum PropertyType
{

    /**
     * A String is basically a free text where only the name or the description might provide
     * further guidance.
     */
    STRING,

    /**
     * A integer number. This allows a number with no decimal places as value.
     */
    INTEGER,

    /**
     * A generic number. This allows a number with decimal places as value.
     */
    NUMBER,

    /**
     * A boolean. This allows the value to be the string returned by
     * {@link Boolean#toString(boolean)} of either true or false.
     */
    BOOLEAN,

    /**
     * A single allowed value from a list of values. This means that exactly 1 element of the list
     * must be selected.
     */
    ONE_OF,

    /**
     * One or more elements of a list can be selected as value.
     */
    SOME_OF,

    /**
     * Zero or more elements of a list can be selected as value.
     */
    ANY_OF,

    /**
     * A file reference. The value is a URI string <b>or</b> a local filesystem path pointing to a
     * file; consumers must accept both. A file chooser should be provided to allow the user to
     * select the file.
     *
     * <p>
     * ⚠ Both forms stay legal deliberately. These values cross the JSON-RPC wire and are persisted
     * in {@code coreCheck.history}, where a Windows {@code Path.toString()} is meaningless to a
     * Linux peer — so the URI form cannot be dropped. Equally, a consumer that hands the value to
     * {@code Path.of(...)} must convert a {@code file:} URI first: {@code Path.of("file:///x")}
     * raises {@code InvalidPathException} on Windows and silently yields a relative, non-existent
     * path elsewhere.
     * </p>
     */
    FILE,

    /**
     * A directory reference. The value is a URI string <b>or</b> a local filesystem path pointing
     * to a directory; consumers must accept both — see {@link #FILE}. A directory chooser should be
     * provided to allow the user to select the directory.
     */
    DIRECTORY,

    /**
     * A secret string such as a password or API token. The UI should display the value using a
     * password field that masks the entered characters.
     */
    PASSWORD,

    /**
     * An ordered list of file references. The value is a comma-separated list of entries, each a
     * URI string <b>or</b> a local filesystem path pointing to a file; consumers must accept both
     * and must convert <b>per element</b> — see {@link #FILE}. The UI should provide a dialog that
     * lets the user add files via a file chooser (with multi-selection) and remove previously added
     * entries.
     */
    FILES,

    /**
     * Opt-out multi-selection over a <em>dynamically resolved</em> universe of values: an empty
     * value means "use all" (no filter), a non-empty comma-separated list means "use only these".
     * The universe is fetched on demand via a {@link Property#dynamicResolverKey() dynamic resolver
     * key} because it depends on the other property values in the same dialog. An explicit NONE
     * (zero items after every row deselected) is not persistable — the UI should refuse to confirm
     * an empty selection.
     */
    SOME_OR_ALL_OF;
}
