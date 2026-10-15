package io.github.thebusybiscuit.slimefun4.implementation;

import io.github.thebusybiscuit.slimefun4.core.SlimefunRegistry;
import io.github.thebusybiscuit.slimefun4.core.services.LocalizationService;
import io.github.thebusybiscuit.slimefun4.core.services.MinecraftRecipeService;
import io.github.bakedlibs.dough.protection.ProtectionManager;
import io.github.thebusybiscuit.slimefun4.api.services.NativeAccelerationService;

/**
 * Compatibility class for legacy addons.
 * Delegates to the new Slimefun class.
 * 
 * @author Drake
 */
public final class SlimefunPlugin {

    private SlimefunPlugin() {}

    public static SlimefunRegistry getRegistry() {
        return Slimefun.instance().getRegistry();
    }

    public static LocalizationService getLocalization() {
        return Slimefun.getLocalization();
    }

    public static MinecraftRecipeService getMinecraftRecipeService() {
        return Slimefun.getMinecraftRecipeService();
    }

    public static ProtectionManager getProtectionManager() {
        return Slimefun.instance().getProtectionManager();
    }

    public static NativeAccelerationService getNativeAccelerationService() {
        return Slimefun.getNativeAccelerationService();
    }
}
