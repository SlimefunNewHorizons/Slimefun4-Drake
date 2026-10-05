package com.github.drakescraft_labs.slimefun4.implementation.operations;

import javax.annotation.Nonnull;

import org.apache.commons.lang.Validate;
import org.bukkit.inventory.ItemStack;

import dev.drake.dough.blocks.BlockPosition;
import com.github.drakescraft_labs.slimefun4.core.machines.MachineOperation;
import com.github.drakescraft_labs.slimefun4.legacy.Objects.SlimefunItem.abstractItems.MachineRecipe;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * This {@link MachineOperation} represents a crafting process.
 * 
 * @author TheBusyBiscuit
 *
 */
public class CraftingOperation implements MachineOperation {

    private final ItemStack[] ingredients;
    private final ItemStack[] results;

    private final int totalTicks;
    private int currentTicks = 0;
    private volatile boolean refunded = false;

    public CraftingOperation(@Nonnull MachineRecipe recipe) {
        this(recipe.getInput(), recipe.getOutput(), recipe.getTicks());
    }

    public CraftingOperation(@Nonnull ItemStack[] ingredients, @Nonnull ItemStack[] results, int totalTicks) {
        Validate.notEmpty(ingredients, "The Ingredients array cannot be empty or null");
        Validate.notEmpty(results, "The results array cannot be empty or null");
        Validate.isTrue(totalTicks >= 0, "The amount of total ticks must be a positive integer or zero, received: " + totalTicks);

        this.ingredients = ingredients;
        this.results = results;
        this.totalTicks = totalTicks;
    }

    /**
     * Atomically marks this operation as refunded so ingredients are never refunded or dropped twice.
     *
     * @return true if this call transitioned refunded from false to true; false if already refunded.
     */
    public synchronized boolean markRefunded() {
        if (refunded) {
            return false;
        }
        refunded = true;
        return true;
    }

    public boolean isRefunded() {
        return refunded;
    }

    @Override
    public void addProgress(int num) {
        Validate.isTrue(num > 0, "Progress must be positive.");
        currentTicks += num;
    }

    public @Nonnull ItemStack[] getIngredients() {
        return ingredients;
    }

    public @Nonnull ItemStack[] getResults() {
        return results;
    }

    @Override
    public int getProgress() {
        return currentTicks;
    }

    @Override
    public int getTotalTicks() {
        return totalTicks;
    }

    @Override
    public void onCancel(BlockPosition position) {
        if (!markRefunded()) {
            return;
        }

        try {
            World world = position.getWorld();
            if (world == null) {
                return;
            }

            Location loc = new Location(world, position.getX(), position.getY(), position.getZ());
            BlockMenu menu = BlockStorage.getInventory(loc);

            for (ItemStack ingredient : ingredients) {
                if (ingredient != null && !ingredient.getType().isAir()) {
                    if (menu != null) {
                        ItemStack remaining = menu.pushItem(ingredient.clone(), 19, 20);
                        if (remaining != null && remaining.getAmount() > 0) {
                            remaining = menu.pushItem(remaining, 24, 25);
                            if (remaining != null && remaining.getAmount() > 0) {
                                world.dropItemNaturally(loc, remaining);
                            }
                        }
                    } else {
                        world.dropItemNaturally(loc, ingredient.clone());
                    }
                }
            }
            if (menu != null) {
                menu.markDirty();
            }
        } catch (Throwable ignored) {
            // Ignore exceptions if world is unloaded
        }
    }

}
