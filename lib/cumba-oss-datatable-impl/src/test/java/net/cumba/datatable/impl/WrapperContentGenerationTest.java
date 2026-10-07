package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.cumba.datatable.ColumnContent;
import net.cumba.datatable.ColumnContent.Fill;
import net.cumba.datatable.ColumnContent.Uniformity;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.support.OverlayDataTable;
import net.cumba.datatable.impl.view.MergeDataTable;
import net.cumba.datatable.impl.view.UnionDataTable;
import net.cumba.datatable.impl.view.ViewDataTable;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * PACK 4 review L3 of {@code PLAN-present-empty-column-fold}: every wrapper that serves another
 * table's cells folds that table's content generation into its own, so its cached column content
 * moves with a change below it. One functional run over every wrapper this module can construct
 * from a plain source, and a roster of every {@code AbstractDataTable} subclass in the module —
 * each either overrides {@code sourceContentGeneration()} or is a listed owner of its own cells —
 * so the next wrapper cannot inherit the constant default silently.
 */
class WrapperContentGenerationTest
{

    /** A source whose one cell the test changes, bumping its own generation. */
    private static final class MutableSource extends AbstractDataTable
    {

        volatile String cell;

        MutableSource(String aColumn, String aCell)
        {
            cell = aCell;
            DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name(aColumn)
                    .type(DataValueType.STRING).build();
            setMetaData(DataTableMeta.builder().name("S").setColumns(cm).rowCount(1)
                    .totalRowCount(1).build());
        }


        @Override
        public long getRowCount()
        {
            return 1;
        }


        @Override
        public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
        {
            return cell;
        }


        void set(String aCell)
        {
            cell = aCell;
            invalidateColumnContent();
        }
    }

    private static void assertFollowsTheSource(String aName, MutableSource aSource,
            Function<MutableSource, IDataTable> aWrap)
    {
        IDataTable wrapper = aWrap.apply(aSource);
        assertEquals(Fill.FILLED, wrapper.getColumnContent(0).fill(), aName + ": filled first");
        long before = wrapper.contentGeneration();
        aSource.set("");
        assertTrue(wrapper.contentGeneration() > before,
                aName + ": the wrapper's generation must move with its source's");
        assertEquals(new ColumnContent(Fill.EMPTY, Uniformity.SINGLE_VALUE),
                wrapper.getColumnContent(0), aName + ": re-read after the change below");
    }


    @Test
    void everyConstructibleWrapperFollowsItsSource()
    {
        assertFollowsTheSource("ViewDataTable", new MutableSource("C", "v"),
                s -> new ViewDataTable(s, null, (Stream<DataTableColumnMeta>) null));
        assertFollowsTheSource("UnionDataTable", new MutableSource("C", "v"),
                s -> new UnionDataTable("U", s));
        assertFollowsTheSource("OverlayDataTable", new MutableSource("C", "v"),
                OverlayDataTable::new);
        assertFollowsTheSource("MergeDataTable", new MutableSource("C", "v"),
                s -> new MergeDataTable(s, new MutableSource("D", "w")));
    }

    /** The table subclasses only — not AbstractDataTableColumn / -Library / -Provider. */
    private static final java.util.regex.Pattern SUBCLASS = java.util.regex.Pattern
            .compile("extends AbstractDataTable\\b");

    /** Subclasses that own every cell they serve: no source, a constant generation is right. */
    private static final Set<String> OWNERS = Set.of("ColumnCachedDataTable", "MatchedFlagTable");

    @Test
    void everyAbstractDataTableSubclassFoldsItsSourceOrIsAListedOwner() throws IOException
    {
        // Surefire runs under target/test-cwd (the home-redirect setup): walk up to the module.
        Path module = Path.of("").toAbsolutePath();
        while (module != null && !(Files.isRegularFile(module.resolve("pom.xml"))
                && Files.isDirectory(module.resolve("src").resolve("main").resolve("java"))))
        {
            module = module.getParent();
        }
        assertTrue(module != null, "no module directory above " + Path.of("").toAbsolutePath());
        Path root = module.resolve("src").resolve("main").resolve("java");
        List<String> seen = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root))
        {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList())
            {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                if (!SUBCLASS.matcher(src).find())
                {
                    continue;
                }
                String name = p.getFileName().toString().replace(".java", "");
                seen.add(name);
                boolean folds = src.contains("sourceContentGeneration()");
                if (!folds && !OWNERS.contains(name))
                {
                    offenders.add(name + " (" + p + ")");
                }
            }
        }
        assertTrue(seen.size() >= 6, "the roster walk found too few subclasses: " + seen);
        assertEquals(List.of(), offenders,
                "an AbstractDataTable subclass that serves another table's cells must override"
                        + " sourceContentGeneration(), or be listed as an owner of its cells here");
        for (String owner : OWNERS)
        {
            assertTrue(seen.contains(owner), "a listed owner no longer exists: " + owner);
        }
    }
}
