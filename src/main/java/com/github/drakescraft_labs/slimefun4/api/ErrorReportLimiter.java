package com.github.drakescraft_labs.slimefun4.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounds duplicate error-report generation so one failing addon cannot turn its
 * diagnostic path into sustained main-thread file-system work.
 */
final class ErrorReportLimiter {

    static final long DEFAULT_WINDOW_MILLIS = 30_000L;
    private final long windowMillis;
    private final int maximumSignatures;
    private final ConcurrentHashMap<String, Long> lastReports = new ConcurrentHashMap<>();

    ErrorReportLimiter(long windowMillis, int maximumSignatures) {
        this.windowMillis = windowMillis;
        this.maximumSignatures = maximumSignatures;
    }

    boolean tryAcquire(String signature, long nowMillis) {
        if (lastReports.size() >= maximumSignatures && !lastReports.containsKey(signature)) {
            lastReports.clear();
        }

        AtomicBoolean allowed = new AtomicBoolean(false);
        lastReports.compute(signature, (ignored, previous) -> {
            if (previous == null || nowMillis - previous >= windowMillis) {
                allowed.set(true);
                return nowMillis;
            }

            return previous;
        });

        return allowed.get();
    }
}
