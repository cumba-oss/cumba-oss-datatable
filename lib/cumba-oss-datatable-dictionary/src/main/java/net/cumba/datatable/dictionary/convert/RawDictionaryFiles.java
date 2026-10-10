package net.cumba.datatable.dictionary.convert;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * The little that every vendor distribution has in common: finding a file whose name carries a
 * release token, and reading a delimited text file that is not guaranteed to be clean UTF-8.
 */
final class RawDictionaryFiles
{

    /** What {@link CodingErrorAction#REPLACE} substitutes for an undecodable byte sequence. */
    private static final char REPLACEMENT = '\uFFFD';

    /**
     * The Unicode byte-order mark. Several real vendor files (the MED-RT release notes,
     * {@code MEDRT.txt}, MedDRA {@code .asc} exports, {@code Loinc.csv} re-saved on Windows) begin
     * with one; it survives {@code trim()} (which strips only up to {@code U+0020}) and
     * {@code isBlank()}, so left in place it silently corrupts whatever reads the first line \u2014
     * a version string, the first row's code, or a header-column lookup.
     */
    private static final char BOM = '\uFEFF';

    /**
     * Replacement characters seen by every reader opened via {@link #reader} on the current thread
     * since the last {@link #resetReplacementCount()}. Thread-confined rather than global because
     * an install converts one dictionary at a time on the calling thread, and a concurrent install
     * on another thread must not inflate this one's count.
     */
    private static final ThreadLocal<AtomicLong> REPLACEMENTS = ThreadLocal
            .withInitial(AtomicLong::new);

    /**
     * Term spellings a converter refused because of a case collision, tallied on the converting
     * thread since the last {@link #resetDroppedTermCount()} — the same thread-confined design as
     * {@link #REPLACEMENTS}, for the same reason.
     */
    private static final ThreadLocal<AtomicLong> DROPPED = ThreadLocal.withInitial(AtomicLong::new);

    /**
     * Every file {@link #reader} opened on the current thread since the last
     * {@link #resetReadFiles()}, absolute and normalised, in first-read order, mapped to the
     * SHA-256 of the bytes that reader delivered — what a {@link LocalDictionarySource}
     * fingerprints into {@code SOURCES.md} (owner ruling OQ4 (b)). The digest is taken from the
     * stream itself and completed when the reader is closed, the unread rest drained first
     * (confirmation look LOW-4): it describes what was converted, not the file as it lies in the
     * folder afterwards. {@link #UNCLOSED} until the reader is closed, {@link #CHANGED} once two
     * readers of one file delivered different bytes. Thread-confined for the same reason as
     * {@link #REPLACEMENTS}.
     */
    private static final ThreadLocal<Map<Path, String>> READ_FILES = ThreadLocal
            .withInitial(LinkedHashMap::new);

    /** {@link #READ_FILES}' value for a file whose reader is still open. */
    private static final String UNCLOSED = "";

    /** {@link #READ_FILES}' value for a file two readers saw with different content. */
    private static final String CHANGED = "changed";

    private RawDictionaryFiles()
    {
    }


    /**
     * Opens a text file for reading.
     *
     * <p>
     * Vendor distributions are not uniformly clean UTF-8, and an undecodable byte anywhere in a 171
     * 912-row file would otherwise abort the whole conversion. A replacement character in one
     * concept name costs that one term; a {@code MalformedInputException} costs the dictionary.
     * </p>
     *
     * <p>
     * ⚠ The replacement must never be <em>silent</em>: a substituted character produces a term that
     * passes the case contract yet matches no real data. Every {@code U+FFFD} delivered by a reader
     * this method opened is therefore tallied on the opening thread, and the installer surfaces the
     * count as an {@link InstallReport} warning. A literal {@code U+FFFD} already present in the
     * raw bytes counts too — it is indistinguishable after decoding, and is itself the residue of
     * an earlier encoding accident, so warning on it is just as warranted.
     * </p>
     *
     * <p>
     * A single leading {@code U+FEFF} (a UTF-8 byte-order mark) is skipped, so every converter
     * inherits the fix: without it the BOM lands in the first token the converter reads — a MED-RT
     * version becomes an untypeable directory name, the first {@code llt.asc} code drops out of
     * every join, a {@code LOINC_NUM} header lookup fails while blaming the column. The BOM is a
     * deliberate encoding signature, not a decoding accident, so it is <em>not</em> tallied as a
     * replacement.
     * </p>
     */
    static BufferedReader reader(Path aFile) throws IOException
    {
        Path file = aFile.toAbsolutePath().normalize();
        Map<Path, String> readFiles = READ_FILES.get();
        readFiles.putIfAbsent(file, UNCLOSED);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        BufferedReader in = new BufferedReader(new ReplacementCountingReader(new InputStreamReader(
                new FingerprintingStream(Files.newInputStream(aFile), file, readFiles), decoder),
                REPLACEMENTS.get()));
        in.mark(1);
        if (in.read() != BOM)
        {
            in.reset();
        }
        return in;
    }


    /** Starts a fresh record of the files read on the current thread — one per install. */
    static void resetReadFiles()
    {
        READ_FILES.get().clear();
    }


    /**
     * The files {@link #reader} opened on this thread since the last {@link #resetReadFiles()},
     * each with the SHA-256 of the bytes its reader delivered (confirmation look LOW-4).
     *
     * @return absolute, normalised paths in first-read order to lower-case hex digests; a copy.
     * @throws IOException
     *             when a reader is still open (its digest is incomplete), or when two readers of
     *             one file delivered different bytes — the file changed while the install read it,
     *             so no single fingerprint describes what was converted
     */
    static Map<Path, String> readDigests() throws IOException
    {
        Map<Path, String> out = new LinkedHashMap<>(READ_FILES.get());
        for (Map.Entry<Path, String> e : out.entrySet())
        {
            if (UNCLOSED.equals(e.getValue()))
            {
                throw new IOException(e.getKey() + " is still open: its fingerprint is incomplete");
            }
            if (CHANGED.equals(e.getValue()))
            {
                throw new IOException(e.getKey() + " changed while the install read it: two reads"
                        + " delivered different bytes, so the conversion is inconsistent —"
                        + " NOT installed. Leave the distribution folder alone while it installs.");
            }
        }
        return Collections.unmodifiableMap(out);
    }


    /** Starts a fresh replacement tally for the current thread — one per conversion. */
    static void resetReplacementCount()
    {
        REPLACEMENTS.get().set(0);
    }


    /**
     * Replacement characters delivered on this thread since the last
     * {@link #resetReplacementCount()}.
     */
    static long replacementCount()
    {
        return REPLACEMENTS.get().get();
    }


    /**
     * Records that a converter refused to publish a term spelling because its case collides with an
     * already-published one. The refusal itself is correct — rewriting either spelling would assert
     * a form the vendor never wrote — but it must never be <em>silent</em>: a concept whose only
     * synonym is refused ends up code-only (in {@code levels.*CD} but in neither the name level nor
     * {@code pairs}), so data reporting that spelling is flagged even though the vendor publishes
     * it. The installer surfaces the tally as an {@link InstallReport} warning, exactly as the
     * replacement-character count is.
     */
    static void countDroppedTerm()
    {
        DROPPED.get().incrementAndGet();
    }


    /** Starts a fresh dropped-term tally for the current thread — one per conversion. */
    static void resetDroppedTermCount()
    {
        DROPPED.get().set(0);
    }


    /**
     * Term spellings refused on this thread since the last {@link #resetDroppedTermCount()}.
     */
    static long droppedTermCount()
    {
        return DROPPED.get().get();
    }

    /**
     * Counts every {@code U+FFFD} that passes through. Only {@code read(char[], int, int)} is
     * overridden; that is sufficient because {@link #reader} always wraps this in a
     * {@link BufferedReader}, whose every read — single-char, {@code readLine}, {@code skip},
     * {@code lines} — fills its buffer through {@code read(char[], int, int)} and never calls the
     * single-char {@code read()} on the underlying reader.
     */
    private static final class ReplacementCountingReader extends FilterReader
    {

        private final AtomicLong tally;

        ReplacementCountingReader(Reader aIn, AtomicLong aTally)
        {
            super(aIn);
            tally = aTally;
        }


        @Override
        public int read(char[] aBuffer, int aOffset, int aLength) throws IOException
        {
            int n = super.read(aBuffer, aOffset, aLength);
            for (int i = 0; i < n; i++)
            {
                if (aBuffer[aOffset + i] == REPLACEMENT)
                {
                    tally.incrementAndGet();
                }
            }
            return n;
        }
    }


    /**
     * Digests every byte read through it, and on {@link #close()} drains the unread rest so the
     * digest covers the whole file as this stream saw it, then records it for {@link #readDigests}
     * — once, however often it is closed.
     */
    private static final class FingerprintingStream extends DigestInputStream
    {

        private final Path file;

        private final Map<Path, String> record;

        private boolean closed;

        FingerprintingStream(InputStream aIn, Path aFile, Map<Path, String> aRecord)
        {
            super(aIn, sha256());
            file = aFile;
            record = aRecord;
        }


        @Override
        public void close() throws IOException
        {
            if (closed)
            {
                return;
            }
            closed = true;
            try (InputStream underlying = in)
            {
                // the unread rest, straight from the file into the digest
                MessageDigest digestOfAll = getMessageDigest();
                byte[] rest = new byte[1 << 16];
                int n = underlying.read(rest, 0, rest.length);
                while (n >= 0)
                {
                    digestOfAll.update(rest, 0, n);
                    n = underlying.read(rest, 0, rest.length);
                }
                String digest = HexFormat.of().formatHex(digestOfAll.digest());
                String earlier = record.get(file);
                record.put(file,
                        earlier == null || UNCLOSED.equals(earlier) || earlier.equals(digest)
                                ? digest
                                : CHANGED);
            }
        }


        private static MessageDigest sha256()
        {
            try
            {
                return MessageDigest.getInstance("SHA-256");
            }
            catch (NoSuchAlgorithmException e)
            {
                throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
            }
        }
    }

    /**
     * The first file in {@code aDir} matching {@code aGlob} in name order, or {@code null} when
     * there is none. Ordered so that a directory holding two releases converts reproducibly rather
     * than by directory-iteration accident.
     */
    static @Nullable Path firstMatching(Path aDir, String aGlob) throws IOException
    {
        if (!Files.isDirectory(aDir))
        {
            return null;
        }
        List<Path> hits = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(aDir, aGlob))
        {
            stream.forEach(hits::add);
        }
        return hits.stream().sorted().findFirst().orElse(null);
    }


    /**
     * The single file in {@code aDir} matching {@code aGlob}, {@code null} when there is none — and
     * an {@link IOException} when there are several. For a converter that derives its data and its
     * version from files matched by <em>different</em> globs, "first in name order" is not good
     * enough: two releases unpacked into one directory would pair one release's data with the
     * other's version stamp, silently. Refusing forces the operator to keep one release per raw
     * directory.
     */
    static @Nullable Path soleMatching(Path aDir, String aGlob) throws IOException
    {
        if (!Files.isDirectory(aDir))
        {
            return null;
        }
        List<Path> hits = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(aDir, aGlob))
        {
            stream.forEach(hits::add);
        }
        if (hits.size() > 1)
        {
            throw new IOException("several files in " + aDir + " match " + aGlob + ": "
                    + hits.stream().map(RawDictionaryFiles::fileNameOf).sorted().toList()
                    + " — two releases in one directory cannot be converted deterministically; "
                    + "keep exactly one");
        }
        return hits.isEmpty() ? null : hits.get(0);
    }


    /** Splits one line of a tab-separated file, keeping trailing empty cells. */
    static String[] cells(String aLine)
    {
        return aLine.split("\t", -1);
    }


    /**
     * The path's file name as a string, or {@code ""} for a path that has none (a filesystem root).
     * {@link Path#getFileName()} is nullable and the converters only ever hand real files here, but
     * a null-safe spelling keeps that reasoning local instead of at every call site (SpotBugs
     * {@code NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE}).
     */
    static String fileNameOf(Path aPath)
    {
        return Objects.toString(aPath.getFileName(), "");
    }


    /** Copies a map into a JSON object, preserving insertion order. */
    static void putAll(ObjectNode aTarget, Map<String, String> aEntries)
    {
        aEntries.forEach(aTarget::put);
    }

}
