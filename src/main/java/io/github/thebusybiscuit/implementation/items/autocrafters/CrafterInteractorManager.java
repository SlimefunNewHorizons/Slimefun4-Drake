package io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bukkit.block.Block;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/** Registro de ids de Slimefun que un autocrafter puede usar como almacén (en lugar de un cofre). */
public final class CrafterInteractorManager {

    private static final Map<String, Function<BlockMenu, CrafterInteractable>> HANDLERS = new ConcurrentHashMap<>();

    private CrafterInteractorManager() {}

    public static void register(String id, Function<BlockMenu, CrafterInteractable> factory) {
        HANDLERS.put(id, factory);
    }

    @Nullable
    public static CrafterInteractable getInteractor(Block b) {
        String id = BlockStorage.checkID(b);
        Function<BlockMenu, CrafterInteractable> factory = id == null ? null : HANDLERS.get(id);
        BlockMenu menu = factory == null ? null : BlockStorage.getInventory(b);
        return menu == null ? null : factory.apply(menu);
    }
}
