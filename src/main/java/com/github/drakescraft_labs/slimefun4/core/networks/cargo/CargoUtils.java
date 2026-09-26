package com.github.drakescraft_labs.slimefun4.core.networks.cargo;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import dev.drake.dough.inventory.InvUtils;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.core.debug.Debug;
import com.github.drakescraft_labs.slimefun4.core.debug.TestCase;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.utils.PaperLibUtils;
import com.github.drakescraft_labs.slimefun4.utils.SlimefunUtils;
import com.github.drakescraft_labs.slimefun4.utils.itemstack.ItemStackWrapper;
import com.github.drakescraft_labs.slimefun4.utils.tags.SlimefunTag;

import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.DirtyChestMenu;
import com.github.drakescraft_labs.slimefun4.legacy.api.item_transport.ItemTransportFlow;

/**
 * This is a helper class for the {@link CargoNet} which provides
 * static utility methods to let the {@link CargoNet} interact with
 * an {@link Inventory} or {@link BlockMenu}.
 * 
 * Includes direct compatibility with Slimefun Barrels (FluffyMachines) and
 * InfinityExpansion Storage Units (InfinityBarrels), safe simulation peeking,
 * and accurate space calculation.
 * 
 * @author TheBusyBiscuit
 * @author Walshy
 * @author DNx5
 * @author DrakesCraft-Labs
 *
 */
final class CargoUtils {

    /**
     * Represents the result of peeking a candidate item for withdrawal without mutating slots.
     */
    static final class PeekResult {
        private final int slot;
        private final ItemStack item;

        PeekResult(int slot, ItemStack item) {
            this.slot = slot;
            this.item = item;
        }

        public int getSlot() {
            return slot;
        }

        public ItemStack getItem() {
            return item;
        }
    }

    /**
     * These are the slots where our filter items sit.
     */
    private static final int[] FILTER_SLOTS = { 19, 20, 21, 28, 29, 30, 37, 38, 39 };

    /**
     * This is a utility class and should not be instantiated.
     * Therefore we just hide the public constructor.
     */
    private CargoUtils() {}

    /**
     * This is a performance-saving shortcut to quickly test whether a given
     * {@link Block} might be an {@link InventoryHolder} or not.
     * 
     * @param block
     *            The {@link Block} to check
     * 
     * @return Whether this {@link Block} represents a {@link BlockState} that is an {@link InventoryHolder}
     */
    static boolean hasInventory(@Nullable Block block) {
        if (block == null) {
            return false;
        }

        Material type = block.getType();
        return SlimefunTag.CARGO_SUPPORTED_STORAGE_BLOCKS.isTagged(type);
    }

    @Nonnull
    static int[] getInputSlotRange(@Nonnull Inventory inv, @Nullable ItemStack item) {
        if (inv instanceof FurnaceInventory) {
            if (item != null && item.getType().isFuel()) {
                if (isSmeltable(item, true)) {
                    return new int[] { 0, 2 };
                } else {
                    return new int[] { 1, 2 };
                }
            } else {
                return new int[] { 0, 1 };
            }
        } else if (inv instanceof BrewerInventory) {
            if (isPotion(item)) {
                return new int[] { 0, 3 };
            } else if (item != null && item.getType() == Material.BLAZE_POWDER) {
                return new int[] { 4, 5 };
            } else {
                return new int[] { 3, 4 };
            }
        } else {
            return new int[] { 0, inv.getSize() };
        }
    }

    @Nonnull
    static int[] getOutputSlotRange(@Nonnull Inventory inv) {
        if (inv instanceof FurnaceInventory) {
            return new int[] { 2, 3 };
        } else if (inv instanceof BrewerInventory) {
            return new int[] { 0, 3 };
        } else {
            return new int[] { 0, inv.getSize() };
        }
    }

    /**
     * Peeks an eligible candidate item from the target block without removing it.
     */
    @Nullable
    static PeekResult peekCandidate(@Nonnull AbstractItemNetwork network, @Nonnull Map<Location, Inventory> inventories, @Nonnull Block node, @Nonnull Block target) {
        prepareTargetBeforeWithdraw(target);

        DirtyChestMenu menu = getChestMenu(target);
        if (menu != null) {
            for (int slot : menu.getPreset().getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null)) {
                ItemStack is = menu.getItemInSlot(slot);
                if (is != null && is.getType() != Material.AIR && matchesFilter(network, node, is)) {
                    return new PeekResult(slot, is.clone());
                }
            }
        } else if (hasInventory(target)) {
            Inventory inventory = inventories.get(target.getLocation());
            if (inventory == null) {
                BlockState state = PaperLibUtils.getBlockState(target, false).getState();
                if (state instanceof InventoryHolder inventoryHolder) {
                    inventory = inventoryHolder.getInventory();
                    inventories.put(target.getLocation(), inventory);
                }
            }
            if (inventory != null) {
                ItemStack[] contents = inventory.getContents();
                int[] range = getOutputSlotRange(inventory);
                int minSlot = range[0];
                int maxSlot = range[1];
                for (int slot = minSlot; slot < maxSlot; slot++) {
                    ItemStack item = contents[slot];
                    if (item != null && item.getType() != Material.AIR && matchesFilter(network, node, item)) {
                        return new PeekResult(slot, item.clone());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Safely withdraws up to amountToWithdraw from the specified slot of target.
     */
    @Nullable
    static ItemStack withdrawAmount(@Nonnull AbstractItemNetwork network, @Nonnull Map<Location, Inventory> inventories, @Nonnull Block node, @Nonnull Block target, int slot, int amountToWithdraw) {
        DirtyChestMenu menu = getChestMenu(target);
        if (menu != null) {
            ItemStack is = menu.getItemInSlot(slot);
            if (is == null || is.getType() == Material.AIR || !matchesFilter(network, node, is)) {
                return null;
            }
            int withdrawCount = Math.min(amountToWithdraw, is.getAmount());
            ItemStack result = is.clone();
            result.setAmount(withdrawCount);
            if (is.getAmount() <= withdrawCount) {
                menu.replaceExistingItem(slot, null);
            } else {
                is.setAmount(is.getAmount() - withdrawCount);
                menu.replaceExistingItem(slot, is);
            }
            return result;
        } else if (hasInventory(target)) {
            Inventory inventory = inventories.get(target.getLocation());
            if (inventory == null) {
                BlockState state = PaperLibUtils.getBlockState(target, false).getState();
                if (state instanceof InventoryHolder inventoryHolder) {
                    inventory = inventoryHolder.getInventory();
                    inventories.put(target.getLocation(), inventory);
                }
            }
            if (inventory != null) {
                ItemStack is = inventory.getItem(slot);
                if (is == null || is.getType() == Material.AIR || !matchesFilter(network, node, is)) {
                    return null;
                }
                int withdrawCount = Math.min(amountToWithdraw, is.getAmount());
                ItemStack result = is.clone();
                result.setAmount(withdrawCount);
                if (is.getAmount() <= withdrawCount) {
                    inventory.setItem(slot, null);
                } else {
                    is.setAmount(is.getAmount() - withdrawCount);
                    inventory.setItem(slot, is);
                }
                return result;
            }
        }
        return null;
    }

    /**
     * Calculates total available space across all given destination locations for the candidate item.
     */
    static int calculateAvailableSpace(@Nonnull AbstractItemNetwork network, @Nonnull Map<Location, Inventory> inventories, @Nonnull Collection<Location> destinations, @Nonnull ItemStack candidate) {
        ItemStackWrapper wrapper = ItemStackWrapper.wrap(candidate);
        int totalCapacity = 0;
        int maxNeeded = candidate.getAmount();

        for (Location output : destinations) {
            Optional<Block> targetOpt = network.getAttachedBlock(output);
            if (targetOpt.isEmpty()) {
                continue;
            }
            Block outputNodeBlock = output.getBlock();
            if (!matchesFilter(network, outputNodeBlock, candidate)) {
                continue;
            }
            Block target = targetOpt.get();
            int space = getAvailableSpaceInTarget(inventories, outputNodeBlock, target, candidate, wrapper);
            totalCapacity += space;
            if (totalCapacity >= maxNeeded) {
                return maxNeeded;
            }
        }
        return totalCapacity;
    }

    /**
     * Calculates available space in a single destination block for the specified item.
     */
    static int getAvailableSpaceInTarget(@Nonnull Map<Location, Inventory> inventories, @Nonnull Block node, @Nonnull Block target, @Nonnull ItemStack item, @Nonnull ItemStackWrapper wrapper) {
        prepareTargetBeforeInsert(target, item);

        DirtyChestMenu menu = getChestMenu(target);
        int space = 0;
        int maxStack = item.getType().getMaxStackSize();

        if (menu != null) {
            for (int slot : menu.getPreset().getSlotsAccessedByItemTransport(menu, ItemTransportFlow.INSERT, wrapper)) {
                ItemStack inSlot = menu.getItemInSlot(slot);
                if (inSlot == null || inSlot.getType() == Material.AIR) {
                    space += maxStack;
                } else if (SlimefunUtils.isItemSimilar(inSlot, wrapper, true, false)) {
                    int room = maxStack - inSlot.getAmount();
                    if (room > 0) {
                        space += room;
                    }
                }
                if (space >= item.getAmount()) {
                    return space;
                }
            }
        } else if (hasInventory(target)) {
            Inventory inv = inventories.get(target.getLocation());
            if (inv == null) {
                BlockState state = PaperLibUtils.getBlockState(target, false).getState();
                if (state instanceof InventoryHolder inventoryHolder) {
                    inv = inventoryHolder.getInventory();
                    inventories.put(target.getLocation(), inv);
                }
            }
            if (inv != null) {
                if (!InvUtils.isItemAllowed(item.getType(), inv.getType())) {
                    return 0;
                }
                ItemStack[] contents = inv.getContents();
                int[] range = getInputSlotRange(inv, item);
                for (int slot = range[0]; slot < range[1]; slot++) {
                    ItemStack inSlot = contents[slot];
                    if (inSlot == null || inSlot.getType() == Material.AIR) {
                        space += maxStack;
                    } else if (SlimefunUtils.isItemSimilar(inSlot, wrapper, true, false)) {
                        int room = maxStack - inSlot.getAmount();
                        if (room > 0) {
                            space += room;
                        }
                    }
                    if (space >= item.getAmount()) {
                        return space;
                    }
                }
            }
        }
        return space;
    }

    @Nullable
    static ItemStack withdraw(AbstractItemNetwork network, Map<Location, Inventory> inventories, Block node, Block target, ItemStack template) {
        prepareTargetBeforeWithdraw(target);

        DirtyChestMenu menu = getChestMenu(target);

        if (menu == null) {
            if (hasInventory(target)) {
                Inventory inventory = inventories.get(target.getLocation());

                if (inventory != null) {
                    return withdrawFromVanillaInventory(network, node, template, inventory);
                }

                BlockState state = PaperLibUtils.getBlockState(target, false).getState();

                if (state instanceof InventoryHolder inventoryHolder) {
                    inventory = inventoryHolder.getInventory();
                    inventories.put(target.getLocation(), inventory);
                    return withdrawFromVanillaInventory(network, node, template, inventory);
                }
            }

            return null;
        }

        ItemStackWrapper wrapperTemplate = ItemStackWrapper.wrap(template);

        for (int slot : menu.getPreset().getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null)) {
            ItemStack is = menu.getItemInSlot(slot);
            if (is == null || is.getType() == Material.AIR) {
                continue;
            }
            ItemStackWrapper wrapperItemInSlot = ItemStackWrapper.wrap(is);

            if (SlimefunUtils.isItemSimilar(wrapperItemInSlot, wrapperTemplate, true) && matchesFilter(network, node, wrapperItemInSlot)) {
                if (is.getAmount() > template.getAmount()) {
                    is.setAmount(is.getAmount() - template.getAmount());
                    menu.replaceExistingItem(slot, is);
                    return template;
                } else {
                    menu.replaceExistingItem(slot, null);
                    return is;
                }
            }
        }

        return null;
    }

    @Nullable
    static ItemStack withdrawFromVanillaInventory(AbstractItemNetwork network, Block node, ItemStack template, Inventory inv) {
        ItemStack[] contents = inv.getContents();
        int[] range = getOutputSlotRange(inv);
        int minSlot = range[0];
        int maxSlot = range[1];

        ItemStackWrapper wrapper = ItemStackWrapper.wrap(template);

        for (int slot = minSlot; slot < maxSlot; slot++) {
            ItemStack itemInSlot = contents[slot];
            if (itemInSlot == null || itemInSlot.getType().isAir()) {
                continue;
            }

            ItemStackWrapper wrapperInSlot = ItemStackWrapper.wrap(itemInSlot);
            if (SlimefunUtils.isItemSimilar(wrapperInSlot, wrapper, true, false) && matchesFilter(network, node, wrapperInSlot)) {
                if (itemInSlot.getAmount() > template.getAmount()) {
                    itemInSlot.setAmount(itemInSlot.getAmount() - template.getAmount());
                    return template;
                } else {
                    ItemStack clone = itemInSlot.clone();
                    itemInSlot.setAmount(0);
                    return clone;
                }
            }
        }

        return null;
    }

    @Nullable
    static ItemStackAndInteger withdraw(AbstractItemNetwork network, Map<Location, Inventory> inventories, Block node, Block target) {
        prepareTargetBeforeWithdraw(target);

        DirtyChestMenu menu = getChestMenu(target);

        if (menu != null) {
            for (int slot : menu.getPreset().getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null)) {
                ItemStack is = menu.getItemInSlot(slot);

                if (is != null && is.getType() != Material.AIR && matchesFilter(network, node, is)) {
                    menu.replaceExistingItem(slot, null);
                    return new ItemStackAndInteger(is, slot);
                }
            }
        } else if (hasInventory(target)) {
            Inventory inventory = inventories.get(target.getLocation());

            if (inventory != null) {
                return withdrawFromVanillaInventory(network, node, inventory);
            }

            BlockState state = PaperLibUtils.getBlockState(target, false).getState();

            if (state instanceof InventoryHolder inventoryHolder) {
                inventory = inventoryHolder.getInventory();
                inventories.put(target.getLocation(), inventory);
                return withdrawFromVanillaInventory(network, node, inventory);
            }
        }

        return null;
    }

    @Nullable
    private static ItemStackAndInteger withdrawFromVanillaInventory(AbstractItemNetwork network, Block node, Inventory inv) {
        ItemStack[] contents = inv.getContents();
        int[] range = getOutputSlotRange(inv);
        int minSlot = range[0];
        int maxSlot = range[1];

        for (int slot = minSlot; slot < maxSlot; slot++) {
            ItemStack item = contents[slot];

            if (item != null && item.getType() != Material.AIR && matchesFilter(network, node, item)) {
                inv.setItem(slot, null);
                return new ItemStackAndInteger(item, slot);
            }
        }

        return null;
    }

    @Nullable
    static ItemStack insert(AbstractItemNetwork network, Map<Location, Inventory> inventories, Block node, Block target, boolean smartFill, ItemStack stack, ItemStackWrapper wrapper) {
        Debug.log(TestCase.CARGO_INPUT_TESTING, "CargoUtils#insert");
        if (!matchesFilter(network, node, stack)) {
            return stack;
        }

        DirtyChestMenu menu = getChestMenu(target);

        if (menu == null) {
            if (hasInventory(target)) {
                Inventory inventory = inventories.get(target.getLocation());

                if (inventory != null) {
                    return insertIntoVanillaInventory(stack, wrapper, smartFill, inventory);
                }

                BlockState state = PaperLibUtils.getBlockState(target, false).getState();

                if (state instanceof InventoryHolder inventoryHolder) {
                    inventory = inventoryHolder.getInventory();
                    inventories.put(target.getLocation(), inventory);
                    return insertIntoVanillaInventory(stack, wrapper, smartFill, inventory);
                }
            }

            return stack;
        }

        prepareTargetBeforeInsert(target, stack);

        for (int slot : menu.getPreset().getSlotsAccessedByItemTransport(menu, ItemTransportFlow.INSERT, wrapper)) {
            ItemStack itemInSlot = menu.getItemInSlot(slot);

            if (itemInSlot == null || itemInSlot.getType() == Material.AIR) {
                menu.replaceExistingItem(slot, stack);
                postProcessTargetAfterInsert(target);
                return null;
            }

            int maxStackSize = itemInSlot.getType().getMaxStackSize();
            int currentAmount = itemInSlot.getAmount();

            if (!smartFill && currentAmount == maxStackSize) {
                // Skip full stacks - Performance optimization for non-smartfill nodes
                continue;
            }

            if (SlimefunUtils.isItemSimilar(itemInSlot, wrapper, true, false)) {
                if (currentAmount < maxStackSize) {
                    int amount = currentAmount + stack.getAmount();

                    itemInSlot.setAmount(Math.min(amount, maxStackSize));
                    if (amount > maxStackSize) {
                        stack.setAmount(amount - maxStackSize);
                    } else {
                        stack = null;
                    }

                    menu.replaceExistingItem(slot, itemInSlot);
                    postProcessTargetAfterInsert(target);
                    return stack;
                } else if (smartFill) {
                    return stack;
                }
            }
        }

        return stack;
    }

    @Nullable
    private static ItemStack insertIntoVanillaInventory(@Nonnull ItemStack stack, @Nonnull ItemStackWrapper wrapper, boolean smartFill, @Nonnull Inventory inv) {
        if (!InvUtils.isItemAllowed(stack.getType(), inv.getType())) {
            return stack;
        }

        ItemStack[] contents = inv.getContents();
        int[] range = getInputSlotRange(inv, stack);
        int minSlot = range[0];
        int maxSlot = range[1];

        for (int slot = minSlot; slot < maxSlot; slot++) {
            ItemStack itemInSlot = contents[slot];

            if (itemInSlot == null || itemInSlot.getType() == Material.AIR) {
                inv.setItem(slot, stack);
                return null;
            } else {
                int currentAmount = itemInSlot.getAmount();
                int maxStackSize = itemInSlot.getType().getMaxStackSize();

                if (!smartFill && currentAmount == maxStackSize) {
                    continue;
                }

                if (SlimefunUtils.isItemSimilar(itemInSlot, wrapper, true, false)) {
                    if (currentAmount < maxStackSize) {
                        int amount = currentAmount + stack.getAmount();

                        if (amount > maxStackSize) {
                            stack.setAmount(amount - maxStackSize);
                            itemInSlot.setAmount(maxStackSize);
                            return stack;
                        } else {
                            itemInSlot.setAmount(Math.min(amount, maxStackSize));
                            return null;
                        }
                    } else if (smartFill) {
                        return stack;
                    }
                }
            }
        }

        return stack;
    }

    /**
     * Hook called before withdrawal from target to replenish output slots if target is a barrel or storage unit.
     */
    static void prepareTargetBeforeWithdraw(@Nonnull Block target) {
        String sfId = BlockStorage.checkID(target);
        if (sfId == null) {
            return;
        }
        SlimefunItem sfItem = SlimefunItem.getById(sfId);
        if (sfItem == null) {
            return;
        }

        // 1. InfinityExpansion StorageUnit
        if (sfItem.getClass().getName().contains("StorageUnit")) {
            try {
                Method getCacheMethod = sfItem.getClass().getDeclaredMethod("getCache", Location.class);
                getCacheMethod.setAccessible(true);
                Object cache = getCacheMethod.invoke(sfItem, target.getLocation());
                if (cache != null) {
                    Method outputMethod = cache.getClass().getDeclaredMethod("output");
                    outputMethod.setAccessible(true);
                    outputMethod.invoke(cache);
                }
            } catch (Exception ignored) {
            }
            return;
        }

        // 2. FluffyMachines Barrel
        if (sfItem.getClass().getName().contains("Barrel")) {
            try {
                DirtyChestMenu menu = getChestMenu(target);
                if (menu instanceof BlockMenu blockMenu) {
                    Method getCapacityMethod = sfItem.getClass().getDeclaredMethod("getCapacity", Block.class);
                    getCapacityMethod.setAccessible(true);
                    int cap = (Integer) getCapacityMethod.invoke(sfItem, target);

                    Method pushOutputMethod = sfItem.getClass().getDeclaredMethod("pushOutput", BlockMenu.class, Block.class, int.class);
                    pushOutputMethod.setAccessible(true);
                    pushOutputMethod.invoke(sfItem, blockMenu, target, cap);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Hook called before inserting to empty input buffer slots into storage if target is a barrel or storage unit.
     */
    static void prepareTargetBeforeInsert(@Nonnull Block target, @Nullable ItemStack item) {
        String sfId = BlockStorage.checkID(target);
        if (sfId == null) {
            return;
        }
        SlimefunItem sfItem = SlimefunItem.getById(sfId);
        if (sfItem == null) {
            return;
        }

        // 1. InfinityExpansion StorageUnit
        if (sfItem.getClass().getName().contains("StorageUnit")) {
            try {
                Method getCacheMethod = sfItem.getClass().getDeclaredMethod("getCache", Location.class);
                getCacheMethod.setAccessible(true);
                Object cache = getCacheMethod.invoke(sfItem, target.getLocation());
                if (cache != null) {
                    Method inputMethod = cache.getClass().getDeclaredMethod("input");
                    inputMethod.setAccessible(true);
                    inputMethod.invoke(cache);
                }
            } catch (Exception ignored) {
            }
            return;
        }

        // 2. FluffyMachines Barrel
        if (sfItem.getClass().getName().contains("Barrel")) {
            try {
                DirtyChestMenu menu = getChestMenu(target);
                if (menu instanceof BlockMenu blockMenu) {
                    Method getCapacityMethod = sfItem.getClass().getDeclaredMethod("getCapacity", Block.class);
                    getCapacityMethod.setAccessible(true);
                    int cap = (Integer) getCapacityMethod.invoke(sfItem, target);

                    Method acceptInputMethod = sfItem.getClass().getDeclaredMethod("acceptInput", BlockMenu.class, Block.class, int.class, int.class);
                    acceptInputMethod.setAccessible(true);
                    acceptInputMethod.invoke(sfItem, blockMenu, target, 19, cap);
                    acceptInputMethod.invoke(sfItem, blockMenu, target, 20, cap);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Hook called after insertion to absorb items immediately from input slots into the storage cache.
     */
    static void postProcessTargetAfterInsert(@Nonnull Block target) {
        prepareTargetBeforeInsert(target, null);
    }

    @Nullable
    static DirtyChestMenu getChestMenu(@Nonnull Block block) {
        if (BlockStorage.hasInventory(block)) {
            return BlockStorage.getInventory(block);
        } else {
            return BlockStorage.getUniversalInventory(block);
        }
    }

    static boolean matchesFilter(@Nonnull AbstractItemNetwork network, @Nonnull Block node, @Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        return network.getItemFilter(node).test(item);
    }

    private static boolean isSmeltable(@Nullable ItemStack stack, boolean lazy) {
        if (lazy) {
            return stack != null && Tag.LOGS.isTagged(stack.getType());
        } else {
            return Slimefun.getMinecraftRecipeService().isSmeltable(stack);
        }
    }

    private static boolean isPotion(@Nullable ItemStack item) {
        if (item != null) {
            Material type = item.getType();
            return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
        } else {
            return false;
        }
    }

    @Nonnull
    public static int[] getFilteringSlots() {
        return FILTER_SLOTS;
    }
}
