package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunGuideOpenEvent;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;

public class SlimefunGuideListener implements Listener {

    private static final NamespacedKey LABORATORY_GUIDE_KEY = NamespacedKey.fromString("slimefun:laboratory_guide");
    private final boolean giveOnFirstJoin;
    private final Slimefun plugin;

    public SlimefunGuideListener(@Nonnull Slimefun plugin, boolean giveOnFirstJoin) {
        this.plugin = plugin;
        this.giveOnFirstJoin = giveOnFirstJoin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        scheduleLaboratoryGuide(e.getPlayer());

        if (giveOnFirstJoin && !e.getPlayer().hasPlayedBefore()) {
            Player p = e.getPlayer();

            if (!Slimefun.getWorldSettingsService().isWorldEnabled(p.getWorld())) {
                return;
            }

            SlimefunGuideMode type = SlimefunGuide.getDefaultMode();
            p.getInventory().addItem(SlimefunGuide.getItem(type).clone());
        }
    }

    /** Synchronizes the testing catalog after modality inventory plugins finish switching inventories. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        removeCheatGuides(e.getPlayer());
        scheduleLaboratoryGuide(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerRightClickEvent e) {
        Player p = e.getPlayer();

        if (tryOpenGuide(p, e, SlimefunGuideMode.SURVIVAL_MODE) == Result.ALLOW) {
            if (p.isSneaking()) {
                SlimefunGuideSettings.openSettings(p, e.getItem());
            } else {
                openGuide(p, e, SlimefunGuideMode.SURVIVAL_MODE);
            }
        } else if (tryOpenGuide(p, e, SlimefunGuideMode.CHEAT_MODE) == Result.ALLOW) {
            if (p.isSneaking()) {
                SlimefunGuideSettings.openSettings(p, e.getItem());
            } else {
                if (io.github.thebusybiscuit.slimefun4.core.services.CheatPolicy.canUseCheat(p)) {
                    openGuide(p, e, SlimefunGuideMode.CHEAT_MODE);
                } else {
                    e.cancel();
                    Slimefun.getLocalization().sendMessage(p, "messages.no-permission", true);
                }
            }
        }
    }

    private void scheduleLaboratoryGuide(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!io.github.thebusybiscuit.slimefun4.core.services.CheatPolicy.isLaboratoryAccess(player)) {
                removeCheatGuides(player);
                return;
            }

            ItemStack guide = createLaboratoryGuide();
            for (ItemStack current : player.getInventory().getContents()) {
                if (isLaboratoryGuide(current)) {
                    return;
                }
            }
            if (player.getInventory().addItem(guide.clone()).isEmpty()) {
                player.sendMessage("§aLaboratory catalog received. Open the book or use /sf cheat.");
            } else {
                player.sendMessage("§eYour inventory is full. Use /sf cheat to open the laboratory catalog.");
            }
        }, 5L);
    }

    private void removeCheatGuides(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (isLaboratoryGuide(current)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private ItemStack createLaboratoryGuide() {
        if (LABORATORY_GUIDE_KEY == null) {
            throw new IllegalStateException("Could not create the laboratory guide key");
        }
        ItemStack guide = SlimefunGuide.getItem(SlimefunGuideMode.CHEAT_MODE).clone();
        ItemMeta meta = guide.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("The Slimefun cheat guide has no item metadata");
        }
        meta.getPersistentDataContainer().set(LABORATORY_GUIDE_KEY, PersistentDataType.BYTE, (byte) 1);
        guide.setItemMeta(meta);
        return guide;
    }

    private boolean isLaboratoryGuide(ItemStack item) {
        return LABORATORY_GUIDE_KEY != null
                && item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(LABORATORY_GUIDE_KEY, PersistentDataType.BYTE);
    }

    @ParametersAreNonnullByDefault
    private void openGuide(Player p, PlayerRightClickEvent e, SlimefunGuideMode layout) {
        SlimefunGuideOpenEvent event = new SlimefunGuideOpenEvent(p, e.getItem(), layout);
        Bukkit.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            e.cancel();
            SlimefunGuide.openGuide(p, event.getGuideLayout());
        }
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    private Result tryOpenGuide(Player p, PlayerRightClickEvent e, SlimefunGuideMode layout) {
        ItemStack item = e.getItem();
        if (SlimefunUtils.isItemSimilar(item, SlimefunGuide.getItem(layout), false, false)) {

            if (!Slimefun.getWorldSettingsService().isWorldEnabled(p.getWorld())) {
                Slimefun.getLocalization().sendMessage(p, "messages.disabled-item", true);
                return Result.DENY;
            }

            return Result.ALLOW;
        }

        return Result.DEFAULT;
    }

}
