package com.github.drakescraft_labs.slimefun4.implementation.tasks;

import javax.annotation.Nonnull;

import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;

import dev.drake.dough.skins.PlayerHead;
import dev.drake.dough.skins.PlayerSkin;
import com.github.drakescraft_labs.slimefun4.implementation.items.electric.Capacitor;
import com.github.drakescraft_labs.slimefun4.utils.HeadTexture;
import io.papermc.lib.PaperLib;

/**
 * This task is run whenever a {@link Capacitor} needs to update their texture.
 * <strong>This must be executed on the main {@link Server} {@link Thread}!</strong>
 * 
 * @author TheBusyBiscuit
 *
 */
public class CapacitorTextureUpdateTask implements Runnable {

    /**
     * The {@link Location} of the {@link Capacitor}.
     */
    private final Location l;

    /**
     * The level of how "full" this {@link Capacitor} is.
     * From 0.0 to 1.0.
     */
    private final double filledPercentage;

    /**
     * This creates a new {@link CapacitorTextureUpdateTask} with the given parameters.
     * 
     * @param l
     *            The {@link Location} of the {@link Capacitor}
     * @param charge
     *            The amount of charge in this {@link Capacitor}
     * @param capacity
     *            The capacity of this {@link Capacitor}
     */
    public CapacitorTextureUpdateTask(@Nonnull Location l, double charge, double capacity) {
        Validate.notNull(l, "The Location cannot be null");

        this.l = l;
        this.filledPercentage = charge / capacity;
    }

    @Override
    public void run() {
        Block b = l.getBlock();
        Material type = b.getType();

        // Ensure that this Block is still a Player Head
        if (type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD) {
            if (filledPercentage <= 0.25) {
                // 0-25% capacity
                setTexture(b, HeadTexture.CAPACITOR_25);
            } else if (filledPercentage <= 0.5) {
                // 25-50% capacity
                setTexture(b, HeadTexture.CAPACITOR_50);
            } else if (filledPercentage <= 0.75) {
                // 50-75% capacity
                setTexture(b, HeadTexture.CAPACITOR_75);
            } else {
                // 75-100% capacity
                setTexture(b, HeadTexture.CAPACITOR_100);
            }
        }
    }

    /**
     * This returns which of the four textures matches the given charge.
     * It uses the same thresholds as {@link #run()}, so callers can skip
     * the update when the texture would not change.
     *
     * @param charge
     *            The amount of charge in this {@link Capacitor}
     * @param capacity
     *            The capacity of this {@link Capacitor}
     *
     * @return The texture level, from 0 (0-25%) to 3 (75-100%)
     */
    public static int getTextureLevel(double charge, double capacity) {
        double filledPercentage = charge / capacity;

        if (filledPercentage <= 0.25) {
            return 0;
        } else if (filledPercentage <= 0.5) {
            return 1;
        } else if (filledPercentage <= 0.75) {
            return 2;
        } else {
            return 3;
        }
    }

    private void setTexture(@Nonnull Block b, @Nonnull HeadTexture texture) {
        PlayerSkin skin = PlayerSkin.fromHashCode(texture.getUniqueId(), texture.getTexture());
        PlayerHead.setSkin(b, skin, false);

        b.getState(false).update(true, false);
    }

}
