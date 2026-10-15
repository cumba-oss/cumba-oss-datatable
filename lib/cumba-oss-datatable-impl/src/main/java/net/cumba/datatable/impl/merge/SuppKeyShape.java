package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyDropWarning;
import net.cumba.datatable.merge.MalformedSidedKeyException;
import net.cumba.datatable.merge.RelrecLinks;

/**
 * The resolved shape of a supplemental merge's key ({@code PLAN-merged-evaluation-table} §3.5.1,
 * MET #19, P-15), decided once from the two tables' metadata:
 *
 * <ul>
 * <li>a plain component ({@code STUDYID}, {@code {left: DOMAIN, right: RDOMAIN}}, ...) is used when
 * present on both sides; present on one side only it is ignored with a log WARNING (the
 * {@link KeyDropWarning} wording every key drop shares, {@code REG-Q3}); present on neither it is
 * ignored. {@code {left: DOMAIN, right: RDOMAIN}} with no {@code DOMAIN} column on the base
 * compares {@code RDOMAIN} against the base's domain (P-15);</li>
 * <li>a <b>subject</b> component ({@code USUBJID}, {@code POOLID}, {@code APID}, {@code SPDEVID})
 * is a plain component whose absence everywhere is an error for every qualifier: with no subject
 * key usable, nothing identifies the record. Whether a supplemental row names a subject at all is
 * decided on the row's OWN subject columns — every named one the supplemental dataset carries,
 * active or not (T2 review L2: a pool's row over a base without {@code POOLID} read as subject-less
 * and qualified every subject);</li>
 * <li>the {@code IDVAR} / {@code IDVARVAL} <b>pair</b> is recognised by its right-side names, must
 * be named whole (P-15), and is active when the supplemental dataset carries both columns; a
 * supplemental dataset without them keys every row at subject level.</li>
 * </ul>
 */
final class SuppKeyShape
{

    private static final Logger LOGGER = System.getLogger(SuppKeyShape.class.getName());

    private static final String IDVAR = "IDVAR";

    private static final String IDVARVAL = "IDVARVAL";

    /**
     * The IG's subject-key column names (P-8): the RELREC subject identifiers
     * ({@link RelrecLinks#SUBJECT_COLUMNS}), one set for both merges.
     */
    static final Set<String> SUBJECT_NAMES = Set.copyOf(RelrecLinks.SUBJECT_COLUMNS);

    /**
     * One active plain component: the base column ({@code -1} when the base's domain constant
     * stands in), the supplemental column and whether it is a subject component.
     *
     * @param left
     *            the base column name.
     * @param baseColumn
     *            the base column index, {@code -1} for the domain constant.
     * @param suppColumn
     *            the supplemental column index.
     * @param subject
     *            whether it identifies the subject (or pool, device, ...).
     */
    record Component(String left, int baseColumn, int suppColumn, boolean subject)
    {
    }

    private final List<Component> components;

    private final int suppIdvarColumn;

    private final int suppIdvarvalColumn;

    private final boolean subjectUsable;

    private final String baseDomain;

    /** The supplemental dataset's own subject columns the key names, active or not. */
    private final int[] suppSubjectColumns;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private SuppKeyShape(List<Component> aComponents, int aSuppIdvarColumn, int aSuppIdvarvalColumn,
            boolean aSubjectUsable, String aBaseDomain, int[] aSuppSubjectColumns)
    {
        components = List.copyOf(aComponents);
        suppIdvarColumn = aSuppIdvarColumn;
        suppIdvarvalColumn = aSuppIdvarvalColumn;
        subjectUsable = aSubjectUsable;
        baseDomain = aBaseDomain;
        suppSubjectColumns = aSuppSubjectColumns;
    }


    /**
     * Resolves the key over the two tables.
     *
     * @param aBase
     *            the dataset the qualifiers belong to.
     * @param aBaseDomain
     *            its domain, compared against {@code RDOMAIN} when the base has no {@code DOMAIN}
     *            column.
     * @param aSupp
     *            the supplemental dataset.
     * @param aSuppName
     *            its name, for messages.
     * @param aKeys
     *            the key in sided form.
     * @return the shape.
     * @throws MalformedSidedKeyException
     *             when only one half of the {@code IDVAR} / {@code IDVARVAL} pair is named, or the
     *             key is empty.
     */
    static SuppKeyShape resolve(IDataTable aBase, String aBaseDomain, IDataTable aSupp,
            String aSuppName, List<KeyColumn> aKeys)
    {
        if (aKeys.isEmpty())
        {
            throw new MalformedSidedKeyException(aSuppName, "no key column is declared");
        }
        DataTableMeta bm = aBase.getMetaData();
        DataTableMeta sm = aSupp.getMetaData();
        boolean namesIdvar = false;
        boolean namesIdvarval = false;
        List<Component> components = new ArrayList<>();
        List<Integer> suppSubjectColumns = new ArrayList<>();
        boolean anySubjectNamed = false;
        boolean subjectUsable = false;
        for (KeyColumn key : aKeys)
        {
            String right = key.right().toUpperCase(Locale.ROOT);
            if (IDVAR.equals(right))
            {
                namesIdvar = true;
                continue;
            }
            if (IDVARVAL.equals(right))
            {
                namesIdvarval = true;
                continue;
            }
            boolean subject = SUBJECT_NAMES.contains(right);
            anySubjectNamed |= subject;
            int suppCol = sm.getColumnIndex(key.right());
            int baseCol = bm.getColumnIndex(key.left());
            if (subject && suppCol >= 0)
            {
                suppSubjectColumns.add(suppCol);
            }
            boolean domainConstant = baseCol < 0 && "DOMAIN".equalsIgnoreCase(key.left())
                    && "RDOMAIN".equals(right);
            if (suppCol >= 0 && (baseCol >= 0 || domainConstant))
            {
                components.add(new Component(key.left(), baseCol, suppCol, subject));
                subjectUsable |= subject;
            }
            else if (suppCol >= 0 || baseCol >= 0)
            {
                LOGGER.log(Level.WARNING, KeyDropWarning.TEMPLATE, aSuppName,
                        KeyDropWarning.keyText(key.left(), key.right()),
                        suppCol >= 0 ? aSuppName : KeyedMatcher.nameOf(aBase));
            }
        }
        if (namesIdvar != namesIdvarval)
        {
            throw new MalformedSidedKeyException(aSuppName,
                    "the IDVAR / IDVARVAL pair must be named whole, found only "
                            + (namesIdvar ? IDVAR : IDVARVAL));
        }
        int idvarCol = -1;
        int idvarvalCol = -1;
        if (namesIdvar)
        {
            int i = sm.getColumnIndex(IDVAR);
            int v = sm.getColumnIndex(IDVARVAL);
            if (i >= 0 && v >= 0)
            {
                idvarCol = i;
                idvarvalCol = v;
            }
        }
        // A key naming no subject component at all is an authored choice (the browser's SUPPDM
        // keys name USUBJID; a key of STUDYID alone would be the author's); only a NAMED subject
        // component that no side can serve is the "no subject key usable" error.
        return new SuppKeyShape(components, idvarCol, idvarvalCol,
                subjectUsable || !anySubjectNamed, aBaseDomain,
                suppSubjectColumns.stream().mapToInt(Integer::intValue).toArray());
    }


    /** The active plain components. */
    List<Component> components()
    {
        return components;
    }


    /** Whether the {@code IDVAR} / {@code IDVARVAL} pair takes part. */
    boolean pairActive()
    {
        return suppIdvarColumn >= 0;
    }


    /** The supplemental {@code IDVAR} column, {@code -1} when the pair is inactive. */
    int suppIdvarColumn()
    {
        return suppIdvarColumn;
    }


    /** The supplemental {@code IDVARVAL} column, {@code -1} when the pair is inactive. */
    int suppIdvarvalColumn()
    {
        return suppIdvarvalColumn;
    }


    /** Whether some subject component is usable (or none was named). */
    boolean subjectUsable()
    {
        return subjectUsable;
    }


    /** Whether some active component is a subject component. */
    boolean hasSubjectComponents()
    {
        return plainCount() < components.size();
    }


    /** The number of plain components that are not subject components. */
    int plainCount()
    {
        int n = 0;
        for (Component c : components)
        {
            if (!c.subject())
            {
                n++;
            }
        }
        return n;
    }


    /**
     * The key identities of a base row: the plain (non-subject) components first, then the subject
     * components.
     *
     * @param aBase
     *            the base.
     * @param aRow
     *            the row.
     * @return the identities, plain then subject.
     */
    Object[] baseKey(IDataTable aBase, long aRow)
    {
        Object[] out = new Object[components.size()];
        int p = 0;
        int s = plainCount();
        for (Component c : components)
        {
            Object id = c.baseColumn() < 0 ? baseDomain
                    : RelrecLinks.keyCell(aBase, c.baseColumn(), aRow);
            if (c.subject())
            {
                out[s++] = id;
            }
            else
            {
                out[p++] = id;
            }
        }
        return out;
    }


    /**
     * The key identities of a supplemental row, laid out as {@link #baseKey}.
     *
     * @param aSupp
     *            the supplemental dataset.
     * @param aRow
     *            the row.
     * @return the identities, plain then subject.
     */
    Object[] suppKey(IDataTable aSupp, long aRow)
    {
        Object[] out = new Object[components.size()];
        int p = 0;
        int s = plainCount();
        for (Component c : components)
        {
            Object id = RelrecLinks.keyCell(aSupp, c.suppColumn(), aRow);
            if (c.subject())
            {
                out[s++] = id;
            }
            else
            {
                out[p++] = id;
            }
        }
        return out;
    }


    /**
     * Whether a supplemental row names no subject — every subject column the key names and the
     * dataset carries (active in the key or not) is {@code ""} or a missing value.
     *
     * @param aSupp
     *            the supplemental dataset.
     * @param aRow
     *            the row.
     * @return whether the row names neither a subject nor a pool (nor a device, ...).
     */
    boolean suppSubjectBlank(IDataTable aSupp, long aRow)
    {
        for (int col : suppSubjectColumns)
        {
            if (RelrecLinks.isNonBlank(RelrecLinks.cell(aSupp, col, aRow)))
            {
                return false;
            }
        }
        return true;
    }


    /**
     * The plain half of a key.
     *
     * @param aKey
     *            a full key.
     * @return its plain components.
     */
    Object[] plainOf(Object[] aKey)
    {
        Object[] out = new Object[plainCount()];
        System.arraycopy(aKey, 0, out, 0, out.length);
        return out;
    }


    /**
     * A description of the active components for messages.
     *
     * @return e.g. {@code STUDYID, DOMAIN/RDOMAIN, USUBJID, IDVAR/IDVARVAL}.
     */
    String describe()
    {
        StringBuilder sb = new StringBuilder();
        for (Component c : components)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(c.left());
        }
        if (pairActive())
        {
            sb.append(sb.length() > 0 ? ", " : "").append("IDVAR/IDVARVAL");
        }
        return sb.toString();
    }
}
