package io.github.thebusybiscuit.slimefun4.core.services.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class RustNativeEngineTest {

    @Test
    void addSaturatingMatchesSumInJava() {
        int[][] cases = {{Integer.MAX_VALUE, 1}, {Integer.MIN_VALUE, -1}, {5, -7}, {0, 0}};
        for (int[] c : cases) {
            assertEquals(RustNativeEngine.sumInJava(c),
                io.github.thebusybiscuit.slimefun4.api.services.NativeAccelerationService.addSaturating(c[0], c[1]));
        }
    }

    @Test
    void sumsValuesWithJavaFallback() {
        assertEquals(60, RustNativeEngine.sumInJava(new int[] {10, 20, 30}));
    }

    @Test
    void fallbackSaturatesOverflowAndUnderflow() {
        assertEquals(Integer.MAX_VALUE, RustNativeEngine.sumInJava(new int[] {Integer.MAX_VALUE, 1}));
        assertEquals(Integer.MIN_VALUE, RustNativeEngine.sumInJava(new int[] {Integer.MIN_VALUE, -1}));
        assertEquals(Integer.MAX_VALUE - 1, RustNativeEngine.sumInJava(new int[] {Integer.MAX_VALUE, 1, -1}));
    }

    @Test
    void nativeLibraryMatchesJavaFallbackWhenProvided() {
        String library = System.getProperty("slimefun.native.library");
        Assumptions.assumeTrue(library != null && !library.isBlank());

        RustNativeEngine.loadLibraryForTesting(library);
        assertEquals(2, RustNativeEngine.nativeAbiVersionForTesting());

        int[][] samples = {
            {10, 20, 30},
            {Integer.MAX_VALUE, 1},
            {Integer.MIN_VALUE, -1},
            {Integer.MAX_VALUE, 1, -1},
            {}
        };
        for (int[] sample : samples) {
            assertEquals(
                RustNativeEngine.sumInJava(sample),
                RustNativeEngine.nativeSumSaturatingForTesting(sample)
            );
        }

        double javaPrice = RustNativeEngine.calculateMarketPriceInJava(
            250.0D, 12L, 53246.0D, 100_000_000.0D, 0.85D, 1.85D, 0.02D, 1.45D, 1.01D
        );
        double nativePrice = RustNativeEngine.nativeCalculateMarketPriceForTesting(
            250.0D, 12L, 53246.0D, 100_000_000.0D, 0.85D, 1.85D, 0.02D, 1.45D, 1.01D
        );
        assertEquals(javaPrice, nativePrice);
    }
}
