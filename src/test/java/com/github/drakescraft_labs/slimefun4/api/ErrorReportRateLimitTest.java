package com.github.drakescraft_labs.slimefun4.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ErrorReportRateLimitTest {

    @Test
    void allowsOnlyTenReportsPerAddonAndMinute() {
        LocalDateTime minute = LocalDateTime.of(2042, 2, 3, 4, 5);

        for (int i = 0; i < 10; i++) {
            assertEquals(ErrorReport.ReportPermit.ALLOWED, ErrorReport.acquireReportPermit("rate-limit-test", minute));
        }

        assertEquals(ErrorReport.ReportPermit.SUPPRESSION_NOTICE, ErrorReport.acquireReportPermit("rate-limit-test", minute));
        assertEquals(ErrorReport.ReportPermit.SUPPRESSED, ErrorReport.acquireReportPermit("rate-limit-test", minute));
    }

    @Test
    void startsANewWindowForTheNextMinuteAndForAnotherAddon() {
        LocalDateTime minute = LocalDateTime.of(2042, 2, 3, 4, 6);

        assertEquals(ErrorReport.ReportPermit.ALLOWED, ErrorReport.acquireReportPermit("rate-limit-next-minute", minute));
        assertEquals(ErrorReport.ReportPermit.ALLOWED, ErrorReport.acquireReportPermit("rate-limit-next-minute", minute.plusMinutes(1)));
        assertEquals(ErrorReport.ReportPermit.ALLOWED, ErrorReport.acquireReportPermit("rate-limit-other-addon", minute));
    }
}
