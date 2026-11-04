package com.github.drakescraft_labs.slimefun4.legacy.api.inventory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.drakescraft_labs.slimefun4.legacy.api.item_transport.ItemTransportFlow;

/** Test addon: install only in a disposable server with fresh data. */
public final class SnapshotBatchProbe extends JavaPlugin {

    private final Path result = Path.of("snapshot-batch-result.txt");

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, this::runProbe, 40);
    }

    private void runProbe() {
        World world = getServer().getWorlds().get(0);
        NamespacedKey key = new NamespacedKey("slimefun", "batch_probe");
        if (Files.exists(result)) {
            verify(world, key, "RESTART", 0, 0);
            return;
        }

        BlockMenuPreset preset = new BlockMenuPreset("SNAPSHOT_BATCH_PROBE", "Snapshot probe") {
            @Override
            public void init() {
                setSize(9);
            }

            @Override
            public boolean canOpen(Block block, Player player) {
                return false;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
        Map<Location, BlockMenu> menus = new LinkedHashMap<>();
        for (int x = 0; x < 128; x++) {
            Location location = new Location(world, x, 64, 0);
            BlockMenu menu = new BlockMenu(preset, location);
            ItemStack item = new ItemStack(Material.DIAMOND, 7);
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "original");
            item.setItemMeta(meta);
            menu.replaceExistingItem(4, item);
            menus.put(location, menu);
        }
        AtomicLong lastTick = new AtomicLong(System.nanoTime());
        AtomicLong maxTickGap = new AtomicLong();
        var heartbeat = getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.nanoTime();
            maxTickGap.accumulateAndGet(now - lastTick.getAndSet(now), Math::max);
        }, 1, 1);
        long started = System.nanoTime();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                int remaining = BlockMenu.saveAll(menus, 128);
                if (remaining != 0 || menus.values().stream().anyMatch(BlockMenu::isDirty)) {
                    throw new IllegalStateException("Incomplete snapshot batch");
                }
                long elapsed = System.nanoTime() - started;
                getServer().getScheduler().runTask(this, () -> {
                    heartbeat.cancel();
                    verify(world, key, "WRITE", elapsed, maxTickGap.get());
                });
            } catch (Throwable failure) {
                heartbeat.cancel();
                finish("FAIL " + failure.getClass().getSimpleName());
            }
        });
    }

    private void verify(World world, NamespacedKey key, String phase, long elapsed, long gap) {
        try {
            for (int x = 0; x < 128; x++) {
                Path file = Path.of("data-storage/Slimefun/stored-inventories/" + world.getName() + ';' + x + ";64;0.sfi");
                YamlConfiguration cfg = readInventory(file);
                ItemStack item = cfg.getItemStack("4");
                if (item == null || item.getAmount() != 7 || !"original".equals(item.getItemMeta()
                        .getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                    throw new IllegalStateException("Snapshot or PDC mismatch");
                }
            }
            finish("PASS " + phase + " menus=128 elapsed_ms=" + elapsed / 1_000_000
                + " max_tick_gap_ms=" + gap / 1_000_000);
        } catch (Throwable failure) {
            finish("FAIL " + phase + ' ' + failure.getClass().getSimpleName());
        }
    }

    private YamlConfiguration readInventory(Path file) throws Exception {
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    private void finish(String message) {
        getLogger().info("T597_" + message);
        try {
            Files.writeString(result, message + System.lineSeparator());
        } catch (java.io.IOException failure) {
            getLogger().severe("Could not persist probe result");
        }
    }
}
