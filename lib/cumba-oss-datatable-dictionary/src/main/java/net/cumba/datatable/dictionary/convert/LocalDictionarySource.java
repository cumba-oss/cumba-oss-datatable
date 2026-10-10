package net.cumba.datatable.dictionary.convert;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A raw distribution the operator already holds — a MedDRA {@code MedAscii} directory, a WHODrug B3
 * set, an unpacked SNOMED release, a LOINC release directory.
 *
 * <p>
 * This is the only source available for the licensed dictionaries, and it is what makes the
 * commercial ones installable at all without coreJ ever handling their data.
 * </p>
 */
public final class LocalDictionarySource implements DictionarySource
{

    private final Path dir;

    public LocalDictionarySource(Path aDir)
    {
        this.dir = aDir;
    }


    @Override
    public Path resolve() throws IOException
    {
        if (!Files.isDirectory(dir))
        {
            throw new IOException("Not a directory: " + dir);
        }
        return dir;
    }


    @Override
    public String provenance()
    {
        return dir.toAbsolutePath().toString();
    }


    /**
     * The SHA-256 of every file the converter read from this directory (owner ruling OQ4 (b),
     * PLAN-dictionary-backed-formats): the installer records them in {@code SOURCES.md} in the
     * artefact-line format downloads use, so a later migration can verify the distribution by hash.
     * "Read" is what {@code RawDictionaryFiles.reader} opened on this thread since the installer
     * started this install — every converter reads through it — restricted to files under this
     * directory, in relative-path order. A file the converter never opened (a notes file, an unused
     * table) is not fingerprinted; before this ruling a local directory recorded no artefact at
     * all.
     *
     * @return one artefact per file read: its path relative to the directory, its {@code file:} URI
     *         and its SHA-256.
     * @throws UncheckedIOException
     *             when a file read during the conversion can no longer be read — the installer asks
     *             before it writes anything, so the install then writes nothing
     */
    @Override
    public List<Artefact> artefacts()
    {
        Path root = dir.toAbsolutePath().normalize();
        Map<String, Path> byName = new TreeMap<>();
        for (Path file : RawDictionaryFiles.readFiles())
        {
            if (file.startsWith(root) && !file.equals(root) && Files.isRegularFile(file))
            {
                byName.put(root.relativize(file).toString().replace('\\', '/'), file);
            }
        }
        List<Artefact> out = new ArrayList<>(byName.size());
        for (Map.Entry<String, Path> e : byName.entrySet())
        {
            out.add(new Artefact(e.getKey(), e.getValue().toUri().toString(),
                    sha256(e.getValue())));
        }
        return List.copyOf(out);
    }


    private static String sha256(Path aFile)
    {
        try (InputStream in = Files.newInputStream(aFile))
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0)
            {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("could not fingerprint " + aFile, e);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }


    /**
     * Releases nothing. The directory belongs to the operator; deleting or altering it would be a
     * side effect they never asked for.
     */
    @Override
    public void close()
    {
        // Deliberately empty — see javadoc.
    }

}
