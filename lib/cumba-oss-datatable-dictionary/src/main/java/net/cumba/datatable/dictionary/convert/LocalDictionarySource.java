package net.cumba.datatable.dictionary.convert;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A raw distribution the operator already holds — a MedDRA {@code MedAscii} directory, a WHODrug B3
 * set, an unpacked SNOMED release, a LOINC release directory.
 *
 * <p>
 * This is the only source available for the licensed dictionaries, and it is what makes the
 * commercial ones installable at all without the dictionary installer ever fetching their data.
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
     * directory, in relative-path order. Each digest is of the bytes that reader delivered, the
     * unread rest included (confirmation look LOW-4), not a re-read of the file afterwards. A file
     * the converter never opened (a notes file, an unused table) is not fingerprinted; before this
     * ruling a local directory recorded no artefact at all.
     *
     * @return one artefact per file read: its path relative to the directory, its {@code file:} URI
     *         and its SHA-256.
     * @throws UncheckedIOException
     *             when a file changed while the install read it (two reads, different bytes) — the
     *             installer asks before it writes anything, so the install then writes nothing
     */
    @Override
    public List<Artefact> artefacts()
    {
        Path root = dir.toAbsolutePath().normalize();
        Map<Path, String> digests;
        try
        {
            digests = RawDictionaryFiles.readDigests();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        Map<String, Artefact> byName = new TreeMap<>();
        for (Map.Entry<Path, String> e : digests.entrySet())
        {
            Path file = e.getKey();
            if (file.startsWith(root) && !file.equals(root))
            {
                String name = root.relativize(file).toString().replace('\\', '/');
                byName.put(name, new Artefact(name, file.toUri().toString(), e.getValue()));
            }
        }
        return List.copyOf(byName.values());
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
