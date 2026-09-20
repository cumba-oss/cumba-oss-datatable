package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The value-level accessors of {@link DefineSupport}: {@code getWhereClauseDefs},
 * {@code getWhereClauseDefByOid} and {@code getValueListDefByOid}.
 *
 * <p>
 * These are the lookups the value-level metadata of a submission is read through — a WhereClauseDef
 * says under which condition a variable takes a particular set of attributes, and a ValueListDef
 * names the set. A lookup that answers empty does not fail: the caller simply sees a variable with
 * no value-level definition, which is indistinguishable from one that genuinely has none. Nothing
 * exercised these three at all, so an empty answer was the untested default.
 * </p>
 */
class DefineSupportValueLevelLookupTest
{

    private DefineSupport support;

    @BeforeEach
    void setUp()
    {
        WhereClauseDef wcSupine = WhereClauseDef.builder().oid("WC.VS.SUPINE").build();
        WhereClauseDef wcStanding = WhereClauseDef.builder().oid("WC.VS.STANDING").build();
        ValueListDef vlPos = ValueListDef.builder().oid("VL.VS.VSPOS").build();
        ValueListDef vlOrres = ValueListDef.builder().oid("VL.VS.VSORRES").build();

        MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.1")
                .whereClauseDefs(List.of(wcSupine, wcStanding))
                .valueListDefs(List.of(vlPos, vlOrres)).build();
        Study study = Study.builder().oid("S.1").metaDataVersions(List.of(mdv)).build();
        ODM odm = ODM.builder().fileOID("F.1").studies(List.of(study)).build();

        support = new DefineSupport(URI.create("file:///test/define.xml"), odm);
    }


    @Test
    void everyWhereClauseDefOfTheDocumentIsStreamed()
    {
        assertEquals(List.of("WC.VS.SUPINE", "WC.VS.STANDING"),
                support.getWhereClauseDefs().map(WhereClauseDef::getOid).toList());
    }


    @Test
    void aWhereClauseDefIsFoundByItsOid()
    {
        assertEquals("WC.VS.STANDING",
                support.getWhereClauseDefByOid("WC.VS.STANDING").orElseThrow().getOid());
    }


    /** An OID that is not in the document must answer empty rather than the first entry. */
    @Test
    void anUnknownWhereClauseOidAnswersEmpty()
    {
        assertTrue(support.getWhereClauseDefByOid("WC.NOPE").isEmpty());
        assertTrue(support.getWhereClauseDefByOid(null).isEmpty());
    }


    @Test
    void aValueListDefIsFoundByItsOid()
    {
        assertEquals("VL.VS.VSORRES",
                support.getValueListDefByOid("VL.VS.VSORRES").orElseThrow().getOid());
    }


    @Test
    void anUnknownValueListOidAnswersEmpty()
    {
        assertTrue(support.getValueListDefByOid("VL.NOPE").isEmpty());
        assertTrue(support.getValueListDefByOid(null).isEmpty());
    }


    /** A document that declares neither family must not make the lookups throw. */
    @Test
    void aDocumentWithNoValueLevelMetadataAnswersEmptyEverywhere()
    {
        MetaDataVersion bare = MetaDataVersion.builder().oid("MDV.0").build();
        Study study = Study.builder().oid("S.0").metaDataVersions(List.of(bare)).build();
        DefineSupport ds = new DefineSupport(URI.create("file:///t"),
                ODM.builder().studies(List.of(study)).build());

        assertEquals(0, ds.getWhereClauseDefs().count());
        assertFalse(ds.getWhereClauseDefByOid("WC.VS.SUPINE").isPresent());
        assertFalse(ds.getValueListDefByOid("VL.VS.VSPOS").isPresent());
    }
}
