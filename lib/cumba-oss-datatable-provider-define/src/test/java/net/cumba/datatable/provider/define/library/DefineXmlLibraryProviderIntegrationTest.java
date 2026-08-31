package net.cumba.datatable.provider.define.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.cumba.cdisc.define.DefineCache;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.DocumentRef;
import net.cumba.cdisc.define.GlobalVariables;
import net.cumba.cdisc.define.ItemDef;
import net.cumba.cdisc.define.ItemGroupDef;
import net.cumba.cdisc.define.ItemRef;
import net.cumba.cdisc.define.Leaf;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.ODM;
import net.cumba.cdisc.define.Study;
import net.cumba.cdisc.define.SupplementalDoc;
import net.cumba.datatable.impl.library.dblib.DataBrowserLibrary;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Integration tests for {@link DefineXmlLibraryProvider}: covers {@code provide()}, the
 * default-library-name computation, validation-report URI discovery and the no-data filter via an
 * in-memory ODM tree and an injected {@link DefineCache.IDefineLoader}.
 */
class DefineXmlLibraryProviderIntegrationTest
{

    private DefineCache.IDefineLoader previousLoader;

    @BeforeEach
    void rememberLoader()
    {
        previousLoader = DefineCache.sharedInstance().getDefineLoader();
    }


    @AfterEach
    void restoreLoader()
    {
        DefineCache.sharedInstance().setDefineLoader(previousLoader);
    }

    // ==================== Builders ====================


    private static URI uniqueUri(String aSlug)
    {
        return URI.create("file:///study/" + aSlug + "-" + System.nanoTime() + "/define.xml");
    }


    private static MetaDataVersion mdvWith(List<ItemGroupDef> aGroups, List<ItemDef> aDefs)
    {
        return MetaDataVersion.builder().oid("M.1").name("V1").standardName("SDTM-IG")
                .standardVersion("3.4").defineVersion("2.1.0").itemGroupDefs(aGroups)
                .itemDefs(aDefs).build();
    }


    private static MetaDataVersion mdvWith(List<ItemGroupDef> aGroups, List<ItemDef> aDefs,
            SupplementalDoc aSupplementalDoc, List<Leaf> aLeaves)
    {
        return MetaDataVersion.builder().oid("M.1").name("V1").standardName("SDTM-IG")
                .standardVersion("3.4").defineVersion("2.1.0").itemGroupDefs(aGroups)
                .itemDefs(aDefs).supplementalDoc(aSupplementalDoc).leaves(aLeaves).build();
    }


    private static DefineSupport buildSupport(URI aUri, MetaDataVersion aMdv, GlobalVariables aGv)
    {
        Study study = Study.builder().oid("S.1").globalVariables(aGv)
                .metaDataVersions(List.of(aMdv)).build();
        ODM odm = ODM.builder().fileOID("f1").studies(List.of(study)).build();
        return new DefineSupport(aUri, odm);
    }


    private static ItemGroupDef dataset(String aOid, String aName, String aHref)
    {
        ItemGroupDef.ItemGroupDefBuilder b = ItemGroupDef.builder().oid(aOid).name(aName);
        if (aHref != null)
        {
            b.leaf(Leaf.builder().id("L." + aName).href(aHref).build());
        }
        return b.build();
    }


    private static ItemGroupDef noDataDataset(String aOid, String aName, String aHref)
    {
        return ItemGroupDef.builder().oid(aOid).name(aName).hasNoData("Yes")
                .leaf(Leaf.builder().id("L." + aName).href(aHref).build()).build();
    }

    // ==================== provide() ====================


    @Test
    void provide_buildsLibraryWithSources_andDefaultName() throws IOException
    {
        URI uri = uniqueUri("provide");
        ItemDef name = ItemDef.builder().oid("I.NAME").name("USUBJID").dataType("text").build();
        ItemGroupDef dm = ItemGroupDef.builder().oid("IG.DM").name("DM")
                .leaf(Leaf.builder().id("L.DM").href("dm.xpt").build())
                .itemRefs(List.of(ItemRef.builder().itemOID("I.NAME").orderNumber(1).build()))
                .build();

        DefineSupport stub = buildSupport(uri, mdvWith(List.of(dm), List.of(name)),
                GlobalVariables.builder().studyName("STUDY-B").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);

        assertNotNull(lib);
        DataBrowserLibrary dbLib = assertInstanceOf(DataBrowserLibrary.class, lib);
        assertEquals("STUDY-B - SDTM-IG 3.4", dbLib.getName());
        assertEquals(1, dbLib.getBean().getSources().length);
        assertEquals("DM", dbLib.getBean().getSources()[0].getName());
        // no validation-report supplemental doc → no attribute
        assertNull(dbLib.getBean().getAttributes());
    }


    @Test
    void provide_skipsSourcesWithoutTableUri() throws IOException
    {
        URI uri = uniqueUri("skip-no-uri");
        ItemGroupDef good = dataset("IG.A", "A", "a.xpt");
        // no leaf → no tableURI → skipped
        ItemGroupDef bad = ItemGroupDef.builder().oid("IG.B").name("B").build();

        DefineSupport stub = buildSupport(uri, mdvWith(List.of(good, bad), List.of()),
                GlobalVariables.builder().studyName("S").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);

        DataBrowserLibrary dbLib = (DataBrowserLibrary) lib;
        assertEquals(1, dbLib.getBean().getSources().length);
        assertEquals("A", dbLib.getBean().getSources()[0].getName());
    }


    @Test
    void provide_skipsSourcesMarkedAsNoData() throws IOException
    {
        URI uri = uniqueUri("nodata");
        ItemGroupDef good = dataset("IG.A", "A", "a.xpt");
        ItemGroupDef empty = noDataDataset("IG.E", "E", "e.xpt");

        DefineSupport stub = buildSupport(uri, mdvWith(List.of(good, empty), List.of()),
                GlobalVariables.builder().studyName("S").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);

        DataBrowserLibrary dbLib = (DataBrowserLibrary) lib;
        assertEquals(1, dbLib.getBean().getSources().length);
        assertEquals("A", dbLib.getBean().getSources()[0].getName());
    }


    @Test
    void provide_studyName_withoutStandardName_isJustStudyName() throws IOException
    {
        URI uri = uniqueUri("study-only");
        // MetaDataVersion without standardName
        MetaDataVersion bareMdv = MetaDataVersion.builder().oid("M.1").name("V1").build();
        DefineSupport stub = buildSupport(uri, bareMdv,
                GlobalVariables.builder().studyName("PLAIN-STUDY").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);
        assertEquals("PLAIN-STUDY", lib.getName());
    }


    @Test
    void provide_studyName_withStandardNameButNoVersion() throws IOException
    {
        URI uri = uniqueUri("std-no-ver");
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .standardName("SDTM-IG").build();
        DefineSupport stub = buildSupport(uri, mdv,
                GlobalVariables.builder().studyName("S").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);
        assertEquals("S - SDTM-IG", lib.getName());
    }

    // ==================== Validation report discovery ====================


    @Test
    void provide_discoversValidationReportUri() throws IOException
    {
        URI uri = uniqueUri("val");
        DocumentRef docRef = DocumentRef.builder().leafID("validationreport-sdtm34-r1").build();
        SupplementalDoc sd = SupplementalDoc.builder().documentRefs(List.of(docRef)).build();
        Leaf leaf = Leaf.builder().id("validationreport-sdtm34-r1").href("p21-report.xlsx").build();

        DefineSupport stub = buildSupport(uri, mdvWith(List.of(), List.of(), sd, List.of(leaf)),
                GlobalVariables.builder().studyName("S").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);

        DataBrowserLibrary dbLib = (DataBrowserLibrary) lib;
        Map<String, String> attrs = dbLib.getBean().getAttributes();
        assertNotNull(attrs);
        String reportUri = attrs.get(ILibraryProvider.ATTRIBUTE_VALIDATION_REPORT);
        assertNotNull(reportUri);
        assertTrue(reportUri.endsWith("p21-report.xlsx"), reportUri);

        // and exposed via getLibraryAttribute as well
        Object lookup = provider.getLibraryAttribute(lib,
                ILibraryProvider.ATTRIBUTE_VALIDATION_REPORT);
        assertEquals(reportUri, lookup);
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("provide_attributesNullCases")
    void provide_doesNotPopulateAttributes(String aCaseName, String aUriToken,
            Supplier<SupplementalDoc> aSdSupplier)
        throws IOException
    {
        // Cases where the validation-report attribute should NOT be populated on the
        // resulting library, either because the supplemental doc is missing, its docRefs are
        // empty, or its docRef leafIDs don't resolve to a validation leaf.
        URI uri = uniqueUri(aUriToken);
        SupplementalDoc sd = aSdSupplier.get();
        MetaDataVersion mdv = sd == null ? mdvWith(List.of(), List.of())
                : mdvWith(List.of(), List.of(), sd, List.of());
        DefineSupport stub = buildSupport(uri, mdv,
                GlobalVariables.builder().studyName("S").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);

        DataBrowserLibrary dbLib = (DataBrowserLibrary) lib;
        assertNull(dbLib.getBean().getAttributes());
    }


    private static Stream<Arguments> provide_attributesNullCases()
    {
        return Stream.of(//
                Arguments.of("ignoresDocRefsWithNonValidationLeafId", "non-val",
                        sdSupplier(() -> SupplementalDoc.builder()
                                .documentRefs(List
                                        .of(DocumentRef.builder().leafID("annotated-crf").build()))
                                .build())),
                Arguments.of("ignoresValidationLeafIdWithoutMatchingLeaf", "val-no-leaf",
                        sdSupplier(() -> SupplementalDoc.builder()
                                .documentRefs(List.of(DocumentRef.builder()
                                        .leafID("validationreport-sdtm34-r1").build()))
                                .build())),
                Arguments.of("handlesNullLeafId", "null-leafid",
                        sdSupplier(() -> SupplementalDoc.builder()
                                .documentRefs(List.of(DocumentRef.builder().leafID(null).build()))
                                .build())),
                Arguments.of("handlesNullSupplementalDoc", "null-sd", sdSupplier(() -> null)),
                Arguments.of("supplementalDocWithEmptyDocRefList", "empty-docrefs", sdSupplier(
                        () -> SupplementalDoc.builder().documentRefs(List.of()).build())));
    }


    private static Supplier<SupplementalDoc> sdSupplier(Supplier<SupplementalDoc> aSupplier)
    {
        return aSupplier;
    }

    // ==================== getLibraryAttribute behaviour ====================


    @Test
    void getLibraryAttribute_attributesMapNull_returnsNull() throws IOException
    {
        URI uri = uniqueUri("attrnull");
        DefineSupport stub = buildSupport(uri, mdvWith(List.of(), List.of()),
                GlobalVariables.builder().studyName("S").build());
        DefineCache.sharedInstance().setDefineLoader(_ -> stub);

        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary lib = provider.provide(uri, null);

        assertNull(provider.getLibraryAttribute(lib, "any-key"));
    }
}
