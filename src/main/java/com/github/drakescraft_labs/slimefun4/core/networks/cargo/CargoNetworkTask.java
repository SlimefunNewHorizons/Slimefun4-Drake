package com.github.drakescraft_labs.slimefun4.core.networks.cargo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.drake.dough.blocks.BlockPosition;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.core.networks.NetworkManager;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.SlimefunItems;
import com.github.drakescraft_labs.slimefun4.utils.itemstack.ItemStackWrapper;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.DirtyChestMenu;

/**
 * The {@link CargoNetworkTask} is the actual {@link Runnable} responsible for moving {@link ItemStack ItemStacks}
 * around the {@link CargoNet}.
 * 
 * Upgraded with safe simulation (zero ground drops), direct barrel/storage compatibility,
 * and dynamic multi-batch transfer per tick.
 * 
 * @see CargoNet
 * @see CargoUtils
 * @see AbstractItemNetwork
 *
 */
class CargoNetworkTask implements Runnable {

    private final NetworkManager manager;
    private final CargoNet network;
    private final Map<Location, Inventory> inventories = new HashMap<>();

    private final Map<Location, Integer> inputs;
    private final Map<Integer, List<Location>> outputs;

    @ParametersAreNonnullByDefault
    CargoNetworkTask(CargoNet network, Map<Location, Integer> inputs, Map<Integer, List<Location>> outputs) {
        this.network = network;
        this.manager = Slimefun.getNetworkManager();

        this.inputs = inputs;
        this.outputs = outputs;
    }

    @Override
    public void run() {
        long timestamp = System.nanoTime();

        try {
            SlimefunItem inputNode = SlimefunItems.CARGO_INPUT_NODE.getItem();
            int maxBatch = Slimefun.getCfg().contains("networks.cargo-batch-per-tick")
                    ? Math.max(1, Slimefun.getCfg().getInt("networks.cargo-batch-per-tick"))
                    : 2;

            for (Map.Entry<Location, Integer> entry : inputs.entrySet()) {
                long nodeTimestamp = System.nanoTime();
                Location input = entry.getKey();
                Optional<Block> attachedBlock = network.getAttachedBlock(input);

                if (attachedBlock.isPresent()) {
                    Block block = attachedBlock.get();
                    int frequency = entry.getValue();

                    for (int b = 0; b < maxBatch; b++) {
                        boolean transferred = routeItems(input, block, frequency, outputs);
                        if (!transferred) {
                            break;
                        }
                    }
                }

                // This will prevent this timings from showing up for the Cargo Manager
                timestamp += Slimefun.getProfiler().closeEntry(entry.getKey(), inputNode, nodeTimestamp);
            }
        } catch (Exception | LinkageError x) {
            Slimefun.logger().log(Level.SEVERE, x, () -> "An Exception was caught while ticking a Cargo network @ " + new BlockPosition(network.getRegulator()));
        }

        // Submit a timings report
        Slimefun.getProfiler().closeEntry(network.getRegulator(), SlimefunItems.CARGO_MANAGER.getItem(), timestamp);
    }

    @ParametersAreNonnullByDefault
    private boolean routeItems(Location inputNode, Block inputTarget, int frequency, Map<Integer, List<Location>> outputNodes) {
        List<Location> destinations = outputNodes.get(frequency);
        if (destinations == null || destinations.isEmpty()) {
            return false;
        }

        // 1. Process any pending overflow from previous ticks for this input node first
        ItemStack pending = network.getPendingOverflow(inputNode);
        if (pending != null && pending.getAmount() > 0) {
            pending = distributeItem(pending, inputNode, destinations);
            if (pending == null || pending.getAmount() <= 0) {
                network.clearPendingOverflow(inputNode);
            } else {
                network.setPendingOverflow(inputNode, pending);
                return false;
            }
        }

        // 2. Safe-simulation: Peek candidate item in input target without withdrawing
        CargoUtils.PeekResult peek = CargoUtils.peekCandidate(network, inventories, inputNode.getBlock(), inputTarget);
        if (peek == null || peek.getItem() == null || peek.getItem().getAmount() <= 0) {
            return false;
        }

        ItemStack candidate = peek.getItem();
        int slot = peek.getSlot();

        // 3. Determine how much space is actually available across the destinations
        Collection<Location> orderedDestinations = getOrderedDestinations(inputNode, destinations);
        int space = CargoUtils.calculateAvailableSpace(network, inventories, orderedDestinations, candidate);
        if (space <= 0) {
            return false;
        }

        int toWithdraw = Math.min(candidate.getAmount(), space);

        // 4. Safely withdraw only the exact amount that destinations can accept
        ItemStack withdrawn = CargoUtils.withdrawAmount(network, inventories, inputNode.getBlock(), inputTarget, slot, toWithdraw);
        if (withdrawn == null || withdrawn.getAmount() <= 0) {
            return false;
        }

        // 5. Distribute the item to destinations
        ItemStack remaining = distributeItem(withdrawn, inputNode, destinations);

        // 6. Safe rollback if unexpected race condition occurred (never drop onto floor!)
        if (remaining != null && remaining.getAmount() > 0) {
            insertItemSafe(inputNode, inputTarget, slot, remaining);
        }

        return true;
    }

    @ParametersAreNonnullByDefault
    private void insertItemSafe(Location inputNode, Block inputTarget, int previousSlot, ItemStack item) {
        Inventory inv = inventories.get(inputTarget.getLocation());

        if (inv != null) {
            ItemStack currentInSlot = inv.getItem(previousSlot);
            if (currentInSlot == null || currentInSlot.getType().isAir()) {
                inv.setItem(previousSlot, item);
                return;
            } else if (currentInSlot.isSimilar(item)) {
                int maxStack = currentInSlot.getType().getMaxStackSize();
                int space = maxStack - currentInSlot.getAmount();
                if (space >= item.getAmount()) {
                    currentInSlot.setAmount(currentInSlot.getAmount() + item.getAmount());
                    return;
                } else if (space > 0) {
                    currentInSlot.setAmount(maxStack);
                    item.setAmount(item.getAmount() - space);
                }
            }

            Map<Integer, ItemStack> leftover = inv.addItem(item);
            if (leftover.isEmpty()) {
                return;
            }

            ItemStack rest = leftover.values().iterator().next();
            if (rest != null && rest.getAmount() > 0 && !manager.isItemDeletionEnabled()) {
                network.setPendingOverflow(inputNode, rest);
            }
        } else {
            DirtyChestMenu menu = CargoUtils.getChestMenu(inputTarget);

            if (menu != null) {
                ItemStack currentInSlot = menu.getItemInSlot(previousSlot);
                if (currentInSlot == null || currentInSlot.getType().isAir()) {
                    menu.replaceExistingItem(previousSlot, item);
                    return;
                } else if (currentInSlot.isSimilar(item)) {
                    int maxStack = currentInSlot.getType().getMaxStackSize();
                    int space = maxStack - currentInSlot.getAmount();
                    if (space >= item.getAmount()) {
                        currentInSlot.setAmount(currentInSlot.getAmount() + item.getAmount());
                        menu.replaceExistingItem(previousSlot, currentInSlot);
                        return;
                    } else if (space > 0) {
                        currentInSlot.setAmount(maxStack);
                        menu.replaceExistingItem(previousSlot, currentInSlot);
                        item.setAmount(item.getAmount() - space);
                    }
                }

                if (!manager.isItemDeletionEnabled()) {
                    network.setPendingOverflow(inputNode, item);
                }
            } else {
                if (!manager.isItemDeletionEnabled()) {
                    network.setPendingOverflow(inputNode, item);
                }
            }
        }
    }

    @Nonnull
    @ParametersAreNonnullByDefault
    private Collection<Location> getOrderedDestinations(Location inputNode, List<Location> outputNodes) {
        Config cfg = BlockStorage.getLocationInfo(inputNode);
        boolean roundrobin = cfg != null && Objects.equals(cfg.getString("round-robin"), "true");

        if (roundrobin) {
            int index = network.roundRobin.getOrDefault(inputNode, 0);
            Deque<Location> tempDestinations = new ArrayDeque<>(outputNodes);
            roundRobinSort(index, tempDestinations);
            return tempDestinations;
        } else {
            return new ArrayList<>(outputNodes);
        }
    }

    @Nullable
    @ParametersAreNonnullByDefault
    private ItemStack distributeItem(ItemStack stack, Location inputNode, List<Location> outputNodes) {
        ItemStack item = stack;

        Config cfg = BlockStorage.getLocationInfo(inputNode);
        boolean roundrobin = cfg != null && Objects.equals(cfg.getString("round-robin"), "true");
        boolean smartFill = cfg != null && Objects.equals(cfg.getString("smart-fill"), "true");

        int index = 0;
        Collection<Location> destinations = getOrderedDestinations(inputNode, outputNodes);

        for (Location output : destinations) {
            Optional<Block> target = network.getAttachedBlock(output);

            if (target.isPresent()) {
                ItemStackWrapper wrapper = ItemStackWrapper.wrap(item);
                item = CargoUtils.insert(network, inventories, output.getBlock(), target.get(), smartFill, item, wrapper);

                if (item == null || item.getAmount() <= 0) {
                    if (roundrobin) {
                        network.roundRobin.put(inputNode, (index + 1) % outputNodes.size());
                    }
                    return null;
                }
            }
            index++;
        }

        return item;
    }

    private void roundRobinSort(int index, Deque<Location> outputNodes) {
        if (index < outputNodes.size()) {
            for (int i = 0; i < index; i++) {
                Location temp = outputNodes.removeFirst();
                outputNodes.add(temp);
            }
        }
    }

}
