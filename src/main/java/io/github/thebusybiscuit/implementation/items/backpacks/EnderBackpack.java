package io.github.thebusybiscuit.slimefun4.implementation.items.backpacks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Material;
import org.bukkit.block.EnderChest;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;

/**
 * The {@link EnderBackpack} is a pretty simple {@link SlimefunItem} which opens your
 * {@link EnderChest} upon right clicking.
 * 
 * @author TheBusyBiscuit
 *
 */
public class EnderBackpack extends SimpleSlimefunItem<ItemUseHandler> implements NotPlaceable {

    private static final long OPEN_COOLDOWN_MS = 250L;
    private final Map<UUID, Long> lastOpenTime = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public EnderBackpack(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return e -> {
            e.cancel();
            Player p = e.getPlayer();
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            if (e.getClickedBlock().isPresent() && e.getClickedBlock().get().getType() == Material.ENDER_CHEST) {
                return;
            }
            InventoryView open = p.getOpenInventory();
            if (open != null && open.getTopInventory() != null) {
                InventoryType topType = open.getTopInventory().getType();
                if (topType != InventoryType.CRAFTING && topType != InventoryType.CREATIVE) {
                    return;
                }
            }
            long now = System.currentTimeMillis();
            Long prev = lastOpenTime.put(p.getUniqueId(), now);
            if (prev != null && (now - prev) < OPEN_COOLDOWN_MS) {
                return;
            }
            p.openInventory(p.getEnderChest());
            SoundEffect.ENDER_BACKPACK_OPEN_SOUND.playFor(p);
        };
    }
}
