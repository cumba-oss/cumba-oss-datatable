package net.cumba.datatable.impl.merge;

import static net.cumba.datatable.impl.merge.MergeFixtures.request;
import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.view.UnionDataTable;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTables;
import net.cumba.datatable.merge.SuppMergeSpec;
import net.cumba.datatable.testkit.IDataTableReadConcurrencyContract;
import org.junit.jupiter.api.Test;

/**
 * {@code IDataTable}'s concurrent-reads contract (§3.10) on the engine's merged evaluation table: a
 * {@code MergedTableImpl} whose keyed part is a lazy key-match view (one volatile probe cache) with
 * a union on both sides — the primary split in two members and a union partner — so the
 * {@code KeyCellReader.Union} readers of the record side and the partner side are both built on
 * first use, plus the SUPP part and the matched flags. The engine's rule pool reads one such table
 * from several threads; the harness's cold arm first-touches a fresh merge under contention (PACK G
 * fold, G4 review).
 */
class MergedTableReadConcurrencyTest
{

    private static final int SUBJECTS = 24;

    @Test
    void aKeyedSuppAndFlaggedMergedTable()
    {
        MergedTable merged = merged();
        // the fixture really merged: the keyed, SUPP and flag parts all carry columns
        assertEquals(SUBJECTS, merged.getRowCount());
        assertEquals(List.of(), merged.issues());

        IDataTableReadConcurrencyContract.checkCold(MergedTableReadConcurrencyTest::merged);
    }


    private static MergedTable merged()
    {
        List<String> aeA = new ArrayList<>();
        List<String> aeB = new ArrayList<>();
        List<String> suppRows = new ArrayList<>();
        List<String> dmA = new ArrayList<>();
        List<String> dmB = new ArrayList<>();
        for (int s = 0; s < SUBJECTS; s++)
        {
            String subject = "01-%03d".formatted(s);
            (s % 2 == 0 ? aeA : aeB).add("S1," + subject + "," + (s + 1) + ",TERM" + (s % 5));
            if (s % 3 != 0)
            {
                suppRows.add("S1,AE," + subject + ",AESEQ," + (s + 1) + ",AETRTEM,"
                        + (s % 2 == 0 ? "Y" : "N"));
            }
            // DM split across two members, one subject missing (an unmatched row, kept by LEFT)
            if (s != 5)
            {
                (s < SUBJECTS / 2 ? dmA : dmB)
                        .add("S1," + subject + "," + (s % 2 == 0 ? "F" : "M"));
            }
        }
        // the primary split in two members (a split domain), so its readers are union readers
        IDataTable ae = new UnionDataTable("AE",
                table("AE", "STUDYID,USUBJID,AESEQ:N,AETERM", aeA.toArray(String[]::new)),
                table("AE", "STUDYID,USUBJID,AESEQ:N,AETERM", aeB.toArray(String[]::new)));
        IDataTable supp = table("SUPPAE", "STUDYID,RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                suppRows.toArray(String[]::new));
        IDataTable dm = new UnionDataTable("DM",
                table("DM", "STUDYID,USUBJID,SEX", dmA.toArray(String[]::new)),
                table("DM", "STUDYID,USUBJID,SEX", dmB.toArray(String[]::new)));

        return MergedTables.build(request(ae, dm, supp)
                .keyed(KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("STUDYID"))
                        .key(KeyColumn.of("USUBJID")).joinType(JoinType.LEFT).build())
                .supp(SuppMergeSpec.builder().build()).matchedFlags(true).build());
    }
}
