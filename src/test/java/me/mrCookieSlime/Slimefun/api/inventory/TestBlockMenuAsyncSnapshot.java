package me.mrCookieSlime.Slimefun.api.inventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import io.github.thebusybiscuit.slimefun4.utils.FileUtils;
import io.github.bakedlibs.dough.config.Config;

/** Reproduces the snapshot handoffs from #597 without clocks, worlds or player data. */
class TestBlockMenuAsyncSnapshot {

    private final BlockingQueue<FutureTask<?>> snapshots = new LinkedBlockingQueue<>();
    private ServerMock server;
    private ExecutorService writer;

    @BeforeEach
    void load() {
        BukkitSchedulerMock scheduler = new BukkitSchedulerMock() {
            @Override
            public <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) {
                FutureTask<T> future = new FutureTask<>(task);
                snapshots.add(future);
                return future;
            }
        };
        server = MockBukkit.mock(new ServerMock() {
            @Override
            public BukkitSchedulerMock getScheduler() {
                return scheduler;
            }
        });
        MockBukkit.load(Slimefun.class);
        writer = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void unload() throws IOException, InterruptedException {
        writer.shutdownNow();
        Assertions.assertTrue(writer.awaitTermination(5, TimeUnit.SECONDS));
        MockBukkit.unmock();
        FileUtils.deleteDirectory(new File("data-storage"));
    }

    private BlockMenu menu(World world, int x) {
        BlockMenuPreset preset = new BlockMenuPreset("ASYNC_SNAPSHOT_" + x, "Snapshot test") {
            @Override
            public void init() {
                setSize(9);
            }

            @Override
            public boolean canOpen(Block block, Player player) {
                return true;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
        BlockMenu menu = new BlockMenu(preset, new Location(world, x, 64, 0));
        menu.replaceExistingItem(4, new ItemStack(Material.DIAMOND, 7));
        return menu;
    }

    private FutureTask<?> nextSnapshot() throws InterruptedException {
        FutureTask<?> task = snapshots.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(task, "the async writer must request a main-thread snapshot");
        return task;
    }

    private Config saved(BlockMenu menu) {
        io.github.thebusybiscuit.slimefun4.storage.backend.SfStores.get().flush();
        Location location = menu.getLocation();
        return new Config(new File("data-storage/Slimefun/stored-inventories/"
            + location.getWorld().getName() + ';' + location.getBlockX() + ";64;0.sfi"));
    }

    @Test
    void testLargeAsyncPassMakesProgressWithoutBlockingMainThread() throws Exception {
        World world = server.addSimpleWorld("async_snapshot_pass");
        List<BlockMenu> menus = new ArrayList<>();
        for (int x = 0; x < 128; x++) {
            menus.add(menu(world, x));
        }

        Future<?> pass = writer.submit(() -> menus.forEach(menu -> menu.save(menu.getLocation())));
        int handoffs = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!pass.isDone()) {
            FutureTask<?> task = snapshots.poll(100, TimeUnit.MILLISECONDS);
            if (task != null) {
                task.run(); // The test thread is MockBukkit's main thread.
                task.get(5, TimeUnit.SECONDS);
                handoffs++;
            }
            Assertions.assertTrue(handoffs <= menus.size(), "the pass must not requeue saved menus");
            Assertions.assertTrue(System.nanoTime() < deadline, "the pass must finish with a live main thread");
        }
        pass.get(5, TimeUnit.SECONDS);

        for (BlockMenu menu : menus) {
            Assertions.assertEquals(7, saved(menu).getItem("4").getAmount());
            Assertions.assertFalse(menu.isDirty());
        }
        System.out.println("#597: menus=" + menus.size() + ", main-thread handoffs=" + handoffs);
    }

    @Test
    void testSnapshotPreservesPdcAndLeavesLaterChangesDirty() throws Exception {
        BlockMenu menu = menu(server.addSimpleWorld("async_snapshot_pdc"), 1);
        NamespacedKey key = new NamespacedKey("slimefun", "snapshot_test");
        ItemStack live = menu.getItemInSlot(4);
        var meta = live.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "original");
        live.setItemMeta(meta);

        Future<?> save = writer.submit(() -> menu.save(menu.getLocation()));
        FutureTask<?> snapshot = nextSnapshot();
        Assertions.assertFalse(save.isDone(), "the writer must wait for the server snapshot");
        snapshot.run();
        snapshot.get(5, TimeUnit.SECONDS);

        live.setAmount(2);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "changed");
        live.setItemMeta(meta);
        menu.markDirty();
        save.get(5, TimeUnit.SECONDS);

        ItemStack persisted = saved(menu).getItem("4");
        Assertions.assertEquals(7, persisted.getAmount());
        Assertions.assertEquals("original", persisted.getItemMeta().getPersistentDataContainer()
            .get(key, PersistentDataType.STRING));
        Assertions.assertTrue(menu.isDirty(), "a later machine update must survive the async write");
    }
}
