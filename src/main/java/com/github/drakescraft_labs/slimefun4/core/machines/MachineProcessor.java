package com.github.drakescraft_labs.slimefun4.core.machines;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import dev.drake.dough.blocks.BlockPosition;
import com.github.drakescraft_labs.slimefun4.api.events.AsyncMachineOperationFinishEvent;
import com.github.drakescraft_labs.slimefun4.core.attributes.MachineProcessHolder;
import com.github.drakescraft_labs.slimefun4.implementation.operations.CraftingOperation;
import com.github.drakescraft_labs.slimefun4.legacy.Objects.SlimefunItem.interfaces.InventoryBlock;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;
import com.github.drakescraft_labs.slimefun4.utils.ChestMenuUtils;

/**
 * A {@link MachineProcessor} manages different {@link MachineOperation}s and handles
 * their progress.
 * 
 * @author TheBusyBiscuit
 *
 * @param <T>
 *            The type of {@link MachineOperation} this processor can hold.
 * 
 * @see MachineOperation
 * @see MachineProcessHolder
 */
public class MachineProcessor<T extends MachineOperation> {

    private static final Set<MachineProcessor<?>> ALL_PROCESSORS = ConcurrentHashMap.newKeySet();

    private final Map<BlockPosition, T> machines = new ConcurrentHashMap<>();
    private final MachineProcessHolder<T> owner;

    private ItemStack progressBar;

    /**
     * This creates a new {@link MachineProcessor}.
     * 
     * @param owner
     *            The owner of this {@link MachineProcessor}.
     */
    public MachineProcessor(@Nonnull MachineProcessHolder<T> owner) {
        Validate.notNull(owner, "The MachineProcessHolder cannot be null.");

        this.owner = owner;
        ALL_PROCESSORS.add(this);
    }

    /**
     * This returns the owner of this {@link MachineProcessor}.
     * 
     * @return The owner / holder
     */
    public @Nonnull MachineProcessHolder<T> getOwner() {
        return owner;
    }

    /**
     * This returns the progress bar icon for this {@link MachineProcessor}
     * or null if no progress bar was set.
     * 
     * @return The progress bar icon or null
     */
    public @Nullable ItemStack getProgressBar() {
        return progressBar;
    }

    /**
     * This sets the progress bar icon for this {@link MachineProcessor}.
     * You can also set it to null to clear the progress bar.
     * 
     * @param progressBar
     *            An {@link ItemStack} or null
     */
    public void setProgressBar(@Nullable ItemStack progressBar) {
        this.progressBar = progressBar;
    }

    /**
     * This method will start a {@link MachineOperation} at the given {@link Location}.
     * 
     * @param loc
     *            The {@link Location} at which our machine is located.
     * @param operation
     *            The {@link MachineOperation} to start
     * 
     * @return Whether the {@link MachineOperation} was successfully started. This will return false if another
     *         {@link MachineOperation} has already been started at that {@link Location}.
     */
    public boolean startOperation(@Nonnull Location loc, @Nonnull T operation) {
        Validate.notNull(loc, "The location must not be null");
        Validate.notNull(operation, "The operation cannot be null");

        return startOperation(new BlockPosition(loc), operation);
    }

    /**
     * This method will start a {@link MachineOperation} at the given {@link Block}.
     * 
     * @param b
     *            The {@link Block} at which our machine is located.
     * @param operation
     *            The {@link MachineOperation} to start
     * 
     * @return Whether the {@link MachineOperation} was successfully started. This will return false if another
     *         {@link MachineOperation} has already been started at that {@link Block}.
     */
    public boolean startOperation(@Nonnull Block b, @Nonnull T operation) {
        Validate.notNull(b, "The Block must not be null");
        Validate.notNull(operation, "The machine operation cannot be null");

        return startOperation(new BlockPosition(b), operation);
    }

    /**
     * This method will actually start the {@link MachineOperation}.
     * 
     * @param pos
     *            The {@link BlockPosition} of our machine
     * @param operation
     *            The {@link MachineOperation} to start
     * 
     * @return Whether the {@link MachineOperation} was successfully started. This will return false if another
     *         {@link MachineOperation} has already been started at that {@link BlockPosition}.
     */
    public boolean startOperation(@Nonnull BlockPosition pos, @Nonnull T operation) {
        Validate.notNull(pos, "The BlockPosition must not be null");
        Validate.notNull(operation, "The machine operation cannot be null");

        return machines.putIfAbsent(pos, operation) == null;
    }

    /**
     * This returns the current {@link MachineOperation} at that given {@link Location}.
     * 
     * @param loc
     *            The {@link Location} at which our machine is located.
     * 
     * @return The current {@link MachineOperation} or null.
     */
    public @Nullable T getOperation(@Nonnull Location loc) {
        Validate.notNull(loc, "The location cannot be null");

        return getOperation(new BlockPosition(loc));
    }

    /**
     * This returns the current {@link MachineOperation} at that given {@link Block}.
     * 
     * @param b
     *            The {@link Block} at which our machine is located.
     * 
     * @return The current {@link MachineOperation} or null.
     */
    public @Nullable T getOperation(@Nonnull Block b) {
        Validate.notNull(b, "The Block cannot be null");

        return getOperation(new BlockPosition(b));
    }

    /**
     * This returns the current {@link MachineOperation} at that given {@link BlockPosition}.
     * 
     * @param pos
     *            The {@link BlockPosition} at which our machine is located.
     * 
     * @return The current {@link MachineOperation} or null.
     */
    public @Nullable T getOperation(@Nonnull BlockPosition pos) {
        Validate.notNull(pos, "The BlockPosition must not be null");

        return machines.get(pos);
    }

    /**
     * This will end the {@link MachineOperation} at the given {@link Location}.
     * 
     * @param loc
     *            The {@link Location} at which our machine is located.
     * 
     * @return Whether the {@link MachineOperation} was successfully ended. This will return false if there was no
     *         {@link MachineOperation} to begin with.
     */
    public boolean endOperation(@Nonnull Location loc) {
        Validate.notNull(loc, "The location should not be null");

        return endOperation(new BlockPosition(loc));
    }

    /**
     * This will end the {@link MachineOperation} at the given {@link Block}.
     * 
     * @param b
     *            The {@link Block} at which our machine is located.
     * 
     * @return Whether the {@link MachineOperation} was successfully ended. This will return false if there was no
     *         {@link MachineOperation} to begin with.
     */
    public boolean endOperation(@Nonnull Block b) {
        Validate.notNull(b, "The Block should not be null");

        return endOperation(new BlockPosition(b));
    }

    /**
     * This will end the {@link MachineOperation} at the given {@link BlockPosition}.
     * 
     * @param pos
     *            The {@link BlockPosition} at which our machine is located.
     * 
     * @return Whether the {@link MachineOperation} was successfully ended. This will return false if there was no
     *         {@link MachineOperation} to begin with.
     */
    public boolean endOperation(@Nonnull BlockPosition pos) {
        Validate.notNull(pos, "The BlockPosition cannot be null");

        T operation = machines.remove(pos);

        if (operation != null) {
            /*
             * Only call an event if the operation actually finished.
             * If it was ended prematurely (aka aborted), then we don't call any event.
             */
            if (operation.isFinished()) {
                Event event = new AsyncMachineOperationFinishEvent(pos, this, operation);
                Bukkit.getPluginManager().callEvent(event);
            } else {
                operation.onCancel(pos);
            }

            return true;
        } else {
            return false;
        }
    }

    public void updateProgressBar(@Nonnull BlockMenu inv, int slot, @Nonnull T operation) {
        Validate.notNull(inv, "The inventory must not be null.");
        Validate.notNull(operation, "The MachineOperation must not be null.");

        if (getProgressBar() == null) {
            // No progress bar, no need to update anything.
            return;
        }

        // Update the progress bar in our inventory (if anyone is watching)
        int remainingTicks = operation.getRemainingTicks();
        int totalTicks = operation.getTotalTicks();

        // Fixes #3538 - If the operation is finished, we don't need to update the progress bar.
        if (remainingTicks > 0 || totalTicks > 0) {
            ChestMenuUtils.updateProgressbar(inv, slot, remainingTicks, totalTicks, getProgressBar());
        }
    }

    /**
     * Refunds all in-flight items currently being processed in all active MachineProcessors
     * back to their respective BlockMenus.
     * This method is called during Slimefun onDisable (prior to AutoSavingService and BlockStorage save)
     * to ensure that all items in-flight survive server restarts and shutdowns with zero data loss.
     */
    public static void refundAllActiveProcessors() {
        for (MachineProcessor<?> processor : ALL_PROCESSORS) {
            try {
                processor.refundActiveOperations();
            } catch (Throwable t) {
                Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Failed to refund active operations for processor " + processor.getOwner(), t);
            }
        }
    }

    /**
     * Refunds all active operations handled by this processor.
     */
    public void refundActiveOperations() {
        if (machines.isEmpty()) {
            return;
        }

        for (Map.Entry<BlockPosition, T> entry : machines.entrySet()) {
            BlockPosition pos = entry.getKey();
            T operation = entry.getValue();
            if (operation != null) {
                try {
                    refundOperation(pos, operation);
                } catch (Throwable t) {
                    Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Error refunding operation at " + pos, t);
                }
            }
        }
        machines.clear();
    }

    private void refundOperation(@Nonnull BlockPosition pos, @Nonnull T operation) {
        World world;
        try {
            world = pos.getWorld();
        } catch (IllegalStateException e) {
            return;
        }

        if (world == null) {
            return;
        }

        Location loc = new Location(world, pos.getX(), pos.getY(), pos.getZ());
        BlockMenu menu = BlockStorage.getInventory(loc);

        if (operation instanceof CraftingOperation craftingOp) {
            if (craftingOp.markRefunded()) {
                if (craftingOp.isFinished()) {
                    int[] outputSlots = (owner instanceof InventoryBlock ib) ? ib.getOutputSlots() : new int[] { 24, 25 };
                    for (ItemStack result : craftingOp.getResults()) {
                        if (result != null && !result.getType().isAir()) {
                            if (menu != null) {
                                ItemStack remaining = menu.pushItem(result.clone(), outputSlots);
                                if (remaining != null && remaining.getAmount() > 0) {
                                    world.dropItemNaturally(loc, remaining);
                                }
                            } else {
                                world.dropItemNaturally(loc, result.clone());
                            }
                        }
                    }
                    if (menu != null) {
                        menu.replaceExistingItem(22, new com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack(
                            "_UI_BLANK", org.bukkit.Material.BLACK_STAINED_GLASS_PANE, " "
                        ));
                        menu.markDirty();
                    }
                } else {
                    int[] inputSlots = (owner instanceof InventoryBlock ib) ? ib.getInputSlots() : new int[] { 19, 20 };
                    for (ItemStack ingredient : craftingOp.getIngredients()) {
                        if (ingredient != null && !ingredient.getType().isAir()) {
                            if (menu != null) {
                                ItemStack remaining = menu.pushItem(ingredient.clone(), inputSlots);
                                if (remaining != null && remaining.getAmount() > 0) {
                                    int[] outputSlots = (owner instanceof InventoryBlock ib) ? ib.getOutputSlots() : new int[] { 24, 25 };
                                    remaining = menu.pushItem(remaining, outputSlots);
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
                        menu.replaceExistingItem(22, new com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack(
                            "_UI_BLANK", org.bukkit.Material.BLACK_STAINED_GLASS_PANE, " "
                        ));
                        menu.markDirty();
                    }
                }
            }
        }

        operation.onCancel(pos);
    }

}
