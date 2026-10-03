package net.cumba.datatable.impl.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link AbstractGenericProvider#downloadToFile}: content is copied byte-exactly, the
 * temp-file variant carries the source extension, and a failing download raises IOException.
 */
class AbstractGenericProviderDownloadTest
{

    private static class Probe extends AbstractGenericProvider<String>
    {

        @Override
        public String provide(java.net.URI aUri, net.cumba.datatable.io.FileInfo aFileInfo)
        {
            return null;
        }
    }

    @TempDir
    Path tmp;

    @Test
    void downloadCopiesTheContentExactly() throws IOException
    {
        byte[] payload = new byte[300_000];
        for (int i = 0; i < payload.length; i++)
        {
            payload[i] = (byte) (i * 31);
        }
        Path src = tmp.resolve("data.bin");
        Files.write(src, payload);

        Probe probe = new Probe();
        File out = tmp.resolve("out.bin").toFile();
        probe.downloadToFile(src.toUri(), out, 4096);
        assertArrayEquals(payload, Files.readAllBytes(out.toPath()));
    }


    @Test
    void tempFileVariantDownloadsAndKeepsTheExtension() throws IOException
    {
        Path src = tmp.resolve("table.csv");
        Files.writeString(src, "A,B\n1,2\n");

        Probe probe = new Probe();
        File out = probe.downloadToFile(src.toUri());
        try
        {
            assertNotNull(out);
            assertTrue(out.getName().endsWith(".csv"), out.getName());
            assertArrayEquals(Files.readAllBytes(src), Files.readAllBytes(out.toPath()));
        }
        finally
        {
            Files.deleteIfExists(out.toPath());
        }
    }


    /**
     * A file opened from inside a zip keeps its extension. For an opaque {@code jar:} URI
     * {@code getPath()} is null, and the temp file used to get no extension at all, so no
     * {@code FileInfo} pattern could match it (see {@code PLAN-jar-url-stream-cache}, D4).
     */
    @Test
    void aZipEntryKeepsItsExtensionAndContent() throws IOException
    {
        Path zip = tmp.resolve("bundle.zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                Files.newOutputStream(zip)))
        {
            zos.putNextEntry(new java.util.zip.ZipEntry("dir/table.csv"));
            zos.write("A,B\n1,2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        File out = new Probe()
                .downloadToFile(java.net.URI.create("jar:" + zip.toUri() + "!/dir/table.csv"));
        try
        {
            assertTrue(out.getName().endsWith(".csv"), out.getName());
            assertArrayEquals("A,B\n1,2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    Files.readAllBytes(out.toPath()));
        }
        finally
        {
            Files.deleteIfExists(out.toPath());
        }
    }


    /**
     * A source without an extension yields a temp file without one. The extension used to be taken
     * from the whole path with {@code getAfterLast(path, '.')}, which answers the WHOLE path when
     * there is no dot — a "suffix" full of path separators.
     */
    @Test
    void aSourceWithoutAnExtensionGetsNoSuffix() throws IOException
    {
        Path src = tmp.resolve("noextension");
        Files.writeString(src, "x");

        File out = new Probe().downloadToFile(src.toUri());
        try
        {
            assertTrue(out.getName().startsWith("temp_cumba"), out.getName());
            assertEquals(false, out.getName().contains("."), out.getName());
        }
        finally
        {
            Files.deleteIfExists(out.toPath());
        }
    }


    /**
     * Only a plausible extension becomes the suffix. A name ending in a dot, or with characters
     * after the last dot that a temp-file name cannot carry (a decoded NUL, a backslash, the
     * characters Windows reserves), yields no suffix instead of making {@code createTempFile} throw
     * an unchecked {@code IllegalArgumentException} out of a method declared
     * {@code throws IOException}.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings =
    {
            "data.", "a.c%00sv", "a.b%5Cc", "a.c%3Fsv"
    })
    void anImplausibleExtensionGivesNoSuffix(String aName) throws IOException
    {
        java.net.URI uri = tmp.toUri().resolve(aName);
        Probe probe = new Probe()
        {

            @Override
            protected void downloadToFile(java.net.URI aUri, File aFile, int aBufferSize)
            {
                // the download itself is not under test; only the temp file's name is
            }
        };

        File out = probe.downloadToFile(uri);
        try
        {
            assertTrue(out.getName().startsWith("temp_cumba"), out.getName());
            assertEquals(false, out.getName().contains("."), out.getName());
        }
        finally
        {
            Files.deleteIfExists(out.toPath());
        }
    }


    @Test
    void missingSourceRaisesIoException()
    {
        Probe probe = new Probe();
        assertThrows(IOException.class,
                () -> probe.downloadToFile(tmp.resolve("absent.bin").toUri()));
    }
}
