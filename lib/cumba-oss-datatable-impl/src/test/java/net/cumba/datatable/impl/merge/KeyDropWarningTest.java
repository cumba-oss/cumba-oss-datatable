package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static net.cumba.datatable.impl.merge.MergeFixtures.column;
import static net.cumba.datatable.impl.merge.MergeFixtures.request;
import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyDropWarning;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MergedTables;
import net.cumba.datatable.merge.SuppMergeSpec;
import org.junit.jupiter.api.Test;

/**
 * {@code REG-Q3} — one wording for every dropped key column: the SUPP merge's WARNING ({@code MET
 * #19}) and the keyed matcher's DEBUG line render from the one {@link KeyDropWarning#TEMPLATE}, so
 * one log search finds every drop. The keyed matcher logs at DEBUG only: the WARNING is its
 * caller's, which knows the rule ({@code PLAN-join-key-one-side-absent-drop} §4.1, QK1).
 */
class KeyDropWarningTest
{

    private static List<LogRecord> at(LogCapture aLogs, Level aLevel)
    {
        return aLogs.records().stream().filter(r -> r.getLevel().equals(aLevel)).toList();
    }


    private static String formatted(LogRecord aRecord)
    {
        return MessageFormat.format(aRecord.getMessage(), aRecord.getParameters());
    }


    @Test
    void theTemplateNamesThePartnerTheKeyAndTheCarrier()
    {
        assertEquals("SV: key column VISITNUM is present on one side only (AE) and is ignored",
                KeyDropWarning.format("SV", KeyDropWarning.keyText("VISITNUM", "VISITNUM"), "AE"));
        assertEquals("DM.RPATHCD/RPATHCD", KeyDropWarning.keyText("DM.RPATHCD", "RPATHCD"),
                "two spellings of one key read LEFT/RIGHT");
    }


    @Test
    void aKeyedColumnOnOneSideOnlyIsLoggedOnceAtDebugInTheSharedWording()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE");
        IDataTable sv = table("SV", "USUBJID,VISITNUM:N,SVSTDTC", "01-001,1,2024-01-01");
        try (var logs = LogCapture.of(KeyedMatcher.class))
        {
            var t = MergedTables.build(request(ae, sv)
                    .keyed(KeyedMergeSpec.builder().qualifier("SV").key(KeyColumn.of("USUBJID"))
                            .key(KeyColumn.of("VISITNUM")).joinType(JoinType.LEFT).build())
                    .build());
            assertEquals(List.of("2024-01-01"), column(t, "SV.SVSTDTC"),
                    "control: the merge ran and paired on USUBJID");
            List<LogRecord> debug = at(logs, Level.FINE);
            assertEquals(1, debug.size(), "one line per dropped column: " + logs.records());
            assertEquals(KeyDropWarning.TEMPLATE, debug.get(0).getMessage(),
                    "the raw pattern is the shared constant, not a copy of its text");
            assertEquals("SV: key column VISITNUM is present on one side only (SV) and is ignored",
                    formatted(debug.get(0)));
            assertEquals(0, logs.count(Level.INFO),
                    "the matcher has no rule to name: it never warns (the caller does)");
        }
    }


    @Test
    void aKeyedColumnAbsentOnBothSidesIsDroppedSilently()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE");
        IDataTable sv = table("SV", "USUBJID,SVSTDTC", "01-001,2024-01-01");
        try (var logs = LogCapture.of(KeyedMatcher.class))
        {
            var t = MergedTables.build(request(ae, sv)
                    .keyed(KeyedMergeSpec.builder().qualifier("SV").key(KeyColumn.of("USUBJID"))
                            .key(KeyColumn.of("VISITNUM")).build())
                    .build());
            assertEquals(List.of("2024-01-01"), column(t, "SV.SVSTDTC"));
            assertEquals(0, logs.records().size(),
                    "QK5 (a): both sides absent is not a one-side drop: " + logs.records());
        }
    }


    @Test
    void theSuppMergeWarnsInTheSameWording()
    {
        IDataTable ae = table("AE", "STUDYID,USUBJID,AESEQ:N", "S1,01-001,1");
        // SUPPAE lacks STUDYID: the component is ignored with the WARNING (MET #19)
        IDataTable supp = table("SUPPAE", "RDOMAIN,USUBJID,IDVAR,IDVARVAL,QNAM,QVAL",
                "AE,01-001,AESEQ,1,Q,Y");
        try (var logs = LogCapture.of(SuppKeyShape.class))
        {
            var t = MergedTables
                    .build(request(ae, supp).supp(SuppMergeSpec.builder().build()).build());
            assertEquals(List.of("Y"), column(t, "Q"), "control: the qualifier merged");
            List<LogRecord> warn = at(logs, Level.WARNING);
            assertEquals(1, warn.size(), logs.records().toString());
            assertEquals(KeyDropWarning.TEMPLATE, warn.get(0).getMessage(),
                    "the SUPP merge and the keyed matcher log the one constant");
            assertEquals("SUPPAE: key column STUDYID is present on one side only (AE) and is"
                    + " ignored", formatted(warn.get(0)));
        }
    }
}
