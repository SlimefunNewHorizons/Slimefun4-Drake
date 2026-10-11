package com.github.drakescraft_labs.slimefun4.utils;

import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Visual effects that used to go through {@link org.bukkit.Effect}.
 * {@code Effect.STEP_SOUND} and {@code Effect.SMOKE} are marked for removal in Paper 26.2,
 * and their replacements ({@code DESTROY_BLOCK}, {@code SMOKE_SHOOT}) do not exist on
 * 1.21.11 or 26.1. Particles and sounds work the same on all of them.
 */
public final class BlockEffects {

    private BlockEffects() {}

    /**
     * Plays the particles and sound of a block being broken, like {@code Effect.STEP_SOUND}.
     *
     * @param location
     *            The {@link Location} of the block
     * @param material
     *            The block type whose particles and sound to use
     */
    public static void playBreakEffect(@Nonnull Location location, @Nonnull Material material) {
        World world = location.getWorld();

        if (world == null || !material.isBlock() || material.isAir()) {
            return;
        }

        BlockData data = material.createBlockData();
        Location center = location.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.BLOCK, center, 32, 0.25, 0.25, 0.25, data);
        world.playSound(center, data.getSoundGroup().getBreakSound(), 1F, 0.8F);
    }

    /**
     * Plays a small puff of smoke, like {@code Effect.SMOKE}.
     *
     * @param location
     *            Where the smoke appears
     */
    public static void playSmoke(@Nonnull Location location) {
        World world = location.getWorld();

        if (world != null) {
            world.spawnParticle(Particle.SMOKE, location, 8, 0.1, 0.1, 0.1, 0.01);
        }
    }
}
