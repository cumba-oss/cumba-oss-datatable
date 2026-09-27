package net.cumba.datatable.values;

// OSS-IDENTITY datatable-key-identity: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The grouping-key policy: what a <b>missing value in a grouping key column that exists</b> means,
 * and — through {@link KeyPart} — <b>the one definition of join/group key identity</b> in the
 * stack.
 *
 * <p>
 * ⭐ <b>Why it lives in {@code cumba-datatable}</b> ({@code PLAN-shared-key-identity}, owner ruling
 * D1 (b), 2026-09-27: <i>"the whole GroupKeyPolicy, something that belongs together should be kept
 * together. And maybe we use it some more in DataTable later on."</i>). It was born inside the
 * conformance engine, but the values it classifies ({@link IDataValue}, {@link MissingValue},
 * {@link DataValueSupport}) live here, and so do two of its consumers' only reachable types: the
 * BASIC manager ({@code cumba-datatable-manager-local}'s {@code RelrecRelationshipResolver}) and
 * the data browser's RELREC dialog ({@code RelrecDialogSupport}) must not depend on the engine, so
 * until this move each carried a hand-kept string copy of the RELREC key encoding. The engine, the
 * manager and the dialog now classify through this one type. It reads only the public value API, so
 * it has no engine dependency and no storage dependency.
 * </p>
 *
 * <p>
 * ⚑ <b>Byte-identical in {@code cumba-oss-datatable}</b> (ruling D3 (a), pinned by the meta repo's
 * {@code check_oss_identity.py datatable-key-identity}). The OSS twin's {@code createDataValue}
 * differs — a {@code NaN} in a {@code LONG} column is a present {@code 0} there, a missing value
 * here — but that is <b>upstream</b> of this classifier: it decides which {@link IDataValue} a cell
 * <em>is</em>, and this type only classifies the value it is handed. ⛔ Do not "fix" that difference
 * in this file; it would fork the one file the two twins share.
 * </p>
 *
 * <p>
 * Before this type the engine answered the missing-key question in eight independent places, three
 * of which kept the row under a {@code ""} key while five discarded the whole group, and it
 * implemented "is this key component missing?" <b>six</b> separate times — twice inside a single
 * method (the engine's {@code GroupSemantics.componentKeyValue}, its singleton and coalesce
 * branches). Every grouping path now routes its decision through one object and one predicate,
 * {@link #isBlankKeyComponent(IDataValue)}.
 * </p>
 *
 * <p>
 * <b>Two orthogonal axes.</b> They are deliberately separate because conflating them is exactly
 * what produced the divergence:
 * </p>
 * <ol>
 * <li>{@link #keepMissings()} — <b>the authored axis</b>. Does a blank key component keep its row
 * in a group (under the blank's own {@link KeyPart} identity), or drop the row/group entirely? This
 * is the axis the rule declares as {@code keep_missings}. A missing value is a valid value of a
 * variable, so the direction of travel is {@code true}; the per-site defaults preserve today's
 * behaviour until the default is flipped.</li>
 * <li>{@link #blankness()} — <b>an internal, per-site descriptor and NOT authorable</b>. Which
 * cells count as blank at all. ⭐ <b>Two</b> notions exist since {@code W32-E3} retired
 * {@code MISSING_ONLY} (2026-08-12); each is load-bearing for at least one operator, so they are
 * named here rather than silently collapsed.</li>
 * </ol>
 *
 * <p>
 * ⚠ Do not promote {@link Blankness} to an authoring parameter. {@code keep_missings} is a boolean
 * on purpose (an enum would imply a third disposition exists); {@code Blankness} describes what the
 * <em>operator family</em> already means by "blank" and is fixed per call site.
 * </p>
 *
 * <p>
 * ⚠⚠ The engine's {@code IndexHelper.buildGroupKey} and {@code IndexHelper.isBlockKeyMissing} both
 * consult this policy but remain <b>two functions</b>, and must stay that way.
 * {@code buildGroupKey} builds the <em>reporting key</em> of a group that has already been formed;
 * {@code isBlockKeyMissing} decides whether the group is formed at all. They answer different
 * questions and merely happen to read the same cells. Unifying them would be a bug, not a tidy-up.
 * </p>
 *
 * @param keepMissings
 *            {@code true} to keep a row whose key component is blank (the blank is a real key
 *            value)
 * @param blankness
 *            which cells count as blank for this call site
 */
public record GroupKeyPolicy(boolean keepMissings, Blankness blankness)
{

    /**
     * Which cells count as a blank key component. Fixed per call site by the operator family — see
     * the class comment's warning against making this authorable.
     */
    public enum Blankness
    {

        /**
         * A genuine missing marker <b>or</b> {@code ""} is blank
         * ({@link DataValueSupport#isEmptyOrMissing} — character variables cannot be null in SAS,
         * so a blank cell arrives as {@code ""}).
         *
         * <p>
         * ⭐ <b>Since {@code W32-E3} (owner ruling 2026-08-12) this is the notion for <em>every</em>
         * grouping key.</b> The former {@code MISSING_ONLY} — <i>"only a genuine marker is blank;
         * {@code ""} is a real key value"</i> — is <b>retired</b>: it made one
         * {@code keep_missings} declaration mean different things depending on how a blank happened
         * to be stored, which the author cannot see. Owner: <i>"a {@code MissingValue} is a valid
         * value to be handled — this is why we introduced {@code keep_missings}. It was not meant
         * to differentiate between blank char variables and missing numerics."</i>
         * </p>
         *
         * <p>
         * ⚑ This also aligns the grouping path with the standing <b>{@code missing ≡ empty}</b>
         * policy of 2026-08-04, which it had contradicted ever since.
         * </p>
         */
        MISSING_OR_EMPTY,

        /**
         * As {@link #MISSING_OR_EMPTY}, and a whitespace-only value is blank too. The EC-24
         * coalesce-component notion: a pooled record carries a blank {@code USUBJID} and identifies
         * by {@code POOLID}, so {@code within: [[USUBJID, POOLID]]} must fall through a blank —
         * even a space-filled — {@code USUBJID}.
         */
        MISSING_OR_WHITESPACE
    }


    /**
     * One <b>grouping-key component</b> as a typed identity — the {@code W38-A1} composite key
     * (owner, 2026-08-13: <i>"lets go with the composite key, it can't collide by
     * construction"</i>; Fix #249 / EC-75).
     *
     * <p>
     * Equality of {@code KeyPart}s <em>is</em> the grouping-identity relation the owner ruled:
     * </p>
     * <ol>
     * <li>for <em>filtering</em> ({@code keep_missings}), {@code ""} and a {@link MissingValue} are
     * both blank — the {@link GroupKeyPolicy#isBlankKeyComponent} bucket is unchanged;</li>
     * <li>wherever blanks are <em>kept</em>, they form <b>separate</b> groups — {@link Empty}
     * {@code ≠} {@link Missing};</li>
     * <li>different {@code MissingValue}s do not fold together — {@code Missing(MIS) ≠
     * Missing(MIS_UNKNOWN)}, and {@code JKM R5} (owner, 2026-09-21): <i>"a MIS will not join a
     * record with an empty string and a MIS_A will not join a record with a MIS or MIS_B"</i>;</li>
     * <li>a {@code MissingValue} is <b>never</b> equal to any String value — in particular not to a
     * literal {@code "."} ({@code Missing(MIS) ≠ Present(".")}), which a
     * {@code getValueAsString()}-based sentinel encoding would have got wrong.</li>
     * </ol>
     *
     * <p>
     * ⭐ <b>Why a sealed type and not a sentinel string.</b> A sentinel is <em>unlikely</em> to
     * collide with a real value; a record <b>cannot</b> — for every possible input. Part 4 says
     * <i>"never"</i>, and only the composite delivers "never".
     * </p>
     *
     * <p>
     * ⭐⭐ <b>And it deletes the emptiness-inference bug class.</b> The D.2 / D.13 emptiness
     * exceptions used to infer blankness by re-reading the <em>rendered</em> key
     * ({@code strip().isEmpty()} on the folded string) — which is exactly why a naive
     * distinct-as-strings encoding silently turned every missing into a participating value
     * (measured: +9 528 findings, 8 rules). With {@code KeyPart} the question is a <b>type
     * test</b>: {@code part instanceof Present}. Blankness can no longer be mis-read from how a
     * value happens to render.
     * </p>
     *
     * <p>
     * ⭐ <b>The identity object</b> ({@code PLAN-shared-key-identity}, owner ruling D2 (c),
     * 2026-09-27). {@code KeyPart} is the type every consumer classifies through; an index that
     * holds one key per row keys on {@link #identity()} instead — the part's text for
     * {@link Present}, its canonical {@link Double} for {@link PresentNumber}, {@code ""} for
     * {@link Empty}, the {@link MissingValue} constant for {@link Missing}. That object is a
     * <b>bijection</b> with {@code KeyPart} ({@link #ofIdentity(Object)} is its inverse): a
     * {@code String}, a {@code Double} and an enum constant never compare equal, so it keeps the
     * "cannot collide" of part 4 and the char {@code ≠} num of {@code D4-R1}, while a text or blank
     * cell needs no wrapper at all. {@link GroupKeyPolicy#textKeyIdentity(IDataValue)} reads the
     * text-join identity straight from a cell without building a part.
     * </p>
     *
     * <p>
     * ⚠⚠ {@link #reportingForm()} is <b>presentation only</b>, derived from the {@code KeyPart} —
     * it must <b>never</b> be re-parsed to recover identity. Identity lives in the type, and in
     * {@link #identity()}.
     * </p>
     */
    public sealed interface KeyPart
    {

        /** A real <b>text</b> value — <b>including</b> {@code "."} and whitespace-only strings. */
        record Present(String value) implements KeyPart
        {

            public Present
            {
                // "" is Empty's identity, never Present's — two spellings of one identity would
                // re-open the collision class this type exists to close. Loud beats silent.
                if (value.isEmpty())
                {
                    throw new IllegalArgumentException(
                            "an empty string is KeyPart.EMPTY, not a Present value");
                }
            }


            @Override
            public String reportingForm()
            {
                return value;
            }


            @Override
            public boolean present()
            {
                return true;
            }


            @Override
            public Object identity()
            {
                return value;
            }


            @Override
            public KeyPart asText()
            {
                return this;
            }
        }


        /**
         * A real <b>numeric</b> value, keyed by its exact double (D84a / D64h: <i>"key identity
         * always exact"</i>).
         *
         * <p>
         * ⭐ Until D84a a numeric cell keyed as {@code Present(dv.getValueAsString())}, and
         * {@code DataValueDouble.getValueAsString()} runs {@code getAsDoubleCleaned}
         * unconditionally — which, until {@code PLAN-numeric-cleaning-and-key-text} (E7,
         * 2026-09-25), rounded to 12 significant digits with a threshold that scaled with the
         * magnitude twice — so two values the data distinguishes could fold into one group with
         * nothing red. The identity is now the value itself; two values that differ at all are
         * <b>different keys</b>.
         * </p>
         *
         * <p>
         * {@link #reportingForm()} renders {@link DataValueSupport#toCleanText}, the very text of
         * {@code DataValueDouble.getValueAsString()}: presentation (and the lockstep rendered-key
         * encoding of the engine's {@code GroupedResult.buildKey} /
         * {@code IndexHelper.buildGroupKey}), never re-parsed for identity. Since E7 that text is
         * lossless outside the ruled noise (integral values exact, noise within {@code 1e-12} of
         * the value's decade folded, plain notation), so the text-keyed sites distinguish what this
         * identity distinguishes, up to that noise.
         * </p>
         *
         * <p>
         * ⚠ One record for {@code LONG} and {@code DOUBLE} cells alike: a {@code LONG} {@code 2}
         * and a {@code DOUBLE} {@code 2.0} keyed identically before (both rendered {@code "2"}) and
         * must keep doing so. A {@code long} beyond 2^53 loses exactness in the double — far beyond
         * any clinical value, and the cleaned text was coarser still.
         * </p>
         */
        record PresentNumber(double value) implements KeyPart
        {

            public PresentNumber
            {
                value = presentKeyNumber(value);
            }


            @Override
            public String reportingForm()
            {
                // The cell's own text (DataValueDouble.getValueAsString): cleaned of noise, plain
                // notation, integral values without the ".0" — one rendering, shared.
                return DataValueSupport.toCleanText(value);
            }


            @Override
            public boolean present()
            {
                return true;
            }


            @Override
            public Object identity()
            {
                return value;
            }


            @Override
            public KeyPart asText()
            {
                return new Present(reportingForm());
            }
        }


        /**
         * The literal {@code ""} (and, under a whitespace-aware notion, a whitespace-only cell).
         */
        record Empty() implements KeyPart
        {

            @Override
            public String reportingForm()
            {
                return "";
            }


            @Override
            public boolean present()
            {
                return false;
            }


            @Override
            public Object identity()
            {
                return "";
            }


            @Override
            public KeyPart asText()
            {
                return this;
            }
        }


        /**
         * A genuine missing marker — {@code MIS} / {@code MIS_UNKNOWN} / {@code MIS_ERROR} or one
         * of the SAS special missings {@code ._} and {@code .A}–{@code .Z}.
         */
        record Missing(MissingValue marker) implements KeyPart
        {

            @Override
            public String reportingForm()
            {
                return "\u0001" + marker.name();
            }


            @Override
            public boolean present()
            {
                return false;
            }


            @Override
            public Object identity()
            {
                return marker;
            }


            @Override
            public KeyPart asText()
            {
                return this;
            }
        }

        // ⚠ These constants instantiate KeyPart's own subclasses, so KeyPart must declare NO
        // default methods: with one, a subclass's initialization would trigger the interface's
        // (JLS 12.4.1) while the interface's initializer needs the subclass — the Error Prone
        // ClassInitializationDeadlock cycle. Every instance method is therefore implemented per
        // record.

        /** Interned {@link Empty} — only {@link Present} allocates on the keying path (§6.4). */
        KeyPart EMPTY = new Empty();

        /** Interned {@link Missing Missing(MIS)}. */
        KeyPart MISSING_MIS = new Missing(MissingValue.MIS);

        /** Interned {@link Missing Missing(MIS_UNKNOWN)}. */
        KeyPart MISSING_UNKNOWN = new Missing(MissingValue.MIS_UNKNOWN);

        /** Interned {@link Missing Missing(MIS_ERROR)}. */
        KeyPart MISSING_ERROR = new Missing(MissingValue.MIS_ERROR);

        /**
         * The {@link Missing} part for {@code marker}, interned for the three markers the engine
         * actually meets in rule data.
         *
         * <p>
         * ⚠ The three interned cases were once the WHOLE enum: the coreJ monorepo's own
         * {@code net.cumba.datatable} carried a reduced {@code MissingValue} with exactly
         * {@code MIS} / {@code MIS_UNKNOWN} / {@code MIS_ERROR}, so this switch was exhaustive and
         * the method never allocated. The full {@code MissingValue} carries the complete SAS
         * special-missing set — {@code ._} and {@code .A}–{@code .Z}, 31 constants — so the switch
         * needs a default arm and the 28 SAS markers allocate.
         * </p>
         *
         * <p>
         * That is safe rather than merely tolerable: {@code Missing} is a record, so equality and
         * {@code hashCode} are by value and an allocated part compares equal to an interned one.
         * Only the {@code assertSame} identity the case tables assert for the three hot markers
         * depends on interning, and those three keep it.
         * </p>
         *
         * @param marker
         *            the missing-value marker
         * @return the {@code Missing} part for {@code marker}, interned where one exists
         */
        static KeyPart missing(MissingValue marker)
        {
            return switch (marker)
            {
            case MIS -> MISSING_MIS;
            case MIS_UNKNOWN -> MISSING_UNKNOWN;
            case MIS_ERROR -> MISSING_ERROR;
            default -> new Missing(marker);
            };
        }


        /**
         * The part whose {@link #identity()} is {@code aIdentity} — the inverse of the bijection
         * (ruling D2 (c)): a non-empty {@code String} is {@link Present}, {@code ""} is
         * {@link #EMPTY}, a {@link MissingValue} is its {@link Missing}, a {@link Double} is its
         * {@link PresentNumber}.
         *
         * @param aIdentity
         *            an identity object as {@link #identity()} returns one
         * @return the part it identifies
         * @throws IllegalArgumentException
         *             for any other object, and for a {@code Double} no part can have as its
         *             identity — a {@code NaN} (a missing encoding) or {@code -0.0} (canonicalised
         *             to {@code 0.0}); a {@code Long}, an {@code Integer} or a {@code Boolean} is
         *             never an identity, a present {@code LONG} cell's identity is its double
         */
        static KeyPart ofIdentity(Object aIdentity)
        {
            if (aIdentity instanceof String s)
            {
                return s.isEmpty() ? EMPTY : new Present(s);
            }
            if (aIdentity instanceof MissingValue m)
            {
                return missing(m);
            }
            if (aIdentity instanceof Double d)
            {
                PresentNumber part = new PresentNumber(d);
                if (!d.equals(part.identity()))
                {
                    throw new IllegalArgumentException(
                            "-0.0 is not a key identity: the identity of a zero is 0.0");
                }
                return part;
            }
            throw new IllegalArgumentException(
                    "not a key identity: " + aIdentity.getClass().getName());
        }


        /**
         * The <b>reporting-key</b> rendering of this component — what the engine's
         * {@code IndexHelper.buildGroupKey} and {@code GroupedResult.buildKey} join into the
         * per-group lookup/report key, so the reporting key distinguishes exactly what the grouping
         * distinguishes: {@link Present} renders its value, {@link Empty} renders {@code ""}, and
         * {@link Missing} renders {@code "\\u0001" + marker.name()} — an SOH-prefixed token that no
         * clinical string value can equal (control characters cannot occur in cell text, the same
         * argument the {@code NUL} key separator already rests on).
         *
         * <p>
         * ⚠⚠ <b>Presentation only — never re-parse this to recover identity, and never key an index
         * on it.</b> Consumers compare whole rendered keys for equality (or against authored
         * real-string values, which the {@code Missing} token can never equal — ruling part 4);
         * nothing may split or interpret one. An index keys on {@link #identity()}.
         * </p>
         *
         * @return the rendered component
         */
        String reportingForm();


        /**
         * Whether this component is a real value — {@link Present} or {@link PresentNumber} — as
         * opposed to a blank identity ({@link Empty}, {@link Missing}). The type test every
         * participation / blank-exclusion site asks, spelled once so a new present-shaped record
         * cannot silently fall out of those sites (implemented per record: KeyPart may declare no
         * default methods — see the class-initialization note above the interned constants).
         *
         * @return whether the component carries a real value
         */
        boolean present();


        /**
         * This part's canonical <b>identity object</b> (ruling D2 (c)): {@link Present}'s text,
         * {@link PresentNumber}'s value as a {@link Double} (never {@code NaN}, never
         * {@code -0.0}), {@code ""} for {@link Empty}, the {@link MissingValue} constant for
         * {@link Missing}. Two parts are equal <b>iff</b> their identities are equal, and
         * {@link #ofIdentity(Object)} is the inverse — pinned by the datatable's
         * {@code GroupKeyPolicyIdentityTest}.
         *
         * <p>
         * ⚠ Only {@link PresentNumber} allocates here (the boxed {@code Double}); the text-join
         * identity of a cell is read without any part by
         * {@link GroupKeyPolicy#textKeyIdentity(IDataValue)}.
         * </p>
         *
         * @return the identity object
         */
        Object identity();


        /**
         * This part as it takes part in a <b>text join</b> — {@code Join_As_String} ({@code D4-R3})
         * and the RELREC link identity, which is a text join by design ({@code D4-R5},
         * {@code RRK E2}): a {@link PresentNumber} becomes the {@link Present} of its
         * {@link #reportingForm()}, every other part is itself.
         *
         * <p>
         * ⛔⛔ <b>Only a PRESENT number is projected.</b> {@link Missing} and {@link #EMPTY} keep
         * their own classification, because that classification is what stops a
         * {@code MissingValue} colliding with a present {@code "."} — the {@code +9 528-finding}
         * bug class recorded above. {@code Present}'s constructor rejects {@code ""}, so the
         * projection cannot manufacture an {@code EMPTY} either.
         * </p>
         *
         * @return the text-join part
         */
        KeyPart asText();
    }

    /**
     * Keep a blank key component as a real key. The index-block reporting key (the engine's
     * {@code IndexHelper.buildGroupKey}) — the group is still formed, and since {@code W38-A1} (Fix
     * #249) the key renders the component's {@link KeyPart} identity: {@code ""} for
     * {@link KeyPart.Empty}, the marker token for {@link KeyPart.Missing}
     * ({@link KeyPart#reportingForm()}), so two groups the grouping distinguishes are never
     * reported under one key. It is also the identity every join-key site classifies through
     * ({@code JKM R4}: KEEP is the default), the RELREC link identity included.
     *
     * <p>
     * ⭐ {@code W32-E3}: the blankness notion moved {@code MISSING_ONLY → MISSING_OR_EMPTY} — a
     * no-op at these call sites at the time, kept so the encoding change ({@code W38-A1}, now
     * <b>implemented</b>) did not land on an inconsistent base.
     * </p>
     *
     * <p>
     * ⚠⚠ <b>This is now value-identical to {@link #FOLD_BLANK_KEYS}</b> — both are
     * {@code (true, MISSING_OR_EMPTY)} — and {@code GroupKeyPolicy} is a {@code record}, so they
     * are {@code equals()}. Nothing compares policies by value today (verified: no {@code ==} or
     * {@code equals} against a constant anywhere in the engine's {@code lib/}), and the two names
     * are kept apart because they document <em>different call-site intent</em>: this one is the
     * <b>reporting</b> key, {@code FOLD_BLANK_KEYS} the <b>per-row</b> key. ⛔ <b>Do not
     * "deduplicate" them</b> — and do not start switching on policy identity, which would silently
     * conflate them.
     * </p>
     */
    public static final GroupKeyPolicy KEEP_MISSING_KEYS = new GroupKeyPolicy(true,
            Blankness.MISSING_OR_EMPTY);

    /**
     * Discard the group when a key component is blank. The engine's
     * {@code GroupSemantics.partition} / {@code IndexHelper.isBlockKeyMissing} and the singleton
     * branch of a coalesced key.
     *
     * <p>
     * ⭐⭐ <b>{@code W32-E3} (owner, 2026-08-12): "blank" here now means a genuine missing marker
     * <em>or</em> {@code ""}.</b> Previously only the marker dropped the group and a {@code ""} key
     * formed a real one. ⚑ <b>These are the call sites where the ruling actually changes
     * behaviour</b> — a blank-keyed group that used to be checked is now discarded, so rules keyed
     * on an unpopulated identifier go quieter. That is the intent: <em>the key IS the code whose
     * decode is being checked; a blank key means there is no identity.</em>
     * </p>
     *
     * <p>
     * ⚠ The javadoc this replaced cited "the EC-26 / Fix #122 parity contract" as the warrant for
     * the old notion. That citation was void: the ledger records {@code Fix #122} as <em>Python
     * fork only; Java unchanged</em> — it moved the <b>fork</b> to match coreJ, so coreJ's
     * behaviour was never derived from parity and the label was retroactive. ⇒ nothing was owed to
     * parity here, which is part of why the ruling was free to move it.
     * </p>
     *
     * <p>
     * ⛔ Still <b>not</b> {@link #COALESCE_COMPONENT}: that one also treats whitespace-only as
     * blank, and collapsing the two would change {@code FDA-SE2279}.
     * </p>
     */
    public static final GroupKeyPolicy DROP_MISSING_KEYS = new GroupKeyPolicy(false,
            Blankness.MISSING_OR_EMPTY);

    /**
     * Keep a blank key component as a real key on the per-row key builders (the engine's
     * {@code GroupSemantics.keyPart} / {@code foldedKey} and the {@code target_is_not_sorted_by}
     * partition).
     *
     * <p>
     * ⚠ <b>The name's "fold" is historical.</b> Until {@code W38-A1} (Fix #249) these sites folded
     * every blank to the one string {@code ""}; the key builders now type each component as a
     * {@link KeyPart}, so blanks are still <em>kept</em> but are distinct identities —
     * {@link KeyPart.Empty} and each {@link KeyPart.Missing} marker form separate groups (ruling
     * parts 2–3), and no {@code Missing} ever equals a real value (part 4). The name is kept
     * because it still marks the call-site intent that differs from {@link #KEEP_MISSING_KEYS}:
     * per-row keys here, index-block reporting keys there.
     * </p>
     */
    public static final GroupKeyPolicy FOLD_BLANK_KEYS = new GroupKeyPolicy(true,
            Blankness.MISSING_OR_EMPTY);

    /**
     * The EC-24 coalesce-component policy: a component whose every column is unpopulated (missing,
     * {@code ""}, or whitespace-only) is missing and the row drops. Reachable by exactly one
     * shipped rule, {@code FDA-SE2279} ({@code within: [[USUBJID, POOLID]]}).
     *
     * <p>
     * ⚠⚠ This is <b>not</b> {@link #DROP_MISSING_KEYS}. Collapsing the two would silently change
     * {@code FDA-SE2279}: the difference is now <b>whitespace only</b> — a space-filled
     * {@code USUBJID} must fall through to {@code POOLID} here, and under {@code MISSING_OR_EMPTY}
     * it would not. ⚑ {@code W32-E3} narrowed the gap between these two policies (both now drop a
     * plain {@code ""}), which makes it <em>easier</em> to collapse them by mistake — the remaining
     * difference is small and load-bearing.
     * </p>
     */
    public static final GroupKeyPolicy COALESCE_COMPONENT = new GroupKeyPolicy(false,
            Blankness.MISSING_OR_WHITESPACE);

    /**
     * <b>The single missing-key predicate.</b> Every grouping path asks this one question of this
     * one method; the answer depends only on {@link #blankness()}, never on {@link #keepMissings()}
     * (what to <em>do</em> about a blank is the caller's decision, and keeping the two apart is
     * what lets one predicate serve both the fold and the discard).
     *
     * <p>
     * ⚠ It asks the static {@link DataValueSupport#isEmptyOrMissing(IDataValue)}, never the
     * interface default {@link IDataValue#isEmptyOrMissing()}: a Mockito mock answers {@code false}
     * to an unstubbed default instead of running it, and the engine's suites mock
     * {@code IDataValue} widely (that static's javadoc records the measurement).
     * </p>
     *
     * @param dv
     *            the key column's cell at the row being keyed; a {@code null} is blank under every
     *            notion
     * @return {@code true} when this cell does not contribute a known key value
     */
    public boolean isBlankKeyComponent(@Nullable IDataValue dv)
    {
        if (dv == null)
        {
            return true;
        }
        return switch (blankness)
        {
        case MISSING_OR_EMPTY -> DataValueSupport.isEmptyOrMissing(dv);
        case MISSING_OR_WHITESPACE -> DataValueSupport.isEmptyOrMissing(dv)
                || dv.getValueAsString().isBlank();
        };
    }


    /**
     * <b>The single key-component classification</b> ({@code W38-A1} / Fix #249): the cell's
     * grouping identity as a {@link KeyPart}. A cell this policy's
     * {@link #isBlankKeyComponent(IDataValue)} calls non-blank is {@link KeyPart.PresentNumber
     * PresentNumber(value)} when its value is a {@link Number}, else {@link KeyPart.Present
     * Present(getValueAsString())}; a blank cell keeps its own identity instead of folding to
     * {@code ""} — a genuine missing marker maps to its {@link KeyPart.Missing} (decoded once, by
     * {@link #missingMarker(IDataValue)}), everything else blank (a literal {@code ""}, or a
     * whitespace-only value under {@link Blankness#MISSING_OR_WHITESPACE}) is
     * {@link KeyPart#EMPTY}.
     *
     * <p>
     * The disposition still belongs to the caller: this classifies, {@link #keepMissings()} says
     * what to do with a blank — exactly the split {@link #isBlankKeyComponent(IDataValue)} already
     * keeps.
     * </p>
     *
     * @param dv
     *            the key column's cell at the row being keyed; {@code null} — a cell no loader
     *            hands out, every notion calls it blank — classifies as
     *            {@link KeyPart#MISSING_UNKNOWN}
     * @return the cell's grouping identity
     */
    public KeyPart keyPart(@Nullable IDataValue dv)
    {
        if (dv == null)
        {
            return KeyPart.MISSING_UNKNOWN;
        }
        if (!isBlankKeyComponent(dv))
        {
            // D84a: a numeric cell keys by its EXACT value. getValueAsString() runs
            // getAsDoubleCleaned unconditionally (noise within 1e-12 of the decade folds, abs(v)
            // < 1e-13 -> 0), so the text form folds near-equal keys — while D64h rules key
            // identity always exact. Text cells keep keying by their text, which is never cleaned.
            if (dv.getValue() instanceof Number n)
            {
                return new KeyPart.PresentNumber(n.doubleValue());
            }
            return new KeyPart.Present(dv.getValueAsString());
        }
        MissingValue marker = missingMarker(dv);
        return marker == null ? KeyPart.EMPTY : KeyPart.missing(marker);
    }


    /**
     * The cell's <b>text-join key identity</b>, read without building a {@link KeyPart}: exactly
     * {@code keyPart(dv).asText().identity()} for every cell (pinned by
     * {@code GroupKeyPolicyIdentityTest}) — the cell's own {@code String} for present text, the
     * {@link DataValueSupport#toCleanText cleaned text} of a present number, {@code ""} for an
     * empty cell, the {@link MissingValue} constant for a missing one.
     *
     * <p>
     * ⭐ This is what the RELREC link indexes key on — the engine's {@code RelrecRowExpander}, the
     * BASIC manager's {@code RelrecRelationshipResolver} and the data browser's
     * {@code RelrecDialogSupport} (ruling D2 (c)). RELREC's present axis stays <b>text</b>
     * ({@code D4-R5}, {@code RRK E2}: <i>"the sites keep comparing text"</i>), so a {@code STRING}
     * {@code "1234567890123"} and a {@code LONG} {@code 1234567890123} are one key there, and
     * {@code 4.9999999999994} is {@code "5"}. What the identity object adds over the string
     * encoding the three sites used to hand-copy ({@code "\\u0001" + marker.name()} for a missing
     * cell) is the "cannot collide" of {@link KeyPart}'s part 4: a text cell is never equal to a
     * missing one, whatever it spells.
     * </p>
     *
     * <p>
     * Allocation: none for a text, empty or missing cell (the value's own string, a literal, an
     * enum constant); a present number allocates its rendering, as every text join of a number
     * must.
     * </p>
     *
     * @param dv
     *            the key column's cell; {@code null} is {@link MissingValue#MIS_UNKNOWN}, as in
     *            {@link #keyPart(IDataValue)}
     * @return the identity object — a {@code String} or a {@link MissingValue}
     */
    public Object textKeyIdentity(@Nullable IDataValue dv)
    {
        if (dv == null)
        {
            return MissingValue.MIS_UNKNOWN;
        }
        if (!isBlankKeyComponent(dv))
        {
            if (dv.getValue() instanceof Number n)
            {
                return DataValueSupport.toCleanText(presentKeyNumber(n.doubleValue()));
            }
            return dv.getValueAsString();
        }
        MissingValue marker = missingMarker(dv);
        return marker == null ? "" : marker;
    }


    /**
     * The missing marker a blank cell keys under — the ONE place a blank cell's missing identity is
     * decided, so {@link #keyPart(IDataValue)} and {@link #textKeyIdentity(IDataValue)} cannot
     * disagree: the stored {@link MissingValue}; for a missing cell that carries none (a
     * {@code NaN} double) the marker its payload encodes, {@link MissingValue#MIS} when it encodes
     * none (a bare {@code NaN} is {@code MIS}, ruling {@code BNM E5}); {@code null} for a blank
     * cell that is not missing ({@code ""}, or a whitespace-only value under
     * {@link Blankness#MISSING_OR_WHITESPACE}).
     */
    private static @Nullable MissingValue missingMarker(IDataValue dv)
    {
        if (dv.getValue() instanceof MissingValue m)
        {
            return m;
        }
        if (dv.isMissingOrInvalid())
        {
            // A numeric cell that is missing without carrying a MissingValue object — a NaN
            // double. Decode the NaN payload; a payload-less NaN is the generic numeric missing,
            // i.e. MIS.
            return Objects
                    .requireNonNull(MissingValue.forValue(dv.getValueAsDouble(), MissingValue.MIS));
        }
        return null;
    }


    /**
     * A present numeric key value, canonical: {@code -0.0} is {@code 0.0} (record equality is by
     * {@code Double.compare}, which separates the two zeros, while the numeric identity — and the
     * legacy text — does not), and a {@code NaN} is rejected (it is the carrier encoding of a
     * {@link MissingValue}, D85a, and must have been decoded to a missing part long before here).
     */
    private static double presentKeyNumber(double aValue)
    {
        if (Double.isNaN(aValue))
        {
            // Loud beats silent.
            throw new IllegalArgumentException(
                    "a NaN is a missing encoding, never a present numeric key");
        }
        return aValue == 0.0 ? 0.0 : aValue;
    }


    /**
     * This policy with {@link #keepMissings()} overridden, or {@code this} when the disposition is
     * already the one asked for. The {@link Blankness} notion is preserved: an authored
     * {@code keep_missings} chooses the <em>disposition</em> of a blank, never what counts as
     * blank.
     *
     * @param keep
     *            the wanted disposition
     * @return a policy with the same blankness notion and the given disposition
     */
    public GroupKeyPolicy withKeepMissings(boolean keep)
    {
        return keep == keepMissings ? this : new GroupKeyPolicy(keep, blankness);
    }


    /**
     * This policy with {@link #keepMissings()} overridden by an authored declaration, or unchanged
     * when the rule declared nothing. The plumbing shape for the {@code keep_missings} authoring
     * surfaces: each call site starts from its own shipped default and lets a declaration win.
     *
     * @param declared
     *            the authored {@code keep_missings}, or {@code null} when the rule is silent
     * @return the effective policy
     */
    public GroupKeyPolicy withDeclared(@Nullable Boolean declared)
    {
        return declared == null ? this : withKeepMissings(declared);
    }

}
