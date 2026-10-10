package net.cumba.datatable.dictionary.convert;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Where an installer gets a dictionary's raw vendor distribution from.
 *
 * <p>
 * Modelled on {@code PickleSource}, the SPI the CDISC-Library cache seeder already uses, and for
 * the same reasons: the network happens in {@link #resolve()} rather than in a constructor, so a
 * source can be built and inspected without touching the wire; and {@link #close()} releases only
 * what the source itself created, never a directory the operator supplied.
 * </p>
 */
public interface DictionarySource extends AutoCloseable
{

    /**
     * Materialises the raw distribution and returns the directory holding it — downloading it, or
     * simply validating a local path the operator already holds.
     */
    Path resolve() throws IOException;


    /**
     * A human-readable description of where this came from, for the report and {@code SOURCES.md}.
     */
    String provenance();


    /**
     * The raw artefacts this source materialised, for the store's {@code SOURCES.md} provenance
     * record — for a download populated by {@link #resolve()}, so empty before it has run; for a
     * local directory the operator supplied, the files the converter read from it (owner ruling OQ4
     * (b), {@link LocalDictionarySource#artefacts()}), so asked for after the conversion.
     */
    default List<Artefact> artefacts()
    {
        return List.of();
    }


    @Override
    void close() throws IOException;

    /**
     * One raw distribution file as it arrived, before any conversion.
     *
     * @param name
     *            the artefact's file name, e.g. {@code UNII_Data.zip}.
     * @param url
     *            where it was fetched from.
     * @param sha256
     *            the SHA-256 of the artefact's bytes as fetched, lower-case hex.
     */
    record Artefact(String name, String url, String sha256)
    {
    }

}
