package com.github.drakescraft_labs.slimefun4.implementation.tasks.armor;

import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import com.github.drakescraft_labs.slimefun4.api.items.HashedArmorpiece;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.player.PlayerProfile;
import com.github.drakescraft_labs.slimefun4.core.attributes.Radioactive;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.items.armor.SlimefunArmorPiece;

/**
 * The {@link SlimefunArmorTask} is responsible for handling {@link SlimefunArmorPiece}
 *
 * @author TheBusyBiscuit
 * @author martinbrom
 * @author Semisol
 */
public class SlimefunArmorTask extends AbstractArmorTask {

    private static final int REFRESH_MARGIN_TICKS = 120;

    @Override
    @ParametersAreNonnullByDefault
    protected void onPlayerTick(Player p, PlayerProfile profile) {
        /*
         * Una sola tarea sincronizada por jugador en vez de una por pieza de armadura.
         * El inventario tambien se lee aqui, en el hilo principal, como exige Bukkit.
         */
        Slimefun.runSync(() -> {
            if (p.isOnline()) {
                updateAndHandleArmor(p, p.getInventory().getArmorContents(), profile.getArmor());
            }
        });
    }

    @ParametersAreNonnullByDefault
    private void updateAndHandleArmor(Player p, ItemStack[] armor, HashedArmorpiece[] cachedArmor) {
        for (int slot = 0; slot < 4; slot++) {
            ItemStack item = armor[slot];
            HashedArmorpiece armorPiece = cachedArmor[slot];

            if (armorPiece.hasDiverged(item)) {
                SlimefunItem sfItem = SlimefunItem.getByItem(item);

                if (!(sfItem instanceof SlimefunArmorPiece)) {
                    // If it isn't actually Armor, then we won't care about it.
                    sfItem = null;
                }

                armorPiece.update(item, sfItem);
            }

            if (item != null && armorPiece.getItem().isPresent()) {
                SlimefunArmorPiece sfArmorPiece = armorPiece.getItem().get();

                if (sfArmorPiece.canUse(p, true)) {
                    onArmorPieceTick(p, sfArmorPiece, item);
                }
            }
        }
    }

    /**
     * Method to handle behavior for pieces of armor.
     * It is called per-player and per piece of armor.
     *
     * @param p
     *            The {@link Player} wearing the piece of armor
     * @param sfArmorPiece
     *            {@link SlimefunArmorPiece} Slimefun instance of the piece of armor
     * @param armorPiece
     *            The actual {@link ItemStack} of the armor piece
     */
    @ParametersAreNonnullByDefault
    protected void onArmorPieceTick(Player p, SlimefunArmorPiece sfArmorPiece, ItemStack armorPiece) {
        for (PotionEffect effect : sfArmorPiece.getPotionEffects()) {
            PotionEffect current = p.getPotionEffect(effect.getType());
            if (current == null || shouldRefresh(
                    current.getAmplifier(), current.getDuration(), effect.getAmplifier())) {
                p.addPotionEffect(effect, true);
            }
        }
    }

    /**
     * Refreshes an armor effect only near expiration or when the requested amplifier is stronger.
     * Removing HEALTH_BOOST before re-adding it temporarily lowers max health and damages players.
     */
    static boolean shouldRefresh(int currentAmplifier, int currentDuration, int expectedAmplifier) {
        return currentAmplifier < expectedAmplifier || currentDuration <= REFRESH_MARGIN_TICKS;
    }
}
