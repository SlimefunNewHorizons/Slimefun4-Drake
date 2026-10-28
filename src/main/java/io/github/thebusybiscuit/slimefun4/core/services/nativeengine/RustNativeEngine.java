package io.github.thebusybiscuit.slimefun4.core.services.nativeengine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.services.NativeAccelerationService;

/**
 * Loads the Linux JNI artifact and provides a fail-safe Java fallback.
 */
public final class RustNativeEngine implements NativeAccelerationService {

    private static final int MIN_ABI = 1;
    private static final int MAX_ABI = 2;

    private final AtomicLong nativeCalls = new AtomicLong();
    private final AtomicLong fallbackCalls = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    private volatile boolean available;
    private volatile int abiVersion;
    private int minimumBatchSize = 2;
    /** Off by default: benchmarks (docs/RUST_NATIVO.md) show JNI sum never beats Java. */
    private boolean nativeSum;

    public void start(@Nonnull JavaPlugin plugin, @Nonnull Config config) {
        minimumBatchSize = Math.max(1, config.getInt("native-engine.minimum-batch-size"));
        nativeSum = config.contains("native-engine.native-sum") && config.getBoolean("native-engine.native-sum");
        plugin.getServer().getServicesManager().register(
            NativeAccelerationService.class,
            this,
            plugin,
            ServicePriority.Normal
        );

        if (!config.getBoolean("native-engine.enabled")) {
            plugin.getLogger().info("[Slimefun-Rust] Motor nativo deshabilitado por configuración.");
            return;
        }

        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("linux")) {
                throw new IllegalStateException("DrakesCraft requiere el artefacto Linux libslimefun_ffi.so");
            }

            Path dataRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            Path library = dataRoot.resolve(config.getString("native-engine.library")).normalize();
            if (!library.startsWith(dataRoot)) {
                throw new IllegalArgumentException("La biblioteca nativa debe vivir dentro de plugins/Slimefun");
            }
            if (!Files.isRegularFile(library)) {
                plugin.getLogger().info(
                    "[Slimefun-Rust] Biblioteca nativa opcional no instalada; EnergyNet usará fallback Java."
                );
                return;
            }

            System.load(library.toString());
            abiVersion = nativeAbiVersion();
            if (abiVersion < MIN_ABI || abiVersion > MAX_ABI) {
                throw new IllegalStateException("ABI " + abiVersion + " incompatible; esperado " + MIN_ABI + "-" + MAX_ABI);
            }
            if (nativeSumSaturating(new int[] {Integer.MAX_VALUE, 1}) != Integer.MAX_VALUE) {
                throw new IllegalStateException("La autoprueba de saturación falló");
            }

            available = true;
            plugin.getLogger().info("[Slimefun-Rust] Motor JNI activo (ABI " + abiVersion + ").");
        } catch (Throwable error) {
            available = false;
            failures.incrementAndGet();
            plugin.getLogger().log(
                Level.SEVERE,
                "[Slimefun-Rust] No se pudo activar el motor nativo; EnergyNet continuará con fallback Java.",
                error
            );
        }
    }

    public void stop(@Nonnull JavaPlugin plugin) {
        plugin.getServer().getServicesManager().unregister(NativeAccelerationService.class, this);
        available = false;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int getAbiVersion() {
        return abiVersion;
    }

    @Override
    public int sumSaturating(@Nonnull int[] values) {
        if (!available || !nativeSum || values.length < minimumBatchSize) {
            fallbackCalls.incrementAndGet();
            return sumInJava(values);
        }

        try {
            int result = nativeSumSaturating(values);
            nativeCalls.incrementAndGet();
            return result;
        } catch (Throwable error) {
            failures.incrementAndGet();
            fallbackCalls.incrementAndGet();
            available = false;
            return sumInJava(values);
        }
    }

    @Override
    public double calculateMarketPrice(
        double basePrice,
        long demand,
        double totalWealth,
        double referenceWealth,
        double minimumFactor,
        double maximumFactor,
        double demandStep,
        double maximumDemandFactor,
        double pulseFactor
    ) {
        if (!available) {
            fallbackCalls.incrementAndGet();
            return calculateMarketPriceInJava(
                basePrice, demand, totalWealth, referenceWealth, minimumFactor,
                maximumFactor, demandStep, maximumDemandFactor, pulseFactor
            );
        }

        try {
            double result = nativeCalculateMarketPrice(
                basePrice, demand, totalWealth, referenceWealth, minimumFactor,
                maximumFactor, demandStep, maximumDemandFactor, pulseFactor
            );
            nativeCalls.incrementAndGet();
            return result;
        } catch (Throwable error) {
            failures.incrementAndGet();
            fallbackCalls.incrementAndGet();
            available = false;
            return calculateMarketPriceInJava(
                basePrice, demand, totalWealth, referenceWealth, minimumFactor,
                maximumFactor, demandStep, maximumDemandFactor, pulseFactor
            );
        }
    }

    @Override
    public long getNativeCalls() {
        return nativeCalls.get();
    }

    @Override
    public long getFallbackCalls() {
        return fallbackCalls.get();
    }

    @Override
    public long getFailures() {
        return failures.get();
    }

    static int sumInJava(int[] values) {
        int result = 0;
        for (int value : values) {
            long candidate = (long) result + value;
            result = candidate > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : candidate < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) candidate;
        }
        return result;
    }

    static double calculateMarketPriceInJava(
        double basePrice,
        long demand,
        double totalWealth,
        double referenceWealth,
        double minimumFactor,
        double maximumFactor,
        double demandStep,
        double maximumDemandFactor,
        double pulseFactor
    ) {
        double safeReference = Math.max(1.0D, referenceWealth);
        double wealthRatio = Math.max(0.0D, totalWealth) / safeReference;
        double wealthFactor = 0.90D + (Math.log1p(wealthRatio) / Math.log(11.0D)) * 0.65D;
        double demandFactor = Math.min(maximumDemandFactor, 1.0D + Math.max(0L, demand) * demandStep);
        double combined = Math.max(
            minimumFactor,
            Math.min(maximumFactor, wealthFactor * demandFactor * pulseFactor)
        );
        return Math.max(0.01D, Math.round(basePrice * combined * 100.0D) / 100.0D);
    }

    static void loadLibraryForTesting(String absolutePath) {
        System.load(absolutePath);
    }

    static int nativeAbiVersionForTesting() {
        return nativeAbiVersion();
    }

    static int nativeSumSaturatingForTesting(int[] values) {
        return nativeSumSaturating(values);
    }

    static double nativeCalculateMarketPriceForTesting(
        double basePrice,
        long demand,
        double totalWealth,
        double referenceWealth,
        double minimumFactor,
        double maximumFactor,
        double demandStep,
        double maximumDemandFactor,
        double pulseFactor
    ) {
        return nativeCalculateMarketPrice(
            basePrice, demand, totalWealth, referenceWealth, minimumFactor,
            maximumFactor, demandStep, maximumDemandFactor, pulseFactor
        );
    }

    private static native int nativeAbiVersion();

    private static native int nativeSumSaturating(int[] values);

    private static native double nativeCalculateMarketPrice(
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
}
