package com.github.drakescraft_labs.slimefun4.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ErrorReportLimiterTest {

    @Test
    void suppressesDuplicateSignaturesOnlyWithinTheConfiguredWindow() {
        ErrorReportLimiter limiter = new ErrorReportLimiter(30_000L, 16);

        assertTrue(limiter.tryAcquire("Networks:IllegalStateException:tick", 1_000L));
        assertFalse(limiter.tryAcquire("Networks:IllegalStateException:tick", 30_999L));
        assertTrue(limiter.tryAcquire("Networks:IllegalStateException:tick", 31_000L));
    }

    @Test
    void permitsDistinctFailuresWithoutSuppressingTheirFirstReport() {
        ErrorReportLimiter limiter = new ErrorReportLimiter(30_000L, 16);

        assertTrue(limiter.tryAcquire("Networks:IllegalStateException:tick", 1_000L));
        assertTrue(limiter.tryAcquire("Networks:NullPointerException:other-tick", 1_000L));
    }
}
