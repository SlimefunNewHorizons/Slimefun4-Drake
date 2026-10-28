package io.github.thebusybiscuit.slimefun4.api.services;

import javax.annotation.Nonnull;

/**
 * Public bridge for deterministic calculations delegated to Slimefun-Rust.
 * Bukkit objects and world mutations must never cross this boundary.
 */
public interface NativeAccelerationService {

    /**
     * Saturating int addition, allocation-free. This is what hot paths (EnergyNet) must use:
     * benchmarks show the JNI sum never beats it and it avoids the temporary arrays.
     */
    static int addSaturating(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : sum < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) sum;
    }

    boolean isAvailable();

    int getAbiVersion();

    int sumSaturating(@Nonnull int[] values);

    double calculateMarketPrice(
        double basePrice,
        long demand,
        double totalWealth,
        double referenceWealth,
        double minimumFactor,
        double maximumFactor,
        double demandStep,
        double maximumDemandFactor,
        double pulseFactor
    );

    long getNativeCalls();

    long getFallbackCalls();

    long getFailures();
}
